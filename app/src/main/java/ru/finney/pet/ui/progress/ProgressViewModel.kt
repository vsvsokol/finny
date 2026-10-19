package ru.finney.pet.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.finney.pet.appContainer
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.GoalProgress
import ru.finney.pet.domain.game.Session
import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.EntryType
import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.domain.model.ItemArt
import ru.finney.pet.domain.model.LedgerEntry
import ru.finney.pet.domain.model.SavedGame
import ru.finney.pet.domain.model.TaskOutcome
import ru.finney.pet.domain.model.TaskTheme
import ru.finney.pet.ui.isReserveMode
import ru.finney.pet.ui.savingsBalance
import ru.finney.pet.ui.tasks.games.taskIcon

/**
 * Строка истории: откуда деньги или куда ушли — словами, и сколько.
 * [type] — вид операции для значка строки; [game] — картинка мини-игры у награды,
 * [itemId] — купленный предмет: у строки видно, про что она, а не только слова.
 */
data class HistoryRow(
    val label: String,
    val balanceDelta: Int,
    val savingsDelta: Int,
    val type: EntryType = EntryType.INCOME,
    val game: ItemArt? = null,
    val itemId: String? = null,
)

/** Операции одного игрового периода; ребёнку он подписан уровнем, который тогда играли. */
data class HistoryPeriod(val number: Int, val level: Int, val rows: List<HistoryRow>)

/** Задание, которое пробовали: лучший исход и сколько было попыток. [icon] — картинка игры, как в её сцене. */
data class TaskRow(val title: String, val theme: String, val passed: Boolean, val attempts: Int, val icon: ItemArt? = null)

sealed interface ProgressUiState {
    data object Loading : ProgressUiState

    data class Ready(
        val petName: String,
        val level: Int,
        /** Сколько всего уровней — для «3 из 6» и полосы рядом. */
        val maxLevel: Int = 6,
        val stage: Int,
        val goal: GoalProgress?,
        val goalsCompleted: Int,
        /** Последний закрытый период — для кнопки итогов. null — закрытых ещё нет. */
        val lastClosedPeriod: Int?,
        /** Уровень, который играли в [lastClosedPeriod], — подпись кнопки итогов. */
        val lastClosedLevel: Int?,
        val tasks: List<TaskRow>,
        /** Новые периоды сверху. */
        val history: List<HistoryPeriod>,
        val reserveMode: Boolean = false,
        val reserveSaved: Int = 0,
        val nextProgress: NextProgress? = null,
    ) : ProgressUiState
}

/**
 * История и учебный прогресс — ТЗ п. 2.5.4 и 2.5.11: у каждого начисления и траты
 * видны источник и сумма, рядом пройденные задания, цель и итоги последнего периода.
 */
class ProgressViewModel(
    session: Session,
    private val game: Game,
    private val content: GameContent,
) : ViewModel() {

    val uiState: StateFlow<ProgressUiState> = session.activeGame
        .filterNotNull()
        .map(::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState.Loading)

    private fun toUiState(saved: SavedGame): ProgressUiState.Ready {
        val state = saved.state
        return ProgressUiState.Ready(
            petName = saved.profile.petName,
            level = game.level(state),
            maxLevel = content.economy.maxLevel,
            stage = game.stage(state),
            nextProgress = nextProgress(content, game.level(state), state.currentPeriod.number, state.isDemo),
            goal = game.goalProgress(state),
            reserveMode = game.isReserveMode(state),
            reserveSaved = if (game.isReserveMode(state)) game.savingsBalance(state) else 0,
            goalsCompleted = state.ledger.count { it.type == EntryType.GOAL_COMPLETE },
            lastClosedPeriod = state.periods.lastOrNull { it.result != null }?.number,
            lastClosedLevel = state.periods.lastOrNull { it.result != null }?.let { game.levelAfter(state, it.number - 1) },
            tasks = state.attempts
                .groupBy { it.taskId }
                .mapNotNull { (taskId, attempts) ->
                    val task = content.task(taskId) ?: return@mapNotNull null
                    TaskRow(
                        title = task.title,
                        theme = task.theme.label(),
                        passed = attempts.any { it.outcome == TaskOutcome.SUCCESS },
                        attempts = attempts.size,
                        icon = taskIcon(task),
                    )
                },
            history = state.ledger
                .groupBy { it.periodNumber }
                .toSortedMap(compareByDescending { it })
                .map { (number, entries) ->
                    HistoryPeriod(
                        number = number,
                        level = game.levelAfter(state, number - 1),
                        // Лента в порядке записи; новые сверху. По времени не сортируем:
                        // у операций одной команды оно совпадает.
                        rows = entries.asReversed().map {
                            HistoryRow(
                                label = content.entryLabel(it),
                                balanceDelta = it.balanceDelta,
                                savingsDelta = it.savingsDelta,
                                type = it.type,
                                game = it.taskId?.let(content::task)?.let(::taskIcon),
                                itemId = it.itemId,
                            )
                        },
                    )
                },
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { appContainer().let { ProgressViewModel(it.session, it.game, it.content) } }
        }
    }
}

/** Подпись операции для ребёнка: источник денег или на что они ушли (ТЗ п. 2.5.4). */
fun GameContent.entryLabel(entry: LedgerEntry): String = when (entry.type) {
    EntryType.INCOME -> "Деньги на уровень"
    EntryType.TASK_REWARD -> "Награда за мини-игру" +
        (entry.taskId?.let(::task)?.let { " «${it.title}»" } ?: "")
    EntryType.PARENT_BONUS -> "Бонус от взрослого"
    EntryType.PURCHASE -> {
        val item = entry.itemId?.let(::item)
        val kind = when (item?.category) {
            Category.NEEDS -> "нужное"
            Category.WANTS -> "хочется"
            null -> null
        }
        "Покупка: ${item?.label ?: "предмет"}" + (kind?.let { " ($it)" } ?: "")
    }
    EntryType.SAVINGS_DEPOSIT -> if (entry.goalId == null) "В запас" else "В копилку" +
        (entry.goalId.let(::goal)?.let { " на «${it.label}»" } ?: "")
    EntryType.SAVINGS_WITHDRAW -> if (entry.goalId == null) "Из запаса" else "Из копилки" +
        (entry.goalId.let(::goal)?.let { " с «${it.label}»" } ?: "")
    EntryType.GOAL_COMPLETE -> "Цель достигнута" + (entry.goalId?.let(::goal)?.let { ": ${it.label}" } ?: "")
}

fun TaskTheme.label(): String = when (this) {
    TaskTheme.PLANNING -> "планирование бюджета"
    TaskTheme.SAVINGS -> "сбережения"
    TaskTheme.SHOPPING -> "покупки и платежи"
}
