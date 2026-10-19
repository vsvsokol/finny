package ru.finney.pet.ui.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finney.pet.domain.Fixtures
import ru.finney.pet.domain.model.GoalSliderTask

class NextProgressTest {
    private val task = Fixtures.tasks.first() as GoalSliderTask
    private val content = Fixtures.content.copy(
        economy = Fixtures.economy.copy(maxLevel = 30, stageStartLevels = listOf(1, 2, 4, 6, 12, 20)),
        tasks = listOf(
            task.copy(id = "cash1", title = "Касса", series = "cash", unlockLevel = 1),
            task.copy(id = "cash2", title = "Касса", series = "cash", unlockLevel = 2),
            task.copy(id = "new", title = "Новая игра", series = "new", unlockLevel = 4),
        ),
    )

    @Test fun `на первом уровне ближайшая стадия и усложнение показаны вместе`() {
        val next = nextProgress(content, level = 1, period = 1, isDemo = false)!!
        assertEquals(2, next.level)
        assertEquals(1, next.levelsLeft)
        assertEquals(listOf("Новая стадия: карапуз.", "Более сложная мини-игра: «Касса»."), next.changes)
        assertFalse(next.changes.any { it.contains("облик") })
    }

    @Test fun `новая серия названа новой игрой а не усложнением`() {
        val next = nextProgress(content, level = 2, period = 2, isDemo = false)!!
        assertEquals(4, next.level)
        assertEquals(2, next.levelsLeft)
        assertTrue(next.changes.contains("Новая мини-игра: «Новая игра»."))
        assertFalse(next.changes.any { it.contains("Касса") })
    }

    @Test fun `не обещаем недоступное по периоду задание`() {
        val gated = content.copy(tasks = listOf(
            task.copy(id = "early", series = "gated", unlockLevel = 1),
            task.copy(id = "late", series = "gated", unlockLevel = 3, unlockPeriod = 10),
        ))
        assertEquals(4, nextProgress(gated, level = 2, period = 2, isDemo = false)!!.level)
        assertEquals(3, nextProgress(gated, level = 2, period = 9, isDemo = false)!!.level)
        assertEquals(3, nextProgress(gated, level = 2, period = 2, isDemo = true)!!.level)
    }

    @Test fun `в демо первая версия игры уже доступна и не обещается как новая`() {
        val future = content.copy(tasks = listOf(task.copy(unlockLevel = 3, unlockPeriod = 10)))
        assertEquals(4, nextProgress(future, level = 2, period = 2, isDemo = true)!!.level)
    }

    @Test fun `за пределами максимального уровня нет обещанных открытий`() {
        val short = content.copy(economy = content.economy.copy(maxLevel = 3))
        val next = nextProgress(short, level = 2, period = 2, isDemo = false)!!
        assertEquals(3, next.level)
        assertEquals(listOf("Самый высокий уровень питомца."), next.changes)
    }

    @Test fun `после последнего открытия остаётся достижимый максимальный уровень`() {
        val next = nextProgress(content, level = 26, period = 26, isDemo = false)!!
        assertEquals(30, next.level)
        assertEquals(4, next.levelsLeft)
    }

    @Test fun `на максимальном уровне новых открытий не обещаем`() {
        assertNull(nextProgress(content, level = 30, period = 45, isDemo = false))
    }
}
