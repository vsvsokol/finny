package ru.finney.pet.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import kotlinx.browser.window
import ru.finney.pet.web.UrlFlags
import ru.finney.pet.web.WebBack
import ru.finney.pet.web.WebBuild
import ru.finney.pet.web.res.drawableNames

// Веб-реализация того, что экранам нужно от платформы (Android — app/.../ui/platform/Platform.kt).

/** Отладочные кнопки на вебе — по флагу ?debug=1 или в сборке разработчика. */
@Composable
fun rememberIsDebugBuild(): Boolean = UrlFlags.has("debug") || WebBuild.isDevelopment

@Composable
fun rememberAppVersion(): String = "Веб-версия ${WebBuild.version} (${WebBuild.commit})"

/** Браузер сам масштабирует картинку при рисовании, уменьшать заранее не нужно. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun rememberThumbBitmap(res: Int, maxSide: Int): ImageBitmap? = ImageBitmap.imageResource(res)

@Composable
fun rememberDrawableByName(name: String?): Int? = name?.let { drawableNames[it] }

@Composable
fun rememberRelaunch(): () -> Unit = { window.location.reload() }

@Composable
fun rememberShowMessage(): (String) -> Unit = { text -> window.alert(text) }

@Composable
fun rememberSystemBack(): () -> Unit = { WebBack.dispatch() }
