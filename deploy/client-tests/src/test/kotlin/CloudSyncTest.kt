package ru.finney.pet.web

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

private fun pet(name: String, id: Long = 1): JsonObject = cloudJson.parseToJsonElement("""
{"profile":{"id":$id,"petName":"$name","isDemo":false},"pet":{"profileId":$id},
"ledger":[{"id":1,"profileId":$id,"seed":9223372036854775807}],"attempts":[],"uses":[],"periods":[]}
""").jsonObject

private class FakeApi : CloudApi {
    val rows = linkedMapOf<Long, RemoteProfile>()
    val replies = mutableMapOf<String, Pair<String, JsonObject>>()
    val sent = mutableListOf<JsonObject>()
    var offline = false
    var loseReply = false
    var onWrite: (() -> Unit)? = null
    var listingOwner = 5L
    private var next = 1L
    override suspend fun request(method: String, path: String, body: String?, key: String?): JsonObject {
        if (offline) error("offline")
        if (method == "GET") {
            if (path == "/api/profiles") return buildJsonObject {
                put("ownerId", listingOwner)
                put("profiles", cloudJson.encodeToJsonElement(rows.values.toList()))
            }
            return cloudJson.encodeToJsonElement(rows.getValue(path.substringAfterLast('/').toLong())).jsonObject
        }
        val fingerprint = method+path+body
        replies[key]?.let { (previous, result) -> check(previous == fingerprint); return result }
        val request = cloudJson.parseToJsonElement(checkNotNull(body)).jsonObject
        sent += request
        onWrite?.also { onWrite = null; it() }
        val id = if (method == "POST") next++ else path.substringAfterLast('/').toLong()
        val old = rows[id]
        if (method != "POST" && (old == null || old.deleted || old.revision != request.getValue("expectedRevision").jsonPrimitive.long)) {
            throw CloudFailure(409, buildJsonObject {
                put("code", "revision_conflict"); put("current", cloudJson.encodeToJsonElement(checkNotNull(old)))
            })
        }
        val row = RemoteProfile(id, (old?.revision ?: 0)+1, method == "DELETE",
            if (method == "DELETE") null else request.getValue("snapshot").jsonObject.withProfileId(id))
        rows[id] = row
        val result = buildJsonObject { put("id", id); put("revision", row.revision); put("deleted", row.deleted) }
        replies[checkNotNull(key)] = fingerprint to result
        if (loseReply) { loseReply = false; error("reply lost") }
        return result
    }
}

private class Device(initial: CloudState = CloudState()) {
    var disk = cloudJson.encodeToString(initial)
    var failDisk = false
    var sequence = 0
    var navigations = 0
    var engine = make()
    private fun make() = CloudSync(5, cloudJson.decodeFromString(disk), {
        if (failDisk) error("quota")
        disk = cloudJson.encodeToString(it)
    }, { "mutation_${System.identityHashCode(this)}_${sequence++}" }, {}, { navigations++ })
    fun reload() { engine = make() }
    fun save(name: String, id: Long = 1, replace: Boolean = false) {
        val current = engine.state.profiles.mapNotNull { it.snapshot?.let { s -> it.localId to s } }.toMap()
        engine.writeGames(current + (id to pet(name, id)), maxOf(engine.state.nextId, id+1), if (replace) setOf(id) else emptySet())
    }
}

class CloudSyncTest {
    @Test fun createsServerIdWithoutChangingActiveLocalId() = runBlocking {
        val device = Device(); val api = FakeApi()
        device.save("Финни", 7)
        device.engine.synchronize(api)
        val local = device.engine.state.profiles.single()
        assertEquals(7, local.localId)
        assertEquals(1, local.serverId)
        assertFalse(local.dirty)
        assertEquals(7, local.snapshot!!["profile"]!!.jsonObject["id"]!!.jsonPrimitive.int)
        assertEquals(1, api.rows.getValue(1).snapshot!!["pet"]!!.jsonObject["profileId"]!!.jsonPrimitive.int)
    }

    @Test fun offlineQueueSurvivesReload() = runBlocking {
        val device = Device(); val api = FakeApi()
        device.save("Офлайн")
        api.offline = true
        assertFails { device.engine.synchronize(api) }
        device.reload()
        assertEquals("Офлайн", device.engine.state.profiles.single().snapshot!!.petName())
        api.offline = false
        device.engine.synchronize(api)
        assertEquals(1, api.rows.size)
        assertEquals(0, device.engine.pendingCount)
    }

    @Test fun lostCreateReplyReplaysSameMutationAfterReload() = runBlocking {
        val device = Device(); val api = FakeApi()
        device.save("Финни"); api.loseReply = true
        assertFails { device.engine.synchronize(api) }
        val key = device.engine.state.profiles.single().pending!!.key
        device.reload()
        assertEquals(key, device.engine.state.profiles.single().pending!!.key)
        device.engine.synchronize(api)
        assertEquals(1, api.rows.size)
        assertEquals(0, device.engine.pendingCount)
    }

    @Test fun lostSaveReplyDoesNotProduceFalseConflict() = runBlocking {
        val device = Device(); val api = FakeApi()
        device.save("Первый"); device.engine.synchronize(api)
        device.save("Второй"); api.loseReply = true
        assertFails { device.engine.synchronize(api) }
        device.reload(); device.engine.synchronize(api)
        assertTrue(device.engine.conflicts.isEmpty())
        assertEquals(2, api.rows.getValue(1).revision)
        assertEquals(0, device.engine.pendingCount)
    }

    @Test fun localEditWhileRequestInFlightStaysDirty() = runBlocking {
        val device = Device(); val api = FakeApi()
        device.save("До запроса")
        api.onWrite = { device.save("Во время запроса") }
        device.engine.synchronize(api)
        assertTrue(device.engine.state.profiles.single().dirty)
        assertEquals("Во время запроса", device.engine.state.profiles.single().snapshot!!.petName())
        device.engine.synchronize(api)
        assertEquals("Во время запроса", api.rows.getValue(1).snapshot!!.petName())
        assertEquals(0, device.engine.pendingCount)
    }

    @Test fun twoDevicesConflictAndKeepBothWithoutOverwriting() = runBlocking {
        val a = Device(); val b = Device(); val api = FakeApi()
        a.save("Общий"); a.engine.synchronize(api); b.engine.synchronize(api)
        a.save("Устройство А"); a.engine.synchronize(api)
        b.save("Устройство Б"); b.engine.synchronize(api)
        assertEquals(1, b.engine.conflicts.size)
        b.reload()
        assertEquals("Устройство Б", b.engine.conflicts.single().snapshot!!.petName())
        b.engine.resolve(1, keepBoth = true)
        assertEquals(2, b.engine.state.backups.size)
        b.engine.synchronize(api)
        assertEquals(setOf("Устройство А", "Устройство Б"), api.rows.values.map { it.snapshot!!.petName() }.toSet())
    }

    @Test fun chooseServerPreservesLocalBackup() = runBlocking {
        val a = Device(); val b = Device(); val api = FakeApi()
        a.save("Первый"); a.engine.synchronize(api); b.engine.synchronize(api)
        a.save("Серверный"); a.engine.synchronize(api)
        b.save("Местный"); b.engine.synchronize(api)
        b.engine.resolve(1, keepBoth = false)
        assertEquals("Серверный", b.engine.state.profiles.single().snapshot!!.petName())
        assertTrue(b.engine.state.backups.any { it.petName() == "Местный" })
    }

    @Test fun deletedServerProfileCanOnlyBeSavedAsNewCopy() = runBlocking {
        val a = Device(); val b = Device(); val api = FakeApi()
        a.save("Общий"); a.engine.synchronize(api); b.engine.synchronize(api)
        a.engine.writeGames(emptyMap(), 2); a.engine.synchronize(api)
        b.save("Офлайн версия"); b.engine.synchronize(api)
        assertTrue(b.engine.conflicts.single().conflict!!.deleted)
        b.engine.resolve(1, keepBoth = true); b.engine.synchronize(api)
        assertTrue(api.rows.getValue(1).deleted)
        assertEquals("Офлайн версия", api.rows.getValue(2).snapshot!!.petName())
    }

    @Test fun deleteWhileCreateReplyLostDoesNotLeaveOrphanOnServer() = runBlocking {
        val device = Device(); val api = FakeApi()
        device.save("Временный"); api.loseReply = true
        assertFails { device.engine.synchronize(api) }
        device.engine.writeGames(emptyMap(), 2)
        device.reload(); device.engine.synchronize(api)
        assertTrue(api.rows.getValue(1).deleted)
        assertTrue(device.engine.state.profiles.isEmpty())
    }

    @Test fun demoStaysLocalAndGuestImportIsExplicitAndOnce() = runBlocking {
        val device = Device(); val api = FakeApi()
        val demo = pet("Демо").let { JsonObject(it + ("profile" to JsonObject(it["profile"]!!.jsonObject + ("isDemo" to JsonPrimitive(true))))) }
        device.engine.writeGames(mapOf(1L to demo), 2)
        device.engine.synchronize(api)
        assertTrue(api.rows.isEmpty())
        device.engine.importGuest(listOf(pet("Гостевой"), demo))
        assertFails { device.engine.importGuest(listOf(pet("Повтор"))) }
        device.engine.synchronize(api)
        assertEquals("Гостевой", api.rows.values.single().snapshot!!.petName())
    }

    @Test fun togglingSyncedProfileToDemoDeletesServerCopy() = runBlocking {
        val device = Device(); val api = FakeApi()
        device.save("Питомец"); device.engine.synchronize(api)
        val original = device.engine.state.profiles.single().snapshot!!
        val demo = JsonObject(original + ("profile" to JsonObject(original["profile"]!!.jsonObject + ("isDemo" to JsonPrimitive(true)))))
        device.engine.writeGames(mapOf(1L to demo), 2); device.engine.synchronize(api)
        assertTrue(api.rows.getValue(1).deleted)
        assertTrue(device.engine.state.profiles.single().snapshot!!.isDemo())
        assertNull(device.engine.state.profiles.single().serverId)
    }

    @Test fun resetKeepsExplicitReplaceUntilAcknowledged() = runBlocking {
        val device = Device(); val api = FakeApi()
        device.save("Обычный"); device.engine.synchronize(api)
        device.save("Сброс", replace = true); device.save("После сброса")
        device.engine.synchronize(api)
        assertEquals("replace", api.sent.last()["mode"]!!.jsonPrimitive.content)
    }

    @Test fun diskFailureNeverPublishesUnsavedState() {
        val device = Device(); val original = device.engine.state
        device.failDisk = true
        assertFails { device.save("Не записалось") }
        assertEquals(original, device.engine.state)
    }

    @Test fun fullWidthSeedSurvivesSerializationAndIdRemapping() = runBlocking {
        val device = Device(); val api = FakeApi()
        device.save("Числа"); device.reload(); device.engine.synchronize(api)
        assertEquals("9223372036854775807", api.rows.getValue(1).snapshot!!["ledger"]!!.jsonArray[0].jsonObject["seed"]!!.jsonPrimitive.content)
    }

    @Test fun mismatchedAccountCannotEnterLocalCache() = runBlocking {
        val device = Device(); val api = FakeApi()
        api.rows[8] = RemoteProfile(8, 1, false, pet("Чужой", 8)); api.listingOwner = 6
        val failure = assertFailsWith<CloudFailure> { device.engine.synchronize(api) }
        assertEquals("account_changed", failure.detail.jsonPrimitive.content)
        assertTrue(device.engine.state.profiles.isEmpty())
    }

    @Test fun remoteAdditionAndDeletionRefreshNavigation() = runBlocking {
        val device = Device(); val api = FakeApi()
        api.rows[8] = RemoteProfile(8, 1, false, pet("Удалённый", 8))
        device.engine.synchronize(api)
        assertEquals(1, device.navigations)
        api.rows[8] = RemoteProfile(8, 2, true)
        device.engine.synchronize(api)
        assertEquals(2, device.navigations)
        assertTrue(device.engine.state.profiles.isEmpty())
    }
}
