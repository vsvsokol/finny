package ru.finney.pet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.ui.pet.PetMood
import ru.finney.pet.ui.pet.PetPose
import ru.finney.pet.ui.pet.PetView
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.StrokeRegular

// Финни говорит сам: голова с тенью-силуэтом и белый пузырь с хвостиком к ней — как
// реплика с ролью на карточке игры (ui/tasks/GameCard.kt). Одна манера на итог, план
// и подсказки: ребёнок узнаёт, что это говорит питомец, а не «система».

/** Выделение в реплике — тёмный коралловый, как «Ты — …» на карточке игры. */
val SpeechLeadColor = Color(0xFFE8694A)

/** Тень пузыря по низу — светло-сиреневая, как у карточки игры. */
private val BubbleShade = Color(0xFFF1ECFF)

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
        SpeechBubble(text, Modifier.weight(1f))
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

/** Белый пузырь с хвостиком слева посередине и сиреневой тенью по низу. */
@Composable
fun SpeechBubble(text: AnnotatedString, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = FinneyInk,
        modifier = modifier
            .clip(LeftTailBubble)
            .background(Color.White)
            .bubbleShade()
            .border(StrokeRegular, FinneyInk, LeftTailBubble)
            .padding(start = 30.dp, end = 14.dp, top = 9.dp, bottom = 13.dp),
    )
}

/** Тень по низу внутри пузыря: форма минус та же форма, поднятая на 6 dp. */
private fun Modifier.bubbleShade(): Modifier = drawWithContent {
    val outline = LeftTailBubble.createOutline(size, layoutDirection, this)
    val shape = Path().apply { addOutline(outline) }
    val lifted = Path().apply { addPath(shape, androidx.compose.ui.geometry.Offset(0f, -(6.dp + StrokeRegular).toPx())) }
    drawPath(Path().apply { op(shape, lifted, PathOperation.Difference) }, BubbleShade)
    drawContent()
}

/** Пузырь с хвостиком слева посередине: тело и хвостик — одна фигура, рамка без шва. */
private object LeftTailBubble : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { 22.dp.toPx() }
        val t = with(density) { 16.dp.toPx() }
        val h = with(density) { 12.5.dp.toPx() }
        val mid = size.height / 2
        val body = Path().apply { addRoundRect(RoundRect(t, 0f, size.width, size.height, CornerRadius(r))) }
        val tip = Path().apply { moveTo(t + r / 2, mid - h); lineTo(0f, mid); lineTo(t + r / 2, mid + h); close() }
        return Outline.Generic(Path().apply { op(body, tip, PathOperation.Union) })
    }
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
