package com.nuvio.app.features.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Exact alarms don't survive a reboot, so re-arm them from the persisted schedule.
 *
 * This deliberately does NOT run a full metadata refresh: at boot the app's storage layer
 * isn't initialized, and the persisted entries already carry everything the alarm needs
 * (trigger instant + notification payload).
 */
class EpisodeReleaseNotificationsBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                EpisodeReleaseNotificationPlatform.initialize(context)
                EpisodeReleaseNotificationPlatform.reschedulePersistedAlarms()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
