package ru.finney.pet.ui.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/** Пять узлов вокруг текущего: два позади, два впереди; у края пути окно сдвигается. */
internal fun miniPathWindow(nodes: List<LevelNode>): List<LevelNode> {
    val current = nodes.indexOfFirst { it.status == NodeStatus.CURRENT }.coerceAtLeast(0)
    val start = (current - 2).coerceIn(0, (nodes.size - MiniNodes).coerceAtLeast(0))
    return nodes.drop(start).take(MiniNodes)
}

@Composable
internal fun MiniPath(nodes: List<LevelNode>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (nodes.isEmpty()) return
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(androidx.compose.ui.graphics.Color.White)
            .border(StrokeBorder, FinneyInk, shape)
            .clickable(onClickLabel = "Открыть «Мой путь»", onClick = onOpen)
            .padding(top = 14.dp, bottom = 34.dp),
    ) {
        // Поля по бокам: у крайнего узла подарок и «ты здесь» шире своего столбца и иначе срезались рамкой.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(AreaHeight)) {
            val n = nodes.size
            val slot = maxWidth / n
            fun centerX(i: Int): Dp = slot * i + slot / 2
            fun centerY(i: Int): Dp = Drop * (i % 2) + CurrentSize / 2
            // Дорога за узлами: пройденная — сплошная, впереди — бледный пунктир.
            Canvas(Modifier.fillMaxSize()) {
                val pts = nodes.indices.map { Offset(centerX(it).toPx(), centerY(it).toPx()) }
                val lead = Offset(-12.dp.toPx(), pts.first().y + Drop.toPx())
                val tail = Offset(size.width + 12.dp.toPx(), pts.last().y + Drop.toPx() * (if (n % 2 == 0) -1 else 1))
                val chain = buildList {
                    if (nodes.first().level > 1) add(Triple(lead, pts.first(), true))
                    for (i in 0 until n - 1) add(Triple(pts[i], pts[i + 1], nodes[i + 1].status != NodeStatus.FUTURE))
                    add(Triple(pts.last(), tail, false))
                }
                chain.forEach { (a, b, walked) ->
                    val mid = (a.x + b.x) / 2
                    val seg = Path().apply { moveTo(a.x, a.y); cubicTo(mid, a.y, mid, b.y, b.x, b.y) }
                    if (walked) {
                        drawPath(seg, FinneyInk, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                    } else {
                        drawPath(seg, FinneyInk.copy(alpha = 0.55f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 7.dp.toPx()))))
                    }
                }
            }
            nodes.forEachIndexed { i, node ->
                val size = nodeSize(node)
                Column(
                    Modifier
                        .width(slot)
                        .offset(x = slot * i, y = centerY(i) - size / 2)
                        .clearAndSetSemantics { contentDescription = spoken(node) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    MiniNode(node, size)
                    when (node.status) {
                        NodeStatus.DONE -> Row(horizontalArrangement = Arrangement.spacedBy((-3).dp)) {
                            repeat(3) { Star(filled = it < node.stars, modifier = Modifier.size(17.dp)) }
                        }
                        NodeStatus.CURRENT -> Text("ты здесь", style = MaterialTheme.typography.bodyLarge, color = SpeechLeadColor,
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
    Box(Modifier.size(size)) {
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
    Box(
        modifier
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

private const val MiniNodes = 5
private val NodeSize = 46.dp
private val CurrentSize = 58.dp
/** Узлы через один ниже — дорога волной, как на большой карте. */
private val Drop = 18.dp
private val AreaHeight = 106.dp
private val StrokeBorder = 4.dp
