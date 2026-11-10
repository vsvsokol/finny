package ru.finney.pet.ui.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Наклейки «Новое» помнятся в отдельных SharedPreferences интерфейса (см. NewStickers.kt). */
@Composable
internal fun rememberNewSeen(): NewSeen {
    val app = LocalContext.current.applicationContext
    return remember {
        val prefs = app.getSharedPreferences("new_stickers", Context.MODE_PRIVATE)
        NewSeen(
            read = { key -> prefs.getInt(key, 0) },
            write = { key, level -> prefs.edit().putInt(key, level).apply() },
        )
    }
}
