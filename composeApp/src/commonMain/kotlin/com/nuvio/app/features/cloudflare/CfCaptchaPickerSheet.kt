package com.nuvio.app.features.cloudflare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.cloudflare_picker_cleared
import nuvio.composeapp.generated.resources.cloudflare_picker_empty
import nuvio.composeapp.generated.resources.cloudflare_picker_needs_check
import nuvio.composeapp.generated.resources.cloudflare_picker_subtitle
import nuvio.composeapp.generated.resources.cloudflare_picker_title
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom sheet listing captcha-gated sources (from [buildCfCaptchaProviders]). Tapping one opens the
 * [CfChallengeSolverDialog] for its host. Shared by the settings page and the source-selection
 * screen so the user can clear a Cloudflare check from either place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CfCaptchaPickerSheet(
    providers: List<CfCaptchaProvider>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = MaterialTheme.nuvio
    var solverHost by remember { mutableStateOf<String?>(null) }

    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = tokens.spacing.screenHorizontal,
                    end = tokens.spacing.screenHorizontal,
                    bottom = NuvioTokensSpacer,
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.cloudflare_picker_title),
                style = MaterialTheme.typography.titleLarge,
                color = tokens.colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.cloudflare_picker_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textSecondary,
            )
        }

        if (providers.isEmpty()) {
            Text(
                text = stringResource(Res.string.cloudflare_picker_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.screenHorizontal, vertical = 24.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = nuvioSafeBottomPadding(16.dp)),
            ) {
                items(providers, key = { it.host }) { provider ->
                    NuvioBottomSheetActionRow(
                        title = provider.label,
                        icon = Icons.Rounded.Shield,
                        onClick = { solverHost = provider.host },
                        trailingContent = {
                            if (provider.cleared) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = tokens.colors.accent,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(Res.string.cloudflare_picker_cleared),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tokens.colors.textSecondary,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            } else {
                                Text(
                                    text = stringResource(Res.string.cloudflare_picker_needs_check),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tokens.colors.textSecondary,
                                )
                            }
                        },
                    )
                    NuvioBottomSheetDivider()
                }
            }
        }
    }

    solverHost?.let { host ->
        CfChallengeSolverDialog(
            host = host,
            onSolved = { solverHost = null },
            onDismiss = { solverHost = null },
        )
    }
}

private val NuvioTokensSpacer = 12.dp
