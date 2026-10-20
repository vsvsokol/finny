package ru.finney.pet.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finney.pet.content.ContentException
import ru.finney.pet.content.ContentParser
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.model.ChoresTask
import ru.finney.pet.domain.model.ReceiptTask
import ru.finney.pet.domain.model.ReserveTask
import ru.finney.pet.domain.model.SorterTask
import ru.finney.pet.domain.model.StandTask
import ru.finney.pet.domain.model.TaskDefinition
import ru.finney.pet.domain.model.TaskOutcome
import ru.finney.pet.domain.tasks.TaskChecks
import ru.finney.pet.domain.tasks.TaskEngines
import ru.finney.pet.domain.tasks.TaskEvaluation
import ru.finney.pet.domain.tasks.TaskGenerator
import ru.finney.pet.domain.tasks.TaskInput
import java.io.File
import kotlin.random.Random

/** Разброс чисел в мини-играх: docs/minigames.md, «Случайные числа». */
class TaskGeneratorTest {

    private fun template(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    /** Лавка: гостей от 6 до 10 через 2, в текстах — подстановки. */
    private val stand = template(
        """
        {
          "id": "stand_x", "theme": "planning", "engine": "stand", "title": "Лавка",
          "intro": "Придут {guests} гостей, лимон по {ingredient.price}.",
          "explainOk": "Лимонов нужно {bestStock}: это {bestCups} стаканов.",
          "explainFail": "Из {fewerStock} выйдет {fewerCups}.",
          "budget": 50,
          "ingredient": { "label": "Лимон", "price": 5, "yields": 2 },
          "cupPrice": 5,
          "guests": { "min": 6, "max": 10, "step": 2 },
          "forecast": "Жарко"
        }
        """,
    )

    private fun guests(seed: Long) = (TaskGenerator.generate(stand, seed) as StandTask).guests

    @Test
    fun `разброс — в пределах и через шаг`() {
        val seen = (1L..200L).map(::guests).toSet()
        assertEquals(setOf(6, 8, 10), seen)
    }

    @Test
    fun `одно зерно — одни и те же числа`() {
        (1L..50L).forEach { assertEquals(TaskGenerator.generate(stand, it), TaskGenerator.generate(stand, it)) }
    }

    /** «Конвейер» без разброса: вещи в контенте через одну — нужное, хочется, нужное. */
    private val sorter = template(
        """
        {
          "id": "sorter_x", "theme": "planning", "engine": "sorter", "title": "Конвейер",
          "intro": "Разложи.", "explainOk": "Да.", "explainFail": "Попробуй ещё.", "minCorrect": 6,
          "items": [
            { "id": "a", "label": "Яблоко", "category": "needs", "why": "Еда." },
            { "id": "b", "label": "Пончик", "category": "wants", "why": "Сладкое." },
            { "id": "c", "label": "Мыло", "category": "needs", "why": "Чистота." },
            { "id": "d", "label": "Мячик", "category": "wants", "why": "Игрушка." },
            { "id": "e", "label": "Молоко", "category": "needs", "why": "Еда." },
            { "id": "f", "label": "Леденец", "category": "wants", "why": "Сладкое." },
            { "id": "g", "label": "Суп", "category": "needs", "why": "Еда." },
            { "id": "h", "label": "Наклейки", "category": "wants", "why": "Игрушка." }
          ]
        }
        """,
    )

    private fun sorterOrder(seed: Long) = (TaskGenerator.generate(sorter, seed) as SorterTask).items.map { it.id }

    @Test
    fun `конвейер — вещи перемешаны, одно зерно — один порядок`() {
        val orders = (1L..50L).map(::sorterOrder)
        // Те же вещи, только порядок другой.
        orders.forEach { assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h"), it.sorted()) }
        (1L..50L).forEach { assertEquals(sorterOrder(it), sorterOrder(it)) }
        assertTrue("порядков мало: ${orders.toSet().size}", orders.toSet().size > 40)
        // Нет закономерности «через одну»: бывают две вещи одной корзины подряд.
        val sorted = TaskGenerator.base(sorter) as SorterTask
        val category = sorted.items.associate { it.id to it.category }
        assertTrue(orders.any { order -> order.zipWithNext().any { (x, y) -> category[x] == category[y] } })
        // Список игр по-прежнему показывает порядок из контента.
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h"), sorted.items.map { it.id })
    }

    @Test
    fun `вариант без случайности — минимум разброса`() {
        assertEquals(6, (TaskGenerator.base(stand) as StandTask).guests)
    }

    @Test
    fun `подстановки берут поле задания и числа движка`() {
        val task = (1L..200L).map { TaskGenerator.generate(stand, it) as StandTask }.first { it.guests == 10 }
        assertEquals("Придут 10 гостей, лимон по 5.", task.intro)
        assertEquals("Лимонов нужно 5: это 10 стаканов.", task.explainOk)
        assertEquals("Из 4 выйдет 8.", task.explainFail)
    }

    @Test
    fun `oneOf и pick выбирают из списка`() {
        val t = template(
            """
            {
              "id": "r", "theme": "planning", "engine": "reserve", "title": "Р", "intro": "{surprises.0.label}",
              "explainOk": "ок", "explainFail": "нет", "amount": 100,
              "spendings": { "pick": 2, "from": [
                { "id": "a", "label": "А", "price": 10, "category": "needs" },
                { "id": "b", "label": "Б", "price": 10, "category": "wants" },
                { "id": "c", "label": "В", "price": 10, "category": "wants" }
              ] },
              "surprises": [{ "oneOf": [
                { "label": "Зонт", "text": "стоит {surprises.0.price}", "price": 10 },
                { "label": "Рюкзак", "text": "стоит {surprises.0.price}", "price": 20 }
              ] }]
            }
            """,
        )
        val tasks = (1L..200L).map { TaskGenerator.generate(t, it) as ReserveTask }
        assertEquals(setOf("Зонт", "Рюкзак"), tasks.map { it.intro }.toSet())
        assertTrue(tasks.all { it.surprises.single().text == "стоит ${it.surprises.single().price}" })
        assertTrue(tasks.all { it.spendings.size == 2 })
        assertEquals(3, tasks.map { r -> r.spendings.map { it.id } }.toSet().size)
        // Порядок — как в списке: «a» никогда не после «b».
        assertTrue(tasks.all { r -> r.spendings.map { it.id }.let { it == it.sorted() } })
    }

    @Test
    fun `chance — элемент списка есть с заданной вероятностью`() {
        val t = template(
            """
            {
              "id": "r", "theme": "planning", "engine": "reserve", "title": "Р", "intro": "", "explainOk": "", "explainFail": "",
              "amount": 100,
              "spendings": [
                { "id": "a", "label": "А", "price": 10, "category": "needs" },
                { "id": "b", "label": "Б", "price": 10, "category": "wants" }
              ],
              "surprises": [
                { "chance": 100, "value": { "label": "Всегда", "text": "", "price": 5 } },
                { "chance": 30, "value": { "label": "Иногда", "text": "", "price": 5 } },
                { "chance": 0, "value": { "label": "Никогда", "text": "", "price": 5 } }
              ]
            }
            """,
        )
        val labels = (1L..1000L).map { seed -> (TaskGenerator.generate(t, seed) as ReserveTask).surprises.map { it.label } }
        assertTrue(labels.all { "Всегда" in it && "Никогда" !in it })
        val sometimes = labels.count { "Иногда" in it }
        assertTrue("выпало $sometimes из 1000", sometimes in 220..380)
        // Без случайности элемент есть.
        assertEquals(3, (TaskGenerator.base(t) as ReserveTask).surprises.size)
    }

    @Test
    fun `нерешаемые числа отбрасываются, иначе — вариант без случайности`() {
        // Денег хватает только на 2 гостей: из 1..1000 почти всё нерешаемо.
        val t = template(stand.toString().replace("\"budget\":50", "\"budget\":5").replace("{\"min\":6,\"max\":10,\"step\":2}", "{\"min\":2,\"max\":1000}"))
        (1L..30L).forEach {
            val task = TaskGenerator.generate(t, it)
            assertEquals(emptyList<String>(), TaskChecks.problems(task))
            assertEquals(2, (task as StandTask).guests)
        }
    }

    @Test
    fun `ошибки шаблона — понятным текстом`() {
        fun parse(tasks: String) = ContentParser.parse { name ->
            if (name == ContentParser.TASKS) tasks else File("src/main/assets/content", name).readText()
        }
        fun error(tasks: String): String = try {
            parse(tasks)
            ""
        } catch (e: ContentException) {
            e.errors.single()
        }
        val guests = "{\"min\":6,\"max\":10,\"step\":2}"
        assertTrue(error("[${stand.toString().replace(guests, "{\"min\":6,\"max\":9,\"step\":2}")}]").contains("целое число шагов"))
        assertTrue(error("[${stand.toString().replace("{guests} гостей", "{visitors} гостей")}]").contains("{visitors}"))
        assertTrue(error("[${stand.toString().replace("\"id\":\"stand_x\"", "\"id\":{\"oneOf\":[\"a\",\"b\"]}")}]").contains("поле id"))
    }

    // ---------- Настоящий контент ----------

    private val content = ContentParser.parse { File("src/main/assets/content", it).readText() }

    private fun texts(task: TaskDefinition): String = listOf(task.intro, task.explainOk, task.explainFail, task.toString()).joinToString()

    @Test
    fun `в контенте есть игры с разбросом`() {
        assertTrue(content.taskTemplates.keys.containsAll(listOf("game_lemonade", "game_cashier", "game_rainy", "game_chores")))
    }

    @Test
    fun `конвейеры из контента перемешиваются в игре`() {
        val sorters = listOf("game_sorter", "game_sorter_2", "game_sorter_3")
        assertTrue(content.taskTemplates.keys.containsAll(sorters))
        for (id in sorters) {
            val inFile = (content.task(id) as SorterTask).items.map { it.id }
            val orders = (1L..20L).map { (TaskGenerator.generate(content.taskTemplates.getValue(id), it) as SorterTask).items.map { i -> i.id } }
            assertTrue("$id: порядок не меняется", orders.any { it != inFile })
        }
    }

    /** Ошибки кассира — сколько их, на каких товарах и есть ли недодача — без одного решения на все попытки. */
    @Test
    fun `в чеках из контента ошибки каждый раз другие`() {
        val receipts = listOf("game_receipt", "game_receipt_2", "game_receipt_3")
        assertTrue(content.taskTemplates.keys.containsAll(receipts))
        for (id in receipts) {
            val tasks = (1L..200L).map { TaskGenerator.generate(content.taskTemplates.getValue(id), it) as ReceiptTask }
            val errors = tasks.map { TaskEngines.receiptErrors(it) }
            tasks.zip(errors).forEach { (task, e) ->
                val count = task.lines.filter { it.id in e.lines }.map { it.item }.distinct().size + if (e.changeShort > 0) 1 else 0
                assertTrue("$id: ошибок $count, а mistakes ${task.mistakes}", count in 1..3)
                assertFalse("$id: в итоге осталась общая фраза", task.explainFail.startsWith("Сверяй"))
            }
            // Ни один способ не решает все попытки: ни «только сдача», ни один и тот же товар.
            val onlyChange = tasks.count { (TaskEngines.evaluate(it, TaskInput.Flags(emptySet(), change = true)) as TaskEvaluation.Done).outcome == TaskOutcome.SUCCESS }
            assertTrue("$id: «только сдача» выигрывает $onlyChange из ${tasks.size}", onlyChange < tasks.size / 2)
            assertTrue("$id: сдачу всегда недодают", errors.any { it.changeShort == 0 })
            assertTrue("$id: сдачу никогда не недодают", errors.any { it.changeShort > 0 })
            val wrongItems = tasks.zip(errors).map { (t, e) -> t.lines.filter { it.id in e.lines }.map { it.item }.toSet() }
            assertTrue("$id: ошибка всегда на одних товарах", wrongItems.toSet().size > 5)
            assertTrue("$id: одни и те же покупки", tasks.map { t -> t.cart.map { it.id }.toSet() }.toSet().size > 1)
        }
    }

    @Test
    fun `чек с mistakes — одно зерно, одни ошибки`() {
        val template = content.taskTemplates.getValue("game_receipt_3")
        (1L..20L).forEach { assertEquals(TaskGenerator.generate(template, it), TaskGenerator.generate(template, it)) }
    }

    /** Каждое зерно даёт выигрываемую игру без недоставленных подстановок, и числа правда меняются. */
    @Test
    fun `случайные игры из контента решаемы на любом зерне`() {
        for ((id, template) in content.taskTemplates) {
            val tasks = (1L..300L).map { TaskGenerator.generate(template, it) }
            tasks.forEach { task ->
                assertEquals(id, task.id)
                assertEquals(emptyList<String>(), TaskChecks.problems(task))
                assertFalse("$id: осталась подстановка в ${texts(task)}", Regex("""\{[A-Za-z]""").containsMatchIn(texts(task)))
            }
            assertTrue("$id: числа не меняются", tasks.toSet().size > 1)
        }
    }

    /**
     * Подработка: цель — `oneOf` из нескольких, у каждой свой разброс цены (чек-лист рывка, раздел 5).
     * Цели меняются от зерна, имя подставляется в текст, и каждая набирается.
     */
    @Test
    fun `подработка — цель из нескольких, каждая решаема и подставляется в текст`() {
        val goals = Json.parseToJsonElement(
            """{ "oneOf": [
                { "label": "Мяч", "price": { "min": 60, "max": 70, "step": 10 } },
                { "label": "Наклейки", "price": { "min": 60, "max": 70, "step": 10 } },
                { "label": "Кубик", "price": { "min": 60, "max": 70, "step": 10 } }
            ] }""",
        )
        val intro = Json.parseToJsonElement("\"Финни копит на «{goal.label}»\"")
        val template = JsonObject(content.taskTemplates.getValue("game_chores") + mapOf("goal" to goals, "intro" to intro))

        val tasks = (1L..60L).map { TaskGenerator.generate(template, it) as ChoresTask }

        assertEquals(setOf("Мяч", "Наклейки", "Кубик"), tasks.map { it.goal.label }.toSet())
        tasks.forEach { task ->
            assertEquals(emptyList<String>(), TaskChecks.problems(task))
            assertEquals("Финни копит на «${task.goal.label}»", task.intro)
        }
        assertEquals("без случайности — первая цель", "Мяч", (TaskGenerator.base(template) as ChoresTask).goal.label)
    }

    /** «Дождливый день» на фиксированных зёрнах: пусто — неудача, разумно — успех с бонусом, жадно — неудача. */
    @Test
    fun `дождливый день — бонус за баланс, отказ от желаемого не провал`() {
        val template = content.taskTemplates.getValue("game_rainy")
        val empty = TaskInput.Reserve(setOf("food", "soap"), emptySet())
        val safe = TaskInput.Reserve(setOf("food", "soap", "icecream"), emptySet())
        val all = setOf("food", "soap", "ball", "stickers", "icecream")
        var stormy = 0
        for (seed in 1L..100L) {
            val task = TaskGenerator.generate(template, seed) as ReserveTask
            assertEquals("зерно $seed: зонт и рюкзак вместе", 25, task.worstCase)
            // Без радостей — пройдено, но без бонуса, хотя запас самый большой.
            val joyless = TaskEngines.evaluate(task, empty) as TaskEvaluation.Done
            assertEquals("зерно $seed", TaskOutcome.SUCCESS, joyless.outcome)
            assertFalse("зерно $seed: без радостей бонуса нет", joyless.bonus)
            val ok = TaskEngines.evaluate(task, safe) as TaskEvaluation.Done
            assertEquals("зерно $seed", TaskOutcome.SUCCESS, ok.outcome)
            assertTrue("зерно $seed: запас 25 на все сюрпризы — бонус", ok.bonus)
            if (task.surprises.isEmpty()) continue
            stormy++
            // Жадно: всё желаемое, запаса 5 — на любой сюрприз приходится откладывать желаемое.
            // Сначала нужное — верный выбор: пройдено, но без бонуса.
            val shortage = TaskEngines.surprisesTotal(task) - TaskEngines.reserveLeft(task, all)
            val dropped = listOf("ball", "stickers", "icecream").let { if (shortage <= 10) it.take(1) else it.take(2) }.toSet()
            val greedy = TaskEngines.evaluate(task, TaskInput.Reserve(all, dropped)) as TaskEvaluation.Done
            assertEquals("зерно $seed", TaskOutcome.SUCCESS, greedy.outcome)
            assertFalse("зерно $seed: отложили желаемое — бонуса нет", greedy.bonus)
        }
        assertTrue("сюрпризы выпадают", stormy > 50)
    }

    @Test
    fun `вариант без случайности — тот, что в списке заданий`() {
        for ((id, template) in content.taskTemplates) assertEquals(content.task(id), TaskGenerator.base(template))
    }

    // ---------- Попытка ----------

    @Test
    fun `ответ проверяется по числам зерна, и зерно сохраняется в попытке`() {
        val game = Game(content, Random(1))
        val fresh = game.selectGoal(game.newGame(isDemo = true), content.goals.first().id).state()
        val start = game.confirmPlan(fresh, 20, 10, 10).state()
        val seeds = (1L..200L).map { it to game.task("game_lemonade_2", it) as StandTask }
        val (seed, task) = seeds.first { it.second.guests != 7 }
        val other = seeds.first { TaskEngines.bestStock(it.second) != TaskEngines.bestStock(task) }.first

        val ok = game.submitTask(start, task.id, TaskInput.Stock(TaskEngines.bestStock(task)), seed).submitted()
        assertEquals(TaskOutcome.SUCCESS, ok.outcome)
        assertEquals(seed, ok.state.attempts.last().seed)

        val wrong = game.submitTask(start, task.id, TaskInput.Stock(TaskEngines.bestStock(task)), other).submitted()
        assertEquals(TaskOutcome.FAIL, wrong.outcome)
    }

    @Test
    fun `бонус движка добавляется к награде один раз`() {
        val game = Game(content, Random(1))
        val fresh = game.selectGoal(game.newGame(isDemo = true), content.goals.first().id).state()
        val start = game.confirmPlan(fresh, 20, 10, 10).state()
        // Без зерна — вариант без случайности: случились оба сюрприза, 10 + 15.
        val keepIcecream = TaskInput.Reserve(setOf("food", "soap", "icecream"), emptySet())
        val first = game.submitTask(start, "game_rainy", keepIcecream).submitted()
        val rates = content.economy.taskReward
        assertEquals(TaskOutcome.SUCCESS, first.outcome)
        assertEquals(rates.bonus, first.bonus)
        assertEquals(rates.success + rates.bonus, first.reward)
        assertTrue(first.state.attempts.last().bonus)

        val again = game.submitTask(first.state, "game_rainy", keepIcecream).submitted()
        assertEquals(0, again.reward)
        assertEquals(0, again.bonus)
    }

    @Test
    fun `новая попытка — новое зерно`() {
        val game = Game(content, Random(1))
        assertNotEquals(game.newTaskSeed(), game.newTaskSeed())
    }
}
