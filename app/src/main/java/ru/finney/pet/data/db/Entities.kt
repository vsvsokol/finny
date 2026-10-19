package ru.finney.pet.data.db

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import ru.finney.pet.domain.model.BodyColor
import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.EntryType
import ru.finney.pet.domain.model.EyesVariant
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.model.PeriodPhase
import ru.finney.pet.domain.model.TaskOutcome

// В базе только факты. Баланс, накопления, уровень и эмоция считаются в domain (docs/architecture.md).
// Все дочерние таблицы удаляются вместе с профилем (CASCADE) — сброс профиля одной командой.

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val petName: String,
    val petCharacter: PetCharacter,
    val bodyColor: BodyColor,
    val eyes: EyesVariant,
    val activeGoalId: String?,
    val isDemo: Boolean,
    val createdAt: Long,
    /** Надетый аксессуар. Колонка добавлена в версии 4 базы, см. [FinneyDatabase.MIGRATION_3_4]. */
    val wornItemId: String? = null,
    /** Надетые очки — второй слот. Колонка добавлена в версии 11, см. [FinneyDatabase.MIGRATION_10_11]. */
    val wornEyesId: String? = null,
)

@Entity(
    tableName = "pet_state",
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class PetStateEntity(
    @PrimaryKey val profileId: Long,
    val satiety: Int,
    val hygiene: Int,
    val mood: Int,
    /** Сон. Колонка добавлена в версии 5 базы, см. [FinneyDatabase.MIGRATION_4_5]. */
    @ColumnInfo(defaultValue = FinneyDatabase.ENERGY_ON_MIGRATION)
    val energy: Int,
    /** Когда лёг спать, мс; null — не спит. Тоже с версии 5. */
    val sleepingSince: Long? = null,
    /** Сессия игры с игрушками: сколько настроения уже дала и когда началась. С версии 7, см. [FinneyDatabase.MIGRATION_6_7]. */
    @ColumnInfo(defaultValue = "0")
    val playMood: Int = 0,
    val playSince: Long? = null,
)

@Entity(
    tableName = "periods",
    primaryKeys = ["profileId", "number"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class PeriodEntity(
    val profileId: Long,
    val number: Int,
    val stage: Int,
    val phase: PeriodPhase,
    // план — заполнен после подтверждения
    val budget: Int?,
    val plannedNeeds: Int?,
    val plannedWants: Int?,
    val plannedSavings: Int?,
    // итоги — заполнены после закрытия
    val factNeeds: Int?,
    val factWants: Int?,
    val factSavings: Int?,
    val unplannedIncome: Int?,
    val needsCovered: Boolean?,
    val planMatched: Boolean?,
    val savingsAdded: Boolean?,
    val successfulTasks: Int?,
    val pointsEarned: Int?,
    /** Игра уровня и пройдена ли она. Колонки с версии 6, см. [FinneyDatabase.MIGRATION_5_6]. */
    val levelTaskId: String? = null,
    val gamePassed: Boolean? = null,
    /** Пройден ли уровень, как решено при закрытии. С версии 8, см. [FinneyDatabase.MIGRATION_7_8]. */
    val passed: Boolean? = null,
)

@Entity(
    tableName = "ledger",
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId")],
)
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val periodNumber: Int,
    val type: EntryType,
    val balanceDelta: Int,
    val savingsDelta: Int,
    val category: Category?,
    val itemId: String?,
    val goalId: String?,
    val taskId: String?,
    val unplanned: Boolean,
    val createdAt: Long,
)

@Entity(
    tableName = "task_attempts",
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId")],
)
data class TaskAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val periodNumber: Int,
    val taskId: String,
    val outcome: TaskOutcome,
    val reward: Int,
    val createdAt: Long,
    val seed: Long? = null,
    val bonus: Boolean = false,
)

/** Расходник взят с полки. С версии 12, см. [FinneyDatabase.MIGRATION_11_12]. */
@Entity(
    tableName = "item_uses",
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId")],
)
data class ItemUseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val periodNumber: Int,
    val itemId: String,
    val createdAt: Long,
)

/** Профиль со всем состоянием одним запросом. Порядок в списках Room не гарантирует — сортирует маппер. */
data class ProfileWithGame(
    @Embedded val profile: ProfileEntity,
    @Relation(parentColumn = "id", entityColumn = "profileId") val pet: PetStateEntity?,
    @Relation(parentColumn = "id", entityColumn = "profileId") val periods: List<PeriodEntity>,
    @Relation(parentColumn = "id", entityColumn = "profileId") val ledger: List<LedgerEntryEntity>,
    @Relation(parentColumn = "id", entityColumn = "profileId") val attempts: List<TaskAttemptEntity>,
    @Relation(parentColumn = "id", entityColumn = "profileId") val uses: List<ItemUseEntity> = emptyList(),
)
