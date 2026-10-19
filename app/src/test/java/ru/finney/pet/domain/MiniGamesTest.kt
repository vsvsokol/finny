package ru.finney.pet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finney.pet.content.ContentValidator
import ru.finney.pet.domain.model.BasketRule
import ru.finney.pet.domain.model.BasketTask
import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.ChangeMode
import ru.finney.pet.domain.model.ChangeRound
import ru.finney.pet.domain.model.ChangeTask
import ru.finney.pet.domain.model.Chore
import ru.finney.pet.domain.model.ChoresTask
import ru.finney.pet.domain.model.GoalRaceTask
import ru.finney.pet.domain.model.RaceEvent
import ru.finney.pet.domain.model.RaceGoal
import ru.finney.pet.domain.model.ReceiptItem
import ru.finney.pet.domain.model.ReceiptLine
import ru.finney.pet.domain.model.ReceiptTask
import ru.finney.pet.domain.model.ReserveTask
import ru.finney.pet.domain.model.ShelfItem
import ru.finney.pet.domain.model.SortItem
import ru.finney.pet.domain.model.SorterTask
import ru.finney.pet.domain.model.Spending
import ru.finney.pet.domain.model.StandIngredient
import ru.finney.pet.domain.model.StandTask
import ru.finney.pet.domain.model.Surprise
import ru.finney.pet.domain.model.TaskOutcome
import ru.finney.pet.domain.model.TaskTheme
import ru.finney.pet.domain.tasks.ReceiptErrors
import ru.finney.pet.domain.tasks.TaskDetails
import ru.finney.pet.domain.tasks.TaskEngines
import ru.finney.pet.domain.tasks.TaskEvaluation
import ru.finney.pet.domain.tasks.TaskInput
import ru.finney.pet.domain.tasks.TaskInputError

/** Движки мини-игр, docs/minigames.md. Настоящий контент проверяет ContentTest. */
class MiniGamesTest {

    private fun TaskEvaluation.outcome() = (this as TaskEvaluation.Done).outcome
    private fun TaskEvaluation.error() = (this as TaskEvaluation.Invalid).error

    // ---------- sorter ----------

    private val sorter = SorterTask(
        id = "s", theme = TaskTheme.PLANNING, title = "", intro = "", explainOk = "", explainFail = "",
        items = listOf(
            SortItem("apple", "", Category.NEEDS, "еда"),
            SortItem("candy", "", Category.WANTS, "сладость"),
            SortItem("soap", "", Category.NEEDS, "уход"),
        ),
        minCorrect = 2,
    )

    @Test
    fun `sorter — считает верные ответы и перечисляет ошибки`() {
        val one = TaskEngines.evaluate(
            sorter,
            TaskInput.Sorting(mapOf("apple" to Category.NEEDS, "candy" to Category.NEEDS, "soap" to Category.NEEDS)),
        )
        assertEquals(TaskEvaluation.Done(TaskOutcome.SUCCESS, TaskDetails.Sorting(2, 3, listOf("candy"))), one)

        val two = TaskEngines.evaluate(
            sorter,
            TaskInput.Sorting(mapOf("apple" to Category.WANTS, "candy" to Category.NEEDS, "soap" to Category.NEEDS)),
        )
        assertEquals(TaskOutcome.FAIL, two.outcome())
    }

    @Test
    fun `sorter — разложено не всё или чужая вещь — ошибка ввода`() {
        assertEquals(
            TaskInputError.NotAllSorted(2),
            TaskEngines.evaluate(sorter, TaskInput.Sorting(mapOf("apple" to Category.NEEDS))).error(),
        )
        assertEquals(
            TaskInputError.UnknownItem("robot"),
            TaskEngines.evaluate(sorter, TaskInput.Sorting(mapOf("robot" to Category.WANTS))).error(),
        )
    }

    // ---------- basket: новые правила списка ----------

    private val shopping = BasketTask(
        id = "b", theme = TaskTheme.SHOPPING, title = "", intro = "", explainOk = "", explainFail = "",
        limit = 50,
        shelf = listOf(
            ShelfItem("apple2", "", 10, Category.NEEDS, qty = 2),
            ShelfItem("apple5", "", 20, Category.NEEDS, qty = 5),
            ShelfItem("soap", "", 10, Category.NEEDS),
            ShelfItem("soap_liquid", "", 15, Category.NEEDS),
            ShelfItem("chocolate", "", 15, Category.WANTS),
        ),
        rules = listOf(
            BasketRule.MinQty(listOf("apple2", "apple5"), qty = 4, label = "4 яблока"),
            BasketRule.AnyOf(listOf("soap", "soap_liquid"), label = "Мыло"),
        ),
    )

    @Test
    fun `basket — minQty считает штуки в упаковках, anyOf — любой из товаров`() {
        fun evaluate(vararg items: String) = TaskEngines.evaluate(shopping, TaskInput.Basket(items.toSet()))

        assertEquals(TaskEvaluation.Done(TaskOutcome.SUCCESS, TaskDetails.Basket(45)), evaluate("apple5", "soap_liquid", "soap"))
        assertEquals(TaskOutcome.FAIL, evaluate("apple2", "soap").outcome())
        assertEquals(TaskOutcome.FAIL, evaluate("apple5", "chocolate").outcome())
        assertEquals(10, (evaluate("apple5", "soap_liquid", "chocolate", "apple2").error() as TaskInputError.OverLimit).shortage)
    }

    @Test
    fun `ruleMet — галочки списка совпадают с оценкой`() {
        val (apples, soap) = shopping.rules
        assertFalse(TaskEngines.ruleMet(shopping, apples, setOf("apple2")))
        assertTrue(TaskEngines.ruleMet(shopping, apples, setOf("apple2", "apple5")))
        assertTrue(TaskEngines.ruleMet(shopping, soap, setOf("soap_liquid")))
        assertFalse(TaskEngines.ruleMet(shopping, soap, emptySet()))
    }

    // ---------- goal_race ----------

    private val race = GoalRaceTask(
        id = "r", theme = TaskTheme.SAVINGS, title = "", intro = "", explainOk = "", explainFail = "",
        goal = RaceGoal("Самокат", 50),
        days = 6,
        incomePerDay = 10,
        events = listOf(RaceEvent(4, "Мороженое", 5), RaceEvent(5, "Кино", 10)),
    )

    @Test
    fun `goal_race — сумма взносов против цены, соблазны считаются по потраченному`() {
        assertEquals(
            TaskEvaluation.Done(TaskOutcome.SUCCESS, TaskDetails.GoalRace(saved = 50, shortfall = 0, eventsTaken = 1, mood = 2), bonus = true),
            TaskEngines.evaluate(race, TaskInput.DailyDeposits(listOf(10, 10, 10, 5, 5, 10))),
        )
        assertEquals(
            TaskEvaluation.Done(TaskOutcome.FAIL, TaskDetails.GoalRace(saved = 40, shortfall = 10, eventsTaken = 2, mood = 3)),
            TaskEngines.evaluate(race, TaskInput.DailyDeposits(listOf(10, 10, 10, 5, 0, 5))),
        )
    }

    /** Как «Самокат» в контенте: свободных монет 10, соблазнов три, сердечек три. */
    private val moodRace = race.copy(
        events = listOf(RaceEvent(2, "Наклейки", 5), RaceEvent(4, "Мороженое", 5), RaceEvent(5, "Кино", 10)),
    )

    private fun raceOf(task: GoalRaceTask, vararg deposits: Int) =
        (TaskEngines.evaluate(task, TaskInput.DailyDeposits(deposits.toList())) as TaskEvaluation.Done)

    @Test
    fun `goal_race — откладывать всё больше не лучшая стратегия`() {
        val saveAll = raceOf(moodRace, 10, 10, 10, 10, 10, 10)
        // Копилка полнее всех, но Финни ни разу себя не порадовал: отказ — не провал,
        // цель засчитана, но без бонуса.
        assertEquals(TaskOutcome.SUCCESS, saveAll.outcome)
        assertFalse(saveAll.bonus)
        assertEquals(TaskDetails.GoalRace(saved = 60, shortfall = 0, eventsTaken = 0, mood = 0), saveAll.details)

        // Одна маленькая радость — и цель, и настроение, и бонус.
        val balanced = raceOf(moodRace, 10, 5, 10, 10, 10, 10)
        assertEquals(TaskOutcome.SUCCESS, balanced.outcome)
        assertTrue(balanced.bonus)
        assertEquals(1, (balanced.details as TaskDetails.GoalRace).mood)

        // Всё на радости — Финни счастлив, но цель не набрана.
        assertEquals(TaskOutcome.FAIL, raceOf(moodRace, 10, 5, 10, 5, 0, 10).outcome)
    }

    @Test
    fun `goal_race — сердечко гаснет за отказ, загорается за радость, не выше старта`() {
        // Наклейки взял (3, выше не бывает), от мороженого отказался (2), кино взял (3).
        assertEquals(listOf(3, 3, 3, 2, 3, 3), TaskEngines.raceMood(moodRace, listOf(10, 5, 10, 10, 0, 10)))
    }

    @Test
    fun `goal_race — погасшие сердечки поздняя радость не возвращает`() {
        val lateOnly = race.copy(
            mood = 2,
            events = listOf(RaceEvent(1, "Наклейки", 5), RaceEvent(2, "Мороженое", 5), RaceEvent(6, "Кино", 5)),
        )
        // Два отказа подряд — ноль. Радость в последний день уже не выручает.
        assertEquals(listOf(1, 0, 0, 0, 0, 0), TaskEngines.raceMood(lateOnly, listOf(10, 10, 10, 10, 10, 5)))
        val late = raceOf(lateOnly, 10, 10, 10, 10, 10, 5)
        assertEquals(TaskOutcome.SUCCESS, late.outcome)
        assertFalse(late.bonus)
    }

    @Test
    fun `goal_race — решатель находит путь с радостями, если он есть`() {
        val plan = TaskEngines.raceSolution(moodRace)!!
        assertTrue(raceOf(moodRace, *plan.toIntArray()).bonus)
        // Свободных монет нет вовсе: копить можно только всё, а это грусть — бонус не получить.
        assertEquals(null, TaskEngines.raceSolution(moodRace.copy(goal = RaceGoal("Самокат", 60))))
    }

    @Test
    fun `goal_race — не тот день или взнос — ошибка ввода`() {
        assertEquals(TaskInputError.WrongCount(6), TaskEngines.evaluate(race, TaskInput.DailyDeposits(listOf(10))).error())
        assertEquals(
            TaskInputError.DepositOutOfRange(10),
            TaskEngines.evaluate(race, TaskInput.DailyDeposits(listOf(15, 10, 10, 10, 10, 10))).error(),
        )
        assertEquals(
            TaskInputError.DepositNotOnStep(5),
            TaskEngines.evaluate(race, TaskInput.DailyDeposits(listOf(7, 10, 10, 10, 10, 10))).error(),
        )
    }

    // ---------- reserve ----------

    private val rainy = ReserveTask(
        id = "w", theme = TaskTheme.PLANNING, title = "", intro = "", explainOk = "", explainFail = "",
        amount = 60,
        spendings = listOf(
            Spending("food", "", 20, Category.NEEDS),
            Spending("soap", "", 10, Category.NEEDS),
            Spending("ball", "", 10, Category.WANTS),
            Spending("stickers", "", 10, Category.WANTS),
            Spending("icecream", "", 5, Category.WANTS),
        ),
        surprises = listOf(Surprise("Зонт", "", 10), Surprise("Рюкзак", "", 5)),
    )

    @Test
    fun `reserve — желаемое и запас, ничего не отменили — успех и бонус`() {
        assertEquals(
            TaskEvaluation.Done(
                TaskOutcome.SUCCESS,
                TaskDetails.Reserve(reserve = 20, shortage = 0, surprises = 15, keptWants = 1, left = 5),
                bonus = true,
            ),
            TaskEngines.evaluate(rainy, TaskInput.Reserve(setOf("food", "soap", "ball"), emptySet())),
        )
    }

    @Test
    fun `reserve — пустой план — неделя без радостей, пройдено без бонуса`() {
        assertEquals(
            TaskEvaluation.Done(
                TaskOutcome.SUCCESS,
                TaskDetails.Reserve(reserve = 30, shortage = 0, surprises = 15, keptWants = 0, left = 15),
            ),
            TaskEngines.evaluate(rainy, TaskInput.Reserve(setOf("food", "soap"), emptySet())),
        )
    }

    @Test
    fun `reserve — жадный план — желаемое отложили ради сюрприза, пройдено без бонуса`() {
        assertEquals(
            TaskEvaluation.Done(
                TaskOutcome.SUCCESS,
                TaskDetails.Reserve(reserve = 5, shortage = 10, surprises = 15, keptWants = 1, left = 10, droppedWants = 2),
            ),
            TaskEngines.evaluate(
                rainy,
                TaskInput.Reserve(setOf("food", "soap", "ball", "stickers", "icecream"), setOf("ball", "stickers")),
            ),
        )
    }

    @Test
    fun `reserve — нужное отложили — неудача`() {
        val all = setOf("food", "soap", "ball", "stickers", "icecream")
        assertEquals(
            TaskEvaluation.Done(
                TaskOutcome.FAIL,
                TaskDetails.Reserve(reserve = 5, shortage = 10, surprises = 15, droppedNeeds = 1, keptWants = 3, left = 0),
            ),
            TaskEngines.evaluate(rainy, TaskInput.Reserve(all, setOf("soap"))),
        )
    }

    @Test
    fun `reserve — бонус только за запас на все сюрпризы, даже невыпавшие`() {
        // Выпал только зонт за 10, а могли оба — на 15.
        val lucky = rainy.copy(surprises = listOf(Surprise("Зонт", "", 10)), worstCase = 15)
        val safe = TaskEngines.evaluate(lucky, TaskInput.Reserve(setOf("food", "soap", "ball"), emptySet())) as TaskEvaluation.Done
        assertEquals(TaskOutcome.SUCCESS, safe.outcome)
        assertTrue("запас 20 ≥ 15", safe.bonus)
        // Запаса 10 хватило на зонт — повезло: игра пройдена, но без бонуса.
        val risky = TaskEngines.evaluate(lucky, TaskInput.Reserve(setOf("food", "soap", "ball", "stickers"), emptySet())) as TaskEvaluation.Done
        assertEquals(TaskOutcome.SUCCESS, risky.outcome)
        assertFalse("запас 10 < 15", risky.bonus)
    }

    @Test
    fun `reserve — без сюрпризов весь запас остаётся на потом`() {
        val calm = rainy.copy(surprises = emptyList())
        val done = TaskEngines.evaluate(calm, TaskInput.Reserve(setOf("food", "soap", "icecream"), emptySet())) as TaskEvaluation.Done
        assertEquals(25, (done.details as TaskDetails.Reserve).left)
        assertEquals(TaskOutcome.SUCCESS, done.outcome)
    }

    @Test
    fun `reserve — нужное нельзя убрать из плана, непредвиденное должно быть покрыто`() {
        val all = setOf("food", "soap", "ball", "stickers", "icecream")
        assertEquals(TaskInputError.NeedsNotPlanned("soap"), TaskEngines.evaluate(rainy, TaskInput.Reserve(setOf("food"), emptySet())).error())
        assertEquals(TaskInputError.NotPlanned("ball"), TaskEngines.evaluate(rainy, TaskInput.Reserve(setOf("food", "soap"), setOf("ball"))).error())
        assertEquals(TaskInputError.SurpriseNotCovered(5), TaskEngines.evaluate(rainy, TaskInput.Reserve(all, setOf("icecream"))).error())
    }

    @Test
    fun `объяснение итога — своё для «пройдено, но можно лучше»`() {
        val texts = rainy.copy(explainOk = "ok", explainFail = "fail", explainDropped = "dropped", explainNoJoy = "nojoy")
        fun explain(planned: Set<String>, dropped: Set<String>): String {
            val done = TaskEngines.evaluate(texts, TaskInput.Reserve(planned, dropped)) as TaskEvaluation.Done
            return TaskEngines.explanation(texts, done.outcome, done.details)
        }
        val all = setOf("food", "soap", "ball", "stickers", "icecream")
        assertEquals("ok", explain(setOf("food", "soap", "ball"), emptySet()))
        assertEquals("dropped", explain(all, setOf("ball", "stickers")))
        assertEquals("nojoy", explain(setOf("food", "soap"), emptySet()))
        assertEquals("fail", explain(all, setOf("soap")))
        // Текста нет в контенте — общий explainOk.
        val plain = texts.copy(explainDropped = null)
        val done = TaskEngines.evaluate(plain, TaskInput.Reserve(all, setOf("ball", "stickers"))) as TaskEvaluation.Done
        assertEquals("ok", TaskEngines.explanation(plain, done.outcome, done.details))

        val sad = moodRace.copy(explainOk = "ok", explainSad = "sad")
        val saveAll = raceOf(sad, 10, 10, 10, 10, 10, 10)
        assertEquals("sad", TaskEngines.explanation(sad, saveAll.outcome, saveAll.details))
        val balanced = raceOf(sad, 10, 5, 10, 10, 10, 10)
        assertEquals("ok", TaskEngines.explanation(sad, balanced.outcome, balanced.details))
    }

    // ---------- stand ----------

    private val stand = StandTask(
        id = "l", theme = TaskTheme.PLANNING, title = "", intro = "", explainOk = "", explainFail = "",
        budget = 50,
        ingredient = StandIngredient("Лимон", price = 5, yields = 2),
        cupPrice = 5,
        guests = 8,
        forecast = "",
    )

    @Test
    fun `stand — успех, когда сырья ровно на всех гостей`() {
        assertEquals(4, TaskEngines.bestStock(stand))
        assertEquals(
            TaskEvaluation.Done(TaskOutcome.SUCCESS, TaskDetails.Stand(cups = 8, sold = 8, earned = 40, spent = 20, leftover = 0, missed = 0, best = 4)),
            TaskEngines.evaluate(stand, TaskInput.Stock(4)),
        )
        val over = TaskEngines.standDay(stand, 5)
        assertEquals(2, over.leftover)
        assertEquals(15, over.kept)
        assertEquals(TaskOutcome.FAIL, TaskEngines.evaluate(stand, TaskInput.Stock(5)).outcome())
        assertEquals(2, TaskEngines.standDay(stand, 3).missed)
        assertEquals(TaskOutcome.FAIL, TaskEngines.evaluate(stand, TaskInput.Stock(3)).outcome())
    }

    @Test
    fun `stand — закупка дороже бюджета — отказ с нехваткой`() {
        assertEquals(5, (TaskEngines.evaluate(stand, TaskInput.Stock(11)).error() as TaskInputError.OverLimit).shortage)
        assertEquals(TaskInputError.NegativeAmount, TaskEngines.evaluate(stand, TaskInput.Stock(-1)).error())
    }

    // ---------- change ----------

    private val cashier = ChangeTask(
        id = "c", theme = TaskTheme.SHOPPING, title = "", intro = "", explainOk = "", explainFail = "",
        coins = listOf(1, 2, 5, 10),
        rounds = listOf(
            ChangeRound(ChangeMode.GIVE, "Сок", price = 17, paid = 20),
            ChangeRound(ChangeMode.EXACT, "Молоко", price = 17, wallet = listOf(10, 5, 5, 2, 1, 1)),
        ),
    )

    @Test
    fun `change — сдача и оплата без сдачи, результат по раундам`() {
        assertEquals(
            TaskEvaluation.Done(TaskOutcome.SUCCESS, TaskDetails.Change(correct = 2, total = 2, results = listOf(0, 0))),
            TaskEngines.evaluate(cashier, TaskInput.Coins(listOf(listOf(2, 1), listOf(10, 5, 2)))),
        )
        assertEquals(
            TaskEvaluation.Done(TaskOutcome.FAIL, TaskDetails.Change(correct = 1, total = 2, results = listOf(1, 0))),
            TaskEngines.evaluate(cashier, TaskInput.Coins(listOf(listOf(2, 2), listOf(10, 5, 1, 1)))),
        )
    }

    @Test
    fun `change — чужая монета, монет нет в кошельке, не то число раундов — ошибка ввода`() {
        assertEquals(TaskInputError.UnknownCoin(3), TaskEngines.evaluate(cashier, TaskInput.Coins(listOf(listOf(3), listOf(10, 5, 2)))).error())
        assertEquals(TaskInputError.CoinsNotInWallet(1), TaskEngines.evaluate(cashier, TaskInput.Coins(listOf(listOf(2, 1), listOf(10, 2, 2, 2, 1)))).error())
        assertEquals(TaskInputError.WrongCount(2), TaskEngines.evaluate(cashier, TaskInput.Coins(listOf(listOf(2, 1)))).error())
    }

    // ---------- receipt ----------

    /** Сок пробит дважды, хлеб дороже ценника, яблок честно два. Дал 50, сдача меньше на 5. */
    private val check = ReceiptTask(
        id = "k", theme = TaskTheme.SHOPPING, title = "", intro = "", explainOk = "", explainFail = "",
        cart = listOf(
            ReceiptItem("juice", "", 10),
            ReceiptItem("bread", "", 5),
            ReceiptItem("apple", "", 3, qty = 2),
        ),
        lines = listOf(
            ReceiptLine("l1", "juice", 10),
            ReceiptLine("l2", "juice", 10),
            ReceiptLine("l3", "bread", 7),
            ReceiptLine("l4", "apple", 3),
            ReceiptLine("l5", "apple", 3),
        ),
        paid = 50,
        change = 12,
    )

    private fun checkOf(task: ReceiptTask, vararg lines: String, change: Boolean) =
        TaskEngines.evaluate(task, TaskInput.Flags(lines.toSet(), change))

    @Test
    fun `receipt — ядро называет ошибки, сдача считается от суммы чека`() {
        assertEquals(33, TaskEngines.receiptTotal(check))
        assertEquals(ReceiptErrors(lines = setOf("l2", "l3"), changeShort = 5), TaskEngines.receiptErrors(check))
    }

    @Test
    fun `receipt — всё найдено — успех и все монеты вернули`() {
        assertEquals(
            TaskEvaluation.Done(TaskOutcome.SUCCESS, TaskDetails.Receipt(found = 3, missed = 0, extra = 0, refund = 17, lost = 0)),
            checkOf(check, "l2", "l3", change = true),
        )
    }

    @Test
    fun `receipt — любая строка из дубля засчитывается`() {
        assertEquals(TaskOutcome.SUCCESS, checkOf(check, "l1", "l3", change = true).outcome())
        // И подсветка экрана согласна с оценкой: отмеченная первая строка — ошибка, вторая — нет.
        assertEquals(setOf("l1", "l3"), TaskEngines.receiptErrors(check, flagged = setOf("l1")).lines)
    }

    @Test
    fun `receipt — пропуск и лишняя отметка — неудача`() {
        assertEquals(
            TaskEvaluation.Done(TaskOutcome.FAIL, TaskDetails.Receipt(found = 2, missed = 1, extra = 0, refund = 12, lost = 5)),
            checkOf(check, "l2", "l3", change = false),
        )
        // Два яблока куплены честно: отметить одно — лишнее.
        assertEquals(
            TaskDetails.Receipt(found = 3, missed = 0, extra = 1, refund = 17, lost = 0),
            (checkOf(check, "l2", "l3", "l4", change = true) as TaskEvaluation.Done).details,
        )
        // Оба сока отмечены, а лишний только один.
        assertEquals(TaskOutcome.FAIL, checkOf(check, "l1", "l2", "l3", change = true).outcome())
    }

    @Test
    fun `receipt — сдача от чека с ошибкой — верно и отметить, и не отмечать`() {
        // Кассир отсчитал 50 − 33 = 17 честно от своего чека, а по ценникам сдача 50 − 21 = 29.
        val fairChange = check.copy(change = 17)
        assertEquals(ReceiptErrors(lines = setOf("l2", "l3"), changeShort = 0, changeEither = true), TaskEngines.receiptErrors(fairChange))
        assertEquals(TaskOutcome.SUCCESS, checkOf(fairChange, "l2", "l3", change = false).outcome())
        assertEquals(
            TaskEvaluation.Done(TaskOutcome.SUCCESS, TaskDetails.Receipt(found = 2, missed = 0, extra = 0, refund = 12, lost = 0)),
            checkOf(fairChange, "l2", "l3", change = true),
        )
        // Строки всё равно нужно найти.
        assertEquals(TaskOutcome.FAIL, checkOf(fairChange, "l3", change = true).outcome())
        // Недодача сдачи — по-прежнему ошибка, которую нужно отметить.
        assertEquals(false, TaskEngines.receiptErrors(check).changeEither)
    }

    @Test
    fun `receipt — чужая строка — ошибка ввода`() {
        assertEquals(TaskInputError.UnknownItem("l9"), checkOf(check, "l9", change = false).error())
    }

    @Test
    fun `receipt — валидатор ловит чек без ошибок и ошибки в пользу покупателя`() {
        fun errors(task: ReceiptTask) = ContentValidator.validate(Fixtures.content.copy(tasks = Fixtures.tasks + task))
        assertTrue(errors(check).none { "задание k" in it })

        val clean = check.copy(lines = check.lines.filter { it.id != "l2" }.map { if (it.id == "l3") it.copy(price = 5) else it }, change = 29)
        assertTrue(errors(clean).any { "нет ни одной ошибки" in it })

        val cheaper = check.copy(lines = check.lines.map { if (it.id == "l3") it.copy(price = 4) else it }, change = 10)
        assertTrue(errors(cheaper).any { "дешевле ценника" in it })

        val missing = check.copy(lines = check.lines.filter { it.id != "l5" }, change = 15)
        assertTrue(errors(missing).any { "меньше строк" in it })

        assertTrue(errors(check.copy(change = 20)).any { "change должен быть" in it })
    }

    // ---------- chores ----------

    /** 5 дней по 2 часика, день отдыха, цель 50. Собака выгоднее посуды, но её дают дважды. */
    private val week = ChoresTask(
        id = "j", theme = TaskTheme.PLANNING, title = "", intro = "", explainOk = "", explainFail = "",
        goal = RaceGoal("Мяч", 50),
        days = 5,
        hoursPerDay = 2,
        chores = listOf(
            Chore("flowers", "", hours = 1, reward = 5),
            Chore("dishes", "", hours = 1, reward = 10),
            Chore("dog", "", hours = 2, reward = 25, maxTimes = 2),
        ),
    )

    private fun weekOf(task: ChoresTask, vararg days: List<String>) = TaskEngines.evaluate(task, TaskInput.Schedule(days.toList()))

    private val rest = emptyList<String>()

    @Test
    fun `chores — набрал на цель и отдохнул — успех`() {
        assertEquals(
            TaskEvaluation.Done(TaskOutcome.SUCCESS, TaskDetails.Chores(earned = 85, shortfall = 0, restDays = 1)),
            weekOf(week, listOf("dog"), listOf("dishes", "dishes"), listOf("dog"), listOf("dishes", "flowers"), rest),
        )
    }

    @Test
    fun `chores — без отдыха или без денег — неудача`() {
        val noRest = weekOf(week, listOf("dog"), listOf("dishes", "dishes"), listOf("dog"), listOf("dishes", "dishes"), listOf("flowers"))
        assertEquals(TaskDetails.Chores(earned = 95, shortfall = 0, restDays = 0), (noRest as TaskEvaluation.Done).details)
        assertEquals(TaskOutcome.FAIL, noRest.outcome)

        assertEquals(
            TaskEvaluation.Done(TaskOutcome.FAIL, TaskDetails.Chores(earned = 40, shortfall = 10, restDays = 3)),
            weekOf(week, listOf("dog"), rest, listOf("flowers", "dishes"), rest, rest),
        )
    }

    @Test
    fun `chores — перегруженный день, лишний раз, чужое дело, не та неделя — ошибка ввода`() {
        assertEquals(TaskInputError.DayOverloaded(1), weekOf(week, rest, listOf("dog", "flowers"), rest, rest, rest).error())
        assertEquals(TaskInputError.ChoreTooOften("dog", 2), weekOf(week, listOf("dog"), listOf("dog"), listOf("dog"), rest, rest).error())
        assertEquals(TaskInputError.UnknownItem("cat"), weekOf(week, listOf("cat"), rest, rest, rest, rest).error())
        assertEquals(TaskInputError.WrongCount(5), weekOf(week, rest).error())
    }

    @Test
    fun `chores — решатель находит самую выгодную неделю с отдыхом или null`() {
        val plan = TaskEngines.choresSolution(week)!!
        assertEquals(5, plan.size)
        assertEquals(1, plan.count { it.isEmpty() })
        // Собаку дают дважды: 25 + 25 и ещё два дня посуды по 20 — больше не заработать.
        assertEquals(90, TaskEngines.choresEarned(week, plan))
        assertEquals(TaskOutcome.SUCCESS, TaskEngines.evaluate(week, TaskInput.Schedule(plan)).outcome())

        assertEquals(null, TaskEngines.choresSolution(week.copy(goal = RaceGoal("Мяч", 95))))
    }

    /** Собака платит больше в последние дни, соседке помогают только во 2-й и 4-й, усталость 10, бонус 10 за 3 дела. */
    private val tired = week.copy(
        goal = RaceGoal("Конструктор", 90),
        tiredCut = 10,
        varietyBonus = 10,
        chores = listOf(
            Chore("flowers", "", hours = 1, reward = 5),
            Chore("dishes", "", hours = 1, reward = 10),
            Chore("dog", "", hours = 2, reward = 20, pay = listOf(20, 20, 20, 30, 30)),
            Chore("neighbor", "", hours = 1, reward = 15, maxTimes = 1, onDays = listOf(2, 4)),
        ),
    )

    @Test
    fun `chores — оплата по дням, усталость за то же дело подряд и бонус за разные дела`() {
        val pay = TaskEngines.choresPay(tired, listOf(listOf("dog"), listOf("dog"), listOf("dishes", "flowers"), listOf("dog"), rest))
        // 20 + 20 + 15 + 30 = 85, собака во второй день подряд — минус 10, три разных дела — плюс 10.
        assertEquals(TaskEngines.ChoresPay(fees = 85, tiredLoss = 10, bonus = 10), pay)
        assertEquals(85, pay.total)
        assertEquals(30, TaskEngines.choreFee(tired.chores[2], 4))
        // Усталость не уводит оплату в минус: второй день цветов — 0, а не −95.
        assertEquals(
            TaskEngines.ChoresPay(fees = 10, tiredLoss = 5, bonus = 0),
            TaskEngines.choresPay(tired.copy(tiredCut = 100), listOf(listOf("flowers"), listOf("flowers"), rest, rest, rest)),
        )
    }

    @Test
    fun `chores — дело не в свой день — ошибка ввода, итог с усталостью и бонусом`() {
        assertEquals(TaskInputError.ChoreNotToday("neighbor", 0), weekOf(tired, listOf("neighbor"), rest, rest, rest, rest).error())
        val done = weekOf(tired, listOf("dog"), listOf("neighbor", "dishes"), listOf("dog"), listOf("dishes", "flowers"), rest) as TaskEvaluation.Done
        // 20 + 25 + 20 + 15 = 80, одинаковых дел подряд нет; разных дел четыре — бонус 10.
        assertEquals(TaskDetails.Chores(earned = 90, shortfall = 0, restDays = 1, tiredLoss = 0, bonus = 10), done.details)
        assertEquals(TaskOutcome.SUCCESS, done.outcome)
    }

    @Test
    fun `chores — решатель учитывает усталость, а «собака каждый день» не выигрывает`() {
        val plan = TaskEngines.choresSolution(tired)!!
        assertTrue(TaskEngines.choresEarned(tired, plan) >= tired.goal.price)
        val dogEveryDay = listOf(listOf("dog"), listOf("dog"), listOf("dog"), listOf("dog"), rest)
        assertTrue(TaskEngines.choresEarned(tired, dogEveryDay) < tired.goal.price)
        assertEquals(emptyList<String>(), ContentValidator.validate(Fixtures.content.copy(tasks = listOf(tired))))
        // Без усталости та же схема выигрывает — валидатор это ловит.
        val easy = tired.copy(tiredCut = 5, goal = RaceGoal("Мяч", 80))
        assertTrue(ContentValidator.validate(Fixtures.content.copy(tasks = listOf(easy))).single().contains("самое выгодное дело"))
    }

    @Test
    fun `chores — валидатор ловит цель, которую не набрать, и дело длиннее дня`() {
        fun errors(task: ChoresTask) = ContentValidator.validate(Fixtures.content.copy(tasks = Fixtures.tasks + task))
        assertTrue(errors(week).none { "задание j" in it })
        assertTrue(errors(week.copy(goal = RaceGoal("Мяч", 95))).any { "цель не набрать" in it })
        assertTrue(errors(week.copy(hoursPerDay = 1)).any { "dog: hours от 1 до 1" in it })
        assertTrue(errors(week.copy(minRestDays = 5)).any { "minRestDays" in it })
    }

    @Test
    fun `мини-игра — обычное задание — награда и попытки общие`() {
        val game = ru.finney.pet.domain.game.Game(Fixtures.content.copy(tasks = Fixtures.tasks + stand))
        val first = game.submitTask(game.newPlannedGame(), "l", TaskInput.Stock(5)).submitted()
        assertEquals(TaskOutcome.FAIL, first.outcome)
        assertEquals(5, first.reward)
        val second = game.submitTask(first.state, "l", TaskInput.Stock(4)).submitted()
        assertEquals(15, second.reward)
        assertEquals(70, second.state.balance)
    }
}
