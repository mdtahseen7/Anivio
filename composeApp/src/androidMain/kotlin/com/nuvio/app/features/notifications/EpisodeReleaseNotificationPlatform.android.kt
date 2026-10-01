package com.nuvio.app.features.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.settings.AppIconPlatform
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal actual object EpisodeReleaseNotificationPlatform {
    private const val permissionRequestCode = 4607
    private const val platformPreferencesName = "nuvio_episode_release_notifications_platform"
    private const val scheduledIdsKey = "scheduled_episode_release_ids"
    private const val workTag = "episode_release_notifications"
    internal const val channelId = "episode_release_notifications"
    internal const val workerRequestIdKey = "request_id"
    internal const val workerTitleKey = "title"
    internal const val workerBodyKey = "body"
    internal const val workerDeepLinkKey = "deep_link"
    internal const val workerBackdropUrlKey = "backdrop_url"
    internal const val alarmAction = "com.nuvio.app.features.notifications.EPISODE_RELEASE_ALARM"
    private const val alarmEntriesKey = "exact_alarm_entries"

    private var appContext: Context? = null
    private var currentActivity: ComponentActivity? = null
    private var pendingPermissionContinuation: kotlin.coroutines.Continuation<Boolean>? = null
    private val alarmJson = Json { ignoreUnknownKeys = true }

    /**
     * Everything an exact alarm needs to re-arm itself after a reboot, persisted because
     * AlarmManager alarms don't survive one. Kept deliberately independent of the app's
     * storage layer so the boot receiver can re-schedule without initializing it.
     */
    @Serializable
    private data class PersistedAlarmEntry(
        val requestId: String,
        val profileId: String,
        val triggerAtEpochMs: Long,
        val title: String,
        val body: String,
        val deepLink: String,
        val backdropUrl: String? = null,
    )
    private val httpClient by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        ensureNotificationChannel()
    }

    fun bindActivity(activity: ComponentActivity) {
        currentActivity = activity
    }

    fun unbindActivity(activity: ComponentActivity) {
        if (currentActivity === activity) {
            currentActivity = null
        }
    }

    fun handlePermissionRequestResult(
        requestCode: Int,
        grantResults: IntArray,
    ): Boolean {
        if (requestCode != permissionRequestCode) return false
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        pendingPermissionContinuation?.resume(granted)
        pendingPermissionContinuation = null
        return true
    }

    actual suspend fun notificationsAuthorized(): Boolean {
        val context = appContext ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionState = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            )
            if (permissionState != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    actual suspend fun requestAuthorization(): Boolean {
        val context = appContext ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ensureNotificationChannel()
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        val permissionState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            ensureNotificationChannel()
            return true
        }

        val activity = currentActivity ?: return false
        return suspendCancellableCoroutine { continuation ->
            pendingPermissionContinuation = continuation
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                permissionRequestCode,
            )
        }
    }

    actual suspend fun scheduleEpisodeReleaseNotifications(requests: List<EpisodeReleaseNotificationRequest>) {
        val context = appContext ?: return
        ensureNotificationChannel()

        withContext(Dispatchers.IO) {
            val workManager = WorkManager.getInstance(context)
            cancelTrackedWork(workManager)
            cancelPersistedAlarms(context)

            // WorkManager's setInitialDelay is a *minimum* delay: Doze, app standby and OEM
            // battery "optimizations" routinely push episode alerts minutes-to-hours late.
            // Prefer an exact alarm so the notification fires at the broadcast instant;
            // fall back to WorkManager when the user hasn't granted "Alarms & reminders".
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val useExactAlarms = alarmManager != null && exactAlarmsAllowed()

            val nowEpochMs = System.currentTimeMillis()
            val profileId = ProfileRepository.activeProfileId.toString()
            val scheduledIds = mutableListOf<String>()
            val alarmEntries = mutableListOf<PersistedAlarmEntry>()

            requests.forEach { request ->
                // The published broadcast instant when there is one, otherwise the morning of the
                // air date. For anime the former is the only correct choice: a 23:30 JST episode is
                // already "tomorrow" in Japan and still "today" in Europe.
                val triggerAtEpochMs = request.airingAtEpochMs
                    ?: triggerAtEpochMs(request.releaseDateIso)
                    ?: return@forEach
                val initialDelayMs = triggerAtEpochMs - nowEpochMs
                if (initialDelayMs <= 0L) return@forEach

                var scheduledExact = false
                if (useExactAlarms && alarmManager != null) {
                    // Guarded by exactAlarmsAllowed(), but the grant can be revoked between the
                    // check and the call — fall back to WorkManager instead of crashing.
                    scheduledExact = runCatching {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtEpochMs,
                            alarmPendingIntent(
                                context = context,
                                requestId = request.requestId,
                                title = request.notificationTitle,
                                body = request.notificationBody,
                                deepLink = request.deepLinkUrl,
                                backdropUrl = request.backdropUrl,
                            ),
                        )
                        true
                    }.getOrDefault(false)
                }

                if (scheduledExact) {
                    alarmEntries += PersistedAlarmEntry(
                        requestId = request.requestId,
                        profileId = profileId,
                        triggerAtEpochMs = triggerAtEpochMs,
                        title = request.notificationTitle,
                        body = request.notificationBody,
                        deepLink = request.deepLinkUrl,
                        backdropUrl = request.backdropUrl,
                    )
                } else {
                    enqueueWorker(workManager, request, initialDelayMs)
                }

                scheduledIds += request.requestId
            }

            writeAlarmEntries(context, alarmEntries)
            preferences(context)
                .edit()
                .putStringSet(scopedScheduledIdsKey(), scheduledIds.toSet())
                .apply()
        }
    }

    actual suspend fun clearScheduledEpisodeReleaseNotifications() {
        val context = appContext ?: return
        withContext(Dispatchers.IO) {
            val workManager = WorkManager.getInstance(context)
            cancelTrackedWork(workManager)
            cancelPersistedAlarms(context)
            preferences(context)
                .edit()
                .remove(scopedScheduledIdsKey())
                .apply()
        }
    }

    /**
     * Re-arms persisted exact alarms after a reboot. Called from
     * [EpisodeReleaseNotificationsBootReceiver]; expired entries are dropped.
     */
    internal fun reschedulePersistedAlarms() {
        val context = appContext ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        if (!exactAlarmsAllowed()) return

        val nowEpochMs = System.currentTimeMillis()
        val kept = mutableListOf<PersistedAlarmEntry>()
        readAlarmEntries(context).forEach { entry ->
            if (entry.triggerAtEpochMs <= nowEpochMs) return@forEach
            val ok = runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    entry.triggerAtEpochMs,
                    alarmPendingIntent(
                        context = context,
                        requestId = entry.requestId,
                        title = entry.title,
                        body = entry.body,
                        deepLink = entry.deepLink,
                        backdropUrl = entry.backdropUrl,
                    ),
                )
                true
            }.getOrDefault(false)
            if (ok) kept += entry
        }
        writeAlarmEntries(context, kept)
    }

    actual fun exactAlarmsAllowed(): Boolean {
        val context = appContext ?: return false
        // Below Android 12 no special grant is needed.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    actual fun openExactAlarmSettings() {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
    }

    actual suspend fun showTestNotification(request: EpisodeReleaseNotificationRequest) {
        val context = appContext ?: return
        ensureNotificationChannel()

        val notification = buildNotification(context, request)

        NotificationManagerCompat.from(context)
            .notify(kotlin.math.abs(request.requestId.hashCode()), notification)
    }

    internal suspend fun buildNotification(
        context: Context,
        request: EpisodeReleaseNotificationRequest,
    ): android.app.Notification {
        val pendingIntent = buildPendingIntent(context, request)
        val backdropBitmap = loadBackdropBitmap(request.backdropUrl)
        val appIconBitmap = ContextCompat.getDrawable(
            context,
            AppIconPlatform.currentLauncherIconResource(context),
        )?.toBitmap()

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.nuvio.app.R.drawable.ic_notification_small)
            .setContentTitle(request.notificationTitle)
            .setContentText(request.notificationBody)
            .setStyle(
                backdropBitmap?.let { bitmap ->
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(appIconBitmap)
                        .setSummaryText(request.notificationBody)
                } ?: NotificationCompat.BigTextStyle().bigText(request.notificationBody),
            )
            .setLargeIcon(appIconBitmap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()
    }

    internal suspend fun loadBackdropBitmap(backdropUrl: String?): Bitmap? {
        val imageUrl = backdropUrl?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return runCatching {
            val bytes: ByteArray = httpClient.get(imageUrl).body()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun buildPendingIntent(
        context: Context,
        request: EpisodeReleaseNotificationRequest,
    ): PendingIntent {
        val launchIntent = Intent().apply {
            component = AppIconPlatform.currentLauncherComponent(context)
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(request.deepLinkUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            kotlin.math.abs(request.requestId.hashCode()),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelTrackedWork(workManager: WorkManager) {
        val context = appContext ?: return
        preferences(context)
            .getStringSet(scopedScheduledIdsKey(), emptySet())
            .orEmpty()
            .forEach { requestId ->
                awaitOperation(workManager.cancelUniqueWork(uniqueWorkName(requestId)))
            }
    }

    /** Inexact fallback used when exact alarms aren't granted. */
    private suspend fun enqueueWorker(
        workManager: WorkManager,
        request: EpisodeReleaseNotificationRequest,
        initialDelayMs: Long,
    ) {
        val inputData = Data.Builder()
            .putString(workerRequestIdKey, request.requestId)
            .putString(workerTitleKey, request.notificationTitle)
            .putString(workerBodyKey, request.notificationBody)
            .putString(workerDeepLinkKey, request.deepLinkUrl)
            .putString(workerBackdropUrlKey, request.backdropUrl)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<EpisodeReleaseNotificationWorker>()
            .setInputData(inputData)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .addTag(workTag)
            .build()

        awaitOperation(
            workManager.enqueueUniqueWork(
                uniqueWorkName(request.requestId),
                ExistingWorkPolicy.REPLACE,
                workRequest,
            ),
        )
    }

    private fun alarmPendingIntent(
        context: Context,
        requestId: String,
        title: String,
        body: String,
        deepLink: String,
        backdropUrl: String?,
    ): PendingIntent {
        val intent = Intent(context, EpisodeReleaseNotificationAlarmReceiver::class.java).apply {
            action = alarmAction
            putExtra(workerRequestIdKey, requestId)
            putExtra(workerTitleKey, title)
            putExtra(workerBodyKey, body)
            putExtra(workerDeepLinkKey, deepLink)
            putExtra(workerBackdropUrlKey, backdropUrl)
        }
        return PendingIntent.getBroadcast(
            context,
            kotlin.math.abs(requestId.hashCode()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelPersistedAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        readAlarmEntries(context).forEach { entry ->
            val pendingIntent = alarmPendingIntent(
                context = context,
                requestId = entry.requestId,
                title = entry.title,
                body = entry.body,
                deepLink = entry.deepLink,
                backdropUrl = entry.backdropUrl,
            )
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        writeAlarmEntries(context, emptyList())
    }

    private fun readAlarmEntries(context: Context): List<PersistedAlarmEntry> =
        preferences(context).getString(alarmEntriesKey, null)
            ?.let { raw -> runCatching { alarmJson.decodeFromString<List<PersistedAlarmEntry>>(raw) }.getOrNull() }
            .orEmpty()

    private fun writeAlarmEntries(context: Context, entries: List<PersistedAlarmEntry>) {
        preferences(context)
            .edit()
            .putString(alarmEntriesKey, alarmJson.encodeToString(entries))
            .apply()
    }

    private fun awaitOperation(operation: Operation) {
        operation.result.get()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(platformPreferencesName, Context.MODE_PRIVATE)

    private fun scopedScheduledIdsKey(): String = ProfileScopedKey.of(scheduledIdsKey)

    private fun triggerAtEpochMs(releaseDateIso: String): Long? = runCatching {
        LocalDate.parse(releaseDateIso)
            .atTime(EpisodeReleaseNotificationHour, EpisodeReleaseNotificationMinute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

    private fun ensureNotificationChannel() {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val existingChannel = notificationManager.getNotificationChannel(channelId)
        if (existingChannel != null) return

        val channel = NotificationChannel(
            channelId,
            runBlocking { getString(Res.string.notifications_channel_episode_releases_name) },
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = runBlocking { getString(Res.string.notifications_channel_episode_releases_description) }
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun uniqueWorkName(requestId: String): String = "$workTag:$requestId"
}
