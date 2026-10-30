package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.nuvio.app.core.sync.AppForegroundMonitor
import com.nuvio.app.core.sync.AppVisibility
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.party.ActiveParty
import com.nuvio.app.features.party.AvatarStack
import com.nuvio.app.features.party.PartyAction
import com.nuvio.app.features.party.PartyChat
import com.nuvio.app.features.party.PartyCorrection
import com.nuvio.app.features.party.PartyEvent
import com.nuvio.app.features.party.PartyMedia
import com.nuvio.app.features.party.PartyMember
import com.nuvio.app.features.party.PartyMemberStatus
import com.nuvio.app.features.party.PartyNotice
import com.nuvio.app.features.party.PartyPanel
import com.nuvio.app.features.party.PartyPlayback
import com.nuvio.app.features.party.PartyRepository
import com.nuvio.app.features.party.PartySource
import com.nuvio.app.features.party.PartyStream
import com.nuvio.app.features.party.PartySync
import com.nuvio.app.features.party.UnreadBadge
import com.nuvio.app.features.party.isShareableStreamUrl
import com.nuvio.app.features.party.messageRes
import com.nuvio.app.features.party.partyMediaKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.party_action_paused
import nuvio.composeapp.generated.resources.party_action_resumed
import nuvio.composeapp.generated.resources.party_action_seeked
import nuvio.composeapp.generated.resources.party_action_speed
import nuvio.composeapp.generated.resources.party_behind
import nuvio.composeapp.generated.resources.party_caught_up
import nuvio.composeapp.generated.resources.party_host_only_note
import nuvio.composeapp.generated.resources.party_paused_for_you
import nuvio.composeapp.generated.resources.party_reconnecting
import nuvio.composeapp.generated.resources.party_waiting
import nuvio.composeapp.generated.resources.party_waiting_for
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal val PlayerScreenRuntime.partyKey: String
    get() = partyMediaKey(parentMetaType, parentMetaId, activeSeasonNumber, activeEpisodeNumber)

internal val PlayerScreenRuntime.isPartySynced: Boolean
    get() = PartyRepository.state.value.party?.media?.key == partyKey

internal val PlayerScreenRuntime.isPartyControlLocked: Boolean
    get() = PartyRepository.state.value.party?.let { party -> !party.canControl && party.media.key == partyKey } == true

internal fun PlayerScreenRuntime.partyBlocksControl(): Boolean {
    if (!isPartyControlLocked) return false
    scope.launch { showGestureMessage(getString(Res.string.party_host_only_note)) }
    return true
}

internal fun PlayerScreenRuntime.openPartyPanel() {
    if (PartyChat.unread.value > 0) showPartyChat = true
    showPartyPanel = true
    controlsVisible = false
}

internal fun PlayerScreenRuntime.ownSeekTarget(targetMs: Long): Long? {
    if (!isPartySynced) return targetMs
    if (partyBlocksControl()) return null
    val durationMs = playbackSnapshot.durationMs
    val positionMs = targetMs
        .coerceAtLeast(0L)
        .let { if (durationMs > 0L) it.coerceAtMost(durationMs) else it }
    partyBehind = false
    partyBehindMs = null
    publishPartyPlayback(positionMs = positionMs)
    return positionMs
}

internal fun PlayerScreenRuntime.currentPartyMedia(): PartyMedia = PartyMedia(
    type = contentType ?: parentMetaType,
    metaId = parentMetaId,
    metaType = parentMetaType,
    videoId = activeVideoId,
    season = activeSeasonNumber,
    episode = activeEpisodeNumber,
    title = title,
    episodeTitle = activeEpisodeTitle,
    description = activePauseDescription?.take(DESCRIPTION_LIMIT),
    poster = poster,
    background = background,
    logo = logo,
    source = PartySource(
        infoHash = activeTorrentInfoHash,
        fileIdx = activeTorrentFileIdx,
        filename = activeTorrentFilename,
        bingeGroup = currentStreamBingeGroup,
        addonId = activeProviderAddonId,
        name = activeStreamTitle,
    ),
)

internal fun PlayerScreenRuntime.currentPartyStream(): PartyStream? {
    if (!isShareableStreamUrl(activeSourceUrl) || activeSourceAudioUrl != null) return null
    return PartyStream(
        mediaKey = partyKey,
        url = activeSourceUrl,
        headers = activeSourceHeaders,
        responseHeaders = activeSourceResponseHeaders,
        name = activeStreamTitle,
        description = activeStreamSubtitle,
        addonName = activeProviderName,
        addonId = activeProviderAddonId.orEmpty(),
        filename = activeTorrentFilename,
        bingeGroup = currentStreamBingeGroup,
        streamType = activeStreamType,
    )
}

internal fun PlayerScreenRuntime.partyBaseRate(): Float =
    PartyRepository.playback.value?.rate ?: playbackSnapshot.playbackSpeed

internal fun PlayerScreenRuntime.currentPlaybackPositionMs(): Long {
    val snapshot = playbackSnapshot
    if (!snapshot.isPlaying || snapshot.isLoading) return snapshot.positionMs
    val elapsedMs = playbackSnapshotMark?.elapsedNow()?.inWholeMilliseconds?.coerceIn(0L, 1_000L) ?: 0L
    return snapshot.positionMs + (elapsedMs * snapshot.playbackSpeed).toLong()
}

internal fun PlayerScreenRuntime.publishPartyPlayback(
    positionMs: Long = currentPlaybackPositionMs(),
    rate: Float = partyBaseRate(),
) {
    if (!isPartySynced) return
    PartyRepository.publishPlayback(paused = !shouldPlay, positionMs = positionMs, rate = rate)
}

internal fun PlayerScreenRuntime.startParty() {
    scope.launch {
        val error = PartyRepository.create(currentPartyMedia())
        if (error != null) {
            NuvioToastController.show(getString(error.messageRes))
            return@launch
        }
        // Seed the room immediately so a participant joining right after creation receives the
        // host's current play/pause state and position instead of starting from zero.
        publishPartyPlayback()
    }
}

private fun PlayerScreenRuntime.applyPartyPaused(paused: Boolean, force: Boolean) {
    if (!force && shouldPlay == !paused) return
    shouldPlay = !paused
    if (paused) playerController?.pause() else playerController?.play()
}

@Composable
internal fun PlayerScreenRuntime.BindPartyEffects() {
    val partyState by PartyRepository.state.collectAsState()
    val partyId = partyState.party?.id
    val key = partyKey
    val sharing = partyState.party?.let { it.isHost && it.sharing } == true
    val sharedStream = if (sharing) currentPartyStream() else null

    LaunchedEffect(sharing, sharedStream) {
        if (sharing) PartyRepository.shareStream(sharedStream)
    }
    LaunchedEffect(
        partyId,
        key,
        initialLoadCompleted,
        playbackSnapshot.isLoading,
        errorMessage,
        partyBehind,
        partyBehindMs,
    ) {
        val party = PartyRepository.state.value.party ?: return@LaunchedEffect
        val status = when {
            party.media.key != key -> PartyMemberStatus.Ready
            partyBehind -> PartyMemberStatus.Behind
            errorMessage != null || !initialLoadCompleted -> PartyMemberStatus.Loading
            playbackSnapshot.isLoading -> PartyMemberStatus.Buffering
            else -> PartyMemberStatus.Ready
        }
        PartyRepository.setLocalStatus(
            status = status,
            mediaKey = key,
            behindMs = partyBehindMs.takeIf { partyBehind },
        )
    }
    LaunchedEffect(Unit) {
        PartyRepository.events.collect { event ->
            if (event is PartyEvent.MediaChanged && event.media.key != partyKey) {
                showPartyPanel = true
            }
        }
    }
    LaunchedEffect(Unit) {
        AppForegroundMonitor.events().collect { partyAppInBackground = it == AppVisibility.Background }
    }
    LaunchedEffect(partyId) {
        if (partyId == null) { clearPartyNudge(); return@LaunchedEffect }
        val clock = TimeSource.Monotonic
        var lastSeq: Pair<Long, String?>? = null
        var lastCorrection: TimeMark? = null
        while (isActive) {
            delay(PARTY_TICK)
            val party = PartyRepository.state.value.party ?: break
            val snapshot = playbackSnapshot
            val playback = PartyRepository.playback.value ?: continue
            val controller = playerController ?: continue
            if (party.media.key != partyKey || errorMessage != null || partyAppInBackground) continue
            val expected = PartySync.expectedPositionMs(playback, PartyRepository.nowMs(), snapshot.durationMs)
            val identity = playback.seq to playback.memberId
            if (identity != lastSeq) {
                lastSeq = identity
                applyPartyPaused(paused = playback.paused, force = true)
                if (abs(snapshot.positionMs - expected) > PartySync.PAUSED_TOLERANCE_MS) controller.seekTo(expected)
                if (abs(snapshot.playbackSpeed - playback.rate) > 0.01f) controller.setPlaybackSpeed(playback.rate)
                continue
            }
            if (snapshot.isLoading || isScrubbingTimeline || snapshot.isEnded) continue
            val due = lastCorrection?.let { it.elapsedNow().inWholeMilliseconds >= CORRECTION_INTERVAL } ?: true
            if (!due) continue
            lastCorrection = clock.markNow()
            when (val correction = PartySync.correction(snapshot.positionMs, expected, playback, partyRateNudge != null)) {
                is PartyCorrection.Seek -> controller.seekTo(correction.positionMs)
                is PartyCorrection.Nudge -> {
                    partyRateNudge = correction.rate
                    controller.setPlaybackSpeed(correction.rate)
                }
                PartyCorrection.InSync -> clearPartyNudge(playback)
            }
        }
        clearPartyNudge()
    }
}

private fun PlayerScreenRuntime.clearPartyNudge(playback: PartyPlayback? = PartyRepository.playback.value) {
    if (partyRateNudge == null) return
    partyRateNudge = null
    playerController?.setPlaybackSpeed(playback?.rate ?: 1f)
}

@Composable
internal fun BoxScope.RenderParty(runtime: PlayerScreenRuntime) {
    val partyState by PartyRepository.state.collectAsStateWithLifecycle()
    val playback by PartyRepository.playback.collectAsStateWithLifecycle()
    val unread by PartyChat.unread.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<PartyNotice?>(null) }
    LaunchedEffect(Unit) {
        PartyRepository.notices.collectLatest { next ->
            notice = next
            delay(NOTICE_DURATION)
            notice = null
        }
    }
    val party = partyState.party
    runtime.run {
        if (party != null) {
            PartyStatusPill(
                visible = (controlsVisible || !party.connected || notice != null || partyBehind) && !playerControlsLocked && !showPartyPanel,
                members = party.members,
                unread = unread,
                message = when {
                    !party.connected -> stringResource(Res.string.party_reconnecting)
                    notice != null -> notice!!.text()
                    partyBehind -> partyBehindMs?.let { stringResource(Res.string.party_behind, formatPlaybackTime(it)) }
                    playback?.waiting == true -> stringResource(Res.string.party_waiting)
                    else -> null
                },
                onClick = { openPartyPanel() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeContent.only(WindowInsetsSides.Top))
                    .padding(top = metrics.verticalPadding / 4 + 8.dp),
            )
        }
        PartyPanel(
            visible = showPartyPanel,
            state = partyState,
            showChat = showPartyChat,
            onShowChatChange = { showPartyChat = it },
            playerMedia = currentPartyMedia(),
            isSupported = PartyRepository.available.collectAsStateWithLifecycle().value,
            canStart = PartyRepository.canUse,
            onStart = { startParty() },
            onSwitchParty = { PartyRepository.publishMedia(currentPartyMedia(), playbackSnapshot.positionMs) },
            onWatchWithParty = { party?.media?.let { showPartyPanel = false; args.onOpenPartyMedia(it) } },
            onLeave = { showPartyPanel = false; PartyRepository.leave() },
            onEnd = { showPartyPanel = false; PartyRepository.end() },
            onRemove = { PartyRepository.removeMember(it.id) },
            onHostOnlyChange = PartyRepository::setHostOnly,
            canShareStream = currentPartyStream() != null,
            onSharingChange = PartyRepository::setSharing,
            behindMs = partyBehindMs.takeIf { partyBehind },
            onCatchUp = { partyBehind = false; showPartyPanel = false },
            onDismiss = { showPartyPanel = false },
        )
    }
}

@Composable
private fun PartyNotice.text(): String = when (action) {
    PartyAction.Paused -> stringResource(Res.string.party_action_paused, name)
    PartyAction.Resumed -> stringResource(Res.string.party_action_resumed, name)
    PartyAction.Seeked -> stringResource(Res.string.party_action_seeked, name, formatPlaybackTime(positionMs))
    PartyAction.Speed -> stringResource(Res.string.party_action_speed, name, formatPlaybackSpeedLabel(rate))
}

@Composable
private fun PartyStatusPill(
    visible: Boolean,
    members: List<PartyMember>,
    unread: Int,
    message: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Row(
            modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).animateContentSize().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarStack(members = members, size = 28.dp, ringColor = Color.Black.copy(alpha = 0.5f))
            UnreadBadge(count = unread, icon = true)
            AnimatedContent(
                targetState = message,
                transitionSpec = { (fadeIn(tween(200)) + slideInVertically(tween(240)) { it / 2 }) togetherWith (fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 2 }) },
            ) { text ->
                if (text != null) Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge.copy(shadow = Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 12f)),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val partyLog = Logger.withTag("Party")
private val PARTY_TICK = 250.milliseconds
private val NOTICE_DURATION = 3_000.milliseconds
private const val CORRECTION_INTERVAL = 500L
private const val DESCRIPTION_LIMIT = 600
