package ru.finney.pet.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.content.AssetContentLoader
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.model.*
import ru.finney.pet.ui.goals.GoalsContent
import ru.finney.pet.ui.goals.GoalsUiState
import ru.finney.pet.ui.home.HomeContent
import ru.finney.pet.ui.home.HomeUiState
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.period.PeriodResultContent
import ru.finney.pet.ui.period.PeriodResultUiState
import ru.finney.pet.ui.progress.NextProgress
import ru.finney.pet.ui.shop.ShelfRow
import ru.finney.pet.ui.shop.ShopContent
import ru.finney.pet.ui.shop.ShopShelf
import ru.finney.pet.ui.shop.ShopUiState
import ru.finney.pet.ui.theme.FinneyTheme
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProgressionScreenTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val content by lazy { AssetContentLoader(context).load() }

    private fun show(block: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            FinneyTheme {
                CompositionLocalProvider(LocalAnimations provides false,
                    LocalDensity provides Density(density.density, 1.3f)) {
                    Box(Modifier.width(360.dp)) { block() }
                }
            }
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.getExternalFilesDir(null), "progression-check").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun shopTransferAndSingleTeaserAtLargeFont() {
        val game = Game(content)
        val state = game.newGame()
        val item = content.forSale.first { it.category == Category.NEEDS }
        val preview = game.previewPurchase(state, item.id)!!
        val upcoming = content.forSale.first { it.unlockLevel > 1 }
        var fromWants = ""
        var bought = ""
        show {
            ShopContent(ShopUiState.Ready(50, true, listOf(ShelfRow(ShopShelf.FOOD, listOf(preview))), item.id,
                0, 30, null, null, upcoming, mapOf(item.id to 10)), {}, { bought = it }, {}, {}, {}, {},
                onBuyFromWants = { fromWants = it })
        }
        compose.onNodeWithText("Взять и купить").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(item.id, fromWants); assertEquals("", bought) }
        compose.onNodeWithText("Скоро: ${upcoming.label} — на уровне ${upcoming.unlockLevel}").performScrollTo().assertIsDisplayed()
        shot("shop-font130")
        compose.onNodeWithText("Купить сверх плана").performClick()
        compose.runOnIdle { assertEquals(item.id, bought) }
    }

    @Test fun lockedGoalTeaserHasNoSelectionAction() {
        val goal = content.goals.first()
        var picked = false
        show { GoalsContent(GoalsUiState.Ready(emptyList(), null, 50, false, false, null, upcoming = goal),
            { picked = true }, {}, {}, {}, {}) }
        compose.onNodeWithText("Скоро: ${goal.label} — на уровне ${goal.unlockLevel}").assertIsDisplayed().assertHasNoClickAction()
        compose.runOnIdle { assertTrue(!picked) }
        shot("goals-font130")
    }

    @Test fun resultHidesEmptyBlockAndKeepsNextPlanAction() {
        var openings by mutableStateOf(emptyList<String>())
        var planned = false
        val state = PeriodResultUiState.Ready(1, 1, Plan(50, 20, 10, 10), PeriodFacts(20, 10, 10, 0),
            true, true, true, 2, true, 2, true, 55, income = 55, canPlanNext = true)
        show { PeriodResultContent(state.copy(unlocks = openings), {}, { planned = true }) }
        compose.onNodeWithText("Открылось").assertDoesNotExist()
        compose.runOnIdle { openings = listOf("Мини-игра «Касса Финни».", "В магазине: Мячик.", "Цель «Корона».", "Звание: Планировщик.") }
        compose.onNodeWithText("Звание: Планировщик.").performScrollTo().assertIsDisplayed()
        shot("result-font130")
        compose.onNodeWithText("Составить следующий план").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(planned) }
    }

    @Test fun homeShowsTitleAndOpensFullPreview() {
        val game = Game(content)
        val state = game.newGame()
        val appearance = PetAppearance(PetCharacter.PUSHISTIK, BodyColor.A, EyesVariant.ROUND)
        val home = HomeUiState.Ready("Финни", appearance, false, state.pet, game.emotion(state), 1,
            game.levelCheck(state), 1, 50, 0, null, 1, PeriodPhase.PLANNING, game.needsHint(state),
            null, emptyList(), emptyList(), title = "Новичок",
            path = ru.finney.pet.ui.progress.levelNodes(
                game, content, state, appearance.character,
                levels = ru.finney.pet.ui.home.miniPathLevels(1, content.economy.maxLevel)))
        var pathOpened = false
        show { HomeContent(home, {}, {}, {}, {}, {}, {}, { pathOpened = true }, {}, {}, {}, {}) }
        // Полосы со званием на главном нет (20.10: сдвигала кнопки) — открытия в панели уровня.
        compose.onNodeWithText("Звание: Новичок").assertDoesNotExist()
        shot("home-font130")
        compose.onNode(SemanticsMatcher("клик «Что нужно для уровня»") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == "Что нужно для уровня"
        }).performClick()
        compose.onNodeWithContentDescription("Уровень 1, ты здесь", useUnmergedTree = true).assertIsDisplayed()
        shot("home-level-font130")
        compose.onNodeWithText("Весь путь ›").performClick()
        compose.runOnIdle { assertTrue(pathOpened) }
    }
}
