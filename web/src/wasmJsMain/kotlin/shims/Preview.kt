// Превью Android Studio. На вебе их никто не показывает.

@file:Suppress("unused")

package androidx.compose.ui.tooling.preview

@Repeatable
annotation class Preview(
    val name: String = "",
    val widthDp: Int = -1,
    val heightDp: Int = -1,
    val showBackground: Boolean = false,
    val backgroundColor: Long = 0,
)

annotation class PreviewParameter(val provider: kotlin.reflect.KClass<out PreviewParameterProvider<*>>, val limit: Int = Int.MAX_VALUE)

interface PreviewParameterProvider<T> {
    val values: Sequence<T>
    val count: Int get() = values.count()
}
