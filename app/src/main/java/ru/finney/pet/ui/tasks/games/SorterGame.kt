package ru.finney.pet.ui.tasks.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.model.SortItem
import ru.finney.pet.domain.model.SorterTask
import ru.finney.pet.domain.tasks.TaskInput
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.categoryIcon
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyPink
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx

// «Конвейер», как «Food Drop» в Pou, но без спешки: лента ждёт, пока вещь не
// смахнут вниз, в корзину «Нужное» или «Хочется». Кто не умеет смахивать,
// нажимает на корзину — это то же самое.
//
// В оценку уходит первый выбор. Ошибся — Финни объясняет одной фразой и
// предлагает переложить: ошибка не штраф, а подсказка.

@Composable
internal fun SorterGame(
    task: SorterTask,
    character: PetCharacter,
    money: Int,
    onClose: () -> Unit,
    onSubmit: (TaskInput) -> Unit,
) {
    val answers = remember { mutableStateMapOf<String, Category>() }
    var index by remember { mutableIntStateOf(0) }
    var mistake by remember { mutableStateOf<SortItem?>(null) }
    val bins = remember { mutableStateMapOf<Category, Rect>() }
    val item = task.items.getOrNull(index)
    val sounds = LocalSounds.current

    fun finishIfLast() {
        if (index == task.items.size) onSubmit(TaskInput.Sorting(answers.toMap()))
    }

    fun choose(category: Category) {
        val current = item ?: return
        if (mistake != null) return
        answers.putIfAbsent(current.id, category)
        if (category != current.category) {
            sounds.play(Sfx.Wrong)
            mistake = current
        } else {
            sounds.play(Sfx.Correct)
            index++
            finishIfLast()
        }
    }

    fun moveOver() {
        mistake = null
        index++
        finishIfLast()
    }

    GameScene(backdrop = Backdrop.DOTS, onClose = onClose, money = money) {
        Column(modifier = Modifier.fillMaxSize().padding(top = HudHeight)) {
            Progress(task.items, index, answers)
            Text(
                item?.let { "Куда положить: ${it.label}? Перетащи в корзину внизу или нажми корзину." }.orEmpty(),
                style = MaterialTheme.typography.bodyLarge, color = FinneyInk,
            )
            Spacer(Modifier.height(12.dp))

            // Лента выше всего остального: вещь, которую тянут вниз, рисуется поверх питомца и корзин.
            Box(Modifier.zIndex(1f).bleed()) {
                Belt(
                    current = item?.takeIf { mistake == null },
                    upcoming = task.items.drop(index + 1).take(3),
                    showHint = index == 0 && mistake == null,
                    bins = bins,
                    onDrop = ::choose,
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val wrong = mistake
                if (wrong == null) {
                    // Вопрос «"Яблоко" — это что?» убран: вещь на ленте и две корзины понятны без слов.
                    if (item != null) ScenePet(character, 96.dp, Modifier.width(96.dp))
                } else {
                    MistakePanel(wrong, onMove = ::moveOver)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                for (category in listOf(Category.NEEDS, Category.WANTS)) {
                    val misplaced = mistake?.takeIf { answers[it.id] == category }
                    val inside = task.items.take(index).filter { it.category == category } + listOfNotNull(misplaced)
                    Bin(
                        category = category,
                        items = inside.takeLast(3),
                        misplaced = misplaced,
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { bins[category] = it.boundsInRoot() },
                        onClick = { choose(category) },
                    )
                }
            }
        }
    }
}

/**
 * Полоски прогресса: зелёная — верно с первого раза, персиковая и перечёркнутая —
 * с подсказкой. Штрих — чтобы ошибку было видно не только цветом (ТЗ п. 3.6).
 * Строка «3 из 8 · верно 2» убрана: полоски говорят то же; число — для TalkBack.
 */
@Composable
private fun Progress(items: List<SortItem>, done: Int, answers: Map<String, Category>) {
    val right = items.take(done).count { answers[it.id] == it.category }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$done из ${items.size}, верно $right" },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, it ->
                val color = when {
                    i >= done -> Color(0xFFFFF7EA)
                    answers[it.id] == it.category -> FinneyGreen
                    else -> FinneyPeach
                }
                val wrong = i < done && answers[it.id] != it.category
                Box(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(color)
                        .drawBehind {
                            if (wrong) drawLine(FinneyInk, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 2.dp.toPx())
                        }
                        .border(2.dp, FinneyInk, RoundedCornerShape(7.dp)),
                )
            }
        }
    }
}

/**
 * Лента: фиолетовые полосы, заклёпки снизу, на ней текущая вещь и следующие.
 * Текущую тянут пальцем; отпустил над корзиной — выбор сделан, мимо — вещь вернётся на ленту.
 */
@Composable
private fun Belt(
    current: SortItem?,
    upcoming: List<SortItem>,
    showHint: Boolean,
    bins: Map<Category, Rect>,
    onDrop: (Category) -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(96.dp)) {
        Canvas(Modifier.fillMaxWidth().height(84.dp)) {
            val stripe = 18.dp.toPx()
            var x = 0f
            var odd = false
            while (x < size.width) {
                drawRect(if (odd) Color(0xFF7C6AC8) else Color(0xFF8C7BD8), Offset(x, 0f), size.copy(width = stripe))
                x += stripe
                odd = !odd
            }
            val border = 5.dp.toPx()
            drawRect(FinneyInk, size = size.copy(height = border))
            drawRect(FinneyInk, Offset(0f, size.height - border), size.copy(height = border))
        }
        // Заклёпки под лентой — чтобы было видно, что это механизм, а не полка.
        Canvas(Modifier.fillMaxWidth().height(12.dp).align(Alignment.BottomStart)) {
            val step = 28.dp.toPx()
            var x = 9.dp.toPx()
            while (x < size.width) {
                drawCircle(FinneyInk, 5.dp.toPx(), Offset(x, size.height / 2))
                x += step
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(84.dp).padding(horizontal = SceneEdge + 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (current != null) {
                key(current.id) { DraggableItem(current, bins, onDrop) }
            } else {
                Spacer(Modifier.size(64.dp))
            }
            upcoming.forEach { ItemPicture(it, it.label, 52.dp) }
        }
        // Подсказка жестом, а не словами: прыгающая стрелка вниз, к корзинам.
        if (showHint) BouncingArrow(Modifier.align(Alignment.BottomStart).offset(x = SceneEdge + 20.dp, y = 30.dp))
    }
}

@Composable
private fun DraggableItem(item: SortItem, bins: Map<Category, Rect>, onDrop: (Category) -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var bounds by remember { mutableStateOf(Rect.Zero) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .size(64.dp)
            .dashedOutline()
            .semantics { contentDescription = "${item.label}. Смахни в корзину или нажми на неё" }
            .pointerInput(item.id) {
                detectDragGestures(
                    onDragEnd = {
                        // Центр вещи — там, где её отпустили. Попала в корзину — выбор, мимо — назад на ленту.
                        val centre = bounds.center
                        val hit = bins.entries.firstOrNull { (_, rect) -> rect.inflate(24f).contains(centre) }?.key
                        scope.launch {
                            offset.animateTo(Offset.Zero, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                        if (hit != null) onDrop(hit)
                    },
                    onDragCancel = { scope.launch { offset.animateTo(Offset.Zero) } },
                ) { change, drag ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + drag) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        ItemPicture(item, item.label, 56.dp)
    }
}

/** Розовая пунктирная рамка вокруг вещи, которую сейчас сортируют или которая лежит не там. */
private fun Modifier.dashedOutline(): Modifier = drawBehind {
    drawRoundRect(
        color = FinneyPink,
        cornerRadius = CornerRadius(14.dp.toPx()),
        style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
    )
}

/** Панель подсказки после ошибки: что это за вещь и почему. Кнопка перекладывает её в нужную корзину. */
@Composable
private fun MistakePanel(item: SortItem, onMove: () -> Unit) {
    ScenePanel(title = null, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ItemPicture(item, item.label, 56.dp)
            Column {
                Text(
                    text = "${item.label} — это «${categoryWord(item.category)}».",
                    style = MaterialTheme.typography.titleMedium,
                    color = FinneyInk,
                )
                Text(item.why, style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
            }
        }
        FinneyButton(text = "Переложить", onClick = onMove)
    }
}

internal fun categoryWord(category: Category) = if (category == Category.NEEDS) "нужное" else "хочется"

/**
 * Корзина — она же кнопка. Сверху видно, что в ней уже лежит; вещь, попавшая не туда,
 * обведена, пока её не переложат. Подпись и значок, а не только цвет (ТЗ п. 3.6).
 */
@Composable
private fun Bin(
    category: Category,
    items: List<SortItem>,
    misplaced: SortItem?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val needs = category == Category.NEEDS
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp), modifier = Modifier.height(40.dp)) {
            items.forEach {
                ItemPicture(it, it.label, 40.dp, if (it == misplaced) Modifier.dashedOutline() else Modifier)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .clip(shape)
                // Корзины различаются значком и словом, а не цветом: зелёная «нужное»
                // рядом с розовой «хочется» подсказывала, какой ответ «правильный».
                .background(FinneyCream)
                .border(4.dp, FinneyInk, shape)
                .clickable(role = Role.Button, onClickLabel = "Положить в «${categoryWord(category)}»", onClick = onClick)
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(FinneyCream).border(3.dp, FinneyInk, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                FinneyIcon(categoryIcon(category), size = 24.dp)
            }
            OutlinedText(
                if (needs) "Нужное" else "Хочется",
                style = MaterialTheme.typography.titleLarge,
                fill = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}
