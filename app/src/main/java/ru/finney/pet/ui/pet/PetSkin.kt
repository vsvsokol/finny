package ru.finney.pet.ui.pet

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.TransformOrigin
import ru.finney.pet.R
import ru.finney.pet.domain.model.PetCharacter

// Всё, что отличает одного нарисованного питомца от другого: набор слоёв и точки
// вращения. Сама сборка и анимация общие — см. PetView и PetAnimation.
//
// Точки заданы долями стороны холста, поэтому не зависят от размера, в котором
// питомца рисуют на экране. Холсты у дизайнеров разные (2048 у Пушистика, 2200 у
// Рогатика), и делитель у каждого скина свой — числители те же пиксели, которые
// видно в редакторе.

/** Выражение лица. Слои различаются только областью лица, тело во всех одинаковое. */
enum class PetMood { HAPPY, SAD, DIRTY, SLEEP }

@Immutable
data class PetSkin(
    @DrawableRes val happy: Int,
    @DrawableRes val sad: Int,
    @DrawableRes val dirty: Int,
    @DrawableRes val sleep: Int,
    /** Закрытые глаза поверх открытых — по слою на каждое настроение, где глаза открыты. */
    @DrawableRes val blinkHappy: Int,
    @DrawableRes val blinkSad: Int,
    @DrawableRes val blinkDirty: Int,
    /** Открытый рот поверх любого настроения — для еды. Один на питомца. */
    @DrawableRes val mouth: Int,
    /** Туловище, руки и ноги в каждом облике роста: [0] — как нарисован, дальше крупнее (PetGrowth.kt). */
    val bodies: List<PetBody>,
    /**
     * Вокруг чего рука идёт вниз и внутрь. Плечо по правилу ниже (угол альфы) давало
     * во вздохе светлую щель у воротника — точки сдвинуты на 8–12 px слоя 512, по рендеру.
     */
    val leftHandPivot: TransformOrigin,
    val rightHandPivot: TransformOrigin,
    /**
     * Вокруг чего рука поворачивается вверх — глубже в рукаве, чем плечо. Вокруг плеча
     * поднятая рука отходила от туловища, а под ней туловище не нарисовано: открывался
     * белый клин. Подобрано перебором по рендеру всех обликов и окрасок до 25° — щелей
     * без контура не осталось (Limb в PetView).
     */
    val leftHandRaised: TransformOrigin,
    val rightHandRaised: TransformOrigin,
    val leftLegPivot: TransformOrigin,
    val rightLegPivot: TransformOrigin,
    /** Точка опоры: персонаж стоит на ногах, поэтому масштаб и наклон идут от ступней, а не от центра. */
    val ground: TransformOrigin,
    /** Низ головы посередине — доля стороны: по нему голова садится на подросшее туловище. */
    val neck: Float,
    /** Где по высоте лежат глаза — в этих пределах опускается веко. Доли стороны, как и точки. */
    val eyesTop: Float,
    val eyesBottom: Float,
    /** Центр рта: туда летит еда, от него растягивается открытый рот. */
    val mouthCenter: TransformOrigin,
    /** Куда садится шляпа. У каждого питомца своя голова — посадка подобрана по рендеру. */
    val hat: HatFit,
) {
    fun face(mood: PetMood): PetFace = when (mood) {
        PetMood.HAPPY -> PetFace(happy, blinkHappy)
        PetMood.SAD -> PetFace(sad, blinkSad)
        PetMood.DIRTY -> PetFace(dirty, blinkDirty)
        // Во сне глаза и так закрыты — моргать нечем.
        PetMood.SLEEP -> PetFace(sleep, blink = null)
    }
}

/**
 * Посадка шляпы на макушку — доли стороны холста питомца, как и остальные точки:
 * [centerX] — середина шляпы, [brim] — высота, на которой лежат поля, [width] — ширина шляпы.
 * К голове прикладывается линия полей, а не низ рисунка: у шляпы волшебника поля свисают,
 * и по низу рисунка она садилась криво. Где у каждой шляпы поля — [accessoryArt].
 */
@Immutable
data class HatFit(val centerX: Float, val brim: Float, val width: Float)

/** Туловище и конечности одного облика роста. Голова (базовый слой) у всех обликов одна. */
@Immutable
data class PetBody(
    @DrawableRes val torso: Int,
    @DrawableRes val leftHand: Int,
    @DrawableRes val rightHand: Int,
    @DrawableRes val leftLeg: Int,
    @DrawableRes val rightLeg: Int,
)

/** Базовый слой настроения и его моргание: меняются только вместе. */
@Immutable
data class PetFace(@DrawableRes val base: Int, @DrawableRes val blink: Int?)

private const val PUSHISTIK_CANVAS = 2048f

// Полосу глаз не снимают в редакторе: её печатает tools/split_pet_base.py, и уже
// в пикселях слоя в ресурсах, а не холста. Центр рта он печатает сразу долями.
private const val BLINK_LAYER = 512f

// Голова в слоях опущена на 4 px из 512 (tools/split_pet_base.py, HEAD_DROP): в старших
// обликах иначе между подбородком и туловищем виден зазор. Глаза, рот и шляпа — вслед за ней.
private const val HEAD_DROP = 4f / BLINK_LAYER
private const val ROGATIK_CANVAS = 2200f

// Звёздочка, Бантик и Лучик рисовались по шаблону Рогатика, поэтому холст у них тот же.
// Точки вращения не подбирались на глаз: бёдра — это верхний внутренний угол альфы
// слоя ноги, точка опоры — низ силуэта. Точки рук сдвинуты от такого угла перебором
// по рендеру — см. leftHandPivot и leftHandRaised.
private const val ZVEZDOCHKA_CANVAS = 2200f
private const val BANTIK_CANVAS = 2200f
private const val LUCHIK_CANVAS = 2200f

private val PushistikSkin = PetSkin(
    happy = R.drawable.pushistik_base_happy,
    sad = R.drawable.pushistik_base_sad,
    dirty = R.drawable.pushistik_base_dirty,
    sleep = R.drawable.pushistik_base_sleep,
    blinkHappy = R.drawable.pushistik_blink_happy,
    blinkSad = R.drawable.pushistik_blink_sad,
    blinkDirty = R.drawable.pushistik_blink_dirty,
    mouth = R.drawable.pushistik_mouth,
    bodies = listOf(
        PetBody(R.drawable.pushistik_torso, R.drawable.pushistik_left_hand, R.drawable.pushistik_right_hand, R.drawable.pushistik_left_leg, R.drawable.pushistik_right_leg),
        PetBody(R.drawable.pushistik_torso_2, R.drawable.pushistik_left_hand_2, R.drawable.pushistik_right_hand_2, R.drawable.pushistik_left_leg_2, R.drawable.pushistik_right_leg_2),
        PetBody(R.drawable.pushistik_torso_3, R.drawable.pushistik_left_hand_3, R.drawable.pushistik_right_hand_3, R.drawable.pushistik_left_leg_3, R.drawable.pushistik_right_leg_3),
    ),
    leftHandPivot = TransformOrigin(898f / PUSHISTIK_CANVAS, 1500f / PUSHISTIK_CANVAS),
    rightHandPivot = TransformOrigin(1162f / PUSHISTIK_CANVAS, 1500f / PUSHISTIK_CANVAS),
    leftHandRaised = TransformOrigin(946f / PUSHISTIK_CANVAS, 1628f / PUSHISTIK_CANVAS),
    rightHandRaised = TransformOrigin(1114f / PUSHISTIK_CANVAS, 1628f / PUSHISTIK_CANVAS),
    leftLegPivot = TransformOrigin(960f / PUSHISTIK_CANVAS, 1640f / PUSHISTIK_CANVAS),
    rightLegPivot = TransformOrigin(1100f / PUSHISTIK_CANVAS, 1640f / PUSHISTIK_CANVAS),
    ground = TransformOrigin(0.5f, 1828f / PUSHISTIK_CANVAS),
    // Не низ воротника (1690): он свисает до плеч, и в старших обликах руки прятались
    // под капюшоном. По плечам голова выше, шов под воротником чистый — проверено рендером.
    neck = 1560f / PUSHISTIK_CANVAS,
    eyesTop = 234f / BLINK_LAYER + HEAD_DROP,
    eyesBottom = 342f / BLINK_LAYER + HEAD_DROP,
    mouthCenter = TransformOrigin(0.5022f, 0.6619f + HEAD_DROP),
    hat = HatFit(centerX = 0.50f, brim = 0.23f + HEAD_DROP, width = 0.72f),
)

private val RogatikSkin = PetSkin(
    happy = R.drawable.rogatik_base_happy,
    sad = R.drawable.rogatik_base_sad,
    dirty = R.drawable.rogatik_base_dirty,
    sleep = R.drawable.rogatik_base_sleep,
    blinkHappy = R.drawable.rogatik_blink_happy,
    blinkSad = R.drawable.rogatik_blink_sad,
    blinkDirty = R.drawable.rogatik_blink_dirty,
    mouth = R.drawable.rogatik_mouth,
    bodies = listOf(
        PetBody(R.drawable.rogatik_torso, R.drawable.rogatik_left_hand, R.drawable.rogatik_right_hand, R.drawable.rogatik_left_leg, R.drawable.rogatik_right_leg),
        PetBody(R.drawable.rogatik_torso_2, R.drawable.rogatik_left_hand_2, R.drawable.rogatik_right_hand_2, R.drawable.rogatik_left_leg_2, R.drawable.rogatik_right_leg_2),
        PetBody(R.drawable.rogatik_torso_3, R.drawable.rogatik_left_hand_3, R.drawable.rogatik_right_hand_3, R.drawable.rogatik_left_leg_3, R.drawable.rogatik_right_leg_3),
    ),
    leftHandPivot = TransformOrigin(995f / ROGATIK_CANVAS, 1562f / ROGATIK_CANVAS),
    rightHandPivot = TransformOrigin(1222f / ROGATIK_CANVAS, 1562f / ROGATIK_CANVAS),
    leftHandRaised = TransformOrigin(1029f / ROGATIK_CANVAS, 1700f / ROGATIK_CANVAS),
    rightHandRaised = TransformOrigin(1188f / ROGATIK_CANVAS, 1700f / ROGATIK_CANVAS),
    leftLegPivot = TransformOrigin(1054f / ROGATIK_CANVAS, 1771f / ROGATIK_CANVAS),
    rightLegPivot = TransformOrigin(1168f / ROGATIK_CANVAS, 1771f / ROGATIK_CANVAS),
    ground = TransformOrigin(0.5f, 1898f / ROGATIK_CANVAS),
    neck = 1545f / ROGATIK_CANVAS,
    eyesTop = 223f / BLINK_LAYER + HEAD_DROP,
    eyesBottom = 337f / BLINK_LAYER + HEAD_DROP,
    mouthCenter = TransformOrigin(0.502f, 0.6395f + HEAD_DROP),
    hat = HatFit(centerX = 0.50f, brim = 0.24f + HEAD_DROP, width = 0.70f),
)

private val ZvezdochkaSkin = PetSkin(
    happy = R.drawable.zvezdochka_base_happy,
    sad = R.drawable.zvezdochka_base_sad,
    dirty = R.drawable.zvezdochka_base_dirty,
    sleep = R.drawable.zvezdochka_base_sleep,
    blinkHappy = R.drawable.zvezdochka_blink_happy,
    blinkSad = R.drawable.zvezdochka_blink_sad,
    blinkDirty = R.drawable.zvezdochka_blink_dirty,
    mouth = R.drawable.zvezdochka_mouth,
    bodies = listOf(
        PetBody(R.drawable.zvezdochka_torso, R.drawable.zvezdochka_left_hand, R.drawable.zvezdochka_right_hand, R.drawable.zvezdochka_left_leg, R.drawable.zvezdochka_right_leg),
        PetBody(R.drawable.zvezdochka_torso_2, R.drawable.zvezdochka_left_hand_2, R.drawable.zvezdochka_right_hand_2, R.drawable.zvezdochka_left_leg_2, R.drawable.zvezdochka_right_leg_2),
        PetBody(R.drawable.zvezdochka_torso_3, R.drawable.zvezdochka_left_hand_3, R.drawable.zvezdochka_right_hand_3, R.drawable.zvezdochka_left_leg_3, R.drawable.zvezdochka_right_leg_3),
    ),
    leftHandPivot = TransformOrigin(1021f / ZVEZDOCHKA_CANVAS, 1530f / ZVEZDOCHKA_CANVAS),
    rightHandPivot = TransformOrigin(1197f / ZVEZDOCHKA_CANVAS, 1530f / ZVEZDOCHKA_CANVAS),
    leftHandRaised = TransformOrigin(1021f / ZVEZDOCHKA_CANVAS, 1668f / ZVEZDOCHKA_CANVAS),
    rightHandRaised = TransformOrigin(1197f / ZVEZDOCHKA_CANVAS, 1668f / ZVEZDOCHKA_CANVAS),
    leftLegPivot = TransformOrigin(1060f / ZVEZDOCHKA_CANVAS, 1764f / ZVEZDOCHKA_CANVAS),
    rightLegPivot = TransformOrigin(1167f / ZVEZDOCHKA_CANVAS, 1765f / ZVEZDOCHKA_CANVAS),
    ground = TransformOrigin(0.5f, 1906f / ZVEZDOCHKA_CANVAS),
    neck = 1530f / ZVEZDOCHKA_CANVAS,
    eyesTop = 201f / BLINK_LAYER + HEAD_DROP,
    eyesBottom = 325f / BLINK_LAYER + HEAD_DROP,
    mouthCenter = TransformOrigin(0.5043f, 0.6307f + HEAD_DROP),
    hat = HatFit(centerX = 0.50f, brim = 0.23f + HEAD_DROP, width = 0.70f),
)

private val BantikSkin = PetSkin(
    happy = R.drawable.bantik_base_happy,
    sad = R.drawable.bantik_base_sad,
    dirty = R.drawable.bantik_base_dirty,
    sleep = R.drawable.bantik_base_sleep,
    blinkHappy = R.drawable.bantik_blink_happy,
    blinkSad = R.drawable.bantik_blink_sad,
    blinkDirty = R.drawable.bantik_blink_dirty,
    mouth = R.drawable.bantik_mouth,
    bodies = listOf(
        PetBody(R.drawable.bantik_torso, R.drawable.bantik_left_hand, R.drawable.bantik_right_hand, R.drawable.bantik_left_leg, R.drawable.bantik_right_leg),
        PetBody(R.drawable.bantik_torso_2, R.drawable.bantik_left_hand_2, R.drawable.bantik_right_hand_2, R.drawable.bantik_left_leg_2, R.drawable.bantik_right_leg_2),
        PetBody(R.drawable.bantik_torso_3, R.drawable.bantik_left_hand_3, R.drawable.bantik_right_hand_3, R.drawable.bantik_left_leg_3, R.drawable.bantik_right_leg_3),
    ),
    leftHandPivot = TransformOrigin(1061f / BANTIK_CANVAS, 1525f / BANTIK_CANVAS),
    rightHandPivot = TransformOrigin(1162f / BANTIK_CANVAS, 1524f / BANTIK_CANVAS),
    leftHandRaised = TransformOrigin(1044f / BANTIK_CANVAS, 1697f / BANTIK_CANVAS),
    rightHandRaised = TransformOrigin(1179f / BANTIK_CANVAS, 1696f / BANTIK_CANVAS),
    leftLegPivot = TransformOrigin(1060f / BANTIK_CANVAS, 1791f / BANTIK_CANVAS),
    rightLegPivot = TransformOrigin(1156f / BANTIK_CANVAS, 1789f / BANTIK_CANVAS),
    ground = TransformOrigin(0.5f, 1902f / BANTIK_CANVAS),
    neck = 1560f / BANTIK_CANVAS,
    eyesTop = 227f / BLINK_LAYER + HEAD_DROP,
    eyesBottom = 335f / BLINK_LAYER + HEAD_DROP,
    mouthCenter = TransformOrigin(0.5043f, 0.6516f + HEAD_DROP),
    hat = HatFit(centerX = 0.50f, brim = 0.25f + HEAD_DROP, width = 0.68f),
)

private val LuchikSkin = PetSkin(
    happy = R.drawable.luchik_base_happy,
    sad = R.drawable.luchik_base_sad,
    dirty = R.drawable.luchik_base_dirty,
    sleep = R.drawable.luchik_base_sleep,
    blinkHappy = R.drawable.luchik_blink_happy,
    blinkSad = R.drawable.luchik_blink_sad,
    blinkDirty = R.drawable.luchik_blink_dirty,
    mouth = R.drawable.luchik_mouth,
    bodies = listOf(
        PetBody(R.drawable.luchik_torso, R.drawable.luchik_left_hand, R.drawable.luchik_right_hand, R.drawable.luchik_left_leg, R.drawable.luchik_right_leg),
        PetBody(R.drawable.luchik_torso_2, R.drawable.luchik_left_hand_2, R.drawable.luchik_right_hand_2, R.drawable.luchik_left_leg_2, R.drawable.luchik_right_leg_2),
        PetBody(R.drawable.luchik_torso_3, R.drawable.luchik_left_hand_3, R.drawable.luchik_right_hand_3, R.drawable.luchik_left_leg_3, R.drawable.luchik_right_leg_3),
    ),
    leftHandPivot = TransformOrigin(1021f / LUCHIK_CANVAS, 1530f / LUCHIK_CANVAS),
    rightHandPivot = TransformOrigin(1182f / LUCHIK_CANVAS, 1530f / LUCHIK_CANVAS),
    leftHandRaised = TransformOrigin(1055f / LUCHIK_CANVAS, 1702f / LUCHIK_CANVAS),
    rightHandRaised = TransformOrigin(1148f / LUCHIK_CANVAS, 1702f / LUCHIK_CANVAS),
    leftLegPivot = TransformOrigin(1064f / LUCHIK_CANVAS, 1752f / LUCHIK_CANVAS),
    rightLegPivot = TransformOrigin(1158f / LUCHIK_CANVAS, 1754f / LUCHIK_CANVAS),
    ground = TransformOrigin(0.5f, 1913f / LUCHIK_CANVAS),
    neck = 1547f / LUCHIK_CANVAS,
    eyesTop = 215f / BLINK_LAYER + HEAD_DROP,
    eyesBottom = 330f / BLINK_LAYER + HEAD_DROP,
    mouthCenter = TransformOrigin(0.5036f, 0.638f + HEAD_DROP),
    hat = HatFit(centerX = 0.50f, brim = 0.27f + HEAD_DROP, width = 0.68f),
)

val PetCharacter.skin: PetSkin
    get() = when (this) {
        PetCharacter.PUSHISTIK -> PushistikSkin
        PetCharacter.ROGATIK -> RogatikSkin
        PetCharacter.ZVEZDOCHKA -> ZvezdochkaSkin
        PetCharacter.BANTIK -> BantikSkin
        PetCharacter.LUCHIK -> LuchikSkin
    }
