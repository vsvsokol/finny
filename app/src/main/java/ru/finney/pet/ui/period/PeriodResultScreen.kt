package ru.finney.pet.ui.period

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import ru.finney.pet.R
import ru.finney.pet.domain.model.ItemArt
import ru.finney.pet.domain.model.PeriodFacts
import ru.finney.pet.domain.model.Plan
import ru.finney.pet.ui.components.AlertBadge
import ru.finney.pet.ui.components.CheckBadge
import ru.finney.pet.ui.components.Coin
import ru.finney.pet.ui.components.CoinAmount
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.PlanJars
import ru.finney.pet.ui.tasks.games.ItemPicture
import ru.finney.pet.ui.components.FinneyPanel
import ru.finney.pet.ui.components.FinneyScreen
import ru.finney.pet.ui.components.MenuBackdrop
import ru.finney.pet.ui.components.LevelBadge
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.motion.motionEnabled
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyGreen
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.RadiusCard
import ru.finney.pet.ui.theme.StrokeRegular

// Что показывать, решает PeriodResultViewModel — экран только раскладывает готовое.
//
// Плейтест 29.09: итог говорил про план и копилку дважды — банками «Как вышло» и
// строками «Условия уровня» ниже, — и читался отчётом, а не игрой. Теперь условия —
// одна строка плиток, и за каждое выполненное зажигается звезда; деньги — одна
// панель: банки плана и то, что пришло на следующий уровень.

@Composable
fun PeriodResultScreen(
    periodNumber: Int,
    onBack: () -> Unit,
    onOpenBudget: () -> Unit = onBack,
    viewModel: PeriodResultViewModel = viewModel(factory = PeriodResultViewModel.factory(periodNumber)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        PeriodResultUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = FinneyInk)
        }

        PeriodResultUiState.Unavailable -> FinneyScreen {
            OutlinedText("Итоги уровня", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = "Этот уровень ещё не завершён.",
                style = MaterialTheme.typography.bodyLarge,
                color = FinneyInk,
            )
            FinneyButton(text = "На главный", onClick = onBack)
        }

        is PeriodResultUiState.Ready -> PeriodResultContent(state = s, onBack = onBack, onOpenBudget = onOpenBudget)
    }
}

/** Условие уровня плиткой в итоге: картинка, одно слово и выполнено ли. */
private class Condition(val label: String, val spoken: String, val done: Boolean, val icon: @Composable () -> Unit)

@Composable
internal fun PeriodResultContent(state: PeriodResultUiState.Ready, onBack: () -> Unit, onOpenBudget: () -> Unit = onBack) {
    val conditions = buildList {
        state.levelGame?.let { game ->
            val need = if (state.gameRequired) "обязательно" else "по желанию"
            add(Condition("Игра", "Игра уровня «$game» — $need", state.gamePassed) {
                state.levelGameIcon?.let { ItemPicture(it, game, 44.dp) }
                    ?: FinneyIcon(FinneyIcons.Star, size = 36.dp)
            })
        }
        add(Condition("Уход", "Питомец сыт, чист и выспался", state.needsCovered) { TileArt(R.drawable.ic_level_care) })
        add(Condition("План", "Траты по плану", state.planMatched) { Coin(size = 40.dp) })
        add(Condition("Копилка", "Отложено в копилку", state.savingsAdded) { TileArt(R.drawable.ic_level_piggy) })
    }

    // Звёзды зажигаются по одной, со звоном — как счёт очков в конце уровня. Потом, если
    // уровень вырос, — праздничный джингл. Один раз на экран: поворот его не повторяет.
    val sounds = LocalSounds.current
    var shown by rememberSaveable { mutableStateOf(false) }
    val lit = remember { conditions.map { Animatable(if (shown) 1f else 0f) } }
    LaunchedEffect(Unit) {
        if (shown) return@LaunchedEffect
        val animate = motionEnabled()
        delay(if (animate) StarStartMs else 0L)
        conditions.forEachIndexed { i, c ->
            if (!c.done) return@forEachIndexed
            sounds.play(Sfx.Coin, 1f + 0.1f * i)
            if (animate) {
                lit[i].animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
                delay(StarGapMs)
            } else {
                lit[i].snapTo(1f)
            }
        }
        if (state.leveledUp) sounds.play(Sfx.LevelUp)
        shown = true
    }

    FinneyScreen(
        backdrop = MenuBackdrop.SHAPES,
        scrollable = true,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        bottom = {
            FinneyButton(
                text = if (state.canPlanNext) "Составить следующий план" else "На главный",
                onClick = if (state.canPlanNext) onOpenBudget else onBack,
                textStyle = MaterialTheme.typography.titleMedium,
            )
        },
    ) {
        OutlinedText("Итоги уровня ${state.playedLevel}", style = MaterialTheme.typography.headlineLarge)

        // Главное — сразу и словами, а не только цветом (ТЗ п. 3.6).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LevelBadge(level = state.level)
            Text(
                text = when {
                    state.leveledUp -> "Уровень пройден! Теперь уровень ${state.level}."
                    state.passed -> "Уровень пройден!"
                    else -> "Почти! Уровень ${state.playedLevel} начнётся заново."
                },
                style = MaterialTheme.typography.titleLarge,
                color = FinneyInk,
            )
        }

        state.growth?.let { growth ->
            FinneyPanel(title = "Что нового") {
                growth.changes.forEach {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
                }
                val next = growth.next
                if (next != null) {
                    Text("Впереди, на уровне ${next.level}:",
                        style = MaterialTheme.typography.titleMedium, color = FinneyInk)
                    Text(next.changes.first(), style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
                } else {
                    Text("Ты на самом высоком уровне. Можно дальше играть и копить.",
                        style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
                }
            }
        }

        val met = listOf(state.needsCovered, state.planMatched, state.savingsAdded).count { it }
        FinneyPanel(title = "Звёзды уровня") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                conditions.forEachIndexed { i, c ->
                    ConditionTile(c, lit = { lit[i].value }, modifier = Modifier.weight(1f))
                }
            }
            // Правило уровня одной строкой — то же, что в панели уровня на главном.
            val rule = if (state.levelGame != null && state.gameRequired) {
                "Нужно: игра и ${state.toPass} из 3 — уход, план, копилка"
            } else {
                "Нужно: ${state.toPass} из 3 — уход, план, копилка"
            }
            Text(rule, style = MaterialTheme.typography.bodyLarge, color = FinneyInk, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (!state.passed) {
                Text(
                    buildString {
                        append("Не хватило: ")
                        val missing = buildList {
                            if (state.levelGame != null && state.gameRequired && !state.gamePassed) add("игры")
                            val left = (state.toPass - met).coerceAtLeast(0)
                            if (left > 0) add(if (left == 1) "ещё одного условия" else "ещё $left условий")
                        }
                        append(missing.joinToString(" и "))
                        append(". Попробуй ещё раз!")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = FinneyInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        FinneyPanel(title = "Деньги") {
            // Банки: черта — задумал, заливка — вышло. Трата сверх плана — розовым и «!».
            PlanJars(state.plan, state.facts)
            if (!state.planMatched) {
                // Совет про план — здесь, у банок, а не отдельной плашкой под экраном.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FinneyIcon(FinneyIcons.Plan, size = 28.dp)
                    Text(
                        "Перед покупкой загляни в план: сколько ещё можно.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = FinneyInk,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (state.facts.unplannedIncome > 0) {
                MoneyLine("Пришло сверх плана", "+${state.facts.unplannedIncome}")
            }
            // Деньги нового уровня приходят и после «почти». Плейтест 29.09: не видя
            // их, ребёнок решил, что денег не дали вовсе.
            if (state.income > 0) {
                MoneyLine("Уже получено в кошелёк", "+${state.income}", highlight = true)
                Text(
                    "Это новый доход для следующего плана. Платить за переход уровня не нужно.",
                    style = MaterialTheme.typography.bodyLarge, color = FinneyInk,
                )
            }
            MoneyLine("Сейчас в кошельке", state.balance.toString())
        }
    }
}

/** Строка про деньги: подпись и сумма с монетой. [highlight] — жёлтая плашка: это пришло ребёнку. */
@Composable
private fun MoneyLine(label: String, amount: String, highlight: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusCard))
            .background(if (highlight) FinneyYellow else FinneyCream)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $amount" },
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = FinneyInk, modifier = Modifier.weight(1f))
        OutlinedText(amount, style = MaterialTheme.typography.titleLarge)
        Coin(size = 24.dp, modifier = Modifier.padding(start = 4.dp))
    }
}

/**
 * Плитка условия: картинка, слово и отметка. Выполненное — зелёная плитка с «✓» и
 * звездой над ней; невыполненное — кремовая с «!». Звезда выпрыгивает, когда до неё
 * дошёл счёт ([lit] 0…1), — читается только при рисовании.
 */
@Composable
private fun ConditionTile(condition: Condition, lit: () -> Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "${condition.spoken}: ${if (condition.done) "выполнено" else "не выполнено"}"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            Box(
                modifier = Modifier
                    .padding(top = StarSize / 2)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(RadiusCard))
                    .background(if (condition.done) FinneyGreen else FinneyCream)
                    .border(StrokeRegular, FinneyInk, RoundedCornerShape(RadiusCard)),
                contentAlignment = Alignment.Center,
            ) { condition.icon() }
            // Звезда сидит на верхней кромке плитки. Невыполненное — пустой контур:
            // видно, что звезда могла быть, и её не хватает.
            Star(
                filled = condition.done,
                modifier = Modifier
                    .size(StarSize)
                    .graphicsLayer {
                        val v = if (condition.done) lit() else 1f
                        scaleX = 0.4f + 0.6f * v
                        scaleY = 0.4f + 0.6f * v
                        alpha = if (condition.done) v.coerceIn(0f, 1f) else 1f
                    },
            )
            Box(Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp)) {
                if (condition.done) CheckBadge(size = 24.dp) else AlertBadge(size = 24.dp)
            }
        }
        Text(condition.label, style = MaterialTheme.typography.bodyLarge, color = FinneyInk, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Пятиконечная звезда: жёлтая — заработана, бледный контур — нет. */
@Composable
private fun Star(filled: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        val path = Path().apply {
            for (i in 0 until 10) {
                val radius = if (i % 2 == 0) r else r * 0.48f
                val angle = PI * i / 5 - PI / 2
                val p = c + Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        // Незаработанная — бледный контур без заливки: рядом с жёлтой её не спутать.
        if (filled) drawPath(path, FinneyYellow)
        drawPath(path, if (filled) FinneyInk else FinneyInk.copy(alpha = 0.35f), style = Stroke(StarStroke.toPx()))
    }
}

/** Рисунок условия — из design/exports/ui/icons, как в панели уровня на главном. */
@Composable
private fun TileArt(@DrawableRes id: Int) {
    Image(painterResource(id), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(44.dp))
}

private val StarSize: Dp = 30.dp
private val StarStroke: Dp = 2.5.dp

/** Счёт звёзд начинается, когда экран уже виден, и идёт по одной. */
private const val StarStartMs = 500L
private const val StarGapMs = 220L

@Preview(showBackground = true, backgroundColor = 0xFFFFEDCD, widthDp = 360)
@Composable
private fun PeriodResultPreview() {
    FinneyTheme {
        PeriodResultContent(
            state = PeriodResultUiState.Ready(
                periodNumber = 1,
                playedLevel = 1,
                plan = Plan(budget = 50, needs = 20, wants = 10, savings = 10),
                facts = PeriodFacts(needs = 20, wants = 5, savings = 10, unplannedIncome = 0),
                needsCovered = true,
                planMatched = false,
                savingsAdded = true,
                toPass = 2,
                passed = true,
                level = 2,
                leveledUp = true,
                balance = 15,
                levelGame = "Дождливый день",
                income = 55,
            ),
            onBack = {},
        )
    }
}
