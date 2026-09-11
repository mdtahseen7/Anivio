# Anivio Plugin Guide

How to write a stream provider plugin for Anivio.

Everything here was read out of the runtime source, not inherited from upstream docs. Where Anivio
differs from the Nuvio provider guide, the difference is called out — there are several, and two of
them will break your plugin if you follow the upstream instructions.

Source of truth:

| What | File |
|---|---|
| Execution, timeout, result parsing | `composeApp/src/fullCommonMain/.../plugins/runtime/PluginRuntime.kt` |
| Globals and polyfills | `.../plugins/runtime/js/JsBindings.kt` |
| Manifest and result models | `composeApp/src/commonMain/.../plugins/PluginModels.kt` |
| Invocation and id resolution | `.../plugins/PluginRepository.kt` |
| HTML parsing bridge | `.../plugins/runtime/dom/DomBridge.kt` |

---

## 1. Differences from the Nuvio guide

Read this before anything else.

**The engine is QuickJS, not Hermes.** Anivio embeds `quickjs-kt`. `async`/`await` is supported
natively, and `PluginRuntime` already awaits your function. **Do not transpile.** The upstream
`node build.js --transpile` step and its "async functions are unsupported" advice do not apply, and
running it only makes your code harder to debug.

**There is no bundler and no `import`.** Your file is evaluated as a plain script inside
`var module = { exports: {} }; var exports = module.exports; (function () { ...your code... })();`.
ES module syntax will throw. Ship one self-contained file, or bundle before loading.

**The result schema is richer than documented.** `size`, `language`, `provider`, `type`, `seeders`,
`peers`, `infoHash` and a `subtitles[]` array are all parsed. See §5.

**There is no in-app Plugin Tester.** Load plugins through a repository manifest (§7).

---

## 2. The contract

Export a function named `getStreams`. `PluginRuntime` looks at `module.exports.getStreams` first,
then `globalThis.getStreams`.

```js
async function getStreams(tmdbId, mediaType, season, episode) {
  return []; // array of stream objects
}

module.exports.getStreams = getStreams;
globalThis.getStreams = getStreams;   // belt and braces
```

| Argument | Type | Notes |
|---|---|---|
| `tmdbId` | `string` | **Not always a TMDB id.** See §3. |
| `mediaType` | `string` | Always `"movie"` or `"tv"`. `normalizePluginType` maps `series`/`show`/`other` to `tv`. |
| `season` | `number \| undefined` | `undefined` for movies. |
| `episode` | `number \| undefined` | `undefined` for movies. |

Return an array. Returning `undefined`, `null` or throwing all yield an empty list — a thrown error
is logged and swallowed, so it will not crash the app but you also will not see a user-facing error.

Hard timeout is **60 seconds** for the entire call (`PLUGIN_TIMEOUT_MS`).

---

## 3. The id you actually receive

This is the biggest practical gotcha, and it is Anivio-specific.

`PluginRepository.executeScraper` runs the incoming id through `resolvePluginTmdbId`, which calls
`TmdbService.ensureTmdbId`. That returns `null` when no TMDB API key is configured, and the code
falls back to the **raw app id**. Anivio is anime-first, so ids look like:

```
anilist:185874     // no TMDB key configured  <- current state of this project
mal:60636
30984              // numeric, only when a TMDB key IS configured
603                // what testScraper() always passes
```

So handle all three. Map anime ids with [ani.zip](https://api.ani.zip), which is free, needs no key,
and returns every external id in one request:

```js
async function resolveIds(rawId) {
  const value = String(rawId || '').trim();
  const anilist = /^anilist:(\d+)/i.exec(value);
  const mal = /^mal:(\d+)/i.exec(value);

  if (!anilist && !mal) {
    return { tmdbId: /^\d+$/.test(value) ? value : null };
  }

  const query = anilist ? `anilist_id=${anilist[1]}` : `mal_id=${mal[1]}`;
  const res = await fetch(`https://api.ani.zip/mappings?${query}`, {
    headers: { Accept: 'application/json' },
  });
  if (!res.ok) return { tmdbId: null };

  const body = await res.json();
  const m = (body && body.mappings) || {};
  const str = (v) => {
    if (v === null || v === undefined) return null;
    const t = String(v).trim().replace(/^"|"$/g, '');
    return t && t !== 'null' ? t : null;
  };

  return {
    tmdbId: str(m.themoviedb_id),
    tvdbId: str(m.thetvdb_id),
    imdbId: str(m.imdb_id),
    malId: str(m.mal_id),
    episodeCount: body.episodeCount || null,
  };
}
```

> **ani.zip has no series title.** It only has per-episode titles under `episodes["<n>"].title`.
> Verified: `anilist:185874` (BLEACH: Thousand-Year Blood War) returns `"God of Thunder"` for episode
> 1 — that is the *episode* name. Searching a provider index with it matches the wrong show. If you
> need a series title, query AniList's GraphQL API directly.

A working reference implementation is in `plugins/anivio-runtime-check.js`.

---

## 4. Available globals

From `JsBindings.buildPolyfillCode`. Anything not listed does not exist.

### Injected values

| Global | Description |
|---|---|
| `SCRAPER_ID` | Your plugin's manifest id. |
| `SCRAPER_SETTINGS` | Object of the user's saved settings (§6). `{}` when unset. |
| `global`, `window`, `self` | All aliased to `globalThis`. |

### `fetch`

Backed by the native HTTP client, so no CORS. The response is a **limited** object — not a real
`Response`:

```js
const res = await fetch(url, {
  method: 'POST',                       // default GET
  headers: { 'User-Agent': 'Anivio' },
  body: 'a=1',                          // string only
  redirect: 'manual',                   // anything else follows redirects
});

res.ok            // boolean
res.status        // number
res.statusText    // string
res.url           // final URL after redirects
res.headers.get('content-type')   // .get() only — no iteration, no .has()
await res.text()  // string
await res.json()  // parsed, or null on invalid JSON (does NOT throw)
```

Note `res.json()` resolving to `null` rather than rejecting. Check for `null` explicitly.
There is no `res.arrayBuffer()`, `res.blob()` or `res.body`.

### HTML parsing: cheerio

A cheerio-compatible subset backed by a native parser.

```js
const cheerio = require('cheerio');       // or use the `cheerio` global
const $ = cheerio.load(html);

$('.episode a').each((i, el) => {
  console.log($(el).attr('href'), $(el).text());
});
```

Supported on a selection: `each`, `find`, `text`, `html`, `attr`, `first`, `last`, `next`, `prev`,
`eq`, `get`, `map`, `filter`, `children`, `toArray`, `length`, and `$.html(el)`.

Two limitations worth knowing: **`parent()` always returns an empty selection**, and `:contains(...)`
is handled specially by the bridge. There is no `.css()`, `.data()`, `.append()` or any mutation API —
this is read-only scraping.

### Crypto

`CryptoJS` (also `require('crypto-js')`) and a partial WebCrypto: `crypto.subtle.digest`,
`importKey`, `generateKey`, `deriveBits`, `deriveKey`, plus `crypto.getRandomValues` and
`crypto.randomUUID`.

### Other

`atob`, `btoa`, `TextEncoder`, `TextDecoder`, `AbortController`, `AbortSignal`,
`console.log/warn/error`, `Array.prototype.flat/flatMap`, `Object.entries`.

`require()` resolves **only** `cheerio`, `cheerio-without-node-native`, `react-native-cheerio` and
`crypto-js`. Anything else throws.

`WebAssembly.instantiate` is a **stub** that logs a warning and returns empty exports. Do not rely
on it.

No Node built-ins, no `Buffer`, no `setTimeout`.

---

## 5. The stream object

Parsed by `PluginRuntime.parseJsonResults`.

```js
{
  url: 'https://host/stream.m3u8',   // REQUIRED — entries without a usable url are dropped
  title: '1080p · Server A',         // falls back to `name`, then "Unknown"
  name: 'MyProvider',
  quality: '1080p',
  size: '1.4 GB',
  language: 'en',
  provider: 'myprovider',
  type: 'hls',
  seeders: 42,                       // torrents
  peers: 7,
  infoHash: 'abc123...',
  headers: {                         // sent with playback requests
    Referer: 'https://host/',
    'User-Agent': 'Mozilla/5.0 ...'
  },
  subtitles: [
    { url: 'https://host/en.vtt', language: 'en', name: 'English', headers: { Referer: '...' } }
  ]
}
```

`url` may also be an object with its own `url` field; the parser unwraps one level.

A string field whose value contains `[object` is discarded — that is a guard against accidentally
interpolating an object into a title.

If a stream needs headers to play, you **must** provide them here. There is no proxy in Anivio, so
unlike the Luna backend approach you cannot rewrite a playlist server-side.

---

## 6. Settings (optional)

Export `onSettings` returning an array of controls. Anivio calls it via
`PluginRuntime.getPluginSettingsLayout` and shows the result in the provider's settings sheet. Saved
values reappear in `SCRAPER_SETTINGS` on the next `getStreams` call.

```js
async function onSettings() {
  return [
    { key: 'preferred_server', type: 'text', title: 'Preferred server', default: '' },
    { key: 'include_dub', type: 'boolean', title: 'Include dubs', default: true },
  ];
}
module.exports.onSettings = onSettings;
```

Set `"hasSettings": true` in the manifest or the sheet will not offer them. A missing or throwing
`onSettings` degrades to `[]`.

---

## 7. Manifest and loading

`plugins/manifest.json`, validated by `PluginManifestParser`. `name`, `version` and a non-empty
`scrapers` array are mandatory.

```json
{
  "name": "My Repo",
  "version": "1.0.0",
  "description": "Optional.",
  "scrapers": [
    {
      "id": "myprovider",
      "name": "My Provider",
      "description": "Optional.",
      "version": "1.0.0",
      "filename": "myprovider.js",
      "supportedTypes": ["movie", "tv"],
      "enabled": true,
      "hasSettings": false,
      "logo": "https://.../logo.png",
      "contentLanguage": ["en"],
      "supportedFormats": ["hls", "mp4"],
      "supportsExternalPlayer": true
    }
  ]
}
```

`filename` is resolved relative to the manifest URL. `supportedTypes` is normalised, so `series`
works and means `tv`.

Serve the folder and add the manifest URL under **Settings → Content & Discovery → Plugins**:

```bash
cd plugins && npx --yes serve -l 3000
# add http://<your-lan-ip>:3000/manifest.json
```

Plugins only run on the `full` distribution — `AppFeaturePolicy.pluginsEnabled` is `false` for
playstore builds.

---

## 8. Testing

### Locally with Node

Fast for logic, but it does **not** prove the plugin works in Anivio. Node has `import`, `Buffer`,
`setTimeout` and a real `fetch`; QuickJS has none of those. Code can pass here and fail on device.

```js
globalThis.SCRAPER_SETTINGS = {};
const mod = require('./myprovider.js');
mod.getStreams('anilist:185874', 'tv', 1, 4).then(console.log);
```

### On device

`console.log` goes to logcat. Filter to the app:

```bash
adb logcat -c
adb logcat --pid=$(adb shell pidof com.anivio.app.debug) | Select-String "myprovider"
```

The built-in **Test** action on a provider calls `testScraper`, which always passes `tmdbId = "603"`
with `season = 1, episode = 1` for tv. Handle a plain numeric id or that button always fails.

---

## 9. Checklist

- [ ] Single file, no `import`/`export`
- [ ] Not transpiled
- [ ] `getStreams` on both `module.exports` and `globalThis`
- [ ] Handles `anilist:`, `mal:` and numeric ids
- [ ] Handles `"603"` so the Test button works
- [ ] Every stream has a non-empty `url`
- [ ] `headers` set on streams that need them for playback
- [ ] No top-level network calls — all work inside `getStreams`
- [ ] Completes within 60s
- [ ] `res.json()` null-checked
- [ ] `"hasSettings": true` if `onSettings` is exported

---

## 10. Common failures

| Symptom | Cause |
|---|---|
| `getStreams function not found` | Not assigned to `module.exports` or `globalThis`, or a syntax error aborted the file. |
| Empty list, no error | Your code threw. `PluginRuntime` logs it and returns `[]`. Check logcat. |
| `Module 'x' is not available` | `require()` only resolves cheerio and crypto-js. |
| Works in Node, fails on device | Node-only API. See §8. |
| Streams listed but will not play | Missing `headers`, usually `Referer`. |
| Test button fails, real playback works | Not handling the numeric `"603"` id. |
| Nothing happens at all | Plugins disabled globally, or a playstore build. |