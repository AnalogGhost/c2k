package com.hackerapps.c2k.ui.screen.workout

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What the workout screen shows when Android refuses to start the workout. */
@RunWith(AndroidJUnit4::class)
class StartFailedContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = composeRule.activity.getString(id)

    private val clicks = mutableListOf<String>()

    private fun show(activityPermissionMissing: Boolean) = composeRule.setContent {
        StartFailedContent(
            activityPermissionMissing = activityPermissionMissing,
            onOpenAppSettings = { clicks += "settings" },
            onRetry = { clicks += "retry" },
            onBack = { clicks += "back" }
        )
    }

    @Test
    fun a_missing_permission_is_named_with_a_way_to_grant_it() {
        show(activityPermissionMissing = true)
        composeRule.onNodeWithText(string(R.string.workout_start_failed_title)).assertExists()
        composeRule.onNodeWithText(string(R.string.workout_start_failed_activity_permission)).assertExists()

        composeRule.onNodeWithTag("button_open_app_settings").performClick()
        composeRule.onNodeWithTag("button_retry_start").performClick()
        composeRule.onNodeWithText(string(R.string.workout_start_failed_back)).performClick()
        assertEquals(listOf("settings", "retry", "back"), clicks)
    }

    @Test
    fun with_the_permission_granted_the_message_is_general_and_settings_is_not_offered() {
        show(activityPermissionMissing = false)
        composeRule.onNodeWithText(string(R.string.workout_start_failed_generic)).assertExists()
        composeRule.onNodeWithText(string(R.string.workout_start_failed_activity_permission)).assertDoesNotExist()
        composeRule.onNodeWithTag("button_open_app_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("button_retry_start").assertExists()
    }
}
