package ru.finney.pet.ui.sound

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.finney.pet.domain.settings.SoundSettings

/**
 * Звуки и музыка для экранов. Экраны берут их из [LocalSounds]: `LocalSounds.current.play(Sfx.Coin)`.
 * Выключенный звук или свёрнутое приложение экран не проверяет — это решает реализация.
 */
interface Sounds {
    val settings: StateFlow<SoundSettings>

    fun play(sfx: Sfx)

    /**
     * Тот же звук, но выше или ниже: [rate] 1 — как записан, 2 — октавой выше.
     * Так тики удержания «−/+» поднимаются в тоне без отдельных файлов.
     */
    fun play(sfx: Sfx, rate: Float)

    /** Зацикленный звук, пока его не остановят: сопение во сне. */
    fun startLoop(sfx: Sfx)

    fun stopLoop(sfx: Sfx)

    /** Какая музыка должна играть сейчас; null — тишина. */
    fun music(track: Music?)

    /** Приглушить музыку, пока питомец спит. */
    fun duckMusic(ducked: Boolean)

    fun setSound(enabled: Boolean)

    fun setMusic(enabled: Boolean)

    fun setHaptics(enabled: Boolean)

    /** Анимации выключает MainActivity через [ru.finney.pet.ui.motion.AppMotion], экранам ничего делать не нужно. */
    fun setAnimations(enabled: Boolean)
}

/** Тишина: в превью и тестах без приложения. */
object NoSounds : Sounds {
    override val settings: StateFlow<SoundSettings> = MutableStateFlow(SoundSettings())
    override fun play(sfx: Sfx) = Unit
    override fun play(sfx: Sfx, rate: Float) = Unit
    override fun startLoop(sfx: Sfx) = Unit
    override fun stopLoop(sfx: Sfx) = Unit
    override fun music(track: Music?) = Unit
    override fun duckMusic(ducked: Boolean) = Unit
    override fun setSound(enabled: Boolean) = Unit
    override fun setMusic(enabled: Boolean) = Unit
    override fun setHaptics(enabled: Boolean) = Unit
    override fun setAnimations(enabled: Boolean) = Unit
}

val LocalSounds = staticCompositionLocalOf<Sounds> { NoSounds }

/** Зацикленный звук, пока [active] и экран на месте. */
@Composable
fun LoopWhile(sfx: Sfx, active: Boolean) {
    val sounds = LocalSounds.current
    DisposableEffect(sfx, active) {
        if (active) sounds.startLoop(sfx)
        onDispose { sounds.stopLoop(sfx) }
    }
}
