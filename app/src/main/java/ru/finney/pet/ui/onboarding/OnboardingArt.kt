package ru.finney.pet.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ru.finney.pet.ui.components.SpeechBubble
import ru.finney.pet.ui.components.Tail
import ru.finney.pet.ui.components.TailLength
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyPink
import ru.finney.pet.ui.theme.FinneyYellow

// Детали знакомства из макета 10.2026 (HTML-прототип на 412 px шириной). Числа
// взяты из его CSS как есть: пиксель прототипа — это dp, его кегль — sp. Свой облик
// у знакомства отдельно от кита: кнопки здесь с обводкой 4 dp при любой высоте,
// а не 1/12, как в остальной игре.

/** Сиреневая полоса по низу белых плашек и пузырей. */
internal val ArtSoft = Color(0xFFEDE6FF)

/** Фон второй страницы — жёлтый светлее кнопок. */
internal val ArtYellowPage = Color(0xFFFCE6A6)

/** Фон третьей страницы. */
internal val ArtLilac = Color(0xFFDAD3F6)

/** Звёзды на сиреневом фоне. */
private val ArtLilacStar = Color(0xFFC6BBF0)

/** Выделение в реплике: «на мечту!». */
internal val ArtHighlight = Color(0xFFE8694A)

/** Текст ошибки — темнее розовой рамки, чтобы читался на белом. */
internal val ArtError = Color(0xFFE5486A)

internal val ArtGreen = Color(0xFF86CC8E)
internal val ArtBlue = Color(0xFF67A6DC)

/** Поля страницы: 30 сверху, 22 по бокам, 24 снизу. */
internal val ArtPagePadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, top = 30.dp, end = 22.dp, bottom = 24.dp)

/** Шаг между блоками страницы. */
internal val ArtGap = 16.dp

/** Обводка кнопок и плашек. */
internal val ArtBorder = 4.dp

// ---------- Текст ----------

/** Текст прототипа: Glina, кегль и межстрочный в долях кегля. */
@Composable
internal fun artText(size: Float, line: Float = 1.15f): TextStyle =
    MaterialTheme.typography.bodyLarge.copy(fontSize = size.sp, lineHeight = (size * line).sp)

/** Вывеска: коралловая заливка с обводкой, буквы чуть разрежены. */
@Composable
internal fun artDisplay(size: Float): TextStyle =
    artText(size, 1.1f).copy(letterSpacing = 0.015.em)

@Composable
internal fun ArtSign(text: String, size: Float, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center) {
    OutlinedText(text, style = artDisplay(size), textAlign = align, modifier = modifier)
}

@Composable
internal fun ArtBody(
    text: String,
    size: Float,
    modifier: Modifier = Modifier,
    color: Color = FinneyInk,
    line: Float = 1.15f,
    align: TextAlign = TextAlign.Center,
) {
    Text(text, style = artText(size, line), color = color, textAlign = align, modifier = modifier)
}

// ---------- Фоны ----------

/** Подложка страниц: точки через 20 dp. */
internal fun Modifier.dotsBackdrop(color: Color): Modifier = drawBehind {
    drawRect(color)
    val step = 20.dp.toPx()
    val dot = FinneyInk.copy(alpha = 0.10f)
    val r = 1.9.dp.toPx()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawCircle(dot, r, Offset(x, y))
            x += step
        }
        y += step
    }
}

private val TileStarA = PathParser().parsePathString("M18 8l2.4 5 5.4.7-4 3.7 1 5.4-4.8-2.6-4.8 2.6 1-5.4-4-3.7 5.4-.7z").toPath()
private val TileStarB = PathParser().parsePathString("M52 40l1.6 3.3 3.6.5-2.6 2.5.6 3.6-3.2-1.7-3.2 1.7.6-3.6-2.6-2.5 3.6-.5z").toPath()

/** Сиреневая подложка третьей страницы: две звезды на плитке 70 × 70. */
internal fun Modifier.starsBackdrop(): Modifier = drawBehind {
    drawRect(ArtLilac)
    val tile = 70.dp.toPx()
    val k = 1.dp.toPx()
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            translate(x, y) {
                scale(k, k, pivot = Offset.Zero) {
                    drawPath(TileStarA, ArtLilacStar)
                    drawPath(TileStarB, ArtLilacStar)
                }
            }
            x += tile
        }
        y += tile
    }
}

// ---------- Плашки ----------

/** Блик плашки: белый овал, повёрнутый на −24°. Координаты — от внутреннего края обводки. */
internal data class Glare(val left: Dp, val top: Dp, val width: Dp, val height: Dp)

/**
 * Объёмная плашка прототипа: заливка, полоса цвета [band] по низу внутри обводки
 * (форма минус та же форма, поднятая на [depth]) и обводка [border] поверх.
 * [radius] больше половины высоты — получается «стадион» или круг.
 */
internal fun Modifier.plate(
    radius: Dp,
    fill: Color,
    band: Color,
    depth: Dp,
    border: Dp = ArtBorder,
    glare: Glare? = null,
    ink: Color = FinneyInk,
): Modifier = drawBehind {
    val b = border.toPx()
    val r = minOf(radius.toPx(), size.minDimension / 2)
    drawRoundRect(fill, cornerRadius = CornerRadius(r))
    val inner = RoundRect(b, b, size.width - b, size.height - b, CornerRadius((r - b).coerceAtLeast(0f)))
    val body = Path().apply { addRoundRect(inner) }
    val lifted = Path().apply { addRoundRect(inner.translate(Offset(0f, -depth.toPx()))) }
    drawPath(Path().apply { op(body, lifted, PathOperation.Difference) }, band)
    if (glare != null) drawGlare(glare, b)
    drawRoundRect(
        color = ink,
        topLeft = Offset(b / 2, b / 2),
        size = Size(size.width - b, size.height - b),
        cornerRadius = CornerRadius((r - b / 2).coerceAtLeast(0f)),
        style = Stroke(b),
    )
}

private fun DrawScope.drawGlare(glare: Glare, border: Float) {
    val w = glare.width.toPx()
    val h = glare.height.toPx()
    val centre = Offset(border + glare.left.toPx() + w / 2, border + glare.top.toPx() + h / 2)
    rotate(-24f, pivot = centre) {
        drawOval(Color.White, topLeft = centre - Offset(w / 2, h / 2), size = Size(w, h))
    }
}

// ---------- Кнопки ----------

private val ButtonGlare = Glare(20.dp, 11.dp, 24.dp, 13.dp)
private val RoundGlare = Glare(13.dp, 10.dp, 15.dp, 10.dp)
internal val DiscGlare = Glare(11.dp, 13.dp, 16.dp, 10.dp)

/** Высота кнопок знакомства и диаметр круглой «назад». */
internal val ArtButtonHeight = 70.dp

/**
 * Кнопка прототипа: жёлтый «стадион», коралловая полоса 11 dp, блик слева.
 * Под пальцем опускается на 4 dp, полоса становится 5 dp — как будто вдавили.
 * [sound] null — звук играет вызывающий ([ru.finney.pet.ui.components.ChargeButton]).
 */
@Composable
internal fun ArtButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sound: Sfx? = Sfx.Tap,
    interaction: MutableInteractionSource? = null,
) {
    val sounds = LocalSounds.current
    val source = interaction ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(ArtButtonHeight)
            .alpha(if (enabled) 1f else 0.45f)
            .graphicsLayer { translationY = if (pressed) 4.dp.toPx() else 0f }
            .plate(ArtButtonHeight, FinneyYellow, FinneyPeach, if (pressed) 5.dp else 11.dp, glare = ButtonGlare)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button) {
                sound?.let(sounds::play)
                onClick()
            }
            .padding(bottom = 9.dp),
    ) {
        OutlinedText(text, style = artDisplay(32f))
    }
}

private val BackArrow = PathParser().parsePathString("M20 12H5M11 5l-7 7 7 7").toPath()

/** Круглая «назад» со стрелкой, ростом с кнопку. */
@Composable
internal fun ArtBackButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val sounds = LocalSounds.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(ArtButtonHeight)
            .graphicsLayer { translationY = if (pressed) 4.dp.toPx() else 0f }
            .plate(ArtButtonHeight, FinneyYellow, FinneyPeach, if (pressed) 5.dp else 11.dp, glare = RoundGlare)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button) {
                sounds.play(Sfx.Back)
                onClick()
            }
            .semantics { contentDescription = "Назад" }
            .padding(bottom = 7.dp),
    ) {
        SvgIcon(BackArrow, Modifier.size(34.dp), stroke = 3.4f)
    }
}

/** Иконка из пути на сетке 24 × 24: заливкой или линией толщиной [stroke] в долях сетки. */
@Composable
internal fun SvgIcon(
    path: Path,
    modifier: Modifier = Modifier,
    color: Color = FinneyInk,
    stroke: Float? = null,
    outline: Float? = null,
) {
    Canvas(modifier) {
        val k = size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            if (stroke == null) {
                drawPath(path, color)
                if (outline != null) {
                    drawPath(path, FinneyInk, style = Stroke(outline, join = StrokeJoin.Round))
                }
            } else {
                drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

// ---------- Точки страниц ----------

/** Где ты: три кружка, текущий крупнее и розовый. */
@Composable
internal fun ArtDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics { contentDescription = "${current + 1} из $total" },
    ) {
        repeat(total) { i ->
            val on = i == current
            Box(
                Modifier
                    .size(if (on) 24.dp else 16.dp)
                    .plate(12.dp, if (on) FinneyPink else FinneyYellow, Color.Transparent, 0.dp, border = 3.dp),
            )
        }
    }
}

// ---------- Пузырь ----------

/**
 * Реплика: общий пузырь с хвостиком к говорящему, текст — кеглем знакомства.
 * Хвостик места в раскладке не занимает — как в прототипе, он заходит в промежуток
 * между блоками.
 */
@Composable
internal fun ArtSay(text: AnnotatedString, tailUp: Boolean, modifier: Modifier = Modifier) {
    SpeechBubble(
        text,
        if (tailUp) Tail.UP else Tail.DOWN,
        modifier.layout { measurable, constraints ->
            val tail = TailLength.roundToPx()
            val placeable = measurable.measure(constraints.offset(vertical = tail))
            layout(placeable.width, placeable.height - tail) { placeable.place(0, if (tailUp) -tail else 0) }
        },
        style = artText(22f, 1.18f),
        textAlign = TextAlign.Center,
    )
}

// ---------- Лучи ----------

/**
 * Жёлтые лучи прототипа: 20 лучей через один на квадрате [box] × [box], к краю гаснут
 * (сплошные до 22 % и прозрачные к 66 % от угла квадрата), оборот за 70 с.
 * [centre] — где центр лучей в элементе, по умолчанию середина. С выключенными
 * анимациями лучи стоят.
 */
@Composable
internal fun Modifier.sunburst(
    box: Dp,
    centre: (Size) -> Offset = { Offset(it.width / 2, it.height / 2) },
): Modifier {
    val turning = LocalAnimations.current
    val spin = rememberInfiniteTransition(label = "sunburst").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(70_000, easing = LinearEasing)),
        label = "spin",
    )
    return drawBehind {
        val centre = centre(size)
        val half = box.toPx() / 2
        // radial-gradient(circle) без размера тянется до угла квадрата.
        val corner = half * 1.4142f
        val fade = Brush.radialGradient(
            0f to FinneyYellow,
            0.22f to FinneyYellow,
            0.66f to FinneyYellow.copy(alpha = 0f),
            center = centre,
            radius = corner,
        )
        val start = (if (turning) spin.value else 0f) - 90f
        repeat(20) { i ->
            drawArc(
                brush = fade,
                startAngle = start + i * 18f,
                sweepAngle = 9f,
                useCenter = true,
                topLeft = centre - Offset(half, half),
                size = Size(half * 2, half * 2),
            )
        }
    }
}

/** Покачивание вверх-вниз на 8 dp за 3,2 с, как у питомца в прототипе. */
@Composable
internal fun Modifier.bob(): Modifier {
    if (!LocalAnimations.current) return this
    val t = rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    return graphicsLayer { translationY = -8.dp.toPx() * t.value }
}
