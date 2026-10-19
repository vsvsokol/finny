package ru.finney.pet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.ui.components.guidedInput

@RunWith(AndroidJUnit4::class)
class GuidedInputTest {
    @get:Rule val compose = createComposeRule()

    /** Затемнение пропускает цель и выход, а нажатия по посторонним кнопкам поглощает. */
    @Test
    fun targetAndEscapeWorkWhileOtherTapsAreBlocked() {
        var target: Rect? = null
        var escape: Rect? = null
        var intended = 0
        var unrelated = 0
        val active = mutableStateOf(true)
        compose.setContent {
            Row(Modifier.guidedInput(active.value, target = { target }, escape = { escape })) {
                Box(Modifier.size(80.dp).testTag("target").onGloballyPositioned { target = it.boundsInRoot() }
                    .clickable { intended++ }) { Text("Цель") }
                Box(Modifier.size(80.dp).testTag("other").clickable { unrelated++ }) { Text("Другое") }
                Box(Modifier.size(80.dp).testTag("escape").onGloballyPositioned { escape = it.boundsInRoot() }
                    .clickable { active.value = false }) { Text("Дальше сам") }
            }
        }
        compose.onNodeWithTag("other").performTouchInput { click() }
        compose.onNodeWithTag("target").performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, unrelated); assertEquals(1, intended) }
        compose.onNodeWithTag("escape").performTouchInput { click() }
        compose.onNodeWithTag("other").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, unrelated) }
    }

    /** Смена шага сразу переносит доступ к новой цели, без устаревшего захвата замыкания. */
    @Test
    fun nextStepUsesTheNewTarget() {
        var firstBounds: Rect? = null
        var secondBounds: Rect? = null
        val secondStep = mutableStateOf(false)
        var completed = 0
        compose.setContent {
            Row(Modifier.guidedInput(true,
                target = { if (secondStep.value) secondBounds else firstBounds }, escape = { null })) {
                Box(Modifier.size(80.dp).testTag("first").onGloballyPositioned { firstBounds = it.boundsInRoot() }
                    .clickable { secondStep.value = true }) { Text("Первый") }
                Box(Modifier.size(80.dp).testTag("second").onGloballyPositioned { secondBounds = it.boundsInRoot() }
                    .clickable { completed++ }) { Text("Второй") }
            }
        }
        compose.onNodeWithTag("second").performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, completed) }
        compose.onNodeWithTag("first").performTouchInput { click() }
        compose.onNodeWithTag("second").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, completed) }
    }
}
