package com.nuvio.app.features.anilist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.anilist.AniListClient
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import kotlinx.coroutines.delay

@Composable
fun AniListRateLimitNotifier(
    modifier: Modifier = Modifier,
) {
    val untilMs by AniListClient.rateLimitUntilMs.collectAsStateWithLifecycle()
    var nowMs by remember { mutableStateOf(EpisodeReleaseDatePlatform.nowEpochMs()) }
    var isExpanded by remember { mutableStateOf(true) }
    var totalDurationMs by remember { mutableStateOf(0L) }
    var lastUntilMs by remember { mutableStateOf(0L) }

    // tick for countdown
    LaunchedEffect(untilMs) {
        while (true) {
            nowMs = EpisodeReleaseDatePlatform.nowEpochMs()
            val remaining = untilMs - nowMs
            if (remaining <= 0L) break
            delay(200L)
        }
    }

    val remainingMs = (untilMs - nowMs).coerceAtLeast(0L)
    val isActive = remainingMs > 0L && untilMs != 0L
    val remainingSec = ((remainingMs + 999) / 1000).toInt().coerceAtLeast(0)

    // capture total duration when a new rate-limit starts or extends
    LaunchedEffect(untilMs, isActive) {
        if (isActive && untilMs != lastUntilMs) {
            val wait = remainingMs.coerceAtMost(15_000L).coerceAtLeast(1000L)
            // keep the max seen for this session so progress doesn't jump
            if (wait > totalDurationMs) totalDurationMs = wait
            lastUntilMs = untilMs
        }
        if (!isActive) {
            totalDurationMs = 0L
            lastUntilMs = 0L
        }
    }

    // auto-collapse banner -> circle after 2.4s
    LaunchedEffect(isActive) {
        if (isActive) {
            isExpanded = true
            delay(2400L)
            if (isActive) isExpanded = false
        } else {
            isExpanded = true
        }
    }

    AnimatedVisibility(
        visible = isActive,
        enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.92f),
        exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.92f),
        modifier = modifier,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
                .statusBarsPadding(),
        ) {
            val cornerPadding = 12.dp
            // container alignment animates via AnimatedContent
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (isExpanded) Alignment.TopCenter else Alignment.TopEnd,
            ) {
                AnimatedContent(
                    targetState = isExpanded,
                    transitionSpec = {
                        (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.88f))
                            .togetherWith(fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.88f))
                    },
                    label = "anilist_rate_limit_morph",
                ) { expanded ->
                    if (expanded) {
                        // Banner
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1A1A1E).copy(alpha = 0.96f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                .clickable { isExpanded = false }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            androidx.compose.foundation.layout.Column(
                                modifier = Modifier.weight(1f, fill = false),
                            ) {
                                Text(
                                    text = "AniList rate limited",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 16.sp,
                                )
                                Text(
                                    text = if (remainingSec > 0) "Retrying in ${remainingSec}s" else "Retrying…",
                                    color = Color.White.copy(alpha = 0.72f),
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                )
                            }
                            // mini circular countdown inside banner
                            Box(
                                modifier = Modifier.size(36.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                val progress by animateFloatAsState(
                                    targetValue = if (totalDurationMs > 0) remainingMs.toFloat() / totalDurationMs.toFloat() else 0f,
                                    animationSpec = tween(200),
                                    label = "banner_progress",
                                )
                                CircularProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.size(36.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.White.copy(alpha = 0.14f),
                                    strokeWidth = 2.5.dp,
                                )
                                Text(
                                    text = "$remainingSec",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    } else {
                        // Corner circle
                        Box(
                            modifier = Modifier
                                .padding(end = cornerPadding)
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A1A1E).copy(alpha = 0.96f))
                                .border(1.2.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                                .clickable { isExpanded = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            val progress by animateFloatAsState(
                                targetValue = if (totalDurationMs > 0) remainingMs.toFloat() / totalDurationMs.toFloat() else 0f,
                                animationSpec = tween(200),
                                label = "circle_progress",
                            )
                            CircularProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.size(46.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.10f),
                                strokeWidth = 2.5.dp,
                            )
                            Text(
                                text = if (remainingSec > 0) "${remainingSec}s" else "…",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
