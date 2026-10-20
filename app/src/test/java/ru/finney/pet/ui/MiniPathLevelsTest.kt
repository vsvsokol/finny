package ru.finney.pet.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.finney.pet.ui.home.miniPathLevels

class MiniPathLevelsTest {

    @Test
    fun `пять уровней вокруг текущего, у краёв окно сдвигается`() {
        assertEquals(1..5, miniPathLevels(current = 1, maxLevel = 30))
        assertEquals(1..5, miniPathLevels(current = 3, maxLevel = 30))
        assertEquals(8..12, miniPathLevels(current = 10, maxLevel = 30))
        assertEquals(26..30, miniPathLevels(current = 30, maxLevel = 30))
        assertEquals(1..3, miniPathLevels(current = 2, maxLevel = 3))
    }
}
