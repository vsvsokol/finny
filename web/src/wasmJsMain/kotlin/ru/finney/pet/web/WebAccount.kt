@file:OptIn(ExperimentalWasmJsInterop::class)

package ru.finney.pet.web

import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
internal data class CachedAccount(val id: Long, val login: String)

@JsFun("(callback) => globalThis.finnyAccount.start(callback)")
private external fun startAccountUi(callback: (String, String) -> Unit)

@JsFun("(state) => globalThis.finnyAccount.render(JSON.parse(state))")
private external fun renderAccountUi(state: String)

/** Один экземпляр на вкладку; переключение учётки всегда создаёт новую игровую сессию. */
internal class WebAccount {
    private val scope = MainScope()
    private val gate = Mutex()
    val account = BrowserStorage.get("account.selected")?.let { cloudJson.decodeFromString<CachedAccount>(it) }
    val activeKey = account?.let { "account.${it.id}.activeProfile" } ?: "activeProfile"
    val navigationEpoch = MutableStateFlow(0)
    val localProblem = MutableStateFlow<String?>(null)
    private val api = BrowserCloudApi(account?.id)
    private val cacheKey = account?.let { "account.${it.id}.state" }
    private var busy = false
    private var switching = false
    private var status = if (account == null) "Сохранения в этом браузере" else "Проверяем сохранения…"
    private var error: String? = null
    private var needsLogin = false
    private var recovery: String? = null
    private var nextAccount: CachedAccount? = null
    private val initial = cacheKey?.let { key -> BrowserStorage.get(key)?.let { cloudJson.decodeFromString<CloudState>(it) } } ?: CloudState()
    private val sync: CloudSync? = account?.let { owner ->
        CloudSync(owner.id, initial, { next ->
            try { BrowserStorage.requireSet(checkNotNull(cacheKey), cloudJson.encodeToString(next)) }
            catch (failure: Throwable) {
                localProblem.value = "Не удалось записать сохранение в браузере. Освободите место и перезагрузите страницу."
                throw failure
            }
        }, ::mutationKey, { publishData() }, { navigationEpoch.value++ })
    }
    val storage = WebGameStorage(object : GamePersistence {
        private val guest = GuestPersistence()
        override fun read(): StoredGames = sync?.state?.games() ?: guest.read()
        override fun write(games: StoredGames, replace: Set<Long>) {
            try {
                if (sync == null) guest.write(games, replace)
                else sync.writeGames(games.profiles.associate { it.profile.id to cloudJson.encodeToJsonElement(it).jsonObject }, games.nextId, replace)
            } catch (failure: Throwable) {
                localProblem.value = "Сохранение не записалось. Не закрывайте игру: проверьте свободное место в браузере."
                throw failure
            }
        }
    })

    private fun CloudState.games() = StoredGames(nextId = nextId,
        profiles = profiles.mapNotNull { it.snapshot?.let { snapshot -> cloudJson.decodeFromJsonElement<StoredProfile>(snapshot) } })

    private fun publishData() {
        storage.refresh(checkNotNull(sync).state.games())
        render()
    }

    fun start() {
        startAccountUi { action, value -> scope.launch { act(action, value) } }
        render()
        scope.launch {
            while (isActive) {
                if (!switching && localProblem.value == null) {
                    gate.withLock {
                        try {
                            val me = api.request("GET", "/api/auth/me")
                            val user = cloudJson.decodeFromJsonElement<CachedAccount>(me.getValue("user"))
                            if (account == null) {
                                BrowserStorage.requireSet("account.selected", cloudJson.encodeToString(user))
                                window.location.reload()
                                return@withLock
                            }
                            check(user.id == account.id)
                            api.csrf = me.getValue("csrfToken").jsonPrimitive.content
                            needsLogin = false
                            sync?.synchronize(api)
                            status = when {
                                checkNotNull(sync).conflicts.isNotEmpty() -> "Есть разные версии сохранения"
                                sync.pendingCount > 0 -> "Есть изменения для отправки"
                                else -> "✓ Сохранено на сервере"
                            }
                        } catch (failure: CancellationException) { throw failure }
                        catch (failure: Throwable) {
                            if (failure is CloudFailure && (failure.status == 401 || failure.status == 403 || failure.detail == JsonPrimitive("account_changed"))) {
                                needsLogin = account != null
                                status = if (account == null) "Сохранения в этом браузере" else "Нужен повторный вход · прогресс сохранён здесь"
                            } else status = if (account == null) "Сохранения в этом браузере" else "Нет связи · прогресс остаётся здесь"
                            if (failure is CloudFailure && failure.status !in listOf(401, 403, 429)) error = friendly(failure)
                        }
                        render()
                    }
                }
                delay(if (account == null) 15_000 else 5_000)
            }
        }
    }

    private fun render() {
        renderAccountUi(buildJsonObject {
            put("login", account?.login ?: "")
            put("status", status)
            put("error", error ?: "")
            put("busy", busy)
            put("needsLogin", needsLogin)
            put("fatal", localProblem.value ?: "")
            put("recoveryCode", recovery ?: "")
            put("firstVisit", account == null && BrowserStorage.get("guest.chosen") != "true")
            put("guestImport", sync != null && !sync.state.guestImported && runCatching { GuestPersistence().read().profiles.any { !it.profile.isDemo } }.getOrDefault(false))
            put("conflicts", JsonArray(sync?.conflicts?.map {
                buildJsonObject { put("id", it.localId); put("name", it.snapshot?.petName() ?: "Удалённый профиль") }
            } ?: emptyList()))
        }.toString())
    }

    private suspend fun act(action: String, raw: String) {
        if (busy) return
        error = null
        if (action == "guest") { BrowserStorage.requireSet("guest.chosen", "true"); render(); return }
        if (action == "recovery-saved") {
            BrowserStorage.requireSet("account.selected", cloudJson.encodeToString(checkNotNull(nextAccount)))
            window.location.reload()
            return
        }
        if (action == "export") {
            val document = sync?.state?.let { cloudJson.encodeToString(it) } ?: storage.exportJson()
            downloadCloudCopy("finny-local-backup.json", document)
            return
        }
        busy = true
        switching = true
        render()
        try {
            gate.withLock {
                when (action) {
                    "import" -> {
                        val snapshots = GuestPersistence().read().profiles.map { cloudJson.encodeToJsonElement(it).jsonObject }
                        checkNotNull(sync).importGuest(snapshots)
                        navigationEpoch.value++
                    }
                    "server", "both" -> {
                        checkNotNull(sync).resolve(raw.toLong(), action == "both")
                        navigationEpoch.value++
                    }
                    "logout" -> {
                        try { api.request("POST", "/api/auth/logout", "{}") }
                        catch (failure: CloudFailure) { if (failure.status != 401) throw failure }
                        BrowserStorage.remove("account.selected")
                        BrowserStorage.requireSet("guest.chosen", "true")
                        window.location.reload()
                        return@withLock
                    }
                    "login", "register", "recover", "password", "recovery-code" -> {
                        val authApi = if (action in listOf("password", "recovery-code")) api else BrowserCloudApi()
                        val result = authApi.request("POST", "/api/auth/$action", raw)
                        val user = result["user"]?.let { cloudJson.decodeFromJsonElement<CachedAccount>(it) } ?: checkNotNull(account)
                        val code = result["recoveryCode"]?.jsonPrimitive?.content
                        if (code == null) {
                            BrowserStorage.requireSet("account.selected", cloudJson.encodeToString(user))
                            window.location.reload()
                        } else {
                            nextAccount = user
                            recovery = code
                        }
                    }
                }
            }
        } catch (failure: CancellationException) { throw failure }
        catch (failure: Throwable) { error = friendly(failure) }
        finally {
            busy = false
            switching = recovery != null
            render()
        }
    }

    private fun friendly(failure: Throwable): String {
        if (failure !is CloudFailure) return "Не удалось связаться с сервером. Локальные сохранения остаются в браузере."
        val code = (failure.detail as? JsonPrimitive)?.content
        return when (code) {
            "invalid_credentials" -> "Проверьте логин, пароль или код восстановления."
            "login_unavailable" -> "Этот логин занят. Выберите другой."
            "invalid_request" -> "Проверьте поля: логин 3–32 латинских символа, пароль 10–128 символов."
            "profile_limit", "profile_history_limit" -> "Достигнут лимит профилей. Локальная версия сохранена."
            "account_changed" -> "В браузере изменилась учётная запись. Войдите снова."
            else -> when (failure.status) {
                401, 403 -> "Войдите в учётную запись ещё раз."
                429 -> "Слишком много запросов. Подождите немного."
                413 -> "Сохранение слишком большое. Скачайте резервную копию."
                else -> "Сервер не принял запрос. Локальная версия сохранена."
            }
        }
    }
}
