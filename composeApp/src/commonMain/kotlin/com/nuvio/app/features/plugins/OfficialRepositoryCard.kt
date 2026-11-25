package com.nuvio.app.features.plugins

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioLoadingIndicator

@Composable
internal fun OfficialRepositoryBadge() {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(50), color = colors.primary.copy(alpha = 0.13f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(12.dp), tint = colors.primary)
            Text("Official", style = MaterialTheme.typography.labelSmall, color = colors.primary)
        }
    }
}

@Composable
internal fun OfficialRepositoryCard(
    repository: OfficialPluginRepository,
    actionLabel: String,
    onAction: (() -> Unit)?,
    busy: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = if (repository.manga) colors.secondaryContainer else colors.primaryContainer,
                    contentColor = if (repository.manga) colors.onSecondaryContainer else colors.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (repository.manga) Icons.AutoMirrored.Rounded.MenuBook
                            else Icons.Rounded.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = repository.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (repository.manga) "Chapter and page sources" else "Episode and movie sources",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OfficialRepositoryBadge()
                if (onAction != null) {
                    TextButton(onClick = onAction, enabled = enabled && !busy) {
                        if (busy) {
                            NuvioLoadingIndicator(modifier = Modifier.padding(end = 8.dp), size = 14.dp)
                        }
                        Text(actionLabel)
                    }
                } else {
                    Text(
                        text = actionLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
