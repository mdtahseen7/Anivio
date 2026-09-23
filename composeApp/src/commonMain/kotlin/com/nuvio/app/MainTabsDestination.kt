package com.nuvio.app

import androidx.compose.material.icons.rounded.Explore
import nuvio.composeapp.generated.resources.compose_search_discover_title
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import com.nuvio.app.core.ui.NuvioCircularGlassButton
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.FloatingNavigationBar
import com.nuvio.app.core.ui.FloatingNavigationItem
import com.nuvio.app.core.ui.LocalNuvioBottomNavigationOverlayPadding
import com.nuvio.app.core.ui.LocalNuvioNavBarScrollState
import com.nuvio.app.core.ui.NuvioClassicNavigationBar
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.rememberNuvioNavBarScrollState
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileSwitcherTab
import com.nuvio.app.features.settings.NavBarStyle
import com.nuvio.app.features.settings.ThemeSettingsRepository
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_nav_home
import nuvio.composeapp.generated.resources.compose_nav_library
import nuvio.composeapp.generated.resources.compose_nav_profile
import nuvio.composeapp.generated.resources.compose_nav_search
import nuvio.composeapp.generated.resources.sidebar_library
import nuvio.composeapp.generated.resources.sidebar_search
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MainTabsDestination(
    selectedTab: AppScreenTab,
    initialHomeReady: Boolean,
    rootRouteActive: Boolean,
    useTabletFloatingTabBar: Boolean,
    useNativeNavigation: Boolean,
    useNativeTabBar: Boolean,
    liquidGlassNativeTabBarSupported: Boolean,
    liquidGlassNativeTabBarEnabled: Boolean,
    requests: AppTabRequests,
    state: AppTabState,
    actions: (isTabletLayout: Boolean) -> AppTabActions,
    onBack: () -> Unit,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
) {
    PlatformBackHandler(enabled = rootRouteActive, onBack = onBack)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletLayout = useTabletFloatingTabBar || maxWidth >= 768.dp
        val useNativeBottomTabs = if (useNativeNavigation) {
            useNativeTabBar
        } else {
            liquidGlassNativeTabBarSupported && liquidGlassNativeTabBarEnabled && initialHomeReady
        }
        val tabsRouteActive = rootRouteActive
        val navBarScrollState = rememberNuvioNavBarScrollState()
        val navBarHazeState = rememberHazeState()
        val navBarStyleSetting by remember { ThemeSettingsRepository.navBarStyle }.collectAsStateWithLifecycle()
        val navBarGlowEnabled by ThemeSettingsRepository.navBarGlowEnabled.collectAsStateWithLifecycle()
        val floatingNavigationItems = listOf(
            FloatingNavigationItem(
                selected = selectedTab == AppScreenTab.Home,
                onClick = { onTabSelected(AppScreenTab.Home) },
                icon = Icons.Filled.Home,
                label = stringResource(Res.string.compose_nav_home),
            ),
            FloatingNavigationItem(
                selected = selectedTab == AppScreenTab.Search,
                onClick = { onTabSelected(AppScreenTab.Search) },
                drawable = Res.drawable.sidebar_search,
                label = stringResource(Res.string.compose_nav_search),
            ),
            FloatingNavigationItem(
                selected = selectedTab == AppScreenTab.Library,
                onClick = { onTabSelected(AppScreenTab.Library) },
                drawable = Res.drawable.sidebar_library,
                label = stringResource(Res.string.compose_nav_library),
            ),
            FloatingNavigationItem(
                selected = selectedTab == AppScreenTab.Settings,
                onClick = { onTabSelected(AppScreenTab.Settings) },
                label = stringResource(Res.string.compose_nav_profile),
                content = {
                    ProfileSwitcherTab(
                        selected = selectedTab == AppScreenTab.Settings,
                        onClick = { onTabSelected(AppScreenTab.Settings) },
                        onProfileSelected = onProfileSelected,
                        onAddProfileRequested = onAddProfileRequested,
                    )
                },
            ),
        )
        val bottomNavigationItems = listOf(
            FloatingNavigationItem(
                selected = selectedTab == AppScreenTab.Home,
                onClick = { onTabSelected(AppScreenTab.Home) },
                icon = Icons.Filled.Home,
                label = stringResource(Res.string.compose_nav_home),
            ),
            FloatingNavigationItem(
                selected = selectedTab == AppScreenTab.Library,
                onClick = { onTabSelected(AppScreenTab.Library) },
                drawable = Res.drawable.sidebar_library,
                label = stringResource(Res.string.compose_nav_library),
            ),
            FloatingNavigationItem(
                selected = selectedTab == AppScreenTab.Discover,
                onClick = { onTabSelected(AppScreenTab.Discover) },
                icon = Icons.Rounded.Explore,
                label = stringResource(Res.string.compose_search_discover_title),
            ),
            FloatingNavigationItem(
                selected = selectedTab == AppScreenTab.Settings,
                onClick = { onTabSelected(AppScreenTab.Settings) },
                label = stringResource(Res.string.compose_nav_profile),
                content = {
                    ProfileSwitcherTab(
                        selected = selectedTab == AppScreenTab.Settings,
                        onClick = { onTabSelected(AppScreenTab.Settings) },
                        onProfileSelected = onProfileSelected,
                        onAddProfileRequested = onAddProfileRequested,
                    )
                },
            ),
        )

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (initialHomeReady) 1f else 0f),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (!isTabletLayout && !useNativeBottomTabs && navBarStyleSetting == NavBarStyle.CLASSIC) {
                    NuvioClassicNavigationBar {
                        NavItem(
                            selected = selectedTab == AppScreenTab.Home,
                            onClick = { onTabSelected(AppScreenTab.Home) },
                            icon = Icons.Filled.Home,
                            contentDescription = stringResource(Res.string.compose_nav_home),
                        )
                        NavItem(
                            selected = selectedTab == AppScreenTab.Library,
                            onClick = { onTabSelected(AppScreenTab.Library) },
                            icon = Res.drawable.sidebar_library,
                            contentDescription = stringResource(Res.string.compose_nav_library),
                        )
                        NavItem(
                            selected = selectedTab == AppScreenTab.Discover,
                            onClick = { onTabSelected(AppScreenTab.Discover) },
                            icon = Icons.Rounded.Explore,
                            contentDescription = stringResource(Res.string.compose_search_discover_title),
                        )
                        NavItem(
                            selected = selectedTab == AppScreenTab.Settings,
                            onClick = { onTabSelected(AppScreenTab.Settings) },
                        ) {
                            ProfileSwitcherTab(
                                selected = selectedTab == AppScreenTab.Settings,
                                onClick = { onTabSelected(AppScreenTab.Settings) },
                                onProfileSelected = onProfileSelected,
                                onAddProfileRequested = onAddProfileRequested,
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalNuvioBottomNavigationOverlayPadding provides if (useNativeBottomTabs) 49.dp else if (!isTabletLayout && navBarStyleSetting != NavBarStyle.CLASSIC) 72.dp else 0.dp,
                    LocalNuvioNavBarScrollState provides navBarScrollState,
                ) {
                    AppTabHost(
                        selectedTab = selectedTab,
                        requests = requests,
                        state = state,
                        actions = actions(isTabletLayout),
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (isTabletLayout || navBarStyleSetting != NavBarStyle.CLASSIC) Modifier.hazeSource(state = navBarHazeState) else Modifier)
                            .then(if (navBarStyleSetting == NavBarStyle.ADAPTIVE) Modifier.nestedScroll(navBarScrollState.nestedScrollConnection) else Modifier)
                            .padding(innerPadding),
                    )
                }

                // Search left the bottom bar, so it lives here as a floating pill-styled button in
                // the top-right corner, above whatever tab is showing.
                if (!isTabletLayout &&
                    selectedTab != AppScreenTab.Search &&
                    selectedTab != AppScreenTab.Library
                ) {
                    NuvioCircularGlassButton(
                        onClick = { onTabSelected(AppScreenTab.Search) },
                        hazeState = if (navBarStyleSetting != NavBarStyle.CLASSIC) navBarHazeState else null,
                        size = 48.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 8.dp, end = 16.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.sidebar_search),
                            contentDescription = stringResource(Res.string.compose_nav_search),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                if (isTabletLayout && !useNativeBottomTabs) {
                    FloatingNavigationBar(
                        modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 416.dp),
                        hazeState = navBarHazeState,
                        contentPadding = PaddingValues(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                            bottom = 8.dp,
                        ),
                        compactSize = true,
                        items = floatingNavigationItems,
                        glowEnabled = navBarGlowEnabled,
                    )
                }

                if (!isTabletLayout && !useNativeBottomTabs && navBarStyleSetting != NavBarStyle.CLASSIC) {
                    when (navBarStyleSetting) {
                        NavBarStyle.EXPANDED -> navBarScrollState.expand()
                        NavBarStyle.COMPACT -> navBarScrollState.collapse()
                        else -> {}
                    }
                    FloatingNavigationBar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        scrollState = navBarScrollState,
                        hazeState = navBarHazeState,
                        items = bottomNavigationItems,
                        glowEnabled = navBarGlowEnabled,
                    )
                }
            }
        }
    }
}
