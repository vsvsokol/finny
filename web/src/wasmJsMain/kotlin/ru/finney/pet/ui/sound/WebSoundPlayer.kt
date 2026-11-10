@file:OptIn(ExperimentalWasmJsInterop::class)

package ru.finney.pet.ui.sound

import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.w3c.dom.Audio
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.events.Event
import ru.finney.pet.domain.settings.SettingsStorage
import ru.finney.pet.domain.settings.SoundSettings
import ru.finney.pet.web.res.Res
import ru.finney.pet.web.res.rawPaths

/**
 * Звуки веб-версии: те же правила, что у Android-SoundPlayer (выключатели, петли,
 * приглушение музыки во сне), на HTMLAudioElement.
 *
 * Браузер не даёт играть звук до первого касания или клика. До него эффекты молча
 * пропускаются, а музыка заводится сама на первом же жесте ([unlock]).
 */
class WebSoundPlayer(
    private val storage: SettingsStorage,
    private val scope: CoroutineScope,
) : Sounds {

    override val settings: StateFlow<SoundSettings> =
        storage.sound.stateIn(scope, SharingStarted.Eagerly, SoundSettings())

    private fun url(res: Int): String = Res.getUri(rawPaths[res])

    /** Готовые элементы на каждый эффект: первый звук не ждёт загрузки. */
    private val sources: Map<Sfx, HTMLAudioElement> by lazy {
        Sfx.entries.associateWith { sfx -> Audio(url(sfx.res)).apply { preload = "auto" } }
    }

    private val wantedLoops = mutableSetOf<Sfx>()
    private val playingLoops = mutableMapOf<Sfx, HTMLAudioElement>()

    private var track: Music? = null
    private var ducked = false
    private var player: HTMLAudioElement? = null
    private var playerRes = -1

    /** Вкладка видна. Скрытая молчит, как свёрнутое приложение. */
    private var foreground = !pageHidden()

    /** Был жест пользователя — браузер разрешил звук. */
    private var unlocked = false

    init {
        scope.launch {
            settings.collect {
                applyLoops()
                applyMusic()
            }
        }
        document.addEventListener("visibilitychange", { _: Event ->
            foreground = !pageHidden()
            applyLoops()
            applyMusic()
        })
        val unlock: (Event) -> Unit = { unlock() }
        document.addEventListener("pointerdown", unlock)
        document.addEventListener("keydown", unlock)
    }

    private fun unlock() {
        if (unlocked) return
        unlocked = true
        sources // начать загрузку эффектов
        applyLoops()
        applyMusic()
    }

    private val audible get() = foreground && unlocked

    override fun play(sfx: Sfx) = play(sfx, 1f)

    override fun play(sfx: Sfx, rate: Float) {
        if (!audible || !settings.value.sound) return
        val shot = sources.getValue(sfx).cloneNode(true) as HTMLAudioElement
        shot.playbackRate = rate.coerceIn(0.5f, 2f).toDouble()
        shot.keepPitch(false)
        shot.safePlay()
    }

    override fun startLoop(sfx: Sfx) {
        wantedLoops += sfx
        applyLoops()
    }

    override fun stopLoop(sfx: Sfx) {
        wantedLoops -= sfx
        applyLoops()
    }

    override fun music(track: Music?) {
        this.track = track
        applyMusic()
    }

    override fun duckMusic(ducked: Boolean) {
        this.ducked = ducked
        player?.volume = volume()
    }

    override fun setSound(enabled: Boolean) {
        scope.launch { storage.setSound(enabled) }
    }

    override fun setMusic(enabled: Boolean) {
        scope.launch { storage.setMusic(enabled) }
    }

    override fun setHaptics(enabled: Boolean) {
        scope.launch { storage.setHaptics(enabled) }
    }

    override fun setAnimations(enabled: Boolean) {
        scope.launch { storage.setAnimations(enabled) }
    }

    private fun applyLoops() {
        val on = audible && settings.value.sound
        playingLoops.keys.filter { !on || it !in wantedLoops }.forEach { sfx -> playingLoops.remove(sfx)?.pause() }
        if (!on) return
        wantedLoops.filter { it !in playingLoops }.forEach { sfx ->
            val loop = Audio(url(sfx.res)).apply {
                loop = true
                volume = LOOP_VOLUME
            }
            loop.safePlay()
            playingLoops[sfx] = loop
        }
    }

    private fun applyMusic() {
        val want = track?.takeIf { audible && settings.value.music }
        if (want == null) {
            player?.pause()
            return
        }
        val current = player
        if (current != null && playerRes == want.res) {
            if (current.paused) current.safePlay()
            return
        }
        current?.pause()
        player = Audio(url(want.res)).apply {
            loop = true
            volume = volume()
            safePlay()
        }
        playerRes = want.res
    }

    private fun volume() = if (ducked) MUSIC_DUCKED else MUSIC_VOLUME

    private companion object {
        const val LOOP_VOLUME = 0.8
        const val MUSIC_VOLUME = 0.8
        const val MUSIC_DUCKED = 0.25
    }
}

/** play() возвращает Promise, который отклоняется без жеста или без файла. Звук — не повод для ошибки. */
private fun HTMLAudioElement.safePlay() {
    runCatching { play().catch { null } }
}

@JsFun("() => document.visibilityState === 'hidden'")
private external fun pageHidden(): Boolean

/** preservesPitch=false — тон меняется вместе со скоростью, как rate у SoundPool. */
@JsFun("(audio, keep) => { audio.preservesPitch = keep; audio.webkitPreservesPitch = keep; }")
private external fun setPreservesPitch(audio: HTMLAudioElement, keep: Boolean)

private fun HTMLAudioElement.keepPitch(keep: Boolean) = setPreservesPitch(this, keep)
