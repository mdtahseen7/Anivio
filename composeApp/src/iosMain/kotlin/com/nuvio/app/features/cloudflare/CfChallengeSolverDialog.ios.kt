package com.nuvio.app.features.cloudflare

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** iOS has no in-app WebView challenge solver yet; dismiss immediately. */
@Composable
actual fun CfChallengeSolverDialog(
    host: String,
    onSolved: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(host) { onDismiss() }
}
