package com.nuvio.app.features.downloads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.util.Locale
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val hlsHttpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(30))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

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
): ByteArray = runInterruptible(Dispatchers.IO) {
    val builder = HttpRequest.newBuilder()
        .uri(URI(url))
        .timeout(Duration.ofSeconds(60))
        .GET()
    builder.addDownloadRequestHeaders(headers)
    val response = hlsHttpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
    if (response.statusCode() !in 200..299) {
        error("HTTP ${response.statusCode()} while downloading")
    }
    response.body()
}

private val ManagedDownloadHeaders = setOf("connection", "content-length", "expect", "host", "range", "upgrade")

internal fun HttpRequest.Builder.addDownloadRequestHeaders(headers: Map<String, String>) {
    headers.withDefaultDownloadHeaders().forEach { (key, value) ->
        if (key.isNotBlank() && value.isNotBlank() && key.lowercase(Locale.ROOT) !in ManagedDownloadHeaders) {
            header(key, value)
        }
    }
}
