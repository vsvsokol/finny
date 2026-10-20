package ru.finney.pet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyTheme

// Значки интерфейса. Рисуются кодом: иконок в ресурсах нет, а выдумывать пути
// к несуществующим файлам нельзя.
//
// В эталоне значки — плотные силуэты цветом обводки (#382C92) внутри круглой
// кнопки, без своей обводки и без полутонов. Поэтому здесь всё рисуется одним
// цветом и сплошной заливкой: эмодзи, стоявшие тут раньше, были многоцветными
// и спорили с плоским стилем макета.
//
// Все координаты — доли стороны квадрата, поэтому значок одинаково ложится
// на кнопку любого размера.

/** Что рисовать. Набор ровно тот, что нужен экранам и нарисован в макете. */
enum class FinneyIcons {
    /** Лампа — «зал/спать». */
    Lamp,

    /** Вилка и нож — «кушать». */
    Food,

    /** Ванна — «мыться». */
    Bath,

    /** Тележка — «магазин». */
    Cart,

    /** Динамик — громкость в настройках. */
    Speaker,

    /** Знак вопроса — подсказка. */
    Help,

    /** Три полосы — меню взрослого. */
    Menu,

    /** Свинка-копилка — цели и накопления. */
    Piggy,

    /** Звезда — задания. */
    Star,

    /** Кубок — прогресс. */
    Trophy,

    /** Тетрадь — план расходов. */
    Plan,

    /** Замок — нужное: из плана не убрать. */
    Lock,

    /** Вешалка — гардероб. */
    Hanger,

    /** Шестерёнка — настройки. */
    Gear,

    /** Кисточка — «Украсить»: окрас, одежда и комната. */
    Brush,
}

/**
 * Значок [icon] размером [size].
 *
 * Без `contentDescription`: значки стоят внутри кнопок, у которых подпись для
 * TalkBack задана своя, а дублирующая подпись заставила бы читать её дважды.
 */
@Composable
fun FinneyIcon(
    icon: FinneyIcons,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    tint: Color = FinneyInk,
) {
    Canvas(modifier = modifier.size(size)) {
        when (icon) {
            FinneyIcons.Lamp -> drawLamp(tint)
            FinneyIcons.Food -> drawFood(tint)
            FinneyIcons.Bath -> drawBath(tint)
            FinneyIcons.Cart -> drawCart(tint)
            FinneyIcons.Speaker -> drawSpeaker(tint)
            FinneyIcons.Help -> drawHelp(tint)
            FinneyIcons.Menu -> drawMenu(tint)
            FinneyIcons.Piggy -> drawPiggy(tint)
            FinneyIcons.Star -> drawStar(tint)
            FinneyIcons.Trophy -> drawTrophy(tint)
            FinneyIcons.Plan -> drawPlan(tint)
            FinneyIcons.Lock -> drawLock(tint)
            FinneyIcons.Hanger -> drawHanger(tint)
            FinneyIcons.Gear -> drawGear(tint)
            FinneyIcons.Brush -> drawBrush(tint)
        }
    }
}

// Четыре значка ниже перерисованы по кадру кита: буквально по маске синих
// пикселей внутри кнопок ряда «нормальное состояние». Прежние были нарисованы
// по памяти и рядом с эталоном читались как другой набор — тоньше, мельче
// и в других пропорциях.
//
// Общее у всех четырёх: рисунок занимает почти весь квадрат (в ките он лежит
// на 0.27..0.80 кнопки, а сам значок — половина её ширины), формы плотные,
// линии толстые. Тонких штрихов в ките нет вовсе.

// Торшер: широкий абажур, короткая ножка, узкое основание.
// В ките абажур втрое выше ножки — раньше было наоборот.
private fun DrawScope.drawLamp(tint: Color) {
    val s = size.minDimension
    val shade = Path().apply {
        moveTo(s * 0.28f, s * 0.04f)
        lineTo(s * 0.72f, s * 0.04f)
        lineTo(s * 0.93f, s * 0.66f)
        lineTo(s * 0.07f, s * 0.66f)
        close()
    }
    drawPath(shade, tint)
    drawRect(tint, Offset(s * 0.43f, s * 0.66f), Size(s * 0.14f, s * 0.16f))
    drawRoundRectSolid(tint, s * 0.28f, s * 0.80f, s * 0.44f, s * 0.16f)
}

// Вилка и нож. В ките это две плотные фигуры, а не штрихи: у вилки три толстых
// зубца на широкой голове, у ножа клинок со скруглённым верхом и своя ручка.
private fun DrawScope.drawFood(tint: Color) {
    val s = size.minDimension

    for (i in 0..2) {
        val x = s * (0.15f + i * 0.15f)
        drawLine(tint, Offset(x, s * 0.06f), Offset(x, s * 0.30f), s * 0.10f, StrokeCap.Round)
    }
    drawRoundRectSolid(tint, s * 0.10f, s * 0.22f, s * 0.40f, s * 0.22f, radius = s * 0.10f)
    drawRoundRectSolid(tint, s * 0.22f, s * 0.38f, s * 0.16f, s * 0.60f, radius = s * 0.08f)

    val blade = Path().apply {
        addRoundRect(
            RoundRect(
                left = s * 0.58f,
                top = s * 0.04f,
                right = s * 0.90f,
                bottom = s * 0.56f,
                // Скруглён только левый верхний угол: правая кромка клинка
                // в ките прямая, и без этого фигура читается как ложка.
                topLeftCornerRadius = CornerRadius(s * 0.30f),
                topRightCornerRadius = CornerRadius(s * 0.06f),
                bottomRightCornerRadius = CornerRadius.Zero,
                bottomLeftCornerRadius = CornerRadius(s * 0.12f),
            ),
        )
    }
    drawPath(blade, tint)
    drawRoundRectSolid(tint, s * 0.68f, s * 0.44f, s * 0.18f, s * 0.54f, radius = s * 0.09f)
}

// Ванна: чаша с плоским верхом и скруглённым дном, кран-крюк слева, две ножки.
private fun DrawScope.drawBath(tint: Color) {
    val s = size.minDimension

    // Кран поднимается от левого края чаши и загибается над ней вправо.
    val tap = Path().apply {
        moveTo(s * 0.22f, s * 0.46f)
        lineTo(s * 0.22f, s * 0.20f)
        quadraticTo(s * 0.22f, s * 0.06f, s * 0.37f, s * 0.06f)
        quadraticTo(s * 0.50f, s * 0.06f, s * 0.50f, s * 0.20f)
    }
    drawPath(tap, tint, style = Stroke(width = s * 0.13f, cap = StrokeCap.Round))

    val tub = Path().apply {
        addRoundRect(
            RoundRect(
                left = s * 0.02f,
                top = s * 0.45f,
                right = s * 0.98f,
                bottom = s * 0.82f,
                topLeftCornerRadius = CornerRadius(s * 0.05f),
                topRightCornerRadius = CornerRadius(s * 0.05f),
                bottomRightCornerRadius = CornerRadius(s * 0.26f),
                bottomLeftCornerRadius = CornerRadius(s * 0.26f),
            ),
        )
    }
    drawPath(tub, tint)

    drawRect(tint, Offset(s * 0.24f, s * 0.78f), Size(s * 0.08f, s * 0.22f))
    drawRect(tint, Offset(s * 0.68f, s * 0.78f), Size(s * 0.08f, s * 0.22f))
}

// Тележка: наклонная ручка слева, корзина-трапеция, два колеса.
private fun DrawScope.drawCart(tint: Color) {
    val s = size.minDimension

    drawLine(
        tint,
        Offset(s * 0.05f, s * 0.10f),
        Offset(s * 0.24f, s * 0.32f),
        s * 0.13f,
        StrokeCap.Round,
    )

    val basket = Path().apply {
        moveTo(s * 0.16f, s * 0.30f)
        lineTo(s * 0.98f, s * 0.30f)
        lineTo(s * 0.84f, s * 0.74f)
        lineTo(s * 0.28f, s * 0.74f)
        close()
    }
    drawPath(basket, tint)

    drawCircle(tint, s * 0.10f, Offset(s * 0.33f, s * 0.89f))
    drawCircle(tint, s * 0.10f, Offset(s * 0.74f, s * 0.89f))
}

// Динамик: квадрат с раструбом и две дуги звука.
private fun DrawScope.drawSpeaker(tint: Color) {
    val s = size.minDimension
    val body = Path().apply {
        moveTo(s * 0.16f, s * 0.38f)
        lineTo(s * 0.32f, s * 0.38f)
        lineTo(s * 0.54f, s * 0.18f)
        lineTo(s * 0.54f, s * 0.82f)
        lineTo(s * 0.32f, s * 0.62f)
        lineTo(s * 0.16f, s * 0.62f)
        close()
    }
    drawPath(body, tint)
    drawArc(
        color = tint,
        startAngle = -55f,
        sweepAngle = 110f,
        useCenter = false,
        topLeft = Offset(s * 0.48f, s * 0.30f),
        size = Size(s * 0.28f, s * 0.40f),
        style = Stroke(width = s * 0.075f, cap = StrokeCap.Round),
    )
}

// Вопросительный знак — рисуется текстом-формой: дуга и точка.
private fun DrawScope.drawHelp(tint: Color) {
    val s = size.minDimension
    val stroke = s * 0.11f
    drawArc(
        color = tint,
        startAngle = 160f,
        sweepAngle = 220f,
        useCenter = false,
        topLeft = Offset(s * 0.30f, s * 0.16f),
        size = Size(s * 0.40f, s * 0.40f),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    drawLine(
        tint,
        Offset(s * 0.50f, s * 0.50f),
        Offset(s * 0.50f, s * 0.64f),
        stroke,
        StrokeCap.Round,
    )
    drawCircle(tint, s * 0.07f, Offset(s * 0.50f, s * 0.80f))
}

// Три полосы меню. В эталоне у кнопки-бургера полосы толстые и со скруглением.
private fun DrawScope.drawMenu(tint: Color) {
    val s = size.minDimension
    val h = s * 0.10f
    for (i in 0..2) {
        drawRoundRectSolid(tint, s * 0.22f, s * 0.26f + i * s * 0.21f, s * 0.56f, h)
    }
}

// Свинка-копилка боком: туловище, пятачок, ушко, ножки, хвостик, прорезь на спине
// и монетка над ней. Раньше здесь была банка-трапеция — на плейтесте 28.09 её
// не узнали: «копилка» у ребёнка — это свинка.
//
// Прорезь, глаз и ноздри вырезаются (Difference), а не закрашиваются цветом фона:
// кнопка под значком меняет цвет при нажатии, и закрашенные дырки остались бы
// пятнами прежнего фона.
private fun DrawScope.drawPiggy(tint: Color) {
    val s = size.minDimension

    // Монетка над прорезью.
    drawCircle(tint, s * 0.085f, Offset(s * 0.40f, s * 0.19f))

    // Части сливаются через Union: при простом наложении контуры с разным
    // направлением обхода гасят друг друга, и на стыке уха оставалась щель.
    val body = listOf(
        Path().apply { addOval(Rect(s * 0.12f, s * 0.33f, s * 0.80f, s * 0.80f)) },
        // Пятачок выступает вперёд из морды.
        Path().apply { addRoundRect(RoundRect(s * 0.74f, s * 0.44f, s * 0.93f, s * 0.66f, CornerRadius(s * 0.07f))) },
        // Ушко торчит вверх над мордой.
        Path().apply {
            moveTo(s * 0.58f, s * 0.42f)
            quadraticTo(s * 0.62f, s * 0.22f, s * 0.70f, s * 0.24f)
            quadraticTo(s * 0.74f, s * 0.34f, s * 0.74f, s * 0.46f)
            close()
        },
        // Ножки: передняя и задняя.
        Path().apply { addRoundRect(RoundRect(s * 0.22f, s * 0.68f, s * 0.35f, s * 0.90f, CornerRadius(s * 0.04f))) },
        Path().apply { addRoundRect(RoundRect(s * 0.55f, s * 0.68f, s * 0.68f, s * 0.90f, CornerRadius(s * 0.04f))) },
    ).reduce { acc, part -> Path().apply { op(acc, part, PathOperation.Union) } }
    val holes = Path().apply {
        addRoundRect(RoundRect(s * 0.30f, s * 0.41f, s * 0.50f, s * 0.47f, CornerRadius(s * 0.03f)))
        addOval(Rect(Offset(s * 0.66f, s * 0.50f), s * 0.035f))
        addOval(Rect(Offset(s * 0.815f, s * 0.55f), s * 0.025f))
        addOval(Rect(Offset(s * 0.875f, s * 0.55f), s * 0.025f))
    }
    drawPath(Path().apply { op(body, holes, PathOperation.Difference) }, tint)

    // Хвостик-закорючка сзади.
    drawArc(
        color = tint,
        startAngle = 90f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = Offset(s * 0.03f, s * 0.44f),
        size = Size(s * 0.11f, s * 0.11f),
        style = Stroke(width = s * 0.05f, cap = StrokeCap.Round),
    )
}

// Звезда о пяти лучах.
private fun DrawScope.drawStar(tint: Color) {
    val s = size.minDimension
    val cx = s / 2f
    val cy = s * 0.52f
    val outer = s * 0.36f
    val inner = outer * 0.42f
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        // Начинаем с вершины: -90° — это верх.
        val a = Math.toRadians((-90f + i * 36f).toDouble())
        val x = cx + r * kotlin.math.cos(a).toFloat()
        val y = cy + r * kotlin.math.sin(a).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, tint)
}

// Кубок: чаша, ручки, ножка и подставка.
private fun DrawScope.drawTrophy(tint: Color) {
    val s = size.minDimension
    val cup = Path().apply {
        moveTo(s * 0.30f, s * 0.18f)
        lineTo(s * 0.70f, s * 0.18f)
        lineTo(s * 0.64f, s * 0.52f)
        lineTo(s * 0.36f, s * 0.52f)
        close()
    }
    drawPath(cup, tint)
    val handle = Stroke(width = s * 0.07f, cap = StrokeCap.Round)
    drawArc(
        color = tint,
        startAngle = 90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(s * 0.16f, s * 0.20f),
        size = Size(s * 0.20f, s * 0.22f),
        style = handle,
    )
    drawArc(
        color = tint,
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(s * 0.64f, s * 0.20f),
        size = Size(s * 0.20f, s * 0.22f),
        style = handle,
    )
    drawRect(tint, Offset(s * 0.465f, s * 0.52f), Size(s * 0.07f, s * 0.18f))
    drawRoundRectSolid(tint, s * 0.32f, s * 0.70f, s * 0.36f, s * 0.09f)
}

// Тетрадь плана: страница в рамке со строками.
//
// Контур, а не сплошная заливка с «дырками»: значок лежит на кнопке, цвет
// которой меняется при нажатии, и вырезать строки фиксированным цветом
// подложки нельзя — на нажатой кнопке они оставались бы от прежнего фона.
private fun DrawScope.drawPlan(tint: Color) {
    val s = size.minDimension
    val line = s * 0.075f
    drawRoundRect(
        color = tint,
        topLeft = Offset(s * 0.22f + line / 2f, s * 0.16f + line / 2f),
        size = Size(s * 0.56f - line, s * 0.68f - line),
        cornerRadius = CornerRadius(s * 0.10f),
        style = Stroke(width = line),
    )
    for (i in 0..2) {
        drawLine(
            tint,
            Offset(s * 0.34f, s * 0.34f + i * s * 0.14f),
            Offset(s * 0.66f, s * 0.34f + i * s * 0.14f),
            line * 0.8f,
            StrokeCap.Round,
        )
    }
}

// Замок: дужка сверху и корпус с вырезанной скважиной. Скважина вырезается,
// а не закрашивается — по той же причине, что прорезь копилки.
private fun DrawScope.drawLock(tint: Color) {
    val s = size.minDimension
    drawArc(
        color = tint,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(s * 0.28f, s * 0.10f),
        size = Size(s * 0.44f, s * 0.44f),
        style = Stroke(width = s * 0.12f),
    )
    drawRect(tint, Offset(s * 0.22f, s * 0.30f), Size(s * 0.12f, s * 0.14f))
    drawRect(tint, Offset(s * 0.66f, s * 0.30f), Size(s * 0.12f, s * 0.14f))
    val body = Path().apply {
        addRoundRect(RoundRect(s * 0.14f, s * 0.42f, s * 0.86f, s * 0.92f, CornerRadius(s * 0.10f)))
    }
    val hole = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(Offset(s * 0.50f, s * 0.60f), s * 0.07f))
        addRect(androidx.compose.ui.geometry.Rect(s * 0.47f, s * 0.62f, s * 0.53f, s * 0.78f))
    }
    drawPath(Path().apply { op(body, hole, PathOperation.Difference) }, tint)
}

// Вешалка: крючок сверху и треугольные плечики. Плечики — контуром, как тетрадь
// плана: сплошной треугольник читался как крыша.
private fun DrawScope.drawHanger(tint: Color) {
    val s = size.minDimension
    val line = s * 0.085f
    drawArc(
        color = tint,
        startAngle = 180f,
        sweepAngle = 250f,
        useCenter = false,
        topLeft = Offset(s * 0.40f, s * 0.12f),
        size = Size(s * 0.20f, s * 0.20f),
        style = Stroke(width = line, cap = StrokeCap.Round),
    )
    val shoulders = Path().apply {
        moveTo(s * 0.50f, s * 0.36f)
        lineTo(s * 0.90f, s * 0.72f)
        lineTo(s * 0.10f, s * 0.72f)
        close()
    }
    drawPath(shoulders, tint, style = Stroke(width = line, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawLine(tint, Offset(s * 0.50f, s * 0.30f), Offset(s * 0.50f, s * 0.38f), line, StrokeCap.Round)
}

// Кисточка: ручка наискось, толстый обжим и мазок-капля с хвостиком влево вниз.
// Как и прочие значки кита — плотные формы и толстые линии, без тонких штрихов.
private fun DrawScope.drawBrush(tint: Color) {
    val s = size.minDimension
    drawLine(tint, Offset(s * 0.86f, s * 0.14f), Offset(s * 0.56f, s * 0.44f), s * 0.11f, StrokeCap.Round)
    drawLine(tint, Offset(s * 0.58f, s * 0.42f), Offset(s * 0.48f, s * 0.52f), s * 0.20f, StrokeCap.Butt)
    drawCircle(tint, radius = s * 0.17f, center = Offset(s * 0.35f, s * 0.65f))
    val flick = Path().apply {
        moveTo(s * 0.20f, s * 0.58f)
        lineTo(s * 0.08f, s * 0.92f)
        lineTo(s * 0.42f, s * 0.80f)
        close()
    }
    drawPath(flick, tint)
}

// Шестерёнка: восемь зубцов вокруг круга и дырка посередине. Дырка вырезается,
// а не закрашивается — по той же причине, что прорезь копилки.
private fun DrawScope.drawGear(tint: Color) {
    val s = size.minDimension
    val c = Offset(s / 2f, s / 2f)
    var gear = Path().apply { addOval(Rect(c, s * 0.30f)) }
    for (i in 0 until 8) {
        val tooth = Path().apply {
            addRoundRect(RoundRect(c.x - s * 0.08f, s * 0.08f, c.x + s * 0.08f, s * 0.30f, CornerRadius(s * 0.03f)))
            transform(
                androidx.compose.ui.graphics.Matrix().apply {
                    translate(c.x, c.y)
                    rotateZ(i * 45f)
                    translate(-c.x, -c.y)
                },
            )
        }
        gear = Path().apply { op(gear, tooth, PathOperation.Union) }
    }
    val hole = Path().apply { addOval(Rect(c, s * 0.12f)) }
    drawPath(Path().apply { op(gear, hole, PathOperation.Difference) }, tint)
}

/** Скруглённый прямоугольник сплошной заливкой — самая частая фигура в значках. */
private fun DrawScope.drawRoundRectSolid(
    color: Color,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    radius: Float = h / 2f,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFEDCD)
@Composable
private fun FinneyIconPreview() {
    FinneyTheme {
        Row(
            modifier = Modifier.background(FinneyCream).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FinneyIcons.entries.forEach { FinneyIcon(it, size = 30.dp) }
        }
    }
}
