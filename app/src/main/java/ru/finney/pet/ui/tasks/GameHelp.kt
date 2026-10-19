package ru.finney.pet.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.finney.pet.domain.model.*
import ru.finney.pet.ui.components.FinneyButton
import ru.finney.pet.ui.components.FinneyPanel
import ru.finney.pet.ui.theme.FinneyInk

data class GameHelp(val role: String, val action: String, val goal: String)

fun gameHelp(task: TaskDefinition): GameHelp = when (task) {
    is SorterTask -> GameHelp("Ты помогаешь Финни разобрать покупки.",
        "Перетащи первый предмет в корзину внизу. Или просто нажми нужную корзину.",
        "Еда и уход — «Нужное». Игрушки и сладости — «Хочется».")
    is BasketTask -> GameHelp("Ты покупатель.",
        "Нажимай товары на полках. Собери список и нажми кнопку кассы.",
        "Возьми нужные покупки и уложись в сумму. Нажми товар ещё раз, чтобы убрать.")
    is GoalRaceTask -> GameHelp("Ты выбираешь покупки Финни на несколько дней.",
        "В каждый день выбери «Купить» или «Пропустить». Остаток дня пойдёт в копилку.",
        "Собери сумму на цель. Покупки по дороге необязательны: их можно пропускать.")
    is ReserveTask -> GameHelp("Ты планируешь неделю Финни.",
        "Нужное уже в плане. Желаемое выбирай сам, остаток станет запасом. Затем начни неделю.",
        "Запас помогает оплатить непредвиденное. Если его мало, перенеси желаемое: это тоже верное решение.")
    is StandTask -> GameHelp("Ты продаёшь лимонад вместе с Финни.",
        "Купи сырьё для гостей. В открытой лавке нажимай «Налить» — по стакану каждому.",
        "Выручка — всё, что заплатили гости. Заработок — выручка минус закупка.")
    is ChangeTask -> GameHelp(
        when {
            task.rounds.all { it.mode == ChangeMode.GIVE } -> "Ты кассир. Покупатели приходят к тебе."
            task.rounds.all { it.mode == ChangeMode.EXACT } -> "Ты покупатель. Помоги Финни заплатить ровно."
            else -> "У кассы Финни будет и покупать, и выдавать сдачу. Роль подписана в каждом раунде."
        },
        "Нажимай монеты, чтобы положить их на поднос. Нажми монету на подносе, чтобы убрать.",
        "Сдача — дали минус цена. Оплата без сдачи — ровно цена.")
    is ReceiptTask -> GameHelp("Ты покупатель. Другой питомец — кассир.",
        "Сравни пакет и чек. Нажми строки с неверной ценой или лишним товаром. Потом проверь сдачу.",
        "Сначала кассир исправит чек и вернёт переплату. Затем посчитай, всей ли сдачи хватает.")
    is ChoresTask -> GameHelp("Ты выбираешь подработки для Финни.",
        "Выбери день наверху, затем дело внизу. Оно появится в выбранном дне. Нажми дело в дне, чтобы убрать.",
        "Заработай на цель и оставь дни отдыха. Меняй дела: за повтор подряд платят меньше.")
    else -> GameHelp("Ты помогаешь Финни принять решение.", "Измени суммы или выбери покупки, затем проверь решение.", task.intro)
}

@Composable
fun GameHelpDialog(task: TaskDefinition, onClose: () -> Unit) {
    val help = gameHelp(task)
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            FinneyPanel(title = "Как играть") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (line in listOf(help.role, help.action, help.goal)) {
                        Text(line, style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
                    }
                    Text("Учебные деньги — внутри игры. В кошелёк Финни поступает только награда.",
                        style = MaterialTheme.typography.bodyLarge, color = FinneyInk)
                    FinneyButton("Продолжить игру", onClick = onClose)
                }
            }
        }
    }
}
