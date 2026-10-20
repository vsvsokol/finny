package ru.finney.pet.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.StrokeRegular
import ru.finney.pet.ui.theme.StrokeThin
import kotlin.math.cos
import kotlin.math.sin

// Круг плана: бюджет целиком — круг, три части — его доли. Плейтест: «план скучный,
// диаграмму прикольно сделать». Круг показывает то, что столбик чисел не показывал:
// сколько от всего уходит на каждую часть и сколько ещё не разложено.

/** Пустая часть круга — ещё не разложено. Светлее кремового, как пустые шкалы. */
private val Unplanned = Color(0xFFFFF7EA)

/** Порядок частей на круге — по часовой от верха. */
private val Parts = listOf(PlanPart.NEEDS, PlanPart.WANTS, PlanPart.SAVINGS)

/**
 * Круг плана: [needs], [wants], [savings] из [budget]. Остаток — пустая дуга.
 * Если разложено больше, чем есть, круг делится по разложенному: доли видны, а
 * «не хватает» говорит строка остатка под ним.
 *
 * Дуги едут пружиной при каждом «−/+» — ребёнок видит, как растёт часть.
 * [content] — что лежит в середине, обычно сумма бюджета.
 */
@Composable
fun PlanDonut(
    budget: Int,
    needs: Int,
    wants: Int,
    savings: Int,
    modifier: Modifier = Modifier,
    diameter: Dp = 168.dp,
    content: @Composable () -> Unit = {},
) {
    val total = maxOf(budget, needs + wants + savings).coerceAtLeast(1)
    val needsSweep by animateFloatAsState(360f * needs / total, label = "needs")
    val wantsSweep by animateFloatAsState(360f * wants / total, label = "wants")
    val savingsSweep by animateFloatAsState(360f * savings / total, label = "savings")
    val sweeps = listOf(needsSweep, wantsSweep, savingsSweep)
    // Часть узнаётся по узору дуги и значку на ней, а не по цвету (ТЗ п. 3.6) — см. PartPatterns.kt.
    val brushes = Parts.map { rememberPartBrush(it) }

    val ring = diameter * 0.24f
    Box(
        modifier = modifier
            .size(diameter)
            .clearAndSetSemantics {
                contentDescription = "Из $budget: нужное $needs, желаемое $wants, копилка $savings"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val width = ring.toPx()
            val radius = (size.minDimension - width) / 2f
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)
            drawCircle(Unplanned, radius, style = Stroke(width))
            var start = -90f
            sweeps.forEachIndexed { i, sweep ->
                if (sweep > 0.5f) {
                    drawArc(brushes[i], start, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width))
                }
                start += sweep
            }
            // Черты между частями и обводка кольца — ink, как у всего в игре.
            val stroke = StrokeRegular.toPx()
            var edge = -90f
            val cuts = listOf(-90f) + sweeps.map { edge += it; edge }
            if (sweeps.sum() > 0.5f) {
                cuts.forEach { angle ->
                    val rad = Math.toRadians(angle.toDouble())
                    val inner = radius - width / 2f
                    val outer = radius + width / 2f
                    drawLine(
                        FinneyInk,
                        Offset(center.x + inner * cos(rad).toFloat(), center.y + inner * sin(rad).toFloat()),
                        Offset(center.x + outer * cos(rad).toFloat(), center.y + outer * sin(rad).toFloat()),
                        stroke,
                    )
                }
            }
            drawCircle(FinneyInk, radius + width / 2f, style = Stroke(stroke))
            drawCircle(FinneyInk, radius - width / 2f, style = Stroke(stroke))
        }

        // Значок части — посередине её дуги, в белом кружке: так он читается на любом узоре.
        // Стоит у каждой части, даже узкой: по нему ребёнок и связывает дугу с частью.
        val iconSize = ring * 0.78f
        val orbit = (diameter - ring) / 2f
        var start = -90f
        sweeps.forEachIndexed { i, sweep ->
            if (sweep > 0.5f) {
                val mid = Math.toRadians((start + sweep / 2f).toDouble())
                Box(
                    modifier = Modifier
                        .offset(x = orbit * cos(mid).toFloat(), y = orbit * sin(mid).toFloat())
                        .size(iconSize)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(StrokeThin, FinneyInk, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    FinneyIcon(partIcon(Parts[i]), size = iconSize * 0.72f)
                }
            }
            start += sweep
        }
        content()
    }
}
