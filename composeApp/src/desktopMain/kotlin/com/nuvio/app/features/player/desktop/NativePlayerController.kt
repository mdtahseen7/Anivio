package com.nuvio.app.features.player.desktop

import androidx.compose.ui.graphics.Color
import co.touchlab.kermit.Logger
import com.nuvio.app.features.player.AudioTrack
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import com.nuvio.app.features.player.PlayerResizeMode
import com.nuvio.app.features.player.SUBTITLE_DELAY_MAX_MS
import com.nuvio.app.features.player.SUBTITLE_DELAY_MIN_MS
import com.nuvio.app.features.player.SubtitleStyleState
import com.nuvio.app.features.player.SubtitleTrack
import com.nuvio.app.features.player.inferForcedSubtitleTrack
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.awt.Component
import java.util.concurrent.CountDownLatch
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.concurrent.Volatile

internal typealias NativePlayerCreate = (
    Long,
    String,
    Array<String>,
    Boolean,
    Long,
    String,
    Int,
    Boolean,
    NativePlayerEventSink,
) -> Long

internal class NativePlayerController(
    private val host: NativePlayerHost,
    private val nativeCreate: NativePlayerCreate = NativePlayerBridge::create,
    private val nativeDispose: (Long) -> Unit = NativePlayerBridge::dispose,
    private val nativeSeekTo: (Long, Long) -> Unit = NativePlayerBridge::seekTo,
    private val isHostDisplayable: () -> Boolean = { host.isDisplayable },
    private val resolveHostView: () -> Long = { AwtNativeViewResolver.resolveNativeViewPointer(host) },
    private val createWaitTimeoutMs: Long = 5_000L,
    private val releaseTimeoutMs: Long = 10_000L,
    private val onCreateWaitCompleted: () -> Unit = {},
) : PlayerEngineController {
    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val log = Logger.withTag("NativePlayerControls")

        /** Cap on waiting for the previous player's teardown so a hung one cannot block playback. */
        const val TEARDOWN_WAIT_MS = 5_000L

        @Volatile
        var rememberedVolumeLevel: Float = DesktopPlayerVolumeStorage.loadVolumeLevel() ?: 1f

        @Volatile
        var rememberedResizeMode: PlayerResizeMode = PlayerResizeMode.Fit
    }

    private data class ReleaseCallback(
        val onReleased: () -> Unit,
        val onFailed: (String) -> Unit,
    )

    private val lifecycleLock = Any()

    @Volatile
    private var handle: Long = 0L

    /** Native teardown of the previous player, if one is still running. */
    @Volatile
    private var disposeInFlight: Thread? = null

    @Volatile
    private var pendingSource: PendingSource? = null
    @Volatile
    private var releaseRequested: Boolean = false
    private val createsInFlight = mutableSetOf<Thread>()
    private var createWaitInFlight: Thread? = null
    private val releaseCallbacks = mutableListOf<ReleaseCallback>()
    private var releaseInFlight: Thread? = null
    private var releaseTimedOut: Boolean = false
    private var terminalReleaseFailure: String? = null
    @Volatile
    private var currentVolumeLevel = rememberedVolumeLevel.coerceDesktopPlayerVolumeLevel()
    private var pendingSubtitleDelayMs: Int? = null
    private var pendingSubtitleStyle: SubtitleStyleState? = null
    private val eventSink = NativePlayerEventSink { type, value ->
        SwingUtilities.invokeLater {
            handlePlayerEvent(type, value)
        }
    }

    fun attach(
        sourceUrl: String,
        sourceHeaders: Map<String, String>,
        playWhenReady: Boolean,
        initialPositionMs: Long,
        decoderPriority: Int,
        nvidiaRtxSuperResolutionEnabled: Boolean,
        onError: (String?) -> Unit,
    ) {
        val pending = PendingSource(
            sourceUrl = sourceUrl,
            headerLines = sourceHeaders.toHeaderLines(),
            playWhenReady = playWhenReady,
            initialPositionMs = initialPositionMs.coerceAtLeast(0L),
            decoderPriority = decoderPriority,
            nvidiaRtxSuperResolutionEnabled = nvidiaRtxSuperResolutionEnabled,
            onError = onError,
        )
        var terminalFailure: String? = null
        val accepted = synchronized(lifecycleLock) {
            when {
                releaseRequested -> false
                terminalReleaseFailure != null -> {
                    terminalFailure = terminalReleaseFailure
                    false
                }
                else -> {
                    pendingSource = pending
                    true
                }
            }
        }
        if (!accepted) {
            terminalFailure?.let { message -> SwingUtilities.invokeLater { pending.onError(message) } }
            return
        }
        log.d {
            "attach requested source=${sourceUrl.toPlaybackLogKey()} headers=${sourceHeaders.size} " +
                "playWhenReady=$playWhenReady initialPositionMs=$initialPositionMs decoderPriority=$decoderPriority"
        }
        host.onPeerReady = { attachPending() }
        if (isHostDisplayable()) {
            attachPending()
        }
    }

    private fun attachPending() {
        val pending = pendingSource ?: return
        SwingUtilities.invokeLater {
            val terminalFailure = synchronized(lifecycleLock) { terminalReleaseFailure }
            if (terminalFailure != null) {
                if (!releaseRequested && pendingSource === pending) pending.onError(terminalFailure)
                return@invokeLater
            }
            if (
                releaseRequested ||
                pendingSource !== pending ||
                !isHostDisplayable()
            ) {
                return@invokeLater
            }
            if (deferAttachUntilCreatesComplete()) return@invokeLater
            disposePlayerHandle()
            val teardown = disposeInFlight
            if (teardown == null || !teardown.isAlive) {
                createPlayer(pending)
                return@invokeLater
            }
            // The previous player is still tearing down natively. It owns child windows of this
            // same host, so creating the next one on top of it races its teardown and can leave the
            // new player wedged (controls never resized, playback never starts). Wait for it, but
            // off the EDT, because the teardown itself needs the EDT to keep pumping messages.
            Thread({
                runCatching { teardown.join(TEARDOWN_WAIT_MS) }
                val teardownCompleted = !teardown.isAlive
                SwingUtilities.invokeLater {
                    val terminalFailure = synchronized(lifecycleLock) { terminalReleaseFailure }
                    if (terminalFailure != null) {
                        if (!releaseRequested && pendingSource === pending) pending.onError(terminalFailure)
                        return@invokeLater
                    }
                    if (!releaseRequested && isHostDisplayable() && pendingSource === pending) {
                        if (teardownCompleted) {
                            createPlayer(pending)
                        } else {
                            log.w { "attach aborted because previous native teardown exceeded ${TEARDOWN_WAIT_MS}ms" }
                            if (!releaseRequested && pendingSource === pending) {
                                pending.onError("Previous player is still shutting down. Please try again.")
                            }
                        }
                    }
                }
            }, "nuvio-player-attach").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun deferAttachUntilCreatesComplete(): Boolean = synchronized(lifecycleLock) {
        createsInFlight.removeAll { worker -> !worker.isAlive }
        if (createsInFlight.isEmpty()) return@synchronized false
        if (createWaitInFlight?.isAlive == true) return@synchronized true
        val workers = createsInFlight.toList()
        val pendingAtWaitStart = pendingSource
        val worker = Thread({
            val deadlineNanos = System.nanoTime() + createWaitTimeoutMs * 1_000_000L
            var interrupted = false
            workers.forEach { create ->
                while (create.isAlive) {
                    val remainingNanos = deadlineNanos - System.nanoTime()
                    if (remainingNanos <= 0L) break
                    try {
                        val millis = (remainingNanos / 1_000_000L).coerceAtLeast(1L)
                        create.join(millis)
                    } catch (_: InterruptedException) {
                        interrupted = true
                    }
                }
            }
            if (interrupted) Thread.currentThread().interrupt()
            synchronized(lifecycleLock) {
                if (createWaitInFlight === Thread.currentThread()) createWaitInFlight = null
            }
            runCatching { onCreateWaitCompleted() }
            SwingUtilities.invokeLater {
                if (releaseRequested || !runCatching { isHostDisplayable() }.getOrDefault(false)) {
                    return@invokeLater
                }
                val (current, terminalFailure) = synchronized(lifecycleLock) {
                    createsInFlight.removeAll { create -> !create.isAlive }
                    pendingSource to terminalReleaseFailure
                }
                if (terminalFailure != null) {
                    current?.onError(terminalFailure)
                    return@invokeLater
                }
                val capturedCreatesRemain = workers.any { create -> create.isAlive }
                when {
                    current == null -> Unit
                    !capturedCreatesRemain -> attachPending()
                    current !== pendingAtWaitStart -> attachPending()
                    else -> current.onError("Previous player is still starting. Please try again.")
                }
            }
        }, "nuvio-player-create-wait").apply { isDaemon = true }
        createWaitInFlight = worker
        worker.start()
        true
    }

    private fun createPlayer(pending: PendingSource) {
        val terminalFailure = synchronized(lifecycleLock) { terminalReleaseFailure }
        if (terminalFailure != null) {
            if (!releaseRequested && pendingSource === pending) pending.onError(terminalFailure)
            return
        }
        // Resolving the AWT peer must happen on the EDT; everything after it must not.
        val hostViewPtr = runCatching { resolveHostView() }
            .getOrElse { error ->
                log.w(error) { "attach failed to resolve host source=${pending.sourceUrl.toPlaybackLogKey()}" }
                if (!releaseRequested && pendingSource === pending) pending.onError(error.message)
                return
            }
        val resolvedSource = if (pending.sourceUrl.startsWith("file:", ignoreCase = true)) {
            runCatching { java.io.File(java.net.URI(pending.sourceUrl)).absolutePath }.getOrElse {
                val stripped = pending.sourceUrl.replaceFirst(Regex("^file:/{1,3}", RegexOption.IGNORE_CASE), "")
                runCatching { java.net.URLDecoder.decode(stripped, "UTF-8") }.getOrDefault(stripped)
            }
        } else {
            pending.sourceUrl
        }

        // Native create blocks until the player's own UI thread finishes initialising, and that
        // thread creates child windows of the AWT host, which needs the EDT to keep pumping
        // messages. Creating on the EDT is therefore the same circular wait that the teardown had:
        // the app stops responding and Windows closes it as "stopped interacting" (Hang 1002).
        // Create off the EDT and come back to it for the parts that touch Swing state.
        val createWorker = Thread({
            try {
                runCatching {
                    // Report a missing system library (e.g. libmpv) with
                    // install instructions instead of a bare link error.
                    NativePlayerBridge.ensureLoaded()
                    nativeCreate(
                        hostViewPtr,
                        resolvedSource,
                        pending.headerLines.toTypedArray(),
                        pending.playWhenReady,
                        pending.initialPositionMs,
                        "",
                        pending.decoderPriority,
                        pending.nvidiaRtxSuperResolutionEnabled,
                        object : NativePlayerEventSink {
                            override fun onPlayerEvent(type: String, value: Double) {
                                eventSink.onPlayerEvent(type, value)
                            }

                            override fun onPlayerError(message: String) {
                                SwingUtilities.invokeLater {
                                    if (!releaseRequested && pendingSource === pending) {
                                        log.w { "Native playback failed: $message" }
                                        pending.onError(message)
                                    }
                                }
                            }
                        },
                    ).also { if (it == 0L) error("Native player did not return a handle.") }
                }.onSuccess { created ->
                    val accepted = synchronized(lifecycleLock) {
                        if (!releaseRequested && terminalReleaseFailure == null && pendingSource === pending) {
                            handle = created
                            true
                        } else {
                            false
                        }
                    }
                    if (!accepted) {
                        runCatching { nativeDispose(created) }
                            .onFailure { error -> recordTerminalDisposeFailure(error, "superseded create", created) }
                        return@onSuccess
                    }
                    SwingUtilities.invokeLater {
                        val shouldConfigure = synchronized(lifecycleLock) {
                            when {
                                handle != created -> false
                                pendingSource !== pending || !isHostDisplayable() -> {
                                    handle = 0L

                                    startTrackedDisposeLocked(created)
                                    false
                                }
                                else -> true
                            }
                        }
                        if (!shouldConfigure) return@invokeLater
                        log.d {
                            "attach created handle=$created source=${resolvedSource.toPlaybackLogKey()} " +
                                "initialPositionMs=${pending.initialPositionMs}"
                        }
                        applyRememberedVolume()
                        setResizeMode(rememberedResizeMode)
                        applyPendingSubtitleSettings()
                    }
                }.onFailure { error ->
                    log.w(error) { "attach failed source=${pending.sourceUrl.toPlaybackLogKey()}" }
                    SwingUtilities.invokeLater {
                        if (!releaseRequested && pendingSource === pending) pending.onError(error.message)
                        // A missing system library is a fatal, actionable config
                        // problem — surface it as a dialog, not just a black player
                        // (the video Canvas paints black when no surface exists).
                        if (error.message?.startsWith("Native player failed to start: missing system library") == true) {
                            runCatching {
                                javax.swing.JOptionPane.showMessageDialog(
                                    null,
                                    error.message,
                                    "Playback error",
                                    javax.swing.JOptionPane.ERROR_MESSAGE,
                                )
                            }
                        }
                    }
                }
            } finally {
                synchronized(lifecycleLock) {
                    createsInFlight.remove(Thread.currentThread())
                }
            }
        }, "nuvio-player-create").apply { isDaemon = true }
        var admissionFailure: String? = null
        val admitted = synchronized(lifecycleLock) {
            when {
                releaseRequested || pendingSource !== pending -> false
                terminalReleaseFailure != null -> {
                    admissionFailure = terminalReleaseFailure
                    false
                }
                else -> {
                    createsInFlight += createWorker
                    createWorker.start()
                    true
                }
            }
        }
        if (!admitted) {
            admissionFailure?.let { message ->
                SwingUtilities.invokeLater {
                    if (!releaseRequested && pendingSource === pending) pending.onError(message)
                }
            }
            return
        }
    }

    fun onDesktopFullscreenChanged() {
        forceHostRelayout()
    }

    /**
     * Forces the video surface to re-layout at the new window size after a
     * fullscreen toggle. Compose's placement updater can skip the layout pass
     * when the native fullscreen state and WindowState.placement disagree,
     * leaving the host Canvas at its old size while the window is fullscreen —
     * the video then renders fit inside the smaller Canvas, showing black bars
     * on the sides and bottom. Revalidating re-lays-out the SwingPanel at the
     * real size. The native fullscreen transition is async on some WMs, so
     * re-validate once more after it settles.
     */
    private fun forceHostRelayout() {
        fun relayout() {
            val window = runCatching { SwingUtilities.getWindowAncestor(host) }.getOrNull() ?: return
            window.validate()
            window.repaint()
            host.repaint()
        }
        if (SwingUtilities.isEventDispatchThread()) {
            relayout()
        } else {
            SwingUtilities.invokeLater { relayout() }
        }
        // Timer fires on the EDT.
        Timer(250) { relayout() }.apply { isRepeats = false }.start()
    }

    fun reparentSurface(host: Component): Boolean {
        val current = handle.takeIf { it != 0L } ?: return false
        if (!host.isDisplayable) return false
        val pointer = runCatching { AwtNativeViewResolver.resolveNativeViewPointer(host) }
            .onFailure { error -> log.w(error) { "failed to resolve PiP native host ${host.javaClass.name}" } }
            .getOrNull() ?: return false
        return NativePlayerBridge.reparentSurface(current, pointer)
    }

    fun setResizeMode(mode: PlayerResizeMode) {
        rememberedResizeMode = mode
        handle.takeIf { it != 0L }?.let { current ->
            NativePlayerBridge.setResizeMode(
                handle = current,
                mode = when (mode) {
                    PlayerResizeMode.Fit -> 0
                    // Native bridge: 0 = fit, 2 = crop-to-fill, 3 = stretch.
                    // Mode 1 falls through to fit in the bridge, so Fill must
                    // map to 2 or the Fill button does nothing.
                    PlayerResizeMode.Fill -> 2
                    PlayerResizeMode.Zoom -> 2
                },
            )
        }
    }

    private fun handlePlayerEvent(type: String, value: Double) {
        when (type) {
            "cursorActivity" -> host.noteCursorActivity()
            "toggleFullscreen" -> {
                toggleDesktopAppFullscreen(SwingUtilities.getWindowAncestor(host))
                onDesktopFullscreenChanged()
            }
            "scrubFinish" -> seekTo(value.toLong())
            "keyboardTogglePlayback" -> if (snapshot().isPlaying) pause() else play()
            "keyboardSeekBack" -> seekBy(-10_000)
            "keyboardSeekForward" -> seekBy(10_000)
            "keyboardVolumeDown" -> setVolumeLevel(currentVolumeLevel - 0.1f)
            "keyboardVolumeUp" -> setVolumeLevel(currentVolumeLevel + 0.1f)
        }
    }

    fun volumeLevel(): Float = currentVolumeLevel

    fun setVolumeLevel(level: Float) {
        val volume = level.coerceDesktopPlayerVolumeLevel()
        currentVolumeLevel = volume
        rememberedVolumeLevel = volume
        DesktopPlayerVolumeStorage.saveVolumeLevel(volume)
        applyRememberedVolume()
    }

    override fun setMuted(muted: Boolean) {
        handle.takeIf { it != 0L }?.let { NativePlayerBridge.setVolume(it, if (muted) 0f else currentVolumeLevel) }
    }

    private fun applyRememberedVolume() {
        handle.takeIf { it != 0L }?.let { NativePlayerBridge.setVolume(it, currentVolumeLevel) }
    }

    fun snapshot(): PlayerPlaybackSnapshot {
        val current = handle
        if (current == 0L) return PlayerPlaybackSnapshot(isLoading = true)
        return runCatching {
            val isLoading = NativePlayerBridge.isLoading(current)
            val isEnded = NativePlayerBridge.isEnded(current)
            PlayerPlaybackSnapshot(
                isLoading = isLoading,
                isPlaying = !NativePlayerBridge.isPaused(current) && !isLoading && !isEnded,
                isEnded = isEnded,
                durationMs = NativePlayerBridge.durationMs(current),
                positionMs = NativePlayerBridge.positionMs(current),
                bufferedPositionMs = NativePlayerBridge.bufferedPositionMs(current),
                playbackSpeed = NativePlayerBridge.speed(current),
            )
        }.getOrDefault(PlayerPlaybackSnapshot(isLoading = true))
    }

    fun releaseBeforeNavigation(onReleased: () -> Unit) {
        releaseBeforeNavigation(onReleased, onReleaseFailed = {})
    }

    fun releaseBeforeNavigation(
        onReleased: () -> Unit,
        onReleaseFailed: (String) -> Unit,
    ) {
        synchronized(lifecycleLock) {
            releaseRequested = true
        }
        host.resetCursorVisibility()
        invalidateForRelease()
        val callback = ReleaseCallback(onReleased, onReleaseFailed)
        var shouldStart = false
        var immediateFailure: String? = null
        val worker = synchronized(lifecycleLock) {
            val active = releaseInFlight?.takeIf { it.isAlive }
            when {
                terminalReleaseFailure != null -> {
                    immediateFailure = terminalReleaseFailure
                    active ?: Thread.currentThread()
                }
                active != null && releaseTimedOut -> {
                    immediateFailure = "Native player is still shutting down. Please retry or quit Nuvio."
                    active
                }
                else -> {
                    releaseCallbacks += callback
                    active ?: Thread({
                    awaitThreadCompletion(disposeInFlight, "native teardown")
                    awaitInFlightCreates()
                    // A create worker publishes/configures its result on the EDT before leaving
                    // createsInFlight. Release must drain those already-queued callbacks before
                    // taking the final disposal snapshot: a stale publication can reject its
                    // handle and start a tracked disposal after the create thread has exited.
                    awaitSwingCallbacksQueuedByCreates()
                    awaitThreadCompletion(disposeInFlight, "native teardown queued by create")
                    var failureMessage = synchronized(lifecycleLock) { terminalReleaseFailure }
                    val current = if (failureMessage == null) detachPlayerHandle() else 0L
                    if (current != 0L) {
                        log.d { "pre-navigation dispose started handle=$current" }
                        runCatching { nativeDispose(current) }
                            .onSuccess { log.d { "pre-navigation dispose completed handle=$current" } }
                            .onFailure { error ->
                                failureMessage = "Native player shutdown failed. Quit Nuvio before removing the player."
                                log.w(error) { "pre-navigation dispose failed handle=$current" }
                            }
                    }
                    val callbacks = synchronized(lifecycleLock) {
                        releaseInFlight = null
                        releaseTimedOut = false
                        if (failureMessage != null) terminalReleaseFailure = failureMessage
                        releaseCallbacks.toList().also { releaseCallbacks.clear() }
                    }
                    SwingUtilities.invokeLater {
                        callbacks.forEach { pending ->
                            val invoke = if (failureMessage == null) {
                                { pending.onReleased() }
                            } else {
                                { pending.onFailed(failureMessage) }
                            }
                            runCatching(invoke)
                                .onFailure { error -> log.w(error) { "release callback failed" } }
                        }
                    }
                }, "nuvio-player-release").apply {
                    isDaemon = true
                    releaseInFlight = this
                    releaseTimedOut = false
                    shouldStart = true
                }
            }
        }
        }
        if (immediateFailure != null) {
            SwingUtilities.invokeLater {
                runCatching { callback.onFailed(immediateFailure) }
                    .onFailure { error -> log.w(error) { "immediate release failure callback failed" } }
            }
            return
        }
        if (shouldStart) {
            worker.start()
            startReleaseWatchdog(worker)
        }
    }

    private fun startReleaseWatchdog(worker: Thread) {
        Thread({
            try {
                Thread.sleep(releaseTimeoutMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return@Thread
            }
            val callbacks = synchronized(lifecycleLock) {
                if (releaseInFlight !== worker || !worker.isAlive) return@synchronized emptyList()
                releaseTimedOut = true
                releaseCallbacks.toList().also { releaseCallbacks.clear() }
            }
            if (callbacks.isEmpty()) return@Thread
            SwingUtilities.invokeLater {
                callbacks.forEach { pending ->
                    runCatching {
                        pending.onFailed("Native player shutdown timed out. Please retry or quit Nuvio.")
                    }.onFailure { error -> log.w(error) { "release timeout callback failed" } }
                }
            }
        }, "nuvio-player-release-watchdog").apply {
            isDaemon = true
            start()
        }
    }

    fun dispose() {
        host.resetCursorVisibility()
        val accepted = synchronized(lifecycleLock) {
            if (releaseRequested) {
                false
            } else {
                pendingSource = null
                true
            }
        }
        if (!accepted) return
        host.onPeerReady = null
        disposePlayerHandle()
    }

    private fun cancelPendingAttachment() {
        synchronized(lifecycleLock) {
            pendingSource = null
        }
        host.onPeerReady = null
    }

    private fun invalidateForRelease() {
        cancelPendingAttachment()
        host.onCursorActivity = null
    }

    private fun awaitInFlightCreates() {
        while (true) {
            val workers = synchronized(lifecycleLock) {
                createsInFlight.removeAll { worker -> !worker.isAlive }
                createsInFlight.toList()
            }
            if (workers.isEmpty()) return
            workers.forEach { worker -> awaitThreadCompletion(worker, "native create") }
        }
    }

    private fun awaitSwingCallbacksQueuedByCreates() {
        val completed = CountDownLatch(1)
        SwingUtilities.invokeLater { completed.countDown() }
        var interrupted = false
        while (true) {
            try {
                completed.await()
                break
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt()
            log.w { "interrupted while awaiting queued native create callbacks; completion was preserved" }
        }
    }

    private fun awaitThreadCompletion(thread: Thread?, operation: String) {
        if (thread == null || thread === Thread.currentThread()) return
        var interrupted = false
        while (thread.isAlive) {
            try {
                thread.join()
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt()
            log.w { "interrupted while awaiting $operation; completion was preserved" }
        }
    }

    private fun detachPlayerHandle(): Long = synchronized(lifecycleLock) {
        val current = handle
        handle = 0L

        current
    }

    private fun recordTerminalDisposeFailure(error: Throwable, operation: String, nativeHandle: Long) {
        synchronized(lifecycleLock) {
            terminalReleaseFailure = "Native player shutdown failed. Quit Nuvio before removing the player."
        }
        log.w(error) { "$operation dispose failed handle=$nativeHandle" }
    }

    /** Must be called while holding [lifecycleLock]. */
    private fun startTrackedDisposeLocked(current: Long) {
        val prior = disposeInFlight?.takeIf { it.isAlive }
        disposeInFlight = Thread(
            {
                awaitThreadCompletion(prior, "prior native teardown")
                runCatching { nativeDispose(current) }
                    .onFailure { error -> recordTerminalDisposeFailure(error, "native", current) }
            },
            "nuvio-player-dispose",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun disposePlayerHandle() {
        synchronized(lifecycleLock) {
            if (releaseRequested) return
            val current = handle
            if (current == 0L) return
            handle = 0L

            // Register and start teardown atomically so terminal release cannot sample an empty
            // worker slot between detaching the handle and starting native disposal.
            startTrackedDisposeLocked(current)
        }
    }

    override fun play() {
        log.d { "play handle=$handle" }
        handle.takeIf { it != 0L }?.let { NativePlayerBridge.setPaused(it, false) }
    }

    override fun pause() {
        log.d { "pause handle=$handle" }
        handle.takeIf { it != 0L }?.let { NativePlayerBridge.setPaused(it, true) }
    }

    override fun seekTo(positionMs: Long) {
        log.d { "seekTo positionMs=$positionMs handle=$handle" }
        handle.takeIf { it != 0L }?.let { nativeSeekTo(it, positionMs) }
    }

    fun trySeekTo(positionMs: Long): Boolean {
        val current = handle.takeIf { it != 0L } ?: return false
        log.d { "trySeekTo positionMs=$positionMs handle=$current" }
        nativeSeekTo(current, positionMs)
        return true
    }

    override fun seekBy(offsetMs: Long) {
        log.d { "seekBy offsetMs=$offsetMs handle=$handle" }
        handle.takeIf { it != 0L }?.let { NativePlayerBridge.seekBy(it, offsetMs) }
    }

    override fun retry() {
        val pending = pendingSource ?: return
        attach(
            sourceUrl = pending.sourceUrl,
            sourceHeaders = pending.headerLines.toHeaderMap(),
            playWhenReady = pending.playWhenReady,
            initialPositionMs = pending.initialPositionMs,
            decoderPriority = pending.decoderPriority,
            nvidiaRtxSuperResolutionEnabled = pending.nvidiaRtxSuperResolutionEnabled,
            onError = pending.onError,
        )
    }

    override fun setPlaybackSpeed(speed: Float) {
        log.d { "setPlaybackSpeed speed=$speed handle=$handle" }
        handle.takeIf { it != 0L }?.let { NativePlayerBridge.setSpeed(it, speed) }
    }

    override fun getAudioTracks(): List<AudioTrack> =
        decodeTracks { NativePlayerBridge.audioTracksJson(it) }.map { track ->
            AudioTrack(
                index = track.index,
                id = track.id,
                label = track.label,
                language = track.language.takeUnless(String::isBlank),
                isSelected = track.selected,
            )
        }

    override fun getSubtitleTracks(): List<SubtitleTrack> =
        decodeTracks { NativePlayerBridge.subtitleTracksJson(it) }.map { track ->
            SubtitleTrack(
                index = track.index,
                id = track.id,
                label = track.label,
                language = track.language.takeUnless(String::isBlank),
                isSelected = track.selected,
                isForced = track.forced || inferForcedSubtitleTrack(
                    label = track.label,
                    language = track.language,
                    trackId = track.id,
                ),
            )
        }

    fun applyAudioLanguagePreferences(languages: List<String>) {
        val preferredLanguages = languages
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(String::lowercase)
        if (preferredLanguages.isEmpty()) return
        val audioTracks = getAudioTracks()
        for(preferred in preferredLanguages){
            val trackIndex = audioTracks.indexOfFirst { track ->
                val language = track.language?.lowercase() ?: return@indexOfFirst false
                language == preferred || language.startsWith("$preferred-")
            }
            if (trackIndex >= 0) {
                selectAudioTrack(trackIndex)
                return
            }
        }
    }

    override fun selectAudioTrack(index: Int) {
        val current = handle.takeIf { it != 0L } ?: return
        val tracks = decodeTracks { NativePlayerBridge.audioTracksJson(it) }
        val trackId = resolveTrackId(index, tracks) ?: run {
            log.w { "selectAudioTrack missing track index=$index count=${tracks.size} handle=$current" }
            return
        }
        log.d { "selectAudioTrack index=$index trackId=$trackId count=${tracks.size} handle=$current" }
        NativePlayerBridge.selectAudioTrack(current, trackId)
    }

    override fun selectSubtitleTrack(index: Int) {
        val current = handle.takeIf { it != 0L } ?: return
        if (index < 0) {
            log.d { "selectSubtitleTrack off handle=$current" }
            NativePlayerBridge.selectSubtitleTrack(current, -1)
            return
        }
        val tracks = decodeTracks { NativePlayerBridge.subtitleTracksJson(it) }
        val trackId = resolveTrackId(index, tracks) ?: run {
            log.w { "selectSubtitleTrack missing track index=$index count=${tracks.size} handle=$current" }
            return
        }
        log.d { "selectSubtitleTrack index=$index trackId=$trackId count=${tracks.size} handle=$current" }
        NativePlayerBridge.selectSubtitleTrack(current, trackId)
        applyPendingSubtitleSettings()
    }

    override fun setSubtitleUri(url: String) {
        log.d { "setSubtitleUri ${url.toPlaybackLogKey()} handle=$handle" }
        handle.takeIf { it != 0L }?.let { current ->
            NativePlayerBridge.clearExternalSubtitles(current)
            NativePlayerBridge.addSubtitleUrl(current, url)
        }
    }

    override fun clearExternalSubtitle() {
        log.d { "clearExternalSubtitle handle=$handle" }
        handle.takeIf { it != 0L }?.let(NativePlayerBridge::clearExternalSubtitles)
    }

    override fun clearExternalSubtitleAndSelect(trackIndex: Int) {
        val current = handle.takeIf { it != 0L } ?: return
        val trackId = if (trackIndex < 0) {
            -1
        } else {
            val tracks = decodeTracks { NativePlayerBridge.subtitleTracksJson(it) }
            resolveTrackId(trackIndex, tracks) ?: run {
                log.w { "clearExternalSubtitleAndSelect missing track index=$trackIndex count=${tracks.size} handle=$current" }
                return
            }
        }
        log.d { "clearExternalSubtitleAndSelect trackIndex=$trackIndex trackId=$trackId handle=$current" }
        NativePlayerBridge.clearExternalSubtitlesAndSelect(current, trackId)
        applyPendingSubtitleSettings()
    }

    override fun setSubtitleDelayMs(delayMs: Int) {
        val clamped = delayMs.coerceIn(SUBTITLE_DELAY_MIN_MS, SUBTITLE_DELAY_MAX_MS)
        pendingSubtitleDelayMs = clamped
        handle.takeIf { it != 0L }?.let { current ->
            NativePlayerBridge.setSubtitleDelayMs(current, clamped)
        }
    }

    override fun applySubtitleStyle(style: SubtitleStyleState) {
        pendingSubtitleStyle = style
        handle.takeIf { it != 0L }?.let { current ->
            applySubtitleStyle(current, style)
        }
    }

    private fun applyPendingSubtitleSettings() {
        val current = handle.takeIf { it != 0L } ?: return
        pendingSubtitleDelayMs?.let { delayMs ->
            NativePlayerBridge.setSubtitleDelayMs(current, delayMs)
        }
        pendingSubtitleStyle?.let { style ->
            applySubtitleStyle(current, style)
        }
    }

    private fun applySubtitleStyle(handle: Long, style: SubtitleStyleState) {
        NativePlayerBridge.applySubtitleStyle(
            handle = handle,
            textColor = style.textColor.toMpvColorString(),
            backgroundColor = style.backgroundColor.toMpvColorString(),
            outlineColor = style.outlineColor.toMpvColorString(),
            outlineSize = if (style.outlineEnabled) style.outlineWidth.toFloat() else 0f,
            bold = style.bold,
            fontSize = style.toMpvSubtitleFontSize(),
            subPos = style.toMpvSubtitlePosition(),
            useLibass = !style.overrideEmbeddedStyles,
            stripSdh = style.stripSdh,
        )
    }

    private fun decodeTracks(readJson: (Long) -> String): List<NativeMpvTrack> {
        val current = handle.takeIf { it != 0L } ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<NativeMpvTrack>>(readJson(current))
        }.getOrDefault(emptyList())
    }
}

private fun String.toPlaybackLogKey(): String {
    val scheme = substringBefore(':', missingDelimiterValue = "unknown")
        .takeIf { it.isNotBlank() }
        ?: "unknown"
    return "scheme=$scheme length=$length hash=${hashCode()}"
}

@Serializable
private data class NativeMpvTrack(
    val index: Int = 0,
    val id: String = "",
    val label: String = "",
    val language: String = "",
    val selected: Boolean = false,
    val forced: Boolean = false,
)

private fun resolveTrackId(index: Int, tracks: List<NativeMpvTrack>): Int? =
    tracks.firstNotNullOfOrNull { track ->
        if (track.index == index) {
            track.id.toIntOrNull()
        } else {
            null
        }
    } ?: tracks.getOrNull(index)?.id?.toIntOrNull()

private fun Color.toMpvColorString(): String {
    val alphaInt = (alpha * 255f).toInt().coerceIn(0, 255)
    val redInt = (red * 255f).toInt().coerceIn(0, 255)
    val greenInt = (green * 255f).toInt().coerceIn(0, 255)
    val blueInt = (blue * 255f).toInt().coerceIn(0, 255)
    return buildString {
        append('#')
        append(alphaInt.toHexByte())
        append(redInt.toHexByte())
        append(greenInt.toHexByte())
        append(blueInt.toHexByte())
    }
}

private fun SubtitleStyleState.toMpvSubtitlePosition(): Int =
    (100 - (bottomOffset / 2)).coerceIn(0, 150)

private fun SubtitleStyleState.toMpvSubtitleFontSize(): Float =
    (fontSizeSp * 3f).coerceIn(18f, 96f)

private fun Int.toHexByte(): String {
    val digits = "0123456789ABCDEF"
    val value = coerceIn(0, 255)
    return buildString {
        append(digits[value / 16])
        append(digits[value % 16])
    }
}

private data class PendingSource(
    val sourceUrl: String,
    val headerLines: List<String>,
    val playWhenReady: Boolean,
    val initialPositionMs: Long,
    val decoderPriority: Int,
    val nvidiaRtxSuperResolutionEnabled: Boolean,
    val onError: (String?) -> Unit,
)

private fun Map<String, String>.toHeaderLines(): List<String> =
    entries.mapNotNull { (key, value) ->
        val cleanKey = key.trim()
        val cleanValue = value.trim()
        if (cleanKey.isBlank() || cleanValue.isBlank()) {
            null
        } else {
            "$cleanKey: $cleanValue"
        }
    }

private fun List<String>.toHeaderMap(): Map<String, String> =
    mapNotNull { line ->
        val separator = line.indexOf(':')
        if (separator <= 0) return@mapNotNull null
        line.substring(0, separator).trim() to line.substring(separator + 1).trim()
    }.toMap()
