package ru.finney.pet.ui.progress

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
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
import kotlin.math.PI
import kotlin.math.sin
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
        // Дорога и холмы — одним слоем под списком, а не в каждой строке: строка, ушедшая
        // за край, выпадает из списка и уносила с собой свой холм и отрезок дороги, хотя
        // они ещё видны в соседней (плейтест 20.10). Строки одной высоты, поэтому где какой
        // узел, считается по прокрутке без замеров.
        Box(
            Modifier
                .matchParentSize()
                // Карта мягко тает у краёв, а не обрезается по линейке под вкладками.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fade = EdgeFade.toPx()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            fade / size.height to Color.Black,
                            1f - fade / size.height to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val step = StepHeight.toPx()
                val scrolled = list.firstVisibleItemIndex * step + list.firstVisibleItemScrollOffset
                val top = ListTop.toPx() - scrolled
                val half = NodeSize.toPx() / 2
                fun center(index: Int): Offset {
                    val b = bias(index + 1)
                    return Offset((size.width - NodeSize.toPx()) * (b + 1) / 2 + half, top + index * step + half)
                }
                val first = (scrolled / step).toInt() - 2
                val last = first + (size.height / step).toInt() + 5
                val range = first.coerceAtLeast(0)..last.coerceAtMost(nodes.lastIndex)
                range.forEach { i -> scenery(i + 1, center(i).copy(x = sceneryX(i + 1)), step / 2) }
                road(nodes.size, range, ::center, currentY = if (nodes.any { it.status == NodeStatus.CURRENT }) center(currentIndex).y else Float.MAX_VALUE)
            }
            // Снизу запас под кнопку «К текущему уровню»: последний узел не должен прятаться под ней.
            LazyColumn(state = list, contentPadding = PaddingValues(top = ListTop, bottom = 88.dp)) {
                items(nodes, key = { it.level }) { node ->
                    PathStep(
                        node = node,
                        selected = node.level == selected,
                        onClick = { sounds.play(Sfx.Tap); onSelect(node.level) },
                    )
                }
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
 * Пейзаж у дороги: мягкий холм с той стороны, где узла нет, и пара точек. Цвета палитры,
 * но бледные: фон не должен спорить с узлами. Холм к краю сходит на нет — соседние холмы
 * разного цвета перетекают друг в друга, а не встречаются резкой кромкой.
 */
private fun DrawScope.scenery(level: Int, at: Offset, drop: Float) {
    // Цвета холмов — из концепта карты: песочный, жёлтый, сиреневый, зелёный, все бледные.
    val tint = listOf(Color(0xFFFBE2BC), FinneyYellow.copy(alpha = 0.45f), Color(0xFFE6E2F7), FinneyGreen.copy(alpha = 0.28f))[level % 4]
    val r = 70.dp.toPx()
    val c = Offset(at.x, at.y + drop)
    scale(scaleX = 1.3f, scaleY = 1f, pivot = c) {
        drawCircle(
            Brush.radialGradient(0f to tint, 0.6f to tint, 1f to tint.copy(alpha = 0f), center = c, radius = r * 1.1f),
            radius = r * 1.1f,
            center = c,
        )
    }
    val dot = 2.6.dp.toPx()
    listOf(-0.6f to -0.9f, 0.5f to -1.1f, 0.1f to -0.5f, -0.2f to 1.0f).forEach { (dx, dy) ->
        drawCircle(FinneyPeach.copy(alpha = 0.35f), radius = dot, center = Offset(c.x + r * dx, c.y + r * dy))
    }
}

/** Где холм уровня [level]: напротив узла. */
private fun DrawScope.sceneryX(level: Int): Float {
    val node = NodeSize.toPx()
    return (size.width - node) * (-bias(level) * 0.9f + 1) / 2 + node / 2
}

/**
 * Дорога через узлы [range] одной волной. Узлы змейки ([bias]: 0, 0.55, 0, −0.55) — это
 * точки синусоиды, поэтому дорога и есть синусоида через них: ни изломов в узлах,
 * ни прямых участков между ними. Раньше каждый отрезок был своей S-кривой с вертикалью
 * в узлах, и змейка шла ступеньками.
 *
 * Пройденная часть — в синей обводке с жёлтой полосой, будущая — в бледной обводке
 * с пунктиром. Смена идёт плавно у текущего узла ([currentY]), без резкого стыка.
 */
private fun DrawScope.road(count: Int, range: IntRange, center: (Int) -> Offset, currentY: Float) {
    if (range.isEmpty()) return
    // Хвост на один узел за край — иначе у края видимой части дорога обрывалась бы.
    val from = (range.first - 1).coerceAtLeast(0)
    val to = (range.last + 1).coerceAtMost(count - 1)
    if (to <= from) return
    val start = center(0)
    val step = center(1).y - start.y
    val mid = size.width / 2
    val amplitude = center(1).x - mid
    val path = Path()
    val y0 = center(from).y
    val y1 = center(to).y
    val dy = 3.dp.toPx()
    var y = y0
    path.moveTo(center(from).x, y0)
    while (y < y1) {
        y = (y + dy).coerceAtMost(y1)
        val t = (y - start.y) / step
        path.lineTo(mid + amplitude * sin(t * PI.toFloat() / 2), y)
    }
    val blend = RoadBlend.toPx()
    fun passed(color: Color) = Brush.verticalGradient(
        0f to color, 1f to color.copy(alpha = 0f),
        startY = currentY - blend, endY = currentY + blend,
    )
    fun ahead(color: Color) = Brush.verticalGradient(
        0f to color.copy(alpha = 0f), 1f to color,
        startY = currentY - blend, endY = currentY + blend,
    )
    // Как в концепте: светлая дорога в синей обводке; пройденная — с жёлтой
    // полосой посередине, будущая — в бледной обводке и с пунктиром.
    drawPath(path, FinneyInk.copy(alpha = 0.3f), style = Stroke(22.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, passed(FinneyInk), style = Stroke(22.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, RoadLight, style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, ahead(FinneyInk.copy(alpha = 0.45f)), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 9.dp.toPx()))))
    drawPath(path, passed(FinneyYellow), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
}

/** Доля ширины, на которой стоит узел: змейка из четырёх положений. */
private fun bias(level: Int): Float = listOf(0f, 0.55f, 0f, -0.55f)[(level - 1) % 4]

/** Строка пути: узел и подпись. Дорогу под ним рисует [LevelRoadmap] одним слоем. */
@Composable
private fun PathStep(node: LevelNode, selected: Boolean, onClick: () -> Unit) {
    val nodeAlign = BiasAlignment(bias(node.level), -1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(StepHeight),
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

/** Отступ списка сверху: по нему слой дороги находит первый узел. */
private val ListTop = 16.dp

/** На сколько вверх и вниз от текущего узла пройденная дорога перетекает в будущую. */
private val RoadBlend = 36.dp

/** Высота, на которой карта тает у верхнего и нижнего края. */
private val EdgeFade = 28.dp
