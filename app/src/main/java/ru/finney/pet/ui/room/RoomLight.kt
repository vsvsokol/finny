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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
    val place: RelRect,
    val footing: Footing?,
    val solid: Solid?,
    val cuts: List<GraphicsLayer>,
    val contact: GraphicsLayer,
    val visibility: () -> Float,
)

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
    /** Где торшер, окно и шов пола: у каждого интерьера свои. */
    val interior: RoomInterior = Interiors.Default,
) {
    val occluders = mutableStateListOf<Occluder>()

    private val lampPoint = interior.lamp.point()

    val lampLevel: Float get() = lamp.value * breath.value

    /**
     * Кисти источников, которые не двигаются. Кисть — это шейдер: пересоздавать
     * его на каждый кадр для каждого предмета незачем, от кадра к кадру у торшера
     * меняется только яркость, а она идёт через прозрачность.
     */
    private val brushes = HashMap<Pair<LightKind, Size>, Brush>()

    fun brush(glow: Glow, canvas: Size): Brush {
        fun make() = Brush.radialGradient(
            0f to glow.color,
            glow.core to glow.color.copy(alpha = 0.5f),
            1f to Color.Transparent,
            center = Offset(canvas.width * glow.x, canvas.height * glow.y),
            radius = canvas.width * glow.reach,
        )
        return if (glow.kind == LightKind.UFO) make() else brushes.getOrPut(glow.kind to canvas, ::make)
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

    fun glows(): List<Glow> = buildList {
        if (ceiling) {
            add(
                Glow(
                    LightKind.LAMP, Lighting.CEILING_X, Lighting.CEILING_Y, LampWarm,
                    reach = Lighting.CEILING_REACH, core = 0.35f, strength = lampLevel,
                ),
            )
        } else {
            add(
                Glow(
                    LightKind.LAMP, interior.lamp.shade.x, interior.lamp.shade.y, LampWarm,
                    reach = Lighting.LAMP_REACH, core = 0.3f, strength = lampLevel,
                    shadow = ShadowSource(interior.lamp.pool.x, interior.lamp.pool.y, length = 0.75f),
                    caster = Caster.Point(lampPoint),
                ),
            )
        }
        if (window) {
            add(
                Glow(
                    LightKind.WINDOW, interior.window.glow.x, interior.window.glow.y, WindowCool,
                    reach = Lighting.WINDOW_REACH, core = 0.4f, strength = 1f,
                ),
            )
            add(
                Glow(
                    LightKind.MOON, Lighting.BEAM_X, Lighting.BEAM_Y, MoonBeam,
                    reach = Lighting.BEAM_REACH, core = 0.5f, strength = 0.8f,
                    // Тени от луны — от основания окна у стены: туда же, куда
                    // съезжает пятно на полу, к зрителю и влево.
                    shadow = ShadowSource(interior.window.moonFromX, interior.wall.floorTop, length = 0.5f),
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
                    shadow = ShadowSource(centre.x, interior.wall.floorTop, length = 0.55f),
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
    window: Boolean = true,
    ceiling: Boolean = false,
    interior: RoomInterior = Interiors.Default,
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
    val light = if (LocalAnimations.current) breath else steady
    return remember(lamp, light, window, ceiling, interior) { RoomLighting(lamp, light, ufo, window, ceiling, interior) }
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

    val lamp = lighting.interior.lamp
    val shade = Offset(w * lamp.shade.x, h * lamp.shade.y)
    val bloom = Brush.radialGradient(
        0f to LampBloom.copy(alpha = 0.55f),
        1f to Color.Transparent,
        center = shade,
        radius = w * Lighting.BLOOM_RADIUS,
    )

    val pool = Offset(w * lamp.pool.x, h * lamp.pool.y)
    val poolGlow = Brush.radialGradient(
        0f to LampPool,
        1f to Color.Transparent,
        center = pool,
        radius = w * Lighting.POOL_RADIUS,
    )

    val patch = moonPatch(w, h, lighting.interior)
    val patchBrush = Brush.verticalGradient(
        0f to MoonPatch,
        0.7f to MoonPatch.copy(alpha = 0.6f),
        // Дальний край растворяется: резкая граница посреди пола читалась
        // как ковёр, а не как свет.
        1f to Color.Transparent,
        startY = h * patchNear(lighting.interior),
        endY = h * Lighting.PATCH_FAR,
    )

    onDrawWithContent {
        drawContent()
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
                            drawCircle(poolGlow, w * Lighting.POOL_RADIUS, pool, alpha = on, blendMode = BlendMode.Plus)
                        }
                        LightKind.MOON -> drawPath(patch, patchBrush, blendMode = BlendMode.Plus)
                        else -> Unit
                    }
                }
                for (occluder in occluders) drawCut(occluder, glow, index, full)
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
            for (occluder in occluders) drawContact(occluder, full)
        }
        drawLayer(light)

        if (on > 0f && !lighting.ceiling) {
            drawCircle(bloom, w * Lighting.BLOOM_RADIUS, shade, alpha = on, blendMode = BlendMode.Screen)
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
private fun DrawScope.drawLightmap(lighting: RoomLighting, origin: Offset, canvas: Size, area: Size) {
    drawRect(lighting.ambient(), size = area)
    for (glow in lighting.glows()) drawGlow(lighting, glow, origin, canvas)
}

/**
 * Вырезает из света источника силуэт предмета, положенный на пол.
 *
 * Рисуется в слое источника, уже записанном в четверть разрешения, поэтому
 * силуэт уменьшается так же.
 */
private fun DrawScope.drawCut(occluder: Occluder, glow: Glow, index: Int, canvas: Size) {
    val shown = occluder.visibility().coerceIn(0f, 1f)
    if (shown <= 0f) return
    val solid = occluder.solid
    if (solid != null) {
        drawSolidCut(occluder, solid, glow.caster ?: return, index, canvas, shown)
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
    cut.renderEffect = BlurEffect(blur, blur)

    // Границы тени — квадрат питомца с запасом на прыжок, положенный на пол.
    val padX = box.width * PET_PAD
    val padY = box.height * PET_PAD
    val laid = listOf(
        Offset(-padX, -padY), Offset(box.width + padX, -padY),
        Offset(-padX, box.height + padY), Offset(box.width + padX, box.height + padY),
    ).map { floor.map(it) + topLeft }
    val bounds = Rect(laid.minOf { it.x }, laid.minOf { it.y }, laid.maxOf { it.x }, laid.maxOf { it.y })

    drawFitted(cut, bounds, blur) {
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
) {
    val shadow = Path()
    for (part in solid.parts) {
        val fallen = part.mapNotNull { caster.toFloor(it) }
        if (fallen.size < 3) continue
        shadow.addHull(fallen.map { RoomSpace.project(it) }, canvas)
    }

    val cut = occluder.cuts[index]
    cut.blendMode = BlendMode.DstOut
    cut.alpha = SHADOW_DEPTH * shown
    val blur = canvas.height * SOLID_BLUR / LIGHT_SCALE
    cut.renderEffect = BlurEffect(blur, blur)
    drawFitted(cut, shadow.getBounds(), blur) { drawPath(shadow, Color.Black) }
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
private fun DrawScope.drawContact(occluder: Occluder, canvas: Size) {
    val shown = occluder.visibility().coerceIn(0f, 1f)
    if (shown <= 0f) return

    val solid = occluder.solid
    if (solid != null) {
        val footprint = Path()
        footprint.addHull(solid.parts.flatten().map { RoomSpace.project(it.copy(y = 0f)) }, canvas)
        val contact = occluder.contact
        contact.alpha = shown
        val blur = canvas.height * SOLID_BLUR * 2f / LIGHT_SCALE
        contact.renderEffect = BlurEffect(blur, blur)
        drawFitted(contact, footprint.getBounds(), blur) {
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
    val body = rememberGraphicsLayer()
    val lit = rememberGraphicsLayer()
    val light = rememberGraphicsLayer()

    if (footing != null || solid != null) {
        val cuts = List(MAX_SHADOWS) { rememberGraphicsLayer() }
        val contact = rememberGraphicsLayer()
        DisposableEffect(lighting, place, footing, solid) {
            val occluder = Occluder(body, place, footing, solid, cuts, contact, visibility)
            lighting.occluders += occluder
            onDispose { lighting.occluders -= occluder }
        }
    }

    Box(
        modifier = modifier.drawWithContent {
            body.record { this@drawWithContent.drawContent() }

            val canvas = Size(size.width / place.width, size.height / place.height)
            val origin = Offset(canvas.width * place.left, canvas.height * place.top)
            val margin = IntOffset((size.width * pad).toInt(), (size.height * padTop).toInt())
            val below = (size.height * pad).toInt()
            val padded = IntSize(size.width.toInt() + margin.x * 2, size.height.toInt() + margin.y + below)

            light.blendMode = BlendMode.Modulate
            light.stretch()
            light.record(padded.shrunk()) {
                scale(1f / LIGHT_SCALE, pivot = Offset.Zero) {
                    drawLightmap(
                        lighting,
                        origin = origin - Offset(margin.x.toFloat(), margin.y.toFloat()),
                        canvas = canvas,
                        area = Size(padded.width.toFloat(), padded.height.toFloat()),
                    )
                }
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
    block: DrawScope.() -> Unit,
) {
    // Размытие растекается за край фигуры, поэтому запас в три радиуса.
    val margin = blur * 3f
    val left = (bounds.left / LIGHT_SCALE - margin).coerceAtLeast(0f)
    val top = (bounds.top / LIGHT_SCALE - margin).coerceAtLeast(0f)
    val right = (bounds.right / LIGHT_SCALE + margin).coerceAtMost(size.width)
    val bottom = (bounds.bottom / LIGHT_SCALE + margin).coerceAtMost(size.height)
    if (right - left < 1f || bottom - top < 1f) return

    layer.topLeft = IntOffset(left.toInt(), top.toInt())
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

/** Ближний к стене край пятна на полу: не выше шва — на плинтус оно не ложится. */
private fun patchNear(interior: RoomInterior) = maxOf(Lighting.PATCH_NEAR, interior.wall.floorTop)

/**
 * Лунный свет на полу: проекция стёкол окна — двух по сторонам перекладины
 * или одного, если перекладины нет.
 *
 * Края пятна идут вдоль досок, то есть к точке схода, иначе пятно лежало
 * бы не на полу, а висело перед ним плоской наклейкой.
 */
private fun moonPatch(w: Float, h: Float, interior: RoomInterior): Path {
    val window = interior.window
    val floorTop = interior.wall.floorTop

    // Где окажется точка с этой долей ширины у стены, если отойти от стены вглубь
    // до строки y: доски расходятся от оси пропорционально расстоянию до схода.
    fun x(atWall: Float, y: Float): Float {
        val spread = (y - RoomSpace.VANISH_Y) / (floorTop - RoomSpace.VANISH_Y)
        val drift = Lighting.PATCH_DRIFT *
            (y - Lighting.PATCH_NEAR) / (Lighting.PATCH_FAR - Lighting.PATCH_NEAR)
        return Room.ROOM_AXIS_X + (atWall - Room.ROOM_AXIS_X) * spread - drift
    }

    val near = patchNear(interior)
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
