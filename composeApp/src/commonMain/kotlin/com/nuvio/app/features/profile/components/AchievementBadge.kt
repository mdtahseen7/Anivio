package com.nuvio.app.features.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.profile.Achievement
import com.nuvio.app.features.profile.AchievementTier

/** Maps an achievement [iconKey] to a Material icon. */
fun achievementIcon(iconKey: String): ImageVector = when (iconKey) {
    "play" -> Icons.Rounded.PlayArrow
    "episodes" -> Icons.Rounded.Videocam
    "check" -> Icons.Rounded.CheckCircle
    "movie" -> Icons.Rounded.Movie
    "mountain" -> Icons.Rounded.Terrain
    "flame" -> Icons.Rounded.LocalFireDepartment
    "calendar" -> Icons.Rounded.CalendarMonth
    "run" -> Icons.Rounded.DirectionsRun
    "moon" -> Icons.Rounded.Bedtime
    "sunrise" -> Icons.Rounded.WbSunny
    "weekend" -> Icons.Rounded.Weekend
    "compass" -> Icons.Rounded.Explore
    "building" -> Icons.Rounded.Business
    "trophy" -> Icons.Rounded.EmojiEvents
    else -> Icons.Rounded.AutoAwesome
}

/** Tier accent color: bronze/silver/gold, or the theme accent for untiered badges. */
@Composable
fun tierColor(tier: AchievementTier): Color = when (tier) {
    AchievementTier.BRONZE -> Color(0xFFCD7F32)
    AchievementTier.SILVER -> Color(0xFFC0C7D0)
    AchievementTier.GOLD -> Color(0xFFF5C542)
    AchievementTier.NONE -> MaterialTheme.nuvio.colors.accent
}

/**
 * A single achievement badge: a colored medallion when unlocked, greyscale + a lock overlay with a
 * progress ring when still in progress.
 */
@Composable
fun AchievementBadge(
    achievement: Achievement,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val accent = tierColor(achievement.tier)
    val unlocked = achievement.unlocked

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tokens.colors.surfaceElevated)
            .border(
                width = if (unlocked) 1.dp else 0.dp,
                color = if (unlocked) accent.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!unlocked) {
                LevelRing(
                    progress = achievement.progress,
                    ringSize = 56.dp,
                    stroke = 4.dp,
                    ringBrush = Brush.sweepGradient(listOf(accent.copy(alpha = 0.7f), accent)),
                ) {}
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (unlocked) {
                            Brush.linearGradient(listOf(accent.copy(alpha = 0.9f), accent.copy(alpha = 0.55f)))
                        } else {
                            Brush.linearGradient(listOf(tokens.colors.surfaceCard, tokens.colors.surfaceCard))
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = achievementIcon(achievement.iconKey),
                    contentDescription = achievement.title,
                    tint = if (unlocked) Color.White else tokens.colors.textMuted,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { if (!unlocked) alpha = 0.6f },
                )
            }
            if (!unlocked) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(tokens.colors.surfaceDialog)
                        .align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Lock, null, tint = tokens.colors.textMuted, modifier = Modifier.size(11.dp))
                }
            }
        }
        Text(
            achievement.title,
            color = if (unlocked) tokens.colors.textPrimary else tokens.colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The detail content shown in the achievement bottom sheet. */
@Composable
fun AchievementDetail(achievement: Achievement, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val accent = tierColor(achievement.tier)
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    if (achievement.unlocked) {
                        Brush.linearGradient(listOf(accent.copy(alpha = 0.9f), accent.copy(alpha = 0.5f)))
                    } else {
                        Brush.linearGradient(listOf(tokens.colors.surfaceCard, tokens.colors.surfaceCard))
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                achievementIcon(achievement.iconKey),
                null,
                tint = if (achievement.unlocked) Color.White else tokens.colors.textMuted,
                modifier = Modifier.size(34.dp),
            )
        }
        Text(achievement.title, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (achievement.tier != AchievementTier.NONE) {
            ProfilePill(achievement.tier.name.lowercase().replaceFirstChar { it.uppercase() }, color = accent)
        }
        Text(
            achievement.description,
            color = tokens.colors.textSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!achievement.unlocked) {
            Spacer(Modifier.height(4.dp))
            ProfileBarRow(
                label = "Progress",
                value = "${achievement.current} / ${achievement.target}",
                fraction = achievement.progress,
                barColor = accent,
            )
        } else {
            ProfilePill("Unlocked", color = tokens.colors.success)
        }
    }
}
