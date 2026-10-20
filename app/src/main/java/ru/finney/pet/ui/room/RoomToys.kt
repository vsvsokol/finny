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
import kotlinx.coroutines.launch

// Игрушки в игровой. Купленная игрушка лежит на полу; её можно взять пальцем и
// положить в любое место пола — там она и останется. Рядом с питомцем он тянется
// к ней и радуется. Кто кого заслоняет, решает глубина: игрушка ближе к зрителю,
// чем ступни питомца, лежит перед ним, дальше — за ним. Тень у игрушки всегда
// своя: лежит — у её низа, в руке — на полу под ней. Что это даёт в игре, решает
// главный экран: сцена только сообщает, где игрушка и как её двигают.

/** Сторона игрушки — доля видимой ширины экрана. На телефоне около 60 dp. */
private const val TOY_SCREEN_SIZE = 0.155f

/**
 * Низ игрушки на своём месте — доля высоты холста. Ступни питомца в игровой — 0.70:
 * ряд чуть ближе к зрителю, чем он.
 */
private const val TOY_BOTTOM = 0.72f

/**
 * Куда по глубине можно положить игрушку — её низ, доли высоты холста. Дальше —
 * плинтус (шов пола у задников от 0.44 до 0.51) с запасом на высоту игрушки, ближе —
 * ряд кнопок комнат. Капсулы в игровой нет, и пол свободен почти до стены.
 */
private const val FLOOR_BACK = 0.56f
private const val FLOOR_FRONT = 0.76f

/** Ступни питомца в игровой: игрушка ниже этой линии — перед ним, выше — за ним. */
private const val PET_FEET = 0.70f

/**
 * Места игрушек — середины, доли видимой ширины экрана. Сначала — справа: питомец
 * стоит левее. Потом — слева, у края.
 */
private val ToyPlaces = listOf(0.64f, 0.88f, 0.76f, 0.09f, 0.21f, 0.52f)

/** Сколько игрушек помещается в игровой. Магазин не продаёт больше (ContentTest). */
const val MAX_ROOM_TOYS = 6

/** Как игрушка лежит на полу: тень под нижним краем рисунка. */
private val ToyFooting = Footing(feetX = 0.5f, feetY = 0.92f)

/** Насколько игрушка крупнее в руке: её «подняли» ближе к экрану. */
private const val HELD_SCALE = 1.2f

/** На сколько поднята игрушка в руке — доля её высоты. Тень остаётся на полу. */
private const val HELD_LIFT = 0.45f

/** Поднятая игрушка дальше от пола — тень под ней бледнее на эту долю. */
private const val HELD_SHADOW_FADE = 0.45f

/** Игрушка перед питомцем — над ним, но под поднятой. */
private const val FRONT_Z = 1f

/** Поднятая игрушка — над питомцем. */
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
 * Одна игрушка на полу.
 *
 * @param place где она лежит, пока её не двигали.
 * @param moves куда её передвинули — общий на все игрушки, см. [rememberToyMoves].
 * @param onDrag игрушку тащат: где её середина сейчас (в координатах экрана) и на сколько сдвинули.
 * @param onDrop отпустили.
 * @param nudge сдвиг рисунка в пикселях, не трогая места: главный экран так показывает,
 *   что с игрушкой делать, и подбрасывает её, когда питомцу скучно. Читается при рисовании.
 * @param onPlaced где игрушка на экране — для руки-подсказки.
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
) {
    val shift = clampToFloor(place, moves[toyId] ?: Offset.Zero, floor)
    val here = place.shiftedX(shift.x).shiftedY(shift.y).let { at ->
        // Чем ближе к стене, тем мельче: пол уходит вглубь до самого плинтуса.
        val k = RoomSpace.scaleAt(at.bottom) / RoomSpace.scaleAt(place.bottom)
        val centre = (at.left + at.right) / 2f
        RelRect(centre - at.width * k / 2f, at.bottom - at.height * k, centre + at.width * k / 2f, at.bottom)
    }
    // Поднята ли игрушка: 0 — лежит, 1 — в руке.
    val lift = remember { Animatable(0f) }
    var held by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val scope = rememberCoroutineScope()
    val drop by rememberUpdatedState(onDrop)
    val move by rememberUpdatedState(onDrag)
    val placed by rememberUpdatedState(onPlaced)
    val canvasPx = with(LocalDensity.current) { Size(canvasW.toPx(), canvasH.toPx()) }

    fun release() {
        if (!held) return
        held = false
        drop(toyId)
        scope.launch { lift.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
    }

    // Рисунок лежит в своём слое, а ловит пальцы отдельная зона поверх всего:
    // у питомца квадрат с прозрачными полями, и стоя рядом, он забирал бы касания себе.
    val box = Modifier
        .offset(canvasW * here.left, canvasH * here.top)
        .requiredSize(canvasW * here.width, canvasH * here.height)
    val lifted = Modifier.graphicsLayer {
        val up = lift.value
        val n = nudge(toyId)
        translationX = n.x
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
                        move(toyId, bounds.center + amount, amount)
                    },
                    onDragEnd = ::release,
                    onDragCancel = ::release,
                )
            },
    )

    LitBody(
        lighting = lighting,
        place = here,
        footing = ToyFooting,
        visibility = { 1f - HELD_SHADOW_FADE * lift.value },
        modifier = box
            .zIndex(
                when {
                    held -> HELD_Z
                    here.bottom > PET_FEET -> FRONT_Z
                    else -> 0f
                },
            )
            .then(lifted),
    ) {
        Image(
            painter = painterResource(art),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
