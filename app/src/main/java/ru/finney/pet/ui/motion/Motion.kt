package ru.finney.pet.ui.motion

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.currentCoroutineContext

/** Включены ли анимации — для украшений, которые идут своим циклом кадров или `delay`. */
val LocalAnimations = staticCompositionLocalOf { true }

/**
 * Идут ли анимации — для корутины эффекта: множитель берётся из её контекста, как его
 * берёт сам Compose, поэтому учтена и системная «скорость анимации».
 */
suspend fun motionEnabled(): Boolean =
    (currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
