package ru.finney.pet.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.finney.pet.R
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.ui.components.Coin
import ru.finney.pet.ui.components.LevelBadge
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.pet.PetMood
import ru.finney.pet.ui.pet.PetPose
import ru.finney.pet.ui.pet.PetView
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPeach
import ru.finney.pet.ui.theme.FinneyPink
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.theme.FinneyYellow

// Знакомство — три страницы по макету 10.2026 (HTML-прототип): кто такой Финни,
// куда тратить финки, как играть. Четвёртый шаг прототипа — создание питомца,
// это отдельный экран [PetSetupScreen]. Тот же экран открывается кнопкой «?»
// на главном — это подсказка, к которой можно вернуться.

private const val PAGES = 3

/** Сдвиг страницы прототипа: 0,55 с с лёгким перелётом. */
private val PageSlide = tween<Float>(550, easing = CubicBezierEasing(0.34f, 1.2f, 0.5f, 1f))

/**
 * [isReplay] — открыто повторно, с главного: в конце «Понятно!» и назад, без демо.
 * Иначе это первый запуск: в конце — создание питомца или тестовый профиль для проверки.
 */
@Composable
fun OnboardingScreen(
    isReplay: Boolean,
    onFinish: () -> Unit,
    onStartDemo: () -> Unit,
) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    fun go(page: Int) {
        scope.launch { pager.animateScrollToPage(page, animationSpec = PageSlide) }
    }
    BackHandler(enabled = pager.currentPage > 0) { go(pager.currentPage - 1) }

    HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
        when (page) {
            0 -> MeetPage(onNext = { go(1) })
            1 -> DecisionsPage(onBack = { go(0) }, onNext = { go(2) })
            else -> HowToPlayPage(
                onBack = { go(1) },
                onFinish = onFinish,
                onStartDemo = if (isReplay) null else onStartDemo,
            )
        }
    }
}

/** Страница прототипа: своя подложка во весь экран, поля 30/22/24, шаг 16. */
@Composable
private fun Page(backdrop: Modifier, footer: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ArtGap),
        modifier = Modifier
            .fillMaxSize()
            .then(backdrop)
            .systemBarsPadding()
            .padding(ArtPagePadding),
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Сохраняем раскладку на высоком экране; на низком прокручивается только тело.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ArtGap),
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .height(IntrinsicSize.Min),
                content = content,
            )
        }
        footer()
    }
}

/** Низ страницы: точки и ряд кнопок, под ними — ссылка, если есть. */
@Composable
private fun Footer(
    page: Int,
    next: String,
    onNext: () -> Unit,
    onBack: (() -> Unit)? = null,
    link: (@Composable () -> Unit)? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        ArtDots(current = page, total = PAGES)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (onBack != null) ArtBackButton(onBack)
            ArtButton(next, onNext, Modifier.weight(1f))
        }
        if (link != null) {
            Spacer(Modifier.height(12.dp))
            link()
        }
    }
}

// ---------- 1 · Знакомство ----------

@Composable
private fun MeetPage(onNext: () -> Unit) {
    Page(Modifier.dotsBackdrop(FinneyCream), footer = {
        Footer(page = 0, next = "Дальше", onNext = onNext)
    }) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        ) {
            ArtBody("Знакомься:", 28f, line = 1f)
            ArtSign("Финни", 88f)
        }
        Hero(Modifier.weight(1f).fillMaxWidth())
        ArtSay(
            buildAnnotatedString {
                append("Привет! Я твой питомец.\nДавай вместе копить ")
                withStyle(SpanStyle(color = ArtHighlight)) { append("на мечту!") }
            },
            tailUp = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        )
    }
}

/** Финни на белом круге, на лучах, вокруг мерцают четыре звёздочки. */
@Composable
private fun Hero(modifier: Modifier) {
    Box(modifier.heightIn(min = 324.dp).sunburst(560.dp), contentAlignment = Alignment.Center) {
        val disc = 300.dp
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(disc)
                .drawBehind { drawCircle(Color.White.copy(alpha = 0.6f), radius = size.minDimension / 2 + 12.dp.toPx()) }
                .plate(disc, Color.White, ArtSoft, 14.dp),
        ) {
            PetView(
                character = PetCharacter.PUSHISTIK,
                mood = PetMood.HAPPY,
                pose = StillPose,
                modifier = Modifier
                    .padding(top = 14.dp * (disc / 300.dp))
                    .size(264.dp * (disc / 300.dp))
                    .bob(),
            )
        }
        Sparkle(FinneyPink, 26.dp, 0, Modifier.align(Alignment.TopStart).offset(x = 22.dp, y = 39.dp))
        Sparkle(FinneyYellow, 34.dp, 600, Modifier.align(Alignment.TopEnd).offset(x = (-26).dp, y = 19.dp))
        Sparkle(Color.White, 20.dp, 1200, Modifier.align(Alignment.BottomStart).offset(x = 40.dp, y = (-26).dp))
        Sparkle(FinneyPeach, 26.dp, 1800, Modifier.align(Alignment.BottomEnd).offset(x = (-30).dp, y = (-45).dp))
    }
}

private val SparklePath = PathParser()
    .parsePathString("M13 1c1.2 6.5 4.3 10 12 12-7.7 2-10.8 5.5-12 12-1.2-6.5-4.3-10-12-12 7.7-2 10.8-5.5 12-12z")
    .toPath()

/** Четырёхлучевая звёздочка: сжимается до 0,6 и поворачивается на 20° за 2,4 с. */
@Composable
private fun Sparkle(color: Color, size: Dp, delayMs: Int, modifier: Modifier) {
    val twinkle = if (LocalAnimations.current) {
        rememberInfiniteTransition(label = "sparkle").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(1200, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
                initialStartOffset = StartOffset(delayMs),
            ),
            label = "twinkle",
        )
    } else {
        null
    }
    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                val t = twinkle?.value ?: 0f
                scaleX = 1f - 0.4f * t
                scaleY = 1f - 0.4f * t
                rotationZ = 20f * t
            },
    ) {
        val k = this.size.minDimension / 26f
        scale(k, k, pivot = Offset.Zero) {
            drawPath(SparklePath, color)
            drawPath(SparklePath, FinneyInk, style = Stroke(1.6f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

// ---------- 2 · Решение за тобой ----------

private val ForkKnife = PathParser().parsePathString(
    "M4.6 2h1.7v5.6h1V2H9v5.6h1V2h1.7v6.4a3.1 3.1 0 0 1-2.1 2.9v9.4a1.4 1.4 0 0 1-2.8 0v-9.4a3.1 3.1 0 0 1-2.2-2.9z" +
        "M18.4 2.1c-2.6 1-3.9 4.2-3.9 8.3 0 1.3.8 2.1 2.1 2.1h.6v8.2a1.4 1.4 0 0 0 2.8 0V3.3a1.3 1.3 0 0 0-1.6-1.2z",
).toPath()
private val WantStar = PathParser()
    .parsePathString("M12 2.2l2.9 6 6.6.9-4.8 4.6 1.2 6.6L12 17.1l-5.9 3.2 1.2-6.6-4.8-4.6 6.6-.9z")
    .toPath()
private val Piggy = PathParser().parsePathString(
    "M8.6 3.6a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0z" +
        "M3.2 13.2c0-3.6 3.5-5.7 7.8-5.7 2.9 0 5 .9 6.2 2.1l2.1-1-.4 2.8c.6.7.9 1.4 1.1 2.1h1.4v3.4h-1.6c-.5 1-1.4 1.8-2.4 2.3V22h-3.1v-1.9h-3.7V22H7.5v-2.8c-2.6-1.2-4.3-3.3-4.3-6z",
).toPath()

@Composable
private fun DecisionsPage(onBack: () -> Unit, onNext: () -> Unit) {
    Page(Modifier.dotsBackdrop(ArtYellowPage), footer = {
        Footer(page = 1, next = "Дальше", onNext = onNext, onBack = onBack)
    }) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ArtSign("Решение за тобой!", 46f, Modifier.semantics { heading() })
            ArtBody("Куда потратить финки?", 21f)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Choice(
                title = "Нужное",
                text = "Еда и мытьё",
                icon = { SvgIcon(ForkKnife, Modifier.size(40.dp)) },
                tag = "Сначала!",
            ) {
                Image(
                    painterResource(R.drawable.item_food_apple), null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.offset(y = 4.dp).width(70.dp),
                )
                Image(
                    painterResource(R.drawable.item_care_soap), null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .width(52.dp)
                        .graphicsLayer { rotationZ = -10f },
                )
            }
            Choice(
                title = "Хочется",
                text = "Игрушки и сладости",
                icon = { SvgIcon(WantStar, Modifier.size(40.dp), outline = 1.4f) },
            ) {
                Image(
                    painterResource(R.drawable.item_toy_bear), null,
                    contentScale = ContentScale.FillHeight,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .height(88.dp)
                        .graphicsLayer { rotationZ = 6f },
                )
            }
            Choice(
                title = "Отложить",
                text = "В копилку на большую цель",
                icon = {
                    Box(Modifier.size(40.dp)) {
                        SvgIcon(Piggy, Modifier.matchParentSize())
                        // Глаз — жёлтая точка на тёмном силуэте.
                        Canvas(Modifier.matchParentSize()) {
                            val k = size.minDimension / 24f
                            drawCircle(FinneyYellow, radius = 1f * k, center = Offset(16.4f * k, 12.4f * k))
                        }
                    }
                },
            ) {
                Coin(Modifier.align(Alignment.BottomStart).offset(y = (-2).dp), size = 46.dp)
                Coin(Modifier.align(Alignment.BottomEnd).offset(y = (-2).dp), size = 46.dp)
                Coin(Modifier.offset(x = 19.dp), size = 46.dp)
            }
        }
    }
}

/** Карточка решения: круглый значок, название вывеской, подпись и картинки справа. */
@Composable
private fun Choice(
    title: String,
    text: String,
    icon: @Composable () -> Unit,
    tag: String? = null,
    art: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .plate(28.dp, Color.White, ArtSoft, 8.dp)
                .padding(start = 14.dp, top = 16.dp, end = 12.dp, bottom = 21.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(74.dp)
                    .plate(74.dp, FinneyYellow, FinneyPeach, 11.dp, glare = DiscGlare)
                    .padding(bottom = 6.dp),
            ) { icon() }
            Column(Modifier.weight(1f)) {
                ArtSign(title, 29f, align = TextAlign.Start)
                Spacer(Modifier.height(5.dp))
                ArtBody(text, 18f, line = 1.2f, align = TextAlign.Start)
            }
            Box(Modifier.size(width = 84.dp, height = 86.dp), content = art)
        }
        if (tag != null) {
            Text(
                tag,
                style = artText(18f, 1.15f),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-20).dp, y = (-11).dp)
                    .graphicsLayer { rotationZ = 4f }
                    .plate(99.dp, FinneyPink, Color.Transparent, 0.dp, border = 3.dp)
                    .padding(start = 15.dp, end = 15.dp, top = 4.dp, bottom = 7.dp),
            )
        }
    }
}

// ---------- 3 · Как играть ----------

private val GoalStar = PathParser()
    .parsePathString("M12 2.4l2.85 5.95 6.5.85-4.75 4.5 1.2 6.5L12 17l-5.8 3.2 1.2-6.5-4.75-4.5 6.5-.85z")
    .toPath()
private val Heart = PathParser()
    .parsePathString("M12 20.5s-7.4-4.5-9.2-9C1.5 8.2 3.6 4.9 7 4.9c2 0 3.4 1.1 5 3 1.6-1.9 3-3 5-3 3.4 0 5.5 3.3 4.2 6.6-1.8 4.5-9.2 9-9.2 9z")
    .toPath()

@Composable
private fun HowToPlayPage(onBack: () -> Unit, onFinish: () -> Unit, onStartDemo: (() -> Unit)?) {
    Page(Modifier.starsBackdrop(), footer = {
        Footer(
            page = 2,
            next = "Понятно!",
            onNext = onFinish,
            onBack = onBack,
            link = onStartDemo?.let { start ->
                {
                    Text(
                        "Режим проверки (демо)",
                        style = artText(17f, 1.15f).copy(textDecoration = TextDecoration.Underline),
                        color = FinneyInk.copy(alpha = 0.75f),
                        modifier = Modifier
                            .clickable(onClick = start)
                            .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 6.dp),
                    )
                }
            },
        )
    }) {
        ArtSign("Как играть?", 46f, Modifier.semantics { heading() })
        // Пунктир держит шаги одной дорожкой: от 14 % до 86 % высоты, по центру картинок.
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .drawBehind {
                    val x = 45.dp.toPx()
                    drawLine(
                        FinneyInk.copy(alpha = 0.32f),
                        Offset(x, size.height * 0.14f),
                        Offset(x, size.height * 0.86f),
                        strokeWidth = 4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
                    )
                },
        ) {
            Step(1, "Получай финки", "Это игровые монетки") { Coin(size = 84.dp) }
            Step(2, "Ставь цель", "И копи на неё") {
                SvgIcon(GoalStar, Modifier.size(84.dp), color = FinneyYellow, outline = 1.5f)
            }
            Step(3, "Заботься о питомце", "Корми, купай, играй") {
                PetView(
                    character = PetCharacter.PUSHISTIK,
                    mood = PetMood.HAPPY,
                    pose = StillPose,
                    modifier = Modifier.size(92.dp),
                )
            }
            Step(4, "Достигай высот", "Новый уровень — новые игры") {
                LevelBadge(level = 9, size = 66.dp, progress = 0.5f)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SvgIcon(Heart, Modifier.size(28.dp), color = FinneyPink, outline = 1.8f)
            ArtBody("Ошибаться не страшно!", 21f, line = 1f)
        }

    }
}

/** Шаг: картинка 90 dp с номером в углу и белая плашка с названием и подписью. */
@Composable
private fun Step(number: Int, title: String, text: String, art: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$number. $title. $text" },
    ) {
        Box(Modifier.size(90.dp), contentAlignment = Alignment.Center) {
            art()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-6).dp, y = (-6).dp)
                    .size(32.dp)
                    .plate(16.dp, Color.White, ArtSoft, 3.dp, border = 3.dp)
                    .padding(bottom = 3.dp),
            ) {
                ArtBody(number.toString(), 19f, line = 1f)
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .weight(1f)
                .plate(20.dp, Color.White, ArtSoft, 5.dp, border = 3.dp)
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 13.dp),
        ) {
            ArtBody(title, 23f, line = 1.05f, align = TextAlign.Start)
            ArtBody(text, 17f, color = FinneyInk.copy(alpha = 0.78f), align = TextAlign.Start)
        }
    }
}

/** Поза питомца на картинках знакомства: стоит ровно, как рисунок в прототипе. */
internal val StillPose: () -> PetPose = { PetPose() }

@Preview(widthDp = 412, heightDp = 880)
@Composable
private fun OnboardingPreview() {
    FinneyTheme { OnboardingScreen(isReplay = false, onFinish = {}, onStartDemo = {}) }
}
