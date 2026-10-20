package ru.finney.pet.ui.budget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.finney.pet.R
import ru.finney.pet.ui.components.AMOUNT_STEP
import ru.finney.pet.ui.components.Coin
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyQuietButton
import ru.finney.pet.ui.components.FinneyScreen
import ru.finney.pet.ui.components.FinniHead
import ru.finney.pet.ui.components.FinniSays
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.components.PlanPart
import ru.finney.pet.ui.components.buttonFill
import ru.finney.pet.ui.components.partIcon
import ru.finney.pet.ui.components.rememberPartBrush
import ru.finney.pet.ui.components.speech
import ru.finney.pet.ui.goals.rewardArt
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.room.itemArt
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyPink
import ru.finney.pet.ui.theme.RadiusCard
import ru.finney.pet.ui.theme.StrokeBold
import ru.finney.pet.ui.theme.StrokeRegular

// План банками на весь экран (разбор 20.10). Вместо «−/+» по пять монет — три банки:
// тап — в банку падает 5 монет, удержание — льются всё быстрее, кнопка ↶ над банкой
// наклоняет её и высыпает 5 обратно. Кучка монет сверху тает — остаток виден без
// слова «остаток» (ТЗ п. 2.5.5). Говорит Финни: что сейчас важно и чего не хватает.
//
// Первый план — банки по одной: сначала нужное, потом желаемое, потом копилка, и
// каждую Финни представляет окном с примерами. На 2-м уровне так же представляется
// «Желаемое»: тогда открываются игрушки. Ядро не меняется — суммы идут тем же
// setNeeds/setWants/setSavings, план подтверждает тот же confirm.

private const val Step = AMOUNT_STEP

/** Части плана по порядку: слово, что входит, узор и значок — те же, что в «План и факт». */
private class Part(val part: PlanPart, val label: String, val sub: String)

private val Parts = listOf(
    Part(PlanPart.NEEDS, "Нужное", "еда и ванна"),
    Part(PlanPart.WANTS, "Желаемое", "игрушки и сладости"),
    Part(PlanPart.SAVINGS, "Копилка", "на цель"),
)

@Composable
internal fun PlanningJars(
    planning: BudgetUiState.Planning,
    onNeedsChange: (Int) -> Unit,
    onWantsChange: (Int) -> Unit,
    onSavingsChange: (Int) -> Unit,
    onSelectGoal: (String) -> Unit,
    onRepeatLast: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val staged = planning.periodNumber == 1
    val hint = planning.needsHint?.takeIf { it > 0 } ?: Step
    // Сколько банок открыто: на первом плане — по одной, дальше — все.
    var open by rememberSaveable { mutableIntStateOf(if (staged) 1 else Parts.size) }
    var intro by rememberSaveable {
        mutableStateOf(
            when {
                staged -> PlanPart.NEEDS
                planning.level == 2 && planning.planned == 0 -> PlanPart.WANTS
                else -> null
            },
        )
    }
    var sealed by rememberSaveable { mutableStateOf(false) }
    // После «Готово!» черновик в ядре сбрасывается раньше, чем экран закроется: показываем
    // план таким, каким его запечатали, иначе банки на миг пустели.
    val frozen = remember(sealed) { planning }
    val state = if (sealed) frozen else planning
    val sounds = LocalSounds.current
    val scope = rememberCoroutineScope()

    // Банки открываются по одной: положил сколько нужно Финни — выезжает «Желаемое»,
    // положил хоть немного в желаемое — копилка.
    LaunchedEffect(open, state.needs, state.wants) {
        if (open == 1 && state.needs >= hint) { delay(450); open = 2; intro = PlanPart.WANTS }
        else if (open == 2 && state.wants > 0) { delay(650); open = 3; intro = PlanPart.SAVINGS }
    }

    fun value(p: PlanPart) = when (p) {
        PlanPart.NEEDS -> state.needs
        PlanPart.WANTS -> state.wants
        PlanPart.SAVINGS -> state.savings
    }
    fun set(p: PlanPart, v: Int) = when (p) {
        PlanPart.NEEDS -> onNeedsChange(v)
        PlanPart.WANTS -> onWantsChange(v)
        PlanPart.SAVINGS -> onSavingsChange(v)
    }

    Box(Modifier.fillMaxSize()) {
        FinneyScreen(
            scrollable = false,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            onClose = onBack,
            top = { OutlinedText("План на уровень ${state.level}", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center) },
            bottom = {
                FinneyButton(
                    text = "Готово!",
                    enabled = state.canConfirm && !sealed,
                    onClick = {
                        sealed = true
                        sounds.play(Sfx.PiggyIn)
                        scope.launch { delay(700); onConfirm() }
                    },
                )
            },
        ) {
            FinniSays(
                character = state.character,
                text = finniLine(state, staged, open, hint, sealed),
                coloring = state.coloring,
                accessories = state.worn,
                headSize = 64.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            CoinPile(state.remainder)
            if (state.canRepeatLast && !staged) {
                FinneyQuietButton(text = "Как в прошлый раз", onClick = onRepeatLast, modifier = Modifier.fillMaxWidth(0.75f))
            }
            state.rejection?.let { RejectionNote(it) }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Parts.forEachIndexed { i, p ->
                    val v = value(p.part)
                    PlanJarColumn(
                        part = p,
                        value = v,
                        budget = state.budget,
                        hint = if (p.part == PlanPart.NEEDS) state.needsHint?.takeIf { it > 0 } else null,
                        last = state.lastPlan?.let { last ->
                            when (p.part) {
                                PlanPart.NEEDS -> last.needs
                                PlanPart.WANTS -> last.wants
                                PlanPart.SAVINGS -> last.savings
                            }
                        },
                        focus = intro == null && !staged && state.level == 2 && p.part == PlanPart.WANTS,
                        locked = i >= open,
                        sealed = sealed,
                        sealDelay = i * 150,
                        needGoal = p.part == PlanPart.SAVINGS && state.goalLabel == null,
                        goals = state.goals.take(GoalsShown).map { it.id to (it.rewardArt() ?: R.drawable.ic_level_piggy) },
                        onSelectGoal = onSelectGoal,
                        onAdd = {
                            if (state.remainder >= Step && !sealed) { set(p.part, v + Step); true } else false
                        },
                        onTake = {
                            if (v >= Step && !sealed) { set(p.part, v - Step); true } else false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        intro?.let { part ->
            PartIntro(
                part = part,
                pictures = introPictures(state, part),
                character = state.character,
                coloring = state.coloring,
                worn = state.worn,
                onOk = { intro = null },
            )
        }
    }
}

private const val GoalsShown = 3

private fun ru.finney.pet.domain.model.Goal.rewardArt(): Int? = reward?.let(::rewardArt)

/** Что сказать Финни: на первом плане — что это за часть, дальше — чего не хватает. */
private fun finniLine(state: BudgetUiState.Planning, staged: Boolean, open: Int, hint: Int, sealed: Boolean): AnnotatedString = when {
    sealed -> speech(null, "Щёлк! План готов.")
    staged && open == 1 -> if (state.needs < hint) speech("Нужное", " — без чего мне плохо: еда и мыло. Мне нужно ≈ $hint.") else speech(null, "Хватит! Теперь — радости.")
    staged && open == 2 -> if (state.wants == 0) speech("Желаемое", " — что меня радует. Сколько не жалко?") else speech(null, "Ура! А остальное — на мечту.")
    staged && state.goalLabel == null -> speech("Копилка", " — копим на мечту. Выбери, на что!")
    staged && state.savings == 0 -> speech(null, "Сколько отложим на мечту?")
    state.needsHint != null && state.needs < state.needsHint -> speech(null, "Мне нужно ≈ ${state.needsHint} на еду и мыло.")
    state.level == 2 && state.wants == 0 -> speech("Новое: игрушки!", " Сколько не жалко на радости?")
    state.remainder > 0 && state.allDirections -> speech(null, "Осталось ${state.remainder}. Можно оставить в кошельке.")
    state.remainder > 0 -> speech(null, "Осталось ${state.remainder}. Куда положим?")
    state.missing.isNotEmpty() -> speech(null, state.missing.first() + ".")
    else -> speech(null, "Отличный план!")
}

/** Три примера к части: еда и мыло, сладости и игрушки, награды целей. */
private fun introPictures(state: BudgetUiState.Planning, part: PlanPart): List<Int> = when (part) {
    // Еда и мыло — ровно то, что говорит Финни; первые товары магазина были бы одной едой.
    PlanPart.NEEDS -> listOf("food_apple", "food_bowl", "care_soap").mapNotNull(::itemArt)
    // Сладости и игрушки: желаемая еда (сок, арбуз) рядом с «сладости, игрушки» читалась как нужное.
    PlanPart.WANTS -> listOf("treat_candy", "toy_duck", "toy_ball").mapNotNull(::itemArt)
        .ifEmpty { state.wantsItems.mapNotNull(::itemArt) }
    PlanPart.SAVINGS -> (state.goals.mapNotNull { it.rewardArt() } + listOfNotNull(state.goalReward?.let(::rewardArt))).distinct()
}.take(3)

// ---------- Кучка монет ----------

/** Сколько ещё разложить: стопка монет и число. Тает с каждой монетой в банке. */
@Composable
private fun CoinPile(rest: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .border(StrokeRegular, FinneyInk, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Ещё разложить: $rest" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(36.dp)) {
            Coin(size = 24.dp, modifier = Modifier.offset(y = 12.dp))
            Coin(size = 24.dp, modifier = Modifier.offset(x = 5.dp, y = 6.dp))
            Coin(size = 24.dp, modifier = Modifier.offset(x = 2.dp))
        }
        OutlinedText(rest.toString(), style = MaterialTheme.typography.headlineMedium)
        Text("разложи", style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
    }
}

// ---------- Банка ----------

/**
 * Банка части с подписями: значок, сумма, слово, что входит. [locked] — ещё не открыта
 * (первый план), [focus] — новая на этом уровне, [needGoal] — копить пока не на что,
 * вместо банки — выбор цели.
 */
@Composable
private fun PlanJarColumn(
    part: Part,
    value: Int,
    budget: Int,
    hint: Int?,
    last: Int?,
    focus: Boolean,
    locked: Boolean,
    sealed: Boolean,
    sealDelay: Int,
    needGoal: Boolean,
    goals: List<Pair<String, Int>>,
    onSelectGoal: (String) -> Unit,
    onAdd: () -> Boolean,
    onTake: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val sounds = LocalSounds.current
    val animations = LocalAnimations.current
    val tilt = remember { Animatable(0f) }
    val bump = remember { Animatable(1f) }
    val lid = remember { Animatable(if (sealed) 1f else 0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(sealed) {
        if (sealed && lid.value < 1f) {
            if (animations) { delay(sealDelay.toLong()); lid.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
            else lid.snapTo(1f)
        }
    }
    val add: (Int) -> Boolean = { n ->
        val ok = onAdd()
        if (ok) {
            sounds.play(Sfx.Coin, (1f + 0.04f * n).coerceAtMost(1.6f))
            if (animations) scope.launch { bump.snapTo(1.04f); bump.animateTo(1f, spring(dampingRatio = 0.4f)) }
        }
        ok
    }
    val take: (Int) -> Boolean = { _ ->
        val ok = onTake()
        if (ok) {
            sounds.play(Sfx.Coin, 0.8f)
            if (animations) scope.launch { tilt.animateTo(-16f, tween(120)); tilt.animateTo(0f, spring(dampingRatio = 0.5f)) }
        }
        ok
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val fill = rememberPartBrush(part.part)
            val shown by animateFloatAsState((value.toFloat() / budget.coerceAtLeast(1)).coerceIn(0f, 1f), label = "банка")
            Box(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = tilt.value
                        scaleX = bump.value; scaleY = bump.value
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .then(if (focus) Modifier.border(5.dp, Color.White, JarShape).padding(5.dp) else Modifier)
                    .clip(JarShape)
                    .background(Color.White)
                    .holdRepeat(enabled = !locked && !needGoal && !sealed, onStep = add)
                    .semantics {
                        role = Role.Button
                        contentDescription = "${part.label}: $value. Положить $Step, держи — льётся"
                        onClick { add(0) }
                    },
            ) {
                JarContents(fill = fill, shown = { shown }, budget = budget, hint = hint, last = last)
            }
            // Рамка банки поверх заливки — по той же форме.
            Box(Modifier.padding(top = 14.dp).fillMaxSize().graphicsLayer { rotationZ = tilt.value; transformOrigin = TransformOrigin(0.5f, 1f) }.border(StrokeBold, FinneyInk, JarShape))
            // Крышка: план подтверждён — банки закрываются по очереди.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(24.dp)
                    .graphicsLayer {
                        scaleX = 1.1f
                        alpha = lid.value.coerceIn(0f, 1f)
                        translationY = (1f - lid.value) * -60.dp.toPx()
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .background(FinneyPeach)
                    .border(StrokeRegular, FinneyInk, RoundedCornerShape(10.dp)),
            )
            if (!locked && !needGoal && !sealed) {
                PourBackButton(
                    label = part.label,
                    onStep = take,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp),
                )
            }
            if (locked) LockedCover(Modifier.padding(top = 14.dp).fillMaxSize())
            if (!locked && needGoal) GoalPicker(goals, onSelectGoal, Modifier.padding(top = 14.dp).fillMaxSize())
            if (focus) {
                Text(
                    "новое",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ru.finney.pet.ui.components.SpeechLeadColor,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .border(StrokeRegular, FinneyInk, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp),
                )
            }
        }
        PartBadge(part.part)
        OutlinedText(value.toString(), style = MaterialTheme.typography.titleLarge)
        Text(part.label, style = MaterialTheme.typography.titleMedium, color = FinneyInk, maxLines = 1)
        Text(part.sub, style = MaterialTheme.typography.labelMedium, color = FinneyInk, textAlign = TextAlign.Center, minLines = 2)
    }
}

/** Форма банки: скругление сверху шире, чем снизу, — горлышко. */
private val JarShape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp, bottomStart = 26.dp, bottomEnd = 26.dp)

/**
 * Заливка узором части снизу на [shown] высоты, сверху — ровная линия уровня. Узор
 * прижат ко дну: прямоугольник рисуется от низа, и плитка не «ползёт», пока банка
 * наполняется. Черта «≈ N» — сколько нужно Финни, розовая — сколько было в прошлый раз.
 */
@Composable
private fun JarContents(fill: androidx.compose.ui.graphics.Brush, shown: () -> Float, budget: Int, hint: Int?, last: Int?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height
            val top = h * (1f - shown())
            if (shown() > 0f) {
                drawRect(fill, topLeft = Offset(0f, top), size = Size(size.width, h - top))
                drawLine(FinneyInk.copy(alpha = 0.6f), Offset(0f, top), Offset(size.width, top), 3.dp.toPx())
            }
            val dash = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
            last?.takeIf { it > 0 }?.let {
                val y = h * (1f - it.toFloat() / budget.coerceAtLeast(1))
                drawLine(FinneyPink, Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), pathEffect = dash)
            }
            hint?.let {
                val y = h * (1f - it.toFloat() / budget.coerceAtLeast(1))
                drawLine(FinneyInk, Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), pathEffect = dash)
            }
        }
        hint?.let {
            val y = maxHeight * (1f - it.toFloat() / budget.coerceAtLeast(1))
            Text(
                "≈ $it",
                style = MaterialTheme.typography.labelMedium,
                color = FinneyInk,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = y - 26.dp)
                    .clip(RoundedCornerShape(50))
                    .background(FinneyCream)
                    .border(2.dp, FinneyInk, RoundedCornerShape(50))
                    .padding(horizontal = 6.dp),
            )
        }
    }
}

/** Значок части в жёлтом круге — тот же, что под банками «План и факт». */
@Composable
private fun PartBadge(part: PlanPart) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(44.dp).clip(CircleShape).buttonFill(pressed = false, round = true),
    ) { FinneyIcon(partIcon(part), size = 26.dp) }
}

/** ↶ над банкой: наклонить и высыпать 5 обратно. Держи — сыплется дальше. 48 dp (ТЗ п. 3.6). */
@Composable
private fun PourBackButton(label: String, onStep: (Int) -> Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(StrokeRegular, FinneyInk, CircleShape)
            .holdRepeat(enabled = true, onStep = onStep)
            .semantics {
                role = Role.Button
                contentDescription = "Высыпать $Step из «$label» обратно"
                onClick { onStep(0) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val s = size.minDimension
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(2.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawArc(FinneyInk, startAngle = 200f, sweepAngle = 250f, useCenter = false,
                topLeft = Offset(s * 0.15f, s * 0.15f), size = Size(s * 0.7f, s * 0.7f), style = stroke)
            drawLine(FinneyInk, Offset(s * 0.12f, s * 0.2f), Offset(s * 0.18f, s * 0.48f), stroke.width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(FinneyInk, Offset(s * 0.18f, s * 0.48f), Offset(s * 0.44f, s * 0.42f), stroke.width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** Ещё не открытая банка первого плана — бледная, с «?». */
@Composable
private fun LockedCover(modifier: Modifier) {
    Box(
        modifier.clip(JarShape).background(FinneyCream.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedText("?", style = MaterialTheme.typography.headlineLarge, fill = FinneyCream, outline = FinneyInk.copy(alpha = 0.4f))
    }
}

/** Выбор цели прямо в банке копилки: без цели откладывать некуда. Решает ребёнок. */
@Composable
private fun GoalPicker(goals: List<Pair<String, Int>>, onSelect: (String) -> Unit, modifier: Modifier) {
    Column(
        modifier.clip(JarShape).background(FinneyCream).padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("На что копим?", style = MaterialTheme.typography.labelMedium, color = FinneyInk, textAlign = TextAlign.Center)
        goals.forEach { (id, art) ->
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(StrokeRegular, FinneyInk, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button, onClickLabel = "Копить на это") { onSelect(id) },
                contentAlignment = Alignment.Center,
            ) { Image(painterResource(art), null, Modifier.size(50.dp)) }
        }
    }
}

// ---------- Знакомство с частью ----------

/**
 * Окно знакомства с частью плана: значок и слово на рамке, три примера картинками и
 * слова Финни. Как окно «Сделай» на карточке игры. Закрывается «Понятно».
 */
@Composable
private fun PartIntro(
    part: PlanPart,
    pictures: List<Int>,
    character: ru.finney.pet.domain.model.PetCharacter?,
    coloring: String?,
    worn: List<String>,
    onOk: () -> Unit,
) {
    val p = Parts.first { it.part == part }
    val line = when (part) {
        PlanPart.NEEDS -> speech("Нужное", " — без чего мне плохо: еда и мыло. Это сначала!")
        PlanPart.WANTS -> speech("Желаемое", " — что меня радует: сладости, игрушки. Можно и без них.")
        PlanPart.SAVINGS -> speech("Копилка", " — откладываем понемногу на большую мечту.")
    }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
    Box(
        Modifier
            .fillMaxSize()
            .background(FinneyInk.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding()
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { scaleX = 0.7f + 0.3f * pop.value; scaleY = 0.7f + 0.3f * pop.value; alpha = pop.value.coerceIn(0f, 1f) }) {
            Column(
                Modifier
                    .padding(top = 30.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(FinneyCream)
                    .drawBehind {
                        // Тень по низу внутри окна — тёплая, как у окон карточки игры.
                        drawRect(Color(0xFFFBDDB4), topLeft = Offset(0f, size.height - 8.dp.toPx()), size = Size(size.width, 8.dp.toPx()))
                    }
                    .border(StrokeBold, FinneyInk, RoundedCornerShape(30.dp))
                    .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pictures.forEach { art ->
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(RadiusCard))
                                .background(Color.White)
                                .border(StrokeRegular, FinneyInk, RoundedCornerShape(RadiusCard)),
                            contentAlignment = Alignment.Center,
                        ) { Image(painterResource(art), null, Modifier.fillMaxSize(0.72f)) }
                    }
                }
                FinniSays(character = character, text = line, coloring = coloring, accessories = worn, headSize = 64.dp, modifier = Modifier.fillMaxWidth())
                FinneyButton(text = "Понятно", onClick = onOk)
            }
            // Заголовок висит на рамке: значок и слово.
            Row(
                Modifier.align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(60.dp).clip(CircleShape).buttonFill(pressed = false, round = true),
                ) { FinneyIcon(partIcon(part), size = 34.dp) }
                OutlinedText(p.label, style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

// ---------- Удержание ----------

/**
 * Тап — один шаг, удержание — шаги всё чаще. [onStep] получает номер шага в удержании
 * (для тона звука) и возвращает false на упоре — тогда повтор встаёт.
 */
@Composable
private fun Modifier.holdRepeat(enabled: Boolean, onStep: (Int) -> Boolean): Modifier {
    // Шаг читается свежим: иначе каждая монета пересоздавала бы жест, и удержание обрывалось.
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    return if (!enabled) this else pointerInput(Unit) {
        detectTapGestures(onPress = {
            if (!step(0)) return@detectTapGestures
            val repeat = scope.launch {
                delay(380)
                var n = 1
                while (step(n)) {
                    delay((160 - n * 12).coerceAtLeast(70).toLong())
                    n++
                }
            }
            try {
                tryAwaitRelease()
            } finally {
                repeat.cancel()
            }
        })
    }
}
