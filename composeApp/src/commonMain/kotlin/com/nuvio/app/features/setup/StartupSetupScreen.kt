package com.nuvio.app.features.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.isDesktop
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.sync.SyncManager
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.features.addons.AddAddonResult
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.membership.ProfileBackgroundRepository
import com.nuvio.app.features.plugins.AddPluginRepositoryResult
import com.nuvio.app.features.plugins.OfficialPluginRepository
import com.nuvio.app.features.plugins.OfficialRepositoryCard
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.plugins.PluginScraper
import com.nuvio.app.features.plugins.officialPluginRepositoryFor
import com.nuvio.app.features.settings.AppBrandWordmark
import com.nuvio.app.features.tmdb.TmdbKeyCheck
import com.nuvio.app.features.tmdb.TmdbService
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import kotlinx.coroutines.launch

private enum class SetupStep { Welcome, Sources, Artwork }

@Composable
internal fun StartupSetupScreen(profileId: Int, onFinish: () -> Unit) {
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val pluginsAvailable = AppFeaturePolicy.pluginsEnabled
    val pluginState by PluginRepository.uiState.collectAsStateWithLifecycle()
    val addonState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val tmdbSettings by TmdbSettingsRepository.uiState.collectAsStateWithLifecycle()
    val backdrops by ProfileBackgroundRepository.catalog.collectAsStateWithLifecycle()
    var step by rememberSaveable(profileId) { mutableStateOf(SetupStep.Welcome) }
    var ready by remember(profileId) { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var installingUrl by remember { mutableStateOf<String?>(null) }
    var sourceError by remember { mutableStateOf<String?>(null) }
    var pickerUrl by rememberSaveable(profileId) { mutableStateOf<String?>(null) }
    var showCustom by rememberSaveable { mutableStateOf(false) }
    var addingAddon by rememberSaveable { mutableStateOf(!pluginsAvailable) }
    var customUrl by rememberSaveable { mutableStateOf("") }
    var tmdbKey by remember { mutableStateOf("") }
    var checkingKey by remember { mutableStateOf(false) }
    var tmdbError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) { ProfileBackgroundRepository.ensureLoaded() }
    LaunchedEffect(step) { scrollState.scrollTo(0) }
    LaunchedEffect(profileId) {
        SyncManager.awaitFullProfilePull(profileId)
        if (pluginsAvailable) PluginRepository.initialize()
        AddonRepository.initialize()
        TmdbSettingsRepository.ensureLoaded()
        ready = true
    }

    fun goBack() {
        when (step) {
            SetupStep.Welcome -> onFinish()
            SetupStep.Sources -> step = SetupStep.Welcome
            SetupStep.Artwork -> step = SetupStep.Sources
        }
    }

    fun installSource(url: String, addon: Boolean) {
        if (installing || !ready) return
        focusManager.clearFocus()
        installing = true
        installingUrl = url
        sourceError = null
        scope.launch {
            try {
                if (addon) {
                    when (val result = AddonRepository.addAddon(url)) {
                        is AddAddonResult.Success -> customUrl = ""
                        is AddAddonResult.Error -> sourceError = result.message
                    }
                } else {
                    when (val result = PluginRepository.addRepository(url)) {
                        is AddPluginRepositoryResult.Success -> {
                            if (showCustom && url == customUrl.trim()) customUrl = ""
                            pickerUrl = result.repository.manifestUrl
                        }
                        is AddPluginRepositoryResult.Error -> sourceError = result.message
                    }
                }
            } finally {
                installing = false
                installingUrl = null
            }
        }
    }

    PlatformBackHandler(enabled = pickerUrl == null && !installing && !checkingKey, onBack = ::goBack)
    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val wide = isDesktop && maxWidth >= 900.dp
            Column(
                modifier = Modifier.widthIn(max = if (wide) 720.dp else 560.dp).fillMaxWidth().verticalScroll(scrollState)
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (step != SetupStep.Welcome) {
                        IconButton(onClick = ::goBack, enabled = !installing && !checkingKey) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                    AppBrandWordmark(modifier = Modifier.height(21.dp), contentDescription = "Anivio")
                    Spacer(Modifier.weight(1f))
                    if (step != SetupStep.Artwork) {
                        TextButton(onClick = onFinish, enabled = !installing) {
                            Text(if (step == SetupStep.Welcome) "Browse first" else "Skip setup")
                        }
                    }
                }
                SetupHero(
                    step = step,
                    backdrop = backdrops.firstOrNull { it.id.startsWith("anilist-banner-") }?.landscapeImageUrl,
                    wide = wide,
                )
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "startup setup step",
                ) { current ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (current) {
                            SetupStep.Welcome -> {
                                Text(
                                    "You can add or change sources anytime in Settings.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurfaceVariant,
                                )
                                NuvioPrimaryButton(text = "Set up playback", onClick = { step = SetupStep.Sources })
                            }
                            SetupStep.Sources -> {
                                if (!ready) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        NuvioLoadingIndicator(size = 16.dp)
                                        Text("Checking your saved settings…", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                val hasAnimeSource = (pluginsAvailable && pluginState.pluginsEnabled &&
                                    pluginState.scrapers.any { !it.manga && it.enabled && it.code.isNotBlank() }) ||
                                    addonState.addons.any { addon ->
                                        addon.enabled && addon.manifest?.resources.orEmpty().any { it.name == "stream" }
                                    }
                                if (hasAnimeSource) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = colors.primary)
                                        Text("Playback source enabled", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                                    }
                                }
                                if (pluginsAvailable) {
                                    if (!pluginState.pluginsEnabled) {
                                        TextButton(onClick = { PluginRepository.setPluginsEnabled(true) }, enabled = ready) {
                                            Text("Enable plugins")
                                        }
                                    }
                                    OfficialPluginRepository.entries.forEach { entry ->
                                        val installed = pluginState.repositories.firstOrNull {
                                            officialPluginRepositoryFor(it.manifestUrl) == entry
                                        }
                                        val enabledCount = pluginState.scrapers.count {
                                            it.repositoryUrl == installed?.manifestUrl && it.enabled && it.code.isNotBlank()
                                        }
                                        OfficialRepositoryCard(
                                            repository = entry,
                                            actionLabel = when {
                                                installingUrl == entry.manifestUrl -> "Installing…"
                                                installed == null -> "Install"
                                                enabledCount > 0 -> "Manage providers"
                                                else -> "Choose providers"
                                            },
                                            onAction = {
                                                if (installed == null) installSource(entry.manifestUrl, addon = false)
                                                else pickerUrl = installed.manifestUrl
                                            },
                                            enabled = ready && !installing,
                                            busy = installingUrl == entry.manifestUrl,
                                        )
                                    }
                                    if (isDesktop) {
                                        Text(
                                            "The manga repository is for the Android reader.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                        )
                                    }
                                }
                                TextButton(onClick = { showCustom = !showCustom }, enabled = ready && !installing) {
                                    Icon(Icons.Rounded.Link, null, Modifier.padding(end = 8.dp).size(18.dp))
                                    Text(if (showCustom) "Hide URL setup" else "Add by URL")
                                }
                                if (showCustom) {
                                    if (pluginsAvailable) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(
                                                selected = !addingAddon,
                                                onClick = { addingAddon = false; sourceError = null },
                                                label = { Text("Plugin repo") },
                                                enabled = !installing,
                                            )
                                            FilterChip(
                                                selected = addingAddon,
                                                onClick = { addingAddon = true; sourceError = null },
                                                label = { Text("Add-on") },
                                                enabled = !installing,
                                            )
                                        }
                                    }
                                    NuvioInputField(
                                        value = customUrl,
                                        onValueChange = { customUrl = it; sourceError = null },
                                        placeholder = "Manifest URL",
                                    )
                                    TextButton(
                                        onClick = { installSource(customUrl.trim(), addingAddon) },
                                        enabled = ready && !installing && customUrl.isNotBlank(),
                                    ) { Text(if (installing && installingUrl == customUrl.trim()) "Installing…" else "Install from URL") }
                                }
                                sourceError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error) }
                                NuvioPrimaryButton(
                                    text = "Continue",
                                    enabled = !installing,
                                    onClick = {
                                        if (tmdbSettings.hasApiKey) onFinish() else step = SetupStep.Artwork
                                    },
                                )
                            }
                            SetupStep.Artwork -> {
                                OutlinedTextField(
                                    value = tmdbKey,
                                    onValueChange = { tmdbKey = it; tmdbError = null },
                                    enabled = !checkingKey,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("TMDB API key (v3)") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    shape = RoundedCornerShape(16.dp),
                                    isError = tmdbError != null,
                                    supportingText = tmdbError?.let { error -> { Text(error) } },
                                )
                                TextButton(
                                    onClick = { uriHandler.openUri("https://www.themoviedb.org/settings/api") },
                                ) { Text("Get a TMDB key") }
                                NuvioPrimaryButton(
                                    text = if (checkingKey) "Checking key…" else "Test & save",
                                    enabled = ready && !checkingKey && tmdbKey.isNotBlank(),
                                    onClick = {
                                        val key = tmdbKey.trim()
                                        checkingKey = true
                                        tmdbError = null
                                        scope.launch {
                                            try {
                                                when (TmdbService.checkApiKey(key)) {
                                                    TmdbKeyCheck.Valid -> {
                                                        TmdbSettingsRepository.setApiKey(key)
                                                        TmdbSettingsRepository.setEnabled(true)
                                                        tmdbKey = ""
                                                        onFinish()
                                                    }
                                                    TmdbKeyCheck.Rejected -> tmdbError = "TMDB rejected this key. Check that you copied the v3 API key."
                                                    TmdbKeyCheck.Unavailable -> tmdbError = "Couldn’t reach TMDB. Try again or skip this step."
                                                }
                                            } finally {
                                                checkingKey = false
                                            }
                                        }
                                    },
                                )
                                TextButton(
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    onClick = onFinish,
                                    enabled = !checkingKey,
                                ) { Text("Finish without TMDB") }
                            }
                        }
                    }
                }
            }
        }
    }

    pickerUrl?.let { url ->
        val repository = pluginState.repositories.firstOrNull { it.manifestUrl == url }
        SetupProviderPicker(
            name = repository?.name ?: "Choose providers",
            providers = pluginState.scrapers
                .filter { it.repositoryUrl == url && it.manifestEnabled }
                .sortedBy { it.name.lowercase() },
            loading = repository?.isRefreshing == true,
            onDismiss = { pickerUrl = null },
        )
    }
}

@Composable
private fun SetupHero(step: SetupStep, backdrop: String?, wide: Boolean) {
    val shape = RoundedCornerShape(26.dp)
    val accent = Color(0xFFB8E6D4)
    val base = Color(0xFF10191D)
    val title = when (step) {
        SetupStep.Welcome -> "Welcome to Anivio"
        SetupStep.Sources -> "Choose your sources"
        SetupStep.Artwork -> "More detail for every title"
    }
    val description = when (step) {
        SetupStep.Welcome -> if (isDesktop) "Choose where Anivio finds episodes and movies."
        else "Set up anime playback and manga reading."
        SetupStep.Sources -> "Install a repository, then enable the providers you want to use."
        SetupStep.Artwork -> "Connect TMDB for more artwork, logos, and episode information."
    }
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = if (wide) 260.dp else 178.dp).clip(shape).background(base),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF315A58).copy(alpha = 0.40f), base.copy(alpha = 0.87f), base),
                ),
            ),
        )
        Column(
            modifier = Modifier.padding(start = 22.dp, top = 33.dp, end = 22.dp, bottom = 21.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (step != SetupStep.Welcome) {
                Text(
                    text = if (step == SetupStep.Sources) "1 of 2 · Sources" else "2 of 2 · Optional",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
            }
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(description, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD9E5E4))
        }
    }
}

@Composable
private fun SetupProviderPicker(
    name: String,
    providers: List<PluginScraper>,
    loading: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            if (providers.isEmpty()) {
                Text(if (loading) "Loading providers…" else "This repository has no available providers.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                    items(providers, key = { it.id }) { provider ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(provider.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Switch(
                                checked = provider.enabled,
                                onCheckedChange = { PluginRepository.toggleScraper(provider.id, it) },
                                modifier = Modifier.semantics {
                                    contentDescription = provider.name
                                },
                            )
                        }
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.42f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
