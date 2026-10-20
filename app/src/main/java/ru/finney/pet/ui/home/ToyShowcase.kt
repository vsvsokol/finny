package ru.finney.pet.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import ru.finney.pet.ui.components.drawPointingHand
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.sound.Sounds
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk

// Игрушка сама показывает, что с ней делать (плейтест 28.09: «предметы просто валяются»).
// Пока с игрушками ни разу не играли — ядро помнит это само, `playSince` пуст, — рука
// из подсказки шага берёт игрушку, ведёт к питомцу и трясёт рядом, с подписью. Потом,
// если питомцу не радостно и ребёнок долго ничего не делает, игрушка сама подпрыгивает.
// Двигается только рисунок (RoomToy, nudge): место на полу и настоящая игра не меняются.

/** Тишина перед показом, мс: новую игрушку ребёнок сначала видит сам. */
const val ToyShowDelayMillis = 1_500L

/** Сколько раз рука показывает игру; потом показ кончается до следующего запуска. */
const val ToyShowRounds = 3

/** Без анимаций рука просто стоит у игрушки — столько, если показ попросили кнопкой «Игра». */
const val ToyPointMillis = 3_000L

/** Простой, после которого скучающему питомцу подпрыгивает игрушка, мс. */
const val ToyIdleMillis = 20_000L

/** Пауза между подпрыгиваниями, мс. */
const val ToyHopGapMillis = 4_000L

/** Подпрыгиваний за один простой — дальше тихо до следующего касания. */
const val ToyHopsPerIdle = 3

/** Встряска в показе — доля ширины игрушки в каждую сторону. */
private const val ShakeReach = 0.18f

/** Подскок — доля высоты игрушки. */
private const val HopHeight = 0.45f

/**
 * Что сейчас делает показ: какую игрушку двигает ([toy]), на сколько ([nudge], пиксели)
 * и видна ли рука ([hand]). Анимация читается при рисовании — комнату кадры не пересобирают.
 */
@Stable
class ToyShowcase {
    var toy by mutableStateOf<String?>(null)
        private set
    var hand by mutableStateOf(false)
        private set
    var press by mutableFloatStateOf(0f)
        private set
    val nudge = Animatable(Offset.Zero, Offset.VectorConverter)

    /** Сдвиг рисунка игрушки [id] — для RoomScene(toyNudge). */
    fun offsetFor(id: String): Offset = if (id == toy) nudge.value else Offset.Zero

    /** Показ прервали: игрушка на месте, руки нет. Без приостановки — годится для finally. */
    fun reset() {
        toy = null
        hand = false
        press = 0f
    }

    /** Без анимаций: рука стоит у игрушки неподвижно, игрушка на месте. */
    fun point(id: String) {
        toy = id
        hand = true
    }

    /**
     * Один круг показа: рука берёт игрушку [id] (она лежит в [from]), ведёт к [to] —
     * к боку питомца — и трясёт там; [onShake] на каждую встряску (звук, сердечки).
     */
    suspend fun show(id: String, from: Rect, to: Offset, onShake: () -> Unit) {
        nudge.snapTo(Offset.Zero)
        toy = id
        hand = true
        press = 1f
        val there = to - from.center
        nudge.animateTo(there, tween(900, easing = FastOutSlowInEasing))
        val reach = from.width * ShakeReach
        repeat(4) { i ->
            nudge.animateTo(there + Offset(if (i % 2 == 0) reach else -reach, 0f), tween(120))
            onShake()
        }
        nudge.animateTo(there, tween(120))
        nudge.animateTo(Offset.Zero, tween(700, easing = FastOutSlowInEasing))
        press = 0f
    }

    /** Игрушка [id] подпрыгивает на месте; [height] — её высота в пикселях. */
    suspend fun hop(id: String, height: Float, sounds: Sounds, sound: Sfx) {
        nudge.snapTo(Offset.Zero)
        toy = id
        nudge.animateTo(Offset(0f, -height * HopHeight), tween(170, easing = FastOutSlowInEasing))
        sounds.play(sound, 1.1f)
        nudge.animateTo(Offset.Zero, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        toy = null
    }
}

/**
 * Рука показа поверх комнаты: держит игрушку сверху и ходит вместе с ней. [bounds] — где
 * игрушки лежат (RoomScene, onToyPlaced), [caption] — подпись под игрушкой. Касаний не ловит.
 */
@Composable
fun ToyShowcaseLayer(showcase: ToyShowcase, bounds: Map<String, Rect>, caption: String, modifier: Modifier = Modifier) {
    val id = showcase.toy?.takeIf { showcase.hand } ?: return
    val toy = bounds[id] ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier = modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        Canvas(Modifier.fillMaxSize()) {
            val c = toy.center - origin + showcase.nudge.value
            // Палец вниз, на верх игрушки: рука её держит.
            drawPointingHand(Offset(c.x, c.y - toy.height * 0.15f), up = false, press = showcase.press)
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyLarge,
            color = FinneyInk,
            modifier = Modifier
                // Под игрушкой, на полу: выше — лицо питомца, подпись бы его закрыла.
                .layout { measurable, constraints ->
                    val margin = 16.dp.roundToPx()
                    val placeable = measurable.measure(
                        Constraints(maxWidth = (constraints.maxWidth - 2 * margin).coerceAtLeast(0)),
                    )
                    val at = toy.translate(-origin)
                    val x = (at.center.x - placeable.width / 2f)
                        .coerceIn(margin.toFloat(), (constraints.maxWidth - margin - placeable.width).toFloat().coerceAtLeast(margin.toFloat()))
                    val y = (at.bottom + 12.dp.toPx()).coerceAtMost((constraints.maxHeight - margin - placeable.height).toFloat())
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(IntOffset(x.roundToInt(), y.roundToInt()))
                    }
                }
                .clearAndSetSemantics { contentDescription = "Подсказка: $caption" }
                .background(FinneyCream, RoundedCornerShape(16.dp))
                .border(2.dp, FinneyInk, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
