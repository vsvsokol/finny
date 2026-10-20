package ru.finney.pet.ui.tasks.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.finney.pet.domain.model.BasketRule
import ru.finney.pet.domain.model.BasketTask
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.model.ShelfItem
import ru.finney.pet.domain.tasks.TaskEngines
import ru.finney.pet.domain.tasks.TaskInput
import ru.finney.pet.domain.tasks.TaskInputError
import ru.finney.pet.ui.components.StableOutlinedText
import ru.finney.pet.ui.components.CheckBadge
import ru.finney.pet.ui.components.Coin
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.SpeechBubble
import ru.finney.pet.ui.components.Tail
import ru.finney.pet.ui.components.SpendBar
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIconButton
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.theme.FinneyGreenDark
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyYellow

// «Список покупок», как магазин в Pou и Tom, но с тележкой и списком. Товары
// стоят на полках с ценниками, нажатие кладёт в тележку. Касса не пропускает
// перебор — тот же отказ, что в магазине игры: «не хватает N». Что убрать,
// ребёнок решает сам, прямо на кассе.

private val Wood = Color(0xFFA0715A)

@Composable
internal fun ShoppingGame(
    task: BasketTask,
    character: PetCharacter,
    inputError: TaskInputError?,
    onInputSeen: () -> Unit,
    onClose: () -> Unit,
    onSubmit: (TaskInput) -> Unit,
) {
    var cart by remember { mutableStateOf(task.preloaded.toSet()) }
    var atCheckout by remember { mutableStateOf(false) }
    val total = task.shelf.filter { it.id in cart }.sumOf { it.price }

    // Касса не приняла — ребёнок остаётся на кассе и видит, сколько не хватает.
    LaunchedEffect(inputError) { if (inputError is TaskInputError.OverLimit) atCheckout = true }

    GameScene(backdrop = Backdrop.SHOP, onClose = onClose, money = task.limit) {
        if (atCheckout) {
            Checkout(
                task = task,
                cart = cart,
                total = total,
                shortage = (inputError as? TaskInputError.OverLimit)?.shortage,
                character = character,
                onRemove = { cart = cart - it; onInputSeen() },
                onBack = { atCheckout = false; onInputSeen() },
                onPay = { onSubmit(TaskInput.Basket(cart)) },
            )
        } else {
            Shelves(
                task = task,
                cart = cart,
                total = total,
                character = character,
                onToggle = { id -> cart = if (id in cart) cart - id else cart + id },
                onCheckout = { onSubmit(TaskInput.Basket(cart)) },
            )
        }
    }
}

@Composable
private fun Shelves(
    task: BasketTask,
    cart: Set<String>,
    total: Int,
    character: PetCharacter,
    onToggle: (String) -> Unit,
    onCheckout: () -> Unit,
) {
    val listRules = task.rules.filter { it.label != null }
    SceneBody(bottom = { CartBar(total = total, limit = task.limit, enabled = cart.isNotEmpty(), onCheckout = onCheckout) }) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (listRules.isNotEmpty()) ShoppingList(task, listRules, cart, Modifier.weight(1f).align(Alignment.Top))
            // Реплика над питомцем, хвостиком к нему. Сбоку от списка она уезжала
            // под питомца и показывала хвостиком в пустоту.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val hint = if (task.shelf.any { it.qty > 1 }) "Где больше за монету?" else "Сначала — по списку!"
                SpeechBubble(hint, Tail.DOWN, Modifier.widthIn(max = 130.dp), tailAt = 0.3f, style = MaterialTheme.typography.bodyMedium)
                ScenePet(character, 180.dp, Modifier.width(180.dp))
            }
        }

        // Полки стоят внизу, на полу магазина, во всю ширину экрана, а товары
        // делят полку поровну. С шириной числом они жались к середине, а под
        // ними оставалась пустая полоса пола.
        Spacer(Modifier.weight(1f))
        task.shelf.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                row.forEach { item -> Goods(item, inCart = item.id in cart, Modifier.weight(1f)) { onToggle(item.id) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Shelf()
        }
    }
}

/**
 * Листок со списком: галочка ставится сама, когда правило выполнено. Отметка —
 * зелёный «✓», а не перечёркнутая строка с крестиком: на плейтесте крест
 * и зачёркивание читали как «ошибка в чеке», а не «уже взял».
 */
@Composable
private fun ShoppingList(task: BasketTask, rules: List<BasketRule>, cart: Set<String>, modifier: Modifier) {
    Column(
        modifier = modifier
            // Отступ слева — под наклон: повёрнутый листок выносит левый верхний угол
            // за край прокрутки сцены, и она срезала рамку с заголовком.
            .padding(start = 8.dp, top = 4.dp, end = 8.dp)
            .rotate(-3f)
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
            .background(Color.White)
            .border(3.dp, FinneyInk, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedText("Список", style = MaterialTheme.typography.titleLarge)
        task.goalText?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = FinneyInk) }
        rules.forEach { rule ->
            val done = TaskEngines.ruleMet(task, rule, cart)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics(mergeDescendants = true) { stateDescription = if (done) "взято" else "ещё нет" },
            ) {
                if (done) {
                    CheckBadge(size = 22.dp)
                } else {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White).border(2.dp, FinneyInk, CircleShape))
                }
                Text(rule.label!!, style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
            }
        }
    }
}

/** Товар на полке: рисунок и ценник «монетка · 2 шт · 10». В тележке — жёлтая подсветка и галочка. */
@Composable
private fun Goods(item: ShelfItem, inCart: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (inCart) FinneyYellow.copy(alpha = 0.6f) else Color.Transparent)
            .toggleable(value = inCart, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(4.dp),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            if (item.qty > 1) {
                // Упаковка — несколько штук рядом: сразу видно, что их больше.
                Row(horizontalArrangement = Arrangement.spacedBy((-18).dp)) {
                    repeat(minOf(item.qty, 3)) { ItemPicture(item, item.label, 48.dp) }
                }
            } else {
                ItemPicture(item, item.label, 68.dp)
            }
            if (inCart) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(FinneyGreen).border(2.dp, FinneyInk, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("✓", style = MaterialTheme.typography.labelMedium, color = FinneyInk) }
            }
        }
        Text(item.label, style = MaterialTheme.typography.labelMedium, color = FinneyInk, textAlign = TextAlign.Center)
        item.promo?.let { OutlinedText(it, style = MaterialTheme.typography.labelLarge) }
        if (item.qty > 1) Text("${item.qty} шт. в наборе", style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
        PriceTag(item.price.toString())
    }
}

@Composable
internal fun PriceTag(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(2.dp, FinneyInk, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = FinneyInk)
        Coin(size = 20.dp)
    }
}

/** Полка — деревянная доска с обводкой во всю ширину. */
@Composable
private fun Shelf() {
    Box(
        Modifier
            .fillMaxWidth()
            .bleed()
            .height(14.dp)
            .background(Wood)
            .border(3.dp, FinneyInk),
    )
}

/** Тележка: сколько набрано, сколько останется, и кнопка на кассу. */
@Composable
private fun CartBar(total: Int, limit: Int, enabled: Boolean, onCheckout: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FinneyCream)
            .border(4.dp, FinneyInk, RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Кошелёк-полоска: сколько в тележке из скольких — числами и шкалой, без слов.
        // Перебор — «!» и минус, не только цветом (ТЗ п. 3.6).
        Column(
            Modifier.weight(1f).semantics(mergeDescendants = true) {
                contentDescription = if (total <= limit) "В корзине $total из $limit" else "В корзине $total, не хватит ${total - limit}"
            },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Coin(size = 24.dp)
                OutlinedText("$total / $limit", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (total > limit) OutlinedText("! −${total - limit}", style = MaterialTheme.typography.titleLarge, fill = FinneyPeach)
            }
            Meter(total.toFloat() / limit, Modifier.height(14.dp), color = if (total <= limit) FinneyYellow else FinneyPeach)
        }
        // «На кассу» — значком тележки.
        FinneyIconButton(onClick = onCheckout, contentDescription = "На кассу", size = 64.dp, enabled = enabled) {
            FinneyIcon(FinneyIcons.Cart, size = 32.dp)
        }
    }
}

/** Касса: чек, «не хватает N», кнопка «−» у каждой строки. */
@Composable
private fun Checkout(
    task: BasketTask,
    cart: Set<String>,
    total: Int,
    shortage: Int?,
    character: PetCharacter,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
    onPay: () -> Unit,
) {
    SceneBody(
        bottom = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Назад к полкам — стрелкой: слова «Вернуться к полкам» были длиннее кнопки оплаты.
                FinneyIconButton(onClick = onBack, contentDescription = "Вернуться к полкам", size = 64.dp, sound = Sfx.Back) {
                    OutlinedText("←", style = MaterialTheme.typography.headlineMedium)
                }
                FinneyButton(text = "Оплатить", onClick = onPay, enabled = cart.isNotEmpty(), modifier = Modifier.weight(1f))
            }
        },
    ) {
        ScenePanel(title = "Касса", modifier = Modifier.fillMaxWidth()) {
            if (shortage != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FinneyPeach)
                        .border(3.dp, FinneyInk, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                ) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(FinneyCream).border(2.dp, FinneyInk, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("!", style = MaterialTheme.typography.titleMedium, color = FinneyInk) }
                    Text("Не хватает $shortage монет", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
                }
            }
            task.shelf.filter { it.id in cart }.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Товар — картинкой; название осталось для TalkBack.
                    ItemPicture(item, item.label, 40.dp)
                    Spacer(Modifier.weight(1f))
                    OutlinedText(item.price.toString(), style = MaterialTheme.typography.titleLarge)
                    // 48 dp — минимум ТЗ п. 3.6 для всего, на что нажимают.
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(FinneyPeach)
                            .border(3.dp, FinneyInk, CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "Убрать «${item.label}»") { onRemove(item.id) },
                        contentAlignment = Alignment.Center,
                    ) { Text("−", style = MaterialTheme.typography.titleLarge, color = FinneyInk) }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Итого $total, в кошельке ${task.limit}" },
            ) {
                Coin(size = 24.dp)
                StableOutlinedText("$total / ${task.limit}", widest = "${task.limit}0 / ${task.limit}", style = MaterialTheme.typography.titleLarge)
                SpendBar(total, task.limit, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.weight(1f))
        // Реплика «Что-то подождёт… / Всё по списку? Платим!» повторяла «Не хватает N» и кнопку «Оплатить».
        // Не хватает — «?», пока ребёнок не уберёт лишнее.
        val pet = rememberPetReactions()
        LaunchedEffect(shortage) { if (shortage != null) pet.oops() else pet.calm() }
        PetAtRight(character, 150.dp, reactions = pet)
    }
}
