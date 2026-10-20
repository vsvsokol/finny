package ru.finney.pet.ui.budget

import ru.finney.pet.ui.goals.fitsPet
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.finney.pet.appContainer
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.GameResult
import ru.finney.pet.domain.game.PlanReport
import ru.finney.pet.domain.game.Rejection
import ru.finney.pet.domain.game.Session
import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.domain.model.Goal
import ru.finney.pet.domain.model.ItemKind
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.model.Plan
import ru.finney.pet.domain.model.SavedGame
import ru.finney.pet.ui.isReserveMode
import ru.finney.pet.ui.room.itemArt

sealed interface BudgetUiState {
    data object Loading : BudgetUiState

    /** План не подтверждён: ребёнок распределяет бюджет, суммы можно менять сколько угодно. */
    data class Planning(
        val periodNumber: Int,
        /** Уровень, который начинается с этого плана. */
        val level: Int,
        /** Всё, что есть на балансе сейчас: доход периода, остаток прошлых, награды до плана. */
        val budget: Int,
        val needs: Int,
        val wants: Int,
        val savings: Int,
        /** Подсказка: сколько минимум заложить на нужное. */
        val needsHint: Int?,
        /** null — цель не выбрана, откладывать пока некуда. */
        val goalLabel: String?,
        /** Цели на выбор прямо в плане, если своей ещё нет: без цели копилку не заполнить. */
        val goals: List<Goal> = emptyList(),
        /** Сколько направлений должно быть заполнено; из economy.json. */
        val directions: Int = 3,
        val rejection: Rejection?,
        val isSaving: Boolean,
        /**
         * Что покупают на каждую часть — id товаров магазина, у которых есть рисунок.
         * Плейтест: «нужное» и «желаемое» словами не объяснить, нужно показать еду и мыло.
         */
        val needsItems: List<String> = emptyList(),
        val wantsItems: List<String> = emptyList(),
        /** Вещь цели — рисунок у копилки; null — цели нет или она без рисунка. */
        val goalReward: String? = null,
        /**
         * План прошлого уровня, если он ещё помещается в бюджет, — для «Как в прошлый раз».
         * null — прошлого плана нет, он не помещается или копить пока не на что.
         */
        val lastPlan: Plan? = null,
        val reserveMode: Boolean = false,
        /** Объяснения берутся из того же контента, что и справочник. */
        val needsHelp: String? = null,
        val wantsHelp: String? = null,
        val savingsHelp: String? = null,
        /** Питомец — он сам объясняет части плана, головой с репликой. */
        val character: PetCharacter? = null,
        val coloring: String? = null,
        val worn: List<String> = emptyList(),
    ) : BudgetUiState {
        val planned: Int get() = needs + wants + savings
        val remainder: Int get() = budget - planned
        /** ТЗ п. 2.5.5: сумма разложена минимум по трём направлениям — в каждом хоть что-то. */
        val allDirections: Boolean get() = listOf(needs, wants, savings).count { it > 0 } >= directions
        val canConfirm: Boolean get() = remainder >= 0 && allDirections &&
            (savings == 0 || goalLabel != null) && !isSaving

        /**
         * Чего не хватает, чтобы подтвердить, — пунктами для подписи у погашенной кнопки.
         * Плейтест: серую кнопку жали и не понимали, что не так. Пусто — подтверждать можно.
         */
        val missing: List<String> get() = buildList {
            if (remainder < 0) add("Убавь: разложено больше, чем есть")
            if (needs == 0) add("Положи монетки в «Нужное»")
            if (wants == 0) add("Положи монетки в «Желаемое»")
            if (goalLabel == null) add("Выбери цель для копилки") else if (savings == 0) add("Положи монетки в копилку")
        }

        /** Кнопку «Как в прошлый раз» прячем, когда черновик уже такой же. */
        val canRepeatLast: Boolean
            get() = lastPlan != null && (lastPlan.needs != needs || lastPlan.wants != wants || lastPlan.savings != savings)
    }

    /** План подтверждён: план против факта до закрытия периода. */
    data class Active(
        val periodNumber: Int,
        val level: Int,
        val report: PlanReport,
        val balance: Int,
        /** Питомец игрока — он сам и говорит вывод «План и факт», в своей шляпе. */
        val character: PetCharacter = PetCharacter.PUSHISTIK,
        val worn: List<String> = emptyList(),
        val incomeToRecover: Int = report.overspend,
    ) : BudgetUiState {
        /**
         * Одна фраза-итог вместо «10 из 10» по строкам (плейтест: «что я должен понять
         * из того, что копилка 10/10?»). Засчитан ли план — решает ядро ([PlanReport.onTrack]);
         * здесь только выбирается, какими словами сказать почему.
         */
        val verdict: PlanVerdict get() = when {
            report.onTrack -> PlanVerdict.OnTrack
            incomeToRecover > 0 -> PlanVerdict.Overspent(incomeToRecover)
            report.facts.savings < report.plan.savings -> PlanVerdict.SavingsShort(report.plan.savings - report.facts.savings)
            else -> PlanVerdict.OffTrack
        }
    }
}

/** Итог «План и факт» одной фразой. */
sealed interface PlanVerdict {
    data object OnTrack : PlanVerdict
    /** Потрачено сверх плана — перерасход по нужному и желаемому от ядра. */
    data class Overspent(val amount: Int) : PlanVerdict
    /** В копилку отложено меньше задуманного. */
    data class SavingsShort(val amount: Int) : PlanVerdict
    data object OffTrack : PlanVerdict
}

class BudgetViewModel(
    private val session: Session,
    private val game: Game,
    private val content: GameContent,
) : ViewModel() {

    private data class Draft(
        val needs: Int = 0,
        val wants: Int = 0,
        val savings: Int = 0,
        val rejection: Rejection? = null,
        val isSaving: Boolean = false,
    )

    private val draft = MutableStateFlow(Draft())

    val uiState: StateFlow<BudgetUiState> = combine(session.activeGame.filterNotNull(), draft, ::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState.Loading)

    fun setNeeds(amount: Int) = draft.update { it.copy(needs = amount.coerceAtLeast(0), rejection = null) }

    fun setWants(amount: Int) = draft.update { it.copy(wants = amount.coerceAtLeast(0), rejection = null) }

    fun setSavings(amount: Int) = draft.update { it.copy(savings = amount.coerceAtLeast(0), rejection = null) }

    /** «Как в прошлый раз»: черновик становится планом прошлого уровня, дальше его можно менять. */
    fun repeatLastPlan() {
        val last = (uiState.value as? BudgetUiState.Planning)?.lastPlan ?: return
        draft.update { it.copy(needs = last.needs, wants = last.wants, savings = last.savings, rejection = null) }
    }

    fun selectGoal(goalId: String) {
        viewModelScope.launch {
            val result = session.execute { selectGoal(it, goalId) }
            if (result is GameResult.Rejected) draft.update { it.copy(rejection = result.reason) }
        }
    }

    /** После успеха экран сам переключится на [BudgetUiState.Active]. */
    fun confirm() {
        val current = draft.value
        if (current.isSaving) return
        draft.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = session.execute { confirmPlan(it, current.needs, current.wants, current.savings) }
            draft.update {
                when (result) {
                    // Черновик больше не нужен; следующий период начнётся с нулей.
                    is GameResult.Ok -> Draft()
                    is GameResult.Rejected -> it.copy(rejection = result.reason, isSaving = false)
                }
            }
        }
    }

    private fun toUiState(saved: SavedGame, draft: Draft): BudgetUiState {
        val state = saved.state
        val report = game.planReport(state)
        if (report != null) {
            return BudgetUiState.Active(
                periodNumber = state.currentPeriod.number,
                level = game.level(state),
                report = report,
                balance = state.balance,
                character = saved.profile.appearance.character,
                worn = state.worn,
                incomeToRecover = (report.overspend - report.facts.unplannedIncome - content.economy.planTolerance)
                    .coerceAtLeast(0),
            )
        }
        val goal = game.goalProgress(state)?.goal
        val reserveMode = game.isReserveMode(state)
        // Прошлый план берётся как есть из истории уровней; предлагаем его, только если он
        // ещё помещается в бюджет и есть цель — иначе половину плана всё равно пришлось бы менять.
        val lastPlan = state.periods.dropLast(1).lastOrNull()?.plan
            ?.takeIf { it.planned <= state.balance && (goal != null || reserveMode) }
        return BudgetUiState.Planning(
            periodNumber = state.currentPeriod.number,
            level = game.level(state),
            budget = state.balance,
            needs = draft.needs,
            wants = draft.wants,
            savings = draft.savings,
            needsHint = game.needsHint(state),
            goalLabel = if (reserveMode) "Запас" else goal?.label,
            goals = if (state.activeGoalId == null) {
                content.goals.filter {
                    it.fitsPet(saved.profile.appearance.character) && !state.isGoalCompleted(it.id) && game.isUnlocked(state, it)
                }
            } else {
                emptyList()
            },
            directions = content.economy.planDirections,
            rejection = draft.rejection,
            isSaving = draft.isSaving,
            needsItems = pictured(Category.NEEDS),
            wantsItems = pictured(Category.WANTS),
            goalReward = goal?.reward,
            lastPlan = lastPlan,
            reserveMode = reserveMode,
            needsHelp = helpText("needs"),
            wantsHelp = helpText("wants"),
            savingsHelp = helpText("savings"),
            character = saved.profile.appearance.character,
            coloring = game.lookFor(state, saved.profile.appearance.character),
            worn = state.worn,
        )
    }

    private fun helpText(id: String): String? =
        content.glossary.firstOrNull { it.id == id }?.text?.takeIf { it.isNotBlank() }

    /**
     * Товары части, у которых есть рисунок, — первые [ItemsShown] по порядку магазина.
     * Шляпы не берём: это вещи целей, а желаемое показываем тем, что радует сразу.
     */
    private fun pictured(category: Category): List<String> =
        content.shop.filter { it.category == category && it.kind != ItemKind.ACCESSORY && itemArt(it.id) != null }
            .map { it.id }
            .take(ItemsShown)

    companion object {
        private const val ItemsShown = 3

        val Factory = viewModelFactory {
            initializer { appContainer().let { BudgetViewModel(it.session, it.game, it.content) } }
        }
    }
}
