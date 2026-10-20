package ru.finney.pet.domain.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
enum class TaskTheme {
    @SerialName("planning") PLANNING,
    @SerialName("savings") SAVINGS,
    @SerialName("shopping") SHOPPING,
}

enum class TaskOutcome { SUCCESS, FAIL }

/** Задание из tasks.json. Движок выбирается полем `engine`, схемы — docs/economy.md, раздел 9. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("engine")
sealed class TaskDefinition {
    abstract val id: String
    abstract val theme: TaskTheme
    abstract val title: String
    abstract val intro: String
    abstract val explainOk: String
    abstract val explainFail: String

    /** Переопределяет награду из economy.json. */
    abstract val reward: TaskReward?

    /** С какого периода задание открыто. В демо-режиме игнорируется. */
    abstract val unlockPeriod: Int

    /** С какого уровня питомца открыт этот вариант. В демо-режиме игнорируется. */
    abstract val unlockLevel: Int

    /**
     * Игра, к которой относится вариант. У «Кассы» три варианта с одним [series] и разным
     * [unlockLevel]: числа и тексты свои, движок и тема общие. Без поля задание — само себе
     * игра из одного варианта. Для ТЗ игра — одно задание, варианты — его сложность.
     */
    abstract val series: String?

    val seriesId: String get() = series ?: id
}

@Serializable
@SerialName("distributor")
data class DistributorTask(
    override val id: String,
    override val theme: TaskTheme,
    override val title: String,
    override val intro: String,
    override val explainOk: String,
    override val explainFail: String,
    override val reward: TaskReward? = null,
    override val unlockPeriod: Int = 1,
    override val unlockLevel: Int = 1,
    override val series: String? = null,
    val amount: Int,
    val baskets: List<TaskBasket>,
    val rules: List<DistributorRule> = emptyList(),
    val goal: TaskGoalPreview? = null,
) : TaskDefinition()

@Serializable
data class TaskBasket(val id: String, val label: String, val hint: String? = null)

/** Цель, на которую показывается влияние суммы в корзине [basket]. */
@Serializable
data class TaskGoalPreview(val label: String, val price: Int, val saved: Int, val basket: String)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class DistributorRule {
    abstract val basket: String

    @Serializable
    @SerialName("min")
    data class Min(override val basket: String, val value: Int) : DistributorRule()

    @Serializable
    @SerialName("max")
    data class Max(override val basket: String, val value: Int) : DistributorRule()
}

@Serializable
@SerialName("basket")
data class BasketTask(
    override val id: String,
    override val theme: TaskTheme,
    override val title: String,
    override val intro: String,
    override val explainOk: String,
    override val explainFail: String,
    override val reward: TaskReward? = null,
    override val unlockPeriod: Int = 1,
    override val unlockLevel: Int = 1,
    override val series: String? = null,
    val limit: Int,
    val shelf: List<ShelfItem>,
    val preloaded: List<String> = emptyList(),
    val rules: List<BasketRule> = emptyList(),
    /** Цель одной фразой — видна с первого экрана: «Купи всё из списка и уложись в 40». */
    val goalText: String? = null,
) : TaskDefinition()

/**
 * Товар на полке задания. [qty] — сколько штук в упаковке: «5 яблок за 20» выгоднее «2 за 10».
 * [promo] — надпись акции на ценнике («Акция!»); цена в [price] уже со скидкой.
 * [art] и [emoji] — только для рисунка, см. [ItemArt].
 */
@Serializable
data class ShelfItem(
    val id: String,
    val label: String,
    val price: Int,
    val category: Category,
    val qty: Int = 1,
    val promo: String? = null,
    override val art: String? = null,
    override val emoji: String? = null,
) : ItemArt

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class BasketRule {
    /** Строка списка покупок. null — правило проверяется, но в списке не показывается. */
    open val label: String? get() = null

    @Serializable
    @SerialName("hasCategory")
    data class HasCategory(val category: Category) : BasketRule()

    @Serializable
    @SerialName("includes")
    data class Includes(val item: String) : BasketRule()

    @Serializable
    @SerialName("excludes")
    data class Excludes(val item: String) : BasketRule()

    /** Взят хотя бы один из [items]: «мыло» — брусок или жидкое. */
    @Serializable
    @SerialName("anyOf")
    data class AnyOf(val items: List<String>, override val label: String? = null) : BasketRule()

    /** Штук из [items] вместе не меньше [qty]: «4 яблока на 2 дня». */
    @Serializable
    @SerialName("minQty")
    data class MinQty(val items: List<String>, val qty: Int, override val label: String? = null) : BasketRule()
}

@Serializable
@SerialName("goal_slider")
data class GoalSliderTask(
    override val id: String,
    override val theme: TaskTheme,
    override val title: String,
    override val intro: String,
    override val explainOk: String,
    override val explainFail: String,
    override val reward: TaskReward? = null,
    override val unlockPeriod: Int = 1,
    override val unlockLevel: Int = 1,
    override val series: String? = null,
    val goalPrice: Int,
    val periods: Int,
    val incomePerPeriod: Int,
    val step: Int = 5,
) : TaskDefinition()

// ---------- Мини-игры ----------
//
// Мини-игра — это задание со своим движком: награда, попытки и прогресс у неё общие
// со всеми заданиями. Живут в assets/content/tasks.json, правила — docs/minigames.md.

/**
 * Рисунок предмета в мини-игре. [art] — имя картинки из res/drawable-nodpi без расширения
 * (`item_food_apple`), [emoji] — если картинки нет. Нет ни того, ни другого — первая буква названия.
 */
interface ItemArt {
    val art: String?
    val emoji: String?
}

/** «Конвейер»: разложить вещи на нужное и желаемое. */
@Serializable
@SerialName("sorter")
data class SorterTask(
    override val id: String,
    override val theme: TaskTheme,
    override val title: String,
    override val intro: String,
    override val explainOk: String,
    override val explainFail: String,
    override val reward: TaskReward? = null,
    override val unlockPeriod: Int = 1,
    override val unlockLevel: Int = 1,
    override val series: String? = null,
    val items: List<SortItem>,
    /** Сколько вещей нужно разложить верно для успеха. */
    val minCorrect: Int,
) : TaskDefinition()

/** [why] — одна фраза, которую ребёнок видит, если отнёс вещь не туда. */
@Serializable
data class SortItem(
    val id: String,
    val label: String,
    val category: Category,
    val why: String,
    override val art: String? = null,
    override val emoji: String? = null,
) : ItemArt

/** «Дорога к цели»: каждый день решить, сколько из дохода отложить. */
@Serializable
@SerialName("goal_race")
data class GoalRaceTask(
    override val id: String,
    override val theme: TaskTheme,
    override val title: String,
    override val intro: String,
    override val explainOk: String,
    override val explainFail: String,
    override val reward: TaskReward? = null,
    override val unlockPeriod: Int = 1,
    override val unlockLevel: Int = 1,
    override val series: String? = null,
    val goal: RaceGoal,
    val days: Int,
    val incomePerDay: Int,
    val step: Int = 5,
    /** Сколько уже лежит в копилке на старте. */
    val startSaved: Int = 0,
    val events: List<RaceEvent> = emptyList(),
    /**
     * Настроение Финни — сердечки на старте, их же больше не бывает. Отказ от
     * соблазна дня — минус одно, позволил себе — плюс одно. Упало до нуля —
     * Финни загрустил до конца дороги: цель засчитывается, но без бонуса.
     */
    val mood: Int = 3,
    /** Объяснение вместо [explainOk], если цель набрана, а Финни загрустил по дороге. */
    val explainSad: String? = null,
) : TaskDefinition()

@Serializable
data class RaceGoal(
    val label: String,
    val price: Int,
    override val art: String? = null,
    override val emoji: String? = null,
) : ItemArt

/** Соблазн дня [day] (с 1). Взят, если в этот день потрачено не меньше [price]. */
@Serializable
data class RaceEvent(
    val day: Int,
    val label: String,
    val price: Int,
    override val art: String? = null,
    override val emoji: String? = null,
) : ItemArt

/**
 * «Дождливый день»: спланировать траты с запасом, потом случается непредвиденное.
 * [surprises] — что случилось на этой неделе: в tasks.json у каждого сюрприза
 * `chance`, и какие выпали, решает зерно попытки. Бывает и ни одного.
 * [worstCase] — сколько стоили бы все сюрпризы шаблона сразу. Его ставит [ru.finney.pet.domain.tasks.TaskGenerator],
 * в tasks.json не пишется; 0 — считать по [surprises].
 */
@Serializable
@SerialName("reserve")
data class ReserveTask(
    override val id: String,
    override val theme: TaskTheme,
    override val title: String,
    override val intro: String,
    override val explainOk: String,
    override val explainFail: String,
    override val reward: TaskReward? = null,
    override val unlockPeriod: Int = 1,
    override val unlockLevel: Int = 1,
    override val series: String? = null,
    val amount: Int,
    val spendings: List<Spending>,
    val surprises: List<Surprise>,
    val worstCase: Int = 0,
    /** Объяснение вместо [explainOk], если ради сюрприза отложили желаемое. */
    val explainDropped: String? = null,
    /** Объяснение вместо [explainOk], если в плане не было ни одного желаемого. */
    val explainNoJoy: String? = null,
) : TaskDefinition()

/**
 * Трата недели. Нужное нельзя убрать из плана. Отложить его можно, если на сюрприз не
 * хватило запаса, но тогда игра не пройдена. Отложить желаемое — не провал.
 */
@Serializable
data class Spending(
    val id: String,
    val label: String,
    val price: Int,
    val category: Category,
    override val art: String? = null,
    override val emoji: String? = null,
) : ItemArt

@Serializable
data class Surprise(
    val label: String,
    val text: String,
    val price: Int,
    override val art: String? = null,
    override val emoji: String? = null,
) : ItemArt

/**
 * «Лимонадная лавка»: закупить сырьё под число гостей.
 *
 * С [days] — лавка на несколько дней: каждое утро ребёнок решает, сколько купить и почём
 * продавать стакан. Гостей приходит по настоящей погоде дня ([StandDay.weather]), а видит
 * он прогноз ([StandDay.forecast]) — «прогноз — подсказка, а не обещание». Лимоны переходят
 * на завтра и портятся через [freshDays] дней. Дороже стакан — меньше гостей
 * ([guestsPerCoin]). Победа — прибыль за все дни не меньше [goalProfit]. Без [days] — один
 * день с [guests] гостями, как раньше.
 */
@Serializable
@SerialName("stand")
data class StandTask(
    override val id: String,
    override val theme: TaskTheme,
    override val title: String,
    override val intro: String,
    override val explainOk: String,
    override val explainFail: String,
    override val reward: TaskReward? = null,
    override val unlockPeriod: Int = 1,
    override val unlockLevel: Int = 1,
    override val series: String? = null,
    /** Сколько денег на закупку. */
    val budget: Int,
    val ingredient: StandIngredient,
    /** Цена одного стакана для гостя. */
    val cupPrice: Int,
    val guests: Int = 0,
    /** Подсказка о погоде и числе гостей: «Жарко — придут около 8 гостей». */
    val forecast: String = "",
    val days: List<StandDay> = emptyList(),
    /** Сколько гостей приходит в такую погоду, если стакан по [cupPrice]. */
    val demand: Map<Weather, Int> = emptyMap(),
    /** Цены стакана на выбор; пусто — только [cupPrice]. */
    val prices: List<Int> = emptyList(),
    /** На столько гостей меньше за каждую монету цены выше [cupPrice] (и больше — за каждую ниже). */
    val guestsPerCoin: Int = 0,
    /** Лимон, купленный утром, годен в этот день и ещё [freshDays] − 1, потом портится. */
    val freshDays: Int = 2,
    val goalProfit: Int = 0,
) : TaskDefinition() {
    /** Цены стакана, из которых выбирает ребёнок. */
    val cupPrices: List<Int> get() = prices.ifEmpty { listOf(cupPrice) }
}

@Serializable
enum class Weather {
    @SerialName("sun") SUN,
    @SerialName("clouds") CLOUDS,
    @SerialName("rain") RAIN,
}

/** День лавки: что обещал прогноз и какая погода была на самом деле. */
@Serializable
data class StandDay(val forecast: Weather, val weather: Weather)

/** Из одной штуки выходит [yields] стаканов. */
@Serializable
data class StandIngredient(
    val label: String,
    val price: Int,
    val yields: Int,
    override val art: String? = null,
    override val emoji: String? = null,
) : ItemArt

/** «Касса»: отсчитать сдачу или заплатить без сдачи. */
@Serializable
@SerialName("change")
data class ChangeTask(
    override val id: String,
    override val theme: TaskTheme,
    override val title: String,
    override val intro: String,
    override val explainOk: String,
    override val explainFail: String,
    override val reward: TaskReward? = null,
    override val unlockPeriod: Int = 1,
    override val unlockLevel: Int = 1,
    override val series: String? = null,
    /** Номиналы монет в ящике кассы. */
    val coins: List<Int>,
    val rounds: List<ChangeRound>,
    /** Сколько раундов нужно решить верно с первой попытки. По умолчанию — все. */
    val minCorrect: Int? = null,
) : TaskDefinition()

@Serializable
enum class ChangeMode {
    /** Покупатель дал [ChangeRound.paid], ребёнок отсчитывает сдачу из ящика. */
    @SerialName("give") GIVE,

    /** Финни платит ровно [ChangeRound.price] монетами из кошелька [ChangeRound.wallet]. */
    @SerialName("exact") EXACT,
}

@Serializable
data class ChangeRound(
    val mode: ChangeMode,
    val label: String,
    val price: Int,
    val paid: Int? = null,
    val wallet: List<Int> = emptyList(),
    override val art: String? = null,
    override val emoji: String? = null,
) : ItemArt {
    /** Сколько монет нужно положить на кассу. */
    val target: Int get() = if (mode == ChangeMode.GIVE) (paid ?: 0) - price else price
}

/**
 * «Проверь чек»: сверить чек с пакетом и сдачу, найти ошибки кассира.
 * [paid] — сколько Финни дал на кассе, [change] — сколько сдачи вернул кассир.
 * С [mistakes] строки, «дали», сдачу и тексты итога пишет генератор (`ReceiptMaker`),
 * каждый раз с новыми ошибками; без него — как записано в tasks.json.
 */
@Serializable
@SerialName("receipt")
data class ReceiptTask(
    override val id: String,
    override val theme: TaskTheme,
    override val title: String,
    override val intro: String,
    override val explainOk: String,
    override val explainFail: String,
    override val reward: TaskReward? = null,
    override val unlockPeriod: Int = 1,
    override val unlockLevel: Int = 1,
    override val series: String? = null,
    /** Что на самом деле в пакете. Цена — как на ценнике в магазине. */
    val cart: List<ReceiptItem>,
    /** Что пробито в чеке, по строке на штуку. */
    val lines: List<ReceiptLine> = emptyList(),
    val paid: Int = 0,
    val change: Int = 0,
    /** Сколько ошибок сделает кассир; null — ошибки уже записаны в [lines] и [change]. */
    val mistakes: Int? = null,
) : TaskDefinition()

/** Товар в пакете: [qty] штук по [price] за штуку. */
@Serializable
data class ReceiptItem(
    val id: String,
    val label: String,
    val price: Int,
    val qty: Int = 1,
    override val art: String? = null,
    override val emoji: String? = null,
) : ItemArt

/** Строка чека: одна штука товара [item] по цене [price]. */
@Serializable
data class ReceiptLine(val id: String, val item: String, val price: Int)

/**
 * «Подработка»: разложить дела по неделе, набрать на цель и оставить дни отдыха.
 * В каждом из [days] дней — [hoursPerDay] часиков; пустых дней нужно не меньше [minRestDays].
 * Усталость: дело, которое было и вчера, приносит на [tiredCut] меньше (не ниже нуля).
 * За [varietyCount] разных дел за неделю — [varietyBonus] сверху. Так «самое выгодное
 * дело каждый день» перестаёт быть лучшим планом.
 */
@Serializable
@SerialName("chores")
data class ChoresTask(
    override val id: String,
    override val theme: TaskTheme,
    override val title: String,
    override val intro: String,
    override val explainOk: String,
    override val explainFail: String,
    override val reward: TaskReward? = null,
    override val unlockPeriod: Int = 1,
    override val unlockLevel: Int = 1,
    override val series: String? = null,
    val goal: RaceGoal,
    val days: Int,
    val hoursPerDay: Int,
    val minRestDays: Int = 1,
    val chores: List<Chore>,
    val tiredCut: Int = 0,
    val varietyBonus: Int = 0,
    val varietyCount: Int = 3,
) : TaskDefinition()

/**
 * Дело: занимает [hours] часиков, приносит [reward]. [maxTimes] — сколько раз за неделю его дают.
 * [pay] — оплата по дням недели, по числу на день: в выходной собака платит больше; нет —
 * каждый день [reward]. [onDays] — в какие дни (с 1) дело дают; нет — в любой.
 */
@Serializable
data class Chore(
    val id: String,
    val label: String,
    val hours: Int,
    val reward: Int,
    val maxTimes: Int? = null,
    val pay: List<Int>? = null,
    val onDays: List<Int>? = null,
    override val art: String? = null,
    override val emoji: String? = null,
) : ItemArt
