package ru.finney.pet.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.finney.pet.ui.components.CheckBadge
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.FinneyQuietButton
import ru.finney.pet.ui.components.LevelBadge
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.FinneyBlue
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyInkFaded
import ru.finney.pet.ui.theme.StrokeBold
import ru.finney.pet.ui.theme.StrokeRegular

/**
 * «Мой путь»: все уровни змейкой сверху вниз, открывается на текущем. Нажатие на узел
 * только показывает, что на уровне, — ни запустить, ни пропустить уровень отсюда нельзя.
 * Узлы различаются не одним цветом: у пройденного «✓» и звёзды, у текущего —
 * «Ты здесь», у будущего — бледный номер.
 */
@Composable
internal fun LevelRoadmap(
    nodes: List<LevelNode>,
    selected: Int?,
    onSelect: (level: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentIndex = nodes.indexOfFirst { it.status == NodeStatus.CURRENT }.coerceAtLeast(0)
    // Текущий — сверху: на 360 dp с крупным шрифтом под шапкой и панелью остаётся
    // места на два узла, и второй должен быть следующим, а не уже пройденным.
    val list = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
    val scope = rememberCoroutineScope()
    val currentVisible by remember {
        derivedStateOf { list.layoutInfo.visibleItemsInfo.any { it.index == currentIndex } }
    }
    val sounds = LocalSounds.current

    Box(modifier) {
        // Снизу запас под кнопку «К текущему уровню»: последний узел не должен прятаться под ней.
        LazyColumn(state = list, contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp)) {
            items(nodes, key = { it.level }) { node ->
                PathStep(
                    node = node,
                    next = nodes.getOrNull(node.level),
                    selected = node.level == selected,
                    onClick = { sounds.play(Sfx.Tap); onSelect(node.level) },
                )
            }
        }
        if (!currentVisible) {
            FinneyQuietButton(
                text = "К текущему уровню",
                onClick = { scope.launch { list.animateScrollToItem(currentIndex) } },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            )
        }
    }
}

/** Доля ширины, на которой стоит узел: змейка из четырёх положений. */
private fun bias(level: Int): Float = listOf(0f, 0.55f, 0f, -0.55f)[(level - 1) % 4]

/**
 * Строка пути: узел и отрезок до следующего. Отрезок рисуется за узлом и тянется
 * в строку ниже — следующий узел ложится поверх его конца. Путь к будущему — пунктиром.
 */
@Composable
private fun PathStep(node: LevelNode, next: LevelNode?, selected: Boolean, onClick: () -> Unit) {
    val nodeAlign = BiasAlignment(bias(node.level), -1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(StepHeight)
            .drawBehind {
                next ?: return@drawBehind
                val half = NodeSize.toPx() / 2
                fun x(b: Float) = (size.width - NodeSize.toPx()) * (b + 1) / 2 + half
                val reached = next.status != NodeStatus.FUTURE
                drawLine(
                    color = if (reached) FinneyInk else FinneyInkFaded,
                    start = Offset(x(bias(node.level)), half),
                    end = Offset(x(bias(next.level)), half + StepHeight.toPx()),
                    strokeWidth = StrokeBold.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = if (reached) null else PathEffect.dashPathEffect(floatArrayOf(18f, 16f)),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .align(nodeAlign)
                .clearAndSetSemantics {
                    contentDescription = nodeDescription(node)
                    role = Role.Button
                    this.selected = selected
                    onClick { onClick(); true }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NodeCircle(node, selected, onClick)
            NodeCaption(node)
        }
    }
}

@Composable
private fun NodeCircle(node: LevelNode, selected: Boolean, onClick: () -> Unit) {
    // Выбранный узел — в голубом кольце: где ты на карте и что открыто в окне — разные вещи.
    val ring = if (selected) Modifier.border(StrokeRegular, FinneyBlue, CircleShape).padding(4.dp) else Modifier.padding(4.dp)
    Box(
        modifier = Modifier
            .size(NodeSize)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .then(ring),
        contentAlignment = Alignment.Center,
    ) {
        when (node.status) {
            NodeStatus.CURRENT -> LevelBadge(node.level, size = NodeSize - 8.dp)
            NodeStatus.DONE -> Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(NodeSize - 8.dp)
                        .clip(CircleShape)
                        .background(FinneyGreen)
                        .border(StrokeRegular, FinneyInk, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { OutlinedText(node.level.toString(), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center) }
                CheckBadge(Modifier.align(Alignment.BottomEnd), size = 22.dp)
            }
            NodeStatus.FUTURE -> Box(
                modifier = Modifier
                    .size(NodeSize - 8.dp)
                    .clip(CircleShape)
                    .background(FinneyCream)
                    .border(StrokeRegular, FinneyInkFaded, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(node.level.toString(), style = MaterialTheme.typography.titleLarge, color = FinneyInkFaded) }
        }
    }
}

/** Подпись под узлом: звёзды у пройденного, «Ты здесь» у текущего. У будущего — ничего. */
@Composable
private fun NodeCaption(node: LevelNode) {
    when (node.status) {
        NodeStatus.DONE -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(3) { i ->
                FinneyIcon(FinneyIcons.Star, size = 16.dp, tint = if (i < node.stars) FinneyInk else FinneyInkFaded.copy(alpha = 0.4f))
            }
        }
        NodeStatus.CURRENT -> Text(
            "Ты здесь",
            style = MaterialTheme.typography.titleMedium,
            color = FinneyInk,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(FinneyCream)
                .padding(horizontal = 8.dp),
        )
        NodeStatus.FUTURE -> Unit
    }
}

private fun nodeDescription(node: LevelNode): String = when (node.status) {
    NodeStatus.DONE -> "Уровень ${node.level}, пройден, звёзд ${node.stars} из 3"
    NodeStatus.CURRENT -> "Уровень ${node.level}, ты здесь"
    NodeStatus.FUTURE -> "Уровень ${node.level}, впереди"
}

private val NodeSize = 64.dp
private val StepHeight = 104.dp
