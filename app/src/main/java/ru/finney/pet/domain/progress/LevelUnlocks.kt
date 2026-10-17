package ru.finney.pet.domain.progress

import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.domain.model.Goal
import ru.finney.pet.domain.model.ShopItem
import ru.finney.pet.domain.model.TaskDefinition

/**
 * Что открывается на уровне [level]. docs/economy.md, раздел 8.
 *
 * [games] — новые игры (первый вариант серии), [harderGames] — новые варианты уже открытых,
 * [items] — что начинает продаваться в магазине, [goals] — новые цели, [title] — новое звание,
 * если на этом уровне начинается стадия. Всё выводится из номера уровня и контента, в базе
 * не хранится: сохранение на любом уровне сразу видит всё, что открыто до него.
 */
data class LevelUnlocks(
    val level: Int,
    val games: List<TaskDefinition> = emptyList(),
    val harderGames: List<TaskDefinition> = emptyList(),
    val items: List<ShopItem> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val title: String? = null,
) {
    /** На уровне не открылось ничего, кроме денег и сложности ухода. */
    val isEmpty: Boolean get() = games.isEmpty() && harderGames.isEmpty() && items.isEmpty() && goals.isEmpty() && title == null
}

object Unlocks {

    fun at(level: Int, content: GameContent): LevelUnlocks {
        val (games, harder) = content.taskSeries.flatMap { variants ->
            variants.withIndex().filter { it.value.unlockLevel == level }.map { (i, task) -> (i == 0) to task }
        }.partition { it.first }
        return LevelUnlocks(
            level = level,
            games = games.map { it.second },
            harderGames = harder.map { it.second },
            // Награды за цели не продаются — они открываются вместе с целью.
            items = content.forSale.filter { it.unlockLevel == level },
            goals = content.goals.filter { it.unlockLevel == level },
            title = title(level, content).takeIf { level in content.economy.stageStartLevels },
        )
    }

    /** Звание на уровне [level]: по стадии. null — званий в контенте нет. */
    fun title(level: Int, content: GameContent): String? =
        content.economy.stageTitles.getOrNull(Progression.stage(level, content.economy) - 1)

    /** Ближайший уровень после [level], на котором что-то открывается; null — дальше ничего нового. */
    fun next(level: Int, content: GameContent): LevelUnlocks? =
        ((level + 1)..content.economy.maxLevel).asSequence().map { at(it, content) }.firstOrNull { !it.isEmpty }
}
