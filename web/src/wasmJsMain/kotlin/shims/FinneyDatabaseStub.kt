package ru.finney.pet.data.db

/** Константы базы, на которые ссылается Entities.kt. Самой базы на вебе нет. */
class FinneyDatabase private constructor() {
    companion object {
        /** Должно совпадать с app/.../FinneyDatabase.kt. */
        const val ENERGY_ON_MIGRATION = "30"
    }
}
