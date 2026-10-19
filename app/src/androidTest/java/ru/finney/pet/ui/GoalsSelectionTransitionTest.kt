package ru.finney.pet.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.domain.game.GoalProgress
import ru.finney.pet.domain.model.Goal
import ru.finney.pet.ui.goals.*
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.theme.FinneyTheme
import java.io.File

@RunWith(AndroidJUnit4::class)
class GoalsSelectionTransitionTest {
    @get:Rule val compose = createComposeRule()
    private val goals = (1..6).map { Goal("goal$it", "Награда $it", 50 + it * 10) }

    @Test fun firstSelectionAndChangingGoalStartAtTopWithoutCollapsingList() {
        var state by mutableStateOf(initialState())
        show(animations = true, state = { state }, onSelect = { state = select(state, it) })
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Награда 6").performScrollTo()
        // Семантическая прокрутка сама ждёт анимацию: замораживаем часы только после неё.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Награда 6").performClick()
        compose.mainClock.advanceTimeByFrame()
        // Старая страница ещё существует во время перехода, а не обрывается в тот же кадр.
        compose.onNodeWithTag("goals-page-choose").assertExists()
        compose.mainClock.advanceTimeBy(400)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("goals-page-choose").assertDoesNotExist()
        compose.onNodeWithText("Награда 6").assertIsDisplayed()
        screenshot("goals-first-selection-font130.png")

        compose.onNodeWithText("Выбрать другую цель").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Награда 1").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Награда 1").assertIsDisplayed()
        compose.onNodeWithTag("goals-page-choose").assertDoesNotExist()
        compose.runOnIdle { assertEquals("goal1", state.progress!!.goal.id) }
    }

    @Test fun reducedMotionShowsCorrectPageAndBackOnlyClosesPicker() {
        var state by mutableStateOf(initialState())
        var exits = 0
        show(animations = false, state = { state }, onSelect = { state = select(state, it) }, onBack = { exits++ })
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("Награда 1").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("goals-page-choose").assertDoesNotExist()
        compose.onNodeWithText("Награда 1").assertIsDisplayed()
        compose.onNodeWithText("Выбрать другую цель").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("goals-page-choose").assertDoesNotExist()
        compose.onNodeWithText("Награда 1").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, exits) }
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    private fun initialState() = GoalsUiState.Ready(
        goals = goals.map { GoalRow(it, 0, false, false) }, progress = null, balance = 50,
        canMoveMoney = true, canComplete = false, rejection = null,
    )

    private fun select(state: GoalsUiState.Ready, id: String): GoalsUiState.Ready {
        val goal = goals.first { it.id == id }
        return state.copy(progress = GoalProgress(goal, 0, goal.price, null, 0),
            goals = state.goals.map { it.copy(isActive = it.goal.id == id) })
    }

    private fun show(animations: Boolean, state: () -> GoalsUiState.Ready,
        onSelect: (String) -> Unit, onBack: () -> Unit = {}) {
        compose.setContent {
            val density = LocalDensity.current
            FinneyTheme {
                CompositionLocalProvider(LocalAnimations provides animations,
                    LocalDensity provides Density(density.density, 1.3f)) {
                    Box(Modifier.width(360.dp)) { GoalsContent(state(), onSelect, {}, {}, {}, onBack) }
                }
            }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "qol-oct17-screens").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
