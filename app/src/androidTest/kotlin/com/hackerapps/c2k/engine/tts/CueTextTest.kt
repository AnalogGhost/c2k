package com.hackerapps.c2k.engine.tts

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.utils.withLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** The spoken distance-and-pace phrases, in every language the app ships. */
@RunWith(AndroidJUnit4::class)
class CueTextTest {

    private val appLocales = listOf("en", "de", "es", "fr", "gl", "pt-BR", "ru", "tr")

    private fun cueText(languageTag: String): CueText {
        val base: Context = ApplicationProvider.getApplicationContext()
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(languageTag)) }
        return CueText(base.createConfigurationContext(config))
    }

    @Test
    fun english_phrases() {
        val text = cueText("en")
        assertEquals(
            "1 kilometre 500 metres. Pace 6 minutes and 40 seconds per kilometre",
            text.of(TtsAnnouncement.DistanceSplit(1500, 400))
        )
        assertEquals("1 kilometre. Pace 6 minutes per kilometre", text.of(TtsAnnouncement.DistanceSplit(1000, 360)))
        assertEquals("500 metres", text.of(TtsAnnouncement.DistanceSplit(500, null)))
        assertEquals("2 kilometres", text.of(TtsAnnouncement.DistanceSplit(2000, null)))
    }

    @Test
    fun an_english_fallback_voice_gets_english_words() {
        // The app in Galician on a device with no Galician voice: TtsManager rebuilds the cue
        // text from the app-language context in the voice's language.
        val galician = cueText("gl")
        val base: Context = ApplicationProvider.getApplicationContext()
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag("gl")) }
        val english = CueText(base.createConfigurationContext(config).withLocale(Locale.ENGLISH))

        val split = TtsAnnouncement.DistanceSplit(2000, null)
        assertEquals("2 quilómetros", galician.of(split))
        assertEquals("2 kilometres", english.of(split))
        assertEquals("Workout complete. Great job!", english.of(TtsAnnouncement.WorkoutComplete))
    }

    @Test
    fun russian_uses_its_plural_forms() {
        val text = cueText("ru")
        assertEquals("1 километр", text.of(TtsAnnouncement.DistanceSplit(1000, null)))
        assertEquals("2 километра", text.of(TtsAnnouncement.DistanceSplit(2000, null)))
        assertEquals("5 километров", text.of(TtsAnnouncement.DistanceSplit(5000, null)))
        assertEquals("21 километр 500 метров", text.of(TtsAnnouncement.DistanceSplit(21500, null)))
        assertTrue(text.of(TtsAnnouncement.DistanceSplit(1000, 400)).startsWith("1 километр. Темп 6 минут"))
    }

    @Test
    fun every_language_fills_every_placeholder() {
        val samples = listOf(
            TtsAnnouncement.DistanceSplit(500, 425),
            TtsAnnouncement.DistanceSplit(1000, 400),
            TtsAnnouncement.DistanceSplit(1500, null),
            TtsAnnouncement.DistanceSplit(2000, 365)
        )
        for (tag in appLocales) {
            val text = cueText(tag)
            for (sample in samples) {
                val spoken = text.of(sample)
                assertFalse("$tag: unfilled placeholder in \"$spoken\"", spoken.contains('%'))
                assertTrue("$tag: no distance in \"$spoken\"", spoken.contains((sample.meters / 1000).takeIf { it > 0 }?.toString() ?: "500"))
                if (sample.paceSecondsPerKm != null) {
                    assertNotEquals("$tag: pace missing", text.of(sample.copy(paceSecondsPerKm = null)), spoken)
                }
            }
        }
    }
}
