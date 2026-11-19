package com.nuvio.app.features.notifications

import co.touchlab.kermit.Logger
import com.nuvio.app.core.deeplink.buildMetaDeepLinkUrl
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.concurrent.Volatile
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import kotlinx.serialization.json.Json

object EpisodeReleaseNotificationsRepository {
    private const val metadataFetchConcurrency = 4
    private const val testNotificationDelaySeconds = 1L

    private val log = Logger.withTag("EpisodeReleaseNotifications")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val refreshMutex = Mutex()

    private val _uiState = MutableStateFlow(EpisodeReleaseNotificationsUiState())
    val uiState: StateFlow<EpisodeReleaseNotificationsUiState> = _uiState.asStateFlow()

    @Volatile
    private var hasLoaded = false
    @Volatile
    private var trackedShowsByKey: Map<String, TrackedFollowedShow> = emptyMap()

    /**
     * Whether the user asked to be notified about this show.
     *
     * Reads straight off the tracked map rather than the ui state, so a caller that has not observed
     * the flow yet still gets the truth.
     */
    fun isSubscribed(contentType: String, contentId: String): Boolean =
        trackedShowsByKey.containsKey(buildTrackedShowKey(contentType, contentId))

    /**
     * Subscribes or unsubscribes a single show.
     *
     * Subscribing turns the feature on and asks for the notification permission if needed, because
     * tapping "Notify me" on a show is an unambiguous request for exactly that — making the user then
     * find a master switch in Settings would be a dead end.
     */
    fun setSubscribed(
        contentType: String,
        contentId: String,
        subscribed: Boolean,
        title: String? = null,
        posterUrl: String? = null,
        backdropUrl: String? = null,
    ) {
        ensureLoaded()
        val key = buildTrackedShowKey(contentType, contentId)
        val alreadySubscribed = trackedShowsByKey.containsKey(key)
        if (alreadySubscribed == subscribed) return

        trackedShowsByKey = if (subscribed) {
            trackedShowsByKey + (
                key to TrackedFollowedShow(
                    contentId = contentId,
                    contentType = contentType,
                    // Only episodes airing from now on are of interest; back-catalogue air dates
                    // would otherwise all qualify and schedule nothing but noise.
                    followedOnIsoDate = CurrentDateProvider.todayIsoDate(),
                    title = title,
                    posterUrl = posterUrl,
                    backdropUrl = backdropUrl,
                )
                )
        } else {
            trackedShowsByKey - key
        }
        publishSubscriptionState()
        persist()

        scope.launch {
            if (subscribed && !_uiState.value.isEnabled) {
                // Routes through the same permission flow the settings switch uses.
                enableAndSchedule()
            } else {
                refreshScheduledNotifications()
            }
        }
    }

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
        scope.launch {
            syncAuthorizationState(refreshIfEnabled = true)
        }
    }

    fun onProfileChanged() {
        loadFromDisk()
        scope.launch {
            syncAuthorizationState(refreshIfEnabled = true)
        }
    }

    fun clearLocalState() {
        hasLoaded = false
        trackedShowsByKey = emptyMap()
        _uiState.value = EpisodeReleaseNotificationsUiState()
        scope.launch {
            runCatching { EpisodeReleaseNotificationPlatform.clearScheduledEpisodeReleaseNotifications() }
                .onFailure { error ->
                    log.w { "Failed to clear scheduled episode release notifications: ${error.message}" }
                }
        }
    }

    internal fun applyFromSyncEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.isEnabled == enabled) return

        _uiState.value = _uiState.value.copy(
            isEnabled = enabled,
            isLoading = false,
            isSendingTest = false,
            statusMessage = null,
            errorMessage = null,
        )
        persist()

        scope.launch {
            refreshScheduledNotifications()
        }
    }

    fun setEnabled(enabled: Boolean) {
        ensureLoaded()
        scope.launch {
            if (!enabled) {
                runCatching { EpisodeReleaseNotificationPlatform.clearScheduledEpisodeReleaseNotifications() }
                    .onFailure { error ->
                        log.w { "Failed to clear episode release notifications: ${error.message}" }
                    }
                _uiState.value = _uiState.value.copy(
                    isEnabled = false,
                    isLoading = false,
                    scheduledCount = 0,
                    statusMessage = null,
                    errorMessage = null,
                )
                persist()
                return@launch
            }

            enableAndSchedule()
        }
    }

    /** Requests the permission, flips the master switch on and schedules. */
    private suspend fun enableAndSchedule() {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null,
        )

        val granted = runCatching { EpisodeReleaseNotificationPlatform.requestAuthorization() }
            .onFailure { error ->
                log.e(error) { "Failed to request episode release notification permission" }
            }
            .getOrDefault(false)

        if (!granted) {
            _uiState.value = _uiState.value.copy(
                isEnabled = false,
                isLoading = false,
                permissionGranted = false,
                scheduledCount = 0,
                statusMessage = null,
                errorMessage = getString(Res.string.settings_notifications_permission_disabled),
            )
            persist()
            return
        }

        _uiState.value = _uiState.value.copy(
            isEnabled = true,
            isLoading = false,
            permissionGranted = true,
            statusMessage = null,
            errorMessage = null,
        )
        persist()
        refreshScheduledNotifications()
    }

    fun sendTestNotification() {
        ensureLoaded()
        scope.launch {
            val target = currentTestTarget()
            if (target == null) {
                _uiState.value = _uiState.value.copy(
                    isSendingTest = false,
                    statusMessage = null,
                    errorMessage = getString(Res.string.settings_notifications_test_requires_saved_show),
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isSendingTest = true,
                statusMessage = null,
                errorMessage = null,
            )

            val granted = runCatching { EpisodeReleaseNotificationPlatform.requestAuthorization() }
                .onFailure { error ->
                    log.e(error) { "Failed to request permission for test notification" }
                }
                .getOrDefault(false)

            if (!granted) {
                _uiState.value = _uiState.value.copy(
                    isSendingTest = false,
                    permissionGranted = false,
                    statusMessage = null,
                    errorMessage = getString(Res.string.settings_notifications_permission_disabled),
                )
                return@launch
            }

            val request = EpisodeReleaseNotificationRequest(
                requestId = "episode-release-test-${ProfileRepository.activeProfileId}-${EpisodeReleaseDatePlatform.nowEpochMs()}",
                notificationTitle = target.name,
                notificationBody = getString(Res.string.notifications_test_preview_body),
                releaseDateIso = CurrentDateProvider.todayIsoDate(),
                deepLinkUrl = buildMetaDeepLinkUrl(type = target.type, id = target.id),
                backdropUrl = target.backdropUrl,
            )

            runCatching {
                EpisodeReleaseNotificationPlatform.showTestNotification(request)
            }.onFailure { error ->
                log.e(error) { "Failed to send test notification" }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isSendingTest = false,
                    permissionGranted = true,
                    statusMessage = getString(Res.string.notifications_test_sent_for, target.name),
                    errorMessage = null,
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isSendingTest = false,
                    permissionGranted = true,
                    statusMessage = null,
                    errorMessage = getString(Res.string.notifications_test_send_failed),
                )
            }
        }
    }

    fun refreshAsync() {
        ensureLoaded()
        scope.launch {
            refreshScheduledNotifications()
        }
    }

    private fun loadFromDisk() {
        hasLoaded = true

        val payload = EpisodeReleaseNotificationsStorage.loadPayload().orEmpty().trim()
        val stored = payload.takeIf { it.isNotEmpty() }
            ?.let { rawPayload ->
                runCatching {
                    json.decodeFromString<StoredEpisodeReleaseNotificationsPayload>(rawPayload)
                }.onFailure { error ->
                    log.w { "Failed to decode episode release notifications payload: ${error.message}" }
                }.getOrNull()
            }

        trackedShowsByKey = buildMap {
            stored?.followedShows.orEmpty().forEach { trackedShow ->
                put(buildTrackedShowKey(trackedShow.contentType, trackedShow.contentId), trackedShow)
            }
        }

        _uiState.value = EpisodeReleaseNotificationsUiState(
            isEnabled = stored?.enabled ?: false,
            permissionGranted = false,
            scheduledCount = 0,
            testTargetTitle = null,
            errorMessage = null,
        )
        // Anything already on disk was written by the old library-wide reconcile. Treating it as the
        // starting subscription set means an upgrade keeps notifying about the same shows instead of
        // silently going quiet.
        publishSubscriptionState()
    }

    private fun persist() {
        EpisodeReleaseNotificationsStorage.savePayload(
            json.encodeToString(
                StoredEpisodeReleaseNotificationsPayload(
                    enabled = _uiState.value.isEnabled,
                    followedShows = trackedShowsByKey.values
                        .sortedWith(compareBy(TrackedFollowedShow::contentType, TrackedFollowedShow::contentId)),
                ),
            ),
        )
    }

    private suspend fun syncAuthorizationState(refreshIfEnabled: Boolean) {
        val granted = runCatching { EpisodeReleaseNotificationPlatform.notificationsAuthorized() }
            .onFailure { error ->
                log.w { "Failed to read episode release notification permission: ${error.message}" }
            }
            .getOrDefault(false)

        _uiState.value = _uiState.value.copy(
            permissionGranted = granted,
            testTargetTitle = currentTestTarget()?.name,
            errorMessage = when {
                _uiState.value.isEnabled && !granted -> runBlocking { getString(Res.string.settings_notifications_permission_disabled) }
                else -> _uiState.value.errorMessage
            },
        )

        if (refreshIfEnabled && _uiState.value.isEnabled) {
            refreshScheduledNotifications()
        }
    }

    private fun publishSubscriptionState() {
        _uiState.value = _uiState.value.copy(
            subscribedShowKeys = trackedShowsByKey.keys.toSet(),
            subscribedShowCount = trackedShowsByKey.size,
            testTargetTitle = currentTestTarget()?.name,
        )
    }

    private suspend fun refreshScheduledNotifications() {
        refreshMutex.withLock {
            val permissionGranted = runCatching { EpisodeReleaseNotificationPlatform.notificationsAuthorized() }
                .onFailure { error ->
                    log.w { "Failed to refresh episode release notification permission: ${error.message}" }
                }
                .getOrDefault(false)

            if (!_uiState.value.isEnabled || !permissionGranted) {
                runCatching { EpisodeReleaseNotificationPlatform.clearScheduledEpisodeReleaseNotifications() }
                    .onFailure { error ->
                        log.w { "Failed to clear scheduled episode release notifications: ${error.message}" }
                    }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    permissionGranted = permissionGranted,
                    scheduledCount = 0,
                    testTargetTitle = currentTestTarget()?.name,
                    errorMessage = if (_uiState.value.isEnabled && !permissionGranted) {
                        runBlocking { getString(Res.string.settings_notifications_permission_disabled) }
                    } else {
                        null
                    },
                )
                return
            }

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                permissionGranted = true,
                errorMessage = null,
            )

            if (trackedShowsByKey.isEmpty()) {
                runCatching { EpisodeReleaseNotificationPlatform.clearScheduledEpisodeReleaseNotifications() }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    scheduledCount = 0,
                    testTargetTitle = currentTestTarget()?.name,
                    errorMessage = null,
                )
                return
            }

            AddonRepository.initialize()
            withTimeoutOrNull(10_000L) {
                AddonRepository.awaitManifestsLoaded()
            }

            val semaphore = Semaphore(metadataFetchConcurrency)
            val requests = trackedShowsByKey.values.map { trackedShow ->
                scope.async {
                    semaphore.withPermit {
                        buildRequestsForShow(trackedShow)
                    }
                }
            }.awaitAll().flatten()

            runCatching {
                EpisodeReleaseNotificationPlatform.scheduleEpisodeReleaseNotifications(requests)
            }.onFailure { error ->
                log.e(error) { "Failed to schedule episode release notifications" }
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                permissionGranted = true,
                scheduledCount = requests.size,
                testTargetTitle = currentTestTarget()?.name,
                errorMessage = null,
            )
        }
    }

    private data class NotificationTestTarget(
        val name: String,
        val type: String,
        val id: String,
        val backdropUrl: String?,
    )

    /**
     * What "Send test notification" previews.
     *
     * A subscribed show first, since that is what the user will actually receive. Falls back to a
     * library series so the button still demonstrates something before anything is subscribed.
     */
    private fun currentTestTarget(): NotificationTestTarget? {
        trackedShowsByKey.values.firstOrNull()?.let { tracked ->
            return NotificationTestTarget(
                name = tracked.title?.takeIf { it.isNotBlank() } ?: tracked.contentId,
                type = tracked.contentType,
                id = tracked.contentId,
                backdropUrl = tracked.backdropUrl ?: tracked.posterUrl,
            )
        }

        LibraryRepository.ensureLoaded()
        val libraryItems = LibraryRepository.uiState.value.items
        val item = libraryItems.firstOrNull { isSeriesLibraryType(it.type) }
            ?: libraryItems.firstOrNull()
            ?: return null
        return NotificationTestTarget(
            name = item.name,
            type = item.type,
            id = item.id,
            backdropUrl = item.banner ?: item.poster,
        )
    }

    private suspend fun buildRequestsForShow(trackedShow: TrackedFollowedShow): List<EpisodeReleaseNotificationRequest> {
        val meta = runCatching {
            MetaDetailsRepository.fetch(
                type = trackedShow.contentType,
                id = trackedShow.contentId,
                cacheResult = false,
            )
        }.onFailure { error ->
            log.w { "Failed to resolve metadata for ${trackedShow.contentType}:${trackedShow.contentId}: ${error.message}" }
        }.getOrNull() ?: return emptyList()

        val showTitle = meta.name.ifBlank { trackedShow.title ?: trackedShow.contentId }
        val nowEpochMs = EpisodeReleaseDatePlatform.nowEpochMs()
        return meta.videos.mapNotNull { episode ->
            // An exact broadcast time is authoritative and needs no date comparison — if it is still
            // in the future it is worth scheduling, whatever the calendar says about time zones.
            val airingAt = episode.airingAtEpochMs?.takeIf { it > nowEpochMs }
            val releaseDate = releaseDateIso(episode.released)
                ?: airingAt?.let(EpisodeReleaseNotificationsClock::isoDateFromEpochMs)
                ?: return@mapNotNull null
            if (airingAt == null && releaseDate < trackedShow.followedOnIsoDate) return@mapNotNull null
            if (episode.season == null && episode.episode == null) return@mapNotNull null

            EpisodeReleaseNotificationRequest(
                requestId = buildEpisodeReleaseNotificationId(
                    profileId = ProfileRepository.activeProfileId,
                    contentType = trackedShow.contentType,
                    contentId = trackedShow.contentId,
                    episodeId = episode.id,
                    releaseDateIso = releaseDate,
                ),
                notificationTitle = showTitle,
                notificationBody = buildEpisodeReleaseNotificationBody(
                    seasonNumber = episode.season,
                    episodeNumber = episode.episode,
                    episodeTitle = episode.title,
                ),
                releaseDateIso = releaseDate,
                deepLinkUrl = buildMetaDeepLinkUrl(
                    type = trackedShow.contentType,
                    id = trackedShow.contentId,
                ),
                backdropUrl = meta.background
                    ?: episode.thumbnail
                    ?: episode.seasonPoster
                    ?: meta.poster
                    ?: trackedShow.backdropUrl
                    ?: trackedShow.posterUrl,
                airingAtEpochMs = airingAt,
            )
        }
    }
}
