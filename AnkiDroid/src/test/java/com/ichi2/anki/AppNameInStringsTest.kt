// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals

/**
 * People see the Fork as Hayaki, written the same way in every language (GLOSSARY.md).
 *
 * Interface text may still say AnkiDroid only where it means Upstream or the Official App,
 * for example Upstream's manual, its JavaScript API, or rating the Official App.
 * Every language is checked, not only English, and also where a translation spells
 * AnkiDroid in its own script.
 */
class AppNameInStringsTest {
    @Test
    fun `no interface text calls the app AnkiDroid unless it means Upstream or the Official App`() {
        val offending =
            interfaceText()
                .filter { it.name !in REFERS_TO_UPSTREAM }
                .filter { UPSTREAM_NAME.containsMatchIn(it.text.replace("/storage/emulated/0/AnkiDroid", "")) }
                .map { it.toString() }
        assertEquals(emptyList(), offending, "${offending.size} strings call the app AnkiDroid")
    }

    @Test
    fun `layouts and settings screens do not call the app AnkiDroid`() {
        val offending =
            resourceDirs
                .flatMap { res -> res.listFiles { dir -> dir.name.matches(Regex("(layout|xml|menu).*")) }.orEmpty().toList() }
                .flatMap { dir -> dir.listFiles { file -> file.extension == "xml" }.orEmpty().toList() }
                .flatMap { file ->
                    LITERAL_TEXT_ATTRIBUTE
                        .findAll(file.readText())
                        .map { it.groupValues[1] }
                        .filter { UPSTREAM_NAME.containsMatchIn(it.replace(PATH, "")) }
                        .map { "${file.parentFile?.name}/${file.name}: $it" }
                }
        assertEquals(emptyList(), offending)
    }

    @Test
    fun `Hayaki is written the same way in every language`() {
        val names = interfaceText().filter { it.name == "app_name" || it.name == "fork_name" }
        assertEquals(emptyList(), names.filter { it.language != DEFAULT_LANGUAGE }.map { it.toString() }, "the name is translated")
        assertEquals(listOf(FORK_NAME), names.filter { it.name == "fork_name" }.map { it.text })
    }

    /** One piece of interface text (a string, a plural form or an array item) in one language */
    private data class InterfaceText(
        val language: String,
        val name: String,
        val text: String,
    ) {
        override fun toString() = "$language/$name: ${text.trim().take(120)}"
    }

    /**
     * Every string a person can see: those defined in a module's default `values/` folder,
     * in that folder and in each of its translations. Addresses are not text.
     */
    private fun interfaceText(): List<InterfaceText> =
        resourceDirs.flatMap { res ->
            val defaultNames = parseValues(File(res, "values"), DEFAULT_LANGUAGE).mapTo(mutableSetOf()) { it.name }
            res
                .listFiles { dir -> dir.name == "values" || dir.name.startsWith("values-") }
                .orEmpty()
                .flatMap { dir -> parseValues(dir, if (dir.name == "values") DEFAULT_LANGUAGE else dir.name.removePrefix("values-")) }
                .filter { it.name in defaultNames && !it.name.startsWith("link_") && !ADDRESS.matches(it.text.trim()) }
        }

    private fun parseValues(
        dir: File,
        language: String,
    ): List<InterfaceText> {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        return dir.listFiles { file -> file.extension == "xml" }.orEmpty().flatMap { file ->
            val root = builder.parse(file).documentElement
            root.childElements().flatMap { element ->
                val name = element.getAttribute("name")
                when (element.tagName) {
                    "string" -> listOf(InterfaceText(language, name, element.textContent))
                    "plurals", "string-array" ->
                        element.childElements().map { InterfaceText(language, name, it.textContent) }.toList()
                    else -> emptyList()
                }
            }
        }
    }

    private fun Element.childElements(): Sequence<Element> =
        (0 until childNodes.length).asSequence().map { childNodes.item(it) }.filterIsInstance<Element>()

    companion object {
        const val FORK_NAME = "Hayaki"
        private const val DEFAULT_LANGUAGE = "en"

        // test working directory is the module directory: `AnkiDroid/`
        private val resourceDirs =
            listOf(
                "src/main/res",
                "../common/android/src/main/res",
                "../widgets/src/main/res",
                "../api/src/main/res",
                "../anki-common/src/main/res",
            ).map { File(it) }

        /**
         * AnkiDroid as translators write it: in Latin script (with the misspellings found in
         * translations) and in the other scripts translations spell it in.
         */
        val UPSTREAM_NAME =
            Regex(
                listOf(
                    "anki[\\s-]?droi",
                    "ankdroid",
                    "ankidorid",
                    "نكي\\s?درويد", // Arabic
                    "نکی[\\s\\u200c]?دروید", // Persian
                    "نکی\\s?ڈیرائڈ", // Urdu
                    "আনকি-?ড্র", // Bengali
                    "অঙ্কিড্র",
                    "[\\u0902\\u0901\\u094D]क[\\u093F\\u0940]ड्र", // Devanagari
                    "ಕಿಡ್ರಾಯ್ಡ್", // Kannada
                    "ಚಿಡ್ರಾಯ್ಡ್",
                    "ങ്കിഡ്രോയിഡ്", // Malayalam
                    "ంకిడ్రాయిడ్", // Telugu
                    "ᱠᱤᱰᱨᱚᱭᱮᱰ", // Santali
                    "анки\\s?-?дро", // Cyrillic
                    "𐌰𐌽𐌺𐌴𐌹𐌳𐍂𐌰𐌿𐌳", // Gothic
                    "アンキドロイド",
                    "אנקידרויד",
                ).joinToString("|"),
                RegexOption.IGNORE_CASE,
            )

        private val ADDRESS = Regex("(https?|market)://\\S*")

        /** A folder on the device, such as `/storage/emulated/0/AnkiDroid`, is not the app's name */
        private val PATH = Regex("/\\S+")

        private val LITERAL_TEXT_ATTRIBUTE = Regex("android:(?:text|title|summary|label|contentDescription|hint)=\"([^@?\"][^\"]*)\"")

        /**
         * Strings which say AnkiDroid because they mean Upstream or the Official App.
         */
        val REFERS_TO_UPSTREAM =
            setOf(
                // Upstream's documents and the Help menu's section about supporting Upstream
                "help_item_ankidroid_manual",
                "help_item_ankidroid_faq",
                "help_item_ankidroid_privacy_policy",
                "help_title_support_ankidroid",
                "help_item_support_develop_ankidroid",
                "help_item_support_rate_ankidroid",
                "help_item_support_other_ankidroid",
                "donate_description",
                "user_is_a_robot",
                // another app using the same folder is most likely the Official App
                "database_locked_summary_new",
                // Upstream's APIs, which the Fork implements under their own names
                "api_version_developer_contact",
                "valid_js_api_version",
                "update_js_api_version",
                "enable_api_title",
                "thirdparty_apps_summary",
                // the Fork's own text about Upstream and the Official App
                "about_fork_description",
                // never shown: tests the translation platform
                "crowdin_test_string_do_not_translate",
            )
    }
}
