package ru.finney.pet.ui.room

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Комната в объёме — ровно настолько, чтобы положить тень мебели на пол.
//
// Стол и ванна нарисованы объёмными: столешница на ножках, чаша с бортом.
// Их тень — не приплюснутая картинка, а то, что заслоняет свет по пути к полу:
// у стола это вся столешница со скатертью, у ванны — вся чаша. Поэтому у
// мебели есть простая модель в объёме, и тень строится лучами от источника.
//
// Камера вычисляется по полу, который уже нарисован. Доски сходятся к точке
// на оси комнаты, и глубина любой точки пола читается по её высоте на холсте.
// Высота предмета — по тому, насколько его верх выше точки пола под ним.

/**
 * Точка в объёме комнаты.
 *
 * [x] — вбок от оси комнаты, [y] — высота над полом, обе в долях высоты холста
 * в масштабе стены. [z] — глубина: 1 у стены, меньше — ближе к зрителю. На
 * холсте всё, что на глубине z, уменьшено в z раз, и точка находится без камеры.
 */
@Immutable
internal data class Point3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Point3) = Point3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Point3) = Point3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = Point3(x * k, y * k, z * k)
}

internal object RoomSpace {
    /**
     * Точка схода досок пола — она же высота глаза. По наклону двух соседних
     * с осью досок выходит на 0.0765 высоты холста.
     */
    const val VANISH_Y = 0.0765f

    /** Высота глаза над полом у стены: от шва стены и пола до точки схода. */
    private const val EYE = Room.FLOOR_TOP - VANISH_Y

    /** Где точка видна на холсте, в долях холста. */
    fun project(p: Point3): Offset {
        val k = 1f / p.z
        return Offset(
            x = Room.ROOM_AXIS_X + p.x * k / Room.CANVAS_RATIO,
            y = VANISH_Y + (EYE - p.y) * k,
        )
    }

    /** Точка на высоте [height], которую на холсте видно в [at]. */
    fun lift(at: Offset, height: Float): Point3 {
        val k = (at.y - VANISH_Y) / (EYE - height)
        return Point3((at.x - Room.ROOM_AXIS_X) * Room.CANVAS_RATIO / k, height, 1f / k)
    }

    /** Точка на глубине [z], которую на холсте видно в [at]. */
    fun behind(at: Offset, z: Float): Point3 = Point3(
        x = (at.x - Room.ROOM_AXIS_X) * Room.CANVAS_RATIO * z,
        y = EYE - (at.y - VANISH_Y) * z,
        z = z,
    )

    /** Глубина, на которой стоит точка пола, видимая на высоте холста [floorY]. */
    fun scaleAt(floorY: Float): Float = (floorY - VANISH_Y) / EYE
}

/** Откуда идёт свет, отбрасывающий тень: куда падает на пол каждая точка. */
internal sealed interface Caster {
    /** Куда на пол падает тень точки [p]; null — точка выше источника и тени не даёт. */
    fun toFloor(p: Point3): Point3?

    /** Лампа, НЛО: лучи расходятся из одной точки, и тень тем длиннее, чем она ближе. */
    data class Point(val at: Point3) : Caster {
        override fun toFloor(p: Point3): Point3? {
            if (p.y >= at.y) return null
            return at + (p - at) * (at.y / (at.y - p.y))
        }
    }

    /**
     * Луна: так далеко, что лучи параллельны. [ray] — куда смещается тень
     * на единицу высоты, его y равен −1.
     */
    data class Parallel(val ray: Point3) : Caster {
        override fun toFloor(p: Point3): Point3 = p + ray * p.y
    }
}

/**
 * Мебель в объёме: несколько выпуклых кусков, каждый — набор угловых точек.
 * Тень куска — выпуклая оболочка его углов, упавших на пол; тени кусков
 * складываются.
 */
@Immutable
internal class Solid(val parts: List<List<Point3>>)

/**
 * Модели мебели. Числа сняты по экспортам `dinner_table.PNG`, `bath.PNG` и `sleep_capsule.PNG`
 * (холст 1440×2400) по сетке с шагом 50 px.
 */
internal object Solids {

    /**
     * Стол: коробка от столешницы до низа скатерти, скатерть свисает со всех
     * сторон, и под ней четыре ножки.
     *
     * Высота столешницы — по передней кромке (1600) над ступнями передних
     * ножек (1930): на этой глубине стол выше пола на столько, насколько
     * кромка выше ступней. Задняя кромка (1440) из этой же высоты даёт глубину
     * стола. Низ скатерти спереди — 1830.
     */
    val Table: Solid = run {
        val feet = 1930f / 2400f
        val k = RoomSpace.scaleAt(feet)
        val top = (feet - 1600f / 2400f) / k
        val cloth = (feet - 1830f / 2400f) / k

        val corners = listOf(
            Offset(225f / 1440f, 1440f / 2400f),
            Offset(1030f / 1440f, 1440f / 2400f),
            Offset(1105f / 1440f, 1600f / 2400f),
            Offset(145f / 1440f, 1600f / 2400f),
        ).map { RoomSpace.lift(it, top) }

        // Ножки чуть внутри углов, толщиной как нарисованы спереди: 55 px.
        val middle = corners.reduce { a, b -> a + b } * (1f / corners.size)
        val half = 27f / 1440f * Room.CANVAS_RATIO / k
        val legs = corners.map { corner ->
            val at = corner + (middle - corner) * 0.06f
            listOf(
                Point3(at.x - half, 0f, at.z),
                Point3(at.x + half, 0f, at.z),
                Point3(at.x - half, cloth, at.z),
                Point3(at.x + half, cloth, at.z),
            )
        }
        val box = corners + corners.map { it.copy(y = cloth) }
        Solid(listOf(box) + legs)
    }

    /**
     * Ванна: овал борта и овал дна чаши, между ними чаша.
     *
     * Ножки встают на пол на 2010, передний борт посередине — 1590, дно
     * чаши — 1960, край к краю борт от 145 до 1075, дно от 360 до 870.
     * Вся модель сдвинута вместе с картинкой: вправо на [Room.BATH_SHIFT], вверх на [Room.BATH_LIFT].
     * Глубины ванны на картинке нет — она видна только спереди, поэтому
     * взята как у настоящей: вдвое меньше длины.
     */
    val Bath: Solid = run {
        val feet = 2010f / 2400f - Room.BATH_LIFT
        val k = RoomSpace.scaleAt(feet)
        val front = 1f / k
        val rim = (2010f - 1590f) / 2400f / k
        val bottom = (2010f - 1960f) / 2400f / k

        val centreX = (610f / 1440f + Room.BATH_SHIFT - Room.ROOM_AXIS_X) * Room.CANVAS_RATIO / k
        val rimLength = 465f / 1440f * Room.CANVAS_RATIO / k
        val bottomLength = 255f / 1440f * Room.CANVAS_RATIO / k
        // Глубина в долях z: половина длины, пересчитанная по расстоянию до
        // стены, которое у такой камеры около полутора ширин холста.
        val rimDepth = rimLength / (1.5f * Room.CANVAS_RATIO) / 2f
        val bottomDepth = rimDepth * 0.55f

        fun ring(length: Float, depth: Float, height: Float): List<Point3> =
            (0 until 16).map { i ->
                val a = 2f * PI.toFloat() * i / 16
                Point3(centreX + length * cos(a), height, front + depth + depth * sin(a))
            }

        Solid(listOf(ring(rimLength, rimDepth, rim) + ring(bottomLength, bottomDepth, bottom)))
    }

    /**
     * Капсула для сна: цилиндр от основания до крышки.
     *
     * Строится по месту капсулы в углу ([Room.Capsule]), а не по экспорту: там
     * она стояла на весь зал. В экспорте основание шириной во всю капсулу,
     * крышка — 720 из 1006 px, середина — на 707 из 1440 (почти посередине).
     * Глубина, как у ванны, — половина ширины.
     */
    val Capsule: Solid = run {
        val rect = Room.Capsule.rect
        val feet = rect.bottom
        val k = RoomSpace.scaleAt(feet)
        val front = 1f / k
        val top = (feet - rect.top) / k

        val centreX = ((rect.left + rect.right) / 2f - Room.ROOM_AXIS_X) * Room.CANVAS_RATIO / k
        val baseRadius = rect.width / 2f * Room.CANVAS_RATIO / k
        val topRadius = baseRadius * 720f / 1006f
        val depth = baseRadius / (1.5f * Room.CANVAS_RATIO) / 2f

        fun ring(radius: Float, height: Float): List<Point3> =
            (0 until 16).map { i ->
                val a = 2f * PI.toFloat() * i / 16
                Point3(centreX + radius * cos(a), height, front + depth + depth * radius / baseRadius * sin(a))
            }

        Solid(listOf(ring(baseRadius, 0f) + ring(topRadius, top)))
    }
}

/** Выпуклая оболочка точек, обход по часовой стрелке. Алгоритм Эндрю. */
internal fun convexHull(points: List<Offset>): List<Offset> {
    if (points.size < 3) return points
    val sorted = points.sortedWith(compareBy<Offset> { it.x }.thenBy { it.y })
    fun cross(o: Offset, a: Offset, b: Offset) =
        (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

    val hull = ArrayList<Offset>(sorted.size * 2)
    for (p in sorted) {
        while (hull.size >= 2 && cross(hull[hull.size - 2], hull[hull.size - 1], p) <= 0f) hull.removeAt(hull.size - 1)
        hull.add(p)
    }
    val lower = hull.size + 1
    for (i in sorted.size - 2 downTo 0) {
        val p = sorted[i]
        while (hull.size >= lower && cross(hull[hull.size - 2], hull[hull.size - 1], p) <= 0f) hull.removeAt(hull.size - 1)
        hull.add(p)
    }
    hull.removeAt(hull.size - 1)
    return hull
}
