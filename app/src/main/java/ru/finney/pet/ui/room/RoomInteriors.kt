package ru.finney.pet.ui.room

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import ru.finney.pet.R
import ru.finney.pet.domain.model.RoomChoice
import ru.finney.pet.domain.model.RoomPart
import ru.finney.pet.ui.sound.Sfx

// Интерьер комнаты: задник, окно и торшер. Меняются по отдельности, поэтому у
// каждой части своё место на холсте и свои точки света — всё, что раньше было
// одной константой на всю комнату. Мебель, капсула и питомец от интерьера не
// зависят: они стоят там же, где стояли.
//
// Числа слоёв печатает tools/pack_room.py, как и в Room. Точки света и шов пола
// сняты с экспортов в design/exports/environment/interiors (см. комментарии у тем).

/**
 * Задник: стена и пол.
 *
 * @param floorTop шов стены и пола, доля высоты холста. От него ложатся пятно
 *   света из окна и тени от того, что за окном. У задников с плинтусом — низ
 *   плинтуса: пол начинается там.
 */
@Immutable
data class WallSkin(val back: RoomLayer, val floorTop: Float)

/**
 * Торшер.
 *
 * @param shade середина абажура, доля холста: отсюда светит лампа.
 * @param pool середина круга света на полу. Не под подставкой, а под абажуром:
 *   у цветка абажур свисает вбок, и свет падает туда.
 */
@Immutable
data class LampSkin(val layer: RoomLayer, val shade: Offset, val pool: Offset)

/**
 * Ковёр игровой. Нарисован отдельно от холста, уже в перспективе пола, поэтому места
 * на холсте у него нет: его кладёт [rugRect] по ширине экрана, как ящик для игрушек.
 *
 * @param aspect ширина к высоте рисунка — печатает tools/pack_room.py.
 */
@Immutable
data class RugSkin(@DrawableRes val image: Int, val aspect: Float)

/** Что видно в окне. */
@Immutable
sealed interface WindowView {
    /** Бесшовная полоса неба, медленно плывёт влево (см. pack_room.py, SKY_COLUMNS). */
    data class Sky(val strip: RoomLayer) : WindowView

    /** Неподвижная картинка по форме проёма. */
    data class Picture(val layer: RoomLayer) : WindowView
}

/** Что живёт за окном. Рисуется только поверх вида — за раму и на обои не вылезает. */
@Immutable
sealed interface WindowLife {
    /**
     * Пролетает через окно, как НЛО.
     *
     * @param facesRight куда смотрит на рисунке; null — симметричен, не разворачивается.
     * @param glows подсвечивает комнату, пролетая (только НЛО — у него светится купол).
     * @param sound звук пролёта; null — летит молча.
     */
    data class Flyer(
        val layer: RoomLayer,
        val facesRight: Boolean?,
        val glows: Boolean = false,
        val sound: Sfx? = null,
    ) : WindowLife

    /** Колесо обозрения: колесо крутится вокруг [hub], кабинки на нём висят ровно. */
    data class Wheel(val wheel: RoomLayer, val stand: RoomLayer, val cabins: List<RoomLayer>, val hub: Offset) : WindowLife

    /** Мерцает на месте — светлячки. */
    data class Twinkle(val layer: RoomLayer) : WindowLife
}

/**
 * Окно.
 *
 * @param hole где виден [view]; живое за окном не выходит за него.
 * @param glassLeft левый край стекла между занавесками — по нему ложится пятно на полу.
 * @param mullion вертикальная перекладина, делит пятно надвое; null — перекладины нет.
 * @param glow откуда свет из окна растекается по стене.
 * @param moonFromX откуда у стены считаются тени от луны.
 */
@Immutable
data class WindowSkin(
    val frame: RoomLayer,
    val view: WindowView,
    val hole: RelRect,
    val glassLeft: Float,
    val glassRight: Float,
    val mullion: ClosedFloatingPointRange<Float>?,
    val glow: Offset,
    val moonFromX: Float,
    val life: WindowLife? = null,
) {
    companion object {
        /**
         * Окно с картинкой: проём — сама картинка. Свет растекается от середины стекла
         * чуть ниже середины проёма, тени от луны — от перекладины или середины стекла,
         * как у окна по умолчанию.
         */
        fun picture(
            frame: RoomLayer,
            view: RoomLayer,
            glass: ClosedFloatingPointRange<Float>,
            mullion: ClosedFloatingPointRange<Float>? = null,
            life: WindowLife? = null,
        ): WindowSkin {
            val middle = (glass.start + glass.endInclusive) / 2f
            return WindowSkin(
                frame = frame,
                view = WindowView.Picture(view),
                hole = view.rect,
                glassLeft = glass.start,
                glassRight = glass.endInclusive,
                mullion = mullion,
                glow = Offset(middle, view.rect.top + view.rect.height * 0.65f),
                moonFromX = mullion?.let { (it.start + it.endInclusive) / 2f } ?: middle,
                life = life,
            )
        }
    }
}

/** Вся обстановка вокруг питомца, которую можно сменить. [label] — для отладки. */
@Immutable
data class RoomInterior(
    val id: String,
    val label: String,
    val wall: WallSkin,
    val window: WindowSkin,
    val lamp: LampSkin,
    val rug: RugSkin,
)

/**
 * Готовые интерьеры.
 *
 * Шов пола промерен по строкам задника: низ плинтуса, с которого идут доски
 * или плитка. Стекло — по прозрачным столбцам рамы на трети и двух третях
 * высоты проёма, перекладина — по непрозрачным. Середина абажура и круг света
 * под ним — по силуэту лампы; круг на 63 px выше низа подставки, как у торшера
 * по умолчанию. Всё в пикселях холста 1440×2400, здесь — доли.
 */
object Interiors {

    /** Комната, с которой всё начиналось: ночное небо, НЛО, торшер-«Сатурн». */
    val Default = RoomInterior(
        id = "default",
        // В выборе ребёнок видит темы по имени: эта — про ночное небо и планету.
        label = "Космос",
        wall = WallSkin(Room.Back, floorTop = Room.FLOOR_TOP),
        window = WindowSkin(
            frame = Room.WindowFrame,
            view = WindowView.Sky(Room.WindowSky),
            hole = Room.WindowHole,
            glassLeft = Room.WindowHole.left,
            glassRight = Room.WindowHole.right,
            mullion = 0.6375f..0.6861f,
            glow = Offset(0.654f, 0.26f),
            moonFromX = 0.648f,
            life = WindowLife.Flyer(Room.WindowUfo, facesRight = null, glows = true, sound = Sfx.Ufo),
        ),
        lamp = LampSkin(Room.Lamp, shade = Offset(0.152f, 0.092f), pool = Offset(0.160f, 0.500f)),
        rug = RugSkin(R.drawable.room_rug_default, aspect = 2.3148f),
    )

    /** Цирк: круглое окно с перекладинами, за ним шатёр и колесо обозрения; торшер — воздушный шар. */
    val Circus = run {
        val back = RoomLayer(R.drawable.room_circus_back, RelRect(0.0000f, 0.0000f, 1.0000f, 1.0000f))
        val frame = RoomLayer(R.drawable.room_circus_window_frame, RelRect(0.4688f, 0.0375f, 0.9847f, 0.3296f))
        val view = RoomLayer(R.drawable.room_circus_window_view, RelRect(0.5340f, 0.0667f, 0.9368f, 0.3121f))
        val lamp = RoomLayer(R.drawable.room_circus_lamp, RelRect(0.0389f, 0.0754f, 0.2986f, 0.4917f))
        val wheel = RoomLayer(R.drawable.room_circus_wheel, RelRect(0.7389f, 0.1625f, 0.9083f, 0.2642f))
        val stand = RoomLayer(R.drawable.room_circus_wheel_stand, RelRect(0.7868f, 0.2004f, 0.8597f, 0.2967f))
        val cabins = listOf(
            RoomLayer(R.drawable.room_circus_cabin_1, RelRect(0.7667f, 0.1633f, 0.7944f, 0.1808f)),
            RoomLayer(R.drawable.room_circus_cabin_2, RelRect(0.8500f, 0.1642f, 0.8778f, 0.1817f)),
            RoomLayer(R.drawable.room_circus_cabin_3, RelRect(0.7500f, 0.2358f, 0.7778f, 0.2533f)),
            RoomLayer(R.drawable.room_circus_cabin_4, RelRect(0.8667f, 0.2367f, 0.8944f, 0.2542f)),
        )
        RoomInterior(
            id = "circus",
            label = "Цирк",
            wall = WallSkin(back, floorTop = 0.4650f),
            window = WindowSkin.picture(
                frame, view,
                glass = 0.5903f..0.8646f,
                mullion = 0.7153f..0.7389f,
                // Ось колеса — середина его круга (1187, 513 px).
                life = WindowLife.Wheel(wheel, stand, cabins, hub = Offset(0.8243f, 0.2138f)),
            ),
            lamp = LampSkin(lamp, shade = Offset(0.1681f, 0.1625f), pool = Offset(0.1694f, 0.4654f)),
            rug = RugSkin(R.drawable.room_rug_circus, aspect = 2.2779f),
        )
    }

    /** Соты: шестиугольное окно на луг, пчела; торшер — жёлтый колокол. */
    val Honey = run {
        val back = RoomLayer(R.drawable.room_honey_back, RelRect(0.0000f, 0.0000f, 1.0000f, 1.0000f))
        val frame = RoomLayer(R.drawable.room_honey_window_frame, RelRect(0.3861f, 0.0362f, 0.9632f, 0.3329f))
        val view = RoomLayer(R.drawable.room_honey_window_view, RelRect(0.4243f, 0.0508f, 0.9326f, 0.3217f))
        val lamp = RoomLayer(R.drawable.room_honey_lamp, RelRect(0.0076f, 0.0679f, 0.2743f, 0.5158f))
        val bee = RoomLayer(R.drawable.room_honey_bee, RelRect(0.6722f, 0.1500f, 0.7368f, 0.1879f))
        RoomInterior(
            id = "honey",
            label = "Соты",
            wall = WallSkin(back, floorTop = 0.4850f),
            window = WindowSkin.picture(frame, view, glass = 0.5278f..0.8264f, life = WindowLife.Flyer(bee, facesRight = false)),
            lamp = LampSkin(lamp, shade = Offset(0.1403f, 0.1575f), pool = Offset(0.1458f, 0.4896f)),
            rug = RugSkin(R.drawable.room_rug_honey, aspect = 2.2779f),
        )
    }

    /** Море: иллюминатор, за ним рыбка; торшер — ракушка. */
    val Sea = run {
        val back = RoomLayer(R.drawable.room_sea_back, RelRect(0.0000f, 0.0000f, 1.0000f, 1.0000f))
        val frame = RoomLayer(R.drawable.room_sea_window_frame, RelRect(0.3757f, 0.0550f, 0.9861f, 0.3525f))
        val view = RoomLayer(R.drawable.room_sea_window_view, RelRect(0.5104f, 0.1013f, 0.8938f, 0.3100f))
        val lamp = RoomLayer(R.drawable.room_sea_lamp, RelRect(0.0111f, 0.1117f, 0.2660f, 0.5158f))
        val fish = RoomLayer(R.drawable.room_sea_fish, RelRect(0.6424f, 0.1933f, 0.7188f, 0.2208f))
        RoomInterior(
            id = "sea",
            label = "Море",
            wall = WallSkin(back, floorTop = 0.4758f),
            window = WindowSkin.picture(frame, view, glass = 0.5486f..0.8208f, life = WindowLife.Flyer(fish, facesRight = true)),
            lamp = LampSkin(lamp, shade = Offset(0.1382f, 0.1763f), pool = Offset(0.1368f, 0.4896f)),
            rug = RugSkin(R.drawable.room_rug_sea, aspect = 2.0325f),
        )
    }

    /** Лес: окно в четыре стекла на вечерний лес со светлячками; торшер — цветок. */
    val Forest = run {
        val back = RoomLayer(R.drawable.room_forest_back, RelRect(0.0000f, 0.0000f, 1.0000f, 1.0000f))
        val frame = RoomLayer(R.drawable.room_forest_window_frame, RelRect(0.3792f, 0.0838f, 0.9951f, 0.4050f))
        val view = RoomLayer(R.drawable.room_forest_window_view, RelRect(0.5056f, 0.1292f, 0.8639f, 0.3367f))
        val lamp = RoomLayer(R.drawable.room_forest_lamp, RelRect(0.0361f, 0.1338f, 0.2694f, 0.5337f))
        val fireflies = RoomLayer(R.drawable.room_forest_fireflies, RelRect(0.5597f, 0.1858f, 0.8514f, 0.3287f))
        RoomInterior(
            id = "forest",
            label = "Лес",
            wall = WallSkin(back, floorTop = 0.4996f),
            window = WindowSkin.picture(
                frame, view,
                glass = 0.5417f..0.8299f,
                mullion = 0.6646f..0.7035f,
                life = WindowLife.Twinkle(fireflies),
            ),
            lamp = LampSkin(lamp, shade = Offset(0.1563f, 0.2333f), pool = Offset(0.1563f, 0.5075f)),
            rug = RugSkin(R.drawable.room_rug_forest, aspect = 2.4570f),
        )
    }

    /** Городок: окно со шторами на домики; торшер с шаром. За окном ничего не движется. */
    val Town = run {
        val back = RoomLayer(R.drawable.room_town_back, RelRect(0.0000f, 0.0000f, 1.0000f, 1.0000f))
        val frame = RoomLayer(R.drawable.room_town_window_frame, RelRect(0.4597f, 0.0375f, 0.9944f, 0.3162f))
        val view = RoomLayer(R.drawable.room_town_window_view, RelRect(0.5750f, 0.0858f, 0.8778f, 0.2808f))
        val lamp = RoomLayer(R.drawable.room_town_lamp, RelRect(0.0514f, 0.0954f, 0.2875f, 0.5254f))
        RoomInterior(
            id = "town",
            label = "Городок",
            wall = WallSkin(back, floorTop = 0.5058f),
            window = WindowSkin.picture(frame, view, glass = 0.5972f..0.8542f),
            lamp = LampSkin(lamp, shade = Offset(0.1688f, 0.1583f), pool = Offset(0.1674f, 0.4992f)),
            rug = RugSkin(R.drawable.room_rug_town, aspect = 2.4390f),
        )
    }

    val all = listOf(Default, Circus, Honey, Sea, Forest, Town)
}

/**
 * Что в комнате меняется по отдельности: вкладки выбора и куда смотрит камера.
 * Стена, окно и торшер — в спальне, ковёр — в игровой.
 */
enum class InteriorPart(val label: String) { WALL("Стена"), WINDOW("Окно"), LAMP("Торшер"), RUG("Ковёр") }

/** Тема, из которой взята часть [part]. У смешанной комнаты у каждой части своя. */
fun RoomInterior.themeOf(part: InteriorPart): RoomInterior = Interiors.all.firstOrNull { theme ->
    when (part) {
        InteriorPart.WALL -> theme.wall == wall
        InteriorPart.WINDOW -> theme.window == window
        InteriorPart.LAMP -> theme.lamp == lamp
        InteriorPart.RUG -> theme.rug == rug
    }
} ?: Interiors.Default

/**
 * Эта комната, где часть [part] взята из темы [from]. Все части из одной
 * темы — это сама тема; из разных — своя комната ребёнка.
 */
fun RoomInterior.withPart(part: InteriorPart, from: RoomInterior): RoomInterior {
    val mixed = when (part) {
        InteriorPart.WALL -> copy(wall = from.wall)
        InteriorPart.WINDOW -> copy(window = from.window)
        InteriorPart.LAMP -> copy(lamp = from.lamp)
        InteriorPart.RUG -> copy(rug = from.rug)
    }
    return Interiors.all.firstOrNull {
        it.wall == mixed.wall && it.window == mixed.window && it.lamp == mixed.lamp && it.rug == mixed.rug
    }
        ?: mixed.copy(id = "mix", label = "Своя комната")
}

/**
 * Что показать крупно, пока выбирают часть [part], — доли холста спальни. Стена — вся,
 * от потолка до шва с полом и чуть ниже: обои и пол меняются вместе. Ковёр — null:
 * он в игровой, и где он лежит, знает только сцена (RoomScene, `focusRug`).
 */
fun RoomInterior.focusOf(part: InteriorPart): RelRect? = when (part) {
    InteriorPart.WALL -> RelRect(0f, 0f, 1f, wall.floorTop + 0.15f)
    InteriorPart.WINDOW -> window.frame.rect
    InteriorPart.LAMP -> lamp.layer.rect
    InteriorPart.RUG -> null
}

/** Тема по id из ядра ([RoomThemes]); незнакомый id — базовая, комната не пропадает. */
fun Interiors.byId(id: String): RoomInterior = all.firstOrNull { it.id == id } ?: Default

/**
 * Комната из сохранения: каждая часть — из своей темы. Выбор хранит ядро
 * (`GameState.room`), здесь только рисунки к нему.
 */
fun RoomChoice.toInterior(): RoomInterior = Interiors.byId(wall)
    .withPart(InteriorPart.WINDOW, Interiors.byId(window))
    .withPart(InteriorPart.LAMP, Interiors.byId(lamp))
    .withPart(InteriorPart.RUG, Interiors.byId(rug))

/** Та же часть комнаты в ядре: вкладки выбора и [RoomPart] совпадают по смыслу. */
val InteriorPart.core: RoomPart get() = when (this) {
    InteriorPart.WALL -> RoomPart.WALL
    InteriorPart.WINDOW -> RoomPart.WINDOW
    InteriorPart.LAMP -> RoomPart.LAMP
    InteriorPart.RUG -> RoomPart.RUG
}
