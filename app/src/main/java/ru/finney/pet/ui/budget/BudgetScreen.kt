package ru.finney.pet.ui.budget

import ru.finney.pet.ui.goals.rewardArt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import ru.finney.pet.ui.components.fadingScroll
import ru.finney.pet.ui.motion.LocalAnimations
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import ru.finney.pet.domain.model.Category
import ru.finney.pet.ui.components.Coin
import ru.finney.pet.ui.components.ContextHelp
import ru.finney.pet.ui.components.ContextHelpIcon
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.FinneyQuietButton
import ru.finney.pet.ui.components.PlanDonut
import ru.finney.pet.ui.components.SavingsIcon
import ru.finney.pet.ui.components.categoryIcon
import ru.finney.pet.ui.room.itemArt
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.StrokeBold
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.finney.pet.domain.game.PlanReport
import ru.finney.pet.domain.game.Rejection
import ru.finney.pet.domain.model.Goal
import ru.finney.pet.domain.model.PeriodFacts
import ru.finney.pet.domain.model.Plan
import ru.finney.pet.ui.components.AMOUNT_STEP
import ru.finney.pet.ui.components.AmountStepper
import ru.finney.pet.ui.components.CheckBadge
import ru.finney.pet.ui.components.WarningBadge
import ru.finney.pet.ui.components.CoinAmount
import ru.finney.pet.ui.components.FeedbackSound
import ru.finney.pet.ui.components.FillBar
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyCard
import ru.finney.pet.ui.components.FinneyScreen
import ru.finney.pet.ui.components.LevelBadge
import ru.finney.pet.ui.components.PlanJars
import ru.finney.pet.domain.model.PetCharacter
import androidx.compose.ui.unit.Dp
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.pet.accessoryArt
import ru.finney.pet.ui.pet.PetMood
import ru.finney.pet.ui.pet.colorFilter
import ru.finney.pet.ui.pet.glassesPlacement
import ru.finney.pet.ui.pet.hatPlacement
import ru.finney.pet.ui.pet.skin
import ru.finney.pet.domain.model.BodyColor
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import kotlin.math.cos
import kotlin.math.sin
import ru.finney.pet.ui.theme.FinneyBlue
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPink
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.theme.RadiusCard
import ru.finney.pet.ui.theme.StrokeThin
import ru.finney.pet.ui.theme.StrokeRegular
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx

// Экран плана (ТЗ п. 2.5.5). Суммы набираются кнопками «плюс» и «минус» (AmountStepper),
// и в каждое из трёх направлений нужно положить хоть что-то.

private const val STEP = AMOUNT_STEP

@Composable
fun BudgetScreen(
    onBack: () -> Unit,
    onOpenShop: () -> Unit = onBack,
    onOpenGoals: () -> Unit = onBack,
    onOpenTasks: () -> Unit = onBack,
    viewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // План подтверждён — часть дохода сразу ушла в копилку: монета падает в неё.
    val sounds = LocalSounds.current
    var plannedSavings by remember { mutableStateOf(0) }
    LaunchedEffect(state) {
        when (val s = state) {
            is BudgetUiState.Planning -> plannedSavings = s.savings
            is BudgetUiState.Active -> {
                if (plannedSavings > 0) sounds.play(Sfx.Correct)
                plannedSavings = 0
            }
            BudgetUiState.Loading -> Unit
        }
    }

    when (val s = state) {
        BudgetUiState.Loading -> FinneyScreen {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FinneyInk)
            }
        }
        is BudgetUiState.Planning -> PlanningContent(
            state = s,
            onNeedsChange = viewModel::setNeeds,
            onWantsChange = viewModel::setWants,
            onSavingsChange = viewModel::setSavings,
            onSelectGoal = viewModel::selectGoal,
            onRepeatLast = viewModel::repeatLastPlan,
            onConfirm = viewModel::confirm,
            onBack = onBack,
        )
        is BudgetUiState.Active -> ActiveContent(state = s, onBack = onBack,
            onOpenShop = onOpenShop, onOpenGoals = onOpenGoals, onOpenTasks = onOpenTasks)
    }
}

@Composable
internal fun PlanningContent(
    state: BudgetUiState.Planning,
    onNeedsChange: (Int) -> Unit,
    onWantsChange: (Int) -> Unit,
    onSavingsChange: (Int) -> Unit,
    onSelectGoal: (String) -> Unit,
    onRepeatLast: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val animations = LocalAnimations.current
    val scroll = rememberScrollState()
    var savingsTop by remember { mutableIntStateOf(0) }
    var awaitingGoal by remember { mutableStateOf(false) }
    LaunchedEffect(state.goalLabel) {
        if (state.goalLabel != null && awaitingGoal) {
            awaitingGoal = false
            if (animations) scroll.animateScrollTo(savingsTop, tween(320)) else scroll.scrollTo(savingsTop)
        }
    }
    FinneyScreen(
        scrollable = false,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        onClose = onBack,
        // Круг — в шапке, а не в прокрутке: на него смотрят, пока жмут «+» в части
        // внизу, и он должен быть виден всегда (плейтест).
        top = { PlanHeader(state) },
        bottom = {
            FinneyButton(text = "Подтвердить план", onClick = onConfirm, enabled = state.canConfirm)
        },
    ) {
        Column(Modifier.fillMaxSize().fadingScroll(scroll), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Зачем план — до того, как что-то раскладывать. Плейтест 29.09: «так и не
        // понимаю, зачем нужен план», а «не выполнил план» было загадкой в конце уровня.
        Text(
            "Планируем. Монетки пока не тратятся.",
            style = MaterialTheme.typography.bodyMedium, color = FinneyInk,
        )
        // Не «бонус»: монет за план нет. Соблюдённый план — одна из трёх звёзд итога уровня
        // (уход, план, копилка), сколько бы очков ни давал points.planMatched для истории.
        // Без значка звезды: звезда в плане уже значит «Желаемое».
        Text("Уложишься в план — звезда к уровню", style = MaterialTheme.typography.bodyLarge, color = FinneyInk)

        // Каждый уровень план собирается заново, и на плейтесте это было скучно:
        // одной кнопкой — как в прошлый раз, а дальше подправить «−/+».
        if (state.canRepeatLast) {
            FinneyQuietButton(
                text = "Как в прошлый раз",
                onClick = onRepeatLast,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Шаг помещается в остаток — значит, добавлять ещё можно.
        val canAdd = state.remainder >= STEP
        // Первый план: зачем каждая часть, одной фразой от Финни прямо в строке (чек-лист 20.10).
        // Дальше фразы уходят, остаётся «?» у строки.
        val first = state.periodNumber == 1
        val savingsSays = "Откладывай понемногу, и мы накопим на мечту."

        AmountRow(
            icon = categoryIcon(Category.NEEDS),
            label = "Нужное",
            pictures = state.needsItems.mapNotNull(::itemArt),
            value = state.needs,
            onChange = onNeedsChange,
            canAdd = canAdd,
            budget = state.budget,
            minimum = state.needsHint?.takeIf { it > 0 },
            subtitle = "Еда и уход",
            finneySays = if (first) "Без еды и ванны мне плохо. Это сначала!" else null,
            helpText = state.needsHelp,
        )
        AmountRow(
            icon = categoryIcon(Category.WANTS),
            label = "Желаемое",
            pictures = state.wantsItems.mapNotNull(::itemArt),
            value = state.wants,
            subtitle = "На радости. Можно не тратить",
            finneySays = if (first) "Игрушки и сладости меня радуют, но могут подождать." else null,
            helpText = state.wantsHelp,
            onChange = onWantsChange,
            canAdd = canAdd,
            budget = state.budget,
        )
        // Без выбранной цели откладывать некуда: цель выбирается прямо здесь,
        // а не на другом экране, — иначе план было бы не собрать.
        // Рука показывает на весь выбор, а не на первую цель: на что копить, ребёнок
        // решает сам (плейтест 29.09 — «игра показывает, на что копить»).
        AnimatedContent(
            targetState = state,
            contentKey = { it.goalLabel != null },
            modifier = Modifier.fillMaxWidth().testTag("plan-savings-transition")
                .onGloballyPositioned { savingsTop = it.positionInParent().y.toInt() },
            transitionSpec = {
                val fade = if (animations) fadeIn(tween(160, delayMillis = 100)) togetherWith fadeOut(tween(100))
                    else EnterTransition.None togetherWith ExitTransition.None
                fade.using(SizeTransform { _, _ -> tween(if (animations) 320 else 0) })
            },
            label = "Выбор цели в плане",
        ) { target ->
            val interactive = !animations || transition.currentState == transition.targetState
        if (target.goalLabel == null) {
            FinneyCard {
                CategoryTitle(SavingsIcon, "Копилка", helpText = target.savingsHelp)
                if (first) Text("Финни: «$savingsSays»", style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
                Text("На что копим? Выбери сам — любую:", style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
                target.goals.forEach { goal ->
                    GoalChoice(goal, onClick = { if (interactive) { awaitingGoal = true; onSelectGoal(goal.id) } })
                }
            }
        } else {
            AmountRow(
                icon = SavingsIcon,
                label = "Копилка",
                pictures = listOfNotNull(target.goalReward?.let { itemArt(it) ?: rewardArt(it) }),
                value = target.savings,
                onChange = { if (interactive) onSavingsChange(it) },
                canAdd = target.remainder >= STEP,
                budget = target.budget,
                subtitle = if (target.reserveMode) "Запас: монеты на потом" else "Цель: «${target.goalLabel}»",
                finneySays = if (first && !target.reserveMode) savingsSays else null,
                helpText = target.savingsHelp,
                )
        }

        }

        FeedbackSound(feedback = null, rejection = state.rejection)
        state.rejection?.let { RejectionNote(it) }

        // Почему кнопка погашена — по пунктам и значком, а не одним серым цветом.
        // Чего не хватает, решает состояние из ViewModel, экран только показывает.
        if (state.missing.isNotEmpty()) MissingList(state.missing.take(1))
        ContextHelp(
            label = "Зачем нужен план?", title = "План",
            text = "Реши, сколько оставить на еду и уход, желания и выбранную цель. «Желаемое» — лимит, его можно не тратить. После подтверждения купи нужное в магазине, а взнос сделай в копилке. Выполненный план помогает пройти уровень.",
        )
        }
    }
}

/** Сумма в середине круга: монетка над числом — рядом в дырку круга они не влезают. */
@Composable
private fun BudgetCenter(budget: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "Есть $budget финок" },
    ) {
        Coin(size = 26.dp)
        OutlinedText(budget.toString(), style = MaterialTheme.typography.titleLarge)
    }
}

/** Заголовок части: значок части (тот же, что на круге) и слово. */
@Composable
private fun CategoryTitle(
    icon: FinneyIcons,
    label: String,
    modifier: Modifier = Modifier,
    helpText: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        FinneyIcon(icon, size = 30.dp)
        Text(label, style = MaterialTheme.typography.titleMedium, color = FinneyInk, modifier = Modifier.weight(1f))
        helpText?.let { ContextHelpIcon(title = label, text = it) }
    }
}

/**
 * Одна часть плана: значок и слово, ниже сумма с кнопками, а над суммой — рисунки
 * того, что на неё покупают. Плейтест: «наглядно показать, что тут речь о еде, мыле»,
 * поэтому у «Нужного» яблоко, суп и мыло, а у копилки — вещь цели.
 *
 * [minimum] — сколько нужно хотя бы: отметкой на шкале ([MinimumBar]), а не строкой
 * текста, которая «плохо сидела» рядом с заголовком.
 *
 * [canAdd] — есть ли ещё нераспределённые деньги. Когда бюджет разобран весь,
 * «плюс» гаснет во всех строках сразу: ребёнок не составит план, который
 * домен всё равно отклонит, и объяснять отказ не придётся.
 */
@Composable
private fun AmountRow(
    icon: FinneyIcons,
    label: String,
    pictures: List<Int>,
    value: Int,
    onChange: (Int) -> Unit,
    canAdd: Boolean,
    budget: Int,
    minimum: Int? = null,
    subtitle: String? = null,
    finneySays: String? = null,
    helpText: String? = null,
) {
    FinneyCard {
        CategoryTitle(icon, label, helpText = helpText)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FinneyInk) }
        finneySays?.let { Text("Финни: «$it»", style = MaterialTheme.typography.bodyLarge, color = FinneyInk) }
        // Рисунки — над суммой, между «−» и «+»: что покупают на эти монеты, стоит
        // прямо на них, а не у края карточки. Диктору они не нужны: слово он уже прочёл.
        AmountStepper(label = label, value = value, onChange = onChange, canAdd = canAdd, step = STEP, pointPlus = false) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                pictures.forEach { art ->
                    Image(painter = painterResource(art), contentDescription = null, modifier = Modifier.size(40.dp))
                }
            }
        }
        minimum?.let { MinimumBar(value = value, minimum = it, budget = budget) }
    }
}

/**
 * Шкала части с отметкой «хотя бы»: заливка — сколько положено из всего бюджета,
 * черта — минимум, под ней число. Дотянул до черты — в конце «✓», не дотянул — «!»:
 * не только цветом (ТЗ п. 3.6).
 */
@Composable
private fun MinimumBar(value: Int, minimum: Int, budget: Int) {
    val total = budget.coerceAtLeast(1)
    val shown by animateFloatAsState((value.toFloat() / total).coerceIn(0f, 1f), label = "part")
    val mark = (minimum.toFloat() / total).coerceIn(0f, 1f)
    val enough = value >= minimum
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = if (enough) "Хватает: нужно хотя бы $minimum" else "Нужно хотя бы $minimum, положено $value"
            },
    ) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val barHeight = 16.dp
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(50))
                    .background(FinneyCream)
                    .border(StrokeRegular, FinneyInk, RoundedCornerShape(50)),
            ) {
                drawRect(FinneyYellow, size = size.copy(width = size.width * shown))
            }
            // Черта минимума выходит за полосу сверху и снизу — её видно и поверх заливки.
            val x = maxWidth * mark
            Box(
                Modifier
                    .offset(x = x - StrokeBold / 2, y = (-4).dp)
                    .size(width = StrokeBold, height = barHeight + 8.dp)
                    .background(FinneyInk, RoundedCornerShape(50)),
            )
            // Подпись под чертой, но не за краем шкалы: у самого края она сдвигается внутрь.
            val labelWidth = 110.dp
            Text(
                text = "хотя бы $minimum",
                style = MaterialTheme.typography.bodyMedium,
                color = FinneyInk,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .padding(top = barHeight + 6.dp)
                    .offset(x = (x - labelWidth / 2).coerceIn(0.dp, (maxWidth - labelWidth).coerceAtLeast(0.dp)))
                    .width(labelWidth),
            )
        }
        if (enough) CheckBadge(size = 26.dp) else WarningBadge(size = 26.dp)
    }
}

/**
 * Цель на выбор: рисунок награды, название и цена. Плейтест: текстовые кнопки
 * «Ковбойская шляпа · 50» не показывали, что это за вещь. Рисунок — по `reward` цели,
 * тем же путём, что в гардеробе и на экране целей; у цели без рисунка — только слова.
 */
@Composable
private fun GoalChoice(goal: Goal, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(RadiusCard))
            .background(FinneyCream)
            .border(StrokeRegular, FinneyInk, RoundedCornerShape(RadiusCard))
            .clickable(role = Role.Button, onClickLabel = "Выбрать цель", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${goal.label}, цена ${goal.price}" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        goal.rewardArt?.let {
            Image(painter = painterResource(it), contentDescription = null, modifier = Modifier.size(56.dp))
        }
        Text(goal.label, style = MaterialTheme.typography.titleMedium, color = FinneyInk, modifier = Modifier.weight(1f))
        CoinAmount(amount = goal.price, coinSize = 26.dp)
    }
}

/** Чего не хватает до «Подтвердить» — по пункту на строку, у каждого значок. */
@Composable
private fun MissingList(items: List<String>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Чтобы подтвердить:", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WarningBadge(size = 24.dp)
                Text(item, style = MaterialTheme.typography.bodyLarge, color = FinneyInk, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Шапка плана: круг слева, справа — заголовок и сколько ещё не разложено.
 * Сколько можно распределить — в середине круга: это главное число экрана.
 * Круг меняется с каждым «−/+» — видно, какая часть от всего уходит куда
 * (плейтест: «диаграмму прикольно сделать»).
 */
@Composable
private fun PlanHeader(state: BudgetUiState.Planning) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        PlanDonut(
            budget = state.budget,
            needs = state.needs,
            wants = state.wants,
            savings = state.savings,
            diameter = 112.dp,
        ) { BudgetCenter(state.budget) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedText("План", style = MaterialTheme.typography.headlineMedium)
            RemainderNote(state.remainder)
        }
    }
}

/**
 * Остаток — пустая дуга круга, словами. «Не разложено» — просто число, без цвета:
 * зелёное читалось как похвала. «Не хватает» — предупреждение: голубое и с «!»
 * (ТЗ п. 3.6 — не только цветом).
 */
@Composable
private fun RemainderNote(remainder: Int) {
    val over = remainder < 0
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(RadiusCard))
            .background(if (over) FinneyBlue else FinneyCream)
            .border(StrokeRegular, FinneyInk, RoundedCornerShape(RadiusCard))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (over) WarningBadge(size = 24.dp)
            Text(
                text = when {
                    over -> "Не хватает"
                    remainder == 0 -> "Всё разложено"
                    else -> "Не разложено"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = FinneyInk,
            )
        }
        // Число — всегда, и ноль тоже: ТЗ п. 2.5.5 требует показывать остаток, а не только слова.
        CoinAmount(amount = if (over) -remainder else remainder)
    }
}

/** Отказ домена, переведённый на детский язык. Тексты потом заменит feedback.json @vsvsokol. */
@Composable
private fun RejectionNote(rejection: Rejection) {
    val text = when (rejection) {
        is Rejection.PlanExceedsBudget ->
            "Ты разделил ${rejection.planned}, а есть только ${rejection.budget}. Убавь что-нибудь."
        is Rejection.InsufficientFunds -> "Не хватает ${rejection.shortage} финок."
        Rejection.InvalidAmount -> "Так не получится: суммы не могут быть меньше нуля."
        Rejection.PlanAlreadyConfirmed -> "План на этот уровень уже готов."
        Rejection.NoActiveGoal -> "Выбери цель — тогда будет куда откладывать."
        Rejection.PlanMissingDirection -> "Положи хоть немного в каждую часть: нужное, желаемое и копилку."
        else -> "Так пока нельзя."
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = FinneyInk,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusCard))
            .background(FinneyPink)
            .border(StrokeRegular, FinneyInk, RoundedCornerShape(RadiusCard))
            .padding(12.dp),
    )
}

@Composable
internal fun ActiveContent(state: BudgetUiState.Active, onBack: () -> Unit,
    onOpenShop: () -> Unit = onBack, onOpenGoals: () -> Unit = onBack, onOpenTasks: () -> Unit = onBack) {
    val report = state.report
    // «Дальше» внизу, а не только «✕»: сразу после подтверждения ребёнок попадает
    // сюда, и на плейтесте не понял, что крестик и есть путь дальше, в комнату.
    FinneyScreen(
        scrollable = true,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        onClose = onBack,
        bottom = { FinneyButton(text = "Дальше", onClick = onBack) },
    ) {
        OutlinedText("План и факт", style = MaterialTheme.typography.headlineLarge)
        Text("Черта — план. Заливка — что уже потратил или отложил. Монеты из плана сами не уходят.",
            style = MaterialTheme.typography.bodyLarge, color = FinneyInk)

        // Уровень и деньги — одной строкой сверху, как в редизайне 28.09: раньше «Сейчас
        // у тебя» стояло под панелью, и до него не долистывали.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                OutlinedText("Уровень", style = MaterialTheme.typography.titleLarge)
                LevelBadge(level = state.level, size = 44.dp)
            }
            // Монетка с числом в плашке, как кошелёк на главном: так видно, что это деньги сейчас.
            CoinAmount(
                amount = state.balance,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .border(StrokeRegular, FinneyInk, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }

        // Банки вместо пар полос «Собирался / Потратил»: черта — задумал, заливка — вышло.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RadiusCard))
                .background(Color.White)
                .border(StrokeRegular, FinneyInk, RoundedCornerShape(RadiusCard))
                .padding(vertical = 16.dp),
        ) {
            PlanJars(report.plan, report.facts)
        }

        // Вывод говорит сам питомец: плейтест спрашивал, «что я должен понять» из чисел, —
        // вот это одной фразой. Уровень ещё идёт, это не итог, поэтому без зелёного и розового.
        // Как в редизайне 28.09: одна голова питомца слева внизу, над ней справа — овальный
        // пузырь, хвостик которого смотрит на голову.
        // Пузырь начинается выше головы, а не над ней целиком: так пара занимает высоту
        // головы, и на 360 dp экран помещается без прокрутки.
        Row(modifier = Modifier.fillMaxWidth()) {
            PetHead(state.character, state.bodyColor, state.worn, HeadSize, Modifier.padding(top = HeadSize * 0.3f))
            SpeechBalloon(verdictSpeech(state.verdict), Modifier.weight(1f))
        }
        when (state.verdict) {
            is PlanVerdict.SavingsShort -> FinneyButton("Отложить в копилку", onClick = onOpenGoals)
            is PlanVerdict.Overspent -> {
                Text("Нужное всё равно можно купить. Заработай монеты в игре: доход сверх плана покроет перерасход. Или возьми из копилки — цель станет дальше.",
                    style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
                FinneyButton("Заработать в игре", onClick = onOpenTasks)
                FinneyQuietButton("Открыть копилку", onClick = onOpenGoals)
            }
            else -> FinneyButton("В магазин", onClick = onOpenShop)
        }
    }
}

/** Ширина головы питомца у реплики «План и факт». */
private val HeadSize = 136.dp

/** Голова на холсте питомца кончается на 0.84 его высоты — ниже пусто, место не занимаем. */
private const val HeadBottom = 0.84f

/**
 * Только голова: базовый слой питомца без туловища, рук и ног, как в редизайне.
 * Слой лица по холсту совпадает с остальными, поэтому шляпа и очки садятся на него
 * так же, как на целого питомца. Картинка неподвижна: реплика читается, а не играется.
 */
@Composable
private fun PetHead(character: PetCharacter, bodyColor: BodyColor, worn: List<String>, size: Dp, modifier: Modifier = Modifier) {
    val skin = character.skin
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .width(size)
            .height(size * HeadBottom)
            .clearAndSetSemantics {},
    ) {
        Box(Modifier.requiredSize(size)) {
            Image(
                painter = painterResource(skin.face(PetMood.HAPPY).base),
                contentDescription = null,
                colorFilter = bodyColor.colorFilter,
                modifier = Modifier.matchParentSize(),
            )
            // Очки раньше шляпы — тот же порядок, что у целого питомца.
            worn.mapNotNull(::accessoryArt).sortedBy { !it.onEyes }.forEach { art ->
                Image(
                    painter = painterResource(art.res),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = if (art.onEyes) Modifier.glassesPlacement(skin, art) else Modifier.hatPlacement(skin.hat, art.brim),
                )
            }
        }
    }
}

/**
 * Пузырь реплики из редизайна: белый овал с обводкой и изогнутый хвостик слева внизу.
 * Овал и хвостик — одна фигура (объединение путей), чтобы обводка не шла поперёк хвостика.
 */
@Composable
private fun SpeechBalloon(text: String, modifier: Modifier = Modifier) {
    val stroke = StrokeRegular
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .drawWithCache {
                val t = BalloonTail.toPx()
                val body = Rect(t * 0.8f, 0f, size.width, size.height - t)
                val rx = body.width / 2
                val ry = body.height / 2
                fun onOval(degrees: Float): Offset {
                    val a = Math.toRadians(degrees.toDouble())
                    return Offset(body.center.x + rx * cos(a).toFloat(), body.center.y + ry * sin(a).toFloat())
                }
                val from = onOval(118f)
                val to = onOval(150f)
                val tip = Offset(0f, size.height)
                val tail = Path().apply {
                    moveTo(from.x, from.y)
                    quadraticTo(from.x - t * 0.4f, tip.y - t * 0.2f, tip.x, tip.y)
                    quadraticTo(to.x - t * 0.2f, to.y + t * 0.6f, to.x, to.y)
                    close()
                }
                val outline = Path().apply { op(Path().apply { addOval(body) }, tail, PathOperation.Union) }
                val ink = Stroke(stroke.toPx(), join = StrokeJoin.Round)
                onDrawBehind {
                    drawPath(outline, Color.White)
                    drawPath(outline, FinneyInk, style = ink)
                }
            }
            .padding(start = BalloonTail + 18.dp, end = 18.dp, top = 18.dp, bottom = BalloonTail + 18.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = FinneyInk,
            textAlign = TextAlign.Center,
        )
    }
}

/** Сколько места под пузырём и слева от него занимает хвостик. */
private val BalloonTail = 20.dp

/** Вывод «План и факт» репликой питомца — коротко, как говорят вслух (ТЗ п. 2.5.9). */
private fun verdictSpeech(verdict: PlanVerdict): String = when (verdict) {
    PlanVerdict.OnTrack -> "Ура, всё по плану!"
    is PlanVerdict.Overspent -> "Ой, на ${verdict.amount} больше плана!"
    is PlanVerdict.SavingsShort -> "Ещё ${verdict.amount} в копилку!"
    PlanVerdict.OffTrack -> "Пока не по плану. Смотри на черту!"
}

@Preview(showBackground = true, backgroundColor = 0xFFFFEDCD)
@Composable
private fun PlanningContentPreview() {
    FinneyTheme {
        PlanningContent(
            state = BudgetUiState.Planning(
                periodNumber = 1, level = 1, budget = 50, needs = 30, wants = 10, savings = 10,
                needsHint = 30, goalLabel = "Ковбойская шляпа", rejection = null, isSaving = false,
                needsItems = listOf("food_apple", "food_bowl", "care_soap"),
                wantsItems = listOf("treat_candy", "toy_ball", "toy_book"),
                goalReward = "hat_cowboy",
                lastPlan = Plan(budget = 50, needs = 25, wants = 15, savings = 10),
            ),
            onNeedsChange = {}, onWantsChange = {}, onSavingsChange = {}, onSelectGoal = {}, onRepeatLast = {}, onConfirm = {}, onBack = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFEDCD)
@Composable
private fun ActiveContentPreview() {
    FinneyTheme {
        ActiveContent(
            state = BudgetUiState.Active(
                periodNumber = 1,
                level = 1,
                report = PlanReport(
                    plan = Plan(budget = 50, needs = 30, wants = 10, savings = 10),
                    facts = PeriodFacts(needs = 25, wants = 10, savings = 10, unplannedIncome = 0),
                    overspend = 0,
                    onTrack = true,
                ),
                balance = 5,
            ),
            onBack = {},
        )
    }
}
