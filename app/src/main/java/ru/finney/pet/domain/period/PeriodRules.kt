package ru.finney.pet.domain.period

import ru.finney.pet.domain.model.Category
import ru.finney.pet.domain.model.EntryType
import ru.finney.pet.domain.model.LedgerEntry
import ru.finney.pet.domain.model.PeriodFacts
import ru.finney.pet.domain.model.Plan

/** План и факт периода. docs/economy.md, раздел 3. */
object PeriodRules {

    fun facts(ledger: List<LedgerEntry>, periodNumber: Int): PeriodFacts {
        val entries = ledger.filter { it.periodNumber == periodNumber }
        fun spent(category: Category) = -entries
            .filter { it.type == EntryType.PURCHASE && it.category == category }
            .sumOf { it.balanceDelta }
        return PeriodFacts(
            needs = spent(Category.NEEDS),
            wants = spent(Category.WANTS),
            savings = entries
                .filter { it.type == EntryType.SAVINGS_DEPOSIT || it.type == EntryType.SAVINGS_WITHDRAW }
                .sumOf { it.savingsDelta },
            unplannedIncome = entries.filter { it.unplanned }.sumOf { it.balanceDelta },
        )
    }

    fun overspend(plan: Plan, facts: PeriodFacts): Int =
        maxOf(0, facts.needs - plan.needs) + maxOf(0, facts.wants - plan.wants)

    /**
     * Недотрата не ошибка; заработанное после плана можно тратить; остаток плана
     * права на перерасход не даёт сам по себе — переложить остаток «хочется» в нужное
     * ребёнок может только кнопкой, [ru.finney.pet.domain.game.Game.moveWantsToNeeds].
     */
    fun planMatched(plan: Plan, facts: PeriodFacts, tolerance: Int): Boolean =
        overspend(plan, facts) <= facts.unplannedIncome + tolerance &&
            facts.savings >= plan.savings - tolerance
}
