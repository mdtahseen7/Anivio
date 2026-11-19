<div align="center">

  <img src="composeApp/src/commonMain/composeResources/drawable/app_logo_wordmark.png" alt="Anivio" width="320" />

  <p>
    A free, open-source <strong>anime-first</strong> app for your phone, your desktop, and the TV you already own.
    <br />
    Anivio pulls trending, popular, and seasonal anime straight from AniList, then turns your own stream sources into a full library — artwork, ratings, subtitles, skip-intro, and your place saved on every screen.
  </p>

  [![License](https://img.shields.io/github/license/mdtahseen7/Anivio-Desktop.svg?style=for-the-badge)](LICENSE)
  [![Stars](https://img.shields.io/github/stars/mdtahseen7/Anivio-Desktop.svg?style=for-the-badge)](https://github.com/mdtahseen7/Anivio-Desktop/stargazers)
  [![Forks](https://img.shields.io/github/forks/mdtahseen7/Anivio-Desktop.svg?style=for-the-badge)](https://github.com/mdtahseen7/Anivio-Desktop/network/members)
  [![Issues](https://img.shields.io/github/issues/mdtahseen7/Anivio-Desktop.svg?style=for-the-badge)](https://github.com/mdtahseen7/Anivio-Desktop/issues)

  [Releases](https://github.com/mdtahseen7/Anivio/releases) · Built on <a href="https://nuvio.tv">Nuvio</a>

</div>

## What it does

- **AniList-native catalog** — trending / popular / seasonal home rows, rich detail pages (episodes, cast, community reviews, prequels & sequels, trailers, more like this), and a weekly airing calendar, all sourced from AniList and `api.ani.zip` mappings.
- **Bring your own sources** — install Stremio-style stream addons and JavaScript scraper plugins; optional debrid support for cached links.
- **Built for watching anime** — skip intro/outro, per-episode downloads, sub/dub-aware stream picking, and content warnings sourced from AniList tags.
- **Signed-in AniList sync** — list status and episode progress write back to your account, plus your AniList notification feed inside the app.
- **Everywhere** — Android, iOS, desktop, and TV from one Kotlin Multiplatform + Compose Multiplatform codebase.

> **Alpha software.** Expect breaking changes with every update. Features, settings, stored data, and compatibility may change or stop working without notice. Do not rely on this build as your primary media app, and report any issues you encounter during testing.

## Get Anivio

- **Android** — grab an APK from [Releases](https://github.com/mdtahseen7/Anivio/releases), or build from source below.
- **Desktop (Linux)** — grab the AppImage built by CI (see [Desktop on Linux](#desktop-on-linux) below), or build from source.
- **Desktop (Windows / macOS)** — build from source below.

## Desktop on Linux

The desktop app is packaged as an **AppImage** by the GitHub Actions workflow in [`.github/workflows/arch-build.yml`](.github/workflows/arch-build.yml). Each push to the `arch-build` branch builds a fresh AppImage; download it from the workflow run's artifacts (`anivio-desktop-linux-appimage`).

### Run it

```bash
chmod +x Anivio-Linux-x86_64-0.1.26-alpha.AppImage
./Anivio-Linux-x86_64-0.1.26-alpha.AppImage
```

On Arch Linux, AppImages need FUSE 2:

```bash
sudo pacman -S fuse2
```

### System requirements (Linux)

The player uses your system's native libraries — they are **not** bundled in the AppImage. If one is missing, playback tells you which library is missing and how to install it instead of failing silently.

| Library | Arch | Fedora | Ubuntu / Debian |
| --- | --- | --- | --- |
| mpv (`libmpv.so.2`) | `sudo pacman -S mpv` | `sudo dnf install mpv` (RPM Fusion) | `sudo apt install libmpv2` |
| WebKitGTK (`libwebkit2gtk-4.1.so.0`) | `sudo pacman -S webkit2gtk` | `sudo dnf install webkit2gtk4.1` | `sudo apt install libwebkit2gtk-4.1-0` |
| GTK 3 (`libgtk-3.so.0`) | `sudo pacman -S gtk3` | `sudo dnf install gtk3` | `sudo apt install libgtk-3-0` |

On first launch the app may take a minute to load its home data (catalog fetch + poster decoding) — it is loading, not stuck.

### Installing it as a system app (Arch)

A `PKGBUILD` (`anivio-desktop-bin`) repackages the AppImage into a pacman-tracked install (`/opt/anivio/`, desktop entry + icon, launchable from the app menu). Copy the AppImage next to the `PKGBUILD` and run:

```bash
makepkg -si
```

### Linux fixes in this branch

The desktop build carries Linux-specific fixes for issues found on Hyprland + XWayland (1366×768, AMD R5):

| Fix | What it solves |
| --- | --- |
| mpv video-surface reconcile (`player_bridge.cpp`) | Fullscreen bars / thin video strip: the mpv video output size is now reconciled with the host window every tick, so a lost resize event can no longer leave the video at a stale size. |
| Badge metadata fetch timeout (`WatchedBadgeBulkResolver`) | Splash-screen hang: the startup badge resolution now caps each metadata fetch at 20s, so a stalled fetch can never block Home forever. |
| No blocking string loads (`TmdbMetadataService`, `RuntimeFormat`, `AniListMetaSource`, `HomeCatalogSettingsRepository`, `Components`) | App freeze on opening a details page: `runBlocking { getString(...) }` / `stringResource()` calls blocked dispatcher workers while waiting for a string load that itself needed a worker — a pool-starvation deadlock on dual-core machines. All of them now use plain literals/templates, so string loading can never starve the thread pool. |
| Details-page fetch caps (`AniListMetaSource`) | Details-page hang: the metadata fetch is capped (first phase 20s, enrichment 10s) and renders partial metadata after a timeout instead of hanging. |
| Playback tuning | Cheaper mpv scaling (bilinear), debanding / dithering / sigmoid upscaling off, larger mpv cache — smoother playback on integrated GPUs. |

## About

Nuvio Desktop is a media client for browsing metadata, managing collections and watch progress, downloading media, and playing streams from user-installed extensions or user-provided sources.

## Development

```bash
git clone https://github.com/mdtahseen7/Anivio-Desktop.git
cd Anivio-Desktop
```

### Android

Requires Android Studio and the Android SDK.

```bash
./gradlew :composeApp:run
```

### Desktop

```bash
./gradlew :composeApp:packageReleaseDistributionForCurrentOS
```

Platform-specific packaging:

```bash
# macOS
./scripts/build-macos-release-dmgs.sh --package-only

# Linux
./gradlew :composeApp:packageReleaseDeb
```

## Project Structure

- `composeApp/` contains the app code.
- `composeApp/src/commonMain/` contains shared UI, features, repositories, and platform-agnostic logic.
- `composeApp/src/desktopMain/` contains desktop-specific integrations.
- `composeApp/Configuration/DesktopVersion.properties` contains the desktop release version and build code.

## Versioning

Desktop versions are set in `composeApp/Configuration/DesktopVersion.properties`.

```properties
VERSION_NAME=0.1.1-alpha
VERSION_CODE=1
```

Use the version helper when changing desktop release versions:

```bash
./scripts/set-version.sh --desktop 0.1.2-alpha --desktop-code 2
./scripts/set-version.sh --show
```

## Contributing

Issues and pull requests are welcome. If you are fixing a desktop/Linux bug, please include the window manager / compositor, GPU, and screen resolution in your report — several desktop bugs only reproduce on specific setups.

## Credits

Anivio is a fork of [Nuvio](https://nuvio.tv), re-focused as an anime-first app. Huge thanks to the Nuvio team for the foundation.

## Legal & DMCA

Nuvio functions solely as a client-side interface for browsing metadata and playing media provided by user-installed extensions and/or user-provided sources. It is intended for content the user owns or is otherwise authorized to access.

Nuvio is not affiliated with any third-party extensions, catalogs, sources, or content providers. It does not host, store, or distribute any media content.

For comprehensive legal information, including our full disclaimer, third-party extension policy, and DMCA/Copyright information, please visit our [Legal & Disclaimer Page](https://nuvioapp.space/legal).

## Built With

- Kotlin Multiplatform
- Compose Multiplatform
- Kotlin
- Compose Desktop packaging
- Native desktop player integrations

## License

See [LICENSE](LICENSE).
