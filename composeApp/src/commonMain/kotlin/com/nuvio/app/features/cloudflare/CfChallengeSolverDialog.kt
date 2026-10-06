package com.nuvio.app.features.cloudflare

import androidx.compose.runtime.Composable

/**
 * Platform WebView that loads the given host's Cloudflare challenge so the user can solve it. When a
 * `cf_clearance` cookie appears for [host], it calls [CfClearanceRepository.putClearance] with the
 * cookie and [CF_WEBVIEW_UA], then invokes [onSolved]. [onDismiss] fires if the user backs out.
 *
 * Only Android has a real implementation; other platforms are no-ops (they immediately dismiss).
 */
@Composable
expect fun CfChallengeSolverDialog(
    host: String,
    onSolved: () -> Unit,
    onDismiss: () -> Unit,
)
