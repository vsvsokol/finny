package ru.finney.pet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finney.pet.content.ContentValidator
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.Rejection
import ru.finney.pet.domain.model.GameState
import ru.finney.pet.domain.model.GoalSliderTask
import ru.finney.pet.domain.model.Period
import ru.finney.pet.domain.model.PeriodFacts
import ru.finney.pet.domain.model.PeriodPhase
import ru.finney.pet.domain.model.PeriodResult
import ru.finney.pet.domain.model.TaskTheme

/** Новый уровень открывает новое: игры, вещи, цели, звание. docs/economy.md, раздел 8. */
class LevelUnlocksTest {

    private fun variant(id: String, series: String, level: Int) = GoalSliderTask(
        id = id, theme = TaskTheme.SAVINGS, title = id, intro = "", explainOk = "", explainFail = "",
        unlockLevel = level, series = series, goalPrice = 30, periods = 3, incomePerPeriod = 50,
    )

    private val content = Fixtures.content.copy(
        economy = Fixtures.economy.copy(stageTitles = listOf("Новичок", "Планировщик", "Копилкин")),
        shop = Fixtures.shop.map {
            when (it.id) {
                "lamp" -> it.copy(unlockLevel = 2)
                "teddy" -> it.copy(unlockLevel = 3)
                else -> it
            }
        },
        goals = Fixtures.goals.map { if (it.id == "bike") it.copy(unlockLevel = 4) else it },
        tasks = listOf(variant("a1", "a", 1), variant("a2", "a", 3), variant("b1", "b", 2), variant("b2", "b", 6)),
    )
    private val game = Game(content) { 0L }

    /** Уровень — число пройденных периодов плюс один: подкладываем нужное число пройденных. */
    private fun GameState.atLevel(level: Int): GameState {
        val passed = (1 until level).map { i ->
            Period(
                number = i - level + 1,
                stage = 1,
                phase = PeriodPhase.CLOSED,
                result = PeriodResult(PeriodFacts(0, 0, 0, 0), true, true, true, true, 0, 6),
            )
        }
        return copy(periods = passed + periods)
    }

    @Test
    fun `на уровне открываются новые игры, их сложные варианты, вещи, цели и звание`() {
        val second = game.unlocksAt(2)
        assertEquals(listOf("b1"), second.games.map { it.id })
        assertEquals(listOf("lamp"), second.items.map { it.id })
        assertTrue(second.harderGames.isEmpty())

        val third = game.unlocksAt(3)
        assertEquals(listOf("a2"), third.harderGames.map { it.id })
        assertEquals(listOf("teddy"), third.items.map { it.id })
        // Стадии фикстур начинаются на 1, 3, 5 — на третьем новое звание.
        assertEquals("Планировщик", third.title)

        assertEquals(listOf("bike"), game.unlocksAt(4).goals.map { it.id })
        assertTrue(game.unlocksAt(7).isEmpty)
    }

    @Test
    fun `итог уровня — что открылось, без пустых уровней, и что дальше`() {
        assertEquals(listOf(4, 5, 6), game.unlocksBetween(3, 6).map { it.level })
        val start = game.newGame()
        assertEquals("Новичок", game.title(start))
        assertEquals(2, game.nextUnlocks(start)?.level)
        assertEquals("Копилкин", game.title(start.atLevel(5)))
        assertEquals(6, game.nextUnlocks(start.atLevel(5))?.level)
        assertNull("дальше ничего нового", game.nextUnlocks(start.atLevel(6)))
    }

    @Test
    fun `закрытую вещь не купить, цель не выбрать — до своего уровня`() {
        val start = game.newPlannedGame()
        assertEquals(Rejection.Locked(3), game.buy(start, "teddy").reason())
        assertEquals(Rejection.Locked(4), game.selectGoal(start, "bike").reason())
        assertFalse(game.isUnlocked(start, content.item("teddy")!!))
        // Нужное открыто всегда.
        game.buy(start, "apple").state()

        val third = start.atLevel(3)
        assertTrue(game.buy(third, "teddy").state().owns("teddy"))
        assertEquals(Rejection.Locked(4), game.selectGoal(third, "bike").reason())
        assertEquals("bike", game.selectGoal(start.atLevel(4), "bike").state().activeGoalId)
    }

    @Test
    fun `в демо открыто всё`() {
        val demo = game.newPlannedGame(isDemo = true)
        assertTrue(game.buy(demo, "teddy").state().owns("teddy"))
        assertEquals("bike", game.selectGoal(demo, "bike").state().activeGoalId)
        assertTrue(game.isTaskAvailable(demo, content.task("b2")!!))
    }

    @Test
    fun `купленное и выбранное раньше остаётся`() {
        // Старое сохранение: цель выбрана и игрушка куплена до того, как их закрыли по уровням.
        val old = game.newPlannedGame().copy(activeGoalId = "bike")
        val bought = Game(Fixtures.content) { 0L }.buy(old, "teddy").state()
        assertTrue(bought.owns("teddy"))
        assertEquals("bike", bought.activeGoalId)
        assertTrue(game.toys(bought).any { it.id == "teddy" })
        assertEquals(10, game.deposit(bought, 10).state().goalSaved("bike"))
    }

    @Test
    fun `валидатор — нужное, радость и цель открыты на первом уровне`() {
        val locked = content.copy(
            shop = content.shop.map { it.copy(unlockLevel = 2) },
            goals = content.goals.map { it.copy(unlockLevel = 2) },
            economy = content.economy.copy(stageTitles = listOf("Новичок")),
        )
        val problems = ContentValidator.validate(locked)
        assertTrue(problems.joinToString(), problems.any { "apple — нужное открыто всегда" in it })
        assertTrue(problems.joinToString(), problems.any { "на уровне 1 нет цели" in it })
        assertTrue(problems.joinToString(), problems.any { "stageTitles — нужно 3" in it })
        assertTrue(problems.joinToString(), problems.any { "нечего купить из «хочется»" in it })
    }
}
