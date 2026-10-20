package ru.finney.pet.content

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.finney.pet.domain.model.EconomyConfig
import ru.finney.pet.domain.model.GameContent
import ru.finney.pet.domain.model.Glossary
import ru.finney.pet.domain.model.Goal
import ru.finney.pet.domain.model.ShopItem
import ru.finney.pet.domain.model.TaskDefinition
import ru.finney.pet.domain.tasks.TaskGenerator

/** Разбор JSON из assets/content. Не зависит от Android — проверяется unit-тестами. */
object ContentParser {

    /** Неизвестное поле — ошибка: опечатка в JSON не должна молча превращаться в значение по умолчанию. */
    private val json = Json { ignoreUnknownKeys = false }

    const val ECONOMY = "economy.json"
    const val SHOP = "shop.json"
    const val GOALS = "goals.json"
    /** Задания — это мини-игры: у каждой свой движок, docs/minigames.md. */
    const val TASKS = "tasks.json"
    const val GLOSSARY = "glossary.json"

    /** @param read возвращает текст файла по имени из [ECONOMY], [SHOP], [GOALS], [TASKS], [GLOSSARY]. */
    fun parse(read: (String) -> String): GameContent {
        val templates = decode<List<JsonObject>>(TASKS, read)
        return GameContent(
            economy = decode<EconomyConfig>(ECONOMY, read),
            shop = decode<List<ShopItem>>(SHOP, read),
            goals = decode<List<Goal>>(GOALS, read),
            tasks = templates.mapIndexed { i, template -> baseTask(template, i) },
            glossary = decode<Glossary>(GLOSSARY, read).terms,
            taskTemplates = templates.filter(TaskGenerator::varies).associateBy { it.id.orEmpty() },
        )
    }

    /**
     * Задание в варианте без случайности. Шаблон с разбросом нельзя разобрать сразу в
     * [TaskDefinition]: сначала числа выбирает [TaskGenerator].
     */
    private fun baseTask(template: JsonObject, index: Int): TaskDefinition =
        try {
            TaskGenerator.base(template)
        } catch (e: Exception) {
            val id = template.id ?: "№${index + 1}"
            throw ContentException(listOf("$TASKS: задание $id: ${e.message}"), e)
        }

    private val JsonObject.id: String? get() = (this["id"] as? JsonPrimitive)?.content

    private inline fun <reified T> decode(file: String, read: (String) -> String): T =
        try {
            json.decodeFromString<T>(read(file))
        } catch (e: Exception) {
            throw ContentException(listOf("$file: ${e.message}"), e)
        }
}

class ContentException(val errors: List<String>, cause: Throwable? = null) :
    IllegalStateException("Ошибки в контенте:\n" + errors.joinToString("\n"), cause)
