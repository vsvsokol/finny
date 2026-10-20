package ru.finney.pet.ui.motion

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs

/**
 * Просит у экрана ровную частоту [hz], пока этот экран на виду.
 *
 * Комната — свет, тени и питомец — рисуется 10–13 мс на кадр. На экране 120 Гц
 * (Galaxy S23) бюджет 8.3 мс: кадр выходил то за один такт, то за два, и движение шло
 * рывками, хотя кадров в секунду было около семидесяти. На 60 Гц бюджет 16.6 мс,
 * каждый кадр успевает, и анимация ровная. Замер — `adb shell dumpsys gfxinfo … framestats`.
 *
 * Меняется только режим окна, частота выбирается из тех, что экран умеет, с тем же
 * разрешением. Нет подходящего — ничего не трогаем. Уходя с экрана, возвращаем как было.
 */
@Composable
fun SteadyFrameRate(hz: Float = 60f) {
    val view = LocalView.current
    DisposableEffect(view, hz) {
        val window = view.context.findActivity()?.window
        val display = view.display
        if (window == null || display == null) return@DisposableEffect onDispose {}
        val current = display.mode
        val mode = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .minByOrNull { abs(it.refreshRate - hz) }
            ?: return@DisposableEffect onDispose {}
        val before = window.attributes.preferredDisplayModeId
        window.attributes = window.attributes.apply { preferredDisplayModeId = mode.modeId }
        onDispose {
            window.attributes = window.attributes.apply { preferredDisplayModeId = before }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
