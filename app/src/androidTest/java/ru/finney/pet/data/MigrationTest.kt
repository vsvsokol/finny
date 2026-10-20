package ru.finney.pet.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.data.db.FinneyDatabase

/** Обновление приложения не стирает прогресс: база версии 3 переносится в 4 … 13 с данными. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinneyDatabase::class.java)

    @Test
    fun migrate3To4KeepsProfileAndAddsEmptyWornItem() {
        helper.createDatabase(dbName, 3).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, petName, petCharacter, bodyColor, eyes, activeGoalId, isDemo, createdAt) " +
                    "VALUES (1, 'Финни', 'PUSHISTIK', 'A', 'ROUND', 'goal_bike', 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(dbName, 4, true, FinneyDatabase.MIGRATION_3_4).use { db ->
            db.query("SELECT petName, activeGoalId, wornItemId FROM profiles WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Финни", c.getString(0))
                assertEquals("goal_bike", c.getString(1))
                assertTrue("после миграции ничего не надето", c.isNull(2))
            }
        }
    }

    @Test
    fun migrate4To5KeepsStatsAndAddsEnergy() {
        helper.createDatabase(dbName, 4).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, petName, petCharacter, bodyColor, eyes, activeGoalId, isDemo, createdAt, wornItemId) " +
                    "VALUES (1, 'Финни', 'PUSHISTIK', 'A', 'ROUND', NULL, 0, 1, 'hat_cowboy')",
            )
            db.execSQL("INSERT INTO pet_state (profileId, satiety, hygiene, mood) VALUES (1, 55, 44, 33)")
        }

        helper.runMigrationsAndValidate(dbName, 5, true, FinneyDatabase.MIGRATION_4_5).use { db ->
            db.query("SELECT satiety, hygiene, mood, energy, sleepingSince FROM pet_state WHERE profileId = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(55, c.getInt(0))
                assertEquals(44, c.getInt(1))
                assertEquals(33, c.getInt(2))
                assertEquals(FinneyDatabase.ENERGY_ON_MIGRATION.toInt(), c.getInt(3))
                assertTrue("после обновления никто не спит", c.isNull(4))
            }
            db.query("SELECT wornItemId FROM profiles WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("hat_cowboy", c.getString(0))
            }
        }
    }

    @Test
    fun migrate5To6KeepsPeriodsWithoutLevelGame() {
        helper.createDatabase(dbName, 5).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, petName, petCharacter, bodyColor, eyes, activeGoalId, isDemo, createdAt, wornItemId) " +
                    "VALUES (1, 'Финни', 'PUSHISTIK', 'A', 'ROUND', NULL, 0, 1, NULL)",
            )
            db.execSQL(
                "INSERT INTO periods (profileId, number, stage, phase, needsCovered, planMatched, savingsAdded, " +
                    "successfulTasks, pointsEarned) VALUES (1, 1, 1, 'CLOSED', 1, 1, 0, 1, 5)",
            )
        }

        helper.runMigrationsAndValidate(dbName, 6, true, FinneyDatabase.MIGRATION_5_6).use { db ->
            db.query("SELECT pointsEarned, levelTaskId, gamePassed FROM periods WHERE profileId = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(5, c.getInt(0))
                assertTrue("у старого периода игры уровня нет", c.isNull(1))
                assertTrue("итог по игре не записан — читается как пройдено", c.isNull(2))
            }
        }
    }

    @Test
    fun migrate6To7KeepsPetAndToysAndStartsWithoutPlaySession() {
        helper.createDatabase(dbName, 6).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, petName, petCharacter, bodyColor, eyes, activeGoalId, isDemo, createdAt, wornItemId) " +
                    "VALUES (1, 'Финни', 'PUSHISTIK', 'A', 'ROUND', NULL, 0, 1, NULL)",
            )
            db.execSQL(
                "INSERT INTO pet_state (profileId, satiety, hygiene, mood, energy, sleepingSince) VALUES (1, 55, 44, 33, 80, NULL)",
            )
            // Мячик, купленный до игрушек, — обычная покупка в ledger: после обновления он в зале.
            db.execSQL(
                "INSERT INTO ledger (profileId, periodNumber, type, balanceDelta, savingsDelta, category, itemId, goalId, " +
                    "taskId, unplanned, createdAt) VALUES (1, 1, 'PURCHASE', -15, 0, 'WANTS', 'toy_ball', NULL, NULL, 0, 2)",
            )
        }

        helper.runMigrationsAndValidate(dbName, 7, true, FinneyDatabase.MIGRATION_6_7).use { db ->
            db.query("SELECT satiety, hygiene, mood, energy, playMood, playSince FROM pet_state WHERE profileId = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(55, c.getInt(0))
                assertEquals(44, c.getInt(1))
                assertEquals(33, c.getInt(2))
                assertEquals(80, c.getInt(3))
                assertEquals("сессии игры ещё не было", 0, c.getInt(4))
                assertTrue(c.isNull(5))
            }
            db.query("SELECT itemId FROM ledger WHERE profileId = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("toy_ball", c.getString(0))
            }
        }
    }

    @Test
    fun migrate7To8KeepsClosedPeriodsUndecided() {
        helper.createDatabase(dbName, 7).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, petName, petCharacter, bodyColor, eyes, activeGoalId, isDemo, createdAt, wornItemId) " +
                    "VALUES (1, 'Финни', 'PUSHISTIK', 'A', 'ROUND', NULL, 1, 1, NULL)",
            )
            db.execSQL(
                "INSERT INTO periods (profileId, number, stage, phase, needsCovered, planMatched, savingsAdded, " +
                    "successfulTasks, pointsEarned, levelTaskId, gamePassed) VALUES (1, 1, 1, 'CLOSED', 1, 1, 0, 1, 5, 'game_race', 1)",
            )
        }

        helper.runMigrationsAndValidate(dbName, 8, true, FinneyDatabase.MIGRATION_7_8).use { db ->
            db.query("SELECT pointsEarned, gamePassed, passed FROM periods WHERE profileId = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(5, c.getInt(0))
                assertEquals(1, c.getInt(1))
                assertTrue("итог старого периода решается по обычным правилам", c.isNull(2))
            }
            db.query("SELECT isDemo FROM profiles WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
        }
    }

    @Test
    fun migrate8To9KeepsAttemptsWithoutSeed() {
        helper.createDatabase(dbName, 8).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, petName, petCharacter, bodyColor, eyes, activeGoalId, isDemo, createdAt, wornItemId) " +
                    "VALUES (1, 'Финни', 'PUSHISTIK', 'A', 'ROUND', NULL, 0, 1, NULL)",
            )
            db.execSQL(
                "INSERT INTO task_attempts (profileId, periodNumber, taskId, outcome, reward, createdAt) " +
                    "VALUES (1, 1, 'game_lemonade', 'SUCCESS', 15, 2)",
            )
        }

        helper.runMigrationsAndValidate(dbName, 9, true, FinneyDatabase.MIGRATION_8_9).use { db ->
            db.query("SELECT taskId, reward, seed FROM task_attempts WHERE profileId = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("game_lemonade", c.getString(0))
                assertEquals(15, c.getInt(1))
                assertTrue("у старой попытки чисел без разброса зерна нет", c.isNull(2))
            }
        }
    }

    @Test
    fun migrate9To10KeepsAttemptsWithoutBonus() {
        helper.createDatabase(dbName, 9).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, petName, petCharacter, bodyColor, eyes, activeGoalId, isDemo, createdAt, wornItemId) " +
                    "VALUES (1, 'Финни', 'PUSHISTIK', 'A', 'ROUND', NULL, 0, 1, NULL)",
            )
            db.execSQL(
                "INSERT INTO task_attempts (profileId, periodNumber, taskId, outcome, reward, createdAt, seed) " +
                    "VALUES (1, 1, 'game_rainy', 'SUCCESS', 15, 2, 42)",
            )
        }

        helper.runMigrationsAndValidate(dbName, 10, true, FinneyDatabase.MIGRATION_9_10).use { db ->
            db.query("SELECT reward, seed, bonus FROM task_attempts WHERE profileId = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(15, c.getInt(0))
                assertEquals(42L, c.getLong(1))
                assertEquals("бонус у старой попытки не засчитан", 0, c.getInt(2))
            }
        }
    }

    @Test
    fun migrate10To11KeepsHatAndAddsEmptyEyesSlot() {
        helper.createDatabase(dbName, 10).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, petName, petCharacter, bodyColor, eyes, activeGoalId, isDemo, createdAt, wornItemId) " +
                    "VALUES (1, 'Финни', 'PUSHISTIK', 'A', 'ROUND', NULL, 0, 1, 'hat_cowboy')",
            )
        }

        helper.runMigrationsAndValidate(dbName, 11, true, FinneyDatabase.MIGRATION_10_11).use { db ->
            db.query("SELECT wornItemId, wornEyesId FROM profiles WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("шляпа на месте", "hat_cowboy", c.getString(0))
                assertTrue("очков у старого профиля нет", c.isNull(1))
            }
        }
    }

    @Test
    fun migrate11To12KeepsPurchasesAndLeavesShelfEmpty() {
        helper.createDatabase(dbName, 11).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, petName, petCharacter, bodyColor, eyes, activeGoalId, isDemo, createdAt) " +
                    "VALUES (1, 'Финни', 'PUSHISTIK', 'A', 'ROUND', NULL, 0, 1)",
            )
            db.execSQL(
                "INSERT INTO ledger (id, profileId, periodNumber, type, balanceDelta, savingsDelta, category, itemId, goalId, taskId, unplanned, createdAt) VALUES " +
                    "(1, 1, 1, 'INCOME', 50, 0, NULL, NULL, NULL, NULL, 0, 10), " +
                    "(2, 1, 1, 'PURCHASE', -10, 0, 'NEEDS', 'food_apple', NULL, NULL, 0, 20), " +
                    "(3, 1, 2, 'PURCHASE', -10, 0, 'NEEDS', 'food_apple', NULL, NULL, 0, 30)",
            )
        }

        helper.runMigrationsAndValidate(dbName, 12, true, FinneyDatabase.MIGRATION_11_12).use { db ->
            db.query("SELECT COUNT(*) FROM ledger").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("операции на месте", 3, c.getInt(0))
            }
            db.query("SELECT periodNumber, itemId, createdAt FROM item_uses ORDER BY id").use { c ->
                assertEquals("каждая старая покупка уже применена", 2, c.count)
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
                assertEquals("food_apple", c.getString(1))
                assertEquals(20L, c.getLong(2))
            }
        }
    }

    @Test
    fun migrate12To13MovesWashesFromLedgerToItemUses() {
        helper.createDatabase(dbName, 12).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, petName, petCharacter, bodyColor, eyes, activeGoalId, isDemo, createdAt) " +
                    "VALUES (1, 'Финни', 'PUSHISTIK', 'A', 'ROUND', NULL, 0, 1)",
            )
            db.execSQL(
                "INSERT INTO ledger (id, profileId, periodNumber, type, balanceDelta, savingsDelta, category, itemId, goalId, taskId, unplanned, createdAt) VALUES " +
                    "(1, 1, 1, 'INCOME', 50, 0, NULL, NULL, NULL, NULL, 0, 10), " +
                    "(2, 1, 1, 'PURCHASE', -10, 0, 'NEEDS', 'care_soap', NULL, NULL, 0, 20), " +
                    "(3, 1, 1, 'WASH', 0, 0, NULL, 'care_soap', NULL, NULL, 0, 30), " +
                    "(4, 1, 1, 'WASH', 0, 0, NULL, NULL, NULL, NULL, 0, 40)",
            )
            db.execSQL("INSERT INTO item_uses (id, profileId, periodNumber, itemId, createdAt) VALUES (1, 1, 1, 'food_apple', 15)")
        }

        helper.runMigrationsAndValidate(dbName, 13, true, FinneyDatabase.MIGRATION_12_13).use { db ->
            db.query("SELECT type FROM ledger ORDER BY id").use { c ->
                val types = List(c.count) { c.moveToPosition(it); c.getString(0) }
                assertEquals("мытьё ушло из журнала денег", listOf("INCOME", "PURCHASE"), types)
            }
            db.query("SELECT itemId, createdAt, portions FROM item_uses ORDER BY id").use { c ->
                assertEquals(2, c.count)
                assertTrue(c.moveToFirst())
                assertEquals("food_apple", c.getString(0))
                assertTrue("старая строка — вся покупка", c.isNull(2))
                assertTrue(c.moveToNext())
                assertEquals("care_soap", c.getString(0))
                assertEquals(30L, c.getLong(1))
                assertEquals("мытьё — один раз", 1, c.getInt(2))
            }
        }
    }

    @Test
    fun migrate13To14KeepsProfileWithBaseRoomAndProfileColor() {
        helper.createDatabase(dbName, 13).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, petName, petCharacter, bodyColor, eyes, activeGoalId, isDemo, createdAt, wornItemId) " +
                    "VALUES (1, 'Финни', 'PUSHISTIK', 'B', 'ROUND', NULL, 0, 1, 'hat_cowboy')",
            )
        }

        helper.runMigrationsAndValidate(dbName, 14, true, FinneyDatabase.MIGRATION_13_14).use { db ->
            db.query("SELECT bodyColor, wornItemId, roomWall, roomWindow, roomLamp, wornLook FROM profiles WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("цвет профиля на месте", "B", c.getString(0))
                assertEquals("шляпа на месте", "hat_cowboy", c.getString(1))
                assertTrue("комната — базовая", (2..4).all { c.isNull(it) })
                assertTrue("платного облика нет", c.isNull(5))
            }
        }
    }

    @Test
    fun migrate14To15PutsRugFromWallTheme() {
        helper.createDatabase(dbName, 14).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, petName, petCharacter, bodyColor, eyes, activeGoalId, isDemo, createdAt, roomWall, roomLamp) " +
                    "VALUES (1, 'Финни', 'PUSHISTIK', 'B', 'ROUND', NULL, 0, 1, 'sea', 'honey'), " +
                    "(2, 'Рогатик', 'ROGATIK', 'A', 'ROUND', NULL, 0, 2, NULL, NULL)",
            )
        }

        helper.runMigrationsAndValidate(dbName, 15, true, FinneyDatabase.MIGRATION_14_15).use { db ->
            db.query("SELECT roomWall, roomLamp, roomRug FROM profiles ORDER BY id").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("стена на месте", "sea", c.getString(0))
                assertEquals("торшер на месте", "honey", c.getString(1))
                assertEquals("ковёр — из темы стены", "sea", c.getString(2))
                assertTrue(c.moveToNext())
                assertTrue("базовая комната — базовый ковёр", c.isNull(2))
            }
        }
    }
}
