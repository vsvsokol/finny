import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

// Веб-версия Финни. Своего кода UI и игры здесь нет: модуль компилирует те же
// исходники app/src/main/java под wasmJs, а Android-части (Room, DataStore,
// SoundPool, уведомления) заменяет своими файлами из src/wasmJsMain/kotlin.
// Картинки, звуки, шрифт и JSON берутся из app/ при сборке — копий в репозитории нет.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val appMain = rootProject.layout.projectDirectory.dir("app/src/main")

/** Файлы app/, которые на вебе заменены своими (src/wasmJsMain/kotlin) или не нужны. */
val androidOnly = listOf(
    "ru/finney/pet/MainActivity.kt",
    "ru/finney/pet/FinneyApplication.kt",
    "ru/finney/pet/AppContainer.kt",
    "ru/finney/pet/ViewModelFactory.kt",
    // Из data/ общие только Entities, Mappers и GameRows: ими веб хранит сохранение (WebGameStorage).
    "ru/finney/pet/data/db/FinneyDatabase.kt",
    "ru/finney/pet/data/db/GameDao.kt",
    "ru/finney/pet/data/prefs/**",
    "ru/finney/pet/data/repository/**",
    "ru/finney/pet/notifications/**",
    "ru/finney/pet/content/AssetContentLoader.kt",
    "ru/finney/pet/ui/sound/SoundPlayer.kt",
    "ru/finney/pet/ui/motion/AppMotion.kt",
    "ru/finney/pet/ui/motion/SteadyFrameRate.kt",
    "ru/finney/pet/ui/home/NewSeenStore.kt",
    "ru/finney/pet/ui/platform/Platform.kt",
)

/**
 * Общие исходники app/ без Android-частей. Копируются, а не подключаются напрямую:
 * exclude у source set задел бы и одноимённые веб-замены в src/wasmJsMain/kotlin.
 */
val appSources = layout.buildDirectory.dir("generated/appSources")
val syncAppSources = tasks.register<Sync>("syncAppSources") {
    description = "Копирует общие исходники app/ (без Android-частей) для веб-сборки."
    from(appMain.dir("java")) { exclude(androidOnly) }
    into(appSources)
}

// ---------- Ресурсы: из app/ в формат Compose Multiplatform ----------

val webResources = layout.buildDirectory.dir("generated/finnyResources")
val webR = layout.buildDirectory.dir("generated/finnyR")

val syncAppResources = tasks.register<Sync>("syncAppResources") {
    description = "Копирует картинки, звуки, шрифты и JSON из app/ в ресурсы веб-сборки."
    into(webResources)
    from(appMain.dir("res/drawable-nodpi")) { into("drawable") }
    from(appMain.dir("res/drawable")) { into("drawable"); exclude("ic_notification.xml") }
    from(appMain.dir("res/font")) { into("font"); include("*.ttf") }
    from(appMain.dir("res/raw")) { into("files/raw") }
    from(appMain.dir("assets/content")) { into("files/content") }
    // Запасной шрифт с кириллицей для текста без своего шрифта: на вебе системных шрифтов нет.
    from(layout.projectDirectory.dir("fonts")) { into("font"); include("*.ttf") }
}

/**
 * Веб-`R`: те же имена, что у Android (`R.drawable.home_shop_icon`), — числа-индексы
 * в таблицы ресурсов Compose. Поэтому 500 мест вида `painterResource(R.drawable.x)`
 * в app/ компилируются под веб без правок (см. src/wasmJsMain/.../res/ResShims.kt).
 */
val generateWebR = tasks.register("generateWebR") {
    description = "Генерирует ru.finney.pet.R и таблицы ресурсов для веб-сборки."
    val drawables = appMain.dir("res").asFileTree.matching {
        include("drawable-nodpi/*.webp", "drawable-nodpi/*.png", "drawable/*.xml")
        exclude("drawable/ic_notification.xml")
    }
    val raws = appMain.dir("res/raw").asFileTree
    val fonts = appMain.dir("res/font").asFileTree.matching { include("*.ttf") }
    inputs.files(drawables, raws, fonts)
    val out = webR
    outputs.dir(out)
    doLast {
        fun names(files: Iterable<File>) = files.map { it.nameWithoutExtension }.distinct().sorted()
        val d = names(drawables.files)
        val r = raws.files.sortedBy { it.nameWithoutExtension }
        val f = names(fonts.files)
        val dir = out.get().asFile.resolve("ru/finney/pet").apply { mkdirs() }
        fun obj(name: String, items: List<String>) = buildString {
            appendLine("    object $name {")
            items.forEachIndexed { i, n -> appendLine("        const val $n: Int = $i") }
            appendLine("    }")
        }
        dir.resolve("R.kt").writeText(buildString {
            appendLine("// Сгенерировано web/build.gradle.kts (generateWebR). Не править.")
            appendLine("package ru.finney.pet")
            appendLine()
            appendLine("object R {")
            append(obj("drawable", d))
            append(obj("raw", r.map { it.nameWithoutExtension }))
            append(obj("font", f))
            appendLine("}")
        })
        dir.resolve("web/res").mkdirs()
        dir.resolve("web/res/ResTables.kt").writeText(buildString {
            appendLine("// Сгенерировано web/build.gradle.kts (generateWebR). Не править.")
            appendLine("package ru.finney.pet.web.res")
            appendLine()
            appendLine("import org.jetbrains.compose.resources.DrawableResource")
            appendLine("import org.jetbrains.compose.resources.FontResource")
            appendLine()
            appendLine("internal val drawableTable: List<DrawableResource> by lazy { listOf(")
            d.forEach { appendLine("    Res.drawable.$it,") }
            appendLine(") }")
            appendLine()
            appendLine("internal val drawableNames: Map<String, Int> by lazy { mapOf(")
            d.forEachIndexed { i, n -> appendLine("    \"$n\" to $i,") }
            appendLine(") }")
            appendLine()
            appendLine("internal val rawPaths: List<String> = listOf(")
            r.forEach { appendLine("    \"files/raw/${it.name}\",") }
            appendLine(")")
            appendLine()
            appendLine("internal val fontTable: List<FontResource> by lazy { listOf(")
            f.forEach { appendLine("    Res.font.$it,") }
            appendLine(") }")
        })
    }
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("finny")
        browser {
            commonWebpackConfig {
                outputFileName = "finny.js"
                // Дев-сервер: localhost:8080, браузер не открывает сам — открывайте свой (см. web/README.md).
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    open = false
                    port = 8080
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        named("wasmJsMain") {
            kotlin.srcDir(syncAppSources)
            kotlin.srcDir(generateWebR)
            dependencies {
                implementation(libs.jb.compose.runtime)
                implementation(libs.jb.compose.foundation)
                implementation(libs.jb.compose.ui)
                implementation(libs.jb.compose.material3)
                implementation(libs.jb.compose.resources)
                implementation(libs.jb.lifecycle.viewmodel.compose)
                implementation(libs.jb.lifecycle.runtime.compose)
                implementation(libs.jb.navigation.compose)
                implementation(libs.jb.navigationevent.compose)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.browser)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "ru.finney.pet.web.res"
    publicResClass = false
    generateResClass = always
    customDirectory(sourceSetName = "wasmJsMain", directoryProvider = syncAppResources.map { webResources.get() })
}

