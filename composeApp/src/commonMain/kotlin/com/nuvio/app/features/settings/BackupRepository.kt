package com.nuvio.app.features.settings

import com.nuvio.app.features.collection.CollectionMobileSettingsRepository
import com.nuvio.app.features.collection.CollectionMobileSettingsStorage
import com.nuvio.app.features.collection.CollectionStorage
import com.nuvio.app.features.details.MangaReaderProgressRepository
import com.nuvio.app.features.details.MangaReaderSettingsRepository
import com.nuvio.app.features.details.MangaReaderSettingsStorage
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.downloads.DownloadsStorage
import com.nuvio.app.features.downloads.MangaDownloadsRepository
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.LibraryStorage
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watched.WatchedStorage
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.WatchProgressStorage
import com.nuvio.app.features.downloads.DownloadsClock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val BACKUP_VERSION = 1
private const val MAX_BACKUP_CHARS = 12 * 1024 * 1024

@Serializable
data class NuvioBackupPayload(
    val version: Int = BACKUP_VERSION,
    @SerialName("exported_at_epoch_ms") val exportedAtEpochMs: Long = 0L,
    @SerialName("source_profile_id") val sourceProfileId: Int = 1,
    @SerialName("library_payload") val libraryPayload: String = "",
    @SerialName("watched_payload") val watchedPayload: String = "",
    @SerialName("watch_progress_payload") val watchProgressPayload: String = "",
    @SerialName("manga_reader_progress_payload") val mangaReaderProgressPayload: String = "",
    @SerialName("manga_reader_settings_payload") val mangaReaderSettingsPayload: String = "",
    @SerialName("downloads_payload") val downloadsPayload: String = "",
    @SerialName("manga_downloads_payload") val mangaDownloadsPayload: String = "",
    @SerialName("collections_payload") val collectionsPayload: String = "",
    @SerialName("collection_mobile_settings_payload") val collectionMobileSettingsPayload: String = "",
    @SerialName("plugin_setup_payload") val pluginSetupPayload: String = "",
)

data class BackupValidationResult(
    val valid: Boolean,
    val message: String? = null,
)

/**
 * Portable, metadata-only backup for the active profile. Credentials, auth sessions and downloaded
 * media files are deliberately excluded; download records are retained so completed files can be
 * reconciled or removed after restore.
 */
object BackupRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportToJson(): String {
        val profileId = ProfileRepository.activeProfileId
        return json.encodeToString(
            NuvioBackupPayload(
                exportedAtEpochMs = DownloadsClock.nowEpochMs(),
                sourceProfileId = profileId,
                libraryPayload = LibraryStorage.loadPayload(profileId).orEmpty(),
                watchedPayload = WatchedStorage.loadPayload(profileId).orEmpty(),
                watchProgressPayload = WatchProgressStorage.loadPayload(profileId).orEmpty(),
                mangaReaderProgressPayload = MangaReaderProgressRepository.exportPayload(),
                mangaReaderSettingsPayload = MangaReaderSettingsRepository.exportPayload(),
                downloadsPayload = DownloadsStorage.loadPayload().orEmpty(),
                mangaDownloadsPayload = DownloadsStorage.loadMangaPayload().orEmpty(),
                collectionsPayload = CollectionStorage.loadPayload().orEmpty(),
                collectionMobileSettingsPayload = CollectionMobileSettingsStorage.loadPayload().orEmpty(),
                pluginSetupPayload = PluginRepository.exportSetupPayload(),
            ),
        )
    }

    fun validateJson(payload: String): BackupValidationResult {
        if (payload.length > MAX_BACKUP_CHARS) {
            return BackupValidationResult(false, "This backup is too large to import safely.")
        }
        val decoded = runCatching { json.decodeFromString<NuvioBackupPayload>(payload) }.getOrNull()
            ?: return BackupValidationResult(false, "The backup is not valid JSON.")
        if (decoded.version != BACKUP_VERSION) {
            return BackupValidationResult(false, "This backup version is not supported.")
        }
        return BackupValidationResult(true)
    }

    /** Replaces the active profile's local metadata and reloads every affected repository. */
    fun restoreFromJson(payload: String): BackupValidationResult {
        val validation = validateJson(payload)
        if (!validation.valid) return validation
        val backup = json.decodeFromString<NuvioBackupPayload>(payload)
        val profileId = ProfileRepository.activeProfileId

        LibraryStorage.savePayload(profileId, backup.libraryPayload)
        WatchedStorage.savePayload(profileId, backup.watchedPayload)
        WatchProgressStorage.savePayload(profileId, backup.watchProgressPayload)
        MangaReaderProgressRepository.replacePayload(backup.mangaReaderProgressPayload)
        MangaReaderSettingsStorage.savePayload(backup.mangaReaderSettingsPayload)
        DownloadsStorage.savePayload(backup.downloadsPayload)
        DownloadsStorage.saveMangaPayload(backup.mangaDownloadsPayload)
        CollectionStorage.savePayload(backup.collectionsPayload)
        CollectionMobileSettingsStorage.savePayload(backup.collectionMobileSettingsPayload)
        if (backup.pluginSetupPayload.isNotBlank()) {
            PluginRepository.restoreSetupPayload(backup.pluginSetupPayload)
        }

        LibraryRepository.clearLocalState()
        LibraryRepository.onProfileChanged(profileId)
        WatchedRepository.clearLocalState()
        WatchedRepository.onProfileChanged(profileId)
        WatchProgressRepository.clearLocalState()
        WatchProgressRepository.onProfileChanged(profileId)
        MangaReaderProgressRepository.onProfileChanged()
        MangaReaderSettingsRepository.replacePayload(backup.mangaReaderSettingsPayload)
        DownloadsRepository.onProfileChanged()
        MangaDownloadsRepository.onProfileChanged()
        CollectionMobileSettingsRepository.onProfileChanged()
        return BackupValidationResult(true)
    }
}
