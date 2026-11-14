package com.hackerapps.c2k.ui.screen.settings

import com.hackerapps.c2k.data.prefs.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class WeightTextTest {

    @Test
    fun parses_a_dot_decimal() {
        assertEquals(72.5f, parseWeightKg("72.5", WeightUnit.KG)!!, 0.0001f)
    }

    @Test
    fun parses_a_comma_decimal() {
        assertEquals(72.5f, parseWeightKg("72,5", WeightUnit.KG)!!, 0.0001f)
    }

    @Test
    fun parses_in_progress_entries() {
        assertEquals(72f, parseWeightKg("72,", WeightUnit.KG)!!, 0.0001f)
        assertEquals(72f, parseWeightKg(" 72 ", WeightUnit.KG)!!, 0.0001f)
    }

    @Test
    fun converts_from_the_display_unit_to_kg() {
        assertEquals(68.0389f, parseWeightKg("150", WeightUnit.LB)!!, 0.001f)
        assertEquals(76.2035f, parseWeightKg("12", WeightUnit.STONE)!!, 0.001f)
    }

    @Test
    fun rejects_text_that_is_not_a_weight() {
        listOf("", ",", "abc", "0", "-5", "NaN", "Infinity", "1e2", "72.5.1", "72,5,1").forEach {
            assertNull("\"$it\" should not parse", parseWeightKg(it, WeightUnit.KG))
        }
    }

    @Test
    fun rejects_implausible_weights() {
        assertNull(parseWeightKg("7000", WeightUnit.KG))
        assertNull(parseWeightKg("1200", WeightUnit.LB))  // ~544 kg
        assertEquals(MAX_WEIGHT_KG, parseWeightKg("500", WeightUnit.KG)!!, 0.0001f)
    }

    @Test
    fun formatted_weights_parse_back_in_every_app_locale() {
        val locales = listOf("en", "de", "es", "fr", "gl", "pt-BR", "ru", "tr").map(Locale::forLanguageTag)
        for (locale in locales) {
            for (kg in listOf(72.5f, 103f, 58.3f)) {
                val text = formatWeight(kg, locale)
                assertEquals("$locale: \"$text\"", kg, parseWeightKg(text, WeightUnit.KG)!!, 0.0001f)
            }
        }
    }

    @Test
    fun format_uses_the_locale_separator_and_drops_a_trailing_zero() {
        assertEquals("72,5", formatWeight(72.5f, Locale.GERMANY))
        assertEquals("72.5", formatWeight(72.5f, Locale.US))
        assertEquals("103", formatWeight(103f, Locale.forLanguageTag("ru")))
        assertEquals("103", formatWeight(103f, Locale.US))
    }
}
