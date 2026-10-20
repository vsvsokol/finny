package ru.finney.pet.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.finney.pet.ui.motion.motionEnabled
import ru.finney.pet.ui.period.Star
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx

// Звезда в моменте (разбор 20.10). Условие уровня выполняется посреди игры — Финни сыт
// и чист, взнос по плану, игра пройдена, — а ребёнок узнавал об этом, только открыв
// панель уровня. Теперь звезда вылетает из питомца и летит в дугу значка уровня,
// значок подпрыгивает и звенит. Дуга сама по себе уже показывает, сколько выполнено.

/** Условия, за которые летит звезда: уход, план, копилка и игра уровня — как сегменты дуги. */
private fun conditions(state: HomeUiState.Ready): List<Boolean> = listOf(
    state.check.needsCovered,
    state.check.planMatched,
    state.check.savingsAdded,
    state.check.levelTaskId != null && state.check.gamePassed,
)

/**
 * Слой летящих звёзд. [from] — откуда вылетает (питомец), [to] — значок уровня.
 * Что было выполнено, помнится через смену экранов: игру проходят на другом экране,
 * и звезда прилетает, когда ребёнок вернулся в комнату. Новый уровень — новый счёт.
 */
@Composable
internal fun LevelStars(
    state: HomeUiState.Ready,
    from: () -> Rect,
    to: () -> Rect,
    onArrive: () -> Unit,
) {
    val sounds = LocalSounds.current
    val arrive by rememberUpdatedState(onArrive)
    val now = conditions(state)
    var seen by rememberSaveable { mutableStateOf(state.periodNumber to now) }
    val flights = remember { mutableStateListOf<Animatable<Float, *>>() }

    LaunchedEffect(state.periodNumber, now) {
        val (period, before) = seen
        seen = state.periodNumber to now
        // Новый уровень или первый показ — звёзд за старое не выпускаем.
        if (period != state.periodNumber) return@LaunchedEffect
        val fresh = now.indices.count { now[it] && !before.getOrElse(it) { false } }
        if (fresh == 0) return@LaunchedEffect
        if (!motionEnabled()) { repeat(fresh) { arrive() }; return@LaunchedEffect }
        repeat(fresh) { i ->
            val flight = Animatable(0f)
            flights.add(flight)
            launch {
                kotlinx.coroutines.delay(i * 250L)
                sounds.play(Sfx.Whoosh)
                flight.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                flights.remove(flight)
                sounds.play(Sfx.Star)
                arrive()
            }
        }
    }

    if (flights.isEmpty()) return
    Box(Modifier.fillMaxSize()) {
        flights.forEach { flight ->
            Star(
                filled = true,
                modifier = Modifier
                    .size(StarSize.dp)
                    .graphicsLayer {
                        val a = from().center
                        val b = to().center
                        val t = flight.value
                        // Дуга: сначала вверх из питомца, потом к значку.
                        val lift = Offset(0f, -size.height * 3f) * (4f * t * (1f - t))
                        val p = a + (b - a) * t + lift
                        translationX = p.x - size.width / 2
                        translationY = p.y - size.height / 2
                        val s = 1.4f - 0.6f * t
                        scaleX = s; scaleY = s
                        rotationZ = 360f * t
                        alpha = if (t < 0.97f) 1f else (1f - t) / 0.03f
                    },
            )
        }
    }
}

private const val StarSize = 46
