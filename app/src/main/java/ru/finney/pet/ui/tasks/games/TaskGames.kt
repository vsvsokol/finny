package ru.finney.pet.ui.tasks.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.finney.pet.domain.model.BasketTask
import ru.finney.pet.domain.model.ChangeRound
import ru.finney.pet.domain.model.ChangeTask
import ru.finney.pet.domain.model.ChoresTask
import ru.finney.pet.domain.model.DistributorTask
import ru.finney.pet.domain.model.GoalRaceTask
import ru.finney.pet.domain.model.GoalSliderTask
import ru.finney.pet.domain.model.ItemArt
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.model.ReceiptTask
import ru.finney.pet.domain.model.ReserveTask
import ru.finney.pet.domain.model.SorterTask
import ru.finney.pet.domain.model.StandTask
import ru.finney.pet.domain.model.TaskDefinition
import ru.finney.pet.domain.tasks.TaskDetails
import ru.finney.pet.domain.tasks.TaskEngines
import ru.finney.pet.domain.tasks.TaskInput
import ru.finney.pet.domain.tasks.TaskInputError
import ru.finney.pet.ui.components.StableOutlinedText
import ru.finney.pet.ui.components.StableText
import ru.finney.pet.ui.components.AlertBadge
import ru.finney.pet.ui.components.CheckBadge
import ru.finney.pet.ui.components.FillBar
import ru.finney.pet.ui.components.SpendBar
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyPink
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.StrokeRegular

// Единственное место, где движок задания встречается с экраном игры: какая
// игра, в какой сцене и что показать в итоге. Новый движок — ветка в каждой из
// трёх функций ниже. Как добавить — docs/minigames.md.

/**
 * Экран игры для задания. [inputError] — ядро не приняло ввод (например, «не хватает 5»);
 * игра показывает его и прячет, когда ребёнок что-то поменял ([onInputSeen]).
 */
@Composable
fun TaskGame(
    task: TaskDefinition,
    character: PetCharacter,
    balance: Int,
    inputError: TaskInputError?,
    onInputSeen: () -> Unit,
    onClose: () -> Unit,
    onSubmit: (TaskInput) -> Unit,
) {
    when (task) {
        is SorterTask -> SorterGame(task, character, balance, onClose, onSubmit)
        is BasketTask -> ShoppingGame(task, character, inputError, onInputSeen, onClose, onSubmit)
        is GoalRaceTask -> GoalRaceGame(task, character, onClose, onSubmit)
        is ReserveTask -> ReserveGame(task, character, inputError, onInputSeen, onClose, onSubmit)
        is StandTask -> StandGame(task, character, inputError, onInputSeen, onClose, onSubmit)
        is ChangeTask -> ChangeGame(task, character, onClose, onSubmit)
        // Учебные движки без своей сцены: в контенте их сейчас нет, экран — запасной.
        is DistributorTask -> EnvelopesGame(task, inputError, onInputSeen, onSubmit)
        is GoalSliderTask -> DepositGame(task, onSubmit)
        is ReceiptTask -> ReceiptGame(task, character, onClose, onSubmit)
        is ChoresTask -> ChoresGame(task, character, onClose, onSubmit)
    }
}

/** Сцена задания — и на вступлении, и в итоге. Лавка в итоге уже вечерняя. */
fun backdropFor(task: TaskDefinition, finished: Boolean = false): Backdrop = when (task) {
    is SorterTask -> Backdrop.DOTS
    is BasketTask -> Backdrop.SHOP
    is GoalRaceTask -> Backdrop.FIELD
    is ReserveTask -> if (finished) Backdrop.DOTS_RAIN else Backdrop.DOTS
    is StandTask -> if (finished) Backdrop.SUNSET else Backdrop.SKY
    is ChangeTask, is ReceiptTask -> Backdrop.STORE
    is ChoresTask, is DistributorTask, is GoalSliderTask -> Backdrop.DOTS
}

/** Фон карточки перед игрой — по макету карточек (вариант A). Игра потом встаёт на свой [backdropFor]. */
fun introBackdropFor(task: TaskDefinition): Backdrop = when (task) {
    is SorterTask -> Backdrop.DOTS
    is BasketTask -> Backdrop.CARD_GREEN
    is GoalRaceTask -> Backdrop.CARD_LILAC
    is ReserveTask -> Backdrop.CARD_RAIN
    is StandTask -> Backdrop.CARD_LEMON
    is ChangeTask -> Backdrop.CARD_YELLOW
    is ReceiptTask -> Backdrop.CARD_BLUE
    is ChoresTask -> Backdrop.CARD_PINK
    is DistributorTask, is GoalSliderTask -> Backdrop.DOTS
}

/** Значок задания — берётся из самого контента, отдельной картинки не нужно. */
fun taskIcon(task: TaskDefinition): ItemArt? = taskIconCandidates(task).firstOrNull()

/**
 * Из чего можно взять значок задания, по порядку: первым — то, что лучше всего
 * говорит об игре (цель, ингредиент), дальше — остальные предметы задания.
 */
fun taskIconCandidates(task: TaskDefinition): List<ItemArt> = when (task) {
    is SorterTask -> task.items
    is BasketTask -> task.shelf
    is GoalRaceTask -> listOf(task.goal) + task.events
    is ReserveTask -> task.surprises + task.spendings
    is StandTask -> listOf(task.ingredient)
    is ChangeTask -> task.rounds
    is ReceiptTask -> task.cart
    is ChoresTask -> listOf(task.goal) + task.chores
    is DistributorTask, is GoalSliderTask -> emptyList()
}

/**
 * Значки для списка заданий — у каждой строки свой рисунок. Первый предмет у
 * нескольких игр один и тот же (яблоко у «Конвейера» и «Списка покупок»), и
 * одинаковые карточки в списке не различить. Каждая строка берёт первый предмет
 * своего задания, чей рисунок ещё не заняли строки выше; если свободных нет —
 * всё-таки первый. Контент при этом не трогаем.
 */
fun distinctTaskIcons(tasks: List<TaskDefinition>): List<ItemArt?> {
    val used = mutableSetOf<String>()
    return tasks.map { task ->
        val candidates = taskIconCandidates(task)
        val pick = candidates.firstOrNull { it.pictureKey() !in used } ?: candidates.firstOrNull()
        pick?.pictureKey()?.let(used::add)
        pick
    }
}

/** Чем предмет нарисован. Без рисунка и эмодзи значок — буква названия, он и так свой. */
private fun ItemArt.pictureKey(): String? = art ?: emoji

/**
 * Итог игры в панели: что получилось, по пунктам, как в концептах. Пояснение
 * «что делать дальше» — в explainOk / explainFail задания, его пишет контент,
 * а показывает сцена итога отдельно от пунктов.
 *
 * Удачное здесь тихое, а неудачное заметное — см. [ResultRow]: ребёнок сразу
 * видит, где ошибся, а не читает все строки подряд. Итоговые числа «3 из 4» —
 * с полосой рядом ([ScoreLine]). [bonus] — часть награды за бонус движка, число из ядра.
 */
@Composable
fun ColumnScope.ResultBody(task: TaskDefinition, details: TaskDetails, input: TaskInput, bonus: Int = 0) {
    when (details) {
        is TaskDetails.Sorting -> {
            val items = (task as? SorterTask)?.items.orEmpty().associateBy { it.id }
            if (details.mistakes.isEmpty()) {
                ResultRow(null, "Всё разложено верно", ok = true)
            } else {
                details.mistakes.mapNotNull(items::get).forEach {
                    ResultRow(it, "${it.label} → «${categoryWord(it.category)}»", ok = false, note = it.why)
                }
            }
            ScoreLine("С первого раза", details.correct, details.total)
        }

        is TaskDetails.Basket -> {
            val basket = task as? BasketTask
            val cart = (input as? TaskInput.Basket)?.items.orEmpty()
            basket?.rules?.filter { it.label != null }?.forEach { rule ->
                ResultRow(null, rule.label!!, ok = TaskEngines.ruleMet(basket, rule, cart))
            }
            basket?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Потрачено", style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
                    SpendBar(details.total, it.limit, Modifier.weight(1f))
                    StableText("${details.total} из ${it.limit}", widest = "${it.limit}0 из ${it.limit}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        is TaskDetails.GoalRace -> {
            val race = task as? GoalRaceTask
            race?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ItemPicture(it.goal, it.goal.label, 56.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SumRow("Копилка", "${details.saved} из ${it.goal.price}", strong = true)
                        FillBar(details.saved, it.goal.price, Modifier.fillMaxWidth())
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Настроение", style = MaterialTheme.typography.bodyLarge, color = FinneyInk, modifier = Modifier.weight(1f))
                    Hearts(details.mood, it.mood, size = 26.dp)
                }
            }
            if (details.shortfall > 0) ResultRow(null, "Не хватило ${details.shortfall}", ok = false)
            if (details.mood == 0) ResultRow(null, "Финни загрустил по дороге", ok = false, note = "Радостей было мало.")
        }

        is TaskDetails.Reserve -> {
            val reserve = task as? ReserveTask
            // Сначала то, из-за чего «почти», потом суммы: причину видно сразу, её не ищут.
            if (details.keptWants == 0) ResultRow(null, "Неделя без радостей", ok = false)
            if (details.shortage > 0) ResultRow(null, "Запаса не хватило ${details.shortage}", ok = false)
            // Отложенное — поимённо, с картинкой. Общие строки «нужное отложили» и
            // «желаемое отложили» — только когда назвать нечего: иначе одно и то же
            // говорилось дважды (редизайн 28.09 сократил итог до трёх строк).
            val dropped = (input as? TaskInput.Reserve)?.dropped.orEmpty()
            val droppedItems = reserve?.spendings?.filter { it.id in dropped }.orEmpty()
            if (droppedItems.isEmpty()) {
                if (details.droppedNeeds > 0) ResultRow(null, "Нужное отложили", ok = false)
                if (details.droppedWants > 0) ResultRow(null, "Желаемое отложили на потом", ok = false)
            }
            droppedItems.forEach { ResultRow(it, "${it.label} — отложили на потом", ok = false) }
            // Без радостей в плане запас хватает сам собой — зелёная строка выглядела бы победой.
            if (details.shortage == 0 && details.keptWants > 0) ResultRow(null, "Запаса хватило", ok = true)
            if (bonus > 0) SumRow("Запас на всё непредвиденное", "+$bonus")
            reserve?.let {
                // Хватило ли запаса на непредвиденное — полосой: запас против того, что понадобилось.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    it.surprises.firstOrNull()?.let { s -> ItemPicture(s, s.label, 36.dp) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SumRow("Запас", details.reserve.toString(), strong = true)
                        SumRow("Понадобилось", details.surprises.toString())
                        FillBar(details.reserve, details.surprises, Modifier.fillMaxWidth(), description = "Запас ${details.reserve}, понадобилось ${details.surprises}")
                    }
                }
            }
            if (details.left > 0) SumRow("Осталось на потом", details.left.toString())
        }

        is TaskDetails.Stand -> {
            val stand = task as? StandTask
            val amounts = listOf("+${details.earned}", "−${details.spent}", details.kept.toString())
            // Столбец сумм — одной ширины по самой длинной, числа прижаты вправо:
            // так «+40», «−12» и «28» стоят разряд под разрядом, как в чеке.
            val widest = amounts.maxBy { it.length }
            LedgerRow("+", FinneyYellow, "Продал ${cupCount(details.sold)}", amounts[0], widest)
            // Название сырья приходит из контента в одной форме, поэтому «лимон, 7 шт.», а не «7 лимон».
            // Не «× 7»: знак читали как умножение (плейтест 02.10).
            LedgerRow("−", FinneyPeach, "Купил: ${stand?.ingredient?.label?.lowercase().orEmpty()}, ${details.spent / (stand?.ingredient?.price ?: 1)} шт.", amounts[1], widest)
            LedgerRow("=", Color.White, "Заработал", amounts[2], widest, highlight = true)
            val notes = listOfNotNull(
                details.leftover.takeIf { it > 0 }?.let { "${cupCount(it)} не купили" },
                details.missed.takeIf { it > 0 }?.let { "${plural(it, "гостю", "гостям", "гостям")} не хватило" },
            )
            if (notes.isEmpty()) {
                ResultRow(null, "Всем хватило, ничего не пропало", ok = true)
            } else {
                ResultRow(
                    stand?.ingredient,
                    notes.joinToString(", "),
                    ok = false,
                    note = "На всех хватило бы: ${stand?.ingredient?.label?.lowercase().orEmpty()}, ${details.best} шт.",
                )
            }
        }

        // Лавка на несколько дней: по строке на день, итог — прибыль против цели.
        // Экран дней ещё не сделан, см. docs/minigames.md, «Лавка на несколько дней».
        is TaskDetails.StandWeek -> {
            details.days.forEachIndexed { d, day ->
                SumRow("День ${d + 1}: продал ${day.sold}, испортилось ${day.spoiled}", "${day.earned - day.spent}")
            }
            ResultRow(null, "Прибыль ${details.profit}, нужно ${details.goal}", ok = details.profit >= details.goal)
        }

        is TaskDetails.Change -> {
            val change = task as? ChangeTask
            val answers = (input as? TaskInput.Coins)?.rounds.orEmpty()
            change?.rounds?.forEachIndexed { i, round ->
                ChangeRow(round, answers.getOrNull(i).orEmpty(), diff = details.results.getOrNull(i) ?: 0)
            }
            ScoreLine("С первого раза", details.correct, details.total)
        }

        is TaskDetails.Receipt -> {
            if (details.found > 0) ResultRow(null, "Нашли ошибок: ${details.found}", ok = true)
            if (details.missed > 0) ResultRow(null, "Не заметили: ${details.missed}", ok = false)
            if (details.extra > 0) ResultRow(null, "Верное приняли за ошибку: ${details.extra}", ok = false)
            // Сколько вернули из того, что могли, — полосой, как любое «сколько из скольки».
            if (details.lost > 0) ScoreLine("Вернули", details.refund, details.refund + details.lost)
            else Summary("Вернули ${details.refund}")
        }

        is TaskDetails.Chores -> {
            val chores = task as? ChoresTask
            chores?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ItemPicture(it.goal, it.goal.label, 56.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SumRow("Заработал", "${details.earned} из ${it.goal.price}", strong = true)
                        FillBar(details.earned, it.goal.price, Modifier.fillMaxWidth())
                    }
                }
                ResultRow(null, "Дней отдыха: ${details.restDays}, нужно ${it.minRestDays}", ok = details.restDays >= it.minRestDays)
                if (details.tiredLoss > 0) SumRow("Устал — заплатили меньше", "−${details.tiredLoss}")
                if (details.bonus > 0) SumRow("Бонус за разные дела", "+${details.bonus}")
            }
            if (details.shortfall > 0) ResultRow(null, "Не хватило ${details.shortfall}", ok = false)
        }

        is TaskDetails.Distribution -> details.goalSavedAfter?.let { Summary("В копилке станет $it, осталось ${details.goalRemaining}") }
        is TaskDetails.GoalSlider -> Summary(
            if (details.shortfall == 0) "Накопится ${details.collected} — хватает!" else "Накопится ${details.collected}, не хватит ${details.shortfall}",
        )
    }
}

/**
 * Строка итога. Удачное — тихо: без подложки, обычным текстом и маленьким «✓».
 * Неудачное — заметно: розовая подложка в рамке, крупнее и с «!». Отметка дублирует
 * цвет знаком (ТЗ п. 3.6).
 *
 * Бледным удачное больше не пишется: на плейтесте полупрозрачные верные строки
 * приняли за ошибку отрисовки. Тише — значит без подложки, а не без цвета.
 */
@Composable
private fun ResultRow(art: ItemArt?, text: String, ok: Boolean, note: String? = null) {
    if (ok) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            art?.let { ItemPicture(it, text, 28.dp) }
            Text(text, style = MaterialTheme.typography.bodyLarge, color = FinneyInk, modifier = Modifier.weight(1f))
            CheckBadge(size = 24.dp)
        }
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().problemCard().padding(8.dp),
    ) {
        art?.let { ItemPicture(it, text, 40.dp) }
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.titleMedium, color = FinneyInk)
            note?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = FinneyInk) }
        }
        AlertBadge(size = 30.dp)
    }
}

/**
 * Раунд кассы: что купили и какие монеты дали — монетами, а не «2 + 1», и сумма.
 * Верный — обычной строкой с «✓» и словом «верно»; ошибка — как любая неудачная
 * строка итога, и под ней сколько было нужно.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChangeRow(round: ChangeRound, coins: List<Int>, diff: Int) {
    val ok = diff == 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (ok) Modifier.padding(horizontal = 4.dp) else Modifier.problemCard().padding(8.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = "${round.label}: ${coins.sum()}" + if (ok) ", верно" else ", нужно было ${round.target}"
            },
    ) {
        ItemPicture(round, round.label, if (ok) 28.dp else 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                coins.forEach { DenominationCoin(it, size = 28.dp) }
                Text(
                    "= ${coins.sum()}",
                    style = if (ok) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                    color = FinneyInk,
                )
            }
            Text(
                if (ok) "верно" else "нужно было ${round.target}",
                style = MaterialTheme.typography.bodyLarge,
                color = FinneyInk,
            )
        }
        if (ok) CheckBadge(size = 24.dp) else AlertBadge(size = 30.dp)
    }
}

/** Итоговое «3 из 4» — числом и полосой рядом. */
@Composable
private fun ScoreLine(label: String, value: Int, max: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
        FillBar(value, max, Modifier.weight(1f))
        StableText("$value из $max", widest = "$max из $max", style = MaterialTheme.typography.titleMedium)
    }
}

/** Подложка неудачной строки: светло-розовая, в рамке. */
private fun Modifier.problemCard(): Modifier = this
    .clip(RoundedCornerShape(14.dp))
    .background(FinneyPink.copy(alpha = 0.28f))
    .border(StrokeRegular, FinneyInk, RoundedCornerShape(14.dp))

/** Строка «истории», как в ките: значок операции, подпись, сумма. */
@Composable
private fun LedgerRow(sign: String, color: Color, text: String, amount: String, widest: String, highlight: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlight) FinneyYellow else Color.Transparent)
            .padding(horizontal = if (highlight) 6.dp else 0.dp, vertical = 2.dp),
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(color).border(2.dp, FinneyInk, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(sign, style = MaterialTheme.typography.titleMedium, color = FinneyInk) }
        Text(text, style = MaterialTheme.typography.titleMedium, color = FinneyInk, modifier = Modifier.weight(1f))
        // Сумма — по правому краю столбца одной ширины. Раньше она стояла как есть и
        // вместе с контуром упиралась в край панели: на плейтесте числа срезало справа.
        StableOutlinedText(
            amount,
            widest = widest,
            style = MaterialTheme.typography.titleLarge,
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

@Composable
private fun Summary(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = FinneyInk, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
}
