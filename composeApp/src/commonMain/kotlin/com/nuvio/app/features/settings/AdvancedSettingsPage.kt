package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingEnrichmentCache
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.settings_advanced_clear_cw_cache
import nuvio.composeapp.generated.resources.settings_advanced_clear_cw_cache_done
import nuvio.composeapp.generated.resources.settings_advanced_clear_cw_cache_subtitle
import nuvio.composeapp.generated.resources.settings_advanced_backup
import nuvio.composeapp.generated.resources.settings_advanced_backup_copy
import nuvio.composeapp.generated.resources.settings_advanced_backup_copy_done
import nuvio.composeapp.generated.resources.settings_advanced_backup_description
import nuvio.composeapp.generated.resources.settings_advanced_backup_export_file
import nuvio.composeapp.generated.resources.settings_advanced_backup_import_file
import nuvio.composeapp.generated.resources.settings_advanced_backup_import
import nuvio.composeapp.generated.resources.settings_advanced_backup_import_title
import nuvio.composeapp.generated.resources.settings_advanced_backup_import_hint
import nuvio.composeapp.generated.resources.settings_advanced_backup_restore
import nuvio.composeapp.generated.resources.settings_advanced_backup_invalid
import nuvio.composeapp.generated.resources.settings_advanced_remember_last_profile
import nuvio.composeapp.generated.resources.settings_advanced_remember_last_profile_description
import nuvio.composeapp.generated.resources.settings_advanced_section_cache
import nuvio.composeapp.generated.resources.settings_advanced_section_diagnostics
import nuvio.composeapp.generated.resources.settings_advanced_section_startup
import nuvio.composeapp.generated.resources.settings_advanced_sentry_reports
import nuvio.composeapp.generated.resources.settings_advanced_sentry_reports_subtitle
import nuvio.composeapp.generated.resources.sentry_disable_dialog_subtitle
import nuvio.composeapp.generated.resources.sentry_disable_dialog_title
import nuvio.composeapp.generated.resources.sentry_enable_dialog_subtitle
import nuvio.composeapp.generated.resources.sentry_enable_dialog_title
import nuvio.composeapp.generated.resources.sentry_help_body
import nuvio.composeapp.generated.resources.sentry_help_title
import nuvio.composeapp.generated.resources.sentry_keep_enabled
import nuvio.composeapp.generated.resources.sentry_not_sent_body
import nuvio.composeapp.generated.resources.sentry_not_sent_title
import nuvio.composeapp.generated.resources.sentry_sent_body
import nuvio.composeapp.generated.resources.sentry_sent_title
import nuvio.composeapp.generated.resources.sentry_turn_off
import nuvio.composeapp.generated.resources.sentry_turn_on
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.advancedSettingsContent(
    isTablet: Boolean,
    rememberLastProfileEnabled: Boolean,
) {
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_advanced_section_startup),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_advanced_remember_last_profile),
                    description = stringResource(Res.string.settings_advanced_remember_last_profile_description),
                    checked = rememberLastProfileEnabled,
                    isTablet = isTablet,
                    onCheckedChange = ProfileRepository::setRememberLastProfileEnabled,
                )
            }
        }
    }
    if (SentrySettingsRepository.isSupported) {
        item {
            val sentryEnabledFlow = remember {
                SentrySettingsRepository.ensureLoaded()
                SentrySettingsRepository.enabled
            }
            val sentryEnabled by sentryEnabledFlow.collectAsStateWithLifecycle()
            var showSentryDialog by rememberSaveable { mutableStateOf(false) }

            SettingsSection(
                title = stringResource(Res.string.settings_advanced_section_diagnostics),
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_advanced_sentry_reports),
                        description = stringResource(Res.string.settings_advanced_sentry_reports_subtitle),
                        checked = sentryEnabled,
                        isTablet = isTablet,
                        onCheckedChange = { showSentryDialog = true },
                    )
                }
            }

            if (showSentryDialog) {
                SentrySettingsDialog(
                    enabled = sentryEnabled,
                    onConfirm = {
                        SentrySettingsRepository.setEnabled(!sentryEnabled)
                    },
                    onDismiss = {
                        showSentryDialog = false
                    },
                )
            }
        }
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_advanced_section_cache),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                val scope = rememberCoroutineScope()
                var cleared by rememberSaveable { mutableStateOf(false) }
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_advanced_clear_cw_cache),
                    description = if (cleared) {
                        stringResource(Res.string.settings_advanced_clear_cw_cache_done)
                    } else {
                        stringResource(Res.string.settings_advanced_clear_cw_cache_subtitle)
                    },
                    isTablet = isTablet,
                    onClick = {
                        if (!cleared) {
                            ContinueWatchingEnrichmentCache.clearAll(ProfileRepository.activeProfileId)
                            cleared = true
                            scope.launch {
                                WatchProgressRepository.clearLocalAndForceSnapshotRefreshFromServer(
                                    ProfileRepository.activeProfileId,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
    item {
        val clipboardManager = LocalClipboardManager.current
        var copied by rememberSaveable { mutableStateOf(false) }
        var showImportDialog by rememberSaveable { mutableStateOf(false) }
        var importText by rememberSaveable { mutableStateOf("") }
        var importError by rememberSaveable { mutableStateOf<String?>(null) }
        var exportRequest by rememberSaveable { mutableStateOf(0) }
        var importRequest by rememberSaveable { mutableStateOf(0) }

        BackupFileActions(
            exportPayload = BackupRepository.exportToJson(),
            exportRequest = exportRequest,
            importRequest = importRequest,
            onImported = {
                importText = it
                importError = null
                showImportDialog = true
            },
            onError = { importError = it },
        )

        SettingsSection(
            title = stringResource(Res.string.settings_advanced_backup),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_advanced_backup_export_file),
                    description = stringResource(Res.string.settings_advanced_backup_description),
                    isTablet = isTablet,
                    onClick = { exportRequest += 1 },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_advanced_backup_import_file),
                    description = stringResource(Res.string.settings_advanced_backup_restore),
                    isTablet = isTablet,
                    onClick = { importRequest += 1 },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(
                        if (copied) Res.string.settings_advanced_backup_copy_done
                        else Res.string.settings_advanced_backup_copy,
                    ),
                    description = stringResource(Res.string.settings_advanced_backup_description),
                    isTablet = isTablet,
                    onClick = {
                        clipboardManager.setText(AnnotatedString(BackupRepository.exportToJson()))
                        copied = true
                    },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_advanced_backup_import),
                    description = stringResource(Res.string.settings_advanced_backup_restore),
                    isTablet = isTablet,
                    onClick = {
                        importText = clipboardManager.getText()?.text.orEmpty()
                        importError = null
                        showImportDialog = true
                    },
                )
            }
        }

        if (showImportDialog) {
            BackupImportDialog(
                importText = importText,
                error = importError,
                onTextChange = {
                    importText = it
                    importError = null
                },
                onConfirm = {
                    val result = BackupRepository.restoreFromJson(importText)
                    if (result.valid) {
                        showImportDialog = false
                        importText = ""
                    } else {
                        importError = result.message
                    }
                },
                onDismiss = {
                    showImportDialog = false
                    importText = ""
                    importError = null
                },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BackupImportDialog(
    importText: String,
    error: String?,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = tokens.colors.surfaceDialog,
            shape = tokens.shapes.dialog,
        ) {
            Column(modifier = Modifier.padding(tokens.spacing.dialogPadding)) {
                Text(
                    text = stringResource(Res.string.settings_advanced_backup_import_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(tokens.spacing.controlGap))
                OutlinedTextField(
                    value = importText,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    label = { Text(stringResource(Res.string.settings_advanced_backup_import_hint)) },
                    isError = error != null,
                )
                error?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = it.ifBlank { stringResource(Res.string.settings_advanced_backup_invalid) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(NuvioTokens.Space.s18))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = tokens.shapes.button,
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.colors.surfaceCard),
                    ) {
                        Text(stringResource(Res.string.action_cancel), color = tokens.colors.textPrimary)
                    }
                    Spacer(modifier = Modifier.width(NuvioTokens.Space.s12))
                    Button(
                        onClick = onConfirm,
                        enabled = importText.isNotBlank(),
                        shape = tokens.shapes.button,
                    ) {
                        Text(stringResource(Res.string.settings_advanced_backup_restore))
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SentrySettingsDialog(
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    BasicAlertDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = tokens.colors.surfaceDialog,
            shape = tokens.shapes.dialog,
        ) {
            Column(
                modifier = Modifier.padding(tokens.spacing.dialogPadding),
            ) {
                Text(
                    text = stringResource(
                        if (enabled) {
                            Res.string.sentry_disable_dialog_title
                        } else {
                            Res.string.sentry_enable_dialog_title
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(tokens.spacing.controlGap))
                Text(
                    text = stringResource(
                        if (enabled) {
                            Res.string.sentry_disable_dialog_subtitle
                        } else {
                            Res.string.sentry_enable_dialog_subtitle
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.colors.textMuted,
                )
                Spacer(modifier = Modifier.height(NuvioTokens.Space.s18))
                Column(
                    verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
                ) {
                    SentryInfoSection(
                        title = stringResource(Res.string.sentry_help_title),
                        body = stringResource(Res.string.sentry_help_body),
                    )
                    SentryInfoSection(
                        title = stringResource(Res.string.sentry_sent_title),
                        body = stringResource(Res.string.sentry_sent_body),
                    )
                    SentryInfoSection(
                        title = stringResource(Res.string.sentry_not_sent_title),
                        body = stringResource(Res.string.sentry_not_sent_body),
                    )
                }
                Spacer(modifier = Modifier.height(NuvioTokens.Space.s18))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = tokens.shapes.button,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.colors.surfaceCard,
                            contentColor = tokens.colors.textPrimary,
                        ),
                    ) {
                        Text(
                            text = stringResource(
                                if (enabled) {
                                    Res.string.sentry_keep_enabled
                                } else {
                                    Res.string.action_cancel
                                },
                            ),
                        )
                    }
                    Spacer(modifier = Modifier.width(NuvioTokens.Space.s10))
                    Button(
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        shape = tokens.shapes.button,
                    ) {
                        Text(
                            text = stringResource(
                                if (enabled) {
                                    Res.string.sentry_turn_off
                                } else {
                                    Res.string.sentry_turn_on
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SentryInfoSection(
    title: String,
    body: String,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
        )
    }
}
