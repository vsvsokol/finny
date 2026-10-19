package ru.finney.pet.domain.model

import kotlinx.serialization.Serializable

/** economy.json. Смысл каждого числа — docs/economy.md. Стадии нумеруются с 1. */
@Serializable
data class EconomyConfig(
    val incomeByStage: List<Int>,
    val taskReward: TaskReward,
    val parentBonus: ParentBonusRule,
    val planTolerance: Int,
    /** В скольких направлениях плана (нужное, желаемое, копилка) должна быть сумма > 0. ТЗ п. 2.5.5 — во всех трёх. */
    val planDirections: Int = 3,
    val points: PointsRule,
    /** Сколько из трёх условий уровня (нужное, план, копилка) нужно выполнить, чтобы его пройти. Игра уровня обязательна сверх них. */
    val conditionsToPass: Int,
    /** То же в демо-режиме: эксперт проходит 6 уровней за несколько минут (docs/economy.md, раздел 8). */
    val demoConditionsToPass: Int = conditionsToPass,
    /** Обязательна ли игра уровня в демо-режиме. Сама игра выдаётся и видна в любом случае. */
    val demoLevelGameRequired: Boolean = true,
    val maxLevel: Int,
    val stageStartLevels: List<Int>,
    /** Звание питомца на каждой стадии — за навык, который ребёнок к ней освоил. Пусто — без званий. */
    val stageTitles: List<String> = emptyList(),
    val pet: PetRule,
    val play: PlayRule = PlayRule(),
    val sleepCards: SleepCardRule = SleepCardRule(),
    val bath: BathRule = BathRule(),
    val reminders: ReminderRule = ReminderRule(),
) {
    fun income(stage: Int): Int = incomeByStage[stage - 1]
    fun decay(stage: Int): StatEffect = pet.decayByStage[stage - 1]
}

@Serializable
/**
 * Награда за задание. [bonus] — сверху к успеху, если движок засчитал бонус (например,
 * в «Дождливом дне» нужное закрыто и желаемое не отменено); выдаётся тоже один раз.
 */
data class TaskReward(val success: Int, val fail: Int, val bonus: Int = 0)

@Serializable
data class ParentBonusRule(val step: Int, val maxPerPeriod: Int)

@Serializable
data class PointsRule(
    val needsCovered: Int,
    val planMatched: Int,
    val savingsAdded: Int,
    val taskSuccess: Int,
    val taskSuccessMaxPerPeriod: Int,
) {
    val maxPerPeriod: Int get() = needsCovered + planMatched + savingsAdded + taskSuccess * taskSuccessMaxPerPeriod
}

@Serializable
data class PetRule(
    val start: PetStats,
    val decayByStage: List<StatEffect>,
    val needsThreshold: Int,
    val emotionLow: Int,
    val emotionHappy: Int,
    /** Полный сон, минуты. */
    val sleepMinutes: Int,
    /** Полный сон в демо-режиме, секунды. */
    val demoSleepSeconds: Int,
    /**
     * Полный сон по стадиям, минуты: малыш спит недолго, чтобы первый заход был игрой, а не
     * ожиданием; взрослый — дольше, сон становится перерывом. null — у всех стадий [sleepMinutes].
     */
    val sleepMinutesByStage: List<Int>? = null,
)

/**
 * Игра с игрушкой. Каждое «потряхивание» рядом с питомцем — плюс [moodPerShake] к настроению,
 * но за одну сессию игры не больше [sessionMoodCap]: дальше питомец наигрался, и игрушка
 * настроения не прибавляет. Сессия начинается с первой прибавки и длится [sessionMinutes]
 * (в демо-режиме — [demoSessionSeconds] секунд); новый уровень начинает новую сессию сразу.
 */
@Serializable
data class PlayRule(
    val moodPerShake: Int = 2,
    val sessionMoodCap: Int = 20,
    val sessionMinutes: Int = 60,
    val demoSessionSeconds: Int = 30,
)

/**
 * Ванна (docs/bath-plan.md). С мылом чистота растёт на его `effect.hygiene`, без мыла водой —
 * на [water], но не выше [waterUpTo]: сполоснуться можно всегда, а отмыться — только с мылом,
 * иначе мыло никто бы не покупал. С [cleanFrom] питомец и так чистый — мыло не тратится зря.
 */
@Serializable
data class BathRule(
    val water: Int = 15,
    val waterUpTo: Int = 40,
    val cleanFrom: Int = 90,
)

/**
 * Сон-загадка: пока питомец спит, ребёнок отвечает на карточки по справочнику. Верный
 * ответ — плюс [energyPerCorrect] ко сну сразу, и питомец просыпается раньше; неверный
 * ничего не отнимает. Карточек за один сон — не больше [maxPerSleep] (счёт ведёт экран).
 */
@Serializable
data class SleepCardRule(
    val energyPerCorrect: Int = 15,
    val maxPerSleep: Int = 5,
)

/**
 * Напоминания о питомце (пакет notifications). Обычно — раз в [everyHours] часов,
 * в демо-режиме — раз в [demoReminderMinutes] минут, чтобы эксперт их увидел.
 * Ночью, с [quietFromHour] до [quietToHour], не пишем — напоминание переносится на утро.
 * Приложение открывали меньше [skipIfOpenedHours] часов назад (в демо — [demoSkipIfOpenedMinutes]
 * минут) — не пишем: ребёнок и так недавно заглядывал.
 */
@Serializable
data class ReminderRule(
    val everyHours: Int = 24,
    val demoReminderMinutes: Int = 2,
    val quietFromHour: Int = 21,
    val quietToHour: Int = 9,
    val skipIfOpenedHours: Int = 6,
    val demoSkipIfOpenedMinutes: Int = 1,
)
