package ru.finney.pet.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.model.StandIngredient
import ru.finney.pet.domain.model.StandTask
import ru.finney.pet.domain.model.TaskTheme
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.tasks.games.StandGame
import ru.finney.pet.ui.theme.FinneyTheme

/** Проверяем лавку до и после появления итоговой строки, которая уменьшает её высоту. */
@RunWith(AndroidJUnit4::class)
class StandStallLayoutTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test fun shortDayKeepsPetProportionsBeforeAndAfterServing() = checkDay(480.dp)

    @Test fun tallDayKeepsPetProportionsBeforeAndAfterServing() = checkDay(740.dp)

    private fun checkDay(height: Dp) {
        val task = StandTask(
            id = "stand-clipping", theme = TaskTheme.PLANNING, title = "Лимонадная лавка",
            intro = "", explainOk = "", explainFail = "", budget = 50,
            ingredient = StandIngredient("Лимон", price = 5, yields = 2, art = "item_food_lemon"),
            cupPrice = 5, guests = 6, forecast = "Жарко — придут 6 гостей",
        )
        compose.setContent {
            FinneyTheme {
                CompositionLocalProvider(LocalAnimations provides false) {
                    Box(Modifier.requiredSize(360.dp, height).testTag("stand-test-viewport")) {
                        StandGame(task, PetCharacter.PUSHISTIK, null, {}, {}, {})
                    }
                }
            }
        }
        repeat(3) {
            compose.onNodeWithContentDescription("Больше: Лимон").performScrollTo().performClick()
        }
        compose.onNodeWithText("Открыть лавку").assertIsDisplayed().performClick()
        screenshot("stand-${height.value.toInt()}-day")
        assertPetFitsAboveControls()
        repeat(6) {
            compose.onNodeWithContentDescription("Налить лимонад: плюс 5 монет").assertIsDisplayed().performClick()
        }
        compose.onNodeWithText("Всем хватило, и ничего не осталось!").assertIsDisplayed()
        compose.onNodeWithText("Закрыть лавку").assertIsDisplayed()
        screenshot("stand-${height.value.toInt()}-done")
        assertPetFitsAboveControls()
    }

    private fun assertPetFitsAboveControls() {
        compose.waitForIdle()
        val pet = compose.onNodeWithTag("stand-stall-pet", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val stall = compose.onNodeWithTag("stand-stall", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("stand-test-viewport").fetchSemanticsNode().boundsInRoot
        // Нейтральная поза чуть вытянута дыханием (0.992 × 1.016), это не сжатие вёрсткой.
        assertTrue("Лавка сжала питомца: $pet", abs(pet.width - pet.height) <= pet.width * 0.04f)
        // Иначе высота прилавка сжимается отдельно, и столешница перекрывает лицо.
        assertTrue("Прилавок перекрыл голову: питомец $pet, лавка $stall", pet.height <= stall.height * 0.71f)
        assertTrue("Питомец вышел ниже сцены: $pet, $viewport", pet.bottom <= viewport.bottom)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "playtest-clipping").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
