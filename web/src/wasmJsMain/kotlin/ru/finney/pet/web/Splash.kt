package ru.finney.pet.web

import kotlinx.browser.document

/** Заставка из index.html — убрать, когда игра готова рисовать. */
fun hideSplash() {
    document.getElementById("splash")?.remove()
}

/** Ошибка загрузки — текстом на заставке, чтобы её было видно без консоли. */
fun showFatal(message: String) {
    document.getElementById("splash-text")?.textContent = message
}
