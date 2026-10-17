package com.nuvio.app.features.details.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Keeps one JavaScript-enabled WebView alive for the comments tab. The embedded widget owns its
 * composer, spoiler controls, reactions, replies, and GIF rendering; Android only supplies the
 * title/episode configuration and the app's color tokens.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun AnimeCommunityWebView(
    config: AnimeCommunityCommentsConfig,
    colorScheme: AnimeCommunityColorScheme,
    modifier: Modifier,
) {
    val initialHtml = remember(config.signature, colorScheme) {
        config.initialHtml(colorScheme)
    }
    val webViewHolder = remember { WebViewHolder() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowContentAccess = true
                settings.allowFileAccess = true
                settings.mediaPlaybackRequiresUserGesture = true
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                loadDataWithBaseURL(
                    "https://theanimecommunity.com/",
                    initialHtml,
                    "text/html",
                    "UTF-8",
                    null,
                )
                webViewHolder.webView = this
                webViewHolder.signature = config.signature
            }
        },
        update = { webView ->
            webViewHolder.webView = webView
            if (webViewHolder.signature != config.signature) {
                webViewHolder.signature = config.signature
                webView.evaluateJavascript(config.updateScript(colorScheme), null)
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            webViewHolder.webView?.apply {
                stopLoading()
                webChromeClient = null
                destroy()
            }
            webViewHolder.webView = null
        }
    }
}

private class WebViewHolder {
    var webView: WebView? = null
    var signature: String? = null
}
