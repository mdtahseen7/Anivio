/**
 * Anivio plugin runtime check.
 *
 * Purpose: prove the whole plugin pipeline works before any real provider is written. It exercises
 * every capability the host exposes and returns publicly licensed test streams so playback can be
 * verified in the native player.
 *
 * What this validates, in order:
 *   1. module.exports.getStreams is discovered by PluginRuntime
 *   2. async/await executes (QuickJS, not Hermes — see notes at the bottom)
 *   3. the fetch polyfill reaches the network and parses JSON
 *   4. ani.zip id mapping, so anilist:/mal: ids resolve without a TMDB key
 *   5. SCRAPER_SETTINGS is readable and typed
 *   6. console.log/warn/error reach the app's log pane
 *   7. the result parser handles quality, size, language, headers and subtitles
 *   8. onSettings() renders a settings layout
 *
 * Anivio passes whatever `resolvePluginTmdbId` produced. That is a numeric TMDB id only when a TMDB
 * API key is configured; otherwise it is the app's own id, e.g. "anilist:185874". A real provider
 * must handle both, which is what resolveIds below demonstrates.
 */

var ANIZIP_ENDPOINT = 'https://api.ani.zip/mappings';

/** Public, licensed test media. Nothing here is scraped from anywhere. */
var TEST_STREAMS = {
    // Apple's official HLS sample. Multi-variant fMP4, so it also proves adaptive playback.
    hls: 'https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8',
    // Big Buck Bunny, Blender Foundation, CC BY 3.0. Progressive MP4 path.
    mp4: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
};

/**
 * Splits the incoming id into whatever we can learn about it.
 *
 * Returns { kind, id } where kind is 'anilist' | 'mal' | 'tmdb'. A bare number is assumed to be
 * TMDB because that is what the app produces when a TMDB key is present.
 */
function classifyId(rawId) {
    var value = String(rawId == null ? '' : rawId).trim();
    if (!value) return { kind: 'unknown', id: '' };

    var lower = value.toLowerCase();
    if (lower.indexOf('anilist:') === 0) {
        return { kind: 'anilist', id: value.slice('anilist:'.length).split(':')[0] };
    }
    if (lower.indexOf('mal:') === 0) {
        return { kind: 'mal', id: value.slice('mal:'.length).split(':')[0] };
    }
    if (/^\d+$/.test(value)) {
        return { kind: 'tmdb', id: value };
    }
    return { kind: 'unknown', id: value };
}

/**
 * Resolves an app id into the external ids a provider would actually need.
 *
 * ani.zip is a free public mapping service keyed by anilist_id, mal_id or kitsu_id, and it returns
 * themoviedb_id, thetvdb_id, imdb_id and per-episode metadata in one request. That makes it the
 * right way to bridge Anivio's anime-first ids to whatever a provider indexes by — and it needs no
 * API key, which matters because this app currently has no TMDB key configured.
 */
async function resolveIds(rawId, episode) {
    var classified = classifyId(rawId);
    var resolved = {
        input: rawId,
        kind: classified.kind,
        anilistId: classified.kind === 'anilist' ? classified.id : null,
        malId: classified.kind === 'mal' ? classified.id : null,
        tmdbId: classified.kind === 'tmdb' ? classified.id : null,
        imdbId: null,
        tvdbId: null,
        episodeTitle: null,
        episodeCount: null,
    };

    // Only anilist/mal ids can be mapped through ani.zip. A numeric TMDB id is already usable.
    var query = null;
    if (classified.kind === 'anilist') query = 'anilist_id=' + encodeURIComponent(classified.id);
    if (classified.kind === 'mal') query = 'mal_id=' + encodeURIComponent(classified.id);
    if (!query) return resolved;

    try {
        var response = await fetch(ANIZIP_ENDPOINT + '?' + query, {
            headers: { Accept: 'application/json', 'User-Agent': 'Anivio' },
        });
        if (!response.ok) {
            console.warn('[runtime-check] ani.zip returned HTTP ' + response.status);
            return resolved;
        }

        var payload = await response.json();
        var mappings = (payload && payload.mappings) || {};

        // ani.zip mixes quoted and unquoted ids, so coerce everything to string.
        function idString(value) {
            if (value === null || value === undefined) return null;
            var text = String(value).trim().replace(/^"|"$/g, '');
            return text && text !== 'null' ? text : null;
        }

        resolved.tmdbId = idString(mappings.themoviedb_id);
        resolved.tvdbId = idString(mappings.thetvdb_id);
        resolved.imdbId = idString(mappings.imdb_id);
        resolved.malId = resolved.malId || idString(mappings.mal_id);
        resolved.episodeCount = payload && payload.episodeCount ? payload.episodeCount : null;

        // ani.zip carries no series title, only per-episode titles. Verified: anilist:185874
        // (BLEACH: TYBW) yields episodes["1"].title.en === "God of Thunder", which is the episode
        // name. A provider that searched its index with that string would match the wrong show, so
        // this is exposed as episodeTitle and a series name must come from AniList directly.
        var episodes = (payload && payload.episodes) || {};
        var firstEpisode = episodes[String(episode == null ? 1 : episode)] || episodes['1'];
        if (firstEpisode && firstEpisode.title) {
            resolved.episodeTitle = firstEpisode.title.en || firstEpisode.title['x-jat'] || null;
        }
    } catch (error) {
        console.warn('[runtime-check] ani.zip lookup failed: ' + (error && error.message));
    }

    return resolved;
}

/**
 * Required export. Anivio calls this as
 *   getStreams(tmdbId, mediaType, season, episode)
 * with mediaType already normalised to "movie" or "tv" by normalizePluginType.
 */
async function getStreams(tmdbId, mediaType, season, episode) {
    console.log(
        '[runtime-check] invoked id=' + tmdbId +
        ' type=' + mediaType +
        ' season=' + season +
        ' episode=' + episode,
    );

    // Proves SCRAPER_SETTINGS arrives as a real object with the values onSettings declared.
    var settings = (typeof SCRAPER_SETTINGS !== 'undefined' && SCRAPER_SETTINGS) || {};
    var includeMp4 = settings.include_mp4 !== false;
    var label = typeof settings.label === 'string' && settings.label ? settings.label : 'Runtime Check';
    console.log('[runtime-check] settings: ' + JSON.stringify(settings));

    var ids = await resolveIds(tmdbId, episode);
    console.log('[runtime-check] resolved: ' + JSON.stringify(ids));

    // A real provider would search its own index using ids.title / ids.tmdbId here, then extract a
    // playable URL. This returns fixed licensed samples instead, so the only thing under test is the
    // pipeline itself.
    var episodeSuffix = mediaType === 'tv' && season != null && episode != null
        ? ' S' + season + 'E' + episode
        : '';

    var streams = [
        {
            name: label,
            title: 'HLS adaptive (Apple sample)' + episodeSuffix,
            url: TEST_STREAMS.hls,
            quality: '1080p',
            type: 'hls',
            language: 'en',
            provider: 'anivio-runtime-check',
            // Exercises the header map in PluginRuntime.parseJsonResults. Harmless for this host.
            headers: {
                'User-Agent': 'Anivio',
                Referer: 'https://developer.apple.com/',
            },
            // Exercises the subtitle array parser. The URL is illustrative; if it 404s the stream
            // still plays, which is the point — subtitle parsing must not break playback.
            subtitles: [
                {
                    url: 'https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/subtitles/eng/prog_index.m3u8',
                    language: 'en',
                    name: 'English (sample)',
                },
            ],
        },
    ];

    if (includeMp4) {
        streams.push({
            name: label,
            title: 'Progressive MP4 (Big Buck Bunny, CC BY)' + episodeSuffix,
            url: TEST_STREAMS.mp4,
            quality: '720p',
            type: 'mp4',
            size: '158 MB',
            language: 'en',
            provider: 'anivio-runtime-check',
        });
    }

    // Surfaces the mapping result in the UI so a failed ani.zip lookup is visible rather than silent.
    if (ids.kind === 'anilist' || ids.kind === 'mal') {
        streams.push({
            name: label,
            title: 'mapping: ' + ids.kind + ' -> tmdb=' + (ids.tmdbId || 'none') +
                ' tvdb=' + (ids.tvdbId || 'none') + ' imdb=' + (ids.imdbId || 'none'),
            url: TEST_STREAMS.mp4,
            quality: 'diagnostic',
            provider: 'anivio-runtime-check',
        });
    }

    console.log('[runtime-check] returning ' + streams.length + ' streams');
    return streams;
}

/**
 * Optional export. PluginRuntime.getPluginSettingsLayout calls this and expects a JSON array, which
 * drives the provider's settings sheet. Values land back in SCRAPER_SETTINGS.
 */
async function onSettings() {
    return [
        {
            key: 'label',
            type: 'text',
            title: 'Stream label',
            description: 'Shown as the source name on each result.',
            default: 'Runtime Check',
        },
        {
            key: 'include_mp4',
            type: 'boolean',
            title: 'Include progressive MP4',
            description: 'Adds the Big Buck Bunny MP4 alongside the HLS sample.',
            default: true,
        },
    ];
}

// PluginRuntime looks for module.exports first, then globalThis, so publish to both. The runtime
// wraps this file in `var module = { exports: {} }` before evaluating it.
module.exports.getStreams = getStreams;
module.exports.onSettings = onSettings;
globalThis.getStreams = getStreams;
globalThis.onSettings = onSettings;

/*
 * Notes specific to Anivio, which differ from the upstream Nuvio provider guide:
 *
 * - Engine is QuickJS (quickjs-kt), not Hermes. `async`/`await` runs natively, so none of the guide's
 *   transpilation or `node build.js --transpile` step is needed. Do NOT transpile for this app.
 * - `import`/`export` are unavailable: the file is evaluated as a plain script. Single-file only,
 *   or bundle before loading.
 * - Available globals from JsBindings: fetch, AbortController, atob/btoa, TextEncoder/TextDecoder,
 *   CryptoJS, crypto.subtle, crypto.randomUUID, SCRAPER_ID, SCRAPER_SETTINGS, console.
 *   WebAssembly is a stub that returns empty exports — do not rely on it.
 * - Result fields Anivio parses, which are richer than the guide documents: title, name, url,
 *   quality, size, language, provider, type, seeders, peers, infoHash, headers{}, subtitles[].
 *   A result without a usable `url` is dropped.
 * - Execution timeout is 60s for the whole getStreams call.
 */
