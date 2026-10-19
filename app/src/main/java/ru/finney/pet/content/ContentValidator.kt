package ru.finney.pet.content

import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.domain.model.GoalRaceTask
import ru.finney.pet.domain.model.ItemKind
import ru.finney.pet.domain.model.PetLooks
import ru.finney.pet.domain.model.ReserveTask
import ru.finney.pet.domain.model.RewardKind
import ru.finney.pet.domain.model.RoomThemes
import ru.finney.pet.domain.tasks.TaskChecks

/**
 * Проверка ссылок и чисел, которые JSON-схема не ловит. Запускается при загрузке и в unit-тесте,
 * поэтому битый контент ловит CI, а не эксперт.
 */
object ContentValidator {

    fun validate(content: GameContent): List<String> = buildList {
        val e = content.economy
        val stages = e.stageStartLevels.size
        if (e.incomeByStage.size != stages) add("economy.json: incomeByStage — нужно $stages значения, по числу стадий")
        if (e.pet.decayByStage.size != stages) add("economy.json: pet.decayByStage — нужно $stages значения, по числу стадий")
        if (e.stageStartLevels.firstOrNull() != 1) add("economy.json: stageStartLevels должен начинаться с 1")
        if (e.stageStartLevels.zipWithNext().any { (a, b) -> a >= b }) add("economy.json: stageStartLevels должен возрастать")
        if (e.stageStartLevels.any { it > e.maxLevel }) add("economy.json: стадия начинается после maxLevel")
        if (e.planDirections !in 0..3) add("economy.json: planDirections должен быть от 0 до 3")
        if (e.conditionsToPass !in 1..3) add("economy.json: conditionsToPass должен быть от 1 до 3")
        if (e.parentBonus.step <= 0) add("economy.json: parentBonus.step должен быть > 0")
        if (e.incomeByStage.any { it <= 0 }) add("economy.json: доход должен быть > 0")
        // Сон не покупают, поэтому в магазине его не проверить: потребность держится только на спаде.
        if (e.pet.decayByStage.any { it.energy <= 0 }) add("economy.json: pet.decayByStage — сон должен убывать за период")
        if (e.pet.sleepMinutes <= 0 || e.pet.demoSleepSeconds <= 0) add("economy.json: pet.sleepMinutes и demoSleepSeconds должны быть > 0")
        e.pet.sleepMinutesByStage?.let { byStage ->
            if (byStage.size != stages) add("economy.json: pet.sleepMinutesByStage — нужно $stages значения, по числу стадий")
            if (byStage.any { it <= 0 }) add("economy.json: pet.sleepMinutesByStage должны быть > 0")
        }
        if (e.sleepCards.energyPerCorrect <= 0 || e.sleepCards.maxPerSleep <= 0) add("economy.json: sleepCards.energyPerCorrect и maxPerSleep должны быть > 0")

        if (e.play.moodPerShake <= 0 || e.play.sessionMoodCap <= 0) add("economy.json: play.moodPerShake и sessionMoodCap должны быть > 0")
        if (e.play.sessionMinutes <= 0 || e.play.demoSessionSeconds <= 0) add("economy.json: play.sessionMinutes и demoSessionSeconds должны быть > 0")

        duplicates(content.shop.map { it.id }).forEach { add("shop.json: повторяется id $it") }
        // Игрушка — радость, а не потребность: её покупают из «хочется».
        content.shop.filter { it.kind == ItemKind.TOY && it.category != Category.WANTS }.forEach {
            add("shop.json: ${it.id} — игрушка должна быть в категории wants")
        }
        content.goals.forEach { goal ->
            val reward = goal.reward ?: return@forEach
            when (goal.rewardKind) {
                RewardKind.ITEM -> {
                    val item = content.item(reward)
                    if (item == null) add("goals.json: ${goal.id} — награда $reward не найдена в shop.json")
                    else if (item.kind != ItemKind.ACCESSORY) add("goals.json: ${goal.id} — награда $reward должна быть аксессуаром")
                }
                RewardKind.ROOM -> when {
                    reward !in RoomThemes.ALL -> add("goals.json: ${goal.id} — нет темы комнаты $reward")
                    reward == RoomThemes.BASE -> add("goals.json: ${goal.id} — базовая тема открыта всегда, копить на неё нельзя")
                }
                RewardKind.LOOK -> if (reward !in PetLooks.ALL) add("goals.json: ${goal.id} — нет облика $reward")
            }
        }
        duplicates(content.goals.mapNotNull { it.reward }).forEach { add("goals.json: награда $it — у двух целей") }
        content.shop.filter { it.price <= 0 }.forEach { add("shop.json: ${it.id} — цена должна быть > 0") }
        val needs = content.shop.filter { it.category == Category.NEEDS }
        if (needs.none { it.effect.satiety > 0 && it.unlockLevel == 1 }) add("shop.json: нет обязательного товара, поднимающего сытость")
        // Чистота — только мылом с полочки: мытьё тратит запас, а не покупает (docs/bath-plan.md).
        if (needs.none { it.effect.hygiene > 0 && it.uses != null && !it.bathExtra && it.unlockLevel == 1 }) {
            add("shop.json: нет мыла с первого уровня — обязательного товара с чистотой и uses")
        }
        content.shop.filter { (it.uses ?: 1) <= 0 }.forEach { add("shop.json: ${it.id} — uses должен быть > 0") }
        content.shop.filter { it.bathExtra && it.uses == null }.forEach { add("shop.json: ${it.id} — bathExtra только у предмета с uses") }
        content.shop.filter { it.pairWith != null && content.item(it.pairWith)?.bathExtra != true }.forEach {
            add("shop.json: ${it.id} — pairWith должен указывать на предмет ванны с bathExtra")
        }

        duplicates(content.goals.map { it.id }).forEach { add("goals.json: повторяется id $it") }

        // Открытия по уровням (docs/economy.md, раздел 8). План на первом же уровне — во всех трёх
        // направлениях (ТЗ п. 2.5.5), поэтому нужное, хоть одна радость и хоть одна цель открыты сразу.
        if (e.stageTitles.isNotEmpty() && e.stageTitles.size != stages) add("economy.json: stageTitles — нужно $stages значения, по числу стадий")
        if (e.stageTitles.any { it.isBlank() }) add("economy.json: stageTitles — пустое звание")
        content.shop.filter { it.unlockLevel !in 1..e.maxLevel }.forEach { add("shop.json: ${it.id} — unlockLevel должен быть от 1 до ${e.maxLevel}") }
        content.goals.filter { it.unlockLevel !in 1..e.maxLevel }.forEach { add("goals.json: ${it.id} — unlockLevel должен быть от 1 до ${e.maxLevel}") }
        // Еда открыта всегда: без неё питомцу плохо с первой минуты. Предметы ванны с запасом
        // (uses) — мыло получше, щётка, паста — могут открываться позже: отмыться можно и обычным
        // мылом, оно с первого уровня (проверка выше). Пару открывают вместе: щётка без пасты бесполезна.
        needs.filter { it.uses == null && it.unlockLevel != 1 }.forEach { add("shop.json: ${it.id} — нужное без uses (еда) открыто всегда, unlockLevel только 1") }
        content.shop.filter { item -> item.pairWith?.let(content::item)?.let { it.unlockLevel != item.unlockLevel } == true }.forEach {
            add("shop.json: ${it.id} — открывается вместе с парой ${it.pairWith}, unlockLevel должен совпадать")
        }
        if (content.forSale.none { it.category == Category.WANTS && it.unlockLevel == 1 }) add("shop.json: на уровне 1 нечего купить из «хочется» — план не составить")
        if (content.goals.none { it.unlockLevel == 1 }) add("goals.json: на уровне 1 нет цели — копилку в плане не заполнить")
        content.goals.filter { it.price <= 0 }.forEach { add("goals.json: ${it.id} — цена должна быть > 0") }

        duplicates(content.glossary.map { it.id }).forEach { add("glossary.json: повторяется id $it") }
        content.glossary.filter { it.term.isBlank() || it.text.isBlank() }.forEach {
            add("glossary.json: ${it.id} — пустой термин или объяснение")
        }
        val series = content.tasks.map { it.seriesId }.toSet()
        content.glossary.forEach { term ->
            term.games.filter { it !in series }.forEach { add("glossary.json: ${term.id} — нет игры $it") }
        }

        duplicates(content.tasks.map { it.id }).forEach { add("tasks.json: повторяется id $it") }
        for (task in content.tasks) {
            val at = "задание ${task.id}"
            if (task.unlockPeriod < 1) add("$at — unlockPeriod должен быть ≥ 1")
            if (task.unlockLevel !in 1..e.maxLevel) add("$at — unlockLevel должен быть от 1 до ${e.maxLevel}")
            addAll(TaskChecks.problems(task))
            // «Пройдено, но можно лучше» — своим текстом, иначе экран скажет «ничего не отменили».
            if (task is ReserveTask) {
                if (task.explainDropped.isNullOrBlank()) add("$at — нет explainDropped: что сказать, если желаемое отложили ради сюрприза")
                if (task.explainNoJoy.isNullOrBlank()) add("$at — нет explainNoJoy: что сказать, если в плане не было желаемого")
            }
            if (task is GoalRaceTask && task.explainSad.isNullOrBlank()) {
                add("$at — нет explainSad: что сказать, если цель набрана, а Финни загрустил")
            }
        }

        for (variants in content.taskSeries) {
            if (variants.size < 2) continue
            val at = "игра ${variants.first().seriesId}"
            if (variants.map { it::class }.distinct().size > 1) add("$at — у вариантов разные движки")
            if (variants.map { it.theme }.distinct().size > 1) add("$at — у вариантов разные темы")
            duplicates(variants.map { it.unlockLevel.toString() }).forEach { add("$at — два варианта с unlockLevel $it") }
        }
    }

    private fun duplicates(ids: List<String>): Set<String> =
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
}
