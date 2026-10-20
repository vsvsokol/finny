package ru.finney.pet.ui.room

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.launch
import ru.finney.pet.ui.theme.FinneyInk

// Игрушки в игровой. Купленная игрушка лежит на полу; её можно взять пальцем и
// положить в любое место пола — там она и останется. Рядом с питомцем он тянется
// к ней и радуется. Кто кого заслоняет, решает глубина: игрушка ближе к зрителю,
// чем ступни питомца, лежит перед ним, дальше — за ним. Тень у игрушки всегда
// своя: лежит — у её низа, в руке — на полу под ней. Что это даёт в игре, решает
// главный экран: сцена только сообщает, где игрушка и как её двигают.

/** Сторона игрушки — доля видимой ширины экрана. На телефоне около 60 dp. */
private const val TOY_SCREEN_SIZE = 0.155f

/**
 * Низ игрушки на своём месте — доля высоты холста: ряд чуть ближе к зрителю,
 * чем ступни питомца ([PET_FEET]).
 */
private const val TOY_BOTTOM = 0.76f

/**
 * Куда по глубине можно положить игрушку — её низ, доли высоты холста. Капсулы
 * в игровой нет, и класть можно глубже, чем в спальне (там упирались в неё на 0.665).
 * Дальше 0.60 игрушка той же величины, что у ног, выглядит великаншей. Ближе — ряд кнопок комнат.
 */
private const val FLOOR_BACK = 0.60f
private const val FLOOR_FRONT = 0.78f

/** Ступни питомца в игровой: игрушка ниже этой линии — перед ним, выше — за ним. */
private const val PET_FEET = Room.PLAY_PET_FEET

/**
 * Места игрушек — середины, доли видимой ширины экрана. Питомец стоит посередине,
 * поэтому игрушки — по бокам от него, от краёв к середине, по очереди справа и слева.
 */
private val ToyPlaces = listOf(0.88f, 0.12f, 0.76f, 0.24f, 0.64f, 0.36f)

/**
 * Глаза у игрушки с мордой: середины её нарисованных глаз и их ширина — доли рисунка,
 * сам рисунок — [artW] × [artH] пикселей. Промерено по тёмным пятнам в item_toy_*.webp.
 */
private class ToyEyes(val left: Offset, val right: Offset, val size: Float, val artW: Float, val artH: Float)

/**
 * У кого есть морда. Когда питомец прыжком уходит из игровой вправо, им на миг
 * вырастают большие глаза и провожают его — игрушки будто живые. Шутка,
 * а не подсказка: без звука и без влияния на игру.
 */
private val EyesByToy = mapOf(
    "toy_bear" to ToyEyes(Offset(0.392f, 0.219f), Offset(0.604f, 0.219f), size = 0.065f, artW = 232f, artH = 256f),
    "toy_duck" to ToyEyes(Offset(0.331f, 0.204f), Offset(0.666f, 0.204f), size = 0.10f, artW = 256f, artH = 242f),
)

/** Сколько игрушек помещается в игровой. Магазин не продаёт больше (ContentTest). */
const val MAX_ROOM_TOYS = 6

/** Как игрушка лежит на полу: тень под нижним краем рисунка. */
private val ToyFooting = Footing(feetX = 0.5f, feetY = 0.92f)

/** Насколько игрушка крупнее в руке: её «подняли» ближе к экрану. */
private const val HELD_SCALE = 1.2f

/** На сколько поднята игрушка в руке — доля её высоты. Тень остаётся на полу. */
internal const val HELD_LIFT = 0.45f

/** Поднятая игрушка дальше от пола — тень под ней бледнее на эту долю. */
private const val HELD_SHADOW_FADE = 0.45f

/** Игрушка перед питомцем — над ним, но под поднятой. */
private const val FRONT_Z = 1f

/** Поднятая игрушка — над питомцем и дверью капсулы. */
private const val HELD_Z = 2f

/** Зона захвата — над всем в комнате, даже над поднятой игрушкой. */
private const val GRAB_Z = 3f

/**
 * Где лежат игрушки: [left]..[right] — видимая часть холста по ширине, доли его ширины.
 * Лишние сверх [MAX_ROOM_TOYS] не показываются.
 */
internal fun toySlots(count: Int, left: Float, right: Float): List<RelRect> {
    val span = right - left
    val size = span * TOY_SCREEN_SIZE
    val height = size * Room.CANVAS_RATIO
    return ToyPlaces.take(count).map { place ->
        val centre = left + span * place
        RelRect(centre - size / 2f, TOY_BOTTOM - height, centre + size / 2f, TOY_BOTTOM)
    }
}

/** Видимая часть пола по ширине — доли ширины холста: за край игрушку не утащить. */
@Immutable
internal data class ToyFloor(val left: Float, val right: Float)

/**
 * Куда игрушки передвинули — сдвиг от своего места, доли холста, по id.
 * Живёт выше комнаты: переход на кухню и обратно, магазин и поворот экрана его не сбрасывают.
 */
@Composable
internal fun rememberToyMoves(): SnapshotStateMap<String, Offset> = rememberSaveable(
    saver = listSaver(
        save = { moves -> moves.flatMap { (id, at) -> listOf(id, at.x, at.y) } },
        restore = { saved ->
            mutableStateMapOf<String, Offset>().apply {
                saved.chunked(3).forEach { (id, x, y) -> put(id as String, Offset(x as Float, y as Float)) }
            }
        },
    ),
) { mutableStateMapOf() }

/** Сдвиг, при котором [place] остаётся на полу [floor]. */
private fun clampToFloor(place: RelRect, shift: Offset, floor: ToyFloor): Offset = Offset(
    x = shift.x.coerceIn(floor.left - place.left, maxOf(floor.left - place.left, floor.right - place.right)),
    y = shift.y.coerceIn(FLOOR_BACK - place.bottom, FLOOR_FRONT - place.bottom),
)

/**
 * На сколько сдвинуть по горизонтали игрушку [toy], чтобы она не пряталась за питомцем [pet]:
 * 0 — если она перед ним (ниже ступней) или видна хотя бы наполовину. Сдвиг — к ближнему
 * краю питомца, а если там кончается пол — к дальнему.
 */
internal fun outFromBehind(toy: RelRect, pet: RelRect, floor: ToyFloor): Float {
    if (toy.bottom > PET_FEET) return 0f
    // У рисунка питомца прозрачные поля: прячет игрушку только середина квадрата.
    val left = pet.left + pet.width * PET_BODY_INSET
    val right = pet.right - pet.width * PET_BODY_INSET
    val hidden = minOf(toy.right, right) - maxOf(toy.left, left)
    if (hidden <= toy.width * 0.5f) return 0f
    val gap = toy.width * 0.1f
    val toLeft = left - gap - toy.right
    val toRight = right + gap - toy.left
    val leftFits = toy.left + toLeft >= floor.left
    val rightFits = toy.right + toRight <= floor.right
    val nearLeft = -toLeft < toRight
    return when {
        nearLeft && leftFits -> toLeft
        rightFits -> toRight
        leftFits -> toLeft
        else -> 0f
    }
}

/** Насколько с каждого бока квадрата питомца — прозрачные поля, доля его ширины. */
private const val PET_BODY_INSET = 0.18f

/** Где игрушка с местом [place] лежит сейчас, с учётом сдвига из [moves]. */
internal fun toyHere(toyId: String, place: RelRect, moves: Map<String, Offset>, floor: ToyFloor): RelRect {
    val shift = clampToFloor(place, moves[toyId] ?: Offset.Zero, floor)
    return place.shiftedX(shift.x).shiftedY(shift.y)
}

/**
 * Одна игрушка на полу.
 *
 * @param place где она лежит, пока её не двигали.
 * @param moves куда её передвинули — общий на все игрушки, см. [rememberToyMoves].
 * @param onDrag игрушку тащат: где её середина сейчас (в координатах экрана) и на сколько сдвинули.
 * @param onDrop отпустили.
 * @param nudge сдвиг рисунка в пикселях, не трогая места: главный экран так показывает,
 *   что с игрушкой делать, и подбрасывает её, когда питомцу скучно. Читается при рисовании.
 * @param onPlaced где игрушка на экране — для руки-подсказки.
 * @param watching смотрят ли игрушки вслед питомцу — у кого есть морда, вырастают глаза.
 *   Читается на отрисовке.
 * @param mirrored комната отражена (игровая): рисунок и сдвиги на экране разворачиваются
 *   обратно, чтобы игрушка выглядела как в магазине, а рука несла её туда, куда надо.
 * @param onMoved игрушку несут, и она теперь здесь — доли холста. Для ящика (ToyBox.kt).
 * @param catch игрушку отпустили здесь, поднятой на столько-то: true — её забрал ящик,
 *   и на пол она не опускается.
 * @param onGone игрушка ушла со сцены: в ящик или из комнаты.
 */
@Composable
internal fun RoomToy(
    toyId: String,
    art: Int,
    place: RelRect,
    moves: SnapshotStateMap<String, Offset>,
    floor: ToyFloor,
    canvasW: Dp,
    canvasH: Dp,
    lighting: RoomLighting,
    enabled: Boolean,
    onDrag: (toyId: String, centre: Offset, delta: Offset) -> Unit,
    onDrop: (toyId: String) -> Unit,
    nudge: (toyId: String) -> Offset = { Offset.Zero },
    onPlaced: (toyId: String, bounds: Rect) -> Unit = { _, _ -> },
    mirrored: Boolean = false,
    watching: () -> Boolean = { false },
    onMoved: (toyId: String, here: RelRect) -> Unit = { _, _ -> },
    catch: (toyId: String, here: RelRect, lift: Float) -> Boolean = { _, _, _ -> false },
    onGone: (toyId: String) -> Unit = {},
    /** Где сейчас питомец — доли холста. Игрушку, брошенную за него, он не прячет. */
    petAt: (() -> RelRect)? = null,
) {
    val flip = if (mirrored) -1f else 1f
    val shift = clampToFloor(place, moves[toyId] ?: Offset.Zero, floor)
    val here = place.shiftedX(shift.x).shiftedY(shift.y)
    // Поднята ли игрушка: 0 — лежит, 1 — в руке.
    val lift = remember { Animatable(0f) }
    var held by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val scope = rememberCoroutineScope()
    val drop by rememberUpdatedState(onDrop)
    val move by rememberUpdatedState(onDrag)
    val placed by rememberUpdatedState(onPlaced)
    val canvasPx = with(LocalDensity.current) { Size(canvasW.toPx(), canvasH.toPx()) }
    val moved by rememberUpdatedState(onMoved)
    val caught by rememberUpdatedState(catch)
    val gone by rememberUpdatedState(onGone)
    val currentPlace by rememberUpdatedState(place)
    val currentFloor by rememberUpdatedState(floor)
    val currentPetAt by rememberUpdatedState(petAt)
    DisposableEffect(toyId) { onDispose { gone(toyId) } }

    // Где игрушка сейчас — по последнему сдвигу, а не по тому, что было при нажатии.
    fun hereNow(): RelRect {
        val now = clampToFloor(currentPlace, moves[toyId] ?: Offset.Zero, currentFloor)
        return currentPlace.shiftedX(now.x).shiftedY(now.y)
    }

    fun release() {
        if (!held) return
        held = false
        drop(toyId)
        if (caught(toyId, hereNow(), lift.value)) return
        scope.launch { lift.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
        // Брошенная за питомца игрушка пряталась за ним целиком: ни увидеть, ни достать,
        // пока он не отойдёт. Такая отъезжает вбок, к ближнему краю питомца.
        val pet = currentPetAt?.invoke() ?: return
        val dx = outFromBehind(hereNow(), pet, currentFloor)
        if (dx == 0f) return
        val start = moves[toyId] ?: Offset.Zero
        scope.launch {
            val slide = Animatable(0f)
            slide.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) {
                moves[toyId] = clampToFloor(currentPlace, start + Offset(dx * value, 0f), currentFloor)
            }
        }
    }

    // Рисунок лежит в своём слое, а ловит пальцы отдельная зона поверх всего:
    // у питомца квадрат с прозрачными полями, и стоя рядом, он забирал бы касания себе.
    val box = Modifier
        .offset(canvasW * here.left, canvasH * here.top)
        .requiredSize(canvasW * here.width, canvasH * here.height)
    val lifted = Modifier.graphicsLayer {
        val up = lift.value
        val n = nudge(toyId)
        translationX = n.x * flip
        translationY = -size.height * HELD_LIFT * up + n.y
        val s = 1f + (HELD_SCALE - 1f) * up
        scaleX = s
        scaleY = s
    }

    Box(
        modifier = box
            .zIndex(GRAB_Z)
            .onGloballyPositioned {
                bounds = it.boundsInRoot()
                placed(toyId, bounds)
            }
            .semantics { contentDescription = "Игрушка" }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        held = true
                        scope.launch { lift.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val now = moves[toyId] ?: Offset.Zero
                        val step = Offset(amount.x / canvasPx.width, amount.y / canvasPx.height)
                        moves[toyId] = clampToFloor(place, now + step, floor)
                        moved(toyId, hereNow())
                        val onScreen = Offset(amount.x * flip, amount.y)
                        move(toyId, bounds.center + onScreen, onScreen)
                    },
                    onDragEnd = ::release,
                    onDragCancel = ::release,
                )
            },
    )

    val depth = when {
        held -> HELD_Z
        here.bottom > PET_FEET -> FRONT_Z
        else -> 0f
    }
    LitBody(
        lighting = lighting,
        place = here,
        footing = ToyFooting,
        visibility = { 1f - HELD_SHADOW_FADE * lift.value },
        modifier = box.zIndex(depth).then(lifted),
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = flip }) {
            Image(
                painter = painterResource(art),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            // Глаза — в свете комнаты, как сама игрушка: ночью они тоже приглушены
            // и не бросаются в глаза. Чисто белые 👀 пробовали — слишком заметно.
            EyesByToy[toyId]?.let { eyes -> PeekingEyes(eyes, shown = { watching() && !held }) }
        }
    }
}

/**
 * Большие глаза 👀 поверх нарисованных: белые, вытянутые, зрачки у правого края —
 * туда, куда ускакал питомец. Видимость [shown] читается на отрисовке: вспышка
 * не пересобирает игрушку.
 */
@Composable
private fun PeekingEyes(eyes: ToyEyes, shown: () -> Boolean) {
    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = if (shown()) 1f else 0f }) {
        // Рисунок вписан в квадрат с полями (ContentScale.Fit): доли — от него, а не от квадрата.
        val k = minOf(size.width / eyes.artW, size.height / eyes.artH)
        val artW = eyes.artW * k
        val artH = eyes.artH * k
        val ox = (size.width - artW) / 2f
        val oy = (size.height - artH) / 2f
        val w = artW * eyes.size * 2.2f
        val h = w * 1.35f
        for (at in listOf(eyes.left, eyes.right)) {
            val c = Offset(ox + artW * at.x, oy + artH * at.y)
            val box = Size(w, h)
            val topLeft = c - Offset(w / 2f, h / 2f)
            drawOval(Color.White, topLeft, box)
            drawOval(FinneyInk, topLeft, box, style = Stroke(w * 0.12f))
            drawCircle(FinneyInk, radius = w * 0.24f, center = c + Offset(w * 0.2f, h * 0.06f))
        }
    }
}
