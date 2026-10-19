package ru.finney.pet.ui

import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finney.pet.domain.Fixtures
import ru.finney.pet.domain.model.BodyColor
import ru.finney.pet.domain.model.EyesVariant
import ru.finney.pet.domain.model.PetAppearance
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.ui.period.PeriodResultUiState
import ru.finney.pet.ui.period.PeriodResultViewModel

class PeriodResultViewModelTest : ViewModelTest() {

    private suspend fun startProfile() {
        session.createProfile("Финни", PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND))
    }

    private fun viewModel(periodNumber: Int) = PeriodResultViewModel(periodNumber, session, game, Fixtures.content)

    @Test
    fun `переход к плану доступен только после последнего закрытия и до подтверждения`() = test {
        startProfile()
        session.execute { confirmPlan(it, needs = 20, wants = 10, savings = 0) }
        session.execute { closePeriod(it) }
        val viewModel = viewModel(periodNumber = 1)
        backgroundScope.launch { viewModel.uiState.collect {} }
        settle()
        assertTrue((viewModel.uiState.value as PeriodResultUiState.Ready).canPlanNext)

        session.execute { confirmPlan(it, needs = 20, wants = 10, savings = 0) }
        settle()
        assertFalse((viewModel.uiState.value as PeriodResultUiState.Ready).canPlanNext)

        session.execute { closePeriod(it) }
        settle()
        assertFalse((viewModel.uiState.value as PeriodResultUiState.Ready).canPlanNext)
    }

    @Test
    fun `итоги закрытого периода — план, факт и очки`() = test {
        startProfile()
        session.execute { confirmPlan(it, needs = 20, wants = 10, savings = 0) }
        session.execute { buy(it, "apple") }
        session.execute { closePeriod(it) }

        val viewModel = viewModel(periodNumber = 1)
        backgroundScope.launch { viewModel.uiState.collect {} }
        settle()

        val state = viewModel.uiState.value as PeriodResultUiState.Ready
        assertEquals(1, state.periodNumber)
        assertEquals(20, state.plan.needs)
        assertEquals(10, state.plan.wants)
        // Куплено одно яблоко из «нужного»; на желаемое не потрачено ничего.
        assertEquals(10, state.facts.needs)
        assertEquals(0, state.facts.wants)
        assertFalse(state.savingsAdded)
        // Нужное не закрыто (питомец грязный), копилки нет — выполнен только план: 1 из 3.
        assertTrue(state.planMatched)
        assertEquals(1, state.playedLevel)
        assertFalse(state.passed)
        assertEquals(1, state.level)
        assertFalse(state.leveledUp)
    }

    @Test
    fun `перерасход считается только сверх плана`() = test {
        startProfile()
        session.execute { confirmPlan(it, needs = 0, wants = 0, savings = 0) }
        session.execute { buy(it, "apple") }
        session.execute { closePeriod(it) }

        val viewModel = viewModel(periodNumber = 1)
        backgroundScope.launch { viewModel.uiState.collect {} }
        settle()

        val state = viewModel.uiState.value as PeriodResultUiState.Ready
        assertEquals(10, state.overspend)
    }

    @Test
    fun `период, который ещё не закрыт, показывать нечего`() = test {
        startProfile()
        session.execute { confirmPlan(it, needs = 20, wants = 0, savings = 0) }

        val viewModel = viewModel(periodNumber = 1)
        backgroundScope.launch { viewModel.uiState.collect {} }
        settle()

        assertTrue(viewModel.uiState.value is PeriodResultUiState.Unavailable)
    }
}
