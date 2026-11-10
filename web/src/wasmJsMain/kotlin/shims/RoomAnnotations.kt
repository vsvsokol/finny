// Аннотации Room для data/db/Entities.kt. На вебе базы нет: строки хранит
// ru.finney.pet.web.WebGameStorage, а аннотации — пустышки с теми же параметрами.

@file:Suppress("unused")

package androidx.room

import kotlin.reflect.KClass

annotation class Entity(
    val tableName: String = "",
    val indices: Array<Index> = [],
    val primaryKeys: Array<String> = [],
    val foreignKeys: Array<ForeignKey> = [],
)

annotation class ForeignKey(
    val entity: KClass<*>,
    val parentColumns: Array<String>,
    val childColumns: Array<String>,
    val onDelete: Int = 1,
) {
    companion object {
        const val CASCADE: Int = 5
    }
}

annotation class Index(vararg val value: String)

annotation class PrimaryKey(val autoGenerate: Boolean = false)

annotation class ColumnInfo(val name: String = "", val defaultValue: String = "")

annotation class Embedded(val prefix: String = "")

annotation class Relation(val parentColumn: String, val entityColumn: String)
