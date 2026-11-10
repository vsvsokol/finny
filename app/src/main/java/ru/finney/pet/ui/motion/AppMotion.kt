package ru.finney.pet.ui.motion

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.MotionDurationScale

/**
 * Скорость всех анимаций окна (ТЗ п. 3.6: анимации отключаются). Compose берёт её из
 * контекста корутин рекомпозера, поэтому `animateTo`, `animate*AsState`, переходы и
 * бесконечные анимации выключаются разом: при 0 они сразу встают в конечное значение.
 * Системная «скорость анимации» учитывается, как и без нас.
 *
 * Одиночному переходу этого хватает. Но при нуле каждый `animateTo` длится ровно кадр,
 * и цепочка шагов (присел — подпрыгнул — приземлился) мелькает промежуточными положениями.
 * Такие цепочки проверяют [motionEnabled] и не начинаются вовсе. А бесконечная анимация
 * встаёт в крайнее положение, не в спокойное — её экран гасит сам по [LocalAnimations].
 *
 * Покадровые циклы на `withFrameNanos` — это игра (полёт еды, пена), а не украшение,
 * их множитель не трогает. Украшение на таком цикле проверяет [LocalAnimations].
 */
class AppMotion(private val context: Context) : MotionDurationScale {

    @Volatile
    var enabled: Boolean = true

    @Volatile
    private var systemScale: Float = readSystemScale()

    override val scaleFactor: Float get() = if (enabled) systemScale else 0f

    /** Перечитать системную скорость анимации: зовёт MainActivity при возврате на экран. */
    fun refreshSystemScale() {
        systemScale = readSystemScale()
    }

    private fun readSystemScale(): Float =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
}
