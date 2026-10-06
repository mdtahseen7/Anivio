package com.nuvio.app.features.cloudflare

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Loads `https://<host>/` in a WebView carrying [CF_WEBVIEW_UA] and watches the cookie jar; as soon
 * as a `cf_clearance` cookie is present it records the clearance and dismisses. The WebView lives
 * only for the lifetime of this dialog — opened on demand, torn down the moment the user solves it
 * or backs out.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun CfChallengeSolverDialog(
    host: String,
    onSolved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cleanHost = remember(host) { cfHostKey(host) }
    val currentOnSolved by rememberUpdatedState(onSolved)
    var loading by remember { mutableStateOf(true) }
    var done by remember { mutableStateOf(false) }

    // Poll the cookie jar after each page settles; cf_clearance is set by the challenge JS.
    fun captureIfCleared(): Boolean {
        if (done || cleanHost.isEmpty()) return false
        val cookie = CookieManager.getInstance().getCookie("https://$cleanHost/") ?: return false
        if (!cookie.contains("cf_clearance=")) return false
        done = true
        CfClearanceRepository.putClearance(
            host = cleanHost,
            cookie = cookie,
            userAgent = CF_WEBVIEW_UA,
        )
        currentOnSolved()
        return true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Text(
                            text = "Clear Cloudflare",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = cleanHost,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            CookieManager.getInstance().setAcceptCookie(true)
                            WebView(ctx).apply {
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = CF_WEBVIEW_UA
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        loading = false
                                        CookieManager.getInstance().flush()
                                        captureIfCleared()
                                    }
                                    override fun doUpdateVisitedHistory(
                                        view: WebView?,
                                        url: String?,
                                        isReload: Boolean,
                                    ) {
                                        // Challenge pages redirect once solved; re-check the jar.
                                        captureIfCleared()
                                    }
                                }
                                if (cleanHost.isNotEmpty()) loadUrl("https://$cleanHost/")
                            }
                        },
                    )
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                Text(
                    text = "Solve the “I’m human” check above. This closes automatically once cleared.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }

    // Belt-and-suspenders: also try once on first composition in case cookies survived from a prior visit.
    DisposableEffect(cleanHost) {
        captureIfCleared()
        onDispose { }
    }
}
