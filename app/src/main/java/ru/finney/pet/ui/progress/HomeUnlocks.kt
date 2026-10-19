package ru.finney.pet.ui.progress

import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.domain.model.GameState

/** Ближайшее реальное открытие: в демо доступ уже есть, но сложность и звания ещё растут. */
internal fun homeUnlocks(game: Game, content: GameContent, state: GameState): NextProgress? {
    val level = game.level(state)
    val next = game.nextUnlocks(state) ?: return null
    return game.unlocksBetween(next.level - 1, content.economy.maxLevel).firstNotNullOfOrNull { unlocks ->
        val shown = unlocks.copy(
            games = if (state.isDemo) emptyList() else unlocks.games.filter {
                it.unlockPeriod <= state.currentPeriod.number + unlocks.level - level
            },
            harderGames = unlocks.harderGames.filter {
                state.isDemo || it.unlockPeriod <= state.currentPeriod.number + unlocks.level - level
            },
            items = if (state.isDemo) emptyList() else unlocks.items,
            goals = if (state.isDemo) emptyList() else unlocks.goals,
        )
        val lines = unlockLines(shown, future = true, isDemo = state.isDemo)
        lines.takeIf { it.isNotEmpty() }?.let { NextProgress(unlocks.level, unlocks.level - level, it) }
    }
}
