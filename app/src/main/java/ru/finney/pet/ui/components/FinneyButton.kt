package ru.finney.pet.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.strokeFor
import ru.finney.pet.ui.theme.FinneyBlue
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.theme.FinneyGlare
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyStrokeRatio
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyPink
import ru.finney.pet.ui.theme.FinneySand
import ru.finney.pet.ui.theme.StrokeThin
import ru.finney.pet.ui.theme.RadiusField
import ru.finney.pet.ui.theme.StrokeRegular
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.theme.FinneyYellow

// Кнопки из UI-кита. Общее у всех: синяя обводка снаружи, плоская заливка,
// полоса потемнее по низу и блик-эллипс слева сверху. Нажатие — не затемнение,
// как в Material, а смена пары цветов с жёлтой на персиковую: именно так
// нарисованы «нормальное» и «нажатое» состояния в макете.
//
// Все числа ниже промерены по эталону design/reference/Figma export finney/Frame 3.png
// (кнопка 420 × 120) — не на глаз и не из ранних заметок. Силуэт со скруглением
// ровно в половину высоты («стадион»), обводка 10 (= 1/12 высоты), нижняя полоса
// начинается на y = 90 из 120, то есть ровно нижняя четверть. Прямая граница
// у полосы только посередине: к краям она загибается вверх вдоль скругления.

/** Высота кнопки. Больше минимальных 48 dp из ТЗ п. 3.6 — по кнопке должен попадать ребёнок. */
private val ButtonHeight = 64.dp

/**
 * Доля высоты под нижнюю полосу. В эталоне полоса занимает y 90..119 из 120.
 * Это не тень-подложка и не градиент: плоский цвет, обрезанный общей формой кнопки.
 */
private const val BottomBandFraction = 0.25f

// Верхний цвет кнопки — не прямоугольник над полосой, а своя фигура: у краёв
// её низ скруглён, и полоса поднимается по бокам до середины высоты, как у
// капсулы, которую видно чуть снизу. Прямая полоса по всей ширине давала
// плоскую плашку вместо объёма.
//
// Промерено по столбцам Frame 3.png: низ жёлтого на x = 20, 30, 40, 60 от
// края — y = 74, 81, 86, 89. Это эллипс с центром в (60, 50) и полуосями 50 × 40:
// по горизонтали он повторяет скругление заливки (центр на x = 60, радиус 50),
// по вертикали доходит ровно до границы полосы на y = 90.

/** Полуось скругления низа по горизонтали — доля высоты кнопки: 50 из 120. */
private const val FaceCornerXFraction = 0.5f - FinneyStrokeRatio

/** Полуось скругления низа по вертикали — доля высоты кнопки: 40 из 120. */
private const val FaceCornerYFraction = BottomBandFraction + FinneyStrokeRatio

// У круглой кнопки фигура та же, только низ жёлтого ниже. Промерено по кружку
// кита (диаметр 155): низ жёлтого по столбцам в 0, 20, 40, 50, 60 от центра —
// 124.5, 122, 113.5, 106.5, 96.5 от верха. Это половина эллипса во всю ширину
// заливки с вертикальной полуосью 51.7, то есть 0.334 диаметра — то же
// скругление, что у «стадиона» (FaceCornerYFraction = 1/3). Прежний «серп»
// рисовал персиковый овал, выгнутый вверх куполом, — ровно наоборот эталону,
// где граница прогибается вниз, а персиковое поднимается по краям.

/** Где кончается жёлтое у круглой кнопки — доля диаметра: 124.5 из 155. */
private const val RoundFaceBottomFraction = 0.803f

// Блик — наклонённый эллипс, а не круг: в эталоне он лежит вдоль скругления угла.
// Всё в долях высоты кнопки. Размеры получены как bbox чисто белых пикселей
// в Frame 3.png: 34 × 24 при высоте 120 с отступом 29 / 24 от левого верхнего угла.
//
// Раньше здесь стояли 0.31 × 0.14 при 0.38 / 0.25, а в шапке файла — третий
// вариант. Числа ниже — замер, остальные версии удалены, чтобы не расходились снова.

/** Ширина блика — доля высоты кнопки. */
private const val GlareWidthFraction = 0.283f

/** Высота блика — доля высоты кнопки. */
private const val GlareHeightFraction = 0.200f

/** Отступ центра блика от левого края — доля высоты кнопки. */
private const val GlareCentreFraction = 0.242f + GlareWidthFraction / 2f

/** Отступ центра блика от верха — доля высоты кнопки. */
private const val GlareTopFraction = 0.200f + GlareHeightFraction / 2f

/**
 * Наклон блика. Отрицательный — против часовой, вдоль скругления угла.
 * Отдельным числом в макете не задан, подобран на глаз по эталону.
 */
private const val GlareAngle = -29.5f

// У круглой кнопки блик свой, заметно мельче. Промерено по кадру кита на кнопке
// с вилкой (диаметр 154): пятно чисто белых пикселей 22 × 25 с центром
// в 29.5 / 40 от левого верхнего угла. Числа от плоской кнопки давали блик
// вдвое шире — он забирал верх кружка и оставлял значку меньше места,
// чем в эталоне.

/** Ширина блика круглой кнопки — доля диаметра. */
private const val RoundGlareWidthFraction = 0.143f

/** Высота блика круглой кнопки — доля диаметра. */
private const val RoundGlareHeightFraction = 0.162f

/** Отступ центра блика круглой кнопки от левого края — доля диаметра. */
private const val RoundGlareCentreFraction = 0.192f

/** Отступ центра блика круглой кнопки от верха — доля диаметра. */
private const val RoundGlareTopFraction = 0.260f

/**
 * Сжатие под пальцем. Нажатие в игре должно отзываться телом, а не только цветом:
 * ребёнок 7–11 лет должен видеть, что игра его услышала.
 */
internal const val PressedScale = 0.96f

/** Надписи кнопок, которые уводят назад: у них свой, более низкий щелчок. */
private val BackLabels = setOf("Назад", "Закрыть", "Отмена", "Не сейчас", "Понятно", "Вернуться к полкам", "Ещё поиграю")

/**
 * Основная кнопка: «Играть», «Закрыть», «Подтвердить план».
 *
 * [fillWidth] — растянуть по ширине родителя; иначе кнопка по размеру надписи.
 * [sound] — щелчок при нажатии; у «Назад» и «Закрыть» по умолчанию свой.
 * [icon] — значок перед надписью: копилка на «В копилку». Надпись всё равно
 * обязательна — значок подсказывает, а смысл несут слова.
 * [sound] null и свой [interactionSource] — для [ChargeButton]: звук и отклик на
 * палец у неё свои.
 * [calm] — зелёная с голубой полосой вместо жёлтой: второстепенные пути в панели
 * уровня («Итоги и история», «Ещё поиграю»), как в макете экрана условий уровня.
 * [textStyle] — кегль надписи: в панели уровня кнопки крупнее обычных, как в макете.
 */
@Composable
fun FinneyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
    sound: Sfx? = if (text in BackLabels) Sfx.Back else Sfx.Tap,
    icon: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    calm: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
) {
    val sounds = LocalSounds.current
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(percent = 50)
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press",
    )

    Surface(
        onClick = {
            sound?.let(sounds::play)
            onClick()
        },
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .defaultMinSize(minHeight = ButtonHeight)
            // Масштаб читается внутри graphicsLayer, то есть на отрисовке:
            // пружина не пересобирает кнопку на каждом кадре.
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled,
        shape = shape,
        color = Color.Transparent,
        interactionSource = interactionSource,
    ) {
        Box(
            modifier = Modifier
                // Погашенная — бледная: раньше она ничем не отличалась от рабочей,
                // и на плейтесте «Подтвердить план» жали, не понимая, что не так.
                // Почему погашена, экран пишет рядом словами — цвет не единственный признак.
                .alpha(if (enabled) 1f else DisabledAlpha)
                .clip(shape)
                .buttonFill(pressed, round = false, calm = calm)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icon?.invoke()
                OutlinedText(text, style = textStyle, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Непрозрачность погашенной кнопки. */
private const val DisabledAlpha = 0.45f

/** Высота второстепенной кнопки: ниже основной, но с запасом над 48 dp из ТЗ п. 3.6. */
private val QuietButtonHeight = 56.dp

/**
 * Второстепенная кнопка — рядом с основной, когда действие есть, но звать к нему
 * не надо: «Взять» в копилке рядом с «В копилку».
 *
 * В ките такой нет — это наше решение, собранное по его правилам: «стадион»,
 * обводка ink снаружи, заливка плоская. Спокойнее основной всем сразу: песочная
 * заливка без нижней полосы и блика, обводка тоньше, надпись без контура.
 * Нажатие — то же сжатие 0.96 и заливка на шаг светлее, чтобы отклик был не только цветом.
 */
@Composable
fun FinneyQuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sound: Sfx = Sfx.Tap,
    icon: (@Composable () -> Unit)? = null,
) {
    val sounds = LocalSounds.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(percent = 50)
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press",
    )

    Surface(
        onClick = {
            sounds.play(sound)
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = QuietButtonHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled,
        shape = shape,
        color = if (pressed) FinneyCream else FinneySand,
        border = BorderStroke(StrokeRegular, FinneyInk),
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            icon?.invoke()
            Text(text, style = MaterialTheme.typography.titleLarge, color = FinneyInk)
        }
    }
}

/**
 * Круглая кнопка-иконка: действия с питомцем (кушать, мыться, магазин, свет)
 * и служебные кнопки вроде истории. Подпись не входит в саму кнопку —
 * под иконкой её ставит вызывающий экран, чтобы попадание было по кругу.
 *
 * [selected] — кнопка показывает то, что открыто сейчас. Берёт «нажатое
 * состояние» из кита: других состояний у круглой кнопки там не нарисовано,
 * а выбранное отличать надо. Уходит и в TalkBack: цвет — не единственный
 * признак (ТЗ п. 3.6).
 *
 * [sound] — null, если звук играет сам вызывающий: кнопки «−/+» с удержанием
 * щёлкают на каждом шаге своим тоном, и второй щелчок на касании задвоил бы его.
 * [interactionSource] — свой, если вызывающему надо знать, что палец ещё на кнопке.
 */
@Composable
fun FinneyIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    enabled: Boolean = true,
    selected: Boolean = false,
    sound: Sfx? = Sfx.Tap,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val sounds = LocalSounds.current
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press",
    )

    Surface(
        onClick = {
            sound?.let(sounds::play)
            onClick()
        },
        // Содержимое кнопки — рисунок без текста, поэтому подпись для TalkBack
        // задаётся здесь и заменяет собой то, что внутри.
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                this.contentDescription = contentDescription
                this.selected = selected
            },
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        interactionSource = source,
    ) {
        Box(
            modifier = Modifier.clip(CircleShape).buttonFill(pressed || selected, round = true),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

// Кольцо шкалы вокруг круглой кнопки. В ките оно подписано «опускание/поднятие
// шкал потребностей (по часовой стрелке)» и нарисовано одинаково у кнопки
// действия и у значка уровня.
//
// Промерено по кадру кита на значке уровня (внешний диаметр 350 px): полосы
// идут снаружи внутрь — синее кольцо 15, дорожка 14, обводка самой кнопки 15,
// и дальше заливка. Дорожка пустая белая, заполненная часть розовая, и цвета
// эти в ките одни и те же в покое и под пальцем.

/**
 * Ширина дорожки — доля диаметра кнопки: 14 из 292 в кадре кита.
 *
 * Внешнее кольцо отдельным числом не задаётся: это обычная обводка, то есть
 * `strokeFor()`. В кадре она немного тоньше, но по всему приложению обводка
 * одна, и кольцо из общего правила не выбивается.
 */
private const val RingTrackFraction = 0.048f

/**
 * Кольцо-шкала вокруг круглого содержимого.
 *
 * [diameter] — диаметр того, что внутри; кольцо ложится снаружи, поэтому сам
 * элемент получается шире. [progress] в долях 0..1; null — шкалы нет, кольцо
 * не рисуется, но место под него остаётся: иначе кнопки в одном ряду разъедутся
 * по размеру и ряд перестанет читаться как ряд.
 */
@Composable
fun ProgressRing(
    diameter: Dp,
    progress: Float?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val track = diameter * RingTrackFraction
    val ring = strokeFor(diameter)
    val fraction by animateFloatAsState(
        targetValue = progress?.coerceIn(0f, 1f) ?: 0f,
        label = "ring",
    )

    Box(
        modifier = modifier
            .size(diameter + (track + ring) * 2)
            .drawBehind {
                if (progress == null) return@drawBehind

                val trackWidth = track.toPx()
                val ringWidth = ring.toPx()

                // Радиус по середине дорожки: она начинается ровно от края
                // содержимого, потому что обводка кнопки уже уложена внутрь
                // её собственного круга.
                val trackRadius = (diameter.toPx() + trackWidth) / 2f
                drawCircle(FinneyCream, radius = trackRadius, style = Stroke(trackWidth))
                drawArc(
                    color = FinneyPink,
                    // -90° — верх. Дуга растёт по часовой стрелке, как подписано в ките.
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = Offset(center.x - trackRadius, center.y - trackRadius),
                    size = Size(trackRadius * 2f, trackRadius * 2f),
                    style = Stroke(trackWidth),
                )

                drawCircle(
                    color = FinneyInk,
                    radius = (diameter.toPx() + trackWidth * 2f + ringWidth) / 2f,
                    style = Stroke(ringWidth),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Кнопка комнаты: круглая иконка со шкалой потребности вокруг.
 *
 * Шкалы живут здесь, а не отдельной полосой сверху, — так они нарисованы
 * в ките и так их видно ровно там, где эту потребность закрывают.
 *
 * [value] — шкала 0..100; null — за кнопкой нет потребности (зал и сон),
 * кольцо тогда пустое. Короткая дуга и есть нецветовой признак «мало»
 * (ТЗ п. 3.6): длина читается и без различения цветов, а число уходит
 * в TalkBack.
 */
@Composable
fun FinneyNeedButton(
    icon: FinneyIcons,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: Int? = null,
    /** Низкая потребность отмечается независимо от выбранной комнаты. */
    critical: Boolean = false,
    selected: Boolean = false,
    size: Dp = 64.dp,
    enabled: Boolean = true,
) {
    val clamped = value?.coerceIn(0, 100)
    val sounds = LocalSounds.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) PressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "roomPress",
    )
    // Круг и подпись — одна кнопка, в том числе для TalkBack и подсказки-пальца.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(enabled = enabled, role = Role.Button, interactionSource = source, indication = null) {
                sounds.play(Sfx.Tap)
                onClick()
            }
            .semantics {
                this.selected = selected
                if (clamped != null) stateDescription = "$clamped из 100" +
                    if (critical) ", критически низко" else ""
            },
    ) {
        Box {
            ProgressRing(diameter = size, progress = clamped?.let { it / 100f }) {
                Box(
                    modifier = Modifier.size(size)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(CircleShape).buttonFill(pressed || selected, round = true),
                    contentAlignment = Alignment.Center,
                ) { FinneyIcon(icon, size = size / 2) }
            }
            if (critical) WarningBadge(size = 22.dp, modifier = Modifier.align(Alignment.TopEnd))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = FinneyInk,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp)
                .background(FinneyCream, RoundedCornerShape(6.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp))
    }
}

/** Непрозрачная подпись сохраняет контраст на обоях и тёмном полу любой комнаты. */
@Composable
fun RoomActionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = FinneyInk,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(FinneyCream, RoundedCornerShape(RadiusField))
            .border(StrokeThin, FinneyInk, RoundedCornerShape(RadiusField))
            .padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

/**
 * Заливка кнопки: плоский цвет, полоса потемнее по низу, блик и обводка.
 * Общая для всех кнопок-«стадионов» кита, в том числе нестандартных вроде
 * кнопки денег на главном, — чтобы форма полосы не расходилась по копиям.
 *
 * Обе пары цветов идут по палитре: в покое жёлтый на персиковом (кнопка —
 * это «деньги и награда»), под пальцем всё сдвигается на шаг в тёплое.
 *
 * [round] — кнопка круглая. Полоса у обеих по краям загибается вверх вдоль
 * скругления (см. [FaceCornerXFraction]); у «стадиона» между скруглениями
 * она прямая, у круга прямого участка нет, и граница — сплошная дуга,
 * прогнутая вниз. Круглая отличается только тем, где кончается жёлтое.
 *
 * [glare] — рисовать ли блик. Кнопка денег на главном рисовалась без него,
 * и это так и оставлено: её в ките нет, она только собрана по его правилам.
 *
 * [calm] — вторая пара из макета экрана условий уровня: зелёный на голубом,
 * под пальцем так же шаг вниз — голубой на синем.
 *
 * Обводка рисуется здесь, а не `BorderStroke` у `Surface`: `BorderStroke`
 * кладёт линию внутрь границ, а в макете `stroke position: outside`. Рисуем
 * сами с `Stroke` по краю — половина толщины уходит за границу формы и
 * обрезается `clip`, поэтому видимая обводка получается ровной и не съедает
 * заливку изнутри.
 */
internal fun Modifier.buttonFill(
    pressed: Boolean,
    round: Boolean,
    glare: Boolean = true,
    calm: Boolean = false,
): Modifier = drawBehind {
    val face = when {
        calm -> if (pressed) FinneyBlue else FinneyGreen
        else -> if (pressed) FinneyPeach else FinneyYellow
    }
    val band = when {
        calm -> if (pressed) FinneyInk else FinneyBlue
        else -> if (pressed) FinneyPink else FinneyPeach
    }

    // Полоса лежит подо всей кнопкой, а верхний цвет кладётся поверх
    // фигурой со скруглённым низом. Сбоку фигура идёт по краю заливки
    // (от обводки), верх её не важен — его срезает clip. У круга ширина
    // фигуры ровно вдвое больше полуоси, и скругления смыкаются в одну дугу.
    drawRect(band)
    val outline = size.height * FinneyStrokeRatio
    val corner = CornerRadius(
        x = size.height * FaceCornerXFraction,
        y = size.height * FaceCornerYFraction,
    )
    val faceBottom = if (round) RoundFaceBottomFraction else 1f - BottomBandFraction
    drawPath(
        path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = outline,
                    top = -size.height,
                    right = size.width - outline,
                    bottom = size.height * faceBottom,
                    bottomLeftCornerRadius = corner,
                    bottomRightCornerRadius = corner,
                ),
            )
        },
        color = face,
    )

    // Блик. Доли высоты кнопки, но у круглой они свои: в ките её блик мельче
    // и сидит ближе к краю.
    val glareWidth = size.height * if (round) RoundGlareWidthFraction else GlareWidthFraction
    val glareHeight = size.height * if (round) RoundGlareHeightFraction else GlareHeightFraction
    val centre = if (round) {
        Offset(size.height * RoundGlareCentreFraction, size.height * RoundGlareTopFraction)
    } else {
        Offset(size.height * GlareCentreFraction, size.height * GlareTopFraction)
    }
    if (glare) {
        rotate(degrees = GlareAngle, pivot = centre) {
            drawOval(
                color = FinneyGlare,
                topLeft = Offset(centre.x - glareWidth / 2f, centre.y - glareHeight / 2f),
                size = Size(glareWidth, glareHeight),
            )
        }
    }

    // Обводка по краю. Толщина — 1/12 высоты, как промерено в эталоне.
    if (round) {
        drawCircle(
            color = FinneyInk,
            radius = (size.minDimension - outline) / 2f,
            style = Stroke(width = outline),
        )
    } else {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(
            color = FinneyInk,
            topLeft = Offset(outline / 2f, outline / 2f),
            size = Size(size.width - outline, size.height - outline),
            cornerRadius = radius,
            style = Stroke(width = outline),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFEDCD)
@Composable
private fun FinneyButtonPreview() {
    FinneyTheme {
        Column(
            modifier = Modifier.background(FinneyCream).padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FinneyButton("Играть", onClick = {})
            FinneyButton("Закрыть", onClick = {}, fillWidth = false)
            FinneyIconButton(onClick = {}, contentDescription = "Кушать") {
                OutlinedText("Ф", style = MaterialTheme.typography.headlineMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FinneyNeedButton(FinneyIcons.Lamp, "Зал и сон", onClick = {})
                FinneyNeedButton(FinneyIcons.Food, "Покормить", onClick = {}, value = 75)
                FinneyNeedButton(FinneyIcons.Bath, "Помыть", onClick = {}, value = 20, selected = true)
            }
        }
    }
}
