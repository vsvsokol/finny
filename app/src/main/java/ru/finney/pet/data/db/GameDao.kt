package ru.finney.pet.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.finney.pet.domain.model.BodyColor
import ru.finney.pet.domain.model.EyesVariant
import ru.finney.pet.domain.model.PetCharacter

@Dao
abstract class GameDao {

    @Query("SELECT * FROM profiles ORDER BY createdAt, id")
    abstract fun observeProfiles(): Flow<List<ProfileEntity>>

    @Transaction
    @Query("SELECT * FROM profiles WHERE id = :profileId")
    abstract fun observeGame(profileId: Long): Flow<ProfileWithGame?>

    @Transaction
    @Query("SELECT * FROM profiles WHERE id = :profileId")
    abstract suspend fun loadGame(profileId: Long): ProfileWithGame?

    @Insert
    abstract suspend fun insertProfile(profile: ProfileEntity): Long

    @Query(
        "UPDATE profiles SET petName = :petName, petCharacter = :character, bodyColor = :bodyColor, eyes = :eyes " +
            "WHERE id = :profileId",
    )
    abstract suspend fun updateProfile(
        profileId: Long,
        petName: String,
        character: PetCharacter,
        bodyColor: BodyColor,
        eyes: EyesVariant,
    )

    @Query("UPDATE profiles SET isDemo = :isDemo WHERE id = :profileId")
    abstract suspend fun setDemo(profileId: Long, isDemo: Boolean)

    @Query("UPDATE profiles SET activeGoalId = :goalId WHERE id = :profileId")
    abstract suspend fun setActiveGoal(profileId: Long, goalId: String?)

    @Query("UPDATE profiles SET wornItemId = :itemId, wornEyesId = :eyesId WHERE id = :profileId")
    abstract suspend fun setWorn(profileId: Long, itemId: String?, eyesId: String?)

    @Query(
        "UPDATE profiles SET roomWall = :wall, roomWindow = :window, roomLamp = :lamp, roomRug = :rug, wornLook = :look " +
            "WHERE id = :profileId",
    )
    abstract suspend fun setRoomAndLook(
        profileId: Long,
        wall: String?,
        window: String?,
        lamp: String?,
        rug: String?,
        look: String?,
    )

    @Upsert
    abstract suspend fun upsertPet(pet: PetStateEntity)

    @Upsert
    abstract suspend fun upsertPeriods(periods: List<PeriodEntity>)

    @Insert
    abstract suspend fun insertLedger(entries: List<LedgerEntryEntity>)

    @Insert
    abstract suspend fun insertAttempts(attempts: List<TaskAttemptEntity>)

    @Insert
    abstract suspend fun insertUses(uses: List<ItemUseEntity>)

    @Query("SELECT COUNT(*) FROM ledger WHERE profileId = :profileId")
    abstract suspend fun ledgerCount(profileId: Long): Int

    @Query("SELECT COUNT(*) FROM task_attempts WHERE profileId = :profileId")
    abstract suspend fun attemptCount(profileId: Long): Int

    @Query("SELECT COUNT(*) FROM item_uses WHERE profileId = :profileId")
    abstract suspend fun useCount(profileId: Long): Int

    @Query("DELETE FROM profiles WHERE id = :profileId")
    abstract suspend fun deleteProfile(profileId: Long)

    @Query("DELETE FROM profiles")
    abstract suspend fun deleteAllProfiles()

    @Query("DELETE FROM periods WHERE profileId = :profileId")
    abstract suspend fun deletePeriods(profileId: Long)

    @Query("DELETE FROM ledger WHERE profileId = :profileId")
    abstract suspend fun deleteLedger(profileId: Long)

    @Query("DELETE FROM task_attempts WHERE profileId = :profileId")
    abstract suspend fun deleteAttempts(profileId: Long)

    @Query("DELETE FROM item_uses WHERE profileId = :profileId")
    abstract suspend fun deleteUses(profileId: Long)

    @Transaction
    open suspend fun createGame(profile: ProfileEntity, game: GameRows): Long {
        val profileId = insertProfile(profile)
        writeGame(profileId, game.withProfileId(profileId))
        return profileId
    }

    /**
     * Операции, попытки заданий и расход с полки в domain только дописываются, поэтому в базу добавляется
     * только хвост, которого там ещё нет. Периоды и шкалы перезаписываются целиком.
     */
    @Transaction
    open suspend fun writeGame(profileId: Long, game: GameRows) {
        setActiveGoal(profileId, game.activeGoalId)
        setWorn(profileId, game.wornItemId, game.wornEyesId)
        setRoomAndLook(profileId, game.roomWall, game.roomWindow, game.roomLamp, game.roomRug, game.wornLook)
        upsertPet(game.pet)
        upsertPeriods(game.periods)

        val storedLedger = ledgerCount(profileId)
        check(storedLedger <= game.ledger.size) { "Операций в базе $storedLedger, в состоянии ${game.ledger.size}" }
        insertLedger(game.ledger.drop(storedLedger))

        val storedAttempts = attemptCount(profileId)
        check(storedAttempts <= game.attempts.size) { "Попыток в базе $storedAttempts, в состоянии ${game.attempts.size}" }
        insertAttempts(game.attempts.drop(storedAttempts))

        val storedUses = useCount(profileId)
        check(storedUses <= game.uses.size) { "Расхода с полки в базе $storedUses, в состоянии ${game.uses.size}" }
        insertUses(game.uses.drop(storedUses))
    }

    /** Сброс прогресса: периоды, операции, попытки и расход с полки стираются и пишутся заново. Профиль тот же. */
    @Transaction
    open suspend fun replaceGame(profileId: Long, game: GameRows) {
        deletePeriods(profileId)
        deleteLedger(profileId)
        deleteAttempts(profileId)
        deleteUses(profileId)
        writeGame(profileId, game)
    }
}

/** Игровое состояние, разложенное по таблицам. */
data class GameRows(
    val activeGoalId: String?,
    val wornItemId: String?,
    val wornEyesId: String?,
    val roomWall: String? = null,
    val roomWindow: String? = null,
    val roomLamp: String? = null,
    val roomRug: String? = null,
    val wornLook: String? = null,
    val pet: PetStateEntity,
    val periods: List<PeriodEntity>,
    val ledger: List<LedgerEntryEntity>,
    val attempts: List<TaskAttemptEntity>,
    val uses: List<ItemUseEntity> = emptyList(),
) {
    fun withProfileId(profileId: Long) = copy(
        pet = pet.copy(profileId = profileId),
        periods = periods.map { it.copy(profileId = profileId) },
        ledger = ledger.map { it.copy(profileId = profileId) },
        attempts = attempts.map { it.copy(profileId = profileId) },
        uses = uses.map { it.copy(profileId = profileId) },
    )
}
