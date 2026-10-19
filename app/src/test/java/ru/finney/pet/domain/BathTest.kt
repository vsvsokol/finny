package ru.finney.pet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.Rejection
import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.domain.model.ItemUse
import ru.finney.pet.domain.model.ShopItem
import ru.finney.pet.domain.model.StatEffect
import ru.finney.pet.domain.period.PeriodRules
import kotlin.random.Random

/** Ванна: мыло на полочке, docs/bath-plan.md. */
class BathTest {

    private val bar = ShopItem("bar", "Мыло", 10, Category.NEEDS, effect = StatEffect(hygiene = 50), uses = 2)
    private val liquid = ShopItem("liquid", "Жидкое мыло", 24, Category.NEEDS, effect = StatEffect(hygiene = 50), uses = 8)
    private val towel = ShopItem("towel_shelf", "Полотенце", 15, Category.NEEDS, effect = StatEffect(hygiene = 10), uses = 6, bathExtra = true)
    private val content = GameContent(Fixtures.economy, Fixtures.shop + bar + liquid + towel, Fixtures.goals, Fixtures.tasks)
    private val game = run {
        var now = 0L
        Game(content, Random(7)) { ++now }
    }

    // Гигиена на старте — 40: ровно потолок воды.
    private val start = game.newGame()
    private val planned = game.confirmPlan(start, needs = 30, wants = 0, savings = 0).state()

    @Test
    fun `мыло ложится на полочку и шкал не меняет`() {
        val s = game.buy(planned, "bar").state()

        assertEquals(planned.pet, s.pet)
        assertEquals(planned.balance - 10, s.balance)
        assertEquals(2, game.stock(s, "bar"))
        assertTrue(game.hasSoap(s))
    }

    @Test
    fun `мытьё тратит один раз мыла, а не деньги`() {
        val bought = game.buy(planned, "bar").state()
        val s = game.wash(bought, "bar").state()

        assertEquals(bought.pet.hygiene + 50, s.pet.hygiene)
        assertEquals(bought.balance, s.balance)
        assertEquals(1, game.stock(s, "bar"))
        // Расход — в общем учёте полки, как у еды, а не в журнале денег.
        assertEquals(bought.ledger, s.ledger)
        assertEquals(listOf("bar" to 1), s.uses.map { it.itemId to it.portions })
    }

    @Test
    fun `вода ничего не тратит и ничего не записывает`() {
        val dirty = planned.copy(pet = planned.pet.copy(hygiene = 10))
        val s = game.wash(dirty, null).state()

        assertEquals(10 + Fixtures.economy.bath.water, s.pet.hygiene)
        assertEquals(dirty.ledger, s.ledger)
        assertTrue(s.uses.isEmpty())
    }

    @Test
    fun `один учёт — еда и мыло на одной полке`() {
        var s = game.buy(planned, "bar").state()
        s = game.buyToShelf(s, "apple").state()

        assertEquals(listOf("apple" to 1, "bar" to 2), game.shelf(s).map { it.item.id to it.count })
        s = game.wash(game.use(s, "apple").state(), "bar").state()
        assertEquals(listOf("bar" to 1), game.shelf(s).map { it.item.id to it.count })
    }

    @Test
    fun `покупка мыла до базы 12 — уже вся потрачена, бесплатного запаса нет`() {
        val old = game.buy(planned, "bar").state()
        // Так миграция 11 → 12 записала каждую старую покупку: применена сразу, целиком.
        val migrated = old.copy(uses = listOf(ItemUse(1, "bar", 0L, portions = null)))

        assertEquals(0, game.stock(migrated, "bar"))
        assertEquals(Rejection.NoStock("bar"), game.wash(migrated.copy(pet = migrated.pet.copy(hygiene = 10)), "bar").reason())
    }

    @Test
    fun `полотенце — свой шаг, без мыла тоже, и прибавляет своё`() {
        var s = game.buy(planned, "towel_shelf").state()
        s = s.copy(pet = s.pet.copy(hygiene = 10))

        val dried = game.wash(s, "towel_shelf").state()
        assertEquals(10 + 10, dried.pet.hygiene)
        assertEquals(5, game.stock(dried, "towel_shelf"))
    }

    @Test
    fun `зубы — щёткой с пастой, без мыла, и тратят пару`() {
        val brush = ShopItem("brush", "Щётка", 12, Category.NEEDS, effect = StatEffect(hygiene = 5), uses = 8, bathExtra = true, pairWith = "paste2")
        val paste2 = ShopItem("paste2", "Паста", 8, Category.NEEDS, effect = StatEffect(hygiene = 10), uses = 4, bathExtra = true, pairWith = "brush")
        val paired = GameContent(Fixtures.economy, Fixtures.shop + bar + brush + paste2, Fixtures.goals, Fixtures.tasks)
        var now = 0L
        val g = Game(paired, Random(7)) { ++now }
        var s = g.confirmPlan(g.newGame(), needs = 30, wants = 0, savings = 0).state()
        s = g.buy(s, "brush").state()
        s = s.copy(pet = s.pet.copy(hygiene = 10))

        // Щётка без пасты зубы не чистит, и щётка не тратится.
        assertEquals(Rejection.NoStock("paste2"), g.wash(s, "brush").reason())

        s = g.buy(s, "paste2").state()
        val brushed = g.wash(s, "brush").state()
        assertEquals(10 + 5 + 10, brushed.pet.hygiene)
        assertEquals(7, g.stock(brushed, "brush"))
        assertEquals(3, g.stock(brushed, "paste2"))
    }

    @Test
    fun `мытьё не считается тратой на нужное`() {
        val bought = game.buy(planned, "bar").state()
        val s = game.wash(bought, "bar").state()

        assertEquals(10, PeriodRules.facts(s.ledger, s.currentPeriod.number).needs)
    }

    @Test
    fun `кончилось мыло — отказ, запас не уходит в минус`() {
        var s = game.buy(planned, "bar").state()
        s = game.wash(s, "bar").state()
        s = s.copy(pet = s.pet.copy(hygiene = 20))
        s = game.wash(s, "bar").state()
        s = s.copy(pet = s.pet.copy(hygiene = 20))

        assertEquals(Rejection.NoStock("bar"), game.wash(s, "bar").reason())
        assertEquals(0, game.stock(s, "bar"))
        assertFalse(game.hasSoap(s))
    }

    @Test
    fun `водой можно и без плана, но слабее и только до потолка`() {
        val dirty = start.copy(pet = start.pet.copy(hygiene = 20))

        val once = game.wash(dirty, null).state()
        assertEquals(35, once.pet.hygiene)
        val twice = game.wash(once, null).state()
        assertEquals(40, twice.pet.hygiene)
        assertEquals(Rejection.NeedsSoap, game.wash(twice, null).reason())
    }

    @Test
    fun `чистого не моют — мыло зря не тратится`() {
        val bought = game.buy(planned, "bar").state()
        val clean = bought.copy(pet = bought.pet.copy(hygiene = 90))

        assertEquals(Rejection.AlreadyClean, game.wash(clean, "bar").reason())
    }

    @Test
    fun `после мыла чистый, но полотенцем ещё можно — до полной шкалы`() {
        val bought = game.buy(game.buy(planned, "bar").state(), "towel_shelf").state()
        val clean = bought.copy(pet = bought.pet.copy(hygiene = 90))

        assertEquals(100, game.wash(clean, "towel_shelf").state().pet.hygiene)
        val full = bought.copy(pet = bought.pet.copy(hygiene = 100))
        assertEquals(Rejection.AlreadyClean, game.wash(full, "towel_shelf").reason())
    }

    @Test
    fun `спящего не моют`() {
        val bought = game.buy(planned, "bar").state()
        val asleep = game.sleep(bought).state()

        assertEquals(Rejection.Asleep, game.wash(asleep, "bar").reason())
    }

    @Test
    fun `мыть можно только мылом с полочки`() {
        assertEquals(Rejection.NotWashable("soap"), game.wash(planned, "soap").reason())
        assertEquals(Rejection.UnknownItem("nope"), game.wash(planned, "nope").reason())
    }

    @Test
    fun `пока мыло есть, чистота в подсказке плана бесплатна`() {
        val bought = game.buy(planned, "liquid").state()
        val dirty = bought.copy(pet = bought.pet.copy(hygiene = 0))
        val noSoap = planned.copy(pet = planned.pet.copy(hygiene = 0))

        val withSoap = game.needsHint(dirty)!!
        val without = game.needsHint(noSoap)!!
        assertTrue("с мылом $withSoap, без $without", withSoap < without)
    }
}
