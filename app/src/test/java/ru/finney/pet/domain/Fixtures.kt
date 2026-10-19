package ru.finney.pet.domain

import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.GameResult
import ru.finney.pet.domain.game.Rejection
import ru.finney.pet.domain.game.TaskResult
import ru.finney.pet.domain.model.AccessorySlot
import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.EconomyConfig
import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.domain.model.GameState
import ru.finney.pet.domain.model.Goal
import ru.finney.pet.domain.model.GoalSliderTask
import ru.finney.pet.domain.model.ItemKind
import ru.finney.pet.domain.model.ParentBonusRule
import ru.finney.pet.domain.model.PetRule
import ru.finney.pet.domain.model.PetStats
import ru.finney.pet.domain.model.PlayRule
import ru.finney.pet.domain.model.PointsRule
import ru.finney.pet.domain.model.ReminderRule
import ru.finney.pet.domain.model.ShopItem
import ru.finney.pet.domain.model.StatEffect
import ru.finney.pet.domain.model.TaskReward
import ru.finney.pet.domain.model.TaskTheme
import ru.finney.pet.domain.tasks.TaskInput
import kotlin.random.Random

/**
 * Контент для тестов правил. Не зависит от assets: @vsvsokol меняет числа в JSON,
 * а тесты правил от этого не краснеют. Реальный контент проверяет ContentTest.
 */
object Fixtures {

    val economy = EconomyConfig(
        incomeByStage = listOf(50, 60, 70),
        taskReward = TaskReward(success = 15, fail = 5),
        parentBonus = ParentBonusRule(step = 5, maxPerPeriod = 20),
        planTolerance = 5,
        // Тесты правил проверяют каждое направление отдельно, поэтому пустые части
        // здесь разрешены. Требование ТЗ «во всех трёх» — отдельный тест с planDirections = 3.
        planDirections = 0,
        points = PointsRule(needsCovered = 2, planMatched = 2, savingsAdded = 2, taskSuccess = 1, taskSuccessMaxPerPeriod = 2),
        conditionsToPass = 2,
        demoConditionsToPass = 1,
        demoLevelGameRequired = false,
        maxLevel = 9,
        stageStartLevels = listOf(1, 3, 5),
        pet = PetRule(
            start = PetStats(70, 70, 70, energy = 90),
            decayByStage = listOf(StatEffect(40, 30, 20, 60), StatEffect(50, 35, 25, 65), StatEffect(60, 40, 30, 70)),
            needsThreshold = 50,
            emotionLow = 30,
            emotionHappy = 60,
            sleepMinutes = 60,
            demoSleepSeconds = 10,
        ),
        play = PlayRule(moodPerShake = 2, sessionMoodCap = 20, sessionMinutes = 60, demoSessionSeconds = 30),
        reminders = ReminderRule(everyHours = 24, demoReminderMinutes = 2),
    )

    val shop = listOf(
        ShopItem("apple", "Яблоко", 10, Category.NEEDS, effect = StatEffect(satiety = 20)),
        ShopItem("bowl", "Миска", 20, Category.NEEDS, effect = StatEffect(satiety = 45)),
        ShopItem("soap", "Мыло", 10, Category.NEEDS, effect = StatEffect(hygiene = 35)),
        ShopItem("towel", "Полотенце", 15, Category.NEEDS, effect = StatEffect(hygiene = 50)),
        // Мыло на полочку — без него контент не проходит валидатор. Нарочно дорогое: подсказка
        // плана выбирает самое дешёвое, и числа в тестах правил от него не меняются. Ванну проверяет BathTest.
        ShopItem("soap_pack", "Мыло на полочку", 40, Category.NEEDS, effect = StatEffect(hygiene = 50), uses = 2),
        ShopItem("candy", "Конфета", 5, Category.WANTS, effect = StatEffect(mood = 10)),
        ShopItem("ball", "Мячик", 15, Category.WANTS, effect = StatEffect(mood = 30)),
        ShopItem("lamp", "Лампа", 25, Category.WANTS, effect = StatEffect(mood = 50)),
        ShopItem("hat", "Шапка", 30, Category.WANTS, ItemKind.ACCESSORY, StatEffect(mood = 20)),
        ShopItem("glasses", "Очки", 10, Category.WANTS, ItemKind.ACCESSORY, StatEffect(mood = 10), AccessorySlot.EYES),
        ShopItem("teddy", "Мишка", 20, Category.WANTS, ItemKind.TOY, StatEffect(mood = 10)),
        // Награда за цель «Корона»: в магазине не продаётся.
        ShopItem("crown_hat", "Корона", 50, Category.WANTS, ItemKind.ACCESSORY, StatEffect(mood = 20)),
    )

    val goals = listOf(
        Goal("crown", "Корона", 50, moodBonus = 20, reward = "crown_hat"),
        Goal("bike", "Велосипед", 100, moodBonus = 30),
    )

    /** t1..t10 — одинаковые простые задания: взнос 10 — успех, 5 — неудача. t10 открывается с 3-го периода. */
    val tasks = (1..10).map { n ->
        GoalSliderTask(
            id = "t$n",
            theme = TaskTheme.SAVINGS,
            title = "Задание $n",
            intro = "",
            explainOk = "",
            explainFail = "",
            unlockPeriod = if (n == 10) 3 else 1,
            goalPrice = 30,
            periods = 3,
            incomePerPeriod = 50,
        )
    }

    val content = GameContent(economy, shop, goals, tasks)

    val success = TaskInput.Deposit(10)
    val failure = TaskInput.Deposit(5)

    fun game(): Game {
        var now = 0L
        return Game(content, Random(7)) { ++now }
    }
}

fun GameResult.state(): GameState = when (this) {
    is GameResult.Ok -> state
    is GameResult.Rejected -> throw AssertionError("Ожидался успех, получен отказ: $reason")
}

fun GameResult.reason(): Rejection = when (this) {
    is GameResult.Ok -> throw AssertionError("Ожидался отказ, команда выполнена")
    is GameResult.Rejected -> reason
}

fun TaskResult.submitted(): TaskResult.Submitted = when (this) {
    is TaskResult.Submitted -> this
    is TaskResult.Rejected -> throw AssertionError("Ожидался исход задания, получен отказ: $reason")
}

/** Докупить нужное до порога самым простым способом. */
fun Game.coverNeeds(start: GameState): GameState {
    var s = start
    val threshold = Fixtures.economy.pet.needsThreshold
    while (s.pet.satiety < threshold) s = buy(s, if (threshold - s.pet.satiety <= 20) "apple" else "bowl").state()
    while (s.pet.hygiene < threshold) s = buy(s, if (threshold - s.pet.hygiene <= 35) "soap" else "towel").state()
    if (s.pet.energy < threshold) s = sleepFully(s)
    return s
}

/** Период с максимумом очков: нужное, план, копилка, игра уровня и два новых успешных задания. */
fun Game.playPerfectPeriod(start: GameState): GameState {
    var s = start
    if (s.activeGoalId == null) s = selectGoal(s, "bike").state()
    s = confirmPlan(s, needs = needsHint(s)!!, wants = 0, savings = 10).state()
    // Копилка в плане — обещание: отложить нужно самому.
    s = deposit(s, 10).state()
    // Игра уровня — первое из двух новых заданий (если её уже проходили, новых добирается два).
    s = passLevelGame(s)
    val done = s.attempts.filter { it.periodNumber < s.currentPeriod.number }.map { it.taskId }.toSet()
    val fresh = s.attempts.count { it.periodNumber == s.currentPeriod.number && it.taskId !in done }
    val tried = s.attempts.map { it.taskId }.toSet()
    Fixtures.tasks.map { it.id }.filter { it !in tried && isTaskAvailable(s, content(it)) }.take(2 - fresh).forEach {
        s = submitTask(s, it, Fixtures.success).submitted().state
    }
    s = coverNeeds(s)
    return closePeriod(s).state()
}

private fun content(id: String) = Fixtures.content.task(id)!!

/** Пройти игру уровня текущего периода. Все задания фикстур проходятся одним [Fixtures.success]. */
fun Game.passLevelGame(start: GameState): GameState {
    val taskId = start.currentPeriod.levelTaskId ?: return start
    return submitTask(start, taskId, Fixtures.success).submitted().state
}

/** Новая игра с подтверждённым пустым планом: мини-игры — только после плана. */
fun Game.newPlannedGame(isDemo: Boolean = false): GameState = confirmPlan(newGame(isDemo), 0, 0, 0).state()

/** Уложить и дать выспаться целиком: время засыпания сдвинуто на полный сон назад. */
fun Game.sleepFully(start: GameState): GameState {
    val asleep = sleep(start).state()
    return wake(asleep.copy(sleepingSince = asleep.sleepingSince!! - sleepMillis(asleep))).state()
}
