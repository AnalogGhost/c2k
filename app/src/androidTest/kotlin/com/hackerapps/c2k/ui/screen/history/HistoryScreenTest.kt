package com.hackerapps.c2k.ui.screen.history

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PROGRAM_ID = "C25K"

// Same rationale as SettingsScreenTest: state changes route through a real Room-backed
// repository, so assertions right after seeding data can race the resulting recomposition.
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

// Covers rendering only: empty state, aggregate stats, and session list content. The
// swipe-to-dismiss delete flow (SwipeToDismissBox in HistoryScreen) needs gesture simulation
// that's fiddly to get reliable in Compose UI tests — deliberately left for a follow-up rather
// than a guessed-at performTouchInput { swipeLeft() } that might be flaky or just wrong.
@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun repo() =
        (ApplicationProvider.getApplicationContext<Application>() as C2KApp).sessionRepository

    // HistoryScreen shows sessions across every program unfiltered, and this is the real
    // on-disk database (no in-memory DI seam), so clear everything it would display rather
    // than just this test's own program — other test classes touching the same DB shouldn't
    // be able to make these assertions flaky.
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

    private fun setContent(onBack: () -> Unit = {}) {
        composeRule.setContent {
            val app = ApplicationProvider.getApplicationContext<Application>()
            HistoryScreen(onBack = onBack, vm = HistoryViewModel(app))
        }
    }

    private fun string(resId: Int, vararg args: Any) = composeRule.activity.getString(resId, *args)

    @Test
    fun shows_empty_message_when_no_sessions_exist() {
        setContent()
        composeRule.onNodeWithText(string(R.string.history_empty)).assertExists()
    }

    @Test
    fun completed_session_appears_in_the_list() {
        runBlocking {
            val id = repo().startSession(PROGRAM_ID, week = 2, day = 3)
            repo().finishSession(id, durationSeconds = 600, distanceMeters = 1000f, completed = true)
        }
        setContent()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.history_week_day, 2, 3), substring = true).assertExists()
        }
    }

    @Test
    fun stats_card_reflects_seeded_sessions() {
        runBlocking {
            val id1 = repo().startSession(PROGRAM_ID, week = 1, day = 1)
            repo().finishSession(id1, durationSeconds = 600, distanceMeters = 1000f, completed = true)
            val id2 = repo().startSession(PROGRAM_ID, week = 1, day = 2)
            repo().finishSession(id2, durationSeconds = 300, distanceMeters = 500f, completed = true)
        }
        setContent()

        // 2 completed sessions, 1.5 km total — matches HistoryViewModel.computeStats, already
        // unit-tested directly; this just checks the screen actually renders those numbers.
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText("2").assertExists()
        }
        composeRule.onNodeWithText("1.5").assertExists()
    }

    @Test
    fun empty_message_is_gone_once_a_session_exists() {
        runBlocking {
            val id = repo().startSession(PROGRAM_ID, week = 1, day = 1)
            repo().finishSession(id, durationSeconds = 600, distanceMeters = 1000f, completed = true)
        }
        setContent()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.history_empty)).assertDoesNotExist()
        }
    }
}
