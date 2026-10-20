package ru.finney.pet.ui.tasks.games

import androidx.compose.foundation.background
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import ru.finney.pet.ui.components.fadingScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.model.ReceiptItem
import ru.finney.pet.domain.model.ReceiptTask
import ru.finney.pet.domain.tasks.TaskDetails
import ru.finney.pet.domain.tasks.TaskEngines
import ru.finney.pet.domain.tasks.TaskEvaluation
import ru.finney.pet.domain.tasks.TaskInput
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.SpeechBubble
import ru.finney.pet.ui.components.Tail
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPink
import ru.finney.pet.ui.theme.FinneyYellow

// «Проверь чек»: кассир отдал пакет, чек и сдачу. Сверяешь строки чека с ценниками
// в пакете, а сдачу — с суммой; нажатие отмечает ошибку. В оценку идёт первая
// проверка. Потом кассир подсказывает, что пропущено и что верно, и ребёнок
// поправляет сам. Что считать ошибкой, решает ядро — экран только показывает.

/** Номиналы, которыми кассир выдаёт сдачу. Только для рисунка: сумма та же. */
private val ChangeCoins = listOf(10, 5, 2, 1)

private val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp)

/** Что показать у строки: отмечена ли она и, после проверки, что про неё сказал кассир. */
private enum class Mark { NONE, FLAGGED, MISSED, FINE }

@Composable
internal fun ReceiptGame(task: ReceiptTask, character: PetCharacter, onClose: () -> Unit, onSubmit: (TaskInput) -> Unit) {
    var lines by remember { mutableStateOf(emptySet<String>()) }
    var change by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf<TaskInput.Flags?>(null) }
    var checked by remember { mutableStateOf(false) }
    val sounds = LocalSounds.current
    val cashier = PetCharacter.entries.first { it != character }

    val errors = TaskEngines.receiptErrors(task, lines)
    val changeWrong = errors.changeShort > 0
    // Сдачу от чека с ошибкой ребёнок прав и отметить, и не отметить (changeEither).
    val right = lines == errors.lines && (change == changeWrong || errors.changeEither)
    val solved = checked && right
    val details = (TaskEngines.evaluate(task, TaskInput.Flags(lines, change)) as? TaskEvaluation.Done)?.details as? TaskDetails.Receipt

    fun mark(flagged: Boolean, error: Boolean): Mark = when {
        !checked || solved -> if (flagged) Mark.FLAGGED else Mark.NONE
        flagged && !error -> Mark.FINE
        !flagged && error -> Mark.MISSED
        flagged -> Mark.FLAGGED
        else -> Mark.NONE
    }

    fun edit(block: () -> Unit) {
        sounds.play(Sfx.Tap)
        block()
        checked = false
    }

    GameScene(backdrop = Backdrop.TILL_DESK, onClose = onClose) {
        val density = LocalDensity.current
        val rowTop = with(density) { WindowInsets.systemBars.getTop(this).toDp() } + 8.dp + HudHeight
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenWidth = maxWidth + SceneEdge * 2
            // Прокручивается только чек: кассир, прилавок и кнопка стоят на месте. Раньше
            // уезжал весь экран, и кассир с прилавком съезжали с нарисованного фона.
            Column(Modifier.fillMaxSize().padding(top = HudHeight), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Кассир с репликой стоит за прилавком фона, покупки лежат на прилавке рядом,
                // как товар у «Сдачи», а чек — под прилавком, на сиреневом: так в макете кассы.
                // Отдельным окном «Пакет» покупки не помещались: чек уходил за низ экрана.
                // Прилавок нарисован в единицах макета от верха экрана, поэтому и место
                // над ним считается от верха экрана.
                val unit = designUnit(screenWidth)
                // Реплика сверху, под ней кассир и покупки. Много покупок — два ряда, и
                // место над прилавком растёт: иначе верхний ряд прятался под реплику.
                var bubbleHeight by remember { mutableStateOf(0.dp) }
                var goodsHeight by remember { mutableStateOf(0.dp) }
                val stand = maxOf(unit * DESK_TOP - rowTop, bubbleHeight + 8.dp + goodsHeight, MinStand)
                SideEffect { DeskTop.floatValue = maxOf(DESK_TOP, (rowTop + stand) / unit) }
                DisposableEffect(Unit) { onDispose { DeskTop.floatValue = DESK_TOP } }
                Box(Modifier.fillMaxWidth().height(stand)) {
                    Guest(cashier, CashierSize, Modifier.align(Alignment.BottomStart).width(CashierSize))
                    Bag(
                        task.cart,
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(start = CashierSize - 16.dp)
                            .onSizeChanged { goodsHeight = with(density) { it.height.toDp() } },
                    )
                    SpeechBubble(
                        cashierLine(checked, solved, details),
                        Tail.DOWN,
                        Modifier
                            .align(Alignment.TopStart)
                            .widthIn(max = 250.dp)
                            .onSizeChanged { bubbleHeight = with(density) { it.height.toDp() } },
                        tailAt = 0.3f,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                // Два промежутка колонки по 12 dp уже есть: до прилавка и после него.
                Spacer(Modifier.height((unit * DESK_HEIGHT - 16.dp).coerceAtLeast(0.dp)))
                Column(Modifier.weight(1f).fillMaxWidth().fadingScroll(rememberScrollState())) {
                    ReceiptPaper(
                        task = task,
                        markOf = { id -> mark(id in lines, id in errors.lines) },
                        changeMark = mark(change, changeWrong || (change && errors.changeEither)),
                        onLine = { id -> edit { lines = if (id in lines) lines - id else lines + id } },
                        onChange = { edit { change = !change } },
                    )
                }
                if (solved) {
                    FinneyButton(text = "Готово", onClick = { onSubmit(first ?: TaskInput.Flags(lines, change)) })
                } else {
                    FinneyButton(text = "Проверить", onClick = {
                        if (first == null) first = TaskInput.Flags(lines, change)
                        checked = true
                        sounds.play(if (right) Sfx.Correct else Sfx.Wrong)
                    })
                }
            }
        }
    }
}

/** Кассир за прилавком — крупно, как покупатель у «Сдачи». */
private val CashierSize = 124.dp

/** Меньше места над прилавком не бывает: на низком экране кассир не сжимается, а чек прокручивается. */
private val MinStand = 160.dp

/** Кассир вежливый: ошибся случайно, после проверки подсказывает, а не ругает. */
private fun cashierLine(checked: Boolean, solved: Boolean, details: TaskDetails.Receipt?): String = when {
    !checked || details == null -> "Вот чек и сдача. Где я ошибся — нажми на строку"
    solved && details.refund > 0 -> "Ой, я ошибся! Возвращаю ${details.refund}"
    solved -> "Всё верно, спасибо!"
    details.missed > 0 && details.extra > 0 -> "Где «?» — ошибка, где «✓» — всё верно"
    details.missed > 0 -> "Ошибок больше. Посмотри, где «?»"
    else -> "Где «✓» — всё верно, сними отметку"
}

/**
 * Покупки на прилавке: рисунок, ценник из магазина и сколько штук. Стоят справа
 * от кассира, нижний ряд — на столешнице; много покупок переносятся рядом выше.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bag(cart: List<ReceiptItem>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
        itemVerticalAlignment = Alignment.Bottom,
    ) {
        cart.forEach { item ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${item.label}: ${item.qty} шт. по ${item.price}"
                },
            ) {
                ItemPicture(item, item.label, 52.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PriceTag(item.price.toString())
                    if (item.qty > 1) Text("${item.qty} шт.", style = MaterialTheme.typography.labelLarge, color = FinneyInk)
                }
            }
        }
    }
}

/** Бумажный чек: строки по штуке, внизу итого, сколько дали и сдача. Строки и сдача нажимаются. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReceiptPaper(
    task: ReceiptTask,
    markOf: (String) -> Mark,
    changeMark: Mark,
    onLine: (String) -> Unit,
    onChange: () -> Unit,
) {
    val cart = task.cart.associateBy { it.id }
    val total = TaskEngines.receiptTotal(task)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(3.dp, FinneyInk, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text("ЧЕК", style = Mono, color = FinneyInk, modifier = Modifier.align(Alignment.CenterHorizontally))
        task.lines.forEach { line ->
            val item = cart.getValue(line.item)
            CheckRow(
                mark = markOf(line.id),
                description = "${item.label}, ${line.price}",
                onToggle = { onLine(line.id) },
            ) {
                ItemPicture(item, item.label, 30.dp)
                Text(item.label, style = MaterialTheme.typography.titleMedium, color = FinneyInk, modifier = Modifier.weight(1f))
                Text(line.price.toString(), style = Mono, color = FinneyInk)
            }
        }
        Dashes()
        PlainRow("Итого", total)
        PlainRow("Дали", task.paid)
        CheckRow(mark = changeMark, description = "Сдача, ${task.change}", onToggle = onChange) {
            Text("Сдача", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            ) {
                coinsFor(task.change).forEach { DenominationCoin(it, size = 30.dp) }
            }
            Text(task.change.toString(), style = Mono, color = FinneyInk)
        }
    }
}

/**
 * Строка, которую можно отметить: 48 dp по высоте, отметка — значком и словом для TalkBack.
 * Без зачёркивания: на плейтесте его читали и как «ошибка», и как «проверено». Отмеченная
 * ошибка — розовая строка с «✗» (розовый в игре — всегда ошибка), пропущенная — жёлтая с «?».
 */
@Composable
private fun CheckRow(mark: Mark, description: String, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val flagged = mark == Mark.FLAGGED || mark == Mark.FINE
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when (mark) {
                    Mark.FLAGGED -> FinneyPink.copy(alpha = 0.28f)
                    Mark.MISSED -> FinneyYellow.copy(alpha = 0.5f)
                    else -> Color.Transparent
                },
            )
            .toggleable(value = flagged, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics {
                contentDescription = description
                stateDescription = when (mark) {
                    Mark.NONE -> "не отмечено"
                    Mark.FLAGGED -> "отмечено как ошибка"
                    Mark.MISSED -> "здесь есть ошибка"
                    Mark.FINE -> "отмечено, но здесь всё верно"
                }
            }
            .padding(horizontal = 4.dp),
    ) {
        content()
        MarkBadge(mark)
    }
}

/** Значок отметки: ✗ — ошибка, ? — пропущена, ✓ — здесь всё верно. Пустой кружок — ещё не отмечено. */
@Composable
private fun MarkBadge(mark: Mark) {
    val (text, fill) = when (mark) {
        Mark.NONE -> "" to Color.White
        Mark.FLAGGED -> "✗" to FinneyPink
        Mark.MISSED -> "?" to FinneyYellow
        Mark.FINE -> "✓" to FinneyGreen
    }
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(fill).border(2.dp, FinneyInk, CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (text.isNotEmpty()) OutlinedText(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun PlainRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
        // Подпись — тем же шрифтом, что названия товаров выше, число — моноширинным, как цены.
        Text(label, style = MaterialTheme.typography.titleMedium, color = FinneyInk, modifier = Modifier.weight(1f))
        Text(value.toString(), style = Mono, color = FinneyInk)
        // Место под значок, чтобы числа стояли столбиком со строками выше.
        Box(Modifier.padding(start = 8.dp).size(width = 30.dp, height = 1.dp))
    }
}

@Composable
private fun Dashes() {
    Text("- ".repeat(40), style = Mono, color = FinneyInk, maxLines = 1, modifier = Modifier.padding(vertical = 2.dp))
}


/** Сдача монетами, крупные первыми. */
private fun coinsFor(amount: Int): List<Int> {
    var left = amount
    return buildList {
        for (coin in ChangeCoins) while (left >= coin) { add(coin); left -= coin }
    }
}
