package ru.finney.pet.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.content.AssetContentLoader
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.LevelCheck
import ru.finney.pet.domain.game.PlanReport
import ru.finney.pet.domain.model.*
import ru.finney.pet.domain.pet.Emotion
import ru.finney.pet.ui.budget.ActiveContent
import ru.finney.pet.ui.budget.BudgetUiState
import ru.finney.pet.ui.home.HomeContent
import ru.finney.pet.ui.home.HomeUiState
import ru.finney.pet.ui.home.miniPathLevels
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.progress.NodeStatus
import ru.finney.pet.ui.progress.levelNodes
import ru.finney.pet.ui.theme.FinneyTheme
import java.io.File

/** Состояния без Session: проверка вёрстки и закрытия панели не меняет профиль ребёнка. */
@RunWith(AndroidJUnit4::class)
class LevelMenuInteriorReviewTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val content by lazy { AssetContentLoader(context).load() }

    @Test fun levelPanelShowsRealRewardsAndCompactConditions() {
        showHome({ home(3) })
        compose.onNodeWithContentDescription("Уровень 3").performClick()
        compose.onNodeWithText("Ещё нет").assertDoesNotExist()
        compose.onNodeWithText("✓ Готово").assertDoesNotExist()
        val future = compose.onAllNodes(hasContentDescription("впереди", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("Впереди должны быть награды, а не одни игры", future.any {
            it.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].any { text -> text.contains(": ") }
        })
        shot("level-3")
    }

    @Test fun pathEndsFitAtLargeFont() {
        val state = mutableStateOf(home(1))
        showHome({ state.value }, fontScale = 1.3f)
        for (level in listOf(1, 30)) {
            compose.runOnIdle { state.value = home(level) }
            compose.onNodeWithContentDescription("Уровень $level").performClick()
            compose.onNodeWithContentDescription("Уровень $level, ты здесь", useUnmergedTree = true).assertIsDisplayed()
            shot("level-$level-font130")
            androidx.test.espresso.Espresso.pressBack()
        }
    }

    @Test fun pathRevealsWithAnimationsAndKeepsItsAction() {
        showHome({ home(3) }, animations = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Уровень 3").performClick()
        for (elapsed in listOf(96L, 160L, 240L, 400L)) {
            compose.mainClock.advanceTimeBy(elapsed)
            shot("path-reveal-$elapsed")
        }
        compose.onNodeWithText("Весь путь ›").assertIsDisplayed().performClick()
        compose.mainClock.autoAdvance = true
    }

    @Test fun planAndFactFitsAtDefaultFont() = planAndFact(1f)
    @Test fun planAndFactFitsAtLargeFont() = planAndFact(1.3f)

    private fun planAndFact(fontScale: Float) {
        var next = false
        val state = BudgetUiState.Active(3, 3,
            PlanReport(Plan(100, 40, 25, 35), PeriodFacts(40, 30, 20, 0), 5, false), 55)
        compose.setContent {
            val density = LocalDensity.current
            FinneyTheme { CompositionLocalProvider(LocalAnimations provides false,
                LocalDensity provides Density(density.density, fontScale)) {
                Box(Modifier.width(360.dp)) { ActiveContent(state, { next = true }) }
            } }
        }
        compose.onNodeWithText("Уровень", useUnmergedTree = true).assertIsDisplayed()
        shot("plan-fact-font${(fontScale * 100).toInt()}")
        compose.onNodeWithText("Дальше").performClick()
        compose.runOnIdle { assertTrue(next) }
    }

    @Test fun interiorTabsCloseAndReopenWithoutChangingTheProfile() {
        showHome({ home(3) }, animations = true)
        openPicker()
        compose.onNodeWithText("Комната").performClick()
        for (tab in listOf("Стена", "Окно", "Торшер", "Ковёр")) {
            compose.onNodeWithText(tab).performClick()
            compose.mainClock.advanceTimeBy(2_000)
            shot("interior-$tab")
        }
        compose.onNodeWithText("Торшер").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Готово").performClick()
        repeat(6) { frame ->
            compose.mainClock.advanceTimeBy(48)
            shot("interior-close-${frame + 1}")
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Меню").assertIsDisplayed()
        openPicker()
        compose.onNodeWithText("Комната").performClick()
        compose.onNodeWithText("Ковёр").assertIsDisplayed()
        compose.onNodeWithContentDescription("Готово").performClick()
        compose.onNodeWithContentDescription("Меню").assertIsDisplayed()
    }

    private fun openPicker() {
        compose.onNodeWithContentDescription("Меню").performClick()
        compose.onNodeWithText("Украсить").performClick()
        compose.onNodeWithText("Окрас").assertIsDisplayed()
    }

    private fun home(level: Int): HomeUiState.Ready {
        val game = Game(content)
        val fresh = game.newGame()
        val path = levelNodes(game, content, fresh, PetCharacter.PUSHISTIK).map {
            it.copy(status = when {
                it.level < level -> NodeStatus.DONE
                it.level == level -> NodeStatus.CURRENT
                else -> NodeStatus.FUTURE
            }, stars = 3)
        }
        return HomeUiState.Ready(
            petName = "Финни", appearance = PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND),
            isDemo = true, stats = PetStats(70, 70, 70, 90), emotion = Emotion.HAPPY,
            level = level, check = LevelCheck(true, true, false, false, 1, null, true, false),
            stage = 2, balance = 60, totalSavings = 0, goal = null, periodNumber = 3,
            phase = PeriodPhase.ACTIVE, needsHint = null, levelGame = null,
            food = emptyList(), care = emptyList(), nextStep = null, path = path.filter { it.level in miniPathLevels(level, content.economy.maxLevel) },
        )
    }

    private fun showHome(state: () -> HomeUiState.Ready, fontScale: Float = 1f, animations: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            FinneyTheme { CompositionLocalProvider(LocalAnimations provides animations,
                LocalDensity provides Density(density.density, fontScale)) {
                Box(Modifier.width(360.dp)) {
                    HomeContent(state(), onOpenBudget = {}, onOpenShop = {}, onOpenGoals = {},
                        onOpenTasks = {}, onOpenTask = {}, onOpenWardrobe = {}, onOpenProgress = {},
                        onOpenSettings = {}, onOpenHelp = {}, onClosePeriod = {}, onBuy = {})
                }
            } }
        }
    }

    private fun shot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(context.getExternalFilesDir(null), "level-menu-interior-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
