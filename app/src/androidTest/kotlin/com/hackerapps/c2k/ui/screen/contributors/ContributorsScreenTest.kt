package com.hackerapps.c2k.ui.screen.contributors

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContributorsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(resId: Int) = composeRule.activity.getString(resId)

    @Test
    fun contributors_are_listed_by_name() {
        composeRule.setContent { ContributorsScreen(onBack = {}) }

        // "xmgz" is both the contributor's name and their GitHub handle, so it legitimately
        // renders as two separate nodes — assert at least one rather than assuming uniqueness.
        assertTrue(composeRule.onAllNodesWithText("xmgz").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Andrew Farabee").assertExists()
        composeRule.onNodeWithText("Ilyushenok Ilya").assertExists()
        assertTrue(composeRule.onAllNodesWithText("Unibilens").fetchSemanticsNodes().isNotEmpty())
        // The list is lazy, so cards below the fold only exist once scrolled to.
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("west1nat0r"))
        assertTrue(composeRule.onAllNodesWithText("west1nat0r").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun contribution_descriptions_are_shown() {
        composeRule.setContent { ContributorsScreen(onBack = {}) }

        composeRule.onNodeWithText(string(R.string.contributor_translation_es)).assertExists()
        composeRule.onNodeWithText(string(R.string.contributor_fix_tts_ducking)).assertExists()
        composeRule.onNodeWithText(string(R.string.contributor_translation_ru)).assertExists()
        composeRule.onNodeWithText(string(R.string.contributor_language_selection)).assertExists()
        composeRule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(string(R.string.contributor_skip_warmup_cooldown_split)))
        composeRule.onNodeWithText(string(R.string.contributor_second_warning_toggle)).assertExists()
        composeRule.onNodeWithText(string(R.string.contributor_skip_warmup_cooldown_split)).assertExists()
    }

    @Test
    fun back_button_triggers_callback() {
        var backClicked = false
        composeRule.setContent { ContributorsScreen(onBack = { backClicked = true }) }

        composeRule.onNodeWithContentDescription(string(R.string.nav_back)).performClick()

        assertTrue(backClicked)
    }
}
