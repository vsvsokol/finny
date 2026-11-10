package ru.finney.pet.web

import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.*

private class LiveApi : CloudApi {
    private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).cookieHandler(CookieManager(null, CookiePolicy.ACCEPT_ALL)).build()
    var owner: Long? = null
    var csrf = ""
    var offline = false
    override suspend fun request(method: String, path: String, body: String?, key: String?): JsonObject {
        if (offline) error("offline")
        val builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:18086$path"))
            .header("Origin", "http://127.0.0.1:18086")
        owner?.let { builder.header("X-Account-Id", it.toString()) }
        if (csrf.isNotEmpty()) builder.header("X-CSRF-Token", csrf)
        key?.let { builder.header("Idempotency-Key", it) }
        if (body != null) builder.header("Content-Type", "application/json")
        val response = client.send(builder.method(method, if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
        val result = cloudJson.parseToJsonElement(response.body()).jsonObject
        if (response.statusCode() !in 200..299) throw CloudFailure(response.statusCode(), result.getValue("error"))
        return result
    }
    suspend fun auth(path: String, payload: JsonObject) {
        val result = request("POST", path, payload.toString())
        owner = result.getValue("user").jsonObject.getValue("id").jsonPrimitive.long
        csrf = result.getValue("csrfToken").jsonPrimitive.content
    }
}

class LiveApiTest {
    @Test fun actualPythonApiTwoDevicesOfflineConflictAndFullWidthSeed() = runBlocking {
        assumeTrue(System.getenv("FINNY_LIVE_TEST") == "1")
        val credentials = buildJsonObject {
            put("login", "sync_"+UUID.randomUUID().toString().take(8))
            put("password", UUID.randomUUID().toString())
        }
        val a = LiveApi(); val b = LiveApi()
        a.auth("/api/auth/register", credentials)
        b.auth("/api/auth/login", credentials)
        var diskA = CloudState(); var diskB = CloudState()
        fun deviceA() = CloudSync(a.owner!!, diskA, { diskA = cloudJson.decodeFromString(cloudJson.encodeToString(it)) }, { UUID.randomUUID().toString() })
        fun deviceB() = CloudSync(b.owner!!, diskB, { diskB = cloudJson.decodeFromString(cloudJson.encodeToString(it)) }, { UUID.randomUUID().toString() })
        val engineA = deviceA()
        val snapshot = cloudJson.parseToJsonElement(javaClass.getResource("/profile.json")!!.readText()).jsonObject
        engineA.writeGames(mapOf(1L to snapshot), 2)
        engineA.synchronize(a)
        var engineB = deviceB(); engineB.synchronize(b)
        assertEquals(1, engineB.state.profiles.size)
        fun rename(core: CloudSync, name: String) {
            val old = core.state.profiles.single().snapshot!!
            val updated = JsonObject(old + ("profile" to JsonObject(old.getValue("profile").jsonObject + ("petName" to JsonPrimitive(name)))))
            core.writeGames(mapOf(1L to updated), 2)
        }
        rename(engineA, "Версия А"); engineA.synchronize(a)
        rename(engineB, "Версия Б"); b.offline = true
        assertFails { engineB.synchronize(b) }
        engineB = deviceB(); b.offline = false
        engineB.synchronize(b)
        assertEquals(1, engineB.conflicts.size)
        engineB.resolve(1, keepBoth = true); engineB.synchronize(b)
        val remote = a.request("GET", "/api/profiles").getValue("profiles").jsonArray
        assertEquals(setOf("Версия А", "Версия Б"), remote.map { it.jsonObject.getValue("snapshot").jsonObject.petName() }.toSet())
        remote.forEach {
            val seed = it.jsonObject.getValue("snapshot").jsonObject.getValue("attempts").jsonArray.single().jsonObject.getValue("seed").jsonPrimitive.content
            assertEquals("9223372036854775807", seed)
        }
    }
}
