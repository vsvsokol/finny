package ru.finney.pet.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.R
import ru.finney.pet.content.AssetContentLoader
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.GoalProgress
import ru.finney.pet.domain.game.LevelCheck
import ru.finney.pet.domain.model.*
import ru.finney.pet.domain.pet.Emotion
import ru.finney.pet.ui.home.*
import ru.finney.pet.ui.pet.grownPet
import ru.finney.pet.ui.pet.skin
import androidx.compose.ui.geometry.Rect
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.theme.FinneyTheme
import java.io.File

/** Проверяем новый главный на реальных данных и сохранение доступных действий. */
@RunWith(AndroidJUnit4::class)
class HomeDesignerLayoutTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test fun compactActionsKeepSavingsGoalAndActiveGameVisible() {
        var goals = 0
        var shops = 0
        show({ initialState() }, onGoal = { goals++ }, onShop = { shops++ })
        compose.onNodeWithText("60", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Накоплено 35 монет").assertIsDisplayed()
        compose.onNodeWithText("Конвейер").assertIsDisplayed()
        compose.onNodeWithContentDescription("Копилка").assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                "Ковбойская шляпа. Накоплено 35 из 90 монет"))
        compose.onNodeWithContentDescription("Игры уровня").assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Пройдено 1 из 3 игр уровня"))
        shot("01-normal")
        compose.onNodeWithContentDescription("Копилка").performClick()
        compose.onNodeWithContentDescription("Магазин").performClick()
        compose.runOnIdle { assertEquals(1, goals); assertEquals(1, shops) }
        compose.onNodeWithContentDescription("Игры уровня").performClick()
        compose.onNodeWithContentDescription("«Проверь чек», по желанию, пройдена").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun completedGoalAndAllGamesHaveIndependentReadyStatesAtFont130() {
        val state = mutableStateOf(initialState())
        show({ state.value }, large = true)
        compose.runOnIdle {
            val goal = state.value.goal!!
            state.value = state.value.copy(goal = goal.copy(saved = goal.goal.price, remaining = 0),
                totalSavings = goal.goal.price)
        }
        compose.onNodeWithContentDescription("Копилка").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                "Ковбойская шляпа. Накоплено 90 из 90 монет. Награда готова"))
        compose.onNodeWithContentDescription("Игры уровня").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Пройдено 1 из 3 игр уровня"))
        compose.runOnIdle { state.value = state.value.copy(levelGames = state.value.levelGames.map { it.copy(passed = true) }) }
        compose.onNodeWithContentDescription("Игры уровня").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                "Пройдено 3 из 3 игр уровня. Все игры пройдены"))
        compose.onNodeWithContentDescription("Накоплено 90 монет").assertIsDisplayed()
        compose.onNodeWithText("Конвейер").assertIsDisplayed()
        shot("02-ready-font130")
    }

    @Test fun heartUsesThreeEmotionsAndRepeatsThePetsReason() {
        val state = mutableStateOf(initialState().copy(emotion = Emotion.CALM))
        show({ state.value })
        val heart = compose.onNodeWithContentDescription("Настроение питомца")
        heart.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Спокойное. 40 из 100"))
        shot("03-heart-calm")
        heart.performClick()
        compose.onNodeWithText("Мне хорошо!").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(emotion = Emotion.HAPPY, stats = state.value.stats.copy(mood = 90)) }
        heart.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Весёлое. 90 из 100"))
        shot("03-heart-happy")
        compose.runOnIdle { state.value = state.value.copy(emotion = Emotion.HUNGRY, stats = state.value.stats.copy(mood = 20)) }
        heart.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Грустное. 20 из 100"))
        heart.performClick()
        compose.onNodeWithText("Я проголодался.\nПокорми меня!").assertIsDisplayed()
        shot("04-heart-hungry")
        compose.onNodeWithText("Я проголодался.\nПокорми меня!").performClick()
        compose.onNodeWithText("Я проголодался.\nПокорми меня!").assertDoesNotExist()
    }

    @Test fun sleepingPetKeepsShopOpenAndDisablesDayActions() {
        val now = System.currentTimeMillis()
        val state = initialState().copy(sleep = SleepInfo(now, now + 600_000, 10))
        show({ state })
        compose.onNodeWithContentDescription("Настроение питомца").assertDoesNotExist()
        compose.onNodeWithContentDescription("Магазин").assertIsEnabled().assertIsDisplayed()
        compose.onNodeWithContentDescription("Покормить").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Помыть").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Копилка").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Игры уровня").assertIsNotEnabled()
        shot("05-sleep")
    }

    @Test fun grownPetSpeechDoesNotCoverSavingsOrTheGameAtFont130() {
        val state = initialState().copy(stage = 6)
        show({ state }, large = true)
        compose.onNodeWithContentDescription("Покормить").performClick()
        val speech = compose.onNodeWithText("Я проголодался.\nПокорми меня!").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        for (description in listOf("Копилка", "Игры уровня", "Магазин")) {
            val action = compose.onNodeWithContentDescription(description).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertFalse("Речь закрыла $description", speech.overlaps(action))
        }
        val heart = compose.onNodeWithContentDescription("Настроение питомца").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        for (description in listOf("Копилка", "Игры уровня")) {
            assertFalse("Сердечко закрыло $description", heart.overlaps(
                compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot))
        }
        shot("06-grown-font130")
    }

    @Test fun careCaptionAppearsForTwoSecondsWithoutMovingTheButton() {
        var clicks = 0
        compose.setContent { FinneyTheme { CompositionLocalProvider(LocalAnimations provides false) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.BottomCenter) {
                HomeCareAction(R.drawable.home_eat_icon, "Еда", "Покормить", 60, false, false, { clicks++ })
            }
        } } }
        compose.mainClock.advanceTimeByFrame()
        val button = compose.onNodeWithContentDescription("Покормить").assertIsDisplayed()
        val before = button.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Еда").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        button.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(100)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.getExternalFilesDir(null), "caption-tree.txt")
            .writeText(compose.onRoot(useUnmergedTree = true).printToString())
        shot("07-caption")
        compose.onNodeWithText("Еда").assertIsDisplayed()
        assertEquals(before, button.fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(2_100)
        compose.onNodeWithText("Еда").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun splitLayoutKeepsControlsAtOppositeEdges() {
        show({ initialState() }, layout = HomeActionLayout.SPLIT)
        val savings = compose.onNodeWithContentDescription("Копилка").fetchSemanticsNode().boundsInRoot
        val games = compose.onNodeWithContentDescription("Игры уровня").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(savings.center.x < games.center.x)
        assertFalse(savings.overlaps(games))
        compose.onNodeWithContentDescription("Накоплено 35 монет").assertIsDisplayed()
        compose.onNodeWithText("Конвейер").assertIsDisplayed()
        shot("A-split")
    }

    @Test fun rightLayoutKeepsControlsNearTheEdge() {
        show({ initialState() }, layout = HomeActionLayout.RIGHT_COLUMN)
        shot("B-right-column")
    }

    @Test fun grownPetSpeechAvoidsBothSidesInSplitLayoutAtFont130() {
        show({ initialState().copy(stage = 6) }, large = true, layout = HomeActionLayout.SPLIT)
        compose.onNodeWithContentDescription("Покормить").performClick()
        val speech = compose.onNodeWithText("Я проголодался.\nПокорми меня!").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        for (description in listOf("Копилка", "Игры уровня", "Магазин")) {
            assertFalse("Речь закрыла $description", speech.overlaps(
                compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot))
        }
        val skin = initialState().appearance.character.skin
        val pet = compose.onNodeWithTag("home-pet").fetchSemanticsNode().boundsInRoot.grownPet(skin, 6)
        val face = Rect(pet.left + pet.width * 0.2f, pet.top + pet.height * skin.eyesTop,
            pet.right - pet.width * 0.2f, pet.top + pet.height * (skin.mouthCenter.pivotFractionY + 0.06f))
        assertFalse("Речь закрыла лицо питомца", speech.overlaps(face))
        shot("A-split-grown-font130")
    }

    @Test fun tutorialFollowsOnlyTheRoundCareButton() {
        val targets = HintTargets()
        compose.setContent { FinneyTheme { CompositionLocalProvider(LocalAnimations provides false) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.BottomCenter) {
                HomeCareAction(R.drawable.home_eat_icon, "Еда", "Покормить", 60, false, false, {},
                    buttonModifier = Modifier.hintTarget(targets, NextStep.FEED))
            }
        } } }
        val button = compose.onNodeWithContentDescription("Покормить").assertIsDisplayed()
        compose.runOnIdle {
            val spot = targets.spots.getValue(NextStep.FEED)
            assertEquals(spot.rect.width, spot.rect.height, 1f)
            org.junit.Assert.assertNull(spot.corner)
        }
        button.performClick()
        compose.onNodeWithText("Еда").assertIsDisplayed()
        compose.runOnIdle {
            val spot = targets.spots.getValue(NextStep.FEED)
            assertEquals(spot.rect.width, spot.rect.height, 1f)
        }
    }

    @Test fun moneyGroupsAreCenteredUnderShopAndSavings() {
        show({ initialState() })
        val shop = compose.onNodeWithTag("home-shop-circle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val balance = compose.onNodeWithTag("home-shop-money", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(shop.center.x, balance.center.x, 1f)
        val savings = compose.onNodeWithContentDescription("Копилка").fetchSemanticsNode().boundsInRoot
        val saved = compose.onNodeWithTag("home-savings-money", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(savings.center.x, saved.center.x, 1f)
        compose.onNodeWithContentDescription("Накоплено 35 монет").assertIsDisplayed()
        shot("money-aligned")
    }

    private fun initialState(): HomeUiState.Ready {
        val content = AssetContentLoader(InstrumentationRegistry.getInstrumentation().targetContext).load()
        val game = Game(content)
        val fresh = game.newGame(isDemo = true)
        val goal = content.goals.first { it.reward == "hat_cowboy" }
        val tasks = listOf("Конвейер", "Проверь чек", "Подработка").mapIndexed { i, title ->
            LevelGame(content.tasks[i].id, title, null, i == 0, i == 1, 15)
        }
        return HomeUiState.Ready(
            petName = "Финни", appearance = PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND),
            isDemo = false, stats = PetStats(35, 75, 40, 80), emotion = Emotion.HUNGRY, level = 2,
            check = LevelCheck(true, false, true, true, 2, tasks.first().taskId, false, true), stage = 2,
            balance = 60, totalSavings = 35, goal = GoalProgress(goal, 35, goal.price - 35, null, 0),
            periodNumber = 2, phase = PeriodPhase.ACTIVE, needsHint = null, levelGame = "Конвейер",
            levelGames = tasks, food = emptyList(), care = emptyList(), nextStep = null,
        )
    }

    private fun show(state: () -> HomeUiState.Ready, large: Boolean = false,
                     layout: HomeActionLayout = HomeActionLayout.SPLIT,
                     onGoal: () -> Unit = {}, onShop: () -> Unit = {}) {
        compose.setContent {
            val density = LocalDensity.current
            FinneyTheme {
                CompositionLocalProvider(LocalAnimations provides false,
                    LocalDensity provides Density(density.density, if (large) 1.3f else 1f)) {
                    Box(Modifier.requiredSize(360.dp, if (large) 640.dp else 740.dp)) {
                        HomeContent(state(), actionLayout = layout, onOpenBudget = {}, onOpenShop = onShop, onOpenGoals = onGoal,
                            onOpenTasks = {}, onOpenTask = {}, onOpenWardrobe = {}, onOpenProgress = {},
                            onOpenSettings = {}, onOpenHelp = {}, onClosePeriod = {}, onBuy = {})
                    }
                }
            }
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "home-polish-2026-10-18").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
