package ru.finney.pet.ui.debug

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.finney.pet.domain.model.PeriodPhase
import ru.finney.pet.domain.model.PetStats
import ru.finney.pet.ui.components.FinneyPanel
import ru.finney.pet.ui.room.InteriorPreview
import ru.finney.pet.ui.room.Interiors
import ru.finney.pet.ui.room.RoomInterior
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneyTheme

/**
 * Панель отладки поверх главного. Не отдельный экран: так не нужен новый
 * маршрут в `navigation/`, а питомец остаётся виден — поставил сытость 10,
 * закрыл панель и сразу видишь, как он погрустнел.
 */
@Composable
fun DebugPanel(
    onDismiss: () -> Unit,
    /** Список всех мини-игр: ребёнку он не показывается, у него — одна игра на уровень. */
    onOpenTasks: () -> Unit,
    modifier: Modifier = Modifier,
    /** Облик питомца поверх уровня — посмотреть стадии роста; null — по уровню. */
    previewStage: Int? = null,
    onPreviewStage: (Int?) -> Unit = {},
    viewModel: DebugViewModel = viewModel(factory = DebugViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DebugEvent.Wiped -> context.relaunch()
                is DebugEvent.Rejected -> Toast.makeText(context, event.reason, Toast.LENGTH_LONG).show()
            }
        }
    }

    state?.let {
        DebugContent(
            state = it,
            onAddMoney = viewModel::addMoney,
            onSetStat = viewModel::setStat,
            onSkipPlan = viewModel::skipPlan,
            onClosePeriod = viewModel::closePeriod,
            onFinishSleep = viewModel::finishSleep,
            onWipe = viewModel::wipeAll,
            onOpenTasks = onOpenTasks,
            previewStage = previewStage,
            onPreviewStage = onPreviewStage,
            interior = InteriorPreview.current,
            onInterior = { InteriorPreview.current = it },
            onDismiss = onDismiss,
            modifier = modifier,
        )
    }
}

@Composable
private fun DebugContent(
    state: DebugUiState,
    onAddMoney: (Int) -> Unit,
    onSetStat: (DebugStat, Int) -> Unit,
    onSkipPlan: () -> Unit,
    onClosePeriod: () -> Unit,
    onWipe: () -> Unit,
    onFinishSleep: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    previewStage: Int? = null,
    onPreviewStage: (Int?) -> Unit = {},
    interior: RoomInterior = Interiors.Default,
    onInterior: (RoomInterior) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Стирание — в два нажатия: одно случайное не должно сносить профиль.
    var confirmWipe by rememberSaveable { mutableStateOf(false) }

    FinneyPanel(title = "Отладка", onClose = onDismiss, modifier = modifier) {
        Column(
            // Кнопка «Закрыть» ниже должна оставаться на экране: прокручивается
            // только содержимое.
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Line("Деньги: ${state.balance}")
            Buttons(
                "−50" to { onAddMoney(-50) },
                "+10" to { onAddMoney(10) },
                "+50" to { onAddMoney(50) },
                "+500" to { onAddMoney(500) },
                "Обнулить" to { onAddMoney(-state.balance) },
            )

            DebugStat.entries.forEach { stat ->
                val value = when (stat) {
                    DebugStat.SATIETY -> state.stats.satiety
                    DebugStat.HYGIENE -> state.stats.hygiene
                    DebugStat.MOOD -> state.stats.mood
                    DebugStat.ENERGY -> state.stats.energy
                }
                Line("${stat.label}: $value")
                Buttons(
                    "0" to { onSetStat(stat, 0) },
                    "−20" to { onSetStat(stat, value - 20) },
                    "+20" to { onSetStat(stat, value + 20) },
                    "100" to { onSetStat(stat, 100) },
                )
            }

            if (state.sleeping) Buttons("Досыпать сейчас" to onFinishSleep)

            Line("Период ${state.periodNumber}: ${state.phase.label}")
            Buttons(
                "Пустой план" to onSkipPlan,
                "Закрыть период" to onClosePeriod,
            )

            Buttons("Все мини-игры" to onOpenTasks)

            // Только вид: уровень и стадия в игре не меняются.
            Line("Облик питомца: ${previewStage?.let { "как на уровне $it" } ?: "по уровню"}")
            Buttons(
                "1" to { onPreviewStage(1) },
                "2" to { onPreviewStage(2) },
                "4" to { onPreviewStage(4) },
                "По уровню" to { onPreviewStage(null) },
            )

            // Тоже только вид и до перезапуска: купить интерьер в игре пока нельзя.
            Line("Комната: ${interior.label}")
            Buttons(*Interiors.all.map { it.label to { onInterior(it) } }.toTypedArray())

            Buttons(
                if (confirmWipe) {
                    "Точно стереть всё?" to onWipe
                } else {
                    "Стереть все данные" to { confirmWipe = true }
                },
            )
        }
    }
}

@Composable
private fun Line(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = FinneyInk)
}

@Composable
private fun Buttons(vararg actions: Pair<String, () -> Unit>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEach { (text, onClick) ->
            OutlinedButton(onClick = onClick) {
                Text(text, style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
            }
        }
    }
}

private val PeriodPhase.label: String
    get() = when (this) {
        PeriodPhase.PLANNING -> "план не подтверждён"
        PeriodPhase.ACTIVE -> "план подтверждён, можно покупать"
        PeriodPhase.CLOSED -> "закрыт"
    }

/**
 * Запуск с нуля после стирания. Новая задача — новый стек и новые ViewModel,
 * поэтому стартовый экран считается заново и ведёт на первый запуск. Иначе
 * главный остался бы висеть без профиля.
 */
private fun Context.relaunch() {
    val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
}

@Preview(widthDp = 360)
@Composable
private fun DebugContentPreview() {
    FinneyTheme {
        DebugContent(
            state = DebugUiState(PetStats(30, 70, 50, 40), balance = 40, periodNumber = 2, phase = PeriodPhase.ACTIVE),
            onAddMoney = {}, onSetStat = { _, _ -> }, onSkipPlan = {}, onClosePeriod = {}, onWipe = {}, onDismiss = {},
        )
    }
}
