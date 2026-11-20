package com.nuvio.app.features.downloads

import com.nuvio.app.core.storage.DesktopStorage
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals

class DesktopDownloadsTest {
    @Test
    fun resumesDirectDownloadAndPreservesProviderHeaders() = runBlocking {
        val video = "video-bytes".encodeToByteArray()
        withServer { server ->
            server.createContext("/episode.mp4") { exchange ->
                if (exchange.requestHeaders.getFirst("Range") == "bytes=5-" &&
                    exchange.requestHeaders.getFirst("Referer") == "https://provider.example/"
                ) {
                    exchange.responseHeaders.set("Content-Range", "bytes 5-${video.lastIndex}/${video.size}")
                    exchange.respond(206, video.copyOfRange(5, video.size))
                } else {
                    exchange.respond(403, byteArrayOf())
                }
            }
            server.start()

            val result = download(
                url = server.url("/episode.mp4"),
                headers = mapOf("Referer" to "https://provider.example/", "Connection" to "keep-alive"),
                existingBytes = video.copyOfRange(0, 5),
            )

            assertContentEquals(video, result)
        }
    }

    @Test
    fun downloadsVideoSegmentsForAnExtensionlessHlsUrl() = runBlocking {
        val first = "first-segment".encodeToByteArray()
        val second = "second-segment".encodeToByteArray()
        withServer { server ->
            server.createContext("/stream") { exchange ->
                exchange.responseHeaders.set("Content-Type", "application/vnd.apple.mpegurl")
                exchange.respond(
                    200,
                    """
                        #EXTM3U
                        #EXTINF:10,
                        1.ts
                        #EXTINF:10,
                        2.ts
                        #EXT-X-ENDLIST
                    """.trimIndent().encodeToByteArray(),
                )
            }
            server.createContext("/1.ts") { it.respond(200, first) }
            server.createContext("/2.ts") { it.respond(200, second) }
            server.start()

            assertContentEquals(first + second, download(server.url("/stream")))
        }
    }

    private suspend fun withServer(test: suspend (HttpServer) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            test(server)
        } finally {
            server.stop(0)
        }
    }

    private suspend fun download(
        url: String,
        headers: Map<String, String> = emptyMap(),
        existingBytes: ByteArray? = null,
    ): ByteArray {
        val id = UUID.randomUUID().toString()
        val fileName = "test-$id.mp4"
        val directory = DesktopStorage.rootDir.resolve("downloads").toFile().apply { mkdirs() }
        val partial = File(directory, "$fileName.part")
        existingBytes?.let(partial::writeBytes)
        val result = CompletableDeferred<String>()
        val item = DownloadItem(
            id = id,
            contentType = "series",
            parentMetaId = "anilist:1",
            parentMetaType = "series",
            videoId = "anilist:1:1:1",
            title = "Test",
            streamTitle = "Local video",
            providerName = "Test server",
            sourceUrl = url,
            sourceHeaders = headers,
            fileName = fileName,
            status = DownloadStatus.Downloading,
            createdAtEpochMs = 0,
            updatedAtEpochMs = 0,
        )
        val handle = DownloadsPlatformDownloader.start(
            DownloadPlatformRequest(item),
            onProgress = { _, _ -> },
            onSuccess = { localFileUri, _ -> result.complete(localFileUri) },
            onFailure = { result.completeExceptionally(AssertionError(it)) },
            onPaused = { result.cancel() },
        )
        return try {
            File(URI(withTimeout(10_000) { result.await() })).readBytes()
        } finally {
            handle.cancel()
            partial.delete()
            File(directory, fileName).delete()
        }
    }

    private fun HttpServer.url(path: String): String = "http://127.0.0.1:${address.port}$path"

    private fun HttpExchange.respond(status: Int, bytes: ByteArray) {
        use {
            sendResponseHeaders(status, bytes.size.toLong())
            responseBody.write(bytes)
        }
    }
}
