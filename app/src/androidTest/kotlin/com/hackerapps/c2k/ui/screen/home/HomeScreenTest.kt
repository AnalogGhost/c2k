package com.hackerapps.c2k.ui.screen.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// HomeScreen shows sessions across every program unfiltered, same as HistoryScreen — clear
// everything it would display rather than assuming other test classes clean up after themselves.
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
class HomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun repo() =
        (ApplicationProvider.getApplicationContext<Application>() as C2KApp).sessionRepository

    private fun clearAllSessions() {
        runBlocking {
            repo().observeAllSessions().first().forEach { repo().deleteSession(it.id) }
        }
    }

    @Before
    fun clearBefore() {
        clearAllSessions()
    }

    @After
    fun clearAfter() {
        clearAllSessions()
    }

    private fun setContent(
        onSelectProgram: (String) -> Unit = {},
        onContinueWorkout: (String, Int, Int) -> Unit = { _, _, _ -> },
        onOpenHistory: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onOpenGuide: () -> Unit = {},
        onOpenContributors: () -> Unit = {}
    ) {
        composeRule.setContent {
            val app = ApplicationProvider.getApplicationContext<Application>()
            HomeScreen(
                onSelectProgram = onSelectProgram,
                onContinueWorkout = onContinueWorkout,
                onOpenHistory = onOpenHistory,
                onOpenSettings = onOpenSettings,
                onOpenGuide = onOpenGuide,
                onOpenContributors = onOpenContributors,
                vm = HomeViewModel(app)
            )
        }
    }

    private fun string(resId: Int, vararg args: Any) = composeRule.activity.getString(resId, *args)

    @Test
    fun program_list_is_displayed() {
        setContent()
        composeRule.onNodeWithText(string(R.string.program_c25k)).assertExists()
    }

    @Test
    fun clicking_a_program_card_triggers_onSelectProgram() {
        var selected: String? = null
        setContent(onSelectProgram = { selected = it })

        composeRule.onNodeWithText(string(R.string.program_c25k)).performClick()

        composeRule.waitUntilAssertion {
            assertEquals("C25K", selected)
        }
    }

    @Test
    fun recent_workout_appears_after_a_completed_session() {
        runBlocking {
            val id = repo().startSession("C25K", week = 1, day = 1)
            repo().finishSession(id, durationSeconds = 600, distanceMeters = 1000f, completed = true)
        }
        setContent()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.home_recent_workouts)).assertExists()
        }
    }

    @Test
    fun streak_shows_after_completing_a_session_today() {
        runBlocking {
            val id = repo().startSession("C25K", week = 1, day = 1)
            repo().finishSession(id, durationSeconds = 600, distanceMeters = 1000f, completed = true)
        }
        setContent()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.home_streak, 1), substring = true).assertExists()
        }
    }

    @Test
    fun top_bar_icons_trigger_their_callbacks() {
        var history = false
        var settings = false
        var guide = false
        var contributors = false
        setContent(
            onOpenHistory = { history = true },
            onOpenSettings = { settings = true },
            onOpenGuide = { guide = true },
            onOpenContributors = { contributors = true }
        )

        composeRule.onNodeWithContentDescription(string(R.string.history_title)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.settings_title)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.guide_title)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.contributors_title)).performClick()

        assertEquals(listOf(true, true, true, true), listOf(history, settings, guide, contributors))
    }
}
