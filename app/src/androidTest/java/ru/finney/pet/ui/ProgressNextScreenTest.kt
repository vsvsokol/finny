package ru.finney.pet.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.progress.NextProgress
import ru.finney.pet.ui.progress.ProgressContent
import ru.finney.pet.ui.progress.ProgressUiState
import ru.finney.pet.ui.theme.FinneyTheme
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProgressNextScreenTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test fun nextOpeningAndMaximumLevelFitAtLargeFont() {
        var state by mutableStateOf(ProgressUiState.Ready(
            petName = "Финни", level = 1, maxLevel = 30, stage = 1, goal = null,
            goalsCompleted = 0, lastClosedPeriod = null, lastClosedLevel = null,
            tasks = emptyList(), history = emptyList(),
            nextProgress = NextProgress(2, 1, listOf(
                "Новая стадия: карапуз.", "Более сложная мини-игра: «Касса Финни».")),
        ))
        compose.setContent {
            val density = LocalDensity.current
            FinneyTheme {
                CompositionLocalProvider(LocalAnimations provides false,
                    LocalDensity provides Density(density.density, 1.3f)) {
                    Box(Modifier.width(360.dp)) { ProgressContent(state, {}, {}, {}) }
                }
            }
        }
        compose.onNodeWithText("Более сложная мини-игра: «Касса Финни».").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Осталось пройти уровней: 1.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Новая стадия: карапуз.").assertIsDisplayed()
        saveScreenshot("next-progress-font130.png")
        compose.runOnIdle { state = state.copy(level = 30, stage = 6, nextProgress = null) }
        compose.onNodeWithText("Самый высокий уровень").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Можно дальше заботиться о питомце, играть и копить.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("На уровне 2").assertDoesNotExist()
        saveScreenshot("max-level-font130.png")
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        android.os.SystemClock.sleep(350)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "qol-final-screens").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
