package ru.finney.pet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.ui.pet.PetMood
import ru.finney.pet.ui.pet.PetPose
import ru.finney.pet.ui.pet.PetView
import ru.finney.pet.ui.theme.FinneyInk

// Финни говорит сам: голова с тенью-силуэтом и белый пузырь с хвостиком к ней — как
// реплика с ролью на карточке игры (ui/tasks/GameCard.kt). Одна манера на итог, план
// и подсказки: ребёнок узнаёт, что это говорит питомец, а не «система».

/** Выделение в реплике — тёмный коралловый, как «Ты — …» на карточке игры. */
val SpeechLeadColor = Color(0xFFE8694A)

/** Реплика с выделенным началом: «**Желаемое** — что меня радует». */
fun speech(lead: String?, rest: String): AnnotatedString = buildAnnotatedString {
    if (lead != null) withStyle(SpanStyle(color = SpeechLeadColor)) { append(lead) }
    append(rest)
}

/** Голова Финни с тенью и реплика справа. */
@Composable
fun FinniSays(
    character: PetCharacter?,
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    coloring: String? = null,
    accessories: List<String> = emptyList(),
    headSize: Dp = 72.dp,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (character != null) FinniHead(character, coloring, accessories, Modifier.size(headSize))
        SpeechBubble(text, Tail.LEFT, Modifier.weight(1f))
    }
}

/** Голова Финни без туловища, с тенью-силуэтом, как в мини-играх. */
@Composable
fun FinniHead(character: PetCharacter, coloring: String?, accessories: List<String>, modifier: Modifier = Modifier) {
    PetView(
        character = character,
        mood = PetMood.HAPPY,
        pose = { PetPose() },
        coloring = coloring,
        accessories = accessories,
        headOnly = true,
        modifier = modifier.silhouetteShadow(),
    )
}

/** Тень по силуэту — то же содержимое, залитое полупрозрачным ink и сдвинутое вниз на 4 dp. */
@Composable
fun Modifier.silhouetteShadow(): Modifier {
    val body = rememberGraphicsLayer()
    val shadow = rememberGraphicsLayer()
    return drawWithContent {
        body.record { this@drawWithContent.drawContent() }
        shadow.colorFilter = ColorFilter.tint(FinneyInk.copy(alpha = 0.15f), BlendMode.SrcIn)
        shadow.record { drawLayer(body) }
        translate(top = 4.dp.toPx()) { drawLayer(shadow) }
        drawLayer(body)
    }
}
