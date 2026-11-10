@file:OptIn(ExperimentalSerializationApi::class)
@file:Suppress("EXTERNAL_SERIALIZER_USELESS")

package ru.finney.pet.web

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.json.Json
import ru.finney.pet.data.db.GameRows
import ru.finney.pet.data.db.ItemUseEntity
import ru.finney.pet.data.db.LedgerEntryEntity
import ru.finney.pet.data.db.PeriodEntity
import ru.finney.pet.data.db.PetStateEntity
import ru.finney.pet.data.db.ProfileEntity
import ru.finney.pet.data.db.ProfileWithGame
import ru.finney.pet.data.db.TaskAttemptEntity
import ru.finney.pet.data.db.toDomain
import ru.finney.pet.data.db.toEntity
import ru.finney.pet.data.db.toRows
import ru.finney.pet.domain.game.GameStorage
import ru.finney.pet.domain.model.GameState
import ru.finney.pet.domain.model.PetAppearance
import ru.finney.pet.domain.model.Profile
import ru.finney.pet.domain.model.SavedGame

// Сериализаторы для строк Room из data/db/Entities.kt: сами классы общие с Android
// и аннотаций kotlinx.serialization не несут, поэтому описаны здесь, снаружи.
@Serializer(forClass = ProfileEntity::class) internal object ProfileEntitySerializer
@Serializer(forClass = PetStateEntity::class) internal object PetStateEntitySerializer
@Serializer(forClass = PeriodEntity::class) internal object PeriodEntitySerializer
@Serializer(forClass = LedgerEntryEntity::class) internal object LedgerEntryEntitySerializer
@Serializer(forClass = TaskAttemptEntity::class) internal object TaskAttemptEntitySerializer
@Serializer(forClass = ItemUseEntity::class) internal object ItemUseEntitySerializer

/** Сохранение одного профиля — те же таблицы, что в базе Android, только одним JSON. */
@Serializable
internal data class StoredProfile(
    @Serializable(ProfileEntitySerializer::class) val profile: ProfileEntity,
    @Serializable(PetStateEntitySerializer::class) val pet: PetStateEntity? = null,
    val periods: List<@Serializable(PeriodEntitySerializer::class) PeriodEntity> = emptyList(),
    val ledger: List<@Serializable(LedgerEntryEntitySerializer::class) LedgerEntryEntity> = emptyList(),
    val attempts: List<@Serializable(TaskAttemptEntitySerializer::class) TaskAttemptEntity> = emptyList(),
    val uses: List<@Serializable(ItemUseEntitySerializer::class) ItemUseEntity> = emptyList(),
) {
    fun toRelation() = ProfileWithGame(profile, pet, periods, ledger, attempts, uses)
}

@Serializable
internal data class StoredGames(
    /** Версия формата. Меняется вместе с Entities.kt — как версия базы Room. */
    val version: Int = FORMAT_VERSION,
    val nextId: Long = 1,
    val profiles: List<StoredProfile> = emptyList(),
) {
    companion object {
        const val FORMAT_VERSION = 15
    }
}

/**
 * [GameStorage] веб-версии: состояние в памяти, копия — в localStorage после каждой записи.
 * Правила те же, что у Room (`GameDao.writeGame`): операции, попытки и расход с полки
 * только дописываются, периоды и шкалы перезаписываются.
 */
internal interface GamePersistence {
    fun read(): StoredGames
    fun write(games: StoredGames, replace: Set<Long>)
}

internal class GuestPersistence : GamePersistence {
    override fun read(): StoredGames = BrowserStorage.get("games")?.let {
        cloudJson.decodeFromString<StoredGames>(it)
    } ?: StoredGames()
    override fun write(games: StoredGames, replace: Set<Long>) {
        BrowserStorage.requireSet("games", cloudJson.encodeToString(games))
    }
}

class WebGameStorage internal constructor(private val persistence: GamePersistence = GuestPersistence()) : GameStorage {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val state = MutableStateFlow(read())

    private fun read(): StoredGames = persistence.read()

    internal fun refresh(games: StoredGames) { state.value = games }

    private fun write(replace: Set<Long> = emptySet(), update: (StoredGames) -> StoredGames) {
        val next = update(state.value)
        persistence.write(next, replace)
        state.value = next
    }

    private fun StoredGames.edit(profileId: Long, change: (StoredProfile) -> StoredProfile) =
        copy(profiles = profiles.map { if (it.profile.id == profileId) change(it) else it })

    /** Всё сохранение как текст — кнопка «Экспорт» отладки и вложение к баг-репорту. */
    fun exportJson(): String = json.encodeToString(StoredGames.serializer(), state.value)

    /** Подменить сохранение присланным из баг-репорта. */
    fun importJson(text: String) {
        val games = json.decodeFromString(StoredGames.serializer(), text)
        write(games.profiles.map { it.profile.id }.toSet()) { games }
    }

    override fun observeProfiles(): Flow<List<Profile>> = state
        .map { games -> games.profiles.map { it.profile }.sortedWith(compareBy({ it.createdAt }, { it.id })).map { it.toDomain() } }
        .distinctUntilChanged()

    override fun observeGame(profileId: Long): Flow<SavedGame?> = state
        .map { games -> games.profiles.firstOrNull { it.profile.id == profileId } }
        .distinctUntilChanged()
        .map { it?.toRelation()?.toDomain() }

    override suspend fun load(profileId: Long): SavedGame? =
        state.value.profiles.firstOrNull { it.profile.id == profileId }?.toRelation()?.toDomain()

    override suspend fun create(profile: Profile, state: GameState): Long {
        var id = 0L
        write { games ->
            id = games.nextId
            val entity = profile.toEntity().copy(id = id)
            val created = games.copy(nextId = id + 1, profiles = games.profiles + StoredProfile(entity))
            created.edit(id) { it.applyRows(state.toRows(id)) }
        }
        return id
    }

    override suspend fun save(profileId: Long, state: GameState) = write { games ->
        games.edit(profileId) { it.applyRows(state.toRows(profileId)) }
    }

    override suspend fun replace(profileId: Long, state: GameState) = write(setOf(profileId)) { games ->
        games.edit(profileId) {
            it.copy(periods = emptyList(), ledger = emptyList(), attempts = emptyList(), uses = emptyList())
                .applyRows(state.toRows(profileId))
        }
    }

    override suspend fun updateProfile(profileId: Long, petName: String, appearance: PetAppearance) = write { games ->
        games.edit(profileId) {
            it.copy(
                profile = it.profile.copy(
                    petName = petName,
                    petCharacter = appearance.character,
                    bodyColor = appearance.bodyColor,
                    eyes = appearance.eyes,
                ),
            )
        }
    }

    override suspend fun setDemo(profileId: Long, isDemo: Boolean) = write { games ->
        games.edit(profileId) { it.copy(profile = it.profile.copy(isDemo = isDemo)) }
    }

    override suspend fun delete(profileId: Long) = write { games ->
        games.copy(profiles = games.profiles.filterNot { it.profile.id == profileId })
    }

    override suspend fun deleteAll() = write { it.copy(profiles = emptyList()) }
}

/** Как `GameDao.writeGame`: хвост операций дописывается, id выдаются по порядку. */
private fun StoredProfile.applyRows(rows: GameRows): StoredProfile {
    check(ledger.size <= rows.ledger.size) { "Операций в сохранении ${ledger.size}, в состоянии ${rows.ledger.size}" }
    check(attempts.size <= rows.attempts.size) { "Попыток в сохранении ${attempts.size}, в состоянии ${rows.attempts.size}" }
    check(uses.size <= rows.uses.size) { "Расхода с полки в сохранении ${uses.size}, в состоянии ${rows.uses.size}" }
    var nextLedger = (ledger.maxOfOrNull { it.id } ?: 0) + 1
    var nextAttempt = (attempts.maxOfOrNull { it.id } ?: 0) + 1
    var nextUse = (uses.maxOfOrNull { it.id } ?: 0) + 1
    return copy(
        profile = profile.copy(
            activeGoalId = rows.activeGoalId,
            wornItemId = rows.wornItemId,
            wornEyesId = rows.wornEyesId,
            roomWall = rows.roomWall,
            roomWindow = rows.roomWindow,
            roomLamp = rows.roomLamp,
            roomRug = rows.roomRug,
            wornLook = rows.wornLook,
        ),
        pet = rows.pet,
        periods = (periods.associateBy { it.number } + rows.periods.associateBy { it.number }).values.sortedBy { it.number },
        ledger = ledger + rows.ledger.drop(ledger.size).map { it.copy(id = nextLedger++) },
        attempts = attempts + rows.attempts.drop(attempts.size).map { it.copy(id = nextAttempt++) },
        uses = uses + rows.uses.drop(uses.size).map { it.copy(id = nextUse++) },
    )
}
