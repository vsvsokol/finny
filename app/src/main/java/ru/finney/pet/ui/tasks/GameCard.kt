package ru.finney.pet.ui.tasks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.finney.pet.domain.model.BasketTask
import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.ChangeTask
import ru.finney.pet.domain.model.ChoresTask
import ru.finney.pet.domain.model.GoalRaceTask
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.model.ReceiptTask
import ru.finney.pet.domain.model.ReserveTask
import ru.finney.pet.domain.model.StandTask
import ru.finney.pet.domain.model.TaskDefinition
import ru.finney.pet.ui.components.Coin
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.components.categoryIcon
import ru.finney.pet.ui.tasks.games.PillShade
import ru.finney.pet.ui.tasks.games.ScenePet
import ru.finney.pet.ui.tasks.games.insetShade
import ru.finney.pet.ui.tasks.games.plural
import ru.finney.pet.ui.theme.FinneyBlue
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyPink
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.StrokeRegular

// Части карточки перед игрой по референсу «Карточки мини-игр» (макет «вариант A»):
// питомец на краю окна говорит роль, ниже окна «Сделай» и «Получишь». Объём — тенью
// по низу внутри рамки у пузыря, кружков, плашек и окон, как у кнопок: карточка
// выглядит частью игры, а не справкой. Размеры — из референса для экрана 360 dp.

/** Цвет флажка на окне «Сделай» — свой у каждой игры. Флажок ничего не значит, он только отличает игры. */
internal fun cardFlag(task: TaskDefinition): Color = when (task) {
    is BasketTask -> Color(0xFF67C7B5)
    is GoalRaceTask -> Color(0xFF7B6FE0)
    is ReserveTask -> FinneyBlue
    is StandTask -> Color(0xFFF2C94C)
    is ChangeTask -> FinneyPeach
    is ReceiptTask -> Color(0xFF5BB8E8)
    is ChoresTask -> FinneyPink
    else -> FinneyPink
}

/**
 * Текст карточки: кегль основного, но строка 24 sp, а не 28. С обычной строкой
 * длинные карточки («Дорога к цели», «Подработка») не влезали в экран 360 dp.
 * Обводки у этого текста нет, верх букв не срезается.
 */
internal val cardText: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.titleMedium.copy(lineHeight = 24.sp)

/** Выделение «Ты — …» в реплике: тёмный коралловый — светлый персиковый на белом не читается. */
private val RoleLeadColor = Color(0xFFE8694A)

/** Тень пузыря по низу — светло-сиреневая, чуть светлее, чем у плашек. */
private val BubbleShade = Color(0xFFF1ECFF)

/** Тень питомца — его же силуэт, сдвинутый вниз, полупрозрачным ink. */
private val PetShadow = FinneyInk.copy(alpha = 0.15f)

/**
 * Питомец слева и его реплика с ролью. Питомец — голова с реакциями, как в самих
 * играх, размером [petSize], с тенью-силуэтом под собой. Хвостик пузыря — к нему.
 */
@Composable
internal fun CardHost(character: PetCharacter, help: GameHelp, petSize: Dp, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // У головы по бокам поле под уши и шляпу: питомец занимает в ряду меньше своей ширины,
        // и хвостик пузыря достаёт до него. Пузырь при этом растёт до правого края.
        ScenePet(character, petSize, Modifier.offset(x = (-4).dp).width(petSize).overlapEnd(petSize * 0.12f).silhouetteShadow(PetShadow, 4.dp))
        RoleBubble(help, Modifier.weight(1f))
    }
}

/** Занять в ряду на [overlap] меньше ширины: следующий элемент заходит на правое поле. */
private fun Modifier.overlapEnd(overlap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout((placeable.width - overlap.roundToPx()).coerceAtLeast(0), placeable.height) { placeable.place(0, 0) }
}

/**
 * Тень по силуэту: то же содержимое, залитое [color] и сдвинутое на [dy] вниз, —
 * как `drop-shadow` в референсе. Прямоугольная тень под головой с ушами смотрелась бы чужой.
 */
@Composable
private fun Modifier.silhouetteShadow(color: Color, dy: Dp): Modifier {
    val body = rememberGraphicsLayer()
    val shadow = rememberGraphicsLayer()
    return drawWithContent {
        body.record { this@drawWithContent.drawContent() }
        shadow.colorFilter = ColorFilter.tint(color, BlendMode.SrcIn)
        shadow.record { drawLayer(body) }
        translate(top = dy.toPx()) { drawLayer(shadow) }
        drawLayer(body)
    }
}

@Composable
private fun RoleBubble(help: GameHelp, modifier: Modifier = Modifier) {
    val tail = 16.dp
    val shape = remember { SideTailBubble(22.dp, tail, 12.5.dp) }
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = RoleLeadColor)) { append(help.lead) }
        append(help.role.removePrefix(help.lead))
    }
    Text(
        text,
        style = cardText,
        color = FinneyInk,
        modifier = modifier
            .clip(shape)
            .background(Color.White)
            .insetShade(shape, BubbleShade, 6.dp)
            .border(StrokeRegular, FinneyInk, shape)
            .padding(start = tail + 14.dp, end = 14.dp, top = 10.dp, bottom = 13.dp),
    )
}

/**
 * Пузырь с хвостиком слева посередине высоты. Одна фигура — тело и хвостик
 * объединены, поэтому рамка обходит их общим контуром, без шва у основания хвостика.
 */
private class SideTailBubble(private val radius: Dp, private val tail: Dp, private val halfBase: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        val t = with(density) { tail.toPx() }
        val h = with(density) { halfBase.toPx() }
        val mid = size.height / 2
        val body = Path().apply { addRoundRect(RoundRect(t, 0f, size.width, size.height, CornerRadius(r))) }
        // Основание хвостика заходит внутрь тела: так объединение не оставляет щели.
        val tip = Path().apply {
            moveTo(t + r / 2, mid - h)
            lineTo(0f, mid)
            lineTo(t + r / 2, mid + h)
            close()
        }
        return Outline.Generic(Path().apply { op(body, tip, PathOperation.Union) })
    }
}

/**
 * Шаги «Сделай» — каждое предложение своей строкой и со своим номером.
 * «Нужное» и «Хочется» в тексте — плашками со значками категорий, как на корзинах
 * в самой игре: ребёнок узнаёт корзину ещё до начала.
 */
@Composable
internal fun CardSteps(intro: String) {
    val steps = introSentences(intro)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        steps.forEachIndexed { i, sentence ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Номер — только когда шагов больше одного: одинокая «1» ничего не считает.
                if (steps.size > 1) StepNumber(i + 1)
                StepText(sentence, Modifier.weight(1f).padding(top = 3.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepText(sentence: String, modifier: Modifier = Modifier) {
    val style = cardText
    val parts = stepParts(sentence)
    if (parts.none { it is StepPart.Chip }) {
        Text(sentence, style = style, color = FinneyInk, modifier = modifier)
        return
    }
    FlowRow(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = sentence },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        parts.forEach { part ->
            when (part) {
                is StepPart.Word -> Text(part.text, style = style, color = FinneyInk,
                    modifier = Modifier.align(Alignment.CenterVertically).clearAndSetSemantics {})
                is StepPart.Chip -> Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.CenterVertically).clearAndSetSemantics {}) {
                    CategoryChip(part.category)
                    if (part.suffix.isNotEmpty()) Text(part.suffix, style = style, color = FinneyInk)
                }
            }
        }
    }
}

internal sealed interface StepPart {
    data class Word(val text: String) : StepPart

    /** Плашка категории и знаки препинания сразу за ней — чтобы точка не уехала на новую строку. */
    data class Chip(val category: Category, val suffix: String) : StepPart
}

/** Предложение — слова и плашки на месте «Нужное» и «Хочется» в кавычках. */
internal fun stepParts(sentence: String): List<StepPart> {
    val parts = mutableListOf<StepPart>()
    var rest = sentence
    while (rest.isNotEmpty()) {
        val m = ChipWord.find(rest)
        val before = if (m == null) rest else rest.substring(0, m.range.first)
        before.split(' ').filter { it.isNotEmpty() }.forEach { parts += StepPart.Word(it) }
        if (m == null) break
        val after = rest.substring(m.range.last + 1)
        val suffix = after.takeWhile { it != ' ' }
        val category = if (m.groupValues[1] == "Нужное") Category.NEEDS else Category.WANTS
        parts += StepPart.Chip(category, suffix)
        rest = after.removePrefix(suffix)
    }
    return parts
}

private val ChipWord = Regex("«(Нужное|Хочется)»")

@Composable
private fun CategoryChip(category: Category) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(shape)
            .background(Color.White)
            .insetShade(shape, PillShade, 3.dp)
            .border(StrokeRegular, FinneyInk, shape)
            .padding(start = 6.dp, end = 12.dp, top = 3.dp, bottom = 4.dp),
    ) {
        FinneyIcon(categoryIcon(category), size = 20.dp)
        Text(if (category == Category.NEEDS) "Нужное" else "Хочется", style = cardText.copy(lineHeight = 22.sp), color = FinneyInk)
    }
}

/** Номер шага в белом кружке с тенью по низу — как плашки, но мельче. */
@Composable
private fun StepNumber(n: Int) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.White)
            .insetShade(CircleShape, PillShade, 4.dp)
            .border(StrokeRegular, FinneyInk, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text("$n", style = cardText, color = FinneyInk, modifier = Modifier.padding(bottom = 2.dp))
    }
}

/**
 * Окно «Получишь»: награда плашкой с монеткой, утешительная — маленькой розовой,
 * а уже полученная — галочкой. Под пунктиром — [note], зачем эти монеты.
 * Для чтения с экрана всё окно — одна фраза [rewardLine].
 */
@Composable
internal fun CardReward(state: TaskUiState.Ready, note: String?) {
    val r = state.reward
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull(rewardLine(state), note).joinToString(" ")
        },
    ) {
        Column(Modifier.clearAndSetSemantics {}) {
            if (state.completed) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DoneMark()
                    Text("Награда уже в кошельке. Сыграй ещё — для тренировки.",
                        style = cardText, color = FinneyInk, modifier = Modifier.weight(1f))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RewardPill(r.success)
                    Text("${coinsWord(r.success)} в кошелёк Финни.", style = cardText, color = FinneyInk, modifier = Modifier.weight(1f))
                }
                if (state.firstTry && r.fail > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Не выйдет — всё равно", style = cardText, color = FinneyInk)
                        ConsolationPill(r.fail)
                    }
                }
            }
            if (note != null) {
                DashedLine(Modifier.padding(top = 9.dp, bottom = 8.dp))
                Text(note, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp), color = FinneyInk.copy(alpha = 0.8f))
            }
        }
    }
}

/** Слово к числу монет без самого числа: число уже стоит на плашке. */
private fun coinsWord(n: Int): String = plural(n, "монета", "монеты", "монет").substringAfter(' ')

/**
 * «+15» с монеткой — белая плашка 46 dp с тенью, как кошелёк в углу. Монетка 34 dp
 * стоит внутри рамки с зазором 3 dp со всех сторон: вплотную к рамке она с ней сливалась.
 */
@Composable
private fun RewardPill(amount: Int) {
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .height(46.dp)
            .clip(shape)
            .background(Color.White)
            .insetShade(shape, PillShade, 5.dp)
            .border(StrokeRegular, FinneyInk, shape)
            .padding(start = 14.dp, end = 6.dp),
    ) {
        OutlinedText("+$amount", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 31.sp, lineHeight = 31.sp))
        Coin(size = 34.dp)
    }
}

/** Утешительная награда — мельче и спокойнее основной: розовая плашка 32 dp с белым числом. */
@Composable
private fun ConsolationPill(amount: Int) {
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(Color(0xFFFBC3B0))
            .border(StrokeRegular, FinneyInk, shape)
            .padding(horizontal = 11.dp),
    ) {
        OutlinedText("+$amount", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 23.sp, lineHeight = 23.sp), fill = Color.White)
    }
}

/** Награда уже получена: зелёный кружок с галочкой, нарисованной линией, — буква «✓» в шрифте выходила рваной. */
@Composable
private fun DoneMark() {
    Canvas(Modifier.size(40.dp).clearAndSetSemantics {}) {
        val s = size.minDimension
        val ring = 3.dp.toPx()
        drawCircle(FinneyInk, s / 2)
        drawCircle(FinneyGreen, s / 2 - ring)
        val check = Path().apply {
            moveTo(s * 0.29f, s * 0.52f)
            lineTo(s * 0.43f, s * 0.66f)
            lineTo(s * 0.72f, s * 0.35f)
        }
        drawPath(check, FinneyInk, style = Stroke(width = s * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun DashedLine(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(3.dp)) {
        drawLine(
            FinneyInk.copy(alpha = 0.25f),
            Offset(0f, size.height / 2),
            Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
        )
    }
}

/**
 * Плашка под названием игры уровня: номер уровня в жёлтом кружке, вокруг — розовая дуга,
 * сколько условий уровня уже выполнено ([progress] 0..1, как дуга у значка уровня на главном).
 */
@Composable
internal fun LevelPill(level: Int, progress: Float, text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(shape)
            .background(Color.White)
            .insetShade(shape, PillShade, 4.dp)
            .border(StrokeRegular, FinneyInk, shape)
            .padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Уровень $level. $text" },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.dp).clearAndSetSemantics {}) {
            Canvas(Modifier.size(30.dp)) {
                val s = size.minDimension
                val line = 3.dp.toPx()
                drawCircle(FinneyInk, s / 2)
                drawCircle(Color.White, s / 2 - line)
                drawArc(
                    FinneyPink,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = true,
                    topLeft = Offset(line, line),
                    size = Size(s - line * 2, s - line * 2),
                )
                drawCircle(FinneyInk, 9.dp.toPx())
                drawCircle(FinneyYellow, 7.dp.toPx())
            }
            Text("$level", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 13.sp), color = FinneyInk)
        }
        Text(text, style = cardText, color = FinneyInk, modifier = Modifier.clearAndSetSemantics {})
    }
}
