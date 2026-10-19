package ru.finney.pet.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.domain.model.PeriodFacts
import ru.finney.pet.domain.model.Plan
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.period.PeriodGrowth
import ru.finney.pet.ui.period.PeriodResultContent
import ru.finney.pet.ui.period.PeriodResultUiState
import ru.finney.pet.ui.progress.NextProgress
import ru.finney.pet.ui.theme.FinneyTheme
import java.io.File

@RunWith(AndroidJUnit4::class)
class PeriodGrowthScreenTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test fun actualOpeningFitsAndNextPlanStillWorks() {
        var opened = false
        val state = PeriodResultUiState.Ready(
            periodNumber = 1, playedLevel = 1, plan = Plan(50, 20, 10, 10),
            facts = PeriodFacts(20, 10, 10, 0), needsCovered = true, planMatched = true,
            savingsAdded = true, toPass = 2, passed = true, level = 2, leveledUp = true,
            balance = 65, income = 55, canPlanNext = true,
            growth = PeriodGrowth(listOf("Новая стадия: карапуз.", "Стала сложнее мини-игра: «Касса Финни»."),
                NextProgress(3, 1, listOf("Более сложная мини-игра: «Конвейер»."))),
        )
        compose.setContent {
            val density = LocalDensity.current
            FinneyTheme {
                CompositionLocalProvider(LocalAnimations provides false,
                    LocalDensity provides Density(density.density, 1.3f)) {
                    Box(Modifier.width(360.dp)) { PeriodResultContent(state, {}, { opened = true }) }
                }
            }
        }
        compose.onNodeWithText("Более сложная мини-игра: «Конвейер».").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Стала сложнее мини-игра: «Касса Финни».").assertIsDisplayed()
        compose.onNodeWithText("Составить следующий план").assertIsDisplayed()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        android.os.SystemClock.sleep(350)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "qol-final-screens").apply { mkdirs() }
        File(directory, "period-growth-font130.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("Составить следующий план").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }
}
