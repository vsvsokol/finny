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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.finney.pet.R
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.buttonFill
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.pet.PetMood
import ru.finney.pet.ui.pet.PetPose
import ru.finney.pet.ui.pet.PetView
import ru.finney.pet.ui.pet.accessoryArt
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

// Панель «Финни / Комната»: снизу, с переключателем на рамке, строкой вкладок
// и рядом карточек. У Финни вкладки «Окрас / Одежда», у комнаты «Стена / Окно /
// Торшер». Комната над панелью остаётся видна, и камера подлетает к тому, что
// выбирают (RoomScene, focus и focusPet), — ребёнок меняет то, что видит.
// Выбор ставится сразу, без «Применить»: так его и пробуют.

/** Высота панели без системной полоски снизу: по ней комната знает, сколько закрыто. */
// 224, а не 214: у комнаты вкладки в два этажа (значок над подписью), иначе карточки срезало снизу.
val InteriorPickerHeight = 224.dp

/** Сколько висит предложение взять тему целиком: прочитать и решить, но не мешать дальше. */
private const val OFFER_MS = 4_500L

/** Пауза между частями, когда тему ставят целиком: каждая смена успевает быть замеченной. */
const val TAKE_ALL_STEP_MS = 420L

/** Вкладка панели. У питомца [room] нет, у комнаты — часть интерьера. */
enum class EditTab(val label: String, val room: InteriorPart?) {
    COLOUR("Окрас", null),
    OUTFIT("Одежда", null),
    WALL("Стена", InteriorPart.WALL),
    WINDOW("Окно", InteriorPart.WINDOW),
    LAMP("Торшер", InteriorPart.LAMP),
    RUG("Ковёр", InteriorPart.RUG),
    ;

    val pet: Boolean get() = room == null
}

/** Шляпа или очки в панели: и свои, и ещё не полученные — те с замком. */
data class Outfit(val id: String, val label: String, val owned: Boolean)

/** Как выглядит питомец сейчас — для карточек «Окрас» и «Одежда». */
data class PetLook(
    val character: PetCharacter,
    val worn: List<String>,
    val outfit: List<Outfit>,
    /** Надетая окраска; null — родной цвет. */
    val coloring: String? = null,
    val colorings: List<Coloring> = emptyList(),
)

/** Платная окраска персонажа (`PetLooks`): получена или её ещё копят. */
data class Coloring(val id: String, val owned: Boolean)

/**
 * Панель выбора облика питомца и интерьера.
 *
 * @param tab открытая вкладка.
 * @param onPick ребёнок выбрал для части комнаты вещь из темы.
 * @param onTakeAll поставить из темы всё: после выбора одной вещи панель предлагает
 *   взять остальные из той же темы — «Вся комната — «Цирк»?».
 * @param ownedRooms полученные темы ([RoomInterior.id]); остальные — с замком, как вещи.
 * @param onLocked нажали вещь или тему, которой ещё нет: их получают за цель в копилке.
 */
@Composable
fun InteriorPicker(
    interior: RoomInterior,
    look: PetLook,
    tab: EditTab,
    onTab: (EditTab) -> Unit,
    onPick: (InteriorPart, RoomInterior) -> Unit,
    onTakeAll: (RoomInterior) -> Unit,
    /** Надеть окраску; null — снять и вернуться к родному цвету. */
    onColoring: (String?) -> Unit = {},
    onWear: (String) -> Unit,
    onTakeOff: (String) -> Unit,
    onLocked: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    ownedRooms: Set<String> = Interiors.all.map { it.id }.toSet(),
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
    // Ушли к питомцу — предложение про комнату уже ни к чему.
    LaunchedEffect(tab.pet) { if (tab.pet) offer = null }
    // Возвращаются на ту вкладку половины, где были.
    var lastPet by remember { mutableStateOf(EditTab.COLOUR) }
    var lastRoom by remember { mutableStateOf(EditTab.WALL) }
    if (tab.pet) lastPet = tab else lastRoom = tab

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
                    EditTab.entries.filter { it.pet == tab.pet }.forEach { t ->
                        PartTab(t, selected = t == tab, onClick = { onTab(t) }, modifier = Modifier.weight(1f))
                    }
                }

                val list = rememberLazyListState()
                // Новая вкладка — ряд с начала: иначе он открывался прокрученным с прошлой.
                LaunchedEffect(tab) { list.scrollToItem(0) }
                LazyRow(
                    state = list,
                    flingBehavior = rememberSnapFlingBehavior(list),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    when (tab) {
                        EditTab.COLOUR -> {
                          // Первая карточка — родной цвет: ею окраску снимают.
                          item(key = "native") {
                            val chosen = look.coloring == null
                            PickCard(
                                description = "Окрас: родной",
                                selected = chosen,
                                animate = animations,
                                onClick = {
                                    if (chosen) return@PickCard
                                    sounds.play(Sfx.Tap)
                                    onColoring(null)
                                },
                            ) {
                                PetView(
                                    character = look.character,
                                    mood = PetMood.HAPPY,
                                    pose = StillPose,
                                    modifier = Modifier.padding(top = 6.dp).size(80.dp),
                                )
                            }
                          }
                          // Окраски — следом за родным, тем же рядом: это тоже «как выглядит Финни».
                          // Неполученные — бледные с замком, как вещи: их копят в копилке.
                          items(look.colorings, key = { it.id }) { coloring ->
                            val chosen = look.coloring == coloring.id
                            PickCard(
                                description = "Окраска",
                                state = if (coloring.owned) null else "ещё нет",
                                selected = chosen,
                                locked = !coloring.owned,
                                animate = animations,
                                onClick = {
                                    if (chosen) return@PickCard
                                    sounds.play(Sfx.Tap)
                                    if (coloring.owned) onColoring(coloring.id) else onLocked()
                                },
                            ) {
                                PetView(
                                    character = look.character,
                                    mood = PetMood.HAPPY,
                                    pose = StillPose,
                                    coloring = coloring.id,
                                    modifier = Modifier.padding(top = 6.dp).size(80.dp)
                                        .alpha(if (coloring.owned) 1f else 0.45f),
                                )
                            }
                          }
                        }

                        EditTab.OUTFIT -> items(look.outfit, key = { it.id }) { item ->
                            val worn = item.id in look.worn
                            PickCard(
                                description = item.label,
                                state = if (worn) "надето" else if (item.owned) "не надето" else "ещё нет",
                                selected = worn,
                                locked = !item.owned,
                                animate = animations,
                                onClick = {
                                    sounds.play(Sfx.Tap)
                                    when {
                                        !item.owned -> onLocked()
                                        worn -> onTakeOff(item.id)
                                        else -> onWear(item.id)
                                    }
                                },
                            ) {
                                accessoryArt(item.id)?.let { art ->
                                    Image(
                                        painter = painterResource(art.res),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(top = 10.dp)
                                            .size(74.dp, 72.dp)
                                            .alpha(if (item.owned) 1f else 0.45f),
                                    )
                                }
                            }
                        }

                        else -> {
                            val part = tab.room!!
                            items(Interiors.all, key = { it.id }) { theme ->
                                val chosen = interior.themeOf(part) == theme
                                val owned = theme.id in ownedRooms
                                PickCard(
                                    description = "${part.label}: ${theme.label}",
                                    label = theme.label,
                                    state = if (owned) null else "ещё нет",
                                    selected = chosen,
                                    locked = !owned,
                                    animate = animations,
                                    onClick = {
                                        if (chosen) return@PickCard
                                        sounds.play(Sfx.Tap)
                                        if (!owned) return@PickCard onLocked()
                                        onPick(part, theme)
                                        val rest = InteriorPart.entries.filter { it != part }
                                        offer = theme.takeIf { rest.any { p -> interior.themeOf(p) != theme } }
                                    },
                                ) {
                                    PartThumb(theme, part, Modifier.padding(top = 6.dp).size(74.dp, 80.dp).alpha(if (owned) 1f else 0.45f))
                                }
                            }
                        }
                    }
                }
            }

            // Переключатель висит на рамке, где раньше был заголовок.
            Row(
                modifier = Modifier.align(Alignment.TopStart).offset(x = 14.dp, y = (-26).dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SideTab("Финни", selected = tab.pet, onClick = { onTab(lastPet) }) {
                    PetView(look.character, PetMood.HAPPY, StillPose, Modifier.size(32.dp), coloring = look.coloring)
                }
                SideTab("Комната", selected = !tab.pet, onClick = { onTab(lastRoom) }) {
                    Canvas(Modifier.size(26.dp)) { houseIcon() }
                }
            }
            // «Готово» — круглой кнопкой на рамке, а не рядом снизу: панель ниже,
            // и над ней видно больше комнаты.
            DoneButton(onDone, Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = (-28).dp))
        }
    }
}

private val PanelShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)

private val StillPose: () -> PetPose = { PetPose() }

/** Половина панели на рамке: «Финни» или «Комната». Выбранная — как основная кнопка кита. */
@Composable
private fun SideTab(label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val sounds = LocalSounds.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .height(48.dp)
            .graphicsLayer {
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
            .semantics { this.selected = selected }
            .padding(start = 8.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(label, style = MaterialTheme.typography.titleMedium, color = FinneyInk, maxLines = 1)
    }
}

/** Вкладка: выбранная — как основная кнопка кита, остальные — спокойные, песочные. */
@Composable
private fun PartTab(tab: EditTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sounds = LocalSounds.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // У комнаты четыре вкладки: в ряд значок с подписью не помещаются уже на 360 dp с крупным
    // шрифтом, поэтому значок над подписью, а подпись ужимается, если и так не влезла.
    val stacked = tab.room != null
    val shape = if (stacked) RoundedCornerShape(20.dp) else RoundedCornerShape(percent = 50)
    val lift by animateDpAsState(if (selected) (-2).dp else 0.dp, spring(dampingRatio = 0.5f), label = "tabLift")
    val tabModifier = modifier
            .height(if (stacked) 58.dp else 48.dp)
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
            .semantics { this.selected = selected }
    val style = MaterialTheme.typography.titleMedium.copy(color = FinneyInk)
    if (stacked) {
        Column(
            modifier = tabModifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(20.dp)) { tabIcon(tab) }
            BasicText(
                tab.label,
                style = style,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize),
            )
        }
    } else {
        Row(
            modifier = tabModifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tab == EditTab.OUTFIT) {
                FinneyIcon(FinneyIcons.Hanger, size = 24.dp)
            } else {
                Canvas(Modifier.size(24.dp)) { tabIcon(tab) }
            }
            Text(tab.label, style = style, maxLines = 1)
        }
    }
}

/**
 * Карточка выбора: в ней нарисована сама вещь, а не цветная плашка. Выбранная
 * приподнимается, желтеет и несёт галочку — не только цвет (ТЗ п. 3.6).
 * Вещь, которой ещё нет, бледная и с замком.
 */
@Composable
private fun PickCard(
    description: String,
    selected: Boolean,
    animate: Boolean,
    onClick: () -> Unit,
    label: String? = null,
    state: String? = null,
    locked: Boolean = false,
    picture: @Composable () -> Unit,
) {
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
                    contentDescription = description
                    state?.let { stateDescription = it }
                    this.selected = selected
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            picture()
            label?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium,
                    color = FinneyInk,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        if (selected) {
            Badge(Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-10).dp)) { tick() }
        } else if (locked) {
            Badge(Modifier.align(Alignment.TopEnd).padding(6.dp), FinneyCream) { lock() }
        }
    }
}

/** Кружок в углу карточки: галочка выбранного или замок. */
@Composable
private fun Badge(modifier: Modifier, fill: Color = FinneyYellow, mark: DrawScope.() -> Unit) {
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(fill)
            .border(StrokeRegular, FinneyInk, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp), onDraw = mark)
    }
}

private fun DrawScope.tick() {
    val path = Path().apply {
        moveTo(size.width * 0.12f, size.height * 0.55f)
        lineTo(size.width * 0.4f, size.height * 0.82f)
        lineTo(size.width * 0.9f, size.height * 0.2f)
    }
    drawPath(path, FinneyInk, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.lock() {
    val u = size.width / 16f
    val stroke = Stroke(2.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawRoundRect(FinneyInk, Offset(2.5f * u, 7 * u), Size(11 * u, 8 * u), CornerRadius(1.5f * u), style = stroke)
    val shackle = Path().apply {
        moveTo(5 * u, 7 * u); lineTo(5 * u, 5 * u)
        cubicTo(5 * u, 1.5f * u, 11 * u, 1.5f * u, 11 * u, 5 * u)
        lineTo(11 * u, 7 * u)
    }
    drawPath(shackle, FinneyInk, style = stroke)
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
 * задника с полом, окно — вид с рамкой, торшер и ковёр — целиком.
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

        InteriorPart.RUG -> {
            val rug = thumbBitmap(theme.rug.image)
            Canvas(modifier) {
                rug ?: return@Canvas
                // Ковёр плоский и широкий: по ширине карточки, посередине по высоте.
                val h = size.width / theme.rug.aspect
                drawImage(
                    rug,
                    dstOffset = IntOffset(0, ((size.height - h) / 2f).toInt()),
                    dstSize = IntSize(size.width.toInt(), h.toInt()),
                )
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

/** Значки вкладок обводкой ink: капля краски, обои в кирпичик, окно с перекладиной, торшер, ковёр. */
private fun DrawScope.tabIcon(tab: EditTab) {
    val u = size.width / 26f
    val path = Path()
    when (tab) {
        EditTab.COLOUR -> {
            path.moveTo(13 * u, 3 * u)
            path.cubicTo(17 * u, 9 * u, 20 * u, 12 * u, 20 * u, 16 * u)
            path.cubicTo(20 * u, 25 * u, 6 * u, 25 * u, 6 * u, 16 * u)
            path.cubicTo(6 * u, 12 * u, 9 * u, 9 * u, 13 * u, 3 * u)
            path.close()
        }

        EditTab.OUTFIT -> Unit

        EditTab.WALL -> {
            path.addRect(Rect(3 * u, 4 * u, 23 * u, 22 * u))
            path.moveTo(3 * u, 10 * u); path.lineTo(23 * u, 10 * u)
            path.moveTo(3 * u, 16 * u); path.lineTo(23 * u, 16 * u)
            path.moveTo(10 * u, 4 * u); path.lineTo(10 * u, 10 * u)
            path.moveTo(16 * u, 10 * u); path.lineTo(16 * u, 16 * u)
            path.moveTo(10 * u, 16 * u); path.lineTo(10 * u, 22 * u)
        }

        EditTab.WINDOW -> {
            path.addRect(Rect(5 * u, 4 * u, 21 * u, 23 * u))
            path.moveTo(13 * u, 4 * u); path.lineTo(13 * u, 23 * u)
            path.moveTo(5 * u, 13 * u); path.lineTo(21 * u, 13 * u)
            path.moveTo(2 * u, 3 * u); path.lineTo(24 * u, 3 * u)
        }

        EditTab.LAMP -> {
            path.moveTo(7 * u, 3 * u); path.lineTo(19 * u, 3 * u); path.lineTo(22 * u, 11 * u)
            path.lineTo(4 * u, 11 * u); path.close()
            path.moveTo(13 * u, 11 * u); path.lineTo(13 * u, 22 * u)
            path.moveTo(8 * u, 23 * u); path.lineTo(18 * u, 23 * u)
        }

        // Овальный ковёр на полу с каймой.
        EditTab.RUG -> {
            path.addOval(Rect(2 * u, 8 * u, 24 * u, 20 * u))
            path.addOval(Rect(7 * u, 11 * u, 19 * u, 17 * u))
        }
    }
    drawPath(path, FinneyInk, style = Stroke(2.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Домик для половины «Комната». */
private fun DrawScope.houseIcon() {
    val u = size.width / 26f
    val path = Path().apply {
        moveTo(3 * u, 12 * u); lineTo(13 * u, 4 * u); lineTo(23 * u, 12 * u)
        moveTo(6 * u, 10 * u); lineTo(6 * u, 22 * u); lineTo(20 * u, 22 * u); lineTo(20 * u, 10 * u)
        moveTo(11 * u, 22 * u); lineTo(11 * u, 16 * u); lineTo(15 * u, 16 * u); lineTo(15 * u, 22 * u)
    }
    drawPath(path, FinneyInk, style = Stroke(2.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Preview(widthDp = 360, heightDp = 480, showBackground = true, backgroundColor = 0xFF8279BB)
@Composable
private fun InteriorPickerPreview() {
    FinneyTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            InteriorPicker(
                interior = Interiors.Default.withPart(InteriorPart.WINDOW, Interiors.Circus),
                look = PetLook(PetCharacter.PUSHISTIK, listOf("hat_pirate"), listOf(Outfit("hat_pirate", "Пиратская шляпа", true))),
                tab = EditTab.WINDOW,
                onTab = {},
                onPick = { _, _ -> },
                onTakeAll = {},
                onWear = {},
                onTakeOff = {},
                onLocked = {},
                onDone = {},
            )
        }
    }
}
