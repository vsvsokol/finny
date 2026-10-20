package ru.finney.pet.ui.room

import androidx.compose.runtime.mutableFloatStateOf
import ru.finney.pet.ui.motion.LocalAnimations
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import java.util.WeakHashMap
import kotlin.math.roundToInt

// Свет в комнате рассчитан в коде: ночной версии обстановки у дизайнеров нет.
//
// Освещение — «карта света» в режиме Modulate: где карта белая, картинка как в
// экспорте, где тёмная, там ночь. Источники поэтому не рисуют свет поверх,
// а снимают тьму с карты, и обводка с узором обоев видны даже в тёмном углу.
//
// Карт две. Стены и пол освещаются своей: на ней есть пятна, которые лежат
// только на полу (лунный свет из окна, круг у торшера). Питомец и мебель
// освещаются каждый отдельно, светом источников там, где стоят. Одна общая
// карта поверх всего клала пятно с пола прямо на питомца.
//
// Тень — не тёмная заливка, а свет, который не дошёл до пола. У каждого
// источника свой слой света на полу, и из него вырезаются силуэты питомца
// и мебели, положенные на пол от этого источника. Поэтому тень ровно того
// цвета, какой был бы у пола без этого света, и видна только там, где свет
// был: питомец заслоняет лунное пятно, а на тёмном полу тени от луны нет.

/**
 * Ночь без единого источника. Сине-фиолетовая, в тон небу за окном (`#261F46`):
 * серая тьма читалась бы как выключенный экран, а не как вечер в комнате.
 */
private val NightAmbient = Color(0xFF4A4486)

/** Торшер подсвечивает всю комнату понемногу, а не только пятно вокруг себя. */
private val LampFill = Color(0xFF3A2C16)
private val LampWarm = Color(0xFFFFC27A)
private val LampPool = Color(0xFFB08850)

/** Ореол абажура поверх сцены: без него торшер темнее собственного света. */
private val LampBloom = Color(0xFFFFB35C)

private val WindowCool = Color(0xFF6E7FD8)
private val MoonPatch = Color(0xFF8C9CF0)
private val MoonBeam = Color(0xFF7080C8)

/** Яркость луча луны в полную силу окна. */
private const val MOON_STRENGTH = 0.8f

/** Купол НЛО зелёный (`#60A87C` в экспорте), и свет от него того же оттенка, только ярче. */
private val UfoGlow = Color(0xFF8CF0B4)

/** Торшер гаснет и загорается не щелчком: резкая смена света режет глаз. */
private const val LAMP_SWITCH_MS = 600

/**
 * Торшер чуть дышит — на 7% яркости за три секунды. Ровный свет в неподвижной
 * комнате делает её картинкой, а сильнее — уже мигание, и оно тревожит.
 */
private const val LAMP_BREATH = 0.93f
private const val LAMP_BREATH_MS = 3000

/**
 * Шаг яркости дыхания. Свет торшера читают все освещённые предметы и все тени на полу,
 * и плавное дыхание перерисовывало их заново на каждом кадре — даже когда в комнате
 * ничего не двигалось. На S23 (120 Гц) кадр комнаты шёл 14–17 мс при бюджете 8.3.
 * Ступенька в полпроцента на глаз не видна, а свет меняется около пяти раз в секунду.
 */
private const val LAMP_BREATH_STEP = 0.005f

/**
 * Сколько света забирает тень. Не весь: свет отражается от стен и пола,
 * и совсем без него силуэт на полу выглядел бы дырой.
 */
private const val SHADOW_DEPTH = 0.85f

/**
 * Размытие тени, доля высоты предмета: резкий край читался бы как вырезанная
 * бумага. При 0.02 в четвертном разрешении край шёл ступеньками, а тени
 * торшера и окна там, где ложатся друг на друга, выглядели двумя наклейками
 * одна на другой. Мягкий край их сливает.
 */
private const val SHADOW_BLUR = 0.05f

/**
 * След мебели на полу: сюда не доходит даже рассеянный свет. Затемняет карту
 * света, а не красит пол, поэтому пол под столом темнеет в своём же цвете.
 *
 * У питомца такого пятна нет: овал под ступнями читался как отдельный
 * тёмный круг поверх настоящих теней, а ступни и так держат тени торшера
 * и окна — обе начинаются от них.
 */
private const val CONTACT_ALPHA = 0.35f

/**
 * Во сколько раз свет и тени мельче экрана.
 *
 * Они плавные, поэтому считаются в четверть разрешения и растягиваются при
 * наложении, а растяжка заодно смягчает края. На полном размере размытие теней
 * и наложение света занимали у телефона 39 мс на кадр: 46 мс против 11 без них.
 */
private const val LIGHT_SCALE = 4f

/**
 * Запас вокруг питомца, доля его размера. В прыжке он выходит за свой квадрат,
 * а освещение рисуется в отдельном слое, который обрезает всё, что за краем.
 *
 * Прыжок из PetAnimation: подъём на 38 dp и растяжение на 5.5% — до 19%
 * квадрата. Голая макушка стоит на 0.12, а шляпа уже в покое торчит над
 * квадратом (пиратская и колдовская — до −0.02), и на пике прыжка её верх
 * уходит к −0.21. При 10% запаса шляпу срезало по прямой. 25% — с запасом.
 * Мебели запас не нужен: она не двигается, и слой ровно по ней — каждый
 * лишний процент тут занимает видеопамять в квадрате.
 */
internal const val PET_PAD = 0.25f

/**
 * Запас над питомцем — больше, чем по бокам и снизу: шляпы крупнее квадрата
 * (`HAT_SCALE` в PetAccessories.kt), и с высокой шляпой на пике прыжка верх
 * уходил за 0.25 — шляпу и макушку срезало по прямой. Вниз и вбок питомец
 * почти не выходит, там хватает [PET_PAD].
 */
internal const val PET_PAD_TOP = 0.6f

/** Сколько источников дают тень одновременно: торшер, луна, НЛО. */
private const val MAX_SHADOWS = 3

/**
 * Как светит, в долях холста 1440×2400.
 *
 * Где абажур, круг света у подставки и окно — у каждого интерьера своё, это
 * в [RoomInterior]. Здесь то, что от обстановки не зависит. Пятно лунного света
 * на полу замера не имеет: его нет в экспортах, и оно положено так, чтобы
 * питомец в зале стоял в нём.
 */
private object Lighting {
    /**
     * Докуда достаёт свет торшера, доля ширины холста.
     *
     * Ступни питомца в зале — в 0.99 ширины от шара. При 0.9 свет до них не
     * доходил, и тени от торшера не было видно вовсе: тень — это свет,
     * вырезанный из пола, а вырезать было нечего. Оставалась одна тень, от
     * окна, и падала она влево, будто торшера в комнате нет.
     */
    const val LAMP_REACH = 1.5f
    const val BLOOM_RADIUS = 0.16f

    /** Лампа под потолком на кухне: над серединой стола, выше кадра. */
    const val CEILING_X = 0.44f
    const val CEILING_Y = -0.02f
    const val CEILING_REACH = 1.2f

    /** Круг света у подставки, сплющенный перспективой пола. От него же падают тени торшера. */
    const val POOL_RADIUS = 0.24f
    const val POOL_SQUASH = 0.22f

    /** Свет из окна растекается по стене чуть ниже проёма. */
    const val WINDOW_REACH = 0.50f

    /**
     * Луч из окна к пятну на полу. Питомец стоит в нём. Без луча лицо питомца
     * было на 40% яркости, а по лицу ребёнок читает его настроение.
     */
    const val BEAM_X = 0.50f
    const val BEAM_Y = 0.45f
    const val BEAM_REACH = 0.45f

    /**
     * Пятно на полу: от ближнего к стене края до дальнего. Ступни питомца
     * в зале на 0.644 — пятно лежит и позади, и впереди них, иначе тени
     * было бы не на чем лечь. Где пол начинается ниже 0.50 (плинтус),
     * ближний край — по шву: на плинтусе пятну не место.
     */
    const val PATCH_NEAR = 0.50f
    const val PATCH_FAR = 0.84f

    /**
     * Насколько пятно уходит влево к дальнему краю. Луна справа вверху,
     * иначе пятно вышло бы за правый край кадра, где его никто не увидит.
     */
    const val PATCH_DRIFT = 0.20f

    const val UFO_REACH = 0.7f
    const val UFO_BLOOM = 0.10f

    /**
     * Где НЛО в глубину: за стеной, чуть дальше окна. Свет от него входит
     * через проём, и тень мебели от него короче, чем от торшера в комнате.
     */
    const val UFO_DEPTH = 1.3f

    /**
     * Куда уходят лучи луны на единицу высоты. Сняты с пятна на полу: нижняя
     * кромка стекла ложится на ближний край пятна, верхняя — на дальний.
     */
    val MoonRay = Point3(x = -0.168f, y = -1f, z = -1.55f)
}

/** Абажур в объёме: над кругом света на полу, на высоте абажура на холсте. */
private fun LampSkin.point(): Point3 = RoomSpace.lift(
    Offset(pool.x, shade.y),
    height = (pool.y - shade.y) / RoomSpace.scaleAt(pool.y),
)

/** Размытие тени мебели, доля высоты холста. У мебели край тени резче, чем у питомца: она ниже. */
private const val SOLID_BLUR = 0.006f

/** Какой это свет: от этого зависит, что он кладёт на пол, кроме себя. */
internal enum class LightKind { LAMP, WINDOW, MOON, UFO }

/** Кисть источника: какой свет, откуда и на каком холсте. */
private data class BrushKey(val kind: LightKind, val x: Float, val y: Float, val canvas: Size)

/**
 * Откуда падают тени от источника и какой они длины.
 *
 * Точка на полу, а не сам источник: торшер высокий, и от шара тень считалась бы
 * так, будто он висит под потолком. [length] — длина тени в долях высоты
 * предмета.
 */
@Immutable
internal data class ShadowSource(val x: Float, val y: Float, val length: Float)

/**
 * Источник света — круг, гаснущий от середины к краю. Всё в долях холста.
 *
 * @param reach радиус, доля ширины холста.
 * @param core где яркость падает вдвое, доля радиуса: мягкий свет или пятно.
 * @param shadow откуда падает тень питомца; null — питомец тени не отбрасывает.
 * @param caster тот же источник в объёме — для тени мебели.
 */
@Immutable
internal data class Glow(
    val kind: LightKind,
    val x: Float,
    val y: Float,
    val color: Color,
    val reach: Float,
    val core: Float,
    val strength: Float,
    val shadow: ShadowSource? = null,
    val caster: Caster? = null,
)

/**
 * Как предмет стоит на полу: где у него ступни и какой ширины след под ним.
 * Всё в долях его собственного прямоугольника.
 *
 * Так стоит питомец: столбиком, и его тень — силуэт, положенный на пол.
 * Мебель стоит иначе, у неё модель в объёме ([Solid]).
 */
@Immutable
internal data class Footing(
    val feetX: Float,
    val feetY: Float,
)

/**
 * Что заслоняет свет на полу: питомец или мебель.
 *
 * У питомца тень — его силуэт: пол вырезает его прямо из [body], отрисовки,
 * записанной в [LitBody], поэтому тень повторяет позу. У мебели тень строится
 * по модели [solid] лучами от источника.
 *
 * [cuts] — по слою на источник, [contact] — затемнение под предметом.
 * [visibility] — насколько предмет сейчас виден: тень появляется и гаснет
 * вместе с ним.
 */
internal class Occluder(
    val body: GraphicsLayer,
    private val placeOf: () -> RelRect,
    val footing: Footing?,
    val solid: Solid?,
    val cuts: List<GraphicsLayer>,
    val contact: GraphicsLayer,
    val visibility: () -> Float,
) {
    /** Где предмет сейчас. Читается на отрисовке: идущий питомец не пересобирает комнату. */
    val place: RelRect get() = placeOf()
}

/**
 * Свет в комнате в данный момент: торшер с его дыханием, НЛО за окном
 * и всё, что стоит на полу и заслоняет свет.
 *
 * Значения читаются только на отрисовке: переход света и пролёт НЛО
 * перерисовывают комнату, но не пересобирают её.
 */
@Stable
internal class RoomLighting(
    private val lamp: State<Float>,
    private val breath: State<Float>,
    /** Середина НЛО в долях холста и насколько оно сейчас в проёме, 0..1. */
    private val ufo: () -> Pair<Offset, Float>?,
    /** Есть ли в комнате окно. Нет — нет ни света из него, ни луны, ни НЛО. */
    private val window: Boolean = true,
    /**
     * Свет лампы не от торшера, а сверху, из-за кадра: ни шара с ореолом,
     * ни круга на полу у подставки, ни теней от него.
     */
    val ceiling: Boolean = false,
    /**
     * Где торшер, окно и шов пола: у каждого интерьера свои. Со сменой — пока часть
     * меняется, свет идёт за ней (см. [lit]).
     */
    private val walls: PartSwap<WallSkin>,
    private val windows: PartSwap<WindowSkin>,
    private val lamps: PartSwap<LampSkin>,
) {
    val occluders = mutableStateListOf<Occluder>()

    /**
     * С чем записан каждый слой тени. Свет пола перерисовывается целиком, стоит одному
     * предмету сдвинуться, и раньше заново записывал и размывал тени всех: пока ребёнок
     * тащил игрушку, кадр шёл 17–21 мс. Теперь слой пишется, только если его тень
     * изменилась; поза питомца в тень попадает и так — слой ссылается на его отрисовку.
     */
    val recorded = WeakHashMap<GraphicsLayer, Any>()

    /** Шов пола, окно и торшер, которые видны сейчас. */
    val wallSkin: WallSkin get() = walls.lit()
    val windowSkin: WindowSkin get() = windows.lit().first
    val lampSkin: LampSkin get() = lamps.lit().first

    val lampLevel: Float get() = lamp.value * breath.value * (if (ceiling) 1f else lamps.lit().second)

    /**
     * Кисти источников, которые не двигаются. Кисть — это шейдер: пересоздавать
     * его на каждый кадр для каждого предмета незачем, от кадра к кадру у торшера
     * меняется только яркость, а она идёт через прозрачность.
     */
    private val brushes = HashMap<BrushKey, Brush>()

    fun brush(glow: Glow, canvas: Size): Brush {
        fun make() = Brush.radialGradient(
            0f to glow.color,
            glow.core to glow.color.copy(alpha = 0.5f),
            1f to Color.Transparent,
            center = Offset(canvas.width * glow.x, canvas.height * glow.y),
            radius = canvas.width * glow.reach,
        )
        // Место в ключе: посреди смены торшер и окно светят то со старого места, то с нового.
        return if (glow.kind == LightKind.UFO) make() else brushes.getOrPut(BrushKey(glow.kind, glow.x, glow.y, canvas), ::make)
    }

    /** Ночь плюс то, чем торшер заливает комнату целиком. */
    fun ambient(): Color {
        val on = lampLevel
        return Color(
            red = NightAmbient.red + LampFill.red * on,
            green = NightAmbient.green + LampFill.green * on,
            blue = NightAmbient.blue + LampFill.blue * on,
        )
    }

    fun ufoNow(): Pair<Offset, Float>? = if (window) ufo() else null

    /** С чем собраны [glows]: пока свет тот же, список один и тот же, без новых объектов на кадр. */
    private var glowsKey: GlowsKey? = null
    private var glowsNow: List<Glow> = emptyList()

    /**
     * Источники света сейчас. Состояния читаются на каждом вызове, поэтому отрисовка
     * по-прежнему подписана на них; собирается список заново, только когда свет сменился.
     */
    fun glows(): List<Glow> {
        val key = GlowsKey(lampLevel, if (ceiling) null else lampSkin, windows.lit(), wallSkin.floorTop, ufoNow())
        if (key != glowsKey) {
            glowsNow = buildGlows()
            glowsKey = key
        }
        return glowsNow
    }

    private data class GlowsKey(
        val lampLevel: Float,
        val lamp: LampSkin?,
        val window: Pair<WindowSkin, Float>,
        val floorTop: Float,
        val ufo: Pair<Offset, Float>?,
    )

    private fun buildGlows(): List<Glow> = buildList {
        if (ceiling) {
            add(
                Glow(
                    LightKind.LAMP, Lighting.CEILING_X, Lighting.CEILING_Y, LampWarm,
                    reach = Lighting.CEILING_REACH, core = 0.35f, strength = lampLevel,
                ),
            )
        } else {
            val lamp = lampSkin
            add(
                Glow(
                    LightKind.LAMP, lamp.shade.x, lamp.shade.y, LampWarm,
                    reach = Lighting.LAMP_REACH, core = 0.3f, strength = lampLevel,
                    shadow = ShadowSource(lamp.pool.x, lamp.pool.y, length = 0.75f),
                    caster = Caster.Point(lamp.point()),
                ),
            )
        }
        val (glass, lit) = windows.lit()
        if (window && lit > 0f) {
            add(
                Glow(
                    LightKind.WINDOW, glass.glow.x, glass.glow.y, WindowCool,
                    reach = Lighting.WINDOW_REACH, core = 0.4f, strength = lit,
                ),
            )
            add(
                Glow(
                    LightKind.MOON, Lighting.BEAM_X, Lighting.BEAM_Y, MoonBeam,
                    reach = Lighting.BEAM_REACH, core = 0.5f, strength = MOON_STRENGTH * lit,
                    // Тени от луны — от основания окна у стены: туда же, куда
                    // съезжает пятно на полу, к зрителю и влево.
                    shadow = ShadowSource(glass.moonFromX, wallSkin.floorTop, length = 0.5f),
                    caster = Caster.Parallel(Lighting.MoonRay),
                ),
            )
        }
        ufoNow()?.let { (centre, presence) ->
            add(
                Glow(
                    LightKind.UFO, centre.x, centre.y, UfoGlow,
                    reach = Lighting.UFO_REACH, core = 0.35f, strength = presence,
                    // НЛО за стеной, свет приходит через окно, поэтому и тень
                    // считается от стены под ним, а не от неба.
                    shadow = ShadowSource(centre.x, wallSkin.floorTop, length = 0.55f),
                    caster = Caster.Point(RoomSpace.behind(centre, Lighting.UFO_DEPTH)),
                ),
            )
        }
    }
}

@Composable
internal fun rememberRoomLighting(
    lampOn: Boolean,
    ufo: () -> Pair<Offset, Float>?,
    walls: PartSwap<WallSkin>,
    windows: PartSwap<WindowSkin>,
    lamps: PartSwap<LampSkin>,
    window: Boolean = true,
    ceiling: Boolean = false,
): RoomLighting {
    val lamp = animateFloatAsState(
        targetValue = if (lampOn) 1f else 0f,
        animationSpec = tween(LAMP_SWITCH_MS),
        label = "lamp",
    )
    val breath = rememberInfiniteTransition(label = "lampBreath").animateFloat(
        initialValue = 1f,
        targetValue = LAMP_BREATH,
        animationSpec = infiniteRepeatable(
            animation = tween(LAMP_BREATH_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    // Без анимаций дыхание застыло бы на пригашенной лампе — свет ровный.
    val steady = remember { mutableFloatStateOf(1f) }
    val smooth = if (LocalAnimations.current) breath else steady
    val light = remember(smooth) {
        derivedStateOf { (smooth.value / LAMP_BREATH_STEP).roundToInt() * LAMP_BREATH_STEP }
    }
    // Летун меняется с окном, а свет остаётся тем же: он берёт летуна свежим на каждой отрисовке.
    val currentUfo by rememberUpdatedState(ufo)
    return remember(lamp, light, window, ceiling, walls, windows, lamps) {
        RoomLighting(lamp, light, { currentUfo() }, window, ceiling, walls, windows, lamps)
    }
}

/**
 * Освещение стен и пола: всего, что стоит за питомцем и мебелью.
 *
 * Здесь пятна, которым место только на полу, тени и ореолы торшера и НЛО.
 * Ореолы рисуются поверх, иначе источник был бы темнее собственного света.
 */
internal fun Modifier.roomLight(lighting: RoomLighting): Modifier = drawWithCache {
    val full = size
    val w = full.width
    val h = full.height
    val small = full.shrunk()

    val light = obtainGraphicsLayer().apply {
        blendMode = BlendMode.Modulate
        stretch()
    }
    // Свет каждого источника с тенями — в своём слое: тени вырезаются только
    // из него и не трогают свет остальных. Торшер заслонён — луна светит.
    val casters = List(MAX_SHADOWS) { obtainGraphicsLayer().apply { blendMode = BlendMode.Plus } }

    // Посреди смены интерьера торшер, окно и шов пола бывают ещё старые: кисти берутся
    // по тем, что видны, и пересобираются, только когда часть сменилась, а не на кадр.
    var lampArt: LampArt? = null
    var moonArt: MoonArt? = null

    onDrawWithContent {
        drawContent()
        val lampSkin = lighting.lampSkin
        val lamp = lampArt?.takeIf { it.skin == lampSkin } ?: LampArt(lampSkin, w, h).also { lampArt = it }
        val windowSkin = lighting.windowSkin
        val floorTop = lighting.wallSkin.floorTop
        val moon = moonArt?.takeIf { it.window == windowSkin && it.floorTop == floorTop }
            ?: MoonArt(windowSkin, floorTop, w, h).also { moonArt = it }
        val pool = lamp.pool

        val on = lighting.lampLevel
        val glows = lighting.glows()
        val occluders = lighting.occluders.toList()

        var used = 0
        for (glow in glows) {
            if (glow.shadow == null || glow.strength <= 0f || used == MAX_SHADOWS) continue
            val index = used++
            casters[index].record(small) {
                scale(1f / LIGHT_SCALE, pivot = Offset.Zero) {
                    drawGlow(lighting, glow, origin = Offset.Zero, canvas = full)
                    when (glow.kind) {
                        LightKind.LAMP -> scale(scaleX = 1f, scaleY = Lighting.POOL_SQUASH, pivot = pool) {
                            drawCircle(lamp.poolGlow, w * Lighting.POOL_RADIUS, pool, alpha = on, blendMode = BlendMode.Plus)
                        }
                        // Пятно гаснет и разгорается вместе со светом окна.
                        LightKind.MOON -> drawPath(moon.path, moon.brush, alpha = (glow.strength / MOON_STRENGTH).coerceAtMost(1f), blendMode = BlendMode.Plus)
                        else -> Unit
                    }
                }
                for (occluder in occluders) drawCut(occluder, glow, index, full, lighting.recorded)
            }
        }

        light.record(small) {
            scale(1f / LIGHT_SCALE, pivot = Offset.Zero) {
                drawRect(lighting.ambient(), size = full)
                for (glow in glows) {
                    if (glow.shadow == null) drawGlow(lighting, glow, origin = Offset.Zero, canvas = full)
                }
            }
            for (i in 0 until used) drawLayer(casters[i])
            for (occluder in occluders) drawContact(occluder, full, lighting.recorded)
        }
        drawLayer(light)

        if (on > 0f && !lighting.ceiling) {
            drawCircle(lamp.bloom, w * Lighting.BLOOM_RADIUS, lamp.shade, alpha = on, blendMode = BlendMode.Screen)
        }
        lighting.ufoNow()?.let { (centre, presence) ->
            val at = Offset(w * centre.x, h * centre.y)
            val radius = w * Lighting.UFO_BLOOM
            drawCircle(
                Brush.radialGradient(
                    0f to UfoGlow.copy(alpha = 0.6f),
                    1f to Color.Transparent,
                    center = at,
                    radius = radius,
                ),
                radius, at, alpha = presence, blendMode = BlendMode.Screen,
            )
        }
    }
}

/**
 * Один источник: круг его света, прибавленный к тому, что уже есть.
 * Кисть в координатах холста, поэтому предмет сдвигает холст, а не кисть.
 */
private fun DrawScope.drawGlow(lighting: RoomLighting, glow: Glow, origin: Offset, canvas: Size) {
    if (glow.strength <= 0f) return
    translate(-origin.x, -origin.y) {
        drawCircle(
            brush = lighting.brush(glow, canvas),
            radius = canvas.width * glow.reach,
            center = Offset(canvas.width * glow.x, canvas.height * glow.y),
            alpha = glow.strength.coerceAtMost(1f),
            blendMode = BlendMode.Plus,
        )
    }
}

/**
 * Свет источников без пятен на полу и без теней: ночь плюс каждый источник
 * в том месте, где он светит. [origin] — где на холсте лежит левый верхний
 * угол того, что сейчас освещается, [area] — его размер.
 */
private fun DrawScope.drawLightmap(lighting: RoomLighting, glows: List<Glow>, ambient: Color, origin: Offset, canvas: Size, area: Size) {
    drawRect(ambient, size = area)
    for (glow in glows) drawGlow(lighting, glow, origin, canvas)
}

/**
 * Вырезает из света источника силуэт предмета, положенный на пол.
 *
 * Рисуется в слое источника, уже записанном в четверть разрешения, поэтому
 * силуэт уменьшается так же.
 */
private fun DrawScope.drawCut(occluder: Occluder, glow: Glow, index: Int, canvas: Size, recorded: MutableMap<GraphicsLayer, Any>) {
    val shown = occluder.visibility().coerceIn(0f, 1f)
    if (shown <= 0f) return
    val solid = occluder.solid
    if (solid != null) {
        drawSolidCut(occluder, solid, glow.caster ?: return, index, canvas, shown, recorded)
        return
    }
    val source = glow.shadow ?: return
    val footing = occluder.footing ?: return
    val place = occluder.place
    val topLeft = Offset(canvas.width * place.left, canvas.height * place.top)
    val box = Size(canvas.width * place.width, canvas.height * place.height)

    val feet = Offset(box.width * footing.feetX, box.height * footing.feetY)
    val from = Offset(canvas.width * source.x, canvas.height * source.y) - topLeft
    val away = feet - from
    val distance = away.getDistance()
    if (distance < 1f) return

    // Силуэт кладётся на пол: каждая точка на высоте t над ступнями уходит
    // от источника на t × v. Низ остаётся у ступней, макушка — дальше всего.
    val v = away / distance * source.length
    val floor = Matrix().apply {
        values[Matrix.SkewX] = -v.x
        values[Matrix.ScaleY] = -v.y
        values[Matrix.TranslateX] = feet.y * v.x
        values[Matrix.TranslateY] = feet.y * (1f + v.y)
    }

    val cut = occluder.cuts[index]
    cut.blendMode = BlendMode.DstOut
    cut.alpha = SHADOW_DEPTH * shown
    // Размытие считается в мелком слое, до растяжки, поэтому и радиус мельче.
    val blur = box.height * SHADOW_BLUR / LIGHT_SCALE

    // Границы тени — квадрат питомца с запасом на прыжок, положенный на пол.
    val padX = box.width * PET_PAD
    val padY = box.height * PET_PAD
    val laid = listOf(
        Offset(-padX, -padY), Offset(box.width + padX, -padY),
        Offset(-padX, box.height + padY), Offset(box.width + padX, box.height + padY),
    ).map { floor.map(it) + topLeft }
    val bounds = Rect(laid.minOf { it.x }, laid.minOf { it.y }, laid.maxOf { it.x }, laid.maxOf { it.y })

    drawFitted(cut, bounds, blur, key = listOf(place, footing, source, canvas, size), recorded) {
        translate(topLeft.x, topLeft.y) {
            withTransform({ transform(floor) }) { drawLayer(occluder.body) }
        }
    }
}

/**
 * Тень мебели от одного источника: каждая точка модели падает на пол по своему
 * лучу, и из света вырезается выпуклая оболочка упавших точек — у стола это
 * столешница со скатертью и отдельно каждая ножка.
 */
private fun DrawScope.drawSolidCut(
    occluder: Occluder,
    solid: Solid,
    caster: Caster,
    index: Int,
    canvas: Size,
    shown: Float,
    recorded: MutableMap<GraphicsLayer, Any>,
) {
    val cut = occluder.cuts[index]
    cut.blendMode = BlendMode.DstOut
    cut.alpha = SHADOW_DEPTH * shown
    val blur = canvas.height * SOLID_BLUR / LIGHT_SCALE
    val key = listOf(solid, caster, canvas, size)
    if (recorded[cut] == key) {
        drawLayer(cut)
        return
    }
    val shadow = Path()
    for (part in solid.parts) {
        val fallen = part.mapNotNull { caster.toFloor(it) }
        if (fallen.size < 3) continue
        shadow.addHull(fallen.map { RoomSpace.project(it) }, canvas)
    }

    drawFitted(cut, shadow.getBounds(), blur, key, recorded) { drawPath(shadow, Color.Black) }
}

/** Добавляет к пути выпуклую оболочку точек, заданных в долях холста. */
private fun Path.addHull(points: List<Offset>, canvas: Size) {
    val hull = convexHull(points)
    if (hull.size < 3) return
    hull.forEachIndexed { i, p ->
        val x = canvas.width * p.x
        val y = canvas.height * p.y
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Затемнение карты света под мебелью: пол темнеет, но остаётся своего цвета. */
private fun DrawScope.drawContact(occluder: Occluder, canvas: Size, recorded: MutableMap<GraphicsLayer, Any>) {
    val shown = occluder.visibility().coerceIn(0f, 1f)
    if (shown <= 0f) return

    val solid = occluder.solid
    if (solid != null) {
        val contact = occluder.contact
        contact.alpha = shown
        val blur = canvas.height * SOLID_BLUR * 2f / LIGHT_SCALE
        val key = listOf(solid, canvas, size)
        if (recorded[contact] == key) {
            drawLayer(contact)
            return
        }
        val footprint = Path()
        footprint.addHull(solid.parts.flatten().map { RoomSpace.project(it.copy(y = 0f)) }, canvas)
        drawFitted(contact, footprint.getBounds(), blur, key, recorded) {
            drawPath(footprint, Color.Black.copy(alpha = CONTACT_ALPHA))
        }
        return
    }

}

/**
 * Питомец или предмет в свете комнаты.
 *
 * Освещается светом источников в том месте, где стоит, — без пятен с пола.
 * Если задан [footing] или [solid], он заслоняет свет на полу: его тень
 * вырезается из света каждого источника (см. [roomLight]).
 *
 * @param place прямоугольник [modifier] в долях холста: по нему освещение
 *   знает, какая часть комнаты сейчас под ним.
 * @param visibility насколько предмет виден в переходе между комнатами.
 *   Лямбдой: читается на отрисовке тени и не пересобирает предмет.
 * @param pad на сколько предмет может выйти за свой прямоугольник, доля размера.
 * @param padTop то же вверх; по умолчанию как [pad].
 */
@Composable
internal fun LitBody(
    lighting: RoomLighting,
    place: RelRect,
    footing: Footing?,
    modifier: Modifier = Modifier,
    solid: Solid? = null,
    visibility: () -> Float = { 1f },
    pad: Float = 0f,
    padTop: Float = pad,
    content: @Composable BoxScope.() -> Unit,
) {
    val current = rememberUpdatedState(place)
    LitBody(lighting, { current.value }, footing, modifier, solid, visibility, pad, padTop, content)
}

/**
 * То же для предмета, который движется: [place] читается на раскладке и отрисовке,
 * поэтому шаги питомца перерисовывают свет и тень, но не пересобирают комнату.
 */
@Composable
internal fun LitBody(
    lighting: RoomLighting,
    place: () -> RelRect,
    footing: Footing?,
    modifier: Modifier = Modifier,
    solid: Solid? = null,
    visibility: () -> Float = { 1f },
    pad: Float = 0f,
    padTop: Float = pad,
    content: @Composable BoxScope.() -> Unit,
) {
    val placeOf = rememberUpdatedState(place)
    // Не состояние: ключ нужен только отрисовке и не должен её перезапускать.
    val lightKey = remember { LightmapHolder() }
    val body = rememberGraphicsLayer()
    val lit = rememberGraphicsLayer()
    val light = rememberGraphicsLayer()

    if (footing != null || solid != null) {
        val cuts = List(MAX_SHADOWS) { rememberGraphicsLayer() }
        val contact = rememberGraphicsLayer()
        DisposableEffect(lighting, footing, solid) {
            val occluder = Occluder(body, { placeOf.value() }, footing, solid, cuts, contact, visibility)
            lighting.occluders += occluder
            onDispose { lighting.occluders -= occluder }
        }
    }

    Box(
        modifier = modifier.drawWithContent {
            body.record { this@drawWithContent.drawContent() }

            val place = placeOf.value()
            val canvas = Size(size.width / place.width, size.height / place.height)
            val origin = Offset(canvas.width * place.left, canvas.height * place.top)
            val margin = IntOffset((size.width * pad).toInt(), (size.height * padTop).toInt())
            val below = (size.height * pad).toInt()
            val padded = IntSize(size.width.toInt() + margin.x * 2, size.height.toInt() + margin.y + below)

            // Карта света переписывается, только если свет или место сменились: у питомца
            // отрисовка меняется каждый кадр (дыхание, моргание), а свет на нём — редко.
            val glows = lighting.glows()
            val ambient = lighting.ambient()
            val from = origin - Offset(margin.x.toFloat(), margin.y.toFloat())
            val key = LightmapKey(glows, ambient, from, canvas, padded)
            if (lightKey.value != key) {
                light.blendMode = BlendMode.Modulate
                light.stretch()
                light.record(padded.shrunk()) {
                    scale(1f / LIGHT_SCALE, pivot = Offset.Zero) {
                        drawLightmap(
                            lighting,
                            glows,
                            ambient,
                            origin = from,
                            canvas = canvas,
                            area = Size(padded.width.toFloat(), padded.height.toFloat()),
                        )
                    }
                }
                lightKey.value = key
            }

            // Свет накладывается в своём слое поверх копии предмета, и слой
            // отдельный: иначе Modulate умножил бы на свет ещё и комнату за ним.
            lit.compositingStrategy = CompositingStrategy.Offscreen
            lit.topLeft = -margin
            lit.record(padded) {
                translate(margin.x.toFloat(), margin.y.toFloat()) { drawLayer(body) }
                drawLayer(light)
            }
            drawLayer(lit)
        },
        content = content,
    )
}

/**
 * Записывает тень в слой ровно по её границам [bounds] (в пикселях холста),
 * а не во весь пол: слоёв по одному на предмет и источник, и во весь пол
 * они занимали десятки мегабайт видеопамяти ради пятна в углу.
 *
 * Рисуется внутри слоя четвертного разрешения, поэтому и сам слой мельче вчетверо.
 */
private fun DrawScope.drawFitted(
    layer: GraphicsLayer,
    bounds: Rect,
    blur: Float,
    key: Any,
    recorded: MutableMap<GraphicsLayer, Any>,
    block: DrawScope.() -> Unit,
) {
    // Тень не изменилась — слой уже записан и размыт, его остаётся только положить.
    if (recorded[layer] == key) {
        drawLayer(layer)
        return
    }
    // Размытие растекается за край фигуры, поэтому запас в три радиуса.
    val margin = blur * 3f
    val left = (bounds.left / LIGHT_SCALE - margin).coerceAtLeast(0f)
    val top = (bounds.top / LIGHT_SCALE - margin).coerceAtLeast(0f)
    val right = (bounds.right / LIGHT_SCALE + margin).coerceAtMost(size.width)
    val bottom = (bounds.bottom / LIGHT_SCALE + margin).coerceAtMost(size.height)
    if (right - left < 1f || bottom - top < 1f) return

    layer.topLeft = IntOffset(left.toInt(), top.toInt())
    layer.renderEffect = BlurEffect(blur, blur)
    recorded[layer] = key
    layer.record(IntSize(ceil(right - left).toInt() + 1, ceil(bottom - top).toInt() + 1)) {
        translate(-left.toInt().toFloat(), -top.toInt().toFloat()) {
            scale(1f / LIGHT_SCALE, pivot = Offset.Zero, block)
        }
    }
    drawLayer(layer)
}

/** Размер слоя в четверть разрешения: с запасом в пиксель, чтобы край не остался пустым. */
private fun Size.shrunk() = IntSize(ceil(width / LIGHT_SCALE).toInt() + 1, ceil(height / LIGHT_SCALE).toInt() + 1)

private fun IntSize.shrunk() = Size(width.toFloat(), height.toFloat()).shrunk()

/** Растягивает слой, записанный в [shrunk], обратно до полного размера. */
private fun GraphicsLayer.stretch() {
    pivotOffset = Offset.Zero
    scaleX = LIGHT_SCALE
    scaleY = LIGHT_SCALE
}

/** Ореол абажура и круг света у подставки торшера [skin] на холсте [w]×[h] пикселей. */
private class LampArt(val skin: LampSkin, w: Float, h: Float) {
    val shade = Offset(w * skin.shade.x, h * skin.shade.y)
    val bloom = Brush.radialGradient(
        0f to LampBloom.copy(alpha = 0.55f),
        1f to Color.Transparent,
        center = shade,
        radius = w * Lighting.BLOOM_RADIUS,
    )
    val pool = Offset(w * skin.pool.x, h * skin.pool.y)
    val poolGlow = Brush.radialGradient(
        0f to LampPool,
        1f to Color.Transparent,
        center = pool,
        radius = w * Lighting.POOL_RADIUS,
    )
}

/** Пятно луны на полу от окна [window] при шве пола [floorTop]. */
private class MoonArt(val window: WindowSkin, val floorTop: Float, w: Float, h: Float) {
    val path = moonPatch(w, h, window, floorTop)
    val brush = Brush.verticalGradient(
        0f to MoonPatch,
        0.7f to MoonPatch.copy(alpha = 0.6f),
        // Дальний край растворяется: резкая граница посреди пола читалась
        // как ковёр, а не как свет.
        1f to Color.Transparent,
        startY = h * patchNear(floorTop),
        endY = h * Lighting.PATCH_FAR,
    )
}

/** Ближний к стене край пятна на полу: не выше шва — на плинтус оно не ложится. */
private fun patchNear(floorTop: Float) = maxOf(Lighting.PATCH_NEAR, floorTop)

/**
 * Лунный свет на полу: проекция стёкол окна — двух по сторонам перекладины
 * или одного, если перекладины нет.
 *
 * Края пятна идут вдоль досок, то есть к точке схода, иначе пятно лежало
 * бы не на полу, а висело перед ним плоской наклейкой.
 */
private fun moonPatch(w: Float, h: Float, window: WindowSkin, floorTop: Float): Path {
    // Где окажется точка с этой долей ширины у стены, если отойти от стены вглубь
    // до строки y: доски расходятся от оси пропорционально расстоянию до схода.
    fun x(atWall: Float, y: Float): Float {
        val spread = (y - RoomSpace.VANISH_Y) / (floorTop - RoomSpace.VANISH_Y)
        val drift = Lighting.PATCH_DRIFT *
            (y - Lighting.PATCH_NEAR) / (Lighting.PATCH_FAR - Lighting.PATCH_NEAR)
        return Room.ROOM_AXIS_X + (atWall - Room.ROOM_AXIS_X) * spread - drift
    }

    val near = patchNear(floorTop)
    val far = Lighting.PATCH_FAR
    val panes = window.mullion?.let {
        listOf(window.glassLeft to it.start, it.endInclusive to window.glassRight)
    } ?: listOf(window.glassLeft to window.glassRight)
    return Path().apply {
        for ((left, right) in panes) {
            moveTo(w * x(left, near), h * near)
            lineTo(w * x(right, near), h * near)
            lineTo(w * x(right, far), h * far)
            lineTo(w * x(left, far), h * far)
            close()
        }
    }
}

/** С чем записана карта света предмета; см. [LitBody]. */
private data class LightmapKey(val glows: List<Glow>, val ambient: Color, val origin: Offset, val canvas: Size, val area: IntSize)

private class LightmapHolder {
    var value: LightmapKey? = null
}
