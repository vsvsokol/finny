package ru.finney.pet.ui.tasks.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import ru.finney.pet.ui.progress.coins
import ru.finney.pet.ui.motion.LocalAnimations
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.model.StandIngredient
import ru.finney.pet.domain.model.StandTask
import ru.finney.pet.domain.tasks.TaskInput
import ru.finney.pet.domain.tasks.TaskInputError
import ru.finney.pet.ui.components.Coin
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyIconButton
import ru.finney.pet.ui.components.HeartIcon
import ru.finney.pet.ui.motion.motionEnabled
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.StrokeRegular
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx

// «Лимонадная лавка», как подработки в Pou, но с расходами. Утром закупка,
// днём ребёнок сам наливает каждому гостю — без таймера, гости ждут. Лавка
// закрылась — итог дня «получил − потратил = заработал» на экране итога.
// Оценивается только закупка: ровно столько сырья, сколько нужно на всех гостей.

@Composable
internal fun StandGame(
    task: StandTask,
    character: PetCharacter,
    inputError: TaskInputError?,
    onInputSeen: () -> Unit,
    onClose: () -> Unit,
    onSubmit: (TaskInput) -> Unit,
) {
    var count by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(false) }
    val cost = count * task.ingredient.price

    if (!open) {
        Morning(task, character, count, inputError, onClose, onChange = { count = it; onInputSeen() }, onOpen = { open = true })
    } else {
        Day(task, character, count, money = task.budget - cost, onClose = onClose, onDone = { onSubmit(TaskInput.Stock(count)) })
    }
}

@Composable
private fun Morning(
    task: StandTask,
    character: PetCharacter,
    count: Int,
    inputError: TaskInputError?,
    onClose: () -> Unit,
    onChange: (Int) -> Unit,
    onOpen: () -> Unit,
) {
    val i = task.ingredient
    GameScene(backdrop = Backdrop.SKY, onClose = onClose, money = task.budget - count * i.price) {
        // Закупка сверху, питомец внизу на траве, «Открыть лавку» у края экрана.
        // Раньше питомец висел в небе, а под панелью оставалось полэкрана пустой травы.
        SceneBody(bottom = { FinneyButton(text = "Открыть лавку", onClick = onOpen, enabled = count > 0) }) {
            ScenePanel(title = "Закупка", modifier = Modifier.fillMaxWidth()) {
                Text(task.forecast, style = MaterialTheme.typography.titleMedium, color = FinneyInk)
                // Сначала рецепт — сколько стаканов из одного лимона и почём лимон, —
                // потом выбор. Плейтест: объяснение «лимоны → стаканы» стояло под кнопками,
                // и выбирали раньше, чем его видели.
                Equation(
                    "Из одной штуки: ${i.label.lowercase()} — ${cupCount(i.yields)}. Штука стоит ${i.price}",
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(FinneyYellow)
                        .border(StrokeRegular, FinneyInk, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    ItemPicture(i, i.label, 32.dp)
                    EqText("→")
                    repeat(i.yields) { CupIcon(24.dp, full = true) }
                    Spacer(Modifier.weight(1f))
                    EqText("= ${i.price}")
                    Coin(size = 22.dp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(3.dp, FinneyInk, RoundedCornerShape(14.dp))
                        .padding(8.dp),
                ) {
                    ItemPicture(i, i.label, 44.dp)
                    Spacer(Modifier.weight(1f))
                    Stepper(
                        value = count.toString(),
                        onMinus = { onChange(count - 1) },
                        onPlus = { onChange(count + 1) },
                        minusEnabled = count > 0,
                        plusEnabled = (count + 1) * i.price <= task.budget,
                        what = i.label,
                    )
                }
                // Что куплено — картинками: у каждого лимона его стаканы. Итог «= 8»
                // числом не пишем: сколько выйдет на всех гостей, ребёнок считает сам —
                // иначе он жал «+», пока два числа не совпадут, и не думал.
                Stock(i, count)
                Equation("Закупка: ${i.label.lowercase()} × $count, всего ${count * i.price}") {
                    ItemPicture(i, i.label, 28.dp)
                    EqText("× $count = ${count * i.price}")
                    Coin(size = 22.dp)
                }
                Equation("стакан продаём за ${task.cupPrice}") {
                    CupIcon(24.dp, full = true)
                    EqText("→ ${task.cupPrice}")
                    Coin(size = 22.dp)
                }
                if (inputError != null) Note(inputErrorText(inputError), color = FinneyPeach)
            }
            Spacer(Modifier.weight(1f))
            PetSays(character, "Сколько взять, чтобы хватило всем гостям?", petSize = 120.dp)
        }
    }
}

/**
 * День лавки. Плейтест 29.09: «непонятно, что происходит», и кнопка «жмётся через раз» —
 * после нажатия на экране почти ничего не менялось: очередь из четырёх так и стояла,
 * «+5» мелькало мелко в углу. Теперь каждое нажатие видно трижды: гость уходит со
 * стаканом и сердечком, монета от него летит в кошелёк, а табло «лимоны − / выручка + /
 * итог» пересчитывается. Итог начинается с минуса — деньги ушли на лимоны — и растёт
 * с каждым стаканом: так видно, когда лавка вышла в плюс. Налить можно и нажатием на гостя.
 */
@Composable
private fun Day(task: StandTask, character: PetCharacter, count: Int, money: Int, onClose: () -> Unit, onDone: () -> Unit) {
    var served by remember { mutableIntStateOf(0) }
    // Сколько монет уже долетело до кошелька: кошелёк растёт, когда монета в нём, а не раньше.
    var landed by remember { mutableIntStateOf(0) }
    val cups = count * task.ingredient.yields
    val waiting = task.guests - served
    val cupsLeft = cups - served
    val done = waiting == 0 || cupsLeft == 0
    val spent = count * task.ingredient.price
    val sounds = LocalSounds.current
    val scope = rememberCoroutineScope()
    val walletPop = remember { Animatable(1f) }
    var walletAt by remember { mutableStateOf<Offset?>(null) }
    var queueAt by remember { mutableStateOf<Offset?>(null) }
    val flying = remember { mutableStateListOf<CoinFlight>() }
    val others = remember(character) { PetCharacter.entries.filter { it != character } }
    // Монетка кошелька — у его правого края; монета вылетает от первого гостя в очереди.
    val density = LocalDensity.current
    val walletCoinInset = with(density) { 27.dp.toPx() }
    val guestInset = with(density) { 36.dp.toPx() }

    fun pour() {
        if (done) return
        sounds.play(Sfx.Pour)
        served++
        val from = queueAt
        val to = walletAt
        scope.launch {
            if (from == null || to == null || !motionEnabled()) {
                landed++
                sounds.play(Sfx.Coin)
                return@launch
            }
            val coin = CoinFlight(from, to)
            flying += coin
            coin.t.animateTo(1f, tween(CoinFlightMs, easing = FastOutSlowInEasing))
            flying -= coin
            landed++
            sounds.play(Sfx.Coin, 1f + 0.03f * landed.coerceAtMost(10))
            walletPop.snapTo(1.2f)
            walletPop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        }
    }

    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        GameScene(
            backdrop = Backdrop.SKY,
            onClose = onClose,
            money = money + landed * task.cupPrice,
            moneyModifier = Modifier
                .onGloballyPositioned { c -> c.boundsInRoot().let { walletAt = Offset(it.right - walletCoinInset, it.center.y) } }
                .graphicsLayer {
                    scaleX = walletPop.value
                    scaleY = walletPop.value
                },
        ) {
            Column(Modifier.fillMaxSize().padding(top = HudHeight), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StandTally(ingredient = task.ingredient, spent = spent, earned = served * task.cupPrice)
                CupStrip(cups = cups, poured = served)

                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Stall(character, task.cupPrice)
                    // Обслуженный гость уходит вправо со стаканом и сердечком.
                    ServedGuest(served, others, Modifier.align(Alignment.BottomEnd))
                }

                when {
                    waiting == 0 && cupsLeft > 0 -> Note("Гости кончились, а осталось ${cupCount(cupsLeft)}.", color = FinneyPeach)
                    cupsLeft == 0 && waiting > 0 -> Note("Лимонад кончился! Без лимонада ушли $waiting.", color = FinneyPeach)
                    done -> Note("Всем хватило, и ничего не осталось!", color = FinneyGreen)
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    // Сколько гостей ждёт — числом над очередью, а не строкой «Гостей ждёт: 8».
                    Box(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "Гостей ждёт: $waiting" }) {
                        if (waiting > 0) {
                            OutlinedText("$waiting", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.TopStart))
                        }
                        Row(
                            modifier = Modifier
                                .padding(top = 28.dp)
                                .onGloballyPositioned { c -> queueAt = c.boundsInRoot().let { Offset(it.left + guestInset, it.center.y) } }
                                // Нажатие на гостя — тоже «налить»: дети жали на того, кого обслуживают.
                                .clickable(enabled = !done, role = Role.Button, onClickLabel = "Налить лимонад", onClick = ::pour),
                            horizontalArrangement = Arrangement.spacedBy((-18).dp),
                        ) {
                            // Больше четырёх гостей не рисуем — очередь видна и числом. Первый в
                            // очереди — следующий по счёту гость, и очередь сдвигается при каждом стакане.
                            repeat(minOf(waiting, 4)) { n -> Guest(others[(served + n) % others.size], 72.dp, Modifier.width(72.dp)) }
                        }
                    }
                    if (done) {
                        FinneyButton(text = "Закрыть лавку", onClick = onDone, fillWidth = false)
                    } else {
                        PourButton(task.cupPrice, pulse = served == 0, onPour = ::pour)
                    }
                }
            }
        }
        // Монеты — поверх всей сцены: они летят от очереди в кошелёк в верхней полосе.
        flying.forEach { coin ->
            Coin(
                size = 30.dp,
                modifier = Modifier
                    .offset {
                        val p = coin.at() - origin
                        IntOffset((p.x - 15.dp.toPx()).roundToInt(), (p.y - 15.dp.toPx()).roundToInt())
                    },
            )
        }
    }
}

/** Монета от гостя к кошельку: по дуге вверх, [t] — 0…1 пути. */
private class CoinFlight(val from: Offset, val to: Offset) {
    val t = Animatable(0f)

    fun at(): Offset {
        val k = t.value
        val bend = Offset(from.x, to.y)
        val u = 1f - k
        return from * (u * u) + bend * (2f * u * k) + to * (k * k)
    }
}

private const val CoinFlightMs = 550

/**
 * Табло дня: сколько ушло на сырьё, сколько пришло за стаканы и что получилось.
 * Итог сначала отрицательный — это и есть расход на лимоны; вышел в плюс — жёлтая
 * плашка и «✓» (жёлтый — деньги, и рядом знак, не только цвет).
 */
@Composable
private fun StandTally(ingredient: StandIngredient, spent: Int, earned: Int) {
    val kept = earned - spent
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "Лимоны минус $spent, выручка $earned, итог $kept" },
    ) {
        TallyCell(Modifier.weight(1f), "−$spent", FinneyCream, "Закупка") { ItemPicture(ingredient, ingredient.label, 24.dp) }
        TallyCell(Modifier.weight(1f), "+$earned", FinneyCream, "Выручка") { CupIcon(22.dp, full = true) }
        Text("=", style = MaterialTheme.typography.titleLarge, color = FinneyInk)
        TallyCell(Modifier.weight(1.2f), if (kept > 0) "+$kept" else if (kept < 0) "−${-kept}" else "0", if (kept > 0) FinneyYellow else FinneyCream, "Заработок") {
            if (kept > 0) Text("✓", style = MaterialTheme.typography.titleLarge, color = FinneyInk) else Coin(size = 22.dp)
        }
    }
}

@Composable
private fun TallyCell(modifier: Modifier, value: String, fill: Color, label: String, icon: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .border(StrokeRegular, FinneyInk, RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            icon()
            OutlinedText(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Гость, которому только что налили: со стаканом и сердечком уходит вправо и тает.
 * Новый стакан — новый уходящий гость ([served] — ключ).
 */
@Composable
private fun ServedGuest(served: Int, others: List<PetCharacter>, modifier: Modifier = Modifier) {
    if (served == 0) return
    val walk = remember(served) { Animatable(0f) }
    LaunchedEffect(served) {
        if (!motionEnabled()) {
            walk.snapTo(0.5f)
            return@LaunchedEffect
        }
        walk.animateTo(1f, tween(ServedWalkMs, easing = LinearEasing))
    }
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .clearAndSetSemantics {}
            .graphicsLayer {
                translationX = walk.value * 60.dp.toPx()
                alpha = (1f - walk.value).coerceIn(0f, 1f) * 1.4f
            },
    ) {
        Guest(others[(served - 1) % others.size], 64.dp, Modifier.width(64.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HeartIcon(filled = true, size = 22.dp)
            CupIcon(26.dp, full = true)
        }
    }
}

private const val ServedWalkMs = 900

/** Стаканы дня: налитые — жёлтые, пустые — белые. */
@Composable
private fun CupStrip(cups: Int, poured: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FinneyCream)
            .border(3.dp, FinneyInk, RoundedCornerShape(12.dp))
            .padding(6.dp)
            .semantics { contentDescription = "Налито $poured из $cups стаканов" },
    ) {
        // Стакан сжимается, если их много: четырнадцать по 20 dp упирались в край на 360 dp.
        repeat(cups.coerceAtMost(14)) { n ->
            Canvas(Modifier.weight(1f, fill = false).widthIn(max = 20.dp).aspectRatio(20f / 26f)) {
                val cup = Path().apply {
                    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width * 0.85f, size.height); lineTo(size.width * 0.15f, size.height); close()
                }
                drawPath(cup, if (n < poured) Color(0xFFF9E27A) else Color.White)
                drawPath(cup, FinneyInk, style = Stroke(3.dp.toPx()))
            }
        }
    }
}

/** Лавка: полосатый навес с фестонами, столбики, питомец за прилавком. */
@Composable
private fun Stall(character: PetCharacter, cupPrice: Int) {
    Box(Modifier.fillMaxWidth(0.85f).height(230.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val awning = 44.dp.toPx()
            val stripe = 26.dp.toPx()
            val stroke = 4.dp.toPx()
            // Столбики
            listOf(10.dp.toPx(), size.width - 18.dp.toPx()).forEach { x ->
                drawRect(Color(0xFFA0715A), Offset(x, awning), Size(8.dp.toPx(), size.height - awning))
                drawRect(FinneyInk, Offset(x, awning), Size(8.dp.toPx(), size.height - awning), style = Stroke(3.dp.toPx()))
            }
            // Навес
            var x = 0f
            var odd = false
            while (x < size.width) {
                drawRect(if (odd) FinneyPeach else FinneyYellow, Offset(x, 0f), Size(minOf(stripe, size.width - x), awning))
                x += stripe
                odd = !odd
            }
            var s = 0f
            while (s < size.width) {
                drawCircle(FinneyPeach, stripe / 2, Offset(s + stripe / 2, awning))
                drawCircle(FinneyInk, stripe / 2, Offset(s + stripe / 2, awning), style = Stroke(3.dp.toPx()))
                s += stripe
            }
            drawRect(FinneyInk, Offset.Zero, Size(size.width, awning), style = Stroke(stroke))
        }
        ScenePet(character, 120.dp, Modifier.width(120.dp).align(Alignment.Center).offset(y = 10.dp))
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(70.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .border(4.dp, FinneyInk, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) { OutlinedText("Стакан: ${coins(cupPrice)}", style = MaterialTheme.typography.titleLarge) }
    }
}

/**
 * Большая круглая кнопка со стаканом и подпись «Налить: +5 монет» под ней: сколько
 * принесёт стакан, видно до нажатия. Кнопка из кита — сжимается под пальцем,
 * как все: прежний плоский круг не отзывался, и казалось, что нажатие не прошло.
 *
 * Плейтест 02.10: «Налить за 5» с монеткой читали как цену, а не заработок, и не
 * понимали, что жать надо её. Поэтому заработок словами, а пока не налит первый
 * стакан, кнопка пульсирует. Без анимаций (ТЗ п. 3.6) — стоит на месте.
 */
@Composable
private fun PourButton(cupPrice: Int, pulse: Boolean, onPour: () -> Unit) {
    val beatAnim by rememberInfiniteTransition(label = "pour").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "beat",
    )
    val beat = if (pulse && LocalAnimations.current) beatAnim else 1f
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FinneyIconButton(
            onClick = onPour,
            contentDescription = "Налить лимонад: плюс ${coins(cupPrice)}",
            size = 96.dp,
            sound = null,
            modifier = Modifier.graphicsLayer {
                scaleX = beat
                scaleY = beat
            },
        ) {
            CupIcon(44.dp, full = true)
        }
        OutlinedText(
            "Налить: +${coins(cupPrice)}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * Закупка картинками: каждая купленная штука и стаканы, которые из неё выйдут.
 * Сколько всего стаканов — пересчитать глазами, а не прочитать готовое число.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Stock(ingredient: StandIngredient, count: Int) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clearAndSetSemantics {
                contentDescription = "Куплено: ${ingredient.label.lowercase()} × $count, это ${cupCount(count * ingredient.yields)}"
            },
    ) {
        repeat(count) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ItemPicture(ingredient, ingredient.label, 26.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    repeat(ingredient.yields) { CupIcon(14.dp, full = true) }
                }
            }
        }
    }
}

/** Строка-пример из картинок; [description] — то же словами для TalkBack. */
@Composable
private fun Equation(description: String, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        content = content,
    )
}

@Composable
private fun EqText(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = FinneyInk)
}

/** Стакан, как в полосе стаканов дня: [full] — с лимонадом. */
@Composable
private fun CupIcon(size: Dp, full: Boolean = false) {
    Canvas(Modifier.width(size * 20f / 26f).height(size)) {
        val cup = Path().apply {
            moveTo(0f, 0f); lineTo(this@Canvas.size.width, 0f); lineTo(this@Canvas.size.width * 0.85f, this@Canvas.size.height)
            lineTo(this@Canvas.size.width * 0.15f, this@Canvas.size.height); close()
        }
        drawPath(cup, if (full) Color(0xFFF9E27A) else Color.White)
        drawPath(cup, FinneyInk, style = Stroke(2.5.dp.toPx()))
    }
}
