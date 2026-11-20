package com.nuvio.app.features.details.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Rule
import kotlin.test.Test

class DesktopCommunityCommentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun longDiscussionScrollsSeparatelyFromTitleDetails() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.size(640.dp, 360.dp).verticalScroll(rememberScrollState())) {
                    DesktopCommunityComments {
                        repeat(100) { index -> Text("Comment $index", Modifier.height(48.dp)) }
                    }
                    Text("More like this")
                }
            }
        }

        compose.onNodeWithText("More like this").assertIsDisplayed()
        compose.onNodeWithText("Comment 0").assertDoesNotExist()

        compose.onNodeWithText("Open comments").performClick()
        compose.onNodeWithText("Comment 0").assertIsDisplayed()
        compose.onNodeWithText("Comment 99").assertIsNotDisplayed()
        compose.onNodeWithText("Comment 99").performScrollTo().assertIsDisplayed()

        compose.onNodeWithContentDescription("Close comments").performClick()
        compose.onNodeWithText("More like this").assertIsDisplayed()
        compose.onNodeWithText("Comment 99").assertDoesNotExist()
    }
}
