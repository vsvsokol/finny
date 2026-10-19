package ru.finney.pet.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finney.pet.domain.Fixtures
import ru.finney.pet.domain.coverNeeds
import ru.finney.pet.domain.passLevelGame
import ru.finney.pet.domain.state
import ru.finney.pet.domain.game.GameResult
import ru.finney.pet.ui.home.NextStep
import ru.finney.pet.domain.model.BodyColor
import ru.finney.pet.domain.model.EyesVariant
import ru.finney.pet.domain.model.PeriodPhase
import ru.finney.pet.domain.model.PetAppearance
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.ui.home.HomeEvent
import ru.finney.pet.ui.home.HomeUiState
import ru.finney.pet.ui.home.HomeViewModel

class HomeViewModelTest : ViewModelTest() {

    @Test
    fun `главный показывает открытый профиль и обновляется после команд`() = test {
        session.createProfile("Финни", PetAppearance(PetCharacter.PUSHISTIK, BodyColor.B, EyesVariant.SLY))
        val viewModel = HomeViewModel(session, game, Fixtures.content)
        backgroundScope.launch { viewModel.uiState.collect {} }
        settle()

        var state = viewModel.uiState.value as HomeUiState.Ready
        assertEquals("Финни", state.petName)
        assertEquals(50, state.balance)
        assertEquals(PeriodPhase.PLANNING, state.phase)
        assertFalse(state.canClosePeriod)
        assertFalse(state.check.planConfirmed)
        val levelTaskId = state.check.levelTaskId!!
        assertEquals(Fixtures.content.task(levelTaskId)!!.title, state.levelGame)
        assertFalse(state.check.gamePassed)

        session.execute { confirmPlan(it, needs = 0, wants = 0, savings = 0) }
        session.submitTask(levelTaskId, Fixtures.success)
        settle()

        state = viewModel.uiState.value as HomeUiState.Ready
        assertEquals(65, state.balance)
        assertTrue(state.check.gamePassed)
        assertTrue(state.canClosePeriod)
        assertTrue(state.check.planConfirmed)
        assertTrue("пустой план выполнен", state.check.planMatched)
    }

    @Test
    fun `закрытие периода сообщает номер закрытого периода`() = test {
        session.createProfile("Финни", PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND))
        session.execute { confirmPlan(it, needs = 0, wants = 0, savings = 0) }
        val viewModel = HomeViewModel(session, game, Fixtures.content)

        viewModel.closePeriod()

        assertEquals(HomeEvent.PeriodClosed(1), viewModel.events.first())
        assertEquals(2, session.activeGame.first()!!.state.currentPeriod.number)
    }
    @Test
    fun `запланированный взнос при допуске не теряется за подсказкой завершения`() = test {
        prepareGoalLevel(savings = 5)
        val viewModel = HomeViewModel(session, game, Fixtures.content)
        backgroundScope.launch { viewModel.uiState.collect {} }
        settle()

        val state = viewModel.uiState.value as HomeUiState.Ready
        assertTrue("два условия уже разрешают пройти уровень", state.check.willPass)
        assertTrue("допуск принимает план без взноса", state.check.planMatched)
        assertFalse(state.check.savingsAdded)
        assertEquals(NextStep.SAVE, state.nextStep)
        assertEquals(5, state.savingsPlanLeft)
        assertTrue("рекомендация не блокирует завершение", state.canClosePeriod)

        viewModel.closePeriod()
        assertEquals(HomeEvent.PeriodClosed(1), viewModel.events.first())
        val closed = session.activeGame.first()!!.state.periods.first()
        assertTrue("отказ от рекомендации не меняет правила успеха", game.isPassed(closed) == true)
    }

    @Test
    fun `частичный взнос напоминает об остатке а полный возвращает завершение`() = test {
        prepareGoalLevel(savings = 10)
        session.execute { deposit(it, 5) }
        val viewModel = HomeViewModel(session, game, Fixtures.content)
        backgroundScope.launch { viewModel.uiState.collect {} }
        settle()

        var state = viewModel.uiState.value as HomeUiState.Ready
        assertTrue(state.check.willPass)
        assertTrue("копилка уже пополнена но обещана большая сумма", state.check.savingsAdded)
        assertTrue(state.check.planMatched)
        assertEquals(NextStep.SAVE, state.nextStep)
        assertEquals(5, state.savingsPlanLeft)

        session.execute { deposit(it, 5) }
        settle()
        state = viewModel.uiState.value as HomeUiState.Ready
        assertEquals(0, state.savingsPlanLeft)
        assertEquals(NextStep.FINISH, state.nextStep)
    }

    @Test
    fun `план без взноса не навязывает копилку перед завершением`() = test {
        prepareGoalLevel(savings = 0)
        val viewModel = HomeViewModel(session, game, Fixtures.content)
        backgroundScope.launch { viewModel.uiState.collect {} }
        settle()

        val state = viewModel.uiState.value as HomeUiState.Ready
        assertTrue(state.check.willPass)
        assertFalse(state.check.savingsAdded)
        assertEquals(NextStep.FINISH, state.nextStep)
    }

    @Test
    fun `остаток плана без денег не скрывает доступное завершение`() = test {
        prepareGoalLevel(savings = 10)
        session.execute { deposit(it, 5) }
        session.execute {
            var spent = it
            while (spent.balance >= 5) spent = buy(spent, "candy").state()
            GameResult.Ok(spent)
        }
        val viewModel = HomeViewModel(session, game, Fixtures.content)
        backgroundScope.launch { viewModel.uiState.collect {} }
        settle()

        val state = viewModel.uiState.value as HomeUiState.Ready
        assertEquals(0, state.balance)
        assertTrue(state.check.willPass)
        assertEquals(NextStep.FINISH, state.nextStep)
    }

    private suspend fun prepareGoalLevel(savings: Int) {
        session.createProfile("Финни", PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND))
        session.execute { selectGoal(it, "bike") }
        session.execute { confirmPlan(it, needs = 20, wants = 5, savings = savings) }
        session.execute { GameResult.Ok(coverNeeds(passLevelGame(it))) }
    }

}
