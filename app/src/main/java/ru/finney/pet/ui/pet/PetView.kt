package ru.finney.pet.ui.pet

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import ru.finney.pet.domain.model.PetCharacter
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Питомец собирается из слоёв одного холста, поэтому детали совпадают по положению
// сами и координаты подбирать не нужно.
//
// Порядок отрисовки снизу вверх: ноги, руки, базовый слой (голова и туловище),
// слой моргания, открытый рот. Моргание и рот собирает из состояний сна тот же скрипт.
// В базовом слое конечности вырезаны из состояния лица — см. tools/split_pet_base.py.
// Без выреза под повёрнутой рукой видно исходную; вырез обязан быть точным по краю,
// иначе по контуру рук и ног идёт светлый шов.
//
// Чем один питомец отличается от другого — только набором слоёв и точками
// вращения: всё это в PetSkin, а сборка и анимация общие.
//
// Стадии роста — масштаб всего питомца от ступней, см. PetGrowth.kt.

/** Мгновенное положение всех частей. Анимации только заполняют эту структуру. */
data class PetPose(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val offsetY: Dp = 0.dp,
    val tilt: Float = 0f,
    val leftHand: Float = 0f,
    val rightHand: Float = 0f,
    val leftLeg: Float = 0f,
    val rightLeg: Float = 0f,
    /** Веко: 0 — глаза открыты, 1 — закрыты. */
    val lid: Float = 0f,
    /** Рот: 0 — как у настроения, 1 — открыт навстречу еде. */
    val mouth: Float = 0f,
    /** Наклон головы от шеи, в градусах. Туловище при этом стоит. */
    val headTilt: Float = 0f,
)

/**
 * Поза передаётся лямбдой, а не значением, и это принципиально.
 *
 * Значением её читал бы сам `PetView`, а он меняется каждый кадр анимации — значит
 * каждый кадр пересобирался бы весь состав, вместе с `painterResource` на пять слоёв.
 * Лямбда же вызывается внутри `graphicsLayer`, то есть на отрисовке: слои достаются
 * из ресурсов один раз, а кадры анимации только двигают уже готовое.
 *
 * Разница измеренная: на экране внешности два питомца разом давали 200 мс на кадр
 * (8 fps), после перехода на лямбду — обычные 60.
 */
@Composable
fun PetView(
    character: PetCharacter,
    mood: PetMood,
    pose: () -> PetPose,
    modifier: Modifier = Modifier,
    /** Надетое — id из магазина: шляпа и очки. Двигается вместе с питомцем: лежит внутри позы. */
    accessories: List<String> = emptyList(),
    /** Стадия роста = уровень: с ней растут туловище, руки и ноги ([lookFor]). Без стадии — как нарисован. */
    stage: Int = 1,
    /**
     * Окраска — id облика из ядра (`PetLooks`, `look_pushistik_1`); null — родной цвет рисунка.
     */
    coloring: String? = null,
    /**
     * Только голова, без туловища, рук и ног: в мини-играх питомец — голова-смайлик
     * с реакциями (`ui/tasks/games/PetHead.kt`). Наклон и масштаб тогда идут от шеи, а не от ступней.
     */
    headOnly: Boolean = false,
) {
    val painted = coloring?.let { lookSkin(it, character.skin) }
    val skin = painted ?: character.skin
    val look = lookFor(stage)
    val body = skin.bodies[look - 1]
    val lift = if (headOnly) 0f else skin.headLift(stage)
    val pivot = if (headOnly) TransformOrigin(0.5f, skin.neck) else skin.ground
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                val current = pose()
                scaleX = current.scaleX
                scaleY = current.scaleY
                translationY = current.offsetY.toPx()
                rotationZ = current.tilt
                transformOrigin = pivot
            },
    ) {
        if (!headOnly) {
            Image(
                painter = painterResource(body.torso),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
            )
            Limb(body.leftLeg, skin.grownPivot(skin.leftLegPivot, look)) { pose().leftLeg }
            Limb(body.rightLeg, skin.grownPivot(skin.rightLegPivot, look)) { pose().rightLeg }
            Limb(body.leftHand, skin.grownPivot(skin.leftHandPivot, look)) { pose().leftHand }
            Limb(body.rightHand, skin.grownPivot(skin.rightHandPivot, look)) { pose().rightHand }
        }

        // Голова с лицом и тем, что на ней надето, — поднята на подросшее туловище.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationY = -size.height * lift
                    transformOrigin = TransformOrigin(0.5f, skin.neck)
                    rotationZ = pose().headTilt
                }
                .then(if (headOnly) Modifier.withoutNeck() else Modifier),
        ) {

        Crossfade(
            targetState = skin.face(mood),
            animationSpec = tween(durationMillis = 280),
            label = "mood",
            modifier = Modifier.matchParentSize(),
        ) { current ->
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(current.base),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
                current.blink?.let { blink ->
                    Image(
                        painter = painterResource(blink),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .eyelid(skin) { pose().lid },
                    )
                }
            }
        }

        // Во сне рот и так приоткрыт, а есть питомец во сне не будет.
        if (mood != PetMood.SLEEP) {
            Image(
                painter = painterResource(skin.mouth),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val open = pose().mouth
                        alpha = if (open > MOUTH_SHOWN) 1f else 0f
                        transformOrigin = skin.mouthCenter
                        scaleX = 1f + open * MOUTH_STRETCH_X
                        scaleY = 1f + open * MOUTH_STRETCH_Y
                    },
            )
        }

        // Очки раньше шляпы: поля шляпы и листья ананаса ложатся поверх оправы, а не под неё.
        accessories.mapNotNull(::accessoryArt).sortedBy { !it.onEyes }.forEach { art ->
            Image(
                painter = painterResource(art.res),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = if (art.onEyes) Modifier.glassesPlacement(skin, art) else Modifier.hatPlacement(skin.hat, art.brim),
            )
        }
        }
    }
}

// Открытый рот — это рот из сна, и меньше него он не бывает: сжатый слой перестал бы
// закрывать улыбку под собой. Растягивается он умеренно: у Пушистика до линии
// подбородка от края слоя всего пикселей пять, дальше кожа рта легла бы на обводку.
private const val MOUTH_SHOWN = 0.02f
private const val MOUTH_STRETCH_X = 0.12f
private const val MOUTH_STRETCH_Y = 0.3f

/**
 * Без туловища под подбородком видны обрывки контура шеи и складки одежды: у Пушистика
 * (и его окрасок) они нарисованы в базовом слое поверх туловища, см. tools/split_pet_base.py.
 * В режиме «только голова» эта полоса не рисуется. У остальных питомцев там пусто —
 * проверено по всем слоям лица, так что вырез общий.
 */
private fun Modifier.withoutNeck(): Modifier = drawWithContent {
    clipRect(
        left = size.width * NECK_CUT_LEFT,
        top = size.height * NECK_CUT_TOP,
        right = size.width * NECK_CUT_RIGHT,
        bottom = size.height,
        clipOp = ClipOp.Difference,
    ) { this@drawWithContent.drawContent() }
}

// Полоса туловища под подбородком: ниже лица Пушистика (0.758) и в ширину туловища с запасом.
private const val NECK_CUT_TOP = 0.762f
private const val NECK_CUT_LEFT = 0.43f
private const val NECK_CUT_RIGHT = 0.57f

// Мягкость края века в долях стороны. Жёсткий край на полпути выглядит разрезом
// глаза, а не веком.
private const val LID_FEATHER = 0.025f

/**
 * Веко: слой закрытых глаз проявляется сверху вниз, будто на глаз опускается кожа.
 * Мгновенная подмена открытых глаз закрытыми читалась как мигание лампочки.
 *
 * Всё, что ниже края века, из слоя стирается. Стирание (DstOut) должно задевать
 * только этот слой, поэтому он рисуется отдельно — но лишь пока веко опущено:
 * всё остальное время отдельный слой только занимал бы память.
 * Как и поза, веко читается на отрисовке, а не в композиции.
 */
private fun Modifier.eyelid(skin: PetSkin, lid: () -> Float): Modifier = this
    .graphicsLayer {
        val closing = lid() > 0f
        alpha = if (closing) 1f else 0f
        compositingStrategy = if (closing) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
    .drawWithCache {
        val feather = size.height * LID_FEATHER
        val top = size.height * skin.eyesTop - feather
        val bottom = size.height * skin.eyesBottom
        val edge = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, endY = feather)
        onDrawWithContent {
            val closed = lid()
            if (closed <= 0f) return@onDrawWithContent
            drawContent()
            if (closed >= 1f) return@onDrawWithContent
            val y = lerp(top, bottom, closed)
            translate(top = y) {
                drawRect(edge, size = Size(size.width, feather), blendMode = BlendMode.DstOut)
            }
            drawRect(
                Color.Black,
                topLeft = Offset(0f, y + feather),
                size = Size(size.width, size.height - y - feather),
                blendMode = BlendMode.DstOut,
            )
        }
    }

/**
 * Конечность не поворачивается целиком, а сгибается: у плеча (бедра) она стоит на месте,
 * дальше угол нарастает до полного к кончику. Жёсткий поворот вокруг точки отводил край
 * среза от туловища, и в стыке открывался клин фона — вырез у плеча не дуга вокруг точки.
 *
 * Поворот читается на отрисовке, как и поза в [PetView].
 */
@Composable
private fun BoxScope.Limb(
    @DrawableRes res: Int,
    pivot: TransformOrigin,
    rotation: () -> Float,
) {
    val image = ImageBitmap.imageResource(res)
    val mesh = remember(image, pivot) { LimbMesh(image.asAndroidBitmap(), pivot) }
    Spacer(
        modifier = Modifier
            .matchParentSize()
            .drawBehind {
                drawIntoCanvas { mesh.draw(it.nativeCanvas, size.width / mesh.side, rotation()) }
            },
    )
}

// Сгиб начинается на этой доле длины конечности (от точки вращения до дальнего пикселя)
// и доходит до полного угла к её концу. Подобрано по рендеру всех пяти питомцев и их окрасок
// в первом и третьем облике, руки вверх и вниз: щелей в стыках в несколько раз меньше, чем
// при жёстком повороте. Раньше начало сгиба — щель возвращается, позже — сгиб слишком резкий.
private const val BEND_FROM = 0.4f
private const val BEND_TO = 1f

// Клеток сетки на сторону. Конечность в слое — около 50 px, клетка выходит в 4–5 px:
// мельче сгиб не отличить на глаз.
private const val MESH_CELLS = 12

/** Сетка поверх одной конечности. Слой обрезан до её пикселей, чтобы клетки не тратились на пустоту. */
private class LimbMesh(layer: Bitmap, pivot: TransformOrigin) {
    val side = layer.width.toFloat()
    private val bitmap: Bitmap
    private val px = pivot.pivotFractionX * layer.width
    private val py = pivot.pivotFractionY * layer.height
    private val rest = FloatArray((MESH_CELLS + 1) * (MESH_CELLS + 1) * 2)
    private val weight = FloatArray(rest.size / 2)
    private val verts = FloatArray(rest.size)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    init {
        val w = layer.width
        val h = layer.height
        val pixels = IntArray(w * h).also { layer.getPixels(it, 0, w, 0, 0, w, h) }
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        var reach = 0f
        for (y in 0 until h) for (x in 0 until w) {
            if (pixels[y * w + x] ushr 24 == 0) continue
            left = min(left, x); top = min(top, y); right = max(right, x); bottom = max(bottom, y)
            reach = max(reach, hypot(x - px, y - py))
        }
        // Пиксель запаса по краю: без него билинейная выборка срезала бы сглаженный контур.
        left = max(left - 1, 0); top = max(top - 1, 0)
        right = min(right + 1, w - 1); bottom = min(bottom + 1, h - 1)
        bitmap = Bitmap.createBitmap(layer, left, top, right - left + 1, bottom - top + 1)

        for (j in 0..MESH_CELLS) for (i in 0..MESH_CELLS) {
            val k = j * (MESH_CELLS + 1) + i
            val x = left + bitmap.width * i / MESH_CELLS.toFloat()
            val y = top + bitmap.height * j / MESH_CELLS.toFloat()
            rest[2 * k] = x
            rest[2 * k + 1] = y
            val t = ((hypot(x - px, y - py) / reach - BEND_FROM) / (BEND_TO - BEND_FROM)).coerceIn(0f, 1f)
            weight[k] = t * t * (3f - 2f * t)
        }
    }

    /** [degrees] — как rotationZ: положительный угол по часовой. */
    fun draw(canvas: android.graphics.Canvas, scale: Float, degrees: Float) {
        val angle = Math.toRadians(degrees.toDouble()).toFloat()
        for (k in weight.indices) {
            val dx = rest[2 * k] - px
            val dy = rest[2 * k + 1] - py
            val a = angle * weight[k]
            val c = cos(a)
            val s = sin(a)
            verts[2 * k] = px + dx * c - dy * s
            verts[2 * k + 1] = py + dx * s + dy * c
        }
        canvas.save()
        canvas.scale(scale, scale)
        canvas.drawBitmapMesh(bitmap, MESH_CELLS, MESH_CELLS, verts, 0, null, 0, paint)
        canvas.restore()
    }
}
