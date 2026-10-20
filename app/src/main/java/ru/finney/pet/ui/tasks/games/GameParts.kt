package ru.finney.pet.ui.tasks.games

import ru.finney.pet.ui.motion.LocalAnimations
import android.annotation.SuppressLint
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.ItemArt
import ru.finney.pet.domain.tasks.TaskInputError
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.categoryIcon
import ru.finney.pet.ui.components.HoldStepper
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.components.StableOutlinedText
import ru.finney.pet.ui.components.fadingScroll
import ru.finney.pet.ui.theme.FinneyBlue
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneySand
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.RadiusCard
import ru.finney.pet.ui.theme.StrokeBold
import ru.finney.pet.ui.theme.StrokeRegular

// Общие детали мини-игр: рамка экрана, рисунок предмета, монета с номиналом,
// подсказка, кнопки «−» и «+». Каждая игра собрана из них, поэтому выглядят
// они одинаково и правятся в одном месте.

/**
 * Рамка игры: короткая подсказка сверху, игра посередине, кнопки снизу.
 *
 * [scrollable] — середина прокручивается: полка магазина или список трат длиннее
 * экрана на 360 dp. Для игр с перетаскиванием прокрутку не включать — она перехватит жест.
 */
@Composable
internal fun MiniGameFrame(
    hint: String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    bottom: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FinneyCream)
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedText(hint, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (scrollable) Modifier.fadingScroll(rememberScrollState()) else Modifier),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
        bottom()
    }
}

/**
 * Рисунок предмета: картинка из res/drawable-nodpi по имени из JSON, иначе эмодзи,
 * иначе первая буква. Так [@vsvsokol] добавляет предмет без программиста, а
 * дизайнеры потом заменяют эмодзи на рисунок одной строкой. Эмодзи — только
 * временный значок предмета; в текстах игр их нет.
 */
@SuppressLint("DiscouragedApi") // Имя картинки приходит из JSON — R.drawable.* здесь не подставить.
@Composable
internal fun ItemPicture(art: ItemArt, label: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable = remember(art.art) {
        art.art?.let { context.resources.getIdentifier(it, "drawable", context.packageName) }?.takeIf { it != 0 }
    }
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        when {
            drawable != null -> Image(painterResource(drawable), contentDescription = null, modifier = Modifier.fillMaxSize())
            art.emoji != null -> Text(art.emoji!!, fontSize = (size.value * 0.62f).sp, textAlign = TextAlign.Center)
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(FinneySand)
                    .border(StrokeRegular, FinneyInk, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                OutlinedText(label.take(1), style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

/** Подпись категории: значок и слово на кремовом. Цвет категорию не несёт — см. ui/components/Categories.kt. */
@Composable
internal fun CategoryChip(category: Category, modifier: Modifier = Modifier) {
    val text = categoryWord(category)
    val color = FinneyCream
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .border(2.dp, FinneyInk, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        FinneyIcon(categoryIcon(category), size = 14.dp)
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = FinneyInk)
    }
}

/**
 * Монета с номиналом: ободок с насечкой, блик и цифра. Отличается от монеты
 * игровой валюты: на кассе важен номинал, а не значок финки. Номинал подписан
 * цифрой, цвет — только помощь глазу. Плоский кружок с цифрой на плейтесте 20.10
 * на монету не походил, поэтому края и блик.
 */
@Composable
internal fun DenominationCoin(
    value: Int,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    faded: Boolean = false,
) {
    val fill = when {
        faded -> FinneySand
        value == 1 -> FinneyYellow
        value == 2 -> FinneyBlue
        value == 5 -> FinneyPeach
        else -> FinneyGreen
    }
    val ink = if (faded) FinneyInk.copy(alpha = 0.35f) else FinneyInk
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = "монета $value" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val c = center
            val edge = lerp(fill, FinneyInk, 0.22f)
            // Ободок с насечкой, как гурт у настоящей монеты. Насечек столько же при любом
            // размере, поэтому мелкая сдача в чеке и крупная монета в ящике — одна монета.
            drawCircle(edge, r * 0.97f, c)
            drawCircle(ink, r * 0.97f, c, style = Stroke(1.5.dp.toPx()))
            val notch = lerp(fill, FinneyInk, 0.5f)
            val notches = 36
            for (i in 0 until notches) {
                val a = (2 * PI * i / notches).toFloat()
                val dx = cos(a)
                val dy = sin(a)
                drawLine(
                    notch,
                    Offset(c.x + r * 0.84f * dx, c.y + r * 0.84f * dy),
                    Offset(c.x + r * 0.93f * dx, c.y + r * 0.93f * dy),
                    strokeWidth = (r * 0.05f).coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                )
            }
            // Лицо монеты и тонкий ободок внутри.
            drawCircle(fill, r * 0.80f, c)
            drawCircle(ink, r * 0.80f, c, style = Stroke(1.5.dp.toPx()))
            drawCircle(edge, r * 0.68f, c, style = Stroke(1.2.dp.toPx()))
            // Блик сверху слева — монета выпуклая, а не плоская.
            if (!faded) drawArc(
                Color.White.copy(alpha = 0.7f),
                startAngle = 200f, sweepAngle = 60f, useCenter = false,
                topLeft = Offset(c.x - r * 0.58f, c.y - r * 0.58f),
                size = Size(r * 1.16f, r * 1.16f),
                style = Stroke(r * 0.09f, cap = StrokeCap.Round),
            )
        }
        OutlinedText(
            value.toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = (size.value * 0.40f).sp),
            fill = if (faded) FinneySand else FinneyCream,
            outline = ink,
        )
    }
}

/** Заметка в рамке: подсказка, ошибка, «верно!». [color] — фон, смысл несёт текст. */
@Composable
internal fun Note(text: String, modifier: Modifier = Modifier, color: Color = FinneyYellow) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = FinneyInk,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .border(2.dp, FinneyInk, RoundedCornerShape(16.dp))
            .padding(12.dp),
    )
}

/** Кремовая карточка с рамкой: ячейка полки, строка траты, панель дня. */
@Composable
internal fun GameCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusCard))
            .background(if (selected) FinneyYellow else Color.White)
            .border(if (selected) StrokeBold else StrokeRegular, FinneyInk, RoundedCornerShape(RadiusCard))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** Число с кнопками «−» и «+» — тот же [HoldStepper], что в плане и копилке. */
@Composable
internal fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    what: String,
    modifier: Modifier = Modifier,
) {
    HoldStepper(
        onMinus = onMinus,
        onPlus = onPlus,
        minusEnabled = minusEnabled,
        plusEnabled = plusEnabled,
        minusDescription = "Меньше: $what",
        plusDescription = "Больше: $what",
        modifier = modifier,
    ) { bump ->
        // Место под число — по самому широкому двузначному: «9» → «10» больше не
        // сдвигает кнопку «−» из-под пальца (плейтест 29.09, лавка: «жмётся через раз»).
        StableOutlinedText(
            value,
            widest = "8".repeat(maxOf(2, value.length)),
            style = MaterialTheme.typography.headlineMedium,
            modifier = bump.padding(horizontal = 4.dp),
        )
    }
}

/** Число со словом в нужном падеже: 1 стакан, 2 стакана, 5 стаканов, 11 стаканов. */
internal fun plural(n: Int, one: String, few: String, many: String): String {
    val word = when {
        n % 100 in 11..14 -> many
        n % 10 == 1 -> one
        n % 10 in 2..4 -> few
        else -> many
    }
    return "$n $word"
}

internal fun cupCount(n: Int) = plural(n, "стакан", "стакана", "стаканов")

/** Строка «подпись — число» для итогов и расчётов. */
@Composable
internal fun SumRow(label: String, value: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = FinneyInk,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (strong) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = FinneyInk,
        )
    }
}

/**
 * Ошибка ввода — не исход задания: ребёнок исправляет и пробует снова.
 * Тексты короткие, в каждом есть следующий шаг (ТЗ п. 2.5.9).
 */
internal fun inputErrorText(error: TaskInputError): String = when (error) {
    is TaskInputError.OverLimit -> "Не хватает ${error.shortage} монет. Убери что-нибудь и попробуй снова."
    is TaskInputError.NotFullyDistributed ->
        if (error.remaining > 0) "Осталось разложить ${error.remaining}." else "Разложено на ${-error.remaining} больше, чем есть."
    is TaskInputError.NotAllSorted -> "Разложи все вещи — осталось ${error.missing}."
    is TaskInputError.SurpriseNotCovered -> "Пока не хватает ${error.shortage}. Отложи на потом ещё что-нибудь."
    is TaskInputError.NeedsNotPlanned -> "Нужное убрать из плана нельзя — без него питомцу будет плохо."
    is TaskInputError.CoinsNotInWallet -> "В кошельке нет таких монет."
    is TaskInputError.DayOverloaded -> "В этот день столько не успеть."
    is TaskInputError.ChoreTooOften -> "Это дело дают только ${error.max} раза в неделю."
    is TaskInputError.ChoreNotToday -> "В этот день это дело не дают. Выбери другой день."
    else -> "Так не получится. Попробуй по-другому."
}

/** Стрелка вниз, которая подпрыгивает: «тяни сюда». */
@Composable
internal fun BouncingArrow(modifier: Modifier = Modifier, description: String = "Смахни вещь вниз, в корзину") {
    val bounceAnim by rememberInfiniteTransition(label = "arrow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "bounce",
    )
    // Без анимаций — на месте, а не съехавшей на крайнюю точку прыжка.
    val bounce = if (LocalAnimations.current) bounceAnim else 0f
    OutlinedText(
        "↓",
        style = MaterialTheme.typography.headlineLarge,
        modifier = modifier
            .graphicsLayer { translationY = bounce * 10.dp.toPx() }
            .semantics { contentDescription = description },
    )
}
