package ru.finney.pet.domain.model

/**
 * Темы комнаты. Рисунки и раскладка частей — в `ui/room/RoomInteriors.kt`, здесь только id:
 * ядро решает, что открыто, и хранит выбор. Базовая тема открыта всегда, остальные — наградой
 * за цель (goals.json, `"rewardKind": "room"`).
 */
object RoomThemes {
    const val BASE = "default"

    val ALL = listOf(BASE, "circus", "honey", "sea", "forest", "town")
}

/**
 * Что в комнате меняется по отдельности — как вкладки выбора комнаты. Ковёр лежит в игровой,
 * остальное — в спальне, но тема у них общая: получил тему — получил и её ковёр.
 */
enum class RoomPart { WALL, WINDOW, LAMP, RUG }

/**
 * Выбранная комната: из какой темы каждая часть. Все части из одной темы — это сама тема,
 * из разных — своя комната ребёнка.
 */
data class RoomChoice(
    val wall: String = RoomThemes.BASE,
    val window: String = RoomThemes.BASE,
    val lamp: String = RoomThemes.BASE,
    val rug: String = RoomThemes.BASE,
) {
    fun themeOf(part: RoomPart): String = when (part) {
        RoomPart.WALL -> wall
        RoomPart.WINDOW -> window
        RoomPart.LAMP -> lamp
        RoomPart.RUG -> rug
    }

    fun with(part: RoomPart, themeId: String): RoomChoice = when (part) {
        RoomPart.WALL -> copy(wall = themeId)
        RoomPart.WINDOW -> copy(window = themeId)
        RoomPart.LAMP -> copy(lamp = themeId)
        RoomPart.RUG -> copy(rug = themeId)
    }

    companion object {
        fun whole(themeId: String) = RoomChoice(themeId, themeId, themeId, themeId)
    }
}

/**
 * Платные облики — раскраски персонажа, по [PER_CHARACTER] на каждого. Девять бесплатных
 * сочетаний цвета и глаз ([BodyColor] × [EyesVariant]) выбираются в профиле и открыты всегда;
 * облик отсюда надевается поверх цвета профиля и открывается наградой за цель
 * (goals.json, `"rewardKind": "look"`). id — `look_<персонаж>_<номер>`, например `look_pushistik_1`.
 */
object PetLooks {
    const val PER_CHARACTER = 2

    val ALL: Map<String, PetCharacter> = PetCharacter.entries
        .flatMap { character -> (1..PER_CHARACTER).map { n -> id(character, n) to character } }
        .toMap()

    fun id(character: PetCharacter, number: Int) = "look_${character.name.lowercase()}_$number"

    /** Чей это облик; null — такого облика нет. */
    fun character(lookId: String): PetCharacter? = ALL[lookId]

    fun of(character: PetCharacter): List<String> = (1..PER_CHARACTER).map { id(character, it) }
}
