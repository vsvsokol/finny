package ru.finney.pet.ui

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.content.AssetContentLoader
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.model.*
import ru.finney.pet.domain.tasks.TaskInput
import ru.finney.pet.domain.tasks.TaskEngines
import ru.finney.pet.domain.tasks.TaskEvaluation
import ru.finney.pet.ui.budget.BudgetUiState
import ru.finney.pet.ui.budget.PlanningContent
import ru.finney.pet.ui.home.HomeContent
import ru.finney.pet.ui.home.HomeUiState
import ru.finney.pet.ui.home.LevelGame
import ru.finney.pet.ui.home.NextStep
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.onboarding.OnboardingScreen
import ru.finney.pet.ui.tasks.games.ReceiptGame
import ru.finney.pet.ui.tasks.games.ChoresGame
import ru.finney.pet.ui.tasks.games.ReserveGame
import ru.finney.pet.ui.tasks.games.GoalRaceGame
import ru.finney.pet.ui.theme.FinneyTheme
import java.io.File

/** Проверка живой вёрстки; снимки сохраняются отдельно от профилей и не меняют игру. */
@RunWith(AndroidJUnit4::class)
class PlaytestScreensTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val content by lazy { AssetContentLoader(context).load() }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        // UiAutomation снимает последний кадр Surface, который на API 26 отстаёт от Compose idle.
        android.os.SystemClock.sleep(350)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = File(context.getExternalFilesDir(null), "playtest-screens").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun onboardingExplainsTheGoal() {
        compose.setContent { FinneyTheme { OnboardingScreen(false, onFinish = {}, onStartDemo = {}) } }
        compose.onNodeWithText("Помоги Финни вырасти", substring = true).assertIsDisplayed()
        screenshot("01-onboarding")
    }

    @Test
    fun homeShowsNamedDestinationsAndGuidance() {
        val game = Game(content)
        val state = game.newGame(isDemo = true)
        val task = content.tasks.first()
        val ui = HomeUiState.Ready(
            petName = "Финни", appearance = PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND),
            isDemo = true, stats = state.pet, emotion = game.emotion(state), level = game.level(state),
            check = game.levelCheck(state), stage = game.stage(state), balance = state.balance,
            totalSavings = 0, goal = null, periodNumber = 1, phase = state.currentPeriod.phase,
            needsHint = game.needsHint(state), levelGame = task.title, food = emptyList(), care = emptyList(),
            nextStep = NextStep.PLAN, levelGames = listOf(LevelGame(task.id, task.title, null, false, false, 15)),
        )
        compose.setContent {
            FinneyTheme { CompositionLocalProvider(LocalAnimations provides false) {
                HomeContent(ui, onOpenBudget = {}, onOpenShop = {}, onOpenGoals = {}, onOpenTasks = {},
                    onOpenTask = {}, onOpenWardrobe = {}, onOpenProgress = {}, onOpenSettings = {},
                    onOpenHelp = {}, onClosePeriod = {}, onBuy = {})
            } }
        }
        compose.onNodeWithText("Магазин").assertIsDisplayed()
        compose.onNodeWithText("Сон").assertIsDisplayed()
        compose.onNodeWithContentDescription("Закрыть подсказки").assertIsDisplayed()
        screenshot("02-home-guide")
    }

    @Test
    fun planSeparatesIntentionsFromTransfers() {
        val ui = BudgetUiState.Planning(periodNumber = 1, level = 1, budget = 50,
            needs = 0, wants = 0, savings = 0, needsHint = 30, goalLabel = null,
            goals = content.goals, rejection = null, isSaving = false,
            needsItems = listOf("food_apple", "care_soap"), wantsItems = listOf("toy_ball", "treat_candy"))
        compose.setContent { FinneyTheme { CompositionLocalProvider(LocalAnimations provides false) {
            PlanningContent(ui, onNeedsChange = {}, onWantsChange = {}, onSavingsChange = {},
                onSelectGoal = {}, onRepeatLast = {}, onConfirm = {}, onBack = {})
        } } }
        compose.onNodeWithText("Монетки пока не тратятся", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Нужное").assertIsDisplayed()
        screenshot("03-plan")
    }

    @Test
    fun receiptCorrectsPricesBeforeCheckingChange() {
        val task = ReceiptTask(id = "check", theme = TaskTheme.SHOPPING, title = "Чек", intro = "",
            explainOk = "", explainFail = "",
            cart = listOf(ReceiptItem("apple", "Яблоко", 10), ReceiptItem("soup", "Суп", 20)),
            lines = listOf(ReceiptLine("a", "apple", 15), ReceiptLine("s", "soup", 20)), paid = 50, change = 10)
        var submitted: TaskInput? = null
        compose.setContent { FinneyTheme { ReceiptGame(task, PetCharacter.PUSHISTIK, onClose = {}, onSubmit = { submitted = it }) } }
        screenshot("04-receipt-prices")
        compose.onNodeWithContentDescription("Яблоко, 15").performClick()
        compose.onNodeWithText("Проверить чек").performClick()
        compose.onNodeWithText("Теперь проверить сдачу").performClick()
        compose.onNodeWithText("На руках сдачи").assertIsDisplayed()
        screenshot("05-receipt-change")
        compose.onNodeWithContentDescription("Сдачи не хватает").performClick()
        compose.onNodeWithText("Проверить сдачу").performClick()
        compose.onNodeWithText("Теперь на руках").assertIsDisplayed()
        screenshot("07-receipt-settled")
        compose.onNodeWithText("Готово").performClick()
        compose.runOnIdle { assertEquals(TaskInput.Flags(setOf("a"), true), submitted) }
    }

    @Test
    fun choresNamesTheSelectedDayAndGoal() {
        val task = content.task("game_chores") as ChoresTask
        compose.setContent { FinneyTheme { ChoresGame(task, PetCharacter.PUSHISTIK, onClose = {}, onSubmit = {}) } }
        compose.onNodeWithText("День 1: нажми дело внизу", substring = true).assertIsDisplayed()
        screenshot("06-chores")
    }

    @Test
    fun rainyDayLetsTheChildPostponeOptionalPurchases() {
        val task = content.task("game_rainy") as ReserveTask
        var submitted: TaskInput? = null
        compose.setContent { FinneyTheme { CompositionLocalProvider(LocalAnimations provides false) {
            ReserveGame(task, PetCharacter.PUSHISTIK, null, {}, {}, { submitted = it })
        } } }
        for (label in listOf("Мячик", "Наклейки", "Мороженое")) {
            compose.onNodeWithText(label).performScrollTo().performClick()
        }
        compose.onNodeWithText("Начать неделю").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Еда на неделю").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Еда на неделю").performScrollTo().assertIsNotEnabled()
        for (label in listOf("Мячик", "Наклейки", "Мороженое")) {
            compose.onNodeWithText(label).performScrollTo().performClick()
        }
        screenshot("08-rainy-postpone")
        compose.onNodeWithText("Готово").performScrollTo().performClick()
        compose.runOnIdle {
            val done = TaskEngines.evaluate(task, checkNotNull(submitted)) as TaskEvaluation.Done
            assertEquals(TaskOutcome.SUCCESS, done.outcome)
        }
    }

    @Test
    fun roadToGoalLetsTheChildSkipEveryOptionalPurchase() {
        val task = content.task("game_race") as GoalRaceTask
        var submitted: TaskInput? = null
        compose.setContent { FinneyTheme { CompositionLocalProvider(LocalAnimations provides false) {
            GoalRaceGame(task, PetCharacter.PUSHISTIK, {}, { submitted = it })
        } } }
        for (day in 1..task.days) {
            if (task.events.any { it.day == day }) {
                compose.onNodeWithText("Пропустить").performScrollTo().performClick()
            }
            if (day == 2) screenshot("09-race-skip")
            compose.onNodeWithText(if (day == task.days) "Финиш" else "Следующий день").performClick()
        }
        compose.runOnIdle {
            val done = TaskEngines.evaluate(task, checkNotNull(submitted)) as TaskEvaluation.Done
            assertEquals(TaskOutcome.SUCCESS, done.outcome)
        }
    }
}
