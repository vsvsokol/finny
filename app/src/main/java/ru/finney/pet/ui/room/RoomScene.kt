package ru.finney.pet.ui.room

import ru.finney.pet.ui.motion.motionEnabled
import ru.finney.pet.ui.motion.LocalAnimations
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.snapshotFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Комната собирается из слоёв одного холста 1440×2400, и каждый предмет уже стоит
// на своём месте в кадре — как у питомца, координаты подбирать не нужно. Числа
// лежат в Room, их печатает tools/pack_room.py при сборке ресурсов.
//
// Обводка у комнаты чёрная, а не FinneyInk: это отдельный пласт от интерфейса.
// Панели и кнопки плавают поверх и намеренно не сливаются с обстановкой.

/** Как питомец стоит на полу: см. [Footing]. Ступни на 0.87 квадрата. Мебель — в [Solids]. */
private val PetFooting = Footing(feetX = 0.5f, feetY = 0.87f)

/**
 * Переход между комнатами: питомец сам уходит в соседнюю. Комнаты стоят в ряд
 * в порядке [RoomSpot] — как кнопки внизу, — и он прыжком уходит за край экрана
 * в сторону нажатой кнопки, а из-за другого края впрыгивает уже в новую комнату.
 * Сама комната не едет и не проступает: пробовали наплыв (все комнаты читались
 * как одна, где переставили мебель) и съезд кадра вбок (питомец скользил отдельно
 * от комнат, а две комнаты разом тормозили). Здесь движется только питомец,
 * и понятно, почему комната сменилась, — он туда пошёл.
 *
 * Длина прыжка за край, мс: быстрее прогулки — это не прогулка, а переход.
 */
private const val LEAP_MS = 300

/**
 * С какой доли прыжка за край игрушки смотрят вслед питомцу (RoomToys.kt) — до смены
 * комнаты остаётся около 0.13 с. Раньше глаза появлялись с толчка, и их видели сразу.
 */
private const val TOYS_WATCH_FROM = 0.55f

/** Насколько за край экрана уходит питомец — доля ширины холста: квадрат с полями не торчит. */
private const val LEAP_OVERSHOOT = 0.02f

/**
 * Насколько торшер в игровой уходит за правый край экрана — доля ширины холста.
 * Так на концепте игровой (20.10): шар виден целиком, край стойки чуть срезан.
 */
private const val PLAY_LAMP_PEEK = 0.04f

/** За сколько камера выбора интерьера возвращается: столько же уезжает панель выбора (HomeScreen). */
private const val FOCUS_RETURN_MS = 250

/**
 * Прогулка по игровой: раз в несколько секунд питомец сам скачет в случайное место.
 * Пауза случайная — по часам его прыжков не ждут, и он выглядит живым, а не заведённым.
 */
private val StrollPauseMs = 6_000L..14_000L

/** Сколько питомец стоит у ящика, прежде чем отойти: подошёл посмотреть — и в сторону. */
private val NearBoxPauseMs = 1_200L..2_500L

/** Насколько питомцу можно зайти на ящик — доля ширины ящика. Краешек можно, закрыть нельзя. */
private const val BOX_OVERLAP = 0.2f

/** Длина одного скачка, доля ширины холста: дальние перебежки — несколько скачков подряд. */
private const val HOP_LENGTH = 0.09f

/** Скачок по времени совпадает с прыжком [ru.finney.pet.ui.pet.PetAnimation.playJoy]: присел, взлетел, приземлился. */
private const val HOP_CROUCH_MS = 140L
private const val HOP_FLIGHT_MS = 530

/**
 * Прозрачные поля вокруг питомца в его квадрате, доля стороны: край квадрата
 * может уйти за край экрана, а сам питомец — нет.
 */
private const val PET_MARGIN = 0.12f

/**
 * Во сколько раз окно на кухне больше, чем в зале. Подоконник стоит, где был, а карниз
 * (0.031 холста) поднимается к потолку: выше ×1.15 он уходит под строку состояния.
 */
private const val KITCHEN_WINDOW_SCALE = 1.15f

/** Сколько комната дышит: пена колышется в этих пределах от своего размера. */
private const val FOAM_SWELL = 0.02f
private const val FOAM_PERIOD_MS = 3400

/**
 * За сколько небо за окном сдвигается на ширину своей полосы (около 215 dp
 * на телефоне), то есть примерно 2 dp в секунду. Быстрее — и небо уже
 * «едет» и отвлекает, медленнее — движения не заметно вовсе.
 */
private const val SKY_LOOP_MS = 120_000

/**
 * За сколько колесо обозрения за окном цирка делает оборот. Медленно, как
 * настоящее: быстрое колесо за окном отвлекает от питомца.
 */
private const val WHEEL_TURN_MS = 40_000

/** Светлячки за окном леса разгораются и гаснут — не до темноты, чтобы не мигали. */
private const val TWINKLE_MS = 1_800
private const val TWINKLE_LOW = 0.35f

/** Фон неба в экспорте, им же залиты края полосы. */
internal val SkyColor = Color(0xFF261F46)

/**
 * Паузы между пролётами НЛО, мс. Первая короткая — чтобы ребёнок успел его
 * увидеть, пока смотрит на комнату, дальше реже: постоянно мелькающее НЛО
 * перестаёт быть событием. Точный срок каждый раз случайный — по часам
 * его ждать неинтересно.
 */
private val UfoFirstPauseMs = 3_000L..8_000L

/** Сколько закрывается и открывается дверь капсулы. */
internal const val DOOR_MS = 600

/**
 * Сколько питомец прыгает в капсулу и обратно: приседание [HOP_CROUCH_MS]
 * и полёт по дуге [HOP_FLIGHT_MS] — столько же, сколько его прыжок
 * [ru.finney.pet.ui.pet.PetAnimation.playJoy]. При 900 мс он приземлялся
 * раньше, чем долетал, и последнюю четверть пути скользил по полу.
 */
internal const val WALK_MS = HOP_CROUCH_MS.toInt() + HOP_FLIGHT_MS

/** Высота дуги прыжка в капсулу, доля высоты холста: около 50 dp на телефоне. */
private const val CAPSULE_JUMP_ARC = 0.06f

/**
 * Во сколько раз камера приближает капсулу. При 1.3 спящий питомец крупный,
 * а капсула почти целиком между плашками и панелью сна; при 1.4 её верх
 * уходил под плашки.
 */
private const val SLEEP_ZOOM = 1.3f

/** Где по высоте экрана встаёт спящий питомец: чуть ниже середины, над панелью сна. */
private const val SLEEP_FOCUS_Y = 0.52f

/** Сколько камера наезжает и отъезжает: чуть дольше прыжка, чтобы не дёргать. */
private const val CAMERA_MS = 1200

/**
 * Насколько темнеет комната, когда гаснет свет: поверх того, что без торшера
 * и так темнее. Цвет — небо за окном, ночь остаётся синей, а не серой.
 */
private const val NIGHT_SHADE = 0.3f
private val UfoPauseMs = 20_000L..45_000L

/**
 * Комната питомца.
 *
 * Сцена всегда рисуется в пропорции холста дизайнера и **заполняет экран
 * целиком**: комната вытянута (0.6), телефон уже (около 0.46), поэтому холст
 * масштабируется по высоте, а лишнее срезается по бокам. Растягивать нельзя —
 * круглые формы поплыли бы и обводка порвалась. Раньше холст вписывался по
 * ширине, и сверху оставалась кремовая полоса почти в четверть экрана.
 *
 * По горизонтали кадр стоит на оси комнаты [Room.ROOM_AXIS_X], но не дальше
 * [Room.FRAME_MIN_LEFT]: слева торшер, который понадобится для света, и ванна,
 * а срезается край занавески.
 * Если экран шире холста (планшет), холст подгоняется по ширине и срезается
 * сверху — пол, стол и ванна нужны целиком, а верх стены пустой.
 *
 * Комнат четыре — спальня, игровая, кухня, ванная, — и при переключении [spot] новая
 * проступает поверх старой. В каждой свой набор: в спальне торшер, окно и капсула, в игровой
 * окно и игрушки на полу, на кухне стол у окна без торшера, в ванной ванна под торшером без окна. Свет у каждой
 * комнаты свой, потому что источники в них разные.
 *
 * В комнате ночь (см. RoomLight.kt): светят торшер, окно и пролетающее НЛО.
 * Стены и пол освещаются одним слоем, питомец и мебель — каждый своим,
 * и у каждого своя тень.
 *
 * @param lampOn горит ли торшер, пока питомец не спит. Во сне свет гаснет сам.
 * @param capsule стоит ли в зале капсула для сна. Мини-играм комната нужна
 *   фоном, и капсула во весь экран там только мешает.
 * @param asleep питомец спит: запрыгивает в капсулу, камера наезжает на неё,
 *   дверь закрывается и гаснет свет. Проснулся — всё в обратном порядке,
 *   и выпрыгивает он на своё место, а не туда, где гулял.
 * @param pet встаёт туда, где ему положено быть в текущей комнате.
 * @param onTapItem нажатие по самому предмету: тому же, что делает нижняя кнопка.
 * @param wander гулять ли питомцу по игровой самому. Выключается, пока с ним что-то
 *   делают: во сне, в играх ухода, в других комнатах.
 * @param onHop питомец скакнул — запустить прыжок в его анимации: поза живёт снаружи.
 * @param toys купленные игрушки — лежат на полу игровой, см. RoomToys.kt.
 * @param toysEnabled можно ли их брать: во сне и в играх ухода нельзя.
 * @param onToyDrag игрушку тащат: где её середина на экране и на сколько сдвинули.
 * @param onToyDrop игрушку отпустили.
 * @param toyNudge сдвиг рисунка игрушки в пикселях — показ и подпрыгивание, см. [RoomToy].
 * @param onToyPlaced где игрушка на экране.
 * @param onToyGone игрушка ушла с пола: в ящик (ToyBox.kt) или вместе с комнатой.
 * @param onToyBoxEmpty нажали на пустой ящик для игрушек.
 * @param interior задник, окно и торшер. Сменённая часть не подменяется, а меняется
 *   с анимацией: стену прокатывает валик, окно схлопывается и раскрывается, торшер
 *   падает сверху и мигает, включаясь.
 * @param focus что показать крупно, пока выбирают интерьер ([focusOf]); null — обычный вид.
 * @param focusPet показать крупно питомца там, где он стоит. Важнее [focus].
 * @param focusRug показать крупно ковёр игровой. Его место знает только сцена: оно
 *   зависит от ширины экрана (RoomRugs.kt). Важнее [focus].
 * @param focusBottom сколько снизу закрыто панелью выбора: часть [focus] встаёт
 *   посередине того, что над ней осталось.
 * @param focusSolid сколько снизу панель закрывает сплошь. Над этим краем низ холста
 *   подниматься не может: между кнопками над рамкой и в её углах видно, что за панелью.
 * @param trip дошёл ли питомец до [spot], и «поторопить» его, см. [RoomTrip].
 */
@Composable
fun RoomScene(
    spot: RoomSpot,
    modifier: Modifier = Modifier,
    lampOn: Boolean = true,
    capsule: Boolean = true,
    asleep: Boolean = false,
    onTapItem: (() -> Unit)? = null,
    wander: Boolean = false,
    onHop: () -> Unit = {},
    toys: List<String> = emptyList(),
    toysEnabled: Boolean = false,
    onToyDrag: (toyId: String, centre: Offset, delta: Offset) -> Unit = { _, _, _ -> },
    onToyDrop: (toyId: String) -> Unit = {},
    toyNudge: (toyId: String) -> Offset = { Offset.Zero },
    onToyPlaced: (toyId: String, bounds: Rect) -> Unit = { _, _ -> },
    onToyGone: (toyId: String) -> Unit = {},
    onToyBoxEmpty: () -> Unit = {},
    interior: RoomInterior = Interiors.Default,
    focus: RelRect? = null,
    focusPet: Boolean = false,
    focusRug: Boolean = false,
    focusBottom: Dp = 0.dp,
    focusSolid: Dp = focusBottom,
    trip: RoomTrip = remember { RoomTrip(spot) },
    pet: @Composable BoxScope.() -> Unit = {},
) {
    // Где питомец в игровой сейчас: сдвиг от его обычного места, доля ширины холста.
    val stroll = remember { Animatable(0f) }
    // Летун у окна свой у каждого интерьера: сменили окно — пролёт начинается заново.
    val flyer = remember(interior.window) { WindowFlyer(interior.window) }

    // Ночь: свет в комнате выключен. Гаснет, когда за питомцем закрылась дверь,
    // загорается, как только его будят, — до того, как дверь открылась.
    var dark by remember { mutableStateOf(asleep) }
    // Летун один на все комнаты: окна во время перехода два, а пролёт один.
    // Звучит, только если окно сейчас видно.
    val sounds = LocalSounds.current
    val currentSpot by rememberUpdatedState(spot)
    LaunchedEffect(flyer) { flyer.fly(onStart = { life -> if (currentSpot.hasWindow) life.sound?.let(sounds::play) }) }
    val animations = LocalAnimations.current

    // Смена частей интерьера: у каждой своя анимация и свой звук. Звуки игрушек
    // сюда не берём — их узнают сразу («кубик Рубика»), а это не игрушка.
    val walls = rememberPartSwap(interior.wall, WALL_SWAP_MS) { sounds.play(Sfx.Pour) }
    val windows = rememberPartSwap(interior.window, WINDOW_SWAP_MS) { sounds.play(Sfx.WashClean) }
    val lamps = rememberPartSwap(interior.lamp, LAMP_SWAP_MS) {
        delay((LAMP_SWAP_MS * LAMP_LANDS).toLong())
        sounds.play(Sfx.CapsuleDoor)
        delay((LAMP_SWAP_MS * (LAMP_FLICKERS - LAMP_LANDS)).toLong())
        sounds.play(Sfx.Tap)
    }
    // Ковёр шуршит, как ткань.
    val rugs = rememberPartSwap(interior.rug, RUG_SWAP_MS) { sounds.play(Sfx.ToyRustle) }
    // Свет у каждой комнаты свой: источники в них разные. Части интерьера общие,
    // и свет от окна и торшера меняется вместе с их сменой, а не раньше.
    val lights = RoomSpot.entries.associateWith { room ->
        rememberRoomLighting(
            lampOn = lampOn && !dark && (room.hasLamp || room.ceilingLight),
            ufo = flyer::light,
            walls = walls,
            windows = windows,
            lamps = lamps,
            window = room.hasWindow,
            ceiling = room.ceilingLight,
        )
    }

    // Отход ко сну: питомец прыгает в капсулу (walk 0 → 1), камера наезжает,
    // закрывается дверь, гаснет свет. Живёт на всю сцену, а не на зал:
    // переход между комнатами его не сбрасывает.
    val walk = remember { Animatable(if (asleep) 1f else 0f) }
    val door = remember { Animatable(if (asleep) 1f else 0f) }
    // Камера: 0 — обычный вид, 1 — капсула крупно.
    val zoom = remember { Animatable(if (asleep) 1f else 0f) }
    LaunchedEffect(asleep) {
        if (asleep) {
            launch { zoom.animateTo(1f, tween(CAMERA_MS, easing = FastOutSlowInEasing)) }
            jump(walk, to = 1f, onHop)
            // В капсуле он уже не там, где гулял: проснувшись, выпрыгнет на своё место.
            stroll.snapTo(0f)
            door.animateTo(1f, tween(DOOR_MS, easing = FastOutSlowInEasing))
            dark = true
        } else {
            dark = false
            door.animateTo(0f, tween(DOOR_MS, easing = FastOutSlowInEasing))
            launch { zoom.animateTo(0f, tween(CAMERA_MS, easing = FastOutSlowInEasing)) }
            jump(walk, to = 0f, onHop)
        }
    }
    val shade = animateFloatAsState(if (dark) NIGHT_SHADE else 0f, tween(DOOR_MS), label = "night")

    BoxWithConstraints(modifier = modifier.clipToBounds().background(FinneyCream)) {
        // Масштаб «накрыть экран»: холст не меньше экрана ни по одной стороне.
        val canvasW = maxOf(maxWidth, maxHeight * Room.CANVAS_RATIO)
        val canvasH = canvasW / Room.CANVAS_RATIO

        // Ось комнаты встаёт в середину экрана, но левее FRAME_MIN_LEFT кадр не
        // уходит, и холст не отъезжает от края: пустой полосы сбоку быть
        // не должно ни на каком экране.
        val shiftX = (maxWidth / 2 - canvasW * Room.ROOM_AXIS_X).coerceIn(
            minimumValue = maxOf(maxWidth - canvasW, -canvasW * Room.FRAME_MIN_LEFT),
            maximumValue = 0.dp,
        )
        // Низ холста — к низу экрана. На телефоне холст ровно в высоту экрана,
        // и сдвиг нулевой; выше экрана он бывает только на широких экранах.
        val shiftY = maxHeight - canvasH

        // Видимая часть холста по ширине, доли его ширины.
        val viewLeft = -shiftX / canvasW
        val viewRight = (maxWidth - shiftX) / canvasW

        // Игровая — зеркало спальни: торшер справа, окно слева. Отражается весь холст
        // вместе со светом и тенями — иначе пришлось бы переставлять каждый источник.
        // Раскладка — по концепту игровой: торшер у правого края почти целиком, окно
        // слева уходит за край. Окно с торшером вместе шире экрана, целиком оба
        // не помещаются, и концепт выбрал торшер. Не меньше половины видимого края:
        // иначе на широком экране за отражённым холстом открылась бы пустота.
        val lampLeft = interior.lamp.layer.rect.left
        val mirrorAxis = maxOf((viewRight + lampLeft + PLAY_LAMP_PEEK) / 2f, viewRight / 2f)

        // Куда можно скакать: только в пределах видимой части холста, чтобы
        // питомец не ушёл за край экрана. Считается в холсте игровой до отражения —
        // видимое на экране там лежит зеркально.
        val home = Room.petGround(RoomSpot.PLAY)
        val visibleLeft = 2f * mirrorAxis - viewRight
        val visibleRight = 2f * mirrorAxis - viewLeft
        val strollMin = visibleLeft - home.left - home.width * PET_MARGIN
        val strollMax = visibleRight - home.right + home.width * PET_MARGIN
        // Игрушки — на полу по бокам видимой части комнаты, см. RoomToys.kt.
        val toyPlaces = toys.mapNotNull { id -> itemArt(id)?.let { id to it } }
            .let { found -> found.zip(toySlots(found.size, visibleLeft, visibleRight)) }
        val toyFloor = ToyFloor(left = visibleLeft, right = visibleRight)
        val toyMoves = rememberToyMoves()
        // Ящик для игрушек (ToyBox.kt). Проданная или пропавшая игрушка из него уходит.
        val toyBox = rememberToyBox()
        val toyBoxRect = toyBoxRect(toyFloor)
        LaunchedEffect(toys) { toyBox.inside.retainAll(toys) }
        // С какого сдвига прогулки питомец не закрывает ящик: заходит на него не больше
        // чем на [BOX_OVERLAP] ширины. Ящик в холсте до отражения слева, и чем меньше
        // сдвиг, тем питомец к нему ближе. Своё место считается свободным всегда.
        val boxClear = (toyBoxRect.right - toyBoxRect.width * BOX_OVERLAP - home.left - home.width * PET_MARGIN)
            .coerceAtMost(0f)
        val currentToyPlaces by rememberUpdatedState(toyPlaces)
        val currentOnHop by rememberUpdatedState(onHop)
        val currentOnToyBoxEmpty by rememberUpdatedState(onToyBoxEmpty)
        fun tapToyBox() {
            val places = currentToyPlaces.associate { (toy, place) -> toy.first to place }
            // Свободное место — подальше от игрушек на полу, тех, что уже летят, и от ног питомца.
            val pet = home.shiftedX(stroll.value)
            val taken = buildList {
                for ((id, place) in places) {
                    if (toyBox.away(id)) continue
                    val here = toyHere(id, place, toyMoves, toyFloor)
                    add(Offset((here.left + here.right) / 2f, here.bottom))
                }
                toyBox.flights.values.filter { !it.into }.forEach { add(Offset((it.to.left + it.to.right) / 2f, it.to.bottom)) }
                val feet = (pet.left + pet.right) / 2f
                add(Offset(feet - pet.width * 0.15f, Room.PLAY_PET_FEET))
                add(Offset(feet + pet.width * 0.15f, Room.PLAY_PET_FEET))
            }
            toyBox.tap(
                box = toyBoxRect,
                landing = toyLanding(toyBox.inside, places, toyFloor, taken),
                moves = toyMoves,
                animated = animations,
                onEmpty = { currentOnToyBoxEmpty() },
                // Игрушка снова на полу — питомец подпрыгивает от радости, если не стоит у ящика:
                // тогда он и так отскочит в сторону.
                onToss = { if (stroll.value >= boxClear) currentOnHop() },
            )
        }
        // Какая комната на экране сейчас: пока питомец уходит, это ещё старая.
        var shown by remember { mutableStateOf(spot) }
        // Где питомец на экране относительно своего места — доля ширины холста, вправо +.
        // Для перехода, а не для прогулки: прогулка живёт в [stroll].
        val travel = remember { Animatable(0f) }
        // Игрушки смотрят вслед: питомец уже почти за краем — до смены комнаты.
        var toysWatch by remember { mutableStateOf(false) }
        // Края питомца на экране в комнате [room], пока он на своём месте: в игровой зеркально.
        fun petOnScreen(room: RoomSpot): RelRect = when (room) {
            RoomSpot.PLAY -> Room.petGround(room).shiftedX(stroll.value).mirrored(mirrorAxis)
            else -> Room.petGround(room)
        }
        // Сколько раз торопили — чтобы отличить «поторопили» от «сменили комнату».
        var hurried by remember { mutableIntStateOf(trip.hurries) }
        LaunchedEffect(spot, trip.hurries) {
            val rushed = trip.hurries != hurried
            hurried = trip.hurries
            if (spot == shown && travel.value == 0f) {
                trip.at = spot
                return@LaunchedEffect
            }
            // Поторопили, передумали посреди перехода, только что проснулись или анимации
            // выключены — сразу на месте: ждать, пока он допрыгает, ребёнку незачем.
            if (rushed || spot == shown || !animations || asleep || travel.value != 0f) {
                shown = spot
                toysWatch = false
                stroll.snapTo(0f)
                travel.snapTo(0f)
                trip.at = spot
                return@LaunchedEffect
            }
            trip.at = null
            val right = spot.ordinal > shown.ordinal
            val from = petOnScreen(shown)
            onHop()
            delay(HOP_CROUCH_MS)
            // Глаза — только в конце прыжка и на миг: заметить их должен не каждый.
            val peek = launch {
                delay((LEAP_MS * TOYS_WATCH_FROM).toLong())
                toysWatch = right
            }
            // Без звука уход за край выглядел сбоем, а не шуткой.
            sounds.play(Sfx.PetLeap)
            travel.animateTo(
                if (right) viewRight - from.left + LEAP_OVERSHOOT else viewLeft - from.right - LEAP_OVERSHOOT,
                tween(LEAP_MS, easing = FastOutLinearInEasing),
            )
            // За краем его не видно — тут и меняется комната. В игровую он
            // возвращается на своё место, а не туда, где гулял.
            peek.cancel()
            shown = spot
            toysWatch = false
            stroll.snapTo(0f)
            val to = petOnScreen(spot)
            travel.snapTo(if (right) viewLeft - to.right - LEAP_OVERSHOOT else viewRight - to.left + LEAP_OVERSHOOT)
            onHop()
            delay(HOP_CROUCH_MS)
            travel.animateTo(0f, tween(LEAP_MS, easing = LinearOutSlowInEasing))
            trip.at = spot
            sounds.play(Sfx.PetLand)
        }

        // Без анимаций не гуляет: прыжки по игровой стали бы телепортом с места на место.
        // Здесь же он отходит от ящика: и сам, постояв у него, и сразу, когда к ящику
        // несут игрушку или нажимают на него, — даже когда гулять ему не время (с ним играют).
        val canMove = spot == RoomSpot.PLAY && shown == RoomSpot.PLAY && !asleep && animations && !travel.isRunning
        val currentWander by rememberUpdatedState(wander)
        LaunchedEffect(canMove, strollMin, strollMax, boxClear) {
            if (!canMove || strollMax <= strollMin) return@LaunchedEffect
            suspend fun hopTo(target: Float) {
                val from = stroll.value
                val hops = maxOf(1, kotlin.math.ceil(kotlin.math.abs(target - from) / HOP_LENGTH).toInt())
                for (i in 1..hops) {
                    onHop()
                    delay(HOP_CROUCH_MS)
                    stroll.animateTo(
                        from + (target - from) * i / hops,
                        tween(HOP_FLIGHT_MS, easing = FastOutSlowInEasing),
                    )
                    // Приземлился — лёгкий шажок.
                    sounds.play(Sfx.Tap)
                }
            }
            // Отходит от ящика недалеко — на шаг-другой, чтобы ящик открылся.
            val awayMin = maxOf(boxClear, strollMin)
            val awayMax = minOf(strollMax, awayMin + HOP_LENGTH * 2f)
            while (true) {
                val near = stroll.value < boxClear
                val pause = if (near) Random.nextLong(NearBoxPauseMs.first, NearBoxPauseMs.last)
                else Random.nextLong(StrollPauseMs.first, StrollPauseMs.last)
                val pokes = toyBox.pokes
                // Ждёт паузу, но к ящику обратились — не ждёт.
                val poked = withTimeoutOrNull(pause) { snapshotFlow { toyBox.pokes }.first { it != pokes } } != null
                when {
                    stroll.value < boxClear && (poked || currentWander) ->
                        hopTo(awayMin + Random.nextFloat() * (awayMax - awayMin))
                    poked || !currentWander -> Unit
                    else -> hopTo(Random.nextFloat() * (strollMax - strollMin) + strollMin)
                }
            }
        }

        // Камера: весь слой комнаты увеличивается от левого верхнего угла и
        // сдвигается так, чтобы спящий встал посередине. Сдвиг зажат, чтобы
        // за краем холста не открылась пустота; масштаб и сдвиг растут вместе,
        // и в промежутке край тоже не виден. Интерфейс лежит поверх сцены
        // отдельно и не увеличивается.
        val density = LocalDensity.current
        val camera = with(density) {
            val sleeper = Room.PetInCapsule
            val cx = (shiftX + canvasW * ((sleeper.left + sleeper.right) / 2f)).toPx()
            val cy = (shiftY + canvasH * ((sleeper.top + sleeper.bottom) / 2f)).toPx()
            val w = maxWidth.toPx()
            val h = maxHeight.toPx()
            val left = shiftX.toPx()
            val top = shiftY.toPx()
            Offset(
                (w / 2f - SLEEP_ZOOM * cx).coerceIn(w - SLEEP_ZOOM * (left + canvasW.toPx()), -SLEEP_ZOOM * left),
                (h * SLEEP_FOCUS_Y - SLEEP_ZOOM * cy).coerceIn(h - SLEEP_ZOOM * (top + canvasH.toPx()), -SLEEP_ZOOM * top),
            )
        }

        // Камера выбора интерьера: выбранная часть крупно над панелью выбора.
        // Масштаб не меньше «холст во всю ширину» и сдвиг зажат, как у камеры сна:
        // за краем холста пусто, и показывать это нельзя.
        // Пока питомец ещё идёт в нужную комнату, кадр держится на старой: иначе камера
        // уезжала бы к вещи в спальне, а на экране оставалась игровая.
        // Игровая отражена: часть и питомец там лежат зеркально, и холст на экране кончается
        // по краям отражения, а не своим.
        val mirroredRoom = shown == RoomSpot.PLAY
        val edit = with(density) {
            val framed = when {
                shown != spot -> null
                focusPet -> petOnScreen(shown)
                focusRug -> rugRect(toyFloor, interior.rug).mirrored(mirrorAxis)
                mirroredRoom -> focus?.mirrored(mirrorAxis)
                else -> focus
            }
            val target = framed?.let { rect ->
                val w = maxWidth.toPx()
                val h = (maxHeight - focusBottom).toPx().coerceAtLeast(1f)
                val floor = (maxHeight - focusSolid).toPx()
                val top = shiftY.toPx()
                val cw = canvasW.toPx()
                val ch = canvasH.toPx()
                val left = shiftX.toPx() + cw * (if (mirroredRoom) maxOf(0f, 2f * mirrorAxis - 1f) else 0f)
                val right = shiftX.toPx() + cw * (if (mirroredRoom) minOf(1f, 2f * mirrorAxis) else 1f)
                val pad = FOCUS_PAD.toPx()
                val rw = cw * rect.width
                val rh = ch * rect.height
                val s = minOf((w - 2 * pad) / rw, (h - 2 * pad) / rh, FOCUS_MAX_ZOOM).coerceAtLeast(w / (right - left))
                val cx = shiftX.toPx() + cw * (rect.left + rect.width / 2f)
                val cy = top + ch * (rect.top + rect.height / 2f)
                val x = (w / 2f - s * cx).coerceIn(minOf(w - s * right, -s * left), -s * left)
                val y = (h / 2f - s * cy).coerceIn(minOf(floor - s * (top + ch), -s * top), -s * top)
                Triple(s, x, y)
            }
            // Без перелёта: масштаб и сдвиг идут одной кривой, и по дороге край холста
            // не отходит от края экрана. С пружиной 0.8 они расходились — проглядывал фон.
            //
            // Низ холста при выборе может стоять выше низа экрана — его закрывает панель. Панель
            // уезжает за 250 мс (HomeScreen), а камера отъезжала пружиной дольше секунды, и
            // под уехавшей панелью был виден кремовый фон. Поэтому назад — за то же время и
            // кривой, что не отстаёт от панели: низ холста всё время ниже её верха.
            val spec: AnimationSpec<Float> = when {
                !animations -> snap()
                target == null -> tween(FOCUS_RETURN_MS, easing = LinearOutSlowInEasing)
                else -> spring(dampingRatio = 1f, stiffness = Spring.StiffnessVeryLow)
            }
            val goal = target ?: Triple(1f, 0f, 0f)
            Triple(
                animateFloatAsState(goal.first, spec, label = "focusScale"),
                animateFloatAsState(goal.second, spec, label = "focusX"),
                animateFloatAsState(goal.third, spec, label = "focusY"),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = edit.first.value
                    scaleY = edit.first.value
                    translationX = edit.second.value
                    translationY = edit.third.value
                },
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val z = zoom.value
                    val s = 1f + (SLEEP_ZOOM - 1f) * z
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = s
                    scaleY = s
                    translationX = camera.x * z
                    translationY = camera.y * z
                },
        ) {
        key(shown) {
            val room = shown
            run {
                RoomCanvas(
                    room = room,
                    lighting = lights.getValue(room),
                    canvasW = canvasW,
                    canvasH = canvasH,
                    shiftX = shiftX,
                    shiftY = shiftY,
                    walls = walls,
                    windows = windows,
                    lamps = lamps,
                    rugs = rugs,
                    flyer = flyer,
                    capsule = capsule,
                    walk = { walk.value },
                    // Сдвиг питомца в холсте комнаты: прогулка и переход. В отражённой
                    // игровой экранное «вправо» в холсте — влево.
                    stroll = { if (room == RoomSpot.PLAY) stroll.value - travel.value else travel.value },
                    door = { door.value },
                    onTapItem = onTapItem,
                    toys = toyPlaces,
                    toyMoves = toyMoves,
                    toyFloor = toyFloor,
                    toysEnabled = toysEnabled,
                    onToyDrag = onToyDrag,
                    onToyDrop = onToyDrop,
                    toyNudge = toyNudge,
                    onToyPlaced = onToyPlaced,
                    onToyGone = onToyGone,
                    toyBox = toyBox,
                    toyBoxRect = toyBoxRect,
                    onToyBoxTap = ::tapToyBox,
                    animations = animations,
                    pet = pet,
                    mirrorAxis = mirrorAxis.takeIf { room == RoomSpot.PLAY },
                    toysWatch = { toysWatch },
                )
            }
        }

        }
        }

        // Ночь поверх комнаты: свет выключен. Лежит вне камеры — увеличенная
        // вместе с комнатой, она съезжала бы с края экрана. Читается на отрисовке.
        Box(
            modifier = Modifier.fillMaxSize().drawBehind {
                if (shade.value > 0f) drawRect(SkyColor, alpha = shade.value)
            },
        )

        // Валик смены стены — поверх всего, и питомца тоже, и во всю ширину экрана.
        // Высота идёт за стеной через обе камеры, толщина — от холста.
        Box(
            modifier = Modifier.fillMaxSize().drawBehind {
                val t = walls.progress.value
                if (t >= 1f) return@drawBehind
                val z = zoom.value
                val sleep = 1f + (SLEEP_ZOOM - 1f) * z
                val scale = edit.first.value * sleep
                val atCanvas = shiftY.toPx() + canvasH.toPx() * rollerAt(t)
                val y = edit.third.value + edit.first.value * (camera.y * z + sleep * atCanvas)
                roller(y, h = canvasW.toPx() * 0.045f * scale, t = t)
            },
        )
    }
}

/**
 * Дошёл ли питомец до комнаты, куда его отправили. Снаружи сцены: кнопка комнаты,
 * нажатая ещё раз, пока он в пути, не открывает выбор (ребёнок жмёт снова, потому что
 * не хочет ждать), а переносит его сразу, и панель ванной не появляется раньше него.
 */
@Stable
class RoomTrip(start: RoomSpot) {
    /** Где питомец стоит; null — в прыжке между комнатами. */
    var at by mutableStateOf<RoomSpot?>(start)
        internal set
    internal var hurries by mutableIntStateOf(0)
        private set

    /** Перенести сразу туда, куда он идёт. */
    fun hurry() {
        hurries++
    }
}

/**
 * Прыжок в капсулу или из неё: присел, как в [ru.finney.pet.ui.pet.PetAnimation.playJoy],
 * и полетел по дуге. Дуга рисуется в [RoomCanvas] по тому же [walk].
 */
private suspend fun jump(walk: Animatable<Float, AnimationVector1D>, to: Float, onHop: () -> Unit) {
    if (walk.value == to) return
    // Без анимаций — сразу на месте: иначе присед и дуга мелькнули бы по кадру.
    if (!motionEnabled()) {
        walk.snapTo(to)
        return
    }
    onHop()
    delay(HOP_CROUCH_MS)
    walk.animateTo(to, tween(WALK_MS - HOP_CROUCH_MS.toInt(), easing = FastOutSlowInEasing))
}

/**
 * Одна комната целиком: стены, её окно и торшер, мебель и питомец.
 *
 * wrapContentSize обязателен: холст больше экрана, и requiredSize без него
 * центрирует его в родителе — комната уезжала влево ещё на полразницы
 * ширин (около 40 dp) мимо shiftX, и торшер срезало наполовину.
 */
@Composable
private fun RoomCanvas(
    room: RoomSpot,
    lighting: RoomLighting,
    canvasW: Dp,
    canvasH: Dp,
    shiftX: Dp,
    shiftY: Dp,
    walls: PartSwap<WallSkin>,
    windows: PartSwap<WindowSkin>,
    lamps: PartSwap<LampSkin>,
    rugs: PartSwap<RugSkin>,
    flyer: WindowFlyer,
    capsule: Boolean,
    walk: () -> Float,
    stroll: () -> Float,
    door: () -> Float,
    onTapItem: (() -> Unit)?,
    toys: List<Pair<Pair<String, Int>, RelRect>>,
    toyMoves: SnapshotStateMap<String, Offset>,
    toyFloor: ToyFloor,
    toysEnabled: Boolean,
    onToyDrag: (toyId: String, centre: Offset, delta: Offset) -> Unit,
    onToyDrop: (toyId: String) -> Unit,
    toyNudge: (toyId: String) -> Offset,
    onToyPlaced: (toyId: String, bounds: Rect) -> Unit,
    onToyGone: (toyId: String) -> Unit,
    toyBox: ToyBoxState,
    toyBoxRect: RelRect,
    onToyBoxTap: () -> Unit,
    animations: Boolean,
    pet: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    petVisibility: () -> Float = { 1f },
    mirrorAxis: Float? = null,
    toysWatch: () -> Boolean = { false },
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .offset(shiftX, shiftY)
                .requiredSize(canvasW, canvasH)
                .then(
                    if (mirrorAxis != null) Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(mirrorAxis, 0.5f)
                        scaleX = -1f
                    } else Modifier,
                ),
        ) {
            Box(modifier = Modifier.fillMaxSize().roomLight(lighting)) {
                WallSwap(walls, canvasW, canvasH)
                // Ковёр плоский, лежит на полу под всем, что стоит, и свет комнаты ложится на него
                // как на пол. Тени у него нет.
                if (room == RoomSpot.PLAY) RugSwap(rugs, toyFloor, canvasW, canvasH, mirrored = mirrorAxis != null)
                if (room.hasWindow) {
                    // На кухне питомец крупнее и сидит выше, окно за ним почти пропадало
                    // под головой и кнопками. Растёт от подоконника вверх: вниз окно
                    // ложилось почти на пол, и комната выглядела ниже.
                    val grow = if (room == RoomSpot.KITCHEN) KITCHEN_WINDOW_SCALE else 1f
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            val frame = windows.now.frame.rect
                            transformOrigin = TransformOrigin(frame.left + frame.width / 2f, frame.bottom)
                            scaleX = grow
                            scaleY = grow
                        },
                    ) { WindowSwap(windows, canvasW, canvasH, flyer) }
                }
                if (room.hasLamp) LampSwap(lamps, canvasW, canvasH)
            }
            // Искры и вспышки — поверх света: свет ночной и приглушил бы их.
            SwapEffects(walls, windows.takeIf { room.hasWindow }, lamps.takeIf { room.hasLamp })

            val visibility = petVisibility
            val ground = Room.petGround(room).shiftedX(stroll())

            // Тень у питомца только там, где он стоит на полу: за столом и в ванне она
            // упала бы на мебель, а мебель пола не знает.
            when (room) {
                // Игрушки на полу: дальше ступней питомца — за ним, ближе — перед ним (RoomToys.kt).
                RoomSpot.PLAY -> {
                    // Ящик — за питомцем и за игрушками на полу: он дальше от зрителя.
                    val arts = toys.associate { (toy, _) -> toy.first to toy.second }
                    ToyBox(
                        state = toyBox,
                        rect = toyBoxRect,
                        arts = arts,
                        canvasW = canvasW,
                        canvasH = canvasH,
                        lighting = lighting,
                        enabled = toysEnabled,
                        animated = animations,
                        mirrored = mirrorAxis != null,
                        onTap = onToyBoxTap,
                    )
                    for ((toy, place) in toys) {
                        if (toyBox.away(toy.first)) continue
                        key(toy.first) {
                            RoomToy(
                                toyId = toy.first,
                                art = toy.second,
                                place = place,
                                moves = toyMoves,
                                floor = toyFloor,
                                canvasW = canvasW,
                                canvasH = canvasH,
                                lighting = lighting,
                                enabled = toysEnabled,
                                onDrag = onToyDrag,
                                onDrop = onToyDrop,
                                nudge = toyNudge,
                                mirrored = mirrorAxis != null,
                                watching = toysWatch,
                                onPlaced = onToyPlaced,
                                onMoved = { _, here -> toyBox.hover(toyBoxRect, here) },
                                catch = { id, here, lift -> toyBox.catch(id, here, lift, toyBoxRect, animations) },
                                onGone = onToyGone,
                            )
                        }
                    }
                    for ((id, flight) in toyBox.flights) {
                        val art = arts[id] ?: continue
                        key(id) {
                            FlyingToy(
                                art = art,
                                flight = flight,
                                rim = toyBoxRim(toyBoxRect),
                                canvasW = canvasW,
                                canvasH = canvasH,
                                lighting = lighting,
                                mirrored = mirrorAxis != null,
                            )
                        }
                    }
                    // Комната отражена, а питомец — нет: левый и правый у него те же, что везде.
                    Pet(ground, canvasW, canvasH, lighting, PetFooting, visibility, pet = {
                        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = -1f }) { pet() }
                    })
                }

                RoomSpot.LIVING -> {
                    if (capsule) {
                        // Капсула в углу, питомец перед ней. Засыпая, он запрыгивает
                        // в капсулу по дуге и становится меньше — она дальше от зрителя.
                        // Дверь закрывается перед ним, стекло у неё полупрозрачное,
                        // и спящего видно. Тень на полу — только пока он стоит на месте.
                        LitLayer(Room.Capsule, canvasW, canvasH, lighting, Solids.Capsule, visibility)
                        TapZone(Room.Capsule.rect, canvasW, canvasH, "Уложить спать", onTapItem)
                        val t = walk()
                        val footing = if (t == 0f) PetFooting else null
                        val arc = -CAPSULE_JUMP_ARC * 4f * t * (1f - t)
                        Pet(ground.lerp(Room.PetInCapsule, t).shiftedY(arc), canvasW, canvasH, lighting, footing, visibility, pet)
                        CapsuleDoor(canvasW, canvasH, lighting, shut = door)
                    } else {
                        Pet(ground, canvasW, canvasH, lighting, PetFooting, visibility, pet)
                    }
                }

                // Питомец рисуется раньше стола: столешница перекрывает
                // ему низ, и получается, что он сидит за столом, а не на нём.
                RoomSpot.KITCHEN -> {
                    Pet(ground, canvasW, canvasH, lighting, footing = null, visibility, pet)
                    LitLayer(Room.Table, canvasW, canvasH, lighting, Solids.Table, visibility)
                    TapZone(Room.Table.rect, canvasW, canvasH, "Покормить", onTapItem)
                }

                // Питомец сидит в ванне, а не перед ней, поэтому чаша
                // рисуется поверх него: она непрозрачна ниже 0.664
                // и прячет всё, что должно быть под водой. Пена заходит
                // выше борта (0.527 против 0.619) и делится на две:
                // задняя за питомцем, передняя перед ним.
                RoomSpot.BATH -> {
                    Foam(Room.BathFoamBack, canvasW, canvasH, lighting, phase = 0f)
                    Pet(ground, canvasW, canvasH, lighting, footing = null, visibility, pet)
                    LitLayer(Room.Bath, canvasW, canvasH, lighting, Solids.Bath, visibility)
                    Foam(Room.BathFoamFront, canvasW, canvasH, lighting, phase = 0.5f)
                    TapZone(Room.Bath.rect, canvasW, canvasH, "Ванна", onTapItem)
                }
            }
        }
    }
}

/** Питомец на своём месте в комнате. Квадрат по ширине холста — как у слоёв. */
@Composable
private fun Pet(
    ground: RelRect,
    canvasW: Dp,
    canvasH: Dp,
    lighting: RoomLighting,
    footing: Footing?,
    visibility: () -> Float,
    pet: @Composable BoxScope.() -> Unit,
) {
    LitBody(
        lighting = lighting,
        place = ground,
        footing = footing,
        visibility = visibility,
        pad = PET_PAD,
        padTop = PET_PAD_TOP,
        modifier = Modifier
            .offset(canvasW * ground.left, canvasH * ground.top)
            .graphicsLayer { alpha = visibility() }
            .requiredSize(canvasW * ground.width),
        content = pet,
    )
}

/**
 * Окно: вид, то, что за ним живёт, и рама.
 *
 * Рама шире проёма и кладётся последней — она перекрывает края вида, и стык
 * не виден. Вид и живое за окном рисуются в отдельном слое ровно по проёму,
 * а живое — режимом SrcAtop, только поверх вида: у иллюминатора и шестиугольника
 * проём не прямоугольный, и рыбка или пчела в его углах оказались бы на обоях.
 *
 * Небо по умолчанию медленно плывёт влево — полоса [WindowView.Sky] повторяется
 * встык и сдвигается на свою ширину за [SKY_LOOP_MS]. Камеры в комнате нет,
 * поэтому движение идёт от времени. Края полосы — ровный фон неба (см.
 * pack_room.py), так что на стыке шва нет, а в конце круга сдвиг ровно на
 * ширину полосы, и перескок в начало не виден.
 *
 * Между пролётами летун не рисуется вовсе, а не ждёт за краем: раньше НЛО
 * стояло там всю паузу, и на экране торчал его обрезанный край.
 *
 * Все движения читаются только на отрисовке и не пересобирают окно.
 */
@Composable
internal fun Window(window: WindowSkin, canvasW: Dp, canvasH: Dp, flyer: WindowFlyer) {
    val hole = window.hole
    val animations = LocalAnimations.current
    val clock = rememberInfiniteTransition(label = "window")
    val driftAnim = clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SKY_LOOP_MS, easing = LinearEasing)),
        label = "drift",
    )
    val turnAnim = clock.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(WHEEL_TURN_MS, easing = LinearEasing)),
        label = "turn",
    )
    val glimmerAnim = clock.animateFloat(
        initialValue = TWINKLE_LOW,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(TWINKLE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glimmer",
    )

    val view = when (val v = window.view) {
        is WindowView.Sky -> v.strip
        is WindowView.Picture -> v.layer
    }
    val viewImage = ImageBitmap.imageResource(view.image)
    // Картинки живого — в том же порядке, в каком рисуются.
    val life = window.life
    val lifeImages = when (life) {
        is WindowLife.Flyer -> listOf(life.layer)
        is WindowLife.Wheel -> listOf(life.wheel, life.stand) + life.cabins.map { it.layer }
        is WindowLife.Twinkle -> listOf(life.layer)
        null -> emptyList()
    }.map { ImageBitmap.imageResource(it.image) }

    Box(
        modifier = Modifier
            .offset(canvasW * hole.left, canvasH * hole.top)
            .requiredSize(canvasW * hole.width, canvasH * hole.height)
            // Свой слой: SrcAtop кладёт живое только на вид, а не на всю комнату под ним.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                val unit = Size(size.width / hole.width, size.height / hole.height)
                when (window.view) {
                    is WindowView.Sky -> {
                        drawRect(SkyColor)
                        val strip = view.rect
                        val tileW = strip.width * unit.width
                        val tileH = strip.height * unit.height
                        val top = (strip.top - hole.top) * unit.height
                        val drift = if (animations) driftAnim.value else 0f
                        var x = (strip.left - hole.left) * unit.width - drift * tileW
                        while (x > 0f) x -= tileW
                        // Сдвиг дробный, и полоса рисуется масштабом, а не в целых
                        // пикселях: иначе на такой скорости небо ползло бы рывками.
                        while (x < size.width) {
                            translate(x, top) {
                                scale(tileW / viewImage.width, tileH / viewImage.height, pivot = Offset.Zero) { drawImage(viewImage) }
                            }
                            x += tileW
                        }
                    }

                    is WindowView.Picture -> drawRoomImage(viewImage, view.rect, hole, unit)
                }

                when (life) {
                    is WindowLife.Flyer -> flyer.flight?.let { current ->
                        val t = flyer.progress.value
                        val corner = flyer.corner(current, t)
                        val rect = life.layer.rect
                        val at = Offset(hole.left + corner.x, hole.top + corner.y)
                        val centre = Offset((corner.x + rect.width / 2f) * unit.width, (corner.y + rect.height / 2f) * unit.height)
                        // Пчела и рыбка нарисованы смотрящими в одну сторону: летят в другую — разворачиваются.
                        val mirror = life.facesRight != null && life.facesRight != current.headingRight(t)
                        withTransform({
                            rotate(current.tilt(t), centre)
                            if (mirror) scale(-1f, 1f, centre)
                        }) {
                            drawRoomImage(lifeImages[0], rect, hole, unit, at, BlendMode.SrcAtop)
                        }
                    }

                    is WindowLife.Wheel -> {
                        val angle = if (animations) turnAnim.value else 0f
                        val hub = Offset((life.hub.x - hole.left) * unit.width, (life.hub.y - hole.top) * unit.height)
                        rotate(angle, hub) { drawRoomImage(lifeImages[0], life.wheel.rect, hole, unit, blend = BlendMode.SrcAtop) }
                        // Подставка перед колесом: её шапка закрывает ось, как в сборке художника.
                        drawRoomImage(lifeImages[1], life.stand.rect, hole, unit, blend = BlendMode.SrcAtop)
                        // Кабинка висит на ободе за верхнюю середину: точка подвеса
                        // идёт по кругу вместе с колесом, а сама кабинка не наклоняется.
                        val radians = angle * PI.toFloat() / 180f
                        val cos = cos(radians)
                        val sin = sin(radians)
                        life.cabins.forEachIndexed { i, cabin ->
                            val r = cabin.layer.rect
                            val attachment = cabin.attachment
                            // Обод и подвес вращаются вокруг одной оси в пикселях:
                            // доли ширины и высоты у портретного холста различаются.
                            val hang = Offset((attachment.x - hole.left) * unit.width, (attachment.y - hole.top) * unit.height) - hub
                            val moved = Offset(hang.x * cos - hang.y * sin, hang.x * sin + hang.y * cos)
                            val at = Offset(attachment.x - r.width / 2f, attachment.y)
                            translate(moved.x - hang.x, moved.y - hang.y) {
                                drawRoomImage(lifeImages[2 + i], r, hole, unit, at = at, blend = BlendMode.SrcAtop)
                            }
                        }
                    }

                    is WindowLife.Twinkle -> {
                        val glimmer = if (animations) glimmerAnim.value else 1f
                        drawRoomImage(lifeImages[0], life.layer.rect, hole, unit, blend = BlendMode.SrcAtop, alpha = glimmer)
                    }

                    null -> Unit
                }
            },
    )

    Layer(window.frame, canvasW, canvasH)
}

/**
 * Слой комнаты внутри куска холста [area]: на долю холста там [unit] пикселей.
 * [at] — куда встаёт левый верхний угол слоя. Масштабом, а не в целых
 * пикселях: летуны движутся плавно.
 */
private fun DrawScope.drawRoomImage(
    image: ImageBitmap,
    rect: RelRect,
    area: RelRect,
    unit: Size,
    at: Offset = Offset(rect.left, rect.top),
    blend: BlendMode = BlendMode.SrcOver,
    alpha: Float = 1f,
) {
    translate((at.x - area.left) * unit.width, (at.y - area.top) * unit.height) {
        scale(rect.width * unit.width / image.width, rect.height * unit.height / image.height, pivot = Offset.Zero) {
            drawImage(image, alpha = alpha, blendMode = blend)
        }
    }
}

/**
 * Летун за окном — НЛО, пчела, рыбка: пролёты и где он сейчас.
 *
 * Живёт в сцене, а не в окне: где НЛО, нужно ещё и свету — пролетая,
 * оно подсвечивает комнату зелёным, и тень питомца поворачивается за ним.
 * У окна без летуна пролётов нет.
 */
@Stable
internal class WindowFlyer(private val window: WindowSkin) {
    private val life = window.life as? WindowLife.Flyer

    var flight by mutableStateOf<UfoFlight?>(null)
        private set
    val progress = Animatable(0f)

    suspend fun fly(onStart: (WindowLife.Flyer) -> Unit) {
        val life = life ?: return
        var pause = UfoFirstPauseMs
        while (true) {
            delay(Random.nextLong(pause.first, pause.last))
            // Без анимаций пролёт — один кадр летуна у края окна: не летит вовсе.
            if (!motionEnabled()) continue
            val next = Random.nextUfoFlight()
            flight = next
            onStart(life)
            progress.snapTo(0f)
            progress.animateTo(1f, tween(next.durationMs, easing = LinearEasing))
            flight = null
            pause = UfoPauseMs
        }
    }

    /**
     * Левый верхний угол летуна относительно проёма, в долях холста.
     *
     * Путь от «целиком за одним краем проёма» до «целиком за другим»: пролёт
     * кончается, только когда летун ушёл из окна полностью, и не обрывается
     * на полпути.
     */
    fun corner(current: UfoFlight, t: Float): Offset {
        val hole = window.hole
        val size = life?.layer?.rect ?: return Offset.Zero
        val x = -size.width + current.along(t) * (hole.width + size.width)
        return Offset(
            x = if (current.fromLeft) x else hole.width - size.width - x,
            y = current.height(t) * (hole.height - size.height),
        )
    }

    /** Середина НЛО в долях холста и насколько оно в проёме; null — пролёта нет или летун не светит. */
    fun light(): Pair<Offset, Float>? {
        val ufo = life?.takeIf { it.glows }?.layer?.rect ?: return null
        val current = flight ?: return null
        val hole = window.hole
        val corner = corner(current, progress.value)
        val centre = Offset(
            hole.left + corner.x + ufo.width / 2f,
            hole.top + corner.y + ufo.height / 2f,
        )
        // Свет входит в комнату через стекло: пока НЛО за рамой, комнату
        // оно не освещает, и зелёный не вспыхивает из ниоткуда.
        val presence = ((centre.x - hole.left) / ufo.width).coerceIn(0f, 1f) *
            ((hole.right - centre.x) / ufo.width).coerceIn(0f, 1f)
        return centre to presence
    }
}

/**
 * Один пролёт НЛО.
 *
 * Пролёты не повторяются: каждый раз заново выбирается, с какой стороны оно
 * летит, на какой высоте, как сильно покачивается и не зависнет ли посреди
 * окна — поглазеть на питомца. Одинаковый ровный пролёт на третий раз
 * смотреть уже скучно.
 *
 * Всё в долях: путь 0..1 от «целиком за краем, откуда летит» до «целиком
 * за противоположным», высота 0..1 от верха проёма до низа.
 */
@Immutable
internal data class UfoFlight(
    val durationMs: Int,
    val fromLeft: Boolean,
    val startHeight: Float,
    val endHeight: Float,
    /** Размах покачивания вверх-вниз, доля высоты проёма. */
    val bob: Float,
    /** Сколько раз НЛО качнётся за пролёт. */
    val bobCycles: Float,
    /** Где на пути зависнуть; null — пролететь не останавливаясь. */
    val hoverAt: Float?,
    /** Повисев, улететь туда же, откуда прилетело. */
    val turnBack: Boolean,
) {
    /** Летит ли вправо в момент [t]: повисев, может развернуться обратно. */
    fun headingRight(t: Float): Boolean = fromLeft != (turnBack && hoverAt != null && t >= HOVER_OUT)

    /** Сколько пути пройдено к моменту [t]. */
    fun along(t: Float): Float {
        val stop = hoverAt ?: return t
        return when {
            // Подлетает с торможением, висит, уходит с разгоном: как будто
            // заметило что-то в комнате, а потом спохватилось.
            t < HOVER_IN -> stop * FastOutSlowInEasing.transform(t / HOVER_IN)
            t < HOVER_OUT -> stop + HOVER_DRIFT * sin((t - HOVER_IN) / (HOVER_OUT - HOVER_IN) * PI.toFloat())
            else -> {
                val exit = FastOutLinearInEasing.transform((t - HOVER_OUT) / (1f - HOVER_OUT))
                val target = if (turnBack) 0f else 1f
                stop + (target - stop) * exit
            }
        }
    }

    /** Высота к моменту [t]: плавный уход с одной высоты на другую плюс покачивание. */
    fun height(t: Float): Float {
        val drift = startHeight + (endHeight - startHeight) * t
        return (drift + bob * sin(t * bobCycles * 2f * PI.toFloat())).coerceIn(0f, 1f)
    }

    /**
     * Наклон по ходу движения: тарелка клюёт носом туда, куда летит, и
     * выравнивается, когда зависает. Скорость берётся разностью — путь
     * кусочный, и производную по формулам пришлось бы писать на каждый кусок.
     */
    fun tilt(t: Float): Float {
        val dt = 0.01f
        val speed = (along((t + dt).coerceAtMost(1f)) - along(t)) / dt
        val direction = if (fromLeft) 1f else -1f
        return (speed * direction * UFO_TILT_PER_SPEED).coerceIn(-UFO_MAX_TILT, UFO_MAX_TILT)
    }
}

/** Доли пролёта с зависанием: до HOVER_IN подлетает, до HOVER_OUT висит. */
private const val HOVER_IN = 0.35f
private const val HOVER_OUT = 0.65f

/** Пока висит, НЛО чуть подаётся вперёд и обратно — неподвижное выглядит картинкой. */
private const val HOVER_DRIFT = 0.02f

/** Градусов наклона на единицу скорости: ровный пролёт — около 6°. */
private const val UFO_TILT_PER_SPEED = 6f
private const val UFO_MAX_TILT = 14f

private fun Random.nextUfoFlight(): UfoFlight {
    val hover = nextFloat() < 0.4f
    // Высоты — в средней части проёма: рама заходит на края вида, и у самого
    // верха или низа НЛО наполовину пряталось бы под ней.
    val start = between(0.2f, 0.8f)
    return UfoFlight(
        // Без зависания пролёт бывает и стремительным, и ленивым.
        durationMs = if (hover) nextInt(7_000, 10_000) else nextInt(2_500, 7_500),
        fromLeft = nextBoolean(),
        startHeight = start,
        endHeight = (start + between(-0.35f, 0.35f)).coerceIn(0.1f, 0.9f),
        bob = between(0f, 0.12f),
        bobCycles = between(1f, 4f),
        hoverAt = if (hover) between(0.35f, 0.6f) else null,
        turnBack = hover && nextBoolean(),
    )
}

private fun Random.between(from: Float, until: Float): Float = from + nextFloat() * (until - from)

/** Пена тихо колышется. Слои дышат в противофазе, иначе движение читается как рывок всей ванны. */
@Composable
private fun Foam(layer: RoomLayer, canvasW: Dp, canvasH: Dp, lighting: RoomLighting, phase: Float) {
    val breath = rememberInfiniteTransition(label = "foam")
    val animations = LocalAnimations.current
    val swellAnim by breath.animateFloat(
        initialValue = -FOAM_SWELL,
        targetValue = FOAM_SWELL,
        animationSpec = infiniteRepeatable(
            animation = tween(FOAM_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset((FOAM_PERIOD_MS * phase).toInt()),
        ),
        label = "swell",
    )
    // Без анимаций бесконечная встаёт в крайнее положение — пена стоит посередине.
    val swell = if (animations) swellAnim else 0f

    LitBody(
        lighting = lighting,
        place = layer.rect,
        footing = null,
        // Пена на вдохе растёт на FOAM_SWELL — запас ровно под это.
        pad = FOAM_SWELL * 2f,
        modifier = Modifier
            .offset(canvasW * layer.rect.left, canvasH * layer.rect.top)
            .requiredSize(canvasW * layer.rect.width, canvasH * layer.rect.height),
    ) {
        Image(
            painter = painterResource(layer.image),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                // Масштаб читается внутри graphicsLayer, чтобы кадры анимации не
                // пересобирали слой — тот же приём, что у позы питомца.
                .graphicsLayer {
                    scaleX = 1f + swell
                    scaleY = 1f + swell
                    // Пена растёт от низа: верх её края должен гулять, а посадка в ванну — нет.
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
        )
    }
}

/**
 * Дверь капсулы. Закрывается, пока питомец спит: выезжает сверху, как
 * шторка, и проявляется. Своей тени у двери нет — её тень уже в капсуле.
 *
 * @param shut насколько закрыта, 0..1. Лямбдой: читается на отрисовке.
 */
@Composable
private fun CapsuleDoor(canvasW: Dp, canvasH: Dp, lighting: RoomLighting, shut: () -> Float) {
    if (shut() == 0f) return
    val rect = Room.CapsuleDoor.rect
    LitBody(
        lighting = lighting,
        place = rect,
        footing = null,
        modifier = Modifier
            .offset(canvasW * rect.left, canvasH * rect.top)
            .requiredSize(canvasW * rect.width, canvasH * rect.height)
            .graphicsLayer {
                alpha = shut()
                translationY = -(1f - shut()) * size.height * 0.25f
            },
    ) {
        Image(
            painter = painterResource(Room.CapsuleDoor.image),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Мебель: освещается там, где стоит, и отбрасывает тень на пол. */
@Composable
private fun LitLayer(
    layer: RoomLayer,
    canvasW: Dp,
    canvasH: Dp,
    lighting: RoomLighting,
    solid: Solid,
    visibility: () -> Float,
) {
    LitBody(
        lighting = lighting,
        place = layer.rect,
        footing = null,
        solid = solid,
        visibility = visibility,
        modifier = Modifier
            .offset(canvasW * layer.rect.left, canvasH * layer.rect.top)
            .requiredSize(canvasW * layer.rect.width, canvasH * layer.rect.height),
    ) {
        Image(
            painter = painterResource(layer.image),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun Layer(layer: RoomLayer, canvasW: Dp, canvasH: Dp) {
    Image(
        painter = painterResource(layer.image),
        contentDescription = null,
        // Размер уже точный — слой обрезан ровно по своим границам, растягивать нечего.
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .offset(canvasW * layer.rect.left, canvasH * layer.rect.top)
            .requiredSize(canvasW * layer.rect.width, canvasH * layer.rect.height),
    )
}

/**
 * Нажимаемое место на мебели.
 *
 * Зона невидима: подсказкой служит значок, который кладут поверх (см. HomeScreen).
 * Название действия уходит в TalkBack — по картинке прочесть его нельзя.
 */
@Composable
private fun TapZone(rect: RelRect, canvasW: Dp, canvasH: Dp, action: String, onTap: (() -> Unit)?) {
    if (onTap == null) return
    Box(
        modifier = Modifier
            .offset(canvasW * rect.left, canvasH * rect.top)
            .requiredSize(canvasW * rect.width, canvasH * rect.height)
            .semantics { contentDescription = action }
            .clickable(onClickLabel = action, onClick = onTap),
    )
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun RoomSceneKitchenPreview() {
    FinneyTheme {
        RoomScene(spot = RoomSpot.KITCHEN, modifier = Modifier.size(360.dp, 740.dp))
    }
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun RoomSceneBathPreview() {
    FinneyTheme {
        RoomScene(spot = RoomSpot.BATH, modifier = Modifier.size(360.dp, 740.dp))
    }
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun RoomScenePlayPreview() {
    FinneyTheme {
        RoomScene(spot = RoomSpot.PLAY, modifier = Modifier.size(360.dp, 740.dp))
    }
}

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun RoomSceneLivingPreview() {
    FinneyTheme {
        RoomScene(spot = RoomSpot.LIVING, modifier = Modifier.size(412.dp, 892.dp))
    }
}

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun RoomSceneLampOffPreview() {
    FinneyTheme {
        RoomScene(spot = RoomSpot.LIVING, lampOn = false, modifier = Modifier.size(412.dp, 892.dp))
    }
}
