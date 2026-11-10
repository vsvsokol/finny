package ru.finney.pet.web

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.finney.pet.domain.game.ActiveProfileStorage
import ru.finney.pet.domain.settings.SettingsStorage
import ru.finney.pet.domain.settings.SoundSettings

/** Открытый профиль — как DataStore на Android, только в localStorage. */
class WebActiveProfileStorage : ActiveProfileStorage {
    private val state = MutableStateFlow(BrowserStorage.get(KEY)?.toLongOrNull())
    override val activeProfileId: Flow<Long?> = state

    override suspend fun setActiveProfileId(profileId: Long?) {
        if (profileId == null) BrowserStorage.remove(KEY) else BrowserStorage.set(KEY, profileId.toString())
        state.value = profileId
    }

    private companion object {
        const val KEY = "activeProfile"
    }
}

/** Звук, музыка, вибрация, анимации. По умолчанию включено всё, как на Android. */
class WebSettingsStorage : SettingsStorage {
    private val state = MutableStateFlow(read())
    override val sound: Flow<SoundSettings> = state

    private fun read(): SoundSettings {
        fun flag(name: String) = BrowserStorage.get("settings.$name")?.toBooleanStrictOrNull() ?: true
        return SoundSettings(flag("sound"), flag("music"), flag("haptics"), flag("animations"))
    }

    private fun put(name: String, value: Boolean, change: (SoundSettings) -> SoundSettings) {
        BrowserStorage.set("settings.$name", value.toString())
        state.value = change(state.value)
    }

    override suspend fun setSound(enabled: Boolean) = put("sound", enabled) { it.copy(sound = enabled) }
    override suspend fun setMusic(enabled: Boolean) = put("music", enabled) { it.copy(music = enabled) }
    override suspend fun setHaptics(enabled: Boolean) = put("haptics", enabled) { it.copy(haptics = enabled) }
    override suspend fun setAnimations(enabled: Boolean) = put("animations", enabled) { it.copy(animations = enabled) }
}
