package ru.finney.pet.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.testTag
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import ru.finney.pet.ui.components.ActionFeedback
import ru.finney.pet.ui.components.ActionFeedbackSummary
import ru.finney.pet.ui.components.PointingHandLength
import ru.finney.pet.ui.components.FinneyQuietButton
import ru.finney.pet.ui.components.drawPointingHand
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyGlare
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyYellow

// Подсказка следующего шага: кольцо вокруг того, что нажать, и рука, которая
// показывает нажатие. Рисуется одним слоем поверх всего экрана, а не у каждой
// кнопки: подсказка одна за раз, и ей нужно уметь выйти за края ряда с целью.
// Слой нажатия не ловит — палец попадает в саму кнопку под кольцом.
//
// На первом уровне это обучение: экран темнеет, светлой остаётся только цель,
// и рядом пузырь с фразой. Затемнение нажатий тоже не ловит — ребёнок не заперт
// в подсказке и может погладить питомца или открыть меню.
//
// Дальше подсказка растворяется (плейтест 28.09: «постоянно показывают, что делать»):
// со 2-го уровня она ждёт, пока ребёнок замешкается, и гаснет от любого касания,
// с 4-го — ждёт дольше и приходит без руки, одним кольцом. Уровень и есть счётчик
// минут: первый ~4 минуты, дальше по ~3, так что за первые 3–10 минут подсказка
// уходит из виду у того, кто уже понял игру, и остаётся у того, кто застрял.
// Сигналы «уровень можно завершить» и «выспался» — не обучение: они не ждут.
//
// Подсветка не только цветом (ТЗ п. 3.6): кольцо — форма, рука — направление,
// нажатие и волна — движение. С выключенными анимациями (ТЗ п. 3.6) кольцо и рука
// стоят на месте.

/**
 * Цель подсказки: где она и какой формы. [corner] — скругление самой кнопки;
 * null — круглая или «стадион», скругление в полвысоты.
 */
class HintSpot(val rect: Rect, val corner: Dp?)

/** Где на экране стоят цели подсказки. Кнопки сообщают о себе через [hintTarget]. */
class HintTargets {
    internal val spots = mutableStateMapOf<NextStep, HintSpot>()
    internal var escape by mutableStateOf<Rect?>(null)
}

/**
 * Эта кнопка — цель шагов [steps]: слой подсказки узнаёт, где её обвести.
 * [corner] — скругление кнопки, если она не круглая: кольцо и вырез в затемнении
 * повторяют её форму. Плейтест 29.09: плашку игры обводил «стадион», и рамка
 * подсказки спорила с прямоугольником под ней.
 */
fun Modifier.hintTarget(targets: HintTargets, vararg steps: NextStep, corner: Dp? = null): Modifier =
    onGloballyPositioned { coordinates ->
        val spot = HintSpot(coordinates.boundsInRoot(), corner)
        steps.forEach { targets.spots[it] = spot }
    }

/** Одна строка к подсказке для первого уровня: до шести слов, чтобы прочитать на бегу. */
fun NextStep.bubble(): String = when (this) {
    NextStep.PLAN -> "Составь план"
    NextStep.FEED -> "Покорми Финни, чтобы он был сыт."
    NextStep.WASH -> "Помой Финни, чтобы он стал чистым."
    NextStep.LEVEL_GAME -> "Сыграй в игру уровня"
    NextStep.SLEEP -> "Уложи питомца спать"
    NextStep.WAKE -> "Выспался — можно будить!"
    NextStep.FINISH -> "Заверши уровень!"
    NextStep.SAVE -> "Отложи монетки на выбранную вещь."
}

/**
 * Сколько ребёнок должен ничего не нажимать, чтобы пришла подсказка к [step] на уровне
 * [level]. 0 — сразу и всегда. Правило растворения — в комментарии в начале файла.
 */
fun hintDelayMillis(step: NextStep, level: Int): Long = when {
    step == NextStep.FINISH || step == NextStep.WAKE -> 0L
    level <= 1 -> 0L
    level <= 3 -> 6_000L
    else -> 20_000L
}

/** Рука — пока учимся; дальше хватает кольца: куда нажать, играющий знает и так. */
fun hintWithHand(level: Int): Boolean = level <= 3

/** Зазор между целью и кольцом. */
private val RingGap = 6.dp

/** Насколько разбегается волна от нажатия. */
private val PulseTravel = 12.dp

/** Сколько рука проходит от отведённой до нажимающей. */
private val HandTravel = 22.dp

/** Один цикл «подвела — нажала — отвела». */
private const val TapCycleMillis = 1_400

/**
 * Слой подсказки. [step] — что подсветить, null — ничего. [bubble] — строка в пузыре
 * или null. [spotlight] — затемнить всё, кроме цели (обучение на первом уровне).
 * [animate] — выключенные анимации оставляют кольцо и руку на месте.
 * [hand] — рисовать ли руку. [visibility] 0..1 — насколько подсказка проявлена:
 * она приходит и уходит плавно (см. [hintDelayMillis]); вспышку уровня это не гасит.
 * [burst] 0..1 — вспышка у значка уровня, когда уровень стал готов к завершению.
 * [note] — итог только что сделанного: на обучении он стоит в пузыре над следующим
 * шагом, а не отдельной карточкой, которую надо ждать или смахивать.
 *
 * Анимация читается только при рисовании: кадры не пересобирают ни слой, ни главный экран.
 */
@Composable
fun NextStepOverlay(
    step: NextStep?,
    targets: HintTargets,
    bubble: String?,
    spotlight: Boolean,
    animate: Boolean,
    burst: () -> Float,
    modifier: Modifier = Modifier,
    hand: Boolean = true,
    visibility: () -> Float = { 1f },
    note: ActionFeedback? = null,
    onSkip: (() -> Unit)? = null,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val spot = step?.let { targets.spots[it] }
    val target = spot?.rect?.translate(-origin)
    val corner = spot?.corner
    val badge = targets.spots[NextStep.FINISH]?.rect?.translate(-origin)

    val transition = rememberInfiniteTransition(label = "hint")
    val cycle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(TapCycleMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "tap",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        // Вспышка — отдельным слоем: подсказку гасит простой, а вспышку гасить нечему.
        Canvas(Modifier.fillMaxSize()) {
            val b = burst()
            if (b > 0f && badge != null) drawBurst(badge, b)
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = visibility() }) {
            if (target == null) return@Canvas
            if (spotlight) drawSpotlight(target, corner)
            val tap = if (animate) TapPhase.at(cycle.value) else TapPhase.Still
            if (step == NextStep.FINISH) drawGlow(target, if (animate) 0.75f + 0.25f * (1f - tap.distance) else 1f)
            drawHintRing(target, corner, if (spotlight) 0f else tap.wave)
            if (hand && !spotlight) drawHand(target, tap)
        }

        if (target != null && bubble != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    // Место считается по цели: под рукой, если цель сверху, и над ней, если снизу.
                    .layout { measurable, constraints ->
                        val margin = 16.dp.roundToPx()
                        val placeable = measurable.measure(
                            Constraints(
                                maxWidth = minOf(240.dp.roundToPx(), (constraints.maxWidth - 2 * margin).coerceAtLeast(0)),
                                maxHeight = (constraints.maxHeight - 2 * margin).coerceAtLeast(0),
                            ),
                        )
                        val below = target.center.y < constraints.maxHeight / 2f
                        val reach = if (spotlight) 8.dp.toPx()
                            else (RingGap + PulseTravel + HandTravel + PointingHandLength + 8.dp).toPx()
                        val x = (target.center.x - placeable.width / 2f)
                            .coerceIn(margin.toFloat(), (constraints.maxWidth - margin - placeable.width).toFloat().coerceAtLeast(margin.toFloat()))
                        val desiredY = if (below) target.bottom + reach else target.top - reach - placeable.height
                        val y = desiredY.coerceIn(
                            margin.toFloat(),
                            (constraints.maxHeight - margin - placeable.height).toFloat().coerceAtLeast(margin.toFloat()),
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(IntOffset(x.roundToInt(), y.roundToInt()))
                        }
                    }
                    .graphicsLayer { alpha = visibility() }
                    .testTag("home-guidance")
                    .background(FinneyCream, RoundedCornerShape(16.dp))
                    .border(2.dp, FinneyInk, RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(bubble, style = MaterialTheme.typography.bodyMedium, color = FinneyInk,
                        modifier = Modifier.weight(1f, fill = false))
                    if (spotlight && onSkip != null) {
                        IconButton(onClick = onSkip, modifier = Modifier.size(48.dp)
                            .onGloballyPositioned { targets.escape = it.boundsInRoot() }
                            .semantics { contentDescription = "Закрыть подсказки" }) {
                            Text("×", fontSize = 26.sp, color = FinneyInk,
                                modifier = Modifier.clearAndSetSemantics { })
                        }
                    }
                }
            }
        }
    }
}

/**
 * Где рука в цикле нажатия. [distance] — 1 отведена, 0 касается кольца; [press] — 0..1,
 * насколько вжата; [wave] — 0..1 волна от нажатия, 0 — волны нет.
 */
private class TapPhase(val distance: Float, val press: Float, val wave: Float) {
    companion object {
        /** Без анимаций: рука на полпути, без волны — видно и куда, и что это рука. */
        val Still = TapPhase(distance = 0.5f, press = 0f, wave = 0f)

        fun at(c: Float): TapPhase = when {
            // Подводит палец к кнопке.
            c < 0.35f -> TapPhase(1f - FastOutSlowInEasing.transform(c / 0.35f), 0f, 0f)
            // Нажимает: рука чуть сжимается, от кнопки идёт волна.
            c < 0.5f -> TapPhase(0f, sin(((c - 0.35f) / 0.15f) * PI).toFloat(), 0f)
            else -> TapPhase(
                distance = FastOutSlowInEasing.transform((c - 0.5f) / 0.5f),
                press = 0f,
                wave = (c - 0.45f) / 0.55f,
            )
        }
    }
}

/**
 * Обводка вокруг цели на [grow] шире неё. Скругление растёт на тот же [grow]: кольцо
 * идёт параллельно краю кнопки, а не срезает углы. [corner] null — кнопка круглая
 * или «стадион», и кольцо такое же.
 */
private fun DrawScope.around(target: Rect, corner: Dp?, grow: Float): RoundRect {
    val r = target.inflate(grow)
    val radius = corner?.let { (it.toPx() + grow).coerceAtMost(min(r.width, r.height) / 2f) }
        ?: (min(r.width, r.height) / 2f)
    return RoundRect(r, CornerRadius(radius))
}

/** Всё темнеет, кроме цели: вырез по форме кольца. */
private fun DrawScope.drawSpotlight(target: Rect, corner: Dp?) {
    val hole = around(target, corner, RingGap.toPx() + 4.dp.toPx())
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(Offset.Zero, size))
        addRoundRect(hole)
    }
    drawPath(path, FinneyInk.copy(alpha = 0.35f))
}

/** Кольцо по форме цели: у круглой кнопки — круг, у плашки — её же скруглённый прямоугольник. */
private fun DrawScope.drawHintRing(target: Rect, corner: Dp?, wave: Float) {
    val gap = RingGap.toPx()
    fun ring(extra: Float, alpha: Float) {
        val path = Path().apply { addRoundRect(around(target, corner, gap + extra)) }
        drawPath(path, FinneyYellow.copy(alpha = alpha), style = Stroke(4.dp.toPx()))
    }
    // Волна от нажатия поверх постоянного кольца: движение зовёт взгляд, кольцо — держит.
    if (wave > 0f) ring(PulseTravel.toPx() * wave, 1f - wave)
    ring(0f, 1f)
}

/** Спокойное свечение вокруг значка уровня, пока его не нажмут. */
private fun DrawScope.drawGlow(target: Rect, strength: Float) {
    val inner = min(target.width, target.height) / 2f
    val outer = inner + 22.dp.toPx()
    val edge = inner / outer
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Transparent,
            edge - 0.001f to Color.Transparent,
            edge to FinneyYellow.copy(alpha = 0.9f * strength),
            1f to Color.Transparent,
            center = target.center,
            radius = outer,
        ),
        radius = outer,
        center = target.center,
    )
}

/**
 * Рука-указатель из простых фигур, без картинки: палец, ладонь и большой палец,
 * слитые в один контур. Цель в верхней половине экрана — рука под ней и смотрит
 * вверх, в нижней — над ней и смотрит вниз. В нажатии рука чуть сжимается к пальцу.
 */
private fun DrawScope.drawHand(target: Rect, tap: TapPhase) {
    val up = target.center.y < size.height / 2f
    val reach = RingGap.toPx() + 4.dp.toPx() + HandTravel.toPx() * tap.distance
    val tip = if (up) Offset(target.center.x, target.bottom + reach) else Offset(target.center.x, target.top - reach)
    drawPointingHand(tip, up, tap.press)
}

/**
 * Вспышка у значка уровня: круг света и звёздочки разлетаются и гаснут.
 * [t] — от 0 до 1 за время вспышки.
 */
private fun DrawScope.drawBurst(badge: Rect, t: Float) {
    val r = min(badge.width, badge.height) / 2f
    drawCircle(FinneyGlare.copy(alpha = (1f - t) * 0.8f), radius = r * (1f + 0.9f * t), center = badge.center)
    val count = 8
    val fade = 1f - t * t
    for (i in 0 until count) {
        val angle = 2 * PI * i / count - PI / 2
        val distance = r * (0.9f + 1.2f * t)
        val c = badge.center + Offset((cos(angle) * distance).toFloat(), (sin(angle) * distance).toFloat())
        val star = starPath(c, 9.dp.toPx() * (1f - 0.4f * t))
        drawPath(star, FinneyYellow.copy(alpha = fade))
        drawPath(star, FinneyInk.copy(alpha = fade), style = Stroke(2.dp.toPx()))
    }
}

/** Пятиконечная звёздочка с центром [c] и внешним радиусом [r]. */
private fun starPath(c: Offset, r: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) r else r * 0.45f
        val angle = PI * i / 5 - PI / 2
        val p = c + Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}
