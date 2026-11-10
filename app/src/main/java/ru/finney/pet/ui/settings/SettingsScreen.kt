package ru.finney.pet.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.FinneyPanel
import ru.finney.pet.ui.components.FinneyScreen
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.platform.rememberAppVersion

/**
 * Настройки из «бургера»: звук и музыка выключаются отдельно и без барьера (ТЗ п. 3.6),
 * раздел взрослого — отсюда же, барьер у него свой.
 *
 * Настройки звука живут в [LocalSounds], экран только переключает их. Демо-режим —
 * свойство профиля, его переключает [SettingsViewModel].
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAdult: () -> Unit,
    onOpenGlossary: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val sounds = LocalSounds.current
    val settings by sounds.settings.collectAsStateWithLifecycle()
    val demo by viewModel.isDemo.collectAsStateWithLifecycle()
    val version = rememberAppVersion()
    SettingsContent(
        version = version,
        sound = settings.sound,
        music = settings.music,
        onSound = sounds::setSound,
        onMusic = sounds::setMusic,
        animations = settings.animations,
        onAnimations = sounds::setAnimations,
        demo = demo,
        onDemo = viewModel::setDemo,
        onBack = onBack,
        onOpenAdult = onOpenAdult,
        onOpenGlossary = onOpenGlossary,
    )
}

@Composable
private fun SettingsContent(
    version: String,
    sound: Boolean,
    music: Boolean,
    onSound: (Boolean) -> Unit,
    onMusic: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenAdult: () -> Unit,
    onOpenGlossary: () -> Unit = {},
    animations: Boolean = true,
    onAnimations: (Boolean) -> Unit = {},
    demo: Boolean? = null,
    onDemo: (Boolean) -> Unit = {},
) {
    FinneyScreen(
        scrollable = true,
        onClose = onBack,
        bottom = {
            Text(
                text = version,
                style = MaterialTheme.typography.bodyMedium,
                color = FinneyInk.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        },
    ) {
        OutlinedText("Настройки", style = MaterialTheme.typography.headlineLarge)

        FinneyPanel(title = "Звук и анимации") {
            ToggleRow(if (sound) "Звуки: включены" else "Звуки: выключены", sound, onSound)
            ToggleRow(if (music) "Музыка: включена" else "Музыка: выключена", music, onMusic)
            ToggleRow(if (animations) "Анимации: включены" else "Анимации: выключены", animations, onAnimations)
        }

        // Демо — для экспертов: короткий сон и напоминания, быстрые уровни, все игры открыты.
        // Состояние — и положением, и словом, как у звука.
        demo?.let { on ->
            FinneyPanel(title = "Проверка") {
                ToggleRow(if (on) "Демо: включено" else "Демо: выключено", on, onDemo, icon = FinneyIcons.Trophy)
            }
        }

        // Плейтест 28.09: справочник искали в настройках — «где объясняют слова».
        // Из «Прогресса» он открывается по-прежнему, это второй вход.
        FinneyPanel(title = "Справочник") {
            Text(
                "Что значат слова из игры: бюджет, план, копилка и другие.",
                style = MaterialTheme.typography.bodyLarge,
                color = FinneyInk,
            )
            FinneyButton(text = "Открыть справочник", onClick = onOpenGlossary)
        }

        FinneyPanel(title = "Для взрослых") {
            Text(
                "Прогресс ребёнка, бонус от родителей, сброс и удаление профиля. Вход — через пример.",
                style = MaterialTheme.typography.bodyLarge,
                color = FinneyInk,
            )
            FinneyButton(text = "Открыть раздел", onClick = onOpenAdult)
        }
    }
}

/** Строка с переключателем. Нажимается целиком; состояние — и положением, и словом (ТЗ п. 3.6). */
@Composable
private fun ToggleRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit, icon: FinneyIcons? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon?.let { FinneyIcon(it, size = 28.dp) }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = FinneyInk,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            // Нажатие ловит вся строка: иначе TalkBack читал бы переключатель дважды.
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = FinneyCream,
                checkedTrackColor = FinneyPeach,
                checkedBorderColor = FinneyInk,
                uncheckedThumbColor = FinneyInk,
                uncheckedTrackColor = FinneyYellow,
                uncheckedBorderColor = FinneyInk,
            ),
        )
    }
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun SettingsPreview() {
    FinneyTheme {
        SettingsContent(version = "Версия 1.1.0 (10100)", sound = true, music = false, onSound = {}, onMusic = {}, onBack = {}, onOpenAdult = {}, demo = false)
    }
}
