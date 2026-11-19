package com.nuvio.app.features.settings

import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.collection.CollectionStorage
import com.nuvio.app.features.downloads.DownloadsClock
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.LibraryStorage
import com.nuvio.app.features.library.StoredLibraryPayload
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watched.StoredWatchedPayload
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watched.WatchedStorage
import com.nuvio.app.features.watchprogress.StoredWatchProgressPayload
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.WatchProgressStorage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val MaxBackupBytes = 12 * 1024 * 1024

@Serializable
internal data class DesktopBackup(
    val version: Int = 1,
    @SerialName("exported_at_epoch_ms") val exportedAtEpochMs: Long = 0,
    @SerialName("source_profile_id") val sourceProfileId: Int = 1,
    @SerialName("library_payload") val library: String? = null,
    @SerialName("watched_payload") val watched: String? = null,
    @SerialName("watch_progress_payload") val progress: String? = null,
    @SerialName("collections_payload") val collections: String? = null,
)

internal object BackupRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun export(): String {
        val profile = ProfileRepository.activeProfileId
        return json.encodeToString(
            DesktopBackup(
                exportedAtEpochMs = DownloadsClock.nowEpochMs(),
                sourceProfileId = profile,
                library = LibraryStorage.loadPayload(profile).orEmpty(),
                watched = WatchedStorage.loadPayload(profile).orEmpty(),
                progress = WatchProgressStorage.loadPayload(profile).orEmpty(),
                collections = CollectionStorage.loadPayload().orEmpty(),
            ),
        )
    }

    fun parse(text: String): Result<DesktopBackup> = runCatching {
        require(text.length <= MaxBackupBytes) { "The backup exceeds 12 MB." }
        val backup = json.decodeFromString<DesktopBackup>(text)
        require(backup.version == 1) { "This backup version is unsupported." }
        require(listOf(backup.library, backup.watched, backup.progress, backup.collections).any { it != null }) {
            "This file contains no supported profile data."
        }
        backup.library?.takeIf(String::isNotBlank)?.let { json.decodeFromString<StoredLibraryPayload>(it) }
        backup.watched?.takeIf(String::isNotBlank)?.let { json.decodeFromString<StoredWatchedPayload>(it) }
        backup.progress?.takeIf(String::isNotBlank)?.let { json.decodeFromString<StoredWatchProgressPayload>(it) }
        backup.collections?.takeIf(String::isNotBlank)?.let { json.decodeFromString<List<Collection>>(it) }
        backup
    }

    fun restore(profile: Int, backup: DesktopBackup) {
        check(ProfileRepository.activeProfileId == profile) { "The active profile changed. Reopen the backup in that profile." }
        backup.library?.let {
            LibraryRepository.clearLocalState()
            LibraryStorage.savePayload(profile, it)
            LibraryRepository.onProfileChanged(profile)
        }
        backup.watched?.let {
            WatchedRepository.clearLocalState()
            WatchedStorage.savePayload(profile, it)
            WatchedRepository.onProfileChanged(profile)
        }
        backup.progress?.let {
            WatchProgressRepository.clearLocalState()
            WatchProgressStorage.savePayload(profile, it)
            WatchProgressRepository.onProfileChanged(profile)
        }
        backup.collections?.let {
            CollectionStorage.savePayload(it)
            CollectionRepository.onProfileChanged()
            CollectionRepository.initialize()
        }
    }
}
