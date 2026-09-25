<div align="center">

  <img src="composeApp/src/commonMain/composeResources/drawable/app_logo_wordmark.png" alt="Anivio" width="320" />

  <p>
    A free, open-source <strong>anime-first</strong> app for your phone, your desktop, and the TV you already own.
    <br />
    Anivio pulls trending, popular, and seasonal anime straight from AniList, then turns your own stream sources into a full library — artwork, ratings, subtitles, skip-intro, and your place saved on every screen.
  </p>

  [Releases](https://github.com/mdtahseen7/Anivio/releases) · Built on <a href="https://nuvio.tv">Nuvio</a>

</div>

## What it does

- **AniList-native catalog** — trending / popular / seasonal home rows, rich detail pages (episodes, cast, community reviews, prequels & sequels, trailers, more like this), and a weekly airing calendar, all sourced from AniList and `api.ani.zip` mappings.
- **Bring your own sources** — install Stremio-style stream addons and JavaScript scraper plugins; optional debrid support for cached links.
- **Built for watching anime** — skip intro/outro, per-episode downloads, sub/dub-aware stream picking, and content warnings sourced from AniList tags.
- **Signed-in AniList sync** — list status and episode progress write back to your account, plus your AniList notification feed inside the app.
- **Everywhere** — Android, iOS, desktop, and TV from one Kotlin Multiplatform + Compose Multiplatform codebase.

## Get Anivio

- **Android** — grab an APK from [Releases](https://github.com/mdtahseen7/Anivio/releases), or build from source below.
- **iOS / desktop** — build from source.

## Build from source

```bash
git clone https://github.com/mdtahseen7/Anivio.git
cd Anivio
```

### Android

Requires Android Studio and the Android SDK.

```bash
./gradlew :androidApp:assembleFullDebug
```

### iOS

Requires macOS and Xcode.

```bash
env NUVIO_IOS_DISTRIBUTION=full xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -derivedDataPath build/ios-derived-full-simulator \
  CODE_SIGNING_ALLOWED=NO \
  build
```

The shared app is built with Kotlin Multiplatform and Compose Multiplatform.

## Credits

Anivio is a fork of [Nuvio](https://nuvio.tv), re-focused as an anime-first app. Huge thanks to the Nuvio team for the foundation.

## License

[GNU General Public License v3.0](./LICENSE)
