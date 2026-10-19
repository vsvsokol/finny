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
import ru.finney.pet.ui.room.CarePanel
import ru.finney.pet.ui.room.ItemPicture
import ru.finney.pet.ui.room.PickHint
import ru.finney.pet.ui.room.itemFallback
import ru.finney.pet.ui.theme.FinneyInk

/**
 * Магазин: открывается кнопкой с деньгами на главном (так в макете — «при нажатии
 * открывается магазин + история трат»).
 *
 * Две панели — «Нужное» и «Хочется»: ребёнок видит категорию раньше цены.
 * Строки и подтверждение — та же [CarePanel], что у кормления и мытья.
 */
@Composable
fun ShopScreen(
    onBack: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenHistory: () -> Unit,
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
            onBuy = viewModel::buy,
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
) {
    val selected = (state.needs + state.wants + state.toys).firstOrNull { it.item.id == state.selectedId }
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
            BuyBar(state, selected, onBuy, onOpenBudget)
        },
    ) {
        FeedbackDialog(
            feedback = null,
            rejection = state.rejection,
            onDismiss = onDismiss,
            actionLabel = if (state.rejection is Rejection.PlanNotConfirmed) "К плану расходов" else null,
            onAction = onOpenBudget,
        )

        ShopSection(
            title = "Нужное",
            kind = SectionKind.NEEDS,
            planLeft = state.needsLeft,
            previews = state.needs,
            selectedId = state.selectedId,
            onSelect = onSelect,
        )
        ShopSection(
            title = "Хочется",
            kind = SectionKind.WANTS,
            planLeft = state.wantsLeft,
            previews = state.wants,
            selectedId = state.selectedId,
            onSelect = onSelect,
        )
        // Игрушки — тоже «хочется» и тратятся из той же части плана (остаток — в полосе выше).
        // Отдельно — потому что покупаются один раз и остаются в зале. Все куплены — раздел уходит.
        if (state.toys.isNotEmpty()) {
            ShopSection(
                title = "Игрушки",
                kind = SectionKind.TOYS,
                planLeft = null,
                previews = state.toys,
                selectedId = state.selectedId,
                onSelect = onSelect,
            )
        }

        FinneyButton(text = "История трат", onClick = onOpenHistory)
    }
}

/** Что за раздел магазина: от этого цвет и слова его полосы. */
private enum class SectionKind { NEEDS, WANTS, TOYS }

@Composable
private fun ShopSection(
    title: String,
    kind: SectionKind,
    planLeft: Int?,
    previews: List<PurchasePreview>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    CarePanel(
        title = title,
        options = previews.map { CareOption(it.item, it, isSelected = it.item.id == selectedId) },
        onPick = onSelect,
        onConfirm = {},
        onDismiss = null,
        allowShortage = true,
        header = { SectionBanner(kind, planLeft) },
        withConfirm = false,
        tiles = true,
    )
}

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

/** Полоса раздела. У игрушек заголовок «Игрушки», поэтому «хочется» сказано в полосе. */
@Composable
private fun SectionBanner(kind: SectionKind, planLeft: Int?) = when (kind) {
    SectionKind.NEEDS -> CategoryBanner(Category.NEEDS, "Нужное — сначала", planLeft)
    SectionKind.WANTS -> CategoryBanner(Category.WANTS, "Хочется — потом", planLeft)
    SectionKind.TOYS -> CategoryBanner(Category.WANTS, "Это «хочется». Игрушка лежит в зале", planLeft)
}

/**
 * Низ магазина: что выбрано и «Купить». Пока ничего не выбрано — подсказка
 * со стрелкой к товарам. Без плана — почему нельзя и путь к плану.
 */
@Composable
private fun BuyBar(state: ShopUiState.Ready, selected: PurchasePreview?, onBuy: (String) -> Unit, onOpenBudget: () -> Unit) {
    when {
        !state.canBuy -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Сначала составь план — потом покупки", style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
            FinneyButton(text = "К плану расходов", onClick = onOpenBudget)
        }
        selected == null -> PickHint()
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (selected.item.kind) {
                    ItemKind.TOY -> "Игрушка останется в зале. Играй с ней рядом с Финни."
                    ItemKind.ACCESSORY -> "Вещь останется в гардеробе."
                    ItemKind.CONSUMABLE -> "${selected.item.label}: Финни использует покупку сразу."
                },
                style = MaterialTheme.typography.bodyLarge, color = FinneyInk,
            )
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
