package com.hackerapps.c2k.ui.screen.settings

import com.hackerapps.c2k.data.prefs.WeightUnit
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Heaviest weight Settings accepts, in kg; anything above is a typo, not a runner. */
internal const val MAX_WEIGHT_KG = 500f

/**
 * Parses the weight field as the user typed it, in [unit]'s terms, and returns kg, or null while
 * the text isn't a usable weight. Accepts either '.' or ',' as the decimal mark: [formatWeight]
 * shows the locale's separator (a comma in most of the app's languages), so the field must read
 * back what it displays, and a keyboard may offer either mark.
 */
internal fun parseWeightKg(text: String, unit: WeightUnit): Float? {
    val normalized = text.trim().replace(',', '.')
    // toFloatOrNull also accepts "NaN", "Infinity" and exponents; only plain decimals are weights.
    if (!normalized.matches(Regex("""\d+(\.\d*)?|\.\d+"""))) return null
    val kg = unit.toKg(normalized.toFloatOrNull() ?: return null)
    return kg.takeIf { it > 0f && it <= MAX_WEIGHT_KG }
}

/** Formats [value] with at most one decimal, in [locale]'s separator, without a trailing ".0". */
internal fun formatWeight(value: Float, locale: Locale = Locale.getDefault()): String {
    // "%.1f" renders the locale's decimal separator (a comma in Russian and many others), so a
    // whole number like 103 becomes "103,0": trim the character the locale actually used.
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    return String.format(locale, "%.1f", value).trimEnd('0').trimEnd(separator)
}
