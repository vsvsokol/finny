package ru.finney.pet.ui.progress

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.finney.pet.R
import ru.finney.pet.appContainer
import ru.finney.pet.domain.game.Session
import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.domain.model.GlossaryTerm
import ru.finney.pet.domain.model.TaskAttempt
import ru.finney.pet.domain.model.TaskOutcome
import ru.finney.pet.ui.components.CheckBadge
import ru.finney.pet.ui.components.Coin
import ru.finney.pet.ui.components.FinneyCard
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.FinneyScreen
import ru.finney.pet.ui.components.LevelBadge
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.components.PlanDonut
import ru.finney.pet.ui.components.JarGlass
import ru.finney.pet.ui.components.SavingsIcon
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.theme.FinneyInk

/**
 * Слово справочника для ребёнка. [games] — названия игр, где оно встречается;
 * [learned] — хоть одна из них пройдена, слово в «Моих знаниях».
 */
data class KnowledgeCard(val term: GlossaryTerm, val games: List<String>, val learned: Boolean)

/**
 * Карточки в порядке glossary.json. Что ребёнок знает, видно по успешным попыткам —
 * отдельно это не хранится (P1.3 в docs/stage-2-plan.md).
 */
fun knowledgeCards(content: GameContent, attempts: List<TaskAttempt>): List<KnowledgeCard> {
    val passed = attempts
        .filter { it.outcome == TaskOutcome.SUCCESS }
        .mapNotNull { content.task(it.taskId)?.seriesId }
        .toSet()
    val titles = content.taskSeries.associate { it.first().seriesId to it.first().title }
    return content.glossary.map { term ->
        KnowledgeCard(term, term.games.mapNotNull(titles::get), term.games.any { it in passed })
    }
}

/** Термины из glossary.json — учебный контент, не код (ТЗ п. 2.5.11, 3.2). */
class GlossaryViewModel(session: Session, content: GameContent) : ViewModel() {
    /** null — профиль ещё читается. Без профиля (справочник из настроек) — ничего не узнано. */
    val cards: StateFlow<List<KnowledgeCard>?> = session.activeGame
        .map { knowledgeCards(content, it?.state?.attempts.orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory = viewModelFactory {
            initializer { appContainer().let { GlossaryViewModel(it.session, it.content) } }
        }
    }
}

// Плейтест: справочник — «просто набор цифр, нереально скучно». Поэтому у термина
// рисунок из самой игры: круг плана, монеты, копилка, вещь цели. Ребёнок узнаёт
// то, что уже видел на экранах, и слово цепляется за картинку.

/** Сторона рисунка у термина. */
private val PictureSize = 72.dp

@Composable
fun GlossaryScreen(
    onBack: () -> Unit,
    viewModel: GlossaryViewModel = viewModel(factory = GlossaryViewModel.Factory),
) {
    FinneyScreen(
        scrollable = true,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        onClose = onBack,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlossaryBook(size = 44.dp)
            OutlinedText("Справочник", style = MaterialTheme.typography.headlineLarge)
        }
        // Справочник и сон-загадка — одно: загадки берутся отсюда, и ребёнок должен
        // это знать с обеих сторон (плейтест 29.09).
        Text(
            text = "Короткие объяснения слов, которые встречаются в игре. " +
                "Эти же слова снятся питомцу: когда он спит, нажми на облачко с книжкой и отгадай.",
            style = MaterialTheme.typography.bodyLarge,
            color = FinneyInk,
        )
        val cards by viewModel.cards.collectAsStateWithLifecycle()
        cards?.let { GlossaryCards(it) }
    }
}

/**
 * Сверху «Мои знания» — слова из пройденных игр, ниже весь остальной справочник.
 * Неузнанные слова не прячем: справочник открыт целиком, а подпись говорит, где слово узнать.
 */
@Composable
private fun GlossaryCards(cards: List<KnowledgeCard>) {
    val (learned, rest) = cards.partition { it.learned }
    val learnable = cards.count { it.games.isNotEmpty() }
    if (learnable > 0) {
        OutlinedText("Мои знания: ${learned.size} из $learnable", style = MaterialTheme.typography.titleLarge)
        if (learned.isEmpty()) {
            Text(
                text = "Пройди мини-игру, и слово из неё появится здесь.",
                style = MaterialTheme.typography.bodyLarge,
                color = FinneyInk,
            )
        }
        learned.forEach { TermCard(it) }
        if (rest.isNotEmpty()) OutlinedText("Все слова", style = MaterialTheme.typography.titleLarge)
    }
    rest.forEach { TermCard(it) }
}

/**
 * Карточка термина: рисунок слева, слово и объяснение справа. Термин без рисунка — только слова.
 * Узнанное слово — зелёное с галочкой, как пройденная игра в «Итогах».
 */
@Composable
private fun TermCard(card: KnowledgeCard) {
    val term = card.term
    FinneyCard(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        accent = if (card.learned) FinneyGreen else null,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (hasPicture(term.id)) {
                // Рисунок только поясняет слово — диктор читает сам термин и текст.
                Box(
                    modifier = Modifier.size(PictureSize).clearAndSetSemantics {},
                    contentAlignment = Alignment.Center,
                ) { TermPicture(term.id) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(term.term, style = MaterialTheme.typography.titleMedium, color = FinneyInk)
                Text(term.text, style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
                gamesLine(card)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FinneyInk) }
            }
            if (card.learned) CheckBadge(size = 28.dp)
        }
    }
}

/** Где слово узнают: «Слово из игры «Касса Финни»» или «Пройди игру «Касса Финни», и слово станет твоим». */
internal fun gamesLine(card: KnowledgeCard): String? {
    if (card.games.isEmpty()) return null
    val titles = card.games.map { "«$it»" }
    return if (card.learned) {
        (if (titles.size == 1) "Слово из игры " else "Слово из игр ") + titles.joinToString(" и ")
    } else {
        "Пройди игру ${titles.joinToString(" или ")}, и слово станет твоим"
    }
}

/** Термины с рисунком — по id из glossary.json. Новый термин без рисунка просто покажется словами. */
private val Pictured = setOf(
    "budget", "plan", "needs", "wants", "savings", "goal", "balance",
    "remainder", "fact", "period", "reserve", "change", "receipt", "earned",
)

internal fun hasPicture(id: String) = id in Pictured

/**
 * Рисунок к термину — то, что ребёнок видит в игре под этим словом:
 * план — круг с экрана плана, остаток — тот же круг с пустым куском,
 * факт — банка с чертой плана, как на «План и факт», запас — зонтик «на всякий случай».
 */
@Composable
internal fun TermPicture(id: String) {
    when (id) {
        "budget" -> Coins(count = 3)
        "balance" -> Coin(size = 48.dp)
        "change" -> Coins(count = 2)
        "plan" -> PlanDonut(budget = 50, needs = 25, wants = 15, savings = 10, diameter = PictureSize)
        "remainder" -> PlanDonut(budget = 50, needs = 20, wants = 10, savings = 5, diameter = PictureSize)
        "needs" -> TwoItems(R.drawable.item_food_apple, R.drawable.item_care_soap)
        "wants" -> TwoItems(R.drawable.item_treat_candy, R.drawable.item_toy_ball)
        "savings" -> FinneyIcon(SavingsIcon, size = 56.dp)
        "goal" -> Art(R.drawable.acc_hat_cowboy, PictureSize)
        "fact" -> PlanFactJar()
        "period" -> LevelBadge(level = 2, size = 60.dp)
        "reserve" -> Art(R.drawable.item_wear_umbrella, PictureSize)
        "receipt" -> FinneyIcon(FinneyIcons.Cart, size = 56.dp)
        "earned" -> Art(R.drawable.item_food_lemon, PictureSize)
    }
}

@Composable
private fun Art(@DrawableRes res: Int, size: Dp) {
    Image(painter = painterResource(res), contentDescription = null, modifier = Modifier.size(size))
}

/** Две вещи внахлёст: «нужное» — это и еда, и мыло, а не что-то одно. */
@Composable
private fun TwoItems(@DrawableRes first: Int, @DrawableRes second: Int) {
    Box(Modifier.size(PictureSize)) {
        Image(painterResource(first), null, Modifier.size(48.dp).align(Alignment.TopStart))
        Image(painterResource(second), null, Modifier.size(48.dp).align(Alignment.BottomEnd))
    }
}

/** Стопка монет лесенкой — «все монетки, что есть». */
@Composable
private fun Coins(count: Int) {
    Box(Modifier.size(PictureSize)) {
        repeat(count) { i ->
            Coin(size = 40.dp, modifier = Modifier.offset(x = (i * 14).dp, y = (28 - i * 12).dp))
        }
    }
}

/** Мини-копия «План и факт»: та же банка — черта задуманного и заливка того, что вышло. */
@Composable
private fun PlanFactJar() {
    JarGlass(line = 0.7f, shown = 0.45f, over = false, modifier = Modifier.size(width = 40.dp, height = PictureSize))
}

/**
 * Книжка справочника — тот же значок, что в облачке сна и на кнопке к справочнику:
 * одна картинка связывает загадку и справочник. Рисуется фигурами, без файла.
 */
@Composable
fun GlossaryBook(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawBook() }
}

/** Раскрытая книжка: две страницы с корешком посередине и строчки на них. */
private fun DrawScope.drawBook() {
    val w = size.width
    val h = size.height
    val stroke = w * 0.07f
    val top = h * 0.2f
    val bottom = h * 0.85f
    val mid = w / 2f
    fun page(left: Boolean) = Path().apply {
        val outer = if (left) w * 0.06f else w * 0.94f
        moveTo(mid, top + h * 0.06f)
        quadraticTo((mid + outer) / 2f, top - h * 0.06f, outer, top)
        lineTo(outer, bottom - h * 0.04f)
        quadraticTo((mid + outer) / 2f, bottom - h * 0.1f, mid, bottom)
        close()
    }
    listOf(page(true), page(false)).forEach { path ->
        drawPath(path, Color.White)
        drawPath(path, FinneyInk, style = Stroke(stroke))
    }
    for (i in 0 until 3) {
        val y = top + h * (0.18f + 0.15f * i)
        drawLine(FinneyInk, Offset(w * 0.16f, y), Offset(mid - w * 0.08f, y + h * 0.02f), stroke * 0.6f)
        drawLine(FinneyInk, Offset(mid + w * 0.08f, y + h * 0.02f), Offset(w * 0.84f, y), stroke * 0.6f)
    }
}
