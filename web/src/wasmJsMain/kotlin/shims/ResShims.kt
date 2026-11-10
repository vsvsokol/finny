// Android-вызовы ресурсов по Int-id (`painterResource(R.drawable.x)`), переведённые
// на ресурсы Compose Multiplatform. Id — индексы из сгенерированного ru.finney.pet.R
// (web/build.gradle.kts, generateWebR), таблицы — ru.finney.pet.web.res.ResTables.

package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import ru.finney.pet.web.res.drawableTable

@Composable
fun painterResource(id: Int): Painter = org.jetbrains.compose.resources.painterResource(drawableTable[id])

@Composable
fun ImageBitmap.Companion.imageResource(id: Int): ImageBitmap =
    org.jetbrains.compose.resources.imageResource(drawableTable[id])
