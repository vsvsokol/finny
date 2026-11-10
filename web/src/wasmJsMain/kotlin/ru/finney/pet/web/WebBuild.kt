@file:OptIn(ExperimentalWasmJsInterop::class)

package ru.finney.pet.web

// Версия и коммит сборки — в баг-репорт и в «Настройки». Подставляет index.html
// (window.FINNY_BUILD), а в CI — workflow web.yml.

@JsFun("() => (globalThis.FINNY_BUILD && globalThis.FINNY_BUILD.version) || 'dev'")
private external fun buildVersion(): String

@JsFun("() => (globalThis.FINNY_BUILD && globalThis.FINNY_BUILD.commit) || 'local'")
private external fun buildCommit(): String

object WebBuild {
    val version: String by lazy { buildVersion() }
    val commit: String by lazy { buildCommit() }
    val isDevelopment: Boolean get() = commit == "local"
}
