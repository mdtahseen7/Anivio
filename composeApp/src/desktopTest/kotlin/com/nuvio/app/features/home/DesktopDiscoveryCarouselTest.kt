package com.nuvio.app.features.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.home.components.DesktopHomeDiscoverySection
import org.junit.Rule
import kotlin.test.Test

class DesktopDiscoveryCarouselTest {
    @get:Rule
    val compose = createComposeRule()

    private val items = List(4) { index -> MetaPreview(id = "pick-$index", type = "series", name = "Pick $index") }

    @Test
    fun spotlightAdvancesAfterManualSelectionAndWraps() {
        show(HomeDiscoverySection.Spotlight, mutableStateOf(true))
        compose.onNode(hasText("Pick 3") and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(400)
        assertSelected(3)
        compose.mainClock.advanceTimeBy(8_500)
        assertSelected(0)
        compose.mainClock.advanceTimeBy(8_500)
        assertSelected(1)
    }

    @Test
    fun surpriseMeWaitsUntilVisibleAndAdvances() {
        val visible = mutableStateOf(false)
        show(HomeDiscoverySection.Shuffle, visible)
        compose.onNode(hasText("Pick 1") and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(9_000)
        assertSelected(1)
        compose.runOnIdle { visible.value = true }
        compose.mainClock.advanceTimeBy(8_500)
        compose.onNode(hasText("Pick 1") and isSelected()).assertDoesNotExist()
    }

    private fun show(section: HomeDiscoverySection, visible: State<Boolean>) {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(920.dp)) {
                    DesktopHomeDiscoverySection(
                        section = section,
                        title = "Featured",
                        content = HomeDiscoveryContent(items.first(), items.drop(1), emptyList()),
                        autoAdvance = visible.value,
                        sectionPadding = 0.dp,
                        onOpen = null,
                        onLongClick = null,
                    )
                }
            }
        }
        compose.mainClock.autoAdvance = false
    }

    private fun assertSelected(index: Int) {
        compose.onNode(hasText("Pick $index") and isSelected()).assertIsDisplayed()
    }
}
