package ru.finney.pet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.finney.pet.domain.model.Category
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.RadiusField
import ru.finney.pet.ui.theme.StrokeRegular
import ru.finney.pet.ui.theme.StrokeThin

// Три направления плана — нужное, желаемое, копилка — различаются значком, а не цветом.
// Плейтест: «почему нужное жёлтым, а копилка зелёным?» — ребёнок искал смысл в цвете,
// которого там не было, а зелёный у нас значит «получилось». Поэтому значок один на всё
// приложение и берётся отсюда: знакомство, план, магазин, итоги уровня, мини-игры.

/** Значок категории: корзинка у нужного, воздушный шарик у желаемого (доска «Части плана», 20.10). */
fun categoryIcon(category: Category): FinneyIcons = when (category) {
    Category.NEEDS -> FinneyIcons.Basket
    Category.WANTS -> FinneyIcons.Balloon
}

/** Значок копилки — третьего направления плана. Копилка не категория товара, отсюда отдельно. */
val SavingsIcon: FinneyIcons = FinneyIcons.Piggy

/**
 * Полоса категории во всю ширину панели товаров: зачем это и сколько на это
 * осталось по плану. Плейтест: заголовок «Нужное» над панелью не замечали и видели
 * просто набор продуктов. Полоса стоит прямо над товарами — в магазине и в окне
 * покупки на кухне и в ванной одна и та же, чтобы ребёнок узнавал в них один магазин.
 *
 * Полоса — узором своей части, как дуга на круге плана: ребёнок узнаёт в магазине ту же
 * часть. Зелёное «нужное» и розовое «хочется» раньше читались как «правильно» и «неправильно»,
 * поэтому смысл — узором, значком и словами, а слова лежат на кремовой вставке (ТЗ п. 3.6).
 * [planLeft] null — остаток не показывается (плана ещё нет или раздел без своей части).
 */
@Composable
fun CategoryBanner(category: Category, why: String, planLeft: Int?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(RadiusField)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(rememberPartBrush(category.toPart()))
            .border(StrokeRegular, FinneyInk, shape)
            .padding(8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(StrokeThin, FinneyInk, CircleShape),
        ) {
            FinneyIcon(categoryIcon(category), size = 28.dp)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .clip(shape)
                .background(FinneyCream)
                .border(StrokeThin, FinneyInk, shape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(why, style = MaterialTheme.typography.titleMedium, color = FinneyInk, modifier = Modifier.weight(1f))
            planLeft?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text("по плану", style = MaterialTheme.typography.labelMedium, color = FinneyInk)
                    CoinAmount(amount = it, coinSize = 22.dp)
                }
            }
        }
    }
}
