package ru.finney.pet.ui

import android.graphics.Color as AndroidColor
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.R
import ru.finney.pet.ui.room.FlyingToy
import ru.finney.pet.ui.room.Interiors
import ru.finney.pet.ui.room.PartSwap
import ru.finney.pet.ui.room.RelRect
import ru.finney.pet.ui.room.RoomLighting
import ru.finney.pet.ui.room.ToyFlight
import ru.finney.pet.ui.room.ToyFloor
import ru.finney.pet.ui.room.toyBoxRect
import ru.finney.pet.ui.room.toyBoxRim
import ru.finney.pet.ui.room.toySlots
import java.io.File

/** Реальный рисунок не должен внезапно терять часть силуэта посреди полёта. */
@RunWith(AndroidJUnit4::class)
class ToyBoxFlightClippingTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    @Test fun enteringToyClearsTheRimBeforePassingBehindIt() = checkDepthSwitch(into = true)

    @Test fun leavingToyClearsTheRimBeforePassingInFrontOfIt() = checkDepthSwitch(into = false)

    private fun checkDepthSwitch(into: Boolean) {
        val floor = ToyFloor(0.1f, 0.9f)
        val box = toyBoxRect(floor)
        val rim = toyBoxRim(box)
        val toy = toySlots(1, floor.left, floor.right).first()
        val centre = (box.left + box.right) / 2f
        fun at(bottom: Float) = RelRect(
            centre - toy.width / 2f, bottom - toy.height,
            centre + toy.width / 2f, bottom,
        )
        val inside = at(rim + toy.height * 0.85f)
        val flight = ToyFlight(
            from = if (into) at(0.70f) else inside,
            to = if (into) inside else at(0.775f),
            into = into,
            lift = 0f,
            spin = if (into) 20f else 360f,
        )
        val lighting = RoomLighting(
            lamp = mutableStateOf(1f),
            breath = mutableStateOf(1f),
            ufo = { null },
            window = false,
            walls = PartSwap(Interiors.Default.wall),
            windows = PartSwap(Interiors.Default.window),
            lamps = PartSwap(Interiors.Default.lamp),
        )
        val threshold = if (into) 0.5f else 0.3f
        runBlocking { flight.progress.snapTo(threshold - 0.001f) }
        compose.setContent {
            view = LocalView.current
            Box(Modifier.requiredSize(300.dp, 500.dp).background(Color.Black).testTag("flight")) {
                FlyingToy(
                    art = R.drawable.item_toy_ball,
                    flight = flight,
                    box = box,
                    canvasW = 300.dp,
                    canvasH = 500.dp,
                    lighting = lighting,
                    mirrored = false,
                )
            }
        }
        val direction = if (into) "enter" else "leave"
        val before = visibleToyPixels("$direction-before")
        compose.runOnIdle { runBlocking { flight.progress.snapTo(threshold + 0.001f) } }
        val after = visibleToyPixels("$direction-after")
        assertTrue("Игрушка должна быть видна перед проверкой: $before", before > 100)
        val ratio = after.toFloat() / before
        assertTrue(
            "На границе глубины силуэт обрезается скачком: до=$before, после=$after, отношение=$ratio",
            ratio in 0.9f..1.1f,
        )
    }

    private fun visibleToyPixels(name: String): Int {
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("flight").fetchSemanticsNode().boundsInRoot
        val origin = IntArray(2)
        compose.runOnIdle { view.getLocationOnScreen(origin) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // waitForIdleSync ждёт очередь UI, а screenshot ещё может вернуть предыдущий
        // аппаратный кадр. Даём SurfaceFlinger завершить кадр, затем проверяем фон.
        SystemClock.sleep(150)
        val left = origin[0] + bounds.left.toInt()
        val top = origin[1] + bounds.top.toInt()
        val right = origin[0] + bounds.right.toInt()
        val bottom = origin[1] + bounds.bottom.toInt()
        var bitmap: Bitmap? = null
        for (attempt in 0 until 20) {
            val captured = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            val corners = listOf(
                captured.getPixel(left + 2, top + 2),
                captured.getPixel(right - 3, top + 2),
                captured.getPixel(left + 2, bottom - 3),
                captured.getPixel(right - 3, bottom - 3),
            )
            if (corners.all { AndroidColor.red(it) < 8 && AndroidColor.green(it) < 8 && AndroidColor.blue(it) < 8 }) {
                bitmap = captured
                break
            }
            captured.recycle()
            SystemClock.sleep(100)
        }
        val frame = checkNotNull(bitmap) { "Не дождались чёрного фона тестовой сцены" }
        try {
            var visible = 0
            for (y in top until bottom) {
                for (x in left until right) {
                    val pixel = frame.getPixel(x, y)
                    if (AndroidColor.red(pixel) > 8 || AndroidColor.green(pixel) > 8 || AndroidColor.blue(pixel) > 8) visible++
                }
            }
            val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "playtest-clipping").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return visible
        } finally {
            frame.recycle()
        }
    }
}
