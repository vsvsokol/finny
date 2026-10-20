package ru.finney.pet.ui.pet

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.motion.motionEnabled
import kotlin.random.Random

/**
 * Анимации питомца.
 *
 * Покоя — бесконечная: дыхание (тело чуть тянется вверх и сжимается),
 * покачивание корпуса и рук, моргание. Идёт всегда, отдельно дёргать не нужно.
 *
 * Радости — по событию [playJoy]: приседание, прыжок с вытягиванием,
 * руки вверх, поджатые ноги, приземление с пружинкой.
 *
 * Еды — [openMouth], пока еда летит к питомцу, и [playEat], когда поймал.
 * Мытья — [playGiggle]: от мыла щекотно.
 * Игры с игрушкой — [lookAt]: тянется к игрушке в руке ребёнка.
 * Грусти — [playSigh]: вздыхает и оседает, руки повисают.
 *
 * Когда с питомцем давно ничего не делали, он сам наклоняет голову, оглядывается
 * или переминается — см. [idle]. Запускает это [rememberPetAnimation].
 *
 * Без анимаций (ТЗ п. 3.6) цепочки `play*` не начинаются: при нулевой скорости каждый
 * шаг длится кадр, и питомец мелькал бы промежуточными позами. Одиночные переходы
 * ([lookAt], [openMouth]) просто встают в конечное положение.
 */
@Stable
class PetAnimation internal constructor(private val scope: CoroutineScope) {

    /** 0 — стоит на земле, 1 — верхняя точка прыжка. */
    internal val lift = Animatable(0f)

    /** 0 — обычная поза, 1 — максимальное приседание перед прыжком. */
    internal val crouch = Animatable(0f)

    /**
     * Вытянутость в прыжке: 0 — как обычно, 1 — вытянут сильнее всего. Отдельно от [lift]:
     * вытягивается он, пока летит быстро, — при отрыве и падении, — а в верхней точке
     * и на земле нет. Когда вытянутость шла за высотой, у земли она пропадала раньше
     * приседания, и между ними мелькала обычная поза: прыжок выглядел деревянным.
     */
    internal val stretch = Animatable(0f)

    /** Наклон головы в градусах, по часовой — положительный. Только в [idle]. */
    internal val headTilt = Animatable(0f)

    /** 0 — рот настроения, 1 — открыт навстречу еде. */
    internal val mouth = Animatable(0f)

    /** Покачивание от щекотки: −1..1, наклон в обе стороны. */
    internal val wiggle = Animatable(0f)

    /** Куда питомец тянется: −1 — влево, 1 — вправо, 0 — прямо. */
    internal val lean = Animatable(0f)

    /** Вздох: 0 — стоит как обычно, 1 — осел и опустил руки. */
    internal val droop = Animatable(0f)

    private var job: Job? = null
    private var mouthJob: Job? = null
    private var wiggleJob: Job? = null
    private var leanJob: Job? = null
    private var droopJob: Job? = null

    /** Прыжок от начала приседания до касания земли. Пружинка после касания сюда не входит. */
    private var airborne = false

    /** Падает вниз из верхней точки. */
    private var falling = false

    /** Когда питомца в последний раз о чём-то просили, по [SystemClock.uptimeMillis]. */
    private var pokedAt = SystemClock.uptimeMillis()

    private fun poke() {
        pokedAt = SystemClock.uptimeMillis()
    }

    /**
     * Потянуться к игрушке: [side] — где она, от −1 (слева) до 1 (справа). Глаза у питомца
     * нарисованы, водить ими нельзя, поэтому «смотрит» он всем телом. 0 — выпрямиться.
     */
    fun lookAt(side: Float) {
        poke()
        leanJob?.cancel()
        leanJob = scope.launch {
            lean.animateTo(side.coerceIn(-1f, 1f), spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
        }
    }

    /** Грустно: медленно оседает, стоит так и нехотя выпрямляется. Пока вздыхает, новый не начинается. */
    fun playSigh() {
        poke()
        if (droopJob?.isActive == true) return
        droopJob = scope.launch {
            if (!motionEnabled()) return@launch
            droop.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
            delay(900)
            droop.animateTo(0f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
        }
    }

    /** Еда летит к питомцу — рот открывается навстречу; мимо — закрывается. */
    fun openMouth(open: Boolean) {
        poke()
        mouthJob?.cancel()
        mouthJob = scope.launch {
            mouth.animateTo(
                targetValue = if (open) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    /** Поймал: рот захлопывается, три жевка с приседанием, потом радость. */
    fun playEat() {
        poke()
        mouthJob?.cancel()
        mouthJob = scope.launch {
            // Рот мог остаться открытым навстречу еде — закрыть без жевания и прыжка.
            if (!motionEnabled()) {
                mouth.snapTo(0f)
                return@launch
            }
            mouth.animateTo(0f, tween(durationMillis = 90))
            repeat(3) {
                launch {
                    crouch.animateTo(0.3f, tween(durationMillis = 110))
                    crouch.animateTo(0f, tween(durationMillis = 130))
                }
                mouth.animateTo(0.45f, tween(durationMillis = 110))
                mouth.animateTo(0f, tween(durationMillis = 130))
            }
            playJoy()
        }
    }

    /** Щекотно от мыла. Пока покачивание идёт, новые касания его не перезапускают. */
    fun playGiggle() {
        poke()
        if (wiggleJob?.isActive == true) return
        wiggleJob = scope.launch {
            if (!motionEnabled()) return@launch
            for (target in floatArrayOf(1f, -0.8f, 0.5f, -0.25f, 0f)) {
                wiggle.animateTo(target, tween(durationMillis = 70))
            }
        }
    }

    /**
     * Прыжок радости. Пока прыгает, новые касания его не перезапускают: раньше каждое касание
     * обрывало прыжок и начинало с приседания, а высота оставалась прежней — при частых
     * касаниях питомец зависал в воздухе.
     *
     * Пружинку после приземления новый прыжок обрывает. Иначе при прогулке по залу
     * (`RoomScene`, скачок раз в 670 мс) каждый второй скачок попадал на пружинку
     * и пропускался, и питомец ехал по полу, не подпрыгивая.
     *
     * Сумма приседания и полёта — 670 мс, как у скачка в `RoomScene`: менять вместе.
     * Следующий скачок прогулки просят ровно в момент касания, и стоит кадру опоздать,
     * прыжок ещё «в воздухе» — поэтому у самой земли новый уже можно начать.
     */
    fun playJoy() {
        poke()
        if (airborne && !(falling && lift.value < LANDING_LIFT)) return
        job?.cancel()
        job = scope.launch {
            if (!motionEnabled()) return@launch
            airborne = true
            try {
                crouch.animateTo(1f, tween(durationMillis = 140, easing = FastOutLinearInEasing))
                // Отрыв: из приседания сразу в вытянутую позу, без остановки в обычной.
                launch { crouch.animateTo(0f, tween(durationMillis = 90)) }
                launch {
                    stretch.animateTo(1f, tween(durationMillis = 90))
                    stretch.animateTo(0f, tween(durationMillis = 160, easing = LinearOutSlowInEasing))
                }
                // Вверх тормозит, вниз разгоняется — так летит брошенное.
                lift.animateTo(1f, tween(durationMillis = 250, easing = LinearOutSlowInEasing))
                launch { stretch.animateTo(0.8f, tween(durationMillis = 280, easing = FastOutLinearInEasing)) }
                falling = true
                lift.animateTo(0f, tween(durationMillis = 280, easing = FastOutLinearInEasing))
                airborne = false
                falling = false
                // Касание: вытянутость сразу переходит в приседание.
                launch { stretch.animateTo(0f, tween(durationMillis = 70)) }
                crouch.animateTo(0.55f, tween(durationMillis = 70))
                crouch.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            } finally {
                airborne = false
                falling = false
                // Прыжок оборвала другая анимация (жевание берёт приседание) — приземлиться.
                if (lift.value != 0f) scope.launch { lift.animateTo(0f, tween(durationMillis = 200)) }
                if (stretch.value != 0f) scope.launch { stretch.animateTo(0f, tween(durationMillis = 120)) }
            }
        }
    }

    /**
     * Сам себя занимает, пока с ним ничего не делают: раз в несколько секунд — наклон
     * головы, оглядывание или переминание. Паузы случайные, как у моргания и прогулки:
     * по часам он выглядел бы заведённым. Пока идёт любая другая анимация — ждёт.
     */
    internal suspend fun idle() {
        while (true) {
            delay(Random.nextLong(IDLE_PAUSE_MS.first, IDLE_PAUSE_MS.last))
            if (!motionEnabled()) continue
            if (SystemClock.uptimeMillis() - pokedAt < IDLE_AFTER_MS) continue
            if (listOf(job, mouthJob, wiggleJob, leanJob, droopJob).any { it?.isActive == true }) continue
            val side = if (Random.nextBoolean()) 1f else -1f
            val roll = Random.nextFloat()
            // Отдельной задачей: касание перехватывает приседание, и прерванная анимация
            // бросает отмену. Здесь она гасит только это движение, а не весь цикл.
            scope.launch {
                when {
                    roll < 0.5f -> {
                        headTilt.animateTo(side * Random.nextFloat() * 3f + side * 5f, HeadSpring)
                        delay(Random.nextLong(900, 1700))
                        headTilt.animateTo(0f, HeadSpring)
                    }
                    roll < 0.8f -> {
                        headTilt.animateTo(side * 6f, HeadSpring)
                        delay(450)
                        headTilt.animateTo(-side * 6f, HeadSpring)
                        delay(450)
                        headTilt.animateTo(0f, HeadSpring)
                    }
                    else -> repeat(2) {
                        crouch.animateTo(0.25f, tween(durationMillis = 180, easing = FastOutSlowInEasing))
                        crouch.animateTo(0f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
                    }
                }
            }.join()
        }
    }
}

// Сколько с питомцем ничего не должно происходить, прежде чем он займёт себя сам,
// и паузы между такими движениями. Прогулка по залу считается делом — скачок идёт
// через [PetAnimation.playJoy].
// Ниже этой высоты падающий питомец уже «у земли» и новый прыжок можно начать.
// Последние ~40 мс падения: хватает на опоздавший кадр-другой и не даёт взлетать
// касаниями посреди прыжка.
private const val LANDING_LIFT = 0.3f

private const val IDLE_AFTER_MS = 5_000L
private val IDLE_PAUSE_MS = 4_000L..9_000L

// Голова наклоняется мягко и чуть пружинит на месте: без пружины выглядело механически.
private val HeadSpring = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)

@Composable
fun rememberPetAnimation(): PetAnimation {
    val scope = rememberCoroutineScope()
    val animation = remember(scope) { PetAnimation(scope) }
    LaunchedEffect(animation) { animation.idle() }
    return animation
}

/**
 * Поза как функция — то, что нужно передавать в [PetView].
 *
 * Здесь наружу отдаётся не значение, а способ его получить. Анимируемые величины
 * читаются только внутри возвращённой лямбды, а её [PetView] вызывает на отрисовке.
 * Поэтому кадры анимации вообще не задевают композицию: слои питомца достаются
 * из ресурсов один раз.
 *
 * Пользоваться так: `PetView(pose = rememberPoseProvider(animation))`.
 */
@Composable
fun rememberPoseProvider(animation: PetAnimation): () -> PetPose {
    val idle = rememberInfiniteTransition(label = "idle")
    val breath = idle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val sway = idle.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sway",
    )

    val lid = rememberBlink()
    // Без анимаций бесконечная встаёт в крайнее значение: питомец стоял бы наклонённым.
    // Покой — прямо и на вдохе.
    val animations = LocalAnimations.current

    // Лямбда запоминается один раз: все источники кадров — стабильные объекты,
    // и значения из них берутся в момент вызова, то есть при отрисовке.
    return remember(animation, breath, sway, lid, animations) {
        {
            composePose(
                if (animations) breath.value else 1f, if (animations) sway.value else 0f,
                animation.lift.value, animation.crouch.value,
                lid.value, animation.mouth.value, animation.wiggle.value,
                animation.lean.value, animation.droop.value,
                animation.stretch.value, animation.headTilt.value,
            )
        }
    }
}

// Как у живого моргания: веко падает быстро и с разгоном, чуть задерживается
// и поднимается медленнее, с торможением. Всё вместе — около четверти секунды:
// короче глаз не успевает увидеть движение, дольше — питомец выглядит сонным.
private const val LID_CLOSE_MS = 80
private const val LID_HOLD_MS = 50L
private const val LID_OPEN_MS = 130

/**
 * Веко опускается через случайные 2–5.5 с, иногда дважды подряд.
 * Ровный ритм выглядит механически, поэтому паузы разные. У каждого питомца
 * на экране свой ритм: двое на экране подбора моргают не хором.
 */
@Composable
private fun rememberBlink(): Animatable<Float, AnimationVector1D> {
    val lid = remember { Animatable(0f) }
    LaunchedEffect(lid) {
        while (true) {
            delay(Random.nextLong(2000L, 5500L))
            // Без анимаций моргание — веко на один кадр: глаза просто открыты.
            if (!motionEnabled()) continue
            repeat(if (Random.nextFloat() < 0.2f) 2 else 1) {
                lid.animateTo(1f, tween(LID_CLOSE_MS, easing = FastOutLinearInEasing))
                delay(LID_HOLD_MS)
                lid.animateTo(0f, tween(LID_OPEN_MS, easing = LinearOutSlowInEasing))
            }
        }
    }
    return lid
}

/** Общая арифметика позы: одна на [rememberPoseProvider] и [currentPose]. */
private fun composePose(
    breath: Float,
    sway: Float,
    lift: Float,
    crouch: Float,
    lid: Float,
    mouth: Float,
    wiggle: Float,
    lean: Float = 0f,
    droop: Float = 0f,
    stretch: Float = 0f,
    headTilt: Float = 0f,
): PetPose =
    // Дыхание и прыжок — это объём: что прибавилось по высоте, то убавилось по ширине.
    PetPose(
        scaleX = 1f - breath * 0.008f + crouch * 0.07f - stretch * 0.035f + droop * 0.03f,
        scaleY = 1f + breath * 0.016f - crouch * 0.10f + stretch * 0.055f - droop * 0.06f,
        offsetY = (-38f * lift + 8f * crouch + 4f * droop).dp,
        tilt = sway * 0.7f + wiggle * 5f + lean * 8f,
        // Левая рука по часовой — вверх, правая — против. Знаки были наоборот: в прыжке
        // руки шли вниз, к животу, а во вздохе поднимались. Выше 25° под поднятой рукой
        // открывается не нарисованное туловище — см. Limb в PetView.
        // Покачивание рук в прыжке гаснет: оно сдвигает обе руки в одну сторону,
        // и вверху одна рука стояла на 6° выше другой.
        leftHand = sway * 3f * (1f - lift) + lift * 25f - droop * 14f,
        rightHand = sway * 3f * (1f - lift) - lift * 25f + droop * 14f,
        leftLeg = -lift * 8f,
        rightLeg = lift * 8f,
        lid = lid,
        mouth = mouth,
        headTilt = headTilt,
    )

/**
 * Складывает анимацию покоя и текущий кадр прыжка в одну позу.
 *
 * Вызывающий пересобирается каждый кадр — в разметке экранов это дорого.
 * Для показа питомца берите [rememberPoseProvider]; эта остаётся для `@Preview`
 * и мест, где поза нужна разово.
 */
@Composable
fun PetAnimation.currentPose(): PetPose {
    val idle = rememberInfiniteTransition(label = "idle")
    val breath by idle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val sway by idle.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sway",
    )

    val animations = LocalAnimations.current

    // Моргание сюда не входит: превью — один неподвижный кадр, глаза в нём открыты.
    return composePose(
        if (animations) breath else 1f, if (animations) sway else 0f, lift.value, crouch.value,
        lid = 0f, mouth.value, wiggle.value, lean.value, droop.value, stretch.value, headTilt.value,
    )
}
