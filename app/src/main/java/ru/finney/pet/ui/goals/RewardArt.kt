package ru.finney.pet.ui.goals

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.finney.pet.domain.model.PetAppearance
import ru.finney.pet.domain.model.RewardKind
import ru.finney.pet.ui.pet.PetMood
import ru.finney.pet.ui.pet.PetView
import ru.finney.pet.ui.pet.PetPose
import ru.finney.pet.R
import ru.finney.pet.domain.model.Goal
import ru.finney.pet.ui.pet.accessoryArt

/**
 * Рисунок награды цели: вещь — её картинка, тема комнаты — маленькая комната этой темы:
 * стена, окно и торшер в рамке (`tools/make_room_previews.py`). Один вид за окном,
 * как было сначала, — слой без рамы и стены, по нему комнату было не узнать.
 * id вещей и тем не пересекаются, поэтому вид награды знать не нужно — хватает id,
 * как в итоге цели ([ru.finney.pet.ui.components.ActionFeedback.itemId]).
 * null — рисунка нет (облики ждут рисунков), место под картинку не занимаем.
 */
@DrawableRes
fun rewardArt(rewardId: String): Int? = accessoryArt(rewardId)?.res ?: when (rewardId) {
    "circus" -> R.drawable.room_circus_preview
    "honey" -> R.drawable.room_honey_preview
    "sea" -> R.drawable.room_sea_preview
    "forest" -> R.drawable.room_forest_preview
    "town" -> R.drawable.room_town_preview
    else -> null
}

/** Рисунок награды цели [Goal.reward]; null — цели без награды или без рисунка. */
val Goal.rewardArt: Int? @DrawableRes get() = reward?.let(::rewardArt)

/**
 * Примерка награды (план 2.7): вещь — сразу на Финни, тема — комнатой целиком. Ребёнок
 * копит на то, что видит на своём питомце, а не на картинку шляпы в углу карточки.
 * Без облика или рисунка — обычная картинка награды; нет и её — ничего.
 */
@Composable
fun RewardPreview(goal: Goal, appearance: PetAppearance?, stage: Int, size: Dp = 112.dp, modifier: Modifier = Modifier) {
    val reward = goal.reward ?: return
    val art = goal.rewardArt
    when {
        goal.rewardKind == RewardKind.ITEM && appearance != null && accessoryArt(reward) != null ->
            Box(modifier.size(size), contentAlignment = Alignment.Center) {
                PetView(
                    character = appearance.character,
                    mood = PetMood.HAPPY,
                    pose = StillPose,
                    modifier = Modifier.size(size),
                    bodyColor = appearance.bodyColor,
                    accessories = listOf(reward),
                    stage = stage,
                )
            }
        art != null -> Image(painterResource(art), contentDescription = null, modifier = modifier.size(size))
    }
}

private val StillPose: () -> PetPose = { PetPose() }
