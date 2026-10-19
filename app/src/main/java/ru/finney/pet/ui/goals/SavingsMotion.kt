package ru.finney.pet.ui.goals

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import ru.finney.pet.ui.components.ActionFeedback
import ru.finney.pet.ui.components.Coin
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.FinneyPanel
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.pet.accessoryArt
import ru.finney.pet.ui.room.ItemPicture
import ru.finney.pet.ui.room.itemFallback
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.theme.RadiusCard
import ru.finney.pet.ui.theme.StrokeRegular

// Копилка картинкой. Плейтест 29.09: «сложно понять, что происходит, — что положил,
// что забрал». Раньше итог взноса был одной строкой под кнопками, а деньги — числом
// без подписи. Теперь кошелёк и копилка стоят рядом, и при взносе монеты летят из
// одного в другое, а над ними всплывает «+15» и «−15». Ниже — последние движения.

/** Сколько монет летит за одно движение — не по одной на финку, а для вида. */
private const val FlyingCoins = 5

/** Полёт монет вместе с «+15» над копилкой, мс. */
private const val FlightMillis = 1100

/** Доля полёта, которую летит одна монета; остальное — очередь за ней. */
private const val CoinShare = 0.6f

/** Задержка между монетами в долях полёта. */
private const val CoinGap = 0.08f

/**
 * Кошелёк и копилка рядом. [move] — последнее движение: монеты летят из кошелька
 * в копилку (взнос) или обратно (снятие), числа досчитывают до нового значения.
 * Кошелёк слева, копилка справа — как «Взять» и «Положить» в переключателе под ними.
 */
@Composable
internal fun MoneyPair(balance: Int, saved: Int, move: SavingsMove?, modifier: Modifier = Modifier) {
    val animations = LocalAnimations.current
    val flight = remember { Animatable(1f) }
    // Ключ — номер движения: два одинаковых взноса подряд — два полёта.
    LaunchedEffect(move?.id) {
        if (move == null || !animations) return@LaunchedEffect
        flight.snapTo(0f)
        flight.animateTo(1f, tween(FlightMillis, easing = LinearEasing))
    }
    val count = tween<Int>(if (animations) FlightMillis * 2 / 3 else 0)
    val shownBalance by animateIntAsState(balance, count, label = "wallet")
    val shownSaved by animateIntAsState(saved, count, label = "piggy")
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier.fillMaxWidth().onSizeChanged { size = it }) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MoneyBox("в кошельке", shownBalance, balance, Modifier.weight(1f).fillMaxHeight()) { Coin(size = 24.dp) }
            MoneyBox("в копилке", shownSaved, saved, Modifier.weight(1f).fillMaxHeight()) {
                FinneyIcon(FinneyIcons.Piggy, size = 24.dp)
            }
        }

        val t = flight.value
        if (move != null && t < 1f && size != IntSize.Zero) {
            val density = LocalDensity.current
            val put = move.delta > 0
            val half = size.width / 2f
            val from = if (put) half / 2f else half * 1.5f
            val to = if (put) half * 1.5f else half / 2f
            val top = size.height * 0.3f
            val lift = with(density) { 56.dp.toPx() }
            val coin = with(density) { 28.dp.toPx() }
            repeat(FlyingCoins) { i ->
                val p = ((t - i * CoinGap) / CoinShare).coerceIn(0f, 1f)
                if (p > 0f && p < 1f) {
                    val x = from + (to - from) * p
                    val y = top - sin(PI * p).toFloat() * lift
                    Coin(
                        size = 28.dp,
                        modifier = Modifier.offset { IntOffset((x - coin / 2).roundToInt(), (y - coin / 2).roundToInt()) },
                    )
                }
            }
            // «+15» над тем, куда пришло, и «−15» над тем, откуда ушло: поднимаются и тают.
            val amount = abs(move.delta)
            // Начинают чуть выше рамок, чтобы не лечь на обводку.
            val rise = with(density) { (20.dp + 24.dp * t).roundToPx() }
            val fade = 1f - t * t
            val halfDp = with(density) { half.toDp() }
            DeltaTag("+$amount", fade, Modifier.width(halfDp).offset { IntOffset(if (put) half.roundToInt() else 0, -rise) })
            DeltaTag("−$amount", fade, Modifier.width(halfDp).offset { IntOffset(if (put) 0 else half.roundToInt(), -rise) })
        }
    }
}

@Composable
private fun DeltaTag(text: String, alpha: Float, modifier: Modifier) {
    Box(modifier.alpha(alpha), contentAlignment = Alignment.Center) {
        OutlinedText(text, style = MaterialTheme.typography.headlineMedium)
    }
}

/**
 * Кошелёк или копилка: значок, число и подпись. [shown] — число, которое сейчас
 * досчитывается; TalkBack читает сразу итоговое [amount].
 */
@Composable
private fun MoneyBox(caption: String, shown: Int, amount: Int, modifier: Modifier, icon: @Composable () -> Unit) {
    val shape = RoundedCornerShape(RadiusCard)
    Row(
        modifier = modifier.clip(shape).background(Color.White).border(StrokeRegular, FinneyInk, shape)
            .clearAndSetSemantics { contentDescription = "$caption $amount финок" }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(shown.toString(), style = MaterialTheme.typography.titleLarge, color = FinneyInk)
            Text(caption, style = MaterialTheme.typography.labelMedium, color = FinneyInk)
        }
    }
}

/**
 * Последние взносы и снятия по цели, новые сверху: стрелка и слово — куда, число —
 * сколько (ТЗ п. 3.6: не одним цветом). [moveId] сменился — верхняя строка
 * вздрагивает: это она только что появилась.
 */
@Composable
internal fun SavingsHistory(moves: List<SavingsEntry>, moveId: Int?, modifier: Modifier = Modifier) {
    FinneyPanel(title = "Положил и взял", modifier = modifier) {
        moves.forEachIndexed { i, entry -> HistoryRow(entry, bumpKey = if (i == 0) moveId else null) }
    }
}

@Composable
private fun HistoryRow(entry: SavingsEntry, bumpKey: Int?) {
    val put = entry.delta > 0
    val bump = remember { Animatable(1f) }
    val animations = LocalAnimations.current
    LaunchedEffect(bumpKey) {
        if (bumpKey == null || !animations) return@LaunchedEffect
        bump.snapTo(1.08f)
        bump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    val shape = RoundedCornerShape(RadiusCard)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = bump.value
                scaleY = bump.value
            }
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(Color.White)
            .border(StrokeRegular, FinneyInk, shape)
            .clearAndSetSemantics {
                contentDescription = if (put) "Положил ${entry.delta}" else "Взял ${-entry.delta}"
            }
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        // Стрелки — те же, что в переключателе: ↓ в копилку, ↑ из неё.
        OutlinedText(if (put) "↓" else "↑", style = MaterialTheme.typography.titleLarge)
        Text(
            if (put) "Положил" else "Взял",
            style = MaterialTheme.typography.bodyLarge,
            color = FinneyInk,
            modifier = Modifier.weight(1f),
        )
        OutlinedText(if (put) "+${entry.delta}" else "−${-entry.delta}", style = MaterialTheme.typography.titleMedium)
        Coin(size = 20.dp)
    }
}

/**
 * Цель забрали: сама вещь крупно, «Шляпа — твоя!» и одна фраза. Без строк «было →
 * стало» — плейтест 29.09 назвал прежнее окно захламлённым: это праздник, а не отчёт.
 */
@Composable
internal fun GoalDoneDialog(feedback: ActionFeedback, onDismiss: () -> Unit) {
    val sounds = LocalSounds.current
    LaunchedEffect(feedback) { sounds.play(Sfx.GameWin) }
    Dialog(onDismissRequest = onDismiss) {
        FinneyPanel(title = "Ура!") {
            val center = Modifier.align(Alignment.CenterHorizontally)
            feedback.itemId?.let { id ->
                val art = accessoryArt(id)
                if (art != null) {
                    Image(painterResource(art.res), contentDescription = null, modifier = center.size(140.dp))
                } else {
                    ItemPicture(id, itemFallback(id), size = 140.dp, modifier = center)
                }
            }
            OutlinedText(feedback.title, modifier = center, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                feedback.why,
                style = MaterialTheme.typography.bodyLarge,
                color = FinneyInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            FinneyButton(text = "Здорово!", onClick = onDismiss)
        }
    }
}

@Preview(widthDp = 360, showBackground = true, backgroundColor = 0xFFFFEDCD)
@Composable
private fun SavingsMotionPreview() {
    FinneyTheme {
        Column(
            modifier = Modifier.background(FinneyCream).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MoneyPair(balance = 20, saved = 35, move = null)
            SavingsHistory(listOf(SavingsEntry(15), SavingsEntry(-10), SavingsEntry(20)), moveId = null)
        }
    }
}
