package ru.finney.pet.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.finney.pet.R
import ru.finney.pet.domain.pet.Emotion
import ru.finney.pet.ui.motion.motionEnabled
import ru.finney.pet.ui.components.ActionFeedback
import ru.finney.pet.ui.components.ActionFeedbackSummary
import androidx.compose.foundation.border
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.*

/** Исходная основа и пиктограмма остаются отдельными слоями, кольцо показывает реальные данные. */
@Composable
internal fun DesignerHomeButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: String? = null,
    progress: Float? = null,
    progressColor: Color = FinneyPink,
    warning: Boolean = false,
    ready: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    diameter: Dp = 56.dp,
    @DrawableRes goalArt: Int? = null,
    ringModifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val animations = LocalAnimations.current
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f,
        animationSpec = if (animations) spring(dampingRatio = 0.65f) else snap(), label = "homeButtonPress")
    val fill by animateFloatAsState(progress?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = if (animations) tween(250) else snap(), label = "homeRing")
    val hop = remember { Animatable(0f) }
    var previousReady by remember { mutableStateOf(ready) }
    LaunchedEffect(ready, enabled, animations) {
        val completed = ready && !previousReady
        previousReady = ready
        hop.snapTo(0f)
        if (ready && enabled && animations && motionEnabled()) {
            if (completed) {
                delay(280) // Сначала замыкается кольцо, затем отмечаем готовность.
                hop.animateTo(-1.5f, tween(140))
                hop.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
            }
            while (true) {
                delay(7_000)
                hop.animateTo(-1f, tween(150))
                hop.animateTo(0f, tween(200))
            }
        }
    }
    val sounds = LocalSounds.current
    val ringSpace = if (progress != null) diameter * 0.12f else 0.dp
    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.45f
                scaleX = scale; scaleY = scale
                translationY = hop.value * 4.dp.toPx()
            }
            .clickable(enabled = enabled, interactionSource = source, indication = null, role = Role.Button) {
                sounds.play(Sfx.Tap)
                onClick()
            }
            .semantics {
                contentDescription = description
                state?.let { stateDescription = it }
                this.selected = selected
            }
            .padding(top = if (goalArt != null) 12.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(ringModifier.size(diameter + ringSpace * 2), contentAlignment = Alignment.Center) {
            if (progress != null) Canvas(Modifier.fillMaxSize()) {
                val track = diameter.toPx() * 0.05f
                val outline = diameter.toPx() * (10f / 155f)
                val radius = (diameter.toPx() + track) / 2f
                drawCircle(Color.White, radius, style = Stroke(track))
                drawArc(progressColor, -90f, 360f * fill, false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2), style = Stroke(track))
                drawCircle(FinneyInk, radius + track / 2 + outline / 2, style = Stroke(outline))
            }
            Image(painterResource(R.drawable.home_round_button_base), null, Modifier.size(diameter))
            // Размеры пиктограмм заданы относительно исходного круга 155 × 155.
            val iconSize = when (icon) {
                R.drawable.home_eat_icon -> 66 to 71
                R.drawable.home_wash_icon -> 76 to 73
                R.drawable.home_sleep_icon -> 67 to 71
                R.drawable.home_menu_icon -> 71 to 57
                R.drawable.home_game_icon -> 92 to 69
                else -> 90 to 81
            }
            Image(painterResource(icon), null,
                Modifier.size(diameter * (iconSize.first / 155f), diameter * (iconSize.second / 155f)))
            // Готовность «забрать» важнее предупреждения: место под значок одно, как в макете.
            if (ready) Image(
                painterResource(R.drawable.home_ready_icon), null,
                Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 1.dp)
                    .size(width = 26.dp, height = 22.dp),
            ) else if (warning) Image(
                painterResource(R.drawable.home_warning_icon), null,
                Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 1.dp)
                    .size(width = 14.dp, height = 31.dp),
            )
        }
        if (goalArt != null) Image(painterResource(goalArt), null,
            Modifier.align(Alignment.TopCenter).offset(y = (-14).dp).size(36.dp))
    }
}

/** Место под подпись сохраняется, чтобы появление текста не двигало круги. */
@Composable
internal fun HomeCareAction(
    @DrawableRes icon: Int,
    label: String,
    description: String,
    value: Int,
    critical: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonModifier: Modifier = Modifier,
) {
    val animations = LocalAnimations.current
    var taps by remember { mutableIntStateOf(0) }
    var showLabel by remember { mutableStateOf(false) }
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        showLabel = true
        delay(2_000)
        showLabel = false
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(showLabel,
                enter = if (animations) fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 4 } else EnterTransition.None,
                exit = if (animations) fadeOut(tween(220)) + slideOutVertically(tween(220)) { -it / 5 } else ExitTransition.None,
            ) {
                Text(label, color = FinneyInk, style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, textAlign = TextAlign.Center,
                    modifier = Modifier.background(FinneyCream, RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 1.dp))
            }
        }
        DesignerHomeButton(
            icon = icon, description = description,
            state = "$value из 100" + if (critical) ", критически низко" else "",
            progress = value / 100f, warning = critical, selected = selected, enabled = enabled,
            onClick = { taps++; onClick() },
            ringModifier = buttonModifier,
        )
    }
}

/** Спокойствие — мягкая улыбка: у самого питомца для CALM тоже используется счастливое лицо. */
@Composable
internal fun PetMoodHeart(
    emotion: Emotion, value: Int, onClick: () -> Unit, modifier: Modifier = Modifier,
    speaking: Boolean = false,
) {
    val sad = emotion.toMood() != ru.finney.pet.ui.pet.PetMood.HAPPY
    val happy = emotion == Emotion.HAPPY
    val animations = LocalAnimations.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.9f else 1f,
        animationSpec = if (animations) spring(dampingRatio = 0.65f) else snap(), label = "heartPress")
    val expression by animateFloatAsState(if (sad) -1f else if (happy) 1f else 0.45f,
        animationSpec = if (animations) tween(260) else snap(), label = "heartExpression")
    val beat = remember { Animatable(1f) }
    LaunchedEffect(emotion, speaking, animations) {
        beat.snapTo(1f)
        if (animations && motionEnabled()) {
            // Сердечко отзывается на речь и смену состояния; в покое не отвлекает от питомца.
            beat.animateTo(1.14f, tween(130))
            beat.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMedium))
        }
    }
    Canvas(modifier.size(48.dp).graphicsLayer {
        scaleX = press * beat.value; scaleY = press * beat.value
    }.clickable(interactionSource = source, indication = null, role = Role.Button,
        onClickLabel = "Что нужно питомцу", onClick = onClick)
        .semantics {
            contentDescription = "Настроение питомца"
            stateDescription = (if (sad) "Грустное" else if (happy) "Весёлое" else "Спокойное") + ". $value из 100"
        }) {
        val u = size.width / 48f
        val heart = Path().apply {
            moveTo(24*u, 42*u)
            cubicTo(19*u, 37*u, 5*u, 28*u, 5*u, 16*u)
            cubicTo(5*u, 5*u, 18*u, 3*u, 24*u, 12*u)
            cubicTo(30*u, 3*u, 43*u, 5*u, 43*u, 16*u)
            cubicTo(43*u, 28*u, 29*u, 37*u, 24*u, 42*u)
            close()
        }
        drawPath(heart, FinneyYellow)
        clipPath(heart) {
            drawOval(FinneyPeach, Offset(0f, 30*u), Size(48*u, 21*u))
            drawOval(Color.White, Offset(10*u, 10*u), Size(9*u, 5*u))
        }
        drawPath(heart, FinneyInk, style = Stroke(StrokeRegular.toPx()))
        drawCircle(FinneyInk, 1.6f*u, Offset(17*u, 22*u))
        drawCircle(FinneyInk, 1.6f*u, Offset(31*u, 22*u))
        val ends = 28f - expression * 1.5f
        val mouth = Path().apply {
            moveTo(18*u, ends*u)
            quadraticTo(24*u, (28f + expression * 7f)*u, 30*u, ends*u)
        }
        drawPath(mouth, FinneyInk, style = Stroke(StrokeThin.toPx(), cap = StrokeCap.Round))
    }
}

/** Хвост остаётся у головы: меняется прозрачность и масштаб, а не размер области размещения. */
@Composable
internal fun PetSpeechBubble(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, visible: Boolean = true,
    tailAbove: Boolean = false,
) {
    val animations = LocalAnimations.current
    Box(modifier) {
        AnimatedVisibility(visible,
            enter = if (animations) fadeIn(tween(180)) + scaleIn(
                spring(dampingRatio = 0.72f, stiffness = 550f), initialScale = 0.84f,
                transformOrigin = TransformOrigin(0.75f, if (tailAbove) 0f else 1f)) else EnterTransition.None,
            exit = if (animations) fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.94f,
                transformOrigin = TransformOrigin(0.75f, if (tailAbove) 0f else 1f)) else ExitTransition.None,
        ) {
            Box(Modifier.widthIn(max = 196.dp).clickable(enabled = visible, role = Role.Button,
                onClickLabel = "Помочь питомцу", onClick = onClick)) {
                Canvas(Modifier.matchParentSize().graphicsLayer {
                    scaleY = if (tailAbove) -1f else 1f
                }) {
                    val tail = 14.dp.toPx()
                    val path = Path().apply {
                        moveTo(size.width * 0.52f, size.height - tail - 4.dp.toPx())
                        quadraticTo(size.width * 0.58f, size.height, size.width * 0.80f, size.height)
                        quadraticTo(size.width * 0.70f, size.height - tail / 2, size.width * 0.72f, size.height - tail - 4.dp.toPx())
                        close()
                    }
                    drawPath(path, Color.White)
                    drawPath(path, FinneyInk, style = Stroke(StrokeThin.toPx()))
                    drawOval(FinneyInk, size = Size(size.width, size.height - tail))
                    val stroke = StrokeThin.toPx()
                    drawOval(Color.White, topLeft = Offset(stroke, stroke),
                        size = Size(size.width - stroke * 2, size.height - tail - stroke * 2))
                }
                AnimatedContent(text, transitionSpec = {
                    if (animations) fadeIn(tween(180, delayMillis = 80)) togetherWith fadeOut(tween(100))
                    else EnterTransition.None togetherWith ExitTransition.None
                }, label = "petSpeechText") { message ->
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = FinneyInk,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp,
                            top = if (tailAbove) 28.dp else 14.dp, bottom = if (tailAbove) 14.dp else 28.dp))
                }
            }
        }
    }
}

/** Держим последний результат до конца затухания, даже когда событие уже очищено. */
@Composable
internal fun HomeFeedbackToast(feedback: ActionFeedback?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val animations = LocalAnimations.current
    var retained by remember { mutableStateOf(feedback) }
    SideEffect { if (feedback != null) retained = feedback }
    val shown = feedback ?: retained
    Box(modifier) {
        AnimatedVisibility(feedback != null,
            enter = if (animations) fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 5 } else EnterTransition.None,
            exit = if (animations) fadeOut(tween(240)) + slideOutVertically(tween(240)) { -it / 8 } else ExitTransition.None,
        ) {
            if (shown != null) ActionFeedbackSummary(shown,
                Modifier.background(FinneyCream, RoundedCornerShape(RadiusField))
                    .border(StrokeThin, FinneyInk, RoundedCornerShape(RadiusField)).padding(12.dp)
                    .clickable(enabled = feedback != null, onClickLabel = "Скрыть", onClick = onDismiss))
        }
    }
}
