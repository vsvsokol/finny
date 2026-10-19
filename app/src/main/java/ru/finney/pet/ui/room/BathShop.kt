package ru.finney.pet.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.finney.pet.domain.model.Category
import ru.finney.pet.ui.components.CheckBadge
import ru.finney.pet.ui.components.CoinAmount
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyCard
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.FinneyPanel
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.components.WarningBadge
import ru.finney.pet.ui.components.categoryIcon
import ru.finney.pet.ui.components.pointHere
import ru.finney.pet.ui.theme.FinneyBlue
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.RadiusCard
import ru.finney.pet.ui.theme.StrokeThin

// Магазин одного шага ванны: открывается с плитки «Купить» и показывает только то, что
// нужно для этого шага. Плейтест 19.10: «не понимаю, какое мыло купить» — оба мыла моют
// одинаково, разница в том, на сколько хватает. Поэтому у каждого крупно «хватит на N раз»
// и цена одного раза, а у того, где раз дешевле, — «Выгоднее». Это и есть урок: дороже
// сразу, но дешевле на каждый раз.

/**
 * Магазин шага [step]: мыло или щётка с пастой. Покупка — выбором и отдельной кнопкой,
 * как везде (ТЗ п. 2.5.6). [block] — купить сейчас нельзя (нет плана) и куда идти.
 * Когда для шага всё есть, окно закрывается само — ребёнок снова в ванной, рука на плитке.
 * [fromWants] — нужное, которое выйдет за план: вместо «Купить» — [FromWantsOffer].
 */
@Composable
fun BathShop(
    step: BathStep,
    shelf: BathShelf,
    onBuy: (itemId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    block: CareBlock? = null,
    coins: (@Composable () -> Unit)? = null,
    fromWants: Map<String, Int> = emptyMap(),
    onBuyFromWants: ((itemId: String) -> Unit)? = null,
) {
    val offers = shelf.items.filter { it.step == step }.sortedBy { it.item.price }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = offers.firstOrNull { it.item.id == picked }

    // Купили то, чего не хватало, — шаг готов, окно больше не нужно.
    val ready = when (step) {
        BathStep.TEETH -> shelf.hasTeeth
        else -> shelf.items.any { it.step == step && it.left > 0 }
    }
    val wasReady = remember { ready }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(ready) { if (ready && !wasReady) dismiss() }

    // Цена одного раза — по ней сравнивают мыло. Выгоднее — где раз дешевле, если мыла больше одного.
    fun perUse(o: ShelfItem) = o.item.price.toFloat() / o.uses
    // Закрытое по уровню не сравниваем: купить его всё равно нельзя.
    val best = offers.filter { it.lockedUntil == null }.takeIf { it.size > 1 }?.minByOrNull(::perUse)
    // Щётка без пасты не чистит: рука — на то, чего нет.
    val pointAt = if (selected == null && block == null) {
        offers.firstOrNull { it.left == 0 && it.preview.shortage == 0 && it.lockedUntil == null } ?: best?.takeIf { it.preview.shortage == 0 }
    } else null

    FinneyPanel(
        title = if (step == BathStep.TEETH) "Для зубов" else "Мыло",
        onClose = onDismiss,
        modifier = modifier,
    ) {
        coins?.invoke()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FinneyIcon(categoryIcon(Category.NEEDS), size = 24.dp)
            Text(
                text = if (step == BathStep.TEETH) "Нужное. Чистят щёткой с пастой — нужны обе" else "Нужное. Моют одинаково — разница, на сколько хватит",
                style = MaterialTheme.typography.bodyLarge,
                color = FinneyInk,
                modifier = Modifier.weight(1f),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            offers.forEach { offer ->
                BathOffer(
                    offer = offer,
                    selected = offer.item.id == picked,
                    best = offer == best,
                    perUse = perUse(offer),
                    pair = step == BathStep.TEETH,
                    onPick = { if (offer.lockedUntil == null) picked = offer.item.id },
                    modifier = Modifier.pointHere(offer == pointAt, corner = RadiusCard),
                )
            }
        }
        if (block != null) {
            Text(block.reason, style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
            FinneyButton(text = block.actionLabel, onClick = block.onAction)
            return@FinneyPanel
        }
        if (selected == null) PickHint()
        val take = selected?.takeIf { it.preview.shortage == 0 && onBuyFromWants != null }?.let { fromWants[it.item.id] }
        if (selected != null && take != null) {
            FromWantsOffer(
                amount = take,
                takeLabel = "Взять и купить",
                overPlanLabel = "Купить сверх плана",
                onTake = {
                    onBuyFromWants?.invoke(selected.item.id)
                    picked = null
                },
                onOverPlan = {
                    onBuy(selected.item.id)
                    picked = null
                },
            )
            return@FinneyPanel
        }
        FinneyButton(
            text = "Купить",
            onClick = {
                selected?.let {
                    onBuy(it.item.id)
                    picked = null
                }
            },
            enabled = selected != null && selected.preview.shortage == 0,
        )
    }
}

/**
 * Строка товара: картинка, «хватит на N раз» крупно, «+40 за раз», цена и цена одного раза.
 * Сколько уже есть — «есть ×N» или «нет». Выбранное — жёлтое с «✓», нехватка — голубое
 * с «!» и словами (ТЗ п. 3.6), как в окне кухни. Закрытое по уровню — бледное, с замком
 * и «откроется на уровне N», выбрать нельзя.
 */
@Composable
private fun BathOffer(
    offer: ShelfItem,
    selected: Boolean,
    best: Boolean,
    perUse: Float,
    pair: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shortage = offer.preview.shortage
    val each = if (perUse % 1f == 0f) perUse.toInt().toString() else "%.1f".format(perUse).replace('.', ',')
    val locked = offer.lockedUntil
    val accent = when {
        locked != null -> null
        shortage > 0 -> FinneyBlue
        selected -> FinneyYellow
        else -> null
    }
    FinneyCard(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = "${offer.item.label}: хватит на ${times(offer.uses)}, ${offer.item.price} финок, " +
                    "раз — $each" + (if (best) ", выгоднее" else "") + (locked?.let { ", откроется на уровне $it" } ?: "")
                this.selected = selected
            }
            .clickable(enabled = locked == null, onClickLabel = "Выбрать: ${offer.item.label}", onClick = onPick),
        accent = accent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(contentAlignment = Alignment.TopEnd) {
                ItemPicture(offer.item.id, itemFallback(offer.item.id), size = 64.dp, modifier = Modifier.alpha(if (locked != null) 0.35f else 1f))
                if (selected) CheckBadge(size = 24.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (pair) offer.item.label else "На ${times(offer.uses)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = FinneyInk,
                )
                Text(
                    text = if (pair) "хватит на ${times(offer.uses)}" else "+${offer.item.effect.hygiene} чистоты за раз",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinneyInk,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        locked != null -> FinneyIcon(FinneyIcons.Lock, size = 20.dp)
                        offer.left == 0 -> WarningBadge(size = 20.dp)
                    }
                    Text(
                        text = when {
                            locked != null -> "с уровня $locked"
                            offer.left == 0 -> "нет на полочке"
                            else -> "есть ×${offer.left}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = FinneyInk,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CoinAmount(amount = offer.item.price)
                if (!pair) Text("раз — $each", style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
                if (best) BestLabel()
            }
        }
        if (shortage > 0 && locked == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WarningBadge(size = 20.dp)
                Text("Не хватает $shortage", style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
            }
        }
    }
}

/** «Выгоднее» — жёлтая, как всё про деньги, и словом (ТЗ п. 3.6). */
@Composable
private fun BestLabel() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(FinneyYellow)
            .border(StrokeThin, FinneyInk, RoundedCornerShape(50))
            .padding(horizontal = 8.dp),
    ) {
        OutlinedText("Выгоднее", style = MaterialTheme.typography.titleMedium)
    }
}
