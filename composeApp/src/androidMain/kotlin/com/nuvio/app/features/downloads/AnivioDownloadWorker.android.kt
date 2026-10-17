package com.nuvio.app.features.downloads

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nuvio.app.features.plugins.PluginStorage
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.delay

internal class AnivioDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val profileId = inputData.getInt(PROFILE_ID, 1)
        val downloadId = inputData.getString(DOWNLOAD_ID).orEmpty()
        val kind = inputData.getString(KIND)?.let { runCatching { DownloadWorkKind.valueOf(it) }.getOrNull() }
            ?: return Result.failure()
        if (downloadId.isBlank()) return Result.failure()

        DownloadsStorage.initialize(applicationContext)
        DownloadsPlatformDownloader.initialize(applicationContext)
        DownloadsLiveStatusPlatform.initialize(applicationContext)
        DownloadBackgroundExecution.initialize(applicationContext)
        PluginStorage.initialize(applicationContext)
        ProfileRepository.loadCachedProfiles()
        if (ProfileRepository.activeProfileId != profileId) {
            ProfileRepository.state.value.profiles
                .firstOrNull { it.profileIndex == profileId }
                ?.let { ProfileRepository.selectProfile(it.profileIndex) }
        }

        val started = when (kind) {
            DownloadWorkKind.ANIME -> DownloadsRepository.runDownloadForWorker(downloadId)
            DownloadWorkKind.MANGA -> MangaDownloadsRepository.runDownloadForWorker(downloadId)
        }
        if (!started) return Result.failure()

        while (!isStopped) {
            val status = when (kind) {
                DownloadWorkKind.ANIME -> DownloadsRepository.statusForWorker(downloadId)
                DownloadWorkKind.MANGA -> MangaDownloadsRepository.statusForWorker(downloadId)
            } ?: return Result.success()
            when (status) {
                DownloadStatus.Completed -> return Result.success()
                DownloadStatus.Failed -> return Result.retry()
                DownloadStatus.Paused -> return Result.success()
                DownloadStatus.Queued,
                DownloadStatus.Downloading,
                -> delay(750)
            }
        }
        return Result.retry()
    }

    companion object {
        const val PROFILE_ID = "profile_id"
        const val DOWNLOAD_ID = "download_id"
        const val KIND = "kind"
    }
}
