package ru.finney.pet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finney.pet.domain.game.Rejection
import ru.finney.pet.domain.model.EntryType
import ru.finney.pet.domain.model.PeriodFacts
import ru.finney.pet.domain.model.PeriodPhase
import ru.finney.pet.domain.model.PeriodResult
import ru.finney.pet.domain.model.Plan
import ru.finney.pet.domain.period.PeriodRules
import ru.finney.pet.domain.progress.Progression

/** docs/economy.md, разделы 3 и 8. */
class PeriodTest {

    private val plan = Plan(budget = 60, needs = 30, wants = 20, savings = 10)

    private fun facts(needs: Int = 30, wants: Int = 20, savings: Int = 10, unplanned: Int = 0) =
        PeriodFacts(needs, wants, savings, unplanned)

    private fun matched(f: PeriodFacts) = PeriodRules.planMatched(plan, f, tolerance = 5)

    @Test
    fun `7 сравнение плана с фактом`() {
        assertTrue("точно по плану", matched(facts()))
        assertTrue("недотрата желаемого — не ошибка", matched(facts(wants = 0)))
        assertTrue("перерасход в пределах допуска", matched(facts(wants = 25)))
        assertTrue("перерасход за счёт заработанного после плана", matched(facts(wants = 40, unplanned = 15)))
        assertFalse("перерасход сверх заработанного и допуска", matched(facts(wants = 41, unplanned = 15)))
        assertFalse("перерасход нужного без дохода", matched(facts(needs = 40)))
        assertTrue("копилка в пределах допуска", matched(facts(savings = 5)))
        assertFalse("копилка меньше плана", matched(facts(savings = 4)))
        assertTrue("копилка больше плана", matched(facts(savings = 50)))
    }

    @Test
    fun `доход уровня — только деньги на уровень, без наград и пополнений`() {
        val game = Fixtures.game()
        var s = game.newGame()
        assertEquals(50, game.levelIncome(s, 1))

        s = game.playPerfectPeriod(s)
        assertEquals(2, s.currentPeriod.number)
        val income = Fixtures.economy.income(s.currentPeriod.stage)
        assertEquals(income, game.levelIncome(s, 2))
        assertEquals("уровня ещё нет", 0, game.levelIncome(s, 3))

        s = game.confirmPlan(s, 0, 0, 0).state()
        s = game.submitTask(s, s.currentPeriod.levelTaskId!!, Fixtures.success).submitted().state
        s = game.addParentBonus(s, 10).state()
        // Отладочное пополнение — тоже INCOME, но позже дохода уровня.
        val extra = s.ledger.last { it.periodNumber == 2 }.copy(type = EntryType.INCOME, balanceDelta = 100, unplanned = true)
        s = s.copy(ledger = s.ledger + extra)
        assertEquals("награды и пополнения не в счёт", income, game.levelIncome(s, 2))
        assertEquals("прошлый уровень не меняется", 50, game.levelIncome(s, 1))
    }

    @Test
    fun `8 очки по таблице`() {
        val rule = Fixtures.economy.points
        assertEquals(8, Progression.periodPoints(true, true, true, successfulTasks = 5, rule))
        assertEquals(8, rule.maxPerPeriod)
        assertEquals(2, Progression.periodPoints(true, false, false, 0, rule))
        assertEquals(3, Progression.periodPoints(false, false, true, 1, rule))
        assertEquals(0, Progression.periodPoints(false, false, false, 0, rule))
    }

    @Test
    fun `9 уровень — плюс один за каждый пройденный период, не выше 9`() {
        val config = Fixtures.economy
        assertEquals(1, Progression.level(0, config))
        assertEquals(5, Progression.level(4, config))
        assertEquals(9, Progression.level(8, config))
        assertEquals(9, Progression.level(1000, config))
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 3, 3, 3), (1..9).map { Progression.stage(it, config) })
    }

    @Test
    fun `уровень пройден при 2 условиях из 3`() {
        val config = Fixtures.economy
        fun result(needs: Boolean, plan: Boolean, savings: Boolean, game: Boolean = true) =
            PeriodResult(PeriodFacts(0, 0, 0, 0), needs, plan, savings, gamePassed = game, successfulTasks = 2, points = 0)
        assertTrue(Progression.passed(result(true, true, true), config))
        assertTrue(Progression.passed(result(true, false, true), config))
        assertTrue(Progression.passed(result(false, true, true), config))
        assertFalse("одного условия мало, даже с играми", Progression.passed(result(false, true, false), config))
        assertFalse(Progression.passed(result(false, false, false), config))
        assertFalse("без игры уровня не пройти, даже со всеми тремя", Progression.passed(result(true, true, true, game = false), config))
    }

    @Test
    fun `9 полный цикл идеальной игры — все три стадии за 5 периодов демо`() {
        val game = Fixtures.game()
        var s = game.newGame()
        val levels = mutableListOf<Int>()
        repeat(5) {
            s = game.playPerfectPeriod(s)
            levels += game.level(s)
        }

        assertEquals(listOf(2, 3, 4, 5, 6), levels)
        assertTrue(s.periods.dropLast(1).all { game.isPassed(it) == true })
        assertEquals(listOf(1, 1, 2, 2, 3, 3), s.periods.map { it.stage })
        assertEquals(PeriodPhase.PLANNING, s.currentPeriod.phase)
    }

    @Test
    fun `непройденный уровень играется заново — с новыми деньгами, без потери прогресса`() {
        val game = Fixtures.game()
        val s = game.playPerfectPeriod(game.newGame())
        assertEquals(2, game.level(s))
        val balance = s.balance

        // План пустой и выполнен, но питомец голоден и ничего не отложено: 1 из 3.
        val failed = game.closePeriod(game.confirmPlan(s, 0, 0, 0).state()).state()
        assertEquals(false, game.isPassed(failed.periods[1]))
        assertEquals(2, game.level(failed))
        assertTrue("доход пришёл", failed.balance > balance)
        assertEquals(PeriodPhase.PLANNING, failed.currentPeriod.phase)
    }

    @Test
    fun `уровни периодов за один проход совпадают с levelAfter`() {
        val game = Fixtures.game()
        var s = game.playPerfectPeriod(game.newGame())
        s = game.closePeriod(game.confirmPlan(s, 0, 0, 0).state()).state()
        s = game.playPerfectPeriod(s)
        val played = game.levelsPlayed(s)
        s.periods.forEach { assertEquals(game.levelAfter(s, it.number - 1), played[it.number]) }
    }

    @Test
    fun `чек-лист уровня считается на эту минуту`() {
        val game = Fixtures.game()
        var s = game.selectGoal(game.newGame(), "bike").state()
        var check = game.levelCheck(s)
        assertFalse(check.planConfirmed)
        assertEquals(0, check.met)

        s = game.confirmPlan(s, needs = game.needsHint(s)!!, wants = 0, savings = 10).state()
        check = game.levelCheck(s)
        assertTrue(check.planConfirmed)
        assertFalse("копилка в плане — только обещание", check.savingsAdded)

        s = game.deposit(s, 10).state()
        check = game.levelCheck(s)
        assertTrue(check.planMatched)
        assertTrue(check.savingsAdded)
        assertFalse("без игры уровня", check.willPass)

        check = game.levelCheck(game.passLevelGame(s))
        assertTrue(check.willPass)
    }

    @Test
    fun `8 уровень не убывает, даже если после хорошей игры ничего не делать`() {
        val game = Fixtures.game()
        var s = game.playPerfectPeriod(game.playPerfectPeriod(game.newGame()))
        var level = game.level(s)
        repeat(5) {
            s = game.closePeriod(game.confirmPlan(s, 0, 0, 0).state()).state()
            assertTrue(game.level(s) >= level)
            level = game.level(s)
        }
    }

    @Test
    fun `закрытие без плана отклоняется, итоги сохраняются в закрытом периоде`() {
        val game = Fixtures.game()
        val start = game.newGame()
        assertEquals(Rejection.PlanNotConfirmed, game.closePeriod(start).reason())

        val s = game.playPerfectPeriod(start)
        val closed = s.periods.first()
        assertEquals(PeriodPhase.CLOSED, closed.phase)
        assertTrue(closed.result!!.needsCovered)
        assertTrue(closed.result.planMatched)
        assertEquals(10, closed.result.facts.savings)
        assertEquals(2, closed.result.successfulTasks)
    }
}
