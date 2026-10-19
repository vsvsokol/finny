package ru.finney.pet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.theme.stroke

/**
 * Текст с наружной обводкой — тот самый приём из макета: заливка `#FA987C`,
 * контур `#382C92`. Рисуется в два прохода: сначала контур, поверх — заливка.
 * Так контур уходит наружу, а не съедает букву, как было бы с одним проходом.
 *
 * Толщина берётся из кегля правилом [stroke], поэтому надпись любого размера
 * выглядит одинаково «мультяшно» и подбирать число вручную не нужно.
 */
@Composable
fun OutlinedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fill: Color = FinneyPeach,
    outline: Color = FinneyInk,
    textAlign: TextAlign? = null,
) {
    // Stroke задаётся в пикселях, а правило обводки — в dp: переводим по плотности экрана.
    val widthPx = with(LocalDensity.current) { stroke(style.fontSize).toPx() }

    // Box, а не два Text подряд: иначе в колонке контур и заливка встали бы
    // друг под другом двумя строками вместо одной надписи.
    Box(modifier = modifier) {
        // Контур. Наружу уходит половина толщины, поэтому берём двойную:
        // видимая часть совпадает с тем, что нарисовано в макете.
        Text(
            text = text,
            style = style.copy(color = outline, drawStyle = Stroke(width = widthPx * 2f)),
            textAlign = textAlign,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text = text,
            style = style.copy(color = fill),
            textAlign = textAlign,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFEDCD)
@Composable
private fun OutlinedTextPreview() {
    FinneyTheme {
        Column(
            modifier = Modifier.background(FinneyCream).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedText("Магазин", style = MaterialTheme.typography.headlineLarge)
            OutlinedText("0123456789", style = MaterialTheme.typography.headlineMedium)
            OutlinedText("Обычный текст", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
