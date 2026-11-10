package ru.finney.pet.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Какой экран у игры. Телефон Android — всегда [COMPACT]: приложение заперто в портрете,
 * и всё ниже для него ничего не меняет. Остальные режимы задаёт веб-версия (web/, main.kt).
 *
 * - [COMPACT] — телефон: игра во весь экран, как на Android.
 * - [MEDIUM] — планшет или узкое окно: портретная игра по центру.
 * - [EXPANDED] — компьютер: комната по центру, кнопки по краям окна, экраны
 *   (план, магазин, копилка…) выезжают панелью справа, а комната остаётся видна.
 */
enum class WindowLayout { COMPACT, MEDIUM, EXPANDED }

val LocalWindowLayout = staticCompositionLocalOf { WindowLayout.COMPACT }

/** Широкий режим: комната-сцена и панель справа. */
val isExpandedLayout: Boolean
    @Composable @ReadOnlyComposable get() = LocalWindowLayout.current == WindowLayout.EXPANDED

/** Ширина панели экрана справа в широком режиме — как у телефона, под неё экраны и нарисованы. */
val PaneWidth: Dp = 440.dp

/** Предел ширины нижних панелей главного (уход, уровень, игры) — чтобы не растягивались на всё окно. */
val WidePanelMax: Dp = 560.dp

/** Ширина колонки портретной игры в режиме [WindowLayout.MEDIUM] и у полноэкранных экранов в широком. */
val PortraitColumnWidth: Dp = 480.dp

/**
 * Режим по размеру окна. Широкий — только когда рядом с портретной комнатой (3:5 по высоте)
 * помещаются и панель справа, и кнопки по краям.
 */
fun windowLayoutFor(width: Dp, height: Dp): WindowLayout = when {
    width < 600.dp -> WindowLayout.COMPACT
    width >= 1000.dp && width >= height * 1.25f -> WindowLayout.EXPANDED
    else -> WindowLayout.MEDIUM
}

/** Ограничить ширину в широком режиме; на телефоне — без изменений. */
@Composable
fun Modifier.capWidthWhenExpanded(max: Dp = WidePanelMax): Modifier =
    if (isExpandedLayout) this.widthIn(max = max) else this

/**
 * Ширина колонки, в которой стоит сцена мини-игры, когда окно шире неё (широкий режим).
 * Задаёт WideShell: игра остаётся портретной колонкой, а фон сцены — во всё окно.
 * [Dp.Unspecified] — сцена во весь экран, как на телефоне.
 */
val LocalSceneColumn = staticCompositionLocalOf { Dp.Unspecified }

/**
 * Сколько фона сцены видно с каждой стороны за колонкой игры. Читают фоны мини-игр:
 * рисуют себя в масштабе колонки и продолжают узор до краёв окна. 0 — на телефоне.
 */
val LocalSceneBleed = staticCompositionLocalOf { 0.dp }
