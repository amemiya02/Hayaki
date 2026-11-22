// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.content.Context
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.deckpicker.BackgroundImage
import com.ichi2.anki.preferences.reviewer.ReviewerMenuSettingsFragment
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.themes.HayakiTheme
import java.text.Normalizer
import kotlin.reflect.KClass

internal data class SettingsSearchEntry(
    val title: String,
    val path: String,
    val destination: KClass<out Fragment>,
    val key: String? = null,
    val details: List<String> = emptyList(),
) {
    val id get() = "${destination.qualifiedName}/${key ?: title}"

    fun matchedDetails(query: String): String? =
        if (title.matches(query.searchTerms())) null else details.firstOrNull { it.matches(query.searchTerms()) }
}

internal fun List<SettingsSearchEntry>.matchingSettings(query: String): List<SettingsSearchEntry> {
    val terms = query.searchTerms()
    if (terms.isEmpty()) return emptyList()
    return filter { entry -> (listOf(entry.title) + entry.details).joinToString(" ").matches(terms) }
        .sortedByDescending { it.title.matches(terms) }
}

private val marks = Regex("\\p{M}+")
private val whitespace = Regex("\\s+")

internal fun String.searchTerms(): List<String> = trim().split(whitespace).filter(String::isNotEmpty)

private fun String.matches(terms: List<String>): Boolean {
    val text = searchable()
    return terms.all { it.searchable() in text }
}

private fun String.searchable(): String = Normalizer.normalize(lowercase(), Normalizer.Form.NFKD).replace(marks, "")

/** Indexes setting titles, summaries and choices without opening their screens. */
internal fun buildSettingsSearchIndex(
    fragment: Fragment,
    groups: List<SettingsGroup>,
): List<SettingsSearchEntry> =
    with(fragment.requireContext()) {
        groups
            .flatMap { group ->
                val title = getString(group.title)
                val groupEntry = SettingsSearchEntry(title, getString(CommonString.settings), group.destination)
                val entries =
                    when (group) {
                        SettingsGroup.GENERAL -> remainingSettingsSearchEntries(R.xml.preferences_general, title, group.destination)
                        SettingsGroup.APPEARANCE -> appearanceSearchEntries(title)
                        SettingsGroup.STUDYING -> studyingSearchEntries(title) + toolbarSearchEntries(title)
                        SettingsGroup.CONTROLS -> controlsSearchEntries(title, fragment::controlPreferenceTitle)
                        SettingsGroup.ACCESSIBILITY ->
                            remainingSettingsSearchEntries(
                                R.xml.preferences_accessibility,
                                title,
                                group.destination,
                            )
                        SettingsGroup.SYNC -> syncSearchEntries(title)
                        SettingsGroup.REVIEW_REMINDERS ->
                            listOf(
                                SettingsSearchEntry(
                                    getString(CommonString.schedule_reminders_notification_settings_menu),
                                    title,
                                    group.destination,
                                ),
                            )
                        SettingsGroup.BACKUPS -> remainingSettingsSearchEntries(R.xml.preferences_backup_limits, title, group.destination)
                        SettingsGroup.ADVANCED -> remainingSettingsSearchEntries(R.xml.preferences_advanced, title, group.destination)
                        SettingsGroup.ABOUT -> aboutSearchEntries(title)
                        SettingsGroup.DEVELOPER ->
                            remainingSettingsSearchEntries(
                                R.xml.preferences_developer_options,
                                title,
                                group.destination,
                            )
                        SettingsGroup.SWITCH_PROFILE -> emptyList()
                    }
                listOf(groupEntry) + entries
            }.distinctBy { it.id }
    }

private fun Context.appearanceSearchEntries(path: String): List<SettingsSearchEntry> {
    fun appearance(
        @StringRes title: Int,
        @StringRes key: Int,
        vararg details: Int,
    ) = SettingsSearchEntry(
        getString(title),
        path,
        AppearanceSettingsFragment::class,
        getString(key),
        details.map(::getString),
    )

    return buildList {
        add(
            appearance(
                R.string.settings_appearance_mode,
                R.string.app_theme_key,
                R.string.settings_theme_system,
                R.string.settings_theme_light,
                R.string.settings_theme_dark,
                R.string.settings_theme_black,
            ),
        )
        add(appearance(CommonString.app_theme, R.string.hayaki_theme_key, *HayakiTheme.entries.map { it.label }.toIntArray()))
        add(appearance(R.string.settings_night_theme, R.string.night_theme_key, R.string.settings_theme_note))
        add(
            appearance(
                R.string.settings_choose_image,
                R.string.pref_deck_picker_background_key,
                R.string.settings_deck_list_background,
                R.string.settings_choose_image_summary,
            ),
        )
        add(
            appearance(
                R.string.settings_remove_background,
                R.string.pref_remove_wallpaper_key,
                R.string.settings_deck_list_background,
            ),
        )
        if (BackgroundImage.shouldBeShown(this@appearanceSearchEntries)) {
            add(
                appearance(
                    R.string.settings_background_image_opacity,
                    R.string.pref_background_image_opacity_key,
                    R.string.settings_deck_list_background,
                ),
            )
            add(
                appearance(
                    R.string.settings_deck_row_opacity,
                    R.string.pref_deck_row_opacity_key,
                    R.string.settings_deck_list_background,
                ),
            )
        }
    }
}

private fun Context.toolbarSearchEntries(studying: String): List<SettingsSearchEntry> =
    ViewerAction.entries.filter { it.defaultDisplayType != null }.map {
        SettingsSearchEntry(
            it.title(this),
            breadcrumb(studying, getString(CommonString.toolbar_actions)),
            ReviewerMenuSettingsFragment::class,
            it.name,
        )
    }

private fun Context.syncSearchEntries(sync: String): List<SettingsSearchEntry> =
    remainingSettingsSearchEntries(R.xml.preferences_sync, sync, SyncSettingsFragment::class) +
        SettingsSearchEntry(
            title = getString(CommonString.paper_sync_server_address),
            path = sync,
            destination = SyncSettingsFragment::class,
            key = getString(R.string.custom_sync_server_key),
            details = listOf(getString(CommonString.paper_sync_change_server), getString(CommonString.sync_server_title)),
        )

private fun Context.aboutSearchEntries(path: String): List<SettingsSearchEntry> =
    listOf(
        SettingsSearchEntry(getString(R.string.settings_version), path, AboutFragment::class, "version"),
        SettingsSearchEntry(getString(R.string.settings_build_date), path, AboutFragment::class, "build_date"),
        SettingsSearchEntry(getString(R.string.settings_engine_version), path, AboutFragment::class, "backend_version"),
        SettingsSearchEntry(getString(R.string.settings_fsrs_version), path, AboutFragment::class, "fsrs_version"),
        SettingsSearchEntry(getString(CommonString.about_fork_title), path, AboutFragment::class, "about_contributors_title"),
        SettingsSearchEntry(getString(CommonString.license), path, AboutFragment::class, "about_license_title"),
        SettingsSearchEntry(getString(R.string.settings_source_code), path, AboutFragment::class, "license_description"),
        SettingsSearchEntry(TR.sentenceCase.checkForUpdates, path, AboutFragment::class, "check_for_updates"),
        SettingsSearchEntry(getString(CommonString.update_check_auto), path, AboutFragment::class, "auto_check_for_updates"),
        SettingsSearchEntry(TR.sentenceCase.copyDebugInfo, path, AboutFragment::class, "copy_debug_info"),
    )

private fun breadcrumb(vararg parts: String): String = parts.joinToString(" › ")
