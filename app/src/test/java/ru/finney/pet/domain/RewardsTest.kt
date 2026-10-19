package ru.finney.pet.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finney.pet.content.ContentValidator
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.Rejection
import ru.finney.pet.domain.model.GameState
import ru.finney.pet.domain.model.Goal
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.model.PetLooks
import ru.finney.pet.domain.model.RewardKind
import ru.finney.pet.domain.model.RoomChoice
import ru.finney.pet.domain.model.RoomPart
import ru.finney.pet.domain.model.RoomThemes
import kotlin.random.Random

/** Копилка выдаёт не только вещи: тема комнаты и облик питомца проходят тот же путь, что шляпа. */
class RewardsTest {

    private val look = PetLooks.id(PetCharacter.PUSHISTIK, 1)

    private val content = Fixtures.content.copy(
        goals = Fixtures.content.goals + listOf(
            Goal("room_sea", "Комната «Море»", 40, moodBonus = 10, reward = "sea", rewardKind = RewardKind.ROOM),
            Goal("look", "Новый облик", 40, moodBonus = 10, reward = look, rewardKind = RewardKind.LOOK),
            Goal("room_town", "Комната «Городок»", 40, reward = "town", rewardKind = RewardKind.ROOM, unlockLevel = 5),
        ),
    )

    private val game = run {
        var now = 0L
        Game(content, Random(7)) { ++now }
    }

    /** Выбрать → положить → забрать. */
    private fun collect(goalId: String, start: GameState = game.newGame()): GameState {
        var s = game.selectGoal(start, goalId).state()
        s = game.confirmPlan(s, needs = 0, wants = 0, savings = 40).state()
        s = game.deposit(s, 40).state()
        return game.completeGoal(s).state()
    }

    @Test
    fun `в новой игре открыта только базовая комната, облик — из профиля`() {
        val s = game.newGame()
        assertEquals(RoomChoice(), s.room)
        assertEquals(listOf(RoomThemes.BASE), game.rooms(s))
        assertTrue(game.ownsRoom(s, RoomThemes.BASE))
        assertFalse(game.ownsRoom(s, "sea"))
        assertFalse(game.ownsLook(s, look))
        assertNull(game.lookFor(s, PetCharacter.PUSHISTIK))
    }

    @Test
    fun `цель-тема — накопили, забрали, комната сразу в новой теме`() {
        val s = collect("room_sea")

        assertTrue(game.ownsRoom(s, "sea"))
        assertEquals(RoomChoice.whole("sea"), s.room)
        assertEquals(listOf(RoomThemes.BASE, "sea"), game.rooms(s))
        assertTrue(s.isGoalCompleted("room_sea"))
        assertNull(s.activeGoalId)
        assertEquals("тема — не вещь: шляпа не надета", null, s.wornItemId)
    }

    @Test
    fun `комнату собирают по частям только из открытых тем`() {
        val start = game.newGame()
        assertEquals(Rejection.NotOwned("sea"), game.setRoom(start, "sea", RoomPart.WALL).reason())
        assertEquals(Rejection.UnknownItem("moon"), game.setRoom(start, "moon").reason())

        val s = game.setRoom(collect("room_sea"), RoomThemes.BASE, RoomPart.LAMP).state()
        assertEquals(RoomChoice(wall = "sea", window = "sea", lamp = RoomThemes.BASE), s.room)
        assertEquals("тема целиком", RoomChoice.whole(RoomThemes.BASE), game.setRoom(s, RoomThemes.BASE).state().room)
        assertEquals("купленное не отбирается", RoomChoice.whole("sea"), game.setRoom(s, "sea").state().room)
    }

    @Test
    fun `смена комнаты бесплатна и не трогает деньги и шкалы`() {
        val s = collect("room_sea")
        val after = game.setRoom(s, RoomThemes.BASE, RoomPart.WALL).state()
        assertEquals(s.balance, after.balance)
        assertEquals(s.pet, after.pet)
        assertEquals(s.ledger, after.ledger)
    }

    @Test
    fun `цель-облик — забрали, облик сразу на питомце, снять и надеть снова`() {
        val s = collect("look")

        assertTrue(game.ownsLook(s, look))
        assertEquals(look, s.wornLook)
        assertEquals(look, game.lookFor(s, PetCharacter.PUSHISTIK))
        assertEquals(listOf(look), game.looks(s, PetCharacter.PUSHISTIK))

        val bare = game.setLook(s, null).state()
        assertNull(bare.wornLook)
        assertEquals(look, game.setLook(bare, look).state().wornLook)
    }

    @Test
    fun `облик другого персонажа не надеть, а свой не виден на чужом питомце`() {
        val other = PetLooks.id(PetCharacter.ROGATIK, 2)
        val s = collect("look")
        assertEquals(Rejection.NotOwned(other), game.setLook(s, other).reason())
        assertEquals(Rejection.UnknownItem("look_dragon_1"), game.setLook(s, "look_dragon_1").reason())
        assertNull("сменили питомца в профиле — показываем цвет профиля", game.lookFor(s, PetCharacter.ROGATIK))
    }

    @Test
    fun `в демо все темы и облики открыты сразу, копить не надо`() {
        val demo = game.newGame(isDemo = true)
        assertEquals(RoomThemes.ALL, game.rooms(demo))
        assertEquals(RoomChoice(wall = "town", window = RoomThemes.BASE, lamp = RoomThemes.BASE),
            game.setRoom(demo, "town", RoomPart.WALL).state().room)
        assertEquals(PetLooks.of(PetCharacter.ROGATIK), game.looks(demo, PetCharacter.ROGATIK))
        assertEquals(look, game.setLook(demo, look).state().wornLook)

        val normal = game.newGame()
        assertEquals(Rejection.Locked(5), game.selectGoal(normal, "room_town").reason())
        assertFalse(game.ownsRoom(normal, "town"))
        assertFalse(game.ownsLook(normal, look))
    }

    @Test
    fun `старая запись цели без rewardKind читается как вещь`() {
        val json = Json { ignoreUnknownKeys = false }
        val old = json.decodeFromString(Goal.serializer(), """{ "id": "g", "label": "Шляпа", "price": 90, "reward": "hat_cowboy" }""")
        assertEquals(RewardKind.ITEM, old.rewardKind)
        val room = json.decodeFromString(Goal.serializer(), """{ "id": "r", "label": "Море", "price": 90, "reward": "sea", "rewardKind": "room" }""")
        assertEquals(RewardKind.ROOM, room.rewardKind)
    }

    @Test
    fun `валидатор проверяет награду по её виду`() {
        assertEquals(emptyList<String>(), ContentValidator.validate(content))
        val broken = content.copy(
            goals = content.goals + listOf(
                Goal("g1", "Луна", 50, reward = "moon", rewardKind = RewardKind.ROOM),
                Goal("g2", "База", 50, reward = RoomThemes.BASE, rewardKind = RewardKind.ROOM),
                Goal("g3", "Дракон", 50, reward = "look_dragon_1", rewardKind = RewardKind.LOOK),
                Goal("g4", "Ещё море", 50, reward = "sea", rewardKind = RewardKind.ROOM),
            ),
        )
        val errors = ContentValidator.validate(broken)
        assertTrue(errors.toString(), errors.any { "нет темы комнаты moon" in it })
        assertTrue(errors.toString(), errors.any { "базовая тема" in it })
        assertTrue(errors.toString(), errors.any { "нет облика look_dragon_1" in it })
        assertTrue(errors.toString(), errors.any { "награда sea — у двух целей" in it })
    }}
