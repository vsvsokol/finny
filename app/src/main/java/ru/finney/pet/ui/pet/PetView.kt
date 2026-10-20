package ru.finney.pet.ui.pet

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import ru.finney.pet.domain.model.PetCharacter

// Питомец собирается из слоёв одного холста, поэтому детали совпадают по положению
// сами и координаты подбирать не нужно.
//
// Порядок отрисовки снизу вверх: ноги, туловище, руки, базовый слой (голова),
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
            // Ноги под туловищем: край рубашки ложится на штаны, и когда ноги расходятся
            // в прыжке, стык прикрыт. Поверх туловища между ними открывалась щель.
            Limb(body.leftLeg, skin.grownPivot(skin.leftLegPivot, look), { pose().leftLeg })
            Limb(body.rightLeg, skin.grownPivot(skin.rightLegPivot, look), { pose().rightLeg })
            Image(
                painter = painterResource(body.torso),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
            )
            // Наружу (вверх) левая рука поворачивается по часовой, правая — против.
            Limb(
                body.leftHand, skin.grownPivot(skin.leftHandPivot, look), { pose().leftHand },
                raised = skin.grownPivot(skin.leftHandRaised, look), outward = 1f,
            )
            Limb(
                body.rightHand, skin.grownPivot(skin.rightHandPivot, look), { pose().rightHand },
                raised = skin.grownPivot(skin.rightHandRaised, look), outward = -1f,
            )
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
 * Конечность поворачивается целиком вокруг [pivot] — плеча или бедра. Рука, идущая
 * вверх (наружу, знак [outward]), поворачивается вокруг [raised]: глубже в рукаве.
 * Вокруг нарисованного плеча поднятая рука отходила от туловища, и в стыке открывался
 * клин фона — туловище под рукой не нарисовано. Вниз и внутрь рука идёт вокруг плеча:
 * там глубокая точка, наоборот, выворачивала верх рукава наружу. В нуле обе точки дают
 * одну и ту же картинку, так что смена на покачивании не видна.
 *
 * Поворот читается на отрисовке, как и поза в [PetView].
 */
@Composable
private fun BoxScope.Limb(
    @DrawableRes res: Int,
    pivot: TransformOrigin,
    rotation: () -> Float,
    raised: TransformOrigin = pivot,
    outward: Float = 0f,
) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                val angle = rotation()
                transformOrigin = if (angle * outward > 0f) raised else pivot
                rotationZ = angle
            },
    )
}
