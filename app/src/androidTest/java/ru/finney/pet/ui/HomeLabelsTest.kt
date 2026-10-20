package ru.finney.pet.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.content.AssetContentLoader
import ru.finney.pet.domain.game.GoalProgress
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.model.*
import ru.finney.pet.ui.home.*
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.theme.FinneyTheme
import java.io.File

/** Проверяем узкий экран и касание самой подписи, не меняя сохранённую игру. */
@RunWith(AndroidJUnit4::class)
class HomeLabelsTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })
    @Test fun shopAtDesignerBalance() = checkLabels(1f, 740, balance = 60)
    @Test fun labelsAtNormalFont() = checkLabels(1f, 740)
    @Test fun labelsAtLargeFontOnShortScreen() = checkLabels(1.3f, 640)

    @Test fun selectedGoalAtLargeFont() = checkLabels(1.3f, 640, 1)
    @Test fun completedGoalAtLargeFont() = checkLabels(1.3f, 640, 2)
    @Test fun reserveAtLargeFont() = checkLabels(1.3f, 640, 3)

    private fun checkLabels(fontScale: Float, height: Int, goalMode: Int = 0, balance: Int = 12345) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = AssetContentLoader(context).load()
        val game = Game(content)
        val fresh = game.newGame(isDemo = true)
        val state = fresh.copy(pet = fresh.pet.copy(satiety = 10))
        val task = content.tasks.first()
        val goal = content.goals.last()
        val progress = when (goalMode) {
            1 -> GoalProgress(goal, 35, goal.price - 35, null, 0)
            2 -> GoalProgress(goal, goal.price, 0, null, 0)
            else -> null
        }
        val ui = HomeUiState.Ready(
            petName = "Финни", appearance = PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND),
            isDemo = false, stats = state.pet, emotion = game.emotion(state), level = 2,
            check = game.levelCheck(state), stage = game.stage(state), balance = balance,
            totalSavings = if (goalMode == 3) 250 else progress?.saved ?: 0, goal = progress, periodNumber = 2, phase = state.currentPeriod.phase,
            needsHint = game.needsHint(state), levelGame = task.title, food = emptyList(), care = emptyList(),
            reserveMode = goalMode == 3, nextStep = null, levelGames = listOf(LevelGame(task.id, task.title, null, false, false, 15)),
        )
        var goalOpens = 0
        var shops = 0
        var sleeps = 0
        compose.setContent {
            val density = LocalDensity.current
            FinneyTheme {
                CompositionLocalProvider(LocalAnimations provides false,
                    LocalDensity provides Density(density.density, fontScale)) {
                    Box(Modifier.requiredSize(360.dp, height.dp)) {
                        HomeContent(ui, onOpenBudget = {}, onOpenShop = { shops++ }, onOpenGoals = { goalOpens++ },
                            onOpenTasks = {}, onOpenTask = {}, onOpenWardrobe = {}, onOpenProgress = {},
                            onOpenSettings = {}, onOpenHelp = {}, onClosePeriod = {}, onBuy = {},
                            onSleep = { sleeps++ })
                    }
                }
            }
        }
        shot("goal-$goalMode-before-${(fontScale * 100).toInt()}")
        val labels = listOf(balance.toString(), ui.totalSavings.toString())
        compose.onNodeWithContentDescription("Копилка").assertIsDisplayed()
        for (label in labels) {
            val node = compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Не получена раскладка: $label", layouts.isNotEmpty())
            val layout = layouts.single()
            if (label in listOf(balance.toString(), "Сон", "Покормить", "Помыть")) {
                assertEquals("Подпись действия перенеслась: $label", 1, layout.lineCount)
            }
            // Semantics может вернуть абзац шире shrink-wrap Text; проверяем строки букв.
            assertTrue("Обрезана высота: $label", layout.multiParagraph.height <= layout.size.height + 1f)
            assertFalse("Обрезаны строки: $label", layout.multiParagraph.didExceedMaxLines)
            for (line in 0 until layout.lineCount) {
                assertFalse("Многоточие: $label", layout.isLineEllipsized(line))
                assertTrue("Обрезана ширина: $label",
                    layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f)
            }
        }
        shot("goal-$goalMode-home-${(fontScale * 100).toInt()}")
        compose.onNodeWithContentDescription("Копилка").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, goalOpens) }
        compose.onNodeWithContentDescription("Магазин").assertIsDisplayed().performTouchInput { click() }
        // Главный открывается в игровой: первое нажатие — спальня, второе — уложить спать.
        compose.onNodeWithContentDescription("Сон").performTouchInput { click() }
        compose.onNodeWithContentDescription("Сон").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, shops); assertEquals(1, sleeps) }
        // Первое нажатие переводит в другую комнату, покупок при этом нет.
        compose.onNodeWithContentDescription("Покормить").performTouchInput { click() }
        shot("goal-$goalMode-kitchen-${(fontScale * 100).toInt()}")
        compose.onNodeWithContentDescription("Помыть").performTouchInput { click() }
        shot("goal-$goalMode-bath-${(fontScale * 100).toInt()}")
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "label-fix-screens").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
