package ru.finney.pet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.StrokeRegular

// Один пузырь реплики на всё приложение: мини-игры, карточка игры, главный, знакомство,
// план и сон. Раньше их было семь, и в играх тело и хвостик были двумя контурами —
// рамка обводила каждый отдельно, и хвостик выглядел приклеенным треугольником.

/** С какой стороны пузыря хвостик — в сторону того, кто говорит. */
enum class Tail { LEFT, RIGHT, UP, DOWN }

/** Тень пузыря по низу — светло-сиреневая, как у плашек. */
private val BubbleShade = Color(0xFFF1ECFF)

private val BubbleRadius = 22.dp
/** Насколько хвостик выступает из тела пузыря. */
val TailLength = 14.dp
private val TailHalfBase = 12.dp
private val ShadeDepth = 5.dp

private val BubblePadding = PaddingValues(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 13.dp)

/**
 * Белый пузырь с хвостиком. [tailAt] — где хвостик вдоль своей стороны, 0..1: у левой
 * и правой стороны — сверху вниз, у верхней и нижней — слева направо. Хвостик не
 * подходит к углу ближе скругления. Место под хвостик пузырь оставляет сам.
 */
@Composable
fun SpeechBubble(
    tail: Tail,
    modifier: Modifier = Modifier,
    tailAt: Float = 0.5f,
    padding: PaddingValues = BubblePadding,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable () -> Unit,
) {
    val shape = remember(tail, tailAt) { SpeechBubbleShape(tail, tailAt) }
    Box(
        contentAlignment = contentAlignment,
        modifier = modifier
            .background(Color.White, shape)
            .bubbleShade(shape)
            .border(StrokeRegular, FinneyInk, shape)
            .padding(
                start = if (tail == Tail.LEFT) TailLength else 0.dp,
                end = if (tail == Tail.RIGHT) TailLength else 0.dp,
                top = if (tail == Tail.UP) TailLength else 0.dp,
                bottom = if (tail == Tail.DOWN) TailLength else 0.dp,
            )
            .padding(padding),
    ) { content() }
}

/** Пузырь с одной строкой текста. */
@Composable
fun SpeechBubble(
    text: AnnotatedString,
    tail: Tail,
    modifier: Modifier = Modifier,
    tailAt: Float = 0.5f,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    textAlign: TextAlign? = null,
) {
    SpeechBubble(
        tail, modifier, tailAt,
        contentAlignment = if (textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart,
    ) {
        Text(text, style = style, color = FinneyInk, textAlign = textAlign)
    }
}

@Composable
fun SpeechBubble(
    text: String,
    tail: Tail,
    modifier: Modifier = Modifier,
    tailAt: Float = 0.5f,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    textAlign: TextAlign? = null,
) = SpeechBubble(AnnotatedString(text), tail, modifier, tailAt, style, textAlign)

/** Тень по низу внутри рамки: форма минус та же форма, поднятая на [ShadeDepth]. */
private fun Modifier.bubbleShade(shape: Shape): Modifier = drawBehind {
    val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }
    val lifted = Path().apply { addPath(outline, Offset(0f, -(ShadeDepth + StrokeRegular).toPx())) }
    drawPath(Path().apply { op(outline, lifted, PathOperation.Difference) }, BubbleShade)
}

/**
 * Тело и хвостик — одна фигура: пути объединены, и рамка идёт по общему контуру,
 * без линии поперёк основания хвостика. Основание заходит внутрь тела на полрадиуса,
 * чтобы между ними не осталось щели.
 */
private data class SpeechBubbleShape(private val tail: Tail, private val tailAt: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val t = with(density) { TailLength.toPx() }
        val h = with(density) { TailHalfBase.toPx() }
        val body = Rect(
            left = if (tail == Tail.LEFT) t else 0f,
            top = if (tail == Tail.UP) t else 0f,
            right = size.width - if (tail == Tail.RIGHT) t else 0f,
            bottom = size.height - if (tail == Tail.DOWN) t else 0f,
        )
        val r = minOf(with(density) { BubbleRadius.toPx() }, body.width / 2, body.height / 2)
        val side = tail == Tail.LEFT || tail == Tail.RIGHT
        val length = if (side) body.height else body.width
        val c = (if (side) body.top else body.left) +
            (tailAt * length).coerceIn(minOf(r, length / 2), maxOf(length - r, length / 2))
        val d = r / 2
        val tip = Path().apply {
            when (tail) {
                Tail.LEFT -> { moveTo(body.left + d, c - h); lineTo(0f, c); lineTo(body.left + d, c + h) }
                Tail.RIGHT -> { moveTo(body.right - d, c - h); lineTo(size.width, c); lineTo(body.right - d, c + h) }
                Tail.UP -> { moveTo(c - h, body.top + d); lineTo(c, 0f); lineTo(c + h, body.top + d) }
                Tail.DOWN -> { moveTo(c - h, body.bottom - d); lineTo(c, size.height); lineTo(c + h, body.bottom - d) }
            }
            close()
        }
        val rounded = Path().apply { addRoundRect(RoundRect(body, CornerRadius(r))) }
        return Outline.Generic(Path().apply { op(rounded, tip, PathOperation.Union) })
    }
}
