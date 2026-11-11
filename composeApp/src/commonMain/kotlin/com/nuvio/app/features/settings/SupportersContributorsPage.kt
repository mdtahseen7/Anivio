package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class Contributor(
    val login: String,
    @SerialName("avatar_url") val avatar: String? = null,
    @SerialName("html_url") val url: String? = null,
    val contributions: Int = 0,
)

internal fun LazyListScope.communityContent(isTablet: Boolean) {
    item { CommunityBody(isTablet) }
}

@Composable
private fun CommunityBody(isTablet: Boolean) {
    var supporters by remember { mutableStateOf(false) }
    var contributors by remember { mutableStateOf<List<Contributor>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val uri = LocalUriHandler.current
    LaunchedEffect(attempt) {
        failed = false
        try {
            val response = httpRequestRaw("GET", "https://api.github.com/repos/mdtahseen7/Anivio/contributors?per_page=100", mapOf("Accept" to "application/vnd.github+json"), "")
            check(response.status in 200..299)
            contributors = Json { ignoreUnknownKeys = true }.decodeFromString<List<Contributor>>(response.body)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Built by people who love anime", style = MaterialTheme.typography.headlineSmall)
        Text("Every contribution, bug report and thoughtful suggestion helps Anivio grow.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!supporters) AiContributions(isTablet)
        SettingsGroup(isTablet) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "Contributors", true to "Supporters").forEach { (tab, label) ->
                    val selected = supporters == tab
                    TextButton(
                        onClick = { supporters = tab },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) { Text(label, style = MaterialTheme.typography.titleSmall) }
                }
            }
        }
        SettingsGroup(isTablet) {
            if (supporters) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Thank you for supporting Anivio", style = MaterialTheme.typography.titleMedium)
                    Text("Our community keeps this project moving through testing, sharing and feedback. No public supporter roster has been published yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (failed) {
                SettingsNavigationRow(title = "Couldn’t load contributors", description = "Tap to retry", isTablet = isTablet, onClick = { attempt++ })
            } else if (contributors == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                contributors.orEmpty().forEachIndexed { index, person ->
                    if (index > 0) SettingsGroupDivider(isTablet)
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = person.url != null) { person.url?.let(uri::openUri) }.padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                            Text(person.login.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
                            AsyncImage(model = person.avatar, contentDescription = "${person.login} profile picture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(person.login, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("${person.contributions} contributions", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        SettingsGroup(isTablet) {
            SettingsNavigationRow(title = "Anivio on GitHub", description = "Contribute, report an issue or follow development", isTablet = isTablet, onClick = { uri.openUri("https://github.com/mdtahseen7/Anivio") })
            SettingsGroupDivider(isTablet)
            SettingsNavigationRow(title = "Built on Nuvio", description = "Thanks to Nuvio’s creators and upstream contributors", isTablet = isTablet, onClick = { uri.openUri("https://github.com/NuvioMedia/NuvioMobile") })
        }
    }
}
