package ru.finney.pet.ui.motion

import androidx.compose.runtime.Composable

/** Частоту кадров вкладки задаёт браузер (requestAnimationFrame) — просить нечего. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SteadyFrameRate(hz: Float = 60f) = Unit
