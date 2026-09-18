package com.nuvio.app.features.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.discord.DiscordAuth
import com.nuvio.app.features.discord.DiscordAuthUiState
import com.nuvio.app.features.discord.DiscordConnectionMode
import com.nuvio.app.features.discord.DiscordGatewayStatus
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.discord_connect_action
import nuvio.composeapp.generated.resources.discord_connect_description
import nuvio.composeapp.generated.resources.discord_connected_as
import nuvio.composeapp.generated.resources.discord_connected_description
import nuvio.composeapp.generated.resources.discord_disconnect
import nuvio.composeapp.generated.resources.discord_disconnect_description
import nuvio.composeapp.generated.resources.discord_disconnect_title
import nuvio.composeapp.generated.resources.discord_gateway_error
import nuvio.composeapp.generated.resources.discord_gateway_offline
import nuvio.composeapp.generated.resources.discord_gateway_online
import nuvio.composeapp.generated.resources.discord_gateway_status_label
import nuvio.composeapp.generated.resources.discord_rich_presence_title
import nuvio.composeapp.generated.resources.discord_rich_presence_description
import nuvio.composeapp.generated.resources.discord_token_dialog_description
import nuvio.composeapp.generated.resources.discord_token_dialog_failed
import nuvio.composeapp.generated.resources.discord_token_dialog_title
import nuvio.composeapp.generated.resources.discord_token_dialog_verifying
import nuvio.composeapp.generated.resources.discord_website
import org.jetbrains.compose.resources.stringResource

/** Discord brand blurple. */
internal val DiscordBrandColor = Color(0xFF5865F2)

private val DiscordCardShape = RoundedCornerShape(NuvioTokens.Radius.xl)

/**
 * Discord Rich Presence connection card, rendered inside the Integrations settings page. Mirrors
 * the tracking provider cards: brand gradient, connected identity with avatar, and a disconnect
 * confirmation dialog.
 */
@Composable
internal fun DiscordConnectionCard(
    isTablet: Boolean,
    modifier: Modifier = Modifier,
) {
    val uiState by remember {
        DiscordAuth.ensureLoaded()
        DiscordAuth.uiState
    }.collectAsStateWithLifecycle()

    var showTokenDialog by rememberSaveable { mutableStateOf(false) }
    var showDisconnectDialog by rememberSaveable { mutableStateOf(false) }

    // Close the token dialog once the session lands (login succeeded).
    LaunchedEffect(uiState.mode) {
        if (uiState.mode == DiscordConnectionMode.CONNECTED) {
            showTokenDialog = false
        }
    }

    val tokens = MaterialTheme.nuvio
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(DiscordCardShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF151823), Color(0xFF23253A), Color(0xFF5865F2)),
                ),
            )
            .border(
                width = tokens.borders.hairline,
                color = Color.White.copy(alpha = 0.2f),
                shape = DiscordCardShape,
            ),
    ) {
        Image(
            painter = discordLogoPainter(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(150.dp)
                .alpha(0.08f),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Discord",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (uiState.mode == DiscordConnectionMode.CONNECTED) {
                    DiscordStatusChip(gatewayStatus = uiState.gatewayStatus)
                }
            }

            when (uiState.mode) {
                DiscordConnectionMode.CONNECTED -> {
                    DiscordConnectedIdentity(
                        uiState = uiState,
                    )
                    DiscordRichPresenceToggleRow(uiState = uiState)
                    TextButton(
                        onClick = { showDisconnectDialog = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White.copy(alpha = 0.84f),
                        ),
                    ) {
                        Text(text = stringResource(Res.string.discord_disconnect))
                    }
                }

                DiscordConnectionMode.DISCONNECTED -> {
                    Text(
                        text = stringResource(Res.string.discord_connect_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                    val uriHandler = LocalUriHandler.current
                    val scope = rememberCoroutineScope()
                    Button(
                        onClick = {
                            val url = DiscordAuth.oAuthAuthorizeUrl()
                            if (url != null) {
                                runCatching { uriHandler.openUri(url) }
                            }
                        },
                        enabled = !uiState.isVerifying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF171717),
                            disabledContainerColor = Color.White.copy(alpha = 0.34f),
                        ),
                    ) {
                        if (uiState.isVerifying) {
                            NuvioLoadingIndicator(
                                color = Color(0xFF171717),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text = "Sign in with Discord")
                    }
                    OutlinedButton(
                        onClick = { showTokenDialog = true },
                        enabled = !uiState.isVerifying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(text = "Enter token manually")
                    }
                    uiState.error?.let {
                        Text(
                            text = when (it) {
                                com.nuvio.app.features.discord.DiscordAuthError.MISSING_CLIENT_ID -> "Discord client not configured"
                                else -> stringResource(Res.string.discord_token_dialog_failed)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF6B6B),
                        )
                    }
                }
            }
        }
    }

    if (showTokenDialog) {
        DiscordTokenDialog(
            isVerifying = uiState.isVerifying,
            errorMessage = uiState.error?.let {
                stringResource(Res.string.discord_token_dialog_failed)
            },
            onDismiss = { showTokenDialog = false },
        )
    }

    if (showDisconnectDialog) {
        DiscordDisconnectDialog(
            onConfirm = {
                showDisconnectDialog = false
                DiscordAuth.disconnect()
            },
            onDismiss = { showDisconnectDialog = false },
        )
    }
}

@Composable
private fun DiscordConnectedIdentity(uiState: DiscordAuthUiState) {
    val displayName = uiState.user?.globalName?.takeIf { it.isNotBlank() }
        ?: uiState.user?.username
        ?: stringResource(Res.string.discord_connected_as)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        uiState.user?.avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.discord_connected_description),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.76f),
            )
        }
    }
}

@Composable
private fun DiscordRichPresenceToggleRow(uiState: DiscordAuthUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(NuvioTokens.Radius.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(Res.string.discord_rich_presence_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(Res.string.discord_rich_presence_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
            Switch(
                checked = uiState.rpcEnabled,
                onCheckedChange = { DiscordAuth.setRpcEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Color.White,
                    checkedThumbColor = DiscordBrandColor,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.24f),
                    uncheckedThumbColor = Color.White,
                ),
            )
        }
    }
}

@Composable
private fun DiscordStatusChip(gatewayStatus: DiscordGatewayStatus) {
    val label = stringResource(
        when (gatewayStatus) {
            DiscordGatewayStatus.ONLINE -> Res.string.discord_gateway_online
            DiscordGatewayStatus.CONNECTING -> Res.string.discord_gateway_status_label
            DiscordGatewayStatus.ERROR -> Res.string.discord_gateway_error
            DiscordGatewayStatus.OFFLINE -> Res.string.discord_gateway_offline
        },
    )
    val dotColor = when (gatewayStatus) {
        DiscordGatewayStatus.ONLINE -> Color(0xFF3BA55D)
        DiscordGatewayStatus.CONNECTING -> Color(0xFFFAA61A)
        DiscordGatewayStatus.ERROR -> Color(0xFFED4245)
        DiscordGatewayStatus.OFFLINE -> Color(0xFF747F8D)
    }
    Surface(
        color = Color.White.copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscordTokenDialog(
    isVerifying: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf("") }
    val isBusy = isVerifying

    BasicAlertDialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Image(
                        painter = discordLogoPainter(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = stringResource(Res.string.discord_token_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = stringResource(Res.string.discord_token_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        runCatching { uriHandler.openUri(DISCORD_LOGIN_URL) }
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = DiscordBrandColor),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(Res.string.discord_website))
                }

                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    singleLine = true,
                    label = { Text("Token") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DiscordBrandColor.copy(alpha = 0.75f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                    ),
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (isBusy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NuvioLoadingIndicator(modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(Res.string.discord_token_dialog_verifying),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isBusy,
                    ) {
                        Text(text = stringResource(Res.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            scope.launch { DiscordAuth.connectWithToken(draft) }
                        },
                        enabled = !isBusy && draft.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DiscordBrandColor,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(text = stringResource(Res.string.discord_connect_action))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscordDisconnectDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MaterialTheme.nuvio.components.dialogMaxWidth),
            shape = MaterialTheme.nuvio.shapes.dialog,
            color = MaterialTheme.nuvio.colors.surfaceDialog,
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.nuvio.spacing.dialogPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.discord_disconnect_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.nuvio.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(Res.string.discord_disconnect_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.nuvio.colors.textMuted,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(Res.string.action_cancel))
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(text = stringResource(Res.string.discord_disconnect))
                    }
                }
            }
        }
    }
}

private const val DISCORD_LOGIN_URL = "https://discord.com/login"
