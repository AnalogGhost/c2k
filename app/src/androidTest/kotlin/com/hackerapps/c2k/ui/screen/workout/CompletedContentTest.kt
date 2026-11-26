package com.hackerapps.c2k.ui.screen.workout

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.model.ProgramSummary
import com.hackerapps.c2k.engine.Celebration
import com.hackerapps.c2k.engine.WorkoutState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What the completed-workout screen shows for each celebration. */
@RunWith(AndroidJUnit4::class)
class CompletedContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int, vararg args: Any) = composeRule.activity.getString(id, *args)

    private val shared = mutableListOf<String>()
    private var rated = 0
    private val opened = mutableListOf<String>()

    // The workout screen scrolls its content, so the buttons can sit below a small test screen.
    private fun show(
        celebration: Celebration?,
        programName: String = "5K to 50K",
        summary: ProgramSummary? = null,
        nextPrograms: List<String> = emptyList()
    ) = composeRule.setContent {
        Column(Modifier.verticalScroll(rememberScrollState())) { CompletedContent(
            state = WorkoutState.Completed(sessionId = 1, elapsedSessionSeconds = 1800),
            distanceMeters = 0f,
            personalBest = null,
            weightKg = null,
            celebration = celebration,
            programName = programName,
            programSummary = summary,
            nextPrograms = nextPrograms,
            onOpenProgram = { opened += it },
            onShare = { shared += it },
            onRate = { rated++ },
            onDone = {}
        ) }
    }

    @Test
    fun finishing_a_program_names_it_and_offers_share_and_rate() {
        show(Celebration.PROGRAM_FINISHED)
        composeRule.onNodeWithText(string(R.string.celebration_title, "5K to 50K")).assertExists()
        composeRule.onNodeWithText(string(R.string.celebration_message)).assertExists()
        composeRule.onNodeWithTag("celebration_week_three").assertDoesNotExist()

        composeRule.onNodeWithTag("celebration_share_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("celebration_rate_button").performScrollTo().performClick()
        assertEquals(listOf(string(R.string.celebration_share_text, "5K to 50K")), shared)
        assertEquals(1, rated)
    }

    @Test
    fun finishing_a_program_shows_its_totals_and_opens_a_suggested_program() {
        show(
            Celebration.PROGRAM_FINISHED, programName = "Couch to 5K",
            summary = ProgramSummary(workouts = 27, durationSeconds = 3 * 3600 + 5 * 60, distanceMeters = 85_200f),
            nextPrograms = listOf("B210K", "5KI")
        )
        composeRule.onNodeWithText(string(R.string.celebration_totals)).assertExists()
        composeRule.onNodeWithText("27").assertExists()
        composeRule.onNodeWithText("85.2").assertExists()
        composeRule.onNodeWithText("3:05:00").assertExists()

        composeRule.onNodeWithTag("celebration_next_5KI").performScrollTo().assertExists()
        composeRule.onNodeWithTag("celebration_next_B210K").performScrollTo().performClick()
        assertEquals(listOf("B210K"), opened)
    }

    @Test
    fun week_three_shows_the_smaller_card() {
        show(Celebration.WEEK_THREE, programName = "Couch to 5K")
        composeRule.onNodeWithText(string(R.string.workout_complete_title)).assertExists()
        composeRule.onNodeWithTag("celebration_week_three").assertExists()
        composeRule.onNodeWithText(string(R.string.celebration_week_three_title)).assertExists()

        composeRule.onNodeWithTag("celebration_share_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("celebration_rate_button").performScrollTo().performClick()
        assertEquals(listOf(string(R.string.celebration_share_text_week_three, "Couch to 5K")), shared)
        assertEquals(1, rated)
    }

    @Test
    fun an_ordinary_workout_has_no_share_or_rate() {
        show(celebration = null)
        composeRule.onNodeWithText(string(R.string.workout_complete_title)).assertExists()
        composeRule.onNodeWithTag("celebration_week_three").assertDoesNotExist()
        composeRule.onNodeWithTag("celebration_totals").assertDoesNotExist()
        composeRule.onNodeWithTag("celebration_share_button").assertDoesNotExist()
        composeRule.onNodeWithTag("celebration_rate_button").assertDoesNotExist()
    }
}
