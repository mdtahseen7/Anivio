# Bundled video enhancement shaders

The unmodified Anime4K shader files are from bloc97/Anime4K at commit
`7684e9586f8dcc738af08a1cdceb024cc184f426`, under the MIT license included
in each shader. https://github.com/bloc97/Anime4K

`FSRCNNX_x2_8-0-4-1.glsl` is from igv/FSRCNN-TensorFlow release 1.1.
Only comment whitespace was normalized; the shader code is unchanged.
The shader's own header specifies LGPL-3.0-or-later. Its license texts are
included alongside the repository's MIT license. The source is shipped here
and remains replaceable in source builds. https://github.com/igv/FSRCNN-TensorFlow/releases/tag/1.1

Anivio's adapter supplies mpv hook bindings, floating-point intermediate
textures and Media3 color conversion. These are phone-oriented chains:

- Light: Restore CNN S, no resize.
- A: Restore CNN S, then one Upscale CNN x2 S.
- B: Restore CNN Soft S, then one Upscale CNN x2 S.
- C: Upscale Denoise CNN x2 S.
- FSRCNNX: x2 8-channel model on transfer-encoded BT.709 luma, preserving chroma.
- GAN: experimental Upscale GAN x2 S.

These A/B/C chains omit the desktop templates' second upscaling stage.
The models' conditional passes run only when the display needs enlargement.
Output follows the display's aspect fit. The selectable caps are 1920×1080 and
2560×1440 (or their portrait equivalents); existing higher-resolution input is
not downscaled. Native display up to 2K is the default output policy, while
enhancement itself remains off until selected.
Intermediate features remain RGBA16F, including negative CNN activations.
Texture slots are reused only after their last consumer; total estimated
working memory, including input/output headroom, is capped at 96 MiB for 1080p.
Native/2K output allows 160 MiB on devices reporting at least 5 GB of RAM and
224 MiB at 7 GB or more; other devices retain 96 MiB. Android low-RAM devices
always retain the 32 MiB limit. Each graph allocates only the textures it needs.
The final subpixel reconstruction, display resize, chroma reconstruction and
transfer decode share a pass, avoiding a full 4K intermediate for 1080p inputs.
Small display increases also run the model; its native 2× samples are bilinearly
resampled to the display size. The player reports the actual input/output sizes.

HDR uses passthrough. GL initialization, resource creation, rendering and
deletion happen on Media3's GL thread. Selection callbacks are generation
scoped. Shader exceptions and sustained slow processing return to the original
picture; Media3 frame-processor errors rebuild the player without processing.
Effects are opt-in and use no network requests. A device-local marker disables
the saved mode after an unclean enhanced session, including an OS process kill.
Native GPU driver crashes cannot be caught by Kotlin; the marker prevents
automatically re-enabling the same effect on the next launch.
