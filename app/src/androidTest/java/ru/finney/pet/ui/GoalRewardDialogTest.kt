package ru.finney.pet.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.content.AssetContentLoader
import ru.finney.pet.ui.components.ActionFeedback
import ru.finney.pet.ui.goals.GoalDoneDialog
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.theme.FinneyTheme
import java.io.File

/** Запускать на окне 360 × 640 dp: длинный итог последней цели при шрифте 130%. */
@RunWith(AndroidJUnit4::class)
class GoalRewardDialogTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test fun lastRewardCanBeDismissedAtLargeFont() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val goal = AssetContentLoader(context).load().goals.last()
        val next = "Все награды собраны! Теперь можно откладывать монеты в запас."
        val feedback = ActionFeedback(
            title = "${goal.label} — твоя!",
            lines = emptyList(),
            why = "Копил — и получилось! Награда уже на питомце. " +
                "Остаток 10 монет вернулся в кошелёк. $next",
            next = next,
            itemId = goal.reward,
        )
        var dismissed = false
        compose.setContent {
            val density = LocalDensity.current
            FinneyTheme {
                CompositionLocalProvider(
                    LocalAnimations provides false,
                    LocalDensity provides Density(density.density, 1.3f),
                ) {
                    GoalDoneDialog(feedback) { dismissed = true }
                }
            }
        }
        // Снимок сохраняется до проверки, чтобы увидеть причину даже при падении теста.
        shot("last-reward-font130-before")
        val button = compose.onNodeWithText("Здорово!")
        try {
            button.assertIsDisplayed()
        } catch (_: AssertionError) {
            button.performScrollTo().assertIsDisplayed()
        }
        shot("last-reward-font130-button")
        button.performClick()
        compose.runOnIdle { assertTrue("Кнопка должна закрывать итог награды", dismissed) }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(350)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "goal-fix-screens")
            .apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
