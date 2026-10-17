package com.nuvio.app.features.details.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

@Composable
actual fun AnimeCommunityWebView(
    config: AnimeCommunityCommentsConfig,
    colorScheme: AnimeCommunityColorScheme,
    modifier: Modifier,
) {
    val initialHtml = config.initialHtml(colorScheme)
    UIKitView(
        modifier = modifier,
        factory = {
            WKWebView(
                frame = CGRectZero,
                configuration = WKWebViewConfiguration(),
            ).apply {
                opaque = false
                backgroundColor = null
                loadHTMLString(
                    string = initialHtml,
                    baseURL = NSURL(string = "https://theanimecommunity.com/"),
                )
                accessibilityIdentifier = config.signature
            }
        },
        update = { webView ->
            if (webView.accessibilityIdentifier != config.signature) {
                webView.accessibilityIdentifier = config.signature
                webView.evaluateJavaScript(config.updateScript(colorScheme), completionHandler = null)
            }
        },
    )
}
