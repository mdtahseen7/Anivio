package com.nuvio.app.features.downloads

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal actual object DownloadBackgroundExecution {
    private var appContext: Context? = null

    actual val isSupported: Boolean = true

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    actual fun schedule(profileId: Int, downloadId: String, kind: DownloadWorkKind) {
        val context = appContext ?: return
        val request = OneTimeWorkRequestBuilder<AnivioDownloadWorker>()
            .setInputData(
                Data.Builder()
                    .putInt(AnivioDownloadWorker.PROFILE_ID, profileId)
                    .putString(AnivioDownloadWorker.DOWNLOAD_ID, downloadId)
                    .putString(AnivioDownloadWorker.KIND, kind.name)
                    .build(),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS,
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(profileId, downloadId, kind),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    actual fun cancel(profileId: Int, downloadId: String, kind: DownloadWorkKind) {
        appContext?.let { WorkManager.getInstance(it).cancelUniqueWork(workName(profileId, downloadId, kind)) }
    }

    private fun workName(profileId: Int, downloadId: String, kind: DownloadWorkKind): String =
        "anivio-download:$profileId:${kind.name.lowercase()}:$downloadId"
}
