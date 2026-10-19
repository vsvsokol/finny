package ru.finney.pet.ui.goals

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.finney.pet.domain.game.GoalProgress
import ru.finney.pet.domain.model.Goal
import ru.finney.pet.ui.components.AMOUNT_STEP
import ru.finney.pet.ui.components.AmountStepper
import ru.finney.pet.ui.components.Coin
import ru.finney.pet.ui.components.ContextHelp
import ru.finney.pet.ui.components.FinneyCard
import ru.finney.pet.ui.components.CoinAmount
import ru.finney.pet.ui.components.FeedbackDialog
import ru.finney.pet.ui.components.fadingScroll
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.components.FillBar
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.FinneyPanel
import ru.finney.pet.ui.components.FinneyScreen
import ru.finney.pet.ui.components.MenuBackdrop
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.pet.accessoryArt
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneySand
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx

// Сумма набирается тем же «минус — плюс», что на экране плана, и уходит одним нажатием.

@Composable
fun GoalsScreen(
    onBack: () -> Unit,
    viewModel: GoalsViewModel = viewModel(factory = GoalsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        GoalsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = FinneyInk)
        }

        is GoalsUiState.Ready -> GoalsContent(
            state = s,
            onSelect = viewModel::selectGoal,
            onDeposit = viewModel::deposit,
            onWithdraw = viewModel::askWithdraw,
            onComplete = viewModel::completeGoal,
            onBack = onBack,
        )
    }

    (state as? GoalsUiState.Ready)?.let { ready ->
        val sounds = LocalSounds.current
        LaunchedEffect(ready.savingsMove?.id) {
            val move = ready.savingsMove ?: return@LaunchedEffect
            sounds.play(if (move.delta > 0) Sfx.PiggyIn else Sfx.PiggyOut)
        }
        ready.pendingWithdraw?.let {
            WithdrawConfirmDialog(
                pending = it,
                goalLabel = ready.progress?.goal?.label.orEmpty(),
                goalPrice = ready.progress?.goal?.price ?: 0,
                reserveMode = ready.reserveMode,
                onConfirm = viewModel::confirmWithdraw,
                onCancel = viewModel::cancelWithdraw,
            )
        }
        // Итог есть только у забранной цели — у неё своё окно, праздничное.
        ready.feedback?.let { GoalDoneDialog(it, onDismiss = viewModel::dismissFeedback) }
        FeedbackDialog(
            feedback = null,
            rejection = ready.rejection,
            onDismiss = viewModel::dismissFeedback,
        )
    }
}

/**
 * Снятие с копилки — только после отдельного подтверждения, и до него видно,
 * сколько останется и как сдвинется срок (ТЗ п. 2.5.7).
 */
@Composable
private fun WithdrawConfirmDialog(
    pending: PendingWithdraw,
    goalLabel: String,
    goalPrice: Int,
    reserveMode: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val p = pending.preview
    Dialog(onDismissRequest = onCancel) {
        FinneyPanel(title = "Взять ${pending.amount}?") {
            Text(
                text = if (reserveMode) "Монеты вернутся в кошелёк. В запасе останется меньше."
                    else "Это деньги на «$goalLabel». Если взять, до цели станет дальше.",
                style = MaterialTheme.typography.bodyLarge,
                color = FinneyInk,
            )
            InfoRow("В копилке", "${p.savedBefore} → ${p.savedAfter}")
            // То же «станет меньше» картинкой: уходящая часть заштрихована крестом.
            if (goalPrice > 0) {
                FillBar(
                    value = p.savedBefore,
                    max = goalPrice,
                    pending = p.savedBefore - p.savedAfter,
                    removing = true,
                    modifier = Modifier.fillMaxWidth(),
                    description = "В копилке ${p.savedBefore} из $goalPrice",
                )
            }
            // В первом периоде темпа пополнений ещё нет — срок не показываем вовсе.
            if (!reserveMode && (p.periodsBefore != null || p.periodsAfter != null)) {
                InfoRow(
                    "Уровней до цели",
                    "${p.periodsBefore?.toString() ?: "—"} → ${p.periodsAfter?.toString() ?: "—"}",
                )
            }
            FinneyButton(text = "Оставить в копилке", onClick = onCancel)
            FinneyButton(text = "Взять ${pending.amount}", onClick = onConfirm)
        }
    }
}

@Composable
internal fun GoalsContent(
    state: GoalsUiState.Ready,
    onSelect: (String) -> Unit,
    onDeposit: (Int) -> Unit,
    onWithdraw: (Int) -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    var amount by rememberSaveable {
        mutableIntStateOf(state.planSavingsLeft.coerceAtMost(state.balance).coerceAtLeast(AMOUNT_STEP))
    }
    var choosingGoal by rememberSaveable { mutableStateOf(false) }
    val animations = LocalAnimations.current
    // Закрываем выбор после подтверждённой смены цели, а не до ответа хранилища.
    LaunchedEffect(state.progress?.goal?.id) { choosingGoal = false }
    BackHandler(enabled = choosingGoal && state.progress != null) { choosingGoal = false }
    FinneyScreen(backdrop = MenuBackdrop.SHAPES, scrollable = false,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        onClose = { if (choosingGoal && state.progress != null) choosingGoal = false else onBack() }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedText(if (choosingGoal) "Выбор цели" else "Копилка", style = MaterialTheme.typography.headlineMedium)
            ContextHelp("Зачем копить?", "Копилка",
                if (state.reserveMode) "Все награды уже собраны. Можно откладывать монеты на потом и брать их из запаса, когда понадобятся. План — твоё намерение; кнопка «Положить» переводит монеты в запас."
                else "Каждый взнос приближает выбранную вещь. Сумму выбираешь ты: оставь деньги на еду и уход, а часть отложи. План — твоё намерение; кнопка «Положить» переводит монеты из кошелька в копилку.")
        }
        AnimatedContent(
            targetState = GoalsPage(state, choosingGoal),
            contentKey = { it.key },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                if (animations) fadeIn(tween(180, delayMillis = 80)) togetherWith fadeOut(tween(100))
                else EnterTransition.None togetherWith ExitTransition.None
            },
            label = "Выбор цели и копилка",
        ) { page ->
            // Пока страницы сменяют друг друга, повторный тап не должен попасть
            // в действие, которое только появилось на месте карточки цели.
            val interactive = !animations || transition.currentState == transition.targetState
            GoalsPageContent(page, amount, { if (interactive) amount = it }, onSelect = {
                if (interactive) {
                    if (it == state.progress?.goal?.id) choosingGoal = false else onSelect(it)
                }
            }, onChooseAnother = { if (interactive) choosingGoal = true },
                onDeposit = { if (interactive) onDeposit(it) },
                onWithdraw = { if (interactive) onWithdraw(it) },
                onComplete = { if (interactive) onComplete() })
        }
    }
}

private data class GoalsPage(val state: GoalsUiState.Ready, val choosing: Boolean) {
    val key: String get() = when {
        state.reserveMode -> "reserve"
        choosing || state.progress == null -> "choose"
        else -> "goal"
    }
}

@Composable
private fun GoalsPageContent(
    page: GoalsPage,
    amount: Int,
    onAmountChange: (Int) -> Unit,
    onSelect: (String) -> Unit,
    onChooseAnother: () -> Unit,
    onDeposit: (Int) -> Unit,
    onWithdraw: (Int) -> Unit,
    onComplete: () -> Unit,
) {
    val state = page.state
    // Каждая страница начинает с собственной верхней карточки. Движения денег
    // остаются на этой странице и не сбрасывают прокрутку при пересчёте баланса.
    Column(Modifier.fillMaxSize().testTag("goals-page-${page.key}").fadingScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val progress = state.progress
        if (state.reserveMode) {
            FinneyCard {
                Text("Запас", style = MaterialTheme.typography.titleLarge, color = FinneyInk)
                Text("Все награды собраны! Теперь здесь можно откладывать монеты на потом.",
                    style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
            }
            MoneyPair(state.balance, state.reserveSaved, state.savingsMove)
            state.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FinneyInk) }
            if (state.canMoveMoney) {
                FinneyCard {
                    if (state.planSavingsLeft > 0) {
                        Text("По твоему плану — ещё ${state.planSavingsLeft}", style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
                    }
                    val limit = maxOf(state.balance, state.reserveSaved)
                    AmountStepper(label = "Сумма", value = amount,
                        onChange = { onAmountChange(it.coerceAtLeast(AMOUNT_STEP)) },
                        canAdd = amount + AMOUNT_STEP <= limit, canRemove = amount > AMOUNT_STEP)
                    SavingsSwitch(amount, onTake = { onWithdraw(amount) }, onPut = { onDeposit(amount) },
                        canTake = state.reserveSaved >= amount, canPut = state.balance >= amount)
                }
            } else {
                Text("Сначала составь план уровня.", style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
            }
            if (state.moves.isNotEmpty()) SavingsHistory(state.moves, state.savingsMove?.id)
        } else if (page.choosing || progress == null) {
            Text("Выбери, на что хочешь накопить.", style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
            CoinAmount(amount = state.balance)
            GoalList(state, onSelect)
        } else {
            FinneyCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    progress.goal.reward?.let(::accessoryArt)?.let {
                        Image(painter = painterResource(it.res), contentDescription = null, modifier = Modifier.size(56.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(progress.goal.label, style = MaterialTheme.typography.titleLarge, color = FinneyInk)
                        Text(if (progress.remaining > 0) "Осталось ${progress.remaining} монет" else "Накоплено!",
                            style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
                    }
                }
                val preview = if (state.canMoveMoney && !state.canComplete)
                    minOf(amount, state.balance, progress.remaining).coerceAtLeast(0) else 0
                SavedBar(progress.saved, progress.goal.price, pending = preview, previews = state.canMoveMoney)
                progress.periodsToGoal?.takeIf { it > 0 }?.let {
                    Text("До цели в таком темпе: уровней — $it", style = MaterialTheme.typography.labelMedium, color = FinneyInk)
                }
            }
            MoneyPair(state.balance, progress.saved, state.savingsMove)
            state.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FinneyInk) }
            when {
                state.canComplete -> FinneyButton("Забрать награду", onClick = onComplete)
                state.canMoveMoney -> FinneyCard {
                    if (state.planSavingsLeft > 0) {
                        Text("По твоему плану — ещё ${state.planSavingsLeft}", style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
                    }
                    val limit = maxOf(state.balance, progress.saved)
                    AmountStepper(label = "Сумма взноса", value = amount,
                        onChange = { onAmountChange(it.coerceAtLeast(AMOUNT_STEP)) },
                        canAdd = amount + AMOUNT_STEP <= limit, canRemove = amount > AMOUNT_STEP)
                    SavingsSwitch(amount, onTake = { onWithdraw(amount) }, onPut = { onDeposit(amount) },
                        canTake = progress.saved >= amount, canPut = state.balance >= amount)
                }
                else -> Text("Сначала составь план уровня.", style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
            }
            TextButton(onClick = onChooseAnother, modifier = Modifier.fillMaxWidth()) {
                Text("Выбрать другую цель", style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
            }
            if (state.moves.isNotEmpty()) SavingsHistory(state.moves, state.savingsMove?.id)
        }
    }
}

@Composable
private fun GoalList(state: GoalsUiState.Ready, onSelect: (String) -> Unit) {
    FinneyPanel(title = "На что копим") {
        state.goals.forEach { row ->
            GoalCard(row = row, onSelect = { onSelect(row.goal.id) })
        }
    }
}

/**
 * Выбор показан значком копилки и толстой рамкой, достигнутая цель — круглым «✓»,
 * а не только цветом (ТЗ п. 3.6). Сколько накоплено — полоской и числом.
 */
@Composable
private fun GoalCard(row: GoalRow, onSelect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (row.isActive) FinneyYellow else FinneySand)
            .border(if (row.isActive) 3.dp else 2.dp, FinneyInk, RoundedCornerShape(20.dp))
            .clickable(enabled = !row.isCompleted, onClick = onSelect)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Копят на вещь, которую видно: рисунок награды прямо в карточке цели.
            row.goal.reward?.let(::accessoryArt)?.let {
                Image(painter = painterResource(it.res), contentDescription = null, modifier = Modifier.size(56.dp))
            }
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    stateDescription = when {
                        row.isCompleted -> "уже твоя"
                        row.isActive -> "копим сюда, ${row.saved} из ${row.goal.price}"
                        else -> "${row.saved} из ${row.goal.price}"
                    }
                },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Значок — в строке названия, а не отдельной колонкой справа: полосе
                // остаётся вся ширина карточки. Место под него есть всегда.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.goal.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = FinneyInk,
                        modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        when {
                            row.isCompleted -> Box(
                                Modifier.size(32.dp).clip(CircleShape).background(FinneyGreen).border(2.dp, FinneyInk, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text("✓", style = MaterialTheme.typography.titleMedium, color = FinneyInk) }
                            row.isActive -> FinneyIcon(FinneyIcons.Piggy, size = 32.dp)
                        }
                    }
                }
                if (!row.isCompleted) SavedBar(saved = row.saved, price = row.goal.price)
            }
        }
    }
}

/**
 * Накоплено: полоска во всю ширину, под ней «30 / 100» с монеткой. Полоска — общая
 * шкала прогресса: от розового к зелёному, полная — с «✓». Число стоит под полосой,
 * а не сбоку: сбоку оно отнимало у полосы ширину, и в узкой карточке от неё
 * оставался огрызок.
 */
@Composable
private fun SavedBar(saved: Int, price: Int, pending: Int = 0, previews: Boolean = false) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.End,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = if (pending > 0) "Накоплено $saved из $price, положишь ещё $pending" else "Накоплено $saved из $price"
        },
    ) {
        FillBar(saved, price, Modifier.fillMaxWidth(), pending = pending, previews = previews)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedText("$saved / $price", style = MaterialTheme.typography.titleMedium)
            Coin(size = 20.dp)
        }
    }
}

/** Число крупно, под ним что оно значит. [coin] — число в монетах. */
@Composable
private fun StatTile(value: String, caption: String, modifier: Modifier = Modifier, coin: Boolean = false) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(FinneySand)
            .border(2.dp, FinneyInk, RoundedCornerShape(18.dp))
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedText(value, style = MaterialTheme.typography.headlineMedium)
            if (coin) Coin(size = 24.dp)
        }
        Text(caption, style = MaterialTheme.typography.bodyMedium, color = FinneyInk, textAlign = TextAlign.Center)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = FinneyInk,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = FinneyInk)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF0D5)
@Composable
private fun GoalsPreview() {
    val crown = Goal(id = "goal_crown", label = "Ковбойская шляпа", price = 50, moodBonus = 20, reward = "hat_cowboy")
    FinneyTheme {
        GoalsContent(
            state = GoalsUiState.Ready(
                goals = listOf(GoalRow(crown, saved = 20, isActive = true, isCompleted = false)),
                progress = GoalProgress(
                    goal = crown,
                    saved = 20,
                    remaining = 30,
                    periodsToGoal = 2,
                    averageDeposit = 10,
                ),
                balance = 30,
                canMoveMoney = true,
                canComplete = false,
                rejection = null,
            ),
            onSelect = {},
            onDeposit = {},
            onWithdraw = {},
            onComplete = {},
            onBack = {},
        )
    }
}
