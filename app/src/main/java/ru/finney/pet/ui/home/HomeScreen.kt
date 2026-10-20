package ru.finney.pet.ui.home

import ru.finney.pet.R
import androidx.compose.foundation.layout.heightIn
import ru.finney.pet.ui.progress.NextProgress
import androidx.annotation.DrawableRes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import ru.finney.pet.ui.components.CloseButton
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import ru.finney.pet.ui.components.buttonFill
import ru.finney.pet.ui.theme.FinneyInkFaded
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.RadiusCard
import ru.finney.pet.ui.theme.RadiusCheckbox
import ru.finney.pet.ui.theme.RadiusPanel
import ru.finney.pet.ui.theme.StrokeBold
import ru.finney.pet.ui.theme.StrokeRegular
import ru.finney.pet.ui.motion.SteadyFrameRate
import ru.finney.pet.ui.motion.motionEnabled
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.LoopWhile
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.FinneyCream
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.finney.pet.ui.components.guidedInput
import ru.finney.pet.ui.components.ActionFeedbackSummary
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.finney.pet.domain.model.BodyColor
import ru.finney.pet.domain.model.EyesVariant
import ru.finney.pet.domain.model.PeriodPhase
import ru.finney.pet.domain.model.PetAppearance
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.model.PetStats
import ru.finney.pet.domain.pet.Emotion
import androidx.compose.ui.layout.layout
import androidx.compose.animation.core.snap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import ru.finney.pet.domain.model.Category
import ru.finney.pet.ui.components.CategoryBanner
import ru.finney.pet.ui.components.CoinAmount
import ru.finney.pet.ui.components.HomeShopButton
import ru.finney.pet.ui.components.FeedbackDialog
import ru.finney.pet.ui.components.ActionFeedbackCard
import ru.finney.pet.ui.components.ActionFeedback
import ru.finney.pet.domain.game.Rejection
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateMapOf
import ru.finney.pet.domain.game.LevelCheck
import ru.finney.pet.ui.components.Coin
import ru.finney.pet.ui.components.FeedbackSound
import ru.finney.pet.ui.components.FillRing
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyQuietButton
import ru.finney.pet.ui.components.CheckBadge
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIconButton
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.LevelBadge
import ru.finney.pet.ui.components.FinneyPanel
import ru.finney.pet.ui.components.WarningBadge
import ru.finney.pet.ui.tasks.games.ItemPicture
import ru.finney.pet.ui.pet.PetMood
import ru.finney.pet.ui.pet.PetView
import ru.finney.pet.ui.pet.grownPet
import ru.finney.pet.ui.pet.rememberPoseProvider
import ru.finney.pet.ui.pet.rememberPetAnimation
import ru.finney.pet.ui.debug.DebugPanel
import androidx.compose.animation.core.animateFloatAsState
import ru.finney.pet.ui.components.RoomActionLabel
import ru.finney.pet.ui.components.OutlinedText
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlin.random.Random
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import ru.finney.pet.ui.pet.skin
import ru.finney.pet.ui.room.FeedingGame
import ru.finney.pet.ui.room.CareBlock
import ru.finney.pet.ui.room.CareOption
import ru.finney.pet.ui.room.BathRoom
import ru.finney.pet.ui.room.BathShop
import ru.finney.pet.ui.room.BathStep
import ru.finney.pet.ui.room.CarePanel
import ru.finney.pet.ui.room.DOOR_MS
import ru.finney.pet.ui.room.WALK_MS
import ru.finney.pet.ui.room.RoomScene
import ru.finney.pet.ui.room.RoomSpot
import ru.finney.pet.ui.room.InteriorPart
import ru.finney.pet.ui.room.EditTab
import ru.finney.pet.ui.room.InteriorPicker
import ru.finney.pet.ui.room.InteriorPickerHeight
import ru.finney.pet.ui.room.toInterior
import ru.finney.pet.ui.room.core
import ru.finney.pet.domain.model.RoomPart
import ru.finney.pet.ui.room.PetLook
import ru.finney.pet.ui.room.RoomTrip
import ru.finney.pet.ui.room.TAKE_ALL_STEP_MS
import ru.finney.pet.ui.room.focusOf
import ru.finney.pet.ui.room.themeOf
import ru.finney.pet.ui.room.withPart
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneySand
import ru.finney.pet.ui.theme.RadiusField
import ru.finney.pet.ui.theme.StrokeThin
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.motion.PortalReveal
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

// Главный экран по обновлённому макету: компактные действия справа и состояние у питомца.
@Composable
fun HomeScreen(
    onOpenBudget: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenTask: (taskId: String) -> Unit,
    onOpenWardrobe: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenGlossary: () -> Unit,
    onPeriodClosed: (periodNumber: Int) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Итог покупки ухода висит несколько секунд и сам уходит: игра не прерывается окном,
    // а ребёнок успевает увидеть, что стало с деньгами и шкалой (ТЗ п. 2.5.9).
    var purchased by remember { mutableStateOf<ActionFeedback?>(null) }
    var rejected by remember { mutableStateOf<Rejection?>(null) }

    LaunchedEffect(purchased) {
        if (purchased != null) {
            delay(PurchaseFeedbackMillis)
            purchased = null
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.PeriodClosed -> onPeriodClosed(event.periodNumber)
                is HomeEvent.Rejected -> rejected = event.reason
                is HomeEvent.Purchased -> purchased = event.feedback
                // Итог сна — уже проснувшемуся, той же карточкой, что и покупка.
                is HomeEvent.Woke -> purchased = event.feedback
                is HomeEvent.Rewarded -> purchased = event.feedback
            }
        }
    }

    when (val s = state) {
        HomeUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = FinneyInk)
        }
        is HomeUiState.Ready -> HomeContent(
            state = s,
            onOpenBudget = onOpenBudget,
            onOpenShop = onOpenShop,
            onOpenGoals = onOpenGoals,
            onOpenTasks = onOpenTasks,
            onOpenTask = onOpenTask,
            onOpenWardrobe = onOpenWardrobe,
            onOpenProgress = onOpenProgress,
            onOpenSettings = onOpenSettings,
            onOpenHelp = onOpenHelp,
            onOpenGlossary = onOpenGlossary,
            onClosePeriod = viewModel::closePeriod,
            onBuy = { viewModel.buy(it) },
            onBuyFromWants = { viewModel.buy(it, fromWants = true) },
            onWash = viewModel::wash,
            onSleep = viewModel::sleep,
            onWake = viewModel::wake,
            onPlay = viewModel::play,
            onWear = viewModel::wear,
            onTakeOff = viewModel::takeOff,
            onSetRoom = viewModel::setRoom,
            onSetColoring = viewModel::setColoring,
            onClaimGoal = viewModel::completeGoal,
            purchased = purchased,
            onHidePurchased = { purchased = null },
            rejectShown = rejected != null,
        )
    }

    FeedbackSound(feedback = purchased, rejection = null)
    FeedbackDialog(
        feedback = null,
        rejection = rejected,
        onDismiss = { rejected = null },
        actionLabel = if (rejected is Rejection.PlanNotConfirmed) "К плану расходов" else null,
        onAction = onOpenBudget,
    )
    // Пришли из портала — зал раскрывается из круга в центре, поверх всего.
    PortalReveal(ready = state is HomeUiState.Ready)
}

private const val PurchaseFeedbackMillis = 2_500L

/** Сколько пути пальца с игрушкой рядом с питомцем — одно потряхивание. */
private val ShakeTravel = 40.dp

/** Раз в сколько потряхивания уходят в игру, пока игрушку не отпустили. */
private const val PlayFlushMillis = 800L

/** Не чаще раза в столько питомец смеётся вслух и подпрыгивает от игры. */
private const val HappySoundGapMillis = 1_500L

/** Самый частый звук встряски игрушки. */
private const val ToySoundGapMillis = 140L

/** Паузы между вздохами грустного питомца. */
private val SighPauseMs = 4_000L..8_000L

/** Настроения, на которые питомец вздыхает. */
private val SadEmotions = setOf(Emotion.HUNGRY, Emotion.DIRTY, Emotion.TIRED, Emotion.SAD)

/**
 * Сон — перерыв в игре: ребёнок не сидит в приложении, пока питомец спит.
 * Открыты только верхний ряд (деньги, уровень, меню) и «Сон»; всё
 * остальное полупрозрачное и не нажимается — игра, кухня, ванная, плашки копилки
 * и игры уровня, «Завершить уровень» в панели уровня.
 */
private const val NightDim = 0.45f

/**
 * Почему питомцу так — ТЗ п. 2.5.10: краткое объяснение причины эмоции и что сделать.
 * Спокойное состояние не комментируем: всё в порядке, лишний текст ни к чему.
 */
private fun emotionReason(emotion: Emotion, hasToys: Boolean): String? = when (emotion) {
    Emotion.HUNGRY -> "Я проголодался.\nПокорми меня!"
    Emotion.DIRTY -> "Я испачкался.\nПомой меня!"
    Emotion.TIRED -> "Я устал.\nХочу спать!"
    Emotion.SAD -> if (hasToys) "Мне грустно.\nПоиграем?" else "Мне грустно.\nХочу игрушку!"
    Emotion.HAPPY -> "Мне весело!\nСпасибо за заботу!"
    Emotion.CALM -> null
}

/** Значок у реплики — тот же, что на кнопке, которая поможет. Весёлому и спокойному значок не нужен. */
@DrawableRes
private fun emotionIcon(emotion: Emotion): Int? = when (emotion) {
    Emotion.HUNGRY -> R.drawable.home_eat_icon
    Emotion.DIRTY -> R.drawable.home_wash_icon
    Emotion.TIRED -> R.drawable.home_sleep_icon
    Emotion.SAD -> R.drawable.home_smile_icon
    Emotion.HAPPY, Emotion.CALM -> null
}

@Composable
internal fun HomeContent(
    state: HomeUiState.Ready,
    onOpenBudget: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenTask: (taskId: String) -> Unit,
    onOpenWardrobe: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onClosePeriod: () -> Unit,
    onBuy: (itemId: String) -> Unit,
    onBuyFromWants: (itemId: String) -> Unit = {},
    onWash: (itemId: String?) -> Unit = {},
    onSleep: () -> Unit = {},
    onWake: () -> Unit = {},
    onPlay: (toyId: String, shakes: Int) -> Unit = { _, _ -> },
    onWear: (itemId: String) -> Unit = {},
    onTakeOff: (itemId: String) -> Unit = {},
    onSetRoom: (themeId: String, part: RoomPart?) -> Unit = { _, _ -> },
    onSetColoring: (lookId: String?) -> Unit = {},
    onOpenGlossary: () -> Unit = {},
    onClaimGoal: () -> Unit = {},
    purchased: ActionFeedback? = null,
    onHidePurchased: () -> Unit = {},
    rejectShown: Boolean = false,
    actionLayout: HomeActionLayout = HomeActionLayout.SPLIT,
    shopContent: @Composable (Int, () -> Unit) -> Unit = { balance, click -> HomeShopButton(balance, click) },
) {
    // Комната тяжёлая для 120 Гц: на ровных 60 анимации идут без рывков (SteadyFrameRate).
    SteadyFrameRate()

    // Питомец спит — это ночь: он в капсуле, свет выключен, игра на паузе
    // до утра. Глаза закрываются, когда он уже внутри, и открываются,
    // как только проснулся. Открыли приложение, а он спит, — глаза закрыты сразу.
    val sleep = state.sleep
    val sleeping = sleep != null
    var eyesClosed by remember { mutableStateOf(sleeping) }
    LaunchedEffect(sleeping) {
        if (!sleeping) {
            eyesClosed = false
        } else if (!eyesClosed) {
            delay((WALK_MS + DOOR_MS).toLong())
            eyesClosed = true
        }
    }

    // Часы сна: раз в секунду — кольцо сна растёт, минуты до пробуждения тают.
    val now by produceState(System.currentTimeMillis(), sleep) {
        while (sleep != null) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    // Срок вышел — просыпается сам, даже если экран открыт весь час.
    LaunchedEffect(sleep?.endsAt) {
        val ends = sleep?.endsAt ?: return@LaunchedEffect
        delay((ends - System.currentTimeMillis()).coerceAtLeast(0))
        onWake()
    }
    val energyNow = sleep?.energyAt(now) ?: state.stats.energy
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var burgerBounds by remember { mutableStateOf(Rect.Zero) }
    // Значок уровня: куда прилетают звёзды условий и как он подпрыгивает от них (LevelStars.kt).
    var badgeBounds by remember { mutableStateOf(Rect.Zero) }
    val badgeBump = remember { Animatable(1f) }
    val starScope = rememberCoroutineScope()
    var savingsBounds by remember { mutableStateOf(Rect.Zero) }
    var gamesBounds by remember { mutableStateOf(Rect.Zero) }
    var headerBottom by remember { mutableFloatStateOf(0f) }
    var speechVisible by remember { mutableStateOf(false) }
    var speechBelow by remember { mutableStateOf(false) }
    var speechRequest by remember { mutableIntStateOf(0) }
    // Реплика не про настроение — «наигрался» при нажатии на «Игру». Гаснет вместе с пузырём.
    var speechNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.emotion, speechRequest) {
        speechVisible = speechRequest > 0 || state.emotion in SadEmotions
        if (speechVisible) {
            delay(6_000)
            speechVisible = false
        }
        speechNote = null
    }


    // Во сне — сопение и приглушённая музыка. Дверь капсулы щёлкает, когда
    // питомец до неё дошёл; открыли приложение, а он уже спит, — без щелчка.
    val sounds = LocalSounds.current
    LoopWhile(Sfx.SleepLoop, active = eyesClosed)
    DisposableEffect(sleeping) {
        sounds.duckMusic(sleeping)
        onDispose { sounds.duckMusic(false) }
    }
    var wasSleeping by remember { mutableStateOf(sleeping) }
    LaunchedEffect(sleeping) {
        if (sleeping != wasSleeping) {
            if (sleeping) delay(WALK_MS.toLong())
            sounds.play(Sfx.CapsuleDoor)
            wasSleeping = sleeping
        }
    }

    // Питомцу плохо — вздыхает, когда ребёнок это видит: на входе и при смене настроения.
    LaunchedEffect(state.emotion, sleeping) {
        if (!sleeping && state.emotion in SadEmotions) sounds.play(Sfx.PetSad)
    }

    // Отладочная сборка — та, что ставится как ru.finney.pet.debug. Флаг читается
    // из манифеста, а не из BuildConfig: генерация BuildConfig в модуле выключена,
    // а включать её — правка в build.gradle.kts, не в этой зоне.
    val context = LocalContext.current
    val isDebuggable = remember(context) {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    // Что стоит в комнате. Живёт на экране, а не в игре: это взгляд ребёнка на
    // комнату, а не событие в правилах. Днём питомец в игровой — с неё и начинаем.
    var spot by rememberSaveable { mutableStateOf(RoomSpot.PLAY) }

    // Какая панель ухода открыта и что в ней выбрано. Выбор живёт на экране,
    // а не в игре: пока не нажали «Купить», ничего не произошло.
    var care by rememberSaveable { mutableStateOf<CareTarget?>(null) }

    var picked by rememberSaveable { mutableStateOf<String?>(null) }

    // Панель отладки: долгое нажатие на уровень, только в отладочной сборке.
    var debugOpen by rememberSaveable { mutableStateOf(false) }
    // Отладка: облик питомца поверх уровня, чтобы посмотреть все стадии роста.
    var previewStage by rememberSaveable { mutableStateOf<Int?>(null) }

    // Панель уровня: чек-лист условий и «Завершить уровень». Открывается только
    // значком уровня, и она же — подтверждение: шаг необратимый, случайное
    // нажатие не должно подводить итоги за ребёнка.
    var levelOpen by rememberSaveable { mutableStateOf(false) }

    // Панель «Игры уровня»: обязательная игра и две по желанию.
    var gamesOpen by rememberSaveable { mutableStateOf(false) }

    // Панель «Финни / Комната»: какая вкладка открыта; null — панель закрыта. Пока
    // выбирают, интерфейс главной расступается, а камера подлетает к питомцу или вещи.
    var roomEdit by rememberSaveable { mutableStateOf<EditTab?>(null) }
    val editing = roomEdit != null
    // Панель живёт там, где видны стена, окно и торшер: в спальне и в игровой. Ковёр лежит
    // только в игровой — на вкладке «Ковёр» питомец уходит туда. Из кухни и ванной — в спальню,
    // а из игровой и спальни на других вкладках никуда не уходит. Закрыли панель — остаётся, где был.
    LaunchedEffect(roomEdit) {
        val tab = roomEdit ?: return@LaunchedEffect
        spot = when {
            tab.room == InteriorPart.RUG -> RoomSpot.PLAY
            spot.hasWindow && spot.hasLamp -> spot
            else -> RoomSpot.LIVING
        }
    }
    val editScope = rememberCoroutineScope()
    // Комната — из сохранения (ядро помнит её после перезапуска). «Вся комната» ставит
    // части по очереди и сверяется со свежей комнатой, а не с той, что была при нажатии.
    val interior = remember(state.room) { state.room.toInterior() }
    val currentInterior by rememberUpdatedState(interior)

    // «Назад» закрывает открытую панель, а не приложение: плейтест 29.09 — из
    // панели непройденного уровня было не выйти.
    BackHandler(enabled = levelOpen || gamesOpen || menuOpen || editing) {
        levelOpen = false
        gamesOpen = false
        menuOpen = false
        roomEdit = null
    }

    // Игра ухода: что выбрали в панели и теперь бросают в рот или трут о питомца.
    // Покупка — в конце игры, см. ui/room/CareGame.kt.
    var playing by rememberSaveable { mutableStateOf<String?>(null) }
    // Еду выбрали с «Взять и покормить»: в конце игры покупка — с переносом из радостей.
    var playingFromWants by rememberSaveable { mutableStateOf(false) }

    // Выключены — питомец не прыгает и не хихикает: цепочка шагов при нулевой
    // скорости мелькает по кадру на шаг, это хуже, чем стоять спокойно.
    val animations = LocalAnimations.current

    // Анимация живёт здесь, а не внутри комнаты: игры ухода открывают питомцу рот
    // и заставляют его хихикать, а сами лежат поверх комнаты.
    val animation = rememberPetAnimation()

    // Где питомец на экране — игры целятся в него и в его рот.
    var petLayout by remember { mutableStateOf(Rect.Zero) }

    // Игра с игрушкой: ребёнок водит игрушкой рядом с питомцем. Каждые ShakeTravel
    // пути пальца рядом с ним — одно потряхивание. Потряхивания копятся и уходят
    // в игру пачкой раз в PlayFlushMillis и когда игрушку отпустили.
    val hearts = rememberHeartBurst()
    val shakeTravelPx = with(LocalDensity.current) { ShakeTravel.toPx() }
    var travel by remember { mutableFloatStateOf(0f) }
    var pendingShakes by remember { mutableIntStateOf(0) }
    var heldToy by remember { mutableStateOf<String?>(null) }
    var lastHappySound by remember { mutableLongStateOf(0L) }
    // Когда игрушка последний раз не прибавила радости: над питомцем — «уже наигрался».
    var playedOutAt by remember { mutableLongStateOf(0L) }
    val currentState by rememberUpdatedState(state)

    // Место PetView — с поправкой на стадию роста: облик меньше рамки и стоит у её низа.
    fun petBounds(): Rect = currentState.let { petLayout.grownPet(it.appearance.character.skin, previewStage ?: it.stage) }
    fun flushPlay() {
        val toy = heldToy ?: return
        if (pendingShakes > 0) onPlay(toy, pendingShakes)
        pendingShakes = 0
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(PlayFlushMillis)
            flushPlay()
        }
    }
    // Звук игрушки: «поп» — взял, своя встряска на ходу, «тук» — отпустил. Встряска
    // звучит где угодно, не только у питомца: игрушка живая сама по себе. Не чаще
    // раза в ToySoundGapMillis и с разбросом тона — серия не звучит пулемётом.
    var soundTravel by remember { mutableFloatStateOf(0f) }
    var lastToySound by remember { mutableLongStateOf(0L) }
    fun onToyDrag(toyId: String, centre: Offset, delta: Offset) {
        if (heldToy == null) sounds.play(Sfx.ToyPickup)
        heldToy = toyId
        soundTravel += delta.getDistance()
        if (soundTravel >= shakeTravelPx) {
            soundTravel = 0f
            val now = System.currentTimeMillis()
            if (now - lastToySound > ToySoundGapMillis) {
                lastToySound = now
                sounds.play(toyShakeSound(toyId), 0.9f + Random.nextFloat() * 0.25f)
            }
        }
        val pet = petBounds()
        if (pet == Rect.Zero) return
        // Тянется к игрушке, где бы она ни была: следит за ней.
        animation.lookAt((centre.x - pet.center.x) / pet.width)
        if (!pet.inflate(pet.width * 0.1f).contains(centre)) return
        travel += delta.getDistance()
        while (travel >= shakeTravelPx) {
            travel -= shakeTravelPx
            val s = currentState
            val gain = pendingShakes * s.moodPerShake
            val joyful = s.playLeft - gain > 0 && s.stats.mood + gain < 100
            if (joyful) pendingShakes++
            hearts.emit(Offset(
                pet.center.x + (Random.nextFloat() - 0.5f) * pet.width * 0.45f,
                pet.top + pet.height * (0.12f + Random.nextFloat() * 0.18f),
            ), filled = joyful)
            if (!joyful) playedOutAt = System.currentTimeMillis()
            if (joyful) {
                if (animations) animation.playGiggle()
                val now = System.currentTimeMillis()
                if (now - lastHappySound > HappySoundGapMillis) {
                    lastHappySound = now
                    sounds.play(Sfx.PetHappy)
                    if (animations) animation.playJoy()
                }
            }
        }
    }
    fun onToyDrop(toyId: String) {
        heldToy = toyId
        flushPlay()
        heldToy = null
        travel = 0f
        soundTravel = 0f
        sounds.play(Sfx.ToyDrop)
        animation.lookAt(0f)
    }

    // Мало радости — питомец время от времени вздыхает и оседает: грусть видна
    // и без слов, а кольцо на «Игре» показывает, насколько.
    LaunchedEffect(state.moodLow, sleeping, animations) {
        if (!state.moodLow || sleeping || !animations) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(SighPauseMs.first, SighPauseMs.last))
            animation.playSigh()
        }
    }

    // Сон-загадка: открытая карточка, выбранное слово и сколько карточек уже было за этот
    // сон. Новый сон — счёт заново: ключ — минута, когда уснул.
    var dreamCard by remember(sleep?.since) { mutableStateOf<SleepCard?>(null) }
    var dreamPicked by remember(sleep?.since) { mutableStateOf<String?>(null) }
    var dreamsAsked by rememberSaveable(sleep?.since) { mutableIntStateOf(0) }
    var lastDream by remember { mutableStateOf<String?>(null) }

    // Подсказка следующего шага: где стоят кнопки-цели и вспышка «уровень готов».
    val hintTargets = remember { HintTargets() }
    var guided by rememberSaveable { mutableStateOf(true) }
    val burst = remember { Animatable(0f) }
    // Условия выполнены — дуга у значка закрылась. Вспышка — только на переходе:
    // что уже видели, помнит rememberSaveable, и вернувшись с игры уровня, ребёнок
    // видит вспышку, а при каждом входе на экран она не повторяется.
    val ready = state.check.planConfirmed && state.check.willPass && !sleeping
    var seenReady by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(ready) {
        val before = seenReady
        seenReady = ready
        if (before != false || !ready) return@LaunchedEffect
        sounds.play(Sfx.Correct)
        if (!animations) return@LaunchedEffect
        burst.snapTo(0f)
        burst.animateTo(1f, tween(durationMillis = 1_000))
        burst.snapTo(0f)
    }

    // Во время игры плашки и кнопки комнат гаснут: на их месте подсказка и
    // «Не сейчас». Гаснут, а не убираются — иначе шкала настроения, которая
    // тянется по свободной высоте, прыгала бы. Деньги остаются: ребёнок видит,
    // как монеты уходят, когда еда попала в рот.
    val hudAlpha by animateFloatAsState(if (playing == null) 1f else 0f, label = "hud")

    // В ванной боковые кнопки (копилка, игры) гаснут: на их месте подсказка, что делать с
    // полочкой. Деньги сверху и кольцо чистоты на кнопке ванны остаются — свои не заводим.
    // Дошёл ли питомец до комнаты: панель ванной встаёт, когда он уже в ней, а не в прыжке.
    val trip = remember { RoomTrip(spot) }
    val inBath = spot == RoomSpot.BATH && trip.at == RoomSpot.BATH && !sleeping
    val sideAlpha by animateFloatAsState(if (playing == null && !inBath) 1f else 0f, label = "side")

    // Сколько раз нажали на ванну внизу, уже стоя в ванной: мыть начинают с панели шагов,
    // и нажатие только зовёт к ней (BathRoom).
    var bathNudge by remember { mutableIntStateOf(0) }
    // Магазин шага ванны: открыт с плитки «Купить» — мыло или щётка с пастой.
    var bathShop by rememberSaveable { mutableStateOf<BathStep?>(null) }

    fun openCare(target: CareTarget) {
        // У ванны окна нет: шаги — на панели в самой ванной (BathRoom).
        if (target == CareTarget.BATH) {
            bathNudge++
            return
        }
        care = target
        picked = null
    }

    // Кнопка внизу сначала показывает комнату, и только повторное нажатие
    // открывает выбор: по макету её дело — «появляется стол», «появляется ванна».
    // Ребёнок сперва видит, куда попал, и лишь потом тратит деньги.
    // Нажали ещё раз, пока он в пути, — не открываем выбор, а переносим его сразу:
    // ребёнок жмёт снова, потому что ждать не хочет.
    fun goTo(next: RoomSpot, action: () -> Unit) {
        if (sleeping) return
        when {
            spot != next -> spot = next
            trip.at != next -> trip.hurry()
            else -> action()
        }
    }

    // Спят только в спальне. Вернулись в приложение, а комната запомнилась другая, —
    // питомец всё равно в капсуле, и на экране должна быть она.
    LaunchedEffect(sleeping) {
        if (sleeping) spot = RoomSpot.LIVING
    }


    // Касания по экрану — для подсказки шага: она гаснет от любого касания и
    // возвращается, только если ребёнок замешкался (NextStepHint.kt, hintDelayMillis).
    // Считаются одни нажатия, без движения пальца: каждое — одна рекомпозиция, не на кадр.
    var touches by remember { mutableIntStateOf(0) }

    // Игрушка показывает, что с ней делать (ToyShowcase.kt): рука — пока с игрушками ни разу
    // не играли, подскок — когда питомцу не радостно и ребёнок долго ничего не делает.
    // Рука уходит от любого касания; до следующего запуска она не вернётся.
    val showcase = remember { ToyShowcase() }
    val toyBounds = remember { mutableStateMapOf<String, Rect>() }
    var toyShown by rememberSaveable { mutableStateOf(false) }
    val roomCalm = spot == RoomSpot.PLAY && !sleeping && playing == null && care == null &&
        heldToy == null && !levelOpen && dreamCard == null && purchased == null && !rejectShown && !menuOpen && !gamesOpen &&
        !editing
    val showToy = !state.toyPlayed && !toyShown && state.toys.isNotEmpty() && roomCalm

    // Один круг показа: рука несёт игрушку [id] к питомцу и трясёт. false — показывать некуда.
    suspend fun showToyRound(id: String, sound: Boolean): Boolean {
        val from = toyBounds[id] ?: return false
        val pet = petBounds()
        if (pet == Rect.Zero) return false
        // К тому боку питомца, с которого лежит игрушка: рука не пересекает его.
        val side = if (from.center.x >= pet.center.x) 1f else -1f
        val to = Offset(pet.center.x + side * pet.width * 0.3f, pet.center.y + pet.height * 0.1f)
        showcase.show(id, from, to) {
            if (sound) sounds.play(toyShakeSound(id), 0.95f + Random.nextFloat() * 0.2f)
            hearts.emit(Offset(pet.center.x, pet.top + pet.height * 0.2f), filled = true)
        }
        return true
    }
    LaunchedEffect(showToy, animations) {
        if (!showToy) return@LaunchedEffect
        try {
            delay(ToyShowDelayMillis)
            val id = currentState.toys.firstOrNull { it in toyBounds } ?: return@LaunchedEffect
            if (!animations) {
                showcase.point(id)
                awaitCancellation()
            }
            repeat(ToyShowRounds) { round ->
                // Звук — только в первом круге: дальше показ идёт молча, не надоедает.
                if (!showToyRound(id, sound = round == 0)) return@LaunchedEffect
                delay(600)
            }
            toyShown = true
        } finally {
            showcase.reset()
        }
    }

    // Второе нажатие на «Игру»: игрушек нет — в магазин; наигрался — питомец говорит
    // об этом сам; иначе рука показывает, что с игрушкой делать. Показы не наслаиваются.
    var playShow by remember { mutableStateOf<Job?>(null) }
    fun play() {
        val s = currentState
        when {
            s.toys.isEmpty() -> onOpenShop()
            s.playLeft <= 0 -> {
                speechNote = "Я уже наигрался!\nНовые игры —\nна следующем уровне."
                speechRequest++
            }
            // Все игрушки убраны в ящик — показывать на полу нечего.
            s.toys.none { it in toyBounds } -> {
                speechNote = "Игрушки в ящике.\nНажми на него —\nи они выпрыгнут!"
                speechRequest++
            }
            playShow?.isActive != true -> playShow = editScope.launch {
                val id = s.toys.firstOrNull { it in toyBounds } ?: return@launch
                toyShown = true
                try {
                    if (animations) {
                        showToyRound(id, sound = true)
                    } else {
                        showcase.point(id)
                        delay(ToyShowDelayMillis * 2)
                    }
                } finally {
                    showcase.reset()
                }
            }
        }
    }
    LaunchedEffect(touches) {
        if (showcase.hand) toyShown = true
    }
    val hopToys = animations && roomCalm && !state.moodHappy && state.toys.isNotEmpty() && !showToy
    LaunchedEffect(touches, hopToys) {
        if (!hopToys) return@LaunchedEffect
        try {
            delay(ToyIdleMillis)
            repeat(ToyHopsPerIdle) { i ->
                val toys = currentState.toys.filter { it in toyBounds }
                if (toys.isEmpty()) return@LaunchedEffect
                val id = toys[i % toys.size]
                showcase.hop(id, toyBounds.getValue(id).height, sounds, toyShakeSound(id))
                delay(ToyHopGapMillis)
            }
        } finally {
            showcase.reset()
        }
    }

    // FinneyScreen тут не подходит: он заливает фон кремовым и сам растит колонку,
    // а под интерфейсом должна быть видна комната. Свой корень — ровно поэтому,
    // сам FinneyScreen не трогаем, на нём держатся пять других экранов.
    val guideStep = if (sleep != null) NextStep.WAKE.takeIf { now >= sleep.enoughAt } else state.nextStep
    // В ванной ведёт своя панель шагов (BathRoom): пузырь «я испачкался» звал бы на кнопку
    // ванны внизу и закрывал плитки.
    val guideBusy = playing != null || care != null || bathShop != null || inBath || levelOpen || debugOpen || rejectShown ||
        menuOpen || dreamCard != null || gamesOpen || editing
    // Интерфейс главной расступается, пока выбирают интерьер, — комната целиком
    // принадлежит выбору. Читается на отрисовке.
    val hudShown by animateFloatAsState(
        if (editing) 0f else 1f,
        if (animations) spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "hudShown",
    )
    // Сколько снизу закрывает панель выбора: камера ставит вещь посередине того, что над ней.
    // Заголовок и «Готово» висят над рамкой — их высота тоже закрыта.
    val pickerCovers = InteriorPickerHeight + 36.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .guidedInput(
                active = guided && state.level == 1 && !guideBusy,
                target = { guideStep?.let { hintTargets.spots[it]?.rect } },
                escape = { hintTargets.escape },
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) touches++
                    }
                }
            },
    ) {

        RoomScene(
            spot = spot,
            trip = trip,
            modifier = Modifier.fillMaxSize(),
            onTapItem = {
                when (spot) {
                    RoomSpot.KITCHEN -> openCare(CareTarget.FOOD)
                    RoomSpot.BATH -> openCare(CareTarget.BATH)
                    RoomSpot.LIVING -> if (sleeping) onWake() else onSleep()
                    RoomSpot.PLAY -> play()
                }
            },
            asleep = sleeping,
            // Сам гуляет по залу, пока с ним ничего не делают — и не играют игрушкой.
            // В панели «Финни / Комната» стоит на месте: камера держит его крупно.
            wander = playing == null && care == null && heldToy == null && !editing,
            // Без анимаций прыжок мелькнул бы по кадру на шаг — не прыгаем вовсе.
            onHop = { if (animations) animation.playJoy() },
            toys = state.toys,
            toysEnabled = !sleeping && playing == null && care == null && !editing,
            interior = interior,
            focus = roomEdit?.room?.let { interior.focusOf(it) },
            focusPet = roomEdit?.pet == true,
            focusRug = roomEdit?.room == InteriorPart.RUG,
            focusBottom = pickerCovers,
            onToyDrag = ::onToyDrag,
            onToyDrop = ::onToyDrop,
            toyNudge = showcase::offsetFor,
            onToyPlaced = { id, bounds -> if (toyBounds[id] != bounds) toyBounds[id] = bounds },
            // Игрушка в ящике не лежит на полу: ни рука, ни подскок её не показывают.
            onToyGone = { id -> toyBounds -= id },
            onToyBoxEmpty = {
                speechNote = "Ящик пустой.\nПеретащи в него\nигрушку!"
                speechRequest++
            },
        ) {
            // Нажатие — питомец подпрыгивает и говорит, как ему: это игра, а не меню.
            // Раньше реплику открывало сердечко у головы — радость теперь кольцом на «Игре».
            //
            // Подсветку убираем: у питомца нет прямоугольной формы, и ripple
            // лёг бы квадратом вокруг.
            PetView(
                character = state.appearance.character,
                coloring = state.coloring,
                accessories = state.worn,
                stage = previewStage ?: state.stage,
                mood = if (eyesClosed) PetMood.SLEEP else state.emotion.toMood(),
                pose = rememberPoseProvider(animation),
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { petLayout = it.boundsInRoot() }.testTag("home-pet")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Погладить питомца",
                        onClick = {
                            if (!sleeping) {
                                sounds.play(Sfx.PetHappy)
                                if (animations) animation.playJoy()
                                speechRequest++
                            }
                        },
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = hudShown
                    val s = 0.85f + 0.15f * hudShown
                    scaleX = s
                    scaleY = s
                }
                // Пока выбирают интерьер, кнопки главной не нажимаются: они растаяли,
                // а палец, попавший в невидимую кнопку, увёл бы с экрана.
                .then(if (editing) Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                } else Modifier)
                .systemBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

        // ---------- Верх: деньги-магазин, уровень, меню ----------
        // Три слота в ряд: слева деньги, по центру уровень, справа «бургер».
        // Кнопка с деньгами и есть вход в магазин — баланс показывает, сколько
        // можно потратить, и нажатие ведёт туда, где тратят. Отдельный кружок
        // магазина в нижнем ряду после этого не нужен.
        //
        // Крайние слоты одинаковой ширины (weight 1), поэтому уровень стоит
        // ровно по середине экрана, а не съезжает от длины суммы.
        Row(
            modifier = Modifier.fillMaxWidth().onGloballyPositioned {
                val bottom = it.boundsInRoot().bottom
                if (headerBottom != bottom) headerBottom = bottom
            },
            verticalAlignment = Alignment.Top,
        ) {
            // Верхний ряд во сне не гаснет: деньги, уровень и меню открыты
            // и ночью. Всё остальное на экране во сне закрыто (см. NightDim).
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                shopContent(state.balance, onOpenShop)
            }

            // Уровень — главный показатель роста, поэтому в полтора раза крупнее кнопок по краям.
            // Верхние края кругов совпадают, сумма остаётся под магазином.
            Box(contentAlignment = Alignment.Center) {
                // Нажатие — что нужно для уровня. Дуга вокруг — сколько условий уже выполнено.
                LevelBadge(
                    level = state.level,
                    size = LevelBadgeSize,
                    progress = state.levelProgress,
                    modifier = Modifier
                        .onGloballyPositioned { badgeBounds = it.boundsInRoot() }
                        .graphicsLayer { scaleX = badgeBump.value; scaleY = badgeBump.value }
                        .hintTarget(hintTargets, NextStep.PLAN, NextStep.FINISH).combinedClickable(
                        onClickLabel = "Что нужно для уровня",
                        onLongClickLabel = if (isDebuggable) "Отладка" else null,
                        onLongClick = if (isDebuggable) ({ debugOpen = true }) else null,
                        onClick = { levelOpen = true },
                    ),
                )
                // Тестовый профиль видно сразу: эксперт знает, почему все игры открыты.
                // Метка лежит на нижнем краю значка, а не под ним — ряд не вырастает.
                // «!» — как у остальных кнопок: значит, план ещё не составлен.
                if (state.nextStep == NextStep.PLAN) Image(
                    painterResource(R.drawable.home_warning_icon), null,
                    Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = (-4).dp)
                        .size(width = 16.dp, height = 35.dp),
                )
                // Подпись — потому что без неё кружок с цифрой не читался как кнопка.
                run {
                    Text(
                        text = if (state.isDemo) "Уровень · демо" else "Уровень",
                        maxLines = 1, softWrap = false,
                        style = MaterialTheme.typography.labelMedium,
                        color = FinneyInk,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 6.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(FinneySand)
                            .border(StrokeThin, FinneyInk, RoundedCornerShape(percent = 50))
                            .padding(horizontal = 8.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Отдельной кнопки «?» нет: подсказка — первый пункт меню («Как играть»).
                // Она по-прежнему доступна в любой момент (ТЗ п. 2.5.1), а верхний ряд
                // остаётся симметричным: монета — уровень — меню.
                // «Бургер» — всё, что не про уход за питомцем и не на экране:
                // игры, план, гардероб, подсказка и раздел взрослого.
                // Низ экрана из-за этого остался про комнаты, а не про меню.
                // Само меню — слоем поверх экрана (HomeMenu ниже): из ряда оно не смогло бы
                // затемнить остальное. Отсюда ему нужно только место бургера.
                DesignerHomeButton(
                    icon = R.drawable.home_menu_icon,
                    description = "Меню",
                    onClick = { menuOpen = true },
                    modifier = Modifier.onGloballyPositioned { burgerBounds = it.boundsInRoot() },
                )
            }
        }

        // Звание и ближайшее открытие — в панели уровня (нажатие на значок). Полосой здесь
        // они стояли над кнопками и сдвигали копилку вниз (20.10).
        // Кольца освобождают комнату, а сумма и активная игра остаются видны по ТЗ 2.5.3.
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            HomeSideActions(
                state = state, layout = actionLayout, enabled = !sleeping && playing == null && !inBath,
                alpha = sideAlpha, hintTargets = hintTargets, onOpenGoals = onOpenGoals, onClaimGoal = onClaimGoal,
                onOpenGames = { gamesOpen = true },
                onSavingsBounds = { if (savingsBounds != it) savingsBounds = it },
                onGamesBounds = { if (gamesBounds != it) gamesBounds = it },
            )
            // Во сне на полу — сколько ещё спать и «Разбудить». Днём здесь пусто:
            // «Завершить уровень» живёт только в панели уровня (значок сверху),
            // чтобы ребёнок подводил итог, видя условия, а не мимоходом.
            if (sleep != null) {
                SleepPanel(
                    petName = state.petName,
                    minutesLeft = ((sleep.endsAt - now + 59_999) / 60_000).toInt(),
                    secondsLeft = ((sleep.endsAt - now + 999) / 1_000).toInt(),
                    enoughIn = (sleep.enoughAt - now).coerceAtLeast(0),
                    onWake = onWake,
                    wakeModifier = Modifier.hintTarget(hintTargets, NextStep.WAKE),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        // ---------- Низ: комнаты ----------
        // Четыре кнопки, слева направо — вечер ребёнка: поиграть, поесть, помыться, спать.
        // Каждая переключает комнату, а кольцо вокруг показывает потребность, которую
        // в этой комнате закрывают, — так они нарисованы в ките («опускание/поднятие шкал
        // потребностей»). Сон у края: ночью три кнопки гаснут, и лампа остаётся одна в углу.
        // Четыре кольца по 70 dp с зазорами помещаются и на экране в 360 dp.
        Row(
            modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = hudAlpha },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Спящий не играет, не ест и не моется: кнопки полупрозрачные и не нажимаются.
            HomeCareAction(
                icon = R.drawable.home_smile_icon,
                label = "Игра",
                description = "Радость",
                onClick = { goTo(RoomSpot.PLAY, ::play) },
                value = state.stats.mood,
                critical = state.stats.mood < state.criticalThreshold,
                selected = spot == RoomSpot.PLAY,
                enabled = !sleeping,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = if (sleeping) NightDim else 1f },
            )
            HomeCareAction(
                icon = R.drawable.home_eat_icon,
                label = "Еда",
                description = "Покормить",
                onClick = { goTo(RoomSpot.KITCHEN) { openCare(CareTarget.FOOD) } },
                value = state.stats.satiety,
                critical = state.stats.satiety < state.criticalThreshold,
                selected = spot == RoomSpot.KITCHEN,
                enabled = !sleeping,
                buttonModifier = Modifier.hintTarget(hintTargets, NextStep.FEED),
                modifier = Modifier.weight(1f).graphicsLayer { alpha = if (sleeping) NightDim else 1f },
            )
            HomeCareAction(
                icon = R.drawable.home_wash_icon,
                label = "Ванна",
                description = "Помыть",
                onClick = { goTo(RoomSpot.BATH) { openCare(CareTarget.BATH) } },
                value = state.stats.hygiene,
                critical = state.stats.hygiene < state.criticalThreshold,
                selected = spot == RoomSpot.BATH,
                enabled = !sleeping,
                buttonModifier = Modifier.hintTarget(hintTargets, NextStep.WASH),
                modifier = Modifier.weight(1f).graphicsLayer { alpha = if (sleeping) NightDim else 1f },
            )
            HomeCareAction(
                icon = R.drawable.home_sleep_icon,
                label = "Сон",
                description = "Сон",
                // Как у остальных: первое нажатие — спальня, второе — уложить спать.
                onClick = {
                    if (sleeping) onWake() else goTo(RoomSpot.LIVING, onSleep)
                },
                value = energyNow,
                critical = energyNow < state.criticalThreshold,
                selected = spot == RoomSpot.LIVING,
                buttonModifier = Modifier.hintTarget(hintTargets, NextStep.SLEEP),
                modifier = Modifier.weight(1f),
            )
        }

        }

        // Значки привязаны к реальной области питомца, в том числе при его прогулке.
        if (!sleeping && !guideBusy && petLayout != Rect.Zero &&
            !(guided && state.level == 1 && guideStep != null)) {
            PetSpeechBubble(
                visible = speechVisible, tailAbove = speechBelow,
                text = speechNote ?: emotionReason(state.emotion, state.toys.isNotEmpty()) ?: "Мне хорошо!",
                icon = if (speechNote == null) emotionIcon(state.emotion) else null,
                onClick = {
                    speechVisible = false
                    if (speechNote == null) when (state.emotion) {
                        Emotion.HUNGRY -> goTo(RoomSpot.KITCHEN) { openCare(CareTarget.FOOD) }
                        Emotion.DIRTY -> goTo(RoomSpot.BATH) { openCare(CareTarget.BATH) }
                        Emotion.TIRED -> goTo(RoomSpot.LIVING, onSleep)
                        Emotion.SAD -> goTo(RoomSpot.PLAY, ::play)
                        else -> Unit
                    }
                },
                modifier = Modifier.layout { measurable, constraints ->
                    val pet = petBounds()
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    val margin = 12.dp.roundToPx()
                    // Речь не закрывает баланс, сумму копилки и активное задание при прогулке.
                    val actions = listOf(savingsBounds, gamesBounds).filter { it != Rect.Zero }
                    val sideLimit = if (actionLayout == HomeActionLayout.RIGHT_COLUMN)
                        actions.minOfOrNull { it.left }?.toInt() ?: constraints.maxWidth else constraints.maxWidth
                    val maxX = (sideLimit - placeable.width - margin).coerceAtLeast(margin)
                    val x = (pet.center.x - placeable.width * 0.65f).toInt().coerceIn(margin, maxX)
                    val actionsBottom = if (actionLayout == HomeActionLayout.SPLIT)
                        actions.maxOfOrNull { it.bottom } ?: headerBottom else headerBottom
                    val minY = (actionsBottom + 8.dp.toPx()).toInt().coerceAtLeast(margin)
                    val headTop = pet.top + pet.height * currentState.appearance.character.skin.hat.brim
                    val desiredAbove = (headTop - placeable.height - 8.dp.toPx()).toInt()
                    val maxY = (constraints.maxHeight - placeable.height - 128.dp.roundToPx()).coerceAtLeast(minY)
                    val below = desiredAbove < minY
                    if (speechBelow != below) speechBelow = below
                    val neck = currentState.appearance.character.skin.neck
                    val belowHead = (pet.top + pet.height * neck + 8.dp.toPx()).toInt()
                    val y = (if (below) belowHead else desiredAbove)
                        .coerceIn(minY, maxY)
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
                },
            )
        }

        // Облачко-мысль над спящим: сон-загадка по справочнику (SleepCards.kt).
        // Ставится от головы питомца вправо-вверх и не выходит за край экрана.
        if (eyesClosed && sleeping && petLayout != Rect.Zero && dreamCard == null && !levelOpen) {
            DreamCloud(
                done = dreamsAsked >= state.sleepCardsPerSleep,
                animate = animations,
                onClick = {
                    sounds.play(Sfx.Tap)
                    dreamCard = sleepCard(state.glossary, Random, avoid = lastDream)
                    dreamPicked = null
                },
                modifier = Modifier.layout { measurable, constraints ->
                    val pet = petBounds()
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    val margin = 8.dp.roundToPx()
                    val x = (pet.center.x + pet.width * 0.08f).toInt()
                        .coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
                    val y = (pet.top + pet.height * 0.18f - placeable.height).toInt().coerceAtLeast(margin)
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
                },
            )
        }

        // Одна подсказка за раз и только когда ничего не открыто поверх: во время
        // игры ухода и в панелях палец занят другим. Первый уровень — обучение:
        // экран темнеет вокруг цели, и рядом пузырь с фразой.
        //
        // Спящего будить подсказываем здесь, а не в ViewModel: сон идёт по часам,
        // а состояние игры за это время не меняется. Минуту, когда сна хватает,
        // считает ViewModel (SleepInfo.enoughAt), экран только сверяет часы.
        val step = if (sleep != null) NextStep.WAKE.takeIf { now >= sleep.enoughAt } else state.nextStep
        // Подсказка говорит одна: всё, что уже открыто поверх комнаты или ещё объясняет
        // своё (окно отказа, меню, сон-загадка), её глушит.
        // В ванной ведёт своя панель шагов — кольцо звало бы к игре уровня поверх мытья.
        val busy = playing != null || care != null || bathShop != null || inBath || levelOpen || debugOpen || rejectShown ||
            menuOpen || dreamCard != null || gamesOpen || editing
        val tutorial = state.level == 1 && guided
        // Итог покупки на обучении — не отдельной карточкой, а в пузыре подсказки,
        // над следующим шагом: «Купили: каша, −10 · +40» и «Дальше: сыграй в игру».
        // Плейтест 29.09: подсказка ждала, пока уйдёт карточка, — ребёнок смотрел на
        // «уведомление», не понимал, что его надо смахнуть, и игра стояла.
        // После обучения — как было: карточка сверху, подсказка после неё.
        val noteInBubble = false
        val quiet = busy || (purchased != null && !noteInBubble)
        val hint = step.takeIf { !quiet }
        // Подсказка проявляется, когда ребёнок замешкался, и гаснет от касания. Какой
        // был последний шаг, помним отдельно: на угасании кольцо должно остаться на
        // цели, а не пропасть кадром, когда шаг сменился на null.
        var shownStep by remember { mutableStateOf<NextStep?>(null) }
        if (hint != null) shownStep = hint
        val wait = hint?.let { hintDelayMillis(it, state.level) }
        var idle by remember { mutableStateOf(false) }
        LaunchedEffect(touches, hint, wait) {
            idle = false
            if (wait == null) return@LaunchedEffect
            delay(wait)
            idle = true
        }
        val visible = hint != null && (wait == 0L || idle)
        val visibility by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = if (animations) tween(durationMillis = 300) else snap(),
            label = "hintVisibility",
        )
        NextStepOverlay(
            // Пока рука показывает игрушку, подсказка шага молчит: двух рук сразу не бывает.
            step = if (visibility > 0f && !showcase.hand) shownStep else null,
            targets = hintTargets,
            bubble = shownStep?.takeIf { tutorial }?.bubble(),
            note = purchased.takeIf { noteInBubble },
            spotlight = tutorial,
            animate = animations,
            burst = { burst.value },
            hand = hintWithHand(state.level),
            visibility = { visibility },
            onSkip = { guided = false },
        )

        ToyShowcaseLayer(showcase, toyBounds, caption = "Возьми игрушку и потряси — ${state.petName} обрадуется")

        HeartBurstLayer(hearts)
        PlayedOutNote(state.petName, playedOutAt, petTop = { petBounds().top })

        // Панель ухода поверх всего: пока выбирают, комната остаётся видна,
        // и понятно, к чему относится выбор.
        //
        // Под панелью — полупрозрачная подложка. Она и приглушает комнату,
        // чтобы заголовок панели не наезжал на плашки, и ловит нажатия: без неё
        // сквозь панель нажимались кнопки комнат, а нажатие мимо панели ничего
        // не делало. Теперь мимо — это «закрыть».
        // Ванна — прямо в комнате, без окна: нажал на ванну — мытьё по шагам (BathRoom).
        if (inBath && playing == null) {
            val pet = petBounds()
            val mouth = state.appearance.character.skin.mouthCenter
            BathRoom(
                petName = state.petName,
                shelf = state.bath,
                nudge = bathNudge,
                pet = pet,
                mouth = Offset(pet.left + pet.width * mouth.pivotFractionX, pet.top + pet.height * mouth.pivotFractionY),
                onUse = onWash,
                onOpenShop = { bathShop = it },
                onTickle = { if (animations) animation.playGiggle() },
                onDone = { if (animations) animation.playJoy() },
            )
        }

        // Магазин шага ванны — с плитки «Купить»: то же окно поверх комнаты, что на кухне,
        // но только с тем, что нужно для этого шага.
        bathShop?.takeIf { inBath }?.let { step ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FinneyInk.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Закрыть",
                        onClick = { bathShop = null },
                    )
                    .systemBarsPadding()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                BathShop(
                    step = step,
                    shelf = state.bath,
                    onBuy = onBuy,
                    fromWants = state.fromWants,
                    onBuyFromWants = onBuyFromWants,
                    onDismiss = { bathShop = null },
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { } },
                    block = if (state.phase == PeriodPhase.ACTIVE) {
                        null
                    } else {
                        CareBlock(
                            reason = "Сначала составь план — потом покупки",
                            actionLabel = "К плану расходов",
                            onAction = onOpenBudget,
                        )
                    },
                    coins = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.clearAndSetSemantics { contentDescription = "У тебя ${state.balance} финок" },
                        ) {
                            Text("У тебя", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
                            CoinAmount(amount = state.balance)
                        }
                    },
                )
            }
        }

        // Магазин ванны открывается из самой ванны («Купить» на ленте шагов) — то же окно, что на кухне.
        care?.let { target ->
            val previews = when (target) {
                CareTarget.FOOD -> state.food
                CareTarget.BATH -> state.bath.items.map { it.preview }
            }
            val stock = state.bath.items.associate { it.item.id to it.left }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FinneyInk.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Закрыть",
                        onClick = { care = null; picked = null },
                    )
                    .systemBarsPadding()
                    .padding(16.dp),
                // Панель прижата к низу, а не по центру: её заголовок висит
                // над рамкой и по центру попадал ровно на плашки копилки
                // и задания. Заодно сверху остаётся видна комната — понятно,
                // кого кормят.
                contentAlignment = Alignment.BottomCenter,
            ) {
                val coins: @Composable () -> Unit = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clearAndSetSemantics { contentDescription = "У тебя ${state.balance} финок" },
                    ) {
                        Text("У тебя", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
                        CoinAmount(amount = state.balance)
                    }
                }
                CarePanel(
                    // Нажатия по самой панели гасятся здесь: иначе попадание
                    // между карточками уходило бы на подложку и закрывало панель.
                    // Не clickable: это не кнопка, и TalkBack не должен звать её
                    // нажимаемой.
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { } },
                    title = target.title,
                    // До подтверждения плана домен покупку не пропустит
                    // (Rejection.PlanNotConfirmed). Раньше кнопка «Купить»
                    // при этом молча закрывала панель и ничего не делала.
                    block = if (state.phase == PeriodPhase.ACTIVE) {
                        null
                    } else {
                        CareBlock(
                            reason = "Сначала составь план — потом покупки",
                            actionLabel = "К плану расходов",
                            onAction = onOpenBudget,
                        )
                    },
                    options = previews.map {
                        CareOption(it.item, it, isSelected = it.item.id == picked, left = stock[it.item.id], fromWants = state.fromWants[it.item.id])
                    },
                    onPick = { picked = it },
                    guide = state.level == 1,
                    upcoming = if (target == CareTarget.FOOD) state.upcomingFood else null,
                    confirmLabel = when (target) {
                        CareTarget.FOOD -> "Купить и покормить"
                        CareTarget.BATH -> "Купить"
                    },
                    onConfirm = { itemId ->
                        // Еду сразу едят (игра кормления), мыло и пасту — кладут на полочку: окно остаётся,
                        // видно, что запас вырос, и можно докупить ещё.
                        when (target) {
                            CareTarget.FOOD -> {
                                playing = itemId
                                care = null
                            }
                            CareTarget.BATH -> onBuy(itemId)
                        }
                        playingFromWants = false
                        picked = null
                    },
                    onConfirmFromWants = { itemId ->
                        when (target) {
                            CareTarget.FOOD -> {
                                playing = itemId
                                playingFromWants = true
                                care = null
                            }
                            CareTarget.BATH -> onBuyFromWants(itemId)
                        }
                        picked = null
                    },
                    fromWantsLabel = when (target) {
                        CareTarget.FOOD -> "Взять и покормить"
                        CareTarget.BATH -> "Взять и купить"
                    },
                    overPlanLabel = when (target) {
                        CareTarget.FOOD -> "Покормить сверх плана"
                        CareTarget.BATH -> "Купить сверх плана"
                    },
                    onDismiss = { care = null; picked = null },
                    // Окно покупки — тот же магазин в миниатюре (плейтест 28.09: еду покупали
                    // двумя путями, и они выглядели как два разных магазина): те же плитки,
                    // та же полоса «нужное» с остатком плана и кошелёк — комната под окном
                    // приглушена, и сумма в углу главного читается плохо.
                    tiles = true,
                    // Картинки мельче, чем в магазине: окно висит поверх комнаты, и еда с конфетой
                    // и кнопкой должны влезть в экран целиком.
                    tilePicture = 64.dp,
                    header = coins,
                    // Полосы — как в магазине. Конфета сытная, но она «хочется»: своя полоса.
                    // Подпись — названием части и одним словом, в строку: прежняя «Без этого Финни
                    // плохо» шла в две строки и звала питомца «Финни», даже если его зовут иначе.
                    groupBanner = { category ->
                        when (category) {
                            Category.NEEDS -> CategoryBanner(Category.NEEDS, "Нужное — сначала", state.needsLeft)
                            else -> CategoryBanner(category, "Хочется — потом", state.wantsLeft)
                        }
                    },
                )
            }
        }

        // Карточка сна поверх всего, как панель ухода: подложка приглушает комнату
        // и закрывает карточку касанием мимо.
        val card = dreamCard
        if (card != null && sleeping) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FinneyInk.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Закрыть",
                        onClick = { dreamCard = null },
                    )
                    .systemBarsPadding()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                SleepCardPanel(
                    petName = state.petName,
                    card = card,
                    picked = dreamPicked,
                    cardsLeft = state.sleepCardsPerSleep - dreamsAsked,
                    onPick = { word ->
                        dreamPicked = word
                        dreamsAsked++
                        lastDream = card.termId
                        if (word == card.answer) {
                            sounds.play(Sfx.Correct)
                            // Верно — над спящим всплывают сердечки, как когда его гладят.
                            val pet = petBounds()
                            val head = Offset(pet.center.x, pet.top + pet.height * 0.2f)
                            repeat(3) { i -> hearts.emit(head + Offset((i - 1) * 40f, 0f), filled = true) }
                        }
                    },
                    onNext = {
                        dreamCard = sleepCard(state.glossary, Random, avoid = lastDream)
                        dreamPicked = null
                    },
                    onClose = { dreamCard = null },
                    onOpenGlossary = { dreamCard = null; onOpenGlossary() },
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { } },
                )
            }
        }

        playing?.let { itemId ->
            val mouth = state.appearance.character.skin.mouthCenter
            val pet = petBounds()
            when (spot) {
                RoomSpot.KITCHEN -> FeedingGame(
                    itemId = itemId,
                    pet = pet,
                    mouth = Offset(
                        pet.left + pet.width * mouth.pivotFractionX,
                        pet.top + pet.height * mouth.pivotFractionY,
                    ),
                    onMouthOpen = animation::openMouth,
                    onEaten = {
                        if (playingFromWants) onBuyFromWants(itemId) else onBuy(itemId)
                        playingFromWants = false
                        if (animations) animation.playEat()
                        playing = null
                    },
                    onCancel = { playing = null },
                )
                // В ванной игры поверх нет: моют прямо с полочки (BathRoom).
                // Игры идут поверх кнопок комнат, из кухни не уйти.
                RoomSpot.BATH, RoomSpot.LIVING, RoomSpot.PLAY -> Unit
            }
        }

        if (levelOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FinneyInk.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Закрыть",
                        onClick = { levelOpen = false },
                    )
                    .systemBarsPadding()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                LevelPanel(
                    level = state.level,
                    upcoming = state.upcoming,
                    check = state.check,
                    levelGame = state.levelGame,
                    planGap = state.planGap,
                    canEarn = state.levelGames.any { !it.passed && it.reward > 0 },
                    asleep = sleeping,
                    careStats = state.stats.copy(energy = energyNow),
                    needsThreshold = state.needsThreshold,
                    onFinish = {
                        levelOpen = false
                        onClosePeriod()
                    },
                    onAction = { action ->
                        levelOpen = false
                        when (action) {
                            LevelAction.GAME -> state.check.levelTaskId?.let(onOpenTask)
                            LevelAction.GAMES -> gamesOpen = true
                            LevelAction.SAVE -> onOpenGoals()
                            LevelAction.PLAN -> onOpenBudget()
                            // Уход — в комнате: панель закрывается, и подсказка шага
                            // показывает, какую кнопку нажать.
                            LevelAction.CARE -> Unit
                        }
                    },
                    onOpenBudget = { levelOpen = false; onOpenBudget() },
                    onOpenProgress = { levelOpen = false; onOpenProgress() },
                    onDismiss = { levelOpen = false },
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { } },
                )
            }
        }

        if (gamesOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FinneyInk.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Закрыть",
                        onClick = { gamesOpen = false },
                    )
                    .systemBarsPadding()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                LevelGamesPanel(
                    games = state.levelGames,
                    onPlay = { gamesOpen = false; onOpenTask(it) },
                    onDismiss = { gamesOpen = false },
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { } },
                )
            }
        }

        HomeMenu(
            visible = menuOpen,
            burger = burgerBounds,
            onDismiss = { menuOpen = false },
            onOpenBudget = onOpenBudget,
            // «Украсить» — панель «Финни» с первой вкладки; в какую комнату, решает roomEdit выше.
            // Во сне — экраном.
            onDecorate = if (!sleeping) ({ roomEdit = EditTab.COLOUR }) else onOpenWardrobe,
            onOpenHelp = onOpenHelp,
            onOpenGlossary = onOpenGlossary,
            onOpenSettings = onOpenSettings,
        )

        if (debugOpen && isDebuggable) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FinneyInk.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { debugOpen = false },
                    )
                    .systemBarsPadding()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                DebugPanel(
                    onDismiss = { debugOpen = false },
                    onOpenTasks = { debugOpen = false; onOpenTasks() },
                    previewStage = previewStage,
                    onPreviewStage = { previewStage = it },
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { } },
                )
            }
        }

        // Выбор интерьера: панель выезжает снизу на пружине, над ней — комната.
        // Выбранное сразу встаёт в комнату; «Готово» и «Назад» просто закрывают.
        AnimatedVisibility(
            visible = editing,
            enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) { it },
            exit = slideOutVertically(tween(250)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            var shownTab by remember { mutableStateOf(EditTab.WALL) }
            roomEdit?.let { shownTab = it }
            InteriorPicker(
                interior = interior,
                ownedRooms = state.ownedRooms,
                look = PetLook(state.appearance.character, state.worn, state.outfit, state.coloring, state.colorings),
                onColoring = onSetColoring,
                tab = shownTab,
                onTab = { roomEdit = it },
                onPick = { part, theme -> onSetRoom(theme.id, part.core) },
                onTakeAll = { theme ->
                    // Тему целиком — со стены: камера отъезжает, и видно, как меняется всё по очереди.
                    roomEdit = EditTab.WALL
                    editScope.launch {
                        for (part in InteriorPart.entries) {
                            if (currentInterior.themeOf(part) == theme) continue
                            delay(TAKE_ALL_STEP_MS)
                            onSetRoom(theme.id, part.core)
                        }
                    }
                },
                onWear = onWear,
                onTakeOff = onTakeOff,
                onLocked = {
                    roomEdit = null
                    onOpenGoals()
                },
                onDone = { roomEdit = null },
            )
        }

        LevelStars(
            state = state,
            from = { petBounds().takeIf { it != Rect.Zero } ?: badgeBounds },
            to = { badgeBounds },
            onArrive = {
                starScope.launch {
                    badgeBump.snapTo(1.25f)
                    badgeBump.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
                }
            },
        )

        // Карточка завершает переход даже после очистки разового события.
        HomeFeedbackToast(
            feedback = purchased.takeUnless { noteInBubble }, onDismiss = onHidePurchased,
            modifier = Modifier.align(Alignment.TopCenter).systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 96.dp),
        )

    }
}


/**
 * Ночь: сколько ещё спать и «Разбудить». Лежит на полу под капсулой, над
 * кнопками комнат — там, куда смотрит ребёнок, когда уложил питомца.
 */
@Composable
private fun SleepPanel(
    petName: String,
    minutesLeft: Int,
    secondsLeft: Int,
    enoughIn: Long,
    onWake: () -> Unit,
    modifier: Modifier = Modifier,
    wakeModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(FinneyCream.copy(alpha = 0.9f))
            .border(2.dp, FinneyInk, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            // Сокращения вместо слов: «минут/минуты/минуту» без склонений.
            text = if (secondsLeft < 60) "$petName спит · ещё $secondsLeft с" else "$petName спит · ещё $minutesLeft мин",
            style = MaterialTheme.typography.titleMedium,
            color = FinneyInk,
        )
        Text("Энергия — силы Финни. Сон возвращает их бесплатно.",
            style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
        // Ждать полного сна не обязательно: для уровня хватает порога, и панель говорит,
        // когда он будет. Иначе ребёнок сидел перед капсулой весь час (плейтест).
        Text(
            text = if (enoughIn <= 0) {
                "Сна уже хватает для уровня — можно будить"
            } else if (enoughIn < 60_000) {
                "Для уровня хватит через ${(enoughIn + 999) / 1_000} с"
            } else {
                "Для уровня хватит через ${(enoughIn + 59_999) / 60_000} мин"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = FinneyInk,
        )
        FinneyButton(text = "Разбудить", onClick = onWake, fillWidth = false, modifier = wakeModifier)
    }
}

/**
 * Панель уровня: что нужно, чтобы его пройти, и «Завершить уровень».
 *
 * Открывается только нажатием на значок уровня, поэтому ребёнок всегда видит
 * условия до того, как подвести итог. До подтверждения плана вместо
 * «Завершить уровень» здесь «Составить план» — следующий шаг, а не отказ.
 * Вернуть уровень нельзя, и панель прямо говорит, что будет: пройден или
 * начнётся заново с новыми деньгами. Прогресс при этом не падает никогда.
 *
 * Вид — по макету экрана условий уровня (28.09): номер уровня плашкой-«стадионом»
 * над панелью, игра уровня строкой-плашкой, три условия плитками с отметкой под
 * каждой, второстепенные кнопки — спокойные зелёные.
 */
@Composable
private fun LevelPanel(
    level: Int,
    check: LevelCheck,
    levelGame: String?,
    planGap: PlanGap?,
    canEarn: Boolean,
    asleep: Boolean,
    careStats: PetStats,
    needsThreshold: Int,
    onFinish: () -> Unit,
    onAction: (LevelAction) -> Unit,
    onOpenBudget: () -> Unit,
    onOpenProgress: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    upcoming: NextProgress? = null,
) {
    var confirmRestart by rememberSaveable { mutableStateOf(false) }
    var careOpen by rememberSaveable { mutableStateOf(false) }
    val careActions = buildList {
        if (careStats.satiety < needsThreshold) add("покорми")
        if (careStats.hygiene < needsThreshold) add("помой")
        if (careStats.energy < needsThreshold) add(if (asleep) "дай выспаться" else "уложи спать")
    }
    val missing = missingSteps(check, levelGame, planGap, canEarn, careActions)
    // Нажали на запертое «Завершить» — строки «чего не хватает» вздрагивают.
    var nudge by remember { mutableIntStateOf(0) }
    // Прокрутка: с пунктами «чего не хватает» панель выше экрана 360 × 740, и без
    // неё нижние кнопки сплющивало (плейтест 29.09).
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // «✕» есть всегда: у непройденного уровня внизу «Начать заново», а не
        // «Ещё поиграю», и выйти из панели было нечем.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LevelTitle(level)
            CloseButton(
                onClick = onDismiss,
                description = "Закрыть",
                size = 56.dp,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RadiusPanel))
                .background(FinneyCream)
                .border(StrokeBold, FinneyInk, RoundedCornerShape(RadiusPanel))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            upcoming?.let { next ->
                Text("На уровне ${next.level}:", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
                next.changes.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = FinneyInk) }
            }
            if (!check.planConfirmed) {
                Text(
                    "Уровень начинается с плана: реши, сколько на нужное, сколько на «хочется» и сколько отложить.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = FinneyInk,
                )
                FinneyButton(text = "Составить план", onClick = onOpenBudget)
            } else {
                if (levelGame != null && check.levelTaskId != null) {
                    LevelGameRow(levelGame, check.gamePassed, required = check.gameRequired)
                }
                // Обязательна ли игра — здесь, а не в её плашке: там крупному
                // названию и так тесно.
                val withGame = levelGame != null && check.levelTaskId != null && check.gameRequired
                Text(
                    if (withGame) {
                        "Пройди игру и выполни ${check.toPass} из $LEVEL_CONDITIONS:"
                    } else {
                        "Выполни ${check.toPass} из $LEVEL_CONDITIONS:"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = FinneyInk,
                    textAlign = TextAlign.Center,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ConditionTile(
                        label = "Уход",
                        spoken = "Сыт, чист и выспался",
                        onClick = { careOpen = !careOpen },
                        expanded = careOpen,
                        done = check.needsCovered,
                        modifier = Modifier.weight(1f),
                    ) { LevelIcon(R.drawable.ic_level_care) }
                    // «По плану» проверяют по числам на другом экране: плитка ведёт
                    // туда, в «План и факт». Иначе этот экран ребёнок видел один
                    // раз — сразу после подтверждения плана.
                    ConditionTile(
                        // «По плану», а не «План»: на карте рядом «План составлен» — другое слово для другого.
                        label = "По плану",
                        spoken = "Траты по плану",
                        done = check.planMatched,
                        onClick = onOpenBudget,
                        modifier = Modifier.weight(1f),
                    ) { Coin(size = 56.dp) }
                    ConditionTile(
                        label = "Копилка",
                        spoken = "Отложено в копилку",
                        done = check.savingsAdded,
                        modifier = Modifier.weight(1f),
                    ) { LevelIcon(R.drawable.ic_level_piggy) }
                }
                if (careOpen) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Для ухода каждая шкала должна быть не ниже $needsThreshold из 100.",
                            style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
                        listOf("Сытость" to careStats.satiety, "Чистота" to careStats.hygiene,
                            "Энергия" to careStats.energy).forEach { (label, value) ->
                            val mark = if (value >= needsThreshold) "✓" else "!"
                            Text("$mark $label: $value из 100",
                                style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
                        }
                    }
                }
                if (check.willPass) {
                    Text(
                        "Готово! Уровень будет пройден, и придут новые деньги.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = FinneyInk,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    // Чего не хватает — прямо и по пунктам, и каждый пункт ведёт туда,
                    // где его выполняют. Плейтест 29.09: ребёнок видел «2 из 3», жал
                    // «Завершить», и уровень молча начинался заново.
                    MissingList(missing, onAction = onAction, nudge = nudge)
                }
                // Во сне уровень не завершают: условия видно, а итог — утром.
                if (asleep) {
                    Text(
                        "Питомец спит. Завершить уровень можно, когда он проснётся.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = FinneyInk,
                        textAlign = TextAlign.Center,
                    )
                }
                if (check.willPass) {
                    FinneyButton(
                        text = "Завершить уровень",
                        onClick = onFinish,
                        enabled = !asleep,
                        textStyle = LevelMainButtonText,
                        modifier = Modifier.graphicsLayer { alpha = if (asleep) NightDim else 1f },
                    )
                } else {
                    // Не готово — «Завершить» заперто: бледное, с замком, и на нажатие
                    // отвечает «нельзя» и показывает, чего не хватает. Плейтест 29.09:
                    // жёлтая «Доиграть уровень» выглядела так же, как «Завершить», а весь
                    // взгляд ребёнка — на главной кнопке, и плашка над ней не спасала.
                    LockedFinishButton(onTap = { nudge++ })
                }
            }
            // Уже основной: рядом с главным действием второстепенные не должны
            // спорить с ним ни цветом, ни шириной.
            FinneyButton(
                text = "Итоги и история",
                onClick = onOpenProgress,
                fillWidth = false,
                calm = true,
                textStyle = LevelSideButtonText,
                modifier = Modifier.fillMaxWidth(SideButtonWidth),
            )
            if (check.planConfirmed && !check.willPass) {
                // Сдаться можно, но только спокойной кнопкой и через вопрос: уровень не
                // засчитается, и ребёнок должен знать это до нажатия, а не после.
                FinneyQuietButton(
                    text = "Начать заново",
                    onClick = { if (!asleep) confirmRestart = true },
                    modifier = Modifier
                        .fillMaxWidth(SideButtonWidth)
                        .graphicsLayer { alpha = if (asleep) NightDim else 1f },
                )
            } else {
                // «Ещё поиграю» — просто закрыть панель, звать к нему не нужно:
                // спокойная кнопка, как «Назад» в знакомстве.
                FinneyQuietButton(
                    text = "Ещё поиграю",
                    onClick = onDismiss,
                    sound = Sfx.Back,
                    modifier = Modifier.fillMaxWidth(SideButtonWidth),
                )
            }
        }
    }
    if (confirmRestart) {
        RestartLevelDialog(
            level = level,
            missing = missing,
            onKeepPlaying = { confirmRestart = false; onDismiss() },
            onRestart = { confirmRestart = false; onFinish() },
        )
    }
}

/** Куда ведёт пункт «чего не хватает». */
private enum class LevelAction { GAME, GAMES, CARE, PLAN, SAVE }

/**
 * Пункт «чего не хватает»: что сделать словами и куда нажатие ведёт; null — никуда.
 * [conditions] — сколько условий уровня он закрывает: взнос по плану — сразу два.
 */
private data class MissingStep(val text: String, val action: LevelAction?, val conditions: Int = 1)

/** Что осталось до прохождения: заголовок над пунктами и сами пункты. */
private data class Missing(val title: String, val steps: List<MissingStep>)

/**
 * Чего не хватает до прохождения — пунктами, которые можно выполнить: «Пройди игру»,
 * «Положи в копилку ещё 10». Пусто — всё готово.
 *
 * План — не «план не выполнен», а почему и что сделать ([PlanGap]): в копилке меньше
 * задуманного — положить; потрачено больше — отыграть в игре, монеты после плана
 * закрывают перерасход (`PeriodRules.planMatched`). Плейтест 29.09: «не понимаю,
 * зачем план и почему он не получается».
 */
private fun missingSteps(check: LevelCheck, levelGame: String?, planGap: PlanGap?, canEarn: Boolean,
    careActions: List<String>): Missing {
    val game = mutableListOf<MissingStep>()
    if (levelGame != null && check.levelTaskId != null && check.gameRequired && !check.gamePassed) {
        game += MissingStep("Пройди игру «$levelGame»", LevelAction.GAME)
    }
    val left = (check.toPass - check.met).coerceAtLeast(0)
    if (left == 0) return Missing("Уровень пока не пройден. Осталось:", game)

    val savingsLeft = planGap?.savingsLeft ?: 0
    val overspent = planGap?.overspent ?: 0
    val open = mutableListOf<MissingStep>()
    if (!check.needsCovered) {
        val action = careActions.joinToString(", ").ifEmpty { "проверь шкалы" }
        open += MissingStep("Уход: $action", LevelAction.CARE)
    }
    // Копилка пуста, а по плану в неё что-то задумано, — один взнос закрывает оба.
    val savingsClosesPlan = !check.savingsAdded && !check.planMatched && savingsLeft > 0 && overspent == 0
    if (!check.savingsAdded) {
        open += if (savingsClosesPlan) {
            MissingStep("Копилка и план: положи в копилку $savingsLeft", LevelAction.SAVE, conditions = 2)
        } else {
            MissingStep("Копилка: положи в неё хоть немного", LevelAction.SAVE)
        }
    }
    if (!check.planMatched && !savingsClosesPlan) {
        open += when {
            overspent > 0 && canEarn ->
                MissingStep("План: потрачено на $overspent больше. Выиграй игру — монеты закроют разницу", LevelAction.GAMES)
            overspent > 0 ->
                MissingStep("План: потрачено на $overspent больше плана — выручат другие условия", null)
            savingsLeft > 0 ->
                MissingStep("План: положи в копилку ещё $savingsLeft", LevelAction.SAVE)
            else -> MissingStep("План: сверь траты в «План и факт»", LevelAction.PLAN)
        }
    }
    // Пункт, без которого остальных не хватит, — обязательный. Хватает одних
    // обязательных — показываем только их: «уход» не просим, если взнос по плану
    // и так закрывает два условия. Не хватает — выбор за ребёнком: «любые из этих».
    val total = open.sumOf { it.conditions }
    val must = open.filter { total - it.conditions < left }
    if (must.sumOf { it.conditions } >= left) return Missing("Уровень пока не пройден. Осталось:", game + must)
    val title = when {
        left == 1 -> if (game.isEmpty()) "Осталось одно — любое из этих:" else "Игра и ещё одно — любое из этих:"
        game.isEmpty() -> "Осталось $left условия — выбирай из этих:"
        else -> "Игра и ещё $left условия — выбирай из этих:"
    }
    return Missing(title, game + open)
}

/**
 * «Чего не хватает» пунктами-кнопками: у каждого «!» — не только цвет (ТЗ п. 3.6), —
 * и стрелка, если пункт ведёт туда, где его выполняют. [nudge] растёт — пункты
 * вздрагивают: нажали на запертое «Завершить».
 */
@Composable
private fun MissingList(missing: Missing, onAction: ((LevelAction) -> Unit)?, nudge: Int = 0) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(missing.title, style = MaterialTheme.typography.titleMedium, color = FinneyInk)
        missing.steps.forEach { step -> MissingRow(step, onAction, nudge) }
    }
}

@Composable
private fun MissingRow(step: MissingStep, onAction: ((LevelAction) -> Unit)?, nudge: Int) {
    val bump = remember { Animatable(1f) }
    LaunchedEffect(nudge) {
        if (nudge == 0 || !motionEnabled()) return@LaunchedEffect
        bump.snapTo(1.06f)
        bump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    val action = step.action.takeIf { onAction != null }
    val shape = RoundedCornerShape(RadiusCard)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = bump.value
                scaleY = bump.value
            }
            .defaultMinSize(minHeight = 52.dp)
            .clip(shape)
            .background(Color.White)
            .border(StrokeRegular, FinneyInk, shape)
            .then(
                if (action != null && onAction != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = "Перейти") { onAction(action) }
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        WarningBadge(size = 28.dp)
        Text(step.text, style = MaterialTheme.typography.bodyLarge, color = FinneyInk, modifier = Modifier.weight(1f))
        if (action != null) OutlinedText("›", style = MaterialTheme.typography.headlineMedium)
    }
}

/**
 * «Завершить уровень», пока рано: бледная, с замком, но нажимается — отвечает звуком
 * «нельзя», вздрагивает и зовёт взгляд к пунктам над собой. Погашенная кнопка у нас
 * бледная, и рядом словами, чего не хватает: слова — пункты выше.
 */
@Composable
private fun LockedFinishButton(onTap: () -> Unit) {
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val animations = LocalAnimations.current
    FinneyButton(
        text = "Завершить уровень",
        onClick = {
            onTap()
            if (animations) {
                scope.launch {
                    shake.snapTo(0f)
                    shake.animateTo(
                        0f,
                        keyframes {
                            durationMillis = 360
                            -10f at 60
                            10f at 140
                            -6f at 220
                            6f at 290
                        },
                    )
                }
            }
        },
        sound = Sfx.Denied,
        icon = { FinneyIcon(FinneyIcons.Lock, size = 28.dp) },
        textStyle = LevelMainButtonText,
        modifier = Modifier
            .graphicsLayer {
                alpha = LockedAlpha
                translationX = shake.value * density
            }
            .semantics { stateDescription = "Закрыто: сначала выполни пункты выше" },
    )
}

/** Бледность запертой «Завершить» — как у погашенной кнопки. */
private const val LockedAlpha = 0.5f

/**
 * Вопрос перед тем, как завершить непройденный уровень. Главная кнопка — доиграть:
 * случайное нажатие не должно стоить ребёнку уровня.
 */
@Composable
private fun RestartLevelDialog(level: Int, missing: Missing, onKeepPlaying: () -> Unit, onRestart: () -> Unit) {
    Dialog(onDismissRequest = onKeepPlaying) {
        FinneyPanel(title = "Начать заново?") {
            Text(
                "Уровень $level не засчитается: питомец останется на уровне $level.",
                style = MaterialTheme.typography.bodyLarge,
                color = FinneyInk,
            )
            MissingList(missing, onAction = null)
            Text(
                "Если начать заново, придут деньги на новую попытку, и нужно будет снова составить план.",
                style = MaterialTheme.typography.bodyLarge,
                color = FinneyInk,
            )
            FinneyButton(text = "Доиграть уровень", onClick = onKeepPlaying)
            FinneyQuietButton(text = "Начать заново", onClick = onRestart, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * «Игры уровня»: обязательная первой и крупнее, две по желанию — за монеты.
 * Обязательная отмечена звездой и словами, а не цветом (ТЗ п. 3.6).
 */
@Composable
private fun LevelGamesPanel(
    games: List<LevelGame>,
    onPlay: (taskId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FinneyPanel(title = "Игры уровня", onClose = onDismiss, modifier = modifier) {
        Text(
            "Первая игра нужна, чтобы пройти уровень. Ещё две — по желанию, за монеты.",
            style = MaterialTheme.typography.bodyLarge,
            color = FinneyInk,
        )
        games.forEach { game -> LevelGameCard(game, onClick = { onPlay(game.taskId) }) }
    }
}

/** Строка игры: картинка, название, обязательна ли, и справа — «✓» или награда. */
@Composable
private fun LevelGameCard(game: LevelGame, onClick: () -> Unit) {
    val shape = RoundedCornerShape(RadiusCard)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .clip(shape)
            .background(if (game.passed) FinneyGreen else Color.White)
            .border(if (game.required) StrokeBold else StrokeRegular, FinneyInk, shape)
            .clickable(role = Role.Button, onClickLabel = "Играть", onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append("«${game.title}», ")
                    append(if (game.required) "нужна для уровня" else "по желанию")
                    append(if (game.passed) ", пройдена" else if (game.reward > 0) ", награда ${game.reward}" else "")
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(contentAlignment = Alignment.TopStart) {
            game.icon?.let { ItemPicture(it, game.title, 48.dp) } ?: FinneyIcon(FinneyIcons.Star, size = 40.dp)
            if (game.required) FinneyIcon(FinneyIcons.Star, size = 20.dp, modifier = Modifier.offset(x = (-6).dp, y = (-6).dp))
        }
        Column(Modifier.weight(1f)) {
            Text(game.title, style = MaterialTheme.typography.titleMedium, color = FinneyInk)
            Text(
                if (game.required) "Нужна для уровня" else "По желанию",
                style = MaterialTheme.typography.bodyMedium,
                color = FinneyInk,
            )
        }
        when {
            game.passed -> CheckBadge(size = 32.dp)
            game.reward > 0 -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                OutlinedText("+${game.reward}", style = MaterialTheme.typography.titleLarge)
                Coin(size = 24.dp)
            }
        }
    }
}

/** Ширина второстепенных кнопок панели уровня — доля ширины панели, как в макете. */
private const val SideButtonWidth = 0.82f

// Кегли панели уровня. В макете надписи здесь заметно крупнее, чем на обычных
// кнопках: это главный выбор уровня, и его должно быть видно издалека.
// lineHeight задан вместе с кеглем: у Glina высокие круглые буквы, и без запаса
// строки им срезает верх (см. Type.kt).

/** «Уровень N» над панелью. */
private val LevelTitleText: TextStyle @Composable get() = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, lineHeight = 52.sp)

/** «Завершить уровень». */
private val LevelMainButtonText: TextStyle @Composable get() = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, lineHeight = 40.sp)

/** «Итоги и история», «Ещё поиграю». */
private val LevelSideButtonText: TextStyle @Composable get() = MaterialTheme.typography.headlineLarge.copy(fontSize = 27.sp, lineHeight = 36.sp)

/** Название игры уровня и подписи плиток. */
private val LevelItemText: TextStyle @Composable get() = MaterialTheme.typography.headlineLarge.copy(fontSize = 23.sp, lineHeight = 31.sp)

/** Номер уровня плашкой-«стадионом» над панелью: та же заливка, что у кнопки, но не нажимается. */
@Composable
private fun LevelTitle(level: Int) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 72.dp)
            .clip(RoundedCornerShape(percent = 50))
            .buttonFill(pressed = false, round = false)
            .semantics { heading() }
            .padding(horizontal = 36.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedText("Уровень $level", style = LevelTitleText)
    }
}

/**
 * Игра уровня строкой-плашкой: геймпад, название и отметка. Отметка обязательна —
 * без неё не видно, пройдена ли игра (ТЗ п. 3.6), хотя в макете её нет.
 */
@Composable
private fun LevelGameRow(game: String, passed: Boolean, required: Boolean) {
    val note = if (required) "обязательно" else "по желанию"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(percent = 50))
            .buttonFill(pressed = false, round = false, glare = false)
            .clearAndSetSemantics {
                contentDescription = "Игра «$game», $note: ${if (passed) "пройдена" else "пока нет"}"
            }
            // Справа поле шире: отметка стоит у скругления плашки, и с узким полем
            // она упиралась в обводку и заходила на нижнюю полосу (плейтест 29.09).
            .padding(start = 20.dp, end = 24.dp, top = 8.dp, bottom = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painterResource(R.drawable.ic_level_game),
            contentDescription = null,
            modifier = Modifier.size(width = 47.dp, height = 40.dp),
        )
        // Без слова «Игра»: о нём говорит геймпад, а с ним название крупным кеглем
        // переносилось и вылезало на нижнюю полосу плашки.
        Text(
            "«$game»",
            // Длинное название («Лимонадная лавка») встаёт в две строки: интервал
            // плотнее, чтобы вторая строка не заходила на нижнюю полосу плашки.
            style = LevelItemText.copy(lineHeight = 26.sp),
            color = FinneyInk,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        Text(if (passed) "✓" else "—", style = MaterialTheme.typography.titleLarge, color = FinneyInk)
    }
}

/**
 * Условие уровня плиткой: крупный значок, подпись и отметка под ней.
 *
 * Выполненное — зелёная плитка, но главный признак — галочка в квадрате
 * под ней: по ТЗ п. 3.6 цвет не может быть единственным. Подпись — наше
 * добавление к макету: без неё смайлик и монету ребёнок не свяжет с условием.
 */
@Composable
private fun ConditionTile(
    label: String,
    spoken: String,
    done: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    expanded: Boolean? = null,
    icon: @Composable () -> Unit,
) {
    val tap = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClickLabel = "Посмотреть", onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusCard))
            .then(tap)
            .clearAndSetSemantics {
                contentDescription = "$spoken: ${if (done) "да" else "пока нет"}"
                expanded?.let { stateDescription = if (it) "Показатели раскрыты" else "Показать показатели" }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(RadiusCard))
                .background(if (done) FinneyGreen else FinneyCream)
                .border(StrokeRegular, FinneyInk, RoundedCornerShape(RadiusCard)),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = LevelItemText, color = FinneyInk, maxLines = 1)
            expanded?.let { open ->
                Canvas(Modifier.size(12.dp)) {
                    val edgeY = size.height * if (open) 0.7f else 0.3f
                    val middleY = size.height - edgeY
                    val middle = Offset(size.width * 0.5f, middleY)
                    drawLine(FinneyInk, Offset(size.width * 0.15f, edgeY), middle, strokeWidth = 2.dp.toPx())
                    drawLine(FinneyInk, middle, Offset(size.width * 0.85f, edgeY), strokeWidth = 2.dp.toPx())
                }
            }
        }
        Text(if (done) "✓ Готово" else "Ещё нет", style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
    }
}

/**
 * Значок условия в плитке — рисунок из design/exports/ui/icons (tools/render_icons.py).
 * Подпись для TalkBack не нужна: плитка читается целиком своей фразой.
 */
@Composable
private fun LevelIcon(@DrawableRes id: Int) {
    Image(
        painterResource(id),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(60.dp),
    )
}

/**
 * Уровень в полтора раза крупнее кнопок по краям верхнего ряда — вместе с кольцом
 * прогресса. Кольцо рисуется снаружи круга и добавляет 26 % диаметра (дорожка и обводка
 * с двух сторон), поэтому круг 66 dp даёт значок 84 dp = 1,5 × 56. Вдвое крупнее
 * (112 dp) уровень перетягивал на себя весь ряд.
 */
private val LevelBadgeSize = 66.dp

/**
 * Плашка-кнопка с фактом: копилка, активное задание.
 *
 * Значок слева обязателен: по ТЗ п. 3.6 одним цветом ничего не сообщают,
 * и плашки должны отличаться друг от друга не только словами. Высота — от
 * 48 dp, потому что по плашке нажимают.
 *
 * [action] — что произойдёт при нажатии; уходит в TalkBack вместо [text],
 * который сам по себе действия не называет.
 *
 * [enabled] = false — плашка полупрозрачная и не нажимается (во сне).
 *
 * [progress] — «сколько из скольки» (накоплено и цена цели): значок встаёт
 * в кольцо, которое заполняется от розового к зелёному. null — просто значок.
 */
@Composable
private fun InfoChip(
    icon: FinneyIcons,
    text: String,
    detail: String? = null,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    progress: Pair<Int, Int>? = null,
    done: Boolean = false,
    doneTitle: String = "Пройдена!",
) {
    Column(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else NightDim }
            .clip(RoundedCornerShape(RadiusField))
            .background(if (done) FinneyGreen else FinneySand)
            .border(StrokeThin, FinneyInk, RoundedCornerShape(RadiusField))
            .clickable(enabled = enabled, onClickLabel = action, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = FinneyInk,
                modifier = Modifier.weight(1f))
            if (done) CheckBadge(size = 28.dp)
            else FinneyIcon(icon, size = 28.dp)
        }
        detail?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = FinneyInk)
        }
    }
}

/**
 * Эмоция домена → выражение лица питомца. Отдельная функция, потому что
 * состояний лица четыре, а эмоций больше: грусть и голод выглядят одинаково.
 */
internal fun Emotion.toMood(): PetMood = when (this) {
    Emotion.HAPPY, Emotion.CALM -> PetMood.HAPPY
    // Голод и усталость отдельного лица не имеют: такой питомец выглядит грустным.
    // Лицо сна — закрытые глаза — только для настоящего сна в капсуле.
    Emotion.HUNGRY, Emotion.TIRED, Emotion.SAD -> PetMood.SAD
    Emotion.DIRTY -> PetMood.DIRTY
}

/** Все шаги подсказки — по превью на каждый. */
private class NextStepProvider : PreviewParameterProvider<NextStep> {
    override val values = NextStep.entries.asSequence()
}

@Preview(showBackground = true, backgroundColor = 0xFFFFEDCD, widthDp = 360, heightDp = 780)
@Composable
private fun HomeNextStepPreview(@PreviewParameter(NextStepProvider::class) step: NextStep) {
    HomePreviewContent(step)
}

@Preview(showBackground = true, backgroundColor = 0xFFFFEDCD)
@Composable
private fun HomeContentPreview() {
    HomePreviewContent(step = null)
}

@Composable
private fun HomePreviewContent(step: NextStep?) {
    FinneyTheme {
        HomeContent(
            state = HomeUiState.Ready(
                petName = "Финни",
                appearance = PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND),
                isDemo = false,
                stats = PetStats(30, 40, 50, 20),
                emotion = Emotion.CALM,
                level = 1,
                check = LevelCheck(
                    planConfirmed = true,
                    needsCovered = true,
                    planMatched = false,
                    savingsAdded = false,
                    toPass = 2,
                    levelTaskId = "change_1",
                    gamePassed = false,
                ),
                stage = 1,
                balance = 50,
                totalSavings = 0,
                goal = null,
                periodNumber = 1,
                phase = PeriodPhase.PLANNING,
                needsHint = 40,
                levelGame = "Касса Финни",
                food = emptyList(),
                care = emptyList(),
                nextStep = step,
            ),
            onOpenBudget = {}, onOpenShop = {}, onOpenGoals = {}, onOpenTasks = {}, onOpenTask = {}, onOpenWardrobe = {},
            onOpenProgress = {}, onOpenSettings = {}, onOpenHelp = {},
            onClosePeriod = {}, onBuy = {},
        )
    }
}

/**
 * «Финни уже наигрался» над питомцем — пока игрушка не прибавляет радости (плейтест:
 * пустые сердечки без слов не объясняли, почему трясти дальше бесполезно; ТЗ п. 2.5.10, 2.5.12).
 * Держится пару секунд после последнего пустого сердечка. [playedOutAt] 0 — ещё не было.
 */
@Composable
private fun PlayedOutNote(petName: String, playedOutAt: Long, petTop: () -> Float) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(playedOutAt) {
        if (playedOutAt == 0L) return@LaunchedEffect
        visible = true
        delay(PlayedOutNoteMillis)
        visible = false
    }
    if (!visible) return
    val lift = with(LocalDensity.current) { 56.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, (petTop() - lift).coerceAtLeast(0f).roundToInt()) },
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = "$petName уже наигрался",
            style = MaterialTheme.typography.bodyLarge,
            color = FinneyInk,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(FinneyCream)
                .border(StrokeThin, FinneyInk, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

private const val PlayedOutNoteMillis = 2_000L
