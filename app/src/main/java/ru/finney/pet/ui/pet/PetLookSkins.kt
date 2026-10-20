package ru.finney.pet.ui.pet

import ru.finney.pet.R

// Создано tools/make_pet_looks.py — не править руками, перезапустить инструмент.

/**
 * Скин платной окраски [lookId] поверх скина персонажа [base]: те же точки вращения,
 * шея, глаза и посадка шляпы, другие картинки. null — облик не этого персонажа или незнаком.
 */
internal fun lookSkin(lookId: String, base: PetSkin): PetSkin? =
    if (!lookId.startsWith("look_") || base != lookCharacterSkin(lookId)) null else when (lookId) {
        "look_pushistik_1" -> base.copy(
            happy = R.drawable.look_pushistik_1_base_happy,
            sad = R.drawable.look_pushistik_1_base_sad,
            dirty = R.drawable.look_pushistik_1_base_dirty,
            sleep = R.drawable.look_pushistik_1_base_sleep,
            blinkHappy = R.drawable.look_pushistik_1_blink_happy,
            blinkSad = R.drawable.look_pushistik_1_blink_sad,
            blinkDirty = R.drawable.look_pushistik_1_blink_dirty,
            mouth = R.drawable.look_pushistik_1_mouth,
            bodies = listOf(
                PetBody(R.drawable.look_pushistik_1_torso, R.drawable.look_pushistik_1_left_hand, R.drawable.look_pushistik_1_right_hand, R.drawable.look_pushistik_1_left_leg, R.drawable.look_pushistik_1_right_leg),
                PetBody(R.drawable.look_pushistik_1_torso_2, R.drawable.look_pushistik_1_left_hand_2, R.drawable.look_pushistik_1_right_hand_2, R.drawable.look_pushistik_1_left_leg_2, R.drawable.look_pushistik_1_right_leg_2),
                PetBody(R.drawable.look_pushistik_1_torso_3, R.drawable.look_pushistik_1_left_hand_3, R.drawable.look_pushistik_1_right_hand_3, R.drawable.look_pushistik_1_left_leg_3, R.drawable.look_pushistik_1_right_leg_3),
            ),
        )
        "look_pushistik_2" -> base.copy(
            happy = R.drawable.look_pushistik_2_base_happy,
            sad = R.drawable.look_pushistik_2_base_sad,
            dirty = R.drawable.look_pushistik_2_base_dirty,
            sleep = R.drawable.look_pushistik_2_base_sleep,
            blinkHappy = R.drawable.look_pushistik_2_blink_happy,
            blinkSad = R.drawable.look_pushistik_2_blink_sad,
            blinkDirty = R.drawable.look_pushistik_2_blink_dirty,
            mouth = R.drawable.look_pushistik_2_mouth,
            bodies = listOf(
                PetBody(R.drawable.look_pushistik_2_torso, R.drawable.look_pushistik_2_left_hand, R.drawable.look_pushistik_2_right_hand, R.drawable.look_pushistik_2_left_leg, R.drawable.look_pushistik_2_right_leg),
                PetBody(R.drawable.look_pushistik_2_torso_2, R.drawable.look_pushistik_2_left_hand_2, R.drawable.look_pushistik_2_right_hand_2, R.drawable.look_pushistik_2_left_leg_2, R.drawable.look_pushistik_2_right_leg_2),
                PetBody(R.drawable.look_pushistik_2_torso_3, R.drawable.look_pushistik_2_left_hand_3, R.drawable.look_pushistik_2_right_hand_3, R.drawable.look_pushistik_2_left_leg_3, R.drawable.look_pushistik_2_right_leg_3),
            ),
        )
        "look_rogatik_1" -> base.copy(
            happy = R.drawable.look_rogatik_1_base_happy,
            sad = R.drawable.look_rogatik_1_base_sad,
            dirty = R.drawable.look_rogatik_1_base_dirty,
            sleep = R.drawable.look_rogatik_1_base_sleep,
            blinkHappy = R.drawable.look_rogatik_1_blink_happy,
            blinkSad = R.drawable.look_rogatik_1_blink_sad,
            blinkDirty = R.drawable.look_rogatik_1_blink_dirty,
            mouth = R.drawable.look_rogatik_1_mouth,
            bodies = listOf(
                PetBody(R.drawable.look_rogatik_1_torso, R.drawable.look_rogatik_1_left_hand, R.drawable.look_rogatik_1_right_hand, R.drawable.look_rogatik_1_left_leg, R.drawable.look_rogatik_1_right_leg),
                PetBody(R.drawable.look_rogatik_1_torso_2, R.drawable.look_rogatik_1_left_hand_2, R.drawable.look_rogatik_1_right_hand_2, R.drawable.look_rogatik_1_left_leg_2, R.drawable.look_rogatik_1_right_leg_2),
                PetBody(R.drawable.look_rogatik_1_torso_3, R.drawable.look_rogatik_1_left_hand_3, R.drawable.look_rogatik_1_right_hand_3, R.drawable.look_rogatik_1_left_leg_3, R.drawable.look_rogatik_1_right_leg_3),
            ),
        )
        "look_rogatik_2" -> base.copy(
            happy = R.drawable.look_rogatik_2_base_happy,
            sad = R.drawable.look_rogatik_2_base_sad,
            dirty = R.drawable.look_rogatik_2_base_dirty,
            sleep = R.drawable.look_rogatik_2_base_sleep,
            blinkHappy = R.drawable.look_rogatik_2_blink_happy,
            blinkSad = R.drawable.look_rogatik_2_blink_sad,
            blinkDirty = R.drawable.look_rogatik_2_blink_dirty,
            mouth = R.drawable.look_rogatik_2_mouth,
            bodies = listOf(
                PetBody(R.drawable.look_rogatik_2_torso, R.drawable.look_rogatik_2_left_hand, R.drawable.look_rogatik_2_right_hand, R.drawable.look_rogatik_2_left_leg, R.drawable.look_rogatik_2_right_leg),
                PetBody(R.drawable.look_rogatik_2_torso_2, R.drawable.look_rogatik_2_left_hand_2, R.drawable.look_rogatik_2_right_hand_2, R.drawable.look_rogatik_2_left_leg_2, R.drawable.look_rogatik_2_right_leg_2),
                PetBody(R.drawable.look_rogatik_2_torso_3, R.drawable.look_rogatik_2_left_hand_3, R.drawable.look_rogatik_2_right_hand_3, R.drawable.look_rogatik_2_left_leg_3, R.drawable.look_rogatik_2_right_leg_3),
            ),
        )
        "look_zvezdochka_1" -> base.copy(
            happy = R.drawable.look_zvezdochka_1_base_happy,
            sad = R.drawable.look_zvezdochka_1_base_sad,
            dirty = R.drawable.look_zvezdochka_1_base_dirty,
            sleep = R.drawable.look_zvezdochka_1_base_sleep,
            blinkHappy = R.drawable.look_zvezdochka_1_blink_happy,
            blinkSad = R.drawable.look_zvezdochka_1_blink_sad,
            blinkDirty = R.drawable.look_zvezdochka_1_blink_dirty,
            mouth = R.drawable.look_zvezdochka_1_mouth,
            bodies = listOf(
                PetBody(R.drawable.look_zvezdochka_1_torso, R.drawable.look_zvezdochka_1_left_hand, R.drawable.look_zvezdochka_1_right_hand, R.drawable.look_zvezdochka_1_left_leg, R.drawable.look_zvezdochka_1_right_leg),
                PetBody(R.drawable.look_zvezdochka_1_torso_2, R.drawable.look_zvezdochka_1_left_hand_2, R.drawable.look_zvezdochka_1_right_hand_2, R.drawable.look_zvezdochka_1_left_leg_2, R.drawable.look_zvezdochka_1_right_leg_2),
                PetBody(R.drawable.look_zvezdochka_1_torso_3, R.drawable.look_zvezdochka_1_left_hand_3, R.drawable.look_zvezdochka_1_right_hand_3, R.drawable.look_zvezdochka_1_left_leg_3, R.drawable.look_zvezdochka_1_right_leg_3),
            ),
        )
        "look_zvezdochka_2" -> base.copy(
            happy = R.drawable.look_zvezdochka_2_base_happy,
            sad = R.drawable.look_zvezdochka_2_base_sad,
            dirty = R.drawable.look_zvezdochka_2_base_dirty,
            sleep = R.drawable.look_zvezdochka_2_base_sleep,
            blinkHappy = R.drawable.look_zvezdochka_2_blink_happy,
            blinkSad = R.drawable.look_zvezdochka_2_blink_sad,
            blinkDirty = R.drawable.look_zvezdochka_2_blink_dirty,
            mouth = R.drawable.look_zvezdochka_2_mouth,
            bodies = listOf(
                PetBody(R.drawable.look_zvezdochka_2_torso, R.drawable.look_zvezdochka_2_left_hand, R.drawable.look_zvezdochka_2_right_hand, R.drawable.look_zvezdochka_2_left_leg, R.drawable.look_zvezdochka_2_right_leg),
                PetBody(R.drawable.look_zvezdochka_2_torso_2, R.drawable.look_zvezdochka_2_left_hand_2, R.drawable.look_zvezdochka_2_right_hand_2, R.drawable.look_zvezdochka_2_left_leg_2, R.drawable.look_zvezdochka_2_right_leg_2),
                PetBody(R.drawable.look_zvezdochka_2_torso_3, R.drawable.look_zvezdochka_2_left_hand_3, R.drawable.look_zvezdochka_2_right_hand_3, R.drawable.look_zvezdochka_2_left_leg_3, R.drawable.look_zvezdochka_2_right_leg_3),
            ),
        )
        "look_bantik_1" -> base.copy(
            happy = R.drawable.look_bantik_1_base_happy,
            sad = R.drawable.look_bantik_1_base_sad,
            dirty = R.drawable.look_bantik_1_base_dirty,
            sleep = R.drawable.look_bantik_1_base_sleep,
            blinkHappy = R.drawable.look_bantik_1_blink_happy,
            blinkSad = R.drawable.look_bantik_1_blink_sad,
            blinkDirty = R.drawable.look_bantik_1_blink_dirty,
            mouth = R.drawable.look_bantik_1_mouth,
            bodies = listOf(
                PetBody(R.drawable.look_bantik_1_torso, R.drawable.look_bantik_1_left_hand, R.drawable.look_bantik_1_right_hand, R.drawable.look_bantik_1_left_leg, R.drawable.look_bantik_1_right_leg),
                PetBody(R.drawable.look_bantik_1_torso_2, R.drawable.look_bantik_1_left_hand_2, R.drawable.look_bantik_1_right_hand_2, R.drawable.look_bantik_1_left_leg_2, R.drawable.look_bantik_1_right_leg_2),
                PetBody(R.drawable.look_bantik_1_torso_3, R.drawable.look_bantik_1_left_hand_3, R.drawable.look_bantik_1_right_hand_3, R.drawable.look_bantik_1_left_leg_3, R.drawable.look_bantik_1_right_leg_3),
            ),
        )
        "look_bantik_2" -> base.copy(
            happy = R.drawable.look_bantik_2_base_happy,
            sad = R.drawable.look_bantik_2_base_sad,
            dirty = R.drawable.look_bantik_2_base_dirty,
            sleep = R.drawable.look_bantik_2_base_sleep,
            blinkHappy = R.drawable.look_bantik_2_blink_happy,
            blinkSad = R.drawable.look_bantik_2_blink_sad,
            blinkDirty = R.drawable.look_bantik_2_blink_dirty,
            mouth = R.drawable.look_bantik_2_mouth,
            bodies = listOf(
                PetBody(R.drawable.look_bantik_2_torso, R.drawable.look_bantik_2_left_hand, R.drawable.look_bantik_2_right_hand, R.drawable.look_bantik_2_left_leg, R.drawable.look_bantik_2_right_leg),
                PetBody(R.drawable.look_bantik_2_torso_2, R.drawable.look_bantik_2_left_hand_2, R.drawable.look_bantik_2_right_hand_2, R.drawable.look_bantik_2_left_leg_2, R.drawable.look_bantik_2_right_leg_2),
                PetBody(R.drawable.look_bantik_2_torso_3, R.drawable.look_bantik_2_left_hand_3, R.drawable.look_bantik_2_right_hand_3, R.drawable.look_bantik_2_left_leg_3, R.drawable.look_bantik_2_right_leg_3),
            ),
        )
        "look_luchik_1" -> base.copy(
            happy = R.drawable.look_luchik_1_base_happy,
            sad = R.drawable.look_luchik_1_base_sad,
            dirty = R.drawable.look_luchik_1_base_dirty,
            sleep = R.drawable.look_luchik_1_base_sleep,
            blinkHappy = R.drawable.look_luchik_1_blink_happy,
            blinkSad = R.drawable.look_luchik_1_blink_sad,
            blinkDirty = R.drawable.look_luchik_1_blink_dirty,
            mouth = R.drawable.look_luchik_1_mouth,
            bodies = listOf(
                PetBody(R.drawable.look_luchik_1_torso, R.drawable.look_luchik_1_left_hand, R.drawable.look_luchik_1_right_hand, R.drawable.look_luchik_1_left_leg, R.drawable.look_luchik_1_right_leg),
                PetBody(R.drawable.look_luchik_1_torso_2, R.drawable.look_luchik_1_left_hand_2, R.drawable.look_luchik_1_right_hand_2, R.drawable.look_luchik_1_left_leg_2, R.drawable.look_luchik_1_right_leg_2),
                PetBody(R.drawable.look_luchik_1_torso_3, R.drawable.look_luchik_1_left_hand_3, R.drawable.look_luchik_1_right_hand_3, R.drawable.look_luchik_1_left_leg_3, R.drawable.look_luchik_1_right_leg_3),
            ),
        )
        "look_luchik_2" -> base.copy(
            happy = R.drawable.look_luchik_2_base_happy,
            sad = R.drawable.look_luchik_2_base_sad,
            dirty = R.drawable.look_luchik_2_base_dirty,
            sleep = R.drawable.look_luchik_2_base_sleep,
            blinkHappy = R.drawable.look_luchik_2_blink_happy,
            blinkSad = R.drawable.look_luchik_2_blink_sad,
            blinkDirty = R.drawable.look_luchik_2_blink_dirty,
            mouth = R.drawable.look_luchik_2_mouth,
            bodies = listOf(
                PetBody(R.drawable.look_luchik_2_torso, R.drawable.look_luchik_2_left_hand, R.drawable.look_luchik_2_right_hand, R.drawable.look_luchik_2_left_leg, R.drawable.look_luchik_2_right_leg),
                PetBody(R.drawable.look_luchik_2_torso_2, R.drawable.look_luchik_2_left_hand_2, R.drawable.look_luchik_2_right_hand_2, R.drawable.look_luchik_2_left_leg_2, R.drawable.look_luchik_2_right_leg_2),
                PetBody(R.drawable.look_luchik_2_torso_3, R.drawable.look_luchik_2_left_hand_3, R.drawable.look_luchik_2_right_hand_3, R.drawable.look_luchik_2_left_leg_3, R.drawable.look_luchik_2_right_leg_3),
            ),
        )
        else -> null
    }

/** Голова в окраске [lookId] — картинка награды цели; null — облик незнаком. */
internal fun lookArt(lookId: String): Int? = when (lookId) {
        "look_pushistik_1" -> R.drawable.look_pushistik_1_base_happy
        "look_pushistik_2" -> R.drawable.look_pushistik_2_base_happy
        "look_rogatik_1" -> R.drawable.look_rogatik_1_base_happy
        "look_rogatik_2" -> R.drawable.look_rogatik_2_base_happy
        "look_zvezdochka_1" -> R.drawable.look_zvezdochka_1_base_happy
        "look_zvezdochka_2" -> R.drawable.look_zvezdochka_2_base_happy
        "look_bantik_1" -> R.drawable.look_bantik_1_base_happy
        "look_bantik_2" -> R.drawable.look_bantik_2_base_happy
        "look_luchik_1" -> R.drawable.look_luchik_1_base_happy
        "look_luchik_2" -> R.drawable.look_luchik_2_base_happy
        else -> null
    }

private fun lookCharacterSkin(lookId: String): PetSkin? =
    ru.finney.pet.domain.model.PetLooks.character(lookId)?.skin
