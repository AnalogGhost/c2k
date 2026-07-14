package com.hackerapps.c2k.ui.screen.program

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.R
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PROGRAM_ID = "C25K"

// Same rationale as SettingsScreenTest: state changes route through a real Room-backed
// repository, so assertions right after a click can race the resulting recomposition.
private fun ComposeTestRule.waitUntilAssertion(timeoutMillis: Long = 5_000, assertion: () -> Unit) {
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
class ProgramSelectScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun repo() =
        (ApplicationProvider.getApplicationContext<Application>() as C2KApp).sessionRepository

    // Uses the real Room database (there's no in-memory DI seam for it), so every test starts
    // and ends from a clean slate for this program rather than relying on execution order.
    // Block bodies, not `= runBlocking { ... }` — see SettingsScreenTest.resetPreferences for
    // why an expression body risks JUnit rejecting the method as non-void.
    @Before
    fun clearProgress() {
        runBlocking { repo().resetProgress(PROGRAM_ID) }
    }

    @After
    fun cleanUpProgress() {
        runBlocking { repo().resetProgress(PROGRAM_ID) }
    }

    private fun setContent(
        onStartWorkout: (Int, Int) -> Unit = { _, _ -> },
        onBack: () -> Unit = {}
    ) {
        composeRule.setContent {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val vm = ProgramSelectViewModel(app, SavedStateHandle(mapOf("programId" to PROGRAM_ID)))
            ProgramSelectScreen(
                programId = PROGRAM_ID,
                onStartWorkout = onStartWorkout,
                onBack = onBack,
                vm = vm
            )
        }
    }

    private fun string(resId: Int, vararg args: Any) = composeRule.activity.getString(resId, *args)

    @Test
    fun week_and_day_labels_are_displayed() {
        setContent()
        // Week 1 is unique, but "Day 1" isn't — it's the first day of every week, and C25K's
        // 9 weeks start expanded (nothing's completed yet), so it legitimately renders once
        // per visible week. Assert at least one exists rather than assuming uniqueness.
        composeRule.onNodeWithText(string(R.string.program_week_label, 1)).assertExists()
        assertTrue(
            composeRule.onAllNodesWithText(string(R.string.program_day_label, 1))
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun back_button_triggers_callback() {
        var backClicked = false
        setContent(onBack = { backClicked = true })

        composeRule.onNodeWithContentDescription(string(R.string.nav_back)).performClick()

        assertTrue(backClicked)
    }

    @Test
    fun reset_menu_is_hidden_when_nothing_is_completed() {
        setContent()
        // No overflow / reset action should be reachable when there's no progress to reset.
        composeRule.onNodeWithText(string(R.string.program_reset_title)).assertDoesNotExist()
    }

    @Test
    fun reset_dialog_confirm_clears_progress() {
        runBlocking {
            val id = repo().startSession(PROGRAM_ID, week = 1, day = 1)
            repo().finishSession(id, durationSeconds = 600, distanceMeters = 1000f, completed = true)
        }
        setContent()

        // Open the overflow menu, then the reset confirmation dialog it reveals.
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("program_overflow_button").assertExists()
        }
        composeRule.onNodeWithTag("program_overflow_button").performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.program_reset_title)).assertExists()
        }
        composeRule.onNodeWithText(string(R.string.program_reset_title)).performClick()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.program_reset_message)).assertExists()
        }
        composeRule.onNodeWithText(string(R.string.program_reset_confirm)).performClick()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.program_reset_message)).assertDoesNotExist()
        }
    }
}
