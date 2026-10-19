package ru.finney.pet.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.finney.pet.appContainer
import ru.finney.pet.ui.progress.NextProgress
import ru.finney.pet.ui.progress.homeUnlocks
import ru.finney.pet.domain.pet.PetRules
import ru.finney.pet.domain.game.Game
import ru.finney.pet.domain.game.GameResult
import ru.finney.pet.domain.game.GoalProgress
import ru.finney.pet.domain.game.LevelCheck
import ru.finney.pet.domain.game.PurchasePreview
import ru.finney.pet.ui.room.BathShelf
import ru.finney.pet.ui.room.BathStep
import ru.finney.pet.ui.room.ShelfItem
import ru.finney.pet.ui.room.times
import ru.finney.pet.domain.game.Rejection
import ru.finney.pet.domain.game.Session
import ru.finney.pet.domain.model.BodyColor
import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.ItemKind
import ru.finney.pet.domain.model.GlossaryTerm
import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.domain.model.GameState
import ru.finney.pet.domain.model.PeriodPhase
import ru.finney.pet.domain.model.PetAppearance
import ru.finney.pet.domain.model.PetStats
import ru.finney.pet.domain.model.SavedGame
import ru.finney.pet.domain.model.ShopItem
import ru.finney.pet.domain.model.ItemArt
import ru.finney.pet.domain.model.TaskDefinition
import ru.finney.pet.domain.model.TaskOutcome
import ru.finney.pet.ui.isReserveMode
import ru.finney.pet.ui.room.Outfit
import ru.finney.pet.ui.tasks.games.taskIcon
import kotlin.random.Random
import ru.finney.pet.domain.pet.Emotion
import ru.finney.pet.ui.components.ActionFeedback
import ru.finney.pet.ui.components.changesBetween

/** Условий у уровня три: нужное, план, копилка. */
const val LEVEL_CONDITIONS = 3

/**
 * Одно действие, которое главный экран подсвечивает прямо сейчас (плейтест 28.09:
 * «ничего не понятно, что делать»). Порядок — как проходится уровень: план, еда и мытьё,
 * игра уровня, сон, обещанный взнос, завершение. Выбирает [nextStepFor].
 */
enum class NextStep {
    /** Плана нет — значок уровня, внутри «Составить план». */
    PLAN,
    FEED,
    WASH,
    LEVEL_GAME,
    SLEEP,
    /** Спит, и сна уже хватает для уровня — «Разбудить». Выбирает экран: сон идёт по часам. */
    WAKE,
    /** Условия выполнены — значок уровня, внутри «Завершить уровень». */
    FINISH,
    /** Отложить в копилку — плашка копилки. */
    SAVE,
}

/**
 * Следующий шаг по чек-листу ядра и шкалам. null — подсказывать нечего: питомец спит
 * или всё, что можно сделать руками, сделано, а условий не хватает (траты не по плану).
 *
 * Нужное — по порогу из конфига ядра, тому же, по которому [LevelCheck.needsCovered]:
 * ниже порога еда и мытьё сразу — первой зовём самую низкую шкалу.
 *
 * Сон — последним перед завершением: пока питомец спит, остальное закрыто, и уложи
 * его первым — ребёнок сидел бы перед капсулой, не сделав ничего другого (плейтест).
 */
internal fun nextStepFor(
    check: LevelCheck,
    stats: PetStats,
    needsThreshold: Int,
    asleep: Boolean,
    savingsPlanLeft: Int,
    availableBalance: Int,
): NextStep? {
    if (asleep) return null
    if (!check.planConfirmed) return NextStep.PLAN
    listOf(NextStep.FEED to stats.satiety, NextStep.WASH to stats.hygiene)
        .filter { it.second < needsThreshold }
        .minByOrNull { it.second }
        ?.let { return it.first }
    if (check.levelTaskId != null && check.gameRequired && !check.gamePassed) return NextStep.LEVEL_GAME
    if (stats.energy < needsThreshold) return NextStep.SLEEP
    // Допуск плана разрешает завершить уровень с неполным взносом. Напоминаем
    // о собственном обещании, пока есть деньги; правила завершения не меняем.
    if (savingsPlanLeft > 0 && availableBalance > 0) return NextStep.SAVE
    if (check.willPass) return NextStep.FINISH
    if (!check.savingsAdded && availableBalance > 0) return NextStep.SAVE
    return null
}

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /** Всё, что ТЗ п. 2.5.3 требует показать на главном одновременно. */
    data class Ready(
        val petName: String,
        val appearance: PetAppearance,
        val isDemo: Boolean,
        val stats: PetStats,
        val emotion: Emotion,
        val level: Int,
        /** Условия текущего уровня на эту минуту: чек-лист и дуга вокруг значка. */
        val check: LevelCheck,
        val stage: Int,
        val balance: Int,
        val totalSavings: Int,
        /** null — цель не выбрана. */
        val goal: GoalProgress?,
        val periodNumber: Int,
        val phase: PeriodPhase,
        /** Сколько стоит закрыть нужное при текущих шкалах. */
        val needsHint: Int?,
        /** Название игры уровня: её id — в [check]. null — игры в этом периоде нет. */
        val levelGame: String?,
        /** Чем покормить: всё из магазина, что поднимает сытость. */
        val food: List<PurchasePreview>,
        /** Чем помыть: всё, что поднимает чистоту. */
        val care: List<PurchasePreview>,
        /** Полочка ванной: мыло с запасом, можно ли сполоснуться водой (docs/bath-plan.md). */
        val bath: BathShelf = BathShelf(),
        /**
         * Сколько на нужное осталось по плану — как в полосе магазина: окно покупки на кухне
         * и в ванной — тот же магазин. null — плана ещё нет.
         */
        val needsLeft: Int? = null,
        /** Сколько по плану осталось на «хочется» — полоса над конфетой на кухне. */
        val wantsLeft: Int? = null,
        /** Нужное, которое выйдет за план, и сколько для него взять из радостей (Game.wantsToNeeds). */
        val fromWants: Map<String, Int> = emptyMap(),
        /** Справочник — для карточек во сне (SleepCards.kt). */
        val glossary: List<GlossaryTerm> = emptyList(),
        /** Сколько карточек сна-загадки за один сон — из economy.json. */
        val sleepCardsPerSleep: Int = 5,
        /** Что надето сейчас — id аксессуара из магазина. */
        val worn: List<String> = emptyList(),
        /** Все шляпы и очки из магазина — и свои, и те, что ещё не получены. */
        val outfit: List<Outfit> = emptyList(),
        /** Питомец спит; null — не спит. */
        val sleep: SleepInfo? = null,
        /** Купленные игрушки — id из магазина. Лежат на полу в зале. */
        val toys: List<String> = emptyList(),
        /** Сколько настроения игрушки ещё дадут в этой сессии игры; 0 — питомец наигрался. */
        val playLeft: Int = 0,
        /** Прибавка настроения за одно потряхивание игрушкой. */
        val moodPerShake: Int = 0,
        /** Настроение ниже порога грусти: питомец вздыхает, шкала зовёт на неё нажать. */
        val moodLow: Boolean = false,
        /** Настроение дошло до «радостного»: скучать игрушкам незачем. */
        val moodHappy: Boolean = false,
        /** С игрушками уже играли (ядро: `playSince`): показывать, как с ними играть, не нужно. */
        val toyPlayed: Boolean = false,
        /** Что подсветить сейчас; null — ничего. */
        val nextStep: NextStep? = null,
        /** Игры уровня: первая — обязательная игра уровня, дальше — по желанию, за монеты. */
        val levelGames: List<LevelGame> = emptyList(),
        /** Почему план пока не сходится — словами и числом; null — сходится или плана нет. */
        val planGap: PlanGap? = null,
        /** Невнесённая часть обещанного взноса, без допуска оценки плана. */
        val savingsPlanLeft: Int = 0,
        val reserveMode: Boolean = false,
        /** Порог трёх шкал ухода из правил игры. */
        val needsThreshold: Int = 50,
        /** Критически низкая шкала — отдельный порог, не условие прохождения уровня. */
        val criticalThreshold: Int = 30,
        val title: String? = null,
        val upcoming: NextProgress? = null,
        val upcomingFood: ShopItem? = null,
    ) : HomeUiState {
        /** Уровень завершается только после подтверждения плана. */
        val canClosePeriod: Boolean get() = phase == PeriodPhase.ACTIVE

        /** Дуга вокруг значка уровня: сколько условий уровня уже выполнено, 0..1. Игра уровня — ещё одно. */
        val levelProgress: Float get() {
            val hasGame = check.levelTaskId != null
            val done = check.met + if (hasGame && check.gamePassed) 1 else 0
            return done / (LEVEL_CONDITIONS + if (hasGame) 1 else 0).toFloat()
        }
    }
}

/**
 * Сон идёт по часам: [energyFrom] — с чем уснул, к [endsAt] сон дорастёт до 100.
 * Экран сам двигает кольцо сна по времени и будит питомца в [endsAt].
 */
data class SleepInfo(
    val since: Long,
    val endsAt: Long,
    val energyFrom: Int,
    /** С этой минуты сна хватает для уровня: будить можно, условие «выспался» выполнено. */
    val enoughAt: Long = since,
) {
    fun energyAt(now: Long): Int {
        val slept = ((now - since).toFloat() / (endsAt - since)).coerceIn(0f, 1f)
        return energyFrom + ((100 - energyFrom) * slept).toInt()
    }
}

/**
 * Игра в панели «Игры уровня». [required] — игра уровня: без неё уровень не пройти.
 * [passed] — пройдена в этом уровне. [reward] — сколько дадут за первую победу;
 * 0 — награда за эту игру уже получена раньше.
 */
data class LevelGame(
    val taskId: String,
    val title: String,
    val icon: ItemArt?,
    val required: Boolean,
    val passed: Boolean,
    val reward: Int,
)

/**
 * Чем план расходится с тратами. [savingsLeft] — сколько ещё положить в копилку, как
 * задумано. [overspent] — сколько потрачено сверх плана и ещё не покрыто монетами,
 * пришедшими после плана: их даёт выигрыш в игре, и перерасход можно отыграть.
 * Засчитан ли план, решает ядро (`LevelCheck.planMatched`), здесь — только почему нет.
 */
data class PlanGap(val savingsLeft: Int, val overspent: Int)

/** Сколько игр по желанию стоит на уровне рядом с обязательной: вместе с ней — три. */
private const val EXTRA_LEVEL_GAMES = 2

/** Какая панель ухода открыта поверх комнаты. */
enum class CareTarget(val title: String) {
    FOOD("Покормить"),
    BATH("Для ванны"),
}

sealed interface HomeEvent {
    /** Открыть итоги: `PeriodResultRoute(periodNumber)`. */
    data class PeriodClosed(val periodNumber: Int) : HomeEvent

    data class Rejected(val reason: Rejection) : HomeEvent

    /** Покупка ухода прошла: что изменилось и что дальше (ТЗ п. 2.5.9). */
    data class Purchased(val feedback: ActionFeedback) : HomeEvent

    /** Питомец проснулся — сам или разбудили: сколько выспался (ТЗ п. 2.5.9). */
    data class Woke(val feedback: ActionFeedback) : HomeEvent
}

class HomeViewModel(
    private val session: Session,
    private val game: Game,
    private val content: GameContent,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = session.activeGame
        .filterNotNull()
        .map(::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    init {
        // Сохранение до игры уровня: текущему периоду она выдаётся при первом входе.
        viewModelScope.launch {
            val saved = session.activeGame.filterNotNull().first()
            if (saved.state.currentPeriod.levelTaskId == null) session.execute { assignLevelGame(it) }
        }
    }

    fun closePeriod() {
        viewModelScope.launch {
            val event = when (val result = session.execute { closePeriod(it) }) {
                is GameResult.Ok -> HomeEvent.PeriodClosed(result.state.periods.dropLast(1).last().number)
                is GameResult.Rejected -> HomeEvent.Rejected(result.reason)
            }
            _events.send(event)
        }
    }

    /**
     * Купить предмет ухода. Состояние обновится само — оно читается из сохранённой игры.
     * [fromWants] — сначала взять из радостей столько, чтобы покупка не вышла за план.
     */
    fun buy(itemId: String, fromWants: Boolean = false) {
        viewModelScope.launch {
            val before = session.activeGame.first()?.state
            when (val result = session.execute { if (fromWants) buyFromWants(it, itemId) else buy(it, itemId) }) {
                is GameResult.Ok -> {
                    val item = content.item(itemId)
                    if (before != null && item != null) {
                        // Мыло не моет сразу, а встаёт на полочку: шкалы не сдвинулись, и отклик говорит,
                        // на сколько его хватит, а не «что изменилось».
                        val uses = item.uses
                        _events.send(
                            HomeEvent.Purchased(
                                ActionFeedback(
                                    title = if (uses != null) "На полочку: ${item.label}" else "Купили: ${item.label}",
                                    lines = changesBetween(before, result.state),
                                    why = if (uses != null) {
                                        "Мыло — нужное. Теперь на полочке хватит на ${times(game.stock(result.state, item.id))}."
                                    } else {
                                        "Еда и ванна — это нужное."
                                    },
                                    next = if (uses != null) "Нажми «Мыть» в ванной." else "Кольца у кнопок покажут, что ещё нужно.",
                                    itemId = item.id,
                                    warning = overPlanWarning(result.state, item.category),
                                ),
                            ),
                        )
                    }
                }
                is GameResult.Rejected -> _events.send(HomeEvent.Rejected(result.reason))
            }
        }
    }

    /** Надеть вещь из панели «Финни»: шляпа и очки носятся вместе. */
    fun wear(itemId: String) {
        viewModelScope.launch {
            (session.execute { wear(it, itemId) } as? GameResult.Rejected)?.let { _events.send(HomeEvent.Rejected(it.reason)) }
        }
    }

    fun takeOff(itemId: String) {
        viewModelScope.launch { session.execute { takeOff(it, itemId) } }
    }

    /** Перекрасить питомца: окрас — часть профиля, как на экране знакомства. */
    fun setBodyColor(color: BodyColor) {
        viewModelScope.launch {
            val profile = session.activeGame.first()?.profile ?: return@launch
            if (profile.appearance.bodyColor == color) return@launch
            session.updateProfile(profile.petName, profile.appearance.copy(bodyColor = color))
        }
    }

    /**
     * Шаг мытья в ванной: предметом [itemId] с полочки или водой (null). Денег не тратит —
     * тратит раз предмета. Окна-отклика нет: прибавка всплывает над питомцем, а кольцо на
     * кнопке ванны растёт (BathRoom). Отказ — словами, как везде.
     */
    fun wash(itemId: String?) {
        viewModelScope.launch {
            (session.execute { wash(it, itemId) } as? GameResult.Rejected)?.let {
                _events.send(HomeEvent.Rejected(it.reason))
            }
        }
    }

    /** Уложить спать в капсулу. Бесплатно: сон не покупают. */
    fun sleep() {
        viewModelScope.launch {
            (session.execute { sleep(it) } as? GameResult.Rejected)?.let {
                _events.send(HomeEvent.Rejected(it.reason))
            }
        }
    }

    /**
     * Разбудить — или отметить, что проснулся сам: срок вышел, и хранилище уже
     * разбудило его перед командой (тогда `wake` отвечает NotAsleep). Итог в обоих
     * случаях считается от состояния до пробуждения.
     */
    fun wake() {
        viewModelScope.launch {
            val before = session.activeGame.first()?.state?.takeIf { it.sleepingSince != null } ?: return@launch
            val after = when (val result = session.execute { wake(it) }) {
                is GameResult.Ok -> result.state
                is GameResult.Rejected -> (game.wake(before) as? GameResult.Ok)?.state ?: return@launch
            }
            val full = after.pet.energy >= 100
            _events.send(
                HomeEvent.Woke(
                    ActionFeedback(
                        // Одной строкой: на главном видна только она и значки шкал.
                        title = if (full) "Выспался!" else "Проснулся раньше — сон не полный",
                        lines = changesBetween(before, after),
                        why = if (full) "Сон — это нужное. И он бесплатный." else "Сон набирается постепенно: чем дольше спит, тем больше.",
                        next = "К следующему уровню сон снова убудет — следи за кольцом у кнопки зала.",
                    ),
                ),
            )
        }
    }

    /**
     * Поиграли с игрушкой: [shakes] потряхиваний рядом с питомцем с прошлого раза.
     * Экран копит их и присылает пачкой — сохранять каждое движение пальца незачем.
     */
    fun play(toyId: String, shakes: Int) {
        if (shakes <= 0) return
        viewModelScope.launch { session.execute { play(it, toyId, shakes) } }
    }

    private fun toUiState(saved: SavedGame): HomeUiState.Ready {
        val state = saved.state
        val level = game.level(state)
        val check = game.levelCheck(state)
        val savingsPlanLeft = game.planReport(state)?.let {
            (it.plan.savings - it.facts.savings).coerceAtLeast(0)
        } ?: 0
        return HomeUiState.Ready(
            petName = saved.profile.petName,
            appearance = saved.profile.appearance,
            isDemo = state.isDemo,
            stats = state.pet,
            emotion = game.emotion(state),
            level = level,
            title = game.title(state),
            upcoming = homeUnlocks(game, content, state),
            upcomingFood = content.forSale.filter { it.effect.satiety > 0 && !game.isUnlocked(state, it) }
                .minByOrNull { it.unlockLevel },
            check = check,
            stage = game.stage(state),
            balance = state.balance,
            totalSavings = state.totalSavings,
            goal = game.goalProgress(state),
            periodNumber = state.currentPeriod.number,
            phase = state.currentPeriod.phase,
            needsHint = game.needsHint(state),
            levelGame = state.currentPeriod.levelTaskId?.let { content.task(it)?.title },
            levelGames = levelGames(state),
            planGap = planGap(state, check),
            savingsPlanLeft = savingsPlanLeft,
            reserveMode = game.isReserveMode(state),
            needsThreshold = content.economy.pet.needsThreshold,
            criticalThreshold = content.economy.pet.emotionLow,
            // Что лежит на столе и что в ванной, решает не список имён, а эффект
            // предмета: добавят в контент новую еду — она появится на столе сама.
            food = previews(state) { it.effect.satiety > 0 },
            care = previews(state) { it.effect.hygiene > 0 },
            bath = bathShelf(state),
            glossary = content.glossary,
            sleepCardsPerSleep = content.economy.sleepCards.maxPerSleep,
            needsLeft = game.planReport(state)?.let { (it.plan.needs - it.facts.needs).coerceAtLeast(0) },
            wantsLeft = game.planReport(state)?.let { (it.plan.wants - it.facts.wants).coerceAtLeast(0) },
            fromWants = content.shop.mapNotNull { item -> game.wantsToNeeds(state, item.id)?.let { item.id to it } }.toMap(),
            worn = state.worn,
            outfit = game.wardrobe(state).map { it.id }.toSet().let { owned ->
                content.shop.filter { it.kind == ItemKind.ACCESSORY }.map { Outfit(it.id, it.label, it.id in owned) }
            },
            sleep =state.sleepingSince?.let { since ->
                val endsAt = game.sleepEndsAt(state)!!
                val threshold = content.economy.pet.needsThreshold
                val energy = state.pet.energy
                SleepInfo(
                    since = since,
                    endsAt = endsAt,
                    energyFrom = energy,
                    // Сон растёт ровно, как в [SleepInfo.energyAt]: до порога — та же доля пути.
                    // Секунда сверху — ядро округляет сон вниз, и разбуженный ровно в срок
                    // получил бы на единицу меньше порога.
                    enoughAt = if (energy >= threshold) {
                        since
                    } else {
                        since + (endsAt - since) * (threshold - energy) / (100 - energy) + 1_000
                    },
                )
            },
            toys = game.toys(state).map { it.id },
            playLeft = game.playMoodLeft(state),
            moodPerShake = content.economy.play.moodPerShake,
            moodLow = state.pet.mood < content.economy.pet.emotionLow,
            moodHappy = state.pet.mood >= content.economy.pet.emotionHappy,
            toyPlayed = state.playSince != null,
            nextStep = nextStepFor(
                check = check,
                stats = state.pet,
                needsThreshold = content.economy.pet.needsThreshold,
                asleep = state.sleepingSince != null,
                savingsPlanLeft = savingsPlanLeft,
                availableBalance = state.balance,
            ),
        )
    }

    /**
     * Три игры на уровень (плейтест 29.09: одной игры на уровень мало). Обязательная —
     * та, что выдало ядро; две по желанию — из открытых игр других серий. Сначала те,
     * за которые награду ещё не получали, чтобы игра по желанию чего-то стоила. Порядок
     * задаёт номер уровня: пока уровень идёт, набор не меняется от входа к входу.
     * Условия уровня и награды не трогаем — их по-прежнему считает ядро.
     */
    private fun levelGames(state: GameState): List<LevelGame> {
        val period = state.currentPeriod
        val required = period.levelTaskId?.let(content::task) ?: return emptyList()
        fun everWon(id: String) = state.attempts.any { it.taskId == id && it.outcome == TaskOutcome.SUCCESS }
        // Порядок — по победам до этого уровня: выигранная сейчас игра не должна
        // уезжать в конец и выпадать из тройки. Плейтест 29.09: «засчитывается только
        // одна» — пройденную по желанию подменяла другая, ещё не пройденная.
        fun wonBefore(id: String) = state.attempts.any {
            it.taskId == id && it.outcome == TaskOutcome.SUCCESS && it.periodNumber < period.number
        }
        fun row(task: TaskDefinition, isRequired: Boolean) = LevelGame(
            taskId = task.id,
            title = task.title,
            icon = taskIcon(task),
            required = isRequired,
            passed = state.attempts.any {
                it.periodNumber == period.number && it.taskId == task.id && it.outcome == TaskOutcome.SUCCESS
            },
            reward = if (everWon(task.id)) 0 else (task.reward ?: content.economy.taskReward).success,
        )
        val extras = game.currentTasks(state)
            .filter { it.seriesId != required.seriesId && game.isTaskAvailable(state, it) }
            .shuffled(Random(period.number))
            .sortedBy { wonBefore(it.id) }
            .take(EXTRA_LEVEL_GAMES)
        return listOf(row(required, isRequired = true)) + extras.map { row(it, isRequired = false) }
    }

    /**
     * Покупка вышла за план — сразу, в карточке покупки, а не только в итогах уровня.
     * Плейтест 29.09: «не выполнил план» узнавали в самом конце и не понимали, когда.
     */
    private fun overPlanWarning(after: GameState, category: Category): String? {
        val report = game.planReport(after) ?: return null
        val over = when (category) {
            Category.NEEDS -> report.facts.needs - report.plan.needs
            Category.WANTS -> report.facts.wants - report.plan.wants
        }
        if (over <= 0) return null
        val part = if (category == Category.NEEDS) "нужное" else "«хочется»"
        return "На $part уже на $over больше плана"
    }

    private fun planGap(state: GameState, check: LevelCheck): PlanGap? {
        if (check.planMatched) return null
        val report = game.planReport(state) ?: return null
        val tolerance = content.economy.planTolerance
        return PlanGap(
            savingsLeft = (report.plan.savings - report.facts.savings).takeIf { it > tolerance } ?: 0,
            overspent = (report.overspend - report.facts.unplannedIncome).takeIf { it > tolerance } ?: 0,
        )
    }

    /** Что стоит на полочке ванной и что можно сделать без мыла. */
    private fun bathShelf(state: GameState): BathShelf {
        val rule = content.economy.bath
        val hygiene = state.pet.hygiene
        return BathShelf(
            items = (game.soaps + game.bathExtras).mapNotNull { item ->
                game.previewPurchase(state, item.id)?.let {
                    ShelfItem(
                        it,
                        left = game.stock(state, item.id),
                        uses = item.uses ?: 1,
                        step = item.bathStep(),
                        lockedUntil = item.unlockLevel.takeUnless { game.isUnlocked(state, item) },
                    )
                }
            },
            hygiene = hygiene,
            clean = hygiene >= rule.cleanFrom,
            canRinse = hygiene < rule.waterUpTo,
            full = hygiene >= PetRules.STAT_MAX,
        )
    }

    private fun previews(state: GameState, fits: (ShopItem) -> Boolean): List<PurchasePreview> =
        content.forSale.filter { fits(it) && game.isUnlocked(state, it) }.mapNotNull { game.previewPurchase(state, it.id) }

    companion object {
        val Factory = viewModelFactory {
            initializer { appContainer().let { HomeViewModel(it.session, it.game, it.content) } }
        }
    }
}

/** Какой шаг мытья делает предмет полочки: мыло — намылить, пара (щётка с пастой) — зубы, остальное — вытереть. */
private fun ShopItem.bathStep(): BathStep = when {
    !bathExtra -> BathStep.SOAP
    pairWith != null -> BathStep.TEETH
    else -> BathStep.DRY
}
