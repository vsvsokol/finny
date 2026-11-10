package ru.finney.pet.ui.motion

import androidx.compose.ui.MotionDurationScale

/** Выключатель анимаций (ТЗ п. 3.6), как на Android, без системной скорости анимаций. */
class AppMotion : MotionDurationScale {
    var enabled: Boolean = true
    override val scaleFactor: Float get() = if (enabled) 1f else 0f
}
