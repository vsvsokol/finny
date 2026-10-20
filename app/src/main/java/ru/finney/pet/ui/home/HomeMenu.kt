package ru.finney.pet.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.finney.pet.ui.components.CloseButton
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.motion.motionEnabled
import ru.finney.pet.ui.progress.GlossaryBook
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.StrokeBold
import ru.finney.pet.ui.theme.StrokeThin
import ru.finney.pet.ui.theme.strokeFor

// Меню «бургера» — кружки в кремовой трубе, которая выезжает вниз из самого бургера
// (концепт «Г», 20.10). Не окно на весь экран: главный остаётся виден под лёгким
// затемнением. Труба стоит ровно по оси бургера, поэтому от края экрана до неё
// столько же, сколько до бургера, и на ширине 360 dp ничего не съезжает.
//
// Труба раскрывается ростом высоты, а не растяжением: обводка и скругления
// остаются ровными на каждом кадре. Кружок появляется, когда до него дошла труба,
// подпись выезжает следом — без этой очерёдности столбик «сыпался» разом.

/** Затемнение под меню: экран должен оставаться виден. */
private const val DimAlpha = 0.3f

private val Circle = 48.dp
private val Gap = 12.dp

private class MenuEntry(val label: String, val icon: @Composable (Dp) -> Unit, val onClick: () -> Unit)

/**
 * Порядок — от игры к служебному: планировать, украшать, потом подсказка,
 * справочник и настройки. Прогресс и итоги — в панели уровня, игра уровня —
 * в плашке над комнатой: дублей в меню нет.
 *
 * [burger] — где бургер, в координатах корня: от него строится вся геометрия.
 */
@Composable
internal fun HomeMenu(
    visible: Boolean,
    burger: Rect,
    onDismiss: () -> Unit,
    onOpenBudget: () -> Unit,
    onDecorate: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenGlossary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    val animations = LocalAnimations.current
    AnimatedVisibility(
        visible = visible && burger != Rect.Zero,
        enter = fadeIn(tween(if (animations) 180 else 0)),
        // Закрывается к бургеру: гаснет и чуть сжимается к правому верхнему углу.
        exit = fadeOut(tween(if (animations) 160 else 0)) +
            scaleOut(tween(if (animations) 160 else 0), targetScale = 0.94f, transformOrigin = TransformOrigin(0.9f, 0.1f)),
    ) {
        val entries = listOf(
            MenuEntry("План расходов", { FinneyIcon(FinneyIcons.Plan, size = it) }, onOpenBudget),
            // Раньше «Гардероб», но в панели не только одежда: окрас и комната тоже.
            // Карта уровней — и здесь, и мини-дорожкой в панели уровня: кнопку «Итоги и история» не находили.
            MenuEntry("Мой путь", { FinneyIcon(FinneyIcons.Trophy, size = it) }, onOpenProgress),
            MenuEntry("Украсить", { FinneyIcon(FinneyIcons.Brush, size = it) }, onDecorate),
            // Знакомство в режиме подсказки: в конце «Понятно» и назад, без «Создать питомца».
            MenuEntry("Как играть", { FinneyIcon(FinneyIcons.Help, size = it) }, onOpenHelp),
            // Справочник раньше жил только в «Прогрессе» и настройках — его не находили
            // (плейтест 29.09). Здесь он рядом с «Как играть»: оба — «что это значит».
            MenuEntry("Справочник", { GlossaryBook(size = it) }, onOpenGlossary),
            MenuEntry("Настройки", { FinneyIcon(FinneyIcons.Gear, size = it) }, onOpenSettings),
        )

        val tube = remember { Animatable(if (animations) 0f else 1f) }
        val items = remember { List(entries.size) { Animatable(if (animations) 0f else 1f) } }
        LaunchedEffect(Unit) {
            if (!animations || !motionEnabled()) {
                tube.snapTo(1f)
                items.forEach { it.snapTo(1f) }
                return@LaunchedEffect
            }
            launch { tube.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
            items.forEachIndexed { i, item ->
                launch {
                    delay(80L + 40L * i)
                    item.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
                }
            }
        }

        // Оверлей может стоять не в начале корня: геометрию бургера переводим в его координаты.
        var origin by remember { mutableStateOf(Offset.Zero) }
        var columnHeight by remember { mutableIntStateOf(0) }
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
            val density = LocalDensity.current
            val b = burger.translate(-origin)
            val width = constraints.maxWidth

            Box(
                Modifier
                    .fillMaxSize()
                    .background(FinneyInk.copy(alpha = DimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Закрыть",
                        onClick = onDismiss,
                    ),
            )

            with(density) {
                // Труба ровно в бургер шириной: шире — и на 360 dp она прижималась к краю
                // экрана ближе бургера (замер на телефоне 20.10: 5 dp против 9).
                val tubeWidth = b.width.toDp()
                val tubeTop = b.top
                val itemsTop = b.center.y + Circle.toPx() / 2f + Gap.toPx()
                // Высота трубы — по настоящей высоте столбика: при крупном шрифте строки выше.
                val fullHeight = (itemsTop + columnHeight + (tubeWidth - Circle).toPx() / 2f - tubeTop).toDp()

                Box(
                    Modifier
                        .offset { IntOffset((b.center.x - tubeWidth.toPx() / 2f).toInt(), tubeTop.toInt()) }
                        .width(tubeWidth)
                        .heightIn(min = lerp(tubeWidth, fullHeight.coerceAtLeast(tubeWidth), tube.value))
                        .background(FinneyCream, RoundedCornerShape(percent = 50))
                        .border(StrokeBold, FinneyInk, RoundedCornerShape(percent = 50))
                        // Тап по трубе мимо кружков не закрывает меню.
                        .pointerInput(Unit) { detectTapGestures { } },
                )

                // «✕» встаёт на место бургера, в оголовок трубы, — того же размера, что кружки.
                CloseButton(
                    onClick = onDismiss,
                    modifier = Modifier.offset {
                        IntOffset((b.center.x - Circle.toPx() / 2f).toInt(), (b.center.y - Circle.toPx() / 2f).toInt())
                    },
                    description = "Закрыть меню",
                    size = Circle,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = itemsTop.toDp(),
                            end = (width - b.center.x - Circle.toPx() / 2f).coerceAtLeast(0f).toDp(),
                        )
                        .onSizeChanged { columnHeight = it.height },
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Gap),
                ) {
                    entries.forEachIndexed { i, entry ->
                        MenuRow(entry, progress = items[i].value, onDismiss = onDismiss)
                    }
                }
            }
        }
    }
}

/**
 * Строка меню: подпись плашкой слева, кружок со значком — по оси трубы.
 * Нажимается вся строка: одного кружка ребёнку мало, подпись тоже кнопка.
 * [progress] — пружина появления; она перелетает за 1, отсюда отскок.
 */
@Composable
private fun MenuRow(entry: MenuEntry, progress: Float, onDismiss: () -> Unit) {
    val sounds = LocalSounds.current
    val travel = with(LocalDensity.current) { 14.dp.toPx() }
    Row(
        modifier = Modifier
            .heightIn(min = Circle)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button) {
                sounds.play(Sfx.Tap)
                // Сначала закрыть: иначе после возврата с экрана меню осталось бы раскрытым.
                onDismiss()
                entry.onClick()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.label,
            style = MaterialTheme.typography.bodyLarge,
            color = FinneyInk,
            modifier = Modifier
                .graphicsLayer {
                    alpha = ((progress - 0.3f) / 0.7f).coerceIn(0f, 1f)
                    translationX = (1f - progress.coerceAtMost(1f)) * travel
                }
                .widthIn(max = 220.dp)
                .background(FinneyCream, RoundedCornerShape(percent = 50))
                .border(StrokeThin, FinneyInk, RoundedCornerShape(percent = 50))
                .padding(horizontal = 12.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .graphicsLayer {
                    alpha = progress.coerceIn(0f, 1f)
                    val scale = 0.7f + 0.3f * progress
                    scaleX = scale
                    scaleY = scale
                    translationY = -(1f - progress) * travel
                }
                .size(Circle)
                .background(FinneyYellow, CircleShape)
                .border(strokeFor(Circle), FinneyInk, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            entry.icon(28.dp)
        }
    }
}
