package ru.finney.pet.ui

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import ru.finney.pet.domain.model.BodyColor
import ru.finney.pet.domain.model.EyesVariant
import ru.finney.pet.domain.model.PetAppearance
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.domain.profile.PetNameError
import ru.finney.pet.ui.onboarding.PetSetupEvent
import ru.finney.pet.ui.onboarding.PetSetupViewModel

class PetSetupViewModelTest : ViewModelTest() {

    @Test
    fun `пустое имя — ошибка и профиль не создаётся, ввод сбрасывает ошибку`() = test {
        val viewModel = PetSetupViewModel(session, isEditing = false)

        viewModel.setName("   ")
        viewModel.save()
        settle()

        assertEquals(PetNameError.BLANK, viewModel.uiState.value.nameError)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(session.activeGame.first())

        viewModel.setName("Ф")
        assertNull(viewModel.uiState.value.nameError)
    }

    @Test
    fun `создание открывает профиль с выбранным питомцем и внешностью`() = test {
        val viewModel = PetSetupViewModel(session, isEditing = false)

        viewModel.setName(" Финни ")
        viewModel.setCharacter(PetCharacter.ROGATIK)
        viewModel.setEyes(EyesVariant.SLY)
        viewModel.save()

        assertEquals(PetSetupEvent.Saved, viewModel.events.first())
        val profile = session.activeGame.first()!!.profile
        assertEquals("Финни", profile.petName)
        assertEquals(PetAppearance(PetCharacter.ROGATIK, BodyColor.A, EyesVariant.SLY), profile.appearance)
    }

    @Test
    fun `повторная настройка подставляет текущие имя и внешность`() = test {
        session.createProfile("Финни", PetAppearance(PetCharacter.ROGATIK, BodyColor.B, EyesVariant.OVAL))
        val viewModel = PetSetupViewModel(session, isEditing = true)
        settle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Финни", viewModel.uiState.value.name)
        assertEquals(PetAppearance(PetCharacter.ROGATIK, BodyColor.B, EyesVariant.OVAL), viewModel.uiState.value.appearance)

        viewModel.setName("Бублик")
        viewModel.save()

        assertEquals(PetSetupEvent.Saved, viewModel.events.first())
        assertEquals("Бублик", session.activeGame.first()!!.profile.petName)
        assertEquals(1, storage.all.value.size)
    }
}
