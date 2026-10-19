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
import ru.finney.pet.ui.shop.ShopUiState
import ru.finney.pet.ui.shop.ShopViewModel

class ShopViewModelTest : ViewModelTest() {

    private suspend fun viewModel(): ShopViewModel {
        session.createProfile("Финни", PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND))
        return ShopViewModel(session, game, Fixtures.content)
    }

    private fun ShopViewModel.ready() = uiState.value as ShopUiState.Ready

    @Test
    fun `нужное и желаемое показаны отдельно, до плана купить нельзя`() = test {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        settle()

        val state = viewModel.ready()
        assertFalse(state.canBuy)
        assertEquals(listOf("apple", "bowl", "soap", "towel", "soap_pack"), state.needs.map { it.item.id })
        assertTrue(state.wants.map { it.item.id }.containsAll(listOf("candy", "ball", "lamp")))

        viewModel.buy("candy")
        settle()
        assertEquals(Rejection.PlanNotConfirmed, viewModel.ready().rejection)
        assertEquals(50, viewModel.ready().balance)
    }

    @Test
    fun `покупка желаемого списывает деньги и объясняет, что изменилось`() = test {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        session.execute { confirmPlan(it, needs = 30, wants = 20, savings = 0) }
        settle()

        viewModel.buy("candy")
        settle()

        val state = viewModel.ready()
        assertEquals(45, state.balance)
        assertEquals(15, state.wantsLeft)
        val feedback = checkNotNull(state.feedback) { "нет обратной связи после покупки" }
        val money = feedback.lines.single { it.label == "Деньги" }
        assertEquals(50, money.before)
        assertEquals(45, money.after)
        assertTrue("шкала питомца тоже в итоге", feedback.lines.any { it.label == "Радость" })
        assertTrue(feedback.why.contains("хочется"))

        viewModel.dismissFeedback()
        settle()
        assertNull(viewModel.ready().feedback)
    }

    @Test
    fun `при нехватке денег покупка не проходит и видно, сколько не хватает`() = test {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        session.execute { confirmPlan(it, needs = 0, wants = 50, savings = 0) }
        settle()

        viewModel.buy("lamp")
        settle()
        viewModel.buy("lamp")
        settle()
        viewModel.buy("lamp")
        settle()

        val state = viewModel.ready()
        assertEquals(0, state.balance)
        assertEquals(Rejection.InsufficientFunds(needed = 25, balance = 0), state.rejection)
        assertNull(state.feedback)
    }

    @Test
    fun `сверх плана — об этом сказано в объяснении`() = test {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        session.execute { confirmPlan(it, needs = 40, wants = 10, savings = 0) }
        settle()

        viewModel.buy("ball")
        settle()

        val feedback = checkNotNull(viewModel.ready().feedback)
        assertTrue(feedback.why.contains("сверх плана"))
    }

    @Test
    fun `аксессуар после покупки из магазина уходит`() = test {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        session.execute { confirmPlan(it, needs = 0, wants = 50, savings = 0) }
        settle()

        viewModel.buy("hat")
        settle()

        assertFalse(viewModel.ready().wants.any { it.item.id == "hat" })
    }

    @Test
    fun `игрушки — отдельный раздел, куплена — уходит с витрины и ждёт в зале`() = test {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        session.execute { confirmPlan(it, needs = 0, wants = 50, savings = 0) }
        settle()

        assertEquals(listOf("teddy"), viewModel.ready().toys.map { it.item.id })
        assertFalse("игрушка не среди прочего «хочется»", viewModel.ready().wants.any { it.item.id == "teddy" })

        viewModel.buy("teddy")
        settle()

        val state = viewModel.ready()
        assertTrue(state.toys.isEmpty())
        assertEquals(30, state.balance)
        assertTrue("подсказано, как играть", checkNotNull(state.feedback).next.contains("потряси"))
    }
}
