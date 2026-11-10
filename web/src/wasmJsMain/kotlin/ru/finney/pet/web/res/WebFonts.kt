package ru.finney.pet.web.res

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * Шрифты веб-сборки. На вебе Compose рисует на canvas и системных шрифтов не видит:
 * текст без своего шрифта (всё, кроме заголовков Glina) остался бы пустыми квадратами.
 * Поэтому Noto Sans с кириллицей подгружается как запасной для всех стилей.
 */
@OptIn(ExperimentalResourceApi::class)
object WebFonts {
    private val loaded = mutableMapOf<Int, ByteArray>()

    /** Запасной шрифт текста: передаётся в FontFamily.Resolver.preload в main.kt. */
    lateinit var fallback: FontFamily
        private set

    fun bytes(resId: Int): ByteArray = checkNotNull(loaded[resId]) { "Шрифт $resId не загружен: WebFonts.load() до первого кадра" }

    suspend fun load() {
        val env = getSystemResourceEnvironment()
        fontTable.forEachIndexed { i, res -> loaded[i] = getFontResourceBytes(env, res) }
        suspend fun font(res: FontResource, weight: FontWeight) =
            Font("noto-${weight.weight}", getFontResourceBytes(env, res), weight)
        fallback = FontFamily(
            font(Res.font.notosans_regular, FontWeight.Normal),
            font(Res.font.notosans_medium, FontWeight.Medium),
            font(Res.font.notosans_bold, FontWeight.Bold),
        )
    }
}
