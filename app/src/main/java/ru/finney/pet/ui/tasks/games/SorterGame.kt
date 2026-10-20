package ru.finney.pet.ui.tasks.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import ru.finney.pet.R
import ru.finney.pet.ui.components.drawPointingHand
import ru.finney.pet.ui.motion.LocalAnimations
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import ru.finney.pet.ui.pet.rememberPetAnimation

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
    val pet = rememberPetAnimation()
    // Показ рукой — один раз за попытку, до первого хода. Без анимаций не показываем:
    // что делать, говорит строка над лентой.
    var demo by rememberSaveable { mutableStateOf(true) }
    val showDemo = demo && index == 0 && mistake == null && LocalAnimations.current
    var belt by remember { mutableStateOf(Rect.Zero) }

    fun finishIfLast() {
        if (index == task.items.size) onSubmit(TaskInput.Sorting(answers.toMap()))
    }

    fun choose(category: Category) {
        val current = item ?: return
        if (mistake != null) return
        answers.putIfAbsent(current.id, category)
        if (category != current.category) {
            sounds.play(Sfx.Wrong)
            // Не грустит (ТЗ п. 2.5.12), а тянется к корзине, куда вещь на самом деле.
            pet.lookAt(if (current.category == Category.NEEDS) -1f else 1f)
            mistake = current
        } else {
            sounds.play(Sfx.Correct)
            // Питомец радуется каждому верному ходу — видно, что ход засчитан.
            pet.lookAt(0f)
            pet.playJoy()
            index++
            finishIfLast()
        }
    }

    fun moveOver() {
        pet.lookAt(0f)
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
            Box(Modifier.zIndex(1f).bleed().onGloballyPositioned { belt = it.boundsInRoot() }) {
                Belt(
                    current = item?.takeIf { mistake == null },
                    upcoming = task.items.drop(index + 1).take(3),
                    bins = bins,
                    onDrop = ::choose,
                    // Пока вещь тянут, питомец тянется следом — к той корзине, над которой она.
                    onLean = pet::lookAt,
                )
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val wrong = mistake
                // На низком экране места хватает только панели — питомца при ошибке тогда не видно.
                val roomForPet = maxHeight >= 280.dp
                if (wrong == null) {
                    // Вопрос «"Яблоко" — это что?» убран: вещь на ленте и две корзины понятны без слов.
                    // Стрелки «↙ Нужное / Хочется ↘» по краям убраны (плейтест 20.10): что вещь
                    // надо тащить, показывает рука в начале игры, а питомец стал крупнее.
                    if (item != null) ScenePet(character, 180.dp, Modifier.width(180.dp), animation = pet)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (roomForPet) ScenePet(character, 100.dp, Modifier.width(100.dp), animation = pet)
                        MistakePanel(wrong, onMove = ::moveOver)
                    }
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
        val needsBin = bins[Category.NEEDS]
        if (showDemo && needsBin != null && belt != Rect.Zero) {
            // Первая вещь на ленте: отступ ряда плюс половина плитки 64 dp, середина ленты 84 dp.
            val from = with(LocalDensity.current) { Offset(belt.left + (SceneEdge * 2 + 44.dp).toPx(), belt.top + 42.dp.toPx()) }
            DragDemo(
                from = from,
                to = needsBin,
                onDone = { demo = false },
                modifier = Modifier.matchParentSize().zIndex(2f),
            )
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
    bins: Map<Category, Rect>,
    onDrop: (Category) -> Unit,
    onLean: (Float) -> Unit,
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
                key(current.id) { DraggableItem(current, bins, onDrop, onLean) }
            } else {
                Spacer(Modifier.size(64.dp))
            }
            upcoming.forEach { ItemPicture(it, it.label, 52.dp) }
        }
    }
}

@Composable
private fun DraggableItem(item: SortItem, bins: Map<Category, Rect>, onDrop: (Category) -> Unit, onLean: (Float) -> Unit) {
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
                        onLean(0f)
                        scope.launch {
                            offset.animateTo(Offset.Zero, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                        if (hit != null) onDrop(hit)
                    },
                    onDragCancel = {
                        onLean(0f)
                        scope.launch { offset.animateTo(Offset.Zero) }
                    },
                ) { change, drag ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + drag) }
                    onLean(leanTowards(bounds.center.x, bins))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        ItemPicture(item, item.label, 56.dp)
    }
}

/**
 * Куда тянуться питомцу: −1 — к «Нужному» слева, 1 — к «Хочется» справа. Середина — между
 * корзинами, край — внешний край корзины. Корзины ещё не измерены — прямо.
 */
private fun leanTowards(x: Float, bins: Map<Category, Rect>): Float {
    val left = bins[Category.NEEDS] ?: return 0f
    val right = bins[Category.WANTS] ?: return 0f
    val middle = (left.right + right.left) / 2
    val half = (right.right - left.left) / 2
    return if (half <= 0f) 0f else ((x - middle) / half).coerceIn(-1f, 1f)
}

/**
 * Показ жестом в начале игры: экран темнеет, кроме корзины «Нужное», и рука переносит
 * туда яблоко с ленты — два раза, потом гаснет. Касание закрывает раньше. Это только
 * показ: в ответы не идёт, вещи на ленте ребёнок тащит сам. На плейтесте 20.10 стрелки
 * у корзин не объясняли, что вещь надо тащить. Для TalkBack показа нет — там строка над лентой.
 */
@Composable
private fun DragDemo(from: Offset, to: Rect, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        repeat(2) {
            t.snapTo(0f)
            t.animateTo(1f, tween(2400, easing = LinearEasing))
        }
        onDone()
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val apple = painterResource(R.drawable.item_food_apple)
    Canvas(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) { detectTapGestures { onDone() } }
            .clearAndSetSemantics {},
    ) {
        val v = t.value
        // Нажал на ленте (0–0.2), несёт (0.2–0.75), отпустил над корзиной, яблоко тает.
        val carry = FastOutSlowInEasing.transform(((v - 0.2f) / 0.55f).coerceIn(0f, 1f))
        val start = from - origin
        val bin = to.translate(-origin)
        val at = lerp(start, bin.center, carry)
        val press = when {
            v < 0.2f -> v / 0.2f
            v < 0.75f -> 1f
            else -> 0f
        }
        val fade = if (v < 0.8f) 1f else 1f - (v - 0.8f) / 0.2f

        // Затемнение до краёв экрана, а не сцены: сцена с полями, рисуем с запасом.
        val far = 4000f
        val scrim = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(-far, -far, size.width + far, size.height + far))
            addRoundRect(RoundRect(bin.inflate(6.dp.toPx()), CornerRadius(24.dp.toPx())))
        }
        drawPath(scrim, FinneyInk.copy(alpha = 0.45f))

        val s = 56.dp.toPx()
        translate(at.x - s / 2, at.y - s / 2) { with(apple) { draw(Size(s, s), alpha = fade) } }
        if (fade > 0.5f) drawPointingHand(at, up = false, press = press)
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
