package ru.finney.pet.ui

import android.os.SystemClock
import ru.finney.pet.ui.components.ActionFeedback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.content.AssetContentLoader
import ru.finney.pet.domain.game.*
import ru.finney.pet.domain.model.*
import ru.finney.pet.domain.pet.Emotion
import ru.finney.pet.ui.home.*
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.theme.FinneyTheme

/** Нативная запись с обычными Android-часами, без покупок и сохранения тестового состояния. */
@RunWith(AndroidJUnit4::class)
class HomeMotionReviewTest {
    @Test fun recordHomeMotion() {
        lateinit var reviewActivity: ComponentActivity
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val state = mutableStateOf(initialState().copy(emotion = Emotion.CALM))
        val feedback = mutableStateOf<ActionFeedback?>(null)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                reviewActivity = activity
                activity.setContent {
                FinneyTheme { CompositionLocalProvider(LocalAnimations provides true) {
                    HomeContent(state.value, purchased = feedback.value, onOpenBudget = {}, onOpenShop = {}, onOpenGoals = {},
                        onOpenTasks = {}, onOpenTask = {}, onOpenWardrobe = {}, onOpenProgress = {},
                        onOpenSettings = {}, onOpenHelp = {}, onClosePeriod = {}, onBuy = {})
                } }
            } }
            SystemClock.sleep(1_000)
            instrumentation.runOnMainSync { state.value = state.value.copy(emotion = Emotion.HUNGRY) }
            SystemClock.sleep(2_500)
            instrumentation.runOnMainSync { state.value = state.value.copy(emotion = Emotion.HAPPY) }
            SystemClock.sleep(1_000)
            instrumentation.runOnMainSync { feedback.value = ActionFeedback("Финни поел!", emptyList(), "", "") }
            SystemClock.sleep(2_000)
            instrumentation.runOnMainSync { feedback.value = null }
            SystemClock.sleep(700)
            instrumentation.runOnMainSync { reviewActivity.finish() }
        }
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

}
