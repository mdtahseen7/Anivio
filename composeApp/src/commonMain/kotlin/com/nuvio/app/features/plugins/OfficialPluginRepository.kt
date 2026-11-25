package com.nuvio.app.features.plugins

internal enum class OfficialPluginRepository(
    val displayName: String,
    repositoryName: String,
    val manga: Boolean,
) {
    Anime("Anivio Anime Providers", "Anivio-providers", false),
    Manga("Anivio Manga Providers", "Anivio-Manga-Providers", true);

    val githubUrl = "https://github.com/mdtahseen7/$repositoryName"
    val manifestUrl = "https://raw.githubusercontent.com/mdtahseen7/$repositoryName/main/manifest.json"
}

private val matureProviderLabel = Regex("""adult|hentai|nsfw|18\+""", RegexOption.IGNORE_CASE)

internal fun PluginManifestScraper.isSuitableForOfficialCatalog(): Boolean =
    listOf(id, name, description.orEmpty()).none(matureProviderLabel::containsMatchIn)

internal fun officialPluginRepositoryFor(url: String): OfficialPluginRepository? {
    val trimmed = url.trim().trimEnd('/')
    val repositoryUrl = trimmed.removePrefix("https://").removePrefix("http://").removeSuffix(".git")
    return OfficialPluginRepository.entries.firstOrNull { entry ->
        trimmed.equals(entry.manifestUrl, ignoreCase = true) ||
            repositoryUrl.equals(entry.githubUrl.removePrefix("https://"), ignoreCase = true)
    }
}
