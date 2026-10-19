package ru.finney.pet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.R
import ru.finney.pet.ui.home.HomeCareAction
import ru.finney.pet.ui.home.PetSpeechBubble
import ru.finney.pet.ui.home.HomeFeedbackToast
import ru.finney.pet.ui.components.ActionFeedback
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyTheme

/** Проверяем настоящий выход, повторное нажатие и отключение движения. */
@RunWith(AndroidJUnit4::class)
class HomeTransientMotionTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })

    @Test fun captionFadesAfterReadingAndRepeatedTapRestartsTheTimer() {
        compose.setContent { FinneyTheme { CompositionLocalProvider(LocalAnimations provides true) {
            Box(Modifier.fillMaxSize().background(FinneyCream), contentAlignment = Alignment.Center) {
                HomeCareAction(R.drawable.home_eat_icon, "Еда", "Покормить", 60, false, false, {})
            }
        } } }
        val button = compose.onNodeWithContentDescription("Покормить").assertIsDisplayed()
        val before = button.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        button.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithText("Еда").assertIsDisplayed()
        button.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithText("Еда").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Еда").assertExists()
        assertEquals(before, button.fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithText("Еда").assertDoesNotExist()
    }

    @Test fun speechKeepsItsContentDuringExitAndCanReopen() {
        val visible = mutableStateOf(true)
        compose.setContent { FinneyTheme { CompositionLocalProvider(LocalAnimations provides true) {
            Box(Modifier.fillMaxSize().background(FinneyCream), contentAlignment = Alignment.Center) {
                PetSpeechBubble("Мне хорошо!", { visible.value = false }, visible = visible.value)
            }
        } } }
        val bubble = compose.onNodeWithText("Мне хорошо!").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        bubble.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(100)
        bubble.assertExists()
        compose.mainClock.advanceTimeBy(250)
        bubble.assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        compose.mainClock.advanceTimeBy(500)
        bubble.assertIsDisplayed()
    }

    @Test fun purchaseResultKeepsItsTextThroughoutTheExit() {
        val feedback = mutableStateOf<ActionFeedback?>(ActionFeedback("Финни поел!", emptyList(), "", ""))
        compose.setContent { FinneyTheme { CompositionLocalProvider(LocalAnimations provides true) {
            Box(Modifier.fillMaxSize().background(FinneyCream), contentAlignment = Alignment.Center) {
                HomeFeedbackToast(feedback.value, { feedback.value = null })
            }
        } } }
        val result = compose.onNodeWithText("Финни поел!").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        result.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(100)
        result.assertExists()
        compose.mainClock.advanceTimeBy(300)
        result.assertDoesNotExist()
    }

    @Test fun disabledAnimationsRemoveSpeechWithoutAnExitTransition() {
        val visible = mutableStateOf(true)
        compose.setContent { FinneyTheme { CompositionLocalProvider(LocalAnimations provides false) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PetSpeechBubble("Мне хорошо!", { visible.value = false }, visible = visible.value)
            }
        } } }
        compose.onNodeWithText("Мне хорошо!").performClick()
        compose.onNodeWithText("Мне хорошо!").assertDoesNotExist()
    }
}
