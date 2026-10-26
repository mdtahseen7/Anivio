package com.nuvio.app.features.addons

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.network_empty_response_body
import nuvio.composeapp.generated.resources.network_request_failed_http
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSUserDefaults

actual object AddonStorage {
    private const val addonUrlsKey = "installed_manifest_urls"
    private const val addonEnabledStatesKey = "installed_manifest_enabled_states"
    private const val addonFailureReasonsKey = "installed_manifest_failure_reasons"
    private const val preferredAddonUrlsKey = "preferred_addon_urls"

    actual fun loadInstalledAddonUrls(profileId: Int): List<String> =
        NSUserDefaults.standardUserDefaults
            .stringForKey("${addonUrlsKey}_$profileId")
            .orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

    actual fun saveInstalledAddonUrls(profileId: Int, urls: List<String>) {
        NSUserDefaults.standardUserDefaults.setObject(
            urls.joinToString(separator = "\n"),
            forKey = "${addonUrlsKey}_$profileId",
        )
    }

    actual fun loadAddonEnabledStates(profileId: Int): Map<String, Boolean> =
        NSUserDefaults.standardUserDefaults
            .stringForKey("${addonEnabledStatesKey}_$profileId")
            .orEmpty()
            .lineSequence()
            .mapNotNull(::parseEnabledStateLine)
            .toMap()

    actual fun saveAddonEnabledStates(profileId: Int, states: Map<String, Boolean>) {
        val payload = states.entries.joinToString(separator = "\n") { (url, enabled) ->
            "$url\t$enabled"
        }
        NSUserDefaults.standardUserDefaults.setObject(
            payload,
            forKey = "${addonEnabledStatesKey}_$profileId",
        )
    }

    actual fun loadAddonFailureReasons(profileId: Int): Map<String, String> =
        NSUserDefaults.standardUserDefaults
            .stringForKey("${addonFailureReasonsKey}_$profileId")
            .orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('\t')
                if (separator <= 0) null else {
                    val url = line.substring(0, separator).trim()
                    val reason = line.substring(separator + 1).trim()
                    url.takeIf { it.isNotEmpty() }?.let { it to reason }
                }
            }
            .toMap()

    actual fun saveAddonFailureReasons(profileId: Int, reasons: Map<String, String>) {
        val payload = reasons.entries.joinToString(separator = "\n") { (url, reason) ->
            "$url\t${reason.replace("\n", " ")}"
        }
        NSUserDefaults.standardUserDefaults.setObject(
            payload,
            forKey = "${addonFailureReasonsKey}_$profileId",
        )
    }

    actual fun loadPreferredAddonUrls(profileId: Int): Map<String, List<String>> =
        NSUserDefaults.standardUserDefaults
            .stringForKey("${preferredAddonUrlsKey}_$profileId")
            .orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('\t')
                if (separator <= 0) null else {
                    val type = line.substring(0, separator).trim()
                    val urls = line.substring(separator + 1)
                        .split('|')
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                    type.takeIf { it.isNotEmpty() }?.let { it to urls }
                }
            }
            .toMap()

    actual fun savePreferredAddonUrls(profileId: Int, preferences: Map<String, List<String>>) {
        val payload = preferences.entries.joinToString(separator = "\n") { (type, urls) ->
            "$type\t${urls.joinToString("|")}"
        }
        NSUserDefaults.standardUserDefaults.setObject(
            payload,
            forKey = "${preferredAddonUrlsKey}_$profileId",
        )
    }
}

internal actual fun clearAddonHttpCache() = Unit

private fun parseEnabledStateLine(line: String): Pair<String, Boolean>? {
    val url = line.substringBefore("\t").trim().takeIf { it.isNotEmpty() } ?: return null
    val rawEnabled = line.substringAfter("\t", "true").trim().lowercase()
    val enabled = when (rawEnabled) {
        "false" -> false
        else -> true
    }
    return url to enabled
}

private val addonHttpClient = HttpClient(Darwin) {
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 60_000
        socketTimeoutMillis = 60_000
    }
    expectSuccess = false
}

actual suspend fun httpGetText(url: String): String =
    addonHttpClient
        .get(url) {
            accept(ContentType.Application.Json)
        }
        .let { response ->
            val payload = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error(runBlocking { getString(Res.string.network_request_failed_http, response.status.value) })
            }
            if (payload.isBlank()) {
                throw IllegalStateException(runBlocking { getString(Res.string.network_empty_response_body) })
            }
            payload
        }

actual suspend fun httpPostJson(url: String, body: String): String =
    addonHttpClient
        .post(url) {
            accept(ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(body)
        }
        .let { response ->
            val payload = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error(runBlocking { getString(Res.string.network_request_failed_http, response.status.value) })
            }
            if (payload.isBlank()) {
                throw IllegalStateException(runBlocking { getString(Res.string.network_empty_response_body) })
            }
            payload
        }

actual suspend fun httpGetTextWithHeaders(
    url: String,
    headers: Map<String, String>,
): String =
    addonHttpClient
        .get(url) {
            accept(ContentType.Application.Json)
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }
        .let { response ->
            val payload = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error(runBlocking { getString(Res.string.network_request_failed_http, response.status.value) })
            }
            if (payload.isBlank()) {
                throw IllegalStateException(runBlocking { getString(Res.string.network_empty_response_body) })
            }
            payload
        }

actual suspend fun httpPostJsonWithHeaders(
    url: String,
    body: String,
    headers: Map<String, String>,
): String =
    addonHttpClient
        .post(url) {
            accept(ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            headers.forEach { (key, value) ->
                header(key, value)
            }
            setBody(body)
        }
        .let { response ->
            val payload = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error(runBlocking { getString(Res.string.network_request_failed_http, response.status.value) })
            }
            if (payload.isBlank()) {
                throw IllegalStateException(runBlocking { getString(Res.string.network_empty_response_body) })
            }
            payload
        }

actual suspend fun httpRequestRaw(
    method: String,
    url: String,
    headers: Map<String, String>,
    body: String,
    followRedirects: Boolean,
    maxResponseBodyBytes: Int,
): RawHttpResponse =
    addonHttpClient
        .request {
            url(url)
            this.method = HttpMethod.parse(method.uppercase())
            headers.forEach { (key, value) ->
                header(key, value)
            }
            if (this.method == HttpMethod.Post || this.method == HttpMethod.Put || this.method == HttpMethod.Patch) {
                setBody(body)
            }
        }
        .let { response ->
            RawHttpResponse(
                status = response.status.value,
                statusText = response.status.description,
                url = response.call.request.url.toString(),
                body = response.bodyAsText(),
                headers = response.headers.entries().associate { (name, values) ->
                    name.lowercase() to values.joinToString(",")
                },
            )
        }
