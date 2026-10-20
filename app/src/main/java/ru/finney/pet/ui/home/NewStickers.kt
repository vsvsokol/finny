package ru.finney.pet.ui.home

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ru.finney.pet.ui.components.SpeechLeadColor
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.period.NewPlace
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.StrokeRegular

// Наклейка «Новое» на кнопке магазина, игр и копилки (разбор 20.10). Итог уровня говорит,
// что открылось и где, а на главном это место ничем не отмечено — ребёнок забывал заглянуть.
// Наклейка висит, пока туда не зашли на этом уровне.
//
// Что уже видели — в отдельных SharedPreferences интерфейса, а не в сохранении игры: это
// не игровое состояние, расчёты от него не зависят, и терять его при сбросе не жалко.
// Помним уровень, на котором заходили: другой уровень — снова «новое», так и после
// новой игры с первого уровня наклейки не пропадут.

internal class NewSeen(private val prefs: SharedPreferences) {
    // Счётчик правок: чтение через него заставляет Compose перерисовать наклейку после mark().
    private var version by mutableIntStateOf(0)

    fun seen(place: NewPlace, level: Int): Boolean {
        version
        return prefs.getInt(place.name, 0) == level
    }

    fun mark(place: NewPlace, level: Int) {
        if (prefs.getInt(place.name, 0) == level) return
        prefs.edit().putInt(place.name, level).apply()
        version++
    }
}

@Composable
internal fun rememberNewSeen(): NewSeen {
    val app = LocalContext.current.applicationContext
    return remember { NewSeen(app.getSharedPreferences("new_stickers", Context.MODE_PRIVATE)) }
}

/** Слой наклеек поверх главного: каждая — у верхнего угла своей кнопки, ближе к середине экрана. */
@Composable
internal fun NewStickers(places: Set<NewPlace>, bounds: (NewPlace) -> Rect, modifier: Modifier = Modifier) {
    if (places.isEmpty()) return
    Box(modifier.fillMaxSize()) {
        places.forEach { place ->
            val button = bounds(place)
            if (button == Rect.Zero) return@forEach
            NewSticker(
                Modifier.layout { measurable, constraints ->
                    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(p.width, p.height) {
                        val toRight = button.center.x < constraints.maxWidth / 2
                        val x = if (toRight) button.right - p.width * 0.45f else button.left - p.width * 0.55f
                        p.place(IntOffset(x.toInt(), (button.top - p.height * 0.35f).toInt()))
                    }
                },
            )
        }
    }
}

@Composable
private fun NewSticker(modifier: Modifier = Modifier) {
    val animations = LocalAnimations.current
    val pop = remember { Animatable(if (animations) 0f else 1f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow)) }
    val wobble = if (animations) {
        rememberInfiniteTransition(label = "newSticker").animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = 2400
                    0f at 0; -10f at 300; 8f at 600; 0f at 900; 0f at 2400
                },
                RepeatMode.Restart,
            ),
            label = "wobble",
        ).value
    } else 0f
    val shape = RoundedCornerShape(50)
    Text(
        "Новое",
        style = MaterialTheme.typography.bodyLarge,
        color = SpeechLeadColor,
        modifier = modifier
            .graphicsLayer {
                scaleX = pop.value; scaleY = pop.value
                rotationZ = wobble
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .clip(shape)
            .background(Color.White)
            .drawBehind {
                // Сиреневая полоса по низу — как у пузыря и «Ты здесь».
                val h = 3.dp.toPx()
                drawRect(StickerShade, topLeft = Offset(0f, size.height - h - StrokeRegular.toPx()), size = size.copy(height = h + StrokeRegular.toPx()))
            }
            .border(StrokeRegular, FinneyInk, shape)
            .padding(start = 10.dp, end = 10.dp, bottom = 3.dp),
    )
}

private val StickerShade = Color(0xFFEDE6FF)
