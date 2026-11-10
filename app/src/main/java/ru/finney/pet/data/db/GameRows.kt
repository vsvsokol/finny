package ru.finney.pet.data.db

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
