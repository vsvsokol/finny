package ru.finney.pet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finney.pet.domain.game.Rejection
import ru.finney.pet.domain.game.ShelfItem
import ru.finney.pet.domain.model.Category

/** Полка расходников: «Купить» кладёт на полку, «Съесть» — отдельно (чек-лист рывка, раздел 4). */
class ShelfTest {

    private val game = Fixtures.game()
    private val active = game.confirmPlan(game.newGame(), needs = 30, wants = 10, savings = 0).state()

    @Test
    fun `купленный расходник ложится на полку и на питомца не действует`() {
        val s = game.buyToShelf(active, "apple").state()

        assertEquals(40, s.balance)
        assertEquals(Category.NEEDS, s.ledger.last().category)
        assertEquals(active.pet, s.pet)
        assertEquals(listOf(ShelfItem(Fixtures.content.item("apple")!!, 1)), game.shelf(s))
    }

    @Test
    fun `съесть с полки — шкала растёт, полка пустеет, деньги не списываются`() {
        val shelved = game.buyToShelf(game.buyToShelf(active, "apple").state(), "apple").state()

        val s = game.use(shelved, "apple").state()

        assertEquals(shelved.pet.satiety + 20, s.pet.satiety)
        assertEquals(shelved.balance, s.balance)
        assertEquals(1, s.onShelf("apple"))
        assertEquals(0, game.use(s, "apple").state().onShelf("apple"))
    }

    @Test
    fun `пустая полка — взять нечего`() {
        assertEquals(Rejection.NotOwned("apple"), game.use(active, "apple").reason())
        assertEquals(Rejection.UnknownItem("nope"), game.use(active, "nope").reason())
        assertEquals(Rejection.NotPlayable("teddy"), game.use(active, "teddy").reason())
    }

    @Test
    fun `купить и сразу съесть — как раньше, на полке ничего не остаётся`() {
        val s = game.buy(active, "apple").state()

        assertEquals(active.pet.satiety + 20, s.pet.satiety)
        assertEquals(40, s.balance)
        assertTrue(game.shelf(s).isEmpty())
    }

    @Test
    fun `взять с полки можно и до плана нового уровня`() {
        val shelved = game.buyToShelf(active, "apple").state()
        val nextLevel = game.closePeriod(shelved).state()

        val s = game.use(nextLevel, "apple").state()

        assertEquals(nextLevel.pet.satiety + 20, s.pet.satiety)
        assertEquals(nextLevel.currentPeriod.number, s.uses.last().periodNumber)
    }

    @Test
    fun `во сне в магазине купить можно, а съесть — нет`() {
        val asleep = game.sleep(active).state()

        val s = game.buyToShelf(asleep, "apple").state()

        assertEquals(Rejection.Asleep, game.use(s, "apple").reason())
        assertEquals(Rejection.Asleep, game.buy(asleep, "apple").reason())
    }

    @Test
    fun `шапка и игрушка на полку не ложатся — действуют сразу`() {
        val s = game.buyToShelf(active, "teddy").state()

        assertEquals(active.pet.mood + 10, s.pet.mood)
        assertTrue(game.shelf(s).isEmpty())
        assertEquals(Rejection.AlreadyOwned, game.buyToShelf(s, "teddy").reason())
    }

    @Test
    fun `полка — в порядке магазина`() {
        var s = game.buyToShelf(active, "candy").state()
        s = game.buyToShelf(s, "apple").state()
        s = game.buyToShelf(s, "candy").state()

        assertEquals(listOf("apple" to 1, "candy" to 2), game.shelf(s).map { it.item.id to it.count })
    }
}
