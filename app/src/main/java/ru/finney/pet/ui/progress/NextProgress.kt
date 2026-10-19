package ru.finney.pet.ui.progress

import ru.finney.pet.domain.model.GameContent

/** Ближайшее изменение из контента, а не обещание нового внешнего вида питомца. */
data class NextProgress(val level: Int, val levelsLeft: Int, val changes: List<String>)

internal fun stageName(stage: Int): String = listOf(
    "малыш", "карапуз", "непоседа", "школьник", "подросток", "взрослый",
).getOrNull(stage - 1) ?: stage.toString()

internal fun nextProgress(content: GameContent, level: Int, period: Int, isDemo: Boolean): NextProgress? {
    val maxLevel = content.economy.maxLevel
    if (level >= maxLevel) return null
    val changes = mutableListOf<Pair<Int, String>>()
    content.economy.stageStartLevels.forEachIndexed { index, target ->
        if (target in (level + 1)..maxLevel) {
            changes += target to "Новая стадия: ${stageName(index + 1)}."
        }
    }
    content.taskSeries.forEach { variants ->
        variants.forEachIndexed { index, task ->
            val target = task.unlockLevel
            // Не обещаем доступ к заданию раньше его периода, даже если уровня уже хватит.
            if (target in (level + 1)..maxLevel && (!isDemo || index > 0) &&
                (isDemo || task.unlockPeriod <= period + target - level)) {
                val label = if (index == 0) "Новая мини-игра" else "Более сложная мини-игра"
                changes += target to "$label: «${task.title}»."
            }
        }
    }
    if (changes.isEmpty()) changes += maxLevel to "Самый высокий уровень питомца."
    val target = changes.minOf { it.first }
    return NextProgress(target, target - level, changes.filter { it.first == target }.map { it.second }.distinct())
}
