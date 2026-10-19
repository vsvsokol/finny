package ru.finney.pet.ui.pet

import androidx.annotation.DrawableRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import ru.finney.pet.R
import kotlin.math.roundToInt

/**
 * Рисунок аксессуара и линия, которой он прикладывается к питомцу, — доля высоты рисунка:
 * у шляпы это поля, у очков [onEyes] — середина стёкол. [widthScale] — во сколько раз очки
 * шире самых простых: художник рисует их в одном масштабе, и оправа-звезда шире обычной.
 */
data class AccessoryArt(
    @DrawableRes val res: Int,
    val brim: Float,
    val onEyes: Boolean = false,
    val widthScale: Float = 1f,
)

/**
 * Рисунок аксессуара по id из магазина. Файлы кладёт tools/pack_items.py
 * (`design/exports/items/acc_<id>.png` → `drawable-nodpi/acc_<id>.webp`).
 * Линия полей — самая широкая строка рисунка, замерена по файлам в ресурсах.
 * null — аксессуар без рисунка: на питомце его не видно.
 */
fun accessoryArt(itemId: String): AccessoryArt? = when (itemId) {
    "hat_cowboy" -> AccessoryArt(R.drawable.acc_hat_cowboy, brim = 0.53f)
    "hat_pirate" -> AccessoryArt(R.drawable.acc_hat_pirate, brim = 0.62f)
    "hat_wizard" -> AccessoryArt(R.drawable.acc_hat_wizard, brim = 0.71f)
    // Середина стёкол и ширина оправы замерены по design/exports/items/acc_glasses_*.png.
    "glasses_black" -> AccessoryArt(R.drawable.acc_glasses_black, brim = 0.46f, onEyes = true)
    "glasses_star" -> AccessoryArt(R.drawable.acc_glasses_star, brim = 0.44f, onEyes = true, widthScale = 1.10f)
    "glasses_pineapple" -> AccessoryArt(R.drawable.acc_glasses_pineapple, brim = 0.68f, onEyes = true, widthScale = 1.12f)
    // 19.10: поля — самая широкая строка минус 0.03, как у первых трёх шляп; у 3D-очков —
    // середина стёкол под тонкой перекладиной, у маски — середина линзы.
    "hat_top" -> AccessoryArt(R.drawable.acc_hat_top, brim = 0.73f)
    "hat_flowers" -> AccessoryArt(R.drawable.acc_hat_flowers, brim = 0.63f)
    "hat_sun" -> AccessoryArt(R.drawable.acc_hat_sun, brim = 0.43f)
    "glasses_3d" -> AccessoryArt(R.drawable.acc_glasses_3d, brim = 0.55f, onEyes = true)
    "goggles_ski" -> AccessoryArt(R.drawable.acc_goggles_ski, brim = 0.50f, onEyes = true, widthScale = 1.05f)
    else -> null
}

/** Очки уже шляпы: шляпа накрывает всю голову, очки — только глаза. Доля ширины шляпы. */
private const val GLASSES_WIDTH = 0.62f

/**
 * Поставить очки: середина стёкол [art] ложится на середину глаз питомца
 * ([PetSkin.eyesTop]…[PetSkin.eyesBottom]), по ширине — доля посадки шляпы.
 * Как и шляпа, слой занимает весь холст и соседей не сдвигает.
 */
internal fun Modifier.glassesPlacement(skin: PetSkin, art: AccessoryArt): Modifier = layout { measurable, constraints ->
    val side = constraints.maxWidth
    val width = (side * skin.hat.width * HAT_SCALE * GLASSES_WIDTH * art.widthScale).roundToInt()
    val placeable = measurable.measure(Constraints.fixedWidth(width))
    val eyes = (skin.eyesTop + skin.eyesBottom) / 2f
    layout(side, constraints.maxHeight) {
        placeable.place(
            x = (side * skin.hat.centerX - width / 2f).roundToInt(),
            y = (constraints.maxHeight * eyes - placeable.height * art.brim).roundToInt(),
        )
    }
}

/** Шляпы крупнее замера по голове: по [HatFit.width] они выглядели маленькими и не налезали. */
private const val HAT_SCALE = 1.2f

/** Насколько выше замера по голове лежат поля — доля высоты холста. */
private const val HAT_LIFT = 0.05f

/**
 * Поставить шляпу по [HatFit]: ширина — доля стороны питомца, линия полей [artBrim]
 * (доля высоты рисунка) ложится на [HatFit.brim]. Слой занимает место всего холста,
 * поэтому соседние слои питомца не сдвигаются. За верх холста шляпа может выходить.
 */
internal fun Modifier.hatPlacement(fit: HatFit, artBrim: Float): Modifier = layout { measurable, constraints ->
    val side = constraints.maxWidth
    val width = (side * fit.width * HAT_SCALE).roundToInt()
    val placeable = measurable.measure(Constraints.fixedWidth(width))
    layout(side, constraints.maxHeight) {
        placeable.place(
            x = (side * fit.centerX - width / 2f).roundToInt(),
            y = (constraints.maxHeight * (fit.brim - HAT_LIFT) - placeable.height * artBrim).roundToInt(),
        )
    }
}
