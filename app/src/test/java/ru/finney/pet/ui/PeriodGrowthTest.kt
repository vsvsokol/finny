package ru.finney.pet.ui.period

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finney.pet.domain.Fixtures
import ru.finney.pet.domain.model.GoalSliderTask

class PeriodGrowthTest {
    private val task = Fixtures.tasks.first() as GoalSliderTask
    private val content = Fixtures.content.copy(
        economy = Fixtures.economy.copy(maxLevel = 30, stageStartLevels = listOf(1, 2, 4, 6, 12, 20)),
        tasks = listOf(
            task.copy(id = "first", title = "Касса", series = "cash", unlockLevel = 1),
            task.copy(id = "second", title = "Касса", series = "cash", unlockLevel = 2),
        ),
    )

    @Test fun `успешный рост показывает только реальные изменения перехода`() {
        val growth = periodGrowth(content, 1, 2, 1, false, true)!!
        assertEquals(listOf("Новая стадия: карапуз.", "Стала сложнее мини-игра: «Касса»."), growth.changes)
        assertEquals(4, growth.next!!.level)
    }

    @Test fun `при повторе уровня ничего нового не обещаем`() {
        assertNull(periodGrowth(content, 1, 1, 1, false, true))
    }

    @Test fun `исторический итог не показывает будущие открытия текущего профиля`() {
        assertNull(periodGrowth(content, 1, 2, 1, false, false))
    }

    @Test fun `следующий вариант недоступный по периоду не объявляется открытым`() {
        val gated = content.copy(tasks = listOf(
            task.copy(id = "first", title = "Касса", series = "cash", unlockLevel = 1),
            task.copy(id = "second", title = "Касса", series = "cash", unlockLevel = 2, unlockPeriod = 10),
        ))
        assertEquals(listOf("Новая стадия: карапуз."), periodGrowth(gated, 1, 2, 1, false, true)!!.changes)
        assertTrue(periodGrowth(gated, 1, 2, 1, true, true)!!.changes.any { it.contains("Касса") })
    }

    @Test fun `первая игра открывшаяся по периоду названа новой`() {
        val fresh = content.copy(tasks = listOf(task.copy(title = "Копилка", unlockLevel = 1, unlockPeriod = 2)))
        assertTrue(periodGrowth(fresh, 1, 2, 1, false, true)!!.changes.contains("Открылась мини-игра: «Копилка»."))
        assertEquals(listOf("Новая стадия: карапуз."), periodGrowth(fresh, 1, 2, 1, true, true)!!.changes)
    }

    @Test fun `на максимальном уровне не обещаем следующий`() {
        val growth = periodGrowth(content, 29, 30, 35, false, true)!!
        assertTrue(growth.changes.isEmpty())
        assertNull(growth.next)
    }
}
