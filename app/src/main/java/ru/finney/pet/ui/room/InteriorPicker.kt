package ru.finney.pet.ui.room

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.finney.pet.R
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.components.buttonFill
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneySand
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.StrokeBold
import ru.finney.pet.ui.theme.StrokeRegular

// Выбор интерьера: панель снизу с вкладками «Стена / Окно / Торшер» и рядом
// карточек. Комната над панелью остаётся видна, и камера подлетает к той
// вещи, что выбирают (RoomScene, focus), — ребёнок меняет то, что видит.
// Выбор ставится в комнату сразу, без «Применить»: так его и пробуют.

/** Высота панели без системной полоски снизу: по ней комната знает, сколько закрыто. */
val InteriorPickerHeight = 214.dp

/** Сколько висит предложение взять тему целиком: прочитать и решить, но не мешать дальше. */
private const val OFFER_MS = 4_500L

/** Пауза между частями, когда тему ставят целиком: каждая смена успевает быть замеченной. */
const val TAKE_ALL_STEP_MS = 420L

/**
 * Панель выбора интерьера.
 *
 * @param part открытая вкладка.
 * @param onPick ребёнок выбрал для [part] вещь из темы.
 * @param onTakeAll поставить из темы всё: после выбора одной вещи панель предлагает
 *   взять остальные из той же темы — «Вся комната — «Цирк»?».
 */
@Composable
fun InteriorPicker(
    interior: RoomInterior,
    part: InteriorPart,
    onPart: (InteriorPart) -> Unit,
    onPick: (InteriorPart, RoomInterior) -> Unit,
    onTakeAll: (RoomInterior) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sounds = LocalSounds.current
    val animations = LocalAnimations.current
    // Тема, которую предлагают взять целиком; null — предложения нет.
    var offer by remember { mutableStateOf<RoomInterior?>(null) }
    LaunchedEffect(offer) {
        if (offer == null) return@LaunchedEffect
        delay(OFFER_MS)
        offer = null
    }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(64.dp), contentAlignment = Alignment.BottomCenter) {
            val shown = offer
            androidx.compose.animation.AnimatedVisibility(
                visible = shown != null,
                enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)),
                exit = scaleOut() + fadeOut(),
            ) {
                var last by remember { mutableStateOf(shown) }
                if (shown != null) last = shown
                last?.let { theme ->
                    TakeAllOffer(theme.label) {
                        offer = null
                        onTakeAll(theme)
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PanelShape)
                    .background(FinneyCream)
                    .border(StrokeBold, FinneyInk, PanelShape)
                    .navigationBarsPadding()
                    .height(InteriorPickerHeight)
                    .padding(top = 28.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    InteriorPart.entries.forEach { p ->
                        PartTab(p, selected = p == part, onClick = { onPart(p) }, modifier = Modifier.weight(1f))
                    }
                }

                val list = rememberLazyListState()
                // Новая вкладка — ряд с начала: иначе он открывался прокрученным с прошлой.
                LaunchedEffect(part) { list.scrollToItem(0) }
                LazyRow(
                    state = list,
                    flingBehavior = rememberSnapFlingBehavior(list),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    items(Interiors.all, key = { it.id }) { theme ->
                        val chosen = interior.themeOf(part) == theme
                        ThemeCard(
                            theme = theme,
                            part = part,
                            selected = chosen,
                            animate = animations,
                            onClick = {
                                if (chosen) return@ThemeCard
                                sounds.play(Sfx.Tap)
                                onPick(part, theme)
                                val rest = InteriorPart.entries.filter { it != part }
                                offer = theme.takeIf { rest.any { p -> interior.themeOf(p) != theme } }
                            },
                        )
                    }
                }

            }

            // Заголовок висит на рамке, как в ките.
            OutlinedText(
                "Моя комната",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-26).dp),
            )
            // «Готово» — круглой кнопкой на рамке, а не рядом снизу: панель ниже,
            // и над ней видно больше комнаты.
            DoneButton(onDone, Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = (-28).dp))
        }
    }
}

private val PanelShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)

/** Вкладка: выбранная — как основная кнопка кита, остальные — спокойные, песочные. */
@Composable
private fun PartTab(part: InteriorPart, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sounds = LocalSounds.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(percent = 50)
    val lift by animateDpAsState(if (selected) (-2).dp else 0.dp, spring(dampingRatio = 0.5f), label = "tabLift")
    Row(
        modifier = modifier
            .height(48.dp)
            .graphicsLayer {
                translationY = lift.toPx()
                val s = if (pressed) 0.96f else 1f
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.buttonFill(pressed, round = false)
                } else {
                    Modifier.background(if (pressed) FinneyCream else FinneySand).border(StrokeRegular, FinneyInk, shape)
                },
            )
            .clickable(interactionSource = source, indication = null, role = Role.Tab) {
                if (!selected) sounds.play(Sfx.Tap)
                onClick()
            }
            .semantics { this.selected = selected },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(24.dp)) { partIcon(part) }
        Text(part.label, style = MaterialTheme.typography.titleMedium, color = FinneyInk, maxLines = 1)
    }
}

/**
 * Карточка темы: в ней нарисована сама вещь, а не цветная плашка. Выбранная
 * приподнимается, желтеет и несёт галочку — не только цвет (ТЗ п. 3.6).
 */
@Composable
private fun ThemeCard(theme: RoomInterior, part: InteriorPart, selected: Boolean, animate: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val lift by animateDpAsState(
        if (selected) (-8).dp else 0.dp,
        if (animate) spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "cardLift",
    )
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .width(92.dp)
            .height(120.dp)
            .graphicsLayer {
                translationY = lift.toPx()
                val s = if (pressed) 0.96f else 1f
                scaleX = s
                scaleY = s
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(if (selected) FinneyYellow else FinneySand)
                .drawBehind { if (selected) drawRect(FinneyPeach, Offset(0f, size.height - 10.dp.toPx()), Size(size.width, 10.dp.toPx())) }
                .border(if (selected) StrokeBold else StrokeRegular, FinneyInk, shape)
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = "${part.label}: ${theme.label}"
                    this.selected = selected
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PartThumb(theme, part, Modifier.padding(top = 6.dp).size(74.dp, 80.dp))
            Text(
                theme.label,
                style = MaterialTheme.typography.titleMedium,
                color = FinneyInk,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-10).dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(FinneyYellow)
                    .border(StrokeRegular, FinneyInk, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(16.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.12f, size.height * 0.55f)
                        lineTo(size.width * 0.4f, size.height * 0.82f)
                        lineTo(size.width * 0.9f, size.height * 0.2f)
                    }
                    drawPath(path, FinneyInk, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

/** Круглая кнопка кита с галочкой: закрыть выбор, всё выбранное уже стоит в комнате. */
@Composable
private fun DoneButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sounds = LocalSounds.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(56.dp)
            .graphicsLayer {
                val s = if (pressed) 0.96f else 1f
                scaleX = s
                scaleY = s
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                sounds.play(Sfx.Back)
                onClick()
            }
            .semantics { contentDescription = "Готово" },
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.home_round_button_base), null, Modifier.fillMaxSize())
        Canvas(Modifier.size(24.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.12f, size.height * 0.55f)
                lineTo(size.width * 0.4f, size.height * 0.82f)
                lineTo(size.width * 0.9f, size.height * 0.2f)
            }
            drawPath(path, FinneyInk, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** «Вся комната — «Цирк»?» — взять из темы и остальное одной кнопкой. */
@Composable
private fun TakeAllOffer(label: String, onAccept: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(FinneyCream)
            .border(StrokeRegular, FinneyInk, RoundedCornerShape(percent = 50))
            .padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Вся комната — «$label»?", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
        FinneyButton("Да!", onClick = onAccept, fillWidth = false)
    }
}

/**
 * Вещь темы для карточки — из тех же слоёв, что и в комнате. Стена — верх
 * задника с полом, окно — вид с рамкой, торшер — целиком.
 */
@Composable
private fun PartThumb(theme: RoomInterior, part: InteriorPart, modifier: Modifier = Modifier) {
    when (part) {
        InteriorPart.WALL -> {
            val back = thumbBitmap(theme.wall.back.image)
            Canvas(modifier) {
                back ?: return@Canvas
                // Обрезка по высоте карточки: стена и полоска пола у шва.
                val bottom = (theme.wall.floorTop + 0.15f).coerceAtMost(1f)
                val srcH = back.height * bottom
                val srcW = (srcH * size.width / size.height).coerceAtMost(back.width.toFloat())
                val left = (back.width - srcW) / 2f
                drawImage(
                    back,
                    srcOffset = IntOffset(left.toInt(), 0),
                    srcSize = IntSize(srcW.toInt(), srcH.toInt()),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )
            }
        }

        InteriorPart.WINDOW -> {
            val window = theme.window
            val view = when (val v = window.view) {
                is WindowView.Sky -> v.strip
                is WindowView.Picture -> v.layer
            }
            val viewImage = thumbBitmap(view.image)
            val frameImage = thumbBitmap(window.frame.image)
            Canvas(modifier) {
                val area = window.frame.rect
                val unit = fitUnit(area)
                clipRect(
                    left = (window.hole.left - area.left) * unit.width + origin(area, unit).x,
                    top = (window.hole.top - area.top) * unit.height + origin(area, unit).y,
                    right = (window.hole.right - area.left) * unit.width + origin(area, unit).x,
                    bottom = (window.hole.bottom - area.top) * unit.height + origin(area, unit).y,
                ) {
                    if (window.view is WindowView.Sky) drawRect(SkyColor)
                    viewImage?.let { drawLayer(it, view.rect, area, unit) }
                }
                frameImage?.let { drawLayer(it, area, area, unit) }
            }
        }

        InteriorPart.LAMP -> {
            val lamp = thumbBitmap(theme.lamp.layer.image)
            Canvas(modifier) {
                val area = theme.lamp.layer.rect
                lamp?.let { drawLayer(it, area, area, fitUnit(area)) }
            }
        }
    }
}

/** Сколько пикселей на долю холста, чтобы [area] целиком влезла в карточку. Холст вытянут: 0.6. */
private fun DrawScope.fitUnit(area: RelRect): Size {
    val perWidth = minOf(size.width / area.width, size.height / (area.height / Room.CANVAS_RATIO))
    return Size(perWidth, perWidth / Room.CANVAS_RATIO)
}

/** Левый верхний угол [area] в карточке: по центру. */
private fun DrawScope.origin(area: RelRect, unit: Size) =
    Offset((size.width - area.width * unit.width) / 2f, (size.height - area.height * unit.height) / 2f)

private fun DrawScope.drawLayer(image: ImageBitmap, rect: RelRect, area: RelRect, unit: Size) {
    val at = origin(area, unit)
    drawImage(
        image,
        dstOffset = IntOffset(
            (at.x + (rect.left - area.left) * unit.width).toInt(),
            (at.y + (rect.top - area.top) * unit.height).toInt(),
        ),
        dstSize = IntSize((rect.width * unit.width).toInt(), (rect.height * unit.height).toInt()),
    )
}

/**
 * Слой комнаты, уменьшенный для карточки. Слои — во весь холст (задник 1080 px
 * шириной), и шесть задников целиком — под 50 МБ памяти ради карточек в 92 dp.
 * Читается в фоне, уменьшенным ещё при чтении.
 */
@Composable
private fun thumbBitmap(@DrawableRes res: Int): ImageBitmap? {
    val resources = LocalContext.current.resources
    val image by produceState<ImageBitmap?>(null, res) {
        value = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, res, bounds)
            // До 300 px по большей стороне: карточка — 92 dp, на телефоне около 280 px.
            while (maxOf(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2) >= 300) options.inSampleSize *= 2
            BitmapFactory.decodeResource(resources, res, options)?.asImageBitmap()
        }
    }
    return image
}

/** Значки вкладок обводкой ink: обои в кирпичик, окно с перекладиной, торшер. */
private fun DrawScope.partIcon(part: InteriorPart) {
    val u = size.width / 26f
    val path = Path()
    when (part) {
        InteriorPart.WALL -> {
            path.addRect(Rect(3 * u, 4 * u, 23 * u, 22 * u))
            path.moveTo(3 * u, 10 * u); path.lineTo(23 * u, 10 * u)
            path.moveTo(3 * u, 16 * u); path.lineTo(23 * u, 16 * u)
            path.moveTo(10 * u, 4 * u); path.lineTo(10 * u, 10 * u)
            path.moveTo(16 * u, 10 * u); path.lineTo(16 * u, 16 * u)
            path.moveTo(10 * u, 16 * u); path.lineTo(10 * u, 22 * u)
        }

        InteriorPart.WINDOW -> {
            path.addRect(Rect(5 * u, 4 * u, 21 * u, 23 * u))
            path.moveTo(13 * u, 4 * u); path.lineTo(13 * u, 23 * u)
            path.moveTo(5 * u, 13 * u); path.lineTo(21 * u, 13 * u)
            path.moveTo(2 * u, 3 * u); path.lineTo(24 * u, 3 * u)
        }

        InteriorPart.LAMP -> {
            path.moveTo(7 * u, 3 * u); path.lineTo(19 * u, 3 * u); path.lineTo(22 * u, 11 * u)
            path.lineTo(4 * u, 11 * u); path.close()
            path.moveTo(13 * u, 11 * u); path.lineTo(13 * u, 22 * u)
            path.moveTo(8 * u, 23 * u); path.lineTo(18 * u, 23 * u)
        }
    }
    drawPath(path, FinneyInk, style = Stroke(2.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Кнопка на главной, которая открывает выбор: круг из кита и валик поверх.
 * Валика в ките нет, он нарисован здесь теми же обводкой и цветами.
 */
@Composable
fun RoomEditButton(onClick: () -> Unit, modifier: Modifier = Modifier, diameter: Dp = 56.dp) {
    val sounds = LocalSounds.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(diameter)
            .graphicsLayer {
                val s = if (pressed) 0.96f else 1f
                scaleX = s
                scaleY = s
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                sounds.play(Sfx.Tap)
                onClick()
            }
            .semantics { contentDescription = "Обустроить комнату" },
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.home_round_button_base), null, Modifier.fillMaxSize())
        Canvas(Modifier.size(diameter * 0.55f)) {
            val u = size.width / 36f
            val stroke = Stroke(3.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val roller = RoundRect(4 * u, 3 * u, 28 * u, 14 * u, CornerRadius(5.5f * u))
            val body = Path().apply { addRoundRect(roller) }
            drawPath(body, FinneyPeach)
            drawPath(body, FinneyInk, style = stroke)
            val handle = Path().apply {
                moveTo(28 * u, 8.5f * u); lineTo(32 * u, 8.5f * u); lineTo(32 * u, 17.5f * u)
                lineTo(17 * u, 17.5f * u); lineTo(17 * u, 23.5f * u)
            }
            drawPath(handle, FinneyInk, style = stroke)
            drawRoundRect(
                FinneyInk,
                topLeft = Offset(13.5f * u, 23 * u),
                size = Size(7 * u, 13 * u),
                cornerRadius = CornerRadius(3 * u),
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 480, showBackground = true, backgroundColor = 0xFF8279BB)
@Composable
private fun InteriorPickerPreview() {
    FinneyTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            InteriorPicker(
                interior = Interiors.Default.withPart(InteriorPart.WINDOW, Interiors.Circus),
                part = InteriorPart.WINDOW,
                onPart = {},
                onPick = { _, _ -> },
                onTakeAll = {},
                onDone = {},
            )
        }
    }
}
