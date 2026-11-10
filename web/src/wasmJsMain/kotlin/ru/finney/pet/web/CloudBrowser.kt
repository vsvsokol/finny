@file:OptIn(ExperimentalWasmJsInterop::class)

package ru.finney.pet.web

import kotlin.js.Promise
import kotlin.js.JsString
import kotlinx.coroutines.await
import kotlinx.serialization.json.*

@JsFun("""(method, path, body, csrf, owner, key) => {
    const abort = new AbortController();
    const timer = setTimeout(() => abort.abort(), 15000);
    const headers = {};
    if (body !== null) headers['Content-Type'] = 'application/json';
    if (csrf) headers['X-CSRF-Token'] = csrf;
    if (owner) headers['X-Account-Id'] = owner;
    if (key) headers['Idempotency-Key'] = key;
    return fetch(path, {method, headers, body, credentials:'same-origin', cache:'no-store', signal:abort.signal})
      .then(async response => String(response.status) + '\n' + await response.text())
      .finally(() => clearTimeout(timer));
}""")
private external fun cloudFetch(method: String, path: String, body: String?, csrf: String, owner: String, key: String): Promise<JsString>

@JsFun("() => crypto.randomUUID()")
internal external fun mutationKey(): String

@JsFun("""() => new Promise(resolve => {
    if (!navigator.locks) { resolve(false); return; }
    navigator.locks.request('finny-game-writer', {ifAvailable:true}, lock => {
        resolve(!!lock);
        return lock ? new Promise(() => {}) : undefined;
    });
})""")
private external fun lockGame(): Promise<kotlin.js.JsBoolean>

internal suspend fun acquireGameLock(): Boolean = lockGame().await().toBoolean()

@JsFun("""(name, text) => {
    const url = URL.createObjectURL(new Blob([text], {type:'application/json'}));
    const a = document.createElement('a'); a.href = url; a.download = name;
    a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
}""")
internal external fun downloadCloudCopy(name: String, text: String)

internal class BrowserCloudApi(var owner: Long? = null) : CloudApi {
    var csrf: String = ""
    override suspend fun request(method: String, path: String, body: String?, key: String?): JsonObject {
        val raw = cloudFetch(method, path, body, csrf, owner?.toString() ?: "", key ?: "").await().toString()
        val code = raw.substringBefore('\n').toInt()
        val response = cloudJson.parseToJsonElement(raw.substringAfter('\n')).jsonObject
        if (code !in 200..299) throw CloudFailure(code, response["error"] ?: JsonPrimitive("request_failed"))
        return response
    }
}
