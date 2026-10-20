package ru.finney.pet.ui.period

import ru.finney.pet.ui.goals.forPet
import ru.finney.pet.domain.model.PetCharacter
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
import ru.finney.pet.domain.game.Session
import ru.finney.pet.domain.model.PeriodFacts
import ru.finney.pet.domain.model.Plan
import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.domain.model.ItemArt
import ru.finney.pet.domain.model.SavedGame
import ru.finney.pet.domain.progress.Progression
import ru.finney.pet.ui.goals.rewardArt
import ru.finney.pet.ui.progress.unlockLines
import ru.finney.pet.ui.room.itemArt
import ru.finney.pet.ui.tasks.games.taskIcon

/** Где новое живёт на главном: туда и смотреть. */
enum class NewPlace(val where: String) { SHOP("в магазине"), GAMES("в играх"), GOALS("в копилке") }

/** Открытое на новом уровне: [art] — рисунок вещи или награды, [icon] — картинка игры. */
data class NewThing(val label: String, val place: NewPlace, val art: Int? = null, val icon: ItemArt? = null)

sealed interface PeriodResultUiState {
    data object Loading : PeriodResultUiState

    /**
     * Периода с таким номером нет или он ещё не закрыт. Бывает после возврата из бэкапа:
     * маршрут в стеке остался, а состояние приехало другое.
     */
    data object Unavailable : PeriodResultUiState

    /**
     * Итоги уровня — закрытого игрового периода: что планировали, что вышло
     * и какие условия уровня выполнены (ТЗ п. 2.5.9).
     */
    data class Ready(
        val periodNumber: Int,
        /** Уровень, который играли в этом периоде. */
        val playedLevel: Int,
        val plan: Plan,
        val facts: PeriodFacts,
        /** Три условия уровня — каждое отдельной строкой, а не одним итогом. */
        val needsCovered: Boolean,
        val planMatched: Boolean,
        val savingsAdded: Boolean,
        /** Сколько условий нужно для прохождения. */
        val toPass: Int,
        /** Уровень пройден: следующий — на один выше. */
        val passed: Boolean,
        /** Уровень сейчас, после этого периода. */
        val level: Int,
        /** Уровень вырос именно за этот период; на последнем уровне расти некуда. */
        val leveledUp: Boolean,
        val balance: Int,
        /** Название игры уровня; null — в этом периоде её не было. */
        val levelGame: String? = null,
        /** Картинка игры уровня — та же, что в её сцене. */
        val levelGameIcon: ItemArt? = null,
        /** Игра уровня пройдена. */
        val gamePassed: Boolean = true,
        /** Игра уровня обязательна; в демо-режиме — нет. */
        val gameRequired: Boolean = true,
        /** Сколько пришло на следующий уровень — при любом исходе: ребёнок должен это видеть. */
        val income: Int = 0,
        val canPlanNext: Boolean = false,
        val growth: PeriodGrowth? = null,
        /** Только новые открытия этого перехода, с учётом демо и периода доступности игр. */
        val unlocks: List<String> = emptyList(),
        /** Питомец как на главном — для тактов церемонии. null — профиля нет, рисовать некого. */
        val character: PetCharacter? = null,
        val coloring: String? = null,
        val worn: List<String> = emptyList(),
        /** Стадии роста до и после перехода: облик меняется не на каждой ([ru.finney.pet.ui.pet.lookFor]). */
        val stageBefore: Int = 1,
        val stageAfter: Int = 1,
        /** Звание новой стадии: «Планировщик», «Копилкин». */
        val stageTitle: String? = null,
        /** Открытое этим переходом — картинкой и местом, где его искать. */
        val newThings: List<NewThing> = emptyList(),
    ) : PeriodResultUiState {
        /** Перерасход по нужному и желаемому сверх плана; 0, если уложились. */
        val overspend: Int
            get() = (facts.needs - plan.needs).coerceAtLeast(0) + (facts.wants - plan.wants).coerceAtLeast(0)
    }
}

/**
 * Экран открывается по `PeriodResultRoute(periodNumber)` сразу после закрытия периода,
 * поэтому номер приходит маршрутом, а не берётся из текущего состояния: пока ребёнок
 * читает итоги, текущим уже стал следующий период.
 */
class PeriodResultViewModel(
    private val periodNumber: Int,
    session: Session,
    private val game: Game,
    private val content: GameContent,
) : ViewModel() {

    val uiState: StateFlow<PeriodResultUiState> = session.activeGame
        .filterNotNull()
        .map(::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodResultUiState.Loading)

    private fun toUiState(saved: SavedGame): PeriodResultUiState {
        val state = saved.state
        val period = state.periods.firstOrNull { it.number == periodNumber }
        val plan = period?.plan
        val result = period?.result
        if (plan == null || result == null) return PeriodResultUiState.Unavailable

        val before = game.levelAfter(state, period.number - 1)
        val after = game.levelAfter(state, period.number)
        val levelTask = period.levelTaskId?.let { content.task(it) }
        val character = saved.profile.appearance.character
        val stageBefore = Progression.stage(before, content.economy)
        val stageAfter = Progression.stage(after, content.economy)
        val growth = periodGrowth(content, before, after, period.number, state.isDemo,
            isLatest = state.currentPeriod.number == period.number + 1)
        return PeriodResultUiState.Ready(
            periodNumber = period.number,
            playedLevel = before,
            plan = plan,
            facts = result.facts,
            needsCovered = result.needsCovered,
            planMatched = result.planMatched,
            savingsAdded = result.savingsAdded,
            toPass = game.levelCheck(state).toPass,
            passed = game.isPassed(period) == true,
            level = after,
            leveledUp = after > before,
            growth = growth?.let { it.copy(changes = it.changes - it.games.toSet()) },
            unlocks = if (growth == null) emptyList() else
                (growth.games + unlockedNow(before, after, state.isDemo, saved.profile.appearance.character)).distinct(),
            balance = state.balance,
            levelGame = levelTask?.title,
            levelGameIcon = levelTask?.let(::taskIcon),
            gamePassed = result.gamePassed,
            gameRequired = game.levelCheck(state).gameRequired,
            income = game.levelIncome(state, period.number + 1),
            canPlanNext = state.currentPeriod.number == period.number + 1 &&
                state.currentPeriod.phase == ru.finney.pet.domain.model.PeriodPhase.PLANNING,
            character = character,
            coloring = game.lookFor(state, character),
            worn = state.worn,
            stageBefore = stageBefore,
            stageAfter = stageAfter,
            stageTitle = content.economy.stageTitles.getOrNull(stageAfter - 1),
            newThings = if (growth == null) emptyList() else newThings(growth, before, after, state.isDemo, character),
        )
    }

    /**
     * Новое картинкой и местом: игры — с учётом периода доступности, как в [periodGrowth],
     * вещи и цели — из [Game.unlocksBetween]. В демо вещи и цели открыты с начала. Звание
     * сюда не входит: у него свой такт.
     */
    private fun newThings(growth: PeriodGrowth, before: Int, after: Int, isDemo: Boolean, character: PetCharacter): List<NewThing> {
        val games = growth.newGames.map { NewThing(if (it.harder) "«${it.task.title}» сложнее" else "«${it.task.title}»", NewPlace.GAMES, icon = taskIcon(it.task)) }
        if (isDemo) return games
        val unlocks = game.unlocksBetween(before, after).map { it.forPet(character) }
        val items = unlocks.flatMap { it.items }.map { NewThing(it.label, NewPlace.SHOP, art = itemArt(it.id)) }
        val goals = unlocks.flatMap { it.goals }.map { NewThing(it.label, NewPlace.GOALS, art = it.rewardArt) }
        return items + games + goals
    }

    /**
     * Вещи, цели и звание, открытые этим переходом. Игры уже посчитал [periodGrowth] — с учётом
     * периода, которого [Game.unlocksBetween] не знает. В демо вещи и цели открыты с начала.
     */
    private fun unlockedNow(before: Int, after: Int, isDemo: Boolean, character: PetCharacter): List<String> =
        game.unlocksBetween(before, after).flatMap { unlocks ->
            val shown = unlocks.forPet(character).copy(games = emptyList(), harderGames = emptyList())
            unlockLines(if (isDemo) shown.copy(items = emptyList(), goals = emptyList()) else shown, future = false, isDemo = isDemo)
                .map { "$it." }
        }

    companion object {
        fun factory(periodNumber: Int) = viewModelFactory {
            initializer { appContainer().let { PeriodResultViewModel(periodNumber, it.session, it.game, it.content) } }
        }
    }
}
