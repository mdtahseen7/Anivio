package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class PolicySection(
    val titleRes: StringResource,
    val bodyRes: StringResource,
)

/**
 * The policy, in the order it is worth reading: what never leaves the device first, then everything
 * that can, then what the app deliberately does not do.
 */
private val policySections = listOf(
    PolicySection(
        titleRes = Res.string.privacy_section_on_device_title,
        bodyRes = Res.string.privacy_section_on_device_body,
    ),
    PolicySection(
        titleRes = Res.string.privacy_section_accounts_title,
        bodyRes = Res.string.privacy_section_accounts_body,
    ),
    PolicySection(
        titleRes = Res.string.privacy_section_sync_title,
        bodyRes = Res.string.privacy_section_sync_body,
    ),
    PolicySection(
        titleRes = Res.string.privacy_section_metadata_title,
        bodyRes = Res.string.privacy_section_metadata_body,
    ),
    PolicySection(
        titleRes = Res.string.privacy_section_addons_title,
        bodyRes = Res.string.privacy_section_addons_body,
    ),
    PolicySection(
        titleRes = Res.string.privacy_section_p2p_title,
        bodyRes = Res.string.privacy_section_p2p_body,
    ),
    PolicySection(
        titleRes = Res.string.privacy_section_crash_title,
        bodyRes = Res.string.privacy_section_crash_body,
    ),
    PolicySection(
        titleRes = Res.string.privacy_section_ads_title,
        bodyRes = Res.string.privacy_section_ads_body,
    ),
    PolicySection(
        titleRes = Res.string.privacy_section_control_title,
        bodyRes = Res.string.privacy_section_control_body,
    ),
)

@Composable
fun PrivacyPolicySettingsScreen(
    onBack: () -> Unit,
) {
    NuvioScreen(
        modifier = Modifier.fillMaxSize(),
    ) {
        stickyHeader {
            NuvioScreenHeader(
                title = stringResource(Res.string.compose_settings_page_privacy_policy),
                onBack = onBack,
            )
        }
        privacyPolicyContent(isTablet = false)
    }
}

internal fun LazyListScope.privacyPolicyContent(
    isTablet: Boolean,
) {
    item {
        PrivacyPolicyBody(isTablet = isTablet)
    }
}

@Composable
private fun PrivacyPolicyBody(
    isTablet: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isTablet) 28.dp else 24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.privacy_last_updated),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.privacy_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        policySections.forEach { section ->
            PlainSettingsStack(
                title = stringResource(section.titleRes),
                isTablet = isTablet,
            ) {
                Text(
                    text = stringResource(section.bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PlainSettingsStack(
            title = stringResource(Res.string.privacy_section_contact_title),
            isTablet = isTablet,
        ) {
            Text(
                text = stringResource(Res.string.privacy_section_contact_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
