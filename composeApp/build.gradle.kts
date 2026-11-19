import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Sync
import org.gradle.jvm.tasks.Jar
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.util.Properties

abstract class GenerateRuntimeConfigsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Optional
    @get:InputFile
    abstract val localPropertiesFile: RegularFileProperty

    @get:Input
    abstract val appVersionName: Property<String>

    @get:Input
    abstract val appVersionCode: Property<Int>

    @get:Input
    abstract val supabaseUrl: Property<String>

    @get:Input
    abstract val supabaseAnonKey: Property<String>

    @get:Input
    abstract val supabaseFallbackUrl: Property<String>

    /** Web page users open to approve a TV/device sign-in (the device-link "enter code" page). */
    @get:Input
    abstract val deviceLinkUrl: Property<String>

    @get:Input
    abstract val sentryDsn: Property<String>

    @get:Input
    abstract val sentryEnvironment: Property<String>

    @get:Input
    abstract val fanartApiKey: Property<String>

    @get:Input
    abstract val tvdbApiKey: Property<String>

    @get:Input
    abstract val discordAppId: Property<String>

    @get:Input
    abstract val malClientId: Property<String>

    @get:Input
    abstract val malRedirectUri: Property<String>

    @get:Input
    abstract val updateGitHubOwner: Property<String>

    @get:Input
    abstract val updateGitHubRepo: Property<String>

    @get:Input
    abstract val updateChannel: Property<String>

    @get:Input
    abstract val updateUserAgent: Property<String>

    @get:Input
    abstract val updateGitHubToken: Property<String>

    @get:Input
    abstract val updateReleaseTag: Property<String>

    @TaskAction
    fun generate() {
        val props = Properties()
        localPropertiesFile.asFile.orNull?.takeIf { it.exists() }?.inputStream()?.use { props.load(it) }

        val outDir = outputDir.get().asFile
        outDir.resolve("com/nuvio/app/core/network").apply {
            mkdirs()
            resolve("SupabaseConfig.kt").writeText(
                """
                |package com.nuvio.app.core.network
                |
                |object SupabaseConfig {
                |    const val URL = "${supabaseUrl.get()}"
                |    const val ANON_KEY = "${supabaseAnonKey.get()}"
                |    const val FALLBACK_URL = "${supabaseFallbackUrl.get()}"
                |    const val DEVICE_LINK_URL = "${deviceLinkUrl.get()}"
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/features/updater").apply {
            mkdirs()
            resolve("UpdateChannelConfig.kt").writeText(
                """
                |package com.nuvio.app.features.updater
                |
                |/**
                | * Release feed the in-app updater polls. Supplied at build time so the app is not tied to
                | * any single publisher; a blank owner or repo leaves the updater switched off entirely.
                | */
                |object UpdateChannelConfig {
                |    /** GitHub account or organisation that owns the release repository. */
                |    const val GITHUB_OWNER = "${updateGitHubOwner.get()}"
                |
                |    /** Repository holding the releases and their APK assets. */
                |    const val GITHUB_REPO = "${updateGitHubRepo.get()}"
                |
                |    /**
                |     * Branch name a release must target, or a marker its tag/title must contain, for the
                |     * updater to treat it as belonging to this channel. Blank accepts any published release,
                |     * which is what a single-channel project wants.
                |     */
                |    const val CHANNEL = "${updateChannel.get()}"
                |
                |    /** Sent as User-Agent on release-feed requests; GitHub rejects requests without one. */
                |    const val USER_AGENT = "${updateUserAgent.get()}"
                |
                |    /**
                |     * Read-only token letting the updater poll a private release repository.
                |     * Blank keeps requests anonymous, which is all a public repository needs.
                |     */
                |    const val GITHUB_TOKEN = "${updateGitHubToken.get()}"
                |
                |    /**
                |     * Tag this build was (or will be) published under, e.g. ci-arm64-v8a-36044651140.
                |     * Lets the updater recognise its own release instead of comparing the CI tag
                |     * against the static version name forever. Blank on non-CI builds.
                |     */
                |    const val RELEASE_TAG = "${updateReleaseTag.get()}"
                |
                |    val isConfigured: Boolean
                |        get() = GITHUB_OWNER.isNotBlank() && GITHUB_REPO.isNotBlank()
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/core/diagnostics").apply {
            mkdirs()
            resolve("SentryConfig.kt").writeText(
                """
                |package com.nuvio.app.core.diagnostics
                |
                |object SentryConfig {
                |    const val DSN = "${sentryDsn.get()}"
                |    const val DESKTOP_DSN = ""
                |    const val ENVIRONMENT = "${sentryEnvironment.get()}"
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/features/discord").apply {
            mkdirs()
            resolve("DiscordConfig.kt").writeText(
                """
                |package com.nuvio.app.features.discord
                |
                |object DiscordConfig {
                |    /** Discord application id used for Rich Presence activity assets. */
                |    const val APP_ID = "${discordAppId.get()}"
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/features/tmdb/TmdbConfig.kt").delete()

        outDir.resolve("com/nuvio/app/core/anilist").apply {
            mkdirs()
            resolve("TvdbConfig.kt").writeText(
                """
                |package com.nuvio.app.core.anilist
                |
                |object TvdbConfig {
                |    /** TheTVDB v4 project API key. Blank disables every TVDB lookup. */
                |    const val API_KEY = "${tvdbApiKey.get()}"
                |
                |    val isConfigured: Boolean
                |        get() = API_KEY.isNotBlank()
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/core/anilist").apply {
            mkdirs()
            resolve("FanartConfig.kt").writeText(
                """
                |package com.nuvio.app.core.anilist
                |
                |object FanartConfig {
                |    /** fanart.tv personal API key. Blank disables fanart artwork lookups entirely. */
                |    const val API_KEY = "${fanartApiKey.get()}"
                |
                |    val isConfigured: Boolean
                |        get() = API_KEY.isNotBlank()
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/features/anilist").apply {
            mkdirs()
            resolve("AniListConfig.kt").writeText(
                """
                |package com.nuvio.app.features.anilist
                |
                |object AniListConfig {
                |    /** AniList OAuth client id. Blank leaves the tracking card in a "not configured" state. */
                |    const val CLIENT_ID = "${props.getProperty("ANILIST_CLIENT_ID", "")}"
                |
                |    /** Must match the redirect URL registered on the AniList developer app exactly. */
                |    const val REDIRECT_URI = "${props.getProperty("ANILIST_REDIRECT_URI", "nuvio://auth/anilist")}"
                |
                |    val isConfigured: Boolean
                |        get() = CLIENT_ID.isNotBlank()
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/features/mal").apply {
            mkdirs()
            resolve("MalConfig.kt").writeText(
                """
                |package com.nuvio.app.features.mal
                |
                |object MalConfig {
                |    /** MAL OAuth client id. Blank leaves MAL authentication disabled. */
                |    const val CLIENT_ID = "${malClientId.get()}"
                |    const val REDIRECT_URI = "${malRedirectUri.get()}"
                |
                |    val isConfigured: Boolean
                |        get() = CLIENT_ID.isNotBlank()
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/features/trakt").apply {
            mkdirs()
            resolve("TraktConfig.kt").writeText(
                """
                |package com.nuvio.app.features.trakt
                |
                |object TraktConfig {
                |    const val CLIENT_ID = "${props.getProperty("TRAKT_CLIENT_ID", "")}" 
                |    const val CLIENT_SECRET = "${props.getProperty("TRAKT_CLIENT_SECRET", "")}" 
                |    const val REDIRECT_URI = "${props.getProperty("TRAKT_REDIRECT_URI", "nuvio://auth/trakt")}" 
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/features/simkl").apply {            mkdirs()
            resolve("SimklConfig.kt").writeText(
                """
                |package com.nuvio.app.features.simkl
                |
                |object SimklConfig {
                |    const val CLIENT_ID = "${props.getProperty("SIMKL_CLIENT_ID", "")}"
                |    const val REDIRECT_URI = "${props.getProperty("SIMKL_REDIRECT_URI", "nuvio://auth/simkl")}"
                |    const val APP_NAME = "${props.getProperty("SIMKL_APP_NAME", "nuvio")}"
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/features/player/skip").apply {
            mkdirs()
            resolve("IntroDbConfig.kt").writeText(
                """
                |package com.nuvio.app.features.player.skip
                |
                |object IntroDbConfig {
                |    const val URL = "${props.getProperty("INTRODB_API_URL", "")}" 
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/features/details").apply {
            mkdirs()
            resolve("ImdbEpisodeRatingsConfig.kt").writeText(
                """
                |package com.nuvio.app.features.details
                |
                |object ImdbEpisodeRatingsConfig {
                |    const val IMDB_RATINGS_API_BASE_URL = "${props.getProperty("IMDB_RATINGS_API_BASE_URL", "")}" 
                |    const val IMDB_TAPFRAME_API_BASE_URL = "${props.getProperty("IMDB_TAPFRAME_API_BASE_URL", "")}" 
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/features/debrid").apply {
            mkdirs()
            resolve("PremiumizeConfig.kt").writeText(
                """
                |package com.nuvio.app.features.debrid
                |
                |object PremiumizeConfig {
                |    const val CLIENT_ID = "${props.getProperty("PREMIUMIZE_CLIENT_ID", "")}"
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/core/build").apply {
            mkdirs()
            resolve("AppVersionConfig.kt").writeText(
                """
                |package com.nuvio.app.core.build
                |
                |object AppVersionConfig {
                |    const val VERSION_NAME = "${appVersionName.get()}"
                |    const val VERSION_CODE = ${appVersionCode.get()}
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/features/settings").apply {
            mkdirs()
            resolve("CommunityConfig.kt").writeText(
                """
                |package com.nuvio.app.features.settings
                |
                |object CommunityConfig {
                |    const val CONTRIBUTIONS_URL = "${props.getProperty("CONTRIBUTIONS_URL", "")}" 
                |    const val SUPPORTERS_WALL_URL = "${props.getProperty("SUPPORTERS_WALL_URL", "https://nuvio.tv/api/supporters/wall")}"
                |    const val DONATIONS_BASE_URL = "${props.getProperty("DONATIONS_BASE_URL", "")}" 
                |    const val DONATIONS_DONATE_URL = "${props.getProperty("DONATIONS_DONATE_URL", "")}" 
                |}
                """.trimMargin()
            )
        }
    }
}

fun readXcconfigValue(file: File, key: String): String? {
    if (!file.exists()) return null
    return file.readLines()
        .asSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
        .map { line ->
            val separatorIndex = line.indexOf('=')
            line.substring(0, separatorIndex).trim() to line.substring(separatorIndex + 1).trim()
        }
        .firstOrNull { (entryKey, _) -> entryKey == key }
        ?.second
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

val supabaseProps = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) propsFile.inputStream().use { load(it) }
}
val appVersionConfigFile = rootProject.file("iosApp/Configuration/Version.xcconfig")
val releaseAppVersionName = providers.gradleProperty("anivioVersionName").orNull
    ?: readXcconfigValue(appVersionConfigFile, "MARKETING_VERSION")
    ?: error("MARKETING_VERSION is missing from ${appVersionConfigFile.path}")
val releaseAppVersionCode = (providers.gradleProperty("anivioVersionCode").orNull
    ?: readXcconfigValue(appVersionConfigFile, "CURRENT_PROJECT_VERSION"))
    ?.toIntOrNull()
    ?: error("CURRENT_PROJECT_VERSION is missing or invalid in ${appVersionConfigFile.path}")
val desktopEnabled = providers.gradleProperty("anivio.desktop").orNull.toBoolean()
val isWindowsHost = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
require(!desktopEnabled || isWindowsHost) {
    "The Anivio desktop target currently builds on Windows."
}

val iosDistribution = (
    providers.gradleProperty("nuvio.ios.distribution").orNull
        ?: System.getenv("NUVIO_IOS_DISTRIBUTION")
        ?: supabaseProps.getProperty("NUVIO_IOS_DISTRIBUTION")
        ?: "appstore"
    ).trim().lowercase()
require(iosDistribution == "appstore" || iosDistribution == "full") {
    "NUVIO_IOS_DISTRIBUTION must be 'appstore' or 'full'."
}
val iosDistributionSourceDir = if (iosDistribution == "full") {
    "src/iosFull/kotlin"
} else {
    "src/iosAppStore/kotlin"
}
val iosFrameworkBundleId = "com.nuvio.media"
val nuvioEngineAppleFramework = rootProject.file("../nuvio-engine/platform/apple/NuvioEngine.xcframework")
val fullCommonSourceDir = project.file("src/fullCommonMain/kotlin")
val generatedRuntimeConfigDir = layout.buildDirectory.dir("generated/runtime-config/kotlin")
val requestedGradleTasks = gradle.startParameter.taskNames.map { taskName ->
    taskName.substringAfterLast(':').lowercase()
}
val requestedAndroidDistributions = requestedGradleTasks.mapNotNull { taskName ->
    when {
        "playstore" in taskName -> "playstore"
        "full" in taskName -> "full"
        else -> null
    }
}.toSet()
require(requestedAndroidDistributions.size <= 1) {
    "Build Android full and playstore distributions separately, or set -Pnuvio.android.distribution=full|playstore."
}
val configuredAndroidDistribution = providers.gradleProperty("nuvio.android.distribution").orNull
    ?: supabaseProps.getProperty("NUVIO_ANDROID_DISTRIBUTION")
val isAmbiguousAndroidPackageTask = requestedGradleTasks.any { taskName ->
    taskName == "build" ||
        taskName.startsWith("assemble") ||
        taskName.startsWith("bundle")
} && requestedAndroidDistributions.isEmpty()
require(configuredAndroidDistribution != null || !isAmbiguousAndroidPackageTask) {
    "Set -Pnuvio.android.distribution=full|playstore for aggregate Android assemble/bundle tasks."
}
val androidDistribution = (
    configuredAndroidDistribution
        ?: requestedAndroidDistributions.singleOrNull()
        ?: "playstore"
    ).trim().lowercase()
require(androidDistribution == "playstore" || androidDistribution == "full") {
    "nuvio.android.distribution must be 'playstore' or 'full'."
}
val androidDistributionSourceDir = if (androidDistribution == "full") {
    "src/androidFull/kotlin"
} else {
    "src/androidPlaystore/kotlin"
}
val runtimeLocalProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun runtimeConfigValue(key: String, fallback: String = ""): String =
    runtimeLocalProperties.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(key).orNull?.trim()?.takeIf { it.isNotBlank() }
        ?: fallback

/**
 * Reads the first key that has a value, so Anivio-prefixed settings win while the inherited
 * NUVIO_-prefixed names keep working for anyone with an existing local.properties.
 */
fun runtimeConfigValueOf(vararg keys: String, fallback: String = ""): String =
    keys.firstNotNullOfOrNull { key -> runtimeConfigValue(key).takeIf { it.isNotBlank() } } ?: fallback

fun runtimeConfigBoolean(key: String, default: Boolean): Boolean =
    when (runtimeConfigValue(key).lowercase()) {
        "1", "true", "yes", "y", "on" -> true
        "0", "false", "no", "n", "off" -> false
        else -> default
    }

val generateRuntimeConfigs = tasks.register<GenerateRuntimeConfigsTask>("generateRuntimeConfigs") {
    outputDir.set(generatedRuntimeConfigDir)
    rootProject.layout.projectDirectory.file("local.properties").takeIf { it.asFile.isFile }?.let {
        localPropertiesFile.set(it)
    }
    appVersionName.set(releaseAppVersionName)
    appVersionCode.set(releaseAppVersionCode)
    supabaseUrl.set(runtimeConfigValueOf("ANIVIO_SUPABASE_URL", "NUVIO_SUPABASE_URL"))
    supabaseAnonKey.set(runtimeConfigValueOf("ANIVIO_SUPABASE_ANON_KEY", "NUVIO_SUPABASE_ANON_KEY"))
    supabaseFallbackUrl.set(
        runtimeConfigValueOf("ANIVIO_SUPABASE_FALLBACK_URL", "NUVIO_SUPABASE_FALLBACK_URL")
    )
    deviceLinkUrl.set(runtimeConfigValueOf("ANIVIO_DEVICE_LINK_URL", "NUVIO_DEVICE_LINK_URL"))
    sentryDsn.set(runtimeConfigValue("SENTRY_DSN"))
    fanartApiKey.set(runtimeConfigValue("FANART_API_KEY"))
    tvdbApiKey.set(runtimeConfigValue("TVDB_API_KEY"))
    discordAppId.set(runtimeConfigValue("DISCORD_APP_ID"))
    malClientId.set(runtimeConfigValue("MAL_CLIENT_ID"))
    malRedirectUri.set(runtimeConfigValue("MAL_REDIRECT_URI", fallback = "nuvio://auth/mal"))
    updateGitHubOwner.set(runtimeConfigValue("ANIVIO_UPDATE_GITHUB_OWNER"))
    updateGitHubRepo.set(runtimeConfigValue("ANIVIO_UPDATE_GITHUB_REPO"))
    updateChannel.set(runtimeConfigValue("ANIVIO_UPDATE_CHANNEL"))
    updateUserAgent.set(runtimeConfigValue("ANIVIO_UPDATE_USER_AGENT", fallback = "Anivio"))
    updateGitHubToken.set(runtimeConfigValue("ANIVIO_UPDATE_GITHUB_TOKEN"))
    updateReleaseTag.set(runtimeConfigValue("ANIVIO_RELEASE_TAG"))
    sentryEnvironment.set(
        when {
            requestedGradleTasks.any { "benchmark" in it } -> "benchmark"
            requestedGradleTasks.any { "debug" in it } -> "debug"
            else -> "production"
        }
    )
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateRuntimeConfigs)
}

kotlin {
    android {
        namespace = "com.nuvio.app"
        compileSdk {
            version = release(libs.versions.android.compileSdk.get().toInt()) {
                minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt()
            }
        }
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources.enable = true
        withHostTest {}

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    if (desktopEnabled) {
        jvm("desktop") {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
    }

    val iosTargets = listOf(
        iosArm64(),
        iosSimulatorArm64()
    )

    iosTargets.forEach { iosTarget ->
        val nuvioEngineSlice = if (iosTarget.name == "iosArm64") {
            "ios-arm64"
        } else {
            "ios-arm64_x86_64-simulator"
        }
        val nuvioEngineSliceDirectory = nuvioEngineAppleFramework.resolve(nuvioEngineSlice)
        iosTarget.compilations.getByName("main") {
            cinterops {
                create("commoncrypto") {
                    defFile(project.file("src/nativeInterop/cinterop/commoncrypto.def"))
                    compilerOpts("-I${project.projectDir}/src/nativeInterop/cinterop")
                }
                create("appicon") {
                    defFile(project.file("src/nativeInterop/cinterop/appicon.def"))
                    compilerOpts("-I${project.projectDir}/src/nativeInterop/cinterop")
                }
                if (iosDistribution == "full") {
                    check(nuvioEngineSliceDirectory.resolve("libCNuvioEngine.a").isFile) {
                        "Build the local Nuvio Engine Apple XCFramework before compiling iOS Full."
                    }
                    create("nuvioengine") {
                        defFile(project.file("src/nativeInterop/cinterop/nuvioengine.def"))
                        compilerOpts("-I${nuvioEngineSliceDirectory.resolve("Headers").absolutePath}")
                        extraOpts("-libraryPath", nuvioEngineSliceDirectory.absolutePath)
                    }
                }
                configureEach {
                    extraOpts("-Xccall-mode", "direct")
                }
            }

            if (iosDistribution == "full") {
                defaultSourceSet.kotlin.srcDir(fullCommonSourceDir)
            }
            defaultSourceSet.kotlin.srcDir(project.file(iosDistributionSourceDir))
            defaultSourceSet.dependencies {
                implementation(libs.ktor.client.darwin)
                if (iosDistribution == "full") {
                    implementation(libs.quickjs.kt)
                    implementation(libs.ksoup)
                }
            }
        }

        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            freeCompilerArgs += listOf("-Xbinary=bundleId=$iosFrameworkBundleId")
            if (iosDistribution == "full") {
                linkerOpts(
                    "-lc++",
                    "-framework", "Security",
                    "-framework", "SystemConfiguration",
                    "-framework", "CoreFoundation",
                )
            }
        }
    }
    
    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generatedRuntimeConfigDir)
        }
        androidMain {
            kotlin.srcDir(project.file(androidDistributionSourceDir))
            if (androidDistribution == "full") {
                kotlin.srcDir(fullCommonSourceDir)
            }

            dependencies {
                implementation(libs.compose.uiToolingPreview)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.splashscreen)
                implementation(libs.androidx.work.runtime)
                implementation(libs.coil.gif)
                implementation("androidx.recyclerview:recyclerview:1.4.0")
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation("com.google.code.gson:gson:2.11.0")
                implementation("io.github.peerless2012:ass-media:0.4.0-beta01")
                implementation(libs.ktor.client.okhttp)
                implementation(libs.sentry.android)
                implementation(libs.androidx.media3.exoplayer.hls)
                implementation(libs.androidx.media3.exoplayer.dash)
                implementation(libs.androidx.media3.exoplayer.smoothstreaming)
                implementation(libs.androidx.media3.exoplayer.rtsp)
                implementation(libs.androidx.media3.datasource)
                implementation(libs.androidx.media3.datasource.okhttp)
                implementation(libs.androidx.media3.decoder)
                implementation(libs.androidx.media3.session)
                implementation(libs.androidx.media3.common)
                implementation(libs.androidx.media3.effect)
                implementation(libs.androidx.media3.container)
                implementation(libs.androidx.media3.extractor)
                implementation(libs.mpv.android.lib)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("lib-*.aar"))))
                if (androidDistribution == "full") {
                    implementation(files("libs/quickjs-kt-android-1.0.5-nuvio.aar"))
                    implementation(libs.ksoup)
                }
            }
        }
        if (desktopEnabled) {
            val desktopMain by getting {
                kotlin.srcDir(fullCommonSourceDir)
                kotlin.srcDir("src/windowsDesktopMain/kotlin")
                dependencies {
                    implementation(compose.desktop.currentOs)
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
                    implementation(libs.ktor.client.okhttp)
                    implementation("io.ktor:ktor-client-cio:${libs.versions.ktor.get()}")
                    implementation("io.github.dokar3:quickjs-kt:1.0.15")
                    implementation(libs.ksoup)
                    implementation("io.sentry:sentry:${libs.versions.sentry.get()}")
                }
            }
            val desktopTest by getting {
                dependencies {
                    implementation(compose.desktop.uiTestJUnit4)
                }
            }
        }
        val androidHostTest by getting {
            dependencies {
                implementation("org.robolectric:robolectric:4.16")
                implementation("androidx.compose.ui:ui-test-junit4:${libs.versions.composeMultiplatform.get()}")
                implementation("androidx.compose.ui:ui-test-manifest:${libs.versions.composeMultiplatform.get()}")
                implementation("androidx.work:work-testing:${libs.versions.androidx.work.get()}")
                implementation("com.squareup.okhttp3:mockwebserver:5.3.2")
            }
            if (androidDistribution == "full") {
                kotlin.srcDir(project.file("src/androidFullHostTest/kotlin"))
            }
        }
        commonMain.dependencies {
            implementation("io.coil-kt.coil3:coil-compose:${libs.versions.coil.get()}") {
                exclude(group = "org.jetbrains.skiko", module = "skiko")
            }
            implementation("io.coil-kt.coil3:coil-network-ktor3:${libs.versions.coil.get()}") {
                exclude(group = "org.jetbrains.skiko", module = "skiko")
            }
            implementation("io.coil-kt.coil3:coil-network-cache-control:${libs.versions.coil.get()}") {
                exclude(group = "org.jetbrains.skiko", module = "skiko")
            }
            implementation("io.coil-kt.coil3:coil-svg:${libs.versions.coil.get()}")
            implementation("dev.chrisbanes.haze:haze:1.7.2")
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compottie)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kmpalette.core)
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.kermit)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.auth)
            implementation(libs.supabase.functions)
            implementation(libs.supabase.storage)
            implementation(libs.supabase.realtime)
            implementation(libs.reorderable)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

configurations.matching { it.name == "iosMainImplementation" }.configureEach {
    project.dependencies.add(name, libs.ktor.client.darwin)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    if (name == "testAndroidHostTest") {
        inputs.dir("src/androidMain/assets/video_enhancement")
        outputs.file(layout.buildDirectory.file("enhancement-gpu-fixtures.json"))
    }
}

configurations.all {
    exclude(group = "androidx.media3", module = "media3-exoplayer")
    exclude(group = "androidx.media3", module = "media3-ui")
}

fun cmdQuote(value: String): String = "\"${value.replace("\"", "\"\"")}\""

fun psSingleQuote(value: String): String = "'${value.replace("'", "''")}'"

fun newestDirectory(parent: File): File? = parent.listFiles(File::isDirectory)
    ?.maxByOrNull(File::lastModified)

abstract class GenerateNativeRuntimeIndexTask : DefaultTask() {
    @get:InputDirectory
    abstract val runtimeDir: DirectoryProperty

    @get:OutputFile
    abstract val indexFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val dir = runtimeDir.get().asFile
        val files = dir
            .listFiles { file -> file.isFile && file.name != indexFile.get().asFile.name }
            .orEmpty()
            .map { it.name }
            .sorted()
        indexFile.get().asFile.writeText(files.joinToString(separator = "\n", postfix = "\n"))
    }
}

if (desktopEnabled) {
    val windowsPlayerBridgeArch = when (System.getProperty("os.arch").lowercase()) {
        "aarch64", "arm64" -> "arm64"
        "x86" -> "x86"
        else -> "x64"
    }
    val windowsPlayerBridgeSource = layout.projectDirectory.file("src/desktopMain/native/windows/player_bridge.cpp")
    val windowsPlayerBridgeOutput = layout.buildDirectory.file("native/windows/player_bridge.dll")
    val windowsPlayerBridgeImportLib = layout.buildDirectory.file("native/windows/player_bridge.lib")
    val windowsPlayerBridgePdb = layout.buildDirectory.file("native/windows/player_bridge.pdb")
    val windowsPlayerBridgeObj = layout.buildDirectory.file("native/windows/player_bridge.obj")
    val windowsPlayerBridgeScript = layout.buildDirectory.file("native/windows/build-player-bridge.bat")
    val windowsPlayerRuntimeOutput = layout.buildDirectory.dir("native/windows-runtime")
    if (isWindowsHost) {
        windowsPlayerBridgeOutput.get().asFile.parentFile.mkdirs()
    }
    val windowsWebView2Root = providers.gradleProperty("nuvio.webview2.dir").orNull
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?: newestDirectory(File(System.getProperty("user.home"), ".nuget/packages/microsoft.web.webview2"))
        ?: File("__missing_webview2__")
    val windowsWebView2IncludeDir = File(windowsWebView2Root, "build/native/include")
    val windowsWebView2NativeDir = File(windowsWebView2Root, "build/native/$windowsPlayerBridgeArch")
    val windowsWebView2LoaderLib = File(windowsWebView2NativeDir, "WebView2Loader.dll.lib")
    val windowsWebView2LoaderDll = File(windowsWebView2NativeDir, "WebView2Loader.dll")
    val bundledWindowsLibmpvRuntimeDir = layout.projectDirectory.dir("src/desktopMain/native/windows/runtime").asFile
    val windowsLibmpvRuntimeDirOverride = providers.gradleProperty("nuvio.windows.libmpv.runtimeDir").orNull
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
    val windowsLibmpvRuntimeDir = windowsLibmpvRuntimeDirOverride
        ?: bundledWindowsLibmpvRuntimeDir.takeIf { File(it, "libmpv-2.dll").exists() }
    val windowsLibmpvDllOverride = providers.gradleProperty("nuvio.windows.libmpv.dll").orNull
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
    val windowsLibmpvDll = windowsLibmpvDllOverride
        ?: windowsLibmpvRuntimeDir?.resolve("libmpv-2.dll")
        ?: listOf(
            File("C:/msys64/ucrt64/bin/libmpv-2.dll"),
            File("C:/msys64/mingw64/bin/libmpv-2.dll"),
        ).firstOrNull(File::exists)
    val windowsCppRuntimeDllNames = listOf(
        "vcruntime140.dll",
        "vcruntime140_1.dll",
        "msvcp140.dll",
        "msvcp140_1.dll",
        "msvcp140_2.dll",
        "msvcp140_atomic_wait.dll",
        "msvcp140_codecvt_ids.dll",
        "concrt140.dll",
    )
    val windowsCppRuntimeDlls = if (isWindowsHost) {
        windowsCppRuntimeDllNames
            .map { File("C:/Windows/System32", it) }
            .filter(File::exists)
    } else {
        emptyList()
    }
    val windowsVsWhere = File("C:/Program Files (x86)/Microsoft Visual Studio/Installer/vswhere.exe")
    val windowsVcvarsRelativePath = when (windowsPlayerBridgeArch) {
        "x86" -> "VC\\Auxiliary\\Build\\vcvars32.bat"
        "arm64" -> "VC\\Auxiliary\\Build\\vcvarsarm64.bat"
        else -> "VC\\Auxiliary\\Build\\vcvars64.bat"
    }
    val windowsVcvarsPath = providers.gradleProperty("nuvio.windows.vcvars.path").orNull
        ?.takeIf { it.isNotBlank() }
    val windowsPlayerBridgeJavaHome = providers.systemProperty("java.home").get()
    val missingWindowsPlayerBridgeInputs = listOfNotNull(
        "WebView2.h".takeUnless { windowsWebView2IncludeDir.resolve("WebView2.h").exists() },
        "WebView2Loader.dll.lib".takeUnless { windowsWebView2LoaderLib.exists() },
    )
    val missingWindowsPlayerBridgeMessage = """
        Windows desktop player bridge inputs are missing: ${missingWindowsPlayerBridgeInputs.joinToString()}.
        Install the Microsoft.Web.WebView2 NuGet package or pass -Pnuvio.webview2.dir=C:/path/to/microsoft.web.webview2/version.
        libmpv is loaded at runtime; pass -Pnuvio.windows.libmpv.runtimeDir=C:/path/to/mpv-dlls to bundle it.
    """.trimIndent()
    val windowsPlayerBridgeCommand = if (missingWindowsPlayerBridgeInputs.isNotEmpty()) {
        listOf(
            "cmd",
            "/c",
            "echo ${missingWindowsPlayerBridgeMessage.replace("\n", " ")} 1>&2 && exit /b 1",
        )
    } else {
        val sourceFile = windowsPlayerBridgeSource.asFile
        val outputFile = windowsPlayerBridgeOutput.get().asFile
        val importLibFile = windowsPlayerBridgeImportLib.get().asFile
        val pdbFile = windowsPlayerBridgePdb.get().asFile
        val objFile = windowsPlayerBridgeObj.get().asFile
        val javaIncludeDir = File(windowsPlayerBridgeJavaHome, "include")
        val javaWin32IncludeDir = File(javaIncludeDir, "win32")
        val compileCommand = listOf(
            "cl",
            "/nologo",
            "/EHsc",
            "/std:c++17",
            "/LD",
            "/DUNICODE",
            "/D_UNICODE",
            "/DNOMINMAX",
            "/DWIN32_LEAN_AND_MEAN",
            "/permissive-",
            cmdQuote(sourceFile.absolutePath),
            "/I${cmdQuote(javaIncludeDir.absolutePath)}",
            "/I${cmdQuote(javaWin32IncludeDir.absolutePath)}",
            "/I${cmdQuote(windowsWebView2IncludeDir.absolutePath)}",
            "/Fo${cmdQuote(objFile.absolutePath)}",
            "/Fd${cmdQuote(pdbFile.absolutePath)}",
            "/Fe${cmdQuote(outputFile.absolutePath)}",
            "/link",
            "/NOLOGO",
            "/INCREMENTAL:NO",
            "/IMPLIB:${cmdQuote(importLibFile.absolutePath)}",
            cmdQuote(windowsWebView2LoaderLib.absolutePath),
            "Ole32.lib",
            "User32.lib",
            "Gdi32.lib",
            "Dwmapi.lib",
            "Shell32.lib",
        ).joinToString(" ")
        val powershellCompileCommand = compileCommand.replace("\"", "__DQ__")
        val powershellCommand = """
            ${'$'}ErrorActionPreference = 'Stop'
            ${'$'}dq = [char]34
            ${'$'}vcvars = ${psSingleQuote(windowsVcvarsPath.orEmpty())}
            if ([string]::IsNullOrWhiteSpace(${'$'}vcvars)) {
              ${'$'}vswhere = ${psSingleQuote(windowsVsWhere.absolutePath)}
              if (Test-Path -LiteralPath ${'$'}vswhere) {
                ${'$'}vcvars = & ${'$'}vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -find ${psSingleQuote(windowsVcvarsRelativePath)} | Select-Object -First 1
              }
            }
            if ([string]::IsNullOrWhiteSpace(${'$'}vcvars) -or -not (Test-Path -LiteralPath ${'$'}vcvars)) {
              Write-Error 'Visual Studio C++ toolchain was not found. Install MSVC or pass -Pnuvio.windows.vcvars.path=C:\path\to\vcvars64.bat.'
              exit 1
            }
            ${'$'}vcvars = ([string]${'$'}vcvars).Trim()
            ${'$'}bat = ${psSingleQuote(windowsPlayerBridgeScript.get().asFile.absolutePath)}
            ${'$'}compile = ${psSingleQuote(powershellCompileCommand)}.Replace('__DQ__', ${'$'}dq)
            ${'$'}lines = @(
              '@echo off',
              ('set {0}VCVARS={1}{0}' -f ${'$'}dq, ${'$'}vcvars),
              ('call {0}%VCVARS%{0} >nul' -f ${'$'}dq),
              'if errorlevel 1 exit /b %errorlevel%',
              ${'$'}compile,
              'exit /b %ERRORLEVEL%'
            )
            Set-Content -LiteralPath ${'$'}bat -Value ${'$'}lines -Encoding ASCII
            & cmd.exe /d /c ${'$'}bat
            ${'$'}code = ${'$'}LASTEXITCODE
            if (${'$'}code -ne 0) { exit ${'$'}code }
        """.trimIndent()
        listOf(
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-Command",
            powershellCommand,
        )
    }
    val buildWindowsPlayerBridge = tasks.register<Exec>("buildWindowsPlayerBridge") {
        notCompatibleWithConfigurationCache("Builds a host-local player bridge against WebView2 and libmpv for Windows.")
        enabled = isWindowsHost
        inputs.file(windowsPlayerBridgeSource)
        if (windowsWebView2IncludeDir.exists()) {
            inputs.dir(windowsWebView2IncludeDir)
        }
        if (windowsWebView2LoaderLib.exists()) {
            inputs.file(windowsWebView2LoaderLib)
        }
        outputs.file(windowsPlayerBridgeOutput)
        outputs.file(windowsPlayerBridgeImportLib)
        outputs.file(windowsPlayerBridgePdb)
        commandLine(windowsPlayerBridgeCommand)
    }

    val prepareWindowsPlayerRuntime = tasks.register<Sync>("prepareWindowsPlayerRuntime") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        enabled = isWindowsHost
        into(windowsPlayerRuntimeOutput)
        if (windowsWebView2LoaderDll.exists()) {
            from(windowsWebView2LoaderDll)
        }
        windowsCppRuntimeDlls.forEach { dllFile ->
            from(dllFile)
        }
        when {
            windowsLibmpvRuntimeDir?.exists() == true -> {
                from(windowsLibmpvRuntimeDir) {
                    include("*.dll")
                }
            }
            windowsLibmpvDll?.exists() == true -> {
                from(windowsLibmpvDll)
            }
        }
    }

    val generateWindowsPlayerRuntimeIndex = tasks.register<GenerateNativeRuntimeIndexTask>("generateWindowsPlayerRuntimeIndex") {
        enabled = isWindowsHost
        dependsOn(prepareWindowsPlayerRuntime)
        runtimeDir.set(windowsPlayerRuntimeOutput)
        indexFile.set(windowsPlayerRuntimeOutput.map { it.file("runtime-files.txt") })
    }


    val desktopTorrServer = providers.gradleProperty("anivio.desktop.torrServer").orNull?.let(project::file)

    tasks.withType<Jar>().named("desktopJar") {
        dependsOn(buildWindowsPlayerBridge, generateWindowsPlayerRuntimeIndex)
        from(windowsPlayerBridgeOutput) { into("native/windows") }
        from(windowsPlayerRuntimeOutput) { into("native/windows") }
        if (desktopTorrServer != null) {
            from(desktopTorrServer) {
                into("torrserver/windows-amd64")
                rename { "TorrServer.exe" }
            }
        }
        from("src/androidMain/assets/video_enhancement") { into("video_enhancement") }

    }

    compose.desktop {
        application {
            mainClass = "com.nuvio.app.MainKt"
            providers.gradleProperty("anivio.desktop.smokePlayer").orNull?.let {
                jvmArgs += "-Dnuvio.desktop.smokePlayerUrl=$it"
            }
            providers.gradleProperty("anivio.desktop.storageRoot").orNull?.let {
                jvmArgs += "-Danivio.desktop.storageRoot=$it"
            }

            jvmArgs(
                "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED",
            )
            nativeDistributions {
                targetFormats(TargetFormat.Exe)
                packageName = "Anivio"
                packageVersion = releaseAppVersionName.substringBefore('-')
                vendor = "Anivio"
                modules("java.net.http", "java.management", "java.instrument", "jdk.accessibility", "jdk.httpserver", "jdk.unsupported")
                windows {
                    iconFile.set(project.file("src/desktopMain/resources/icons/app-icon-av-transparent.ico"))
                    upgradeUuid = "395990ee-9b8a-3548-922c-e7a23a495b8d"
                    shortcut = true
                    menu = true
                    menuGroup = "Anivio"
                }
            }
        }
    }
}
