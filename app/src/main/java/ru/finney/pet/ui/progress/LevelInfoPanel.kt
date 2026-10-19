package ru.finney.pet.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyPanel
import ru.finney.pet.ui.components.fadingScroll
import ru.finney.pet.ui.theme.FinneyInk

/**
 * Окно уровня, выбранного на карте. Про будущий уровень — только открытия: какая игра
 * уровня выпадет, решается, когда до него дойдёшь, и заранее её не обещаем.
 */
@Composable
internal fun LevelInfoPanel(
    node: LevelNode,
    current: CurrentLevel?,
    isDemo: Boolean,
    onOpenResult: (periodNumber: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        FinneyPanel(title = "Уровень ${node.level}", onClose = onDismiss) {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).fadingScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (node.status) {
                    NodeStatus.DONE -> {
                        Body("Пройден. Звёзд: ${node.stars} из 3.")
                        if (node.failedTries > 0) Body("Не засчитались попытки: ${node.failedTries}.")
                        Unlocks("Что открылось", node.unlocks)
                    }
                    NodeStatus.CURRENT -> {
                        Body("Ты здесь.")
                        if (node.failedTries > 0) Body("Не засчитались попытки: ${node.failedTries}.")
                        current?.let {
                            if (it.nextLevel != null) {
                                CoreLines(it)
                                ConditionLines(it)
                                AmountLines(it)
                            }
                        }
                        Unlocks("Открыто на этом уровне", node.unlocks)
                    }
                    NodeStatus.FUTURE -> {
                        Body("Этот уровень впереди. Какая игра уровня будет обязательной, станет известно, когда до него дойдёшь.")
                        if (isDemo) Body("В режиме проверки игры, вещи и цели уже открыты.")
                        Unlocks("На этом уровне", node.unlocks)
                    }
                }
            }
            node.resultPeriod?.let { period ->
                FinneyButton(text = "Итоги уровня ${node.level}", onClick = { onOpenResult(period) })
            }
        }
    }
}

@Composable
private fun Unlocks(title: String, lines: List<String>) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = FinneyInk, modifier = Modifier.fillMaxWidth())
    if (lines.isEmpty()) Body("Новых игр, вещей и целей нет.")
    lines.forEach { Body(it) }
}
