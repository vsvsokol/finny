package ru.finney.pet.ui.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import ru.finney.pet.domain.model.ItemKind
import ru.finney.pet.domain.model.ShopItem
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.semantics
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.WarningBadge
import ru.finney.pet.ui.theme.RadiusField
import ru.finney.pet.ui.theme.StrokeThin
import ru.finney.pet.ui.components.ActionFeedbackSummary
import ru.finney.pet.ui.components.FeedbackSound
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.finney.pet.domain.model.Category
import ru.finney.pet.ui.components.CategoryBanner
import ru.finney.pet.ui.components.CloseButton
import ru.finney.pet.ui.components.WalletButton
import ru.finney.pet.domain.game.PurchasePreview
import ru.finney.pet.domain.game.Rejection
import ru.finney.pet.ui.components.CoinAmount
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyScreen
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.components.FeedbackDialog
import ru.finney.pet.ui.room.CareOption
import ru.finney.pet.ui.room.CareTile
import ru.finney.pet.ui.room.FromWantsOffer
import ru.finney.pet.ui.room.ItemPicture
import ru.finney.pet.ui.room.PickHint
import ru.finney.pet.ui.room.UpcomingItem
import ru.finney.pet.ui.components.FinneyPanel
import ru.finney.pet.ui.room.itemFallback
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyInkFaded
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.components.MoodFace
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height

/**
 * Магазин: открывается кнопкой с деньгами на главном (так в макете — «при нажатии
 * открывается магазин + история трат»).
 *
 * Два отдела — «Нужное» и «Хочется»: ребёнок видит категорию раньше цены.
 * Плитки — те же [CareTile], что у кормления и мытья.
 */
@Composable
fun ShopScreen(
    onBack: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenHistory: () -> Unit,
    /** null — пока навигация не ведёт из магазина в копилку, плитка цели без перехода. */
    onOpenGoals: (() -> Unit)? = null,
    viewModel: ShopViewModel = viewModel(factory = ShopViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        ShopUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = FinneyInk)
        }

        is ShopUiState.Ready -> ShopContent(
            state = s,
            onSelect = viewModel::select,
            onBuy = { viewModel.buy(it) },
            onBuyFromWants = { viewModel.buy(it, fromWants = true) },
            onOpenGoals = onOpenGoals,
            onDismiss = viewModel::dismissFeedback,
            onBack = onBack,
            onOpenBudget = onOpenBudget,
            onOpenHistory = onOpenHistory,
        )
    }
}

@Composable
internal fun ShopContent(
    state: ShopUiState.Ready,
    onSelect: (String) -> Unit,
    onBuy: (String) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenHistory: () -> Unit,
    onBuyFromWants: (String) -> Unit = {},
    onOpenGoals: (() -> Unit)? = null,
) {
    val selected = state.shelves.flatMap { it.previews }.firstOrNull { it.item.id == state.selectedId }
    FeedbackSound(state.feedback, null)
    LaunchedEffect(state.feedback) {
        if (state.feedback != null) { delay(4_000); onDismiss() }
    }
    FinneyScreen(
        scrollable = true,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        // Шапка как верхний ряд главного: кошелёк слева, по центру заголовок, справа «✕».
        // Она над прокруткой и не уезжает (плейтест 28.09: сумма стояла под заголовком
        // по центру и пропадала, пока листаешь товары, — а смотрят на неё как раз тогда).
        top = { ShopHeader(balance = state.balance, onBack = onBack) },
        // Кнопка «Купить» одна на весь магазин и всегда на виду: раньше в каждой из трёх
        // панелей стояла своя, а в ней — подсказка «Выбери, что купить» вместо действия.
        bottom = {
            state.feedback?.let {
                ActionFeedbackSummary(it)
                Text(it.why, style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
            }
            BuyBar(state, selected, onBuy, onBuyFromWants, onOpenBudget)
        },
    ) {
        FeedbackDialog(
            feedback = null,
            rejection = state.rejection,
            onDismiss = onDismiss,
            actionLabel = if (state.rejection is Rejection.PlanNotConfirmed) "К плану расходов" else null,
            onAction = onOpenBudget,
        )

        state.saving?.let { SavingCard(it, onOpenGoals) }

        // Два отдела, а не четыре одинаковые панели (20.10: «Еда» и «Чистота» подряд
        // сливались в одно полотно). Отдел — категория и остаток по плану, один раз;
        // внутри полки со своим значком и «доской» под товарами.
        ShopDepartment(
            title = "Нужное",
            banner = { CategoryBanner(Category.NEEDS, "Сначала — нужное", state.needsLeft) },
            rows = state.shelves.filter { it.shelf == ShopShelf.FOOD || it.shelf == ShopShelf.CLEAN },
            selectedId = state.selectedId,
            onSelect = onSelect,
        )
        ShopDepartment(
            title = "Хочется",
            banner = { CategoryBanner(Category.WANTS, "Потом — то, что хочется", state.wantsLeft) },
            rows = state.shelves.filter { it.shelf == ShopShelf.TREATS || it.shelf == ShopShelf.TOYS },
            selectedId = state.selectedId,
            onSelect = onSelect,
        )
        state.upcoming?.let { item ->
            FinneyPanel(title = "Скоро") { UpcomingItem(item) }
        }
        FinneyButton(text = "История трат", onClick = onOpenHistory)
    }
}

/**
 * Отдел магазина: заголовок, полоса категории и полки. Пустая полка (игрушки все
 * куплены или ещё не открыты) уходит целиком, пустой отдел — тоже.
 */
@Composable
private fun ShopDepartment(
    title: String,
    banner: @Composable () -> Unit,
    rows: List<ShelfRow>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val filled = rows.filter { it.previews.isNotEmpty() }
    if (filled.isEmpty()) return
    FinneyPanel(title = title) {
        banner()
        filled.forEach { row ->
            Column(
                Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShelfLabel(row.shelf)
                row.previews.chunked(ShelfColumns).forEach { line ->
                    // Высота ряда — по высокой плитке: у соседки бывает лишняя плашка.
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        line.forEach { preview ->
                            CareTile(
                                CareOption(preview.item, preview, isSelected = preview.item.id == selectedId),
                                ShopTilePicture,
                                short = true,
                                onPick = { onSelect(preview.item.id) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        repeat(ShelfColumns - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                ShelfPlank()
            }
        }
    }
}

/**
 * Подпись полки: значок, название и черта до края. Значок тот же, что у шкалы или
 * комнаты: вилка — еда, ванна — чистота, лицо — радость, лампа — зал, где живут игрушки.
 */
@Composable
private fun ShelfLabel(shelf: ShopShelf) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (shelf) {
            ShopShelf.FOOD -> FinneyIcon(FinneyIcons.Food, size = 24.dp)
            ShopShelf.CLEAN -> FinneyIcon(FinneyIcons.Bath, size = 24.dp)
            ShopShelf.TREATS -> MoodFace(size = 24.dp)
            ShopShelf.TOYS -> FinneyIcon(FinneyIcons.Lamp, size = 24.dp)
        }
        Text(shelf.title, style = MaterialTheme.typography.titleMedium, color = FinneyInk)
        Box(Modifier.weight(1f).height(StrokeThin).background(FinneyInkFaded, RoundedCornerShape(50)))
    }
}

/** «Доска», на которой стоят товары: полка заканчивается видимой чертой, а не пустотой. */
@Composable
private fun ShelfPlank() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(FinneyPeach, RoundedCornerShape(50))
            .border(StrokeThin, FinneyInk, RoundedCornerShape(50)),
    )
}

private const val ShelfColumns = 3

/**
 * Верхний ряд магазина. Крайние слоты одинаковой ширины (weight 1), как на главном:
 * заголовок стоит ровно по центру и не съезжает от длины суммы.
 */
@Composable
private fun ShopHeader(balance: Int, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            WalletButton(balance = balance, onClick = null)
        }
        OutlinedText("Магазин", style = MaterialTheme.typography.headlineLarge)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            CloseButton(onBack)
        }
    }
}

private val ShopTilePicture = 64.dp

/**
 * Низ магазина: что выбрано и «Купить». Пока ничего не выбрано — подсказка
 * со стрелкой к товарам. Без плана — почему нельзя и путь к плану.
 */
@Composable
private fun BuyBar(
    state: ShopUiState.Ready,
    selected: PurchasePreview?,
    onBuy: (String) -> Unit,
    onBuyFromWants: (String) -> Unit,
    onOpenBudget: () -> Unit,
) {
    val fromWants = selected?.let { state.fromWants[it.item.id] }
    when {
        !state.canBuy -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Сначала составь план — потом покупки", style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
            FinneyButton(text = "К плану расходов", onClick = onOpenBudget)
        }
        selected == null -> PickHint()
        fromWants != null -> FromWantsOffer(
            amount = fromWants,
            takeLabel = "Взять и купить",
            overPlanLabel = "Купить сверх плана",
            onTake = { onBuyFromWants(selected.item.id) },
            onOverPlan = { onBuy(selected.item.id) },
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (selected.item.kind) {
                    ItemKind.TOY -> "Игрушка останется в зале. Играй с ней рядом с Финни."
                    ItemKind.ACCESSORY -> "Вещь останется в гардеробе."
                    ItemKind.CONSUMABLE -> "${selected.item.label}: Финни использует покупку сразу."
                },
                style = MaterialTheme.typography.bodyLarge, color = FinneyInk,
            )
            // На плитке только «!»: сколько не хватает и где взять — здесь, у выбранного.
            if (selected.shortage > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WarningBadge(size = 24.dp)
                    Text(
                        "Не хватает ${selected.shortage}. Монеты дают игры уровня.",
                        style = MaterialTheme.typography.bodyLarge, color = FinneyInk,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CoinAmount(amount = selected.item.price)
                FinneyButton(
                    text = if (selected.item.kind == ItemKind.CONSUMABLE) "Купить и использовать" else "Купить",
                    onClick = { onBuy(selected.item.id) }, modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
