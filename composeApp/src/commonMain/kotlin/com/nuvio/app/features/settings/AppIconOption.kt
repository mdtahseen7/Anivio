package com.nuvio.app.features.settings

internal enum class AppIconOption(
    val key: String,
    val platformName: String?,
) {
    AV(key = "av", platformName = null),
    ORIGINAL(
        key = "original",
        platformName = "AppIconOriginal",
    ),
    ARCTIC_BLUE(
        key = "arctic_blue",
        platformName = "AppIconArcticBlue",
    ),
    EMERALD(
        key = "emerald",
        platformName = "AppIconEmerald",
    ),
    ROSE_GOLD(
        key = "rose_gold",
        platformName = "AppIconRoseGold",
    ),
    COPPER(
        key = "copper",
        platformName = "AppIconCopper",
    ),
    GRAPHITE(
        key = "graphite",
        platformName = "AppIconGraphite",
    ),
    ;

    companion object {
        fun fromPlatformName(name: String?): AppIconOption =
            entries.firstOrNull { it.platformName == name } ?: AV
    }
}
