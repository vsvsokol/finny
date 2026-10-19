package ru.finney.pet.ui

import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finney.pet.content.ContentValidator
import ru.finney.pet.domain.Fixtures
import ru.finney.pet.domain.model.BodyColor
import ru.finney.pet.domain.model.EyesVariant
import ru.finney.pet.domain.model.GlossaryTerm
import ru.finney.pet.domain.model.PetAppearance
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.model.TaskAttempt
import ru.finney.pet.domain.model.TaskOutcome
import ru.finney.pet.ui.progress.GlossaryViewModel
import ru.finney.pet.ui.progress.gamesLine
import ru.finney.pet.ui.progress.knowledgeCards

class GlossaryViewModelTest : ViewModelTest() {

    private val change = GlossaryTerm("change", "Сдача", "…", games = listOf("t1"))
    private val earned = GlossaryTerm("earned", "Заработал", "…", games = listOf("t2", "t3"))
    private val balance = GlossaryTerm("balance", "Баланс", "…")

    /** t1 — игра из двух вариантов: t1 и t1_hard. */
    private val content = Fixtures.content.copy(
        tasks = Fixtures.tasks + Fixtures.tasks.first().copy(id = "t1_hard", series = "t1", title = "Задание 1"),
        glossary = listOf(change, earned, balance),
    )

    private fun attempt(taskId: String, outcome: TaskOutcome) = TaskAttempt(1, taskId, outcome, 0, 0L)

    private fun learned(vararg attempts: TaskAttempt) =
        knowledgeCards(content, attempts.toList()).filter { it.learned }.map { it.term.id }

    @Test
    fun `слово узнано после успешной игры, неудача и чужая игра не считаются`() {
        assertEquals(emptyList<String>(), learned())
        assertEquals(emptyList<String>(), learned(attempt("t1", TaskOutcome.FAIL), attempt("t5", TaskOutcome.SUCCESS)))
        assertEquals(listOf("change"), learned(attempt("t1", TaskOutcome.FAIL), attempt("t1", TaskOutcome.SUCCESS)))
    }

    @Test
    fun `засчитывается любой вариант игры и любая из игр слова`() {
        assertEquals(listOf("change"), learned(attempt("t1_hard", TaskOutcome.SUCCESS)))
        assertEquals(listOf("earned"), learned(attempt("t3", TaskOutcome.SUCCESS)))
    }

    @Test
    fun `весь справочник на месте, слово без игры остаётся просто словом`() {
        val cards = knowledgeCards(content, listOf(attempt("t1", TaskOutcome.SUCCESS)))
        assertEquals(listOf("change", "earned", "balance"), cards.map { it.term.id })
        assertEquals(listOf("Задание 1"), cards[0].games)
        assertEquals(null, gamesLine(cards[2]))
        assertEquals("Слово из игры «Задание 1»", gamesLine(cards[0]))
        assertEquals("Пройди игру «Задание 2» или «Задание 3», и слово станет твоим", gamesLine(cards[1]))
    }

    @Test
    fun `попытка удалённого задания не ломает экран`() {
        assertEquals(emptyList<String>(), learned(attempt("gone", TaskOutcome.SUCCESS)))
    }

    @Test
    fun `опечатка в игре слова видна при проверке контента`() {
        val broken = content.copy(glossary = listOf(change.copy(games = listOf("t99"))))
        assertTrue(ContentValidator.validate(broken).any { it.contains("change") && it.contains("t99") })
    }

    @Test
    fun `пройденная мини-игра сразу открывает слово в справочнике`() = test {
        session.createProfile("Финни", PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND))
        val viewModel = GlossaryViewModel(session, content)
        backgroundScope.launch { viewModel.cards.collect {} }
        settle()
        assertEquals(emptyList<String>(), viewModel.cards.value!!.filter { it.learned }.map { it.term.id })

        session.execute { confirmPlan(it, needs = 0, wants = 0, savings = 0) }
        session.submitTask("t1", Fixtures.success)
        settle()
        assertEquals(listOf("change"), viewModel.cards.value!!.filter { it.learned }.map { it.term.id })
    }
}
