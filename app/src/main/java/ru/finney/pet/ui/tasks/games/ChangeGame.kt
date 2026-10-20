package ru.finney.pet.ui.tasks.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.finney.pet.domain.model.ChangeMode
import ru.finney.pet.domain.model.ChangeTask
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.tasks.TaskEngines
import ru.finney.pet.domain.tasks.TaskInput
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.components.SpeechBubble
import ru.finney.pet.ui.components.Tail
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyPink
import ru.finney.pet.ui.theme.FinneyYellow

// «Касса Финни» по макету-артефакту от 20.10. Все размеры — из него: экран макета 412 px
// в ширину, как телефон в dp, поэтому числа переносятся один к одному.
//
// Сверху — мятная стена: покупатель за прилавком, его реплика и светлый чек. Снизу —
// сиреневая зона кассы: лоток со сдачей, ящик с монетами и кнопка. Два режима: отсчитать
// сдачу из ящика (монет сколько угодно) и заплатить ровно из кошелька (монеты считаны).
//
// В оценку идёт первая попытка каждого раунда. Ошибся — говорящий вежливо говорит,
// сколько лишнего или не хватает, и ребёнок поправляет сам.

/** Больше монет на лоток не ложится: восемь — две строки, и лоток не выталкивает ящик. */
private const val TrayMax = 8

private val Wood = Color(0xFF9C6B53)
private val WoodTop = Color(0xFFC08A6C)
private val WoodDeep = Color(0xFF7A4E3C)
private val Soft = Color(0xFFEDE6FF)
private val TrayShade = Color(0xFFFBDDB4)
private val Bad = Color(0xFFFBC3B0)

/** Монета на лотке. [id] — чтобы новая падала на лоток, а убранная не дёргала соседей. */
private data class TrayCoin(val id: Int, val value: Int)

/** Как отреагировал тот, кто говорит: прыжок за верную сдачу, «нет» головой за ошибку. */
private enum class Jolt { HOP, NO }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChangeGame(task: ChangeTask, character: PetCharacter, onClose: () -> Unit, onSubmit: (TaskInput) -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    var tray by remember { mutableStateOf(emptyList<TrayCoin>()) }
    var nextId by remember { mutableIntStateOf(0) }
    var firsts by remember { mutableStateOf(emptyList<List<Int>>()) }
    var checked by remember { mutableStateOf<Int?>(null) }
    // Реплика вместо обычной, пока ребёнок не тронет лоток: «лоток полный», «положи монетки».
    var nudge by remember { mutableStateOf<String?>(null) }
    var jolt by remember { mutableStateOf<Pair<Jolt, Int>?>(null) }
    val current = task.rounds[round]
    val give = current.mode == ChangeMode.GIVE
    val solved = checked == 0
    val coins = tray.map { it.value }
    // Покупатель — любой питомец, кроме своего; в каждом раунде новый.
    val others = PetCharacter.entries.filter { it != character }
    val customer = others[round % others.size]
    val sounds = LocalSounds.current
    val pet = rememberPetReactions()

    fun react(kind: Jolt) {
        jolt = kind to ((jolt?.second ?: 0) + 1)
        if (!give) if (kind == Jolt.HOP) pet.right() else pet.oops()
    }

    fun put(value: Int) {
        if (solved) return
        if (tray.size >= TrayMax) {
            nudge = "Ой, лоток полный! Убери лишние монетки."
            return
        }
        sounds.play(Sfx.Coin)
        tray = tray + TrayCoin(nextId++, value)
        checked = null
        nudge = null
    }

    fun check() {
        if (tray.isEmpty()) {
            nudge = if (give) "Положи монетки в лоток для сдачи!" else "Положи монетки на кассу!"
            sounds.play(Sfx.Wrong)
            react(Jolt.NO)
            return
        }
        if (firsts.size == round) firsts = firsts + listOf(coins)
        checked = TaskEngines.changeDiff(current, coins)
        nudge = null
        sounds.play(if (checked == 0) Sfx.Correct else Sfx.Wrong)
        react(if (checked == 0) Jolt.HOP else Jolt.NO)
    }

    fun next() {
        if (round + 1 == task.rounds.size) {
            onSubmit(TaskInput.Coins(firsts))
            return
        }
        round++
        tray = emptyList()
        checked = null
        nudge = null
        pet.calm()
    }

    GameScene(
        backdrop = Backdrop.TILL,
        onClose = onClose,
        closeSize = RoundButtonSize,
        lead = { Progress(round, task.rounds.size, solved) },
    ) {
        val density = LocalDensity.current
        val barsTop = with(density) { WindowInsets.systemBars.getTop(this).toDp() }
        val barsBottom = with(density) { WindowInsets.systemBars.getBottom(this).toDp() }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Низ верхней половины — ровно на стыке фона: прилавок стоит на границе стены и кассы.
            val screen = maxHeight + barsTop + barsBottom + 16.dp
            val aboveDesk = (screen * TILL_SPLIT_AT - barsTop - 8.dp).coerceAtLeast(HudHeight + 260.dp)
            val line = nudge ?: if (give) customerLine(current.target, coins.sum(), checked, paid = current.paid ?: 0)
            else ownLine(current.price, coins.sum(), checked)

            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(aboveDesk)) {
                    val speaker = Modifier.align(Alignment.BottomStart).padding(bottom = 36.dp).offset(x = (-4).dp).width(190.dp)
                    Jolting(jolt, speaker) { m ->
                        if (give) Guest(customer, 190.dp, m) else ScenePet(character, 190.dp, m, reactions = pet)
                    }
                    ItemPicture(
                        current, current.label, 84.dp,
                        Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 30.dp),
                    )
                    Counter(Modifier.align(Alignment.BottomCenter))
                    Say(line, Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 92.dp).width(228.dp))
                    Check(
                        give, current.price, current.paid ?: 0, current.target, solved,
                        Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 92.dp).width(142.dp),
                    )
                }
                Column(
                    Modifier.weight(1f).padding(start = 4.dp, end = 4.dp, top = 34.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Tray(
                        title = if (give) "Отсчитай сдачу" else "Заплати ровно",
                        label = if (give) "Сдача покупателю" else "На кассу, нужно ровно ${current.price}",
                        coins = tray,
                        enabled = !solved,
                        modifier = Modifier.weight(1f),
                        onRemove = { id ->
                            sounds.play(Sfx.Tap)
                            tray = tray.filter { it.id != id }
                            checked = null
                            nudge = null
                        },
                    )
                    if (give) Drawer(task.coins, enabled = !solved, onTake = ::put)
                    else Wallet(current.wallet, coins, enabled = !solved, onTake = ::put)
                    FinneyButton(
                        text = when {
                            !solved -> "Проверить"
                            round + 1 == task.rounds.size -> "Готово"
                            else -> "Следующий"
                        },
                        onClick = if (solved) ::next else ::check,
                        modifier = Modifier.height(68.dp),
                        textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp, lineHeight = 30.sp),
                    )
                }
            }
        }
    }
}

/** Круглые кнопки верхней полосы — 54, как в макете кассы. */
internal val RoundButtonSize = 54.dp

/** Реплика покупателя. Числа на плашках — в фигурных скобках: `{4}`, `{6:bad}`, `{4:good}`, см. [Say]. */
private fun customerLine(target: Int, sum: Int, checked: Int?, paid: Int): String = when {
    checked == null -> "Вот {$paid} финок. Сколько мне сдачи?"
    checked == 0 -> "Спасибо! Сдача ровно {$target:good}!"
    checked > 0 -> "Тут {$sum}, а сдача {$target}. Лишнее: {$checked:bad}"
    else -> "Тут {$sum}, а сдача {$target}. Не хватает: {${-checked}:bad}"
}

private fun ownLine(price: Int, sum: Int, checked: Int?): String = when {
    checked == null -> "Заплачу ровно {$price}, без сдачи!"
    checked == 0 -> "Ровно {$price:good}! Сдача не нужна"
    checked > 0 -> "Тут {$sum}, а нужно {$price}. Лишнее: {$checked:bad}"
    else -> "Тут {$sum}, а нужно {$price}. Не хватает: {${-checked}:bad}"
}

/** Для TalkBack — та же реплика без скобок. */
private fun plain(line: String) = line.replace(Regex("""\{(\d+)(:\w+)?\}"""), "$1")

/**
 * Реплика: пузырь с хвостиком вниз, к тому, кто говорит. Числа — на плашках, «лишнее»
 * и «не хватает» — розовой, верная сдача — зелёной. Новая реплика выскакивает.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Say(line: String, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(1f) }
    val animate = LocalAnimations.current
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(line) {
        if (first || !animate) { first = false; return@LaunchedEffect }
        pop.snapTo(0f)
        pop.animateTo(1f, tween(300, easing = LinearOutSlowInEasing))
    }
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 26.sp)
    SpeechBubble(
        Tail.DOWN,
        modifier
            .graphicsLayer {
                val s = 0.85f + 0.15f * pop.value
                scaleX = s
                scaleY = s
                alpha = pop.value
            }
            .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = plain(line) },
        tailAt = 0.36f,
        padding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 15.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            line.split(' ').forEach { word ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Regex("""\{(\d+)(?::(\w+))?\}|[^{]+""").findAll(word).forEach { m ->
                        val n = m.groups[1]?.value
                        if (n == null) Text(m.value, style = style, color = FinneyInk)
                        else Chip(n, m.groups[2]?.value, style)
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(n: String, kind: String?, style: TextStyle) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .padding(vertical = 1.dp)
            .defaultMinSize(minWidth = 28.dp)
            .clip(shape)
            .background(
                when (kind) {
                    "bad" -> Bad
                    "good" -> FinneyGreen
                    else -> FinneyCream
                },
            )
            .border(2.5.dp, FinneyInk, shape)
            .padding(start = 6.dp, end = 6.dp, bottom = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(n, style = style.copy(lineHeight = 22.sp), color = FinneyInk, textAlign = TextAlign.Center)
    }
}

/**
 * Прыжок за верную сдачу и «нет» головой за ошибку — у того, кто говорит. Покупатель
 * в сцене стоит одной позой, поэтому качается он целиком, как в макете.
 */
@Composable
private fun Jolting(jolt: Pair<Jolt, Int>?, modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    val dy = remember { Animatable(0f) }
    val dx = remember { Animatable(0f) }
    val animate = LocalAnimations.current
    LaunchedEffect(jolt) {
        if (jolt == null || !animate) return@LaunchedEffect
        when (jolt.first) {
            Jolt.HOP -> dy.animateTo(0f, keyframes {
                durationMillis = 500
                0f at 0
                -18f at 200 using FastOutSlowInEasing
                0f at 500
            })
            Jolt.NO -> dx.animateTo(0f, keyframes {
                durationMillis = 450
                0f at 0
                -8f at 112
                8f at 337
                0f at 450
            })
        }
    }
    content(modifier.graphicsLayer { translationX = dx.value.dp.toPx(); translationY = dy.value.dp.toPx() })
}

/** Покупатели кружками в белой плашке: текущий — жёлтый с ореолом, обслуженные — зелёные с галочкой. */
@Composable
private fun Progress(round: Int, total: Int, solved: Boolean) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .height(50.dp)
            .clip(shape)
            .background(Color.White)
            .insetShade(shape, Soft, 5.dp)
            .border(3.dp, FinneyInk, shape)
            .padding(horizontal = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Покупатель ${round + 1} из $total" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(total) { i ->
            val done = i < round || (i == round && solved)
            val now = i == round && !solved
            Canvas(Modifier.offset(y = (-1.5).dp).size(22.dp)) {
                val r = size.minDimension / 2
                val b = 3.dp.toPx()
                if (now) drawCircle(FinneyYellow, r + 3.dp.toPx())
                drawCircle(if (done) FinneyGreen else if (now) FinneyYellow else Color.White, r - b / 2)
                drawCircle(FinneyInk, r - b / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(b))
                if (done) {
                    val u = 1.dp.toPx()
                    val tick = androidx.compose.ui.graphics.Path().apply {
                        moveTo(center.x - 4.5f * u, center.y)
                        lineTo(center.x - 1.5f * u, center.y + 2.8f * u)
                        lineTo(center.x + 4.5f * u, center.y - 3f * u)
                    }
                    drawPath(tick, FinneyInk, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f * u, cap = StrokeCap.Round))
                }
            }
        }
    }
}

/**
 * Светлый чек вместо тёмного табло: «Цена» и «Дали» сверху, «Сдача ?» внизу в жёлтой
 * плашке. После верного ответа плашка зеленеет и показывает сдачу.
 */
@Composable
private fun Check(give: Boolean, price: Int, paid: Int, target: Int, solved: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    val row = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 21.sp)
    val value = row.copy(fontSize = 25.sp, lineHeight = 27.sp)
    Box(modifier.padding(top = 15.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color.White)
                .insetShade(shape, Soft, 6.dp, border = 4.dp)
                .border(4.dp, FinneyInk, shape)
                .padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CheckRow("Цена", price.toString(), row, value)
            if (give) CheckRow("Дали", paid.toString(), row, value)
            Dashed(Modifier.padding(vertical = 2.dp), alpha = 0.35f)
            val ansShape = RoundedCornerShape(14.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(ansShape)
                    .background(if (solved) FinneyGreen else FinneyYellow)
                    .border(3.dp, FinneyInk, ansShape)
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (give) "Сдача" else "Плачу", style = row, color = FinneyInk)
                OutlinedText(if (solved) target.toString() else "?", style = value.copy(fontSize = 28.sp, lineHeight = 30.sp))
            }
        }
        Title("Чек", Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun CheckRow(label: String, value: String, row: TextStyle, big: TextStyle) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label, style = row, color = FinneyInk, modifier = Modifier.padding(bottom = 2.dp))
        Text(value, style = big, color = FinneyInk)
    }
}

/** Заголовок окна на его рамке, 26 sp с обводкой: «Чек», «Отсчитай сдачу». */
@Composable
private fun Title(text: String, modifier: Modifier = Modifier) {
    OutlinedText(
        text,
        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, lineHeight = 30.sp),
        modifier = modifier.offset(y = (-17).dp),
    )
}

/** Пунктир через всю ширину — между строками чека и над подсчётом лотка. */
@Composable
private fun Dashed(modifier: Modifier = Modifier, alpha: Float) {
    Canvas(modifier.fillMaxWidth().height(2.5.dp)) {
        drawLine(
            FinneyInk.copy(alpha = alpha),
            Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())),
        )
    }
}

/** Прилавок: дерево с объёмным краем сверху и контуром, во всю ширину экрана, перед покупателем. */
@Composable
private fun Counter(modifier: Modifier = Modifier) {
    Box(
        modifier
            .bleed()
            .height(42.dp)
            .drawBehind {
                val u = 1.dp.toPx()
                drawRect(FinneyInk)
                drawRect(Wood, Offset(0f, 4 * u), size.copy(height = size.height - 8 * u))
                drawRect(WoodTop, Offset(0f, 4 * u), size.copy(height = 12 * u))
                drawRect(FinneyInk.copy(alpha = 0.35f), Offset(0f, 16 * u), size.copy(height = 3 * u))
            },
    )
}

/**
 * Лоток со сдачей: задание — заголовком на рамке, монеты посередине, подсчёт внизу.
 * На каждой монете розовый минус: так видно, что её можно убрать.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tray(
    title: String,
    label: String,
    coins: List<TrayCoin>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onRemove: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(FinneyCream)
                .insetShade(shape, TrayShade, 7.dp, border = 4.dp)
                .border(4.dp, FinneyInk, shape)
                .semantics { contentDescription = label }
                .padding(start = 14.dp, end = 14.dp, top = 26.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (coins.isEmpty()) {
                    Text(
                        "Нажимай на монетки в ящике, и они лягут сюда",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 19.sp, lineHeight = 24.sp),
                        color = FinneyInk.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                } else {
                    // Прокрутка режет всё, что за её краем, а минус торчит над монетой на 6 dp:
                    // без поля сверху его срезало.
                    FlowRow(
                        Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        coins.forEach { coin ->
                            key(coin.id) { TrayCoinView(coin, enabled) { onRemove(coin.id) } }
                        }
                    }
                }
            }
            Dashed(alpha = 0.3f)
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (coins.isNotEmpty()) {
                    Text(
                        coins.joinToString(" + ") { it.value.toString() } + " =",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 21.sp, lineHeight = 24.sp),
                        color = FinneyInk,
                    )
                }
                Sum(coins.sumOf { it.value })
            }
        }
        Title(title, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun Sum(total: Int) {
    val shape = RoundedCornerShape(50)
    Text(
        total.toString(),
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 26.sp),
        color = FinneyInk,
        modifier = Modifier
            .clip(shape)
            .background(Color.White)
            .insetShade(shape, Soft, 4.dp)
            .border(3.dp, FinneyInk, shape)
            .padding(start = 14.dp, end = 14.dp, top = 3.dp, bottom = 6.dp),
    )
}

/** Монета на лотке: падает сверху, когда её положили; нажатие убирает. */
@Composable
private fun TrayCoinView(coin: TrayCoin, enabled: Boolean, onRemove: () -> Unit) {
    val drop = remember { Animatable(0f) }
    val animate = LocalAnimations.current
    LaunchedEffect(Unit) {
        if (animate) drop.animateTo(1f, tween(220, easing = LinearOutSlowInEasing)) else drop.snapTo(1f)
    }
    Box(
        Modifier
            .graphicsLayer {
                val k = drop.value
                translationY = (1 - k) * -14.dp.toPx()
                scaleX = 0.8f + 0.2f * k
                scaleY = 0.8f + 0.2f * k
                alpha = k
            }
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Убрать монету ${coin.value}", onClick = onRemove),
    ) {
        DenominationCoin(coin.value)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(FinneyPink)
                .border(2.5.dp, FinneyInk, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 8.dp, height = 2.5.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
        }
    }
}

/** Ящик кассы: деревянный, по ячейке на номинал, монет сколько угодно. */
@Composable
private fun Drawer(coins: List<Int>, enabled: Boolean, onTake: (Int) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Wood)
            .drawBehind {
                drawRect(WoodTop, size = size.copy(height = 9.dp.toPx()))
            }
            .insetShade(shape, WoodDeep, 7.dp, border = 4.dp)
            .border(4.dp, FinneyInk, shape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        coins.forEach { coin -> Slot(coin, enabled, Modifier.weight(1f)) { onTake(coin) } }
    }
}

@Composable
private fun Slot(coin: Int, enabled: Boolean, modifier: Modifier, onTake: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(100), label = "slot")
    Box(
        modifier
            .height(84.dp)
            .clip(shape)
            .background(WoodDeep)
            .drawBehind { drawRect(Color(0x38140A28), size = size.copy(height = 10.dp.toPx())) }
            .border(3.dp, FinneyInk, shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClickLabel = "Положить монету $coin", onClick = onTake),
        contentAlignment = Alignment.Center,
    ) {
        DenominationCoin(
            coin, size = 62.dp,
            modifier = Modifier.graphicsLayer {
                translationY = press * 3.dp.toPx()
                scaleX = 1f - 0.05f * press
                scaleY = 1f - 0.05f * press
            },
        )
    }
}

/** Кошелёк Финни: каждая монета своя, потраченные бледнеют и не нажимаются. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Wallet(wallet: List<Int>, tray: List<Int>, enabled: Boolean, onTake: (Int) -> Unit) {
    val used = tray.groupingBy { it }.eachCount().toMutableMap()
    val shape = RoundedCornerShape(24.dp)
    FlowRow(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(FinneyPeach)
            .border(4.dp, FinneyInk, shape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        wallet.sortedDescending().forEach { coin ->
            val spent = (used[coin] ?: 0) > 0
            if (spent) used[coin] = used.getValue(coin) - 1
            DenominationCoin(
                value = coin,
                size = 56.dp,
                faded = spent,
                modifier = Modifier.clickable(enabled = enabled && !spent, role = Role.Button, onClickLabel = "Положить монету $coin") {
                    onTake(coin)
                },
            )
        }
    }
}
