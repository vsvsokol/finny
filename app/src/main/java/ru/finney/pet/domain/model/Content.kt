package ru.finney.pet.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class Category {
    @SerialName("needs") NEEDS,
    @SerialName("wants") WANTS,
}

@Serializable
enum class ItemKind {
    /** Еда, уход, лакомства: покупается сколько угодно раз. */
    @SerialName("consumable") CONSUMABLE,

    /** Шапка, шарф, очки: покупается один раз и остаётся на питомце. */
    @SerialName("accessory") ACCESSORY,

    /**
     * Игрушка: покупается один раз и лежит в зале. С ней играют — берут пальцем
     * и трясут рядом с питомцем, настроение растёт (см. [ru.finney.pet.domain.game.Game.play]).
     */
    @SerialName("toy") TOY,
}

/**
 * Куда надевается аксессуар. Вещи из разных слотов носятся вместе — шляпа и очки, —
 * а в одном слоте новая вещь сменяет прежнюю.
 */
@Serializable
enum class AccessorySlot {
    @SerialName("head") HEAD,
    @SerialName("eyes") EYES,
}

/** Изменение шкал питомца. В снижении за период значения положительные и вычитаются. */
@Serializable
data class StatEffect(
    val satiety: Int = 0,
    val hygiene: Int = 0,
    val mood: Int = 0,
    val energy: Int = 0,
)

@Serializable
data class ShopItem(
    val id: String,
    val label: String,
    val price: Int,
    val category: Category,
    val kind: ItemKind = ItemKind.CONSUMABLE,
    val effect: StatEffect = StatEffect(),
    /** Только у аксессуара: куда он надевается. */
    val slot: AccessorySlot = AccessorySlot.HEAD,
    /** С какого уровня продаётся. Купленное раньше остаётся. docs/economy.md, раздел 8. */
    val unlockLevel: Int = 1,
)

@Serializable
data class Goal(
    val id: String,
    val label: String,
    val price: Int,
    val moodBonus: Int = 0,
    /**
     * Вещь из shop.json, которую ребёнок получает, когда цель достигнута. Копят не на
     * абстрактную «цель», а на то, что потом видно на питомце. В магазине такая вещь
     * не продаётся: её можно только накопить.
     */
    val reward: String? = null,
    /** С какого уровня цель можно выбрать. Выбранная раньше остаётся. docs/economy.md, раздел 8. */
    val unlockLevel: Int = 1,
)

/** glossary.json — справочник терминов, ТЗ п. 2.5.11. */
@Serializable
data class Glossary(
    @SerialName("_comment") val comment: String? = null,
    val terms: List<GlossaryTerm>,
)

@Serializable
data class GlossaryTerm(val id: String, val term: String, val text: String)

/** Весь учебный контент из assets/content. Загружается и проверяется пакетом `content`. */
data class GameContent(
    val economy: EconomyConfig,
    val shop: List<ShopItem>,
    val goals: List<Goal>,
    /** Задания без случайности: разброс — минимум (см. [ru.finney.pet.domain.tasks.TaskGenerator.base]). */
    val tasks: List<TaskDefinition>,
    val glossary: List<GlossaryTerm> = emptyList(),
    /** Шаблоны заданий с разбросом по id; задания без разброса сюда не попадают. */
    val taskTemplates: Map<String, JsonObject> = emptyMap(),
) {
    fun item(id: String): ShopItem? = shop.firstOrNull { it.id == id }
    fun goal(id: String): Goal? = goals.firstOrNull { it.id == id }

    /** Цель, наградой за которую служит вещь [itemId]; null — вещь продаётся в магазине. */
    fun goalFor(itemId: String): Goal? = goals.firstOrNull { it.reward == itemId }

    /** То, что продаётся в магазине: всё, кроме наград за цели. */
    val forSale: List<ShopItem> get() = shop.filter { goalFor(it.id) == null }
    fun task(id: String): TaskDefinition? = tasks.firstOrNull { it.id == id }

    /**
     * Задания по играм: игры — в порядке первого появления в tasks.json, варианты внутри
     * игры — от простого к сложному, по [TaskDefinition.unlockLevel].
     */
    val taskSeries: List<List<TaskDefinition>> =
        tasks.groupBy { it.seriesId }.values.map { variants -> variants.sortedBy { it.unlockLevel } }
}
