package com.nuvio.app.features.settings

import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_desktop_navigation_sidebar
import nuvio.composeapp.generated.resources.settings_desktop_navigation_top_bar
import org.jetbrains.compose.resources.StringResource

internal enum class DesktopNavigationLayout(val labelRes: StringResource) {
    Sidebar(Res.string.settings_desktop_navigation_sidebar),
    TopBar(Res.string.settings_desktop_navigation_top_bar);

    companion object {
        fun fromName(name: String?): DesktopNavigationLayout = entries.firstOrNull { it.name == name } ?: Sidebar
    }
}
