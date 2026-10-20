package ru.finney.pet.ui.home

import ru.finney.pet.ui.goals.rewardArt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.finney.pet.R
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.HomeMoneyBadge
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.finney.pet.ui.theme.*

internal enum class HomeActionLayout { RIGHT_COLUMN, SPLIT }

@Composable
internal fun BoxScope.HomeSideActions(
    state: HomeUiState.Ready, layout: HomeActionLayout, enabled: Boolean, alpha: Float,
    hintTargets: HintTargets, onOpenGoals: () -> Unit, onOpenGames: () -> Unit,
    onClaimGoal: () -> Unit,
    onSavingsBounds: (Rect) -> Unit, onGamesBounds: (Rect) -> Unit,
) {
    val goal = state.goal
    val savings: @Composable () -> Unit = {
        Column(Modifier.widthIn(max = 96.dp).onGloballyPositioned { onSavingsBounds(it.boundsInRoot()) },
            horizontalAlignment = Alignment.CenterHorizontally) {
            val ready = goal != null && goal.remaining <= 0
            DesignerHomeButton(R.drawable.home_pig_icon, "Копилка", onOpenGoals,
                state = if (goal != null) "${goal.goal.label}. Накоплено ${goal.saved} из ${goal.goal.price} монет" +
                    if (ready) ". Награда готова" else "" else "Накоплено ${state.totalSavings} монет. " +
                    if (state.reserveMode) "Режим запаса" else "Цель ещё не выбрана",
                progress = if (goal != null) goal.saved.toFloat() / goal.goal.price.coerceAtLeast(1) else 0f,
                // Прогресс копилки — жёлтый, как деньги: зелёный — только итог действия.
                progressColor = FinneyYellow, goalArt = goal?.goal?.rewardArt,
                // Без цели копить некуда — «!» зовёт её выбрать.
                warning = goal == null && !state.reserveMode,
                ready = ready, enabled = enabled,
                ringModifier = Modifier.hintTarget(hintTargets, NextStep.SAVE))
            HomeMoneyBadge(state.totalSavings, Modifier.padding(top = 3.dp).hintCover(hintTargets)
                .testTag("home-savings-money").semantics { contentDescription = "Накоплено ${state.totalSavings} монет" })
            if (goal == null && !state.reserveMode) ActionCaption("Выбрать цель", hintTargets)
            // Накопили — забрать не уходя в копилку: вещь сразу на питомце, это и есть отклик.
            if (ready) FinneyButton(
                text = "Забрать", onClick = onClaimGoal, enabled = enabled, fillWidth = false, compact = true,
                textStyle = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp).hintCover(hintTargets),
            )
        }
    }
    val games: @Composable () -> Unit = {
        if (state.levelGames.isNotEmpty() && state.levelGame != null) {
            val passed = state.levelGames.count { it.passed }
            Column(Modifier.widthIn(max = 96.dp)
                .padding(top = if (layout == HomeActionLayout.SPLIT && goal?.goal?.rewardArt != null) 12.dp else 0.dp)
                .onGloballyPositioned { onGamesBounds(it.boundsInRoot()) },
                horizontalAlignment = Alignment.CenterHorizontally) {
                DesignerHomeButton(R.drawable.home_game_icon, "Игры уровня", onOpenGames,
                    state = "Пройдено $passed из ${state.levelGames.size} игр уровня" +
                        if (passed == state.levelGames.size) ". Все игры пройдены" else "",
                    progress = passed.toFloat() / state.levelGames.size, ready = passed == state.levelGames.size,
                    enabled = enabled, ringModifier = Modifier.hintTarget(hintTargets, NextStep.LEVEL_GAME))
                ActionCaption(state.levelGame, hintTargets)
            }
        }
    }
    if (layout == HomeActionLayout.SPLIT) {
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 12.dp)
            .graphicsLayer { this.alpha = alpha }, horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top) {
            savings()
            games()
        }
    } else {
        Column(Modifier.align(Alignment.TopEnd).padding(top = 12.dp)
            .graphicsLayer { this.alpha = alpha }, horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            savings()
            games()
        }
    }
}

@Composable
private fun ActionCaption(text: String, hintTargets: HintTargets) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = FinneyInk,
        textAlign = TextAlign.Center, maxLines = 3,
        modifier = Modifier.padding(top = 3.dp).hintCover(hintTargets, corner = 8.dp).background(FinneyCream, RoundedCornerShape(8.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp))
}
