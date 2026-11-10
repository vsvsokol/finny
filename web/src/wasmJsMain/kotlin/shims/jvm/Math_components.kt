// JVM-вызов из общих исходников app/: на wasm `java.lang.Math` нет.
package ru.finney.pet.ui.components

internal object Math {
    const val PI: Double = kotlin.math.PI
    fun toRadians(degrees: Double): Double = degrees / 180.0 * PI
    fun toDegrees(radians: Double): Double = radians * 180.0 / PI
}
