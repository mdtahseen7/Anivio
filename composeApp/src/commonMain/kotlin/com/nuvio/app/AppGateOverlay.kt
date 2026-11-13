package com.nuvio.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import com.nuvio.app.core.ui.AvSplash
import com.nuvio.app.core.ui.NativeProfileSwitcherController
import com.nuvio.app.navigation.TabsRoute

@Composable
internal fun AppGateOverlay(
    onActivate: (AppScreenTab) -> Unit,
    onAppReady: (Boolean) -> Unit,
    onMainContentMountChanged: (Boolean) -> Unit,
    onMainContentVisibleChanged: (Boolean) -> Unit,
    nativeProfileSwitcherController: NativeProfileSwitcherController,
    appGateController: AppGateController,
) {
    val currentOnActivate by rememberUpdatedState(onActivate)
    val currentOnAppReady by rememberUpdatedState(onAppReady)
    val currentOnMainContentMountChanged by rememberUpdatedState(onMainContentMountChanged)
    val currentOnMainContentVisibleChanged by rememberUpdatedState(onMainContentVisibleChanged)

    AppEnvironment {
        var splashComplete by rememberSaveable { mutableStateOf(false) }
        var gateReady by remember { mutableStateOf(false) }
        LaunchedEffect(gateReady, splashComplete) { currentOnAppReady(gateReady && splashComplete) }
        Box {
        AppGate(
            initialTab = AppScreenTab.Home,
            initialRoute = TabsRoute,
            useNativeNavigation = true,
            useNativeTabBar = true,
            useTabletFloatingTabBar = false,
            ownsAppRuntime = true,
            bypassAppGate = false,
            renderMainContent = false,
            onNavigate = null,
            onGoBack = null,
            onReplace = null,
            onActivate = currentOnActivate,
            onAppReady = { gateReady = it },
            onMainContentMountChanged = currentOnMainContentMountChanged,
            onMainContentVisibleChanged = currentOnMainContentVisibleChanged,
            onTabTitles = null,
            nativeProfileSwitcherController = nativeProfileSwitcherController,
            appGateController = appGateController,
        )
        if (!splashComplete) AvSplash(onFinished = { splashComplete = true })
        }
    }
}
