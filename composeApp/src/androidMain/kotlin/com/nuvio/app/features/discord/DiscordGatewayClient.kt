package com.nuvio.app.features.discord

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Discord Rich Presence over the user Gateway (Kizzy-style), ported from Luna-Native.
 *
 * Connects to the Discord Gateway with the captured account token, keeps the heartbeat alive and
 * pushes presence (op 3) updates. There is no official mobile RPC API, so this drives presence by
 * acting as the user's client. Automating a user account is against Discord's ToS; presence-only
 * use is generally tolerated but carries a small account-action risk.
 */
internal class DiscordGatewayClient(
    private val scope: CoroutineScope,
    private val onStatus: (DiscordGatewayStatus) -> Unit,
) {
    private val log = Logger.withTag("DiscordRpc")
    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(0, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var token: String? = null
    private var heartbeatJob: kotlinx.coroutines.Job? = null
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private var reconnectAttempts = 0
    private var intentionalClose = false
    private var lastSequence: Long? = null
    private var pendingPresence: JsonObject? = null

    @Volatile
    private var socketOpen = false

    @Volatile
    private var identified = false

    fun connect(accountToken: String) {
        token = accountToken
        intentionalClose = false
        if (socketOpen) return
        openSocket()
    }

    fun disconnect() {
        intentionalClose = true
        reconnectAttempts = 0
        reconnectJob?.cancel()
        reconnectJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        pendingPresence = null
        lastSequence = null
        identified = false
        token = null
        try {
            webSocket?.close(1000, null)
        } catch (_: Exception) {
        }
        webSocket = null
        socketOpen = false
        onStatus(DiscordGatewayStatus.OFFLINE)
    }

    /** Push (or queue until READY) a full presence payload. */
    fun setPresence(presence: JsonObject) {
        pendingPresence = presence
        if (socketOpen && identified) {
            sendPresence()
        } else if (!socketOpen) {
            token?.let(::connect)
        }
    }

    /** Clear the activity but keep the connection alive. */
    fun clearPresence() {
        pendingPresence = IDLE_PRESENCE
        if (socketOpen && identified) {
            sendPresence()
        }
    }

    fun shutdown() {
        disconnect()
        client.dispatcher.executorService.shutdown()
    }

    private fun openSocket() {
        if (socketOpen) return
        val accountToken = token ?: return
        identified = false
        onStatus(DiscordGatewayStatus.CONNECTING)
        val request = Request.Builder()
            .url(GATEWAY_URL)
            .header("User-Agent", GATEWAY_USER_AGENT)
            .build()
        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    socketOpen = true
                    log.i { "gateway socket open" }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleGatewayMessage(accountToken, text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    socketOpen = false
                    identified = false
                    log.i { "gateway failure: ${t.message}" }
                    onStatus(DiscordGatewayStatus.ERROR)
                    scheduleReconnect()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    socketOpen = false
                    identified = false
                    log.i { "gateway closed code=$code" }
                    if (!intentionalClose) scheduleReconnect()
                }
            },
        )
    }

    private fun handleGatewayMessage(accountToken: String, text: String) {
        val payload = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val op = payload["op"]?.jsonPrimitive?.content?.toIntOrNull() ?: return
        payload["s"]?.jsonPrimitive?.longOrNull?.let { seq -> lastSequence = seq }
        when (op) {
            OP_HELLO -> {
                val interval = payload["d"]
                    ?.jsonObject?.get("heartbeat_interval")
                    ?.jsonPrimitive?.longOrNull
                    ?: HEARTBEAT_FALLBACK_MS
                startHeartbeat(interval)
                send(
                    buildJsonObject {
                        put("op", OP_IDENTIFY)
                        put(
                            "d",
                            buildJsonObject {
                                put("token", accountToken)
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put("os", "Windows")
                                        put("browser", "Discord Client")
                                        put("release_channel", "stable")
                                        put("client_version", "1.0.9034")
                                        put("os_version", "10.0.19045")
                                        put("os_arch", "x64")
                                        put("system_locale", "en-US")
                                    },
                                )
                                put("compress", false)
                            },
                        )
                    },
                )
            }

            OP_RECONNECT -> {
                log.i { "gateway requested reconnect" }
                reconnectSoon()
            }

            OP_INVALID_SESSION -> {
                log.i { "gateway invalid session" }
                reconnectSoon()
            }

            OP_DISPATCH -> when (payload["t"]?.jsonPrimitive?.content) {
                "READY" -> {
                    identified = true
                    reconnectAttempts = 0
                    onStatus(DiscordGatewayStatus.ONLINE)
                    pendingPresence?.let { sendPresence() }
                }
            }
        }
    }

    private fun startHeartbeat(intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (true) {
                send(
                    buildJsonObject {
                        put("op", OP_HEARTBEAT)
                        put("d", lastSequence ?: 0L)
                    },
                )
                delay(intervalMs)
            }
        }
    }

    private fun sendPresence() {
        val presence = pendingPresence ?: return
        send(
            buildJsonObject {
                put("op", OP_PRESENCE_UPDATE)
                put("d", presence)
            },
        )
    }

    private fun send(payload: JsonObject) {
        val socket = webSocket ?: return
        if (!socketOpen) return
        runCatching { socket.send(payload.toString()) }
    }

    private fun scheduleReconnect() {
        if (intentionalClose) return
        reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(6)
        val backoff = (2000L shl (reconnectAttempts - 1)).coerceAtMost(60_000L)
        reconnectSoon(backoff)
    }

    private fun reconnectSoon(delayMs: Long = 1_500L) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(delayMs)
            if (!intentionalClose && token != null) openSocket()
        }
    }

    companion object {
        private const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"
        private const val GATEWAY_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "discord/1.0.9034 Chrome/120.0.6099.291 Electron/28.2.10 Safari/537.36"
        private const val OP_DISPATCH = 0
        private const val OP_HEARTBEAT = 1
        private const val OP_IDENTIFY = 2
        private const val OP_PRESENCE_UPDATE = 3
        private const val OP_HELLO = 10
        private const val OP_RECONNECT = 7
        private const val OP_INVALID_SESSION = 9
        private const val HEARTBEAT_FALLBACK_MS = 41_250L
        private const val ACTIVITY_WATCHING = 3

        private val IDLE_PRESENCE: JsonObject = buildJsonObject {
            put("since", 0)
            put("activities", JsonArray(emptyList()))
            put("status", "online")
            put("afk", false)
        }
    }
}
