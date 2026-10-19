package ru.finney.pet.ui.components

import ru.finney.pet.ui.motion.LocalAnimations
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import ru.finney.pet.ui.theme.FinneyGlare
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyYellow

// Подсказка следующего шага без слов. Плейтест: ребёнок пропускает вступление и
// жмёт наугад, поэтому экран сам показывает, куда нажать, — пульсом, а не абзацем
// текста (ТЗ п. 3.6: понятно без чтения длинной инструкции).

/** Насколько элемент «вдыхает» на пульсе. Больше — дёргается, меньше — не заметно. */
private const val PulseScale = 1.07f

/** Полупериод пульса. */
private const val PulseMs = 650

/**
 * Мягкая пульсация — «нажми сюда». [active] false — элемент стоит спокойно.
 * Масштаб читается в graphicsLayer, на отрисовке: пульс не пересобирает элемент.
 */
fun Modifier.pulse(active: Boolean): Modifier = if (!active) this else composed {
    // Без анимаций пульс застыл бы на вдохе, увеличенным: элемент стоит как есть.
    if (!LocalAnimations.current) return@composed Modifier
    val scale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = PulseScale,
        animationSpec = infiniteRepeatable(tween(PulseMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Длина руки-указателя от кончика пальца до запястья. */
internal val PointingHandLength = 46.dp

/** Зазор между элементом и кольцом «нажми сюда». */
private val PointRingGap = 5.dp

/** Сколько рука проходит от отведённой до нажимающей. */
private val PointHandTravel = 16.dp

/** Один цикл «подвела — нажала — отвела», как у подсказки шага на главном. */
private const val PointCycleMillis = 1_400

/**
 * «Нажми сюда» для тех, кто не читает: жёлтое кольцо по форме элемента и рука,
 * которая нажимает на него сверху. Те же кольцо и рука, что у подсказки шага на
 * главном (NextStepHint.kt), но внутри экрана: плейтест 29.09 — на обучении в плане
 * и в копилке ребёнок, не дочитавший строку, «встревал» и не знал, что жать.
 *
 * Рука — сверху: всё, что выше в столбце, уже нарисовано, и она ложится поверх,
 * а не уходит под следующую карточку. Элемент ещё и прокручивается на виду:
 * «Подтвердить» и «Положить» бывают ниже края экрана.
 *
 * [corner] — скругление элемента, null — круглый или «стадион».
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pointHere(active: Boolean, corner: Dp? = null): Modifier = if (!active) this else composed {
    val animate = LocalAnimations.current
    val cycle = if (animate) {
        rememberInfiniteTransition(label = "point").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(PointCycleMillis, easing = LinearEasing), RepeatMode.Restart),
            label = "tap",
        )
    } else null
    drawWithContent {
        drawContent()
        // Внутренняя обводка не залезает на подписи соседей и не обрезается прокруткой.
        val fade = cycle?.value?.let { 0.7f + 0.3f * sin(it * PI).toFloat() } ?: 1f
        val inset = 5.dp.toPx()
        val rect = Rect(Offset.Zero, size).deflate(inset)
        val radius = corner?.toPx()?.minus(inset)?.coerceAtLeast(0f) ?: min(rect.width, rect.height) / 2f
        val path = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(radius))) }
        drawPath(path, FinneyYellow.copy(alpha = fade), style = Stroke(3.dp.toPx()))
    }
}

/** Кольцо вокруг [rect] на [grow] шире него: жёлтое с синей серединой, как у подсказки шага. */
private fun DrawScope.drawRing(rect: Rect, corner: Dp?, grow: Float, alpha: Float) {
    val r = rect.inflate(grow)
    val radius = corner?.let { (it.toPx() + grow).coerceAtMost(min(r.width, r.height) / 2f) }
        ?: (min(r.width, r.height) / 2f)
    val path = Path().apply { addRoundRect(RoundRect(r, CornerRadius(radius))) }
    drawPath(path, FinneyYellow.copy(alpha = alpha), style = Stroke(7.dp.toPx()))
    drawPath(path, FinneyInk.copy(alpha = alpha), style = Stroke(2.5.dp.toPx()))
}

/**
 * Рука кончиком пальца в [tip]: [up] — палец смотрит вверх, рука ниже точки; иначе наоборот.
 * [press] 0..1 — насколько рука сжата к пальцу. Её водят подсказка шага на главном,
 * показ игрушки (ToyShowcase.kt) и [pointHere].
 */
internal fun DrawScope.drawPointingHand(tip: Offset, up: Boolean, press: Float = 0f) {
    val u = PointingHandLength.toPx() / 46f

    // Кончик пальца — в начале координат, рука уходит вниз, по +y.
    fun rr(l: Float, t: Float, r: Float, b: Float, c: Float) =
        Path().apply { addRoundRect(RoundRect(l * u, t * u, r * u, b * u, CornerRadius(c * u))) }
    val hand = Path().apply {
        op(rr(-5f, 0f, 5f, 26f, 5f), rr(-7f, 18f, 17f, 46f, 9f), PathOperation.Union)
    }.let { Path().apply { op(it, rr(-14f, 22f, -2f, 32f, 5f), PathOperation.Union) } }

    val squeeze = 1f - 0.1f * press
    translate(tip.x, tip.y) {
        rotate(if (up) 0f else 180f, pivot = Offset.Zero) {
            scale(squeeze, squeeze, pivot = Offset.Zero) {
                drawPath(hand, FinneyGlare)
                drawPath(hand, FinneyInk, style = Stroke(2.5.dp.toPx()))
                // Складки пальцев на ладони — чтобы читалась рука, а не варежка.
                for (x in listOf(8f, 13f)) {
                    drawLine(FinneyInk, Offset(x * u, 19f * u), Offset(x * u, 26f * u), strokeWidth = 2.dp.toPx())
                }
            }
        }
    }
}
