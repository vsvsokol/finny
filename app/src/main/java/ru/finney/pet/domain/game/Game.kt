package ru.finney.pet.domain.game

import ru.finney.pet.domain.economy.SavingsRules
import ru.finney.pet.domain.model.AccessorySlot
import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.EconomyConfig
import ru.finney.pet.domain.model.EntryType
import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.domain.model.GameState
import ru.finney.pet.domain.model.Goal
import ru.finney.pet.domain.model.ItemKind
import ru.finney.pet.domain.model.ItemUse
import ru.finney.pet.domain.model.LedgerEntry
import ru.finney.pet.domain.model.Period
import ru.finney.pet.domain.model.PeriodFacts
import ru.finney.pet.domain.model.PeriodPhase
import ru.finney.pet.domain.model.PeriodResult
import ru.finney.pet.domain.model.PetStats
import ru.finney.pet.domain.model.Plan
import ru.finney.pet.domain.model.ShopItem
import ru.finney.pet.domain.model.StatEffect
import ru.finney.pet.domain.model.TaskAttempt
import ru.finney.pet.domain.model.TaskDefinition
import ru.finney.pet.domain.model.TaskOutcome
import ru.finney.pet.domain.period.PeriodRules
import ru.finney.pet.domain.pet.Emotion
import ru.finney.pet.domain.pet.PetRules
import ru.finney.pet.domain.progress.LevelUnlocks
import ru.finney.pet.domain.progress.Progression
import ru.finney.pet.domain.progress.Unlocks
import ru.finney.pet.domain.tasks.TaskDetails
import ru.finney.pet.domain.tasks.TaskEngines
import ru.finney.pet.domain.tasks.TaskEvaluation
import ru.finney.pet.domain.tasks.TaskGenerator
import ru.finney.pet.domain.tasks.TaskInput
import kotlin.random.Random

data class GoalProgress(
    val goal: Goal,
    val saved: Int,
    val remaining: Int,
    /** null — срок пока не посчитать: пополнений не было. */
    val periodsToGoal: Int?,
    val averageDeposit: Int,
)

data class PurchasePreview(
    val item: ShopItem,
    val statsBefore: PetStats,
    val statsAfter: PetStats,
    /** 0 — денег хватает. */
    val shortage: Int,
)

/** Расходник на полке: что и сколько штук. */
data class ShelfItem(val item: ShopItem, val count: Int)

data class WithdrawPreview(
    val savedBefore: Int,
    val savedAfter: Int,
    val periodsBefore: Int?,
    val periodsAfter: Int?,
)

/** План текущего периода против факта на эту минуту. */
data class PlanReport(
    val plan: Plan,
    val facts: PeriodFacts,
    /** Перерасход по нужному и желаемому сверх плана. */
    val overspend: Int,
    /** Засчитается ли «план выполнен», если закрыть период сейчас. */
    val onTrack: Boolean,
)

/**
 * Условия текущего уровня на эту минуту — то, что увидит ребёнок в чек-листе,
 * и то, что засчитается, если завершить уровень сейчас.
 */
data class LevelCheck(
    /** План составлен: без него уровень не завершить и в мини-игры не сыграть. */
    val planConfirmed: Boolean,
    val needsCovered: Boolean,
    val planMatched: Boolean,
    val savingsAdded: Boolean,
    /** Сколько из трёх условий выше нужно для прохождения. */
    val toPass: Int,
    /** Игра уровня; null — в этом периоде её нет, условие не действует. */
    val levelTaskId: String? = null,
    /** Игра уровня пройдена в этом периоде. Обязательна сверх [toPass], если [gameRequired]. */
    val gamePassed: Boolean = true,
    /** Обязательна ли игра уровня. В демо-режиме — нет: эксперт проходит уровни быстро. */
    val gameRequired: Boolean = true,
) {
    val met: Int get() = listOf(needsCovered, planMatched, savingsAdded).count { it }
    val willPass: Boolean get() = (gamePassed || !gameRequired) && met >= toPass
}

sealed interface TaskResult {
    /**
     * Исход задания и числа для экрана объяснения. [reward] = 0, если награда уже выдавалась.
     * [bonus] — часть [reward] за бонус движка, 0 — бонуса нет или он уже выдавался.
     */
    data class Submitted(
        val state: GameState,
        val outcome: TaskOutcome,
        val details: TaskDetails,
        val reward: Int,
        val bonus: Int = 0,
    ) : TaskResult

    /** Ввод не принят или задание недоступно. Состояние не изменилось. */
    data class Rejected(val reason: Rejection) : TaskResult
}

/**
 * Единственная точка изменения игрового состояния. Чистые функции: состояние на входе,
 * новое состояние или отказ на выходе. Правила — docs/economy.md.
 */
class Game(
    private val content: GameContent,
    /** Выбор игры уровня и зёрна чисел мини-игр. В тестах — с зерном. */
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val economy get() = content.economy

    fun newGame(isDemo: Boolean = false): GameState = openPeriod(
        GameState(
            isDemo = isDemo,
            pet = economy.pet.start,
            activeGoalId = null,
            periods = emptyList(),
            ledger = emptyList(),
            attempts = emptyList(),
        ),
    )

    // ---------- Чтение ----------

    fun level(state: GameState): Int = levelAfter(state, state.periods.size)

    /**
     * Уровень после периодов с номерами до [periodNumber] включительно: нужен, чтобы
     * сравнить уровень до и после периода. Каждый пройденный период — плюс уровень.
     */
    fun levelAfter(state: GameState, periodNumber: Int): Int = Progression.level(
        state.periods.count { period ->
            period.number <= periodNumber && period.result?.let { Progression.passed(it, economy) } == true
        },
        economy,
    )

    /** Пройден ли уровень, сыгранный в этом периоде; null — период ещё идёт. */
    fun isPassed(period: Period): Boolean? = period.result?.let { Progression.passed(it, economy) }

    /** Чек-лист текущего уровня. Сон считается на сейчас: спящий досыпает. */
    fun levelCheck(state: GameState): LevelCheck {
        val period = state.currentPeriod
        val plan = period.plan
        val facts = PeriodRules.facts(state.ledger, period.number)
        return LevelCheck(
            planConfirmed = period.phase == PeriodPhase.ACTIVE && plan != null,
            needsCovered = PetRules.needsCovered(state.pet.copy(energy = energyAt(state)), economy.pet),
            planMatched = plan != null && PeriodRules.planMatched(plan, facts, economy.planTolerance),
            savingsAdded = facts.savings > 0,
            toPass = conditionsToPass(state),
            levelTaskId = period.levelTaskId,
            gamePassed = levelGamePassed(state, period),
            gameRequired = levelGameRequired(state),
        )
    }

    /** Сколько из трёх условий нужно для уровня: в демо-режиме меньше, см. [EconomyConfig.demoConditionsToPass]. */
    fun conditionsToPass(state: GameState): Int =
        if (state.isDemo) economy.demoConditionsToPass else economy.conditionsToPass

    private fun levelGameRequired(state: GameState): Boolean = !state.isDemo || economy.demoLevelGameRequired

    /** Как часто напоминать о питомце: раз в сутки, в демо-режиме — раз в несколько минут. */
    fun reminderEveryMillis(state: GameState): Long =
        if (state.isDemo) economy.reminders.demoReminderMinutes * 60_000L else economy.reminders.everyHours * 3_600_000L

    /** Игра уровня пройдена успешно именно в этом периоде. Нет игры — условие выполнено. */
    private fun levelGamePassed(state: GameState, period: Period): Boolean {
        val taskId = period.levelTaskId ?: return true
        return state.attempts.any {
            it.periodNumber == period.number && it.taskId == taskId && it.outcome == TaskOutcome.SUCCESS
        }
    }

    fun stage(state: GameState): Int = Progression.stage(level(state), economy)

    /** Эмоция по шкалам сейчас: у спящего сон растёт, см. [energyAt]. */
    fun emotion(state: GameState): Emotion = PetRules.emotion(state.pet.copy(energy = energyAt(state)), economy.pet)

    /**
     * Сколько минимально стоит закрыть нужное при текущих шкалах. Мыло на полочке уже
     * куплено: пока оно есть, чистота в подсказке ничего не стоит.
     */
    fun needsHint(state: GameState): Int? {
        val threshold = economy.pet.needsThreshold
        val pet = if (hasSoap(state)) state.pet.copy(hygiene = maxOf(state.pet.hygiene, threshold)) else state.pet
        // Полотенцем и пастой без мыла не помоешься — в подсказке их нет.
        return PetRules.needsCost(pet, content.shop.filterNot { it.bathExtra }, threshold)
    }

    /** Зерно новой попытки мини-игры: по нему [task] выбирает числа, см. [TaskGenerator]. */
    fun newTaskSeed(): Long = random.nextLong()

    /**
     * Задание с числами попытки [seed] — его показывает экран и по нему же [submitTask]
     * оценивает ответ. Без разброса в tasks.json или без зерна — задание как в контенте.
     */
    fun task(taskId: String, seed: Long?): TaskDefinition? {
        val template = content.taskTemplates[taskId]
        return if (template != null && seed != null) TaskGenerator.generate(template, seed) else content.task(taskId)
    }

    fun isTaskAvailable(state: GameState, task: TaskDefinition): Boolean =
        state.isDemo || (task.unlockPeriod <= state.currentPeriod.number && task.unlockLevel <= level(state))

    /**
     * Вариант игры для питомца сейчас: самый сложный из тех, до чьего уровня он дорос.
     * Так сложность растёт с уровнем и в демо-режиме. Не дорос ни до одного — первый
     * вариант, экран покажет его закрытым.
     */
    fun currentVariant(state: GameState, variants: List<TaskDefinition>): TaskDefinition =
        variants.lastOrNull { it.unlockLevel <= level(state) } ?: variants.first()

    /** По одному варианту каждой игры — то, что видит ребёнок в списке мини-игр. */
    fun currentTasks(state: GameState): List<TaskDefinition> =
        content.taskSeries.map { currentVariant(state, it) }

    /** Игры, которые стали сложнее или открылись при переходе с уровня [from] на [to]. */
    fun tasksUnlockedBetween(from: Int, to: Int): List<TaskDefinition> =
        content.tasks.filter { it.unlockLevel in (from + 1)..to }

    // ---------- Открытия по уровням ----------

    /** Что открывается на уровне [level]: игры, вещи, цели, звание. */
    fun unlocksAt(level: Int): LevelUnlocks = Unlocks.at(level, content)

    /** Что открылось при переходе с уровня [from] на [to] — для итога уровня. Уровни без новинок пропущены. */
    fun unlocksBetween(from: Int, to: Int): List<LevelUnlocks> =
        ((from + 1)..to).map(::unlocksAt).filterNot { it.isEmpty }

    /** Ближайший уровень с новинками после текущего — «что дальше» на главном; null — открыто всё. */
    fun nextUnlocks(state: GameState): LevelUnlocks? = Unlocks.next(level(state), content)

    /** Звание питомца сейчас; null — званий в контенте нет. */
    fun title(state: GameState): String? = Unlocks.title(level(state), content)

    /** Продаётся ли вещь на этом уровне. В демо-режиме открыто всё. */
    fun isUnlocked(state: GameState, item: ShopItem): Boolean = state.isDemo || item.unlockLevel <= level(state)

    /** Можно ли выбрать цель на этом уровне. В демо-режиме открыто всё. */
    fun isUnlocked(state: GameState, goal: Goal): Boolean = state.isDemo || goal.unlockLevel <= level(state)

    fun goalProgress(state: GameState, goalId: String? = state.activeGoalId): GoalProgress? {
        val goal = goalId?.let(content::goal) ?: return null
        val saved = state.goalSaved(goal.id)
        val remaining = maxOf(0, goal.price - saved)
        val pace = SavingsRules.pace(state.periods)
        return GoalProgress(goal, saved, remaining, SavingsRules.periodsToGoal(remaining, pace), pace.average)
    }

    fun previewPurchase(state: GameState, itemId: String): PurchasePreview? {
        val item = content.item(itemId) ?: return null
        return PurchasePreview(
            item = item,
            statsBefore = state.pet,
            statsAfter = PetRules.apply(state.pet, item.effect),
            shortage = maxOf(0, item.price - state.balance),
        )
    }

    fun previewWithdraw(state: GameState, amount: Int): WithdrawPreview? {
        val goal = state.activeGoalId?.let(content::goal) ?: return null
        val saved = state.goalSaved(goal.id)
        val savedAfter = maxOf(0, saved - amount)
        val pace = SavingsRules.pace(state.periods)
        return WithdrawPreview(
            savedBefore = saved,
            savedAfter = savedAfter,
            periodsBefore = SavingsRules.periodsToGoal(goal.price - saved, pace),
            periodsAfter = SavingsRules.periodsToGoal(goal.price - savedAfter, pace),
        )
    }

    // ---------- План ----------

    /** null — план текущего периода ещё не подтверждён. */
    fun planReport(state: GameState): PlanReport? {
        val period = state.currentPeriod
        val plan = period.plan ?: return null
        val facts = PeriodRules.facts(state.ledger, period.number)
        return PlanReport(
            plan = plan,
            facts = facts,
            overspend = PeriodRules.overspend(plan, facts),
            onTrack = PeriodRules.planMatched(plan, facts, economy.planTolerance),
        )
    }

    /**
     * Весь план — намерение. Нужное и желаемое ребёнок покупает в магазине, копилку
     * пополняет сам кнопкой «Положить» ([deposit]). Иначе условие «копилка» выполнялось бы
     * само, в момент подтверждения, и не требовало решения (плейтест 28.09).
     * План с копилкой без выбранной цели не принимается: откладывать будет некуда.
     */
    fun confirmPlan(state: GameState, needs: Int, wants: Int, savings: Int): GameResult {
        val period = state.currentPeriod
        if (period.phase != PeriodPhase.PLANNING) return reject(Rejection.PlanAlreadyConfirmed)
        if (needs < 0 || wants < 0 || savings < 0) return reject(Rejection.InvalidAmount)
        // ТЗ п. 2.5.5: сумма распределяется минимум по трём направлениям. Пустой план
        // ничего не решает, а после него открываются и покупки, и игры.
        if (listOf(needs, wants, savings).count { it > 0 } < economy.planDirections) {
            return reject(Rejection.PlanMissingDirection)
        }
        val plan = Plan(budget = state.balance, needs = needs, wants = wants, savings = savings)
        if (plan.remainder < 0) return reject(Rejection.PlanExceedsBudget(plan.budget, plan.planned))
        if (savings > 0 && state.activeGoalId == null) return reject(Rejection.NoActiveGoal)
        return ok(state.withCurrentPeriod(period.copy(phase = PeriodPhase.ACTIVE, plan = plan)))
    }

    /**
     * Сколько плана «хочется» ещё не потрачено — его можно переложить в нужное ([moveWantsToNeeds]).
     * null — плана нет или уровень не идёт.
     */
    fun wantsLeft(state: GameState): Int? {
        val report = planReport(state) ?: return null
        if (state.currentPeriod.phase != PeriodPhase.ACTIVE) return null
        return maxOf(0, report.plan.wants - report.facts.wants)
    }

    /**
     * «Возьми из радостей»: сколько плана переложить из «хочется» в нужное, чтобы покупка
     * нужного [itemId] не вышла за план. null — кнопки нет: товар не нужное, за план и так
     * не выходит, денег на покупку нет (из плана монет не взять — кошелёк один) или
     * нетронутого «хочется» на всю разницу не хватает.
     */
    fun wantsToNeeds(state: GameState, itemId: String): Int? {
        val item = content.item(itemId)?.takeIf { it.category == Category.NEEDS } ?: return null
        val report = planReport(state) ?: return null
        val left = wantsLeft(state) ?: return null
        if (item.price > state.balance) return null
        val over = report.facts.needs + item.price - report.plan.needs
        return over.takeIf { it in 1..left }
    }

    /**
     * Переложить [amount] плана из «хочется» в нужное посреди уровня — решение ребёнка
     * «радость подождёт, сначала нужное». Только из нетронутого остатка «хочется»: что уже
     * потрачено на радости, назад не вернуть. Копилка и бюджет не меняются. После переноса
     * план — обычный план: «план выполнен» считается по нему. Деньги не двигаются.
     */
    fun moveWantsToNeeds(state: GameState, amount: Int): GameResult {
        val period = state.currentPeriod
        val plan = period.plan
        if (period.phase != PeriodPhase.ACTIVE || plan == null) return reject(Rejection.PlanNotConfirmed)
        val left = wantsLeft(state) ?: return reject(Rejection.PlanNotConfirmed)
        if (amount <= 0 || amount > left) return reject(Rejection.InvalidAmount)
        val moved = plan.copy(needs = plan.needs + amount, wants = plan.wants - amount)
        return ok(state.withCurrentPeriod(period.copy(plan = moved)))
    }

    // ---------- Покупки ----------

    /**
     * Купить и сразу применить: расходник съеден или использован в момент покупки — так
     * кормят на кухне и моют в ванной. Для остального то же, что [buyToShelf].
     */
    fun buy(state: GameState, itemId: String): GameResult {
        val item = content.item(itemId) ?: return reject(Rejection.UnknownItem(itemId))
        // Спящего не кормят и не моют — это кухня и ванная. «Хочется» и шляпы
        // из магазина покупать можно: магазин во сне открыт.
        if (state.sleepingSince != null && item.feedsOrWashes) return reject(Rejection.Asleep)
        val bought = buyToShelf(state, itemId)
        // Мыло, щётка, паста — запас полочки ванной: их тратят по разу при мытье ([wash]), не сразу.
        if (bought !is GameResult.Ok || item.kind != ItemKind.CONSUMABLE || item.uses != null) return bought
        return use(bought.state, itemId)
    }

    /**
     * Купить в магазине. Расходник ложится на полку ([shelf]) и на питомца пока не действует:
     * съесть — отдельно, [use]. Поэтому спит питомец или нет, не важно. Аксессуар и игрушка
     * действуют сразу, как и раньше: они остаются у питомца.
     */
    fun buyToShelf(state: GameState, itemId: String): GameResult {
        val item = content.item(itemId) ?: return reject(Rejection.UnknownItem(itemId))
        if (state.currentPeriod.phase != PeriodPhase.ACTIVE) return reject(Rejection.PlanNotConfirmed)
        content.goalFor(item.id)?.let { return reject(Rejection.NotForSale(item.id, it.label)) }
        if (!isUnlocked(state, item)) return reject(Rejection.Locked(item.unlockLevel))
        // Аксессуар и игрушка покупаются один раз: они остаются у питомца.
        if (item.kind != ItemKind.CONSUMABLE && state.owns(item.id)) return reject(Rejection.AlreadyOwned)
        if (item.price > state.balance) return reject(Rejection.InsufficientFunds(item.price, state.balance))
        val entry = entry(state, EntryType.PURCHASE, balanceDelta = -item.price)
            .copy(category = item.category, itemId = item.id)
        if (item.kind == ItemKind.CONSUMABLE) return ok(state.copy(ledger = state.ledger + entry))
        val bought = state.copy(pet = PetRules.apply(state.pet, item.effect), ledger = state.ledger + entry)
        // Купленный аксессуар сразу надевается: ребёнок видит покупку на питомце.
        return ok(if (item.kind == ItemKind.ACCESSORY) bought.wearing(item) else bought)
    }

    /**
     * Взять расходник с полки: съесть, помыться, посмотреть мультик. Бесплатно — он уже
     * куплен, поэтому и до плана можно. Во сне не едят и не моются, как и при [buy].
     */
    fun use(state: GameState, itemId: String): GameResult {
        val item = content.item(itemId) ?: return reject(Rejection.UnknownItem(itemId))
        if (item.kind != ItemKind.CONSUMABLE) return reject(Rejection.NotPlayable(itemId))
        // Мыло, щётку и пасту берут мытьём ([wash]): там шаги ванны и пара щётка — паста.
        if (item.uses != null) return reject(Rejection.NotPlayable(itemId))
        if (state.sleepingSince != null && item.feedsOrWashes) return reject(Rejection.Asleep)
        if (stock(state, itemId) <= 0) return reject(Rejection.NotOwned(itemId))
        return ok(state.copy(pet = PetRules.apply(state.pet, item.effect), uses = state.uses + takeOne(state, itemId)))
    }

    /**
     * Что лежит на полке, в порядке магазина: еда — штуками, мыло, щётка, паста — сколько раз
     * ещё хватит. Пустых мест нет.
     */
    fun shelf(state: GameState): List<ShelfItem> =
        content.shop
            .filter { it.kind == ItemKind.CONSUMABLE }
            .mapNotNull { item -> stock(state, item.id).takeIf { it > 0 }?.let { ShelfItem(item, it) } }

    /**
     * Сколько раз ещё можно взять расходник [itemId]: куплено × [ShopItem.uses] (у еды — по одному)
     * минус взятое ([GameState.uses]). Один учёт на кухню и ванну; считается из фактов, как баланс,
     * отдельно запас нигде не хранится. Не расходник — 0.
     */
    fun stock(state: GameState, itemId: String): Int {
        val item = content.item(itemId)?.takeIf { it.kind == ItemKind.CONSUMABLE } ?: return 0
        val portions = item.uses ?: 1
        val bought = state.ledger.count { it.type == EntryType.PURCHASE && it.itemId == itemId }
        val taken = state.uses.filter { it.itemId == itemId }.sumOf { it.portions ?: portions }
        return maxOf(0, bought * portions - taken)
    }

    private fun takeOne(state: GameState, itemId: String) =
        ItemUse(periodNumber = state.currentPeriod.number, itemId = itemId, createdAt = clock())

    // ---------- Ванна ----------

    /** Мыло для полочки ванной: им намыливают, у него есть запас на несколько раз. */
    val soaps: List<ShopItem> get() = content.shop.filter { it.uses != null && !it.bathExtra && it.effect.hygiene > 0 }

    /** Полотенце, щётка, паста: у каждого свой шаг мытья и своя прибавка. */
    val bathExtras: List<ShopItem> get() = content.shop.filter { it.uses != null && it.bathExtra }

    /** Есть ли на полочке хоть какое-то мыло. */
    fun hasSoap(state: GameState): Boolean = soaps.any { stock(state, it.id) > 0 }

    /**
     * Шаг мытья предметом [itemId] с полочки или, если null, просто водой. Ничего не покупает:
     * тратит один раз предмета — и его пары ([ShopItem.pairWith]: щётка без пасты зубы не чистит) —
     * и прибавляет их чистоту. Шаги независимы: зубы можно почистить и без мыла. Денег не трогает,
     * поэтому план не нужен. Вода прибавляет мало и только до [ru.finney.pet.domain.model.BathRule.waterUpTo]:
     * отмыться можно только с мылом.
     */
    fun wash(state: GameState, itemId: String?): GameResult {
        if (state.sleepingSince != null) return reject(Rejection.Asleep)
        val bath = economy.bath
        // Зубы чистят и у чистого питомца — после мыла он как раз чистый, а шаг зубов идёт
        // следом. Не дают только при полной шкале: прибавлять некуда.
        val extra = itemId?.let(content::item)?.takeIf { it.bathExtra } != null
        val cleanFrom = if (extra) PetRules.STAT_MAX else bath.cleanFrom
        if (state.pet.hygiene >= cleanFrom) return reject(Rejection.AlreadyClean)
        if (itemId == null) {
            if (state.pet.hygiene >= bath.waterUpTo) return reject(Rejection.NeedsSoap)
            // Вода ничего не тратит — и записывать нечего.
            val water = minOf(bath.water, bath.waterUpTo - state.pet.hygiene)
            return ok(state.copy(pet = PetRules.apply(state.pet, StatEffect(hygiene = water))))
        }
        val item = content.item(itemId) ?: return reject(Rejection.UnknownItem(itemId))
        if (item !in soaps && item !in bathExtras) return reject(Rejection.NotWashable(itemId))
        val pair = item.pairWith?.let { content.item(it) ?: return reject(Rejection.UnknownItem(it)) }
        val used = listOfNotNull(item, pair)
        used.firstOrNull { stock(state, it.id) <= 0 }?.let { return reject(Rejection.NoStock(it.id)) }
        val hygiene = used.sumOf { it.effect.hygiene }
        return ok(state.copy(pet = PetRules.apply(state.pet, StatEffect(hygiene = hygiene)), uses = state.uses + used.map { takeOne(state, it.id) }))
    }

    // ---------- Сон ----------

    /**
     * Сколько спать от 0 до 100: по стадии ([PetRule.sleepMinutesByStage]) — малыш спит недолго,
     * взрослый дольше; в демо-режиме — секунды, чтобы эксперт увидел весь цикл. Стадия во сне
     * не меняется: уровень завершают только бодрствуя.
     */
    fun fullSleepMillis(state: GameState): Long {
        if (state.isDemo) return economy.pet.demoSleepSeconds * 1_000L
        val minutes = economy.pet.sleepMinutesByStage?.let { it[(stage(state) - 1).coerceIn(it.indices)] }
            ?: economy.pet.sleepMinutes
        return minutes * 60_000L
    }

    /**
     * Сколько спать с того сна, с каким уснул, до 100: доля от [fullSleepMillis].
     * Уснул с 37 — спит 63 % часа, с 0 — час. Округление вверх, чтобы к концу сон был ровно 100.
     */
    fun sleepMillis(state: GameState): Long {
        val missing = (PetRules.STAT_MAX - state.pet.energy).coerceAtLeast(0)
        return (fullSleepMillis(state) * missing + PetRules.STAT_MAX - 1) / PetRules.STAT_MAX
    }

    /** Когда питомец проснётся сам; null — не спит. */
    fun sleepEndsAt(state: GameState): Long? = state.sleepingSince?.plus(sleepMillis(state))

    /**
     * Сон сейчас. У спящего растёт с одной скоростью — 100 за [fullSleepMillis] — от того,
     * с чем уснул, до 100; в [GameState.pet] лежит значение на момент засыпания.
     */
    fun energyAt(state: GameState, now: Long = clock()): Int {
        val since = state.sleepingSince ?: return state.pet.energy
        val slept = (now - since).coerceAtLeast(0L)
        val gained = slept * PetRules.STAT_MAX / fullSleepMillis(state)
        return (state.pet.energy + gained).coerceAtMost(PetRules.STAT_MAX.toLong()).toInt()
    }

    /**
     * Уложить питомца спать в капсулу. Бесплатно и в любой фазе периода — сон не покупают.
     * Выспавшегося не уложить: ребёнок видит, что сон нужен, только когда шкала просела.
     * Сон — перерыв в игре: пока питомец спит, его не кормят и не моют, мини-игры
     * и завершение уровня закрыты ([Rejection.Asleep]). Ребёнок ждёт или будит.
     * Открыты только финансы и меню: план, магазин «хочется», копилка в плане, гардероб.
     */
    fun sleep(state: GameState): GameResult {
        if (state.sleepingSince != null) return reject(Rejection.Asleep)
        if (state.pet.energy >= PetRules.STAT_MAX) return reject(Rejection.NotSleepy)
        return ok(state.copy(sleepingSince = clock()))
    }

    /** Разбудить: сон — сколько успел набрать. Разбудили раньше — прибавка меньше. */
    fun wake(state: GameState): GameResult {
        if (state.sleepingSince == null) return reject(Rejection.NotAsleep)
        return ok(state.copy(pet = state.pet.copy(energy = energyAt(state)), sleepingSince = null))
    }

    /**
     * Ответ на карточку сна-загадки. Верно — сон сразу прибавляет [SleepCardRule.energyPerCorrect]:
     * прибавка ложится в сон на момент засыпания, и питомец просыпается раньше на ту же долю
     * [fullSleepMillis]. Неверно — ничего не меняется и не отнимается: это загадка во сне,
     * не экзамен (ТЗ п. 8.1). Сколько карточек за сон — считает экран, до [SleepCardRule.maxPerSleep].
     */
    fun answerSleepCard(state: GameState, correct: Boolean): GameResult {
        if (state.sleepingSince == null) return reject(Rejection.NotAsleep)
        if (!correct) return ok(state)
        val energy = (state.pet.energy + economy.sleepCards.energyPerCorrect).coerceAtMost(PetRules.STAT_MAX)
        return ok(state.copy(pet = state.pet.copy(energy = energy)))
    }

    /** Если срок сна вышел, питомец уже проснулся. Хранилище вызывает перед каждой командой. */
    fun settleSleep(state: GameState): GameState {
        val ends = sleepEndsAt(state) ?: return state
        return if (clock() >= ends) (wake(state) as GameResult.Ok).state else state
    }

    // ---------- Гардероб ----------

    /**
     * Надеть купленный аксессуар. Он сменяет то, что было в его слоте, а вещь из другого
     * слота остаётся: шляпа и очки носятся вместе. Бесплатно и в любой фазе периода.
     */
    fun wear(state: GameState, itemId: String): GameResult {
        val item = content.item(itemId) ?: return reject(Rejection.UnknownItem(itemId))
        if (item.kind != ItemKind.ACCESSORY) return reject(Rejection.NotWearable(itemId))
        if (!state.owns(itemId)) return reject(Rejection.NotOwned(itemId))
        return ok(state.wearing(item))
    }

    /** Снять [itemId]; null — снять всё. */
    fun takeOff(state: GameState, itemId: String? = null): GameResult = ok(
        state.copy(
            wornItemId = state.wornItemId.takeUnless { itemId == null || it == itemId },
            wornEyesId = state.wornEyesId.takeUnless { itemId == null || it == itemId },
        ),
    )

    private fun GameState.wearing(item: ShopItem): GameState = when (item.slot) {
        AccessorySlot.HEAD -> copy(wornItemId = item.id)
        AccessorySlot.EYES -> copy(wornEyesId = item.id)
    }

    /** Купленные и заработанные целями аксессуары в порядке магазина. */
    fun wardrobe(state: GameState): List<ShopItem> =
        content.shop.filter { it.kind == ItemKind.ACCESSORY && state.owns(it.id) }

    // ---------- Игрушки ----------

    /** Купленные игрушки в порядке магазина — то, что лежит в зале. */
    fun toys(state: GameState): List<ShopItem> =
        content.shop.filter { it.kind == ItemKind.TOY && state.owns(it.id) }

    /** Сколько длится сессия игры: после неё игрушки снова радуют. В демо-режиме — секунды. */
    fun playSessionMillis(state: GameState): Long =
        if (state.isDemo) economy.play.demoSessionSeconds * 1_000L else economy.play.sessionMinutes * 60_000L

    /** Сессия игры кончилась или не начиналась: следующая прибавка начнёт новую. */
    private fun playSessionOver(state: GameState, now: Long): Boolean {
        val since = state.playSince ?: return true
        return now - since >= playSessionMillis(state)
    }

    /**
     * Сколько настроения игрушки ещё могут дать сейчас. 0 — питомец наигрался:
     * ждать конца сессии или нового уровня.
     */
    fun playMoodLeft(state: GameState, now: Long = clock()): Int =
        if (playSessionOver(state, now)) economy.play.sessionMoodCap
        else maxOf(0, economy.play.sessionMoodCap - state.playMood)

    /**
     * Поиграть с игрушкой: [shakes] потряхиваний рядом с питомцем. Бесплатно и в любой фазе
     * периода — игрушка уже куплена. Прибавка к настроению — [ru.finney.pet.domain.model.PlayRule.moodPerShake]
     * за потряхивание, но не больше того, что осталось в сессии ([playMoodLeft]), и не выше 100.
     * Прибавки нет — состояние не меняется, сессия не начинается. Во сне не играют.
     */
    fun play(state: GameState, toyId: String, shakes: Int): GameResult {
        if (state.sleepingSince != null) return reject(Rejection.Asleep)
        val item = content.item(toyId) ?: return reject(Rejection.UnknownItem(toyId))
        if (item.kind != ItemKind.TOY) return reject(Rejection.NotPlayable(toyId))
        if (!state.owns(toyId)) return reject(Rejection.NotOwned(toyId))
        if (shakes <= 0) return reject(Rejection.InvalidAmount)
        val now = clock()
        val gain = minOf(
            shakes * economy.play.moodPerShake,
            playMoodLeft(state, now),
            PetRules.STAT_MAX - state.pet.mood,
        )
        if (gain <= 0) return ok(state)
        val fresh = playSessionOver(state, now)
        return ok(
            state.copy(
                pet = PetRules.apply(state.pet, StatEffect(mood = gain)),
                playMood = (if (fresh) 0 else state.playMood) + gain,
                playSince = if (fresh) now else state.playSince,
            ),
        )
    }

    // ---------- Накопления ----------

    /** Закрытую цель не выбрать, но выбранная раньше — на старом сохранении — остаётся. */
    fun selectGoal(state: GameState, goalId: String): GameResult {
        val goal = content.goal(goalId) ?: return reject(Rejection.UnknownGoal(goalId))
        if (!isUnlocked(state, goal)) return reject(Rejection.Locked(goal.unlockLevel))
        if (state.isGoalCompleted(goalId)) return reject(Rejection.GoalAlreadyCompleted)
        return ok(state.copy(activeGoalId = goalId))
    }

    fun deposit(state: GameState, amount: Int): GameResult {
        if (state.currentPeriod.phase != PeriodPhase.ACTIVE) return reject(Rejection.PlanNotConfirmed)
        if (amount <= 0) return reject(Rejection.InvalidAmount)
        val goalId = state.activeGoalId ?: return reject(Rejection.NoActiveGoal)
        if (amount > state.balance) return reject(Rejection.InsufficientFunds(amount, state.balance))
        val entry = entry(state, EntryType.SAVINGS_DEPOSIT, balanceDelta = -amount, savingsDelta = amount)
            .copy(goalId = goalId)
        return ok(state.copy(ledger = state.ledger + entry))
    }

    /** UI обязан показать [previewWithdraw] и получить отдельное подтверждение (ТЗ п. 2.5.7). */
    fun withdraw(state: GameState, amount: Int): GameResult {
        if (state.currentPeriod.phase != PeriodPhase.ACTIVE) return reject(Rejection.PlanNotConfirmed)
        if (amount <= 0) return reject(Rejection.InvalidAmount)
        val goalId = state.activeGoalId ?: return reject(Rejection.NoActiveGoal)
        val saved = state.goalSaved(goalId)
        if (amount > saved) return reject(Rejection.InsufficientSavings(amount, saved))
        val entry = entry(state, EntryType.SAVINGS_WITHDRAW, balanceDelta = amount, savingsDelta = -amount)
            .copy(goalId = goalId)
        return ok(state.copy(ledger = state.ledger + entry))
    }

    fun completeGoal(state: GameState): GameResult {
        val goal = state.activeGoalId?.let(content::goal) ?: return reject(Rejection.NoActiveGoal)
        val saved = state.goalSaved(goal.id)
        if (saved < goal.price) return reject(Rejection.GoalNotReached(goal.price, saved))
        val entry = entry(state, EntryType.GOAL_COMPLETE, balanceDelta = 0, savingsDelta = -goal.price)
            .copy(goalId = goal.id, itemId = goal.reward)
        // Награда сразу на питомце, как купленная шляпа: ребёнок видит, на что копил.
        val reward = goal.reward?.let(content::item)?.takeIf { it.kind == ItemKind.ACCESSORY }
        val completed = state.copy(
            pet = PetRules.apply(state.pet, StatEffect(mood = goal.moodBonus)),
            activeGoalId = null,
            ledger = state.ledger + entry,
        )
        return ok(reward?.let { completed.wearing(it) } ?: completed)
    }

    // ---------- Доход ----------

    fun addParentBonus(state: GameState, amount: Int): GameResult {
        val rule = economy.parentBonus
        if (amount <= 0) return reject(Rejection.InvalidAmount)
        if (amount % rule.step != 0) return reject(Rejection.BonusNotOnStep(rule.step))
        val given = state.ledger
            .filter { it.type == EntryType.PARENT_BONUS && it.periodNumber == state.currentPeriod.number }
            .sumOf { it.balanceDelta }
        val left = rule.maxPerPeriod - given
        if (amount > left) return reject(Rejection.BonusLimitExceeded(left))
        return ok(state.copy(ledger = state.ledger + income(state, EntryType.PARENT_BONUS, amount)))
    }

    /** [seed] — зерно, с которым экран показал игру ([newTaskSeed]); сохраняется в попытке. */
    fun submitTask(state: GameState, taskId: String, input: TaskInput, seed: Long? = null): TaskResult {
        if (state.sleepingSince != null) return TaskResult.Rejected(Rejection.Asleep)
        val task = task(taskId, seed) ?: return TaskResult.Rejected(Rejection.UnknownTask(taskId))
        if (!isTaskAvailable(state, task)) return TaskResult.Rejected(Rejection.TaskLocked)
        // Мини-игры — часть уровня, а уровень начинается с плана: без него игры
        // превращались в случайный перебор ради монет.
        if (state.currentPeriod.phase != PeriodPhase.ACTIVE) return TaskResult.Rejected(Rejection.PlanNotConfirmed)
        val evaluation = when (val e = TaskEngines.evaluate(task, input)) {
            is TaskEvaluation.Invalid -> return TaskResult.Rejected(Rejection.InvalidTaskInput(e.error))
            is TaskEvaluation.Done -> e
        }

        val previous = state.attempts.filter { it.taskId == taskId }
        val rates = task.reward ?: economy.taskReward
        val reward = when (evaluation.outcome) {
            TaskOutcome.SUCCESS -> if (previous.none { it.outcome == TaskOutcome.SUCCESS }) rates.success else 0
            TaskOutcome.FAIL -> if (previous.isEmpty()) rates.fail else 0
        }
        val bonusEarned = evaluation.outcome == TaskOutcome.SUCCESS && evaluation.bonus
        val bonus = if (bonusEarned && previous.none { it.bonus }) rates.bonus else 0
        val attempt = TaskAttempt(state.currentPeriod.number, taskId, evaluation.outcome, reward + bonus, clock(), seed, bonusEarned)
        val ledger = if (reward + bonus > 0) {
            state.ledger + income(state, EntryType.TASK_REWARD, reward + bonus).copy(taskId = taskId)
        } else {
            state.ledger
        }
        return TaskResult.Submitted(
            state = state.copy(ledger = ledger, attempts = state.attempts + attempt),
            outcome = evaluation.outcome,
            details = evaluation.details,
            reward = reward + bonus,
            bonus = bonus,
        )
    }

    // ---------- Период ----------

    /** Итоги закрытого периода — `periods[size - 2].result` в новом состоянии. */
    fun closePeriod(state: GameState): GameResult {
        if (state.sleepingSince != null) return reject(Rejection.Asleep)
        val period = state.currentPeriod
        val plan = period.plan
        if (period.phase != PeriodPhase.ACTIVE || plan == null) return reject(Rejection.PlanNotConfirmed)

        val facts = PeriodRules.facts(state.ledger, period.number)
        val needsCovered = PetRules.needsCovered(state.pet, economy.pet)
        val planMatched = PeriodRules.planMatched(plan, facts, economy.planTolerance)
        val savingsAdded = facts.savings > 0
        val gamePassed = levelGamePassed(state, period)
        val successfulTasks = firstSuccessesIn(state, period.number)
        val result = PeriodResult(
            facts = facts,
            needsCovered = needsCovered,
            planMatched = planMatched,
            savingsAdded = savingsAdded,
            gamePassed = gamePassed,
            successfulTasks = successfulTasks,
            points = Progression.periodPoints(needsCovered, planMatched, savingsAdded, successfulTasks, economy.points),
            passed = Progression.decide(
                needsCovered, planMatched, savingsAdded, gamePassed,
                toPass = conditionsToPass(state),
                gameRequired = levelGameRequired(state),
            ),
        )
        val closed = state.withCurrentPeriod(period.copy(phase = PeriodPhase.CLOSED, result = result))
        return ok(openPeriod(closed))
    }

    /** Задания, впервые пройденные успешно именно в этом периоде — повтор очков не даёт. */
    private fun firstSuccessesIn(state: GameState, periodNumber: Int): Int =
        state.attempts
            .filter { it.outcome == TaskOutcome.SUCCESS }
            .groupBy { it.taskId }
            .count { (_, attempts) -> attempts.first().periodNumber == periodNumber }

    private fun openPeriod(state: GameState): GameState {
        val number = state.periods.size + 1
        val stage = stage(state)
        val opened = state.copy(
            pet = PetRules.decay(state.pet, economy.decay(stage)),
            // Новый уровень — новая сессия игры: игрушки снова радуют в полную силу.
            playMood = 0,
            playSince = null,
            periods = state.periods + Period(number = number, stage = stage, phase = PeriodPhase.PLANNING),
        )
        val income = entry(opened, EntryType.INCOME, balanceDelta = economy.income(stage))
        val withGame = opened.withCurrentPeriod(opened.currentPeriod.copy(levelTaskId = pickLevelGame(opened)))
        return withGame.copy(ledger = withGame.ledger + income)
    }

    /**
     * Выдать игру уровня периоду, открытому до её появления (база до версии 6).
     * Уже выдана — состояние не меняется.
     */
    fun assignLevelGame(state: GameState): GameResult {
        val period = state.currentPeriod
        if (period.levelTaskId != null || period.phase == PeriodPhase.CLOSED) return ok(state)
        return ok(state.withCurrentPeriod(period.copy(levelTaskId = pickLevelGame(state))))
    }

    /**
     * Игра уровня: случайная из открытых, в варианте по уровню питомца. Сначала —
     * из тех, что ещё не выпадали ни на одном уровне: игр больше, чем уровней, и
     * иначе часть из них могла бы не выпасть за всю игру. Когда выпали все, та же
     * игра два уровня подряд не выпадает — разве что открыта всего одна.
     */
    private fun pickLevelGame(state: GameState): String? {
        val earlier = state.periods.dropLast(1).map { it.levelTaskId?.let(content::task)?.seriesId }
        val previous = earlier.lastOrNull()
        val open = currentTasks(state).filter { isTaskAvailable(state, it) }
        val candidates = open.filter { it.seriesId !in earlier }
            .ifEmpty { open.filter { it.seriesId != previous } }
            .ifEmpty { open }
        return candidates.randomOrNull(random)?.id
    }

    // ---------- Служебное ----------

    private fun entry(state: GameState, type: EntryType, balanceDelta: Int, savingsDelta: Int = 0) = LedgerEntry(
        periodNumber = state.currentPeriod.number,
        type = type,
        balanceDelta = balanceDelta,
        savingsDelta = savingsDelta,
        createdAt = clock(),
    )

    /** Доход после подтверждения плана — незапланированный. */
    private fun income(state: GameState, type: EntryType, amount: Int) =
        entry(state, type, balanceDelta = amount).copy(unplanned = state.currentPeriod.phase == PeriodPhase.ACTIVE)

    private fun GameState.withCurrentPeriod(period: Period) = copy(periods = periods.dropLast(1) + period)

    private fun ok(state: GameState) = GameResult.Ok(state)

    /** Еда: то, что сразу съедают на кухне. Мыло на полочку купить можно и во сне — мыть им будут потом. */
    private val ShopItem.feedsOrWashes: Boolean get() = uses == null && (effect.satiety > 0 || effect.hygiene > 0)

    private fun reject(reason: Rejection) = GameResult.Rejected(reason)
}
