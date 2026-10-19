package ru.finney.pet.ui

import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finney.pet.domain.Fixtures
import ru.finney.pet.domain.game.Rejection
import ru.finney.pet.domain.model.BodyColor
import ru.finney.pet.domain.model.EyesVariant
import ru.finney.pet.domain.model.PetAppearance
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.ui.budget.BudgetUiState
import ru.finney.pet.ui.budget.BudgetViewModel
import ru.finney.pet.ui.budget.PlanVerdict

class BudgetViewModelTest : ViewModelTest() {

    private suspend fun viewModel(): BudgetViewModel {
        session.createProfile("Финни", PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND))
        return BudgetViewModel(session, game, Fixtures.content)
    }

    @Test
    fun `совет учитывает уже заработанные деньги и направляет к недостающему взносу`() = test {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        session.execute { selectGoal(it, "bike") }
        viewModel.setSavings(10)
        viewModel.confirm()
        settle()
        session.execute { buy(it, "apple") }
        session.submitTask("t1", Fixtures.success)
        settle()
        val state = viewModel.uiState.value as BudgetUiState.Active
        assertEquals(0, state.incomeToRecover)
        assertEquals(PlanVerdict.SavingsShort(10), state.verdict)
    }

    @Test
    fun `остаток пересчитывается при вводе, перерасход бюджета не подтверждается`() = test {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        settle()

        viewModel.setNeeds(30)
        viewModel.setWants(15)
        viewModel.setSavings(-5)
        settle()
        var state = viewModel.uiState.value as BudgetUiState.Planning
        assertEquals(50, state.budget)
        assertEquals(0, state.savings)
        assertEquals(5, state.remainder)
        assertTrue(state.canConfirm)

        viewModel.setSavings(10)
        settle()
        state = viewModel.uiState.value as BudgetUiState.Planning
        assertEquals(-5, state.remainder)
        assertFalse(state.canConfirm)

        viewModel.confirm()
        settle()
        state = viewModel.uiState.value as BudgetUiState.Planning
        assertEquals(Rejection.PlanExceedsBudget(budget = 50, planned = 55), state.rejection)
    }

    @Test
    fun `после подтверждения — план против факта`() = test {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.setNeeds(20)
        viewModel.setWants(10)
        viewModel.confirm()
        settle()
        session.execute { buy(it, "apple") }
        session.execute { buy(it, "ball") }
        settle()

        val state = viewModel.uiState.value as BudgetUiState.Active
        assertEquals(10, state.report.facts.needs)
        assertEquals(15, state.report.facts.wants)
        assertEquals(5, state.report.overspend)
        assertTrue(state.report.onTrack)
        assertEquals(25, state.balance)
    }
}
