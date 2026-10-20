package ru.finney.pet.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ru.finney.pet.ui.components.AlertBadge
import ru.finney.pet.ui.components.CheckBadge
import ru.finney.pet.ui.components.FillBar
import ru.finney.pet.ui.components.fadingScroll
import ru.finney.pet.ui.home.LEVEL_CONDITIONS
import ru.finney.pet.ui.tasks.games.plural
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.RadiusPanel
import ru.finney.pet.ui.theme.StrokeBold

/**
 * Панель под картой: что осталось до следующего уровня. Всегда про текущий уровень,
 * какой бы уровень ни был открыт на карте. Свёрнутая — план, игра уровня и счёт
 * условий; развёрнутая — каждое условие, недостающие суммы и цель.
 * Завершают уровень по-прежнему на главном: здесь только подсказка, что делать.
 */
@Composable
internal fun CurrentLevelPanel(
    current: CurrentLevel,
    reserveMode: Boolean,
    reserveSaved: Int,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusPanel))
            .background(FinneyCream)
            .border(StrokeBold, FinneyInk, RoundedCornerShape(RadiusPanel))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val heading = current.nextLevel?.let { "До уровня $it" } ?: "Самый высокий уровень"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Свернуть" else "Подробнее") { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(heading, style = MaterialTheme.typography.titleLarge, color = FinneyInk, modifier = Modifier.weight(1f))
            Text(if (expanded) "Свернуть" else "Подробнее", style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
        }
        if (current.nextLevel == null) {
            Body("Можно дальше заботиться о питомце, играть и копить.")
            if (expanded) GoalLines(current, reserveMode, reserveSaved)
            return@Column
        }
        // Свёрнутая — три строки, которые читаются с одного взгляда: сколько условий нужно,
        // какие уже есть, и одно дело на сейчас. Раньше тут был столбик фраз про план,
        // игру по желанию и «0 из 1 нужного условия» — его не читали (плейтест 20.10).
        ConditionChips(current)
        nextAction(current)?.let { NextAction(it) }
        if (expanded) {
            Column(
                modifier = Modifier.heightIn(max = 260.dp).fadingScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusLine(current.planConfirmed, if (current.planConfirmed) "План составлен" else "Составь план")
                GameLine(current)
                ConditionLines(current)
                AmountLines(current)
                GoalLines(current, reserveMode, reserveSaved)
            }
        }
    }
}

/** «Выполни 1 из 3» и три условия фишками: «✓» есть, «!» нет. Фишка плоская — отметка, не кнопка. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionChips(current: CurrentLevel) {
    val done = current.met >= current.toPass
    Text(
        if (done) "Условий хватает!" else "Выполни ${current.toPass} из $LEVEL_CONDITIONS:",
        style = MaterialTheme.typography.titleMedium,
        color = FinneyInk,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ConditionChip(current.needsCovered, "Уход", "Уход: сыт, чист и выспался")
        ConditionChip(current.planMatched, "По плану", "Траты по плану")
        ConditionChip(current.savingsAdded, "Копилка", "Отложено в копилку")
    }
}

@Composable
private fun ConditionChip(done: Boolean, label: String, description: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .padding(start = 6.dp, end = 12.dp)
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = if (done) "выполнено" else "не выполнено"
            },
    ) {
        if (done) CheckBadge(size = 22.dp) else AlertBadge(size = 22.dp)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
    }
}

/**
 * Одно дело на сейчас — то, без чего уровень не сдвинется. null — подсказывать нечего:
 * чего не хватает, видно по фишкам.
 */
private fun nextAction(current: CurrentLevel): String? = when {
    !current.planConfirmed -> "Сначала составь план"
    current.gameTitle != null && current.gameRequired && !current.gamePassed -> "Пройди игру «${current.gameTitle}»"
    current.ready && current.asleep -> "Разбуди ${current.petName} — и можно завершать"
    current.asleep -> "${current.petName} спит"
    current.ready -> "Готово! Заверши уровень на главном"
    else -> null
}

@Composable
private fun NextAction(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("→", style = MaterialTheme.typography.titleMedium, color = FinneyInk, modifier = Modifier.clearAndSetSemantics { })
        Text(text, style = MaterialTheme.typography.titleMedium, color = FinneyInk)
    }
}

/** План, игра уровня, счёт условий и что мешает завершить. Общие для панели и окна текущего уровня. */
@Composable
internal fun CoreLines(current: CurrentLevel) {
    StatusLine(current.planConfirmed, if (current.planConfirmed) "План составлен" else "Составь план")
    GameLine(current)
    val conditions = if (current.toPass == 1) "нужного условия" else "нужных условий"
    Body("Выполнено ${minOf(current.met, current.toPass)} из ${current.toPass} $conditions")
    when {
        current.ready && current.asleep -> Body("Всё готово, но ${current.petName} спит. Разбуди его, чтобы завершить уровень.")
        current.asleep -> Body("${current.petName} спит. Завершить уровень можно, когда он проснётся.")
        current.ready -> Body("Готово! Завершить уровень можно на главном экране.")
    }
}

/** Игра уровня: пройдена, нужна или по желанию. */
@Composable
private fun GameLine(current: CurrentLevel) {
    val title = current.gameTitle ?: return
    val text = when {
        current.gamePassed -> "Игра уровня «$title» пройдена"
        current.gameRequired -> "Пройди «$title»"
        else -> "«$title» — по желанию"
    }
    StatusLine(current.gamePassed || !current.gameRequired, text)
}

@Composable
internal fun ConditionLines(current: CurrentLevel) {
    StatusLine(current.needsCovered, "Уход: сыт, чист и выспался")
    StatusLine(current.planMatched, "Траты по плану")
    StatusLine(current.savingsAdded, "Отложено в копилку")
}

/** Сколько не хватает — числами. Остаток плана по копилке — не то же, что условие «Отложено». */
@Composable
internal fun AmountLines(current: CurrentLevel) {
    if (current.savingsPlanLeft > 0) {
        Body("В копилку по плану осталось положить ${coins(current.savingsPlanLeft)}")
    }
    if (current.overspent > 0) {
        Body("Потрачено сверх плана: ${coins(current.overspent)}")
    }
    current.careShort.forEach {
        Body("${it.label}: ${it.value} из 100, нужно не меньше ${current.needsThreshold}")
    }
}

@Composable
private fun ColumnScope.GoalLines(current: CurrentLevel, reserveMode: Boolean, reserveSaved: Int) {
    val goal = current.goal
    when {
        reserveMode -> Body("В запасе: ${coins(reserveSaved)}")
        goal == null -> Body("Цель пока не выбрана — выбери её в копилке.")
        else -> {
            Body("Копилка на «${goal.goal.label}»: ${goal.saved} из ${goal.goal.price}")
            FillBar(goal.saved, goal.goal.price, Modifier.fillMaxWidth())
            Body("До цели осталось собрать ${coins(goal.remaining)}")
        }
    }
}

/** Строка чек-листа: выполнено — «✓», нет — «!». Не только цветом (ТЗ п. 3.6). */
@Composable
internal fun StatusLine(done: Boolean, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = text
                stateDescription = if (done) "выполнено" else "не выполнено"
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (done) CheckBadge(size = 22.dp) else AlertBadge(size = 22.dp)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = FinneyInk, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
}

internal fun coins(n: Int) = plural(n, "монета", "монеты", "монет")
