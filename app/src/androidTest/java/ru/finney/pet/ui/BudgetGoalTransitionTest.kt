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
import ru.finney.pet.content.AssetContentLoader
import ru.finney.pet.ui.budget.BudgetUiState
import ru.finney.pet.ui.budget.PlanningContent
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.theme.FinneyTheme
import java.io.File

@RunWith(AndroidJUnit4::class)
class BudgetGoalTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstRealGoalShrinksSmoothlyAndStaysVisible() = verifySelection(animations = true)
    @Test fun reducedMotionSelectsRealGoalWithoutWaitingForAnimation() = verifySelection(animations = false)

    private fun verifySelection(animations: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val goals = AssetContentLoader(context).load().goals
        val chosen = goals.last()
        var state by mutableStateOf(BudgetUiState.Planning(
            periodNumber = 1, level = 1, budget = 50, needs = 10, wants = 10, savings = 10,
            needsHint = 10, goalLabel = null, goals = goals, rejection = null, isSaving = false,
        ))
        var confirmed = false
        compose.setContent {
            val density = LocalDensity.current
            FinneyTheme {
                CompositionLocalProvider(LocalAnimations provides animations,
                    LocalDensity provides Density(density.density, 1.3f)) {
                    Box(Modifier.width(360.dp)) {
                        PlanningContent(state, {}, {}, {}, onSelectGoal = { id ->
                            val goal = goals.first { it.id == id }
                            state = state.copy(goalLabel = goal.label, goalReward = goal.reward)
                        }, onRepeatLast = {}, onConfirm = { confirmed = true }, onBack = {})
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText(chosen.label).performScrollTo()
        val before = compose.onNodeWithTag("plan-savings-transition").getUnclippedBoundsInRoot().let { it.bottom.value - it.top.value }
        // Прокрутка завершается на живых часах; вручную измеряем только смену карточки.
        if (animations) compose.mainClock.autoAdvance = false
        compose.onNodeWithText(chosen.label).performClick()
        if (animations) {
            compose.mainClock.advanceTimeBy(160)
            val middle = compose.onNodeWithTag("plan-savings-transition").getUnclippedBoundsInRoot().let { it.bottom.value - it.top.value }
            assertTrue("Список сжимается плавно, а не исчезает одним кадром", middle < before)
            compose.mainClock.advanceTimeBy(400)
            val after = compose.onNodeWithTag("plan-savings-transition").getUnclippedBoundsInRoot().let { it.bottom.value - it.top.value }
            assertTrue("Промежуточная высота больше окончательной", middle > after)
        } else {
            compose.mainClock.advanceTimeBy(64)
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Цель: «${chosen.label}»").assertIsDisplayed()
        compose.onNodeWithText("На что копим? Выбери сам — любую:").assertDoesNotExist()
        compose.onNodeWithText("Подтвердить план").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(confirmed); assertEquals(chosen.label, state.goalLabel) }
        if (animations) {
            compose.waitForIdle()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            val directory = File(context.getExternalFilesDir(null), "qol-oct17-screens").apply { mkdirs() }
            File(directory, "budget-goal-selection-font130.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
