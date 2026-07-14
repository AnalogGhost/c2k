package com.hackerapps.c2k.ui.screen.contributors

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    }

    @Test
    fun contribution_descriptions_are_shown() {
        composeRule.setContent { ContributorsScreen(onBack = {}) }

        composeRule.onNodeWithText(string(R.string.contributor_translation_es)).assertExists()
        composeRule.onNodeWithText(string(R.string.contributor_fix_tts_ducking)).assertExists()
    }

    @Test
    fun back_button_triggers_callback() {
        var backClicked = false
        composeRule.setContent { ContributorsScreen(onBack = { backClicked = true }) }

        composeRule.onNodeWithContentDescription(string(R.string.nav_back)).performClick()

        assertTrue(backClicked)
    }
}
