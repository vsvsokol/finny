package ru.finney.pet.ui

import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finney.pet.domain.Fixtures
import ru.finney.pet.domain.game.Rejection
import ru.finney.pet.domain.model.BodyColor
import ru.finney.pet.domain.model.EyesVariant
import ru.finney.pet.domain.model.PetAppearance
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.ui.goals.GoalsUiState
import ru.finney.pet.ui.goals.GoalsViewModel

class GoalsViewModelTest : ViewModelTest() {

    private val bike = "bike"

    private suspend fun viewModel(): GoalsViewModel {
        session.createProfile("Финни", PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND))
        return GoalsViewModel(session, game, Fixtures.content)
    }

    @Test
    fun `цель выбирается, до подтверждения плана деньги двигать нельзя`() = test {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        settle()

        var state = viewModel.uiState.value as GoalsUiState.Ready
        assertNull("цель по умолчанию не выбрана", state.progress)
        assertFalse("период ещё в планировании", state.canMoveMoney)

        viewModel.selectGoal(bike)
        settle()
        state = viewModel.uiState.value as GoalsUiState.Ready
        assertEquals(bike, state.progress?.goal?.id)
        assertTrue(state.goals.single { it.goal.id == bike }.isActive)
    }

    @Test
    fun `после подтверждения плана можно отложить и снять`() = test {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.selectGoal(bike)
        settle()
        session.execute { confirmPlan(it, needs = 0, wants = 0, savings = 0) }
        settle()

        viewModel.deposit(20)
        settle()
        var state = viewModel.uiState.value as GoalsUiState.Ready
        assertEquals(20, state.progress?.saved)
        assertEquals(30, state.balance)

        viewModel.askWithdraw(5)
        settle()
        state = viewModel.uiState.value as GoalsUiState.Ready
        assertEquals("до подтверждения деньги не двигаются", 20, state.progress?.saved)
        val pending = checkNotNull(state.pendingWithdraw) { "окно подтверждения не открылось" }
        assertEquals(20, pending.preview.savedBefore)
        assertEquals(15, pending.preview.savedAfter)

        viewModel.confirmWithdraw()
        settle()
        state = viewModel.uiState.value as GoalsUiState.Ready
        assertEquals(15, state.progress?.saved)
        assertEquals(35, state.balance)
        assertNull(state.pendingWithdraw)
        assertNull("снятие — не событие, окна нет", state.feedback)
        assertEquals("Взяли 5 монет.", state.note)
    }

    @Test
    fun `отмена снятия ничего не меняет`() = test {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.selectGoal(bike)
        settle()
        session.execute { confirmPlan(it, needs = 0, wants = 0, savings = 0) }
        viewModel.deposit(10)
        settle()

        viewModel.askWithdraw(5)
        settle()
        viewModel.cancelWithdraw()
        settle()

        val state = viewModel.uiState.value as GoalsUiState.Ready
        assertNull(state.pendingWithdraw)
        assertEquals(10, state.progress?.saved)
        assertEquals(40, state.balance)
    }

    @Test
    fun `отказ домена доходит до экрана`() = test {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.selectGoal(bike)
        settle()
        session.execute { confirmPlan(it, needs = 0, wants = 0, savings = 0) }
        settle()

        viewModel.deposit(500)
        settle()

        val state = viewModel.uiState.value as GoalsUiState.Ready
        assertEquals(Rejection.InsufficientFunds(needed = 500, balance = 50), state.rejection)
    }
}
