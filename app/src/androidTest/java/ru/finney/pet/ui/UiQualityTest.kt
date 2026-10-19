package ru.finney.pet.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finney.pet.content.AssetContentLoader
import ru.finney.pet.domain.game.*
import ru.finney.pet.domain.model.*
import ru.finney.pet.domain.tasks.*
import ru.finney.pet.ui.budget.*
import ru.finney.pet.ui.goals.*
import ru.finney.pet.ui.home.*
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.period.*
import ru.finney.pet.ui.progress.*
import ru.finney.pet.ui.shop.*
import ru.finney.pet.ui.tasks.*
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.ui.wardrobe.*
import java.io.File

/** Полные состояния интерфейса на реальной ширине окна, без доступа к сохранениям. */
@RunWith(AndroidJUnit4::class)
class UiQualityTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor: Float = 0f
    })
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val content by lazy { AssetContentLoader(context).load() }
    private var frame by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var frameName by mutableStateOf("")
    private var font by mutableStateOf<Float?>(null)
    private val directory get() = File(context.getExternalFilesDir(null), "ui-quality").apply { mkdirs() }
    private val character = PetCharacter.PUSHISTIK
    private val appearance = PetAppearance(character, BodyColor.A, EyesVariant.ROUND)
    private val goal by lazy { content.goals.first { it.label.contains("Пират") } }
    private val progress get() = GoalProgress(goal, 15, goal.price - 15, 8, 15)

    private fun begin() {
        compose.setContent { FinneyTheme {
            val actual = LocalDensity.current
            CompositionLocalProvider(LocalAnimations provides false,
                LocalDensity provides Density(actual.density, font ?: actual.fontScale)) {
                key(frameName) { Box(Modifier.fillMaxSize()) { frame?.invoke() } }
            }
        } }
    }

    private fun show(name: String, scale: Float? = null, block: @Composable () -> Unit) {
        compose.runOnIdle { frameName = name; font = scale; frame = block }
        compose.waitForIdle()
    }

    private fun shot(name: String = frameName) {
        compose.waitForIdle()
        SystemClock.sleep(350)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun home(step: NextStep): HomeUiState.Ready {
        val game = Game(content)
        val state = game.newGame(isDemo = true)
        val check = game.levelCheck(state).copy(planConfirmed = step != NextStep.PLAN)
        return HomeUiState.Ready("Финни", appearance, true, state.pet, game.emotion(state), 1,
            check, 1, 35, 15, progress, 1, if (step == NextStep.PLAN) PeriodPhase.PLANNING else PeriodPhase.ACTIVE,
            game.needsHint(state), "Проверь чек", emptyList(), emptyList(), nextStep = step,
            levelGames = listOf(LevelGame("game_receipt", "Проверь чек", null, false, false, 15)))
    }

    @Composable private fun HomeFixture(step: NextStep) {
        HomeContent(home(step), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }

    private fun planned() = BudgetUiState.Planning(periodNumber = 1, level = 1, budget = 50, needs = 20, wants = 15, savings = 15, needsHint = 30, goalLabel = goal.label,
        goals = content.goals, rejection = null, isSaving = false, goalReward = goal.reward, needsItems = listOf("food_apple", "care_soap"),
        wantsItems = listOf("treat_candy", "toy_ball"))

    private fun savings() = GoalsUiState.Ready(
        content.goals.map { GoalRow(it, if (it.id == goal.id) 15 else 0, it.id == goal.id, false) },
        progress, 35, true, false, null, planSavingsLeft = 15, guide = true)

    @Test fun homeFormsAndHelpStayUsable() {
        begin()
        for (step in listOf(NextStep.PLAN, NextStep.FEED, NextStep.WASH, NextStep.SAVE, NextStep.LEVEL_GAME)) {
            show("home-${step.name.lowercase()}") { HomeFixture(step) }
            compose.onNodeWithText("Ещё ${progress.remaining}").assertIsDisplayed()
            compose.onNodeWithContentDescription("Закрыть подсказки").assertIsDisplayed()
            shot()
            compose.onNodeWithContentDescription("Закрыть подсказки").performClick()
            compose.onNodeWithContentDescription("Закрыть подсказки").assertDoesNotExist()
        }
        for (scale in listOf(1f, 1.3f)) {
            show("plan-complete-$scale", scale) {
                PlanningContent(planned(), {}, {}, {}, {}, {}, {}, {})
            }
            compose.onNodeWithText("Подтвердить план").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithText("Цель: «${goal.label}»").performScrollTo()
            shot()
            compose.onNodeWithText("Зачем нужен план?").performScrollTo().performClick()
            shot("plan-help-$scale")
        }
        var deposited = 0
        show("savings") { GoalsContent(savings(), {}, { deposited = it }, {}, {}, {}) }
        compose.onNodeWithText("Положить 15").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(15, deposited) }
        shot()
        compose.onNodeWithText("Зачем копить?").assertIsDisplayed().performClick()
        shot("savings-help")
        show("savings-large-font", 1.3f) { GoalsContent(savings(), {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Положить 15").performScrollTo().assertIsDisplayed()
        shot()
        compose.onNodeWithText("Выбрать другую цель").performScrollTo().performClick()
        compose.onNodeWithText("Ковбойская шляпа").performScrollTo().assertIsDisplayed()
        shot("savings-choose-goal")
    }

    private fun taskState(task: TaskDefinition, phase: TaskPhase = TaskPhase.INTRO,
        result: TaskOutcomeUi? = null) = TaskUiState.Ready(task, character, balance = 35,
            available = true, lockedUntilLevel = null, completed = false,
            reward = task.reward ?: content.economy.taskReward, phase = phase,
            attempt = 0, result = result, inputError = null, isLevelGame = true)

    @Test fun everyMiniGameVariantAndItsHelp() {
        begin()
        for (task in content.tasks) {
            show("intro-${task.id}") { TaskIntro(taskState(task), {}, {}, {}) }
            compose.onNodeWithText("Играть").assertIsDisplayed()
            shot()
            show("play-${task.id}") { TaskPlay(taskState(task, TaskPhase.PLAY), {}, {}, {}) }
            compose.onNodeWithContentDescription("Как играть").assertIsDisplayed().performClick()
            compose.onNodeWithText("Как играть").assertIsDisplayed()
            shot("help-${task.id}")
            compose.onNodeWithText("Продолжить игру").performScrollTo().performClick()
            shot("play-${task.id}")
        }
    }

    @Test fun outcomesShopWardrobeAndProgress() {
        begin()
        for (task in content.taskSeries.map { it.first() }) {
            val inputs = solve(task)
            for ((index,input) in listOf(inputs.first, inputs.second).withIndex()) {
                val done = TaskEngines.evaluate(task, input) as TaskEvaluation.Done
                val result = TaskOutcomeUi(done.outcome, done.details, if (index == 0) 15 else 0, input)
                show("result-${task.id}-$index") {
                    TaskResultScene(taskState(task, TaskPhase.RESULT, result), {}, {})
                }
                compose.onNodeWithText("Дальше").assertIsDisplayed()
                shot()
            }
        }
        val game = Game(content)
        val start = game.newGame(isDemo = true)
        fun previews(category: Category, kind: ItemKind) = content.shop.filter { it.category == category && it.kind == kind }
            .mapNotNull { game.previewPurchase(start, it.id) }
        val needs = previews(Category.NEEDS, ItemKind.CONSUMABLE)
        val wants = previews(Category.WANTS, ItemKind.CONSUMABLE)
        val toys = previews(Category.WANTS, ItemKind.TOY)
        for (item in listOf(needs.first(), wants.first(), toys.first())) {
            val shelves = ShopShelf.entries.map { shelf ->
                ShelfRow(shelf, (needs + wants + toys).filter { shelf.holds(it.item) })
            }
            val state = ShopUiState.Ready(50, true, shelves, item.item.id, 30, 15, null, null)
            show("shop-${item.item.kind}-${item.item.category}") { ShopContent(state, {}, {}, {}, {}, {}, {}) }
            compose.onNodeWithText(if (item.item.kind == ItemKind.CONSUMABLE) "Купить и использовать" else "Купить").assertIsDisplayed()
            shot()
        }
        val accessories = content.shop.filter { it.kind == ItemKind.ACCESSORY }
        val wardrobe = WardrobeUiState.Ready(appearance, emptyList(), accessories.map { WardrobeItem(it, true, null) })
        show("wardrobe") { WardrobeContent(wardrobe, {}, {}, {}, {}) }
        shot()
        val report = ProgressUiState.Ready("Финни", 12, 30, 5, progress, 2, 11, 11,
            content.taskSeries.map { ru.finney.pet.ui.progress.TaskRow(it.first().title, "", true, 2) },
            listOf(HistoryPeriod(11, 11, listOf(HistoryRow("Еда", -20, 0, EntryType.PURCHASE)))))
        show("progress") { ProgressContent(report, {}, {}, {}) }
        shot()
        compose.onNodeWithText("Справочник").performScrollTo()
        shot("progress-history")
        for (passed in listOf(true, false)) {
            val result = PeriodResultUiState.Ready(1, 1, Plan(50, 20, 15, 15),
                PeriodFacts(20, 0, 15, 0), true, passed, passed, 2, passed, if (passed) 2 else 1,
                passed, 65, "Проверь чек", gamePassed = passed, income = 55, canPlanNext = true)
            show("level-result-$passed") { PeriodResultContent(result, {}, {}) }
            shot()
            compose.onNodeWithText("Составить следующий план").assertIsDisplayed()
            shot("level-result-bottom-$passed")
        }
    }
    private fun action(label: String) = compose.onAllNodes(SemanticsMatcher(label) {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    } and isEnabled()).onFirst()

    @Test fun remainingGamesCanBeCompletedThroughControls() {
        begin()
        for (task in content.tasks.filter { it is SorterTask || it is BasketTask || it is StandTask || it is ChangeTask || it is ChoresTask }) {
            var submitted: TaskInput? = null
            show("completed-${task.id}") { TaskPlay(taskState(task, TaskPhase.PLAY), {}, {}, { submitted = it }) }
            when (task) {
                is SorterTask -> task.items.forEach { item ->
                    action(if (item.category == Category.NEEDS) "Положить в «нужное»" else "Положить в «хочется»").performClick()
                }
                is BasketTask -> {
                    val input = solve(task).first as TaskInput.Basket
                    for (id in input.items) compose.onNodeWithText(task.shelf.first { it.id == id }.label).performScrollTo().performClick()
                    shot("selected-${task.id}")
                    compose.onNodeWithContentDescription("На кассу").performClick()
                }
                is StandTask -> {
                    repeat(TaskEngines.bestStock(task)) {
                        compose.onNodeWithContentDescription("Больше: ${task.ingredient.label}").performClick()
                    }
                    compose.onNodeWithText("Открыть лавку").performClick()
                    repeat(task.guests) {
                        compose.onNodeWithContentDescription("Налить лимонад, плюс ${task.cupPrice}").performClick()
                    }
                    shot()
                    compose.onNodeWithText("Закрыть лавку").performClick()
                }
                is ChangeTask -> {
                    val input = solve(task).first as TaskInput.Coins
                    input.rounds.forEachIndexed { index, coins ->
                        coins.forEach { action("Положить монету $it").performClick() }
                        compose.onNodeWithText("Проверить").performClick()
                        shot("round-${task.id}-$index")
                        compose.onNodeWithText(if (index == input.rounds.lastIndex) "Готово" else "Следующий покупатель").performClick()
                    }
                }
                is ChoresTask -> {
                    val input = solve(task).first as TaskInput.Schedule
                    input.days.forEachIndexed { index, chores ->
                        action("Выбрать день ${index + 1}").performClick()
                        chores.forEach { id ->
                            val label = task.chores.first { it.id == id }.label
                            compose.onNode(hasContentDescription("$label:", substring = true) and hasClickAction()).performScrollTo().performClick()
                        }
                    }
                    shot()
                    compose.onNodeWithText("Готово").performClick()
                }
                else -> error("Unsupported fixture")
            }
            compose.runOnIdle { assertEquals("${task.id}: controls submitted a different answer", solve(task).first, submitted) }
        }
    }

    private fun solve(task: TaskDefinition): Pair<TaskInput, TaskInput> = when (task) {
        is SorterTask -> {
            val right = task.items.associate { it.id to it.category }
            TaskInput.Sorting(right) to TaskInput.Sorting(right.mapValues { (_, c) -> if (c == Category.NEEDS) Category.WANTS else Category.NEEDS })
        }
        is BasketTask -> {
            val win = (0 until (1 shl task.shelf.size)).asSequence()
                .map { mask -> task.shelf.filterIndexed { i, _ -> mask and (1 shl i) != 0 } }
                .first { items -> items.sumOf { it.price } <= task.limit && task.rules.all { TaskEngines.ruleMet(task, it, items.map { i -> i.id }.toSet()) } }
            TaskInput.Basket(win.map { it.id }.toSet()) to TaskInput.Basket(emptySet())
        }
        is GoalRaceTask -> TaskInput.DailyDeposits(TaskEngines.raceSolution(task)!!) to TaskInput.DailyDeposits(List(task.days) { 0 })
        is ReserveTask -> {
            val needs = task.spendings.filter { it.category == Category.NEEDS }.map { it.id }.toSet()
            // Неудача: все желаемые, какие влезают, без запаса; на сюрпризы приходится переносить нужное.
            val planned = task.spendings.filter { it.category == Category.WANTS }.sortedBy { it.price }
                .fold(needs) { acc, s -> if (TaskEngines.reserveLeft(task, acc + s.id) >= 0) acc + s.id else acc }
            val shortage = TaskEngines.surprisesTotal(task) - TaskEngines.reserveLeft(task, planned)
            val dropped = task.spendings.filter { it.id in planned }.sortedWith(compareBy({ it.category != Category.NEEDS }, { -it.price }))
                .fold(emptyList<String>()) { acc, s -> if (acc.sumOf { id -> task.spendings.first { it.id == id }.price } >= shortage) acc else acc + s.id }
            // Успех: нужное и самое дешёвое желаемое, остальное — запас.
            val cheapest = task.spendings.filter { it.category == Category.WANTS }.minBy { it.price }.id
            TaskInput.Reserve(needs + cheapest, emptySet()) to TaskInput.Reserve(planned, dropped.toSet())
        }
        is StandTask -> {
            val best = TaskEngines.bestStock(task)
            TaskInput.Stock(best) to TaskInput.Stock(if ((best + 1) * task.ingredient.price <= task.budget) best + 1 else best - 1)
        }
        is ChangeTask -> {
            val right = task.rounds.map { round ->
                // Сдачу дают из ящика, где монет сколько угодно: хватит target / номинал каждой.
                val pool = if (round.mode == ChangeMode.EXACT) round.wallet else task.coins.flatMap { c -> List(round.target / c) { c } }
                payExact(round.target, pool)
            }
            val wrong = task.rounds.map { round ->
                val pool = if (round.mode == ChangeMode.EXACT) round.wallet else task.coins
                listOf(pool.firstOrNull { it != round.target } ?: pool.first())
            }
            TaskInput.Coins(right) to TaskInput.Coins(wrong)
        }
        is ReceiptTask -> {
            // В чеке всегда есть ошибка (валидатор), поэтому «ничего не отмечено» — неудача.
            val errors = TaskEngines.receiptErrors(task)
            TaskInput.Flags(errors.lines, errors.changeShort > 0) to TaskInput.Flags(emptySet(), false)
        }
        is ChoresTask -> {
            val best = TaskEngines.choresSolution(task) ?: throw AssertionError("${task.id}: цель не набрать")
            TaskInput.Schedule(best) to TaskInput.Schedule(List(task.days) { emptyList() })
        }
        else -> throw AssertionError("${task.id}: движок без решателя в тесте")
    }

    /** Точный набор [sum] из [coins], каждая монета — не больше одного раза. Перебор, а не жадность: 6 из [5, 2, 2, 2]. */
    private fun payExact(sum: Int, coins: List<Int>): List<Int> {
        val from = arrayOfNulls<List<Int>>(sum + 1).also { it[0] = emptyList() }
        for (coin in coins) for (s in sum downTo coin) if (from[s] == null && from[s - coin] != null) from[s] = from[s - coin]!! + coin
        return from[sum] ?: throw AssertionError("$sum не набрать из $coins")
    }


}
