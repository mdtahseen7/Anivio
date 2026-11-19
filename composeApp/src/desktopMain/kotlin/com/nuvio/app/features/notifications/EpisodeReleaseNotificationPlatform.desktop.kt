package com.nuvio.app.features.notifications

internal actual object EpisodeReleaseNotificationPlatform {
    actual fun exactAlarmsAllowed(): Boolean = true
    actual fun openExactAlarmSettings() = Unit
    actual suspend fun notificationsAuthorized(): Boolean = false

    actual suspend fun requestAuthorization(): Boolean = false

    actual suspend fun scheduleEpisodeReleaseNotifications(
        requests: List<EpisodeReleaseNotificationRequest>,
    ) = Unit

    actual suspend fun clearScheduledEpisodeReleaseNotifications() = Unit

    actual suspend fun showTestNotification(request: EpisodeReleaseNotificationRequest) = Unit
}
