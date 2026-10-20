package ru.finney.pet.ui.room

// Ковёр игровой: где он лежит. Рисунок у каждой темы свой (RoomInteriors.kt, RugSkin),
// а место одно — по концепту игровой (20.10): почти во всю ширину экрана, питомец стоит
// на нём, ящик для игрушек — на заднем краю справа. Холст шире экрана, поэтому место
// считается от видимой части пола, как у ящика (ToyBox.kt), а не долями холста.

/** Середина ковра — доля видимой ширины экрана слева. На концепте чуть левее середины. */
private const val RUG_SCREEN_X = 0.48f

/** Ширина ковра — доля видимой ширины экрана: края почти у бортов. */
private const val RUG_SCREEN_W = 0.96f

/**
 * Середина ковра по высоте — доля высоты холста. Чуть дальше ступней питомца
 * ([Room.PLAY_PET_FEET]): он стоит на ковре ближе к переднему краю, а задний край
 * уходит под ящик.
 */
private const val RUG_CENTRE_Y = 0.705f

/**
 * Ковёр [skin] на полу игровой — доли холста до отражения: игровая — зеркало спальни,
 * и видимое на экране слева лежит в холсте справа, как у ящика.
 */
internal fun rugRect(floor: ToyFloor, skin: RugSkin): RelRect {
    val span = floor.right - floor.left
    val centre = floor.right - span * RUG_SCREEN_X
    val width = span * RUG_SCREEN_W
    val height = width * Room.CANVAS_RATIO / skin.aspect
    return RelRect(centre - width / 2f, RUG_CENTRE_Y - height / 2f, centre + width / 2f, RUG_CENTRE_Y + height / 2f)
}
