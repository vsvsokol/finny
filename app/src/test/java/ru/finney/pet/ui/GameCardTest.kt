package ru.finney.pet.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.finney.pet.domain.model.Category
import ru.finney.pet.ui.tasks.GameHelp
import ru.finney.pet.ui.tasks.StepPart
import ru.finney.pet.ui.tasks.roleLead
import ru.finney.pet.ui.tasks.stepParts

class GameCardTest {

    @Test
    fun `кто ты — до двоеточия или до конца первого предложения`() {
        assertEquals("Ты — сортировщик:", roleLead("Ты — сортировщик: разбираешь покупки Финни."))
        assertEquals("Ты — покупатель.", roleLead("Ты — покупатель. Другой питомец — кассир."))
        assertEquals("Ты — то кассир, то покупатель.", roleLead("Ты — то кассир, то покупатель. Роль подписана в каждом раунде."))
        assertEquals("без точки — вся строка", "Ты — кассир", roleLead("Ты — кассир"))
        assertEquals("выделение можно задать явно", "Ты — покупатель",
            GameHelp("Ты — покупатель в магазине.", "", "", lead = "Ты — покупатель").lead)
    }

    @Test
    fun `«Нужное» и «Хочется» — плашками, точка остаётся при плашке`() {
        assertEquals(
            listOf(
                StepPart.Word("Отнеси"), StepPart.Word("каждую"), StepPart.Word("в"), StepPart.Word("корзину:"),
                StepPart.Chip(Category.NEEDS, ""), StepPart.Word("или"), StepPart.Chip(Category.WANTS, "."),
            ),
            stepParts("Отнеси каждую в корзину: «Нужное» или «Хочется»."),
        )
        assertEquals("другие кавычки — просто слова", listOf(StepPart.Word("Копит"), StepPart.Word("на"), StepPart.Word("«Кубик».")),
            stepParts("Копит на «Кубик»."))
    }
}
