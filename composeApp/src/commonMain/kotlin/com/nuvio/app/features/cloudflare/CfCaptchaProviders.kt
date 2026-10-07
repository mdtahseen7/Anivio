package com.nuvio.app.features.cloudflare

import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.plugins.PluginScraper

/**
 * One captcha-gated source the user can open in the challenge solver. Built from installed providers
 * that declare `needsCaptcha` in their manifest, merged with any hosts that returned a live
 * Cloudflare challenge. [cleared] reflects whether a fresh clearance token already exists.
 */
data class CfCaptchaProvider(
    /** Display name (provider name, or the bare host for a live-challenge entry). */
    val label: String,
    /** Host key opened in the solver (e.g. "animepahe.pw"). */
    val host: String,
    val cleared: Boolean,
)

/**
 * Collects the sources the user may need to solve a Cloudflare challenge for: installed providers
 * flagged `needsCaptcha` (one entry per declared domain), plus any host currently returning a live
 * challenge. De-duplicated by host; clearance state is read from [CfClearanceRepository].
 */
fun buildCfCaptchaProviders(
    scrapers: List<PluginScraper> = PluginRepository.uiState.value.scrapers,
    challengeHosts: Set<String> = CfClearanceRepository.uiState.value.challengeHosts,
): List<CfCaptchaProvider> {
    val byHost = linkedMapOf<String, CfCaptchaProvider>()

    scrapers.asSequence()
        .filter { it.needsCaptcha }
        .forEach { scraper ->
            scraper.domains
                .map(::cfHostKey)
                .filter { it.isNotEmpty() }
                .forEach { host ->
                    byHost.putIfAbsent(
                        host,
                        CfCaptchaProvider(
                            label = scraper.name,
                            host = host,
                            cleared = CfClearanceRepository.freshClearanceHeadersFor("https://$host") != null,
                        ),
                    )
                }
        }

    // Any host that just returned a live challenge but wasn't pre-flagged still deserves an entry.
    challengeHosts.forEach { raw ->
        val host = cfHostKey(raw).ifEmpty { return@forEach }
        byHost.putIfAbsent(
            host,
            CfCaptchaProvider(
                label = host,
                host = host,
                cleared = CfClearanceRepository.freshClearanceHeadersFor("https://$host") != null,
            ),
        )
    }

    return byHost.values.toList()
}
