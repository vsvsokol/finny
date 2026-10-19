package ru.finney.pet.ui.tasks.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import ru.finney.pet.domain.model.Surprise
import ru.finney.pet.ui.components.HeartIcon
import ru.finney.pet.ui.components.categoryIcon
import ru.finney.pet.ui.components.WarningBadge
import ru.finney.pet.ui.motion.motionEnabled
import ru.finney.pet.ui.pet.PetMood
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.model.ReserveTask
import ru.finney.pet.domain.model.Spending
import ru.finney.pet.domain.tasks.TaskEngines
import ru.finney.pet.domain.tasks.TaskInput
import ru.finney.pet.domain.tasks.TaskInputError
import ru.finney.pet.ui.components.CheckBadge
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.theme.StrokeThin
import ru.finney.pet.ui.theme.FinneyBlue
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.StrokeBold
import ru.finney.pet.ui.theme.StrokeRegular

// «Дождливый день» — спланированный случай непредвиденной траты, который ТЗ
// разрешает прямо (раздел 2). Сначала «Моя неделя»: траты конвертами, нужное
// всегда в плане, остаток падает в банку «Запас». Потом начинается дождь и
// случается непредвиденное — от нуля до нескольких трат, заранее неизвестно. Запаса
// не хватило — ребёнок сам откладывает желаемое на потом. Отказ от желаемого — не провал:
// и пустой план, и жадный проходятся, но без бонуса. Бонус — за баланс: радость в плане и
// запас на все сюрпризы, какие могли случиться, а не только выпавшие: запас откладывают заранее.
// Питомец не болеет и не пугается.
//
// Плейтест 29.09: «так же скучно, как раньше, только теперь есть скрытое условие про
// неделю без радости». Теперь обе цели видны при планировании — отметками «радость» и
// «запас», и питомец отвечает на план. А неделя не пролистывается одной кнопкой, а
// проживается: дни идут по одному, в дни радостей у Финни сердечки, в дождливые дни
// случаются сюрпризы, и запас в банке тает на глазах. Правила и оценка прежние.

@Composable
internal fun ReserveGame(
    task: ReserveTask,
    character: PetCharacter,
    inputError: TaskInputError?,
    onInputSeen: () -> Unit,
    onClose: () -> Unit,
    onSubmit: (TaskInput) -> Unit,
) {
    var planned by remember { mutableStateOf(task.spendings.filter { it.category == Category.NEEDS }.map { it.id }.toSet()) }
    var weekStarted by remember { mutableStateOf(false) }
    var dropped by remember { mutableStateOf(emptySet<String>()) }
    val reserve = TaskEngines.reserveLeft(task, planned)
    val joys = task.spendings.count { it.category == Category.WANTS && it.id in planned }

    if (!weekStarted) {
        GameScene(backdrop = Backdrop.DOTS, onClose = onClose, money = task.amount) {
            SceneBody(
                bottom = { FinneyButton(text = "Начать неделю", onClick = { weekStarted = true }, enabled = reserve >= 0) },
            ) {
                // Обе цели недели — наверху и с отметками: раньше «хоть одна радость» была
                // только в итоге, и проигрыш казался несправедливым.
                WeekGoals(joys = joys, reserve = maxOf(0, reserve))
                ScenePanel(title = "Моя неделя", modifier = Modifier.fillMaxWidth()) {
                    // По три конверта в ряд на всю ширину панели. С шириной числом они
                    // оставляли справа пустую полосу, а на широком экране — ещё шире.
                    task.spendings.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(IntrinsicSize.Max)) {
                            row.forEach { s ->
                                Envelope(s, checked = s.id in planned, Modifier.weight(1f).fillMaxHeight()) {
                                    planned = if (it) planned + s.id else planned - s.id
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    ReserveJar(maxOf(0, reserve))
                    val spent = task.amount - reserve
                    // Числом, без слова «разложено»: полоска ниже показывает то же. Перебор — «!» и минус.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = if (reserve >= 0) "разложено $spent из ${task.amount}" else "не хватает ${-reserve}"
                        },
                    ) {
                        OutlinedText(
                            if (reserve >= 0) "$spent / ${task.amount}" else "−${-reserve}",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(if (reserve >= 0) "✓" else "!", style = MaterialTheme.typography.titleLarge, color = FinneyInk)
                    }
                    TwoPartMeter(spent = spent, total = task.amount)
                }
                Spacer(Modifier.weight(1f))
                // Питомец отвечает на план: без радостей — скучает, без запаса — тревожится.
                val (mood, line) = when {
                    joys == 0 -> PetMood.SAD to "Целая неделя без радостей? Скучно…"
                    reserve <= 0 -> PetMood.HAPPY to "Ура, радость! А если что-то случится?"
                    else -> PetMood.HAPPY to "И радость есть, и запас. Поехали!"
                }
                PetSays(character, line, petSize = 100.dp, mood = mood)
            }
        }
        return
    }

    val surprises = task.surprises
    val total = TaskEngines.surprisesTotal(task)
    val shortage = maxOf(0, total - reserve)
    val freed = task.spendings.filter { it.id in dropped }.sumOf { it.price }
    val week = remember(planned) { planWeek(task, planned) }

    // Неделя идёт по дню. Где сюрприз съел весь запас, она встаёт: ребёнок переносит траты,
    // и дальше — итог. Без анимаций — сразу последний день.
    val sounds = LocalSounds.current
    var today by remember { mutableIntStateOf(0) }
    val stopDay = remember(week, reserve) { stopDay(week, reserve) }
    LaunchedEffect(Unit) {
        val animate = motionEnabled()
        for (d in 1..DAYS_IN_WEEK) {
            if (animate) delay(if (d == 1) FirstDayMs else DayMs)
            today = d
            val day = week[d - 1]
            if (animate || d == stopDay) {
                when {
                    day.surprise != null -> sounds.play(Sfx.Wrong)
                    day.joy != null -> sounds.play(Sfx.PetHappy)
                    else -> sounds.play(Sfx.Tap)
                }
            }
            if (d == stopDay) break
        }
    }
    val spentSoFar = week.take(today).sumOf { it.surprise?.price ?: 0 }
    val jarNow = maxOf(0, reserve - spentSoFar)
    val joysSoFar = week.take(today).count { it.joy != null }
    val finished = today >= DAYS_IN_WEEK || (stopDay != null && today >= stopDay)

    GameScene(backdrop = Backdrop.DOTS_RAIN, onClose = onClose, money = jarNow) {
        SceneBody {
            WeekStrip(week, today)
            DayCard(week.getOrNull(today - 1), today)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { ReserveJar(jarNow) }
                JoyCounter(joysSoFar)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .border(4.dp, FinneyInk, RoundedCornerShape(20.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!finished) {
                    Text("Неделя идёт…", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
                } else if (shortage == 0) {
                    OutlinedText(
                        when {
                            surprises.isEmpty() -> "Неделя прошла спокойно"
                            else -> "Запас пригодился: −$total"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fill = FinneyInk,
                        outline = Color.White,
                    )
                    // Неделя без радостей видна до итога, а не только в нём.
                    if (joys == 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WarningBadge(size = 28.dp)
                            Text("Финни всю неделю скучал: ни одной радости.", style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
                        }
                    }
                    FinneyButton(
                        text = "Итог недели",
                        onClick = { onSubmit(TaskInput.Reserve(planned, dropped)) },
                    )
                } else {
                    OutlinedText(
                        "Нужно $total: " + surprises.joinToString { it.label.lowercase().trimEnd('!') },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    // Сколько не хватает — числом, вопрос — коротко: что делать, без слов не понять.
                    Text("Не хватает $shortage. Что отложим на потом?", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
                    task.spendings.filter { it.id in planned }.forEach { s ->
                        PostponeRow(s, checked = s.id in dropped) {
                            dropped = if (it) dropped + s.id else dropped - s.id
                            onInputSeen()
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Нашлось $freed из $shortage" },
                    ) {
                        Meter(freed.toFloat() / shortage, Modifier.weight(1f).height(14.dp))
                        OutlinedText("$freed / $shortage" + if (freed >= shortage) " ✓" else "", style = MaterialTheme.typography.titleLarge)
                    }
                    if (inputError != null) Note(inputErrorText(inputError), color = FinneyPeach)
                    FinneyButton(text = "Готово", onClick = { onSubmit(TaskInput.Reserve(planned, dropped)) }, enabled = freed >= shortage)
                }
            }
            Spacer(Modifier.weight(1f))
            // Реплика «Хорошо, что был запас! / Желаемое купим потом…» повторяла объяснение
            // итога слово в слово — питомец просто стоит рядом. Лицо — по радостям недели.
            PetAtRight(character, 100.dp, mood = if (today > 1 && joys == 0) PetMood.SAD else PetMood.HAPPY)
        }
    }
}

/** Сколько дней в неделе «Дождливого дня». */
private const val DAYS_IN_WEEK = 7

/** Сколько длится день недели на экране; первый ждёт, пока сцена сменится на дождь. */
private const val DayMs = 750L
private const val FirstDayMs = 900L

private val DayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/** День недели: нужное (оплачено в понедельник), радость или сюрприз — или ничего. */
private class WeekDay(val joy: Spending? = null, val surprise: Surprise? = null, val needs: List<Spending> = emptyList())

/**
 * Раскладка недели по дням. Сюрпризы — в середине и ближе к концу, чтобы неделя
 * успела начаться; радости — в остальные дни по порядку, начиная со вторника.
 * Расклад только для показа: оценка недели по-прежнему у ядра.
 */
private fun planWeek(task: ReserveTask, planned: Set<String>): List<WeekDay> {
    val surpriseDays = when (task.surprises.size) {
        0 -> emptyList()
        1 -> listOf(3)
        2 -> listOf(2, 5)
        else -> listOf(1, 3, 5, 6).take(task.surprises.size)
    }
    val joys = task.spendings.filter { it.category == Category.WANTS && it.id in planned }
    val needs = task.spendings.filter { it.category == Category.NEEDS }
    val free = (1 until DAYS_IN_WEEK).filter { it !in surpriseDays }
    return List(DAYS_IN_WEEK) { d ->
        val s = surpriseDays.indexOf(d).takeIf { it >= 0 }?.let { task.surprises.getOrNull(it) }
        val j = free.indexOf(d).takeIf { it in joys.indices }?.let { joys[it] }
        // Понедельник — день покупок нужного: неделя начинается с главного.
        WeekDay(joy = j, surprise = s, needs = if (d == 0) needs else emptyList())
    }
}

/** День (с 1), когда сюрпризы впервые превысили запас; null — запаса хватило на всю неделю. */
private fun stopDay(week: List<WeekDay>, reserve: Int): Int? {
    var spent = 0
    week.forEachIndexed { d, day ->
        spent += day.surprise?.price ?: 0
        if (spent > reserve) return d + 1
    }
    return null
}

/** Цели недели отметками: хоть одна радость и запас. Не выполнено — голубой «!», не только цвет. */
@Composable
private fun WeekGoals(joys: Int, reserve: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        GoalChip(
            done = joys > 0,
            text = if (joys > 0) "Радость: $joys" else "Нужна радость",
            modifier = Modifier.weight(1f),
        ) { HeartIcon(filled = joys > 0, size = 22.dp) }
        GoalChip(
            done = reserve > 0,
            text = "Запас: $reserve",
            modifier = Modifier.weight(1f),
        ) { FinneyIcon(FinneyIcons.Piggy, size = 22.dp) }
    }
}

@Composable
private fun GoalChip(done: Boolean, text: String, modifier: Modifier, icon: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FinneyCream)
            .border(StrokeRegular, FinneyInk, RoundedCornerShape(14.dp))
            .semantics(mergeDescendants = true) { stateDescription = if (done) "есть" else "пока нет" }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        icon()
        Text(text, style = MaterialTheme.typography.bodyLarge, color = FinneyInk, modifier = Modifier.weight(1f))
        if (done) CheckBadge(size = 22.dp) else WarningBadge(size = 22.dp)
    }
}

/**
 * Дни недели плашками: прошедшие — с тем, что в них было (радость — сердечко,
 * сюрприз — его картинка и «!»), сегодняшний — в толстой рамке.
 */
@Composable
private fun WeekStrip(week: List<WeekDay>, today: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        week.forEachIndexed { i, day ->
            val passed = i < today
            val current = i == today - 1
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (passed && day.surprise != null) FinneyBlue else if (passed) FinneyCream else Color.White)
                    .border(if (current) StrokeBold else StrokeThin, FinneyInk, RoundedCornerShape(10.dp))
                    .padding(vertical = 4.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = DayNames[i] + when {
                            !passed -> ""
                            day.surprise != null -> ": ${day.surprise.label}"
                            day.joy != null -> ": ${day.joy.label}"
                            else -> ""
                        }
                    },
            ) {
                Text(DayNames[i], style = MaterialTheme.typography.labelMedium, color = FinneyInk)
                Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                    when {
                        !passed -> Unit
                        day.surprise != null -> ItemPicture(day.surprise, day.surprise.label, 24.dp)
                        day.joy != null -> HeartIcon(filled = true, size = 20.dp)
                        day.needs.isNotEmpty() -> FinneyIcon(categoryIcon(Category.NEEDS), size = 20.dp)
                        else -> Text("·", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
                    }
                }
            }
        }
    }
}

/**
 * Что случилось сегодня: радость — картинка и «Финни радуется», сюрприз — картинка,
 * его фраза и «−10 из запаса». Карточка выпрыгивает заново с каждым днём.
 */
@Composable
private fun DayCard(day: WeekDay?, today: Int) {
    val pop = remember(today) { Animatable(0.85f) }
    LaunchedEffect(today) {
        if (motionEnabled()) pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)) else pop.snapTo(1f)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 76.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .clip(RoundedCornerShape(16.dp))
            .background(if (day?.surprise != null) FinneyBlue else FinneyCream)
            .border(StrokeRegular, FinneyInk, RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        val name = if (today in 1..DAYS_IN_WEEK) DayNames[today - 1] else ""
        when {
            today == 0 -> Text("Начинается неделя. Смотри, что будет!", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
            day?.surprise != null -> {
                ItemPicture(day.surprise, day.surprise.label, 48.dp)
                Column(Modifier.weight(1f)) {
                    OutlinedText("$name: ${day.surprise.label}", style = MaterialTheme.typography.titleMedium, fill = FinneyInk, outline = Color.White)
                    Text(day.surprise.text, style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
                }
                OutlinedText("−${day.surprise.price}", style = MaterialTheme.typography.titleLarge)
            }
            day?.joy != null -> {
                ItemPicture(day.joy, day.joy.label, 48.dp)
                Column(Modifier.weight(1f)) {
                    Text("$name: ${day.joy.label}", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
                    Text("Финни радуется!", style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
                }
                HeartIcon(filled = true, size = 28.dp)
            }
            day != null && day.needs.isNotEmpty() -> {
                ItemPicture(day.needs.first(), day.needs.first().label, 48.dp)
                Column(Modifier.weight(1f)) {
                    Text("$name: нужное оплачено", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
                    Text(day.needs.joinToString { it.label.lowercase() }, style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
                }
                CheckBadge(size = 28.dp)
            }
            else -> Text("$name: идёт дождь, всё спокойно", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
        }
    }
}

/** Сколько радостей уже было за неделю — сердечком и числом. */
@Composable
private fun JoyCounter(joys: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(FinneyCream)
            .border(3.dp, FinneyInk, RoundedCornerShape(14.dp))
            .semantics(mergeDescendants = true) { contentDescription = "Радостей за неделю: $joys" }
            .padding(horizontal = 10.dp, vertical = 14.dp),
    ) {
        HeartIcon(filled = joys > 0, size = 26.dp)
        OutlinedText("$joys", style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * Конверт траты: день недели не важен, важно — что это и сколько.
 *
 * Плейтест: невыбранные конверты (бледная рамка) выглядели закрытыми, «откроются
 * позже», а выбранные — вариантами на выбор. Теперь как у обычного выбора:
 * выбранное — жёлтое, в толстой рамке и с «✓»; невыбранное — белое, в обычной
 * рамке и с пустым кружком, который хочется отметить; нужное — выбрано навсегда,
 * вместо «✓» замок. Слова «купить / в запас» — для TalkBack.
 */
@Composable
private fun Envelope(spending: Spending, checked: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
    val locked = spending.category == Category.NEEDS
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) FinneyYellow else Color.White)
            .border(if (checked) StrokeBold else StrokeRegular, FinneyInk, RoundedCornerShape(12.dp))
            .toggleable(value = checked, enabled = !locked, role = Role.Checkbox, onValueChange = onChange)
            .semantics { stateDescription = if (locked) "нужное, уже в плане" else if (checked) "купить" else "в запас" }
            .padding(6.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            ItemPicture(spending, spending.label, 36.dp)
            Text(spending.label, style = MaterialTheme.typography.labelMedium, color = FinneyInk, textAlign = TextAlign.Center)
            OutlinedText(spending.price.toString(), style = MaterialTheme.typography.titleLarge)
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            when {
                locked -> Box(
                    Modifier.size(24.dp).clip(CircleShape).background(FinneyCream).border(2.dp, FinneyInk, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { FinneyIcon(FinneyIcons.Lock, size = 16.dp) }
                checked -> CheckBadge(size = 24.dp)
                else -> Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White).border(2.dp, FinneyInk, CircleShape))
            }
        }
    }
}

/** Банка «Запас»: жёлтые монетки внутри — сколько отложено на всякий случай. */
@Composable
private fun ReserveJar(amount: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FinneyYellow)
            .border(3.dp, FinneyInk, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Canvas(Modifier.size(width = 44.dp, height = 52.dp)) {
            val lid = 9.dp.toPx()
            val stroke = 3.dp.toPx()
            val body = Size(size.width, size.height - lid)
            drawRoundRect(FinneyCream, Offset(0f, lid), body, CornerRadius(10.dp.toPx()))
            drawRoundRect(FinneyYellow, Offset(0f, lid + body.height * 0.4f), Size(size.width, body.height * 0.6f), CornerRadius(10.dp.toPx()))
            drawRoundRect(FinneyInk, Offset(0f, lid), body, CornerRadius(10.dp.toPx()), style = Stroke(stroke))
            drawRoundRect(FinneyPeach, Offset(4.dp.toPx(), 0f), Size(size.width - 8.dp.toPx(), lid), CornerRadius(4.dp.toPx()))
            drawRoundRect(FinneyInk, Offset(4.dp.toPx(), 0f), Size(size.width - 8.dp.toPx(), lid), CornerRadius(4.dp.toPx()), style = Stroke(stroke))
        }
        Column {
            OutlinedText("Запас: $amount", style = MaterialTheme.typography.titleLarge)
            Text("деньги на всякий случай", style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
        }
    }
}

/** Полоса: синяя — разложено по тратам, жёлтая — запас (деньги, а не «успех»). */
@Composable
private fun TwoPartMeter(spent: Int, total: Int) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(3.dp, FinneyInk, RoundedCornerShape(8.dp)),
    ) {
        val split = size.width * (spent.toFloat() / total).coerceIn(0f, 1f)
        drawRect(FinneyBlue, size = size.copy(width = split))
        drawRect(FinneyYellow, Offset(split, 0f), size.copy(width = size.width - split))
    }
}

/** Строка «что перенести». Нужное — пунктиром и замком: переносить нельзя. */
@Composable
private fun PostponeRow(spending: Spending, checked: Boolean, onChange: (Boolean) -> Unit) {
    val locked = spending.category == Category.NEEDS
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) FinneyPeach else FinneyCream)
            .then(
                if (locked) {
                    Modifier.dashedBorder()
                } else {
                    Modifier.border(3.dp, FinneyInk, RoundedCornerShape(12.dp))
                },
            )
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // Отметка «переносим» — тем же кружком, что у конвертов: пустой — на месте, «✓» — перенесли.
        when {
            locked -> FinneyIcon(FinneyIcons.Lock, size = 22.dp)
            checked -> CheckBadge(size = 24.dp)
            else -> Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White).border(2.dp, FinneyInk, CircleShape))
        }
        ItemPicture(spending, spending.label, 32.dp)
        Text(spending.label, style = MaterialTheme.typography.bodyLarge, color = FinneyInk, modifier = Modifier.weight(1f))
        Text(
            if (locked) "нужное" else "хочется",
            style = MaterialTheme.typography.labelMedium,
            color = FinneyInk,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(FinneyCream)
                .border(StrokeThin, FinneyInk, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp),
        )
        OutlinedText(spending.price.toString(), style = MaterialTheme.typography.titleLarge)
    }
}

private fun Modifier.dashedBorder(): Modifier = drawBehind {
    drawRoundRect(
        FinneyInk.copy(alpha = 0.6f),
        cornerRadius = CornerRadius(12.dp.toPx()),
        style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
    )
}
