package ru.finney.pet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.GameStore
import ru.finney.pet.domain.game.Session
import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.ui.sound.WebSoundPlayer
import ru.finney.pet.web.WebActiveProfileStorage
import ru.finney.pet.web.WebGameStorage
import ru.finney.pet.web.WebSettingsStorage

/**
 * Веб-версия ручного DI (Android — app/.../AppContainer.kt). Те же поля, что берут
 * ViewModel-фабрики: content, game, session. Контент уже прочитан в main.kt.
 */
class AppContainer(val content: GameContent, val storage: WebGameStorage = WebGameStorage(), activeKey: String = "activeProfile") {

    val game: Game by lazy { Game(content) }

    val gameStore: GameStore by lazy { GameStore(game, storage) }

    val session: Session by lazy { Session(gameStore, WebActiveProfileStorage(activeKey)) }

    var appVisible: Boolean = true

    val sounds: WebSoundPlayer by lazy {
        WebSoundPlayer(WebSettingsStorage(), CoroutineScope(SupervisorJob() + Dispatchers.Main))
    }

    companion object {
        /** Создаётся в main.kt до первого кадра. */
        lateinit var instance: AppContainer
    }
}
