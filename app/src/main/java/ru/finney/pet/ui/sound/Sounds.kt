package ru.finney.pet.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.finney.pet.domain.settings.SettingsStorage
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

/**
 * Эффекты — SoundPool: короткие, грузятся один раз и играют без задержки.
 * Музыка — MediaPlayer: длинный файл, потоком.
 *
 * Все вызовы — с главного потока: из Compose и из [scope] на Dispatchers.Main.
 */
class SoundPlayer(
    context: Context,
    private val storage: SettingsStorage,
    private val scope: CoroutineScope,
) : Sounds {

    private val app = context.applicationContext

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(attributes)
        .build()
        // Петля, которую попросили до конца загрузки, стартует, как только файл готов.
        .apply { setOnLoadCompleteListener { _, _, status -> if (status == 0) applyLoops() } }

    private val musicAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    // Короткие эффекты грузятся заранее, чтобы совпадать с действием на экране.
    private val ids: Map<Sfx, Int> = Sfx.entries.associateWith { pool.load(app, it.res, 1) }

    override val settings: StateFlow<SoundSettings> =
        storage.sound.stateIn(scope, SharingStarted.Eagerly, SoundSettings())

    /** Какие петли должны звучать и id потоков тех, что звучат. */
    private val wantedLoops = mutableSetOf<Sfx>()
    private val playingLoops = mutableMapOf<Sfx, Int>()

    private var track: Music? = null
    private var ducked = false
    private var player: MediaPlayer? = null
    private var playerRes = 0

    /** Приложение на экране. Свёрнутое молчит. */
    private var foreground = false

    init {
        scope.launch {
            settings.collect {
                applyLoops()
                applyMusic()
            }
        }
    }

    override fun play(sfx: Sfx) = play(sfx, 1f)

    override fun play(sfx: Sfx, rate: Float) {
        if (!foreground || !settings.value.sound) return
        val id = ids.getValue(sfx)
        // SoundPool принимает скорость только в 0.5..2, остальное молча обрезает сам,
        // но лучше обрезать явно — чтобы потолок был виден здесь.
        pool.play(id, 1f, 1f, 1, 0, rate.coerceIn(0.5f, 2f))
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
        player?.setVolume(volume(), volume())
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

    /** Activity на экране — onStart. */
    fun resume() {
        foreground = true
        applyLoops()
        applyMusic()
    }

    /** Activity ушла с экрана — onStop. */
    fun pause() {
        foreground = false
        applyLoops()
        applyMusic()
    }

    private fun applyLoops() {
        val audible = foreground && settings.value.sound
        playingLoops.keys.filter { !audible || it !in wantedLoops }.forEach { sfx ->
            playingLoops.remove(sfx)?.let(pool::stop)
        }
        if (!audible) return
        wantedLoops.filter { it !in playingLoops }.forEach { sfx ->
            val stream = pool.play(ids.getValue(sfx), LOOP_VOLUME, LOOP_VOLUME, 0, -1, 1f)
            if (stream != 0) playingLoops[sfx] = stream
        }
    }

    private fun applyMusic() {
        val want = track?.takeIf { foreground && settings.value.music }
        if (want == null) {
            player?.takeIf { it.isPlaying }?.pause()
            return
        }
        val current = player
        if (current != null && playerRes == want.res) {
            if (!current.isPlaying) current.start()
            return
        }
        current?.release()
        val session = app.getSystemService(AudioManager::class.java).generateAudioSessionId()
        player = MediaPlayer.create(app, want.res, musicAttributes, session)?.apply {
            isLooping = true
            setVolume(volume(), volume())
            start()
        }
        playerRes = want.res
    }

    private fun volume() = if (ducked) MUSIC_DUCKED else MUSIC_VOLUME

    private companion object {
        const val MAX_STREAMS = 6
        const val LOOP_VOLUME = 0.8f
        const val MUSIC_VOLUME = 0.8f
        const val MUSIC_DUCKED = 0.25f
    }
}
