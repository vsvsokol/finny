package ru.finney.pet.ui.room

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.sound.Sounds
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.StrokeThin
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

// Ящик для игрушек в игровой — по концепту игровой (20.10): справа, под торшером.
// Игрушку с пола можно отнести в ящик и отпустить над ним — она нырнёт внутрь и
// выглянет из-за борта. Нажатие на ящик выбрасывает на пол одну случайную игрушку
// из тех, что внутри. Это уборка, а не покупка: деньги и шкалы не меняются,
// поэтому ящик живёт только в сцене и ядро о нём не знает.
//
// Рисунка ящика от дизайнеров нет — он нарисован кодом по правилам UI-кита:
// кремовая спинка с ручкой, полосатый перед, звезда в кружке, обводка ink.
//
// Полёты читаются только на отрисовке, а тень игрушки в полёте лежит на одном месте:
// в первой версии летящая игрушка пересобиралась и заново вставала в свет комнаты
// каждый кадр, и при нескольких игрушках разом анимация шла рывками.

/** Середина ящика — доля видимой ширины экрана слева. На концепте он у правого края. */
private const val BOX_SCREEN_X = 0.80f

/** Ширина ящика — доля видимой ширины экрана. На концепте около трети. */
private const val BOX_SCREEN_W = 0.34f

/** Высота ящика к ширине на экране — как на концепте (285 × 310 px). */
private const val BOX_ASPECT = 1.09f

/**
 * Низ ящика — доля высоты холста. Дальше ступней питомца ([Room.PLAY_PET_FEET]):
 * ящик стоит за ним, на ковре у стены, и перед торшером.
 */
private const val BOX_BOTTOM = 0.665f

/** Кромка переднего борта — доля высоты ящика. Ниже неё игрушки внутри не видно. */
private const val RIM = 0.36f

/**
 * Насколько ниже ящика может быть низ игрушки, чтобы она считалась над ним, — доля
 * высоты холста. Поднятая игрушка висит выше своего места, и над ящиком она уже тогда,
 * когда её место — чуть перед ним.
 */
private const val CATCH_BELOW = 0.04f

/** Куда падают выброшенные игрушки: низ — доли высоты холста, перед ящиком и не за краем пола. */
private const val LAND_BACK = 0.715f
private const val LAND_FRONT = 0.775f

/** Сколько мест падения перебирается: из них берётся самое свободное. */
private const val LAND_TRIES = 24

/** Сколько игрушка летит в ящик, мс. */
private const val CATCH_MS = 380

/** Сколько летит выброшенная игрушка, мс. */
private const val TOSS_MS = 620

/** Высота дуги, в высотах игрушки: в ящик — подкинуть через борт, из ящика — высоко, как из хлопушки. */
private const val ARC_IN = 0.7f
private const val ARC_OUT = 2.2f

/**
 * Переворот выброшенной игрушки: ровно один оборот, и закончен он к [SPIN_DONE] полёта —
 * на пол игрушка садится ровно. Поворот на случайный угол оставлял её косой,
 * и она рывком выпрямлялась уже на полу.
 */
private const val SPIN_DONE = 0.8f

/** Сколько игрушек выглядывает из ящика. Больше не помещается по ширине. */
private const val MAX_PEEKS = 3

/** Плавный разгон и торможение переворота. */
private val SpinEasing = CubicBezierEasing(0.3f, 0f, 0.4f, 1f)

/** Игрушка, которая летит в ящик или из него. */
@Stable
internal class ToyFlight(
    val from: RelRect,
    val to: RelRect,
    /** В ящик (true) или из него. */
    val into: Boolean,
    /** Насколько игрушка была поднята в руке в начале полёта: 0 — лежала. */
    val lift: Float,
    /** Поворот за полёт, градусы. */
    val spin: Float,
) {
    val progress = Animatable(0f)

    /** Приземление: 1 — сплющена об пол, пружиной к 0. */
    val land = Animatable(0f)
}

/** Куда упадёт выброшенная игрушка: [place] — её место на полу без сдвига, [target] — куда легла. */
internal class Landing(val id: String, val place: RelRect, val target: RelRect)

/**
 * Что в ящике и что летит. Содержимое переживает переход в другую комнату и поворот
 * экрана ([rememberToyBox]), полёты — нет: прерванный полёт просто доигрывается сразу.
 */
@Stable
internal class ToyBoxState(
    val inside: SnapshotStateList<String>,
    private val scope: CoroutineScope,
    private val sounds: Sounds,
) {
    val flights: SnapshotStateMap<String, ToyFlight> = mutableStateMapOf()

    /** Игрушку несут — ящик покачивается, зовёт. */
    var carrying by mutableStateOf(false)
        private set

    /** Несут прямо над ящиком — отпустишь, и она упадёт внутрь. */
    var hovered by mutableStateOf(false)
        private set

    /**
     * Сколько раз к ящику обращались: понесли игрушку или нажали. Питомец, стоящий
     * у ящика, на это отходит (RoomScene).
     */
    var pokes by mutableIntStateOf(0)
        private set

    /** Толчок: больше 0 — ящик сплющен (поймал), меньше 0 — вытянут (выбросил). */
    val bump = Animatable(0f)

    /** Покачивание пустого ящика, градусы. */
    val shake = Animatable(0f)

    /** Игрушки не на полу: внутри или в полёте. */
    fun away(id: String) = id in inside || id in flights

    /** Игрушку [toy] несут: над ящиком [box] ли она. */
    fun hover(box: RelRect, toy: RelRect) {
        if (!carrying) {
            carrying = true
            pokes++
        }
        hovered = over(box, toy)
    }

    /**
     * Игрушку [id] отпустили на месте [toy], поднятой на [lift]. Над ящиком — она летит
     * внутрь, и ответ true: на пол её опускать не надо.
     */
    fun catch(id: String, toy: RelRect, lift: Float, box: RelRect, animated: Boolean): Boolean {
        carrying = false
        val hit = hovered || over(box, toy)
        hovered = false
        if (!hit) return false
        if (!animated) {
            inside += id
            sounds.play(Sfx.ToyClick, 0.8f)
            return true
        }
        val flight = ToyFlight(
            from = toy,
            to = insideSpot(box, toy),
            into = true,
            lift = lift,
            spin = Random.nextFloat() * 40f - 20f,
        )
        flights[id] = flight
        scope.launch {
            try {
                flight.progress.animateTo(1f, tween(CATCH_MS, easing = LinearEasing))
            } finally {
                // Обе записи без приостановки между ними: в кадре игрушка не пропадает и не двоится.
                flights -= id
                if (id !in inside) inside += id
            }
            // Ящик деревянный: стук пониже, чем у кубиков.
            sounds.play(Sfx.ToyClick, 0.8f)
            bump.snapTo(0.8f)
            bump.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
        }
        return true
    }

    /**
     * Нажали на ящик. Пустой — качает головой. Иначе подпрыгивает и выбрасывает одну
     * игрушку — [landing]; [onToss] — в момент выброса.
     */
    fun tap(
        box: RelRect,
        landing: Landing?,
        moves: SnapshotStateMap<String, Offset>,
        animated: Boolean,
        onEmpty: () -> Unit,
        onToss: () -> Unit,
    ) {
        pokes++
        if (landing == null) {
            sounds.play(Sfx.Tap)
            onEmpty()
            if (animated) scope.launch {
                shake.snapTo(0f)
                shake.animateTo(0f, keyframes {
                    durationMillis = 520
                    9f at 70
                    -8f at 160
                    6f at 250
                    -4f at 340
                    2f at 430
                })
            }
            return
        }
        val id = landing.id
        // Место на полу — сразу: следующая выброшенная игрушка будет искать свободное уже без него.
        moves[id] = Offset(landing.target.left - landing.place.left, landing.target.bottom - landing.place.bottom)
        if (!animated) {
            inside -= id
            sounds.play(Sfx.ToyDrop)
            onToss()
            return
        }
        val flight = ToyFlight(
            from = insideSpot(box, landing.place),
            to = landing.target,
            into = false,
            lift = 0f,
            spin = if ((landing.target.left + landing.target.right) / 2f > (box.left + box.right) / 2f) 360f else -360f,
        )
        flights[id] = flight
        inside -= id
        sounds.play(Sfx.ToyBounce, 0.9f + Random.nextFloat() * 0.3f)
        onToss()
        scope.launch {
            // Выплюнул: ящик вытягивается и пружинит обратно, пока игрушка летит.
            bump.snapTo(-0.8f)
            bump.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
        }
        scope.launch {
            try {
                // Без смягчения: полёт по параболе, по-честному.
                flight.progress.animateTo(1f, tween(TOSS_MS, easing = LinearEasing))
                sounds.play(Sfx.ToyDrop, 1f + Random.nextFloat() * 0.2f)
                flight.land.snapTo(1f)
                flight.land.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
            } finally {
                flights -= id
            }
        }
    }

    private fun over(box: RelRect, toy: RelRect): Boolean {
        val x = (toy.left + toy.right) / 2f
        return x in box.left..box.right && toy.bottom < box.bottom + CATCH_BELOW
    }
}

/** Ящик на сцене: содержимое помнится через переходы между комнатами и поворот экрана. */
@Composable
internal fun rememberToyBox(): ToyBoxState {
    val inside = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(
            save = { it.toList() },
            restore = { mutableStateListOf<String>().apply { addAll(it) } },
        ),
    ) { mutableStateListOf() }
    val scope = rememberCoroutineScope()
    val sounds = LocalSounds.current
    return remember(inside, scope, sounds) { ToyBoxState(inside, scope, sounds) }
}

/**
 * Где стоит ящик на полу [floor]. Игровая отражена: правый край экрана — левый край
 * видимой части холста, поэтому место считается от [ToyFloor.right].
 */
internal fun toyBoxRect(floor: ToyFloor): RelRect {
    val span = floor.right - floor.left
    val centre = floor.right - span * BOX_SCREEN_X
    val width = span * BOX_SCREEN_W
    val height = width * BOX_ASPECT * Room.CANVAS_RATIO
    return RelRect(centre - width / 2f, BOX_BOTTOM - height, centre + width / 2f, BOX_BOTTOM)
}

/** Кромка ящика [box] — доля высоты холста: ниже неё игрушка в ящике не видна. */
internal fun toyBoxRim(box: RelRect): Float = box.top + box.height * RIM

/** Игрушка размера [toy] внутри ящика, посередине: почти целиком за бортом. */
private fun insideSpot(box: RelRect, toy: RelRect): RelRect {
    val centre = (box.left + box.right) / 2f
    val bottom = toyBoxRim(box) + toy.height * 0.85f
    return RelRect(centre - toy.width / 2f, bottom - toy.height, centre + toy.width / 2f, bottom)
}

/**
 * Куда упадёт следующая игрушка из ящика: случайная из тех, что внутри, на самое
 * свободное место пола — подальше от [taken] (низ-середина игрушек на полу и питомца,
 * доли холста). null — в ящике пусто. [places] — места игрушек на полу без сдвига, по id.
 */
internal fun toyLanding(
    inside: List<String>,
    places: Map<String, RelRect>,
    floor: ToyFloor,
    taken: List<Offset>,
): Landing? {
    val id = inside.filter { it in places }.randomOrNull() ?: return null
    val place = places.getValue(id)
    val left = floor.left + place.width / 2f
    val right = floor.right - place.width / 2f
    // Расстояние — в долях ширины холста по обеим осям: холст выше, чем шире.
    fun room(x: Float, y: Float) = taken.minOfOrNull { hypot(it.x - x, (it.y - y) / Room.CANVAS_RATIO) } ?: 1f
    var best = Offset.Zero
    var bestRoom = -1f
    repeat(LAND_TRIES) {
        val x = left + (right - left) * Random.nextFloat()
        val y = LAND_BACK + (LAND_FRONT - LAND_BACK) * Random.nextFloat()
        val r = room(x, y)
        if (r > bestRoom) {
            bestRoom = r
            best = Offset(x, y)
        }
    }
    return Landing(id, place, RelRect(best.x - place.width / 2f, best.y - place.height, best.x + place.width / 2f, best.y))
}

/**
 * Сам ящик: спинка, темнота внутри, игрушки, которые выглядывают из-за борта, и передний
 * борт. Несут игрушку — покачивается, над ним — подрастает и игрушки внутри привстают:
 * видно, что сюда можно положить. Нажатие ловит отдельная зона поверх питомца — как
 * у игрушек: квадрат питомца с прозрачными полями закрывал бы ящик.
 *
 * Всё движение ящика — в слое поверх освещённого рисунка: качание и толчки не
 * перерисовывают свет. Выглядывающие игрушки с ящиком не качаются по той же причине.
 *
 * @param arts рисунки игрушек по id.
 * @param mirrored комната отражена: рисунок разворачивается обратно.
 */
@Composable
internal fun ToyBox(
    state: ToyBoxState,
    rect: RelRect,
    arts: Map<String, Int>,
    canvasW: Dp,
    canvasH: Dp,
    lighting: RoomLighting,
    enabled: Boolean,
    animated: Boolean,
    mirrored: Boolean,
    onTap: () -> Unit,
) {
    val flip = if (mirrored) -1f else 1f
    val boxW = canvasW * rect.width
    val boxH = canvasH * rect.height
    val place = Modifier
        .offset(canvasW * rect.left, canvasH * rect.top)
        .requiredSize(boxW, boxH)

    val hover = animateFloatAsState(
        if (state.hovered) 1f else 0f,
        if (animated) spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "boxHover",
    )
    // Зовёт, пока несут игрушку: покачивается из стороны в сторону.
    val sway = remember { Animatable(0f) }
    LaunchedEffect(state.carrying, animated) {
        if (!state.carrying || !animated) {
            sway.animateTo(0f, spring())
            return@LaunchedEffect
        }
        while (true) {
            sway.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            sway.animateTo(-1f, tween(420, easing = FastOutSlowInEasing))
        }
    }

    val peeks = state.inside.filter { it in arts }.takeLast(MAX_PEEKS)
    val count = state.inside.count { it in arts }

    LitBody(
        lighting = lighting,
        place = rect,
        footing = BoxFooting,
        modifier = place.graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 1f)
            val b = state.bump.value
            val grow = 1f + 0.07f * hover.value
            scaleX = grow * (1f + 0.08f * b)
            scaleY = grow * (1f - 0.12f * b)
            rotationZ = (sway.value * 2.5f + state.shake.value) * flip
        },
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = flip }) {
            Canvas(Modifier.fillMaxSize()) { drawBoxBack() }
            val peekSize = boxW * 0.40f
            peeks.forEachIndexed { i, id ->
                key(id) {
                    val at = PeekPlaces[peeks.size - 1][i]
                    // Новая игрушка в ящике выныривает из-за борта.
                    val pop = remember { Animatable(if (animated) 0f else 1f) }
                    LaunchedEffect(Unit) {
                        pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
                    }
                    Image(
                        painter = painterResource(arts.getValue(id)),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .offset(boxW * at - peekSize / 2f, boxH * RIM - peekSize * 0.45f)
                            .size(peekSize)
                            .graphicsLayer {
                                translationY = size.height * (0.6f * (1f - pop.value) - 0.18f * hover.value)
                                rotationZ = PeekTilts[i]
                            },
                    )
                }
            }
            Canvas(Modifier.fillMaxSize()) { drawBoxFront() }
        }
    }

    Box(
        modifier = place
            .zIndex(BOX_TAP_Z)
            .semantics {
                contentDescription = "Ящик с игрушками"
                stateDescription = if (count == 0) "пустой" else "внутри игрушек: $count"
                role = Role.Button
            }
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = null,
                onClickLabel = "Достать игрушку",
                onClick = onTap,
            ),
    )
}

/**
 * Игрушка в полёте — в ящик или из него. Ниже кромки ящика её срезает: так видно,
 * что она ныряет за борт и выпрыгивает из-за него.
 *
 * Игрушка стоит там, где полёт кончается (из ящика) или начинается (в ящик), а летит
 * сдвигом слоя: ни пересборки, ни перерисовки света по кадрам. Тень — тоже там: у
 * вылетающей она растёт на месте падения, и на полу игрушка встаёт точно в свою тень.
 */
@Composable
internal fun FlyingToy(
    art: Int,
    flight: ToyFlight,
    rim: Float,
    canvasW: Dp,
    canvasH: Dp,
    lighting: RoomLighting,
    mirrored: Boolean,
) {
    val flip = if (mirrored) -1f else 1f
    val anchor = if (flight.into) flight.from else flight.to

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(FLIGHT_Z)
            .drawWithContent {
                val t = flight.progress.value
                // Над бортом срезать нельзя: в начале полёта в ящик игрушка ещё перед ним.
                val clipped = if (flight.into) t > 0.5f else t < 0.3f
                if (clipped) clipRect(bottom = size.height * rim) { this@drawWithContent.drawContent() } else drawContent()
            },
    ) {
        LitBody(
            lighting = lighting,
            place = anchor,
            footing = FlightFooting,
            visibility = {
                val t = flight.progress.value
                if (flight.into) (1f - 2f * t).coerceIn(0f, 1f) * (1f - HELD_SHADOW_SHARE * flight.lift)
                else ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
            },
            modifier = Modifier
                .offset(canvasW * anchor.left, canvasH * anchor.top)
                .requiredSize(canvasW * anchor.width, canvasH * anchor.height)
                .graphicsLayer {
                    val t = flight.progress.value
                    // Холст в пикселях — из размера самой игрушки: она его доля.
                    val cw = size.width / anchor.width
                    val ch = size.height / anchor.height
                    val other = if (flight.into) flight.to else flight.from
                    // Насколько игрушка ушла от якоря к другому концу полёта.
                    val away = if (flight.into) FastOutSlowInEasing.transform(t) else 1f - t
                    val height = if (flight.into) {
                        (1f - t) * HELD_LIFT * flight.lift + ARC_IN * 4f * t * (1f - t)
                    } else {
                        ARC_OUT * 4f * t * (1f - t)
                    }
                    translationX = (other.left - anchor.left) * cw * away
                    translationY = (other.bottom - anchor.bottom) * ch * away - size.height * height

                    val squash = flight.land.value
                    val grow = if (flight.into) (1f + 0.2f * flight.lift) + (0.85f - 1f - 0.2f * flight.lift) * t else 0.85f + 0.15f * t
                    // В ящик — от середины, как игрушка в руке (RoomToys.kt): полёт начинается без скачка.
                    // Из ящика — от низа: об пол сплющивается низ.
                    transformOrigin = if (flight.into) TransformOrigin.Center else TransformOrigin(0.5f, 0.92f)
                    scaleX = grow * (1f + 0.16f * squash)
                    scaleY = grow * (1f - 0.22f * squash)
                    rotationZ = if (flight.into) {
                        flight.spin * sin(PI.toFloat() * t)
                    } else {
                        flight.spin * SpinEasing.transform((t / SPIN_DONE).coerceAtMost(1f))
                    }
                },
        ) {
            Image(
                painter = painterResource(art),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = flip },
            )
        }
    }
}

/** Где выглядывают игрушки — доли ширины ящика, по числу игрушек. */
private val PeekPlaces = listOf(
    listOf(0.5f),
    listOf(0.34f, 0.66f),
    listOf(0.27f, 0.5f, 0.73f),
)

/** Наклон выглядывающих игрушек, градусы: вразнобой, как набросали. */
private val PeekTilts = listOf(-14f, 5f, 15f)

/** Тень ящика — под его низом. */
private val BoxFooting = Footing(feetX = 0.5f, feetY = 0.97f)

/** Тень летящей игрушки — как у лежащей (RoomToys.kt). */
private val FlightFooting = Footing(feetX = 0.5f, feetY = 0.92f)

/** Насколько бледнее тень поднятой игрушки — как в RoomToys.kt. */
private const val HELD_SHADOW_SHARE = 0.45f

/** Летящая игрушка — над питомцем, как поднятая. */
private const val FLIGHT_Z = 2f

/** Зона нажатия на ящик — над питомцем, но под зонами игрушек (их добавляют позже). */
private const val BOX_TAP_Z = 3f

/** Спинка с ручкой и темнота внутри. */
private fun DrawScope.drawBoxBack() {
    val w = size.width
    val h = size.height
    val stroke = Stroke(StrokeThin.toPx(), join = StrokeJoin.Round)
    val corner = CornerRadius(w * 0.12f)
    val back = Path().apply {
        addRoundRect(
            RoundRect(
                Rect(w * 0.07f, h * 0.02f, w * 0.93f, h * 0.62f),
                topLeft = corner,
                topRight = corner,
            ),
        )
    }
    val handle = Path().apply {
        addRoundRect(RoundRect(Rect(w * 0.33f, h * 0.08f, w * 0.67f, h * 0.15f), CornerRadius(h * 0.035f)))
    }
    val panel = Path().apply { op(back, handle, PathOperation.Difference) }
    drawPath(panel, FinneyCream)
    drawPath(panel, FinneyInk, style = stroke)
    // Внутри темно — так ящик читается глубоким, а игрушки в нём видны.
    drawRoundRect(
        color = FinneyInk,
        topLeft = Offset(w * 0.09f, h * 0.24f),
        size = Size(w * 0.82f, h * 0.18f),
        cornerRadius = CornerRadius(w * 0.05f),
    )
}

/** Передний борт: полоски, кромка и звезда в кружке. */
private fun DrawScope.drawBoxFront() {
    val w = size.width
    val h = size.height
    val line = StrokeThin.toPx()
    val stroke = Stroke(line, join = StrokeJoin.Round)
    val top = h * RIM
    val bottomCorner = CornerRadius(w * 0.08f)
    val body = Path().apply {
        addRoundRect(
            RoundRect(
                Rect(w * 0.03f, top, w * 0.97f, h - line / 2f),
                bottomLeft = bottomCorner,
                bottomRight = bottomCorner,
            ),
        )
    }
    clipPath(body) {
        drawRect(FinneyCream)
        val stripes = 7
        val stripe = w / stripes
        for (i in 0 until stripes step 2) {
            drawRect(FinneyPeach, topLeft = Offset(stripe * i, top), size = Size(stripe, h - top))
        }
        // Снизу чуть темнее — ящик стоит, а не висит.
        drawRect(FinneyInk.copy(alpha = 0.12f), topLeft = Offset(0f, h * 0.88f), size = Size(w, h * 0.12f))
    }
    drawPath(body, FinneyInk, style = stroke)

    // Кромка — чуть шире борта.
    val lipTop = Offset(0f + line / 2f, top - h * 0.035f)
    val lipSize = Size(w - line, h * 0.08f)
    drawRoundRect(FinneyCream, lipTop, lipSize, CornerRadius(h * 0.03f))
    drawRoundRect(FinneyInk, lipTop, lipSize, CornerRadius(h * 0.03f), style = stroke)

    val centre = Offset(w * 0.5f, (top + h) / 2f + h * 0.03f)
    val badge = w * 0.2f
    drawCircle(FinneyCream, badge, centre)
    drawCircle(FinneyInk, badge, centre, style = stroke)
    val star = starPath(centre, outer = badge * 0.78f, inner = badge * 0.36f)
    drawPath(star, FinneyYellow)
    drawPath(star, FinneyInk, style = stroke)
}

/** Пятиконечная звезда остриём вверх. */
private fun starPath(centre: Offset, outer: Float, inner: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = -PI / 2 + i * PI / 5
        val p = Offset(centre.x + (r * cos(a)).toFloat(), centre.y + (r * sin(a)).toFloat())
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}
