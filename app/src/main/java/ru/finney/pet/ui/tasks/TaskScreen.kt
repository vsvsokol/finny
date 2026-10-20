package ru.finney.pet.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.finney.pet.ui.tasks.games.PetInFreeSpace
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.finney.pet.domain.model.TaskOutcome
import ru.finney.pet.domain.model.TaskTheme
import ru.finney.pet.domain.tasks.TaskEngines
import ru.finney.pet.ui.components.AlertBadge
import ru.finney.pet.ui.components.CheckBadge
import ru.finney.pet.ui.components.Coin
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIconButton
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.FinneyQuietButton
import ru.finney.pet.ui.components.FinneyScreen
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.tasks.games.Bubble
import ru.finney.pet.ui.tasks.games.GameScene
import ru.finney.pet.ui.tasks.games.LocalPlayerAccessory
import ru.finney.pet.ui.tasks.games.PetAtRight
import ru.finney.pet.ui.tasks.games.rememberPetReactions
import ru.finney.pet.ui.tasks.games.PetOffers
import ru.finney.pet.ui.tasks.games.PetSays
import ru.finney.pet.ui.tasks.games.ResultBody
import ru.finney.pet.ui.tasks.games.SceneBody
import ru.finney.pet.ui.tasks.games.SceneStage
import ru.finney.pet.ui.tasks.games.ScenePanel
import ru.finney.pet.ui.tasks.games.Tail
import ru.finney.pet.ui.tasks.games.TaskGame
import ru.finney.pet.ui.tasks.games.backdropFor
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.GapInner
import ru.finney.pet.ui.theme.StrokeThin
import ru.finney.pet.ui.theme.StrokeRegular
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.progress.coins

/** Подпись темы задания — те же три темы, что в ТЗ п. 2.5.8. */
fun TaskTheme.label(): String = when (this) {
    TaskTheme.PLANNING -> "Планирование бюджета"
    TaskTheme.SAVINGS -> "Сбережения"
    TaskTheme.SHOPPING -> "Платежи и покупки"
}

@Composable
fun TaskScreen(
    taskId: String,
    onBack: () -> Unit,
    onOpenBudget: () -> Unit = {},
    viewModel: TaskViewModel = viewModel(key = taskId, factory = TaskViewModel.factory(taskId)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        TaskUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = FinneyInk)
        }

        TaskUiState.NotFound -> FinneyScreen(onClose = onBack) {
            OutlinedText("Такой игры нет", style = MaterialTheme.typography.headlineMedium)
        }

        is TaskUiState.Ready -> CompositionLocalProvider(
            LocalPlayerAccessory provides s.worn,
        ) {
            // Фон один на вступление, игру и итог — меняется плавно, а не с каждым экраном.
            SceneStage(initial = backdropFor(s.task, finished = s.phase == TaskPhase.RESULT)) {
                when (s.phase) {
                    TaskPhase.INTRO -> TaskIntro(s, onStart = viewModel::start, onBack = onBack, onOpenBudget = onOpenBudget)
                    // Крестик и системное «назад» посреди игры закрывают её: до итога ничего не засчитано.
                    TaskPhase.PLAY -> key(s.attempt) {
                        TaskPlay(s, viewModel::inputSeen, onBack, viewModel::submit)
                    }
                    TaskPhase.RESULT -> TaskResultScene(s, onReplay = viewModel::replay, onDone = onBack)
                }
            }
        }
    }
}

@Composable
internal fun TaskPlay(state: TaskUiState.Ready, onInputSeen: () -> Unit,
    onBack: () -> Unit, onSubmit: (ru.finney.pet.domain.tasks.TaskInput) -> Unit) {
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        TaskGame(state.task, state.character, state.balance, state.inputError, onInputSeen, onBack, onSubmit)
        FinneyIconButton(onClick = { helpOpen = true }, contentDescription = "Как играть", size = 48.dp,
            modifier = Modifier.align(Alignment.TopCenter).systemBarsPadding().padding(top = 8.dp)) {
            FinneyIcon(FinneyIcons.Help, size = 24.dp)
        }
    }
    if (helpOpen) GameHelpDialog(state.task) { helpOpen = false }
}

/** Вступление в той же сцене, что игра: питомец рассказывает, что делать. */
@Composable
internal fun TaskIntro(state: TaskUiState.Ready, onStart: () -> Unit, onBack: () -> Unit, onOpenBudget: () -> Unit) {
    GameScene(backdrop = backdropFor(state.task), onClose = onBack, money = state.balance) {
        // Питомец стоит внизу, на полу сцены, а «Играть» прижата к краю —
        // раньше всё собиралось наверху, и под кнопкой оставалось полэкрана пустоты.
        SceneBody(
            horizontalAlignment = Alignment.CenterHorizontally,
            // Без плана — не «Играть», а путь к плану: уровень начинается с него,
            // и игры не превращаются в перебор ради монет.
            bottom = {
                when {
                    !state.available -> Unit
                    state.needsPlan -> FinneyButton(text = "Составить план", onClick = onOpenBudget)
                    else -> FinneyButton(text = "Играть", onClick = onStart)
                }
            },
        ) {
            val density = LocalDensity.current
            var titleHeight by remember { mutableStateOf(0.dp) }
            var cardHeight by remember { mutableStateOf(0.dp) }
            OutlinedText(
                state.task.title,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.onSizeChanged { titleHeight = with(density) { it.height.toDp() } },
            )
            // Без заголовка на рамке: персиковое «Что делать» стояло вплотную к панели и
            // спорило с названием игры того же цвета (плейтест 29.09). Что это правила,
            // говорят номера шагов. Тема ребёнку ничего не говорит — она у взрослого.
            // Карточка «Ты — … · Сделай … · Получишь …» (плейтест 02.10): ребёнок не понимал,
            // кто он в игре и зачем играть. Поэтому видно и то, что игра даёт уровню.
            ScenePanel(title = null, modifier = Modifier.fillMaxWidth().onSizeChanged { cardHeight = with(density) { it.height.toDp() } }) {
                Text(gameHelp(state.task).role, style = MaterialTheme.typography.titleMedium, color = FinneyInk)
                CardLabel("Сделай")
                IntroSteps(state.task.intro)
                CardLabel("Получишь")
                Text(rewardLine(state), style = MaterialTheme.typography.titleMedium, color = FinneyInk)
                Text(progressLine(state), style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
                Text(
                    "Монеты внутри игры — учебные.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinneyInk,
                )
            }
            // Сколько дадут — монеткой в пузыре, а не фразой. Уже пройдено — просто «ещё?».
            // У короткой карточки питомец до 240 dp, у длинной заходит на её угол.
            PetInFreeSpace(above = titleHeight + cardHeight + 12.dp, min = 160.dp, max = 240.dp, maxLift = 48.dp) { size, modifier ->
                if (state.completed) {
                    PetSays(state.character, "Сыграем ещё?", modifier, petSize = size)
                } else {
                    PetOffers(state.character, state.reward.success, modifier, petSize = size)
                }
            }
            if (state.available && state.needsPlan) {
                ScenePanel(title = null, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Сначала составь план уровня — потом играть.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = FinneyInk,
                    )
                }
            }
            if (!state.available) {
                ScenePanel(title = null, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (state.lockedUntilLevel != null) {
                            "Эта игра откроется на уровне ${state.lockedUntilLevel}. Проходи уровни: план, забота о питомце, копилка."
                        } else {
                            "Эта игра откроется на следующих уровнях."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = FinneyInk,
                    )
                }
            }
        }
    }
}

/** Подпись части карточки перед игрой: «Сделай», «Получишь». */
@Composable
private fun CardLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = FinneyInk.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 4.dp).semantics { heading() })
}

/** Что попадёт в кошелёк. Правила — как в [ru.finney.pet.domain.game.Game.submitTask]. */
internal fun rewardLine(state: TaskUiState.Ready): String {
    val r = state.reward
    return when {
        state.completed -> "Награда уже в кошельке. Сыграй ещё — для тренировки."
        state.firstTry && r.fail > 0 ->
            "+${coins(r.success)} в кошелёк Финни. Не выйдет — всё равно\u00A0+${r.fail}."
        else -> "+${coins(r.success)} в кошелёк Финни."
    }
}

/** Как игра двигает уровень: игру уровня нужно пройти, остальные дают монеты на план и копилку. */
internal fun progressLine(state: TaskUiState.Ready): String = when {
    !state.isLevelGame -> "Монеты пригодятся для плана и копилки."
    state.levelGamePassed -> "Это игра уровня, она уже пройдена."
    state.levelGameRequired -> "Это игра уровня: пройди её, чтобы закрыть уровень."
    // В демо игра уровня не обязательна — не обещаем, что без неё уровень не закрыть.
    else -> "Это игра уровня."
}

/**
 * Вступление по шагам: каждое предложение — своей строкой и со своим номером.
 * Плейтест 29.09: «Финни копит на самокат. Каждый день…» сплошным абзацем дети
 * 7–11 лет читали через силу, а с новой строки каждая мысль видна отдельно.
 */
@Composable
private fun IntroSteps(intro: String) {
    val steps = introSentences(intro)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { i, sentence ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Номер — только когда шагов больше одного: одинокая «1» ничего не считает.
                if (steps.size > 1) StepNumber(i + 1)
                Text(sentence, style = MaterialTheme.typography.titleMedium, color = FinneyInk, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Предложения вступления. Режем после «.», «!» и «?», за которыми идёт пробел и
 * заглавная буква, — «10 монет. Каждый» разрежется, а «т. е.» и «3.5» нет.
 */
internal fun introSentences(intro: String): List<String> =
    intro.split(Regex("""(?<=[.!?…])\s+(?=[«"A-ZА-ЯЁ0-9])""")).map { it.trim() }.filter { it.isNotEmpty() }

/** Номер шага в кружке: белый с обводкой, как пустая отметка, — не кнопка. */
@Composable
private fun StepNumber(n: Int) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(StrokeThin, FinneyInk, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text("$n", style = MaterialTheme.typography.titleSmall, color = FinneyInk)
    }
}

/**
 * Итог в сцене игры: что получилось по пунктам, объяснение при любом исходе и
 * награда (ТЗ п. 2.5.8, 2.5.9). Без стыда: «почти», а не «проиграл».
 *
 * Три отдельных блока, а не одна панель: плейтест назвал итог «стеной текста».
 * В панели — только пункты, и удачные в ней тихие, а неудачные подсвечены
 * ([ResultBody]); награда — своей плашкой; объяснение говорит питомец.
 */
@Composable
internal fun TaskResultScene(state: TaskUiState.Ready, onReplay: () -> Unit, onDone: () -> Unit) {
    val result = state.result ?: return
    val success = result.outcome == TaskOutcome.SUCCESS
    // Джингл итога, следом — монеты награды летят в кошелёк. Раз на попытку.
    val sounds = LocalSounds.current
    val flight = remember(state.attempt) { RewardFlight(result.reward) }
    LaunchedEffect(state.attempt) {
        sounds.play(if (success) Sfx.GameWin else Sfx.GameFail)
        flight.play(sounds)
    }
    val density = LocalDensity.current
    // Монетка кошелька — у его правого края: поле плашки и половина монетки.
    val walletCoinInset = with(density) { (12.dp + 15.dp).toPx() }
    Box(Modifier.fillMaxSize()) {
        GameScene(
            backdrop = backdropFor(state.task, finished = true),
            onClose = onDone,
            money = flight.wallet(state.balance),
            moneyModifier = Modifier
                .onGloballyPositioned { c ->
                    val b = c.boundsInRoot()
                    flight.to = Offset(b.right - walletCoinInset, b.center.y)
                }
                .graphicsLayer {
                    scaleX = flight.walletPop.value
                    scaleY = flight.walletPop.value
                },
        ) {
            // Разбор бывает длиннее экрана — прокручивается он, а «Ещё раз» и «Дальше»
            // всегда видны: раньше они уезжали вниз, и ребёнок не знал, как выйти.
            SceneBody(
                // Главная кнопка — та, что ведёт по игре дальше: после успеха «Дальше», после
                // неудачи «Ещё раз». Вторая — спокойная, как «Взять» в копилке. Раньше обе были
                // одинаковыми, и после победы ребёнок жал «Ещё раз» наугад (плейтест).
                bottom = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (success) {
                            FinneyQuietButton(text = "Ещё раз", onClick = onReplay, modifier = Modifier.weight(1f))
                            FinneyButton(text = "Дальше", onClick = onDone, modifier = Modifier.weight(1f))
                        } else {
                            FinneyQuietButton(text = "Дальше", onClick = onDone, modifier = Modifier.weight(1f))
                            FinneyButton(text = "Ещё раз", onClick = onReplay, modifier = Modifier.weight(1f))
                        }
                    }
                },
            ) {
                // Итог — значком на рамке, крупно: «✓» или «!» видно раньше, чем прочитано слово.
                // Цвет здесь — итог действия ребёнка, как и велит правило цвета, и не единственный признак.
                ScenePanel(
                    title = if (success) "Готово!" else "Почти!",
                    modifier = Modifier.fillMaxWidth(),
                    badge = { if (success) CheckBadge(size = 44.dp) else AlertBadge(size = 44.dp) },
                ) {
                    ResultBody(state.task, result.details, result.input, result.bonus)
                }
                // Награды нет — нет и плашки: «награда уже получена» была лишней строкой
                // в и без того длинном итоге (редизайн 28.09).
                if (result.reward > 0) RewardChip(
                    result.reward,
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .onGloballyPositioned { flight.from = it.boundsInRoot().center }
                        .graphicsLayer {
                            scaleX = flight.chipPop.value
                            scaleY = flight.chipPop.value
                        },
                )
                Spacer(Modifier.weight(1f))
                // Объяснение — репликой питомца во всю ширину, хвостиком к нему: так оно
                // отделено от пунктов и читается как совет, а не как ещё один абзац.
                Bubble(
                    TaskEngines.explanation(state.task, result.outcome, result.details),
                    Tail.DOWN_RIGHT,
                    Modifier.fillMaxWidth(),
                    maxWidth = 600.dp,
                )
                // Итог: получилось — два прыжка и конфетти, не вышло — грустит и капля.
                val pet = rememberPetReactions()
                LaunchedEffect(success) { if (success) pet.win() else pet.fail() }
                PetAtRight(state.character, 225.dp, Modifier.padding(end = 8.dp), reactions = pet)
            }
        }
        RewardCoins(flight)
    }
}

/** Награда плашкой: монетка и «+15». Показывается, только когда награда есть. */
@Composable
private fun RewardChip(reward: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(FinneyYellow)
            .border(StrokeRegular, FinneyInk, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Награда: $reward монет" },
    ) {
        OutlinedText("+$reward", style = MaterialTheme.typography.headlineMedium)
        Coin(size = 30.dp)
    }
}
