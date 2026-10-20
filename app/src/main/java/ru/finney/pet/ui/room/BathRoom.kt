package ru.finney.pet.ui.room

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.finney.pet.domain.game.PurchasePreview
import ru.finney.pet.domain.model.ShopItem
import ru.finney.pet.ui.components.CheckBadge
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.components.WarningBadge
import ru.finney.pet.ui.components.drawPointingHand
import ru.finney.pet.ui.components.pulse
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.FinneyBlue
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyGlare
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.StrokeBold
import ru.finney.pet.ui.theme.StrokeRegular
import ru.finney.pet.ui.theme.StrokeThin
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

// Ванна — панель из трёх шагов прямо в комнате: 1 мыло → 2 душ → 3 зубы. Каждый шаг ребёнок
// начинает сам, нажатием на плитку (плейтест 19.10: «хочу сам тыкать этапы, а не всё сразу»).
// Что нажать дальше, показывает рука на плитке и одна строка над питомцем.
//
// Чего нет на полочке — видно сразу, до мытья: на плитке вместо запаса «Купить», нажал —
// магазин ровно этого шага (мыло или щётка с пастой). Раньше про щётку узнавали только
// в конце, когда уже помыли (плейтест 19.10).
//
// Своих шкал и денег нет — они на главном, как были. Сделанный шаг тратит раз предмета
// (Game.wash) и сразу прибавляет чистоту; прибавка всплывает над питомцем (ТЗ п. 2.5.9).

/** Шаг мытья. DRY — полотенце: в продаже его сейчас нет, шаг оставлен для контента. */
enum class BathStep(val label: String) { SOAP("Намылить"), RINSE("Смыть"), DRY("Вытереть"), TEETH("Зубы") }

/**
 * Предмет ванны: сколько раз ещё хватит и на сколько хватает одной покупки.
 * [lockedUntil] — уровень, с которого его можно купить; null — уже можно (Game.isUnlocked).
 */
data class ShelfItem(val preview: PurchasePreview, val left: Int, val uses: Int, val step: BathStep, val lockedUntil: Int? = null) {
    val item: ShopItem get() = preview.item
}

data class BathShelf(
    val items: List<ShelfItem> = emptyList(),
    val hygiene: Int = 0,
    /** Питомец и так чистый: мыть незачем, мыло сбережём. */
    val clean: Boolean = false,
    /** Чистота не ниже порога нужного: для уровня мыться хватит, «совсем чистый» — по желанию. */
    val enough: Boolean = false,
    /** Водой ещё можно сполоснуться: чистота ниже потолка воды. */
    val canRinse: Boolean = false,
    /** Шкала чистоты полная: и зубы чистить некуда. */
    val full: Boolean = false,
) {
    /** Чем намыливать: то мыло, что есть. null — мыла нет. */
    val soap: ShelfItem? get() = items.filter { it.step == BathStep.SOAP && it.left > 0 }.minByOrNull { it.item.price }

    /** Сколько раз ещё хватит мыла — всего. */
    val soapLeft: Int get() = items.filter { it.step == BathStep.SOAP }.sumOf { it.left }

    /** Зубы: щётка — её держат (из пары то, что служит дольше); null — чистить нечем. */
    val brush: ShelfItem? get() = items.filter { it.step == BathStep.TEETH }.maxByOrNull { it.uses }

    /** Сколько раз ещё почистить зубы: по меньшему из пары щётка — паста. */
    val teethLeft: Int get() = items.filter { it.step == BathStep.TEETH }.minOfOrNull { it.left } ?: 0

    /** Есть чем чистить зубы: и щётка, и паста. */
    val hasTeeth: Boolean get() = brush != null && teethLeft > 0

    /**
     * Зубы ещё закрыты: чистить нечем, а щётку с пастой купить нельзя до этого уровня.
     * null — открыты или запас остался с прошлого.
     */
    val teethLockedUntil: Int? get() = if (hasTeeth) null else
        items.filter { it.step == BathStep.TEETH }.mapNotNull { it.lockedUntil }.maxOrNull()
}

private const val SCRUB_COLS = 5
private const val SCRUB_ROWS = 4
private const val SCRUB_DONE = 0.7f
private const val RINSE_COLS = 6
private const val RINSE_DONE = 5
private const val TEETH_STROKES = 8

/** Как часто в пути мыла вырастает пена, dp. Чаще — пена сливается в кашу. */
private const val FOAM_STEP = 12f
private const val FOAM_MAX = 220
private const val FLOATER_LIFE_MS = 1400L
private const val BUBBLE_GROW_MS = 180L
private const val POP_MS = 420L
private const val GAIN_LIFE_MS = 1600L
private const val FLASH_MS = 2500L

private class Bubble(val center: Offset, val radius: Float, val bornMs: Long, val phase: Float)
private class Floater(val start: Offset, val radius: Float, val bornMs: Long, val drift: Float)
private class Gain(val amount: Int, val bornMs: Long)

/** Всё, что видно в ванной: пена, шаги, что в руке. */
@Stable
private class BathSession {
    val bubbles = mutableStateListOf<Bubble>()
    val floaters = mutableStateListOf<Floater>()
    val gains = mutableStateListOf<Gain>()
    val cells = mutableSetOf<Int>()
    var nowMs by mutableLongStateOf(0L)

    /** Шаг, который делают сейчас; null — ждём, какую плитку нажмут. */
    var step by mutableStateOf<BathStep?>(null)

    /** Намылили, а смыть ещё не смыли: следующий шаг — душ. */
    var soaped by mutableStateOf(false)

    /** Что уже сделано, пока ребёнок в ванной: ✓ на плитке. */
    val done = mutableStateListOf<BathStep>()

    /** Чем моем: мыло и щётка на это мытьё. */
    var soapId: String? = null
    var brushId: String? = null

    var progress by mutableFloatStateOf(0f)
    var touched by mutableStateOf(false)
    var held by mutableStateOf(Offset.Unspecified)
    var tilt by mutableFloatStateOf(0f)

    /** Когда лопнула пена; -1 — не лопалась. */
    var poppedAtMs by mutableLongStateOf(-1L)
    var lastFoam = Offset.Unspecified
    var brushAnchor = 0f
    var brushDir = 0f
    var strokes = 0

    /** Короткая подсказка вместо обычной: «уже чистый», «мыла нет». */
    var flash by mutableStateOf<String?>(null)
    var flashAt by mutableLongStateOf(0L)

    fun begin(next: BathStep) {
        step = next
        progress = 0f
        touched = false
        held = Offset.Unspecified
        cells.clear()
        strokes = 0
        brushDir = 0f
    }
}

/**
 * Ванна поверх комнаты. [pet] и [mouth] — питомец и его рот в координатах корня.
 * [nudge] растёт с каждым нажатием на ванну внизу: мыть начинают с панели, и рука на ней дёргается.
 * [onUse] — шаг сделан: тратит раз предмета [itemId] (null — вода).
 */
@Composable
fun BathRoom(
    petName: String,
    shelf: BathShelf,
    nudge: Int,
    pet: Rect,
    mouth: Offset,
    onUse: (itemId: String?) -> Unit,
    onOpenShop: (BathStep) -> Unit,
    onTickle: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val sounds = LocalSounds.current
    val animations = LocalAnimations.current
    val session = remember { BathSession() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val currentShelf by rememberUpdatedState(shelf)
    val currentOnUse by rememberUpdatedState(onUse)
    val currentOnTickle by rememberUpdatedState(onTickle)
    val currentOnDone by rememberUpdatedState(onDone)
    val currentOnOpenShop by rememberUpdatedState(onOpenShop)

    LaunchedEffect(session) {
        while (true) withFrameNanos { session.nowMs = it / 1_000_000 }
    }

    fun flash(text: String) {
        session.flash = text
        session.flashAt = session.nowMs + 1
    }
    LaunchedEffect(session.flashAt) {
        if (session.flash == null) return@LaunchedEffect
        delay(FLASH_MS)
        session.flash = null
    }

    // Прибавка чистоты всплывает над питомцем — видно, что шаг сработал.
    var lastHygiene by remember { mutableIntStateOf(shelf.hygiene) }
    LaunchedEffect(shelf.hygiene) {
        val gain = shelf.hygiene - lastHygiene
        if (gain > 0) session.gains += Gain(gain, session.nowMs)
        lastHygiene = shelf.hygiene
    }

    // Нажали на ванну внизу — мыть начинают с панели, туда и зовём: строка мигает.
    val opened = remember { nudge }
    var nudged by remember { mutableIntStateOf(0) }
    LaunchedEffect(nudge) { if (nudge != opened) nudged++ }

    // Нажали на плитку: шаг начинается, если для него всё есть; нечем — магазин этого шага.
    fun begin(step: BathStep) {
        if (session.step != null) return
        val s = currentShelf
        when (step) {
            BathStep.SOAP -> {
                val soap = s.soap ?: return currentOnOpenShop(BathStep.SOAP)
                if (s.clean) return flash("$petName уже чистый")
                session.soapId = soap.item.id
                // Новый круг: ✓ мыла и душа — заново.
                session.done -= listOf(BathStep.SOAP, BathStep.RINSE)
                session.bubbles.clear()
                session.poppedAtMs = -1L
            }
            BathStep.RINSE -> when {
                session.soaped -> Unit
                s.clean -> return flash("$petName уже чистый")
                !s.canRinse -> return flash("Водой больше не отмыть — нужно мыло")
            }
            BathStep.TEETH -> {
                s.teethLockedUntil?.let { return flash("Зубы откроются на уровне $it") }
                val brush = s.brush?.takeIf { s.hasTeeth } ?: return currentOnOpenShop(BathStep.TEETH)
                if (session.soaped) return flash("Сначала смой пену — нажми «Душ»")
                if (s.full) return flash("$petName и так чистый")
                session.brushId = brush.item.id
                session.bubbles.clear()
                session.poppedAtMs = -1L
            }
            BathStep.DRY -> return
        }
        session.begin(step)
    }

    val px = with(density) { 1.dp.toPx() }
    val area = Rect(
        left = pet.left + pet.width * 0.14f - origin.x,
        top = pet.top + pet.height * 0.10f - origin.y,
        right = pet.right - pet.width * 0.14f - origin.x,
        bottom = pet.top + pet.height * 0.72f - origin.y,
    )
    val mouthAt = mouth - origin

    // Шаг сделан: тратим раз предмета, дальше — следующий шаг или конец.
    fun complete() {
        val step = session.step ?: return
        sounds.play(Sfx.WashClean)
        val itemId = when (step) {
            BathStep.SOAP -> session.soapId
            BathStep.TEETH -> session.brushId
            else -> null
        }
        when (step) {
            BathStep.RINSE -> {
                session.bubbles.take(12).forEach {
                    session.floaters += Floater(it.center, it.radius * 0.5f, session.nowMs, Random.nextFloat() - 0.5f)
                }
                session.bubbles.clear()
            }
            BathStep.TEETH -> session.poppedAtMs = session.nowMs
            else -> Unit
        }
        // Смывают пену после мыла — мыло уже засчитано, вода сверху не нужна.
        val counts = !(step == BathStep.RINSE && session.soaped)
        if (counts) currentOnUse(itemId)
        session.soaped = step == BathStep.SOAP
        if (step !in session.done) session.done += step
        session.step = null
        session.held = Offset.Unspecified
        if (step != BathStep.SOAP) currentOnDone()
    }

    Box(modifier = modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        Canvas(Modifier.fillMaxSize()) {
            val now = session.nowMs
            val pop = if (session.poppedAtMs < 0) 0f else ((now - session.poppedAtMs) / POP_MS.toFloat()).coerceIn(0f, 1f)
            session.bubbles.forEachIndexed { index, bubble ->
                // Лопаются не хором: каждый пузырь со своей задержкой.
                val own = ((pop - (index % 7) * 0.06f) / 0.6f).coerceIn(0f, 1f)
                if (own >= 1f) return@forEachIndexed
                val grow = ((now - bubble.bornMs) / BUBBLE_GROW_MS.toFloat()).coerceIn(0f, 1f)
                val breathe = 1f + 0.06f * sin(now / 380f + bubble.phase)
                drawBubble(bubble.center, bubble.radius * grow * breathe * (1f + own * 0.5f), alpha = 1f - own)
            }
            session.floaters.forEach { f ->
                val t = (now - f.bornMs) / FLOATER_LIFE_MS.toFloat()
                if (t !in 0f..1f) return@forEach
                val sway = sin(t * 9f + f.drift * 6f) * 10f * px
                drawBubble(f.start + Offset(sway + f.drift * 30f * px, -t * 140f * px), f.radius * (1f + t * 0.4f), 1f - t)
            }
            if (session.step == BathStep.RINSE) {
                // Пока душ не взяли, он сам ходит над питомцем — показывает жест.
                val shower = if (session.held.isSpecified) {
                    session.held
                } else {
                    val t = if (animations) now / 1600f * 2f * PI.toFloat() else 0f
                    Offset(area.center.x + sin(t) * area.width * 0.35f, area.top - 10f * px)
                }
                drawShower(shower - Offset(0f, 64f * px), area.bottom, if (animations) now else 0L, px, if (session.held.isSpecified) 1f else 0.55f)
            }
        }

        // Всплывающая прибавка: «+40» со значком ванны, тает вверх.
        session.gains.lastOrNull()?.let { gain ->
            val t = (session.nowMs - gain.bornMs) / GAIN_LIFE_MS.toFloat()
            if (t in 0f..1f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .offset { IntOffset((area.center.x - 40f * px).roundToInt(), (area.top - t * 60f * px).roundToInt()) }
                        .graphicsLayer { alpha = 1f - t }
                        .clearAndSetSemantics {},
                ) {
                    OutlinedText("+${gain.amount}", style = MaterialTheme.typography.headlineMedium)
                    FinneyIcon(FinneyIcons.Bath, size = 28.dp)
                }
            }
        }

        // Предмет в руке: мыло или щётка. Пока не коснулись — призрак показывает жест.
        val step = session.step
        val heldId = when (step) {
            BathStep.SOAP -> session.soapId
            BathStep.TEETH -> session.brushId
            else -> null
        }
        if (heldId != null) {
            val itemPx = with(density) { ItemSize.toPx() }
            if (!session.touched) {
                GestureGhost(
                    itemId = heldId,
                    fallback = FinneyIcons.Bath,
                    visible = true,
                    periodMs = if (step == BathStep.TEETH) 900 else 1800,
                    at = { t ->
                        val a = t * 2f * PI.toFloat()
                        if (step == BathStep.TEETH) {
                            Offset(mouthAt.x + sin(a) * 30f * px, mouthAt.y)
                        } else {
                            Offset(area.center.x + sin(a) * area.width * 0.3f, area.center.y + sin(2 * a) * area.height * 0.2f)
                        }
                    },
                )
            } else if (session.held.isSpecified) {
                ItemPicture(
                    itemId = heldId,
                    fallback = FinneyIcons.Bath,
                    size = ItemSize,
                    modifier = Modifier
                        .offset { IntOffset((session.held.x - itemPx / 2).roundToInt(), (session.held.y - itemPx / 2).roundToInt()) }
                        .graphicsLayer { rotationZ = session.tilt },
                )
            }
        }

        // Где трут: питомец и немного над ним — там и душ. Касания ловит только во время мытья;
        // иначе комната и кнопки под ним нажимаются как обычно.
        if (step != null) {
            val zoneTop = (area.top - 90f * px).coerceAtLeast(0f)
            val zoneHeight = (pet.bottom - origin.y - zoneTop).coerceAtLeast(1f)
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, zoneTop.roundToInt()) }
                    .fillMaxWidth()
                    .height(with(density) { zoneHeight.toDp() })
                    .semantics {
                        contentDescription = hint(step, petName)
                        // Для TalkBack жест заменяется нажатием: шаг сделан.
                        onClick(label = step.label) { complete(); true }
                    }
                    .pointerInput(step, area, mouthAt, zoneTop) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            session.touched = true
                            fun act(p: Offset) {
                                if (session.step != step) return
                                session.held = p
                                val onStep: (Boolean) -> Unit = { new ->
                                    if (new) {
                                        currentOnTickle()
                                        if (step != BathStep.RINSE) sounds.play(Sfx.WashBubble)
                                    }
                                    if (session.progress >= 1f) complete()
                                }
                                when (step) {
                                    BathStep.SOAP -> scrub(session, p, area, px, onStep)
                                    BathStep.RINSE -> rinse(session, p, area, px, onStep)
                                    BathStep.TEETH -> brush(session, p, mouthAt, px, onStep)
                                    BathStep.DRY -> Unit
                                }
                            }
                            val first = down.position + Offset(0f, zoneTop)
                            session.brushAnchor = first.x
                            session.lastFoam = Offset.Unspecified
                            act(first)
                            drag(down.id) { change ->
                                session.tilt = ((change.position.x - change.previousPosition.x) * 1.5f).coerceIn(-20f, 20f)
                                act(change.position + Offset(0f, zoneTop))
                                change.consume()
                            }
                            session.tilt = 0f
                            if (session.step == step) session.held = Offset.Unspecified
                        }
                    },
            )
        }

        // Одна строка над питомцем и рука на плитке: что делать сейчас и куда нажать.
        val guide = guide(shelf, session, petName)
        val note = session.flash ?: guide.text
        val kind = if (session.flash != null) NoteKind.Warn else guide.kind
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, (pet.top - origin.y - 64f * px).coerceAtLeast(0f).roundToInt()) }
                .padding(horizontal = 56.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            // Нажали на ванну внизу — строка подпрыгивает: мыть начинают с панели.
            val jump = remember { Animatable(0f) }
            LaunchedEffect(nudged) {
                if (nudged == 0) return@LaunchedEffect
                jump.animateTo(-14f, tween(120))
                jump.animateTo(0f, spring(dampingRatio = 0.35f))
            }
            BathNote(note, kind = kind, modifier = Modifier.graphicsLayer { translationY = jump.value * density.density })
        }

        // Панель шагов над нижним рядом: сплошная, с толстой обводкой, как окна приложения, —
        // чтобы не сливалась с комнатой. Номера и стрелки говорят порядок без слов.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(start = 10.dp, end = 10.dp, bottom = 104.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(FinneyCream)
                .border(StrokeBold, FinneyInk, RoundedCornerShape(24.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            fun tile(s: BathStep): TileState = when {
                step == s -> TileState.Now
                step == null && guide.point == s -> TileState.Next
                else -> TileState.Idle
            }
            val soapPicture = (shelf.soap ?: shelf.items.firstOrNull { it.step == BathStep.SOAP })?.item?.id
            val brushPicture = (shelf.brush ?: shelf.items.firstOrNull { it.step == BathStep.TEETH })?.item?.id
            StepTile(
                number = 1, step = BathStep.SOAP, picture = soapPicture,
                left = shelf.soapLeft, state = tile(BathStep.SOAP), done = BathStep.SOAP in session.done,
                onClick = { begin(BathStep.SOAP) }, modifier = Modifier.weight(1f),
            )
            StepArrow()
            StepTile(
                number = 2, step = BathStep.RINSE, picture = null,
                left = null, state = tile(BathStep.RINSE), done = BathStep.RINSE in session.done,
                onClick = { begin(BathStep.RINSE) }, modifier = Modifier.weight(1f),
            )
            StepArrow()
            StepTile(
                number = 3, step = BathStep.TEETH, picture = brushPicture,
                left = shelf.teethLeft, state = tile(BathStep.TEETH), done = BathStep.TEETH in session.done,
                lockedUntil = shelf.teethLockedUntil,
                onClick = { begin(BathStep.TEETH) }, modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Что сказать ребёнку и на какую плитку показать рукой. */
private class BathGuide(val text: String, val kind: NoteKind, val point: BathStep?)

/**
 * Куда вести дальше. Порядок один: мыло → душ → зубы. Нечем — зовём купить (рука на «Купить»
 * той же плитки), но про щётку — только когда до зубов дошли: пока не помыли, она не отвлекает,
 * а «Купить» на плитке и так видно с самого начала.
 */
private fun guide(shelf: BathShelf, session: BathSession, petName: String): BathGuide {
    val step = session.step
    // Закрытые зубы не зовём: купить щётку ещё нельзя, круг кончается на душе.
    val teethNext = !shelf.full && BathStep.TEETH !in session.done && shelf.teethLockedUntil == null
    // Круг мыла прошли и нужное закрыто — второй раз не зовём, домыть до «совсем чистого»
    // можно и самому. На 1-м уровне круг даёт 40 → 80 из 90, и обучение просило мыться
    // дважды подряд (плейтест 20.10).
    val dirty = !shelf.clean && !(shelf.enough && BathStep.RINSE in session.done)
    return when {
        step != null -> BathGuide(hint(step, petName), NoteKind.Info, null)
        session.soaped -> BathGuide("Смой пену — нажми «Душ»", NoteKind.Info, BathStep.RINSE)
        // Один круг мыла грязнулю не отмывает: прибавка за раз меньше, чем от нуля до чистого.
        dirty && shelf.soap != null && BathStep.RINSE in session.done ->
            BathGuide("Ещё не отмылся — ещё раз «Мыло»", NoteKind.Info, BathStep.SOAP)
        dirty && shelf.soap != null -> BathGuide("Нажми «Мыло» — начнём мыть", NoteKind.Info, BathStep.SOAP)
        dirty -> BathGuide("Мыло кончилось — купи новое", NoteKind.Warn, BathStep.SOAP)
        teethNext && shelf.hasTeeth -> BathGuide("Почисти зубы — нажми «Зубы»", NoteKind.Info, BathStep.TEETH)
        teethNext && BathStep.SOAP in session.done ->
            BathGuide("Для зубов нужны щётка и паста", NoteKind.Warn, BathStep.TEETH)
        else -> BathGuide("$petName чистый!", NoteKind.Ok, null)
    }
}

/** Что делать на шаге — одна строка для ребёнка. */
private fun hint(step: BathStep, petName: String): String = when (step) {
    BathStep.SOAP -> "Води мылом по $petName"
    BathStep.RINSE -> "Смой пену: веди душем"
    BathStep.DRY -> "Вытри $petName полотенцем"
    BathStep.TEETH -> "Почисти зубы: води щёткой у рта"
}

private enum class TileState { Idle, Next, Now }

/**
 * Плитка шага: номер, картинка, название и запас «×N». Кончилось — вместо запаса персиковая
 * «Купить» с тележкой: нажал на плитку — магазин этого шага. Следующий шаг — белый, как все,
 * но дышит и на него жмёт рука (плейтест 19.10: жёлтым он читался как уже выбранный).
 * Идущий — жёлтый с толстой обводкой, сделанный — «✓». Не только цвет (ТЗ п. 3.6).
 * Закрытый по уровню ([lockedUntil]) — замок и «ур. N» вместо «Купить»: в магазин не ведёт.
 */
@Composable
private fun StepTile(
    number: Int,
    step: BathStep,
    picture: String?,
    left: Int?,
    state: TileState,
    done: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lockedUntil: Int? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    val empty = left == 0
    val action = if (empty && lockedUntil == null) "Купить" else step.label
    val spoken = "$number. ${step.short()}" + when {
        state == TileState.Now -> ", сейчас"
        lockedUntil != null -> ", откроется на уровне $lockedUntil"
        empty -> ", нет на полочке"
        done -> ", сделано"
        left != null -> ", хватит на ${times(left)}"
        else -> ""
    }
    Box(
        modifier = modifier
            .pulse(state == TileState.Next)
            .tapHand(state == TileState.Next)
            .clip(shape)
            .background(if (state == TileState.Now) FinneyYellow else FinneyGlare)
            .border(if (state == TileState.Now) StrokeBold else StrokeRegular, FinneyInk, shape)
            .clickable(onClickLabel = action, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                onClick(label = action) { onClick(); true }
            },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (picture != null) {
                ItemPicture(picture, itemFallback(picture), size = 44.dp, modifier = Modifier.alpha(if (empty) 0.35f else 1f))
            } else {
                Canvas(Modifier.size(44.dp)) {
                    drawShower(Offset(size.width / 2, size.height * 0.45f), size.height, 0L, size.width / 80f, 1f)
                }
            }
            Text(step.short(), style = MaterialTheme.typography.bodyLarge, color = FinneyInk, maxLines = 1)
            when {
                lockedUntil != null -> LockPill(lockedUntil)
                empty -> BuyPill()
                left != null -> Text("×$left", style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
                else -> Text("вода", style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
            }
        }
        NumberBadge(number, Modifier.align(Alignment.TopStart).padding(4.dp))
        if (done && state != TileState.Now) CheckBadge(Modifier.align(Alignment.TopEnd).padding(4.dp), size = 22.dp)
    }
}

/**
 * Рука, которая жмёт на плитку снизу справа, — та же, что у подсказки шага на главном.
 * Рисуется поверх плитки и не обрезается её скруглением: модификатор стоит до clip.
 */
private fun Modifier.tapHand(active: Boolean): Modifier = if (!active) this else composed {
    val press = if (LocalAnimations.current) {
        rememberInfiniteTransition(label = "hand").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "press",
        ).value
    } else 0f
    drawWithContent {
        drawContent()
        val travel = 10.dp.toPx() * (1f - press)
        drawPointingHand(Offset(size.width * 0.72f + travel * 0.5f, size.height * 0.58f + travel), up = true, press = press)
    }
}

/** Короткое название на плитке: «Мыло», «Душ», «Зубы». */
private fun BathStep.short(): String = when (this) {
    BathStep.SOAP -> "Мыло"
    BathStep.RINSE -> "Душ"
    BathStep.DRY -> "Полотенце"
    BathStep.TEETH -> "Зубы"
}

/** Номер шага в кружке: порядок читается и без стрелок. */
@Composable
private fun NumberBadge(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(FinneyCream)
            .border(StrokeThin, FinneyInk, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("$number", style = MaterialTheme.typography.labelLarge, color = FinneyInk)
    }
}

/** «Купить» на плитке, где кончилось: персиковая, как кнопки, с тележкой. */
@Composable
private fun BuyPill() {
    // На крупном шрифте (130 %) тележка со словом не влезали в плитку, и слово резалось
    // до «Купит». Слово важнее значка: его и оставляем, а персиковая плашка остаётся кнопкой.
    val roomy = LocalDensity.current.fontScale < 1.15f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(FinneyPeach)
            .border(StrokeThin, FinneyInk, RoundedCornerShape(50))
            .padding(horizontal = if (roomy) 8.dp else 6.dp, vertical = 1.dp),
    ) {
        if (roomy) FinneyIcon(FinneyIcons.Cart, size = 16.dp)
        Text("Купить", style = MaterialTheme.typography.bodyMedium, color = FinneyInk, maxLines = 1)
    }
}

/** Замок на плитке, которую ещё нельзя купить: кремовая, как отметки, — не кнопка. */
@Composable
private fun LockPill(level: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(FinneyCream)
            .border(StrokeThin, FinneyInk, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 1.dp),
    ) {
        FinneyIcon(FinneyIcons.Lock, size = 16.dp)
        Text("ур. $level", style = MaterialTheme.typography.bodyMedium, color = FinneyInk, maxLines = 1)
    }
}

/** Стрелка между шагами: порядок виден без слов. */
@Composable
private fun StepArrow() {
    Text("→", style = MaterialTheme.typography.titleLarge, color = FinneyInk, modifier = Modifier.clearAndSetSemantics {})
}

internal enum class NoteKind { Warn, Ok, Info }

/** Одна фраза в плашке: что сейчас и что делать. Значок рядом — не только цвет (ТЗ п. 3.6). */
@Composable
internal fun BathNote(text: String, kind: NoteKind, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(FinneyCream)
            .border(StrokeThin, FinneyInk, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        when (kind) {
            NoteKind.Warn -> WarningBadge()
            NoteKind.Ok -> CheckBadge()
            NoteKind.Info -> Unit
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = FinneyInk, textAlign = TextAlign.Center)
    }
}

/** «1 раз», «2 раза», «5 раз». */
internal fun times(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    val word = when {
        mod100 in 11..14 -> "раз"
        mod10 == 1 -> "раз"
        mod10 in 2..4 -> "раза"
        else -> "раз"
    }
    return "$n $word"
}

// ---------- Жесты ----------
// Каждый считает, сколько сделано, в [BathSession.progress]; [onStep] получает true,
// когда касание засчитано заново (питомцу щекотно).

/**
 * Мыло: сетка над питомцем (SCRUB_COLS × SCRUB_ROWS), клетка засчитана, если по ней провели.
 * Сетка, а не число пузырей, — иначе можно тереть одно место, и оно засчиталось бы за всё.
 */
private fun scrub(session: BathSession, at: Offset, area: Rect, px: Float, onStep: (Boolean) -> Unit) {
    if (!area.contains(at)) return
    val last = session.lastFoam
    if (last.isSpecified && (at - last).getDistance() < FOAM_STEP * px) return
    session.lastFoam = at
    if (session.poppedAtMs >= 0) {
        session.bubbles.clear()
        session.poppedAtMs = -1L
    }
    foam(session, at, spread = 28f * px, radius = 7f * px..18f * px)
    if (Random.nextFloat() < 0.35f) session.floaters += Floater(at, (3f + Random.nextFloat() * 5f) * px, session.nowMs, Random.nextFloat() - 0.5f)
    session.floaters.removeAll { session.nowMs - it.bornMs > FLOATER_LIFE_MS }
    val col = ((at.x - area.left) / area.width * SCRUB_COLS).toInt().coerceIn(0, SCRUB_COLS - 1)
    val row = ((at.y - area.top) / area.height * SCRUB_ROWS).toInt().coerceIn(0, SCRUB_ROWS - 1)
    val new = session.cells.add(row * SCRUB_COLS + col)
    session.progress = session.cells.size / (SCRUB_COLS * SCRUB_ROWS * SCRUB_DONE)
    onStep(new)
}

/** Душ: вода льётся вниз, пузыри под струёй лопаются, столбец над питомцем смыт. */
private fun rinse(session: BathSession, finger: Offset, area: Rect, px: Float, onStep: (Boolean) -> Unit) {
    if (finger.x !in area.left..area.right || finger.y > area.bottom) return
    val reach = 26f * px
    val washed = session.bubbles.filter { abs(it.center.x - finger.x) < reach && it.center.y > finger.y - reach }
    if (washed.isNotEmpty()) {
        session.bubbles.removeAll(washed)
        washed.take(3).forEach { session.floaters += Floater(it.center, it.radius * 0.5f, session.nowMs, Random.nextFloat() - 0.5f) }
    }
    val col = ((finger.x - area.left) / area.width * RINSE_COLS).toInt().coerceIn(0, RINSE_COLS - 1)
    val new = session.cells.add(col)
    session.progress = session.cells.size / RINSE_DONE.toFloat()
    onStep(new)
}

/** Щётка у рта: каждое «туда-обратно» — штрих, у рта растёт пенка. */
private fun brush(session: BathSession, at: Offset, mouth: Offset, px: Float, onStep: (Boolean) -> Unit) {
    if ((at - mouth).getDistance() > 70f * px) return
    val dx = at.x - session.brushAnchor
    if (abs(dx) < 14f * px) return
    val dir = sign(dx)
    val stroke = session.brushDir != 0f && dir != session.brushDir
    session.brushDir = dir
    session.brushAnchor = at.x
    if (!stroke) return
    if (session.poppedAtMs >= 0) {
        session.bubbles.clear()
        session.poppedAtMs = -1L
    }
    session.strokes++
    foam(session, mouth, spread = 30f * px, radius = 3f * px..7f * px)
    session.progress = session.strokes / TEETH_STROKES.toFloat()
    onStep(true)
}

private fun foam(session: BathSession, at: Offset, spread: Float, radius: ClosedFloatingPointRange<Float>) {
    repeat(Random.nextInt(1, 3)) {
        if (session.bubbles.size >= FOAM_MAX) session.bubbles.removeAt(0)
        session.bubbles += Bubble(
            center = at + Offset(Random.nextFloat() - 0.5f, Random.nextFloat() - 0.5f) * spread,
            radius = radius.start + Random.nextFloat() * (radius.endInclusive - radius.start),
            bornMs = session.nowMs,
            phase = Random.nextFloat() * 2f * PI.toFloat(),
        )
    }
}

// ---------- Рисунки ----------

/**
 * Пузырь пены в стиле кита: обводка ink и непрозрачный белый блик слева сверху.
 * Заливка полупрозрачная — сквозь пену чуть видно питомца, и блик на ней читается.
 */
private fun DrawScope.drawBubble(center: Offset, radius: Float, alpha: Float) {
    if (radius <= 0f || alpha <= 0f) return
    drawCircle(FinneyGlare, radius, center, alpha = alpha * 0.75f)
    drawCircle(FinneyInk, radius, center, alpha = alpha, style = Stroke(width = maxOf(1.5f, radius * 0.14f)))
    drawOval(FinneyGlare, center + Offset(-radius * 0.62f, -radius * 0.62f), Size(radius * 0.57f, radius * 0.4f), alpha = alpha)
}

/**
 * Лейка душа в стиле кита — голубая с обводкой ink и белым бликом — и струи до [bottom].
 * Нарисованной лейки у дизайнеров нет; появится — заменить картинкой.
 */
private fun DrawScope.drawShower(head: Offset, bottom: Float, now: Long, px: Float, alpha: Float) {
    val headSize = Size(56f * px, 20f * px)
    val topLeft = head - Offset(headSize.width / 2, headSize.height / 2)
    // Струи: штрихи бегут вниз, чтобы вода читалась льющейся, а не висящей.
    val dash = 10f * px
    val shift = (now % 400L) / 400f * dash * 2
    for (i in -2..2) {
        val x = head.x + i * 9f * px
        var y = head.y + headSize.height / 2 + shift
        while (y < bottom) {
            drawLine(FinneyBlue, Offset(x, y), Offset(x, minOf(y + dash, bottom)), strokeWidth = 3f * px, cap = StrokeCap.Round, alpha = alpha)
            y += dash * 2
        }
    }
    // Ручка — к правому верхнему краю, как у настоящей лейки.
    val handle = head + Offset(headSize.width / 2 - 6f * px, -headSize.height / 2)
    drawLine(FinneyInk, handle, handle + Offset(22f * px, -36f * px), strokeWidth = 10f * px, cap = StrokeCap.Round, alpha = alpha)
    drawLine(FinneyBlue, handle, handle + Offset(22f * px, -36f * px), strokeWidth = 5f * px, cap = StrokeCap.Round, alpha = alpha)
    val corner = CornerRadius(headSize.height / 2)
    drawRoundRect(FinneyBlue, topLeft, headSize, corner, alpha = alpha)
    drawRoundRect(FinneyInk, topLeft, headSize, corner, style = Stroke(width = 2.5f * px), alpha = alpha)
    drawOval(FinneyGlare, topLeft + Offset(8f * px, 4f * px), Size(14f * px, 5f * px), alpha = alpha)
}
