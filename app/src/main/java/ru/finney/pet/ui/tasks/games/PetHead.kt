package ru.finney.pet.ui.tasks.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.motion.motionEnabled
import ru.finney.pet.ui.pet.PetAnimation
import ru.finney.pet.ui.pet.PetMood
import ru.finney.pet.ui.pet.PetView
import ru.finney.pet.ui.pet.rememberPoseProvider
import ru.finney.pet.ui.theme.FinneyBlue
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyPink
import ru.finney.pet.ui.theme.FinneyYellow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// В мини-играх питомец — голова-смайлик: голова из арта, а эмоцию добавляют
// наклейки рядом (стиль «Арт + эффекты», выбран 20.10 по концепту «Головы Финни»).
// Туловище в играх было мелким и ничего не говорило; голова крупнее и реагирует
// на каждый ход: верно — прыжок и искры, ошибка — наклон и «?», победа — два прыжка
// и конфетти, не вышло — грустное лицо и капля.
//
// На ошибке питомец не грустит, а задумывается (ТЗ п. 2.5.12): грусть — только в итоге.

/** Реакция головы на ход или итог игры. */
enum class PetReaction { IDLE, RIGHT, OOPS, WIN, FAIL }

/**
 * Реакции питомца в игре. Игра дёргает [right], [oops], [win], [fail], а голова
 * сама прыгает, наклоняется и показывает наклейку. [oops] и [fail] держатся до [calm]
 * или следующей реакции, [right] и [win] проходят сами.
 */
@Stable
class PetReactions internal constructor(private val scope: CoroutineScope, val body: PetAnimation) {

    var current by mutableStateOf(PetReaction.IDLE)
        private set

    /** Ход наклейки текущей реакции: 0 — только началась, 1 — закончилась. */
    internal val fx = Animatable(0f)

    private var job: Job? = null

    /** Верный ход: прыжок, рот открыт, искры. */
    fun right() = start(PetReaction.RIGHT, RIGHT_MS, settle = true) {
        body.lookAt(0f)
        body.playJoy()
        flashMouth(350)
    }

    /**
     * Ошибка: наклон и «?» над головой. [side] — куда тянуться, если верный ответ
     * в стороне (−1 слева, 1 справа), 0 — просто склонить голову.
     */
    fun oops(side: Float = 0f) = start(PetReaction.OOPS, OOPS_MS, settle = false) {
        body.lookAt(if (side == 0f) -0.6f else side)
    }

    /** Победа: два прыжка и конфетти. */
    fun win() = start(PetReaction.WIN, WIN_MS, settle = true) {
        body.lookAt(0f)
        body.playJoy()
        flashMouth(900)
        scope.launch {
            delay(620)
            body.playJoy()
        }
    }

    /** Не вышло: грустное лицо, вздох и капля. Держится до [calm]. */
    fun fail() = start(PetReaction.FAIL, FAIL_MS, settle = false) {
        body.lookAt(0f)
        body.playSigh()
    }

    /** Вернуться к спокойному лицу. */
    fun calm() {
        job?.cancel()
        body.lookAt(0f)
        current = PetReaction.IDLE
        scope.launch { fx.snapTo(0f) }
    }

    private fun flashMouth(ms: Long) {
        body.openMouth(true)
        scope.launch {
            delay(ms)
            body.openMouth(false)
        }
    }

    private fun start(reaction: PetReaction, durationMs: Int, settle: Boolean, motion: () -> Unit) {
        job?.cancel()
        current = reaction
        motion()
        job = scope.launch {
            // Без анимаций (ТЗ п. 3.6) наклейка сразу в конечном виде: «?» стоит, искры не мелькают.
            if (!motionEnabled()) {
                fx.snapTo(1f)
                if (settle) current = PetReaction.IDLE
                return@launch
            }
            fx.snapTo(0f)
            fx.animateTo(1f, tween(durationMs, easing = LinearEasing))
            if (settle) current = PetReaction.IDLE
        }
    }
}

/** На сколько опустить голову в квадрате: низ головы на холсте — около 0.78 стороны. */
private const val HEAD_DROP = 0.14f

private const val RIGHT_MS = 900
private const val OOPS_MS = 1100
private const val WIN_MS = 1400
private const val FAIL_MS = 1800

@Composable
fun rememberPetReactions(body: PetAnimation = ru.finney.pet.ui.pet.rememberPetAnimation()): PetReactions {
    val scope = rememberCoroutineScope()
    return remember(scope, body) { PetReactions(scope, body) }
}

/**
 * Голова питомца с наклейками реакций. Квадрат [size] по ширине; наклейки выходят
 * за его края, но не двигают соседей. [mood] — лицо вне реакций: «Дождливый день»
 * и «Дорога к цели» грустят сами, когда без радостей.
 */
@Composable
internal fun PetHead(
    character: PetCharacter,
    size: Dp,
    reactions: PetReactions,
    modifier: Modifier = Modifier,
    mood: PetMood = PetMood.HAPPY,
    accessories: List<String> = emptyList(),
) {
    val reaction = reactions.current
    Box(modifier.widthIn(max = size).fillMaxWidth().aspectRatio(1f)) {
        PetView(
            character = character,
            accessories = accessories,
            mood = if (reaction == PetReaction.FAIL) PetMood.SAD else mood,
            pose = rememberPoseProvider(reactions.body),
            headOnly = true,
            // Голова на холсте занимает середину с запасом сверху под шляпу; без туловища
            // снизу оставалась пустота — голова опущена, чтобы стояла на «полу» квадрата.
            modifier = Modifier.fillMaxSize().graphicsLayer { translationY = this.size.height * HEAD_DROP },
        )
        if (reaction != PetReaction.IDLE) {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                val t = reactions.fx.value
                when (reaction) {
                    PetReaction.RIGHT -> sparkles(t)
                    PetReaction.WIN -> confetti(t)
                    PetReaction.FAIL -> drop(t)
                    else -> Unit
                }
            }
        }
        if (reaction == PetReaction.OOPS) {
            // «?» над головой справа: выпрыгивает, покачивается и остаётся, пока ребёнок думает.
            OutlinedText(
                "?",
                fill = FinneyYellow,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = (size.value * 0.28f).sp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(p.width, p.height) { p.place(0, -p.height / 4) }
                    }
                    .graphicsLayer {
                        val t = reactions.fx.value
                        val inT = (t / 0.25f).coerceIn(0f, 1f)
                        alpha = inT
                        val pop = if (t < 0.25f) 0.5f + 0.65f * inT else 1f + 0.15f * (1f - ((t - 0.25f) / 0.15f).coerceIn(0f, 1f))
                        scaleX = pop
                        scaleY = pop
                        translationY = (1f - inT) * 12f * density
                        // Покачивание «хм…» один раз, потом стоит ровно.
                        rotationZ = if (t in 0.4f..0.75f) 10f * sin((t - 0.4f) / 0.35f * 2 * PI.toFloat()) else 0f
                    },
            )
        }
    }
}

/** Искры вокруг головы: выскакивают по очереди, крутятся и гаснут. */
private fun DrawScope.sparkles(t: Float) {
    val spots = listOf(
        Triple(Offset(0.12f, 0.28f), 0.10f, FinneyYellow),
        Triple(Offset(0.86f, 0.18f), 0.08f, Color.White),
        Triple(Offset(0.90f, 0.70f), 0.08f, FinneyGreen),
    )
    spots.forEachIndexed { i, (at, r, color) ->
        val local = ((t - i * 0.12f) / 0.75f).coerceIn(0f, 1f)
        if (local <= 0f || local >= 1f) return@forEachIndexed
        val scale = when {
            local < 0.3f -> local / 0.3f * 1.25f
            local < 0.7f -> 1.25f - (local - 0.3f) / 0.4f * 0.25f
            else -> (1f - local) / 0.3f
        }
        val c = Offset(size.width * at.x, size.height * at.y)
        rotate(local * 70f, c) { star(c, size.width * r * scale, color) }
    }
}

/** Четырёхлучевая звёздочка с обводкой, как у наклеек в концепте. */
private fun DrawScope.star(c: Offset, r: Float, color: Color) {
    val inner = r * 0.32f
    val path = Path().apply {
        for (k in 0 until 8) {
            val a = (PI / 4 * k - PI / 2).toFloat()
            val rr = if (k % 2 == 0) r else inner
            val p = Offset(c.x + rr * cos(a), c.y + rr * sin(a))
            if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(path, color)
    drawPath(path, FinneyInk, style = Stroke(size.width * 0.014f, join = StrokeJoin.Round))
}

private val ConfettiColors = listOf(FinneyYellow, FinneyPeach, FinneyPink, FinneyGreen, FinneyBlue, Color.White)

/** Конфетти разлетается из-под головы во все стороны и гаснет. */
private fun DrawScope.confetti(t: Float) {
    val n = 12
    val origin = Offset(size.width * 0.5f, size.height * 0.55f)
    val w = size.width * 0.045f
    val h = size.width * 0.07f
    for (i in 0 until n) {
        val local = ((t - (i % 4) * 0.04f) / 0.9f).coerceIn(0f, 1f)
        if (local <= 0f || local >= 1f) continue
        val ease = FastOutSlowInEasing.transform(local)
        val a = (2 * PI * i / n + if (i % 2 == 1) 0.25 else 0.0).toFloat()
        val dist = size.width * (0.42f + (i % 3) * 0.09f)
        val p = Offset(origin.x + cos(a) * dist * ease, origin.y + (sin(a) * dist - size.width * 0.1f) * ease)
        val alpha = 1f - local
        rotate((if (i % 2 == 1) 1 else -1) * (120f + i * 20f) * ease, p) {
            val topLeft = Offset(p.x - w / 2, p.y - h / 2)
            drawRect(ConfettiColors[i % ConfettiColors.size], topLeft, Size(w, h), alpha = alpha)
            drawRect(FinneyInk, topLeft, Size(w, h), alpha = alpha, style = Stroke(size.width * 0.01f))
        }
    }
}

/** Капля у виска медленно стекает вниз и тает. */
private fun DrawScope.drop(t: Float) {
    val local = ((t - 0.15f) / 0.85f).coerceIn(0f, 1f)
    if (local <= 0f) return
    val alpha = when {
        local < 0.25f -> local / 0.25f
        else -> 1f - (local - 0.25f) / 0.75f
    }
    val r = size.width * 0.045f
    val x = size.width * 0.84f
    val y = size.height * 0.45f + size.height * 0.15f * local
    val path = Path().apply {
        moveTo(x, y - r * 2.2f)
        quadraticTo(x + r * 1.1f, y - r * 0.4f, x + r, y)
        quadraticTo(x + r, y + r, x, y + r)
        quadraticTo(x - r, y + r, x - r, y)
        quadraticTo(x - r * 1.1f, y - r * 0.4f, x, y - r * 2.2f)
        close()
    }
    drawPath(path, FinneyBlue, alpha = alpha)
    drawPath(path, FinneyInk, alpha = alpha, style = Stroke(size.width * 0.012f, join = StrokeJoin.Round))
}
