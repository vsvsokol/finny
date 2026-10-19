package ru.finney.pet.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.finney.pet.R
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.theme.FinneyCream
import ru.finney.pet.ui.theme.FinneyInk
import ru.finney.pet.ui.theme.StrokeThin

/** Круг центрируется над всей суммой с монетой: одна ось у магазина и копилки. */
@Composable
fun HomeShopButton(balance: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val animations = LocalAnimations.current
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f,
        animationSpec = if (animations) spring(dampingRatio = 0.65f) else snap(), label = "shopPress")
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, role = Role.Button,
                onClickLabel = "Открыть магазин", onClick = onClick)
            .semantics { contentDescription = "Магазин"; stateDescription = "У тебя $balance финок" }
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((-8).dp),
    ) {
        Box(Modifier.size(56.dp).testTag("home-shop-circle"), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.home_round_button_base), null, Modifier.fillMaxSize())
            Image(painterResource(R.drawable.home_shop_icon), null, Modifier.size(32.dp))
        }
        HomeMoneyBadge(balance, Modifier.testTag("home-shop-money"))
    }
}

/** Одна плашка для доступных денег и накоплений; разные значения приходят из состояния экрана. */
@Composable
internal fun HomeMoneyBadge(amount: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(modifier.background(FinneyCream, shape).border(StrokeThin, FinneyInk, shape)
        .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(amount.toString(), color = FinneyInk,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 28.sp),
            maxLines = 1, softWrap = false, modifier = Modifier.weight(1f, fill = false))
        Image(painterResource(R.drawable.home_money_icon), null, Modifier.size(22.dp))
    }
}
