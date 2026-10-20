package ru.finney.pet.ui

import android.view.View
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.room.InteriorPart
import ru.finney.pet.ui.room.Interiors
import ru.finney.pet.ui.room.RoomScene
import ru.finney.pet.ui.room.RoomSpot
import ru.finney.pet.ui.room.focusOf
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyTheme

/** Проверяем края комнаты во время зума в обе стороны, в том числе в зеркальной игровой. */
@RunWith(AndroidJUnit4::class)
class RoomFocusCameraTest {
    private lateinit var roomView: View
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })

    @Test fun bedroomFocusKeepsRoomAcrossVisibleArea() = checkFocus(RoomSpot.LIVING)

    @Test fun mirroredPlayroomFocusKeepsRoomAcrossVisibleArea() = checkFocus(RoomSpot.PLAY)

    private fun checkFocus(spot: RoomSpot) {
        val focus = mutableStateOf(Interiors.Default.focusOf(InteriorPart.WALL))
        compose.setContent {
            FinneyTheme {
                CompositionLocalProvider(LocalAnimations provides true) {
                    roomView = LocalView.current
                    RoomScene(
                        spot = spot,
                        modifier = Modifier.requiredSize(360.dp, 740.dp).testTag("room"),
                        focus = focus.value,
                        focusBottom = 300.dp,
                    ) {}
                }
            }
        }
        compose.onNodeWithTag("room").assertIsDisplayed()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeByFrame()
        assertRoomEdges()
        for (part in listOf(InteriorPart.LAMP, InteriorPart.WALL)) {
            compose.runOnIdle { focus.value = Interiors.Default.focusOf(part) }
            repeat(12) {
                compose.mainClock.advanceTimeBy(64)
                assertRoomEdges()
            }
        }
        // Сменили выбор, пока камера ещё приближает торшер.
        compose.runOnIdle { focus.value = Interiors.Default.focusOf(InteriorPart.LAMP) }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { focus.value = Interiors.Default.focusOf(InteriorPart.WALL) }
        repeat(12) {
            compose.mainClock.advanceTimeBy(64)
            assertRoomEdges()
        }
    }

    private fun assertRoomEdges() {
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("room").fetchSemanticsNode().boundsInRoot
        val origin = IntArray(2)
        compose.runOnIdle { roomView.getLocationOnScreen(origin) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val pixels = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val visibleHeight = bounds.height * 440 / 740
            for (edge in listOf(bounds.left + 2, bounds.right - 3)) {
                val x = origin[0] + edge.toInt()
                for (step in 1..8) {
                    val y = origin[1] + (bounds.top + visibleHeight * step / 9).toInt()
                    assertNotEquals("Пустой край комнаты: x=$x, y=$y", FinneyCream.toArgb(), pixels.getPixel(x, y))
                }
            }
        } finally {
            pixels.recycle()
        }
    }
}
