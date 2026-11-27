package com.hackerapps.c2k.ui.share

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareCardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val stats = ShareCardStats(
        programId = "C25K", week = 3, day = 2, finishedProgram = false,
        durationSeconds = 1800, distanceMeters = 3900f, startedAt = 0L
    )
    private val route = listOf(OutlinePoint(0.0, 0.0), OutlinePoint(1.0, 0.5), OutlinePoint(0.2, 1.0))

    @Test
    fun the_image_is_story_sized_for_every_backdrop() {
        for (background in ShareBackground.builtIn + ShareBackground.Sticker) {
            val image = ShareCardRenderer.render(context, stats, route, background, customPhoto = null)
            assertEquals(1080, image.width)
            assertEquals(1920, image.height)
        }
    }

    @Test
    fun the_sticker_is_transparent_and_photos_are_not() {
        val sticker = ShareCardRenderer.render(context, stats, route, ShareBackground.Sticker, null)
        assertEquals(0, android.graphics.Color.alpha(sticker.getPixel(540, 700)))
        val photo = ShareCardRenderer.render(context, stats, route, ShareBackground.Photo(5), null)
        assertEquals(255, android.graphics.Color.alpha(photo.getPixel(540, 700)))
    }

    @Test
    fun a_picked_photo_fills_the_image() {
        val red = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        val image = ShareCardRenderer.render(context, stats, emptyList(), ShareBackground.Custom, red)
        // Top corner, above the darkened lower half: still clearly red after centre-cropping.
        val pixel = image.getPixel(1070, 300)
        assertTrue(android.graphics.Color.red(pixel) > 150)
        assertNotEquals(android.graphics.Color.red(pixel), android.graphics.Color.green(pixel))
    }

    @Test
    fun the_dialog_offers_backdrops_and_shares() {
        composeRule.setContent {
            ShareCardDialog(stats = stats, loadRoute = { route }, message = null, onDismiss = {})
        }
        composeRule.onNodeWithTag("share_bg_photo_1").assertIsSelected()
        composeRule.onNodeWithTag("share_bg_gradient_1").performScrollTo().performClick()
        composeRule.onNodeWithTag("share_bg_gradient_1").assertIsSelected()
        composeRule.onNodeWithTag("share_bg_sticker").performScrollTo().performClick()
        composeRule.onNodeWithTag("share_bg_sticker").assertIsSelected()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("share_card_preview")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("share_card_share").assertIsEnabled()
    }
}
