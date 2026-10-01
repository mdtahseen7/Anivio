package com.nuvio.app.features.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Fires an episode-release notification for an alarm scheduled with
 * [android.app.AlarmManager.setExactAndAllowWhileIdle].
 *
 * Unlike the [EpisodeReleaseNotificationWorker] path (WorkManager, inexact by design), this
 * receiver fires at the exact broadcast instant, even in Doze / battery-saver modes.
 */
class EpisodeReleaseNotificationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != EpisodeReleaseNotificationPlatform.alarmAction) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                EpisodeReleaseNotificationPlatform.initialize(context)

                if (!EpisodeReleaseNotificationPlatform.notificationsAuthorized()) {
                    // Permission revoked after scheduling: drop silently, the settings page
                    // surfaces the disabled state on next refresh.
                    return@launch
                }

                val requestId =
                    intent.getStringExtra(EpisodeReleaseNotificationPlatform.workerRequestIdKey)
                        ?: return@launch
                val title =
                    intent.getStringExtra(EpisodeReleaseNotificationPlatform.workerTitleKey)
                        ?: return@launch
                val body =
                    intent.getStringExtra(EpisodeReleaseNotificationPlatform.workerBodyKey)
                        ?: return@launch
                val deepLink =
                    intent.getStringExtra(EpisodeReleaseNotificationPlatform.workerDeepLinkKey)
                        ?: return@launch
                val backdropUrl =
                    intent.getStringExtra(EpisodeReleaseNotificationPlatform.workerBackdropUrlKey)

                val notification = EpisodeReleaseNotificationPlatform.buildNotification(
                    context = context,
                    request = EpisodeReleaseNotificationRequest(
                        requestId = requestId,
                        notificationTitle = title,
                        notificationBody = body,
                        releaseDateIso = "",
                        deepLinkUrl = deepLink,
                        backdropUrl = backdropUrl,
                    ),
                )

                NotificationManagerCompat.from(context)
                    .notify(abs(requestId.hashCode()), notification)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
