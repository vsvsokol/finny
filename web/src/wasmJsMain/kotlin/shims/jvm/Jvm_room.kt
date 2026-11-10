package ru.finney.pet.ui.room

// @JvmName из RoomInteriorSwap.kt: на wasm перегрузки по стёртому типу не конфликтуют, аннотация — пустышка.
internal annotation class JvmName(val name: String)

/** "%.1f".format(x) из BathShop.kt: на wasm String.format нет. Поддержан только этот шаблон. */
internal fun String.format(vararg args: Any?): String {
    require(this == "%.1f" && args.size == 1) { "Веб-shim String.format: шаблон $this не поддержан" }
    val value = (args[0] as Number).toDouble()
    val tenths = kotlin.math.round(value * 10).toLong()
    val sign = if (tenths < 0) "-" else ""
    val abs = kotlin.math.abs(tenths)
    return "$sign${abs / 10}.${abs % 10}"
}
