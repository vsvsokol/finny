package ru.finney.pet.ui.progress

import ru.finney.pet.ui.goals.rewardArt
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.GoalProgress
import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.domain.model.GameState
import ru.finney.pet.domain.progress.LevelUnlocks
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.room.itemArt

enum class NodeStatus { DONE, CURRENT, FUTURE }

/**
 * Уровень на карте «Мой путь». [stars] — сколько из трёх условий выполнено в периоде,
 * которым уровень пройден, как звёзды на итоге уровня. [resultPeriod] — этот период,
 * чтобы открыть его итог. [failedTries] — периоды на этом уровне, которые не засчитались.
 */
data class LevelNode(
    val level: Int,
    val status: NodeStatus,
    val stars: Int = 0,
    val resultPeriod: Int? = null,
    val failedTries: Int = 0,
    val unlocks: List<String> = emptyList(),
    val chips: List<UnlockChip> = emptyList(),
    /** Рисунки наград целей, которые открываются на уровне, — у узла на карте. */
    val goalArt: List<Int> = emptyList(),
)

/** Открытие фишкой: рисунок или значок и одно-два слова вместо строки текста. */
data class UnlockChip(val label: String, val art: Int? = null, val icon: FinneyIcons? = null)

/** Строка про уход, где шкала ниже порога: «Сытость: 40 из 100». */
data class CareShort(val label: String, val value: Int)

/**
 * Что осталось до следующего уровня — всегда про текущий уровень, а не про тот,
 * что ребёнок открыл на карте. [nextLevel] null — выше уровней нет.
 */
data class CurrentLevel(
    val level: Int,
    val nextLevel: Int?,
    val planConfirmed: Boolean,
    /** Игра уровня; null — в этом периоде её нет. */
    val gameTitle: String?,
    val gamePassed: Boolean,
    val gameRequired: Boolean,
    val needsCovered: Boolean,
    val planMatched: Boolean,
    val savingsAdded: Boolean,
    val met: Int,
    val toPass: Int,
    /** Все условия на месте: завершить уровень можно на главном. */
    val ready: Boolean,
    val asleep: Boolean,
    val petName: String,
    /** Сколько ещё положить в копилку по плану. Не то же, что условие «Отложено в копилку». */
    val savingsPlanLeft: Int,
    /** Перерасход сверх плана с учётом допуска и заработанного после плана. */
    val overspent: Int,
    val careShort: List<CareShort>,
    val needsThreshold: Int,
    val goal: GoalProgress?,
)

internal fun levelNodes(game: Game, content: GameContent, state: GameState): List<LevelNode> {
    val current = game.level(state)
    // Период играли на уровне, который был до него: проигранный период уровень не двигает.
    val played = state.periods.filter { it.result != null }.groupBy { game.levelAfter(state, it.number - 1) }
    return (1..content.economy.maxLevel).map { level ->
        val periods = played[level].orEmpty()
        val passed = periods.firstOrNull { game.isPassed(it) == true }
        val status = when {
            level == current -> NodeStatus.CURRENT
            level < current -> NodeStatus.DONE
            else -> NodeStatus.FUTURE
        }
        val unlocks = game.unlocksAt(level)
        LevelNode(
            level = level,
            status = status,
            stars = passed?.result?.let { listOf(it.needsCovered, it.planMatched, it.savingsAdded).count { met -> met } } ?: 0,
            resultPeriod = passed?.number,
            failedTries = periods.count { game.isPassed(it) == false },
            unlocks = unlockLines(unlocks, future = level > current, isDemo = state.isDemo),
            chips = unlockChips(unlocks),
            goalArt = unlocks.goals.mapNotNull { goal -> goal.rewardArt },
        )
    }
}

/**
 * Открытия уровня словами. Открытие — доступ, а не подарок: вещь появляется в магазине,
 * цель — в списке целей, покупать и копить всё равно самому. В демо-режиме игры, вещи
 * и цели открыты сразу, по уровню растут только сложность игр и звание.
 */
internal fun unlockLines(unlocks: LevelUnlocks, future: Boolean, isDemo: Boolean): List<String> {
    val ahead = future && !isDemo
    return buildList {
        unlocks.games.forEach { add(if (ahead) "Откроется мини-игра «${it.title}»" else "Мини-игра «${it.title}»") }
        unlocks.harderGames.forEach {
            add(if (future) "Откроется более сложная мини-игра «${it.title}»" else "Более сложная мини-игра «${it.title}»")
        }
        unlocks.items.forEach { add(if (ahead) "Откроется в магазине: ${it.label}" else "В магазине: ${it.label}") }
        unlocks.goals.forEach { add(if (ahead) "Станет доступна цель «${it.label}»" else "Цель «${it.label}»") }
        unlocks.title?.let { add(if (future) "Новое звание: $it" else "Звание: $it") }
    }
}

/** Те же открытия, что в [unlockLines], но фишками для окна уровня на карте. */
internal fun unlockChips(unlocks: LevelUnlocks): List<UnlockChip> = buildList {
    unlocks.games.forEach { add(UnlockChip("«${it.title}»", icon = FinneyIcons.Star)) }
    unlocks.harderGames.forEach { add(UnlockChip("«${it.title}» сложнее", icon = FinneyIcons.Star)) }
    unlocks.items.forEach { add(UnlockChip(it.label, art = itemArt(it.id), icon = FinneyIcons.Cart)) }
    unlocks.goals.forEach { add(UnlockChip("Цель: ${it.label}", art = it.rewardArt, icon = FinneyIcons.Piggy)) }
    unlocks.title?.let { add(UnlockChip("Звание: $it")) }
}

internal fun currentLevel(game: Game, content: GameContent, state: GameState, petName: String, now: Long = System.currentTimeMillis()): CurrentLevel {
    val level = game.level(state)
    val check = game.levelCheck(state)
    val report = game.planReport(state)
    val tolerance = content.economy.planTolerance
    val threshold = content.economy.pet.needsThreshold
    val energy = game.energyAt(state, now)
    return CurrentLevel(
        level = level,
        nextLevel = (level + 1).takeIf { it <= content.economy.maxLevel },
        planConfirmed = check.planConfirmed,
        gameTitle = check.levelTaskId?.let { content.task(it)?.title },
        gamePassed = check.gamePassed,
        gameRequired = check.gameRequired,
        needsCovered = check.needsCovered,
        planMatched = check.planMatched,
        savingsAdded = check.savingsAdded,
        met = check.met,
        toPass = check.toPass,
        ready = check.planConfirmed && check.willPass,
        asleep = game.sleepEndsAt(state)?.let { it > now } == true,
        petName = petName,
        savingsPlanLeft = report?.let { (it.plan.savings - it.facts.savings).coerceAtLeast(0) } ?: 0,
        // Как на главном: заработанное после плана можно тратить, мелочь в пределах допуска не считается.
        overspent = report?.let { r -> (r.overspend - r.facts.unplannedIncome).takeIf { it > tolerance } } ?: 0,
        careShort = listOf(
            CareShort("Сытость", state.pet.satiety),
            CareShort("Чистота", state.pet.hygiene),
            CareShort("Энергия", energy),
        ).filter { it.value < threshold },
        needsThreshold = threshold,
        goal = game.goalProgress(state),
    )
}
