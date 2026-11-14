package com.hackerapps.c2k.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class WeightUnitTest {

    @Test
    fun known_stored_names_round_trip() {
        WeightUnit.entries.forEach { assertEquals(it, WeightUnit.fromStored(it.name)) }
    }

    @Test
    fun missing_or_unknown_stored_values_fall_back_to_kg() {
        assertEquals(WeightUnit.KG, WeightUnit.fromStored(null))
        assertEquals(WeightUnit.KG, WeightUnit.fromStored("FURLONG"))
        assertEquals(WeightUnit.KG, WeightUnit.fromStored(""))
    }
}
