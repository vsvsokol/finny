package ru.finney.pet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.theme.FinneyGreenDark
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyStrokeRatio
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.theme.FinneyYellow

// Монета и уровень из UI-кита. Монета — зелёный кружок с «Ф» (финки, игровая
// валюта), уровень — жёлтый кружок с цифрой. Нарисованы кодом, а не картинкой:
// графики в ресурсах пока нет, а формы простые.

/**
 * Монета игровой валюты. Только значок, без суммы — сумму ставит [CoinAmount].
 *
 * Структура слоёв снята с эталона `Frame 24.png` промером по горизонтали:
 * синее кольцо снаружи → кремовый зазор → тёмно-зелёное кольцо → светло-зелёное
 * тело → кремовая «Ф». Раньше монета была просто зелёным кружком с буквой,
 * и рядом с эталоном выглядела пустой.
 *
 * Доли радиуса, а не dp: монета одинаково собирается и в 20 dp у цены товара,
 * и в 40 dp у баланса.
 */
@Composable
fun Coin(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val d = this.size.minDimension
            val centre = Offset(this.size.width / 2f, this.size.height / 2f)

            // Радиусы — доли диаметра, снятые с горизонтального среза эталона
            // Frame 24.png. Рисуем от внешнего к внутреннему: каждый следующий
            // круг перекрывает предыдущий.
            drawCircle(FinneyInk, d * 0.500f, centre)       // синяя обводка
            drawCircle(FinneyCream, d * 0.448f, centre)     // кремовый зазор
            drawCircle(FinneyGreenDark, d * 0.428f, centre) // тёмное кольцо
            drawCircle(FinneyGreen, d * 0.376f, centre)     // светлое тело
        }

        // Кегль привязан к размеру кружка, а не к настройкам шрифта системы:
        // это значок, и буква должна помещаться в монету при любом масштабе текста.
        OutlinedText(
            text = "Ф",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = (size.value * 0.58f).sp),
            fill = FinneyCream,
            outline = FinneyGreenDark,
        )
    }
}

/**
 * Сумма с монетой: баланс на главном, цена товара, размер награды.
 * Для TalkBack читается одной фразой, иначе он произносит «Ф» отдельной буквой.
 */
@Composable
fun CoinAmount(
    amount: Int,
    modifier: Modifier = Modifier,
    coinSize: Dp = 28.dp,
) {
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$amount финок" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedText(amount.toString(), style = MaterialTheme.typography.titleLarge)
        Coin(size = coinSize)
    }
}

/**
 * Уровень игрока — жёлтый кружок с цифрой, как в макете.
 *
 * [progress] — сколько пройдено до следующего уровня, 0..1. В ките вокруг
 * значка нарисована ровно такая же дуга, как у кнопок потребностей, — это
 * один и тот же [ProgressRing]. null — дуги нет: место под неё остаётся,
 * чтобы значок не прыгал по размеру.
 */
@Composable
fun LevelBadge(
    level: Int,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    progress: Float? = null,
) {
    ProgressRing(diameter = size, progress = progress, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                // Блик и полоса — как у круглых кнопок: на плоский кружок дети не нажимали.
                .buttonFill(pressed = false, round = true)
                .clearAndSetSemantics { contentDescription = "Уровень $level" },
            contentAlignment = Alignment.Center,
        ) {
            // Цифра растёт вместе с кругом: на 56 dp это прежние 28 sp, на 112 dp — вдвое больше.
            // Привязка к размеру круга, а не к шрифту системы: это значок, цифра обязана влезать.
            OutlinedText(
                text = level.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = (size.value * 0.5f).sp,
                    lineHeight = (size.value * 0.68f).sp,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFEDCD)
@Composable
private fun MoneyPreview() {
    FinneyTheme {
        Column(
            modifier = Modifier.background(FinneyCream).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoinAmount(amount = 120)
            LevelBadge(level = 1, progress = 0.6f)
        }
    }
}
