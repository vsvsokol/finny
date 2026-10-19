package ru.finney.pet.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.finney.pet.appContainer
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.GameResult
import ru.finney.pet.domain.game.Session
import ru.finney.pet.domain.model.EntryType
import ru.finney.pet.domain.model.GameState
import ru.finney.pet.domain.model.LedgerEntry
import ru.finney.pet.domain.model.PeriodPhase
import ru.finney.pet.domain.model.PetStats
import ru.finney.pet.domain.pet.PetRules

data class DebugUiState(
    val stats: PetStats,
    val balance: Int,
    val periodNumber: Int,
    val phase: PeriodPhase,
    /** Питомец спит — можно досыпать сразу, не ждать час. */
    val sleeping: Boolean = false,
)

enum class DebugStat(val label: String) {
    SATIETY("Сытость"),
    HYGIENE("Чистота"),
    MOOD("Настроение"),
    ENERGY("Сон"),
}

sealed interface DebugEvent {
    /** Данных больше нет — приложение запускается заново, с первого запуска. */
    data object Wiped : DebugEvent

    /** Команда игры не прошла: показать причину, чтобы не гадать. */
    data class Rejected(val reason: String) : DebugEvent
}

/**
 * Отладочные рычаги: деньги, шкалы, период, стирание данных. Только для
 * отладочной сборки — вход на главном открыт, лишь когда приложение debuggable.
 *
 * Деньги и шкалы пишутся в сохранение напрямую, мимо правил `Game`: в домене
 * нет команды «положи 100 монет» или «поставь сытость 10», и заводить её ради
 * проверки — правка в чужой зоне. Деньги при этом остаются суммой операций:
 * добавляется запись INCOME, баланс не хранится отдельно. Шкалы зажаты в те же
 * 0..100, что и в [PetRules].
 *
 * План и закрытие периода, наоборот, идут через настоящие команды — снижение
 * шкал за период должно быть ровно таким, как в игре.
 */
class DebugViewModel(private val session: Session) : ViewModel() {

    val uiState: StateFlow<DebugUiState?> = session.activeGame
        .filterNotNull()
        .map { saved ->
            val state = saved.state
            DebugUiState(state.pet, state.balance, state.currentPeriod.number, state.currentPeriod.phase, state.sleepingSince != null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = Channel<DebugEvent>(Channel.BUFFERED)
    val events: Flow<DebugEvent> = _events.receiveAsFlow()

    fun addMoney(amount: Int) = patch { state ->
        // Уйти в минус нельзя: списывается не больше, чем есть.
        val delta = maxOf(amount, -state.balance)
        if (delta == 0) return@patch state
        val entry = LedgerEntry(
            periodNumber = state.currentPeriod.number,
            type = EntryType.INCOME,
            balanceDelta = delta,
            // Как у дохода после плана в Game: иначе «план выполнен» считался бы
            // по деньгам, которых в плане не было.
            unplanned = state.currentPeriod.phase == PeriodPhase.ACTIVE,
            createdAt = System.currentTimeMillis(),
        )
        state.copy(ledger = state.ledger + entry)
    }

    fun setStat(stat: DebugStat, value: Int) = patch { state ->
        val v = value.coerceIn(PetRules.STAT_MIN, PetRules.STAT_MAX)
        state.copy(
            pet = when (stat) {
                DebugStat.SATIETY -> state.pet.copy(satiety = v)
                DebugStat.HYGIENE -> state.pet.copy(hygiene = v)
                DebugStat.MOOD -> state.pet.copy(mood = v)
                DebugStat.ENERGY -> state.pet.copy(energy = v)
            },
        )
    }

    /**
     * Досыпать сразу: питомец будто лёг давным-давно. Хранилище разбудит его
     * перед следующей командой, главный экран — сразу, со «Выспался!».
     */
    fun finishSleep() = patch { state -> if (state.sleepingSince == null) state else state.copy(sleepingSince = 0L) }

    /**
     * Всё с полочки ванны кончилось — проверить «Купить» на плитках и магазин ванны,
     * не намывая питомца по десять раз. Запас — покупки минус мытьё, поэтому дописываются
     * записи мытья, как будто всё истратили; деньги не трогаются.
     */
    fun emptyBath() = command { state ->
        val now = System.currentTimeMillis()
        val used = (soaps + bathExtras).flatMap { item ->
            List(stock(state, item.id)) {
                LedgerEntry(state.currentPeriod.number, EntryType.WASH, balanceDelta = 0, itemId = item.id, createdAt = now)
            }
        }
        GameResult.Ok(state.copy(ledger = state.ledger + used))
    }

    /** Пустой план — чтобы сразу можно было покупать, не заходя в экран плана. */
    fun skipPlan() = command { confirmPlan(it, needs = 0, wants = 0, savings = 0) }

    fun closePeriod() = command { closePeriod(it) }

    fun wipeAll() {
        viewModelScope.launch {
            session.deleteAll()
            _events.send(DebugEvent.Wiped)
        }
    }

    private fun patch(change: (GameState) -> GameState) = command { GameResult.Ok(change(it)) }

    private fun command(run: Game.(GameState) -> GameResult) {
        viewModelScope.launch {
            val result = session.execute(run)
            if (result is GameResult.Rejected) _events.send(DebugEvent.Rejected(result.reason.toString()))
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { DebugViewModel(appContainer().session) }
        }
    }
}
