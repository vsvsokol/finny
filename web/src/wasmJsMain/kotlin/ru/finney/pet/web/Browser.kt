@file:OptIn(ExperimentalWasmJsInterop::class)

package ru.finney.pet.web

import kotlinx.browser.localStorage
import kotlinx.browser.window

// Тонкая прослойка над API браузера. Всё, что может бросить (localStorage в приватном
// окне, заблокированное хранилище), бросает здесь, а не в игре.

@JsFun("() => Date.now()")
private external fun dateNow(): Double

/** Миллисекунды с 1970 года, как System.currentTimeMillis на Android. */
fun nowMillis(): Long = dateNow().toLong()

object BrowserStorage {
    /** Префикс ключей: на одном домене GitHub Pages могут жить и другие сайты команды. */
    private const val PREFIX = "finny."

    fun get(key: String): String? = runCatching { localStorage.getItem(PREFIX + key) }.getOrNull()

    fun set(key: String, value: String) {
        runCatching { localStorage.setItem(PREFIX + key, value) }
            .onFailure { println("finny: не удалось сохранить $key: ${it.message}") }
    }

    fun remove(key: String) {
        runCatching { localStorage.removeItem(PREFIX + key) }
    }

    /** Стереть всё сохранённое Финни (флаг ?reset=1). */
    fun clearAll() {
        runCatching {
            val keys = (0 until localStorage.length).mapNotNull { localStorage.key(it) }.filter { it.startsWith(PREFIX) }
            keys.forEach { localStorage.removeItem(it) }
        }
    }
}

/** Параметры адреса: ?demo=1&layout=expanded&debug=1&reset=1 — для отладки командой. */
object UrlFlags {
    private val params: Map<String, String> by lazy {
        window.location.search.removePrefix("?").split('&').filter { it.isNotBlank() }.associate {
            val (k, v) = (it.split('=', limit = 2) + "").take(2)
            k to v
        }
    }

    fun has(name: String): Boolean = params[name].let { it != null && it != "0" && it != "false" }

    fun value(name: String): String? = params[name]
}
