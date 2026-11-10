package androidx.compose.ui.text.font

import ru.finney.pet.web.res.WebFonts

/**
 * `Font(R.font.glina_script)` из ui/theme/Type.kt. Вызывается вне композиции, поэтому
 * байты шрифта загружаются заранее, до первого кадра (см. WebFonts.load в main.kt).
 */
fun Font(resId: Int, weight: FontWeight = FontWeight.Normal, style: FontStyle = FontStyle.Normal): Font =
    androidx.compose.ui.text.platform.Font("finny-font-$resId", WebFonts.bytes(resId), weight, style)
