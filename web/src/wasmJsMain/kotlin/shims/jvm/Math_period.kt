// JVM-вызов из общих исходников app/: на wasm `java.lang.Math` нет.
package ru.finney.pet.ui.period

internal object Math {
    const val PI: Double = kotlin.math.PI
}
