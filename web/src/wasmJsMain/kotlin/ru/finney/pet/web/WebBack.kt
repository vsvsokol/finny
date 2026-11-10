package ru.finney.pet.web

import kotlinx.browser.document
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/**
 * «Назад» на вебе. Compose Multiplatform понимает Esc как системную «назад»: его ловят
 * и BackHandler экранов, и NavHost. Кнопка «назад» на экране (PetSetupScreen) жмёт тот же Esc.
 */
object WebBack {
    fun dispatch() {
        val init = KeyboardEventInit(key = "Escape", code = "Escape", bubbles = true, cancelable = true)
        val target = document.querySelector("canvas") ?: document.body ?: return
        target.dispatchEvent(KeyboardEvent("keydown", init))
        target.dispatchEvent(KeyboardEvent("keyup", init))
    }
}
