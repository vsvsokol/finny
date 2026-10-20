package ru.finney.pet.ui.wardrobe

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
import kotlinx.coroutines.launch
import ru.finney.pet.appContainer
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.GameResult
import ru.finney.pet.domain.game.Rejection
import ru.finney.pet.domain.game.Session
import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.domain.model.ItemKind
import ru.finney.pet.domain.model.PetAppearance
import ru.finney.pet.domain.model.SavedGame
import ru.finney.pet.domain.model.ShopItem

/**
 * Вещь в гардеробе. Та, что ещё не твоя, тоже видна — с подписью, как её получить:
 * гардероб заодно показывает, ради чего копить и что есть в магазине.
 */
data class WardrobeItem(
    val item: ShopItem,
    val owned: Boolean,
    /** Как получить, если ещё нет: «копи: …» или «в магазине за …». null — уже твоя. */
    val howToGet: String?,
    /** Накоплено и цена цели, если вещь дают за цель, — для полосы под «копи: …». */
    val progress: Pair<Int, Int>? = null,
)

sealed interface WardrobeUiState {
    data object Loading : WardrobeUiState

    data class Ready(
        val appearance: PetAppearance,
        /** Что надето: шляпа и очки носятся вместе. */
        val worn: List<String>,
        val items: List<WardrobeItem>,
        val rejection: Rejection? = null,
        /** Надетая окраска; null — цвет профиля. */
        val coloring: String? = null,
    ) : WardrobeUiState
}

/** Гардероб отдельным экраном: только питомец и его вещи, без комнаты и кнопок главного. */
class WardrobeViewModel(
    private val session: Session,
    private val game: Game,
    private val content: GameContent,
) : ViewModel() {

    private val rejection = MutableStateFlow<Rejection?>(null)

    val uiState: StateFlow<WardrobeUiState> =
        combine(session.activeGame.filterNotNull(), rejection, ::toUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WardrobeUiState.Loading)

    fun wear(itemId: String) = run { session.execute { wear(it, itemId) } }

    /** Снять вещь; null — снять всё. */
    fun takeOff(itemId: String? = null) = run { session.execute { takeOff(it, itemId) } }

    fun dismissRejection() {
        rejection.value = null
    }

    private fun run(command: suspend () -> GameResult) {
        viewModelScope.launch {
            rejection.value = (command() as? GameResult.Rejected)?.reason
        }
    }

    private fun toUiState(saved: SavedGame, rejection: Rejection?): WardrobeUiState.Ready {
        val state = saved.state
        val owned = game.wardrobe(state).map { it.id }.toSet()
        return WardrobeUiState.Ready(
            appearance = saved.profile.appearance,
            worn = state.worn,
            coloring = game.lookFor(state, saved.profile.appearance.character),
            items = content.shop.filter { it.kind == ItemKind.ACCESSORY }.map { item ->
                val goal = content.goalFor(item.id)
                WardrobeItem(
                    item = item,
                    owned = item.id in owned,
                    howToGet = when {
                        item.id in owned -> null
                        goal != null -> "копи: ${state.goalSaved(goal.id)} из ${goal.price}"
                        else -> "в магазине за ${item.price}"
                    },
                    progress = goal?.takeIf { item.id !in owned }?.let { state.goalSaved(it.id) to it.price },
                )
            },
            rejection = rejection,
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { appContainer().let { WardrobeViewModel(it.session, it.game, it.content) } }
        }
    }
}
