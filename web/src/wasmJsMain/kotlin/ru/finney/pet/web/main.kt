package ru.finney.pet.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import ru.finney.pet.AppContainer
import ru.finney.pet.content.ContentException
import ru.finney.pet.content.ContentParser
import ru.finney.pet.content.ContentValidator
import ru.finney.pet.domain.model.GameContent
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.finney.pet.navigation.StartViewModel
import ru.finney.pet.navigation.FinneyNavHost
import ru.finney.pet.ui.layout.LocalWindowLayout
import ru.finney.pet.ui.layout.PortraitColumnWidth
import ru.finney.pet.ui.layout.WindowLayout
import ru.finney.pet.ui.layout.windowLayoutFor
import ru.finney.pet.ui.motion.LocalAnimations
import ru.finney.pet.ui.sound.LocalSounds
import ru.finney.pet.ui.theme.FinneyTheme
import ru.finney.pet.web.res.Res
import ru.finney.pet.web.res.WebFonts

/** Учебный контент — те же JSON, что в app/src/main/assets/content. */
private val CONTENT_FILES = listOf(
    ContentParser.ECONOMY, ContentParser.SHOP, ContentParser.GOALS, ContentParser.TASKS, ContentParser.GLOSSARY,
)

@OptIn(ExperimentalResourceApi::class)
private suspend fun loadContent(): GameContent {
    val texts = CONTENT_FILES.associateWith { Res.readBytes("files/content/$it").decodeToString() }
    val content = ContentParser.parse { file -> texts[file] ?: error("Нет файла контента $file") }
    val errors = ContentValidator.validate(content)
    if (errors.isNotEmpty()) throw ContentException(errors)
    return content
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    MainScope().launch {
        try {
            if (!acquireGameLock()) {
                showFatal("Финни уже открыт в другой вкладке. Закройте её и обновите эту страницу. Если вкладок нет — обновите браузер.")
                return@launch
            }
            if (UrlFlags.has("reset") && BrowserStorage.get("account.selected") == null) {
                BrowserStorage.remove("games")
                BrowserStorage.remove("activeProfile")
            }
            WebFonts.load()
            val account = WebAccount()
            webAccount = account
            AppContainer.instance = AppContainer(loadContent(), account.storage, account.activeKey)
            account.start()
        } catch (e: Throwable) {
            showFatal("Не удалось загрузить игру: ${e.message}")
            throw e
        }
        hideSplash()
        ComposeViewport(document.getElementById("finny-game")!!) { FinnyWebApp() }
    }
}

private lateinit var webAccount: WebAccount

/** То же, что MainActivity.setContent на Android: звуки, анимации, тема, граф экранов. */
@Composable
fun FinnyWebApp() {
    val epoch by webAccount.navigationEpoch.collectAsState()
    val fatal by webAccount.localProblem.collectAsState()
    if (fatal != null) { TextSaveFailure(fatal!!); return }
    val container = AppContainer.instance
    val sounds = container.sounds
    val settings by sounds.settings.collectAsState()

    // Запасной шрифт с кириллицей — до первого текста, иначе мелькнут квадраты.
    val resolver = LocalFontFamilyResolver.current
    var fontsReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        resolver.preload(WebFonts.fallback)
        fontsReady = true
    }
    if (!fontsReady) return

    CompositionLocalProvider(LocalSounds provides sounds, LocalAnimations provides settings.animations) {
        FinneyTheme {
            AppFrame {
                Scaffold(modifier = Modifier.fillMaxSize(), contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
                    key(epoch) {
                        FinneyNavHost(modifier = Modifier.fillMaxSize(),
                            startViewModel = viewModel(key = "web-start-$epoch", factory = StartViewModel.Factory))
                    }
                }
            }
        }
    }
}

/**
 * Режим по размеру окна (ui/layout/WindowLayout.kt): телефон — игра во весь экран,
 * планшет — портретная колонка по центру, компьютер — комната-сцена и панель справа.
 * `?layout=compact|medium|expanded` — принудительно, для проверки командой.
 */
@Composable
private fun AppFrame(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(DesktopBackdrop), contentAlignment = Alignment.Center) {
        val forced = UrlFlags.value("layout")?.let { name -> WindowLayout.entries.firstOrNull { it.name.equals(name, true) } }
        val layout = forced ?: windowLayoutFor(maxWidth, maxHeight)
        CompositionLocalProvider(LocalWindowLayout provides layout) {
            when (layout) {
                WindowLayout.COMPACT, WindowLayout.EXPANDED -> content()
                WindowLayout.MEDIUM -> Box(Modifier.fillMaxHeight().widthIn(max = PortraitColumnWidth).clipToBounds()) { content() }
            }
        }
    }
}

private val DesktopBackdrop = Color(0xFFE9E1FF)

@Composable
private fun TextSaveFailure(message: String) {
    androidx.compose.material3.Text(message, Modifier.fillMaxSize().padding(24.dp))
}
