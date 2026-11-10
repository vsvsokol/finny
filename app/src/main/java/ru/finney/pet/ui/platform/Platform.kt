package ru.finney.pet.ui.platform

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// То, что экранам нужно от платформы. Здесь — Android; у веб-сборки (web/) такой же
// файл со своей реализацией. Экраны зовут только эти функции и не знают про Context.

/**
 * Отладочная сборка — та, что ставится как ru.finney.pet.debug. Флаг читается
 * из манифеста, а не из BuildConfig: генерация BuildConfig в модуле выключена.
 */
@Composable
fun rememberIsDebugBuild(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
}

/**
 * «Версия 1.1.0 (10100)» из установленного пакета: номер задаёт version.properties
 * через сборку. BuildConfig в модуле выключен, поэтому читается из PackageManager.
 */
@Composable
fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "Версия ${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
    }
}

/** Картинка [res], уменьшенная при чтении до [maxSide] px по большей стороне. Читается в фоне. */
@Composable
fun rememberThumbBitmap(@DrawableRes res: Int, maxSide: Int): ImageBitmap? {
    val resources = LocalContext.current.resources
    val image by produceState<ImageBitmap?>(null, res) {
        value = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, res, bounds)
            while (maxOf(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2) >= maxSide) options.inSampleSize *= 2
            BitmapFactory.decodeResource(resources, res, options)?.asImageBitmap()
        }
    }
    return image
}

/** Картинка по имени из JSON (`"art": "item_apple"`); null — такой нет. */
@SuppressLint("DiscouragedApi") // Имя приходит из JSON — R.drawable.* здесь не подставить.
@Composable
fun rememberDrawableByName(name: String?): Int? {
    val context = LocalContext.current
    return remember(name) {
        name?.let { context.resources.getIdentifier(it, "drawable", context.packageName) }?.takeIf { it != 0 }
    }
}

/**
 * Запуск с нуля после стирания. Новая задача — новый стек и новые ViewModel,
 * поэтому стартовый экран считается заново и ведёт на первый запуск. Иначе
 * главный остался бы висеть без профиля.
 */
@Composable
fun rememberRelaunch(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
        }
    }
}

/** Короткое сообщение поверх экрана: Toast. */
@Composable
fun rememberShowMessage(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { text -> Toast.makeText(context, text, Toast.LENGTH_LONG).show() } }
}

/** «Назад» как системная кнопка: сработают BackHandler экранов, иначе — навигация. */
@Composable
fun rememberSystemBack(): () -> Unit {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    return remember(dispatcher) { { dispatcher?.onBackPressed() } }
}
