# Anivio AV wordmark

Final: custom flat lockup, transparent, 776×160 design viewBox. AV monogram with uppercase ANIVIO, triangular counters, sharp diagonals and chamfered O. The app tints it for each theme.

Built-in image generation was used for concept exploration. Prompt: "Preserve the supplied AV silhouette, add matching uppercase ANIVIO, bold geometric strokes, triangular A, sharp V, chamfered O, clear spacing, solid ivory white, transparent background, no gradients or effects." Two generated drafts had noisy alpha and were not shipped. The final artwork was authored as clean vector geometry in the design-only SVG.

Runtime asset: `composeApp/src/commonMain/composeResources/drawable/app_logo_av_wordmark.png`, 2328×480 RGBA. Regenerate with `python scripts/export_av_wordmark.py` (Pillow). Only the PNG is packaged; the app uses `imageResource` and `BitmapPainter`, with no SVG or vector XML wordmark decoder. Transparent antialiased edges and theme tint preserve readability in both themes.

The older app-icon artwork is retained. AppBrandWordmark and AppIconOption.wordmarkResource share the new PNG resource, so footer, authentication, membership branding and other callers stay consistent.
