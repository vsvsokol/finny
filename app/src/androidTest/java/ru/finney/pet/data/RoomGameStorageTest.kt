package ru.finney.pet.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.content.AssetContentLoader
import ru.finney.pet.data.db.FinneyDatabase
import ru.finney.pet.data.repository.RoomGameStorage
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.GameResult
import ru.finney.pet.domain.game.GameStore
import ru.finney.pet.domain.game.ProfileResult
import ru.finney.pet.domain.game.TaskResult
import ru.finney.pet.domain.model.BodyColor
import ru.finney.pet.domain.model.EntryType
import ru.finney.pet.domain.model.EyesVariant
import ru.finney.pet.domain.model.GameState
import ru.finney.pet.domain.model.PetAppearance
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.tasks.TaskInput
import kotlin.random.Random

/** ТЗ п. 2.5.13: профиль, баланс, покупки, накопления, цель и прогресс переживают перезапуск. */
@RunWith(AndroidJUnit4::class)
class RoomGameStorageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "room-storage-test.db"
    private val content = AssetContentLoader(context).load()

    /**
     * Игра уровня выбирается случайно, поэтому каждому сценарию — своя игра с одним
     * и тем же зерном: база и сценарий в памяти выбирают одинаково.
     */
    private fun seededGame() = Game(content, Random(SEED))

    private val game = seededGame()
    private lateinit var db: FinneyDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        db = open()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun open() = Room.databaseBuilder(context, FinneyDatabase::class.java, dbName).build()

    private fun store(database: FinneyDatabase) = GameStore(seededGame(), RoomGameStorage(database.gameDao()))

    private suspend fun GameStore.ok(id: Long, command: Game.(ru.finney.pet.domain.model.GameState) -> GameResult) {
        val result = execute(id, command)
        assertTrue("команда отклонена: $result", result is GameResult.Ok)
    }

    /**
     * Два периода со всеми видами операций. Профиль — демо: в нём всё открыто, иначе лавка
     * и «Дорога к цели» закрыты до своих уровней. Цель — «Корона», она открыта всегда.
     */
    private suspend fun playTwoPeriods(store: GameStore, id: Long) {
        repeat(2) { round ->
            store.ok(id) { selectGoal(it, "goal_crown") }
            store.ok(id) { addParentBonus(it, 10) }
            store.ok(id) { confirmPlan(it, needs = 30, wants = 10, savings = 10) }
            store.ok(id) { buy(it, "food_bowl") }
            store.ok(id) { buy(it, "care_soap") }
            store.ok(id) { buy(it, "treat_candy") }
            val task = if (round == 0) "game_lemonade" else "game_race"
            val input = if (round == 0) TaskInput.Stock(4) else TaskInput.DailyDeposits(listOf(10, 10, 5, 5, 10, 10))
            assertTrue(store.submitTask(id, task, input) is TaskResult.Submitted)
            store.ok(id) { deposit(it, 15) }
            store.ok(id) { withdraw(it, 5) }
            store.ok(id) { closePeriod(it) }
        }
    }

    @Test
    fun stateSurvivesDatabaseReopen() = runBlocking {
        val store = store(db)
        val look = PetAppearance(PetCharacter.PUSHISTIK, BodyColor.C, EyesVariant.OVAL)
        val id = (store.createProfile("Финни", look, isDemo = true) as ProfileResult.Saved).profileId
        playTwoPeriods(store, id)
        val before = RoomGameStorage(db.gameDao()).load(id)!!

        db.close()
        db = open()
        val after = RoomGameStorage(db.gameDao()).load(id)!!

        assertEquals(before, after)
        assertEquals(3, after.state.periods.size)
        assertTrue(after.state.periods.take(2).all { it.result != null })
        assertTrue(after.state.balance >= 0)
        // За круг: + 15 − 5 кнопками; копилка в плане — только обещание.
        assertEquals(20, after.state.goalSaved("goal_crown"))
        assertEquals("Финни", after.profile.petName)
        assertEquals(look, after.profile.appearance)
    }

    @Test
    fun wornAccessorySurvivesReopen() = runBlocking {
        val store = store(db)
        val look = PetAppearance(PetCharacter.ROGATIK, BodyColor.A, EyesVariant.ROUND)
        val id = (store.createProfile("Финни", look) as ProfileResult.Saved).profileId
        // Шляпа — награда за цель: копим 90 за два уровня и забираем.
        store.ok(id) { selectGoal(it, "goal_crown") }
        store.ok(id) { addParentBonus(it, 20) }
        store.ok(id) { confirmPlan(it, needs = 5, wants = 5, savings = 60) }
        store.ok(id) { deposit(it, 60) }
        store.ok(id) { closePeriod(it) }
        store.ok(id) { confirmPlan(it, needs = 5, wants = 5, savings = 30) }
        store.ok(id) { deposit(it, 30) }
        store.ok(id) { completeGoal(it) }

        db.close()
        db = open()
        assertEquals("hat_cowboy", RoomGameStorage(db.gameDao()).load(id)!!.state.wornItemId)

        val reopened = store(db)
        reopened.ok(id) { takeOff(it) }
        assertNull(RoomGameStorage(db.gameDao()).load(id)!!.state.wornItemId)
    }

    @Test
    fun shelfSurvivesReopen() = runBlocking {
        val store = store(db)
        val look = PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND)
        val id = (store.createProfile("Финни", look) as ProfileResult.Saved).profileId
        store.ok(id) { selectGoal(it, "goal_crown") }
        store.ok(id) { confirmPlan(it, needs = 30, wants = 10, savings = 10) }
        store.ok(id) { buyToShelf(it, "food_apple") }
        store.ok(id) { buyToShelf(it, "food_apple") }
        store.ok(id) { use(it, "food_apple") }
        store.ok(id) { buy(it, "care_soap") }
        store.ok(id) { wash(it, "care_soap") }

        db.close()
        db = open()
        val state = RoomGameStorage(db.gameDao()).load(id)!!.state

        // Один учёт: яблоко и мыло — на одной полке, мытьё в журнал денег не пишется.
        assertEquals(1, game.stock(state, "food_apple"))
        assertEquals(1, game.stock(state, "care_soap"))
        assertEquals(listOf("food_apple" to 1, "care_soap" to 1), state.uses.map { it.itemId to it.portions })
        assertTrue(state.ledger.none { it.type == EntryType.WASH })
    }

    @Test
    fun savedStateEqualsInMemoryGame() = runBlocking {
        val store = store(db)
        val id = (store.createProfile("Финни", PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND), isDemo = true) as ProfileResult.Saved).profileId
        playTwoPeriods(store, id)

        // Тот же сценарий без базы: сохранение не должно ничего терять или добавлять.
        val game = seededGame()
        var expected = game.newGame(isDemo = true)
        fun apply(result: GameResult) {
            expected = (result as GameResult.Ok).state
        }
        repeat(2) { round ->
            apply(game.selectGoal(expected, "goal_crown"))
            apply(game.addParentBonus(expected, 10))
            apply(game.confirmPlan(expected, 30, 10, 10))
            apply(game.buy(expected, "food_bowl"))
            apply(game.buy(expected, "care_soap"))
            apply(game.buy(expected, "treat_candy"))
            val task = if (round == 0) "game_lemonade" else "game_race"
            val input = if (round == 0) TaskInput.Stock(4) else TaskInput.DailyDeposits(listOf(10, 10, 5, 5, 10, 10))
            expected = (game.submitTask(expected, task, input) as TaskResult.Submitted).state
            apply(game.deposit(expected, 15))
            apply(game.withdraw(expected, 5))
            apply(game.closePeriod(expected))
        }
        val actual = RoomGameStorage(db.gameDao()).load(id)!!.state

        // createdAt у операций разный: сравниваем без времени.
        fun timeless(state: GameState) = state.copy(
            ledger = state.ledger.map { it.copy(createdAt = 0) },
            attempts = state.attempts.map { it.copy(createdAt = 0) },
            uses = state.uses.map { it.copy(createdAt = 0) },
        )
        assertEquals(timeless(expected), timeless(actual))
    }

    @Test
    fun deletingProfileRemovesAllItsRows() = runBlocking {
        val store = store(db)
        val look = PetAppearance(PetCharacter.PUSHISTIK, BodyColor.B, EyesVariant.SLY)
        val first = (store.createProfile("Финни", look, isDemo = true) as ProfileResult.Saved).profileId
        val second = (store.createProfile("Бублик", look) as ProfileResult.Saved).profileId
        playTwoPeriods(store, first)

        store.deleteProfile(first)

        assertNull(RoomGameStorage(db.gameDao()).load(first))
        val cursor = db.openHelper.readableDatabase.query("SELECT (SELECT COUNT(*) FROM ledger WHERE profileId = $first) + (SELECT COUNT(*) FROM periods WHERE profileId = $first) + (SELECT COUNT(*) FROM task_attempts WHERE profileId = $first) + (SELECT COUNT(*) FROM pet_state WHERE profileId = $first) + (SELECT COUNT(*) FROM item_uses WHERE profileId = $first)")
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
        cursor.close()
        assertEquals(listOf(second), store.observeProfiles().first().map { it.id })

        store.deleteAll()
        assertTrue(store.observeProfiles().first().isEmpty())
    }

    @Test
    fun resetProgressKeepsPetAndStartsOver() = runBlocking {
        val store = store(db)
        val look = PetAppearance(PetCharacter.PUSHISTIK, BodyColor.C, EyesVariant.OVAL)
        val id = (store.createProfile("Финни", look, isDemo = true) as ProfileResult.Saved).profileId
        playTwoPeriods(store, id)

        store.resetProgress(id)

        val reset = RoomGameStorage(db.gameDao()).load(id)!!
        assertEquals("Финни", reset.profile.petName)
        assertEquals(look, reset.profile.appearance)
        // Игра уровня у нового начала своя, случайная: сравниваем без неё.
        assertTrue(reset.state.currentPeriod.levelTaskId != null)
        val fresh = game.newGame(isDemo = true).withoutLevelGame()
        assertEquals(fresh, reset.state.withoutLevelGame().let { it.copy(ledger = it.ledger.map { e -> e.copy(createdAt = fresh.ledger.first().createdAt) }) })

        // После сброса игра продолжается обычными командами: хвосты операций считаются с нуля.
        store.ok(id) { selectGoal(it, "goal_bike") }
        store.ok(id) { confirmPlan(it, needs = 5, wants = 5, savings = 5) }
        store.ok(id) { closePeriod(it) }
        assertEquals(2, RoomGameStorage(db.gameDao()).load(id)!!.state.periods.size)
    }

    private fun GameState.withoutLevelGame() = copy(periods = periods.map { it.copy(levelTaskId = null) })

    private companion object {
        const val SEED = 7
    }
}
