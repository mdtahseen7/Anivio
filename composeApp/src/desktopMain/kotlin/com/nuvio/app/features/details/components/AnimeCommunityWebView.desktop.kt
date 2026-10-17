package com.nuvio.app.features.details.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Desktop currently keeps the details screen native; the full widget is available on mobile. */
@Composable
actual fun AnimeCommunityWebView(
    config: AnimeCommunityCommentsConfig,
    colorScheme: AnimeCommunityColorScheme,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Comments are available in the Android and iOS app.",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "The Anime Community widget needs a mobile WebView so spoilers, GIFs, replies, and sign-in remain fully interactive.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
