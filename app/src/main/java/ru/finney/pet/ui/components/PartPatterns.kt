package ru.finney.pet.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import ru.finney.pet.domain.model.Category
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyYellow
import kotlin.math.roundToInt

// Узор у каждой части плана — вместе со значком это её «форма» на всю игру: круг плана,
// банки итога, полоса над товарами. Подобрано на доске «Части плана» (20.10): одного
// значка ребёнку мало, а голый цвет у нас уже занят смыслами (жёлтый — деньги, зелёный — успех).
// Ни одна часть не «лучше» других: нужное — самое обычное (клетка), желаемое — конфетти,
// копилка — монетки тон в тон, без блеска.

/** Часть плана. Копилка не категория товара, поэтому своё перечисление, а не [Category]. */
enum class PlanPart { NEEDS, WANTS, SAVINGS }

fun Category.toPart(): PlanPart = when (this) {
    Category.NEEDS -> PlanPart.NEEDS
    Category.WANTS -> PlanPart.WANTS
}

/** Значок части — тот же, что [categoryIcon] и [SavingsIcon]. */
fun partIcon(part: PlanPart): FinneyIcons = when (part) {
    PlanPart.NEEDS -> categoryIcon(Category.NEEDS)
    PlanPart.WANTS -> categoryIcon(Category.WANTS)
    PlanPart.SAVINGS -> SavingsIcon
}

/** Сторона плитки узора в dp: узор повторяется ею во все стороны. */
private fun tileSide(part: PlanPart): Float = when (part) {
    PlanPart.NEEDS -> 22f
    PlanPart.WANTS -> 24f
    PlanPart.SAVINGS -> 20f
}

/** Лавандовая подложка клетки — ink, разбавленный до фона. */
private val NeedsBase = Color(0xFFE6E2F7)

/** Монета на копилке — на тон темнее жёлтого, чтобы не блестела. */
private val CoinFace = Color(0xFFEFC765)

/**
 * Кисть с узором [part] — для `background`, `drawArc`, `drawRect`. Плитка рисуется
 * один раз на плотность экрана и дальше только повторяется.
 */
@Composable
fun rememberPartBrush(part: PlanPart): ShaderBrush {
    val density = LocalDensity.current
    return remember(part, density.density) {
        val px = (tileSide(part) * density.density).roundToInt().coerceAtLeast(1)
        val bitmap = ImageBitmap(px, px)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
            // Плитка задана в dp — масштаб в пиксели здесь, чтобы координаты ниже читались как на доске.
            val k = px / tileSide(part)
            when (part) {
                PlanPart.NEEDS -> drawGingham(k)
                PlanPart.WANTS -> drawConfetti(k)
                PlanPart.SAVINGS -> drawCoins(k)
            }
        }
        ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
    }
}

// Клетка-«виши»: полоса вдоль и поперёк, на пересечении темнее — как на ткани.
private fun DrawScope.drawGingham(k: Float) {
    val band = FinneyInk.copy(alpha = 0.16f)
    drawRect(NeedsBase)
    drawRect(band, size = Size(11f * k, 22f * k))
    drawRect(band, size = Size(22f * k, 11f * k))
}

// Конфетти с доски «2 · клетка · конфетти · монетки», в 1.3 раза крупнее: без контуров —
// кремовая и синяя ленточки, жёлтый и кремовый кружки. Координаты — как на доске (плитка 18).
private fun DrawScope.drawConfetti(k: Float) {
    val m = k * ConfettiScale
    drawRect(FinneyPeach)
    ribbon(FinneyCream, x = 2f, y = 3f, angle = 30f, m = m)
    ribbon(FinneyInk.copy(alpha = 0.5f), x = 10f, y = 11f, angle = -35f, m = m)
    drawCircle(FinneyYellow, 1.6f * m, Offset(14f * m, 4f * m))
    drawCircle(FinneyCream, 1.6f * m, Offset(4.5f * m, 13.5f * m))
}

/** Во сколько раз конфетти крупнее, чем на доске: плитка 18 → 24 dp. */
private const val ConfettiScale = 24f / 18f

private fun DrawScope.ribbon(fill: Color, x: Float, y: Float, angle: Float, m: Float) {
    rotate(angle, pivot = Offset((x + 3f) * m, (y + 1.1f) * m)) {
        drawRoundRect(fill, Offset(x * m, y * m), Size(6f * m, 2.2f * m), CornerRadius(1f * m))
    }
}

// Монетки тон в тон: жёлтое на жёлтом и бледный контур — копилка не должна сиять ярче других частей.
private fun DrawScope.drawCoins(k: Float) {
    val c = Offset(10f * k, 10f * k)
    drawRect(FinneyYellow)
    drawCircle(CoinFace, 6.5f * k, c)
    drawCircle(FinneyInk.copy(alpha = 0.45f), 6.5f * k, c, style = Stroke(1.3f * k))
    drawCircle(FinneyInk.copy(alpha = 0.3f), 3.4f * k, c, style = Stroke(1.1f * k))
}
