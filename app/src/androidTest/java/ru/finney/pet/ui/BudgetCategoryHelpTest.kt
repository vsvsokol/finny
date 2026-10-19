package ru.finney.pet.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.content.AssetContentLoader
import ru.finney.pet.ui.budget.BudgetUiState
import ru.finney.pet.ui.budget.PlanningContent
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.theme.FinneyTheme
import java.io.File

@RunWith(AndroidJUnit4::class)
class BudgetCategoryHelpTest {
    @get:Rule val compose = createComposeRule()

    @Test fun existingTermsAreAvailableAtEveryPlanRowAtLargeFont() = verify(1.3f)
    @Test fun existingTermsAreAvailableAtDefaultFont() = verify(1f)

    private fun verify(fontScale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = AssetContentLoader(context).load()
        fun term(id: String) = content.glossary.first { it.id == id }.text
        val goal = content.goals.first()
        var changed = false
        var confirmed = false
        val state = BudgetUiState.Planning(
            periodNumber = 1, level = 1, budget = 50,
            needs = 20, wants = 10, savings = 15, needsHint = 20,
            goalLabel = goal.label, goalReward = goal.reward,
            rejection = null, isSaving = false,
            needsHelp = term("needs"), wantsHelp = term("wants"), savingsHelp = term("savings"),
        )
        compose.setContent {
            val density = LocalDensity.current
            FinneyTheme {
                CompositionLocalProvider(LocalAnimations provides false,
                    LocalDensity provides Density(density.density, fontScale)) {
                    Box(Modifier.width(360.dp)) {
                        PlanningContent(state, { changed = true }, { changed = true }, { changed = true },
                            onSelectGoal = { changed = true }, onRepeatLast = { changed = true },
                            onConfirm = { confirmed = true }, onBack = {})
                    }
                }
            }
        }
        for ((label, id) in listOf("Нужное" to "needs", "Желаемое" to "wants", "Копилка" to "savings")) {
            compose.onNodeWithContentDescription("Подсказка: $label").performScrollTo().performClick()
            compose.onNodeWithText(term(id)).assertIsDisplayed()
            saveScreenshot("$id-${(fontScale * 100).toInt()}.png")
            pressBack()
            compose.onNodeWithText(term(id)).assertDoesNotExist()
        }
        compose.onNodeWithText("Подтвердить план").assertIsEnabled()
        compose.runOnIdle { assertFalse(changed); assertFalse(confirmed) }
        saveScreenshot("plan-${(fontScale * 100).toInt()}.png")
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "stage2-plan-help").apply { mkdirs() }
        File(directory, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
