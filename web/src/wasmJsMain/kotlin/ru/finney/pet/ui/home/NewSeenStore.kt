package ru.finney.pet.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ru.finney.pet.web.BrowserStorage

/** Наклейки «Новое» помнятся в localStorage (Android — SharedPreferences), см. NewStickers.kt. */
@Composable
internal fun rememberNewSeen(): NewSeen = remember {
    NewSeen(
        read = { key -> BrowserStorage.get("newStickers.$key")?.toIntOrNull() ?: 0 },
        write = { key, level -> BrowserStorage.set("newStickers.$key", level.toString()) },
    )
}
