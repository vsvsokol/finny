package ru.finney.pet.ui.tasks.games

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.ui.components.Coin
import ru.finney.pet.ui.components.CloseButton
import ru.finney.pet.ui.components.FinneyIconButton
import ru.finney.pet.ui.components.MenuBackdrop
import ru.finney.pet.ui.components.menuBackdrop
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.components.fadingScroll
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.pet.PetAnimation
import ru.finney.pet.ui.pet.PetMood
import ru.finney.pet.ui.pet.PetPose
import ru.finney.pet.ui.pet.PetView
import ru.finney.pet.ui.pet.rememberPetAnimation
import ru.finney.pet.ui.pet.rememberPoseProvider
import ru.finney.pet.ui.theme.FinneyBlue
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.StrokeRegular

// Сцена мини-игры, как в концептах: игра идёт не на пустом экране, а в месте —
// на спокойном фоне с точками, в магазине, на поле, у лавки. Сверху деньги и крестик,
// поверх фона — сама игра, питомец и его реплики.

/**
 * Где идёт игра. Всё нарисовано кодом. [DOTS] — кремовый с точками, как у экранов-дел:
 * раньше здесь стояла комната с главного, и на плейтесте (28.09) она отвлекала от игры.
 */
enum class Backdrop {
    DOTS, DOTS_RAIN, SHOP, FIELD, SKY, SUNSET, STORE,

    // Фоны карточки перед игрой — свой у каждой игры, как в макете карточек (вариант A).
    // У «Конвейера» карточка на тех же точках, что игра, — отдельного фона нет.
    CARD_GREEN, CARD_LILAC, CARD_RAIN, CARD_LEMON, CARD_YELLOW, CARD_BLUE, CARD_PINK,
}

/**
 * Экран игры: фон, верхняя полоса и содержимое.
 * [money] — число в кошельке этой игры; null — кошелёк не показываем.
 * [moneyModifier] — на сам кошелёк: итог узнаёт по нему, куда лететь монетам награды.
 *
 * Внутри [SceneStage] фон рисует сцена, а экран только говорит, какой он: так
 * вступление, игра и итог стоят на одном фоне, и смена фона плавная. Без сцены
 * (превью) экран рисует фон сам.
 */
@Composable
internal fun GameScene(
    backdrop: Backdrop,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    money: Int? = null,
    moneyModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val stage = LocalStageBackdrop.current
    if (stage != null) SideEffect { stage.value = backdrop }
    Box(modifier = modifier.fillMaxSize()) {
        if (stage == null) BackdropLayer(backdrop)
        Box(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = SceneEdge, vertical = 8.dp)) {
            content()
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (money != null) MoneyPill(money, moneyModifier)
                Box(Modifier.weight(1f))
                CloseButton(onClose, description = "Закрыть игру")
            }
        }
    }
}

/**
 * Один фон на всё задание: вступление, игру и итог. Раньше каждый экран рисовал
 * фон заново, и при переходе фон начинал жить с начала, а лавка из полудня рывком
 * становилась закатом. Теперь фон живёт, пока открыто задание, и меняется плавно:
 * начинается дождь, над лавкой садится солнце.
 */
@Composable
internal fun SceneStage(initial: Backdrop, content: @Composable () -> Unit) {
    val backdrop = remember { mutableStateOf(initial) }
    Box(Modifier.fillMaxSize()) {
        BackdropLayer(backdrop.value)
        CompositionLocalProvider(LocalStageBackdrop provides backdrop, content = content)
    }
}

/** Какой фон просит экран — для [SceneStage]. null — сцены нет, экран рисует фон сам. */
private val LocalStageBackdrop = staticCompositionLocalOf<MutableState<Backdrop>?> { null }

/** Поле сцены по бокам. Кнопки и панели держатся в нём, а лента и полки выходят за него — см. [bleed]. */
internal val SceneEdge: Dp = 12.dp

/**
 * Растянуть на всю ширину экрана, за поле сцены. Для того, что по смыслу
 * уходит за край: лента конвейера, доска полки. С полем они обрывались
 * в 12 dp от края и выглядели недорисованными.
 */
internal fun Modifier.bleed(): Modifier = layout { measurable, constraints ->
    val extra = (SceneEdge * 2).roundToPx()
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** Высота видимой части [SceneBody] без кнопок: по ней содержимое решает, сколько места свободно. */
internal val LocalSceneViewport = compositionLocalOf { Dp.Unspecified }

/**
 * Питомец внизу [SceneBody] на всё свободное место: от [min] до [max]. Места меньше [min] —
 * заходит на то, что выше, не больше чем на [maxLift], а дальше прокрутка. [above] — высота
 * всего, что стоит выше, вместе с промежутками между этим. На плейтесте 20.10 питомец
 * в играх был мелким, хотя под ним пустовало место.
 */
@Composable
internal fun ColumnScope.PetInFreeSpace(
    above: Dp,
    min: Dp,
    max: Dp,
    maxLift: Dp = 32.dp,
    pet: @Composable (size: Dp, modifier: Modifier) -> Unit,
) {
    val viewport = LocalSceneViewport.current
    // Два промежутка колонки: до распорки и после неё.
    val room = if (viewport == Dp.Unspecified) min else viewport - above - 24.dp
    val size = (room + maxLift).coerceIn(min, max)
    val lift = (size - room).coerceIn(0.dp, maxLift)
    Spacer(Modifier.weight(1f))
    pet(
        size,
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val up = lift.roundToPx()
            layout(placeable.width, (placeable.height - up).coerceAtLeast(0)) { placeable.place(0, -up) }
        },
    )
}

/** Высота верхней полосы: содержимое сцены начинается ниже, чтобы не залезть под крестик. */
internal val HudHeight: Dp = 60.dp

/**
 * Содержимое сцены под верхней полосой. Середина прокручивается, если не влезла,
 * а [bottom] — кнопки — всегда у нижнего края: их видно без прокрутки, и они
 * не уезжают из-под пальца, когда середина растёт.
 *
 * Короткая середина растягивается на всю высоту, поэтому `Spacer(Modifier.weight(1f))`
 * в ней работает: он забирает свободное место, а при прокрутке сжимается в ноль.
 */
@Composable
internal fun SceneBody(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    bottom: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().padding(top = HudHeight), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            CompositionLocalProvider(LocalSceneViewport provides maxHeight) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .fadingScroll(rememberScrollState())
                        .heightIn(min = maxHeight),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = horizontalAlignment,
                    content = content,
                )
            }
        }
        bottom()
    }
}

@Composable
private fun MoneyPill(amount: Int, modifier: Modifier = Modifier) {
    // Белая плашка с тенью по низу, как в макете карточек: кошелёк выглядит
    // выпуклой кнопкой-фишкой, а не полем.
    // Плашка 40 dp, монетка 30 dp — с зазором до рамки и справа: вплотную к рамке
    // она с ней сливалась. Число со строкой в свой кегль — по центру высоты.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .insetShade(RoundedCornerShape(50), PillShade, 5.dp)
            .border(StrokeRegular, FinneyInk, RoundedCornerShape(50))
            .padding(start = 12.dp, end = 5.dp)
            .clearAndSetSemantics { contentDescription = "$amount финок" },
    ) {
        OutlinedText("$amount", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, lineHeight = 26.sp))
        Coin(size = 30.dp)
    }
}

/**
 * Фон. Дождь и закат — не отдельные картинки, а состояние того же фона и того
 * же луга: они наступают постепенно, и фон при этом не пересоздаётся.
 */
@Composable
private fun BackdropLayer(backdrop: Backdrop) {
    val rain = animateFloatAsState(if (backdrop == Backdrop.DOTS_RAIN) 1f else 0f, tween(RAIN_MS), label = "rain")
    val dusk = animateFloatAsState(
        if (backdrop == Backdrop.SUNSET) 1f else 0f,
        tween(DUSK_MS, easing = FastOutSlowInEasing),
        label = "dusk",
    )
    // Карточка перед игрой и сама игра стоят на разных фонах: при «Играть» фон
    // перетекает, а не сменяется рывком. Дождь и закат — не смена фона, а его
    // состояние: они идут своей анимацией внутри того же фона.
    Crossfade(targetState = backdrop.family(), animationSpec = tween(CROSSFADE_MS), label = "backdrop") { family ->
        BackdropFamily(family, rain = { rain.value }, dusk = { dusk.value })
    }
}

/** Дождь и закат — тот же фон, что без них. */
private fun Backdrop.family(): Backdrop = when (this) {
    Backdrop.DOTS_RAIN -> Backdrop.DOTS
    Backdrop.SUNSET -> Backdrop.SKY
    else -> this
}

@Composable
private fun BackdropFamily(backdrop: Backdrop, rain: () -> Float, dusk: () -> Float) {
    when (backdrop) {
        Backdrop.DOTS, Backdrop.DOTS_RAIN -> Box(Modifier.fillMaxSize().menuBackdrop(MenuBackdrop.DOTS)) {
            Rain(rain)
        }
        // Пол — под нижней полкой: полки прижаты к тележке, и стеллаж стоит на полу.
        Backdrop.SHOP -> Split(top = Color(0xFFF9E2B8), bottom = Color(0xFFC99A76), at = 0.85f, dots = true)
        Backdrop.FIELD -> Canvas(Modifier.fillMaxSize()) {
            drawRect(FinneyGreen)
            drawCircle(Color(0xFF9BD8A2), size.width * 0.55f, Offset(size.width * 0.3f, size.height * 0.2f))
            drawCircle(Color(0xFF9BD8A2), size.width * 0.4f, Offset(size.width * 0.85f, size.height * 0.62f))
        }
        Backdrop.SKY, Backdrop.SUNSET -> Meadow(dusk)
        Backdrop.STORE -> Split(top = Color(0xFFCFE3F5), bottom = FinneyYellow, at = 0.42f, dots = true, desk = true)
        Backdrop.CARD_GREEN -> Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFFC9EAC1))
            drawCircle(Color(0xFFB4E0AE), size.width * 0.52f, Offset(size.width * 1.05f, size.height * 0.7f))
            drawCircle(Color(0xFFB4E0AE), size.width * 0.31f, Offset(size.width * 0.55f, -size.height * 0.06f))
        }
        Backdrop.CARD_LILAC -> Pattern(Color(0xFFDAD3F6), 80.dp, 80.dp) { tile ->
            star(Offset(tile * 0.26f, tile * 0.22f), tile * 0.13f, Color(0xFFC6BBF0))
            star(Offset(tile * 0.74f, tile * 0.65f), tile * 0.085f, Color(0xFFC6BBF0))
        }
        Backdrop.CARD_BLUE -> Pattern(Color(0xFFD3E8F7), 80.dp, 80.dp) { tile ->
            star(Offset(tile * 0.26f, tile * 0.22f), tile * 0.13f, Color(0xFFB9D9F1))
            drawCircle(Color(0xFFB9D9F1), tile * 0.043f, Offset(tile * 0.74f, tile * 0.66f))
        }
        Backdrop.CARD_RAIN -> Pattern(Color(0xFFDCE6F2), 64.dp, 74.dp) { tile ->
            drop(Offset(tile * 0.25f, tile * 0.1f), tile * 0.09f, Color(0xFFC2D3E8))
            drop(Offset(tile * 0.75f, tile * 0.64f), tile * 0.072f, Color(0xFFC2D3E8))
        }
        Backdrop.CARD_LEMON -> Pattern(Color(0xFFFFF4BF), 40.dp, 40.dp) { tile ->
            drawCircle(Color(0xFFF6E08A), tile * 0.15f, Offset(tile * 0.24f, tile * 0.35f))
        }
        Backdrop.CARD_YELLOW -> Pattern(Color(0xFFFCE6A6), 24.dp, 24.dp) { tile ->
            drawCircle(FinneyInk.copy(alpha = 0.10f), tile * 0.09f, Offset(tile / 2, tile / 2))
        }
        Backdrop.CARD_PINK -> Pattern(Color(0xFFFBDDE2), 24.dp, 24.dp) { tile ->
            drawCircle(FinneyInk.copy(alpha = 0.08f), tile * 0.09f, Offset(tile / 2, tile / 2))
        }
    }
}

/** Заливка [base] и узор [tile], повторённый плиткой [w] × [h] на весь экран. Узор получает ширину плитки в px. */
@Composable
private fun Pattern(base: Color, w: Dp, h: Dp, tile: DrawScope.(Float) -> Unit) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(base)
        val tw = w.toPx()
        val th = h.toPx()
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                translate(x, y) { tile(tw) }
                x += tw
            }
            y += th
        }
    }
}

/** Пятиконечная звезда радиусом [r]. */
private fun DrawScope.star(center: Offset, r: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.48f
        val a = Math.toRadians(-90.0 + i * 36.0)
        val p = Offset(center.x + rr * cos(a).toFloat(), center.y + rr * sin(a).toFloat())
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
}

/** Капля: острый верх в [top], круглый низ радиусом [r]. */
private fun DrawScope.drop(top: Offset, r: Float, color: Color) {
    val c = Offset(top.x, top.y + r * 2.2f)
    drawCircle(color, r, c)
    val path = Path().apply {
        moveTo(top.x, top.y)
        lineTo(c.x + r * 0.92f, c.y - r * 0.4f)
        lineTo(c.x - r * 0.92f, c.y - r * 0.4f)
        close()
    }
    drawPath(path, color)
}

private const val CROSSFADE_MS = 450

/**
 * Луг у лавки. [dusk] 0 — полдень, 1 — вечер: небо теплеет до золотистого, солнце
 * опускается, желтеет в персиковый и уходит за траву. Читается только на
 * отрисовке — закат не пересобирает экран.
 *
 * Небо только из палитры — голубое днём, вечером жёлтое пополам с кремовым: чистый
 * кремовый сливался бы с панелями итога. Раньше оно уходило в
 * красно-персиковый, и на плейтесте (28.09) «красное небо, зелёная трава и солнце»
 * смотрелись странно. Облака — белые с обводкой, как всё в игре; у солнца
 * (у всех лавок в прогнозе жарко или тепло) они не заслоняют.
 */
@Composable
private fun Meadow(dusk: () -> Float) {
    Canvas(Modifier.fillMaxSize()) {
        val t = dusk()
        val horizon = size.height * HORIZON_AT
        val sky = if (t < 0.5f) lerp(SkyNoon, FinneyCream, t * 2) else lerp(FinneyCream, SkyEvening, t * 2 - 1)
        drawRect(sky, size = size.copy(height = horizon))
        // Облака встают из-за луга, низом в траву: выше они налезали на заголовки
        // панелей, а из-под утренней панели торчали обрывками.
        cloud(Offset(size.width * 0.14f, horizon - 10.dp.toPx()), 1f)
        cloud(Offset(size.width * 0.5f, horizon - 6.dp.toPx()), 0.7f)
        // Солнце до травы: садясь, оно прячется за горизонт, а не ложится поверх луга.
        val c = Offset(size.width * 0.84f, lerp(size.height * 0.16f, horizon - 8.dp.toPx(), t))
        val sun = lerp(FinneyYellow, FinneyPeach, t)
        drawCircle(sun.copy(alpha = 0.5f), 42.dp.toPx(), c)
        drawCircle(FinneyInk, 32.dp.toPx(), c)
        drawCircle(sun, 28.dp.toPx(), c)
        drawRect(lerp(FinneyGreen, Color(0xFF6FB679), t), topLeft = Offset(0f, horizon), size = size.copy(height = size.height - horizon))
    }
}

/** Облако из трёх кругов: сначала обводка всех, потом заливка — контур общий, без швов внутри. */
private fun DrawScope.cloud(center: Offset, scale: Float) {
    val r = 22.dp.toPx() * scale
    val stroke = 4.dp.toPx() * scale
    val puffs = listOf(Offset(-r * 1.1f, r * 0.25f) to r * 0.8f, Offset(0f, 0f) to r, Offset(r * 1.1f, r * 0.3f) to r * 0.7f)
    puffs.forEach { (o, rr) -> drawCircle(FinneyInk, rr + stroke, center + o) }
    puffs.forEach { (o, rr) -> drawCircle(Color.White, rr, center + o) }
}

/** Небо в полдень — светлый тон голубого из палитры (#67A6DC). */
private val SkyNoon = Color(0xFF9CCBF0)

/** Вечернее небо — жёлтый #F7DC8B пополам с кремовым #FFEDCD. */
private val SkyEvening = lerp(FinneyYellow, FinneyCream, 0.5f)

/** Линия горизонта на лугу — доля высоты. */
private const val HORIZON_AT = 0.58f
private const val RAIN_MS = 700
private const val DUSK_MS = 1600

/** Стена и пол: верх до доли [at], низ после. [desk] — деревянная столешница на стыке, как у кассы. */
@Composable
private fun Split(top: Color, bottom: Color, at: Float, dots: Boolean, desk: Boolean = false) {
    Canvas(Modifier.fillMaxSize()) {
        val line = size.height * at
        drawRect(top, size = size.copy(height = line))
        drawRect(bottom, topLeft = Offset(0f, line), size = size.copy(height = size.height - line))
        if (dots) {
            val step = 28.dp.toPx()
            var y = step / 2
            while (y < line) {
                var x = step / 2
                while (x < size.width) {
                    drawCircle(FinneyInk.copy(alpha = 0.08f), 3.dp.toPx(), Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        if (desk) {
            val h = 22.dp.toPx()
            drawRect(FinneyInk, topLeft = Offset(0f, line - 4.dp.toPx()), size = size.copy(height = h + 8.dp.toPx()))
            drawRect(Color(0xFFA0715A), topLeft = Offset(0f, line), size = size.copy(height = h))
        }
    }
}

/**
 * Дождь: косые голубые штрихи и лёгкая синева поверх кремового. Белые штрихи и тень ink
 * были под комнату — на кремовом белое не видно, а ink делал фон грязно-серым.
 * Без анимации — не отвлекает.
 * [amount] 0…1 — насколько он уже начался; 0 — дождя нет.
 */
@Composable
private fun Rain(amount: () -> Float) {
    Canvas(Modifier.fillMaxSize()) {
        val a = amount()
        if (a <= 0f) return@Canvas
        drawRect(FinneyBlue.copy(alpha = 0.15f * a))
        val step = 40.dp.toPx()
        val len = 60.dp.toPx()
        var x = -size.height
        while (x < size.width) {
            var y = 0f
            while (y < size.height) {
                drawLine(
                    FinneyBlue.copy(alpha = 0.55f * a),
                    Offset(x + y * 0.27f, y),
                    Offset(x + (y + len) * 0.27f, y + len),
                    strokeWidth = 2.dp.toPx(),
                )
                y += len * 2
            }
            x += step
        }
    }
}

/**
 * Надетый аксессуар питомца игрока. Задаёт экран задания, читает [ScenePet]: так его не нужно
 * протаскивать параметром через все шесть игр. Гостей в сценах он не касается.
 */
internal val LocalPlayerAccessory = staticCompositionLocalOf<List<String>> { emptyList() }

/**
 * Питомец игрока в сцене — голова-смайлик с реакциями ([PetHead]): дышит, моргает,
 * радуется верному ходу и задумывается над ошибкой.
 */
@Composable
internal fun ScenePet(
    character: PetCharacter,
    size: Dp,
    modifier: Modifier = Modifier,
    mood: PetMood = PetMood.HAPPY,
    // Снаружи — когда игра сама дёргает питомца: прыжок за верный ход, «?» за ошибку.
    reactions: PetReactions = rememberPetReactions(),
) {
    PetHead(
        character = character,
        size = size,
        reactions = reactions,
        mood = mood,
        accessories = LocalPlayerAccessory.current,
        modifier = modifier,
    )
}

/**
 * Другие питомцы — гости, покупатели, кассир. Стоят спокойно, одной позой на всех.
 * Как и питомец игрока, в играх они — только голова ([PetHead]): с туловищем они
 * выбивались из стиля голов.
 */
@Composable
internal fun Guest(character: PetCharacter, size: Dp, modifier: Modifier = Modifier) {
    PetView(
        character = character,
        mood = PetMood.HAPPY,
        pose = StillPose,
        headOnly = true,
        modifier = modifier
            .widthIn(max = size)
            .fillMaxWidth()
            .graphicsLayer { translationY = this.size.height * HEAD_DROP },
    )
}

private val StillPose: () -> PetPose = { PetPose() }

/**
 * Питомец и его реплика. Питомец справа, как в играх с полками и кассой: плейтест
 * заметил, что на вступлении он стоял слева, а везде — справа. Пузырь вплотную
 * к питомцу и не шире, чем нужно тексту: растянутый на всю строку, он отъезжал
 * к краю, и хвостик смотрел в пустоту.
 */
@Composable
internal fun PetSays(
    character: PetCharacter,
    text: String,
    modifier: Modifier = Modifier,
    petSize: Dp = 180.dp,
    mood: PetMood = PetMood.HAPPY,
    reactions: PetReactions = rememberPetReactions(),
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        Bubble(text, Tail.RIGHT, Modifier.weight(1f, fill = false), maxWidth = 240.dp)
        ScenePet(character, petSize, Modifier.width(petSize), mood = mood, reactions = reactions)
    }
}

/** Питомец сам по себе, у правого края сцены — там же, где он говорит в [PetSays]. */
@Composable
internal fun PetAtRight(
    character: PetCharacter,
    petSize: Dp,
    modifier: Modifier = Modifier,
    mood: PetMood = PetMood.HAPPY,
    reactions: PetReactions = rememberPetReactions(),
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        ScenePet(character, petSize, Modifier.width(petSize), mood = mood, reactions = reactions)
    }
}

/** Куда смотрит хвостик реплики — в сторону того, кто говорит. */
enum class Tail { LEFT, RIGHT, DOWN_LEFT, DOWN_RIGHT }

/** Реплика в белом пузыре, как в концептах. Текст короткий — одна-две строки. */
@Composable
internal fun Bubble(text: String, tail: Tail, modifier: Modifier = Modifier, maxWidth: Dp = 200.dp) {
    val tailSize = 12.dp
    val shape = remember(tail) { BubbleShape(tail, 16.dp, tailSize) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = FinneyInk,
        modifier = modifier
            .widthIn(max = maxWidth)
            .background(Color.White, shape)
            .border(StrokeRegular, FinneyInk, shape)
            .padding(
                start = 12.dp + if (tail == Tail.LEFT) tailSize else 0.dp,
                end = 12.dp + if (tail == Tail.RIGHT) tailSize else 0.dp,
                top = 8.dp,
                bottom = 8.dp + if (tail == Tail.DOWN_LEFT || tail == Tail.DOWN_RIGHT) tailSize else 0.dp,
            ),
    )
}

/** Пузырь реплики: скруглённое тело и треугольный хвостик с одной стороны. */
private class BubbleShape(private val tail: Tail, private val radius: Dp, private val tailSize: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        val t = with(density) { tailSize.toPx() }
        val down = tail == Tail.DOWN_LEFT || tail == Tail.DOWN_RIGHT
        val body = RoundRect(
            left = if (tail == Tail.LEFT) t else 0f,
            top = 0f,
            right = size.width - if (tail == Tail.RIGHT) t else 0f,
            bottom = size.height - if (down) t else 0f,
            cornerRadius = CornerRadius(r),
        )
        val path = Path().apply {
            addRoundRect(body)
            when (tail) {
                Tail.LEFT -> { moveTo(t, r); lineTo(0f, r + t * 0.6f); lineTo(t, r + t * 1.4f); close() }
                Tail.RIGHT -> { moveTo(size.width - t, r); lineTo(size.width, r + t * 0.6f); lineTo(size.width - t, r + t * 1.4f); close() }
                Tail.DOWN_LEFT -> { moveTo(r, body.bottom); lineTo(r + t * 0.3f, size.height); lineTo(r + t * 1.4f, body.bottom); close() }
                Tail.DOWN_RIGHT -> {
                    moveTo(size.width - r, body.bottom); lineTo(size.width - r - t * 0.3f, size.height)
                    lineTo(size.width - r - t * 1.4f, body.bottom); close()
                }
            }
        }
        return Outline.Generic(path)
    }
}

/**
 * Кремовая панель с заголовком на рамке, как «Проверка», «Касса», «Итог дня» в концептах.
 * [badge] — значок перед заголовком на той же рамке: так итог игры читается и без слов.
 */
@Composable
internal fun ScenePanel(
    title: String?,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
    /** Цветной флажок на рамке справа — у окон карточки перед игрой. */
    flag: Color? = null,
    /** Тень по низу внутри рамки: панель выглядит выпуклой плашкой, как в макете карточек. */
    raised: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Над рамкой — место под всю верхнюю половину заголовка. Было 18 dp при сдвиге
    // заголовка на 22: верх букв с обводкой выходил за панель, и прокрутка сцены
    // срезала его — «Готово!» в итоге игры было обрезано сверху.
    // Выпуклое окно карточки скруглено сильнее, как в референсе: 30 против 22.
    val shape = RoundedCornerShape(if (raised) 30.dp else 22.dp)
    Box(modifier = modifier.padding(top = if (title != null) PanelTitleRise else 0.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(FinneyCream)
                .then(if (raised) Modifier.insetShade(shape, PanelShade, 9.dp, border = 4.dp) else Modifier)
                .border(4.dp, FinneyInk, shape)
                .padding(start = 14.dp, end = 14.dp, bottom = if (raised) 18.dp else 14.dp, top = if (title != null) 26.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
        if (flag != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-18).dp, y = (-14).dp)
                    .rotate(12f)
                    .size(width = 34.dp, height = 22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(flag)
                    .border(StrokeRegular, FinneyInk, RoundedCornerShape(7.dp)),
            )
        }
        if (title != null) {
            // Заголовок садится на рамку: половина над ней, половина — внутри.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.TopCenter).offset(y = -PanelTitleRise),
            ) {
                badge?.invoke()
                OutlinedText(title, style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

/**
 * Тень по низу внутри рамки — «объём» плашек, кружков и окон, как `inset` тень
 * в макете карточек: форма минус та же форма, поднятая на [depth]. Поэтому у кружка
 * тень — серп, а у плашки она загибается вверх по скруглениям. Полоса считается
 * от внутреннего края рамки [border]: прямая полоса той же высоты почти вся
 * уходила под рамку. Ставится после фона и до рамки.
 */
internal fun Modifier.insetShade(shape: Shape, color: Color, depth: Dp, border: Dp = StrokeRegular): Modifier = drawBehind {
    val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }
    val lifted = Path().apply {
        addPath(outline, Offset(0f, -(depth + border).toPx()))
    }
    drawPath(Path().apply { op(outline, lifted, PathOperation.Difference) }, color)
}

/** Тень белых плашек — светло-сиреневая. */
internal val PillShade = Color(0xFFEDE6FF)

/** Тень кремовых окон — тёплая, тон кремового потемнее. */
private val PanelShade = Color(0xFFFBDDB4)

/** На сколько заголовок [ScenePanel] поднимается над рамкой — ровно столько места над ней и оставляем. */
private val PanelTitleRise = 22.dp

/** Шкала «сколько из скольки»: копилка, тележка. [extra] — сегодняшняя добавка штриховкой. */
@Composable
internal fun Meter(fraction: Float, modifier: Modifier = Modifier, extra: Float = 0f, color: Color = FinneyYellow) {
    Canvas(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFF7EA))
            .border(StrokeRegular, FinneyInk, RoundedCornerShape(8.dp)),
    ) {
        val done = fraction.coerceIn(0f, 1f)
        drawRect(color, size = size.copy(width = size.width * done))
        val plus = extra.coerceIn(0f, 1f - done)
        if (plus > 0f) {
            drawRect(
                Brush.linearGradient(listOf(FinneyYellow, Color.White, FinneyYellow)),
                topLeft = Offset(size.width * done, 0f),
                size = size.copy(width = size.width * plus),
            )
        }
    }
}
