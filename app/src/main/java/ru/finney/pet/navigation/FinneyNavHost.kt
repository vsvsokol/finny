package ru.finney.pet.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ru.finney.pet.ui.adult.AdultScreen
import ru.finney.pet.ui.adult.ResetProgressScreen
import ru.finney.pet.ui.budget.BudgetScreen
import ru.finney.pet.ui.goals.GoalsScreen
import ru.finney.pet.ui.home.HomeScreen
import ru.finney.pet.ui.onboarding.OnboardingScreen
import ru.finney.pet.notifications.AskNotificationsOnce
import ru.finney.pet.ui.onboarding.PetSetupScreen
import ru.finney.pet.ui.period.PeriodResultScreen
import ru.finney.pet.ui.progress.GlossaryScreen
import ru.finney.pet.ui.progress.ProgressScreen
import ru.finney.pet.ui.settings.SettingsScreen
import ru.finney.pet.ui.shop.ShopScreen
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.sound.Music
import ru.finney.pet.ui.wardrobe.WardrobeScreen
import ru.finney.pet.ui.tasks.TaskScreen
import ru.finney.pet.ui.tasks.TasksScreen

/**
 * Граф экранов. Экраны не знают про NavController: получают лямбды `onOpenX`,
 * а куда ведёт каждая — решается только здесь.
 */
@Composable
fun FinneyNavHost(
    modifier: Modifier = Modifier,
    startViewModel: StartViewModel = viewModel(factory = StartViewModel.Factory),
) {
    val startDestination by startViewModel.startDestination.collectAsStateWithLifecycle()
    val start = startDestination
    if (start == null) {
        // Проверка профиля — доли секунды, отдельный сплэш не нужен.
        Box(modifier.fillMaxSize())
        return
    }

    val navController = rememberNavController()
    BackgroundMusic(navController)
    NavHost(navController = navController, startDestination = start, modifier = modifier) {

        // ---------- Первый запуск ----------

        composable<OnboardingRoute> { entry ->
            val route = entry.toRoute<OnboardingRoute>()
            OnboardingScreen(
                isReplay = route.isReplay,
                onFinish = {
                    if (route.isReplay) navController.back() else navController.go(PetSetupRoute())
                },
                onStartDemo = { navController.go(PetSetupRoute(isDemo = true)) },
            )
        }

        composable<PetSetupRoute> { entry ->
            val route = entry.toRoute<PetSetupRoute>()
            PetSetupScreen(
                isEditing = route.isEditing,
                isDemo = route.isDemo,
                onSaved = {
                    if (route.isEditing) navController.back() else navController.openGame()
                },
            )
        }

        // ---------- Игра ----------

        composable<HomeRoute> {
            // Питомец уже есть — теперь понятно, о ком будут напоминания.
            AskNotificationsOnce()
            HomeScreen(
                onOpenBudget = { navController.go(BudgetRoute) },
                onOpenShop = { navController.go(ShopRoute) },
                onOpenGoals = { navController.go(GoalsRoute) },
                onOpenTasks = { navController.go(TasksRoute) },
                onOpenTask = { taskId -> navController.go(TaskRoute(taskId)) },
                onOpenWardrobe = { navController.go(WardrobeRoute) },
                onOpenProgress = { navController.go(ProgressRoute) },
                onOpenSettings = { navController.go(SettingsRoute) },
                onOpenHelp = { navController.go(OnboardingRoute(isReplay = true)) },
                onOpenGlossary = { navController.go(GlossaryRoute) },
                onPeriodClosed = { number -> navController.go(PeriodResultRoute(number)) },
            )
        }

        composable<BudgetRoute> {
            BudgetScreen(
                onBack = { navController.back() },
                onOpenShop = { navController.go(ShopRoute) },
                onOpenGoals = { navController.go(GoalsRoute) },
                onOpenTasks = { navController.go(TasksRoute) },
            )
        }

        composable<ShopRoute> {
            ShopScreen(
                onBack = { navController.back() },
                onOpenBudget = { navController.go(BudgetRoute) },
                onOpenHistory = { navController.go(ProgressRoute) },
            )
        }

        composable<WardrobeRoute> {
            WardrobeScreen(
                onBack = { navController.back() },
                onOpenGoals = { navController.go(GoalsRoute) },
            )
        }

        composable<GoalsRoute> {
            GoalsScreen(onBack = { navController.back() })
        }

        composable<TasksRoute> {
            TasksScreen(
                onOpenTask = { taskId -> navController.go(TaskRoute(taskId)) },
                onOpenBudget = { navController.go(BudgetRoute) },
                onBack = { navController.back() },
            )
        }

        // Задание и мини-игра — один экран: какую игру показать, решает движок задания.
        composable<TaskRoute> { entry ->
            val route = entry.toRoute<TaskRoute>()
            TaskScreen(
                taskId = route.taskId,
                onBack = { navController.back() },
                onOpenBudget = { navController.go(BudgetRoute) },
            )
        }

        composable<PeriodResultRoute> { entry ->
            val route = entry.toRoute<PeriodResultRoute>()
            PeriodResultScreen(
                periodNumber = route.periodNumber,
                onBack = { navController.back() },
                onOpenBudget = {
                    if (navController.ready) navController.navigate(BudgetRoute) {
                        popUpTo<HomeRoute>()
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<ProgressRoute> {
            ProgressScreen(
                onBack = { navController.back() },
                onOpenGlossary = { navController.go(GlossaryRoute) },
                onOpenPeriodResult = { number -> navController.go(PeriodResultRoute(number)) },
            )
        }

        composable<GlossaryRoute> {
            GlossaryScreen(onBack = { navController.back() })
        }

        // ---------- Взрослый ----------

        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.back() },
                onOpenAdult = { navController.go(AdultRoute) },
                onOpenGlossary = { navController.go(GlossaryRoute) },
            )
        }

        composable<AdultRoute> {
            AdultScreen(
                onBack = { navController.back() },
                onEditPet = { navController.go(PetSetupRoute(isEditing = true)) },
                onResetProgress = { navController.go(ResetProgressRoute) },
                onProfileDeleted = { hasProfile -> navController.restart(hasProfile) },
            )
        }

        composable<ResetProgressRoute> {
            ResetProgressScreen(
                onDone = { navController.openGame() },
                onCancel = { navController.back() },
            )
        }
    }
}

/** Музыка по текущему экрану: в играх — своя, в знакомстве — своя, иначе — тема комнаты. */
@Composable
private fun BackgroundMusic(navController: NavController) {
    val sounds = LocalSounds.current
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val track = when {
        destination == null -> null
        destination.hasRoute<TaskRoute>() -> Music.Games
        destination.hasRoute<OnboardingRoute>() || destination.hasRoute<PetSetupRoute>() -> Music.Setup
        else -> Music.Room
    }
    LaunchedEffect(track) { sounds.music(track) }
}

/**
 * Нажатие считается, только пока текущий экран на переднем плане. Двойное касание «✕»
 * иначе закрывало и главный — оставался пустой фон без выхода; двойное касание кнопки
 * открывало экран дважды. Во время перехода экран ещё не на переднем плане — лишнее
 * касание пропускается.
 */
private val NavController.ready: Boolean
    get() = currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

/** Закрыть текущий экран — один раз, сколько ни нажимай. */
fun NavController.back() {
    if (ready) popBackStack()
}

/** Открыть экран — один раз, сколько ни нажимай. */
fun <T : Any> NavController.go(route: T) {
    if (ready) navigate(route)
}

/** Вход в игру после создания профиля: знакомство и настройка уходят из стека, «назад» закрывает приложение. */
fun NavController.openGame() = navigate(HomeRoute) {
    popUpTo(graph.id) { inclusive = true }
}

/**
 * После удаления профиля из раздела взрослого: [hasProfile] — `Session.restore()`.
 * Остался другой профиль — главный, не осталось — первый запуск.
 */
fun NavController.restart(hasProfile: Boolean) = navigate(if (hasProfile) HomeRoute else OnboardingRoute()) {
    popUpTo(graph.id) { inclusive = true }
}
