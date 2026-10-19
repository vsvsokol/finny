package ru.finney.pet.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.finney.pet.domain.game.PurchasePreview
import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.PetStats
import ru.finney.pet.domain.model.ShopItem
import ru.finney.pet.domain.model.StatEffect
import ru.finney.pet.ui.components.pointHere
import ru.finney.pet.ui.components.CheckBadge
import ru.finney.pet.ui.components.CoinAmount
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyCard
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.FinneyPanel
import ru.finney.pet.ui.components.FinneyQuietButton
import ru.finney.pet.ui.components.WarningBadge
import ru.finney.pet.ui.components.MoodFace
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.components.fadingScroll
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.FinneyBlue
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.theme.RadiusCard
import ru.finney.pet.ui.theme.StrokeThin

/**
 * Что выбирают: предмет и что с ним станет, если купить.
 *
 * Всё, кроме флага выбора, приходит из домена готовым. Считать здесь нечего:
 * цена в предмете, влияние в предпросмотре, нехватка тоже.
 */
data class CareOption(
    val item: ShopItem,
    val preview: PurchasePreview,
    val isSelected: Boolean,
    /** Сколько раз ещё хватит того, что уже есть (мыло, паста); null — товар без запаса. */
    val left: Int? = null,
    /** Сколько взять из радостей, чтобы покупка не вышла за план (Game.wantsToNeeds); null — не нужно. */
    val fromWants: Int? = null,
)

/**
 * Почему покупать сейчас нельзя и куда идти, чтобы стало можно.
 *
 * Отдельно от нехватки денег: та относится к одной строке, а это запрет
 * на всю панель. Единственный такой случай сегодня — не подтверждён план
 * расходов (`Rejection.PlanNotConfirmed`).
 */
data class CareBlock(
    val reason: String,
    val actionLabel: String,
    val onAction: () -> Unit,
)

/**
 * Выбор предмета для ухода: чем покормить, чем помыть.
 *
 * Требование ТЗ п. 2.5.6 целиком: до покупки видны цена, категория и то, как
 * предмет повлияет на питомца; покупка подтверждается отдельным нажатием;
 * если купить нельзя — сказано, почему именно и что сделать.
 *
 * Два шага, а не покупка по нажатию на строку, — это и есть подтверждение:
 * ребёнок сначала смотрит, что изменится, и только потом соглашается.
 *
 * [block] — покупка запрещена целиком. Тогда вместо «Купить» стоит кнопка,
 * которая ведёт туда, где запрет снимается: тупика с неработающей кнопкой
 * у ребёнка быть не должно.
 *
 * [confirmLabel] — что случится по нажатию. На главном это «Купить и покормить»:
 * дальше идёт игра, а деньги уходят, когда еда попала в рот.
 *
 * [allowShortage] — кнопку можно нажать и при нехватке денег. Так в магазине:
 * домен откажет, и экран покажет, чего не хватает и что делать (шаг 7
 * Приложения А — «попытка покупки при нехватке средств»).
 *
 * [header] — что стоит в панели над товарами: в магазине это полоса категории.
 * [withConfirm] false — кнопки в панели нет, её ставит экран: в магазине панелей
 * три, а кнопка «Купить» одна, внизу.
 *
 * [tiles] — товары плитками по два в ряд с крупной картинкой сверху: так в магазине
 * (плейтест 28.09: «уныленько», картинка терялась в строке). На главном панель
 * открывается поверх комнаты, и там строки ниже и короче — остаётся список.
 *
 * [groupBanner] — товары разных категорий идут группами, «нужное» первым, и над каждой
 * группой её полоса: на кухне рядом с едой лежит конфета, а она — «хочется» (п. 2.5.6).
 *
 * [tilePicture] — размер картинки в плитке: в окне поверх комнаты плитки ниже, чтобы
 * кухня целиком и кнопка помещались на экран 360 × 740 dp без прокрутки.
 *
 * [onConfirmFromWants] — у выбранного нужное выходит за план ([CareOption.fromWants]):
 * вместо одной кнопки — [FromWantsOffer]. null — предложения нет.
 */
@Composable
fun CarePanel(
    title: String,
    options: List<CareOption>,
    onPick: (itemId: String) -> Unit,
    onConfirm: (itemId: String) -> Unit,
    /** null — панель без кнопки «Закрыть»: так она стоит на экране магазина. */
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    block: CareBlock? = null,
    confirmLabel: String = "Купить",
    allowShortage: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    withConfirm: Boolean = true,
    tiles: Boolean = false,
    groupBanner: (@Composable (Category) -> Unit)? = null,
    tilePicture: Dp = TilePicture,
    guide: Boolean = false,
    upcoming: ShopItem? = null,
    onConfirmFromWants: ((itemId: String) -> Unit)? = null,
    fromWantsLabel: String = "Взять и купить",
    overPlanLabel: String = "Купить сверх плана",
) {
    val selected = options.firstOrNull { it.isSelected }
    // Обучение: рука показывает сначала товар, потом «Купить». Плейтест 29.09 — не
    // дочитавший «Нажми на товар» ребёнок жал погашенную кнопку и застревал. Первым
    // зовём нужное, на которое хватает: с него уровень и начинается.
    val pointAt = if (guide && selected == null && block == null) {
        options.firstOrNull { it.item.category == Category.NEEDS && it.preview.shortage == 0 }
            ?: options.firstOrNull { it.preview.shortage == 0 }
    } else null

    FinneyPanel(title = title, onClose = onDismiss, modifier = modifier) {
        header?.invoke()
        if (options.isEmpty()) {
            Text(
                text = "Пока нечего купить",
                style = MaterialTheme.typography.bodyLarge,
                color = FinneyInk,
            )
            return@FinneyPanel
        }

        // С кнопкой внутри панели товары прокручиваются, а кнопка стоит на месте: когда на
        // кухню добавилась конфета своей группой, плитки перестали влезать, и «Купить и
        // покормить» уезжала за низ экрана — купить было нечем. Без кнопки (магазин)
        // прокручивает весь экран, и своя прокрутка здесь не нужна.
        val list = if (withConfirm) Modifier.weight(1f, fill = false).fadingScroll(rememberScrollState()) else Modifier
        Column(list, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val groups = if (groupBanner == null) {
                listOf(null to options)
            } else {
                options.groupBy { it.item.category }.entries.sortedBy { it.key != Category.NEEDS }.map { it.key to it.value }
            }
            for ((category, group) in groups) {
                if (category != null) groupBanner?.invoke(category)
                if (tiles) {
                    group.chunked(2).forEach { pair ->
                        // Высота ряда — по высокой плитке: у соседки с «не хватает» текста больше.
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { option ->
                                CareTile(
                                    option,
                                    tilePicture,
                                    onPick = { onPick(option.item.id) },
                                    modifier = Modifier.weight(1f).fillMaxHeight().pointHere(option == pointAt, corner = RadiusCard),
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    group.forEach { option ->
                        CareRow(
                            option = option,
                            onPick = { onPick(option.item.id) },
                            modifier = Modifier.pointHere(option == pointAt, corner = RadiusCard),
                        )
                    }
                }
            }
        }

        upcoming?.let { UpcomingItem(it) }
        if (!withConfirm) return@FinneyPanel
        Spacer(Modifier.padding(top = 4.dp))

        if (block != null) {
            Text(
                text = block.reason,
                style = MaterialTheme.typography.bodyLarge,
                color = FinneyInk,
            )
            FinneyButton(text = block.actionLabel, onClick = block.onAction)
            return@FinneyPanel
        }

        // Кнопка — всегда действие: «Купить», а не подсказка «Выбери, что купить»
        // внутри неё (плейтест: кнопка должна говорить, что сделает). Пока ничего
        // не выбрано, подсказка стоит над кнопкой, а сама кнопка не нажимается.
        if (selected == null) PickHint()
        // Купить можно только выбранное и только когда хватает денег. У строки
        // с нехваткой уже написано, сколько не достаёт и что делать, — кнопка
        // молча неактивной не остаётся.
        val canBuy = selected != null && (allowShortage || selected.preview.shortage == 0)
        val fromWants = selected?.fromWants?.takeIf { canBuy && onConfirmFromWants != null }
        if (selected != null && fromWants != null) {
            FromWantsOffer(
                amount = fromWants,
                takeLabel = fromWantsLabel,
                overPlanLabel = overPlanLabel,
                onTake = { onConfirmFromWants?.invoke(selected.item.id) },
                onOverPlan = { onConfirm(selected.item.id) },
                guide = guide,
            )
            return@FinneyPanel
        }
        FinneyButton(
            text = confirmLabel,
            onClick = { selected?.let { onConfirm(it.item.id) } },
            enabled = canBuy,
            modifier = Modifier.pointHere(guide && canBuy),
        )
    }
}

/** Один тизер без действия покупки: доступ откроется на указанном уровне. */
@Composable
internal fun UpcomingItem(item: ShopItem) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ItemPicture(item.id, itemFallback(item.id), 48.dp)
        Text("Скоро: ${item.label} — на уровне ${item.unlockLevel}",
            style = MaterialTheme.typography.bodyLarge, color = FinneyInk, modifier = Modifier.weight(1f))
    }
}

/**
 * «Возьми из радостей» (Game.wantsToNeeds): нужное выйдет за план, а «хочется» ещё не
 * потрачено. Предупреждение, а не ошибка — голубое «!» и словами (ТЗ п. 3.6). Главная
 * кнопка перекладывает [amount] из радостей и покупает, тихая — покупает сверх плана.
 */
@Composable
fun FromWantsOffer(
    amount: Int,
    takeLabel: String,
    overPlanLabel: String,
    onTake: () -> Unit,
    onOverPlan: () -> Unit,
    modifier: Modifier = Modifier,
    guide: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WarningBadge(size = 24.dp)
            Text(
                text = "Нужное выйдет за план на $amount. Возьми $amount из радостей — радость подождёт.",
                style = MaterialTheme.typography.bodyLarge,
                color = FinneyInk,
                modifier = Modifier.weight(1f),
            )
        }
        FinneyButton(text = takeLabel, onClick = onTake, modifier = Modifier.pointHere(guide))
        FinneyQuietButton(text = overPlanLabel, onClick = onOverPlan)
    }
}

/** «Нажми на товар, чтобы выбрать» — со стрелкой вверх, к товарам. */
@Composable
fun PickHint(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedText("↑", style = MaterialTheme.typography.titleLarge)
        Text("Нажми на товар, чтобы выбрать", style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
    }
}

/**
 * Строка предмета — картинкой, а не словами: рисунок, что он даёт («+20» со
 * значком шкалы) и цена с монеткой. Название — только для TalkBack.
 *
 * Категория видна по панели, в которой стоит предмет (в магазине у неё цветная
 * полоса со словом). Значки тетради и звезды в углу рисунка убраны: на плейтесте
 * их не понимали, они сливались с предметом и закрывали его. Выбранное — зелёным
 * и «✓», нехватка — розовым и строкой «не хватает»: ТЗ п. 3.6 запрещает цвет как
 * единственный способ что-то сообщить, а п. 2.5.6 требует, чтобы цена,
 * категория и влияние были видны до покупки.
 */
@Composable
private fun CareRow(option: CareOption, onPick: () -> Unit, modifier: Modifier = Modifier) {
    val preview = option.preview
    val notEnough = preview.shortage > 0
    val needed = option.item.category == Category.NEEDS

    // Нехватка — предупреждение, а не ошибка: голубое, и словами ниже. Выбранное —
    // жёлтое с «✓»: выбор не оценка, зелёный у нас только «получилось».
    val accent = when {
        notEnough -> FinneyBlue
        option.isSelected -> FinneyYellow
        else -> null
    }

    FinneyCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${option.item.label}, ${if (needed) "нужное" else "желаемое"}"
                selected = option.isSelected
            }
            .clickable(onClickLabel = "Выбрать: ${option.item.label}", onClick = onPick),
        accent = accent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                ItemPicture(option.item.id, itemFallback(option.item.id), size = 64.dp)
                if (option.isSelected) CheckBadge(size = 24.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EffectChip(FinneyIcons.Food, "сытость", option.item.effect.satiety, preview.statsBefore.satiety, preview.statsAfter.satiety)
                EffectChip(FinneyIcons.Bath, "чистота", option.item.effect.hygiene, preview.statsBefore.hygiene, preview.statsAfter.hygiene)
                EffectChip(null, "радость", option.item.effect.mood, preview.statsBefore.mood, preview.statsAfter.mood)
            }
            CoinAmount(amount = option.item.price)
        }
        option.item.uses?.let { UsesNote(it, option.left) }

        if (notEnough) {
            Text(
                text = "Не хватает ${preview.shortage}. Сыграй в мини-игру или заверши уровень",
                style = MaterialTheme.typography.bodyMedium,
                color = FinneyInk,
            )
        }
    }
}

/**
 * Товар плиткой: картинка во всю ширину — главное, под ней что изменится и цена.
 * Всё, что требует п. 2.5.6, на месте, как и в [CareRow]; признаки выбора и
 * нехватки те же — заливка, «✓» и слова, не только цвет (ТЗ п. 3.6).
 */
@Composable
private fun CareTile(option: CareOption, picture: Dp, onPick: () -> Unit, modifier: Modifier = Modifier) {
    val preview = option.preview
    val notEnough = preview.shortage > 0
    val needed = option.item.category == Category.NEEDS
    val accent = when {
        notEnough -> FinneyBlue
        option.isSelected -> FinneyYellow
        else -> null
    }

    FinneyCard(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = "${option.item.label}, ${if (needed) "нужное" else "желаемое"}"
                selected = option.isSelected
            }
            .clickable(onClickLabel = "Выбрать: ${option.item.label}", onClick = onPick),
        accent = accent,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ItemPicture(option.item.id, itemFallback(option.item.id), size = picture)
            if (option.isSelected) CheckBadge(Modifier.align(Alignment.TopEnd), size = 28.dp)
        }
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EffectChip(FinneyIcons.Food, "сытость", option.item.effect.satiety, preview.statsBefore.satiety, preview.statsAfter.satiety)
            EffectChip(FinneyIcons.Bath, "чистота", option.item.effect.hygiene, preview.statsBefore.hygiene, preview.statsAfter.hygiene)
            EffectChip(null, "радость", option.item.effect.mood, preview.statsBefore.mood, preview.statsAfter.mood)
            CoinAmount(amount = option.item.price)
        }
        option.item.uses?.let { UsesNote(it, option.left) }
        if (notEnough) {
            // В узкой плитке — коротко; что делать, сказано в отказе при попытке купить.
            Text(
                text = "Не хватает ${preview.shortage}",
                style = MaterialTheme.typography.bodyMedium,
                color = FinneyInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Мыло не моет сразу, а встаёт на полочку в ванной: «+50» у него — за каждый раз.
 * Без этой строки покупка выглядела бы обманом — шкала после неё не двигается.
 */
@Composable
private fun UsesNote(uses: Int, left: Int?) {
    Text(
        text = when (left) {
            null -> "На полочку в ванной: хватит на ${times(uses)}"
            0 -> "Кончилось. Покупка — на ${times(uses)}"
            else -> "Есть на ${times(left)}. Покупка — ещё на ${times(uses)}"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = FinneyInk,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Картинка товара в плитке: на 360 dp плитка ~135 dp, картинка занимает её почти целиком. */
private val TilePicture = 88.dp

/**
 * Что станет со шкалой: «+20» и значок шкалы одной плашкой. Раньше значок стоял
 * отдельно от числа, и их читали порознь; теперь «+20» и вилка — одно целое.
 * Плашка молчит, только если предмет эту шкалу не трогает вовсе. Шкала уже полная —
 * плашка остаётся со словом «полно»: раньше она пропадала, и у мыла при чистой
 * шкале не было написано ничего, будто оно бесполезно. [icon] null — радость,
 * её знак — лицо, как под шкалой на главном.
 */
@Composable
private fun EffectChip(icon: FinneyIcons?, label: String, effect: Int, before: Int, after: Int) {
    if (effect == 0 && before == after) return
    val delta = after - before
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .border(StrokeThin, FinneyInk, RoundedCornerShape(50))
            .padding(start = 10.dp, end = 8.dp)
            .clearAndSetSemantics {
                contentDescription = if (before == after) "$label: уже полная" else "$label: было $before, станет $after"
            },
    ) {
        if (delta == 0) {
            Text("полно", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
        } else OutlinedText(
            text = if (delta > 0) "+$delta" else "$delta",
            style = MaterialTheme.typography.titleLarge,
            // Направление — знаком «+» / «−», цвет один: зелёный «+» читался как похвала.
            fill = FinneyPeach,
        )
        if (icon != null) FinneyIcon(icon, size = 22.dp) else MoodFace(size = 22.dp)
    }
}

@Preview(widthDp = 360)
@Composable
private fun CarePanelPreview() {
    val apple = ShopItem("food_apple", "Яблоко", 10, Category.NEEDS, effect = StatEffect(satiety = 20))
    val bowl = ShopItem("food_bowl", "Суп", 20, Category.NEEDS, effect = StatEffect(satiety = 45))
    val stats = PetStats(satiety = 30, hygiene = 60, mood = 70, energy = 40)

    FinneyTheme {
        CarePanel(
            title = "Покормить",
            options = listOf(
                CareOption(apple, PurchasePreview(apple, stats, stats.copy(satiety = 50), 0), isSelected = true),
                CareOption(bowl, PurchasePreview(bowl, stats, stats.copy(satiety = 75), 5), isSelected = false),
            ),
            onPick = {},
            onConfirm = {},
            onDismiss = {},
            modifier = Modifier.width(360.dp),
        )
    }
}
