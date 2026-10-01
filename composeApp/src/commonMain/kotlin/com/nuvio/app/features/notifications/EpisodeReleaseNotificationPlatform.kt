package com.nuvio.app.features.notifications

internal expect object EpisodeReleaseNotificationPlatform {
    suspend fun notificationsAuthorized(): Boolean
    suspend fun requestAuthorization(): Boolean
    suspend fun scheduleEpisodeReleaseNotifications(requests: List<EpisodeReleaseNotificationRequest>)
    suspend fun clearScheduledEpisodeReleaseNotifications()
    suspend fun showTestNotification(request: EpisodeReleaseNotificationRequest)
    /**
     * Whether the platform can fire notifications at an exact instant.
     *
     * Android 12+ needs the "Alarms & reminders" special grant; without it scheduling falls
     * back to inexact WorkManager. iOS always uses exact calendar triggers.
     */
    fun exactAlarmsAllowed(): Boolean
    /** Opens the system screen where the user can grant exact-alarm access. No-op where unneeded. */
    fun openExactAlarmSettings()
}