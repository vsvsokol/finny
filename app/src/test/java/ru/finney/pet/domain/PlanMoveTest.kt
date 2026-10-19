package ru.finney.pet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finney.pet.domain.game.Rejection
import ru.finney.pet.domain.model.Plan

/** «Возьми из радостей»: перенос плана из «хочется» в нужное посреди уровня (чек-лист рывка, раздел 3). */
class PlanMoveTest {

    private val game = Fixtures.game()

    /** Бюджет 50: нужное 20, «хочется» 20, копилка 10 — отложена. Суп за 20 уже куплен. */
    private val fed = run {
        val start = game.selectGoal(game.newGame(), "crown").state()
        var s = game.confirmPlan(start, needs = 20, wants = 20, savings = 10).state()
        s = game.deposit(s, 10).state()
        game.buy(s, "bowl").state()
    }

    @Test
    fun `нужное сверх плана — кнопка предлагает ровно разницу`() {
        assertEquals(20, game.wantsLeft(fed))
        assertEquals(10, game.wantsToNeeds(fed, "apple"))
    }

    @Test
    fun `после переноса покупка не выходит за план, и план выполнен`() {
        val moved = game.moveWantsToNeeds(fed, 10).state()

        assertEquals(Plan(budget = 50, needs = 30, wants = 10, savings = 10), moved.currentPeriod.plan)
        assertEquals(fed.balance, moved.balance)
        val bought = game.buy(moved, "apple").state()
        assertTrue(game.planReport(bought)!!.onTrack)
        // Ещё одно яблоко снова сверх плана — кнопка на оставшиеся 10 «хочется».
        assertEquals(10, game.wantsToNeeds(bought, "apple"))
    }

    @Test
    fun `без переноса тот же уровень план не выполняет`() {
        val bought = game.buy(fed, "apple").state()

        assertEquals(10, game.planReport(bought)!!.overspend)
        assertFalse(game.planReport(bought)!!.onTrack)
    }

    @Test
    fun `потраченное на радости назад не взять`() {
        var s = game.buy(fed, "candy").state()
        s = game.buy(s, "ball").state()

        assertEquals(0, game.wantsLeft(s))
        assertNull(game.wantsToNeeds(s, "apple"))
        assertEquals(Rejection.InvalidAmount, game.moveWantsToNeeds(s, 1).reason())
    }

    @Test
    fun `больше нетронутого остатка — отказ, ноль и минус — тоже`() {
        assertEquals(Rejection.InvalidAmount, game.moveWantsToNeeds(fed, 21).reason())
        assertEquals(Rejection.InvalidAmount, game.moveWantsToNeeds(fed, 0).reason())
        assertEquals(Rejection.InvalidAmount, game.moveWantsToNeeds(fed, -5).reason())
    }

    @Test
    fun `денег нет — кнопки нет, из плана монет не взять`() {
        // Бюджет 50 весь в нужном и «хочется», ничего не куплено; бонус взрослого не берём.
        var s = game.confirmPlan(game.newGame(), needs = 10, wants = 40, savings = 0).state()
        s = game.buy(s, "towel").state()
        s = game.buy(s, "bowl").state()

        assertEquals(15, s.balance)
        // Прошлый перерасход нужного (25) тоже закрывается: иначе план всё равно не выполнен.
        assertEquals(35, game.wantsToNeeds(s, "apple"))
        assertNull("суп за 20 дороже кошелька", game.wantsToNeeds(s, "bowl"))
    }

    @Test
    fun `радость и покупка в плане — кнопки нет`() {
        assertNull(game.wantsToNeeds(fed, "candy"))
        assertNull(game.wantsToNeeds(fed, "nope"))
    }

    @Test
    fun `до плана переносить нечего`() {
        val planning = game.newGame()

        assertNull(game.wantsLeft(planning))
        assertNull(game.wantsToNeeds(planning, "apple"))
        assertEquals(Rejection.PlanNotConfirmed, game.moveWantsToNeeds(planning, 5).reason())
    }

    @Test
    fun `в пределах плана нужного кнопки нет`() {
        val s = game.confirmPlan(game.newGame(), needs = 30, wants = 20, savings = 0).state()

        assertNull(game.wantsToNeeds(s, "apple"))
    }
}
