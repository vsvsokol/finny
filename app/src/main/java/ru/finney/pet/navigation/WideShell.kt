package ru.finney.pet.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import ru.finney.pet.ui.layout.LocalSceneColumn
import ru.finney.pet.ui.layout.PaneWidth
import ru.finney.pet.ui.layout.PortraitColumnWidth
import ru.finney.pet.ui.theme.FinneyCream

/**
 * Экраны, которые в широком режиме открываются панелью справа, а не поверх комнаты:
 * ребёнок раскладывает деньги или покупает и тут же видит питомца.
 * Знакомство и создание питомца занимают всю ширину. У мини-игр фон доходит
 * до краёв окна, а сама игра остаётся в колонке (SceneStage).
 */
private val PaneRoutes = listOf(
    BudgetRoute::class, ShopRoute::class, WardrobeRoute::class, GoalsRoute::class,
    TasksRoute::class, ProgressRoute::class, GlossaryRoute::class, SettingsRoute::class, AdultRoute::class,
)

internal fun NavDestination.isPane(): Boolean = PaneRoutes.any { hasRoute(it) }

/**
 * Каркас широкого режима (компьютер): комната всегда на сцене, экран — панелью справа.
 *
 * NavHost остаётся одним и тем же в дереве, меняется только его место: на главном он
 * пустой (комнату рисует [home] здесь, вне графа), с экраном-панелью — полоса справа,
 * с полноэкранным экраном — колонка по центру поверх всего.
 *
 * Выключен ([enabled] = false, телефон и Android) — граф как был, во весь экран.
 */
@Composable
internal fun WideShell(
    navController: NavController,
    enabled: Boolean,
    home: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navHost: @Composable (Modifier) -> Unit,
) {
    if (!enabled) {
        navHost(modifier)
        return
    }
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val atHome = destination?.hasRoute<HomeRoute>() == true
    val inPane = destination?.isPane() == true
    val paneShift by animateDpAsState(if (inPane) PaneWidth else 0.dp, tween(280), label = "pane")

    Box(modifier.fillMaxSize().background(WideBackdrop)) {
        // Комната — пока открыт главный или панель рядом с ним.
        if (atHome || inPane) {
            Box(Modifier.fillMaxSize().padding(end = paneShift)) { home() }
        }
        // На главном граф пуст, но экран, который уезжает, ещё рисуется: его не сжимаем
        // в ноль (вёрстка экранов на это не рассчитана), а оставляем на прежнем месте
        // невидимым. Пустой граф нажатий не перехватывает — они доходят до комнаты.
        var lastPlace by remember { mutableStateOf(Place.FULL) }
        val fullPlace = when {
            destination?.hasRoute<TaskRoute>() == true -> Place.WIDE
            destination?.hasRoute<OnboardingRoute>() == true -> Place.WIDE
            destination?.hasRoute<PetSetupRoute>() == true && entry?.toRoute<PetSetupRoute>()?.isEditing == false -> Place.WIDE
            else -> Place.FULL
        }
        if (inPane) lastPlace = Place.PANE else if (!atHome && destination != null) lastPlace = fullPlace
        val place = if (atHome) lastPlace else if (inPane) Place.PANE else fullPlace
        val navModifier = when (place) {
            Place.PANE -> Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(PaneWidth)
                .shadow(16.dp, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                .background(FinneyCream)
            Place.FULL -> Modifier.align(Alignment.Center).fillMaxHeight().widthIn(max = PortraitColumnWidth)
            Place.WIDE -> Modifier.fillMaxSize()
        }
        // Прозрачность — первой в цепочке: иначе фон и тень панели остались бы видны.
        CompositionLocalProvider(LocalSceneColumn provides PortraitColumnWidth) {
            navHost(if (atHome) Modifier.graphicsLayer { alpha = 0f }.then(navModifier) else navModifier)
        }
    }
}

private enum class Place { PANE, FULL, WIDE }

/** Фон окна вокруг сцены — сиреневый, как у полей вокруг портретной игры. */
internal val WideBackdrop = androidx.compose.ui.graphics.Color(0xFFE9E1FF)
