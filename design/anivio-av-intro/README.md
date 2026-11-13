# Anivio AV entrance

Design source for the AV entrance used by the native app splash. The six previous launcher icons remain available in Appearance settings.

- `assets/anivio-av-original.jpg`: supplied logo, unchanged.
- `compositions/av-reveal.html`: masked entrance and settle.
- `index.html`: 3.2-second, 1080-square, 60-fps composition.

Run `npm run dev` to preview and `npm run check` to validate. Reveal masks adapt the measured-dash technique from HyperFrames' installed `svg-stroke-trace` primitive. The visible logo is the original source, with no AI redraw.
