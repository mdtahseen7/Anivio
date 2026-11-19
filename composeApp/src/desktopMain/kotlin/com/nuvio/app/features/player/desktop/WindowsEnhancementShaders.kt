package com.nuvio.app.features.player.desktop

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.player.EnhancementResult
import com.nuvio.app.features.player.EnhancementResolution
import com.nuvio.app.features.player.VideoEnhancement
import java.nio.file.Files
import java.nio.file.Path

internal object WindowsEnhancementShaders {
    private val directory: Path by lazy {
        Files.createTempDirectory("anivio-shaders-").also { it.toFile().deleteOnExit() }
    }
    private val extracted = mutableMapOf<Pair<String, EnhancementResolution>, Path>()

    private val recovery = DesktopStorage.store("windows_enhancement_recovery")
    private var interrupted = recovery.getBoolean("running") == true

    init {
        Runtime.getRuntime().addShutdownHook(Thread { recovery.remove("running") })
    }

    @Synchronized
    fun apply(handle: Long, mode: VideoEnhancement, output: EnhancementResolution): EnhancementResult {
        if (interrupted) {
            interrupted = false
            recovery.remove("running")
            if (mode != VideoEnhancement.Off) return EnhancementResult.Recovered
        }
        return runCatching {
            val files = files(mode, output)
            if (files.isNotEmpty()) recovery.putBoolean("running", true)
            if (NativePlayerBridge.setVideoShaders(handle, files)) {
                if (files.isEmpty()) {
                    recovery.remove("running")
                    EnhancementResult.Off
                } else {
                    EnhancementResult.Loaded
                }
            } else {
                recovery.remove("running")
                EnhancementResult.Failed
            }
        }.getOrElse {
            runCatching { NativePlayerBridge.setVideoShaders(handle, "") }
            recovery.remove("running")
            EnhancementResult.Failed
        }
    }

    private fun files(mode: VideoEnhancement, output: EnhancementResolution): String =
        mode.shaders.joinToString(";") { shader ->
            extracted.getOrPut(shader to output) { extract(shader, output) }.toString()
        }

    private fun extract(shader: String, output: EnhancementResolution): Path {
        val resource = requireNotNull(javaClass.getResourceAsStream("/video-enhancement/$shader"))
        val source = resource.bufferedReader().use { it.readText() }
        val text = if ("Upscale" in shader || shader.startsWith("FSRCNNX")) {
            val x = output.longSide + 1
            val y = output.shortSide + 1
            // Match landscape and portrait video output, limiting the upscaling pass as the window resizes.
            val ceiling = "OUTPUT.w $x < OUTPUT.h $y < * OUTPUT.w $y < OUTPUT.h $x < * +"
            source.lineSequence().joinToString("\n") { line ->
                if (line.startsWith("//!WHEN ")) "$line $ceiling *" else line
            }
        } else {
            source
        }
        val file = directory.resolve("${output.name}-$shader")
        require(';' !in file.toString())
        file.toFile().deleteOnExit()
        return Files.writeString(file, text)
    }
}
