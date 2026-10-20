package ru.finney.pet.ui.progress

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.res.painterResource
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
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyYellow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

/**
 * Пейзаж у дороги: мягкий холм с той стороны, где узла нет, и пара точек. Рисуется
 * в строке пути, поэтому едет вместе с картой. Цвета палитры, но бледные: фон не должен
 * спорить с узлами.
 */
private fun DrawScope.scenery(level: Int, x: Float, y: Float) {
    // Цвета холмов — из концепта карты: песочный, жёлтый, сиреневый, зелёный, все бледные.
    val tint = listOf(Color(0xFFFBE2BC), FinneyYellow.copy(alpha = 0.45f), Color(0xFFE6E2F7), FinneyGreen.copy(alpha = 0.28f))[level % 4]
    val r = 70.dp.toPx()
    drawOval(tint, topLeft = Offset(x - r * 1.2f, y - r * 0.6f), size = androidx.compose.ui.geometry.Size(r * 2.4f, r * 1.8f))
    val dot = 2.6.dp.toPx()
    listOf(-0.6f to -0.9f, 0.5f to -1.1f, 0.1f to -0.5f, -0.2f to 1.0f).forEach { (dx, dy) ->
        drawCircle(FinneyPeach.copy(alpha = 0.35f), radius = dot, center = Offset(x + r * dx, y + r * dy))
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
                val half = NodeSize.toPx() / 2
                fun x(b: Float) = (size.width - NodeSize.toPx()) * (b + 1) / 2 + half
                scenery(node.level, x(-bias(node.level) * 0.9f), half + StepHeight.toPx() / 2)
                next ?: return@drawBehind
                val reached = next.status != NodeStatus.FUTURE
                // Дорога, а не линия: S-кривая с вертикальными касательными у узлов,
                // пройденная — жёлтая, будущая — кремовая с пунктиром посередине.
                val a = Offset(x(bias(node.level)), half)
                val b = Offset(x(bias(next.level)), half + StepHeight.toPx())
                val bend = (b.y - a.y) * 0.6f
                val road = Path().apply { moveTo(a.x, a.y); cubicTo(a.x, a.y + bend, b.x, b.y - bend, b.x, b.y) }
                // Как в концепте: светлая дорога в синей обводке; пройденная — с жёлтой
                // полосой посередине, будущая — в бледной обводке и с пунктиром.
                val edge = 22.dp.toPx()
                val inner = 14.dp.toPx()
                drawPath(road, if (reached) FinneyInk else FinneyInk.copy(alpha = 0.3f), style = Stroke(edge, cap = StrokeCap.Round))
                drawPath(road, RoadLight, style = Stroke(inner, cap = StrokeCap.Round))
                if (reached) {
                    drawPath(road, FinneyYellow, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                } else {
                    drawPath(road, FinneyInk.copy(alpha = 0.45f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 9.dp.toPx()))))
                }
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
            // Награда цели, которая здесь открывается, — у края узла: на карте видно, к чему идёшь.
            Box {
                NodeCircle(node, selected, onClick)
                node.goalArt.firstOrNull()?.let {
                    Image(painterResource(it), contentDescription = null,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 22.dp, y = (-6).dp).size(36.dp))
                }
            }
            NodeCaption(node)
        }
    }
}

@Composable
private fun NodeCircle(node: LevelNode, selected: Boolean, onClick: () -> Unit) {
    // Выбранный узел — в голубом кольце: где ты на карте и что открыто в окне — разные вещи.
    val ring = if (selected) Modifier.border(StrokeRegular, FinneyBlue, CircleShape).padding(4.dp) else Modifier.padding(4.dp)
    // «✓» пройденного — поверх круга, а не внутри него: круг обрезан по себе (clip нужен
    // нажатию), и галочка в его углу срезалась наполовину (20.10).
    Box(Modifier.size(NodeSize)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .then(ring),
            contentAlignment = Alignment.Center,
        ) {
            when (node.status) {
                NodeStatus.CURRENT -> LevelBadge(node.level, size = NodeSize - 8.dp)
                NodeStatus.DONE -> Box(
                    modifier = Modifier
                        .size(NodeSize - 8.dp)
                        .clip(CircleShape)
                        .background(FinneyGreen)
                        .border(StrokeRegular, FinneyInk, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { OutlinedText(node.level.toString(), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center) }
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
        if (node.status == NodeStatus.DONE) {
            CheckBadge(Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp), size = 24.dp)
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

/** Светлое полотно дороги — теплее кремового фона, чтобы дорога читалась на нём. */
private val RoadLight = androidx.compose.ui.graphics.Color(0xFFFFF7E9)
private val NodeSize = 64.dp
private val StepHeight = 104.dp
