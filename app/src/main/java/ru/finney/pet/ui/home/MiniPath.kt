package ru.finney.pet.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.math.PI
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.components.SpeechLeadColor
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.period.Star
import ru.finney.pet.ui.progress.LevelNode
import ru.finney.pet.ui.progress.NodeStatus
import ru.finney.pet.ui.progress.UnlockChip
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.StrokeRegular

// Мини-дорожка в панели уровня (разбор 20.10, концепт «Мини-дорожка»). Карту «Мой путь»
// открывали через кнопку «Итоги и история», на которую жать не хотелось. Теперь кусок той же
// карты виден сразу: пройденные узлы со звёздами, «ты здесь» и подарки впереди — картинкой.
// Вся полоска — кнопка, а узлы похожи на большую карту, поэтому переход туда не удивляет.

/** Пять уровней вокруг текущего: два позади, два впереди; у края пути окно сдвигается. */
internal fun miniPathLevels(current: Int, maxLevel: Int): IntRange {
    val start = (current - 2).coerceIn(1, (maxLevel - MiniNodes + 1).coerceAtLeast(1))
    return start..minOf(maxLevel, start + MiniNodes - 1)
}

@Composable
internal fun MiniPath(nodes: List<LevelNode>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (nodes.isEmpty()) return
    val shape = RoundedCornerShape(24.dp)
    val animations = LocalAnimations.current
    val reveal = remember { Animatable(if (animations) 0f else 1f) }
    LaunchedEffect(nodes, animations) {
        reveal.snapTo(if (animations) 0f else 1f)
        if (animations) reveal.animateTo(1f, tween(700, easing = LinearEasing))
    }
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(androidx.compose.ui.graphics.Color.White)
            .border(StrokeBorder, FinneyInk, shape)
            .clickable(onClickLabel = "Открыть «Мой путь»", onClick = onOpen)
            .padding(top = 14.dp, bottom = 34.dp),
    ) {
        // Дорога занимает прежнюю ширину. Края подписи помещаются за счёт кегля подписи,
        // а не сжатия расстояний между узлами; 16 sp — минимум ТЗ, в том числе при 130 %.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(AreaHeight)) {
            val n = nodes.size
            val slot = maxWidth / n
            fun centerY(i: Int): Dp = Drop * (i % 2) + CurrentSize / 2
            // При открытии дорога прорисовывается слева направо, как в концепте.
            // Геометрия остаётся в кеше; кадры анимации читаются только при рисовании.
            Box(Modifier.fillMaxSize().drawWithCache {
                val step = size.width / n
                val points = nodes.indices.map { Offset(step * (it + 0.5f), centerY(it).toPx()) }
                val lead = Offset(-12.dp.toPx(), points.first().y + Drop.toPx())
                val tail = Offset(size.width + 12.dp.toPx(), points.last().y + Drop.toPx() * (if (n % 2 == 0) -1 else 1))
                val chain = buildList {
                    if (nodes.first().level > 1) add(Triple(lead, points.first(), true))
                    for (i in 0 until n - 1) add(Triple(points[i], points[i + 1], nodes[i + 1].status != NodeStatus.FUTURE))
                    add(Triple(points.last(), tail, false))
                }
                val measure = PathMeasure()
                var length = 0f
                val segments = chain.map { (a, b, walked) ->
                    val middle = (a.x + b.x) / 2
                    val path = Path().apply { moveTo(a.x, a.y); cubicTo(middle, a.y, middle, b.y, b.x, b.y) }
                    measure.setPath(path, false)
                    MiniRoadSegment(path, walked, length, measure.length).also { length += it.length }
                }
                val solid = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
                val dashed = Stroke(4.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 7.dp.toPx())))
                val partial = Path()
                onDrawBehind {
                    val end = length * reveal.value
                    segments.forEach { segment ->
                        val visible = (end - segment.start).coerceIn(0f, segment.length)
                        if (visible > 0f) {
                            val path = if (visible >= segment.length) segment.path else {
                                partial.reset()
                                measure.setPath(segment.path, false)
                                measure.getSegment(0f, visible, partial, true)
                                partial
                            }
                            drawPath(path, if (segment.walked) FinneyInk else FinneyInk.copy(alpha = 0.55f),
                                style = if (segment.walked) solid else dashed)
                        }
                    }
                }
            })
            nodes.forEachIndexed { i, node ->
                val size = nodeSize(node)
                Column(
                    Modifier
                        .width(slot)
                        .offset(x = slot * i, y = centerY(i) - size / 2)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                            val t = ((reveal.value - i * 0.08f) / 0.6f).coerceIn(0f, 1f)
                            val scale = 0.82f + 0.18f * t + 0.12f * sin(PI.toFloat() * t)
                            alpha = t
                            scaleX = scale
                            scaleY = scale
                        }
                        .clearAndSetSemantics { contentDescription = spoken(node) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    MiniNode(node, size)
                    when (node.status) {
                        NodeStatus.DONE -> Row(horizontalArrangement = Arrangement.spacedBy((-3).dp)) {
                            repeat(3) { Star(filled = it < node.stars, modifier = Modifier.size(17.dp)) }
                        }
                        NodeStatus.CURRENT -> Text("ты здесь", style = MaterialTheme.typography.labelMedium.copy(fontSize = 16.sp, lineHeight = 22.sp), color = SpeechLeadColor,
                            textAlign = TextAlign.Center, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentWidth(unbounded = true))
                        NodeStatus.FUTURE -> Unit
                    }
                }
            }
        }
        Text(
            "Весь путь ›",
            style = MaterialTheme.typography.bodyLarge,
            color = FinneyInk,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-10).dp, y = 28.dp)
                .clip(RoundedCornerShape(50))
                .background(FinneyYellow)
                .border(StrokeRegular, FinneyInk, RoundedCornerShape(50))
                .padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun MiniNode(node: LevelNode, size: Dp) {
    val animations = LocalAnimations.current
    // requiredSize не даёт узкому столбцу превратить круг текущего уровня в овал.
    Box(Modifier.requiredSize(size)) {
        if (node.status == NodeStatus.CURRENT && animations) {
            // Кольцо-пульс расходится от текущего узла: «ты здесь» видно и без подписи.
            val t by rememberInfiniteTransition(label = "here").animateFloat(
                0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "ring",
            )
            Box(Modifier.size(size).drawBehind {
                drawCircle(FinneyYellow.copy(alpha = 0.9f * (1 - t)), radius = this.size.minDimension / 2 + 14.dp.toPx() * t)
            })
        }
        val fill = when (node.status) {
            NodeStatus.DONE -> Brush.linearGradient(listOf(FinneyGreen, FinneyGreen))
            // Как на большой карте: жёлтая середина в персиковом кольце.
            NodeStatus.CURRENT -> Brush.radialGradient(0f to FinneyYellow, 0.62f to FinneyYellow, 0.63f to FinneyPeach, 1f to FinneyPeach)
            NodeStatus.FUTURE -> Brush.linearGradient(listOf(FinneyCream, FinneyCream))
        }
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(fill)
                .then(
                    if (node.status == NodeStatus.FUTURE) Modifier.dashedCircle() else Modifier.border(StrokeBorder, FinneyInk, CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (node.status) {
                NodeStatus.CURRENT -> OutlinedText("${node.level}", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                NodeStatus.DONE -> Text("${node.level}", style = MaterialTheme.typography.titleLarge, color = FinneyInk)
                NodeStatus.FUTURE -> Text("${node.level}", style = MaterialTheme.typography.titleLarge, color = FinneyInk.copy(alpha = 0.55f))
            }
        }
        if (node.status == NodeStatus.FUTURE) reward(node)?.let {
            Gift(it, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-12).dp))
        }
    }
}

/** Пунктирная обводка будущего узла: дорога туда ещё не пройдена. */
private fun Modifier.dashedCircle(): Modifier = drawBehind {
    val w = StrokeBorder.toPx()
    drawCircle(
        FinneyInk.copy(alpha = 0.7f),
        radius = size.minDimension / 2 - w / 2,
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))),
    )
}

/**
 * Награда впереди — то, ради чего идти: вещь цели, иначе вещь магазина. Игры и звания
 * у узла не рисуем: геймпад на каждом втором узле ничего не обещал (разбор 20.10).
 */
private fun reward(node: LevelNode): UnlockChip? =
    node.chips.firstOrNull { it.art != null && it.label.startsWith("Цель") } ?: node.chips.firstOrNull { it.art != null }

/** Награда в белом кружке у узла — рисунком самой вещи. */
@Composable
private fun Gift(chip: UnlockChip, modifier: Modifier = Modifier) {
    val float = if (LocalAnimations.current) {
        val phase by rememberInfiniteTransition(label = "gift").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "float")
        Modifier.graphicsLayer { translationY = -2.dp.toPx() * sin(2f * PI.toFloat() * phase) }
    } else Modifier
    Box(
        modifier
            .then(float)
            .size(34.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color.White)
            .border(StrokeRegular, FinneyInk, CircleShape)
            .semantics { contentDescription = chip.label },
        contentAlignment = Alignment.Center,
    ) {
        chip.art?.let { Image(painterResource(it), contentDescription = null, modifier = Modifier.size(26.dp)) }
    }
}

private fun spoken(node: LevelNode): String = when (node.status) {
    NodeStatus.DONE -> "Уровень ${node.level} пройден, звёзд: ${node.stars}"
    NodeStatus.CURRENT -> "Уровень ${node.level}, ты здесь"
    NodeStatus.FUTURE -> "Уровень ${node.level} впереди" + (reward(node)?.let { ": ${it.label}" } ?: "")
}

private fun nodeSize(node: LevelNode): Dp = if (node.status == NodeStatus.CURRENT) CurrentSize else NodeSize

private data class MiniRoadSegment(val path: Path, val walked: Boolean, val start: Float, val length: Float)

private const val MiniNodes = 5
private val NodeSize = 46.dp
private val CurrentSize = 58.dp
/** Узлы через один ниже — дорога волной, как на большой карте. */
private val Drop = 18.dp
private val AreaHeight = 106.dp
private val StrokeBorder = 4.dp
