package ru.finney.pet.ui.room

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.motion.motionEnabled
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyYellow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

// Смена частей интерьера в комнате. Часть не подменяется кадром: ребёнок должен
// увидеть, что именно поменялось, и порадоваться этому. У каждой части свой ход:
// стену прокатывает валик (он поверх всей комнаты, см. RoomScene), окно схлопывается и раскрывается, торшер падает сверху
// и мигает, включаясь, старый ковёр сворачивается и раскатывается новый. Всё читается на отрисовке
// и не пересобирает комнату.

/**
 * Сколько идёт смена: стена — проход валика, окно — схлопнулось и раскрылось, торшер — до конца
 * мигания, ковёр — свернули старый и раскатали новый.
 */
internal const val WALL_SWAP_MS = 750
internal const val WINDOW_SWAP_MS = 950
internal const val LAMP_SWAP_MS = 1600
internal const val RUG_SWAP_MS = 1300

/** Доли смены торшера: когда новый стукнулся об пол и когда щёлкнул выключатель. */
internal const val LAMP_LANDS = 0.5f
internal const val LAMP_FLICKERS = 0.8f

/** Поля вокруг части, которую показывают крупно, и предел приближения: ближе — и пропадает комната вокруг. */
internal val FOCUS_PAD = 20.dp
internal const val FOCUS_MAX_ZOOM = 1.25f

/** Валик разгоняется и тормозит у пола — как рука, а не как принтер. */
private val RollerEasing = CubicBezierEasing(0.6f, 0f, 0.3f, 1f)

/** Окно: старое схлопывается за эту долю смены, новое начинает раскрываться с этой. */
private const val WINDOW_OUT_END = 0.3f
private const val WINDOW_IN_START = 0.25f

/** Торшер: старый пропадает за эту долю, новый начинает падать с этой. */
private const val LAMP_OUT_END = 0.18f
private const val LAMP_FALL_START = 0.15f

/** Ковёр: старый сворачивается до этой доли, новый раскатывается от и до. */
private const val RUG_OUT_END = 0.35f
private const val RUG_IN_START = 0.3f
private const val RUG_IN_END = 0.85f

/** Ковёр сворачивается и раскатывается рукой: быстро в начале, мягко у края. */
private val RugEasing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)

/**
 * Часть интерьера в смене: что было, что стало и сколько смены прошло (1 — закончилась).
 * Пока смена идёт, на экране обе: старая уходит, новая приходит.
 */
@Stable
internal class PartSwap<T>(initial: T) {
    var old by mutableStateOf(initial)
        private set
    var now by mutableStateOf(initial)
        private set
    val progress = Animatable(1f)

    /** Идёт ли смена — меняется дважды за смену и пересобирает только то, что от неё зависит. */
    val running by derivedStateOf { progress.value < 1f }

    fun start(next: T) {
        // Сменили посреди смены — уходит уже новая, а не та, что была до неё.
        old = now
        now = next
    }
}

/**
 * Следит за частью [value] и, когда она сменилась, проигрывает смену за [durationMs].
 * Без анимаций — сразу новая. [onStart] — звук смены, идёт рядом с ней.
 */
@Composable
internal fun <T> rememberPartSwap(value: T, durationMs: Int, onStart: suspend () -> Unit): PartSwap<T> {
    val swap = remember { PartSwap(value) }
    val animations = LocalAnimations.current
    LaunchedEffect(value) {
        if (value == swap.now) return@LaunchedEffect
        swap.start(value)
        launch { onStart() }
        if (!animations || !motionEnabled()) {
            swap.progress.snapTo(1f)
            return@LaunchedEffect
        }
        swap.progress.snapTo(0f)
        swap.progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    return swap
}

/** Доля [t] внутри отрезка [from]..[to], зажатая в 0..1. */
private fun phase(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

/**
 * Пружинка: от 0 к 1 с перелётом и затуханием. На трети пути перелёт около 13%,
 * дальше почти ровно — окно «встаёт на место», а не дрожит.
 */
private fun springy(k: Float): Float = 1f - exp(-6f * k) * cos(3f * PI.toFloat() * k)

/** Стена: новые обои ложатся сверху вниз за валиком, старые видны ниже него. */
@Composable
internal fun WallSwap(swap: PartSwap<WallSkin>, canvasW: Dp, canvasH: Dp) {
    if (swap.running) Layer(swap.old.back, canvasW, canvasH)
    Box(
        Modifier.fillMaxSize().drawWithContent {
            val t = swap.progress.value
            if (t >= 1f) {
                drawContent()
            } else {
                clipRect(bottom = size.height * RollerEasing.transform(t)) { this@drawWithContent.drawContent() }
            }
        },
    ) {
        Layer(swap.now.back, canvasW, canvasH)
    }
}

/** Окно: старое схлопывается в щёлку, новое раскрывается из неё с пружинкой. */
@Composable
internal fun WindowSwap(swap: PartSwap<WindowSkin>, canvasW: Dp, canvasH: Dp, flyer: WindowFlyer) {
    if (swap.running) {
        val old = swap.old
        key(old) {
            // У уходящего окна свой летун, который никуда не летит: пролёт — у нового.
            val still = remember { WindowFlyer(old) }
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val k = phase(swap.progress.value, 0f, WINDOW_OUT_END)
                    transformOrigin = old.frame.rect.origin()
                    scaleX = 1f + 0.15f * k
                    scaleY = 1f - 0.98f * k
                    alpha = 1f - k
                },
            ) { Window(old, canvasW, canvasH, still) }
        }
    }
    val now = swap.now
    key(now) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val t = swap.progress.value
                if (t >= 1f) return@graphicsLayer
                val k = phase(t, WINDOW_IN_START, 1f)
                val e = springy(k)
                transformOrigin = now.frame.rect.origin()
                scaleX = 1.2f - 0.2f * e
                scaleY = 0.02f + 0.98f * e
                alpha = (k * 3f).coerceAtMost(1f)
            },
        ) { Window(now, canvasW, canvasH, flyer) }
    }
}

/** Торшер: старый сплющивается и пропадает, новый падает сверху и приземляется с «плюх». */
@Composable
internal fun LampSwap(swap: PartSwap<LampSkin>, canvasW: Dp, canvasH: Dp) {
    if (swap.running) {
        val old = swap.old
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val k = phase(swap.progress.value, 0f, LAMP_OUT_END)
                transformOrigin = old.layer.rect.base()
                scaleX = 1f + 0.15f * k
                scaleY = 1f - k
                alpha = 1f - k * k
            },
        ) { Layer(old.layer, canvasW, canvasH) }
    }
    val now = swap.now
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val t = swap.progress.value
            if (t >= 1f) return@graphicsLayer
            transformOrigin = now.layer.rect.base()
            if (t < LAMP_LANDS) {
                // Падает с ускорением, как настоящий: из-за верхнего края экрана.
                val f = phase(t, LAMP_FALL_START, LAMP_LANDS)
                translationY = -(1f - f * f) * size.height * (now.layer.rect.bottom + 0.05f)
                alpha = if (t < LAMP_FALL_START) 0f else 1f
            } else {
                // Плюх: сплющился, вытянулся, встал.
                val k = phase(t, LAMP_LANDS, LAMP_FLICKERS)
                val s = exp(-4f * k) * cos(3f * PI.toFloat() * k)
                scaleX = 1f + 0.16f * s
                scaleY = 1f - 0.2f * s
            }
        },
    ) { Layer(now.layer, canvasW, canvasH) }
}

/**
 * Ковёр: старый сворачивается вправо, новый раскатывается слева направо и чуть
 * подпрыгивает, расправляясь. Место у каждого своё ([rugRect]): рисунки разной высоты.
 *
 * @param mirrored холст отражён (игровая): рисунок разворачивается обратно, чтобы
 *   ковёр был таким, как нарисован, а раскатывался по экрану слева направо.
 */
@Composable
internal fun RugSwap(swap: PartSwap<RugSkin>, floor: ToyFloor, canvasW: Dp, canvasH: Dp, mirrored: Boolean) {
    if (swap.running) {
        val old = swap.old
        Rug(old, rugRect(floor, old), canvasW, canvasH, mirrored) {
            // Сворачивается к правому краю: слева остаётся пол.
            val k = RugEasing.transform(phase(swap.progress.value, 0f, RUG_OUT_END))
            clipRect(left = size.width * k) { this@Rug.drawContent() }
        }
    }
    val now = swap.now
    val rect = rugRect(floor, now)
    Rug(now, rect, canvasW, canvasH, mirrored) {
        val t = swap.progress.value
        if (t >= 1f) return@Rug drawContent()
        val k = RugEasing.transform(phase(t, RUG_IN_START, RUG_IN_END))
        if (k <= 0f) return@Rug
        clipRect(right = size.width * k) { this@Rug.drawContent() }
    }
    // Искры над раскатанным ковром — как у окна и торшера.
    Box(
        Modifier.fillMaxSize().drawBehind {
            val t = swap.progress.value
            if (t < 1f) sparks(Offset(rect.left + rect.width / 2f, rect.top + rect.height / 2f), phase(t, RUG_IN_END - 0.1f, 1f))
        },
    )
}

@Composable
private fun Rug(
    skin: RugSkin,
    rect: RelRect,
    canvasW: Dp,
    canvasH: Dp,
    mirrored: Boolean,
    clip: ContentDrawScope.() -> Unit,
) {
    Image(
        painter = painterResource(skin.image),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .offset(canvasW * rect.left, canvasH * rect.top)
            .requiredSize(canvasW * rect.width, canvasH * rect.height)
            .graphicsLayer { if (mirrored) scaleX = -1f }
            .drawWithContent { clip() },
    )
}

/**
 * Искры от сменённой части и вспышки: белая — в окне, когда новое раскрывается,
 * тёплая — у абажура, когда торшер мигает, включаясь.
 */
@Composable
internal fun SwapEffects(walls: PartSwap<WallSkin>, windows: PartSwap<WindowSkin>?, lamps: PartSwap<LampSkin>?) {
    Box(
        Modifier.fillMaxSize().drawBehind {
            walls.progress.value.takeIf { it < 1f }?.let { t ->
                sparks(Offset(0.5f, walls.now.floorTop * 0.45f), phase(t, 0.55f, 1f))
            }
            windows?.progress?.value?.takeIf { it < 1f }?.let { t ->
                val frame = windows.now.frame.rect
                val k = phase(t, WINDOW_IN_START - 0.03f, 0.6f)
                if (k in 0f..<1f) {
                    drawCircle(
                        Color.White,
                        radius = size.width * frame.width * (0.1f + 0.5f * k),
                        center = frame.centre(size),
                        alpha = 0.9f * (1f - k),
                    )
                }
                sparks(Offset(frame.left + frame.width / 2f, frame.top + frame.height / 2f), phase(t, 0.35f, 0.9f))
            }
            lamps?.progress?.value?.takeIf { it < 1f }?.let { t ->
                val shade = lamps.now.shade
                val k = phase(t, LAMP_FLICKERS, 1f)
                // Мигание выключателя: вспыхнул, погас, вспыхнул — и растворился в свете комнаты.
                val glow = when {
                    t < LAMP_FLICKERS -> 0f
                    k < 0.2f -> 0.8f
                    k < 0.35f -> 0.1f
                    k < 0.55f -> 0.9f
                    k < 0.7f -> 0.2f
                    else -> 0.6f * (1f - phase(k, 0.7f, 1f))
                }
                if (glow > 0f) {
                    val centre = Offset(size.width * shade.x, size.height * shade.y)
                    val radius = size.width * 0.16f
                    drawCircle(
                        Brush.radialGradient(listOf(LampGlow, LampGlow.copy(alpha = 0f)), centre, radius),
                        radius = radius,
                        center = centre,
                        alpha = glow,
                    )
                }
                sparks(shade, phase(t, LAMP_LANDS, 0.95f))
            }
        },
    )
}

private val LampGlow = Color(0xFFFFE28C)

/** Центр окна — тот, вокруг которого оно схлопывается. */
private fun RelRect.origin() = TransformOrigin(left + width / 2f, top + height / 2f)

/** Низ торшера посередине: на нём он стоит, когда сплющивается. */
private fun RelRect.base() = TransformOrigin(left + width / 2f, bottom)

private fun RelRect.centre(size: Size) = Offset(size.width * (left + width / 2f), size.height * (top + height / 2f))

/** Где валик на доле смены [t]: доля высоты холста от потолка. */
internal fun rollerAt(t: Float): Float = RollerEasing.transform(t)

/**
 * Валик во всю ширину экрана: жёлтый «стадион» с персиковой полосой снизу и обводкой,
 * как кнопки интерфейса, — его ведут к низу стены, к шву с полом. Рисуется поверх
 * всей комнаты (RoomScene), а не на холсте: холст шире экрана, и на холсте концы
 * валика срезало краем, а питомец и капсула его заслоняли. [y] — середина валика
 * на экране, [h] — толщина, [t] — доля смены: у пола валик тает.
 */
internal fun DrawScope.roller(y: Float, h: Float, t: Float) {
    val alpha = 1f - phase(t, 0.85f, 1f)
    val margin = 4.dp.toPx()
    val shape = RoundRect(margin, y - h / 2f, size.width - margin, y + h / 2f, CornerRadius(h / 2f))
    val path = Path().apply { addRoundRect(shape) }
    drawPath(path, FinneyPeach, alpha = alpha)
    drawPath(
        Path().apply {
            addRoundRect(RoundRect(margin, y - h / 2f, size.width - margin, y + h * 0.2f, CornerRadius(h / 2f)))
        },
        FinneyYellow,
        alpha = alpha,
    )
    drawPath(path, FinneyInk, alpha = alpha, style = Stroke(width = h * 0.16f))
}

/**
 * Восемь звёздочек разлетаются от [from] (доли холста) по кругу и гаснут.
 * [k] — доля полёта: 0 — ещё не вылетели, 1 — погасли.
 */
private fun DrawScope.sparks(from: Offset, k: Float) {
    if (k <= 0f || k >= 1f) return
    val centre = Offset(size.width * from.x, size.height * from.y)
    val fly = 1f - (1f - k) * (1f - k)
    val star = size.width * 0.03f
    val alpha = if (k < 0.2f) k / 0.2f else 1f - phase(k, 0.6f, 1f)
    repeat(8) { i ->
        val angle = i / 8f * 2f * PI.toFloat() + 0.3f
        val reach = size.width * (0.12f + 0.045f * (i % 3)) * fly
        val at = centre + Offset(cos(angle) * reach, sin(angle) * reach)
        translate(at.x, at.y) {
            rotate(160f * fly, Offset.Zero) {
                val r = star * (0.4f + 0.7f * fly)
                val path = starPath(r)
                drawPath(path, FinneyYellow, alpha = alpha)
                drawPath(path, FinneyInk, alpha = alpha, style = Stroke(width = r * 0.18f, join = StrokeJoin.Round))
            }
        }
    }
}

/** Четырёхлучевая звёздочка с центром в нуле. */
private fun starPath(r: Float) = Path().apply {
    val inner = r * 0.38f
    for (i in 0 until 8) {
        val angle = i * PI.toFloat() / 4f - PI.toFloat() / 2f
        val radius = if (i % 2 == 0) r else inner
        val x = cos(angle) * radius
        val y = sin(angle) * radius
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
