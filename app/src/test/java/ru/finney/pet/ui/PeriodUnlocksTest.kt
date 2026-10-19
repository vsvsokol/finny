package ru.finney.pet.ui

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finney.pet.domain.FakeActiveProfile
import ru.finney.pet.domain.FakeStorage
import ru.finney.pet.domain.Fixtures
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.GameStore
import ru.finney.pet.domain.game.Session
import ru.finney.pet.domain.model.BodyColor
import ru.finney.pet.domain.model.EyesVariant
import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.domain.model.GoalSliderTask
import ru.finney.pet.domain.model.PetAppearance
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.ui.period.PeriodResultUiState
import ru.finney.pet.ui.period.PeriodResultViewModel

class PeriodUnlocksTest : ViewModelTest() {
    private val base = (Fixtures.tasks.first() as GoalSliderTask).copy(
        id = "base", title = "Касса", series = "cash", unlockLevel = 1,
    )
    private val content = Fixtures.content.copy(
        economy = Fixtures.economy.copy(
            stageStartLevels = listOf(1, 2, 5),
            stageTitles = listOf("Малыш", "Хранитель", "Мастер"),
        ),
        tasks = listOf(
            base,
            base.copy(id = "harder", unlockLevel = 2),
            base.copy(id = "fresh", title = "Новая игра", series = "fresh", unlockLevel = 2),
            base.copy(id = "later", title = "Позже", series = "later", unlockLevel = 2, unlockPeriod = 10),
        ),
        shop = Fixtures.shop.map { if (it.id == "apple" || it.id == "crown_hat") it.copy(unlockLevel = 2) else it },
        goals = Fixtures.goals.map { if (it.id == "crown") it.copy(unlockLevel = 2) else it },
    )

    private suspend fun TestScope.result(
        content: GameContent = this@PeriodUnlocksTest.content,
        demo: Boolean = false,
        pass: Boolean = true,
        historical: Boolean = false,
    ): PeriodResultUiState.Ready {
        val game = Game(content)
        val session = Session(GameStore(game, FakeStorage()), FakeActiveProfile())
        session.createProfile("Финни", PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND), demo)
        session.execute { selectGoal(it, "bike") }
        session.execute { confirmPlan(it, needs = 0, wants = 0, savings = if (pass) 10 else 0) }
        if (pass) {
            session.execute { deposit(it, 10) }
            session.submitTask("base", Fixtures.success)
        }
        session.execute { closePeriod(it) }
        if (historical) {
            session.execute { confirmPlan(it, needs = 0, wants = 0, savings = 0) }
            session.execute { closePeriod(it) }
        }
        val viewModel = PeriodResultViewModel(1, session, game, content)
        backgroundScope.launch { viewModel.uiState.collect {} }
        settle()
        return viewModel.uiState.value as PeriodResultUiState.Ready
    }

    @Test fun `новые игры вещи цели и звание показаны один раз без награды за цель`() = test {
        val state = result()
        assertEquals(2, state.level)
        assertEquals(listOf(
            "Стала сложнее мини-игра: «Касса».",
            "Открылась мини-игра: «Новая игра».",
            "В магазине: Яблоко.",
            "Цель «Корона».",
            "Звание: Хранитель.",
        ), state.unlocks)
        assertEquals(listOf("Новая стадия: карапуз."), state.growth!!.changes)
    }

    @Test fun `демо показывает только новую сложность и звание`() = test {
        assertEquals(listOf("Стала сложнее мини-игра: «Касса».", "Звание: Хранитель."), result(demo = true).unlocks)
    }

    @Test fun `переход без открытий оставляет список пустым`() = test {
        val plain = Fixtures.content.copy(tasks = listOf(base))
        val state = result(content = plain)
        assertEquals(2, state.level)
        assertTrue(state.unlocks.isEmpty())
    }

    @Test fun `непройденный уровень не объявляет открытия`() = test {
        val state = result(pass = false)
        assertEquals(1, state.level)
        assertTrue(state.unlocks.isEmpty())
    }

    @Test fun `старые итоги сохраняют правило не показывать текущие награды`() = test {
        assertTrue(result(historical = true).unlocks.isEmpty())
    }
}
