package com.nuvio.app.features.downloads

import com.nuvio.app.features.streams.StreamSubtitle

/**
 * Downloads a stream's external subtitle tracks and stores them next to the video for offline
 * playback. Shared by every download entry point so subtitles are saved the same way whether a
 * download is started from the streams screen or the bulk episode picker.
 */
internal suspend fun saveStreamSubtitles(
    subtitles: List<StreamSubtitle>,
    baseFileName: String,
): List<DownloadedSubtitle> {
    if (subtitles.isEmpty()) return emptyList()
    val saved = mutableListOf<DownloadedSubtitle>()
    subtitles.forEachIndexed { index, sub ->
        val url = sub.url.trim().takeIf { it.isNotBlank() } ?: return@forEachIndexed
        val bytes = runCatching {
            httpDownloadBytes(url = url, headers = sub.headers.orEmpty())
        }.getOrNull()
        if (bytes == null || bytes.isEmpty()) return@forEachIndexed
        val lang = sub.language.filter { it.isLetterOrDigit() }.ifBlank { "sub" }
        val fileName = "$baseFileName.$index.$lang.${subtitleExtension(url)}"
        val uri = DownloadsPlatformDownloader.saveAuxiliaryFile(fileName, bytes) ?: return@forEachIndexed
        saved += DownloadedSubtitle(localFileUri = uri, language = sub.language, name = sub.name)
    }
    return saved
}

private fun subtitleExtension(url: String): String {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return when {
        path.endsWith(".vtt") -> "vtt"
        path.endsWith(".ass") -> "ass"
        path.endsWith(".ssa") -> "ssa"
        else -> "srt"
    }
}
