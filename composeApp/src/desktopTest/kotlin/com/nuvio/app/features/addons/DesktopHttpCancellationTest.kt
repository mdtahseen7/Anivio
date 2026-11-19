package com.nuvio.app.features.addons

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request

class DesktopHttpCancellationTest {
    @Test
    fun consumesSuccessfulResponse() = runBlocking {
        withServer({ socket ->
            socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok".toByteArray())
        }) { url ->
            val client = OkHttpClient()
            try {
                assertEquals("ok", client.newCall(Request.Builder().url(url).build()).awaitBody { requireNotNull(it.body).string() })
            } finally {
                client.dispatcher.executorService.shutdownNow()
                client.connectionPool.evictAll()
            }
        }
    }

    @Test
    fun cancellationClosesSocketWhileReadingBody() = runBlocking {
        val started = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        withServer({ socket ->
            socket.soTimeout = 5_000
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\nx".toByteArray())
                flush()
            }
            started.countDown()
            if (socket.getInputStream().read() == -1) disconnected.countDown()
        }) { url ->
            val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()
            try {
                val call = client.newCall(Request.Builder().url(url).build())
                val request = async(Dispatchers.IO) { call.awaitBody { requireNotNull(it.body).string() } }
                assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
                withTimeout(2_000) { request.cancelAndJoin() }
                assertTrue(call.isCanceled())
                assertTrue(withContext(Dispatchers.IO) { disconnected.await(2, TimeUnit.SECONDS) })
            } finally {
                client.dispatcher.executorService.shutdownNow()
                client.connectionPool.evictAll()
            }
        }
    }

    @Test
    fun totalDeadlineStopsContinuouslyTricklingBody() = runBlocking {
        withServer({ socket ->
            val output = socket.getOutputStream()
            output.write("HTTP/1.1 200 OK\r\nContent-Length: 10000\r\n\r\n".toByteArray())
            repeat(100) {
                output.write('x'.code)
                output.flush()
                Thread.sleep(30)
            }
        }) { url ->
            val client = OkHttpClient.Builder()
                .readTimeout(2, TimeUnit.SECONDS)
                .callTimeout(500, TimeUnit.MILLISECONDS)
                .build()
            try {
                withTimeout(5_000) {
                    assertFailsWith<IOException> {
                        client.newCall(Request.Builder().url(url).build()).awaitBody { requireNotNull(it.body).string() }
                    }
                }
            } finally {
                client.dispatcher.executorService.shutdownNow()
                client.connectionPool.evictAll()
            }
        }
    }

    private suspend fun withServer(serve: (Socket) -> Unit, test: suspend (String) -> Unit) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val worker = thread(isDaemon = true) {
                try {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) { /* consume request headers */ }
                        serve(socket)
                    }
                } catch (_: IOException) {
                    // Expected when the request is cancelled or its deadline expires.
                }
            }
            try {
                test("http://127.0.0.1:${server.localPort}/")
            } finally {
                server.close()
                withContext(Dispatchers.IO) { worker.join(5_000) }
            }
        }
    }
}
