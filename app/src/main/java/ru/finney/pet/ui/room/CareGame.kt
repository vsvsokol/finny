package ru.finney.pet.ui.room

import ru.finney.pet.ui.motion.LocalAnimations
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ru.finney.pet.R
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.CloseButton
import ru.finney.pet.ui.components.FinneyIconButton
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.components.ProgressRing
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.StrokeRegular
import kotlin.math.roundToInt

// Общее у игр ухода: кормления (FeedingGame) и мытья (WashingGame).
//
// Покупка в них случается в конце игры, а не на кнопке панели: ребёнок
// выбирает и соглашается в CarePanel (цена и эффект видны до покупки, ТЗ п. 2.5.6),
// а деньги уходят, когда еда попала в рот или питомец отмыт. Промахнулся —
// ничего не потрачено, можно ещё раз. Передумал — «✕» внизу слева, тоже бесплатно.

/** Где предмет ждёт, пока его возьмут: над нижним рядом, по центру. */
internal val ItemRestFromBottom = 200.dp

/** Размер предмета в руке. */
internal val ItemSize = 96.dp

/**
 * Рисунок предмета по id из магазина. null — дизайнеры его ещё не нарисовали,
 * тогда в руке значок. Файлы кладёт tools/pack_items.py.
 */
@DrawableRes
internal fun itemArt(itemId: String): Int? = when (itemId) {
    "food_apple" -> R.drawable.item_food_apple
    "food_bowl" -> R.drawable.item_food_bowl
    "food_bread" -> R.drawable.item_food_bread
    "food_egg" -> R.drawable.item_food_egg
    "food_milk" -> R.drawable.item_food_milk
    "food_water" -> R.drawable.item_food_water
    "food_lemon" -> R.drawable.item_food_lemon
    "food_watermelon" -> R.drawable.item_food_watermelon
    "food_juice" -> R.drawable.item_food_juice
    "care_soap" -> R.drawable.item_care_soap
    "care_soap_liquid" -> R.drawable.item_care_soap_liquid
    "care_towel" -> R.drawable.item_care_towel
    "care_toothbrush" -> R.drawable.item_care_toothbrush
    "care_toothpaste" -> R.drawable.item_care_toothpaste
    "treat_candy" -> R.drawable.item_treat_candy
    "fun_cartoon" -> R.drawable.item_fun_tv
    "toy_ball" -> R.drawable.item_toy_ball
    "toy_book" -> R.drawable.item_toy_book
    "toy_blocks" -> R.drawable.item_toy_blocks
    "toy_bear" -> R.drawable.item_toy_bear
    "toy_duck" -> R.drawable.item_toy_duck
    "toy_cube" -> R.drawable.item_toy_cube
    "hat_cowboy" -> R.drawable.acc_hat_cowboy
    "hat_pirate" -> R.drawable.acc_hat_pirate
    "hat_wizard" -> R.drawable.acc_hat_wizard
    "glasses_black" -> R.drawable.acc_glasses_black
    "glasses_star" -> R.drawable.acc_glasses_star
    "glasses_pineapple" -> R.drawable.acc_glasses_pineapple
    else -> null
}

/** Значок вместо рисунка, пока его нет: лампа — лампой, остальное — значком комнаты или звездой. */
internal fun itemFallback(itemId: String): FinneyIcons = when {
    itemId.startsWith("food_") -> FinneyIcons.Food
    itemId.startsWith("care_") -> FinneyIcons.Bath
    itemId == "decor_lamp" -> FinneyIcons.Lamp
    else -> FinneyIcons.Star
}

/** Предмет в руке: рисунок, а если рисунка нет — значок комнаты. */
@Composable
internal fun ItemPicture(itemId: String, fallback: FinneyIcons, size: Dp, modifier: Modifier = Modifier) {
    val art = itemArt(itemId)
    if (art != null) {
        Image(painter = painterResource(art), contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            FinneyIcon(fallback, size = size * 0.7f)
        }
    }
}

/** Какой по счёту в нижнем ряду главного стоит кнопка комнаты: зал, кухня, ванная. */
internal enum class CareSlot { KITCHEN, BATH }

/**
 * Рамка игры ухода, одинаковая у кормления и мытья. Внизу — тот же ряд, что
 * кнопки комнат на главном: значок комнаты стоит на своём месте, кольцо вокруг —
 * сколько сделано (у мытья), а слева — привычный «✕», как в мини-играх.
 * Системное «назад» тоже выходит. Подписей нет: что делать, показывает сама
 * игра — предмет-подсказка повторяет жест, пока ребёнок не коснулся экрана.
 *
 * Раньше сверху была надпись («Намыль питомца») — она налезала на значок
 * уровня, — а снизу кнопка «Не сейчас», которой нет больше нигде.
 */
@Composable
internal fun CareGameFrame(
    slot: CareSlot,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    progressLabel: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    BackHandler(onBack = onCancel)
    Box(modifier = modifier.fillMaxSize()) {
        content()
        // Ряд — как на главном: колонка там с системными полями и полями 16 dp,
        // кнопки 64 dp поровну по ширине. Так значок не сдвигается, когда игра открылась.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(RoomButtonSize), contentAlignment = Alignment.Center) {
                CloseButton(onCancel, description = "Не сейчас", size = 56.dp)
            }
            Box(Modifier.size(RoomButtonSize)) {
                if (slot == CareSlot.KITCHEN) RoomMark(FinneyIcons.Food, progress, progressLabel)
            }
            Box(Modifier.size(RoomButtonSize)) {
                if (slot == CareSlot.BATH) RoomMark(FinneyIcons.Bath, progress, progressLabel)
            }
        }
    }
}

/** Размер кнопок комнат на главном — у FinneyNeedButton по умолчанию. */
private val RoomButtonSize = 64.dp

/** Значок комнаты на своём месте в ряду: где идёт игра и сколько уже сделано. */
@Composable
private fun RoomMark(icon: FinneyIcons, progress: Float?, label: String?) {
    ProgressRing(
        diameter = RoomButtonSize,
        progress = progress,
        modifier = Modifier.semantics { contentDescription = label ?: "" },
    ) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(FinneyYellow).border(StrokeRegular, FinneyInk, CircleShape),
            contentAlignment = Alignment.Center,
        ) { FinneyIcon(icon, size = RoomButtonSize / 2) }
    }
}

/**
 * Подсказка жестом: полупрозрачная копия предмета сама делает то, что нужно,
 * — трёт питомца или летит ему в рот. [at] — где она в момент [t] 0…1 круга.
 * Пропадает с первым касанием ([visible]).
 */
@Composable
internal fun GestureGhost(
    itemId: String,
    fallback: FinneyIcons,
    visible: Boolean,
    periodMs: Int,
    at: (Float) -> Offset,
    alphaAt: (Float) -> Float = { 1f },
) {
    if (!visible) return
    val density = LocalDensity.current
    val tAnim by rememberInfiniteTransition(label = "ghost").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = "t",
    )
    // Без анимаций призрак застыл бы в конце пути, где он уже тает. Стоит на полпути —
    // видно, откуда и куда вести.
    val t = if (LocalAnimations.current) tAnim else 0.5f
    val half = with(density) { ItemSize.toPx() } / 2
    ItemPicture(
        itemId = itemId,
        fallback = fallback,
        size = ItemSize,
        modifier = Modifier
            .offset {
                val p = at(t)
                IntOffset((p.x - half).roundToInt(), (p.y - half).roundToInt())
            }
            .graphicsLayer { alpha = GHOST_ALPHA * alphaAt(t) },
    )
}

private const val GHOST_ALPHA = 0.55f
