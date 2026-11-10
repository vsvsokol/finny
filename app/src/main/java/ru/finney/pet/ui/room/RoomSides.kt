package ru.finney.pet.ui.room

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.painterResource
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Что сцена сообщает бокам ([RoomSides]): какой сейчас задник, отражена ли комната
 * (игровая — зеркало спальни). Заполняет RoomScene.
 */
@Stable
class RoomSidesState {
    internal var wall by mutableStateOf<WallSkin?>(null)

    /** Ось отражения игровой, доля холста; null — комната не отражена. */
    internal var mirrorAxis by mutableStateOf<Float?>(null)

}

/**
 * Комната по бокам от сцены в широком режиме (компьютер). Сцена — портретный холст 3:5
 * по центру, а по бокам продолжается её задник:
 *
 * - обои повторяются до краёв, через раз зеркально. Задники нарисованы не бесшовными
 *   (гирлянды цирка и листики леса у краёв обрезаны), а отражение у шва совпадает само
 *   с собой — стык не виден ни у одной темы. Копии считаются от холста, как он стоит
 *   на экране: у игровой он отражён и сдвинут;
 * - пол — тот же задник, растянутый по ширине, и под самой сценой тоже: её собственный
 *   пол в широком режиме не рисуется, иначе две разные перспективы сходились бы швом.
 *
 * Свет на бока кладёт сама сцена: в широком режиме её свет считается и за кадром (roomLight).
 * Ночь на боках рисует сама сцена — её затемнение в широком режиме во всю ширину.
 */
@Composable
internal fun RoomSides(state: RoomSidesState, modifier: Modifier = Modifier) {
    val wall = state.wall ?: return
    val painter = painterResource(wall.back.image)
    Canvas(modifier.fillMaxSize()) {
        val h = size.height
        val cw = h * Room.CANVAS_RATIO
        val axis = state.mirrorAxis
        // Где холст сцены на экране и смотрит ли он зеркально.
        val origin = (size.width - cw) / 2f + if (axis != null) (2f * axis - 1f) * cw else 0f
        val flipped = axis != null
        val seam = h * wall.floorTop

        // Пол: задник, растянутый по ширине, — и под сценой; виден только ниже шва.
        clipRect(top = seam) {
            withTransform({
                scale(size.width / cw, 1f, Offset.Zero)
                if (flipped) scale(-1f, 1f, Offset(cw / 2f, 0f))
            }) { with(painter) { draw(Size(cw, h)) } }
        }

        // Обои: копии холста влево и вправо, только выше шва.
        clipRect(bottom = seam) {
            val first = floor(-origin / cw).toInt()
            val last = ceil((size.width - origin) / cw).toInt() - 1
            for (k in first..last) {
                withTransform({
                    translate(origin + k * cw, 0f)
                    if ((k % 2 != 0) != flipped) scale(-1f, 1f, Offset(cw / 2f, 0f))
                }) {
                    clipRect(0f, 0f, cw, h) { with(painter) { draw(Size(cw, h)) } }
                }
            }
        }

    }
}
