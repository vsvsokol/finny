package ru.finney.pet.web

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

internal val cloudJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
internal data class RemoteProfile(
    val id: Long, val revision: Long, val deleted: Boolean,
    val snapshot: JsonObject? = null,
)

@Serializable
internal data class PendingWrite(
    val method: String, val path: String, val body: String,
    val key: String, val generation: Long,
)

@Serializable
internal data class LocalProfile(
    val localId: Long, val snapshot: JsonObject? = null,
    val serverId: Long? = null, val revision: Long = 0,
    val generation: Long = 0, val dirty: Boolean = false,
    val mode: String = "append", val pending: PendingWrite? = null,
    val conflict: RemoteProfile? = null,
)

@Serializable
internal data class CloudState(
    val format: Int = 1, val nextId: Long = 1,
    val profiles: List<LocalProfile> = emptyList(),
    val backups: List<JsonObject> = emptyList(),
    val guestImported: Boolean = false,
)

internal class CloudFailure(val status: Int, val detail: JsonElement) : Exception("cloud:$status")

internal interface CloudApi {
    suspend fun request(method: String, path: String, body: String? = null, key: String? = null): JsonObject
}

internal fun JsonObject.isDemo(): Boolean = getValue("profile").jsonObject["isDemo"]?.jsonPrimitive?.booleanOrNull == true
internal fun JsonObject.petName(): String = getValue("profile").jsonObject["petName"]?.jsonPrimitive?.content ?: "Финни"

/** Переносим ссылки, не преобразуя JSON-числа в Double: seed может занимать все 64 бита. */
internal fun JsonObject.withProfileId(id: Long): JsonObject = buildJsonObject {
    for ((key, value) in this@withProfileId) {
        put(key, when {
            key == "profile" -> JsonObject(value.jsonObject + ("id" to JsonPrimitive(id)))
            key == "pet" && value is JsonObject -> JsonObject(value + ("profileId" to JsonPrimitive(id)))
            key in listOf("periods", "ledger", "attempts", "uses") -> JsonArray(value.jsonArray.map {
                JsonObject(it.jsonObject + ("profileId" to JsonPrimitive(id)))
            })
            else -> value
        })
    }
}

/** Чистая машина синхронизации. persist обязан записать весь документ до публикации состояния. */
internal class CloudSync(
    val ownerId: Long,
    initial: CloudState,
    private val persist: (CloudState) -> Unit,
    private val newKey: () -> String,
    private val changed: () -> Unit = {},
    private val navigationChanged: () -> Unit = {},
) {
    var state = initial
        private set
    private val mutex = Mutex()
    val conflicts: List<LocalProfile> get() = state.profiles.filter { it.conflict != null }
    val pendingCount: Int get() = state.profiles.count { it.dirty || it.pending != null }

    init { require(initial.format == 1) { "Неизвестный формат локального кэша" } }

    private fun commit(next: CloudState) {
        if (next == state) return
        persist(next)
        state = next
        changed()
    }

    private fun edit(id: Long, transform: (LocalProfile) -> LocalProfile?) {
        commit(state.copy(profiles = state.profiles.mapNotNull { if (it.localId == id) transform(it) else it }))
    }

    fun writeGames(snapshots: Map<Long, JsonObject>, nextId: Long, replace: Set<Long> = emptySet()) {
        val previous = state.profiles.associateBy { it.localId }
        val ids = previous.keys + snapshots.keys
        val rows = ids.mapNotNull { id ->
            val old = previous[id] ?: LocalProfile(id)
            val next = snapshots[id]
            if (old.snapshot == next) old else {
                val localOnly = next == null || next.isDemo()
                if (localOnly && old.serverId == null && old.pending == null) {
                    next?.let { old.copy(snapshot = it, dirty = false, conflict = null) }
                } else old.copy(snapshot = next, dirty = true, generation = old.generation + 1,
                    mode = if (id in replace || old.mode == "replace") "replace" else "append")
            }
        }
        commit(state.copy(profiles = rows, nextId = maxOf(state.nextId, nextId)))
    }

    fun importGuest(snapshots: List<JsonObject>) {
        check(!state.guestImported) { "Гостевые профили уже перенесены" }
        var nextId = state.nextId
        val rows = snapshots.filterNot { it.isDemo() }.map { snapshot ->
            val id = nextId++
            LocalProfile(id, snapshot.withProfileId(id), dirty = true, generation = 1)
        }
        commit(state.copy(profiles = state.profiles + rows, nextId = nextId, guestImported = true))
    }

    /** Обе версии сохраняются до изменения отображаемого профиля. */
    fun resolve(localId: Long, keepBoth: Boolean) {
        val row = state.profiles.first { it.localId == localId }
        check(row.pending == null)
        val remote = checkNotNull(row.conflict)
        val local = row.snapshot
        val backups = state.backups + listOfNotNull(local, remote.snapshot)
        var nextId = state.nextId
        val others = state.profiles.filterNot { it.localId == localId }.toMutableList()
        if (!remote.deleted && remote.snapshot != null) {
            others += LocalProfile(localId, remote.snapshot.withProfileId(localId), remote.id, remote.revision)
        }
        if (keepBoth && local != null) {
            val id = nextId++
            others += LocalProfile(id, local.withProfileId(id), dirty = !local.isDemo(), generation = 1)
        }
        commit(state.copy(profiles = others, backups = backups, nextId = nextId))
    }

    private fun prepare(id: Long) {
        val row = state.profiles.firstOrNull { it.localId == id } ?: return
        if (row.pending != null || row.conflict != null || !row.dirty) return
        val snapshot = row.snapshot
        val deleting = snapshot == null || snapshot.isDemo()
        if (row.serverId == null && deleting) {
            edit(id) { if (snapshot == null) null else it.copy(dirty = false) }
            return
        }
        val method = if (deleting) "DELETE" else if (row.serverId == null) "POST" else "PUT"
        val path = if (row.serverId == null) "/api/profiles" else "/api/profiles/${row.serverId}"
        val body = buildJsonObject {
            if (row.serverId != null) put("expectedRevision", row.revision)
            if (!deleting) {
                put("version", 15)
                put("snapshot", checkNotNull(snapshot).withProfileId(row.serverId ?: row.localId))
                if (row.serverId != null) put("mode", row.mode)
            }
        }.toString()
        edit(id) { it.copy(pending = PendingWrite(method, path, body, newKey(), row.generation)) }
    }

    private suspend fun send(id: Long, api: CloudApi) {
        val before = state.profiles.firstOrNull { it.localId == id } ?: return
        val pending = before.pending ?: return
        val result = try {
            api.request(pending.method, pending.path, pending.body, pending.key)
        } catch (failure: CloudFailure) {
            if (failure.status != 409) throw failure
            val error = failure.detail
            val current = (error as? JsonObject)?.get("current")?.let {
                cloudJson.decodeFromJsonElement<RemoteProfile>(it)
            } ?: if (before.serverId != null && (error as? JsonPrimitive)?.content == "journal_not_append_only") {
                cloudJson.decodeFromJsonElement<RemoteProfile>(api.request("GET", "/api/profiles/${before.serverId}"))
            } else throw failure
            edit(id) { it.copy(pending = null, conflict = current) }
            return
        }
        val serverId = result.getValue("id").jsonPrimitive.long
        val revision = result.getValue("revision").jsonPrimitive.long
        edit(id) { current ->
            check(current.pending == pending)
            if (pending.method == "DELETE") {
                val snapshot = current.snapshot
                if (snapshot == null) null
                else LocalProfile(id, snapshot, generation = current.generation,
                    dirty = !snapshot.isDemo())
            } else current.copy(serverId = serverId, revision = revision, pending = null,
                dirty = current.generation != pending.generation,
                mode = if (current.generation == pending.generation) "append" else current.mode)
        }
    }

    private fun receive(remote: List<RemoteProfile>) {
        var nextId = state.nextId
        val rows = state.profiles.toMutableList()
        for (server in remote) {
            val index = rows.indexOfFirst { it.serverId == server.id }
            if (index < 0) {
                if (server.deleted || server.snapshot == null) continue
                val id = nextId++
                rows += LocalProfile(id, server.snapshot.withProfileId(id), server.id, server.revision)
                continue
            }
            val local = rows[index]
            if (local.pending != null || local.conflict != null) continue
            if (local.dirty) {
                if (server.revision != local.revision) rows[index] = local.copy(conflict = server)
            } else if (server.deleted) rows.removeAt(index)
            else if (server.snapshot != null) rows[index] = local.copy(
                snapshot = server.snapshot.withProfileId(local.localId), revision = server.revision)
        }
        val before = state.profiles.filter { it.snapshot != null }.map { it.localId }.toSet()
        commit(state.copy(profiles = rows, nextId = nextId))
        if (before != rows.filter { it.snapshot != null }.map { it.localId }.toSet()) navigationChanged()
    }

    suspend fun synchronize(api: CloudApi) = mutex.withLock {
        // Ответ на потерянный запрос мог уже попасть на сервер: сначала повторяем его тем же ключом.
        for (id in state.profiles.filter { it.pending != null }.map { it.localId }) send(id, api)
        val listing = api.request("GET", "/api/profiles")
        if (listing["ownerId"]?.jsonPrimitive?.long != ownerId) {
            throw CloudFailure(409, JsonPrimitive("account_changed"))
        }
        receive(cloudJson.decodeFromJsonElement(listing.getValue("profiles")))
        for (id in state.profiles.map { it.localId }) {
            prepare(id)
            send(id, api)
        }
    }
}
