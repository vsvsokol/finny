package ru.finney.pet.ui.goals

import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.domain.model.GameState
import ru.finney.pet.domain.model.Goal
import ru.finney.pet.ui.components.ActionFeedback
import ru.finney.pet.ui.components.changesBetween
import ru.finney.pet.ui.isReserveMode

/**
 * Ступень цели — одна лестница на магазин, копилку и главный: открылось → копим →
 * накопили → твоё. Каждый экран рисует ступень по-своему, но считает её отсюда,
 * иначе шляпа в магазине и в копилке разойдутся.
 */
enum class GoalStage {
    /** Откроется не на следующем уровне: не показываем, чтобы не вываливать всё сразу. */
    HIDDEN,

    /** Откроется на следующем уровне: видна бледной, с замком. */
    SOON,

    /** Открыта, но копим не на неё. */
    OPEN,

    /** Копим на неё сейчас. */
    SAVING,

    /** Накоплено, осталось забрать. */
    READY,

    /** Забрали: вещь у питомца. */
    OWNED,
}

/** В демо открыто всё ([Game.isUnlocked]), поэтому ступени «скоро» там не бывает (ТЗ п. 2.5.13). */
internal fun goalStage(game: Game, state: GameState, goal: Goal): GoalStage = when {
    state.isGoalCompleted(goal.id) -> GoalStage.OWNED
    // Выбранная раньше цель остаётся, даже если на старом сохранении она ещё закрыта.
    goal.id == state.activeGoalId -> if (state.goalSaved(goal.id) >= goal.price) GoalStage.READY else GoalStage.SAVING
    game.isUnlocked(state, goal) -> GoalStage.OPEN
    goal.unlockLevel == game.level(state) + 1 -> GoalStage.SOON
    else -> GoalStage.HIDDEN
}

/**
 * Цель достигнута: вещь переходит питомцу и сразу надета. Итог — окном с самой
 * вещью и одной фразой: плейтест 29.09 назвал прежнее окно захламлённым.
 * Один текст на все места, где можно нажать «Забрать».
 */
internal fun goalCompletedFeedback(game: Game, content: GameContent, before: GameState, after: GameState): ActionFeedback {
    val goal = before.activeGoalId?.let(content::goal)
    val returned = after.balance - before.balance
    val next = if (game.isReserveMode(after)) {
        "Все награды собраны! Теперь можно откладывать монеты в запас."
    } else {
        "Выбери новую цель в копилке."
    }
    return ActionFeedback(
        title = "${goal?.label ?: "Цель"} — твоя!",
        lines = changesBetween(before, after),
        why = buildList {
            add(if (goal?.reward != null) "Копил — и получилось! Награда уже на питомце." else "Копил — и получилось!")
            if (returned > 0) add("Остаток $returned монет вернулся в кошелёк.")
            add(next)
        }.joinToString(" "),
        next = next,
        itemId = goal?.reward,
    )
}
