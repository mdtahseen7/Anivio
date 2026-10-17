package com.nuvio.app.features.details.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.anilist.parseAniListMediaId
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.detail_comments_title
import nuvio.composeapp.generated.resources.detail_tab_info
import nuvio.composeapp.generated.resources.detail_tab_comments
import org.jetbrains.compose.resources.stringResource

enum class DetailContentTab {
    INFO,
    COMMENTS,
}

data class AnimeCommunityCommentsConfig(
    val malId: String?,
    val aniListId: String?,
    val mediaType: String,
    val episodeChapterNumber: String = "0",
) {
    val signature: String
        get() = listOf(malId.orEmpty(), aniListId.orEmpty(), mediaType, episodeChapterNumber).joinToString("|")

    fun javascriptConfig(colorScheme: AnimeCommunityColorScheme): String = buildString {
        append("window.theAnimeCommunityConfig = {")
        append("MAL_ID:").append(jsString(malId.orEmpty())).append(',')
        append("AniList_ID:").append(jsString(aniListId.orEmpty())).append(',')
        append("episodeChapterNumber:").append(jsString(episodeChapterNumber)).append(',')
        append("mediaType:").append(jsString(mediaType)).append(',')
        append("removeBorder:'true',removePadding:'true',")
        append("colorScheme:{")
        append("primaryColor:").append(jsString(colorScheme.primary)).append(',')
        append("backgroundColor:").append(jsString(colorScheme.background)).append(',')
        append("dropDownTextColor:").append(jsString(colorScheme.primaryText)).append(',')
        append("strongTextColor:").append(jsString(colorScheme.primaryText)).append(',')
        append("primaryTextColor:").append(jsString(colorScheme.primaryText)).append(',')
        append("secondaryTextColor:").append(jsString(colorScheme.secondaryText)).append(',')
        append("iconColor:").append(jsString(colorScheme.primary)).append(',')
        append("accentColor:").append(jsString(colorScheme.primary))
        append("},customCSS:").append(jsString(ANIME_COMMUNITY_CUSTOM_CSS))
        append("};")
    }

    fun initialHtml(colorScheme: AnimeCommunityColorScheme): String = """
        <!doctype html>
        <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1" />
            <style>html,body{margin:0;padding:0;background:${colorScheme.background};color:${colorScheme.primaryText};}</style>
            <script>${javascriptConfig(colorScheme)}</script>
          </head>
          <body>
            <div id="anime-community-comment-section"></div>
            <script src="https://theanimecommunity.com/embed.js" id="anime-community-script" defer></script>
          </body>
        </html>
    """.trimIndent()

    fun updateScript(colorScheme: AnimeCommunityColorScheme): String =
        "${javascriptConfig(colorScheme)}window.theAnimeCommunity?.reload?.();"
}

data class AnimeCommunityColorScheme(
    val primary: String,
    val background: String,
    val primaryText: String,
    val secondaryText: String,
)

@Composable
fun DetailContentTabBar(
    selectedTab: DetailContentTab,
    onTabSelected: (DetailContentTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DetailContentTabButton(
                label = stringResource(Res.string.detail_tab_info),
                icon = Icons.Default.Info,
                selected = selectedTab == DetailContentTab.INFO,
                onClick = { onTabSelected(DetailContentTab.INFO) },
                modifier = Modifier.weight(1f),
            )
            DetailContentTabButton(
                label = stringResource(Res.string.detail_tab_comments),
                icon = Icons.Default.ChatBubbleOutline,
                selected = selectedTab == DetailContentTab.COMMENTS,
                onClick = { onTabSelected(DetailContentTab.COMMENTS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DetailContentTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Tab, onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.onBackground
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.background
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun AnimeCommunityCommentsSection(
    meta: MetaDetails,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val config = remember(meta.id, meta.type) {
        AnimeCommunityCommentsConfig(
            malId = meta.malIdForComments(),
            aniListId = parseAniListMediaId(meta.id)?.toString(),
            mediaType = if (meta.mangaInfo != null) "manga" else "anime",
        )
    }
    val webColorScheme = remember(colorScheme.primary, colorScheme.surface, colorScheme.onSurface, colorScheme.onSurfaceVariant) {
        AnimeCommunityColorScheme(
            primary = colorScheme.primary.toCssHex(),
            background = colorScheme.surface.toCssHex(),
            primaryText = colorScheme.onSurface.toCssHex(),
            secondaryText = colorScheme.onSurfaceVariant.toCssHex(),
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.detail_comments_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 620.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 1.dp,
        ) {
            AnimeCommunityWebView(
                config = config,
                colorScheme = webColorScheme,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
expect fun AnimeCommunityWebView(
    config: AnimeCommunityCommentsConfig,
    colorScheme: AnimeCommunityColorScheme,
    modifier: Modifier,
)

private fun MetaDetails.malIdForComments(): String? {
    val directId = id
        .takeIf { it.startsWith("mal:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.substringBefore(':')
        ?.takeIf { it.toIntOrNull() != null }
    if (directId != null) return directId

    val malLink = links.firstOrNull { link ->
        link.url.contains("myanimelist.net/", ignoreCase = true)
    }?.url ?: return null
    return Regex("myanimelist\\.net/(?:anime|manga)/(\\d+)", RegexOption.IGNORE_CASE)
        .find(malLink)
        ?.groupValues
        ?.getOrNull(1)
        ?: id.substringAfterLast(':').substringBefore(':').toIntOrNull()?.toString()
}

private fun Color.toCssHex(): String {
    fun channel(value: Float): String = (value * 255f)
        .toInt()
        .coerceIn(0, 255)
        .toString(16)
        .uppercase()
        .padStart(2, '0')
    return "#${channel(red)}${channel(green)}${channel(blue)}"
}

private fun jsString(value: String): String = buildString {
    append('\'')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '\'' -> append("\\'")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '<' -> append("\\u003C")
            else -> append(char)
        }
    }
    append('\'')
}

private const val ANIME_COMMUNITY_CUSTOM_CSS = """
* { box-sizing: border-box; font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; }
body { overflow-x: hidden; }
.mantine-Paper-root { border-radius: 14px !important; box-shadow: none !important; }
.mantine-Button-root { border-radius: 12px !important; min-height: 40px; }
.mantine-ActionIcon-root { border-radius: 10px !important; }
.mantine-Textarea-input { border-radius: 12px !important; }
"""
