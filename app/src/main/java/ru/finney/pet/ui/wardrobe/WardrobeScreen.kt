package ru.finney.pet.ui.wardrobe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.finney.pet.ui.components.FeedbackDialog
import ru.finney.pet.ui.components.FillBar
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyScreen
import ru.finney.pet.ui.components.OutlinedText
import ru.finney.pet.ui.pet.PetMood
import ru.finney.pet.ui.pet.PetView
import ru.finney.pet.ui.pet.accessoryArt
import ru.finney.pet.ui.pet.rememberPetAnimation
import ru.finney.pet.ui.pet.rememberPoseProvider
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.FinneySand
import ru.finney.pet.ui.theme.FinneyYellow

// Гардероб — отдельный экран, а не панель поверх комнаты: на главном вокруг питомца
// мебель, шкала, плашки и кнопки, и примерка терялась среди них. Здесь только
// питомец крупно и его вещи. Нажатие на вещь — надеть, на надетую — снять.

@Composable
fun WardrobeScreen(
    onBack: () -> Unit,
    onOpenGoals: () -> Unit,
    viewModel: WardrobeViewModel = viewModel(factory = WardrobeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        WardrobeUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = FinneyInk)
        }
        is WardrobeUiState.Ready -> {
            WardrobeContent(
                state = s,
                onWear = viewModel::wear,
                onTakeOff = viewModel::takeOff,
                onOpenGoals = onOpenGoals,
                onBack = onBack,
            )
            FeedbackDialog(feedback = null, rejection = s.rejection, onDismiss = viewModel::dismissRejection)
        }
    }
}

@Composable
internal fun WardrobeContent(
    state: WardrobeUiState.Ready,
    onWear: (String) -> Unit,
    onTakeOff: (String?) -> Unit,
    onOpenGoals: () -> Unit,
    onBack: () -> Unit,
) {
    val animation = rememberPetAnimation()
    FinneyScreen(
        scrollable = true,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        onClose = onBack,
    ) {
        OutlinedText("Гардероб", style = MaterialTheme.typography.headlineLarge)

        // Питомец крупно на спокойном круге. Нажатие — подпрыгнет: видно, как сидит шляпа.
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(CircleShape)
                .background(FinneySand)
                .border(3.dp, FinneyInk, CircleShape),
            contentAlignment = Alignment.BottomCenter,
        ) {
            PetView(
                character = state.appearance.character,
                coloring = state.coloring,
                accessories = state.worn,
                mood = PetMood.HAPPY,
                pose = rememberPoseProvider(animation),
                modifier = Modifier
                    .size(220.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Подпрыгнуть",
                        onClick = { animation.playJoy() },
                    ),
            )
        }

        // «Без шляпы» — первой плиткой: снять так же просто, как надеть.
        Tile(
            label = "Без шляпы",
            status = null,
            selected = state.worn.isEmpty(),
            enabled = true,
            onClick = { onTakeOff(null) },
        )
        state.items.chunked(2).forEach { pair ->
            // Плитки в ряду одной высоты: подпись «как получить» у одной не растягивает
            // соседку, и сетка не перестраивается, когда шляпу получили.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { entry ->
                    val worn = entry.item.id in state.worn
                    Tile(
                        label = entry.item.label,
                        // Слова остались только у ещё не полученной шляпы — как её получить.
                        status = entry.howToGet.takeUnless { entry.owned },
                        progress = entry.progress,
                        art = accessoryArt(entry.item.id)?.res,
                        selected = worn,
                        enabled = entry.owned,
                        onClick = { if (worn) onTakeOff(entry.item.id) else onWear(entry.item.id) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (state.items.any { !it.owned }) {
            Text(
                "Шляпы получают за цели в копилке.",
                style = MaterialTheme.typography.bodyLarge,
                color = FinneyInk,
                textAlign = TextAlign.Center,
            )
            FinneyButton(text = "В копилку", onClick = onOpenGoals)
        }
    }
}

/**
 * Плитка вещи. Надетое отмечено и заливкой, и словом «надето» (ТЗ п. 3.6).
 * Чужая вещь бледная и не нажимается, но подписана, как её получить.
 * [progress] — сколько накоплено на цель из скольких: полосой под подписью.
 */
@Composable
private fun Tile(
    label: String,
    status: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    art: Int? = null,
    progress: Pair<Int, Int>? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    // «✓» — значком поверх угла, а не строкой внутри: надели шляпу — плитка
    // не вырастает, и всё ниже не съезжает.
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                // Надето — жёлтое с толстой рамкой и «✓»: выбор, а не «получилось».
                .background(if (selected) FinneyYellow else if (enabled) FinneyCream else FinneySand)
                .border(if (selected) 3.dp else 2.dp, FinneyInk, shape)
                // Название и «надето» — для TalkBack; на плитке — картинка и «✓».
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    stateDescription = if (selected) "надето" else if (enabled) "не надето" else "ещё нет"
                }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (art != null) {
                Image(
                    painter = painterResource(art),
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .alpha(if (enabled) 1f else 0.45f),
                )
            }
            // Без рисунка («Без шляпы») без подписи не понять — она остаётся.
            if (art == null) Text(label, style = MaterialTheme.typography.titleMedium, color = FinneyInk, textAlign = TextAlign.Center)
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FinneyInk, textAlign = TextAlign.Center) }
            progress?.let { (saved, price) -> FillBar(saved, price, Modifier.fillMaxWidth(), height = 10.dp) }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(FinneyCream)
                    .border(2.dp, FinneyInk, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("✓", style = MaterialTheme.typography.titleMedium, color = FinneyInk) }
        }
    }
}
