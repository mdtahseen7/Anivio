# Anivio Stream Provider Plugins

A complete collection of native stream provider plugins for the **Anivio** streaming app, strictly conforming to the [Anivio Plugin Specification](../../Anivio/Anivio/plugins/PLUGIN_GUIDE.md).

All plugins run directly inside Anivio's internal **QuickJS** runtime (single-file architecture, native `async`/`await`, zero build steps, zero external npm dependencies).

---

## Provider Catalog

| Provider | File | Content Type | Stream Format | Features & Highlights |
|---|---|---|---|---|
| **Torrentio** | `torrentio.js` | Anime, Series & Movies | Torrent (P2P) | Ported from Saikou's torrent engine. Queries Nyaa, TokyoTosho, HorribleSubs, etc. via AniZip ID mapping. Full magnet URIs with trackers, seeders (👤), file sizes (💾), and Real-Debrid / TorBox support. |
| **KickAssAnime** | `kickassanime.js` | Anime | HLS (m3u8) | Direct 1080p master HLS stream extraction via CatStream Astro player embeds, complete with embedded VTT subtitles. |
| **MKissa** | `mkissa.js` | Anime | HLS / MP4 | Fast SUB and DUB stream extraction via the Anivexa API. Exposes custom API mirror URL in provider settings. |
| **AniBD** | `anibd.js` | Anime | HLS (m3u8) | Multi-server stream extraction with fallback resolution for anime series and films. |
| **Anikoto** | `anikoto.js` | Anime | HLS (m3u8) | Multi-source SUB and DUB streams with intelligent health and latency ranking. |
| **MegaPlay** | `megaplay.js` | Anime | HLS (m3u8) | Direct high-bitrate HLS streams for both subbed and dubbed releases. |
| **OppaiStream** | `oppaistream.js` | Hentai / Adult | MP4 / WebM | Direct multi-resolution streams (4K, 1080p, 720p) bypassing Cloudflare watch-page challenges via direct video resolution. Subtitles included. |
| **Hentaigasm** | `hentaigasm.js` | Hentai / Adult | MP4 | High-speed direct JWPlayer video sources with range-request streaming support. |
| **WatchHentai** | `watchhentai.js` | Hentai / Adult | MP4 / HLS | Direct 1080p streams deciphered on-the-fly using native XOR video decryption. |

---

## Directory Structure

```
anivio-plugins/
├── manifest.json        # Repository manifest listing all 9 scrapers with logos and settings
├── icons/               # Bundled local icons (PNG / ICO) for offline / local hosting
│   ├── anibd.png
│   ├── anikoto.png
│   ├── hentaigasm.ico
│   ├── kickassanime.ico
│   ├── megaplay.png
│   ├── mkissa.png
│   ├── oppaistream.png
│   ├── torrentio.png
│   └── watchhentai.ico
├── anibd.js             # AniBD provider
├── anikoto.js           # Anikoto provider
├── hentaigasm.js        # Hentaigasm provider
├── kickassanime.js      # KickAssAnime provider
├── megaplay.js          # MegaPlay provider
├── mkissa.js            # MKissa provider
├── oppaistream.js       # OppaiStream provider
├── torrentio.js         # Torrentio (P2P / Stremio) provider
└── watchhentai.js       # WatchHentai provider
```

---

## Setup & Installation in Anivio

### Step 1: Host the Plugins Repository

You can serve this folder over your local network using any static file server:

```bash
cd anivio-plugins
npx --yes serve -l 3000
```

*(Or host on GitHub Pages, Cloudflare Pages, or any public web server).*

### Step 2: Add Repository in Anivio

1. Launch **Anivio** on your Android device, TV, or emulator.
2. Navigate to **Settings → Content & Discovery → Plugins**.
3. Tap **Add Repository** and enter your manifest URL:
   ```
   http://<YOUR_LOCAL_IP>:3000/manifest.json
   ```
4. The repository and all 9 providers will appear automatically with their respective logos and descriptions.
5. Enable the providers you want to use.

### Step 3: Test Providers

Tap the **Test** button next to any provider in the settings list:
* Anivio sends test ID `603` to the provider.
* Each plugin internally maps test ID `603` to a valid media entry (One Piece for anime providers, Overflow for adult providers) to verify live network connectivity and stream resolution.

---

## In-App Provider Settings

Several providers expose custom settings accessible via the gear icon in **Plugins Settings**:

* **Torrentio**:
  * **Torrentio Instance URL**: Point to self-hosted Torrentio or alternative Stremio-compatible torrent addons (defaults to `https://torrentio.strem.fun`).
  * **Configuration / Debrid Key**: Provide your Real-Debrid, AllDebrid, Premiumize, or TorBox API credentials or custom provider filter strings.
* **MKissa**:
  * **API Base URL**: Configure a custom or self-hosted Anivexa API mirror.
* **AniBD**:
  * **Server Preference**: Choose preferred default streaming mirror.
