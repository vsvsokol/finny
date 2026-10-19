package ru.finney.pet.ui.period

import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.ui.progress.NextProgress
import ru.finney.pet.ui.progress.nextProgress
import ru.finney.pet.ui.progress.stageName

/** Новое именно после этого перехода; старые итоги не выдают текущее будущее за свою награду. */
data class PeriodGrowth(val changes: List<String>, val next: NextProgress?)

internal fun periodGrowth(
    content: GameContent,
    before: Int,
    after: Int,
    period: Int,
    isDemo: Boolean,
    isLatest: Boolean,
): PeriodGrowth? {
    if (!isLatest || after <= before) return null
    val changes = buildList {
        content.economy.stageStartLevels.forEachIndexed { index, level ->
            if (level in (before + 1)..after) add("Новая стадия: ${stageName(index + 1)}.")
        }
        content.taskSeries.forEach { variants ->
            // Выбор варианта такой же, как у Game.currentVariant, включая первую версию в демо.
            val previous = variants.lastOrNull { it.unlockLevel <= before } ?: variants.first()
            val current = variants.lastOrNull { it.unlockLevel <= after } ?: variants.first()
            val wasAvailable = isDemo || (previous.unlockLevel <= before && previous.unlockPeriod <= period)
            val available = isDemo || (current.unlockLevel <= after && current.unlockPeriod <= period + 1)
            if (available && (!wasAvailable || previous.id != current.id)) {
                val label = if (wasAvailable) "Стала сложнее мини-игра" else "Открылась мини-игра"
                add("$label: «${current.title}».")
            }
        }
    }.distinct()
    return PeriodGrowth(changes, nextProgress(content, after, period + 1, isDemo))
}
