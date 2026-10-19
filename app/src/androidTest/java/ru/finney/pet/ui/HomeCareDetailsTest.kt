package ru.finney.pet.ui

import android.graphics.Bitmap
import java.io.File
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalDensity
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

/** Достаточная шкала не должна требовать повторного ухода; профиль тест не открывает. */
@RunWith(AndroidJUnit4::class)
class HomeCareDetailsTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test fun careDetailsExpandAndCollapseWithConfiguredThreshold() {
        showHome(threshold = 60)
        compose.onNodeWithText("Для ухода каждая шкала должна быть не ниже 60 из 100.", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Уход: помой").performScrollTo().assertIsDisplayed()
        val care = compose.onNodeWithContentDescription("Сыт, чист и выспался: пока нет")
        care.performScrollTo().assertHasClickAction().performClick()
        // Текст раскрытия читаем отдельно от общей семантики кликабельной подложки панели.
        compose.onNodeWithText("Для ухода каждая шкала должна быть не ниже 60 из 100.", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("✓ Сытость: 70 из 100", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("! Чистота: 35 из 100", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("✓ Энергия: 70 из 100", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        shot("care-details-expanded-font130")
        care.performScrollTo().performClick()
        compose.onNodeWithText("! Чистота: 35 из 100", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun careDetailsUseRecoveredEnergyWhileSleepStateIsStillPresent() {
        showHome(threshold = 50, asleep = true)
        compose.onNodeWithText("Уход: помой").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Сыт, чист и выспался: пока нет").performScrollTo().performClick()
        compose.onNodeWithText("✓ Энергия: 100 из 100", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("! Энергия: 10 из 100", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "care-details-screens").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun showHome(threshold: Int, asleep: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = AssetContentLoader(context).load()
        val game = Game(content)
        val fresh = game.newGame(isDemo = true)
        val stats = PetStats(satiety = 70, hygiene = 35, mood = 10, energy = if (asleep) 10 else 70)
        val now = System.currentTimeMillis()
        val state = HomeUiState.Ready(
            petName = "Финни", appearance = PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND),
            isDemo = false, stats = stats, emotion = game.emotion(fresh.copy(pet = stats)), level = 2,
            check = LevelCheck(true, false, false, true, 2, gameRequired = false),
            stage = 2, balance = 30, totalSavings = 10, goal = null, periodNumber = 2,
            phase = PeriodPhase.ACTIVE, needsHint = null, levelGame = null,
            food = emptyList(), care = emptyList(), needsThreshold = threshold,
            // Сон уже достиг конца; callback намеренно не меняет тестовый снимок состояния.
            sleep = if (asleep) SleepInfo(now - 20_000, now - 10_000, 10) else null,
        )
        compose.setContent {
            val density = LocalDensity.current
            FinneyTheme {
                CompositionLocalProvider(LocalAnimations provides false,
                    LocalDensity provides Density(density.density, 1.3f)) {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
                        HomeContent(state, onOpenBudget = {}, onOpenShop = {}, onOpenGoals = {},
                            onOpenTasks = {}, onOpenTask = {}, onOpenWardrobe = {}, onOpenProgress = {},
                            onOpenSettings = {}, onOpenHelp = {}, onClosePeriod = {}, onBuy = {})
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Уровень 2", useUnmergedTree = true).performTouchInput { click() }
    }
}
