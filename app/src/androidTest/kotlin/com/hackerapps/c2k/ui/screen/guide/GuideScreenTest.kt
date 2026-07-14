package com.hackerapps.c2k.ui.screen.guide

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun ComposeTestRule.waitUntilAssertion(timeoutMillis: Long = 10_000, assertion: () -> Unit) {
    waitUntil(timeoutMillis) {
        try {
            assertion()
            true
        } catch (e: AssertionError) {
            false
        }
    }
}

@RunWith(AndroidJUnit4::class)
class GuideScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(resId: Int) = composeRule.activity.getString(resId)

    @Test
    fun section_titles_are_displayed() {
        composeRule.setContent { GuideScreen(onBack = {}) }

        // Each section is a tall card of several FAQ entries, so only the first section or two
        // are actually composed within the LazyColumn's initial viewport — "Glossary" (the last
        // of 4 sections) isn't reachable without scrolling. Stick to what's on-screen by default.
        composeRule.onNodeWithText(string(R.string.guide_section_before_start)).assertExists()
    }

    @Test
    fun answer_is_hidden_until_question_is_tapped() {
        composeRule.setContent { GuideScreen(onBack = {}) }

        composeRule.onNodeWithText(string(R.string.guide_a_conversational_pace), substring = true)
            .assertDoesNotExist()

        composeRule.onNodeWithText(string(R.string.guide_q_conversational_pace)).performClick()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.guide_a_conversational_pace), substring = true)
                .assertExists()
        }
    }

    @Test
    fun tapping_again_collapses_the_answer() {
        composeRule.setContent { GuideScreen(onBack = {}) }

        composeRule.onNodeWithText(string(R.string.guide_q_conversational_pace)).performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.guide_a_conversational_pace), substring = true)
                .assertExists()
        }

        composeRule.onNodeWithText(string(R.string.guide_q_conversational_pace)).performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.guide_a_conversational_pace), substring = true)
                .assertDoesNotExist()
        }
    }

    @Test
    fun back_button_triggers_callback() {
        var backClicked = false
        composeRule.setContent { GuideScreen(onBack = { backClicked = true }) }

        composeRule.onNodeWithContentDescription(string(R.string.nav_back)).performClick()

        assertTrue(backClicked)
    }
}
