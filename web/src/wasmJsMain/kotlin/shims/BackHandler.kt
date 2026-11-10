package androidx.activity.compose

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * `BackHandler` экранов app/ — на вебе тот же диспетчер «назад», что у NavHost в
 * Compose Multiplatform (androidx.navigationevent): Esc и «назад» браузера.
 */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )
}
