package com.nuvio.app.features.downloads

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

/**
 * Default request headers used when the stream does not provide its own. Many providers
 * reject requests without a browser-like User-Agent (the playback path does the same).
 */
internal val DownloadDefaultRequestHeaders: Map<String, String> = mapOf(
    "User-Agent" to
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
)

internal fun Map<String, String>.withDefaultDownloadHeaders(): Map<String, String> {
    val hasUserAgent = keys.any { it.equals("User-Agent", ignoreCase = true) }
    return if (hasUserAgent) this else DownloadDefaultRequestHeaders + this
}

internal fun String.isHlsPlaylistUrl(): Boolean {
    val withoutQuery = substringBefore('?').substringBefore('#').lowercase()
    return withoutQuery.endsWith(".m3u8")
}

internal fun mergeDownloadHeaders(vararg layers: Map<String, String>?): Map<String, String> =
    layers
        .filterNotNull()
        .fold(mutableMapOf<String, String>()) { acc, layer ->
            layer.forEach { (key, value) ->
                val normalizedKey = key.trim()
                if (normalizedKey.isBlank() || value.isBlank()) return@forEach
                // Later layers win, but never let a generic default overwrite a real UA.
                acc[normalizedKey] = value.trim()
            }
            acc
        }
        .withDefaultDownloadHeaders()

/**
 * Downloads an HLS playlist (and all of its segments) to a caller-provided sink.
 * Supports master playlists (picks the highest-bandwidth variant), media initialization
 * segments ([EXT-X-MAP]) and relative/absolute segment URIs. AES-128 encrypted playlists are
 * decrypted on the fly (key fetched from the EXT-X-KEY URI, IV explicit or derived from the
 * media sequence); SAMPLE-AES is rejected since it can only be undone inside the codec pipeline.
 */
internal object DownloadsHlsPipeline {

    private const val MAX_PLAYLIST_BYTES = 4L * 1024L * 1024L
    private const val MAX_SEGMENTS = 20_000

    /** How many segments to fetch concurrently. Bounded so memory stays modest while hiding latency. */
    private const val SEGMENT_CONCURRENCY = 6

    /** Per-segment fetch attempts before giving up, so a single dropped connection doesn't fail the job. */
    private const val SEGMENT_RETRY_ATTEMPTS = 3

    suspend fun run(
        playlistUrl: String,
        headers: Map<String, String>,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        appendChunk: (ByteArray) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        val mediaPlaylistUrl = resolveToMediaPlaylist(
            playlistUrl = playlistUrl,
            headers = headers,
        )
        val playlistBody = httpDownloadText(
            url = mediaPlaylistUrl,
            headers = headers,
            maxBytes = MAX_PLAYLIST_BYTES,
        )
        val playlist = parseMediaPlaylist(playlistBody)
            ?: error("Unsupported HLS playlist format")

        if (playlist.sampleAesEncrypted) {
            error("Sample-AES encrypted HLS streams cannot be downloaded")
        }

        // Cache fetched AES-128 keys by their URI so a playlist that reuses one key fetches it once.
        val keyCache = mutableMapOf<String, ByteArray>()
        suspend fun keyBytesFor(keyUri: String): ByteArray =
            keyCache.getOrPut(keyUri) {
                val resolved = resolveSegmentUri(mediaPlaylistUrl, keyUri)
                httpDownloadBytes(url = resolved, headers = headers).also {
                    if (it.size != 16) error("Unexpected AES-128 key length ${it.size}")
                }
            }

        var downloadedBytes = 0L

        playlist.initializationUri?.let { initUri ->
            if (isCancelled()) return
            val uri = resolveSegmentUri(mediaPlaylistUrl, initUri)
            // The init segment is not encrypted even in an AES-128 playlist, so it is appended as-is.
            val chunk = downloadSegmentWithRetry(uri, headers)
            appendChunk(chunk)
            downloadedBytes += chunk.size
            onProgress(downloadedBytes, null)
        }

        val segments = playlist.segments
        if (segments.isEmpty()) {
            error("HLS playlist contains no segments")
        }
        if (segments.size > MAX_SEGMENTS) {
            error("HLS playlist has too many segments to download")
        }

        // Estimate the total size once the first segment reveals the average segment size.
        var estimatedTotalBytes: Long? = null

        // Fetch segments in bounded-concurrency windows, then append them strictly in playlist order.
        segments.chunked(SEGMENT_CONCURRENCY).forEach { window ->
            if (isCancelled()) return
            val chunks = coroutineScope {
                window.map { segment ->
                    async {
                        val uri = resolveSegmentUri(mediaPlaylistUrl, segment.uri)
                        val raw = downloadSegmentWithRetry(uri, headers)
                        val encryption = segment.encryption
                        if (encryption == null) {
                            raw
                        } else {
                            val key = keyBytesFor(encryption.keyUri)
                            hlsAes128CbcDecrypt(raw, key, encryption.iv)
                        }
                    }
                }.awaitAll()
            }
            if (isCancelled()) return
            chunks.forEach { chunk ->
                appendChunk(chunk)
                downloadedBytes += chunk.size
                if (estimatedTotalBytes == null && segments.size > 1) {
                    estimatedTotalBytes = chunk.size.toLong() * segments.size
                }
                onProgress(downloadedBytes, estimatedTotalBytes)
            }
        }
    }

    private suspend fun downloadSegmentWithRetry(
        url: String,
        headers: Map<String, String>,
    ): ByteArray {
        var lastError: Throwable? = null
        repeat(SEGMENT_RETRY_ATTEMPTS) { attempt ->
            try {
                return httpDownloadBytes(url = url, headers = headers)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                lastError = error
                delay(300L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("Segment download failed: $url")
    }

    private suspend fun resolveToMediaPlaylist(
        playlistUrl: String,
        headers: Map<String, String>,
    ): String {
        val body = httpDownloadText(
            url = playlistUrl,
            headers = headers,
            maxBytes = MAX_PLAYLIST_BYTES,
        )
        if (!body.contains("#EXTM3U")) {
            error("Downloaded playlist is not a valid M3U8 document")
        }
        if (body.contains("#EXT-X-STREAM-INF")) {
            val bestVariant = parseMasterPlaylist(body)
                .maxByOrNull { it.bandwidth }
                ?: error("Master playlist contains no variants")
            return resolveSegmentUri(playlistUrl, bestVariant.uri)
        }
        return playlistUrl
    }

    private fun parseMasterPlaylist(body: String): List<MasterPlaylistVariant> {
        val variants = mutableListOf<MasterPlaylistVariant>()
        var pendingBandwidth = 0L
        body.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXT-X-STREAM-INF") -> {
                    pendingBandwidth = Regex("BANDWIDTH=(\\d+)").find(line)
                        ?.groupValues
                        ?.get(1)
                        ?.toLongOrNull()
                        ?: 0L
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    variants += MasterPlaylistVariant(uri = line, bandwidth = pendingBandwidth)
                    pendingBandwidth = 0L
                }
            }
        }
        return variants
    }

    /**
     * Downloads any in-manifest subtitle renditions (`#EXT-X-MEDIA:TYPE=SUBTITLES`) referenced by an
     * HLS master playlist and returns the concatenated WebVTT text for each. ExoPlayer renders these
     * tracks automatically during online playback, so without this a downloaded episode would lose the
     * subtitles the user saw while streaming. Returns an empty list for media playlists or when no
     * subtitle renditions are present.
     */
    suspend fun extractSubtitles(
        playlistUrl: String,
        headers: Map<String, String>,
    ): List<HlsSubtitleTrack> {
        val masterBody = runCatching {
            httpDownloadText(url = playlistUrl, headers = headers, maxBytes = MAX_PLAYLIST_BYTES)
        }.getOrNull() ?: return emptyList()
        if (!masterBody.contains("#EXT-X-STREAM-INF")) return emptyList()

        val renditions = parseSubtitleRenditions(masterBody)
        if (renditions.isEmpty()) return emptyList()

        val tracks = mutableListOf<HlsSubtitleTrack>()
        renditions.forEachIndexed { index, rendition ->
            val subtitlePlaylistUrl = resolveSegmentUri(playlistUrl, rendition.uri)
            val text = runCatching {
                downloadWebVttTrack(subtitlePlaylistUrl, headers)
            }.getOrNull()
            if (!text.isNullOrBlank()) {
                tracks += HlsSubtitleTrack(
                    language = rendition.language ?: rendition.name ?: "sub$index",
                    name = rendition.name,
                    content = text,
                )
            }
        }
        return tracks
    }

    /** Parses `#EXT-X-MEDIA:TYPE=SUBTITLES` lines out of a master playlist. */
    private fun parseSubtitleRenditions(body: String): List<SubtitleRendition> {
        val renditions = mutableListOf<SubtitleRendition>()
        body.lineSequence().map { it.trim() }.forEach { line ->
            if (!line.startsWith("#EXT-X-MEDIA:")) return@forEach
            val type = Regex("TYPE=([A-Za-z0-9-]+)").find(line)?.groupValues?.get(1)?.uppercase()
            if (type != "SUBTITLES") return@forEach
            val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: return@forEach
            val language = Regex("LANGUAGE=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
            val name = Regex("NAME=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
            renditions += SubtitleRendition(uri = uri, language = language, name = name)
        }
        return renditions
    }

    /**
     * Resolves a subtitle rendition URI (which may itself be a tiny media playlist pointing at one or
     * more .vtt segments, or a direct .vtt/.srt file) and returns a single concatenated WebVTT document.
     */
    private suspend fun downloadWebVttTrack(
        subtitleUri: String,
        headers: Map<String, String>,
    ): String {
        val lower = subtitleUri.substringBefore('?').lowercase()
        // A direct subtitle file (no playlist indirection).
        if (lower.endsWith(".vtt") || lower.endsWith(".srt") || lower.endsWith(".ass") || lower.endsWith(".ssa")) {
            return httpDownloadText(url = subtitleUri, headers = headers, maxBytes = MAX_PLAYLIST_BYTES)
        }

        val playlistBody = httpDownloadText(url = subtitleUri, headers = headers, maxBytes = MAX_PLAYLIST_BYTES)
        if (!playlistBody.contains("#EXTM3U")) {
            // Not a playlist after all — treat the body as the subtitle itself.
            return playlistBody
        }

        val segmentUris = playlistBody.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { resolveSegmentUri(subtitleUri, it) }
            .toList()
        if (segmentUris.isEmpty()) return ""

        val builder = StringBuilder()
        segmentUris.forEach { uri ->
            val part = runCatching {
                httpDownloadText(url = uri, headers = headers, maxBytes = MAX_PLAYLIST_BYTES)
            }.getOrNull().orEmpty()
            if (part.isNotBlank()) {
                if (builder.isNotEmpty()) builder.append('\n')
                builder.append(part)
            }
        }
        return builder.toString()
    }

    private fun parseMediaPlaylist(body: String): MediaPlaylist? {
        if (!body.contains("#EXTM3U")) return null

        var initializationUri: String? = null
        val segments = mutableListOf<MediaSegment>()
        var sampleAesEncrypted = false

        // The active EXT-X-KEY applies to every following segment until the next EXT-X-KEY. A
        // media sequence counter is tracked so AES-128 segments without an explicit IV can derive
        // one from their sequence number per RFC 8216 §5.2.
        var currentKeyUri: String? = null
        var currentExplicitIv: ByteArray? = null
        var mediaSequence = parseStartMediaSequence(body)

        body.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("#EXT-X-MAP:") -> {
                    initializationUri = Regex("URI=\"([^\"]+)\"").find(line)
                        ?.groupValues
                        ?.get(1)
                }
                line.startsWith("#EXT-X-KEY:") -> {
                    val method = Regex("METHOD=([A-Za-z0-9-]+)").find(line)
                        ?.groupValues
                        ?.get(1)
                        ?.uppercase()
                    when (method) {
                        null, "NONE" -> {
                            currentKeyUri = null
                            currentExplicitIv = null
                        }
                        "AES-128" -> {
                            currentKeyUri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                            currentExplicitIv = Regex("IV=0[xX]([0-9A-Fa-f]+)").find(line)
                                ?.groupValues
                                ?.get(1)
                                ?.let(::hexToBytes)
                        }
                        else -> {
                            // SAMPLE-AES (or any other non-decryptable method): flag and stop.
                            sampleAesEncrypted = true
                        }
                    }
                }
                line.startsWith("#EXT-X-BYTERANGE") -> Unit
                line.isNotEmpty() && !line.startsWith("#") -> {
                    val keyUri = currentKeyUri
                    val encryption = if (keyUri != null) {
                        SegmentEncryption(
                            keyUri = keyUri,
                            iv = currentExplicitIv ?: ivFromSequence(mediaSequence),
                        )
                    } else {
                        null
                    }
                    segments += MediaSegment(uri = line, encryption = encryption)
                    mediaSequence++
                }
            }
        }

        return MediaPlaylist(
            initializationUri = initializationUri,
            segments = segments,
            sampleAesEncrypted = sampleAesEncrypted,
        )
    }

    private fun parseStartMediaSequence(body: String): Long =
        Regex("#EXT-X-MEDIA-SEQUENCE:(\\d+)").find(body)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
            ?: 0L

    /** 16-byte big-endian IV from a segment's media sequence number (RFC 8216 §5.2 default IV). */
    private fun ivFromSequence(sequence: Long): ByteArray {
        val iv = ByteArray(16)
        var value = sequence
        for (i in 0 until 8) {
            iv[15 - i] = (value and 0xFF).toByte()
            value = value ushr 8
        }
        return iv
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.length % 2 == 1) "0$hex" else hex
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun resolveSegmentUri(playlistUrl: String, segmentUri: String): String {
        val raw = segmentUri.trim()
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw
        }
        if (raw.startsWith("//")) {
            val scheme = playlistUrl.substringBefore(":", missingDelimiterValue = "")
            if (scheme.startsWith("http")) {
                return "$scheme:$raw"
            }
        }
        if (raw.startsWith("/")) {
            val schemeEnd = playlistUrl.indexOf("://")
            if (schemeEnd > 0) {
                val hostEnd = playlistUrl.indexOf('/', schemeEnd + 3)
                if (hostEnd > 0) {
                    return playlistUrl.substring(0, hostEnd) + raw
                }
                return playlistUrl + raw.trimStart('/')
            }
        }
        // Relative path: strip everything after the last '/' of the playlist path.
        val queryIndex = playlistUrl.indexOf('?')
        val baseUrl = if (queryIndex > 0) playlistUrl.substring(0, queryIndex) else playlistUrl
        val lastSlash = baseUrl.lastIndexOf('/')
        if (lastSlash > 0) {
            return baseUrl.substring(0, lastSlash + 1) + raw
        }
        error("Cannot resolve segment URI '$raw' against playlist '$playlistUrl'")
    }

    private data class MasterPlaylistVariant(
        val uri: String,
        val bandwidth: Long,
    )

    private data class MediaPlaylist(
        val initializationUri: String? = null,
        val segments: List<MediaSegment> = emptyList(),
        val sampleAesEncrypted: Boolean = false,
    )

    private data class MediaSegment(
        val uri: String,
        val encryption: SegmentEncryption? = null,
    )

    private class SegmentEncryption(
        val keyUri: String,
        val iv: ByteArray,
    )
}

/** A subtitle track extracted from an HLS master playlist, as a concatenated WebVTT document. */
internal data class HlsSubtitleTrack(
    val language: String,
    val name: String?,
    val content: String,
)

private data class SubtitleRendition(
    val uri: String,
    val language: String?,
    val name: String?,
)

expect suspend fun httpDownloadText(
    url: String,
    headers: Map<String, String>,
    maxBytes: Long = 8L * 1024L * 1024L,
): String

expect suspend fun httpDownloadBytes(
    url: String,
    headers: Map<String, String>,
): ByteArray
