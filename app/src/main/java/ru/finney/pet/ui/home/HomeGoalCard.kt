package ru.finney.pet.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ru.finney.pet.domain.game.GoalProgress
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.pet.accessoryArt
import ru.finney.pet.ui.theme.*

/** На главном видна сама желанная вещь, а не только сумма без назначения. */
@Composable
internal fun HomeGoalCard(
    goal: GoalProgress?,
    reserveMode: Boolean,
    reserveSaved: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val ready = goal != null && goal.remaining <= 0
    val shape = RoundedCornerShape(RadiusField)
    Column(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(shape)
            .background(if (ready) FinneyYellow else FinneySand)
            .border(StrokeThin, FinneyInk, shape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Открыть копилку", onClick = onClick)
            .semantics {
                if (goal != null) stateDescription = "${goal.goal.label}. Накоплено ${goal.saved} из ${goal.goal.price} монет"
            }
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (goal != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Копилка", style = MaterialTheme.typography.bodyMedium, color = FinneyInk,
                    modifier = Modifier.weight(1f))
                val art = goal.goal.reward?.let(::accessoryArt)
                if (art != null) {
                    Image(painterResource(art.res), contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
            Text(if (ready) "Забрать награду" else "Ещё ${goal.remaining}",
                style = MaterialTheme.typography.labelMedium, color = FinneyInk)
        } else {
            Text(if (reserveMode) "Запас" else "Копилка", style = MaterialTheme.typography.bodyMedium, color = FinneyInk)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FinneyIcon(FinneyIcons.Piggy, size = 28.dp)
                Text(if (reserveMode) "$reserveSaved монет" else "Выбрать цель",
                    style = MaterialTheme.typography.labelMedium, color = FinneyInk)
            }
        }
    }
}
