package ru.finney.pet.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.finney.pet.appContainer
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.GameResult
import ru.finney.pet.domain.game.PurchasePreview
import ru.finney.pet.domain.game.Rejection
import ru.finney.pet.domain.game.Session
import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.domain.model.GameState
import ru.finney.pet.domain.model.Goal
import ru.finney.pet.domain.model.ItemKind
import ru.finney.pet.domain.model.PeriodPhase
import ru.finney.pet.domain.model.SavedGame
import ru.finney.pet.domain.model.ShopItem
import ru.finney.pet.ui.components.ActionFeedback
import ru.finney.pet.ui.components.changesBetween

/**
 * Раздел магазина. Делится по тому, что ребёнок узнаёт в вещи, — еда, мыло, вкусности,
 * игрушки, — а «нужное / хочется» остаётся полосой и значком внутри раздела.
 */
enum class ShopShelf(val title: String) {
    FOOD("Еда"),
    CLEAN("Чистота"),
    TREATS("Радости"),
    TOYS("Игрушки"),
    ;

    fun holds(item: ShopItem): Boolean = when (this) {
        FOOD -> item.category == Category.NEEDS && item.effect.hygiene <= 0 && item.uses == null
        CLEAN -> item.category == Category.NEEDS && (item.effect.hygiene > 0 || item.uses != null)
        TREATS -> item.category == Category.WANTS && item.kind != ItemKind.TOY
        TOYS -> item.kind == ItemKind.TOY
    }
}

/** Раздел целиком: что можно купить сейчас. Закрытое — одним тизером на весь магазин. */
data class ShelfRow(
    val shelf: ShopShelf,
    val previews: List<PurchasePreview>,
)

/**
 * Цель, на которую копят сейчас, — одной плиткой со ссылкой в копилку. Сами награды
 * целей в магазине не продаются и не показываются: на них только копят (20.10, решение команды).
 */
data class SavingTile(val goal: Goal, val saved: Int)

sealed interface ShopUiState {
    data object Loading : ShopUiState

    data class Ready(
        val balance: Int,
        /** Покупки открываются после подтверждения плана периода. */
        val canBuy: Boolean,
        val shelves: List<ShelfRow>,
        val selectedId: String?,
        /** Сколько по плану осталось на нужное и на желаемое. null — плана ещё нет. */
        val needsLeft: Int?,
        val wantsLeft: Int?,
        val rejection: Rejection?,
        val feedback: ActionFeedback?,
        /** Ближайшее закрытое — один тизер, гора замков в начале игры только пугает. */
        val upcoming: ShopItem? = null,
        /** Нужное, которое выйдет за план, и сколько для него взять из радостей (Game.wantsToNeeds). */
        val fromWants: Map<String, Int> = emptyMap(),
        /** null — цель не выбрана. */
        val saving: SavingTile? = null,
    ) : ShopUiState {
        // Прежний срез «нужное / хочется / игрушки» — из тех же разделов, ничего не пересчитывая.
        val needs: List<PurchasePreview> get() = previewsOf(ShopShelf.FOOD, ShopShelf.CLEAN)
        val wants: List<PurchasePreview> get() = previewsOf(ShopShelf.TREATS)
        val toys: List<PurchasePreview> get() = previewsOf(ShopShelf.TOYS)

        private fun previewsOf(vararg kinds: ShopShelf) = shelves.filter { it.shelf in kinds }.flatMap { it.previews }
    }
}

/** Выбор на экране: что отмечено, чем закончилась последняя попытка. */
private data class ShopSelection(
    val selectedId: String? = null,
    val rejection: Rejection? = null,
    val feedback: ActionFeedback? = null,
)

/**
 * Магазин — ТЗ п. 2.5.6 и шаг 7 Приложения А: нужное и желаемое, цена,
 * категория и влияние на питомца до покупки, подтверждение отдельной кнопкой.
 *
 * Купить при нехватке денег можно попробовать: домен откажет, и ребёнок увидит,
 * чего не хватает и что делать. Молча неактивная кнопка этого не объясняет.
 */
class ShopViewModel(
    private val session: Session,
    private val game: Game,
    private val content: GameContent,
) : ViewModel() {

    private val selection = MutableStateFlow(ShopSelection())

    val uiState: StateFlow<ShopUiState> =
        combine(session.activeGame.filterNotNull(), selection, ::toUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShopUiState.Loading)

    fun select(itemId: String) = selection.update { ShopSelection(selectedId = itemId) }

    fun dismissFeedback() = selection.update { it.copy(feedback = null, rejection = null) }

    /** [fromWants] — сначала взять из радостей столько, чтобы покупка не вышла за план. */
    fun buy(itemId: String, fromWants: Boolean = false) {
        viewModelScope.launch {
            val before = session.activeGame.first()?.state ?: return@launch
            when (val result = session.execute { if (fromWants) buyFromWants(it, itemId) else buy(it, itemId) }) {
                is GameResult.Ok -> selection.value = ShopSelection(
                    feedback = feedback(itemId, before, result.state),
                )
                is GameResult.Rejected -> selection.update { it.copy(rejection = result.reason, feedback = null) }
            }
        }
    }

    private fun feedback(itemId: String, before: GameState, after: GameState): ActionFeedback {
        val item = checkNotNull(content.item(itemId))
        val report = game.planReport(after)
        val overPlan = report?.let {
            when (item.category) {
                Category.NEEDS -> it.facts.needs > it.plan.needs
                Category.WANTS -> it.facts.wants > it.plan.wants
            }
        } == true
        val why = when (item.category) {
            Category.NEEDS -> "Это нужное: без него питомцу плохо."
            Category.WANTS -> "Это «хочется»: радует, но можно и без него."
        } + if (overPlan) " Это уже сверх плана." else ""
        val next = when {
            item.kind == ItemKind.ACCESSORY -> "Уже надето! Переодеть — в гардеробе в меню."
            item.kind == ItemKind.TOY -> "Игрушка уже в зале: возьми её пальцем и потряси рядом с питомцем."
            overPlan -> "В следующий раз заложи больше или купи меньше."
            item.category == Category.WANTS -> "Хватит ли на нужное?"
            else -> "Что ещё нужно питомцу?"
        }
        return ActionFeedback(
            title = "Купили: ${item.label}",
            lines = changesBetween(before, after),
            why = why,
            next = next,
            itemId = item.id,
        )
    }

    private fun toUiState(saved: SavedGame, selection: ShopSelection): ShopUiState.Ready {
        val state = saved.state
        val report = game.planReport(state)
        val shelves = ShopShelf.entries.map { shelf ->
            ShelfRow(
                shelf = shelf,
                previews = content.forSale
                    .filter { shelf.holds(it) && it.isOnSale(state) && game.isUnlocked(state, it) }
                    .mapNotNull { game.previewPurchase(state, it.id) },
            )
        }
        return ShopUiState.Ready(
            balance = state.balance,
            canBuy = state.currentPeriod.phase == PeriodPhase.ACTIVE,
            shelves = shelves,
            selectedId = selection.selectedId,
            needsLeft = report?.let { (it.plan.needs - it.facts.needs).coerceAtLeast(0) },
            wantsLeft = report?.let { (it.plan.wants - it.facts.wants).coerceAtLeast(0) },
            fromWants = content.forSale.mapNotNull { item -> game.wantsToNeeds(state, item.id)?.let { item.id to it } }.toMap(),
            rejection = selection.rejection,
            feedback = selection.feedback,
            upcoming = content.forSale.filter { it.isOnSale(state) && !game.isUnlocked(state, it) }
                .minByOrNull { it.unlockLevel },
            saving = state.activeGoalId?.let(content::goal)?.let { SavingTile(it, state.goalSaved(it.id)) },
        )
    }

    /** Аксессуар и игрушка покупаются один раз: купленные из магазина уходят. */
    private fun ShopItem.isOnSale(state: GameState): Boolean =
        kind == ItemKind.CONSUMABLE || !state.owns(id)

    companion object {
        val Factory = viewModelFactory {
            initializer { appContainer().let { ShopViewModel(it.session, it.game, it.content) } }
        }
    }
}
