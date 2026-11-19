package com.nuvio.app.features.downloads

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
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
 * segments ([EXT-X-MAP]) and relative/absolute segment URIs. Encrypted playlists
 * (AES-128/SAMPLE-AES) are rejected with a descriptive error.
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

        if (playlist.encrypted) {
            error("Encrypted HLS streams cannot be downloaded")
        }

        var downloadedBytes = 0L
        val initializedSegmentUris = mutableListOf<String>()

        playlist.initializationUri?.let { initUri ->
            if (isCancelled()) return
            val uri = resolveSegmentUri(mediaPlaylistUrl, initUri)
            val chunk = downloadSegmentWithRetry(uri, headers)
            appendChunk(chunk)
            initializedSegmentUris += initUri
            downloadedBytes += chunk.size
            onProgress(downloadedBytes, null)
        }

        val segments = playlist.segmentUris
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
                window.map { segmentUri ->
                    async {
                        val uri = resolveSegmentUri(mediaPlaylistUrl, segmentUri)
                        downloadSegmentWithRetry(uri, headers)
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

    private fun parseMediaPlaylist(body: String): MediaPlaylist? {
        if (!body.contains("#EXTM3U")) return null

        var initializationUri: String? = null
        val segmentUris = mutableListOf<String>()
        var encrypted = false

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
                    if (method != null && method != "NONE") {
                        encrypted = true
                    }
                }
                line.startsWith("#EXT-X-BYTERANGE") -> Unit
                line.isNotEmpty() && !line.startsWith("#") -> {
                    segmentUris += line
                }
            }
        }

        return MediaPlaylist(
            initializationUri = initializationUri,
            segmentUris = segmentUris,
            encrypted = encrypted,
        )
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

    @Serializable
    private data class MediaPlaylist(
        val initializationUri: String? = null,
        val segmentUris: List<String> = emptyList(),
        val encrypted: Boolean = false,
    )
}

expect suspend fun httpDownloadText(
    url: String,
    headers: Map<String, String>,
    maxBytes: Long = 8L * 1024L * 1024L,
): String

expect suspend fun httpDownloadBytes(
    url: String,
    headers: Map<String, String>,
): ByteArray
