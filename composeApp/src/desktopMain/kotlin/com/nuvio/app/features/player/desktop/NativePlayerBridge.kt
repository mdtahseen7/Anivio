package com.nuvio.app.features.player.desktop

import com.nuvio.app.core.storage.DesktopCache
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal fun interface NativePlayerEventSink {
    fun onPlayerEvent(type: String, value: Double)
    fun onPlayerError(message: String) {}
}

internal object NativePlayerBridge {
    private val windowsNativeRuntimeDependencyNames = listOf(
        "vcruntime140.dll",
        "vcruntime140_1.dll",
        "msvcp140.dll",
        "msvcp140_1.dll",
        "msvcp140_2.dll",
        "msvcp140_atomic_wait.dll",
        "msvcp140_codecvt_ids.dll",
        "concrt140.dll",
        "WebView2Loader.dll",
    )
    private val preloadStarted = AtomicBoolean(false)
    // The loader names the missing dependency right before "cannot open
    // shared object file" (e.g. ".../libplayer_bridge.so: libmpv.so.2:
    // cannot open shared object file"). Do NOT take the first .so in the
    // message — that is the library being loaded, not the missing one.
    private val MISSING_LIB_REGEX =
        Regex("""([A-Za-z0-9_.\-]+\.so(\.\d+)*): cannot open shared object file""")
    private val ANY_LIB_REGEX = Regex("""[A-Za-z0-9_.\-]+\.so(\.\d+)*""")

    /**
     * Set when the native library failed to load (e.g. a missing system
     * library such as libmpv on a distro that does not ship it). Stored
     * instead of thrown from the initializer so the first playback attempt
     * can report the real cause (which system library is missing + how to
     * install it) instead of a bare "Could not initialize class" from the
     * class initializer, which hides the cause.
     */
    @Volatile
    private var loadError: Throwable? = null

    init {
        runCatching { loadNativeLibrary() }.onFailure { loadError = it }
    }

    /**
     * Throws a clear, actionable error if the native library failed to load
     * (names the missing system library + how to install it). Call before
     * [create] so a missing system library is reported instead of a bare
     * UnsatisfiedLinkError / "Could not initialize class".
     */
    fun ensureLoaded() {
        val error = loadError ?: return
        throw IllegalStateException(nativeLoadErrorMessage(error), error)
    }

    private fun nativeLoadErrorMessage(error: Throwable): String {
        // Non-link errors (e.g. unsupported platform) already say what is wrong.
        if (error !is LinkageError) {
            return "Native player failed to start: ${error.message ?: error::class.simpleName}"
        }
        // Walk the cause chain (bounded, in case of a cycle) to find the
        // linker's message, which names the missing .so.
        val messages = mutableListOf<String>()
        var current: Throwable? = error
        var hops = 0
        while (current != null && hops < 5) {
            current.message?.let { messages += it }
            current = current.cause
            hops++
        }
        val causeText = messages.joinToString(" | ")
        val missingLib = MISSING_LIB_REGEX.find(causeText)?.groupValues?.getOrNull(1)
            ?: ANY_LIB_REGEX.findAll(causeText).lastOrNull()?.value
            ?: "a required system library"
        val install = when {
            missingLib.startsWith("libmpv") ->
                "Arch: sudo pacman -S mpv | Fedora: sudo dnf install mpv (needs RPM Fusion) | Ubuntu/Debian: sudo apt install libmpv2"
            missingLib.startsWith("libwebkit2gtk") ->
                "Arch: sudo pacman -S webkit2gtk | Fedora: sudo dnf install webkit2gtk4.1 | Ubuntu/Debian: sudo apt install libwebkit2gtk-4.1-0"
            missingLib.startsWith("libgtk-3") || missingLib.startsWith("libgdk-3") ->
                "Arch: sudo pacman -S gtk3 | Fedora: sudo dnf install gtk3 | Ubuntu/Debian: sudo apt install libgtk-3-0"
            else ->
                "install the package that provides '$missingLib' for your distro"
        }
        return "Native player failed to start: missing system library '$missingLib'. " +
            "Install it, then restart the app. $install " +
            "(Original error: $causeText)"
    }

    /**
     * Linux only: initialize GTK before any AWT/Compose/Skia code runs, so GDK's
     * types are registered once and canonically (prevents a GdkDisplayManager
     * GType conflict with Skiko on some JDK builds). Must be the first thing
     * main() calls. Approach from skoruppa's linux-webkitgtk branch.
     */
    external fun initGtkEarly(): Boolean

    external fun create(
        hostViewPtr: Long,
        sourceUrl: String,
        headerLines: Array<String>,
        playWhenReady: Boolean,
        initialPositionMs: Long,
        controlsPageUrl: String,
        decoderPriority: Int,
        nvidiaRtxSuperResolutionEnabled: Boolean,
        eventSink: NativePlayerEventSink,
    ): Long

    external fun dispose(handle: Long)
    external fun updateControls(handle: Long, controlsJson: String)
    external fun requestFocus(handle: Long)
    external fun beginWindowDrag(handle: Long)
    external fun setWindowResizable(windowHwnd: Long, enabled: Boolean)
    private external fun reparentSurfaceNative(handle: Long, hostViewPtr: Long)

    fun reparentSurface(handle: Long, hostViewPtr: Long): Boolean =
        runCatching { reparentSurfaceNative(handle, hostViewPtr) }.isSuccess
    external fun setPaused(handle: Long, paused: Boolean)
    external fun seekTo(handle: Long, positionMs: Long)
    external fun seekBy(handle: Long, offsetMs: Long)
    external fun setSpeed(handle: Long, speed: Float)
    external fun adjustVolume(handle: Long, delta: Float)
    external fun setVolume(handle: Long, level: Float)
    external fun volume(handle: Long): Float
    external fun setResizeMode(handle: Long, mode: Int)
    external fun durationMs(handle: Long): Long
    external fun positionMs(handle: Long): Long
    external fun bufferedPositionMs(handle: Long): Long
    external fun isLoading(handle: Long): Boolean
    external fun isEnded(handle: Long): Boolean
    external fun isPaused(handle: Long): Boolean
    external fun speed(handle: Long): Float
    external fun audioTracksJson(handle: Long): String
    external fun subtitleTracksJson(handle: Long): String
    external fun selectAudioTrack(handle: Long, trackId: Int)
    external fun selectSubtitleTrack(handle: Long, trackId: Int)
    external fun addSubtitleUrl(handle: Long, url: String)
    external fun clearExternalSubtitles(handle: Long)
    external fun clearExternalSubtitlesAndSelect(handle: Long, trackId: Int)
    external fun applyWindowChrome(
        windowHwnd: Long,
        darkMode: Boolean,
        captionColorRgb: Int,
        borderColorRgb: Int,
        textColorRgb: Int,
    )
    external fun setWindowBorderlessFullscreen(
        windowHwnd: Long,
        fullscreen: Boolean,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    )
    external fun setMacosWindowFullscreen(windowViewPtr: Long, fullscreen: Boolean)

    external fun setSubtitleDelayMs(handle: Long, delayMs: Int)
    external fun setAudioTuning(handle: Long, delayMs: Int, gainDb: Float)
    external fun chaptersJson(handle: Long): String
    external fun setVideoShaders(handle: Long, files: String): Boolean
    external fun setSubtitleFontFamily(handle: Long, family: Int, preserveEmbeddedStyles: Boolean, bold: Boolean)
    external fun applySubtitleStyle(
        handle: Long,
        textColor: String,
        backgroundColor: String,
        outlineColor: String,
        outlineSize: Float,
        bold: Boolean,
        fontSize: Float,
        subPos: Int,
        useLibass: Boolean,
        stripSdh: Boolean,
    )
    external fun warmupWebView2(controlsPageUrl: String): Boolean
    external fun shutdownWebView2Warmup()
    external fun setWindowsDisplaySleepInhibited(inhibited: Boolean): Boolean

    val controlsPageUrl: String by lazy { controlsPageAssets.url }
    private val controlsPageAssets: ControlsPageAssets by lazy { exportControlsPageAssets() }

    fun preloadAsync() {
        if (!preloadStarted.compareAndSet(false, true)) return
        Thread {
            val controlsPage = runCatching { controlsPageAssets }
                .getOrNull()
                ?: return@Thread
            // Linux warms the WebKitGTK engine through the same entry point
            // (the bridge maps warmupWebView2 to a hidden WebKitGTK view).
            if (DesktopHostOs.current == DesktopHostOs.WINDOWS ||
                DesktopHostOs.current == DesktopHostOs.LINUX
            ) {
                runCatching { warmupWebView2(controlsPage.url) }
            }
        }.apply {
            name = "nuvio-native-player-preload"
            isDaemon = true
            start()
        }
        if (DesktopHostOs.current == DesktopHostOs.WINDOWS ||
            DesktopHostOs.current == DesktopHostOs.LINUX
        ) {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    runCatching { shutdownWebView2Warmup() }
                }.apply {
                    name = "nuvio-webview2-warmup-shutdown"
                }
            )
        }
    }

    private fun loadNativeLibrary() {
        val platform = DesktopHostOs.current
        require(
            platform == DesktopHostOs.MACOS ||
                platform == DesktopHostOs.WINDOWS ||
                platform == DesktopHostOs.LINUX
        ) {
            "Native desktop playback is not implemented for $platform yet."
        }

        val libraryName = nativeLibraryName(platform)
        val platformDir = nativeDirectoryName(platform)
        findPackagedApplicationLibrary(platformDir, libraryName)?.let { packagedLibrary ->
            loadNativeRuntimeDependencies(platform, packagedLibrary.parentFile)
            System.load(packagedLibrary.absolutePath)
            return
        }
        findLocalBuildLibrary(platformDir, libraryName)?.let { localLibrary ->
            copyLocalRuntimeResources(platformDir, localLibrary.parentFile)
            loadNativeRuntimeDependencies(platform, localLibrary.parentFile)
            System.load(localLibrary.absolutePath)
            return
        }

        val resource = "/native/$platformDir/$libraryName"
        val files = buildMap {
            put(libraryName, readResourceBytes(resource))
            bundledRuntimeResourceNames(platformDir).forEach { name ->
                resourceBytesOrNull("/native/$platformDir/$name")?.let { bytes -> put(name, bytes) }
            }
        }
        val directory = DesktopCache.installVersionedFiles("native-player-bridge/$platformDir", files).toFile()
        loadNativeRuntimeDependencies(platform, directory)
        System.load(directory.resolve(libraryName).absolutePath)
    }

    private fun findPackagedApplicationLibrary(platformDir: String, libraryName: String): File? {
        val resourcesDir = System.getProperty("compose.application.resources.dir")
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?: return null
        return resourcesDir.resolve("native/$platformDir/$libraryName").takeIf(File::isFile)
    }

    private fun loadNativeRuntimeDependencies(platform: DesktopHostOs, directory: File) {
        if (platform != DesktopHostOs.WINDOWS) return

        windowsNativeRuntimeDependencyNames.forEach { name ->
            val dependency = directory.resolve(name)
            if (dependency.exists()) {
                System.load(dependency.absolutePath)
            }
        }
    }

    private fun bundledRuntimeResourceNames(platformDir: String): List<String> {
        val indexResource = "/native/$platformDir/runtime-files.txt"
        val indexed = NativePlayerBridge::class.java.getResourceAsStream(indexResource)
            ?.bufferedReader()
            ?.useLines { lines ->
                lines.map(String::trim)
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toList()
            }
            .orEmpty()
        if (indexed.isNotEmpty()) return indexed

        return when (platformDir) {
            "windows" -> listOf("libmpv-2.dll")
            else -> emptyList()
        }
    }

    private fun findLocalBuildLibrary(platformDir: String, libraryName: String): File? {
        val architectureDirectories = nativeArchitectureDirectoryNames(platformDir)
        val roots = listOf(
            File("composeApp/build/native/$platformDir"),
            File("build/native/$platformDir"),
        )
        val candidates = roots.map { it.resolve(libraryName) } + roots.flatMap { root ->
            architectureDirectories.map { architecture -> root.resolve(architecture).resolve(libraryName) }
        }
        return candidates.firstOrNull { it.exists() }
    }

    private fun nativeArchitectureDirectoryNames(platformDir: String): List<String> =
        when (platformDir) {
            "macos" -> when (System.getProperty("os.arch").lowercase()) {
                "aarch64", "arm64" -> listOf("arm64", "aarch64")
                "amd64", "x64", "x86_64" -> listOf("x86_64")
                else -> emptyList()
            }
            else -> emptyList()
        }

    private fun copyLocalRuntimeResources(platformDir: String, targetDir: File) {
        val runtimeRoots = listOf(
            File("composeApp/build/native/$platformDir-runtime"),
            File("build/native/$platformDir-runtime"),
        )
        val runtimeDirs = runtimeRoots.flatMap { root ->
            nativeArchitectureDirectoryNames(platformDir).map(root::resolve)
        } + runtimeRoots
        runtimeDirs.firstOrNull(File::isDirectory)
            ?.listFiles { file -> file.isFile }
            ?.forEach { runtimeFile ->
                val target = targetDir.resolve(runtimeFile.name)
                if (runtimeFile.absolutePath != target.absolutePath) {
                    runCatching { runtimeFile.copyTo(target, overwrite = true) }
                }
            }
    }

    private fun nativeDirectoryName(platform: DesktopHostOs): String =
        when (platform) {
            DesktopHostOs.MACOS -> "macos"
            DesktopHostOs.WINDOWS -> "windows"
            DesktopHostOs.LINUX -> "linux"
            DesktopHostOs.UNKNOWN -> "unknown"
        }

    private fun nativeLibraryName(platform: DesktopHostOs): String =
        when (platform) {
            DesktopHostOs.MACOS -> "libplayer_bridge.dylib"
            DesktopHostOs.WINDOWS -> "player_bridge.dll"
            DesktopHostOs.LINUX -> "libplayer_bridge.so"
            DesktopHostOs.UNKNOWN -> "player_bridge"
        }

    private fun exportControlsPageAssets(): ControlsPageAssets {
        val files = linkedMapOf(
            "controls.html" to readResourceBytes("/player-ui/controls.html"),
            "controls.css" to readTextResource("/player-ui/controls.css")
                .replace("/* __NUVIO_PLAYER_FONT_FACES__ */", nativePlayerFontFaces())
                .toByteArray(Charsets.UTF_8),
            "controls.js" to readResourceBytes("/player-ui/controls.js"),
            "fonts/jetbrains_sans_regular.ttf" to readResourceBytes(
                "/composeResources/nuvio.composeapp.generated.resources/font/jetbrains_sans_regular.ttf",
            ),
            "fonts/jetbrains_sans_semibold.ttf" to readResourceBytes(
                "/composeResources/nuvio.composeapp.generated.resources/font/jetbrains_sans_semibold.ttf",
            ),
            "fonts/jetbrains_sans_bold.ttf" to readResourceBytes(
                "/composeResources/nuvio.composeapp.generated.resources/font/jetbrains_sans_bold.ttf",
            ),
        )
        val root = DesktopCache.installVersionedFiles("player-ui", files).toFile()
        return ControlsPageAssets(
            url = root.resolve("controls.html").toURI().toASCIIString(),
        )
    }

    private fun nativePlayerFontFaces(): String =
        """
            @font-face {
              font-family: "Nuvio JetBrains Sans";
              src: url("fonts/jetbrains_sans_regular.ttf") format("truetype");
              font-weight: 400;
              font-style: normal;
              font-display: block;
            }
            @font-face {
              font-family: "Nuvio JetBrains Sans";
              src: url("fonts/jetbrains_sans_semibold.ttf") format("truetype");
              font-weight: 600;
              font-style: normal;
              font-display: block;
            }
            @font-face {
              font-family: "Nuvio JetBrains Sans";
              src: url("fonts/jetbrains_sans_bold.ttf") format("truetype");
              font-weight: 700 900;
              font-style: normal;
              font-display: block;
            }
        """.trimIndent()

    private fun readTextResource(resource: String): String =
        readResourceBytes(resource).toString(Charsets.UTF_8)

    private fun readResourceBytes(resource: String): ByteArray =
        resourceBytesOrNull(resource) ?: error("Missing native player resource: $resource")

    private fun resourceBytesOrNull(resource: String): ByteArray? =
        NativePlayerBridge::class.java.getResourceAsStream(resource)?.use { it.readBytes() }

    private data class ControlsPageAssets(
        val url: String,
    )
}

internal fun preloadNativePlayerBridgeAsync() {
    if (DesktopHostOs.current == DesktopHostOs.MACOS ||
        DesktopHostOs.current == DesktopHostOs.WINDOWS ||
        DesktopHostOs.current == DesktopHostOs.LINUX
    ) {
        runCatching {
            NativePlayerBridge.preloadAsync()
        }
    }
}
