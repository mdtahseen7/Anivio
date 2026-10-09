package com.nuvio.app.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.anilist.AniListStatisticsRepository
import com.nuvio.app.features.profile.components.AchievementBadge
import com.nuvio.app.features.profile.components.AchievementDetail
import com.nuvio.app.features.profile.components.ActivityHeatmap
import com.nuvio.app.features.profile.components.LevelRing
import com.nuvio.app.features.profile.components.ProfileBarRow
import com.nuvio.app.features.profile.components.ProfilePill
import com.nuvio.app.features.profile.components.ProfileStatTile
import com.nuvio.app.features.profile.components.ProfileVerticalBar
import com.nuvio.app.features.profile.components.tierColor
import com.nuvio.app.features.profiles.ActiveProfileMiniAvatar
import com.nuvio.app.features.profiles.AvatarRepository
import com.nuvio.app.features.profiles.ProfileRepository

/**
 * The gamified profile dashboard: identity + level, streak, activity heatmap, lifetime totals,
 * watch-habit analytics and the achievements grid. Reads everything from [ProfileStatsRepository]
 * (which recomputes from local history + AniList) and layers in a share action and celebration
 * popups for level-ups / freshly-unlocked badges.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onOpenStats: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    val uiState by ProfileStatsRepository.uiState.collectAsStateWithLifecycle()
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val avatars by AvatarRepository.avatars.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        ProfileStatsRepository.start()
        AniListStatisticsRepository.refresh()
        AvatarRepository.refreshAvatars()
    }

    val stats = uiState.stats
    var selectedAchievement by remember { mutableStateOf<Achievement?>(null) }
    var showShareCard by remember { mutableStateOf(false) }
    var showAllAchievements by remember { mutableStateOf(false) }

    // Surface one-shot celebrations.
    val celebration = uiState.pendingCelebrations.firstOrNull()

    NuvioScreen(modifier = modifier) {
        stickyHeader {
            NuvioScreenHeader(
                title = "Profile",
                onBack = onBack,
                actions = {
                    onOpenSettings?.let { open ->
                        androidx.compose.material3.IconButton(onClick = open) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                tint = tokens.colors.textSecondary,
                            )
                        }
                    }
                    androidx.compose.material3.IconButton(onClick = { showShareCard = true }) {
                        Icon(
                            imageVector = Icons.Rounded.IosShare,
                            contentDescription = "Share",
                            tint = tokens.colors.textSecondary,
                        )
                    }
                },
            )
        }

        if (!uiState.isReady) {
            item("loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            return@NuvioScreen
        }

        item("identity") {
            IdentityCard(
                levelInfo = stats.level,
                profileName = profileState.activeProfile?.name ?: "You",
                habit = stats.habit,
                avatarSlot = {
                    ActiveProfileMiniAvatar(
                        profile = profileState.activeProfile,
                        avatars = avatars,
                        selected = false,
                        size = 56,
                    )
                },
            )
        }

        item("streak") { StreakCard(stats.streak, stats.totals) }

        item("heatmap") {
            HeatmapCard(
                intensities = stats.heatmap.map { it.intensity },
                activeDays = stats.totals.activeDays,
                currentStreak = stats.streak.currentDays,
            )
        }

        item("totals") { TotalsCard(stats.totals, aniListConnected = uiState.aniListConnected) }

        item("analytics") { HabitAnalyticsCard(stats.hourHistogram, stats.weekdayHistogram, stats.habit) }

        item("anilist-breakdown") {
            if (uiState.aniListConnected) {
                val aniStats by AniListStatisticsRepository.uiState.collectAsStateWithLifecycle()
                aniStats.statistics?.anime?.let { TasteBreakdownCard(it) }
            }
        }

        item("achievements-header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val unlockedCount = stats.achievements.count { it.unlocked }
                Text(
                    "Achievements",
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                ProfilePill("$unlockedCount / ${stats.achievements.size}")
            }
        }

        item("achievements-grid") {
            AchievementsPreview(
                achievements = stats.achievements,
                onAchievementClick = { selectedAchievement = it },
                onSeeAll = { showAllAchievements = true },
            )
        }

        item("footer-space") {
            if (onOpenStats != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(tokens.colors.surfaceElevated)
                        .clickable { onOpenStats() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("View detailed stats", color = tokens.colors.textPrimary, fontSize = 14.sp)
                    Icon(Icons.Rounded.ChevronRight, null, tint = tokens.colors.textSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // Achievement detail sheet.
    selectedAchievement?.let { ach ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        com.nuvio.app.core.ui.NuvioModalBottomSheet(
            onDismissRequest = { selectedAchievement = null },
            sheetState = sheetState,
        ) {
            AchievementDetail(ach)
            Spacer(Modifier.height(24.dp))
        }
    }

    // Full achievements sheet.
    if (showAllAchievements) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        com.nuvio.app.core.ui.NuvioModalBottomSheet(
            onDismissRequest = { showAllAchievements = false },
            sheetState = sheetState,
            fullHeight = true,
        ) {
            AllAchievementsContent(
                achievements = stats.achievements,
                onAchievementClick = { selectedAchievement = it },
            )
        }
    }

    // Shareable recap card.
    if (showShareCard) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        com.nuvio.app.core.ui.NuvioModalBottomSheet(
            onDismissRequest = { showShareCard = false },
            sheetState = sheetState,
        ) {
            ProfileShareSheet(
                stats = stats,
                profileName = profileState.activeProfile?.name ?: "Anivio",
                onDismiss = { showShareCard = false },
            )
        }
    }

    // Celebration overlay.
    if (celebration != null) {
        ProfileCelebrationOverlay(
            celebration = celebration,
            onDismiss = { ProfileStatsRepository.consumeCelebration(celebration) },
        )
    }
}

@Composable
private fun IdentityCard(
    levelInfo: ProfileLevel,
    profileName: String,
    habit: WatchHabitType,
    avatarSlot: @Composable () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    NuvioSurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LevelRing(progress = levelInfo.progress, ringSize = 84.dp, stroke = 7.dp) {
                avatarSlot()
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(profileName, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    "Lv. ${levelInfo.level} · ${levelInfo.title}",
                    color = tokens.colors.accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Text(habit.label, color = tokens.colors.textSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("XP", color = tokens.colors.textMuted, fontSize = 11.sp)
                Text(
                    "${levelInfo.xp} total XP · ${levelInfo.xpIntoLevel} / ${levelInfo.xpForNextLevel}",
                    color = tokens.colors.textMuted,
                    fontSize = 11.sp,
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                    .background(tokens.colors.surfaceElevated),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(levelInfo.progress).height(6.dp).clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(listOf(tokens.colors.accent, tokens.colors.accentStrong)),
                        ),
                )
            }
        }
    }
}

@Composable
private fun StreakCard(streak: ProfileStreak, totals: ProfileTotals) {
    val tokens = MaterialTheme.nuvio
    NuvioSurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape)
                    .background(
                        if (streak.currentDays > 0) {
                            Brush.linearGradient(listOf(Color(0xFFFB923C), Color(0xFFF43F5E)))
                        } else {
                            Brush.linearGradient(listOf(tokens.colors.surfaceElevated, tokens.colors.surfaceElevated))
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (streak.currentDays > 0) Color.White else tokens.colors.textMuted,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${streak.currentDays}",
                        color = tokens.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                    )
                    Text(
                        "day streak",
                        color = tokens.colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
                val sub = when {
                    streak.watchedToday -> "You've watched today — keep it going!"
                    streak.freezeActive -> "Streak freeze active · watch today to secure it"
                    streak.currentDays > 0 -> "Watch something today to extend it"
                    else -> "Watch an episode to start a new streak"
                }
                Text(sub, color = tokens.colors.textMuted, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${streak.longestDays}", color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("best", color = tokens.colors.textMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun HeatmapCard(intensities: List<Int>, activeDays: Int, currentStreak: Int) {
    val tokens = MaterialTheme.nuvio
    NuvioSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Activity", color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (activeDays > 0) {
                ProfilePill(
                    if (currentStreak > 0) "$activeDays active · $currentStreak-day streak" else "$activeDays active days",
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        ActivityHeatmap(intensities = intensities, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Less", color = tokens.colors.textMuted, fontSize = 10.sp)
            (0..4).forEach { level ->
                Box(
                    modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp))
                        .background(
                            if (level == 0) tokens.colors.surfaceElevated
                            else tokens.colors.accent.copy(alpha = 0.25f + 0.1875f * level),
                        ),
                )
            }
            Text("More", color = tokens.colors.textMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun TotalsCard(totals: ProfileTotals, aniListConnected: Boolean) {
    val tokens = MaterialTheme.nuvio
    val days = totals.minutesWatched / 1440.0
    NuvioSurfaceCard {
        Text("Lifetime", color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileStatTile("${totals.episodesWatched}", "Episodes", Modifier.weight(1f), tokens.colors.accent, Icons.Rounded.Videocam)
            ProfileStatTile(formatDaysCompact(days), "Days", Modifier.weight(1f), tokens.colors.accent, Icons.Rounded.Schedule)
            ProfileStatTile("${totals.seriesCompleted}", "Completed", Modifier.weight(1f), tokens.colors.accent, Icons.Rounded.CheckCircle)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileStatTile("${totals.seriesTouched}", "Series", Modifier.weight(1f), icon = Icons.Rounded.Theaters)
            ProfileStatTile("${totals.activeDays}", "Active days", Modifier.weight(1f), icon = Icons.Rounded.LocalFireDepartment)
            ProfileStatTile("${(totals.completionRate * 100).toInt()}%", "Completion", Modifier.weight(1f), icon = Icons.Rounded.Percent)
        }
        if (!aniListConnected) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Connect AniList for accurate watch time and completed-series counts.",
                color = tokens.colors.textMuted,
                fontSize = 11.sp,
            )
        }
    }
}

private val weekdayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
private fun HabitAnalyticsCard(
    hourHistogram: List<Int>,
    weekdayHistogram: List<Int>,
    habit: WatchHabitType,
) {
    val tokens = MaterialTheme.nuvio
    NuvioSurfaceCard {
        Text("When you watch", color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(habit.blurb, color = tokens.colors.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))

        // Hour-of-day histogram (grouped into 3-hour buckets for a cleaner 8-bar chart).
        val buckets = IntArray(8)
        hourHistogram.forEachIndexed { hour, count -> buckets[hour / 3] += count }
        val hourMax = (buckets.maxOrNull() ?: 0).coerceAtLeast(1)
        val peakBucket = buckets.indices.maxByOrNull { buckets[it] } ?: -1
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            buckets.forEachIndexed { i, c ->
                ProfileVerticalBar(
                    fraction = c.toFloat() / hourMax,
                    label = "${i * 3}",
                    highlighted = i == peakBucket,
                )
            }
        }
        if (peakBucket >= 0 && buckets[peakBucket] > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Peak hours · ${formatHourLabel(peakBucket * 3)}–${formatHourLabel(peakBucket * 3 + 3)}",
                color = tokens.colors.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text("Days of the week", color = tokens.colors.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        val weekMax = (weekdayHistogram.maxOrNull() ?: 0).coerceAtLeast(1)
        val peakDay = weekdayHistogram.indices.maxByOrNull { weekdayHistogram[it] } ?: -1
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            weekdayHistogram.forEachIndexed { i, c ->
                ProfileVerticalBar(
                    fraction = c.toFloat() / weekMax,
                    label = weekdayLabels.getOrElse(i) { "" },
                    highlighted = i == peakDay,
                )
            }
        }
    }
}

@Composable
private fun TasteBreakdownCard(anime: com.nuvio.app.core.anilist.AniListAnimeStatistics) {
    val tokens = MaterialTheme.nuvio
    val topGenres = anime.genres.sortedByDescending { it.count }.take(5)
    if (topGenres.isEmpty()) return
    val maxCount = (topGenres.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    NuvioSurfaceCard {
        Text("Your taste", color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            topGenres.forEach { genre ->
                ProfileBarRow(
                    label = genre.genre ?: "—",
                    value = "${genre.count}",
                    fraction = genre.count.toFloat() / maxCount,
                )
            }
        }
        val topStudio = anime.studios.maxByOrNull { it.count }?.studio?.name
        if (topStudio != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfilePill("Top studio: $topStudio")
                if (anime.meanScore > 0) ProfilePill("Mean ${oneDecimal(anime.meanScore)}", color = tokens.colors.success)
            }
        }
    }
}

@Composable
private fun AchievementsPreview(
    achievements: List<Achievement>,
    onAchievementClick: (Achievement) -> Unit,
    onSeeAll: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    // Show unlocked first, then nearest-to-unlock.
    val ordered = achievements.sortedWith(
        compareByDescending<Achievement> { it.unlocked }.thenByDescending { it.progress },
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(ordered.take(10)) { ach ->
            AchievementBadge(achievement = ach, modifier = Modifier.width(96.dp)) { onAchievementClick(ach) }
        }
        item {
            Box(
                modifier = Modifier.width(96.dp).height(120.dp).clip(RoundedCornerShape(16.dp))
                    .background(tokens.colors.surfaceElevated)
                    .clickable { onSeeAll() },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.ChevronRight, null, tint = tokens.colors.accent)
                    Text("See all", color = tokens.colors.textSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AllAchievementsContent(
    achievements: List<Achievement>,
    onAchievementClick: (Achievement) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Text(
        "Achievements",
        color = tokens.colors.textPrimary,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    AchievementCategory.entries.forEach { category ->
        val inCategory = achievements.filter { it.category == category }
        if (inCategory.isEmpty()) return@forEach
        Text(
            category.label,
            color = tokens.colors.textSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        inCategory.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { ach ->
                    AchievementBadge(achievement = ach, modifier = Modifier.weight(1f)) { onAchievementClick(ach) }
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    Spacer(Modifier.height(32.dp))
}

private fun formatDaysCompact(days: Double): String = when {
    days >= 100 -> "${days.toInt()}"
    days >= 10 -> "${days.toInt()}"
    else -> oneDecimal(days)
}

/** 24h hour → compact 12h label, e.g. 0 → "12am", 15 → "3pm", 24 → "12am". */
private fun formatHourLabel(hour: Int): String {
    val h = ((hour % 24) + 24) % 24
    val period = if (h < 12) "am" else "pm"
    val display = if (h % 12 == 0) 12 else h % 12
    return "$display$period"
}

/** Common-safe one-decimal formatter (no java.util / String.format). */
internal fun oneDecimal(value: Double): String {
    val scaled = kotlin.math.round(value * 10).toLong()
    val whole = scaled / 10
    val frac = kotlin.math.abs(scaled % 10)
    return "$whole.$frac"
}
