package ru.finney.pet.domain.tasks

import ru.finney.pet.domain.model.BasketRule
import ru.finney.pet.domain.model.BasketTask
import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.ChangeMode
import ru.finney.pet.domain.model.ChangeRound
import ru.finney.pet.domain.model.ChangeTask
import ru.finney.pet.domain.model.Chore
import ru.finney.pet.domain.model.ChoresTask
import ru.finney.pet.domain.model.DistributorRule
import ru.finney.pet.domain.model.DistributorTask
import ru.finney.pet.domain.model.GoalRaceTask
import ru.finney.pet.domain.model.GoalSliderTask
import ru.finney.pet.domain.model.RaceEvent
import ru.finney.pet.domain.model.ReceiptItem
import ru.finney.pet.domain.model.ReceiptLine
import ru.finney.pet.domain.model.ReceiptTask
import ru.finney.pet.domain.model.ReserveTask
import ru.finney.pet.domain.model.SorterTask
import ru.finney.pet.domain.model.StandTask
import ru.finney.pet.domain.model.TaskDefinition
import ru.finney.pet.domain.model.TaskOutcome
import ru.finney.pet.domain.model.Weather

/** Утро лавки: сколько сырья купить и почём продавать стакан. */
data class StandChoice(val buy: Int, val price: Int)

sealed interface TaskInput {
    /** distributor: сумма по id корзины. Отсутствующая корзина = 0. */
    data class Distribution(val amounts: Map<String, Int>) : TaskInput

    /** basket: выбранные товары с полки. */
    data class Basket(val items: Set<String>) : TaskInput

    /** goal_slider: регулярный взнос. */
    data class Deposit(val amount: Int) : TaskInput

    /** sorter: куда ребёнок отнёс каждую вещь — первый выбор, до подсказки. */
    data class Sorting(val answers: Map<String, Category>) : TaskInput

    /** goal_race: сколько отложено в каждый день, по порядку. */
    data class DailyDeposits(val amounts: List<Int>) : TaskInput

    /** reserve: что взято в план недели и что перенесено ради непредвиденной траты. */
    data class Reserve(val planned: Set<String>, val dropped: Set<String>) : TaskInput

    /** stand: сколько штук сырья закуплено. */
    data class Stock(val count: Int) : TaskInput

    /** stand на несколько дней: утренние решения по порядку дней. */
    data class StandDays(val days: List<StandChoice>) : TaskInput

    /** change: монеты каждого раунда с первой попытки, по порядку раундов. */
    data class Coins(val rounds: List<List<Int>>) : TaskInput

    /** receipt: отмеченные строки чека и отмечена ли сдача — с первой проверки. */
    data class Flags(val lines: Set<String>, val change: Boolean) : TaskInput

    /** chores: id дел по дням недели. Пустой день — отдых. */
    data class Schedule(val days: List<List<String>>) : TaskInput
}

/** Ввод, который нельзя оценить. Это не исход задания: ребёнок исправляет и пробует снова. */
sealed interface TaskInputError {
    data object WrongInputType : TaskInputError
    data object NegativeAmount : TaskInputError
    data class UnknownBasket(val id: String) : TaskInputError
    data class UnknownItem(val id: String) : TaskInputError

    /** [remaining] > 0 — разложено не всё, < 0 — разложено больше суммы. */
    data class NotFullyDistributed(val remaining: Int) : TaskInputError

    /** Оплата сверх лимита — «не хватает [shortage] монет», как в магазине. */
    data class OverLimit(val total: Int, val limit: Int) : TaskInputError {
        val shortage: Int get() = total - limit
    }

    data class DepositOutOfRange(val max: Int) : TaskInputError
    data class DepositNotOnStep(val step: Int) : TaskInputError

    /** sorter: разложены не все вещи. */
    data class NotAllSorted(val missing: Int) : TaskInputError

    /** goal_race и change: дней или раундов не столько, сколько в задании. */
    data class WrongCount(val expected: Int) : TaskInputError

    /** reserve: нужное нельзя убрать из плана. */
    data class NeedsNotPlanned(val id: String) : TaskInputError

    /** reserve: перенести можно только то, что было в плане. */
    data class NotPlanned(val id: String) : TaskInputError

    /** reserve: непредвиденная трата всё ещё не покрыта — не хватает [shortage]. */
    data class SurpriseNotCovered(val shortage: Int) : TaskInputError

    /** change: такой монеты нет в ящике. */
    data class UnknownCoin(val value: Int) : TaskInputError

    /** change: в кошельке нет таких монет, раунд [round] с 0. */
    data class CoinsNotInWallet(val round: Int) : TaskInputError

    /** chores: в день [day] (с 0) дел больше, чем часиков. */
    data class DayOverloaded(val day: Int) : TaskInputError

    /** chores: дело [id] взято больше [max] раз за неделю. */
    data class ChoreTooOften(val id: String, val max: Int) : TaskInputError

    /** stand: такой цены стакана в игре нет. */
    data class PriceNotOffered(val price: Int) : TaskInputError

    /** chores: дело [id] в день [day] (с 0) не дают. */
    data class ChoreNotToday(val id: String, val day: Int) : TaskInputError
}

/** Числа для объяснения исхода. */
sealed interface TaskDetails {
    /** Заполнено, если у задания есть `goal`. */
    data class Distribution(val goalSavedAfter: Int?, val goalRemaining: Int?) : TaskDetails
    data class Basket(val total: Int) : TaskDetails
    data class GoalSlider(val collected: Int, val shortfall: Int) : TaskDetails

    /** [mistakes] — id вещей, отнесённых не туда, в порядке задания. */
    data class Sorting(val correct: Int, val total: Int, val mistakes: List<String>) : TaskDetails

    /**
     * [eventsTaken] — сколько соблазнов дня ребёнок себе позволил,
     * [mood] — сердечки Финни на финише: 0 — загрустил по дороге, цель засчитана без бонуса.
     */
    data class GoalRace(val saved: Int, val shortfall: Int, val eventsTaken: Int, val mood: Int) : TaskDetails

    /**
     * [reserve] — запас после плана; [surprises] — сколько стоило непредвиденное;
     * [shortage] — сколько не хватило запаса; [droppedNeeds] и [droppedWants] — сколько
     * нужного и желаемого пришлось отложить (нужное — игра не пройдена, желаемое — пройдена
     * без бонуса); [keptWants] — желаемого осталось в плане (0 — неделя без радостей, тоже без бонуса);
     * [left] — сколько запаса осталось на потом.
     */
    data class Reserve(
        val reserve: Int,
        val shortage: Int,
        val surprises: Int = 0,
        val droppedNeeds: Int = 0,
        val keptWants: Int = 0,
        val left: Int = 0,
        val droppedWants: Int = 0,
    ) : TaskDetails

    /**
     * Итог дня лавки. [leftover] — непроданные стаканы, [missed] — гости, которым не хватило.
     * [best] — сколько сырья было бы в самый раз.
     */
    data class Stand(
        val cups: Int,
        val sold: Int,
        val earned: Int,
        val spent: Int,
        val leftover: Int,
        val missed: Int,
        val best: Int,
    ) : TaskDetails {
        val kept: Int get() = earned - spent
    }

    /**
     * Лавка на несколько дней: итог каждого прошедшего дня, [profit] — сколько денег стало
     * больше, чем на старте, [goal] — сколько нужно для победы.
     */
    data class StandWeek(val days: List<StandDayResult>, val profit: Int, val goal: Int) : TaskDetails

    /** [results] — по раунду: положено минус нужно. 0 — верно, > 0 — лишнее, < 0 — не хватает. */
    data class Change(val correct: Int, val total: Int, val results: List<Int>) : TaskDetails

    /**
     * Сверка чека. [found] — ошибок отмечено верно, [missed] — не замечено, [extra] — верное
     * отмечено как ошибка. [refund] — сколько монет вернули за найденное, [lost] — за пропущенное.
     */
    data class Receipt(val found: Int, val missed: Int, val extra: Int, val refund: Int, val lost: Int) : TaskDetails

    /**
     * [restDays] — пустых дней недели. [earned] — уже за вычетом усталости [tiredLoss] и с
     * бонусом за разные дела [bonus].
     */
    data class Chores(
        val earned: Int,
        val shortfall: Int,
        val restDays: Int,
        val tiredLoss: Int = 0,
        val bonus: Int = 0,
    ) : TaskDetails
}

/**
 * День лавки. [guests] — сколько пришло по настоящей погоде и цене. [used] — сколько сырья
 * выжато (сначала старое), [sold] — стаканов продано. [stockLeft] — сырьё на завтра,
 * [spoiled] — испортилось вечером. [money] — деньги в конце дня.
 */
data class StandDayResult(
    val forecast: Weather,
    val weather: Weather,
    val price: Int,
    val guests: Int,
    val bought: Int,
    val used: Int,
    val sold: Int,
    val missed: Int,
    val earned: Int,
    val spent: Int,
    val stockLeft: Int,
    val spoiled: Int,
    val money: Int,
)

/** Ошибки кассира: строки чека, которые нужно отметить, и сколько сдачи недодали. */
data class ReceiptErrors(val lines: Set<String>, val changeShort: Int)

sealed interface TaskEvaluation {
    /** [bonus] — успех с бонусом: к награде добавляется [ru.finney.pet.domain.model.TaskReward.bonus]. */
    data class Done(val outcome: TaskOutcome, val details: TaskDetails, val bonus: Boolean = false) : TaskEvaluation
    data class Invalid(val error: TaskInputError) : TaskEvaluation
}

/** Движки заданий: учебные (docs/economy.md, раздел 9) и мини-игры (docs/minigames.md). */
object TaskEngines {

    fun evaluate(task: TaskDefinition, input: TaskInput): TaskEvaluation = when {
        task is DistributorTask && input is TaskInput.Distribution -> distribute(task, input)
        task is BasketTask && input is TaskInput.Basket -> checkout(task, input)
        task is GoalSliderTask && input is TaskInput.Deposit -> slide(task, input)
        task is SorterTask && input is TaskInput.Sorting -> sort(task, input)
        task is GoalRaceTask && input is TaskInput.DailyDeposits -> race(task, input)
        task is ReserveTask && input is TaskInput.Reserve -> reserve(task, input)
        task is StandTask && input is TaskInput.Stock && task.days.isEmpty() -> stand(task, input)
        task is StandTask && input is TaskInput.StandDays && task.days.isNotEmpty() -> standWeek(task, input)
        task is ChangeTask && input is TaskInput.Coins -> change(task, input)
        task is ReceiptTask && input is TaskInput.Flags -> receipt(task, input)
        task is ChoresTask && input is TaskInput.Schedule -> chores(task, input)
        else -> TaskEvaluation.Invalid(TaskInputError.WrongInputType)
    }

    // ---------- Подсказки для экранов: те же правила, что в оценке ----------

    /** Выполнено ли правило корзины для выбранных товаров — для галочек списка покупок. */
    fun ruleMet(task: BasketTask, rule: BasketRule, items: Set<String>): Boolean {
        val chosen = task.shelf.filter { it.id in items }
        return when (rule) {
            is BasketRule.HasCategory -> chosen.any { it.category == rule.category }
            is BasketRule.Includes -> rule.item in items
            is BasketRule.Excludes -> rule.item !in items
            is BasketRule.AnyOf -> rule.items.any { it in items }
            is BasketRule.MinQty -> chosen.filter { it.id in rule.items }.sumOf { it.qty } >= rule.qty
        }
    }

    /** Разница «положено минус нужно» для раунда кассы: 0 — верно. */
    fun changeDiff(round: ChangeRound, coins: List<Int>): Int = coins.sum() - round.target

    /** Можно ли заплатить этими монетами из кошелька раунда: каждой монеты — не больше, чем есть. */
    fun fitsWallet(round: ChangeRound, coins: List<Int>): Boolean {
        val have = round.wallet.groupingBy { it }.eachCount()
        return coins.groupingBy { it }.eachCount().all { (coin, n) -> n <= (have[coin] ?: 0) }
    }

    /** Итог лавки для [count] штук сырья — экран вечера показывает те же числа, что уйдут в оценку. */
    fun standDay(task: StandTask, count: Int): TaskDetails.Stand {
        val cups = count * task.ingredient.yields
        val sold = minOf(cups, task.guests)
        return TaskDetails.Stand(
            cups = cups,
            sold = sold,
            earned = sold * task.cupPrice,
            spent = count * task.ingredient.price,
            leftover = cups - sold,
            missed = task.guests - sold,
            best = bestStock(task),
        )
    }

    /** Сколько сырья хватит на всех гостей без лишнего: ⌈гости / стаканов из штуки⌉. */
    fun bestStock(task: StandTask): Int = (task.guests + task.ingredient.yields - 1) / task.ingredient.yields

    /**
     * Числа, которые движок считает сам, — для подстановок в тексты задания ([TaskGenerator]):
     * когда гостей выпало случайно, «5 лимонов — это 10 стаканов» тоже должно пересчитаться.
     * Имена — в docs/minigames.md.
     */
    fun facts(task: TaskDefinition): Map<String, Int> = when (task) {
        is StandTask -> {
            val best = standDay(task, bestStock(task))
            val fewer = standDay(task, bestStock(task) - 1)
            mapOf(
                "bestStock" to bestStock(task),
                "bestCups" to best.cups,
                "bestSpent" to best.spent,
                "bestEarned" to best.earned,
                "bestLeftover" to best.leftover,
                "fewerStock" to bestStock(task) - 1,
                "fewerCups" to fewer.cups,
                "fewerMissed" to fewer.missed,
            )
        }
        else -> emptyMap()
    }

    /** Сколько гостей придёт в погоду [weather], если стакан стоит [price]. */
    fun standGuests(task: StandTask, weather: Weather, price: Int): Int =
        maxOf(0, (task.demand[weather] ?: 0) + (task.cupPrice - price) * task.guestsPerCoin)

    /**
     * Итоги дней лавки по утренним решениям [choices] — столько дней, сколько решений.
     * Экран зовёт после каждого дня, чтобы показать вечер: погода объявляется после выбора.
     * Решения не проверяет — это делает оценка; сырья больше, чем по деньгам, не купить.
     */
    fun standDays(task: StandTask, choices: List<StandChoice>): List<StandDayResult> {
        var money = task.budget
        // Партии сырья: день покупки → сколько осталось. Выжимается сначала старое.
        val batches = sortedMapOf<Int, Int>()
        return choices.take(task.days.size).mapIndexed { d, choice ->
            val day = task.days[d]
            val bought = choice.buy.coerceIn(0, money / task.ingredient.price)
            val spent = bought * task.ingredient.price
            if (bought > 0) batches[d] = bought
            val guests = standGuests(task, day.weather, choice.price)
            var need = (guests + task.ingredient.yields - 1) / task.ingredient.yields
            var used = 0
            for (key in batches.keys.toList()) {
                val take = minOf(need, batches.getValue(key))
                batches[key] = batches.getValue(key) - take
                need -= take
                used += take
            }
            val sold = minOf(guests, used * task.ingredient.yields)
            val spoiled = batches.filterKeys { it <= d - task.freshDays + 1 }.values.sum()
            batches.keys.removeAll { it <= d - task.freshDays + 1 || batches[it] == 0 }
            val earned = sold * choice.price
            money += earned - spent
            StandDayResult(
                forecast = day.forecast,
                weather = day.weather,
                price = choice.price,
                guests = guests,
                bought = bought,
                used = used,
                sold = sold,
                missed = guests - sold,
                earned = earned,
                spent = spent,
                stockLeft = batches.values.sum(),
                spoiled = spoiled,
                money = money,
            )
        }
    }

    /** Сколько можно заработать, если знать погоду заранее: для проверки, что порог достижим. */
    fun standBestProfit(task: StandTask): Int {
        val maxGuests = task.days.maxOf { day -> task.cupPrices.maxOf { standGuests(task, day.weather, it) } }
        val maxBuy = (maxGuests + task.ingredient.yields - 1) / task.ingredient.yields + 1
        val options = (0..maxBuy).flatMap { buy -> task.cupPrices.map { StandChoice(buy, it) } }
        fun best(prefix: List<StandChoice>): Int =
            if (prefix.size == task.days.size) {
                standDays(task, prefix).last().money - task.budget
            } else {
                options.maxOf { best(prefix + it) }
            }
        return best(emptyList())
    }

    /** Позволил ли себе соблазн дня: после взноса на него хватает. */
    fun raceTaken(task: GoalRaceTask, event: RaceEvent, deposit: Int): Boolean = task.incomePerDay - deposit >= event.price

    /**
     * Сердечки Финни после каждого из [deposits] по порядку. Соблазн дня отвергнут —
     * минус одно, позволен — плюс одно, не больше [GoalRaceTask.mood]. Ноль — насовсем:
     * загрустившего Финни поздняя радость уже не выручает, важна регулярность.
     */
    fun raceMood(task: GoalRaceTask, deposits: List<Int>): List<Int> {
        var mood = task.mood
        return deposits.mapIndexed { i, deposit ->
            val event = task.events.firstOrNull { it.day == i + 1 }
            if (event != null && mood > 0) {
                mood = if (raceTaken(task, event, deposit)) minOf(task.mood, mood + 1) else mood - 1
            }
            mood
        }
    }

    /**
     * Взносы, с которыми дорога проходится с бонусом — цель набрана и Финни не загрустил, —
     * или null, если никак. Перебор того, какие соблазны взять: в их день отложить остаток,
     * в остальные — весь доход.
     */
    fun raceSolution(task: GoalRaceTask): List<Int>? {
        val events = task.events.filter { it.day in 1..task.days && it.price <= task.incomePerDay }
        return (0 until (1 shl events.size)).asSequence()
            .map { mask ->
                List(task.days) { i ->
                    val event = events.withIndex().firstOrNull { (n, e) -> e.day == i + 1 && mask and (1 shl n) != 0 }?.value
                    if (event == null) task.incomePerDay else (task.incomePerDay - event.price) / task.step * task.step
                }
            }
            .firstOrNull { (race(task, TaskInput.DailyDeposits(it)) as? TaskEvaluation.Done)?.bonus == true }
    }

    /** Сумма по чеку — как её посчитал кассир, с ошибками. */
    fun receiptTotal(task: ReceiptTask): Int = task.lines.sumOf { it.price }

    /**
     * Что должно быть отмечено. Одинаковые строки одного товара взаимозаменяемы: из дубля
     * ошибкой названы сначала уже отмеченные в [flagged], потом последние — так экран
     * подсвечивает то же, что засчитывает оценка.
     */
    fun receiptErrors(task: ReceiptTask, flagged: Set<String> = emptySet()): ReceiptErrors = ReceiptErrors(
        lines = task.cart.flatMap { item ->
            val check = checkItem(task, item)
            val (marked, rest) = check.samePrice.partition { it.id in flagged }
            val duplicates = (marked + rest.reversed()).take(check.duplicates)
            check.wrongPrice.map { it.id } + duplicates.map { it.id }
        }.toSet(),
        changeShort = changeShort(task),
    )

    /** Недоданная сдача: сдача считается от суммы чека, какой бы она ни была. */
    private fun changeShort(task: ReceiptTask): Int = maxOf(0, task.paid - receiptTotal(task) - task.change)

    /**
     * Строки товара. Цена не как на ценнике — ошибка в каждой такой строке. Строк больше, чем
     * штук в пакете, — лишние [duplicates]. Оба вида ошибки у одного товара валидатор не пускает.
     */
    private class ItemCheck(val wrongPrice: List<ReceiptLine>, val samePrice: List<ReceiptLine>, val duplicates: Int)

    private fun checkItem(task: ReceiptTask, item: ReceiptItem): ItemCheck {
        val (wrong, same) = task.lines.filter { it.item == item.id }.partition { it.price != item.price }
        val duplicates = if (wrong.isEmpty()) maxOf(0, same.size - item.qty) else 0
        return ItemCheck(wrong, same, duplicates)
    }

    /** Сколько часиков занимают дела одного дня. Неизвестные id не считаются. */
    fun dayHours(task: ChoresTask, ids: List<String>): Int {
        val byId = task.chores.associateBy { it.id }
        return ids.sumOf { byId[it]?.hours ?: 0 }
    }

    /** Оплата дела в день [day] (с 0) — без усталости. */
    fun choreFee(chore: Chore, day: Int): Int = chore.pay?.getOrNull(day) ?: chore.reward

    /** Дают ли дело в день [day] (с 0). */
    fun choreOpen(chore: Chore, day: Int): Boolean = chore.onDays?.contains(day + 1) ?: true

    /** Заработок недели по частям: [fees] — оплата дел, [tiredLoss] — минус усталость, [bonus] — за разные дела. */
    data class ChoresPay(val fees: Int, val tiredLoss: Int, val bonus: Int) {
        val total: Int get() = fees - tiredLoss + bonus
    }

    /** Разбор заработка недели. Неизвестные id не считаются. */
    fun choresPay(task: ChoresTask, days: List<List<String>>): ChoresPay {
        val byId = task.chores.associateBy { it.id }
        var fees = 0
        var tired = 0
        days.forEachIndexed { d, ids ->
            val yesterday = days.getOrNull(d - 1).orEmpty().toSet()
            ids.mapNotNull(byId::get).forEach { chore ->
                val fee = choreFee(chore, d)
                fees += fee
                if (chore.id in yesterday) tired += minOf(fee, task.tiredCut)
            }
        }
        val distinct = days.flatten().filter { it in byId }.toSet().size
        return ChoresPay(fees, tired, varietyBonus(task, distinct))
    }

    private fun varietyBonus(task: ChoresTask, distinct: Int): Int =
        if (task.varietyBonus > 0 && distinct >= task.varietyCount) task.varietyBonus else 0

    /** Сколько заработано за неделю: с усталостью и бонусом. */
    fun choresEarned(task: ChoresTask, days: List<List<String>>): Int = choresPay(task, days).total

    /**
     * Неделя с самым большим заработком, в которой не меньше [ChoresTask.minRestDays] дней
     * отдыха. null — даже так цель не набрать. Перебор по дням: состояние — сколько раз взяты
     * дела с [Chore.maxTimes], какие дела были вчера (усталость), какие были за неделю (бонус)
     * и сколько уже дней отдыха.
     */
    fun choresSolution(task: ChoresTask): List<List<String>>? {
        if (task.minRestDays > task.days) return null
        data class State(val used: List<Int>, val yesterday: Set<String>, val seen: Set<String>, val rest: Int)
        val limited = task.chores.filter { it.maxTimes != null }
        var best = mapOf(State(List(limited.size) { 0 }, emptySet(), emptySet(), 0) to (0 to emptyList<List<String>>()))
        for (d in 0 until task.days) {
            val packs = dayPacks(task.chores.filter { choreOpen(it, d) }, task.hoursPerDay)
            val next = mutableMapOf<State, Pair<Int, List<List<String>>>>()
            for ((state, value) in best) for (pack in packs) {
                val used = limited.mapIndexed { i, chore -> state.used[i] + pack.count { it == chore } }
                if (limited.indices.any { used[it] > limited[it].maxTimes!! }) continue
                val earned = value.first + pack.sumOf { chore ->
                    val fee = choreFee(chore, d)
                    fee - if (chore.id in state.yesterday) minOf(fee, task.tiredCut) else 0
                }
                val ids = pack.map { it.id }
                val after = State(
                    used = used,
                    yesterday = ids.toSet(),
                    seen = state.seen + ids,
                    rest = minOf(task.minRestDays, state.rest + if (pack.isEmpty()) 1 else 0),
                )
                if (earned > (next[after]?.first ?: -1)) next[after] = earned to value.second + listOf(ids)
            }
            best = next
        }
        val (earned, week) = best
            .filterKeys { it.rest >= task.minRestDays }
            .map { (state, value) -> value.first + varietyBonus(task, state.seen.size) to value.second }
            .maxByOrNull { it.first } ?: return null
        if (earned < task.goal.price) return null
        return week
    }

    /** Все наборы дел, которые помещаются в один день, включая пустой. */
    private fun dayPacks(chores: List<Chore>, hours: Int): List<List<Chore>> {
        fun from(i: Int, left: Int): List<List<Chore>> {
            if (i == chores.size) return listOf(emptyList())
            val chore = chores[i]
            val most = minOf(left / chore.hours, chore.maxTimes ?: Int.MAX_VALUE)
            return (0..most).flatMap { n -> from(i + 1, left - n * chore.hours).map { List(n) { chore } + it } }
        }
        return from(0, hours)
    }

    /** Сколько стоит всё непредвиденное недели. */
    fun surprisesTotal(task: ReserveTask): Int = task.surprises.sumOf { it.price }

    /** Сколько стоили бы все сюрпризы, какие могли случиться, — запас «на самый дождливый день». */
    fun surprisesWorstCase(task: ReserveTask): Int = maxOf(task.worstCase, surprisesTotal(task))

    /** Запас после плана: сумма минус всё запланированное. */
    fun reserveLeft(task: ReserveTask, planned: Set<String>): Int =
        task.amount - task.spendings.filter { it.id in planned }.sumOf { it.price }

    private fun distribute(task: DistributorTask, input: TaskInput.Distribution): TaskEvaluation {
        val basketIds = task.baskets.map { it.id }.toSet()
        input.amounts.keys.firstOrNull { it !in basketIds }?.let {
            return TaskEvaluation.Invalid(TaskInputError.UnknownBasket(it))
        }
        if (input.amounts.values.any { it < 0 }) return TaskEvaluation.Invalid(TaskInputError.NegativeAmount)
        val remaining = task.amount - input.amounts.values.sum()
        if (remaining != 0) return TaskEvaluation.Invalid(TaskInputError.NotFullyDistributed(remaining))

        fun amountIn(basket: String) = input.amounts[basket] ?: 0
        val success = task.rules.all { rule ->
            when (rule) {
                is DistributorRule.Min -> amountIn(rule.basket) >= rule.value
                is DistributorRule.Max -> amountIn(rule.basket) <= rule.value
            }
        }
        val savedAfter = task.goal?.let { it.saved + amountIn(it.basket) }
        val details = TaskDetails.Distribution(
            goalSavedAfter = savedAfter,
            goalRemaining = task.goal?.let { maxOf(0, it.price - savedAfter!!) },
        )
        return TaskEvaluation.Done(outcome(success), details)
    }

    private fun checkout(task: BasketTask, input: TaskInput.Basket): TaskEvaluation {
        val shelf = task.shelf.associateBy { it.id }
        input.items.firstOrNull { it !in shelf }?.let {
            return TaskEvaluation.Invalid(TaskInputError.UnknownItem(it))
        }
        val chosen = input.items.map { shelf.getValue(it) }
        val total = chosen.sumOf { it.price }
        if (total > task.limit) return TaskEvaluation.Invalid(TaskInputError.OverLimit(total, task.limit))

        val success = task.rules.all { ruleMet(task, it, input.items) }
        return TaskEvaluation.Done(outcome(success), TaskDetails.Basket(total))
    }

    private fun slide(task: GoalSliderTask, input: TaskInput.Deposit): TaskEvaluation {
        if (input.amount !in 0..task.incomePerPeriod) {
            return TaskEvaluation.Invalid(TaskInputError.DepositOutOfRange(task.incomePerPeriod))
        }
        if (input.amount % task.step != 0) return TaskEvaluation.Invalid(TaskInputError.DepositNotOnStep(task.step))
        val collected = input.amount * task.periods
        val details = TaskDetails.GoalSlider(collected = collected, shortfall = maxOf(0, task.goalPrice - collected))
        return TaskEvaluation.Done(outcome(collected >= task.goalPrice), details)
    }

    private fun sort(task: SorterTask, input: TaskInput.Sorting): TaskEvaluation {
        val ids = task.items.map { it.id }.toSet()
        input.answers.keys.firstOrNull { it !in ids }?.let {
            return TaskEvaluation.Invalid(TaskInputError.UnknownItem(it))
        }
        val missing = ids.count { it !in input.answers }
        if (missing > 0) return TaskEvaluation.Invalid(TaskInputError.NotAllSorted(missing))
        val mistakes = task.items.filter { input.answers[it.id] != it.category }.map { it.id }
        val correct = task.items.size - mistakes.size
        return TaskEvaluation.Done(
            outcome(correct >= task.minCorrect),
            TaskDetails.Sorting(correct = correct, total = task.items.size, mistakes = mistakes),
        )
    }

    private fun race(task: GoalRaceTask, input: TaskInput.DailyDeposits): TaskEvaluation {
        if (input.amounts.size != task.days) return TaskEvaluation.Invalid(TaskInputError.WrongCount(task.days))
        if (input.amounts.any { it !in 0..task.incomePerDay }) {
            return TaskEvaluation.Invalid(TaskInputError.DepositOutOfRange(task.incomePerDay))
        }
        if (input.amounts.any { it % task.step != 0 }) return TaskEvaluation.Invalid(TaskInputError.DepositNotOnStep(task.step))
        val saved = task.startSaved + input.amounts.sum()
        val taken = task.events.count { event ->
            val deposit = input.amounts.getOrNull(event.day - 1) ?: return@count false
            raceTaken(task, event, deposit)
        }
        // Отказ от радостей — не провал: цель набрана — пройдено. Но учит игра «понемногу,
        // но регулярно», поэтому бонус — только если Финни не загрустил по дороге.
        val mood = raceMood(task, input.amounts).last()
        val success = saved >= task.goal.price
        return TaskEvaluation.Done(
            outcome(success),
            TaskDetails.GoalRace(saved = saved, shortfall = maxOf(0, task.goal.price - saved), eventsTaken = taken, mood = mood),
            bonus = success && mood > 0,
        )
    }

    private fun reserve(task: ReserveTask, input: TaskInput.Reserve): TaskEvaluation {
        val byId = task.spendings.associateBy { it.id }
        (input.planned + input.dropped).firstOrNull { it !in byId }?.let {
            return TaskEvaluation.Invalid(TaskInputError.UnknownItem(it))
        }
        task.spendings.firstOrNull { it.category == Category.NEEDS && it.id !in input.planned }?.let {
            return TaskEvaluation.Invalid(TaskInputError.NeedsNotPlanned(it.id))
        }
        val reserve = reserveLeft(task, input.planned)
        if (reserve < 0) return TaskEvaluation.Invalid(TaskInputError.OverLimit(task.amount - reserve, task.amount))
        input.dropped.firstOrNull { it !in input.planned }?.let {
            return TaskEvaluation.Invalid(TaskInputError.NotPlanned(it))
        }

        val total = surprisesTotal(task)
        val shortage = maxOf(0, total - reserve)
        val freed = input.dropped.sumOf { byId.getValue(it).price }
        if (freed < shortage) return TaskEvaluation.Invalid(TaskInputError.SurpriseNotCovered(shortage - freed))
        val droppedNeeds = input.dropped.count { byId.getValue(it).category == Category.NEEDS }
        val droppedWants = input.dropped.size - droppedNeeds
        val keptWants = (input.planned - input.dropped).count { byId.getValue(it).category == Category.WANTS }
        // Отказ от необязательного — не провал: отложил желаемое ради сюрприза или не взял
        // его вовсе — пройдено, но без бонуса. Не пройдено, только если отложено нужное.
        val success = droppedNeeds == 0
        return TaskEvaluation.Done(
            outcome(success),
            TaskDetails.Reserve(
                reserve = reserve,
                shortage = shortage,
                surprises = total,
                droppedNeeds = droppedNeeds,
                keptWants = keptWants,
                left = reserve + freed - total,
                droppedWants = droppedWants,
            ),
            // Бонус — за баланс: радость осталась в плане, и запаса хватило бы на все сюрпризы,
            // даже те, что не случились: запас откладывают до того, как узнали, понадобится ли он.
            bonus = success && droppedWants == 0 && keptWants > 0 && reserve >= surprisesWorstCase(task),
        )
    }

    private fun stand(task: StandTask, input: TaskInput.Stock): TaskEvaluation {
        if (input.count < 0) return TaskEvaluation.Invalid(TaskInputError.NegativeAmount)
        val cost = input.count * task.ingredient.price
        if (cost > task.budget) return TaskEvaluation.Invalid(TaskInputError.OverLimit(cost, task.budget))
        val day = standDay(task, input.count)
        return TaskEvaluation.Done(outcome(input.count == day.best), day)
    }

    private fun standWeek(task: StandTask, input: TaskInput.StandDays): TaskEvaluation {
        if (input.days.size != task.days.size) return TaskEvaluation.Invalid(TaskInputError.WrongCount(task.days.size))
        if (input.days.any { it.buy < 0 }) return TaskEvaluation.Invalid(TaskInputError.NegativeAmount)
        input.days.firstOrNull { it.price !in task.cupPrices }?.let {
            return TaskEvaluation.Invalid(TaskInputError.PriceNotOffered(it.price))
        }
        // Купить больше, чем есть денег к утру, нельзя: проверка по дням, как в магазине.
        var money = task.budget
        input.days.forEachIndexed { d, choice ->
            val cost = choice.buy * task.ingredient.price
            if (cost > money) return TaskEvaluation.Invalid(TaskInputError.OverLimit(cost, money))
            money = standDays(task, input.days.take(d + 1)).last().money
        }
        val days = standDays(task, input.days)
        val profit = days.last().money - task.budget
        return TaskEvaluation.Done(outcome(profit >= task.goalProfit), TaskDetails.StandWeek(days, profit, task.goalProfit))
    }

    private fun change(task: ChangeTask, input: TaskInput.Coins): TaskEvaluation {
        if (input.rounds.size != task.rounds.size) return TaskEvaluation.Invalid(TaskInputError.WrongCount(task.rounds.size))
        input.rounds.flatten().firstOrNull { it !in task.coins }?.let {
            return TaskEvaluation.Invalid(TaskInputError.UnknownCoin(it))
        }
        task.rounds.forEachIndexed { i, round ->
            if (round.mode == ChangeMode.EXACT && !fitsWallet(round, input.rounds[i])) {
                return TaskEvaluation.Invalid(TaskInputError.CoinsNotInWallet(i))
            }
        }
        val results = task.rounds.mapIndexed { i, round -> changeDiff(round, input.rounds[i]) }
        val correct = results.count { it == 0 }
        return TaskEvaluation.Done(
            outcome(correct >= (task.minCorrect ?: task.rounds.size)),
            TaskDetails.Change(correct = correct, total = task.rounds.size, results = results),
        )
    }

    private fun receipt(task: ReceiptTask, input: TaskInput.Flags): TaskEvaluation {
        val lineIds = task.lines.map { it.id }.toSet()
        input.lines.firstOrNull { it !in lineIds }?.let {
            return TaskEvaluation.Invalid(TaskInputError.UnknownItem(it))
        }
        var found = 0
        var missed = 0
        var extra = 0
        var refund = 0
        var lost = 0
        for (item in task.cart) {
            val check = checkItem(task, item)
            check.wrongPrice.forEach { line ->
                val over = line.price - item.price
                if (line.id in input.lines) { found++; refund += over } else { missed++; lost += over }
            }
            val flagged = check.samePrice.count { it.id in input.lines }
            val caught = minOf(flagged, check.duplicates)
            found += caught
            missed += check.duplicates - caught
            extra += flagged - caught
            refund += caught * item.price
            lost += (check.duplicates - caught) * item.price
        }
        val short = changeShort(task)
        when {
            short > 0 && input.change -> { found++; refund += short }
            short > 0 -> { missed++; lost += short }
            input.change -> extra++
        }
        return TaskEvaluation.Done(
            outcome(missed == 0 && extra == 0),
            TaskDetails.Receipt(found = found, missed = missed, extra = extra, refund = refund, lost = lost),
        )
    }

    private fun chores(task: ChoresTask, input: TaskInput.Schedule): TaskEvaluation {
        if (input.days.size != task.days) return TaskEvaluation.Invalid(TaskInputError.WrongCount(task.days))
        val byId = task.chores.associateBy { it.id }
        input.days.flatten().firstOrNull { it !in byId }?.let {
            return TaskEvaluation.Invalid(TaskInputError.UnknownItem(it))
        }
        input.days.indexOfFirst { dayHours(task, it) > task.hoursPerDay }.takeIf { it >= 0 }?.let {
            return TaskEvaluation.Invalid(TaskInputError.DayOverloaded(it))
        }
        val times = input.days.flatten().groupingBy { it }.eachCount()
        task.chores.firstOrNull { chore -> chore.maxTimes != null && (times[chore.id] ?: 0) > chore.maxTimes }?.let {
            return TaskEvaluation.Invalid(TaskInputError.ChoreTooOften(it.id, it.maxTimes!!))
        }
        input.days.forEachIndexed { d, ids ->
            ids.firstOrNull { !choreOpen(byId.getValue(it), d) }?.let {
                return TaskEvaluation.Invalid(TaskInputError.ChoreNotToday(it, d))
            }
        }
        val pay = choresPay(task, input.days)
        val rest = input.days.count { it.isEmpty() }
        return TaskEvaluation.Done(
            outcome(pay.total >= task.goal.price && rest >= task.minRestDays),
            TaskDetails.Chores(
                earned = pay.total,
                shortfall = maxOf(0, task.goal.price - pay.total),
                restDays = rest,
                tiredLoss = pay.tiredLoss,
                bonus = pay.bonus,
            ),
        )
    }

    /**
     * Объяснение итога для экрана. Пройдено, но можно лучше («Дождливый день» с отложенным
     * желаемым или без радостей, «Дорога к цели» с грустным Финни) — своим текстом из
     * контента, иначе [TaskDefinition.explainOk] / [TaskDefinition.explainFail].
     */
    fun explanation(task: TaskDefinition, outcome: TaskOutcome, details: TaskDetails): String {
        if (outcome == TaskOutcome.FAIL) return task.explainFail
        val partial = when {
            task is ReserveTask && details is TaskDetails.Reserve -> when {
                details.droppedWants > 0 -> task.explainDropped
                details.keptWants == 0 -> task.explainNoJoy
                else -> null
            }
            task is GoalRaceTask && details is TaskDetails.GoalRace -> if (details.mood == 0) task.explainSad else null
            else -> null
        }
        return partial ?: task.explainOk
    }

    private fun outcome(success: Boolean) = if (success) TaskOutcome.SUCCESS else TaskOutcome.FAIL
}
