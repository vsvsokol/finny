package ru.finney.pet.ui.shop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.finney.pet.ui.components.FinneyCard
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.SavingsIcon
import ru.finney.pet.ui.room.ItemPicture
import ru.finney.pet.ui.room.itemFallback
import ru.finney.pet.ui.tasks.games.Meter
import ru.finney.pet.ui.theme.FinneyInk

/**
 * «Копишь на шляпу: 40 из 90» — напоминание посреди трат, что часть монет уже обещана
 * мечте. Нажатие ведёт в копилку: класть и забирать можно только там.
 * Без [onOpen] плитка просто показывает, сколько накоплено.
 */
@Composable
internal fun SavingCard(tile: SavingTile, onOpen: (() -> Unit)?) {
    val goal = tile.goal
    FinneyCard(
        modifier = Modifier
            .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClickLabel = "Открыть копилку", onClick = onOpen) else Modifier)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val reward = goal.reward
            if (reward != null) ItemPicture(reward, itemFallback(reward), size = 48.dp)
            else FinneyIcon(SavingsIcon, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Название в именительном: «Копишь на Ковбойская шляпа» склонять нечем.
                Text("Копишь: ${goal.label}", style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
                Meter(tile.saved.toFloat() / goal.price.coerceAtLeast(1), Modifier.height(12.dp))
                Text("${tile.saved} из ${goal.price}", style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
            }
            if (onOpen != null) FinneyIcon(SavingsIcon, size = 28.dp)
        }
    }
}
