package com.nuvio.app.features.player

import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource

enum class VideoEnhancement(val label: StringResource, val description: StringResource, val shaders: List<String>) {
    Off(Res.string.enhancement_off, Res.string.enhancement_off_description, emptyList()),
    Light(Res.string.enhancement_light, Res.string.enhancement_light_description, listOf("Anime4K_Restore_CNN_S.glsl")),
    Anime4kA(Res.string.enhancement_a, Res.string.enhancement_a_description, listOf("Anime4K_Restore_CNN_S.glsl", "Anime4K_Upscale_CNN_x2_S.glsl")),
    Anime4kB(Res.string.enhancement_b, Res.string.enhancement_b_description, listOf("Anime4K_Restore_CNN_Soft_S.glsl", "Anime4K_Upscale_CNN_x2_S.glsl")),
    Anime4kC(Res.string.enhancement_c, Res.string.enhancement_c_description, listOf("Anime4K_Upscale_Denoise_CNN_x2_S.glsl")),
    Fsrcnnx(Res.string.enhancement_fsrcnnx, Res.string.enhancement_fsrcnnx_description, listOf("FSRCNNX_x2_8-0-4-1.glsl")),
    Anime4kGan(Res.string.enhancement_gan, Res.string.enhancement_gan_description, listOf("Anime4K_Upscale_GAN_x2_S.glsl"));

    companion object {
        fun fromStorage(value: String?): VideoEnhancement = entries.firstOrNull { it.name == value } ?: Off
    }
}

enum class EnhancementResolution(val label: StringResource, val description: StringResource, val longSide: Int, val shortSide: Int) {
    FullHd(Res.string.enhancement_output_1080, Res.string.enhancement_output_1080_description, 1920, 1080),
    Native2k(Res.string.enhancement_output_2k, Res.string.enhancement_output_2k_description, 2560, 1440);

    companion object {
        fun fromStorage(value: String?): EnhancementResolution = entries.firstOrNull { it.name == value } ?: Native2k
    }
}

enum class EnhancementResult {
    Off, Loaded, Unavailable, Failed, Recovered,
}
