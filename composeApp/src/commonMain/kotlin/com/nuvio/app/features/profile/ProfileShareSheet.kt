package com.nuvio.app.features.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.profile.components.LevelRing
import kotlinx.coroutines.launch

/**
 * Bottom-sheet content showing a polished recap card plus a Share button. The card is rendered into
 * a graphics layer so it can be captured to an image and handed to the OS share sheet.
 */
@Composable
fun ProfileShareSheet(
    stats: ProfileStats,
    profileName: String,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Share your card",
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    // Record into the layer, then draw it to screen.
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(graphicsLayer)
                },
        ) {
            ShareCard(stats = stats, profileName = profileName)
        }

        Spacer(Modifier.height(20.dp))
        NuvioPrimaryButton(
            text = "Share",
            onClick = {
                scope.launch {
                    val bitmap = runCatching { graphicsLayer.toImageBitmap() }.getOrNull()
                    if (bitmap != null) {
                        ProfileShare.shareImage(
                            image = bitmap,
                            fileName = "anivio-profile",
                            caption = "My Anivio profile — Lv. ${stats.level.level} ${stats.level.title}",
                        )
                    }
                    onDismiss()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** The actual visual recap card captured for sharing. */
@Composable
private fun ShareCard(stats: ProfileStats, profileName: String) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        tokens.colors.accent.copy(alpha = 0.28f),
                        tokens.colors.surfaceCard,
                        tokens.colors.background,
                    ),
                ),
            )
            .padding(24.dp),
    ) {
        Text("ANIVIO", color = tokens.colors.accent, fontWeight = FontWeight.Black, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LevelRing(progress = stats.level.progress, ringSize = 72.dp, stroke = 6.dp) {
                Text(
                    "${stats.level.level}",
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                )
            }
            Column {
                Text(profileName, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(stats.level.title, color = tokens.colors.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(stats.habit.label, color = tokens.colors.textSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ShareStat("${stats.totals.episodesWatched}", "episodes")
            ShareStat("${stats.totals.seriesCompleted}", "completed")
            ShareStat("${stats.streak.longestDays}", "best streak")
            ShareStat("${stats.achievements.count { it.unlocked }}", "badges")
        }
        Spacer(Modifier.height(16.dp))
        val days = stats.totals.minutesWatched / 1440.0
        if (days >= 0.1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocalFireDepartment, null, tint = Color(0xFFFB923C), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${oneDecimal(days)} days watched",
                    color = tokens.colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ShareStat(value: String, label: String) {
    val tokens = MaterialTheme.nuvio
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(label, color = tokens.colors.textMuted, fontSize = 10.sp)
    }
}

/**
 * A lightweight full-screen celebration shown once when the user levels up or unlocks a badge. Taps
 * anywhere to dismiss; a gentle animated glow stands in for confetti so it stays cheap.
 */
@Composable
fun ProfileCelebrationOverlay(
    celebration: ProfileCelebration,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val transition = rememberInfiniteTransition(label = "celebrate")
    val glow by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "glow",
    )

    AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(onClickLabel = null) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    tokens.colors.accent.copy(alpha = glow),
                                    tokens.colors.accent.copy(alpha = 0f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val (icon, _) = when (celebration) {
                        is ProfileCelebration.LevelUp ->
                            com.nuvio.app.features.profile.components.achievementIcon("trophy") to Unit
                        is ProfileCelebration.AchievementUnlocked ->
                            com.nuvio.app.features.profile.components.achievementIcon(celebration.achievement.iconKey) to Unit
                    }
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(52.dp))
                }
                val (title, subtitle) = when (celebration) {
                    is ProfileCelebration.LevelUp ->
                        "Level ${celebration.newLevel}!" to celebration.title
                    is ProfileCelebration.AchievementUnlocked ->
                        "Achievement unlocked!" to celebration.achievement.title
                }
                Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)
                Text(subtitle, color = tokens.colors.accent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Tap to continue", color = tokens.colors.textMuted, fontSize = 12.sp)
            }
        }
    }
}
