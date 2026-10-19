package ru.finney.pet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.finney.pet.domain.game.Rejection
import ru.finney.pet.domain.model.GameState
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.room.ItemPicture
import ru.finney.pet.ui.room.itemFallback
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPink
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.theme.StrokeRegular

// Обратная связь после действия — ТЗ п. 2.5.9: ребёнок видит, что изменилось
// (деньги, копилка, шкала питомца), почему и что делать дальше. Отказ устроен
// так же: что не так и какой следующий шаг. Тексты отказов раньше жили в трёх
// экранах по-разному; здесь они в одном месте.

/** Что не получилось и что делать дальше. */
data class RejectionMessage(val problem: String, val next: String)

/** Отказ домена словами для ребёнка. Каждый ответ заканчивается следующим шагом. */
fun rejectionMessage(rejection: Rejection): RejectionMessage = when (rejection) {
    is Rejection.InsufficientFunds -> RejectionMessage(
        "Не хватает ${rejection.shortage} финок.",
        "Сыграй в мини-игру или заверши уровень — придут новые деньги.",
    )
    is Rejection.InsufficientSavings -> RejectionMessage(
        "В копилке пока только ${rejection.saved}.",
        "Сними меньше.",
    )
    Rejection.PlanNotConfirmed -> RejectionMessage(
        "План уровня ещё не готов.",
        "Сначала составь план.",
    )
    Rejection.PlanMissingDirection -> RejectionMessage(
        "В плане пусто.",
        "Положи хоть немного в каждое: нужное, желаемое и копилку.",
    )
    is Rejection.NotForSale -> RejectionMessage(
        "Это не продаётся.",
        "На «${rejection.goalLabel}» копят в копилке.",
    )
    Rejection.PlanAlreadyConfirmed -> RejectionMessage(
        "План этого уровня уже подтверждён.",
        "Новый план — на следующем уровне.",
    )
    is Rejection.PlanExceedsBudget -> RejectionMessage(
        "В плане ${rejection.planned}, а есть только ${rejection.budget}.",
        "Убери лишнее.",
    )
    Rejection.NoActiveGoal -> RejectionMessage(
        "Цель не выбрана.",
        "Выбери, на что копить.",
    )
    Rejection.GoalAlreadyCompleted -> RejectionMessage(
        "Эту цель ты уже достиг.",
        "Выбери новую цель.",
    )
    is Rejection.GoalNotReached -> RejectionMessage(
        "Накоплено ${rejection.saved} из ${rejection.price}.",
        "Откладывай понемногу — и получится.",
    )
    Rejection.AlreadyOwned -> RejectionMessage(
        "Это у тебя уже есть.",
        "Выбери что-нибудь другое.",
    )
    Rejection.Asleep -> RejectionMessage(
        "Питомец спит.",
        "Подожди, пока проснётся, или разбуди его.",
    )
    Rejection.NotSleepy -> RejectionMessage(
        "Спать пока не хочется.",
        "Уложи спать, когда шкала сна станет меньше.",
    )
    is Rejection.NotOwned -> RejectionMessage(
        "Этого у тебя пока нет.",
        "Это получают за цель: накопи в копилке.",
    )
    is Rejection.BonusLimitExceeded -> RejectionMessage(
        "На этом уровне можно добавить ещё ${rejection.left}.",
        "Остальное — на следующем уровне.",
    )
    is Rejection.BonusNotOnStep -> RejectionMessage(
        "Бонус добавляется по ${rejection.step}.",
        "Выбери сумму, кратную ${rejection.step}.",
    )
    is Rejection.Locked -> RejectionMessage(
        "Это пока закрыто.",
        "Откроется на уровне ${rejection.unlockLevel}.",
    )
    Rejection.TaskLocked -> RejectionMessage(
        "Это задание ещё закрыто.",
        "Откроется на следующих уровнях.",
    )
    is Rejection.NoStock -> RejectionMessage(
        "Мыло кончилось.",
        "Пополни полочку в ванной — мыло нужное, его можно вписать в план.",
    )
    Rejection.AlreadyClean -> RejectionMessage(
        "Питомец и так чистый.",
        "Помоешь потом, когда испачкается, — мыло сбережёшь.",
    )
    Rejection.NeedsSoap -> RejectionMessage(
        "Водой чище уже не станет.",
        "Нужно мыло — пополни полочку в ванной.",
    )
    Rejection.InvalidAmount,
    is Rejection.NotWashable,
    is Rejection.UnknownGoal,
    is Rejection.UnknownItem,
    is Rejection.NotWearable,
    is Rejection.NotPlayable,
    is Rejection.UnknownTask,
    is Rejection.InvalidTaskInput,
    Rejection.NotAsleep,
    -> RejectionMessage("Так не получится.", "Попробуй по-другому.")
}

/** Плашка отказа: розовая, но смысл несёт текст, а не цвет (ТЗ п. 3.6). */
@Composable
fun RejectionNote(rejection: Rejection, modifier: Modifier = Modifier) {
    val message = rejectionMessage(rejection)
    FeedbackBox(color = FinneyPink, modifier = modifier) {
        Text(message.problem, style = MaterialTheme.typography.titleMedium, color = FinneyInk)
        Text(message.next, style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
    }
}

/** Одна строка «было → стало». */
data class ChangeLine(val label: String, val before: Int, val after: Int) {
    val delta: Int get() = after - before
}

/**
 * Что произошло после действия: заголовок, изменения, причина и следующий шаг.
 * [itemId] — купленный предмет: его рисунок стоит в карточке рядом с заголовком.
 * [warning] — о чём предупредить даже в короткой всплывашке: трата вышла за план.
 */
data class ActionFeedback(
    val title: String,
    val lines: List<ChangeLine>,
    val why: String,
    val next: String,
    val itemId: String? = null,
    val warning: String? = null,
)

/**
 * Изменения между двумя состояниями игры: деньги, копилка, шкалы питомца.
 * Строки без изменений не попадают — ребёнку показывается только то, что сдвинулось.
 */
fun changesBetween(before: GameState, after: GameState): List<ChangeLine> = listOf(
    ChangeLine("Деньги", before.balance, after.balance),
    ChangeLine("Копилка", before.totalSavings, after.totalSavings),
    ChangeLine("Сытость", before.pet.satiety, after.pet.satiety),
    ChangeLine("Чистота", before.pet.hygiene, after.pet.hygiene),
    ChangeLine("Сон", before.pet.energy, after.pet.energy),
    ChangeLine("Радость", before.pet.mood, after.pet.mood),
).filter { it.delta != 0 }

/**
 * Звук итога по тому, что сдвинулось: копилка важнее денег, потому что пополнение
 * копилки тоже уменьшает деньги. Шкалы питомца своего звука здесь не дают:
 * еду и мытьё озвучивают сами игры ухода.
 */
fun feedbackSound(feedback: ActionFeedback): Sfx? {
    val savings = feedback.lines.firstOrNull { it.label == "Копилка" }?.delta ?: 0
    val money = feedback.lines.firstOrNull { it.label == "Деньги" }?.delta ?: 0
    return when {
        savings > 0 -> Sfx.PiggyIn
        savings < 0 -> Sfx.PiggyOut
        money < 0 -> Sfx.Purchase
        money > 0 -> Sfx.Coin
        else -> null
    }
}

/** Звук итога один раз, когда итог или отказ появился на экране. */
@Composable
fun FeedbackSound(feedback: ActionFeedback?, rejection: Rejection?) {
    val sounds = LocalSounds.current
    LaunchedEffect(feedback, rejection) {
        when {
            rejection != null -> sounds.play(Sfx.Denied)
            feedback != null -> feedbackSound(feedback)?.let(sounds::play)
        }
    }
}

/**
 * Итог действия: рисунок предмета и заголовок одной строкой, под ними — что сдвинулось,
 * значком и числом: «[вилка] +20», а не «Сытость: 40 → 60 (+20)».
 *
 * Карточка кремовая, а не зелёная: плейтест назвал прежнюю «полотном на зелёном,
 * как из логов», а зелёный у нас — только «получилось» в игре. Покупка — не оценка.
 *
 * [compact] — всплывашка на главном: только заголовок и изменения, она сама уходит
 * через несколько секунд. Полная — в окне магазина: там ещё причина и следующий шаг
 * словами (ТЗ п. 2.5.9).
 */
@Composable
fun ActionFeedbackCard(feedback: ActionFeedback, modifier: Modifier = Modifier, compact: Boolean = false) {
    FeedbackBox(color = FinneyCream, modifier = modifier) {
        ActionFeedbackSummary(feedback)
        if (!compact) {
            Text(feedback.why, style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
            if (feedback.next.isNotEmpty()) Text(feedback.next, style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
        }
    }
}

/**
 * Суть итога без рамки: рисунок и заголовок, что сдвинулось и предупреждение.
 * Её же показывает пузырь подсказки на обучении — вместе со следующим шагом.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionFeedbackSummary(feedback: ActionFeedback, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            feedback.itemId?.let { ItemPicture(it, itemFallback(it), size = 44.dp) }
            Text(
                feedback.title,
                style = MaterialTheme.typography.titleMedium,
                color = FinneyInk,
                modifier = Modifier.weight(1f),
            )
        }
        if (feedback.lines.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                feedback.lines.forEach { ChangeChip(it) }
            }
        }
        feedback.warning?.let { warning ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                WarningBadge(size = 26.dp)
                Text(warning, style = MaterialTheme.typography.titleMedium, color = FinneyInk, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Одно изменение: значок шкалы и «+20». Знак — словом, а не цветом (ТЗ п. 3.6);
 * «было → стало» целиком — для TalkBack.
 */
@Composable
private fun ChangeChip(line: ChangeLine) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "${line.label}: было ${line.before}, стало ${line.after}"
        },
    ) {
        ChangeIcon(line.label)
        OutlinedText(
            if (line.delta > 0) "+${line.delta}" else "−${-line.delta}",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/**
 * Итог действия поверх экрана: успех карточкой «было → стало», отказ — что не так
 * и что делать. Экран под окном может быть длинным, и надпись в его начале ребёнок,
 * нажавший кнопку внизу, не увидел бы.
 */
@Composable
fun FeedbackDialog(
    feedback: ActionFeedback?,
    rejection: Rejection?,
    onDismiss: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    FeedbackSound(feedback, rejection)
    if (feedback == null && rejection == null) return
    Dialog(onDismissRequest = onDismiss) {
        FinneyPanel(title = if (feedback != null) "Готово" else "Не вышло") {
            feedback?.let { ActionFeedbackCard(it) }
            rejection?.let { RejectionNote(it) }
            if (actionLabel != null && onAction != null) {
                FinneyButton(text = actionLabel, onClick = { onDismiss(); onAction() })
            }
            FinneyIconButton(
                onClick = onDismiss,
                contentDescription = "Понятно",
                size = 64.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { OutlinedText("✓", style = MaterialTheme.typography.headlineMedium) }
        }
    }
}

/** Значок строки «было → стало» по её подписи. */
@Composable
private fun ChangeIcon(label: String) {
    val size = 28.dp
    when (label) {
        "Деньги" -> Coin(size = size)
        "Копилка" -> FinneyIcon(FinneyIcons.Piggy, size = size)
        "Сытость" -> FinneyIcon(FinneyIcons.Food, size = size)
        "Чистота" -> FinneyIcon(FinneyIcons.Bath, size = size)
        "Сон" -> FinneyIcon(FinneyIcons.Lamp, size = size)
        "Радость" -> MoodFace(size = size)
        else -> Text(label, style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
    }
}

@Composable
private fun FeedbackBox(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .border(StrokeRegular, FinneyInk, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content()
    }
}

@Preview(widthDp = 360, showBackground = true, backgroundColor = 0xFFFFEDCD)
@Composable
private fun FeedbackPreview() {
    FinneyTheme {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            ActionFeedbackCard(
                ActionFeedback(
                    title = "Купили: мячик",
                    lines = listOf(ChangeLine("Деньги", 40, 25), ChangeLine("Радость", 50, 80)),
                    why = "Это «хочется»: радует, но можно и без него.",
                    next = "Хватит ли на нужное?",
                ),
            )
            RejectionNote(Rejection.InsufficientFunds(needed = 25, balance = 10))
        }
    }
}
