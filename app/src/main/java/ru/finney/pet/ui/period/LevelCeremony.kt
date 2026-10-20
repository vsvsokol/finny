package ru.finney.pet.ui.period

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import ru.finney.pet.domain.model.PetCharacter
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.finney.pet.R
import ru.finney.pet.ui.components.Coin
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.FinneyQuietButton
import ru.finney.pet.ui.components.LevelBadge
import ru.finney.pet.ui.components.MenuBackdrop
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.components.menuBackdrop
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.motion.motionEnabled
import ru.finney.pet.ui.pet.PetMood
import ru.finney.pet.ui.pet.PetView
import ru.finney.pet.ui.pet.lookFor
import ru.finney.pet.ui.pet.rememberPetAnimation
import ru.finney.pet.ui.pet.rememberPoseProvider
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.RadiusCard
import ru.finney.pet.ui.theme.StrokeBold
import ru.finney.pet.ui.theme.StrokeRegular

// Конец уровня — праздник по тактам, каждый на весь экран: звёзды, рост или звание,
// что открылось, новые монеты. Плейтест и разбор 20.10: прежний итог был отчётом из
// трёх прокруток, и ребёнок не понимал, что здесь главное. Подробности (банки, деньги)
// остались — по «Подробнее» и в «Моём пути».
//
// Каждый такт сначала играет, потом показывает кнопку; касание по экрану не нужно —
// кнопка «Дальше» внизу всегда на одном месте. Без анимаций (ТЗ п. 3.6) — сразу конечный кадр.

/** Такт церемонии. Какие из них показывать, решает [ceremonyBeats]. */
internal enum class Beat { STARS, GROW, RANK, NEW, COINS }

/**
 * Такты для этого итога: рост — только когда меняется облик (на 2-м и 6-м уровне),
 * звание — когда облик тот же, а стадия выросла. «Новое» — если что-то открылось.
 */
internal fun ceremonyBeats(state: PeriodResultUiState.Ready): List<Beat> = buildList {
    add(Beat.STARS)
    if (state.stageAfter > state.stageBefore && state.character != null) {
        add(if (lookFor(state.stageAfter) != lookFor(state.stageBefore)) Beat.GROW else Beat.RANK)
    }
    if (state.newThings.isNotEmpty()) add(Beat.NEW)
    if (state.income > 0) add(Beat.COINS)
}

@Composable
internal fun LevelCeremony(
    state: PeriodResultUiState.Ready,
    onFinish: () -> Unit,
    onDetails: () -> Unit,
) {
    val beats = remember(state.periodNumber) { ceremonyBeats(state) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val last = index >= beats.lastIndex
    val next: () -> Unit = { if (last) onFinish() else index += 1 }
    val finishLabel = if (state.canPlanNext) "Составить план" else "На главный"
    Crossfade(targetState = beats[index.coerceAtMost(beats.lastIndex)], label = "Такт итога") { beat ->
        val nextLabel = if (beat == beats.last()) finishLabel else "Дальше"
        when (beat) {
            Beat.STARS -> StarsBeat(state, nextLabel, next, onDetails)
            Beat.GROW -> GrowBeat(state, nextLabel = if (beat == beats.last()) nextLabel else "Ура!", onNext = next)
            Beat.RANK -> RankBeat(state, nextLabel = nextLabel, onNext = next)
            Beat.NEW -> NewBeat(state, nextLabel, next)
            Beat.COINS -> CoinsBeat(state, nextLabel, next)
        }
    }
}

// ---------- Такт 1: звёзды ----------

/** Условие уровня в такте звёзд: подпись, картинка и почему звезды нет — коротко. */
private class StarTile(val label: String, val done: Boolean, val why: String, val art: @Composable () -> Unit)

@Composable
private fun StarsBeat(state: PeriodResultUiState.Ready, nextLabel: String, onNext: () -> Unit, onDetails: () -> Unit) {
    val tiles = listOf(
        StarTile("Уход", state.needsCovered, "не сыт или не чист") { TileImage(R.drawable.ic_level_care) },
        StarTile("По плану", state.planMatched, if (state.overspend > 0) "вышел за банку" else "отложил меньше") { Coin(size = 44.dp) },
        StarTile("Копилка", state.savingsAdded, "пусто") { TileImage(R.drawable.ic_level_piggy) },
    )
    val sounds = LocalSounds.current
    val animation = rememberPetAnimation()
    val lit = remember { tiles.map { Animatable(0f) } }
    val flip = remember { Animatable(1f) }
    var shownLevel by remember { mutableIntStateOf(state.playedLevel) }
    LaunchedEffect(Unit) {
        val animate = motionEnabled()
        if (!animate) {
            tiles.forEachIndexed { i, t -> if (t.done) lit[i].snapTo(1f) }
            shownLevel = state.level
            return@LaunchedEffect
        }
        delay(600)
        var n = 0
        tiles.forEachIndexed { i, t ->
            if (!t.done) return@forEachIndexed
            sounds.play(Sfx.Coin, 1f + 0.12f * n++)
            lit[i].animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
            delay(160)
        }
        if (state.leveledUp) {
            flip.animateTo(0f, tween(160))
            shownLevel = state.level
            flip.animateTo(1f, tween(200))
            sounds.play(Sfx.LevelUp)
        }
        animation.playJoy()
    }

    Box(Modifier.fillMaxSize().background(FinneyCream)) {
        Sunburst(rays = FinneyYellow.copy(alpha = 0.55f), ground = FinneyCream, centerY = 0.40f)
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LevelBadge(shownLevel, size = 80.dp, modifier = Modifier.graphicsLayer { scaleX = flip.value })
            OutlinedText(
                text = when {
                    state.passed -> "Уровень ${state.playedLevel} пройден!"
                    else -> "Почти!"
                },
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                state.character?.let { character ->
                    PetView(
                        character = character,
                        mood = PetMood.HAPPY,
                        pose = rememberPoseProvider(animation),
                        accessories = state.worn,
                        stage = state.stageBefore,
                        coloring = state.coloring,
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                    )
                }
            }
            state.levelGame?.let { game -> GameChip(game, state.gamePassed) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.forEachIndexed { i, t -> StarTileView(t, lit = { lit[i].value }, Modifier.weight(1f)) }
            }
            if (!state.passed) {
                Text(
                    "Нужно ${state.toPass} из 3 звёзд. Монеты нового уровня всё равно придут.",
                    style = MaterialTheme.typography.bodyLarge, color = FinneyInk, textAlign = TextAlign.Center,
                )
            }
            FinneyButton(text = nextLabel, onClick = onNext)
            FinneyQuietButton(text = "Подробнее", onClick = onDetails, modifier = Modifier.fillMaxWidth(0.7f))
        }
    }
}

/** Плитка условия: звезда на кромке выпрыгивает, когда до неё дошёл счёт. Не цветом единым (ТЗ п. 3.6): «✓» или причина словами. */
@Composable
private fun StarTileView(tile: StarTile, lit: () -> Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "${tile.label}: " + if (tile.done) "звезда есть" else "звезды нет, ${tile.why}"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            Box(
                modifier = Modifier
                    .padding(top = 22.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(RadiusCard))
                    .background(if (tile.done) FinneyGreen else Color.White)
                    .border(StrokeBold, FinneyInk, RoundedCornerShape(RadiusCard)),
                contentAlignment = Alignment.Center,
            ) { tile.art() }
            Star(
                filled = tile.done,
                modifier = Modifier.size(46.dp).graphicsLayer {
                    val v = if (tile.done) lit() else 1f
                    scaleX = 0.3f + 0.7f * v
                    scaleY = 0.3f + 0.7f * v
                    alpha = if (tile.done) v.coerceIn(0f, 1f) else 1f
                },
            )
        }
        Text(tile.label, style = MaterialTheme.typography.bodyLarge, color = FinneyInk, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        Text(if (tile.done) "✓" else tile.why, style = MaterialTheme.typography.bodyMedium, color = FinneyInk, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TileImage(res: Int) {
    Image(painterResource(res), contentDescription = null, modifier = Modifier.size(52.dp))
}

/** Игра уровня одной плашкой: название и «✓», если пройдена. */
@Composable
private fun GameChip(title: String, passed: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .border(StrokeRegular, FinneyInk, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Игра «$title»", style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
        Text(if (passed) "✓" else "—", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
    }
}

// ---------- Такт 2: рост и звание ----------

/** Тёмная сцена роста: ink и два круга посветлее — как «круги» экранов-событий, только ночью. */
private val InkLight = Color(0xFF4A3DAA)

/**
 * Рост — только когда меняется облик. Питомец в кремовой медали на тёмной сцене.
 * Превращение без переворотов (разбор 20.10: «видно, что персонаж плоский»): два прыжка,
 * белый силуэт с обводкой, вспышка — и он уже новой стадии. Сам облик растёт мало
 * (туловище в 1,25 раза), поэтому после превращения прежний силуэт на миг ложится поверх
 * тенью, а справа выезжает ростомер стадий: голова Финни поднимается на ступеньку.
 */
@Composable
private fun GrowBeat(state: PeriodResultUiState.Ready, nextLabel: String, onNext: () -> Unit) {
    val character = state.character ?: return
    val sounds = LocalSounds.current
    val animation = rememberPetAnimation()
    val disc = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }
    val flash = remember { Animatable(0f) }
    val ghost = remember { Animatable(0f) }
    val words = remember { Animatable(0f) }
    val ruler = remember { Animatable(0f) }
    val climb = remember { Animatable(0f) }
    var stage by remember { mutableIntStateOf(state.stageBefore) }
    LaunchedEffect(Unit) {
        if (!motionEnabled()) {
            disc.snapTo(1f); stage = state.stageAfter; words.snapTo(1f); ruler.snapTo(1f); climb.snapTo(1f)
            return@LaunchedEffect
        }
        disc.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
        animation.playJoy()
        delay(650); animation.playJoy()
        delay(650)
        // Силуэт проявляется быстро: на полпути видно и питомца, и обводку — кадр мутный.
        glow.animateTo(1f, tween(120))
        pulse.animateTo(1.07f, tween(180)); pulse.animateTo(1f, tween(160)); pulse.animateTo(1.12f, tween(220))
        sounds.play(Sfx.LevelUp)
        launch { flash.animateTo(1f, tween(120)); flash.animateTo(0f, tween(420)) }
        stage = state.stageAfter
        launch { pulse.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow)) }
        glow.animateTo(0f, tween(380))
        sounds.play(Sfx.PetLand)
        delay(250)
        launch { ghost.animateTo(0.32f, tween(300)); delay(1200); ghost.animateTo(0f, tween(500)) }
        // После превращения справа выезжает ростомер, и голова Финни поднимается на ступеньку.
        ruler.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
        climb.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
        sounds.play(Sfx.PetHappy)
        words.animateTo(1f, tween(400))
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(FinneyInk)
                drawCircle(InkLight, size.width * 0.22f, Offset(size.width * 0.12f, size.height * 0.1f))
                drawCircle(InkLight, size.width * 0.26f, Offset(size.width * 0.95f, size.height * 0.86f))
            },
    ) {
        Sunburst(rays = FinneyCream.copy(alpha = 0.12f), ground = FinneyInk, centerY = 0.38f)
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Ribbon("Финни подрос!")
            // Медаль сначала по центру; когда выезжает ростомер, она уходит влево и даёт ему место.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = RulerWidth + RulerGap
                val medal = minOf(maxWidth - side - 8.dp, maxHeight)
                Box(
                    modifier = Modifier
                        .size(medal)
                        .graphicsLayer {
                            scaleX = 0.5f + 0.5f * disc.value; scaleY = 0.5f + 0.5f * disc.value; alpha = disc.value.coerceIn(0f, 1f)
                            translationX = -ruler.value * (maxWidth - medal).toPx() / 2f
                        }
                        .border(10.dp, InkLight, CircleShape)
                        .padding(5.dp)
                        .border(8.dp, FinneyYellow, CircleShape)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFFFFF6E2), FinneyCream))),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val petModifier = Modifier.fillMaxWidth(0.88f).aspectRatio(1f).offset(y = (-14).dp)
                    PetView(
                        character = character,
                        mood = PetMood.HAPPY,
                        pose = rememberPoseProvider(animation),
                        accessories = state.worn,
                        stage = stage,
                        coloring = state.coloring,
                        modifier = petModifier
                            .graphicsLayer {
                                scaleX = pulse.value; scaleY = pulse.value
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.9f)
                            }
                            .silhouette { glow.value },
                    )
                    // Прежний рост — тенью поверх: видно, на сколько вырос.
                    PetView(
                        character = character,
                        mood = PetMood.HAPPY,
                        pose = { ru.finney.pet.ui.pet.PetPose() },
                        stage = state.stageBefore,
                        coloring = state.coloring,
                        modifier = petModifier.graphicsLayer { alpha = ghost.value }.tinted(FinneyInk),
                    )
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(
                        Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f))),
                    ))
                }
                StageRuler(
                    character = character,
                    coloring = state.coloring,
                    from = state.stageBefore,
                    to = state.stageAfter,
                    progress = { climb.value },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(RulerWidth)
                        .fillMaxHeight(0.62f)
                        .graphicsLayer { alpha = ruler.value.coerceIn(0f, 1f); translationX = (1f - ruler.value) * 60.dp.toPx() },
                )
            }
            Column(
                Modifier.graphicsLayer { alpha = words.value; translationY = (1f - words.value) * 24.dp.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Теперь он", style = MaterialTheme.typography.titleMedium, color = FinneyCream)
                state.stageTitle?.let { OutlinedText(it, style = MaterialTheme.typography.headlineLarge, fill = FinneyYellow) }
                Text(
                    "Финни растёт, когда ты заботишься, планируешь и копишь.",
                    style = MaterialTheme.typography.bodyLarge, color = FinneyCream, textAlign = TextAlign.Center,
                )
            }
            FinneyButton(text = nextLabel, onClick = onNext)
        }
    }
}

/**
 * Новое звание, когда облик тот же (4, 12, 20-й уровень): медаль падает сверху
 * и качается на ленточках, Финни подпрыгивает.
 */
@Composable
private fun RankBeat(state: PeriodResultUiState.Ready, nextLabel: String, onNext: () -> Unit) {
    val sounds = LocalSounds.current
    val animation = rememberPetAnimation()
    val drop = remember { Animatable(0f) }
    val swing = remember { Animatable(0f) }
    val words = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!motionEnabled()) { drop.snapTo(1f); words.snapTo(1f); return@LaunchedEffect }
        delay(200)
        drop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        sounds.play(Sfx.LevelUp)
        launch { swing.animateTo(1f, tween(900)) }
        animation.playJoy()
        words.animateTo(1f, tween(400))
    }
    Box(Modifier.fillMaxSize().background(FinneyCream)) {
        Sunburst(rays = FinneyYellow.copy(alpha = 0.55f), ground = FinneyCream, centerY = 0.42f)
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RankMedal(Modifier.size(130.dp, 150.dp).graphicsLayer {
                translationY = -(1f - drop.value) * 400.dp.toPx()
                // Затухающее покачивание на ленточках.
                rotationZ = 6f * kotlin.math.sin(swing.value * 3f * Math.PI.toFloat()) * (1f - swing.value)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
            })
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                state.character?.let { character ->
                    PetView(
                        character = character, mood = PetMood.HAPPY, pose = rememberPoseProvider(animation),
                        accessories = state.worn, stage = state.stageAfter, coloring = state.coloring,
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                    )
                }
            }
            Column(
                Modifier.graphicsLayer { alpha = words.value; translationY = (1f - words.value) * 24.dp.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Новое звание", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
                state.stageTitle?.let { OutlinedText(it, style = MaterialTheme.typography.headlineLarge) }
                Text("Звание растёт с каждым шагом пути.", style = MaterialTheme.typography.bodyLarge, color = FinneyInk, textAlign = TextAlign.Center)
            }
            FinneyButton(text = nextLabel, onClick = onNext)
        }
    }
}

private val RulerWidth = 44.dp
private val RulerGap = 28.dp

/**
 * Ростомер стадий: [from] → [to] по [progress] 0…1. Шесть зарубок — стадии снизу вверх,
 * ровная жёлтая заливка — пройденное, голова Финни сбоку — где он сейчас.
 */
@Composable
private fun StageRuler(
    character: PetCharacter,
    coloring: String?,
    from: Int,
    to: Int,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val stages = 6
    fun at(stage: Int) = (stage - 0.5f) / stages
    fun level() = at(from) + (at(to) - at(from)) * progress()
    BoxWithConstraints(modifier) {
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val r = 14.dp.toPx()
            val stroke = 4.dp.toPx()
            val path = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, androidx.compose.ui.geometry.CornerRadius(r)))
            }
            drawPath(path, FinneyCream)
            clipPath(path) {
                val l = level()
                drawRect(FinneyYellow, topLeft = Offset(0f, size.height * (1f - l)), size = Size(size.width, size.height * l))
            }
            for (k in 1..stages) {
                val y = size.height * (1f - at(k))
                drawLine(FinneyInk, Offset(0f, y), Offset(size.width * 0.45f, y), stroke * 0.75f)
            }
            drawPath(path, FinneyInk, style = Stroke(stroke))
        }
        PetView(
            character = character,
            mood = PetMood.HAPPY,
            pose = { ru.finney.pet.ui.pet.PetPose() },
            coloring = coloring,
            headOnly = true,
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer {
                    translationX = -30.dp.toPx()
                    translationY = (h * (1f - level()) - 23.dp).toPx()
                }
                .petShadow(),
        )
    }
}

/** Медаль звания: жёлтый круг со звездой на двух персиковых ленточках. */
@Composable
private fun RankMedal(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            fun tail(cx: Float, tilt: Float) {
                val w = 34.dp.toPx(); val h = 84.dp.toPx(); val top = 60.dp.toPx()
                val path = Path().apply {
                    moveTo(-w / 2, 0f); lineTo(w / 2, 0f); lineTo(w / 2, h); lineTo(0f, h * 0.82f); lineTo(-w / 2, h); close()
                }
                rotate(tilt, Offset(cx, top)) {
                    translate(cx, top) {
                        drawPath(path, FinneyPeach)
                        drawPath(path, FinneyInk, style = Stroke(stroke))
                    }
                }
            }
            tail(size.width / 2 - 26.dp.toPx(), 14f)
            tail(size.width / 2 + 26.dp.toPx(), -14f)
        }
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(0.58f to FinneyYellow, 0.6f to Color(0xFFEFC765)))
                .border(StrokeBold, FinneyInk, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Star(filled = true, modifier = Modifier.size(58.dp)) }
    }
}

/** Всё содержимое — одним цветом [color]: тень прежнего силуэта. */
@Composable
private fun Modifier.tinted(color: Color): Modifier {
    val body = rememberGraphicsLayer()
    return drawWithContent {
        body.record { this@drawWithContent.drawContent() }
        body.colorFilter = ColorFilter.tint(color, BlendMode.SrcIn)
        drawLayer(body)
    }
}

// ---------- Такт 3: новое ----------

/**
 * Что открылось — каждое своей карточкой: картинка, имя и где его искать на главном.
 * Карточки выходят по одной. Звание сюда не входит: у него свой такт.
 */
@Composable
private fun NewBeat(state: PeriodResultUiState.Ready, nextLabel: String, onNext: () -> Unit) {
    val shown = state.newThings.take(MaxNewCards)
    val more = state.newThings.size - shown.size
    val appear = remember { shown.map { Animatable(0f) } }
    val sounds = LocalSounds.current
    LaunchedEffect(Unit) {
        if (!motionEnabled()) { appear.forEach { it.snapTo(1f) }; return@LaunchedEffect }
        delay(350)
        appear.forEachIndexed { i, a ->
            sounds.play(Sfx.Coin, 1.2f + 0.1f * i)
            launch { a.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
            delay(420)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .menuBackdrop(MenuBackdrop.SHAPES)
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Ribbon("Новое на уровне ${state.level}")
        Spacer(Modifier.height(4.dp))
        shown.forEachIndexed { i, thing ->
            NewCard(thing, Modifier.graphicsLayer {
                val v = appear[i].value
                alpha = v.coerceIn(0f, 1f); scaleX = 0.6f + 0.4f * v; scaleY = 0.6f + 0.4f * v
            })
        }
        if (more > 0) Text("И ещё $more", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
        Spacer(Modifier.weight(1f))
        Text("Всё это уже ждёт на главном. Загляни!", style = MaterialTheme.typography.bodyLarge, color = FinneyInk, textAlign = TextAlign.Center)
        FinneyButton(text = nextLabel, onClick = onNext)
    }
}

private const val MaxNewCards = 4

@Composable
private fun NewCard(thing: NewThing, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(RadiusCard)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(StrokeRegular, FinneyInk, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = "${thing.label} — ${thing.place.where}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            when {
                thing.art != null -> Image(painterResource(thing.art), null, Modifier.size(60.dp))
                // Игре — значок игр, как на плитке «Игра» в итоге: предмет из самой игры
                // (конструктор у «Дороги к цели») читался как новая вещь.
                thing.place == NewPlace.GAMES -> Image(painterResource(R.drawable.ic_level_game), null, Modifier.size(56.dp))
                else -> FinneyIcon(FinneyIcons.Star, size = 44.dp)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(thing.label, style = MaterialTheme.typography.titleMedium, color = FinneyInk)
            Text(thing.place.where, style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
        }
        PlaceButton(thing.place)
    }
}

/** Кнопка главного, где живёт новое, — та же картинка, что на главном. */
@Composable
private fun PlaceButton(place: NewPlace) {
    val icon = when (place) {
        NewPlace.SHOP -> R.drawable.home_shop_icon
        NewPlace.GAMES -> R.drawable.home_game_icon
        NewPlace.GOALS -> R.drawable.home_pig_icon
    }
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.home_round_button_base), null, Modifier.fillMaxSize())
        Image(painterResource(icon), null, Modifier.size(30.dp))
    }
}

// ---------- Такт 4: монеты ----------

/** Доход нового уровня на кремовой медали, монеты летят в кошелёк и считаются (ТЗ п. 2.5.4: источник и сумма). */
@Composable
private fun CoinsBeat(state: PeriodResultUiState.Ready, nextLabel: String, onNext: () -> Unit) {
    val sounds = LocalSounds.current
    val start = (state.balance - state.income).coerceAtLeast(0)
    var wallet by remember { mutableIntStateOf(start) }
    val fly = remember { Animatable(0f) }
    val medal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!motionEnabled()) { medal.snapTo(1f); wallet = state.balance; return@LaunchedEffect }
        medal.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
        delay(300)
        val steps = 8
        repeat(steps) { i ->
            sounds.play(Sfx.Coin, 1f + 0.05f * i)
            fly.snapTo(0f)
            fly.animateTo(1f, tween(260))
            wallet = start + state.income * (i + 1) / steps
        }
    }
    Box(Modifier.fillMaxSize().background(FinneyCream)) {
        Sunburst(rays = FinneyYellow.copy(alpha = 0.5f), ground = FinneyCream, centerY = 0.42f)
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WalletChip(wallet)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .size(240.dp)
                        .graphicsLayer { scaleX = 0.5f + 0.5f * medal.value; scaleY = 0.5f + 0.5f * medal.value; alpha = medal.value.coerceIn(0f, 1f) }
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFFFFF6E2), FinneyCream)))
                        .border(StrokeBold, FinneyInk, CircleShape)
                        .clearAndSetSemantics { contentDescription = "Доход уровня ${state.level}: ${state.income} монет" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Монета рядом с числом, как «+15» на карточке игры: над числом она висела отдельно.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedText("+${state.income}", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 64.sp, lineHeight = 76.sp))
                        Coin(size = 46.dp)
                    }
                    Text("доход уровня ${state.level}", style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
                }
                // Монетка летит из медали в кошелёк — вверх и влево.
                Coin(size = 30.dp, modifier = Modifier.graphicsLayer {
                    val t = fly.value
                    alpha = if (t in 0.01f..0.99f) 1f else 0f
                    translationX = -t * size.width * 4.5f
                    translationY = -t * size.height * 9f
                })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.character?.let { character ->
                    PetView(character, PetMood.HAPPY, pose = { ru.finney.pet.ui.pet.PetPose() }, coloring = state.coloring,
                        accessories = state.worn, headOnly = true, modifier = Modifier.size(84.dp).petShadow())
                }
                SpeechBubble(
                    if (state.canPlanNext) "Новые монетки! Разложим по банкам?" else "Новые монетки уже в кошельке.",
                    Modifier.weight(1f),
                )
            }
            FinneyButton(text = nextLabel, onClick = onNext)
        }
    }
}

/** Кошелёк — белая плашка с монеткой, как в играх. */
@Composable
private fun WalletChip(amount: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .border(StrokeRegular, FinneyInk, RoundedCornerShape(50))
            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
            .clearAndSetSemantics { contentDescription = "В кошельке $amount" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedText(amount.toString(), style = MaterialTheme.typography.titleLarge)
        Coin(size = 30.dp)
    }
}

// ---------- Общие детали ----------

/**
 * Лучи из одной точки — та же, что центр питомца. Медленно вращаются; к центру и к краю
 * гаснут в [ground], чтобы не спорить с фоном. Без анимаций — стоят на месте.
 */
@Composable
private fun Sunburst(rays: Color, ground: Color, centerY: Float) {
    val animate = LocalAnimations.current
    val angle = if (animate) {
        rememberInfiniteTransition(label = "Лучи").animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "Поворот",
        ).value
    } else 0f
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2f, size.height * centerY)
        val r = size.maxDimension * 0.7f
        rotate(angle, c) {
            for (i in 0 until RayCount) {
                drawArc(rays, startAngle = i * 360f / RayCount, sweepAngle = 7f, useCenter = true,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2))
            }
        }
        drawRect(Brush.radialGradient(0f to Color.Transparent, 0.45f to Color.Transparent, 1f to ground, center = c, radius = r * 0.85f))
        drawCircle(Brush.radialGradient(0f to ground, 1f to ground.copy(alpha = 0f), center = c, radius = r * 0.22f), r * 0.22f, c)
    }
}

private const val RayCount = 18

/** Лента с вырезами на концах — выделение, которое не путается с кнопкой. */
@Composable
private fun Ribbon(text: String) {
    Box(
        modifier = Modifier
            .clip(RibbonShape)
            .background(FinneyYellow)
            .border(StrokeBold, FinneyInk, RibbonShape)
            .padding(horizontal = 34.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedText(text, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    }
}

/** Лента: прямоугольник с треугольными вырезами по бокам. */
private object RibbonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val notch = size.height * 0.32f
        return Outline.Generic(Path().apply {
            moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width - notch, size.height / 2f)
            lineTo(size.width, size.height); lineTo(0f, size.height); lineTo(notch, size.height / 2f); close()
        })
    }
}

/** Реплика Финни — белый пузырь с хвостиком слева, как на карточке игры. */
@Composable
private fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    val shape = remember { LeftTailBubble }
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = FinneyInk,
        modifier = modifier
            .clip(shape)
            .background(Color.White)
            .border(StrokeRegular, FinneyInk, shape)
            .padding(start = 30.dp, end = 14.dp, top = 10.dp, bottom = 13.dp),
    )
}

/** Пузырь с хвостиком слева посередине: тело и хвостик — одна фигура, рамка без шва. */
private object LeftTailBubble : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { 22.dp.toPx() }
        val t = with(density) { 16.dp.toPx() }
        val h = with(density) { 12.5.dp.toPx() }
        val mid = size.height / 2
        val body = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(t, 0f, size.width, size.height, androidx.compose.ui.geometry.CornerRadius(r))) }
        val tip = Path().apply { moveTo(t + r / 2, mid - h); lineTo(0f, mid); lineTo(t + r / 2, mid + h); close() }
        return Outline.Generic(Path().apply { op(body, tip, androidx.compose.ui.graphics.PathOperation.Union) })
    }
}

/** Тень по силуэту — то же содержимое, залитое полупрозрачным ink и сдвинутое вниз, как у голов в играх. */
@Composable
private fun Modifier.petShadow(): Modifier {
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

/**
 * Белый силуэт с обводкой ink поверх питомца — на [amount] 0…1. Отдельными слоями, а не
 * фильтром на самом питомце: так на старте ничего не мелькает.
 */
@Composable
private fun Modifier.silhouette(amount: () -> Float): Modifier {
    val body = rememberGraphicsLayer()
    val ink = rememberGraphicsLayer()
    val white = rememberGraphicsLayer()
    return drawWithContent {
        body.record { this@drawWithContent.drawContent() }
        drawLayer(body)
        val a = amount()
        if (a <= 0f) return@drawWithContent
        ink.colorFilter = ColorFilter.tint(FinneyInk, BlendMode.SrcIn)
        ink.alpha = a
        ink.record { drawLayer(body) }
        white.colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn)
        white.alpha = a
        white.record { drawLayer(body) }
        val d = 2.5.dp.toPx()
        for ((x, y) in listOf(d to 0f, -d to 0f, 0f to d, 0f to -d)) translate(x, y) { drawLayer(ink) }
        drawLayer(white)
    }
}
