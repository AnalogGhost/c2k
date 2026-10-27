package com.hackerapps.c2k.ui.screen.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.prefs.UserPreferences
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

    private fun prefs() = UserPreferences(ApplicationProvider.getApplicationContext<Application>())

    private fun clearAllSessions() {
        runBlocking {
            repo().observeAllSessions().first().forEach { repo().deleteSession(it.id) }
            // A repeated week or dismissal left by one test would change another's next workout.
            prefs().clearProgression()
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

    private fun quantityString(resId: Int, quantity: Int, vararg args: Any) =
        composeRule.activity.resources.getQuantityString(resId, quantity, *args)

    @Test
    fun program_list_is_displayed() {
        setContent()
        composeRule.onNodeWithText(string(R.string.program_c25k)).assertExists()
    }

    @Test
    fun clicking_a_program_card_triggers_onSelectProgram() {
        var selected: String? = null
        setContent(onSelectProgram = { selected = it })

        // Invoke the card's click action directly instead of tapping its on-screen position: the
        // streak and continue items load asynchronously above the program list, and when they
        // arrive between locating the card and the tap, the tap lands on the neighbouring card.
        composeRule.onNodeWithText(string(R.string.program_c25k))
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
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

        // "Recent workouts" renders after all 6 program cards in the LazyColumn — off-screen
        // without scrolling (same class of issue as "Day 1" and "Glossary" elsewhere this
        // session, just surfacing as a timeout instead of an immediate failure). Scroll first.
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("home_screen_list")
                .performScrollToNode(hasText(string(R.string.home_recent_workouts)))
        }
        composeRule.onNodeWithText(string(R.string.home_recent_workouts)).assertExists()
    }

    @Test
    fun streak_shows_after_completing_a_session_today() {
        runBlocking {
            val id = repo().startSession("C25K", week = 1, day = 1)
            repo().finishSession(id, durationSeconds = 600, distanceMeters = 1000f, completed = true)
        }
        setContent()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(quantityString(R.plurals.home_streak, 1, 1), substring = true).assertExists()
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

        composeRule.runOnIdle {
            assertEquals(listOf(true, true, true, true), listOf(history, settings, guide, contributors))
        }
    }

    // Finishes C25K week 3 day 1, then stops week 3 day 2 early twice, so Continue points at
    // week 3 day 2 and the runner qualifies for a suggestion to repeat week 2.
    private fun struggleWithC25KWeek3Day2() = runBlocking {
        prefs().setLastProgramId("C25K")
        val done = repo().startSession("C25K", week = 3, day = 1)
        repo().finishSession(done, durationSeconds = 1200, distanceMeters = 2000f, completed = true)
        repeat(2) {
            // startedAt has millisecond resolution; keep each attempt strictly after the last.
            Thread.sleep(5)
            val id = repo().startSession("C25K", week = 3, day = 2)
            repo().finishSession(id, durationSeconds = 600, distanceMeters = 1000f, completed = false)
        }
    }

    private fun continueText(week: Int, day: Int) =
        string(R.string.home_continue_workout, string(R.string.program_c25k), week, day)

    @Test
    fun repeated_early_stops_suggest_repeating_the_previous_week() {
        struggleWithC25KWeek3Day2()
        setContent()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("progression_suggestion").assertIsDisplayed()
        }
        composeRule.onNodeWithText(
            quantityString(R.plurals.progression_body_repeat_week, 2, 2, 2)
        ).assertExists()
        composeRule.onNodeWithText(continueText(3, 2)).assertExists()
    }

    @Test
    fun accepting_the_suggestion_moves_continue_back_to_the_previous_week() {
        struggleWithC25KWeek3Day2()
        setContent()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("button_progression_accept").assertIsDisplayed()
        }

        composeRule.onNodeWithTag("button_progression_accept").performClick()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(continueText(2, 1)).assertExists()
        }
        composeRule.onNodeWithTag("progression_suggestion").assertDoesNotExist()
    }

    @Test
    fun dismissing_the_suggestion_hides_it_and_keeps_the_next_workout() {
        struggleWithC25KWeek3Day2()
        setContent()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("button_progression_dismiss").assertIsDisplayed()
        }

        composeRule.onNodeWithTag("button_progression_dismiss").performClick()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("progression_suggestion").assertDoesNotExist()
        }
        composeRule.onNodeWithText(continueText(3, 2)).assertExists()
    }
}
