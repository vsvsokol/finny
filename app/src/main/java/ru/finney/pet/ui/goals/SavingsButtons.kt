package ru.finney.pet.ui.goals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.theme.FinneyInk

/** Пополнение — главное действие; снятие отдельно и подтверждается экраном. */
@Composable
internal fun SavingsSwitch(
    amount: Int,
    onTake: () -> Unit,
    onPut: () -> Unit,
    canTake: Boolean,
    canPut: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FinneyButton("Положить $amount", onClick = onPut, enabled = canPut, calm = true)
        TextButton(onClick = onTake, enabled = canTake, modifier = Modifier.fillMaxWidth()) {
            Text("Взять $amount из копилки", style = MaterialTheme.typography.labelMedium,
                color = FinneyInk.copy(alpha = if (canTake) 1f else 0.45f))
        }
    }
}
