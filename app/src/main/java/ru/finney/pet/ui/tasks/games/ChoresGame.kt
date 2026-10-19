package ru.finney.pet.ui.tasks.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ru.finney.pet.ui.components.WarningBadge
import ru.finney.pet.domain.model.Chore
import ru.finney.pet.domain.model.ChoresTask
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.tasks.TaskEngines
import ru.finney.pet.domain.tasks.TaskInput
import ru.finney.pet.ui.components.CheckBadge
import ru.finney.pet.ui.components.Coin
import ru.finney.pet.ui.components.FillBar
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.FinneyBlue
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyYellow

// «Подработка»: неделя дел. Выбираешь день, нажимаешь дела внизу — они встают в
// часики дня. Дело в дне нажатием убирается. Пустой день — отдых, и хотя бы
// один нужен: деньги за работу, но и время не бесконечно. Сколько заработано
// и сколько часиков занято, считает ядро.
//
// Одно дело два дня подряд — питомец устаёт, и платят меньше (tiredCut). Это видно
// сразу, до «Готово»: плитка дела показывает уменьшенную плату и зачёркнутую
// обычную, а дело в дне — голубую метку «−10». Плейтест 29.09: правило было только
// во вступлении, и в самой игре его не замечали.

@Composable
internal fun ChoresGame(task: ChoresTask, character: PetCharacter, onClose: () -> Unit, onSubmit: (TaskInput) -> Unit) {
    var week by remember { mutableStateOf(List(task.days) { emptyList<String>() }) }
    var selected by remember { mutableIntStateOf(0) }
    val sounds = LocalSounds.current
    val earned = TaskEngines.choresEarned(task, week)
    val pay = TaskEngines.choresPay(task, week)
    val rest = week.count { it.isEmpty() }
    val times = week.flatten().groupingBy { it }.eachCount()
    val freeToday = task.hoursPerDay - TaskEngines.dayHours(task, week[selected])

    fun canAdd(chore: Chore) = TaskEngines.choreOpen(chore, selected) && chore.hours <= freeToday &&
        (chore.maxTimes == null || (times[chore.id] ?: 0) < chore.maxTimes)

    // Сколько на самом деле принесёт дело в выбранный день — с усталостью, но без
    // бонуса за разные дела: бонус — отдельная награда, а не плата за это дело.
    fun gain(chore: Chore): Int {
        val with = TaskEngines.choresPay(task, week.mapIndexed { d, day -> if (d == selected) day + chore.id else day })
        return (with.fees - with.tiredLoss) - (pay.fees - pay.tiredLoss)
    }

    GameScene(backdrop = Backdrop.DOTS, onClose = onClose) {
        SceneBody(
            bottom = {
                FinneyButton(text = "Готово", onClick = { onSubmit(TaskInput.Schedule(week)) })
            },
        ) {
            // Условия, дни и дела — в один экран 360 × 740 dp без прокрутки: на плейтесте,
            // чтобы понять задачу, пришлось листать вниз. Поэтому здесь теснее, чем в сцене
            // обычно, дни стоят одним рядом, а питомец меньше.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalCard(task, earned, rest)
                PetSays(character, petLine(task, earned, rest, pay.tiredLoss), petSize = 56.dp)

                // Все дни одним рядом: вторым рядом неделя не влезала в экран.
                Row(horizontalArrangement = Arrangement.spacedBy(DayGap), modifier = Modifier.fillMaxWidth()) {
                    week.forEachIndexed { i, ids ->
                        DayCard(
                            number = i + 1,
                            chores = ids.map { id -> task.chores.first { it.id == id } },
                            yesterday = week.getOrNull(i - 1).orEmpty().toSet(),
                            tiredCut = task.tiredCut,
                            hours = task.hoursPerDay,
                            selected = i == selected,
                            modifier = Modifier.weight(1f),
                            onSelect = { sounds.play(Sfx.Tap); selected = i },
                            onRemove = { n ->
                                sounds.play(Sfx.Tap)
                                week = week.mapIndexed { d, day -> if (d == i) day.filterIndexed { k, _ -> k != n } else day }
                            },
                        )
                    }
                }

                // Дела — без панели вокруг: её поля съедали место, а плитки и так в рамках.
                Text("День ${selected + 1}: нажми дело внизу. Пустой день останется для отдыха.",
                    style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    task.chores.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { chore ->
                                ChoreTile(
                                    chore = chore,
                                    fee = TaskEngines.choreFee(chore, selected),
                                    gain = gain(chore),
                                    left = chore.maxTimes?.let { it - (times[chore.id] ?: 0) },
                                    enabled = canAdd(chore),
                                    modifier = Modifier.weight(1f),
                                    onAdd = {
                                        sounds.play(Sfx.Coin)
                                        week = week.mapIndexed { d, day -> if (d == selected) day + chore.id else day }
                                    },
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Реплика. Про отдых — сразу, как только свободных дней стало мало, а не когда
 * деньги уже набраны: иначе ребёнок узнаёт правило только в итоге.
 */
private fun petLine(task: ChoresTask, earned: Int, rest: Int, tiredLoss: Int): String {
    val left = task.goal.price - earned
    return when {
        rest < task.minRestDays -> "Мне нужен отдых — освободи день"
        left > 0 && tiredLoss > 0 -> "Одно дело два дня подряд — я устал, платят меньше"
        left > 0 -> "Нужно ещё $left"
        else -> "Хватает, и есть отдых!"
    }
}

/**
 * Два условия недели, оба видны с начала: цель — числом и полосой, отдых — строкой
 * с лампой. Плейтест: ребёнок пропустил вступление, поставил собаку во все дни и
 * только в итоге узнал, что отдых обязателен.
 */
@Composable
private fun GoalCard(task: ChoresTask, earned: Int, rest: Int) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FinneyCream)
            .border(3.dp, FinneyInk, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text("Копим на «${task.goal.label}»", style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ItemPicture(task.goal, task.goal.label, 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedText("$earned / ${task.goal.price}", style = MaterialTheme.typography.titleLarge)
                FillBar(earned, task.goal.price, Modifier.fillMaxWidth(), description = "Заработано $earned из ${task.goal.price}")
            }
        }
        RestLine(rest, task.minRestDays)
    }
}

/**
 * Условие «хотя бы N дней отдыха». Пока свободных дней хватает — тихо, с «✓»; не
 * хватает — голубая строка с «!»: это предупреждение по ходу, а не итог, и цвет
 * не единственный знак (ТЗ п. 3.6).
 */
@Composable
private fun RestLine(rest: Int, need: Int) {
    val ok = rest >= need
    val rule = "Отдых — хотя бы ${plural(need, "день", "дня", "дней")}"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (ok) Color.Transparent else FinneyBlue.copy(alpha = 0.45f))
            .clearAndSetSemantics { contentDescription = if (ok) "$rule. Есть" else "$rule. Освободи день" }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        FinneyIcon(FinneyIcons.Lamp, size = 28.dp)
        Text(rule, style = MaterialTheme.typography.bodyLarge, color = FinneyInk, modifier = Modifier.weight(1f))
        if (ok) CheckBadge(size = 24.dp) else WarningBadge(size = 26.dp)
    }
}

/** Клетка одного часика: 48 dp — дело в дне нажимается, чтобы убрать (ТЗ п. 3.6). */
private val SlotHeight = 48.dp
private val SlotGap = 4.dp

/**
 * Дни стоят вплотную: в самой длинной неделе их шесть, и при 360 dp ширины клетке
 * остаётся ровно на 48 dp пальца, только если зазоры и поля дня — по 2–4 dp.
 */
private val DayGap = 4.dp

private fun slotsHeight(hours: Int) = SlotHeight * hours + SlotGap * (hours - 1)

/**
 * День: номер и столбик часиков. Дело занимает столько клеток, сколько часиков,
 * нажатие на него убирает. Свободные часики — пустые клетки. Пустой день — лампа
 * и «отдых» во весь столбик. Выбранный день — жёлтый и с толстой рамкой.
 * Дело, которое было и [yesterday], — голубое и с меткой «−[tiredCut]»: устал.
 */
@Composable
private fun DayCard(
    number: Int,
    chores: List<Chore>,
    yesterday: Set<String>,
    tiredCut: Int,
    hours: Int,
    selected: Boolean,
    modifier: Modifier,
    onSelect: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SlotGap),
        modifier = modifier
            .clip(shape)
            .background(if (selected) FinneyYellow else FinneyCream)
            .border(if (selected) 3.dp else 2.dp, FinneyInk, shape)
            .clickable(role = Role.Tab, onClickLabel = "Выбрать день $number", onClick = onSelect)
            .semantics {
                this.selected = selected
                contentDescription = if (chores.isEmpty()) "День $number, отдых" else "День $number: " + chores.joinToString { it.label }
            }
            .padding(horizontal = 2.dp, vertical = 3.dp),
    ) {
        OutlinedText(number.toString(), style = MaterialTheme.typography.titleMedium)
        if (chores.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().height(slotsHeight(hours)),
            ) {
                FinneyIcon(FinneyIcons.Lamp, size = 36.dp)
                Text(if (selected) "выбери дело" else "пусто", style = MaterialTheme.typography.bodyMedium,
                    color = FinneyInk, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            chores.forEachIndexed { n, chore ->
                val tired = chore.id in yesterday && tiredCut > 0
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(slotsHeight(chore.hours))
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (tired) FinneyBlue.copy(alpha = 0.45f) else Color.White)
                        .border(2.dp, FinneyInk, RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClickLabel = "Убрать ${chore.label}") { onRemove(n) }
                        .semantics { if (tired) contentDescription = "${chore.label}: второй день подряд, на $tiredCut меньше" },
                ) {
                    ItemPicture(chore, chore.label, 32.dp)
                    if (tired) TiredMark(tiredCut, Modifier.align(Alignment.BottomCenter))
                }
            }
            repeat(hours - chores.sumOf { it.hours }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(SlotHeight)
                        .clip(RoundedCornerShape(10.dp))
                        .border(2.dp, FinneyInk.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                )
            }
        }
    }
}

/** Метка усталости на деле в дне: «−10» на голубом — предупреждение, не ошибка. */
@Composable
private fun TiredMark(cut: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(bottom = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(FinneyBlue)
            .border(2.dp, FinneyInk, RoundedCornerShape(8.dp))
            .padding(horizontal = 3.dp),
    ) { Text("−$cut", style = MaterialTheme.typography.labelMedium, color = FinneyInk) }
}

/** Часики кружками — сколько времени занимает дело. */
@Composable
private fun Clocks(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(count) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(FinneyBlue).border(2.dp, FinneyInk, CircleShape))
        }
    }
}

/**
 * Дело плиткой: рисунок, награда и часики. Название — для TalkBack, на плитке его
 * заменяет рисунок. «×2» — сколько раз ещё дают. Нажатие кладёт дело в выбранный день.
 * [gain] меньше [fee] — дело рядом с таким же днём: плитка голубая, плата — уменьшенная,
 * а обычная рядом зачёркнута.
 */
@Composable
private fun ChoreTile(chore: Chore, fee: Int, gain: Int, left: Int?, enabled: Boolean, modifier: Modifier, onAdd: () -> Unit) {
    val tired = enabled && gain < fee
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    !enabled -> FinneyCream
                    tired -> FinneyBlue.copy(alpha = 0.45f)
                    else -> Color.White
                },
            )
            .border(3.dp, FinneyInk, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Добавить в день", onClick = onAdd)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("${chore.label}: ${chore.hours} ч., ")
                    append(if (tired) "два дня подряд — $gain финок вместо $fee" else "$fee финок")
                    if (left != null) append(", осталось $left раз")
                }
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        // Бледнеет только содержимое: с alpha на всей плитке рамка и фон пропадали после смены доступности.
        val fade = Modifier.alpha(if (enabled) 1f else 0.45f)
        ItemPicture(chore, chore.label, 36.dp, fade)
        Column(fade.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedText("+${if (tired) gain else fee}", style = MaterialTheme.typography.titleMedium)
                Coin(size = 18.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Clocks(chore.hours)
                if (tired) {
                    Text(
                        "+$fee",
                        style = MaterialTheme.typography.labelMedium.copy(textDecoration = TextDecoration.LineThrough),
                        color = FinneyInk,
                    )
                }
            }
        }
        if (left != null) {
            Box(
                fade.clip(RoundedCornerShape(8.dp)).background(FinneyCream).border(2.dp, FinneyInk, RoundedCornerShape(8.dp)).padding(horizontal = 4.dp),
            ) { Text("×$left", style = MaterialTheme.typography.labelLarge, color = FinneyInk) }
        }
    }
}

