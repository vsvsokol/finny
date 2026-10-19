package ru.finney.pet.ui

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Test
import ru.finney.pet.domain.FakeActiveProfile
import ru.finney.pet.domain.FakeStorage
import ru.finney.pet.domain.Fixtures
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.GameStore
import ru.finney.pet.domain.game.Session
import ru.finney.pet.domain.model.*
import ru.finney.pet.ui.goals.GoalsUiState
import ru.finney.pet.ui.goals.GoalsViewModel
import ru.finney.pet.ui.home.HomeUiState
import ru.finney.pet.ui.home.HomeViewModel
import ru.finney.pet.ui.progress.homeUnlocks
import ru.finney.pet.ui.shop.ShopUiState
import ru.finney.pet.ui.shop.ShopViewModel

class ProgressionUiTest : ViewModelTest() {
    private val content = Fixtures.content.copy(
        economy = Fixtures.economy.copy(stageTitles = listOf("Новичок", "Копилкин", "Мастер")),
        shop = Fixtures.shop.map {
            when (it.id) {
                "candy" -> it.copy(unlockLevel = 2)
                "ball" -> it.copy(unlockLevel = 3)
                else -> it
            }
        },
        goals = Fixtures.goals.map { it.copy(unlockLevel = if (it.id == "crown") 2 else 3) },
    )

    private data class Rig(val session: Session, val shop: ShopViewModel, val goals: GoalsViewModel, val home: HomeViewModel) {
        fun shopState() = shop.uiState.value as ShopUiState.Ready
        fun goalsState() = goals.uiState.value as GoalsUiState.Ready
        fun homeState() = home.uiState.value as HomeUiState.Ready
    }

    private suspend fun TestScope.rig(demo: Boolean = false): Rig {
        val game = Game(content)
        val session = Session(GameStore(game, FakeStorage()), FakeActiveProfile())
        session.createProfile("Финни", PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND), demo)
        val rig = Rig(session, ShopViewModel(session, game, content), GoalsViewModel(session, game, content),
            HomeViewModel(session, game, content))
        backgroundScope.launch { rig.shop.uiState.collect {} }
        backgroundScope.launch { rig.goals.uiState.collect {} }
        backgroundScope.launch { rig.home.uiState.collect {} }
        settle()
        return rig
    }

    @Test fun `магазин и цели показывают доступное и только один ближайший тизер`() = test {
        val rig = rig()
        val shop = rig.shopState()
        assertFalse((shop.needs + shop.wants + shop.toys).any { it.item.id in listOf("candy", "ball", "crown_hat") })
        assertEquals("candy", shop.upcoming!!.id)
        assertTrue(rig.goalsState().goals.isEmpty())
        assertEquals("crown", rig.goalsState().upcoming!!.id)
        assertEquals(2, rig.homeState().upcoming!!.level)
        assertNotNull(rig.homeState().title)
    }

    @Test fun `в демо нет тизеров магазина и целей и доступно все содержимое`() = test {
        val rig = rig(demo = true)
        assertNull(rig.shopState().upcoming)
        assertNull(rig.goalsState().upcoming)
        assertTrue(rig.shopState().wants.any { it.item.id == "candy" })
        assertTrue((rig.shopState().wants + rig.shopState().toys).any { it.item.id == "ball" })
        assertEquals(2, rig.goalsState().goals.size)
        // На уровне 2 только доступ, который в демо уже есть; ближайшее новое — стадия на 3-м.
        assertEquals(3, rig.homeState().upcoming!!.level)
    }

    @Test fun `взять из радостей в магазине переносит план и покупает одним шагом`() = test {
        val rig = rig()
        assertTrue(rig.shopState().fromWants.isEmpty())
        rig.session.execute { confirmPlan(it, 0, 30, 0) }
        settle()
        assertEquals(10, rig.shopState().fromWants["apple"])
        rig.shop.buy("apple", fromWants = true)
        settle()
        assertEquals(40, rig.shopState().balance)
        assertEquals(0, rig.shopState().needsLeft)
        assertEquals(20, rig.shopState().wantsLeft)
    }

    @Test fun `взять из радостей на кухне меняет тот же план`() = test {
        val rig = rig()
        rig.session.execute { confirmPlan(it, 0, 30, 0) }
        settle()
        assertEquals(10, rig.homeState().fromWants["apple"])
        rig.home.buy("apple", fromWants = true)
        settle()
        assertEquals(0, rig.homeState().needsLeft)
        assertEquals(20, rig.homeState().wantsLeft)
        assertEquals(40, rig.homeState().balance)
    }

    @Test fun `нехватка радостей не предлагает взять из них`() = test {
        val rig = rig()
        rig.session.execute { confirmPlan(it, 0, 5, 0) }
        settle()
        assertNull(rig.shopState().fromWants["apple"])
        assertNull(rig.homeState().fromWants["apple"])
        assertNull(rig.shopState().fromWants["candy"])
    }

    @Test fun `выбранная в демо цель остается видна после выключения демо`() = test {
        val rig = rig(demo = true)
        rig.goals.selectGoal("bike")
        settle()
        rig.session.setDemo(false)
        settle()
        assertTrue(rig.goalsState().goals.single { it.goal.id == "bike" }.isActive)
        assertEquals("crown", rig.goalsState().upcoming!!.id)
    }

    @Test fun `на последнем уровне главная не обещает открытий`() {
        val content = content.copy(economy = content.economy.copy(maxLevel = 1))
        val game = Game(content)
        assertNull(homeUnlocks(game, content, game.newGame()))
    }

    @Test fun `игру с более поздним периодом не обещаем на ближайшем уровне`() {
        val task = Fixtures.tasks.first().copy(id = "gated", unlockLevel = 2, unlockPeriod = 10)
        val content = Fixtures.content.copy(tasks = listOf(task))
        val game = Game(content)
        assertNull(homeUnlocks(game, content, game.newGame()))
    }
}
