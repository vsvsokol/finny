package ru.finney.pet.domain.tasks

import ru.finney.pet.domain.model.ReceiptItem
import ru.finney.pet.domain.model.ReceiptLine
import ru.finney.pet.domain.model.ReceiptTask
import kotlin.random.Random

/**
 * Ошибки кассира для «Проверь чек», каждый раз новые. Раньше они были записаны в tasks.json:
 * в первом чеке всегда сок пробит дважды, в третьем всегда недодана сдача, и после первой
 * попытки игру решали по памяти, не сверяя чек (20.10).
 *
 * У задания с `mistakes` строки, «дали» и сдачу пишет [make]: какие товары с ошибкой, какая
 * ошибка (лишняя строка или цена выше ценника) и недодана ли сдача — от зерна попытки. Ошибок
 * ровно [ReceiptTask.mistakes], на товар — не больше одной, все — в пользу кассира, как требует
 * [TaskChecks]. Тексты итога пишутся здесь же: в них названы ошибки этой попытки.
 */
internal object ReceiptMaker {

    /** Чем Финни платит: самая маленькая купюра, которой хватает на чек. */
    private val NOTES = listOf(20, 50, 100, 200)

    /** На сколько кассир завышает цену: заметно, но не в разы. */
    private val OVER = 2..5

    /** Сколько недодаёт сдачи — на сколько ошибся, отсчитывая монеты. */
    private val SHORT = listOf(1, 2, 5, 10)

    private sealed interface Mistake {
        data class Twice(val item: ReceiptItem) : Mistake
        data class Over(val item: ReceiptItem, val by: Int) : Mistake
        data class Short(val by: Int, val fair: Int) : Mistake
    }

    fun make(task: ReceiptTask, random: Random): ReceiptTask {
        val count = task.mistakes ?: return task
        // Где ошибиться — товары и сдача вперемешку, по одной ошибке на место.
        val spots: List<ReceiptItem?> = (task.cart + null).shuffled(random).take(count.coerceIn(1, task.cart.size + 1))
        val onItems = spots.filterNotNull().associateTo(LinkedHashMap()) { it.id to itemMistake(it, random) }
        var lines = linesOf(task.cart, onItems)
        var short: Mistake.Short? = null
        if (null in spots) {
            val fair = changeOf(lines)
            val by = SHORT.filter { it < fair }.randomOrNull(random)
            if (by != null) {
                short = Mistake.Short(by, fair)
            } else {
                // Сдачи почти нет, недодать нечего — кассир ошибается на другом товаре.
                task.cart.firstOrNull { it.id !in onItems }?.let { onItems[it.id] = itemMistake(it, random) }
                lines = linesOf(task.cart, onItems)
            }
        }
        val total = lines.sumOf { it.price }
        val made = task.copy(lines = lines, paid = paidFor(total), change = changeOf(lines) - (short?.by ?: 0))
        return made.copy(explainOk = explainOk(made), explainFail = explainFail(onItems.values + listOfNotNull(short)))
    }

    private fun itemMistake(item: ReceiptItem, random: Random): Mistake =
        if (random.nextBoolean()) Mistake.Twice(item) else Mistake.Over(item, OVER.random(random))

    private fun linesOf(cart: List<ReceiptItem>, mistakes: Map<String, Mistake>): List<ReceiptLine> {
        var n = 0
        return cart.flatMap { item ->
            val mistake = mistakes[item.id]
            val price = if (mistake is Mistake.Over) item.price + mistake.by else item.price
            val times = if (mistake is Mistake.Twice) item.qty + 1 else item.qty
            List(times) { ReceiptLine(id = "l${++n}", item = item.id, price = price) }
        }
    }

    private fun paidFor(total: Int): Int = NOTES.firstOrNull { it > total } ?: ((total / 100 + 1) * 100)

    /** Честная сдача от суммы чека — какой бы та ни была. */
    private fun changeOf(lines: List<ReceiptLine>): Int = lines.sumOf { it.price }.let { paidFor(it) - it }

    private fun explainOk(task: ReceiptTask): String {
        val all = TaskInput.Flags(TaskEngines.receiptErrors(task).lines, change = TaskEngines.receiptErrors(task).changeShort > 0)
        val refund = ((TaskEngines.evaluate(task, all) as? TaskEvaluation.Done)?.details as? TaskDetails.Receipt)?.refund ?: 0
        return "Ты нашёл все ошибки — кассир вернул $refund! Проверять чек и сдачу — это нормально."
    }

    private fun explainFail(mistakes: List<Mistake>): String {
        val said = mistakes.map {
            when (it) {
                is Mistake.Twice -> "${accusative(it.item.label)} пробили лишний раз"
                is Mistake.Over -> "${accusative(it.item.label)} пробили по ${it.item.price + it.by}, а на ценнике ${it.item.price}"
                is Mistake.Short -> "сдачи нужно ${it.fair}, а дали ${it.fair - it.by}"
            }
        }
        return said.joinToString("; ").replaceFirstChar { it.uppercase() } +
            ". Сверяй каждую строку с ценником в пакете и пересчитывай сдачу."
    }

    /**
     * «Пробили что?» — у товаров женского рода на -а/-я другая форма: «воду», «шоколадку».
     * У остальных товаров магазина она совпадает с названием: «сок», «молоко», «мороженое».
     */
    private fun accusative(label: String): String {
        val word = label.lowercase()
        return when {
            word.endsWith("а") -> word.dropLast(1) + "у"
            word.endsWith("я") -> word.dropLast(1) + "ю"
            else -> word
        }
    }
}
