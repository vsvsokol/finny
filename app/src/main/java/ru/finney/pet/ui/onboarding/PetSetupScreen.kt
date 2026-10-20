package ru.finney.pet.ui.onboarding

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseInBack
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.profile.PetNameError
import ru.finney.pet.ui.components.ChargeButton
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.motion.PortalArrival
import ru.finney.pet.ui.motion.drawPortal
import ru.finney.pet.ui.pet.PetMood
import ru.finney.pet.ui.pet.PetView
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyPink
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.theme.FinneyYellow

/**
 * Имена питомцев для TalkBack. На карточках их нет: питомца выбирают по картинке,
 * а имя ему ребёнок даёт сам строкой ниже. В домене они названы по автору рисунка
 * (design/exports/pet) — ребёнку такое имя ничего не говорит.
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
        isDemo = isDemo && !isEditing,
        onNameChange = viewModel::setName,
        onCharacterChange = viewModel::setCharacter,
        onSave = viewModel::save,
    )
}

/**
 * Последний шаг знакомства по макету 10.2026: выбранный питомец крупно, на лучах,
 * и спрашивает, как его зовут; под ним карусель, имя и кнопки.
 *
 * Выбора цвета здесь нет: питомец начинает в родном цвете, окраски копят в копилке
 * и надевают в панели «Финни». Выбора глаз тоже нет: варианты глаз не нарисованы,
 * а выбор, который ничего не меняет на экране, ребёнка только путает. Сеттер в
 * [PetSetupViewModel] остался.
 */
@Composable
internal fun PetSetupContent(
    state: PetSetupUiState,
    isDemo: Boolean,
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
    // Назад — как системная кнопка: на знакомство или в настройки, смотря откуда пришли.
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val canSave = !state.isSaving && !state.isLoading

    Box(Modifier.fillMaxSize()) {
        SetupLayout(
            Modifier
                .fillMaxSize()
                .dotsBackdrop(FinneyCream)
                .systemBarsPadding()
                .padding(ArtPagePadding),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ArtSign(if (state.isEditing) "Настройка" else "Твой питомец", 46f)
                if (isDemo) DemoBadge()
            }

            Stage(
                character = state.appearance.character,
                name = state.name.trim(),
                portal = portal,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ArtGap),
            ) {
                CharacterPicker(
                    selected = state.appearance.character,
                    // Карусель стоит, пока питомец уходит: иначе выбор сменился бы на полпути.
                    enabled = !state.isLoading && !entering,
                    onSelect = onCharacterChange,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ArtSign("Как назовёшь?", 30f)
                    NameField(
                        name = state.name,
                        error = state.nameError,
                        maxLength = state.maxNameLength,
                        enabled = !state.isLoading,
                        onNameChange = onNameChange,
                        // «Готово» с пустым именем — то же, что «Начать игру»: покажет ошибку.
                        onDone = { if (!state.nameValid) onSave() },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ArtBackButton(onClick = { back?.onBackPressed() }, enabled = !entering)
                // «Начать игру» заряжается — начало игры бывает один раз и должно ощущаться.
                // С неверным именем заряжать нечего: обычное нажатие сразу покажет ошибку.
                // «Сохранить» в настройке — обычная кнопка, её жмут по делу.
                if (!state.isEditing && state.nameValid) {
                    ChargeButton(
                        text = "Начать игру",
                        onCharged = { if (animations) entering = true else onSave() },
                        enabled = canSave,
                        modifier = Modifier.weight(1f),
                        button = { onClick, interaction, modifier ->
                            ArtButton(
                                "Начать игру", onClick, modifier,
                                enabled = canSave, sound = null, interaction = interaction,
                            )
                        },
                    )
                } else {
                    ArtButton(
                        text = if (state.isEditing) "Сохранить" else "Начать игру",
                        onClick = onSave,
                        enabled = canSave,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        PortalOverlay(portal, blockTouches = entering)
    }
}

/** Синяя плашка под заголовком: профиль создаётся в демо-режиме. */
@Composable
private fun DemoBadge() {
    Text(
        "Демо: все игры открыты",
        style = artText(16f, 1.15f),
        color = Color.White,
        modifier = Modifier
            .padding(top = 2.dp)
            .plate(99.dp, ArtBlue, Color.Transparent, 0.dp, border = 3.dp)
            .padding(start = 15.dp, end = 15.dp, top = 3.dp, bottom = 6.dp),
    )
}

/**
 * Четыре блока по порядку: заголовок, сцена, выбор с именем, кнопки.
 *
 * Свободное место делится поровну: половина между заголовком и пузырём, половина
 * между полем имени и кнопками. В прототипе всё лишнее уходило под заголовок, и сцена
 * с каруселью проседали вниз. Если места мало, первым уменьшается питомец на сцене:
 * сцену меряем тем, что осталось после остальных блоков.
 *
 * Сцена кладётся под остальными блоками: лучи шире её и заходят под заголовок и карусель.
 */
@Composable
private fun SetupLayout(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        val viewportHeight = constraints.maxHeight
        Layout(content, Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { measurables, constraints ->
            val (title, stage, form, buttons) = measurables
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val t = title.measure(loose)
            val f = form.measure(loose)
            val b = buttons.measure(loose)
            val gap = ArtGap.roundToPx()
            // Прокрутка даёт неограниченную высоту: исходный размер окна берём снаружи.
            val height = viewportHeight
            val stageRoom = (height - t.height - f.height - b.height - gap * 3).coerceAtLeast(0)
            // Даже на низком экране остаются реплика и небольшой питомец.
            val s = stage.measure(loose.copy(maxHeight = maxOf(stageRoom, 160.dp.roundToPx())))
            val contentHeight = maxOf(height, t.height + s.height + f.height + b.height + gap * 3)
            val free = (contentHeight - t.height - s.height - gap - f.height - b.height).coerceAtLeast(0)
            val half = free / 2

            layout(constraints.maxWidth, contentHeight) {
                fun centred(w: Int) = (constraints.maxWidth - w) / 2
                var y = 0
                t.place(centred(t.width), y)
                y += t.height + half
                s.place(centred(s.width), y, zIndex = -1f)
                y += s.height + gap
                f.place(centred(f.width), y)
                y += f.height + half
                b.place(centred(b.width), y)
            }
        }
    }
}

/**
 * Выбранный питомец крупно, на вращающихся лучах, с репликой сверху. Пока имени нет,
 * он спрашивает, как его зовут; с именем — радуется ему.
 */
@Composable
private fun Stage(character: PetCharacter, name: String, portal: PortalRun) {
    // Лучи — за всей сценой, иначе легли бы поверх пузыря. Центр — середина питомца.
    var petHalf by remember { mutableFloatStateOf(0f) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.sunburst(440.dp) { Offset(it.width / 2, it.height - petHalf) },
    ) {
        ArtSay(
            AnnotatedString(if (name.isEmpty()) "Как меня зовут?" else "Ура! Меня зовут $name!"),
            tailUp = false,
            modifier = Modifier
                .widthIn(max = 270.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        PetView(
            character = character,
            mood = PetMood.HAPPY,
            pose = StillPose,
            // Вес без заполнения: питомец берёт сколько осталось, но не больше StagePet.
            modifier = Modifier
                .weight(1f, fill = false)
                .sizeIn(maxWidth = StagePet, maxHeight = StagePet)
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .onSizeChanged { petHalf = it.height / 2f }
                .intoPortal(portal)
                .bob(),
        )
    }
}

/** Наибольший размер питомца на сцене: картинка 230 dp шириной, как в прототипе. */
private val StagePet = 230.dp

/**
 * Портал по шагам: круг открывается за питомцем на сцене, питомец уменьшается
 * и уходит в него, круг растёт на весь экран. Всего ~0,7 с.
 * [center] и [radius] — где круг на экране: их пишет сцена, пока стоит на месте.
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
 * Вторая половина портала — круг, который растёт со сцены на весь экран. Лежит
 * поверх экрана целиком. Пока идёт переход, забирает касания — карусель под ним
 * не должна сменить питомца.
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

/**
 * Поле имени из прототипа: белое, тень по верху внутри, текст по центру. В фокусе —
 * жёлтое кольцо снаружи. Ошибка — розовой рамкой, встряской и словами под полем:
 * цвет не единственный признак (ТЗ п. 3.6).
 */
@Composable
private fun NameField(
    name: String,
    error: PetNameError?,
    maxLength: Int,
    enabled: Boolean,
    onNameChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val message = when (error) {
        PetNameError.BLANK -> "Придумай имя питомцу"
        PetNameError.TOO_LONG -> "Не длиннее $maxLength букв"
        null -> null
    }
    val focus = LocalFocusManager.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (error == null) return@LaunchedEffect
        shake.animateTo(0f, keyframes {
            durationMillis = 350
            -8f at 88
            8f at 262
        })
    }
    val style = artText(24f, 1.15f).copy(color = FinneyInk, textAlign = TextAlign.Center)

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            enabled = enabled,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(FinneyInk),
            interactionSource = source,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focus.clearFocus()
                onDone()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .graphicsLayer { translationX = shake.value.dp.toPx() }
                .fieldFrame(focused, error != null)
                .semantics { contentDescription = "Имя питомца" },
            decorationBox = { inner ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, bottom = 4.dp),
                ) {
                    if (name.isEmpty()) {
                        Text("Например, Финни", style = style.copy(color = FinneyInk.copy(alpha = 0.38f)))
                    }
                    inner()
                }
            },
        )
        if (message != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .drawBehind { drawCircle(FinneyPink) }
                        .padding(bottom = 2.dp),
                ) {
                    Text("!", style = artText(16f, 1f), color = Color.White)
                }
                ArtBody(message, 20f, color = ArtError, line = 1f)
            }
        }
    }
}

/** Рамка поля: кольцо фокуса 5 dp, белая заливка, тень 5 dp по верху, обводка 4 dp. */
private fun Modifier.fieldFrame(focused: Boolean, error: Boolean): Modifier = drawBehind {
    val r = 22.dp.toPx()
    val b = ArtBorder.toPx()
    if (focused) {
        val ring = 5.dp.toPx()
        drawRoundRect(FinneyYellow, Offset(-ring, -ring), Size(size.width + ring * 2, size.height + ring * 2), CornerRadius(r + ring))
    }
    drawRoundRect(Color.White, cornerRadius = CornerRadius(r))
    val inner = RoundRect(b, b, size.width - b, size.height - b, CornerRadius(r - b))
    val body = Path().apply { addRoundRect(inner) }
    val lowered = Path().apply { addRoundRect(inner.translate(Offset(0f, 5.dp.toPx()))) }
    drawPath(Path().apply { op(body, lowered, PathOperation.Difference) }, ArtSoft)
    drawRoundRect(
        if (error) FinneyPink else FinneyInk,
        Offset(b / 2, b / 2),
        Size(size.width - b, size.height - b),
        CornerRadius(r - b / 2),
        style = Stroke(b),
    )
}

/**
 * Выбор питомца — карусель из пяти карточек, на карточках только картинки.
 * Выбранный — тот, кто в середине (плейтест 28.09): листаешь — и сразу выбираешь,
 * без второго нажатия. Прокрутка защёлкивается по центру.
 */
@Composable
private fun CharacterPicker(
    selected: PetCharacter,
    enabled: Boolean,
    onSelect: (PetCharacter) -> Unit,
) {
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
            // срезались по линейке у края полей.
            .layout { measurable, constraints ->
                val bleed = ArtPagePadding.calculateLeftPadding(layoutDirection).roundToPx()
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
            contentPadding = PaddingValues(horizontal = side, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            flingBehavior = rememberSnapFlingBehavior(list, SnapPosition.Center),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(PetCharacter.entries) { index, character ->
                CharacterCard(
                    character = character,
                    isSelected = character == selected,
                    enabled = enabled,
                    onSelect = {
                        onSelect(character)
                        scope.launch { list.animateScrollToItem(index) }
                    },
                    modifier = Modifier.width(CardWidth),
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

/** Ширина карточки: в середине целиком, соседи видны почти целиком по бокам. */
private val CardWidth = 150.dp

/**
 * Карточка одного питомца, как в прототипе: белая с сиреневой полосой по низу;
 * выбранная — жёлтая с коралловой полосой и бликом, приподнята на 3 dp. Кроме цвета
 * выбранную отмечает зелёная галочка в углу (ТЗ п. 3.6: цвет — не единственный признак).
 */
@Composable
private fun CharacterCard(
    character: PetCharacter,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = CharacterLabels.getValue(character)
    Box(modifier.graphicsLayer { translationY = if (isSelected) -3.dp.toPx() else 0f }) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) {
                        Modifier.plate(26.dp, FinneyYellow, FinneyPeach, 10.dp).cardGlare()
                    } else {
                        Modifier.plate(26.dp, Color.White, ArtSoft, 7.dp)
                    },
                )
                .selectable(
                    selected = isSelected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onSelect,
                )
                .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)
                .clearAndSetSemantics { contentDescription = label },
        ) {
            // Картинка 78 dp ростом: при её пропорциях это 88 dp шириной.
            PetView(
                character = character,
                mood = PetMood.HAPPY,
                pose = StillPose,
                modifier = Modifier.size(88.dp),
            )
        }
        if (isSelected) {
            CheckBadge(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp))
        }
    }
}

/** Блик выбранной карточки: мягкий белый овал 30 × 18 в левом верхнем углу. */
private fun Modifier.cardGlare(): Modifier = drawBehind {
    val centre = Offset((ArtBorder + 26.dp).toPx(), (ArtBorder + 20.dp).toPx())
    val rx = 15.dp.toPx()
    val ry = 9.dp.toPx()
    // Круговой градиент, сжатый по высоте, — овал с размытым краем от 85 % радиуса.
    scale(1f, ry / rx, pivot = centre) {
        drawCircle(
            Brush.radialGradient(0.85f to Color.White, 1f to Color.White.copy(alpha = 0f), center = centre, radius = rx),
            radius = rx,
            center = centre,
        )
    }
}

private val Tick = PathParser().parsePathString("M5 12.5l4.5 4.5L19 7.5").toPath()

/** Зелёный кружок 38 dp с галочкой. Появляется «хлопком» за 0,25 с. */
@Composable
private fun CheckBadge(modifier: Modifier = Modifier) {
    val pop = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, tween(250)) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                alpha = (pop.value - 0.7f) / 0.3f
            }
            .plate(19.dp, ArtGreen, Color.Transparent, 0.dp, border = 3.dp),
    ) {
        Canvas(Modifier.size(22.dp)) {
            val k = size.width / 24f
            scale(k, k, pivot = Offset.Zero) {
                drawPath(Tick, FinneyInk, style = Stroke(3.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/**
 * Первая половина портала — на сцене: круг открывается позади питомца, питомец
 * крутится, уменьшается и пропадает в нём. Круг рисуется до слоя питомца,
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

@Preview(widthDp = 412, heightDp = 880)
@Composable
private fun PetSetupContentPreview() {
    FinneyTheme {
        PetSetupContent(
            state = PetSetupUiState(isEditing = false, name = ""),
            isDemo = true,
            onNameChange = {}, onCharacterChange = {}, onSave = {},
        )
    }
}
