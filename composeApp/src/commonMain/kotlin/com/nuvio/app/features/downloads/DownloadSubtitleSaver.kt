package com.nuvio.app.features.downloads

import com.nuvio.app.features.player.SubtitleRepository
import com.nuvio.app.features.streams.StreamSubtitle

/**
 * Downloads a stream's external subtitle tracks and stores them next to the video for offline
 * playback. Shared by every download entry point so subtitles are saved the same way whether a
 * download is started from the streams screen or the bulk episode picker.
 *
 * Anime streams almost never carry subtitles inline, so this also searches the enabled subtitle
 * addons (the same source the player uses at play time) and saves whatever they return — otherwise
 * downloaded anime episodes would always play without any subtitle track.
 */
internal suspend fun saveStreamSubtitles(
    subtitles: List<StreamSubtitle>,
    baseFileName: String,
    type: String? = null,
    videoId: String? = null,
    hlsPlaylistUrl: String? = null,
    hlsHeaders: Map<String, String> = emptyMap(),
): List<DownloadedSubtitle> {
    val saved = mutableListOf<DownloadedSubtitle>()
    var index = 0

    println("saveStreamSubtitles: streamCarried=${subtitles.size} type=$type videoId=$videoId hls=$hlsPlaylistUrl base=$baseFileName")

    // Subtitles carried on the stream itself (rare for anime, common for movies/debrid).
    // Fall back to the stream's playback headers (Referer/UA) when the subtitle carries none, since
    // many CDNs 403 a subtitle request that lacks the same Referer the video segments use.
    subtitles.forEach { sub ->
        val url = sub.url.trim().takeIf { it.isNotBlank() } ?: return@forEach
        val subHeaders = sub.headers.orEmpty().ifEmpty { hlsHeaders }.withDefaultDownloadHeaders()
        saveOneSubtitle(url, subHeaders, sub.language, sub.name, baseFileName, index)
            ?.let { saved += it; index++ }
    }

    // Subtitle tracks embedded in the HLS manifest (#EXT-X-MEDIA:TYPE=SUBTITLES). These are what
    // ExoPlayer renders automatically during online playback, so for anime HLS streams this is the
    // branch that actually recovers the subtitles the user saw while streaming.
    if (!hlsPlaylistUrl.isNullOrBlank() && hlsPlaylistUrl.isHlsPlaylistUrl()) {
        val tracks = runCatching {
            DownloadsHlsPipeline.extractSubtitles(
                playlistUrl = hlsPlaylistUrl,
                headers = hlsHeaders.withDefaultDownloadHeaders(),
            )
        }.onFailure { println("saveStreamSubtitles: hls subtitle extract failed: ${it.message}") }
            .getOrDefault(emptyList())
        println("saveStreamSubtitles: hlsTracks=${tracks.size}")
        tracks.forEach { track ->
            saveSubtitleBytes(
                bytes = track.content.encodeToByteArray(),
                language = track.language,
                name = track.name,
                extension = "vtt",
                baseFileName = baseFileName,
                index = index,
            )?.let { saved += it; index++ }
        }
    }

    // Subtitles from the installed subtitle addons.
    if (!type.isNullOrBlank() && !videoId.isNullOrBlank()) {
        val addonSubs = runCatching {
            SubtitleRepository.searchAddonSubtitles(type = type, videoId = videoId)
        }.onFailure { println("saveStreamSubtitles: addon search failed: ${it.message}") }
            .getOrDefault(emptyList())
        println("saveStreamSubtitles: addonSubs=${addonSubs.size}")
        // De-dup by url so a sub that is both stream-carried and addon-listed isn't saved twice.
        val existingUrls = subtitles.mapNotNull { it.url.trim().takeIf(String::isNotBlank) }.toMutableSet()
        addonSubs.forEach { sub ->
            val url = sub.url.trim().takeIf { it.isNotBlank() } ?: return@forEach
            if (!existingUrls.add(url)) return@forEach
            saveOneSubtitle(url, emptyMap(), sub.language, sub.addonName, baseFileName, index)
                ?.let { saved += it; index++ }
        }
    }

    println("saveStreamSubtitles: saved=${saved.size} -> ${saved.joinToString { it.localFileUri }}")
    return saved
}

private fun saveSubtitleBytes(
    bytes: ByteArray,
    language: String,
    name: String?,
    extension: String,
    baseFileName: String,
    index: Int,
): DownloadedSubtitle? {
    if (bytes.isEmpty()) return null
    val lang = language.filter { it.isLetterOrDigit() }.ifBlank { "sub" }
    val fileName = "$baseFileName.$index.$lang.$extension"
    val uri = DownloadsPlatformDownloader.saveAuxiliaryFile(fileName, bytes) ?: return null
    return DownloadedSubtitle(localFileUri = uri, language = language, name = name)
}

private suspend fun saveOneSubtitle(
    url: String,
    headers: Map<String, String>,
    language: String,
    name: String?,
    baseFileName: String,
    index: Int,
): DownloadedSubtitle? {
    val bytes = runCatching { httpDownloadBytes(url = url, headers = headers) }
        .onFailure { println("saveOneSubtitle: FAILED url=$url err=${it.message}") }
        .getOrNull()
    if (bytes == null || bytes.isEmpty()) {
        println("saveOneSubtitle: empty bytes url=$url")
        return null
    }
    val lang = language.filter { it.isLetterOrDigit() }.ifBlank { "sub" }
    val fileName = "$baseFileName.$index.$lang.${subtitleExtension(url)}"
    val uri = DownloadsPlatformDownloader.saveAuxiliaryFile(fileName, bytes) ?: return null
    return DownloadedSubtitle(localFileUri = uri, language = language, name = name)
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
