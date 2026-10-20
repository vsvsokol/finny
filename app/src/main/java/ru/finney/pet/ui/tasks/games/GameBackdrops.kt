package ru.finney.pet.ui.tasks.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import ru.finney.pet.ui.theme.FinneyInk

// Фоны мини-игр по макету «Фоны мини-игр»: луг гонки, магазин сладостей, касса
// с прилавком и комната трёх игр. Макет нарисован на холсте 900 × 2000 (телефон
// 412 dp), поэтому здесь всё в его единицах: холст масштабируется по ширине экрана.
// Что стоит у верха экрана (небо, маркиза, стена кассы), привязано к верху, а пол,
// плинтус и панели — к низу: на вытянутых телефонах между ними растёт стена.

/** Фигурки узора, каждая в квадрате 24 × 24 с центром в 12, 12 — как в svg макета. */
internal enum class MotifShape { STAR, SPARK, HEART, COIN, DROP, CLOUD, CLOVER, V, W, FLOWER }

/**
 * Фигурка узора: центр [x], [y] в единицах плитки, масштаб от квадрата 24 × 24,
 * поворот в градусах. [color] — своя заливка (цветочки), иначе цвет узора.
 */
internal class Motif(
    val shape: MotifShape,
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotation: Float = 0f,
    val color: Color? = null,
)

/** Фигурка в квадрате 24 × 24, как в svg макета. */
internal fun DrawScope.drawMotif(shape: MotifShape, color: Color) {
    when (shape) {
        MotifShape.STAR -> {
            drawPath(MotifStar, color)
            drawPath(MotifStar, color, style = Stroke(2f, join = StrokeJoin.Round))
        }
        MotifShape.SPARK -> drawPath(MotifSpark, color)
        MotifShape.HEART -> drawPath(MotifHeart, color)
        MotifShape.COIN -> {
            drawCircle(color, 9.5f, Offset(12f, 12f), style = Stroke(3.2f))
            drawOval(color, Offset(12f - 4.2f, 12f - 3.2f), Size(8.4f, 6.4f), style = Stroke(2.6f))
            drawLine(color, Offset(12f, 6.5f), Offset(12f, 17.5f), 2.6f, StrokeCap.Round)
        }
        MotifShape.DROP -> drawPath(MotifDrop, color)
        MotifShape.CLOUD -> drawPath(MotifCloud, color)
        MotifShape.CLOVER -> CloverLeaves.forEach { drawCircle(color, 4.6f, it) }
        MotifShape.V -> drawPath(MotifV, color, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        MotifShape.W -> drawPath(MotifW, color, style = Stroke(3.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        MotifShape.FLOWER -> {
            FlowerPetals.forEach { drawCircle(color, 5f, it) }
            drawCircle(FlowerMiddle, 3.8f, Offset(12f, 12f))
        }
    }
}

private val MotifStar = svgPath("M12 2.5l2.9 6 6.6.9-4.8 4.6 1.2 6.6L12 17.4l-5.9 3.2 1.2-6.6L2.5 9.4l6.6-.9z")
private val MotifSpark = svgPath("M12 0c1.1 6.4 4.6 10.9 12 12-7.4 1.1-10.9 5.6-12 12-1.1-6.4-4.6-10.9-12-12 7.4-1.1 10.9-5.6 12-12z")
private val MotifHeart = svgPath(
    "M12 21.5S3 16 2.2 10.2C1.6 6.6 4 4 7.2 4c2 0 3.6 1.1 4.8 2.9C13.2 5.1 14.8 4 16.8 4 20 4 22.4 6.6 21.8 10.2 21 16 12 21.5 12 21.5z",
)
private val MotifDrop = svgPath("M12 2c4.2 6 7 9.4 7 13a7 7 0 0 1-14 0c0-3.6 2.8-7 7-13z")
private val MotifCloud = svgPath("M6.5 18h11a4.5 4.5 0 0 0 .6-9 6 6 0 0 0-11.5 1.4A3.8 3.8 0 0 0 6.5 18z")
private val MotifV = svgPath("M5 6l7 13 7-13")
private val MotifW = svgPath("M1 7l5.5 11L12 7l5.5 11L23 7")
private val CloverLeaves = listOf(Offset(12f, 7f), Offset(17f, 12f), Offset(12f, 17f), Offset(7f, 12f))
private val FlowerPetals = listOf(Offset(12f, 6f), Offset(17.7f, 10.1f), Offset(15.5f, 16.9f), Offset(8.5f, 16.9f), Offset(6.3f, 10.1f))
private val FlowerMiddle = Color(0xFFF7DC8B)

/**
 * Узор плиткой [tile] × [tile] единиц холста в пределах [area], как `<pattern>` в svg:
 * начало плиток в 0, 0 со сдвигом [shift]. [k] — масштаб раскладки: плитки стен
 * разложены под телефон 412 dp и на холсте 900 увеличены в 2,2 раза.
 */
private fun DrawScope.tiles(area: Rect, size: Float, motifs: List<Motif>, ink: Color, k: Float = 2.2f, shift: Offset = Offset.Zero) {
    val t = size * k
    val sx = shift.x * k
    val sy = shift.y * k
    clipRect(area.left, area.top, area.right, area.bottom) {
        // Одна плитка с запасом слева и сверху: фигурки у её края заходят на соседнюю.
        var y = firstTile(sy, area.top, t)
        while (y < area.bottom) {
            var x = firstTile(sx, area.left, t)
            while (x < area.right) {
                motifs.forEach { m ->
                    withTransform({
                        translate(x + m.x * k, y + m.y * k)
                        rotate(m.rotation, Offset.Zero)
                        scale(m.scale * k, m.scale * k, Offset.Zero)
                        translate(-12f, -12f)
                    }) { drawMotif(m.shape, m.color ?: ink) }
                }
                x += t
            }
            y += t
        }
    }
}

/** Начало первой плитки с началом [origin] шагом [t], чтобы покрыть всё от [from]. */
private fun firstTile(origin: Float, from: Float, t: Float): Float =
    origin + kotlin.math.floor((from - origin) / t) * t - t

/** Холст макета 900 единиц в ширину. [block] получает высоту экрана в этих единицах. */
@Composable
private fun DesignCanvas(block: DrawScope.(height: Float) -> Unit) {
    Canvas(Modifier.fillMaxSize()) {
        val s = size.width / DESIGN_W
        scale(s, s, Offset.Zero) { block(size.height / s) }
    }
}

private const val DESIGN_W = 900f

/** Высота холста макета: всё, что привязано к низу, считается от неё. */
private const val DESIGN_H = 2000f

private fun DrawScope.outlinedRect(x: Float, y: Float, w: Float, h: Float, fill: Color, stroke: Float, r: Float = 0f) {
    val radius = CornerRadius(r, r)
    drawRoundRect(fill, Offset(x, y), Size(w, h), radius)
    drawRoundRect(FinneyInk, Offset(x, y), Size(w, h), radius, style = Stroke(stroke))
}

/** Пол из досок рядами по 70 единиц, стыки в соседних рядах сдвинуты. */
private fun DrawScope.planks(y0: Float, y1: Float, base: Color, seam: Color) {
    drawRect(base, Offset(0f, y0), Size(DESIGN_W, y1 - y0))
    val row = 70f
    var y = y0 + row
    while (y < y1) {
        drawLine(seam, Offset(0f, y), Offset(DESIGN_W, y), 5f, StrokeCap.Round)
        y += row
    }
    var r = y0
    var i = 0
    while (r < y1) {
        var x = PlankSeams[i % PlankSeams.size]
        while (x < DESIGN_W) {
            drawLine(seam, Offset(x, r), Offset(x, minOf(r + row, y1)), 5f, StrokeCap.Round)
            x += 420f
        }
        r += row
        i++
    }
}

private val PlankSeams = listOf(240f, 110f, 330f, 180f)

// ---------- Луг гонки ----------

/**
 * Луг для «Дороги к цели»: та же трава, что у лимонадной лавки, горизонт высоко,
 * на дальнем холме два дерева, облака без солнца — под крестиком ничего не прячется.
 * Луг в клевере, травинках и цветочках тремя слоями плиток разного размера.
 */
@Composable
internal fun RaceMeadow() {
    DesignCanvas { h ->
        drawRect(RaceSky, size = Size(DESIGN_W, 520f))
        listOf(Motif(MotifShape.SPARK, 300f, 290f, 2f), Motif(MotifShape.SPARK, 820f, 265f, 1.5f), Motif(MotifShape.SPARK, 170f, 330f, 1.1f))
            .forEach { m ->
                withTransform({
                    translate(m.x, m.y)
                    scale(m.scale, m.scale, Offset.Zero)
                    translate(-12f, -12f)
                }) { drawMotif(m.shape, RaceSkySpark) }
            }
        raceCloud(40f, 250f, 1.25f)
        raceCloud(600f, 290f, 0.95f)
        raceCloud(360f, 120f, 0.8f)
        raceTuft(240f, 372.5f, 0.75f, RaceHillFar)
        raceTuft(720f, 365f, 0.75f, RaceHillFar)
        drawPath(RaceBack, RaceHillFar)
        clipPath(RaceBack) { drawPath(RaceBack, RaceHillFarLight, style = Stroke(24f)) }
        drawPath(RaceBack, FinneyInk, style = Stroke(7f, join = StrokeJoin.Round))
        raceTree(630f, 380f, 0.9f)
        raceTree(120f, 388f, 0.7f)
        raceTuft(285f, 450f, 1f, RaceGrass)
        raceTuft(555f, 453f, 1f, RaceGrass)
        raceTuft(825f, 448f, 1f, RaceGrass)
        raceTuft(80f, 452f, 0.75f, RaceGrass)
        drawPath(RaceFront, RaceGrass)
        clipPath(RaceFront) {
            val area = Rect(0f, 430f, DESIGN_W, h)
            tiles(area, 210f, RaceLayer1, RacePattern, k = 1.4f)
            tiles(area, 330f, RaceLayer2, RacePattern, k = 1.4f, shift = Offset(70f, 45f))
            tiles(area, 470f, RaceLayer3, RacePattern, k = 1.4f, shift = Offset(190f, 120f))
            drawPath(RaceFront, RaceGrassLight, style = Stroke(26f))
        }
        drawPath(RaceFront, FinneyInk, style = Stroke(7f, join = StrokeJoin.Round))
    }
}

private fun DrawScope.raceCloud(x: Float, y: Float, s: Float) {
    withTransform({
        translate(x, y)
        scale(s, s, Offset.Zero)
    }) {
        drawPath(RaceCloudPath, Color.White)
        drawPath(RaceCloudPath, FinneyInk, style = Stroke(6f / s, join = StrokeJoin.Round))
    }
}

private fun DrawScope.raceTuft(x: Float, y: Float, s: Float, fill: Color) {
    withTransform({
        translate(x, y)
        scale(s, s, Offset.Zero)
    }) {
        drawPath(RaceTuftPath, fill)
        drawPath(RaceTuftPath, FinneyInk, style = Stroke(6.5f / s, join = StrokeJoin.Round))
    }
}

private fun DrawScope.raceTree(x: Float, y: Float, s: Float) {
    withTransform({
        translate(x, y)
        scale(s, s, Offset.Zero)
    }) {
        outlinedRect(-9f, -46f, 18f, 50f, RaceTrunk, 6f, r = 5f)
        drawCircle(RaceGrass, 46f, Offset(0f, -82f))
        drawCircle(FinneyInk, 46f, Offset(0f, -82f), style = Stroke(6f))
        withTransform({ rotate(-25f, Offset(-17f, -100f)) }) {
            drawOval(RaceGrassLight, Offset(-17f - 13f, -100f - 9f), Size(26f, 18f))
        }
    }
}

// Низ холмов в макете — 2000; здесь ниже, чтобы на вытянутых экранах луг не кончался.
private val RaceBack = svgPath(
    "M-20,6000 L-20,382 L0,380 Q120,350 240,372.5 Q360,395 480,372.5 Q600,350 720,365 Q840,380 930,370 L930,6000 Z",
)
private val RaceFront = svgPath(
    "M-20,6000 L-20,452 L15,450 Q150,430 285,450 Q420,470 555,453 Q690,436 825,448 Q960,460 1000,465 L1000,6000 Z",
)
private val RaceTuftPath = svgPath("M-32,20 L-23,-15 L-11,5 L0,-31 L11,5 L23,-17 L32,20 Z")
private val RaceCloudPath = svgPath("M14 70C-6 70-8 40 16 37 16 14 44 4 60 22 70 0 108 2 110 30 132 26 144 50 130 70Z")

private val RaceSky = Color(0xFF9CCBF0)
private val RaceSkySpark = Color(0xFFC4E1F7)
private val RaceHillFar = Color(0xFFB4E0AE)
private val RaceHillFarLight = Color(0xFFCDEBC7)
private val RaceGrass = Color(0xFF86CC8E)
private val RaceGrassLight = Color(0xFFA5DBA9)
private val RacePattern = Color(0xFF72BD7C)
private val RaceTrunk = Color(0xFFA0715A)
private val RaceCoral = Color(0xFFFA987C)

private val RaceLayer1 = listOf(
    Motif(MotifShape.V, 30f, 40f, 1f), Motif(MotifShape.CLOVER, 120f, 30f, 0.75f), Motif(MotifShape.W, 150f, 130f, 1f),
    Motif(MotifShape.V, 70f, 150f, 0.8f), Motif(MotifShape.V, 180f, 190f, 0.7f),
)
private val RaceLayer2 = listOf(
    Motif(MotifShape.CLOVER, 250f, 80f, 0.6f), Motif(MotifShape.FLOWER, 90f, 250f, 0.85f, 15f, RaceCoral),
    Motif(MotifShape.W, 40f, 60f, 0.9f), Motif(MotifShape.FLOWER, 260f, 290f, 0.7f, 0f, Color.White),
)
private val RaceLayer3 = listOf(
    Motif(MotifShape.FLOWER, 120f, 90f, 0.95f, 0f, Color.White), Motif(MotifShape.CLOVER, 370f, 300f, 0.7f),
    Motif(MotifShape.FLOWER, 330f, 60f, 0.6f, 20f, Color.White), Motif(MotifShape.FLOWER, 60f, 400f, 0.75f, 0f, RaceCoral),
)

// ---------- Магазин сладостей ----------

/**
 * Магазин для «Списка покупок»: розовые обои с сердечками и звёздочками, коралловая
 * маркиза с фестонами и мягкой тенью, плинтус и медовый пол из досок. Полки с товарами
 * рисует сама игра ([ShopShelf]) — они стоят под товарами, где бы те ни оказались.
 */
@Composable
internal fun SweetShop() {
    DesignCanvas { h ->
        val b = h - DESIGN_H
        val wall = Rect(0f, 0f, DESIGN_W, 1720f + b)
        drawRect(ShopWall, wall.topLeft, wall.size)
        tiles(wall, 160f, ShopLayer1, ShopPattern)
        tiles(wall, 250f, ShopLayer2, ShopPattern, shift = Offset(55f, 90f))
        drawPath(AwningShadow, ShopShadow)
        clipPath(Awning) {
            drawRect(ShopAwningLight, size = Size(DESIGN_W, 330f))
            var x = 0f
            while (x < DESIGN_W) {
                drawRect(ShopAwning, Offset(x, 0f), Size(90f, 330f))
                x += 180f
            }
            drawRect(FinneyInk.copy(alpha = 0.10f), size = Size(DESIGN_W, 46f))
        }
        drawPath(Awning, FinneyInk, style = Stroke(7f, join = StrokeJoin.Round))
        outlinedRect(-10f, 1690f + b, 920f, 32f, ShopWood, 7f)
        planks(1722f + b, DESIGN_H + b, ShopShelfTop, ShopSeam)
    }
}

/** Маркиза: полосы до 250, дальше фестоны по 90 единиц глубиной до 308. */
private val Awning = svgPath(
    buildString {
        append("M-100,-10 L1000,-10 L1000,250 L900,250")
        var x = 900
        while (x > -90) {
            append(" Q${x - 45},308 ${x - 90},250")
            x -= 90
        }
        append(" L-100,250 Z")
    },
)

/** Тень под маркизой — те же фестоны, только ниже. */
private val AwningShadow = svgPath(
    buildString {
        append("M-90,250 L-90,262")
        var x = -90
        while (x < 990) {
            append(" Q${x + 45},332 ${x + 90},262")
            x += 90
        }
        append(" L990,250 Z")
    },
)

private val ShopWall = Color(0xFFFBE0E4)
private val ShopPattern = Color(0xFFF5CBD4)
private val ShopShadow = Color(0xFFF3C3CE)
private val ShopAwning = Color(0xFFFA987C)
private val ShopAwningLight = Color(0xFFFFEDCD)
internal val ShopShelfTop = Color(0xFFD9AE8A)
internal val ShopWood = Color(0xFFA0715A)
private val ShopSeam = Color(0xFFC99A76)

private val ShopLayer1 = listOf(
    Motif(MotifShape.HEART, 34f, 40f, 1.1f, -12f), Motif(MotifShape.SPARK, 118f, 34f, 0.75f),
    Motif(MotifShape.STAR, 84f, 112f, 0.8f, 14f), Motif(MotifShape.SPARK, 26f, 128f, 0.5f),
)
private val ShopLayer2 = listOf(
    Motif(MotifShape.STAR, 190f, 190f, 1f, -18f), Motif(MotifShape.HEART, 60f, 200f, 0.7f, 10f), Motif(MotifShape.SPARK, 200f, 60f, 0.9f),
)

/**
 * Полка магазина по макету: светлый край доски, тёмная передняя кромка и два
 * кронштейна под ней. Нарисована в единицах полки 852 (ширина сцены в макете):
 * [width] — ширина сцены в px. Кронштейны свисают ниже доски — их рисует тот, кто
 * знает, что под полкой есть место.
 */
internal fun DrawScope.shopShelf(width: Float) {
    val s = width / SHELF_W
    scale(s, s, Offset.Zero) {
        drawPath(ShelfBrackets, ShopWood)
        drawPath(ShelfBrackets, FinneyInk, style = Stroke(6f, join = StrokeJoin.Round))
        outlinedRect(0f, 0f, SHELF_W, 16f, ShopShelfTop, 6f, r = 4f)
        outlinedRect(0f, 16f, SHELF_W, 26f, ShopWood, 6f, r = 4f)
    }
}

/** Ширина полки в единицах макета: 852 из 900, по полю сцены. */
internal const val SHELF_W = 852f

/** Высота доски полки в единицах макета (без кронштейнов). */
internal const val SHELF_BOARD_H = 42f

private val ShelfBrackets = svgPath("M86,40 L132,40 L86,86 Z M766,40 L720,40 L766,86 Z")

// ---------- Касса с прилавком ----------

/**
 * Касса для «Чека»: та же мятная стена и сиреневый низ, что у «Сдачи», а между ними
 * прилавок как настоящий — столешница со светлым краем и передняя стенка с тремя
 * филёнками. Привязана к верху: кассир с репликой стоит над прилавком.
 * Прилавок выше, чем в макете ([DESK_TOP] против 832): иначе чек на четыре строки
 * уходил под кнопку «Проверить». Всё остальное сдвинуто вместе с ним.
 */
@Composable
internal fun CashDesk() {
    DesignCanvas { h ->
        val d = DESK_TOP - 832f
        val wall = Rect(0f, 0f, DESIGN_W, 860f + d)
        drawRect(DeskWall, wall.topLeft, wall.size)
        tiles(wall, 150f, DeskWallLayer1, DeskWallPattern)
        tiles(wall, 236f, DeskWallLayer2, DeskWallPattern, shift = Offset(61f, 37f))
        val low = Rect(0f, 1060f + d, DESIGN_W, h)
        drawRect(DeskLow, low.topLeft, low.size)
        tiles(low, 140f, DeskLowLayer1, DeskLowPattern)
        tiles(low, 220f, DeskLowLayer2, DeskLowPattern, shift = Offset(61f, 37f))
        outlinedRect(-10f, 864f + d, 920f, 198f, DeskFront, 7f)
        listOf(40f, 320f, 600f).forEach { x ->
            outlinedRect(x, 892f + d, 260f, 142f, DeskPanel, 5f, r = 20f)
            drawRoundRect(DeskPanelLight, Offset(x + 14f, 904f + d), Size(232f, 10f), CornerRadius(5f, 5f))
        }
        outlinedRect(-10f, DESK_TOP, 920f, 36f, DeskTop, 7f, r = 6f)
        drawRect(DeskTopLight, Offset(0f, 842f + d), Size(DESIGN_W, 8f))
    }
}

/** Верх столешницы и низ прилавка кассы на холсте макета: по ним «Чек» ставит кассира и пакет. */
internal const val DESK_TOP = 700f
internal const val DESK_BOTTOM = DESK_TOP + 230f

/** Сколько dp в единице холста макета на экране шириной [screenWidth]. */
internal fun designUnit(screenWidth: Dp): Dp = screenWidth / DESIGN_W

private val DeskWall = Color(0xFFD4EEDB)
private val DeskWallPattern = Color(0xFFBCE2C7)
private val DeskLow = Color(0xFFE7E1FB)
private val DeskLowPattern = Color(0xFFD7CDF7)
private val DeskFront = Color(0xFF9C6B53)
private val DeskPanel = Color(0xFFA9775E)
private val DeskPanelLight = Color(0xFFB88468)
private val DeskTop = Color(0xFFC08A6C)
private val DeskTopLight = Color(0xFFD4A385)

private val DeskWallLayer1 = listOf(
    Motif(MotifShape.STAR, 26f, 28f, 0.9f, -12f), Motif(MotifShape.SPARK, 104f, 24f, 0.65f), Motif(MotifShape.COIN, 74f, 84f, 0.95f),
    Motif(MotifShape.STAR, 124f, 114f, 0.6f, 18f), Motif(MotifShape.SPARK, 28f, 120f, 0.5f),
)
private val DeskWallLayer2 = listOf(
    Motif(MotifShape.SPARK, 170f, 180f, 0.85f), Motif(MotifShape.STAR, 52f, 196f, 0.7f, 30f),
    Motif(MotifShape.COIN, 196f, 56f, 0.7f), Motif(MotifShape.SPARK, 110f, 118f, 0.35f),
)
private val DeskLowLayer1 = listOf(
    Motif(MotifShape.SPARK, 22f, 26f, 0.75f), Motif(MotifShape.COIN, 100f, 36f, 0.85f), Motif(MotifShape.HEART, 66f, 92f, 0.75f, -10f),
    Motif(MotifShape.SPARK, 122f, 118f, 0.45f), Motif(MotifShape.STAR, 24f, 114f, 0.55f, 12f),
)
private val DeskLowLayer2 = listOf(
    Motif(MotifShape.STAR, 160f, 154f, 0.85f, -20f), Motif(MotifShape.HEART, 56f, 176f, 0.55f, 14f), Motif(MotifShape.SPARK, 170f, 40f, 0.6f),
)

// ---------- Комната трёх игр ----------

/** Цвета и узор комнаты одной игры: стена в цвет её карточки. */
internal enum class RoomLook(
    val wall: Color,
    val ink: Color,
    val wainscot: Color,
    val small: List<Motif>,
    val large: List<Motif>,
) {
    /** «Дела» — розовая стена с сердечками. */
    CHORES(
        Color(0xFFFBDDE2), Color(0xFFF5C6D0), Color(0xFFF6C9D2),
        listOf(
            Motif(MotifShape.HEART, 30f, 34f, 1.05f, -14f), Motif(MotifShape.SPARK, 112f, 30f, 0.7f),
            Motif(MotifShape.HEART, 86f, 108f, 0.7f, 12f), Motif(MotifShape.STAR, 26f, 124f, 0.55f, 8f),
        ),
        listOf(Motif(MotifShape.SPARK, 180f, 180f, 0.95f), Motif(MotifShape.HEART, 60f, 196f, 0.6f, -6f), Motif(MotifShape.STAR, 200f, 60f, 0.75f, 20f)),
    ),

    /** «Копилка» — голубая стена с каплями и тучками. */
    RESERVE(
        Color(0xFFDCE6F2), Color(0xFFC6D5EA), Color(0xFFC9D8EC),
        listOf(
            Motif(MotifShape.DROP, 30f, 36f, 0.95f, -8f), Motif(MotifShape.CLOUD, 112f, 34f, 1.1f),
            Motif(MotifShape.DROP, 84f, 108f, 0.7f, 10f), Motif(MotifShape.SPARK, 26f, 124f, 0.5f),
        ),
        listOf(Motif(MotifShape.CLOUD, 180f, 184f, 1.2f), Motif(MotifShape.DROP, 58f, 196f, 0.75f, -12f), Motif(MotifShape.DROP, 200f, 58f, 0.6f, 8f)),
    ),

    /** «Сортер» — кремовая стена со звёздами и монетками. */
    SORTER(
        Color(0xFFFFEDCD), Color(0xFFF8D8AB), Color(0xFFF9DCB0),
        listOf(
            Motif(MotifShape.STAR, 30f, 32f, 1f, -12f), Motif(MotifShape.COIN, 112f, 34f, 0.85f),
            Motif(MotifShape.SPARK, 80f, 108f, 0.75f), Motif(MotifShape.STAR, 24f, 122f, 0.5f, 14f),
        ),
        listOf(Motif(MotifShape.COIN, 180f, 180f, 0.9f), Motif(MotifShape.STAR, 60f, 196f, 0.75f, 20f), Motif(MotifShape.SPARK, 200f, 58f, 0.7f)),
    ),
}

/**
 * Комната для «Дел», «Копилки» и «Сортера»: стена в цвет карточки игры с узором
 * под её настроение, внизу панели с филёнками, светлый плинтус и пол из досок.
 * Окна нет: его почти целиком закрывала бы панель задания.
 */
@Composable
internal fun GameRoom(look: RoomLook) {
    DesignCanvas { h ->
        val b = h - DESIGN_H
        drawRect(look.wall, size = Size(DESIGN_W, 1720f + b))
        val wall = Rect(0f, 0f, DESIGN_W, 1600f + b)
        tiles(wall, 150f, look.small, look.ink)
        tiles(wall, 240f, look.large, look.ink, shift = Offset(55f, 80f))
        outlinedRect(-10f, 1574f + b, 920f, 120f, look.wainscot, 7f)
        repeat(6) { i -> outlinedRect(22f + i * 148f, 1596f + b, 118f, 76f, look.wall, 4f, r = 14f) }
        outlinedRect(-10f, 1690f + b, 920f, 32f, RoomBaseboard, 7f)
        planks(1722f + b, DESIGN_H + b, RoomFloor, RoomSeam)
    }
}

private val RoomBaseboard = Color(0xFFFFF7EA)
private val RoomFloor = Color(0xFFE4BE98)
private val RoomSeam = Color(0xFFD3A57E)

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()
