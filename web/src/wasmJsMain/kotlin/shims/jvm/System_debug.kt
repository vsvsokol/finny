// JVM-вызов из общих исходников app/: на wasm `java.lang.System` нет, а объект
// в том же пакете перекрывает его без правок в коде.
package ru.finney.pet.ui.debug

internal object System {
    fun currentTimeMillis(): Long = ru.finney.pet.web.nowMillis()
}
