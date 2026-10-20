package ru.finney.pet.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.domain.profile.PetNameError
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.onboarding.OnboardingScreen
import ru.finney.pet.ui.onboarding.PetSetupContent
import ru.finney.pet.ui.onboarding.PetSetupUiState
import ru.finney.pet.ui.theme.FinneyTheme

@RunWith(AndroidJUnit4::class)
class OnboardingOverflowTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor: Float = 0f
    })

    @Test fun shortOnboardingKeepsNavigationAndEveryInstructionReachable() {
        var finished = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 1.3f),
                LocalAnimations provides false,
            ) {
                FinneyTheme {
                    Box(Modifier.requiredSize(360.dp, 480.dp)) {
                        OnboardingScreen(false, onFinish = { finished = true }, onStartDemo = {})
                    }
                }
            }
        }
        compose.onNodeWithText("Дальше").assertIsDisplayed().performClick()
        compose.onNodeWithText("Дальше").assertIsDisplayed()
        compose.onNodeWithText("В копилку на большую цель").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Дальше").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("4. Достигай высот. Новый уровень — новые игры")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Понятно!").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(finished) }
    }

    @Test fun shortSetupWithValidationErrorKeepsSaveReachable() {
        var saved = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 1.3f),
                LocalAnimations provides false,
            ) {
                FinneyTheme {
                    Box(Modifier.requiredSize(360.dp, 320.dp)) {
                        PetSetupContent(
                            state = PetSetupUiState(isEditing = false, nameError = PetNameError.BLANK),
                            isDemo = true,
                            onNameChange = {}, onCharacterChange = {}, onSave = { saved = true },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Придумай имя питомцу").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Начать игру").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(saved) }
    }
}
