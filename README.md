<div align="center">

  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="website/public/wordmark.png" />
    <img src="https://raw.githubusercontent.com/mdtahseen7/Anivio/main/design/anivio-branding/anivio-av-wordmark-on-light.svg" alt="Anivio" width="320" />
  </picture>

  <p>A free, open-source anime player for Windows and Android.</p>

  [Website](https://anivio.eu.cc/) · [Windows downloads](https://anivio.eu.cc/#download) · [GitHub releases](https://github.com/mdtahseen7/Anivio/releases)

</div>

## Get Anivio for Windows

Download and run the Windows x64 EXE installer. [Anivio downloads](https://anivio.eu.cc/#download)

<img src="https://raw.githubusercontent.com/mdtahseen7/Anivio/main/website/public/showcase/windows-home.webp" alt="Anivio for Windows showing the Home screen and Continue Watching" width="960" />

## Features

- Browse trending and seasonal titles from AniList, check airing dates, and explore episodes, trailers, reviews, and related titles.
- Connect Stremio-style addons and provider plugins, choose sub or dub streams, and download episodes for offline viewing.
- Customize subtitle fonts and timing, and skip intros and outros.
- Watch together with synchronized playback and chat in Watch Party.
- Keep separate libraries and watch progress for each profile. Customize avatars and anime backgrounds, and connect AniList to update your lists.

The web dashboard brings Continue Watching, profiles, saved titles, sources, and synced app preferences into one place. [Anivio dashboard](https://anivio.eu.cc/account)

## Development branches

- [`windows`](https://github.com/mdtahseen7/Anivio/tree/windows) contains the Windows app.
- [`main`](https://github.com/mdtahseen7/Anivio/tree/main) is the default branch for Android and the website.

## Build and release

```powershell
git clone --branch windows https://github.com/mdtahseen7/Anivio.git
cd Anivio
```

The Windows installer uses the native libmpv player, WebView2 SDK, and Inno Setup. Run **Build Windows installer** on the `windows` branch to get a test installer from Actions. [Windows build workflow](.github/workflows/windows-preview.yml)

For a combined Android and Windows release, run **Anivio release** from `main` with **Build Windows x64 and attach it to the same release** enabled. [Release workflow](https://github.com/mdtahseen7/Anivio/actions/workflows/build-apk.yml) · [Release guide](https://github.com/mdtahseen7/Anivio/blob/main/scripts/ANDROID_RELEASES.md)

## Credits

Anivio is maintained by [Stella](https://github.com/mdtahseen7) and built on [Nuvio](https://nuvio.tv). Video enhancement includes work from the [Anime4K project](https://github.com/bloc97/Anime4K).

## License

[GNU General Public License v3.0](./LICENSE)
