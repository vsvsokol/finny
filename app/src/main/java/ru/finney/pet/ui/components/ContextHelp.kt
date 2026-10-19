package ru.finney.pet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.finney.pet.ui.theme.FinneyInk

/** Пояснение по запросу: основной экран оставляет место для действия. */
@Composable
fun ContextHelp(label: String, title: String, text: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = modifier) {
        FinneyIcon(FinneyIcons.Help, size = 20.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = FinneyInk)
    }
    if (open) ContextHelpDialog(title, text, onDismiss = { open = false })
}

/** Короткий вход в помощь у конкретного действия, без подписи в тесном заголовке. */
@Composable
fun ContextHelpIcon(title: String, text: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    FinneyIconButton(
        onClick = { open = true },
        contentDescription = "Подсказка: $title",
        modifier = modifier,
        size = 48.dp,
    ) {
        FinneyIcon(FinneyIcons.Help, size = 24.dp)
    }
    if (open) ContextHelpDialog(title, text, onDismiss = { open = false })
}

@Composable
private fun ContextHelpDialog(title: String, text: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            FinneyPanel(title = title, onClose = onDismiss) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text, style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
                }
            }
        }
    }
}
