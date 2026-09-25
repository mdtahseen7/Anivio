package com.nuvio.app.features.downloads

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

private val downloadHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    // Transparently retry when a pooled connection drops mid-download instead of failing the task.
    .retryOnConnectionFailure(true)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

internal actual object DownloadsPlatformDownloader {
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    actual fun start(
        request: DownloadPlatformRequest,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        onSuccess: (localFileUri: String, totalBytes: Long?) -> Unit,
        onFailure: (message: String) -> Unit,
    ): DownloadsTaskHandle {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        var call: Call? = null

        scope.launch {
            val context = appContext
            if (context == null) {
                onFailure(runBlocking { getString(Res.string.downloads_error_not_initialized) })
                return@launch
            }

            val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
            val destination = File(downloadsDir, request.destinationFileName)
            val tempFile = File(downloadsDir, "${request.destinationFileName}.part")
            val effectiveHeaders = request.sourceHeaders.withDefaultDownloadHeaders()

            try {
                // HLS playlists are downloaded by segment and re-muxed into a playable file.
                if (request.sourceUrl.isHlsPlaylistUrl()) {
                    try {
                        // HLS is re-downloaded from scratch each run (no per-segment resume), so start
                        // from a clean temp file to avoid appending duplicate segments on resume.
                        if (tempFile.exists()) tempFile.delete()
                        DownloadsHlsPipeline.run(
                            playlistUrl = request.sourceUrl,
                            headers = effectiveHeaders,
                            onProgress = onProgress,
                            appendChunk = { chunk ->
                                FileOutputStream(tempFile, true).use { output ->
                                    output.write(chunk)
                                    output.flush()
                                }
                            },
                            isCancelled = { !job.isActive },
                        )
                        // If paused/cancelled, do NOT finalize — otherwise a partial download would be
                        // renamed into place and reported as a successful (complete) file.
                        ensureActive()
                        if (tempFile.length() <= 0L) {
                            error(runBlocking { getString(Res.string.downloads_error_empty_body) })
                        }
                        if (destination.exists()) destination.delete()
                        if (!tempFile.renameTo(destination)) {
                            tempFile.copyTo(destination, overwrite = true)
                            tempFile.delete()
                        }
                        val finalSize = destination.length()
                        onSuccess(destination.toURI().toString(), finalSize)
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        tempFile.delete()
                        onFailure(error.message ?: runBlocking { getString(Res.string.download_failed) })
                    }
                    return@launch
                }

                var resumeFromBytes = tempFile.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L

                fun buildRequest(rangeStart: Long?): Request {
                    val requestBuilder = Request.Builder().url(request.sourceUrl)
                    effectiveHeaders.forEach { (key, value) ->
                        requestBuilder.header(key, value)
                    }
                    if (rangeStart != null && rangeStart > 0L) {
                        requestBuilder.header("Range", "bytes=$rangeStart-")
                    }
                    return requestBuilder.get().build()
                }

                var attemptedRangeRequest = resumeFromBytes > 0L
                var httpRequest = buildRequest(if (attemptedRangeRequest) resumeFromBytes else null)
                call = downloadHttpClient.newCall(httpRequest)
                var response = call?.execute() ?: error(
                    runBlocking { getString(Res.string.downloads_error_request_failed) },
                )

                if (attemptedRangeRequest && response.code == 416) {
                    response.close()
                    tempFile.delete()
                    resumeFromBytes = 0L
                    attemptedRangeRequest = false
                    httpRequest = buildRequest(null)
                    call = downloadHttpClient.newCall(httpRequest)
                    response = call?.execute() ?: error(
                        runBlocking { getString(Res.string.downloads_error_request_failed) },
                    )
                }

                if (attemptedRangeRequest && response.code == 403) {
                    // Some CDNs reject Range requests outright; retry the full body without it.
                    response.close()
                    tempFile.delete()
                    resumeFromBytes = 0L
                    attemptedRangeRequest = false
                    httpRequest = buildRequest(null)
                    call = downloadHttpClient.newCall(httpRequest)
                    response = call?.execute() ?: error(
                        runBlocking { getString(Res.string.downloads_error_request_failed) },
                    )
                }

                response.use { response ->
                    if (!response.isSuccessful) {
                        error(
                            runBlocking {
                                getString(Res.string.downloads_error_http_failed, response.code)
                            },
                        )
                    }

                    val isPartialResume = attemptedRangeRequest && response.code == 206 && resumeFromBytes > 0L
                    val appendToTemp = isPartialResume
                    val startingBytes = if (appendToTemp) resumeFromBytes else 0L

                    if (!appendToTemp && tempFile.exists()) {
                        tempFile.delete()
                    }

                    val body = response.body ?: error(
                        runBlocking { getString(Res.string.downloads_error_empty_body) },
                    )
                    val totalBytes = resolveTotalBytes(
                        startingBytes = startingBytes,
                        isPartialResume = isPartialResume,
                        contentRangeHeader = response.header("Content-Range"),
                        contentLength = body.contentLength().takeIf { it > 0L },
                    )
                    var downloadedBytes = startingBytes
                    onProgress(downloadedBytes, totalBytes)

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile, appendToTemp).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                downloadedBytes += read.toLong()
                                onProgress(downloadedBytes, totalBytes)
                            }
                            output.flush()
                        }
                    }

                    if (destination.exists()) {
                        destination.delete()
                    }
                    if (!tempFile.renameTo(destination)) {
                        tempFile.copyTo(destination, overwrite = true)
                        tempFile.delete()
                    }

                    val finalSize = destination.length()
                    onSuccess(destination.toURI().toString(), totalBytes ?: finalSize)
                }
            } catch (error: CancellationException) {
                // Paused/cancelled: leave the .part file in place and report nothing (the repository
                // keeps the item Paused). Never fall through to onFailure/onSuccess.
                throw error
            } catch (error: Throwable) {
                onFailure(error.message ?: runBlocking { getString(Res.string.download_failed) })
            }
        }

        job.invokeOnCompletion {
            call?.cancel()
        }

        return AndroidDownloadsTaskHandle(job)
    }

    actual fun removeFile(localFileUri: String?): Boolean {
        if (localFileUri.isNullOrBlank()) return false
        val file = localFileUri.toLocalFileOrNull() ?: return false
        return runCatching { file.delete() }.getOrDefault(false)
    }

    actual fun removePartialFile(destinationFileName: String): Boolean {
        val context = appContext ?: return false
        val downloadsDir = File(context.filesDir, "downloads")
        val tempFile = File(downloadsDir, "$destinationFileName.part")
        if (!tempFile.exists()) return true
        return runCatching { tempFile.delete() }.getOrDefault(false)
    }

    actual fun saveAuxiliaryFile(destinationFileName: String, bytes: ByteArray): String? {
        val context = appContext ?: return null
        val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
        val file = File(downloadsDir, destinationFileName)
        return runCatching {
            file.writeBytes(bytes)
            file.toURI().toString()
        }.getOrNull()
    }

    actual fun resolveLocalFileUri(localFileUri: String?, destinationFileName: String): String? {
        localFileUri
            ?.toLocalFileOrNull()
            ?.takeIf { it.exists() }
            ?.let { return it.toURI().toString() }

        val context = appContext ?: return null
        val fileName = destinationFileName.trim().takeIf { it.isNotBlank() }
            ?: localFileUri
                ?.toLocalFileOrNull()
                ?.name
                ?.takeIf { it.isNotBlank() }
            ?: return null
        val downloadsDir = File(context.filesDir, "downloads")
        val localFile = File(downloadsDir, fileName)
        return localFile.takeIf { it.exists() }?.toURI()?.toString()
    }

    actual fun openDownloadsDirectory(): Boolean {
        val context = appContext ?: return false
        val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                downloadsDir,
            )
        }.getOrNull() ?: return false

        val intents = listOf(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
            },
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "vnd.android.document/directory")
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = uri
            },
        )

        return intents.any { intent ->
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)

            runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }
    }
}

private class AndroidDownloadsTaskHandle(
    private val job: Job,
) : DownloadsTaskHandle {
    override fun cancel() {
        job.cancel()
    }
}

private fun String.toLocalFileOrNull(): File? {
    return runCatching {
        if (startsWith("file:")) {
            File(URI(this))
        } else {
            File(this)
        }
    }.getOrNull()
}

private fun resolveTotalBytes(
    startingBytes: Long,
    isPartialResume: Boolean,
    contentRangeHeader: String?,
    contentLength: Long?,
): Long? {
    parseContentRangeTotal(contentRangeHeader)?.let { return it }
    val normalizedLength = contentLength?.takeIf { it > 0L } ?: return null
    return if (isPartialResume && startingBytes > 0L) {
        startingBytes + normalizedLength
    } else {
        normalizedLength
    }
}

private fun parseContentRangeTotal(headerValue: String?): Long? {
    val value = headerValue?.trim().orEmpty()
    if (value.isBlank()) return null
    val slashIndex = value.lastIndexOf('/')
    if (slashIndex == -1 || slashIndex == value.lastIndex) return null
    val totalPart = value.substring(slashIndex + 1).trim()
    if (totalPart == "*") return null
    return totalPart.toLongOrNull()?.takeIf { it > 0L }
}

actual suspend fun httpDownloadText(
    url: String,
    headers: Map<String, String>,
    maxBytes: Long,
): String {
    val bytes = httpDownloadBytes(url, headers)
    if (bytes.size > maxBytes) {
        error("Downloaded playlist exceeds the size limit")
    }
    return bytes.decodeToString()
}

actual suspend fun httpDownloadBytes(
    url: String,
    headers: Map<String, String>,
): ByteArray {
    val requestBuilder = Request.Builder().url(url)
    headers.forEach { (key, value) ->
        requestBuilder.header(key, value)
    }
    val request = requestBuilder.get().build()

    downloadHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            error("HTTP ${response.code} while downloading")
        }
        val body = response.body ?: error("Empty download response")
        return body.bytes()
    }
}
