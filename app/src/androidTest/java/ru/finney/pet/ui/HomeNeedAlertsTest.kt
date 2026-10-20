package ru.finney.pet.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.content.AssetContentLoader
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.LevelCheck
import ru.finney.pet.domain.model.*
import ru.finney.pet.ui.home.*
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.theme.FinneyTheme
import java.io.File

/** Предупреждения независимы: выбранная комната и порог ухода не скрывают низкую шкалу. */
@RunWith(AndroidJUnit4::class)
class HomeNeedAlertsTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test fun allThreeNeedsWarnIndependentlyAndStopAtCriticalThreshold() {
        val ui = mutableStateOf(initialState())
        show { ui.value }
        for (label in listOf("Сон", "Покормить", "Помыть")) assertNeed(label, 39, true)
        assertNeed("Радость", 70, false)
        shot("three-critical-needs-font130")
        compose.runOnIdle { ui.value = ui.value.copy(stats = ui.value.stats.copy(energy = 40)) }
        assertNeed("Сон", 40, false)
        assertNeed("Покормить", 39, true)
        assertNeed("Помыть", 39, true)
        compose.runOnIdle { ui.value = ui.value.copy(stats = ui.value.stats.copy(satiety = 40)) }
        assertNeed("Покормить", 40, false)
        assertNeed("Помыть", 39, true)
        compose.runOnIdle { ui.value = ui.value.copy(stats = ui.value.stats.copy(hygiene = 40)) }
        for (label in listOf("Сон", "Покормить", "Помыть")) assertNeed(label, 40, false)
    }

    @Test fun sleepAlertUsesRecoveredEnergyAndDoesNotHideOtherNeeds() {
        val now = System.currentTimeMillis()
        val state = initialState().let { it.copy(stats = it.stats.copy(energy = 10),
            sleep = SleepInfo(now - 20_000, now - 10_000, 10)) }
        show { state }
        assertNeed("Сон", 100, false)
        assertNeed("Радость", 70, false)
        assertNeed("Покормить", 39, true)
        assertNeed("Помыть", 39, true)
    }

    private fun assertNeed(label: String, value: Int, critical: Boolean) {
        compose.onNodeWithContentDescription(label).assertIsDisplayed().assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "$value из 100" + if (critical) ", критически низко" else ""))
    }

    private fun initialState(): HomeUiState.Ready {
        val content = AssetContentLoader(InstrumentationRegistry.getInstrumentation().targetContext).load()
        val game = Game(content)
        val fresh = game.newGame(isDemo = true)
        val stats = PetStats(39, 39, 70, 39)
        return HomeUiState.Ready(
            petName = "Финни", appearance = PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND),
            isDemo = false, stats = stats, emotion = game.emotion(fresh.copy(pet = stats)), level = 2,
            check = LevelCheck(true, false, true, true, 2, gameRequired = false),
            stage = 2, balance = 30, totalSavings = 10, goal = null, periodNumber = 2,
            phase = PeriodPhase.ACTIVE, needsHint = null, levelGame = null,
            food = emptyList(), care = emptyList(), needsThreshold = 60, criticalThreshold = 40,
        )
    }

    private fun show(state: () -> HomeUiState.Ready) {
        compose.setContent {
            val density = LocalDensity.current
            FinneyTheme {
                CompositionLocalProvider(LocalAnimations provides false,
                    LocalDensity provides Density(density.density, 1.3f)) {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
                        HomeContent(state(), onOpenBudget = {}, onOpenShop = {}, onOpenGoals = {},
                            onOpenTasks = {}, onOpenTask = {}, onOpenWardrobe = {}, onOpenProgress = {},
                            onOpenSettings = {}, onOpenHelp = {}, onClosePeriod = {}, onBuy = {})
                    }
                }
            }
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "need-alerts-screens").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
