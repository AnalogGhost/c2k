package com.hackerapps.c2k.data.prefs

import com.hackerapps.c2k.R

/**
 * Body weight is always stored in kg; this only controls how Settings displays and edits it.
 */
enum class WeightUnit(val labelRes: Int, private val kgPerUnit: Float) {
    KG(R.string.weight_unit_kg, 1f),
    LB(R.string.weight_unit_lb, 0.45359237f),
    STONE(R.string.weight_unit_stone, 6.35029f);

    fun toKg(native: Float) = native * kgPerUnit
    fun fromKg(kg: Float) = kg / kgPerUnit
}
