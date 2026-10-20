package ru.finney.pet.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.finney.pet.R
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.period.Star
import ru.finney.pet.ui.progress.LevelNode
import ru.finney.pet.ui.progress.NodeStatus
import ru.finney.pet.ui.progress.UnlockChip
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyInkFaded
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.RadiusCard
import ru.finney.pet.ui.theme.StrokeRegular

// Мини-дорожка в панели уровня (разбор 20.10). Карту «Мой путь» открывали через кнопку
// «Итоги и история», на которую жать не хотелось. Теперь кусок карты виден сразу: что
// пройдено, где ты и что откроется дальше — картинкой. Нажатие ведёт на всю карту.

/** Узлы вокруг текущего: один пройденный позади и до трёх впереди. */
internal fun miniPathWindow(nodes: List<LevelNode>): List<LevelNode> {
    val current = nodes.indexOfFirst { it.status == NodeStatus.CURRENT }.coerceAtLeast(0)
    val start = (current - 1).coerceIn(0, (nodes.size - MiniNodes).coerceAtLeast(0))
    return nodes.drop(start).take(MiniNodes)
}

@Composable
internal fun MiniPath(nodes: List<LevelNode>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (nodes.isEmpty()) return
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusCard))
            .clickable(onClickLabel = "Открыть весь путь", onClick = onOpen)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Мой путь", style = MaterialTheme.typography.titleMedium, color = FinneyInk, modifier = Modifier.weight(1f))
            Text("Весь путь ›", style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
        }
        Box(Modifier.fillMaxWidth().height(RowHeight)) {
            // Дорога за узлами: пройденная часть — сплошная, будущая — пунктиром.
            Canvas(Modifier.matchParentSize()) {
                val n = nodes.size
                val centers = nodes.indices.map { i -> Offset(size.width * (i + 0.5f) / n, (NodeTop + NodeSize / 2 + lift(i)).toPx()) }
                val road = 9.dp.toPx()
                for (i in 0 until n - 1) {
                    val a = centers[i]
                    val b = centers[i + 1]
                    val mid = (a.x + b.x) / 2
                    val seg = Path().apply { moveTo(a.x, a.y); cubicTo(mid, a.y, mid, b.y, b.x, b.y) }
                    val walked = nodes[i + 1].status != NodeStatus.FUTURE
                    drawPath(seg, FinneyInk, style = Stroke(road + StrokeRegular.toPx() * 2, cap = StrokeCap.Round))
                    if (walked) {
                        drawPath(seg, FinneyYellow, style = Stroke(road, cap = StrokeCap.Round))
                    } else {
                        drawPath(seg, FinneyCream, style = Stroke(road, cap = StrokeCap.Round))
                        drawPath(seg, FinneyInkFaded, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx()))))
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                nodes.forEachIndexed { i, node ->
                    Column(
                        Modifier.weight(1f).offset(y = NodeTop + lift(i) - (nodeSize(node) - NodeSize) / 2),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        MiniNode(node)
                        when (node.status) {
                            NodeStatus.DONE -> Row(horizontalArrangement = Arrangement.spacedBy((-2).dp)) {
                                repeat(3) { Star(filled = it < node.stars, modifier = Modifier.size(18.dp)) }
                            }
                            else -> node.chips.firstOrNull()?.let { ChipArt(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniNode(node: LevelNode) {
    val size = nodeSize(node)
    val fill = when (node.status) {
        NodeStatus.DONE -> FinneyYellow
        NodeStatus.CURRENT -> FinneyPeach
        NodeStatus.FUTURE -> FinneyCream
    }
    val spoken = when (node.status) {
        NodeStatus.DONE -> "Уровень ${node.level} пройден, звёзд: ${node.stars}"
        NodeStatus.CURRENT -> "Уровень ${node.level}, ты здесь"
        NodeStatus.FUTURE -> "Уровень ${node.level} впереди"
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .border(StrokeRegular, FinneyInk, CircleShape)
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${node.level}",
            style = if (node.status == NodeStatus.CURRENT) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = if (node.status == NodeStatus.FUTURE) FinneyInkFaded else FinneyInk,
        )
    }
}

/** Что откроется — картинкой: вещь или цель своим рисунком, игра значком игр, звание кубком. */
@Composable
private fun ChipArt(chip: UnlockChip) {
    val art = chip.art ?: if (!chip.label.startsWith("Звание")) R.drawable.ic_level_game else null
    if (art != null) {
        Image(painterResource(art), contentDescription = chip.label, modifier = Modifier.size(30.dp))
    } else {
        FinneyIcon(chip.icon ?: FinneyIcons.Trophy, size = 30.dp)
    }
}

private fun nodeSize(node: LevelNode): Dp = if (node.status == NodeStatus.CURRENT) CurrentSize else NodeSize

/** Узлы через один чуть ниже: дорога волной, а не линейкой. */
private fun lift(i: Int): Dp = if (i % 2 == 0) 0.dp else 14.dp

private const val MiniNodes = 4
private val NodeSize = 44.dp
private val CurrentSize = 52.dp
private val NodeTop = 6.dp
private val RowHeight = 112.dp
