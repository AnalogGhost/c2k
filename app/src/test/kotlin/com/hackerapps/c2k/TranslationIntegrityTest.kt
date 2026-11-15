package com.hackerapps.c2k

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks every translation against the English strings. A translated string whose placeholders
 * differ from the English one throws when the app formats it (MissingFormatArgumentException,
 * IllegalFormatConversionException) — for a voice cue, mid-run and only in that language, where
 * no other test would notice.
 */
class TranslationIntegrityTest {

    private val res = File("src/main/res")

    private data class Resources(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
        val arrays: Map<String, List<String>>,
    ) {
        val names get() = strings.keys + plurals.keys + arrays.keys
    }

    private fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun load(dir: String): Resources {
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(File(res, "$dir/strings.xml"))
        val root = doc.documentElement
        fun Element.translatable() = getAttribute("translatable") != "false"
        return Resources(
            strings = root.children("string").filter { it.translatable() }
                .associate { it.getAttribute("name") to it.textContent },
            plurals = root.children("plurals").filter { it.translatable() }
                .associate { p -> p.getAttribute("name") to p.children("item").associate { it.getAttribute("quantity") to it.textContent } },
            arrays = root.children("string-array").filter { it.translatable() }
                .associate { a -> a.getAttribute("name") to a.children("item").map { it.textContent } },
        )
    }

    private val locales: List<String> =
        res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!
            .map { it.name }.sorted()

    private val english = load("values")

    private val specifier = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?([a-zA-Z%])""")

    /**
     * The format arguments a string consumes, as "position:conversion" (e.g. "1:d", "2:s").
     * Unindexed specifiers take the next position, as String.format does. A '%' that isn't a valid
     * specifier is reported as "invalid" so a stray "50%" is caught.
     */
    private fun placeholders(text: String): List<String> {
        val found = mutableListOf<String>()
        var next = 1
        var index = 0
        while (true) {
            val percent = text.indexOf('%', index)
            if (percent < 0) break
            val match = specifier.matchAt(text, percent)
            if (match == null) {
                found += "invalid '%' at $percent"
                index = percent + 1
                continue
            }
            val conversion = match.groupValues[3]
            if (conversion != "%" && conversion != "n") {
                val position = match.groupValues[1].removeSuffix("$").toIntOrNull() ?: next++
                found += "$position:${conversion.lowercase()}"
            }
            index = match.range.last + 1
        }
        return found.sorted()
    }

    /** CLDR plural categories Android selects from for each language; a missing one falls back to English. */
    private fun requiredQuantities(locale: String): Set<String> = when (locale.removePrefix("values-").substringBefore('-')) {
        "ru" -> setOf("one", "few", "many", "other")
        else -> setOf("one", "other")
    }

    @Test
    fun the_locale_list_is_the_one_the_app_ships() {
        assertTrue(locales.toString(), locales.size >= 7)
    }

    @Test
    fun every_language_translates_every_string() {
        val problems = locales.flatMap { locale ->
            val missing = english.names - load(locale).names
            missing.sorted().map { "$locale: missing $it" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun no_language_has_strings_the_app_no_longer_uses() {
        val problems = locales.flatMap { locale ->
            val stale = load(locale).names - english.names
            stale.sorted().map { "$locale: $it isn't in the English strings" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun english_strings_have_valid_placeholders() {
        val problems = english.strings.flatMap { (name, text) -> placeholders(text).filter { it.startsWith("invalid") }.map { "$name: $it" } } +
            english.plurals.flatMap { (name, items) ->
                items.flatMap { (q, text) -> placeholders(text).filter { it.startsWith("invalid") }.map { "$name[$q]: $it" } }
            }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun translated_strings_take_the_same_arguments_as_english() {
        val problems = locales.flatMap { locale ->
            val translated = load(locale).strings
            english.strings.mapNotNull { (name, text) ->
                val theirs = translated[name] ?: return@mapNotNull null
                val expected = placeholders(text)
                val actual = placeholders(theirs)
                if (expected != actual) "$locale: $name has $actual, English has $expected" else null
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun translated_plurals_take_the_same_arguments_as_english() {
        // Every quantity keeps the number: in Russian "one" also covers 21, 31…, and in French
        // it covers 0, so dropping the %d from "one" would say the wrong number.
        val problems = locales.flatMap { locale ->
            val translated = load(locale).plurals
            english.plurals.flatMap { (name, items) ->
                val expected = placeholders(items.getValue("other"))
                val theirs = translated[name] ?: return@flatMap emptyList()
                theirs.mapNotNull { (q, text) ->
                    val actual = placeholders(text)
                    if (expected != actual) "$locale: $name[$q] has $actual, English has $expected" else null
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun plurals_have_every_form_the_language_needs() {
        val problems = locales.flatMap { locale ->
            val required = requiredQuantities(locale)
            load(locale).plurals.mapNotNull { (name, items) ->
                val missing = required - items.keys
                if (missing.isNotEmpty()) "$locale: $name is missing ${missing.sorted()}" else null
            }
        } + english.plurals.mapNotNull { (name, items) ->
            val missing = setOf("one", "other") - items.keys
            if (missing.isNotEmpty()) "values: $name is missing ${missing.sorted()}" else null
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun translated_lists_are_not_empty() {
        val problems = locales.flatMap { locale ->
            load(locale).arrays.mapNotNull { (name, items) ->
                if (items.isEmpty() || items.any { it.isBlank() }) "$locale: $name has an empty entry" else null
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
