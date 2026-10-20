package ru.finney.pet.ui.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Игрушка, брошенная за питомца, отъезжает вбок и остаётся видна. */
class ToyBehindPetTest {
    private val floor = ToyFloor(0.1f, 0.9f)
    private val pet = RelRect(0.4f, 0.4f, 0.6f, 0.74f)

    @Test fun toyBehindPetSlidesToNearerSide() {
        val toy = RelRect(0.44f, 0.6f, 0.5f, 0.66f)
        val dx = outFromBehind(toy, pet, floor)
        assertTrue("сдвиг влево, к ближнему краю: $dx", dx < 0f)
        val moved = toy.shiftedX(dx)
        assertTrue("за краем тела питомца", moved.right <= pet.left + pet.width * 0.18f)
    }

    @Test fun toyInFrontOfPetStays() {
        assertEquals(0f, outFromBehind(RelRect(0.45f, 0.70f, 0.51f, 0.76f), pet, floor), 0f)
    }

    @Test fun toyBesidePetStays() {
        assertEquals(0f, outFromBehind(RelRect(0.2f, 0.6f, 0.26f, 0.66f), pet, floor), 0f)
    }

    @Test fun noFloorOnNearSideGoesToFarSide() {
        val narrow = ToyFloor(0.42f, 0.9f)
        val toy = RelRect(0.44f, 0.6f, 0.5f, 0.66f)
        assertTrue(outFromBehind(toy, pet, narrow) > 0f)
    }
}
