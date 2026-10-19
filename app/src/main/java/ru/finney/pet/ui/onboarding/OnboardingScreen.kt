package ru.finney.pet.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.PetCharacter
import ru.finney.pet.ui.components.Coin
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyIcon
import ru.finney.pet.ui.components.FinneyIconButton
import ru.finney.pet.ui.components.FinneyIcons
import ru.finney.pet.ui.components.FinneyQuietButton
import ru.finney.pet.ui.components.FinneyScreen
import ru.finney.pet.ui.components.LevelBadge
import ru.finney.pet.ui.components.MenuBackdrop
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.components.SavingsIcon
import ru.finney.pet.ui.components.StepDots
import ru.finney.pet.ui.components.buttonFill
import ru.finney.pet.ui.components.categoryIcon
import ru.finney.pet.ui.pet.PetMood
import ru.finney.pet.ui.pet.PetView
import ru.finney.pet.ui.pet.rememberPetAnimation
import ru.finney.pet.ui.pet.rememberPoseProvider
import ru.finney.pet.ui.sound.Sfx
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.theme.GapBlock
import ru.finney.pet.ui.theme.GapSection
import ru.finney.pet.ui.theme.RadiusCard

// Знакомство — ТЗ п. 2.5.1: цель игры и три типа решений (нужное, желаемое, отложить).
// Три коротких страницы вместо одной длинной: ребёнок 7 лет читает фразу за фразой,
// а не абзац. Тот же экран открывается кнопкой «?» на главном — это подсказка,
// к которой можно вернуться в любой момент.
//
// Облик — из редизайна 28.09: вывески-заголовки, картинка вместо строки, где можно,
// и одна короткая фраза на пункт. Смысл прежних текстов оставлен: «нужное — сначала»
// и «составь план» — это то, чему игра учит, а в редизайне они потерялись.

private const val PAGES = 3

/**
 * [isReplay] — открыто повторно, с главного: в конце «Понятно» и назад.
 * Иначе это первый запуск: в конце — создание питомца или тестовый профиль для проверки.
 */
@Composable
fun OnboardingScreen(
    isReplay: Boolean,
    onFinish: () -> Unit,
    onStartDemo: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val last = page == PAGES - 1

    FinneyScreen(
        backdrop = MenuBackdrop.SHAPES,
        scrollable = true,
        // Разделы страницы — с воздухом: на плейтесте приветствие назвали тесным.
        verticalArrangement = Arrangement.spacedBy(GapSection),
        bottom = {
            Column(
                verticalArrangement = Arrangement.spacedBy(GapBlock),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Где ты — точками, а не «2 из 3»: число здесь ничего не значит. Точки над
                // кнопкой — там на них смотрят, когда решают, листать ли дальше.
                StepDots(done = page, total = PAGES, current = page)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GapBlock),
                ) {
                    // «Назад» — круглой стрелкой, а не второй длинной кнопкой: одинаковые
                    // с «Дальше» кнопки жали наугад (плейтест 28.09), а стрелку с ней не спутать.
                    if (page > 0) {
                        FinneyIconButton(
                            onClick = { page-- },
                            contentDescription = "Назад",
                            size = BackSize,
                            sound = Sfx.Back,
                        ) {
                            OutlinedText("←", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    FinneyButton(
                        text = when {
                            !last -> "Дальше"
                            isReplay -> "Понятно"
                            else -> "Создать питомца"
                        },
                        onClick = { if (last) onFinish() else page++ },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (last && !isReplay) {
                    FinneyQuietButton(text = "Режим проверки (демо)", onClick = onStartDemo)
                }
            }
        },
    ) {
        when (page) {
            0 -> MeetPage()
            1 -> DecisionsPage()
            else -> PeriodPage(isReplay)
        }
    }
}

/** Круглая «←» — ростом с основную кнопку, чтобы ряд читался одной линией. */
private val BackSize = 64.dp

/** Заголовок страницы — крупнее заголовков панелей: это вывеска, а не подпись. */
@Composable
private fun Title(text: String) {
    OutlinedText(
        text,
        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 42.sp, lineHeight = 54.sp),
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
}

/**
 * Текст страницы — кеглем крупнее основного: это первое, что ребёнок читает,
 * и на плейтесте он показался мелким. По центру, как заголовок: иначе короткая
 * строка вставала посередине, а длинные — влево.
 */
@Composable
private fun Body(vararg paragraphs: String) {
    Column(verticalArrangement = Arrangement.spacedBy(GapBlock), modifier = Modifier.fillMaxWidth()) {
        paragraphs.forEach {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = FinneyInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MeetPage() {
    // «Знакомься:» мелко, имя — вывеской: запоминается имя, а не служебное слово.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) { heading() },
    ) {
        OutlinedText("Знакомься:", style = MaterialTheme.typography.headlineMedium)
        OutlinedText("Финни", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 64.sp, lineHeight = 80.sp))
    }
    // Питомец на белом круге, как на сцене: так он первое, что видно на экране.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(MeetStage).clip(CircleShape).background(Color.White),
    ) {
        PetView(
            character = PetCharacter.PUSHISTIK,
            mood = PetMood.HAPPY,
            pose = rememberPoseProvider(rememberPetAnimation()),
            modifier = Modifier.size(MeetStage * 0.8f),
        )
    }
    Body(
        "Помоги Финни вырасти и накопить на цель.",
        "Планируй игровые монетки. Покупай еду, ухаживай и откладывай.",
    )
}

/** Белый круг под питомцем на первой странице. */
private val MeetStage = 160.dp

@Composable
private fun DecisionsPage() {
    Title("Решение за тобой!")
    // Решения различаются значком, а не заливкой: зелёный у нас — «получилось»,
    // и зелёная карточка «Нужное» читалась как оценка, а не как название.
    // Полосы через одну — белая и прозрачная: три пункта видно тремя и без рамок.
    Column(Modifier.fillMaxWidth()) {
        Decision(categoryIcon(Category.NEEDS), "Нужное", "Еда и мытьё — это сначала", striped = true)
        Decision(categoryIcon(Category.WANTS), "Хочется", "Игрушки и сладости — могут подождать", striped = false)
        Decision(SavingsIcon, "Отложить", "В копилку — на большую цель", striped = true)
    }
}

/** Кружок под картинкой — как круглая кнопка кита, но это рисунок, а не кнопка. */
private val ArtCircle = 64.dp

@Composable
private fun IconCircle(icon: FinneyIcons) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(ArtCircle).clip(CircleShape).background(Color.White),
    ) {
        FinneyIcon(icon, size = 36.dp)
    }
}

@Composable
private fun Decision(icon: FinneyIcons, title: String, text: String, striped: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GapBlock),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusCard))
            .background(if (striped) Color.White else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        IconCircle(icon)
        Column(Modifier.weight(1f)) {
            OutlinedText(title, style = MaterialTheme.typography.headlineMedium)
            Text(text, style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
        }
    }
}

@Composable
private fun PeriodPage(isReplay: Boolean) {
    Title("Как играть?")
    // Четыре шага уровня — картинкой и одной фразой, а не абзацем. Порядок держит сам
    // список сверху вниз, номера в кружках не нужны. Условия уровня подробно
    // показывает кнопка «Завершить уровень».
    Column(verticalArrangement = Arrangement.spacedBy(GapBlock), modifier = Modifier.fillMaxWidth()) {
        Step("Получай финки!") { Coin(size = ArtCircle) }
        Step("Составь план!") { IconCircle(FinneyIcons.Plan) }
        Step("Заботься о питомце!") {
            PetView(
                character = PetCharacter.PUSHISTIK,
                mood = PetMood.HAPPY,
                pose = rememberPoseProvider(rememberPetAnimation()),
                modifier = Modifier.size(ArtCircle),
            )
        }
        // У значка уровня вокруг круга место под кольцо шкалы — сам круг берём меньше.
        Step("Проходи уровни — питомец растёт!") { LevelBadge(level = 2, size = 52.dp) }
    }
    if (isReplay) {
        Body("Ошибаться не страшно.")
    } else {
        Body("Ошибаться не страшно.", "Для взрослых: в демо-режиме все игры открыты сразу.")
    }
}

/**
 * Шаг уровня: картинка слева и одна фраза. Картинки одного роста — подписи идут
 * ровной колонкой. TalkBack читает только фразу: картинка её повторяет.
 */
@Composable
private fun Step(text: String, art: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GapBlock),
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = text },
    ) {
        Box(Modifier.size(ArtCircle), contentAlignment = Alignment.Center) { art() }
        Text(text, style = MaterialTheme.typography.titleMedium, color = FinneyInk, modifier = Modifier.weight(1f))
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun OnboardingPreview() {
    FinneyTheme { OnboardingScreen(isReplay = false, onFinish = {}, onStartDemo = {}) }
}
