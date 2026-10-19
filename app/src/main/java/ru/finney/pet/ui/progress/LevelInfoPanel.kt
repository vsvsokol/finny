package ru.finney.pet.ui.progress

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyIcon
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
                        Unlocks("Что открылось", node.chips)
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
                        Unlocks("Открыто на этом уровне", node.chips)
                    }
                    NodeStatus.FUTURE -> {
                        Body("Этот уровень впереди.")
                        if (isDemo) Body("В режиме проверки игры, вещи и цели уже открыты.")
                        Unlocks(if (isDemo) "На этом уровне" else "Откроется", node.chips)
                    }
                }
            }
            node.resultPeriod?.let { period ->
                FinneyButton(text = "Итоги уровня ${node.level}", onClick = { onOpenResult(period) })
            }
        }
    }
}

/**
 * Открытия уровня фишками: рисунок и пара слов. Столбец фраз на плейтесте не читали.
 * Фишка плоская, без обводки: это отметка, а не кнопка (чек-лист 20.10).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Unlocks(title: String, chips: List<UnlockChip>) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = FinneyInk, modifier = Modifier.fillMaxWidth())
    if (chips.isEmpty()) {
        Body("Новых игр, вещей и целей нет.")
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { Chip(it) }
    }
}

@Composable
private fun Chip(chip: UnlockChip) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        when {
            chip.art != null -> Image(painterResource(chip.art), contentDescription = null, modifier = Modifier.size(32.dp))
            chip.icon != null -> FinneyIcon(chip.icon, size = 24.dp)
        }
        Text(chip.label, style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
    }
}
