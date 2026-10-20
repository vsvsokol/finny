package ru.finney.pet.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.finney.pet.domain.model.PeriodFacts
import ru.finney.pet.domain.model.Plan
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyPink
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.theme.FinneyYellow
import ru.finney.pet.ui.theme.StrokeRegular

// «План и факт» тремя банками — из редизайна 28.09. Раньше на каждую часть было по две
// полосы со словами «Собирался» и «Потратил»; банка говорит то же без слов: черта — сколько
// задумал, заливка — сколько вышло. Заливка — узором своей части, как на круге плана, и значок
// под банкой: часть узнаётся не по одному цвету (ТЗ п. 3.6).

/** Доля высоты банки, до которой доходит большее из «задумал» и «вышло»: сверху остаётся воздух, и черту видно. */
private const val JarHeadroom = 0.85f

private val JarWidth = 64.dp
private val JarHeight = 124.dp
private val JarIcon = 48.dp

/**
 * Три банки плана: нужное, желаемое, копилка. Числа ядра показываются как есть —
 * здесь ничего не пересчитывается, только раскладывается по высоте.
 *
 * Под значком — слово и что в эту часть входит. Плейтест 29.09: на первых уровнях
 * по одному значку не понять, что тут «нужное», а что «хочется».
 */
@Composable
fun PlanJars(plan: Plan, facts: PeriodFacts, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val jar = Modifier.weight(1f)
        PlanJar(PlanPart.NEEDS, "Нужное", "еда и ванна", plan.needs, facts.needs, jar)
        PlanJar(PlanPart.WANTS, "Желаемое", "игрушки и сладости", plan.wants, facts.wants, jar)
        PlanJar(PlanPart.SAVINGS, "Копилка", "на цель", plan.savings, facts.savings, jar)
    }
}

/**
 * Одна банка. Трата сверх плана — розовая часть над чертой и «!» у числа: цвет не
 * единственный признак (ТЗ п. 3.6). У копилки больше плана — не ошибка,
 * заливка просто идёт выше черты.
 */
@Composable
private fun PlanJar(
    part: PlanPart,
    label: String,
    hint: String,
    planned: Int,
    fact: Int,
    modifier: Modifier = Modifier,
) {
    val saving = part == PlanPart.SAVINGS
    val over = !saving && fact > planned
    val scale = maxOf(planned, fact).coerceAtLeast(1) / JarHeadroom
    val shown by animateFloatAsState((fact / scale).coerceIn(0f, 1f), label = "jar")
    val line = (planned / scale).coerceIn(0f, 1f)
    val done = if (saving) "отложил" else "потратил"
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "$label ($hint): задумал $planned, $done $fact" +
                if (over) ", больше плана на ${fact - planned}" else ""
        },
    ) {
        JarGlass(line, shown, over, Modifier.width(JarWidth).height(JarHeight), fill = rememberPartBrush(part))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(JarIcon).clip(CircleShape).buttonFill(pressed = false, round = true),
        ) {
            FinneyIcon(partIcon(part), size = 28.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$fact/$planned", style = MaterialTheme.typography.titleMedium, color = FinneyInk)
            if (over) AlertBadge(size = 22.dp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = FinneyInk, textAlign = TextAlign.Center)
            Text(hint, style = MaterialTheme.typography.labelMedium, color = FinneyInk, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Сама банка: [shown] — заливка в долях высоты, [line] — черта плана в тех же долях.
 * Отдельно от подписей — справочник рисует ею слово «факт», и в игре ребёнок узнаёт ту же банку.
 * [fill] — узор части; без части (в справочнике) банка просто жёлтая — деньги.
 */
@Composable
internal fun JarGlass(
    line: Float,
    shown: Float,
    over: Boolean,
    modifier: Modifier = Modifier,
    fill: Brush = SolidColor(FinneyYellow),
) {
    Canvas(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .border(StrokeRegular, FinneyInk, RoundedCornerShape(50)),
    ) {
        val w = size.width
        val h = size.height
        val planY = h * (1f - line)
        val factY = h * (1f - shown)
        // Узор части — до черты или до факта, что ниже; сверх плана у трат — розовое.
        val fillTop = if (over) planY else factY
        drawRect(fill, topLeft = Offset(0f, fillTop), size = Size(w, h - fillTop))
        if (over && factY < planY) {
            drawRect(FinneyPink, topLeft = Offset(0f, factY), size = Size(w, planY - factY))
        }
        // Черта плана — пунктиром, как в редизайне: она мерка, а не граница заливки.
        val dash = 6.dp.toPx()
        drawLine(
            FinneyInk,
            start = Offset(0f, planY),
            end = Offset(w, planY),
            strokeWidth = StrokeRegular.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f)),
        )
    }
}

@Preview(widthDp = 360, showBackground = true, backgroundColor = 0xFFFFEDCD)
@Composable
private fun PlanJarsPreview() {
    FinneyTheme {
        Column(Modifier.padding(16.dp)) {
            PlanJars(
                plan = Plan(budget = 100, needs = 40, wants = 25, savings = 35),
                facts = PeriodFacts(needs = 40, wants = 30, savings = 20, unplannedIncome = 0),
            )
        }
    }
}
