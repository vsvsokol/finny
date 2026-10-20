package ru.finney.pet.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.FinneyInk

// «Начать игру» заряжается: вокруг кнопки набирается кольцо, тон щелчков растёт,
// на полном — вспышка, и только потом переход (плейтест 28.09: начало игры должно
// ощущаться весомым). Кольцо и тон — те же, что у «−/+» с удержанием.
//
// Держать не обязательно: касание запускает зарядку, и она доходит сама за то же
// время. Маленький ребёнок и TalkBack жмут коротко — их кнопка не должна подводить.
// Это не таймер, который торопит (ТЗ п. 8.1): ждать приходится меньше секунды,
// и ничего не сгорает.

/** За сколько заряжается кнопка. */
private const val ChargeMs = 800L

/** Щелчок на каждый такой отрезок зарядки — тон растёт с каждым. */
private const val TickEveryMs = 70L

/** Вспышка на полном заряде. */
private const val FlashMs = 260

/** Насколько кнопка набухает к концу зарядки. */
private const val SwellScale = 0.05f

/**
 * Основная кнопка, которую надо «зарядить», — для одного действия, которое не
 * повторяется: начать игру. Остальные кнопки жмутся сразу.
 *
 * [onCharged] зовётся один раз, после вспышки. С выключенными анимациями —
 * сразу по нажатию, без кольца.
 */
@Composable
fun ChargeButton(
    text: String,
    onCharged: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * Сама кнопка под кольцом. Своя — на экранах со своим обликом (знакомство):
     * зарядка, звук и кольцо остаются здесь, кнопка только рисуется. Звук она не играет.
     */
    button: @Composable (onClick: () -> Unit, interaction: MutableInteractionSource, modifier: Modifier) -> Unit =
        { onClick, interaction, buttonModifier ->
            FinneyButton(
                text = text,
                onClick = onClick,
                enabled = enabled,
                sound = null,
                interactionSource = interaction,
                modifier = buttonModifier,
            )
        },
) {
    val sounds = LocalSounds.current
    val haptics = LocalHapticFeedback.current
    val animations = LocalAnimations.current
    val charged by rememberUpdatedState(onCharged)
    val canCharge by rememberUpdatedState(enabled)
    val interaction = remember { MutableInteractionSource() }
    val charge = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val run = remember { ChargeRun() }

    // Кольцо идёт по кадрам, как у «−/+»: с выключенными анимациями animateTo
    // встал бы в конец сразу. Но при выключенных сюда и не заходим.
    fun start() {
        if (run.job?.isActive == true) return
        run.job = scope.launch {
            val begin = withFrameMillis { it }
            charge.snapTo(0f)
            flash.snapTo(0f)
            var tick = 0
            while (charge.value < 1f) {
                val now = withFrameMillis { it }
                val elapsed = now - begin
                charge.snapTo((elapsed.toFloat() / ChargeMs).coerceAtMost(1f))
                if (elapsed >= tick * TickEveryMs) {
                    sounds.play(Sfx.Tap, pitchFor(tick * 3))
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    tick++
                }
            }
            sounds.play(Sfx.LevelUp)
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            flash.animateTo(1f, tween(FlashMs))
            charged()
        }
    }

    LaunchedEffect(interaction, animations) {
        if (!animations) return@LaunchedEffect
        interaction.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> if (canCharge) start()
                // Палец увели с кнопки — передумал. Отпустил на кнопке — зарядка идёт дальше.
                is PressInteraction.Cancel -> if (flash.value == 0f) {
                    run.job?.cancel()
                    scope.launch { charge.animateTo(0f, tween(180)) }
                }
                else -> Unit
            }
        }
    }

    button(
        {
            if (animations) {
                // TalkBack жмёт без касания — Press не приходит, зарядку запускает клик.
                start()
            } else {
                sounds.play(Sfx.Tap)
                charged()
            }
        },
        interaction,
        modifier
            .graphicsLayer {
                val s = 1f + SwellScale * charge.value * (1f - flash.value)
                scaleX = s
                scaleY = s
            }
            .drawWithCache {
                // Кольцо повторяет «стадион» кнопки снаружи обводки и начинается сверху
                // посередине — как у круглых «−/+» от двенадцати часов.
                val width = RingDp * density
                val inset = size.height / 12f + (RingGapDp + RingDp / 2) * density
                val rect = Rect(-inset, -inset, size.width + inset, size.height + inset)
                val track = stadium(rect)
                val measure = PathMeasure().apply { setPath(track, false) }
                val part = Path()
                onDrawBehind {
                    val c = charge.value
                    if (c <= 0f) return@onDrawBehind
                    val f = flash.value
                    drawPath(track, FinneyInk.copy(alpha = 0.18f * (1f - f)), style = Stroke(width))
                    part.reset()
                    measure.getSegment(0f, measure.length * c, part, true)
                    drawPath(part, FinneyInk.copy(alpha = 1f - f), style = Stroke(width, cap = StrokeCap.Round))
                    if (f > 0f) {
                        // Вспышка — белая волна, которая расходится от кольца и тает.
                        val grow = f * 14f * density
                        val wave = stadium(Rect(rect.left - grow, rect.top - grow, rect.right + grow, rect.bottom + grow))
                        drawPath(wave, Color.White.copy(alpha = 1f - f), style = Stroke(width * (3f - 2f * f)))
                    }
                }
            },
    )
}

/** Идущая зарядка. Не состояние Compose: перерисовывать из-за неё нечего. */
private class ChargeRun {
    var job: Job? = null
}

/** Контур «стадиона» по часовой стрелке от середины верхнего края. */
private fun stadium(r: Rect): Path = Path().apply {
    val radius = r.height / 2f
    val cx = r.center.x
    moveTo(cx, r.top)
    lineTo(r.right - radius, r.top)
    arcTo(Rect(r.right - 2 * radius, r.top, r.right, r.bottom), -90f, 180f, false)
    lineTo(r.left + radius, r.bottom)
    arcTo(Rect(r.left, r.top, r.left + 2 * radius, r.bottom), 90f, 180f, false)
    close()
}
