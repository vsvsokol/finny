package ru.finney.pet.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseInBack
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.launch
import ru.finney.pet.ui.components.ScreenPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.motion.PortalArrival
import ru.finney.pet.ui.motion.drawPortal
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.profile.PetNameError
import ru.finney.pet.ui.components.ChargeButton
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyTextField
import ru.finney.pet.ui.components.FinneyScreen
import ru.finney.pet.ui.components.MenuBackdrop
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.pet.PetMood
import ru.finney.pet.ui.pet.PetPose
import ru.finney.pet.ui.pet.PetView
import ru.finney.pet.ui.pet.rememberPoseProvider
import ru.finney.pet.ui.pet.rememberPetAnimation
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneySand
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.theme.RadiusCard
import ru.finney.pet.ui.theme.StrokeBold
import ru.finney.pet.ui.theme.StrokeThin
import ru.finney.pet.ui.theme.FinneyYellow

/**
 * Подписи питомцев для игрока. В домене они названы по автору рисунка
 * (design/exports/pet) — ребёнку такое имя ничего не говорит. Здесь заглушки:
 * настоящие имена придумывает @vsvsokol вместе с текстами знакомства.
 */
private val CharacterLabels = mapOf(
    PetCharacter.PUSHISTIK to "Пушистик",
    PetCharacter.ROGATIK to "Рогатик",
    PetCharacter.ZVEZDOCHKA to "Звёздочка",
    PetCharacter.BANTIK to "Бантик",
    PetCharacter.LUCHIK to "Лучик",
)

@Composable
fun PetSetupScreen(
    isEditing: Boolean,
    onSaved: () -> Unit,
    isDemo: Boolean = false,
    viewModel: PetSetupViewModel = viewModel(factory = PetSetupViewModel.factory(isEditing, isDemo)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PetSetupEvent.Saved -> onSaved()
            }
        }
    }

    PetSetupContent(
        state = state,
        onNameChange = viewModel::setName,
        onCharacterChange = viewModel::setCharacter,
        onSave = viewModel::save,
    )
}

/**
 * Выбора цвета здесь нет: питомец начинает в родном цвете, окраски копят в копилке
 * и надевают в панели «Финни». Выбора глаз тоже нет: варианты глаз не нарисованы,
 * а выбор, который ничего не меняет на экране, ребёнка только путает. Сеттер в
 * [PetSetupViewModel] остался.
 */
@Composable
private fun PetSetupContent(
    state: PetSetupUiState,
    onNameChange: (String) -> Unit,
    onCharacterChange: (PetCharacter) -> Unit,
    onSave: () -> Unit,
) {
    // Переход в мир: после зарядки питомец уходит в портал, и только потом сохранение
    // и переход. «Сохранить» в настройке и выключенные анимации — сразу, без портала.
    val animations = LocalAnimations.current
    val portal = remember { PortalRun() }
    var entering by remember { mutableStateOf(false) }
    LaunchedEffect(entering) {
        if (!entering) return@LaunchedEffect
        portal.play()
        PortalArrival.arm()
        onSave()
    }
    // Имя не приняли — портал закрывается, ребёнок видит ошибку.
    LaunchedEffect(state.nameError) {
        if (state.nameError != null && entering) {
            PortalArrival.take()
            portal.reset()
            entering = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        FinneyScreen(
            backdrop = MenuBackdrop.SHAPES,
            scrollable = true,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedText(
                text = if (state.isEditing) "Настройка" else "Твой питомец",
                style = MaterialTheme.typography.headlineLarge,
            )

            // Заголовка «Кто это будет» нет: выбор — картинками самих питомцев, он понятен
            // без подписи. Имя без подписи не понять — она осталась.
            CharacterPicker(
                selected = state.appearance.character,
                // Карусель стоит, пока питомец уходит: иначе выбор сменился бы на полпути.
                // Остальное не гасим — погасшие поля мелькнули бы бледными до конца перехода.
                enabled = !state.isLoading && !entering,
                portal = portal,
                onSelect = onCharacterChange,
            )

            OutlinedText("Как назовёшь?", style = MaterialTheme.typography.headlineMedium)
            NameField(
                name = state.name,
                error = state.nameError,
                maxLength = state.maxNameLength,
                enabled = !state.isLoading,
                onNameChange = onNameChange,
            )

            // «Начать игру» заряжается — начало игры бывает один раз и должно ощущаться.
            // С неверным именем заряжать нечего: обычное нажатие сразу покажет ошибку.
            // «Сохранить» в настройке — обычная кнопка, её жмут по делу.
            val canSave = !state.isSaving && !state.isLoading
            if (!state.isEditing && state.nameValid) {
                ChargeButton(
                    text = "Начать игру",
                    onCharged = { if (animations) entering = true else onSave() },
                    enabled = canSave,
                )
            } else {
                FinneyButton(
                    text = if (state.isEditing) "Сохранить" else "Начать игру",
                    onClick = onSave,
                    enabled = canSave,
                )
            }
        }
        PortalOverlay(portal, blockTouches = entering)
    }
}

/**
 * Портал по шагам: круг открывается на карточке выбранного питомца, питомец
 * уменьшается и уходит в него, круг растёт на весь экран. Всего ~0,7 с.
 * [center] и [radius] — где круг на экране: их пишет карточка, пока стоит на месте.
 */
private class PortalRun {
    val open = Animatable(0f)
    val swallow = Animatable(0f)
    val grow = Animatable(0f)
    var center = Offset.Zero
    var radius = 0f

    suspend fun play() = coroutineScope {
        launch { open.animateTo(1f, tween(220, easing = EaseOutBack)) }
        delay(120)
        swallow.animateTo(1f, tween(300, easing = EaseInBack))
        grow.animateTo(1f, tween(280, easing = EaseIn))
    }

    suspend fun reset() {
        open.snapTo(0f)
        swallow.snapTo(0f)
        grow.snapTo(0f)
    }
}

/**
 * Вторая половина портала — круг, который растёт с карточки на весь экран. Лежит
 * поверх экрана целиком: внутри карточки ему тесно. Пока идёт переход, забирает
 * касания — карусель под ним не должна сменить питомца.
 */
@Composable
private fun PortalOverlay(portal: PortalRun, blockTouches: Boolean) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val touches = if (blockTouches) {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent().changes.forEach { it.consume() }
            }
        }
    } else {
        Modifier
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .then(touches),
    ) {
        val g = portal.grow.value
        if (g <= 0f) return@Canvas
        val c = portal.center - origin
        // До самого дальнего угла и ещё запас на обводку — чтобы кольцо ушло за край.
        val far = maxOf(
            c.getDistance(),
            (c - Offset(size.width, 0f)).getDistance(),
            (c - Offset(0f, size.height)).getDistance(),
            (c - Offset(size.width, size.height)).getDistance(),
        ) + 16.dp.toPx()
        drawPortal(c, lerp(portal.radius, far, g))
    }
}

@Composable
private fun NameField(
    name: String,
    error: PetNameError?,
    maxLength: Int,
    enabled: Boolean,
    onNameChange: (String) -> Unit,
) {
    // Ошибка — и словом, и цветом рамки: цвет не единственный признак (ТЗ п. 3.6).
    val message = when (error) {
        PetNameError.BLANK -> "Впиши имя"
        PetNameError.TOO_LONG -> "Не длиннее $maxLength букв"
        null -> null
    }
    FinneyTextField(
        value = name,
        onValueChange = onNameChange,
        placeholder = "Например, Финни",
        isError = error != null,
        supportingText = message,
        enabled = enabled,
    )
}

/**
 * Выбор питомца: на карточках сами питомцы, а не подписи — ребёнок может ещё не
 * читать. Выбранный дышит и подпрыгивает в ответ на нажатие, остальные стоят:
 * так видно, кто выбран, даже мимо рамки.
 */
@Composable
private fun CharacterPicker(
    selected: PetCharacter,
    enabled: Boolean,
    portal: PortalRun,
    onSelect: (PetCharacter) -> Unit,
) {
    // LazyRow, а не Row: питомцев стало пять, и в ряд по ширине экрана они не
    // помещаются — карточка ужималась до полусотни точек. Ширина фиксированная,
    // список прокручивается вбок.
    //
    // Ленивый он не ради памяти, а ради кадров: каждая карточка крутит свою
    // анимацию (см. комментарий к CharacterCard про 5 fps), и в LazyRow
    // одновременно живут только видимые — две-три вместо пяти.
    //
    // Выбранный — тот, кто в середине (плейтест 28.09): листаешь — и сразу выбираешь,
    // без второго нажатия. Соседи меньше и бледнее, прокрутка защёлкивается по центру.
    val list = rememberLazyListState(initialFirstVisibleItemIndex = selected.ordinal)
    val scope = rememberCoroutineScope()
    val latestSelected by rememberUpdatedState(selected)
    val latestOnSelect by rememberUpdatedState(onSelect)

    // Профиль при редактировании загружается позже первого кадра: докручиваем
    // к выбранному, если он не в середине.
    LaunchedEffect(selected) {
        if (!list.isScrollInProgress && list.centeredIndex() != selected.ordinal) {
            list.animateScrollToItem(selected.ordinal)
        }
    }
    LaunchedEffect(list, enabled) {
        if (!enabled) return@LaunchedEffect
        var wasScrolling = false
        snapshotFlow { list.isScrollInProgress }.collect { scrolling ->
            if (wasScrolling && !scrolling) {
                val centered = PetCharacter.entries.getOrNull(list.centeredIndex() ?: -1)
                if (centered != null && centered != latestSelected) latestOnSelect(centered)
            }
            wasScrolling = scrolling
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            // Карусель — во всю ширину экрана, поверх его полей: иначе соседи
            // срезались по линейке в 16 dp от края.
            .layout { measurable, constraints ->
                val bleed = ScreenPadding.roundToPx()
                val wide = constraints.copy(
                    minWidth = constraints.minWidth + bleed * 2,
                    maxWidth = constraints.maxWidth + bleed * 2,
                )
                val placeable = measurable.measure(wide)
                layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
            },
    ) {
        // Крайний отступ такой, чтобы первый и последний питомец вставали в середину.
        val side = (maxWidth - CardWidth) / 2
        LazyRow(
            state = list,
            contentPadding = PaddingValues(horizontal = side),
            flingBehavior = rememberSnapFlingBehavior(list, SnapPosition.Center),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(PetCharacter.entries) { index, character ->
                CharacterCard(
                    character = character,
                    isSelected = character == selected,
                    enabled = enabled,
                    portal = if (character == selected) portal else null,
                    onSelect = {
                        onSelect(character)
                        scope.launch { list.animateScrollToItem(index) }
                    },
                    modifier = Modifier
                        .width(CardWidth)
                        .shrinkAwayFromCenter(list, index),
                )
            }
        }
    }
}

/** Индекс карточки, ближайшей к середине карусели. */
private fun LazyListState.centeredIndex(): Int? {
    val info = layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    return info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - center) }?.index
}

private val CardWidth = 132.dp

/**
 * Карточка уменьшается и бледнеет по мере того, как уходит от середины.
 * Положение читается в graphicsLayer — прокрутка не пересобирает карточки.
 */
private fun Modifier.shrinkAwayFromCenter(list: LazyListState, index: Int): Modifier = graphicsLayer {
    val info = list.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@graphicsLayer
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val distance = kotlin.math.abs(item.offset + item.size / 2f - center) / item.size
    val t = distance.coerceIn(0f, 1f)
    scaleX = 1f - 0.25f * t
    scaleY = 1f - 0.25f * t
    alpha = 1f - 0.4f * t
}

/**
 * Карточка одного питомца.
 *
 * Вынесена из [CharacterPicker] не для красоты: `currentPose()` читает кадры анимации,
 * и та функция, в теле которой стоит чтение, пересобирается каждый кадр. Пока вызов
 * был в `CharacterPicker`, каждый кадр пересобирал обе карточки разом — вместе с
 * `PetView`, а тот на каждой пересборке заново достаёт слои питомца из ресурсов.
 * На экране выходило 5 кадров в секунду. Здесь пересборка ограничена одной карточкой.
 */
@Composable
private fun CharacterCard(
    character: PetCharacter,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    portal: PortalRun? = null,
) {
    val animation = rememberPetAnimation()
    val label = CharacterLabels.getValue(character)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(RadiusCard))
            // Выбранная карточка отличается и заливкой, и толщиной рамки,
            // а не одним цветом (ТЗ п. 3.6).
            .background(if (isSelected) FinneyYellow else FinneySand)
            .border(
                width = if (isSelected) StrokeBold else StrokeThin,
                color = FinneyInk,
                shape = RoundedCornerShape(RadiusCard),
            )
            .selectable(
                selected = isSelected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = {
                    onSelect()
                    animation.playJoy()
                },
            )
            .padding(8.dp)
            .clearAndSetSemantics { contentDescription = label },
    ) {
        // Живым остаётся только выбранный: неподвижному поза не нужна,
        // и лишний источник кадров ему незачем.
        val pose = rememberPoseProvider(animation)
        PetView(
            character = character,
            mood = PetMood.HAPPY,
            pose = if (isSelected) pose else StillPose,
            modifier = Modifier.fillMaxWidth().then(if (portal != null) Modifier.intoPortal(portal) else Modifier),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = FinneyInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Первая половина портала — на самой карточке: круг открывается позади питомца,
 * питомец крутится, уменьшается и пропадает в нём. Круг рисуется до слоя питомца,
 * поэтому не поворачивается вместе с ним. Сам питомец не меняется — только слой поверх.
 */
private fun Modifier.intoPortal(portal: PortalRun): Modifier = this
    .onGloballyPositioned {
        val bounds = it.boundsInRoot()
        portal.center = bounds.center
        portal.radius = minOf(bounds.width, bounds.height) * PortalShare
    }
    .drawBehind {
        drawPortal(Offset(size.width / 2, size.height / 2), minOf(size.width, size.height) * PortalShare * portal.open.value)
    }
    .graphicsLayer {
        val s = 1f - portal.swallow.value
        scaleX = s
        scaleY = s
        rotationZ = 200f * portal.swallow.value
    }

/** Радиус открывшегося портала — доля меньшей стороны питомца. */
private const val PortalShare = 0.42f

/** Поза невыбранного питомца: стоит ровно. Одна на всех, чтобы не плодить лямбды. */
private val StillPose: () -> PetPose = { PetPose() }

@Preview(showBackground = true, backgroundColor = 0xFFFFEDCD)
@Composable
private fun PetSetupContentPreview() {
    FinneyTheme {
        PetSetupContent(
            state = PetSetupUiState(isEditing = false, name = "Финни"),
            onNameChange = {}, onCharacterChange = {}, onSave = {},
        )
    }
}
