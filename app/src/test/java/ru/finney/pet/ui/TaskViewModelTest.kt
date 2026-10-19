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
import ru.finney.pet.domain.model.TaskOutcome
import ru.finney.pet.domain.tasks.TaskInput
import ru.finney.pet.domain.tasks.TaskInputError
import ru.finney.pet.ui.tasks.TaskPhase
import ru.finney.pet.ui.tasks.TaskUiState
import ru.finney.pet.ui.tasks.TaskViewModel
import ru.finney.pet.ui.tasks.progressLine
import ru.finney.pet.ui.tasks.rewardLine

class TaskViewModelTest : ViewModelTest() {

    private suspend fun viewModel(taskId: String = "t1"): TaskViewModel {
        session.createProfile("Финни", PetAppearance(PetCharacter.LUCHIK, BodyColor.A, EyesVariant.ROUND))
        // Мини-игры — только после плана.
        session.execute { confirmPlan(it, needs = 0, wants = 0, savings = 0) }
        return TaskViewModel(taskId, session, game, Fixtures.content)
    }

    private fun TaskViewModel.ready() = uiState.value as TaskUiState.Ready

    @Test
    fun `вступление, игра, итог с наградой — объяснение и при неудаче`() = test {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        settle()
        assertEquals(TaskPhase.INTRO, viewModel.ready().phase)
        assertEquals(PetCharacter.LUCHIK, viewModel.ready().character)

        viewModel.start()
        settle()
        assertEquals(TaskPhase.PLAY, viewModel.ready().phase)

        viewModel.submit(Fixtures.failure)
        settle()
        var state = viewModel.ready()
        assertEquals(TaskPhase.RESULT, state.phase)
        assertEquals(TaskOutcome.FAIL, state.result?.outcome)
        assertEquals(5, state.result?.reward)
        assertEquals(55, state.balance)

        viewModel.replay()
        settle()
        assertEquals(1, viewModel.ready().attempt)
        viewModel.submit(Fixtures.success)
        settle()
        state = viewModel.ready()
        assertEquals(TaskOutcome.SUCCESS, state.result?.outcome)
        assertTrue("пройдено", state.completed)
    }

    @Test
    fun `карточка перед игрой говорит, что попадёт в кошелёк`() = test {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        settle()
        assertTrue(viewModel.ready().firstTry)
        assertEquals("+15 монет в кошелёк Финни. Не выйдет — всё равно\u00A0+5.", rewardLine(viewModel.ready()))
        assertEquals("Монеты пригодятся для плана и копилки.", progressLine(viewModel.ready()))

        viewModel.start()
        viewModel.submit(Fixtures.failure)
        settle()
        assertFalse("за вторую неудачу монет нет", viewModel.ready().firstTry)
        assertEquals("+15 монет в кошелёк Финни.", rewardLine(viewModel.ready()))

        viewModel.replay()
        viewModel.submit(Fixtures.success)
        settle()
        assertEquals("Награда уже в кошельке. Сыграй ещё — для тренировки.", rewardLine(viewModel.ready()))
    }

    @Test
    fun `ошибка ввода не завершает игру — ребёнок исправляет`() = test {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.start()
        settle()

        viewModel.submit(TaskInput.Deposit(7))
        settle()
        assertEquals(TaskPhase.PLAY, viewModel.ready().phase)
        assertEquals(TaskInputError.DepositNotOnStep(5), viewModel.ready().inputError)

        viewModel.inputSeen()
        settle()
        assertEquals(null, viewModel.ready().inputError)
    }

    @Test
    fun `закрытое задание видно, но играть нельзя`() = test {
        val viewModel = viewModel("t10")
        backgroundScope.launch { viewModel.uiState.collect {} }
        settle()
        assertFalse(viewModel.ready().available)
    }
}
