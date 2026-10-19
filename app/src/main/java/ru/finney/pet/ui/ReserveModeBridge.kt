package ru.finney.pet.ui

import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.model.GameState

// Временная прослойка. Экраны уже умеют показывать «запас» — копилку после всех
// целей, — но в ядре его пока нет: правки лежат на ревью в proposal/goal-surplus.
// До решения запаса нет никогда. Примут — методы Game перекроют эти функции
// (компилятор предупредит, что расширение затенено), и файл останется удалить.

/** После всех целей копилка становится запасом. Пока ядро этого не умеет — никогда. */
internal fun Game.isReserveMode(@Suppress("UNUSED_PARAMETER") state: GameState): Boolean = false

/** Сколько лежит в копилке: на активной цели, а без цели — ничего. */
internal fun Game.savingsBalance(state: GameState): Int = state.activeGoalId?.let(state::goalSaved) ?: 0
