// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.text.style.URLSpan
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.text.parseAsHtml
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.compose.theme.foundation
import com.ichi2.compose.ui.dialogs.ConfirmationDialog
import com.ichi2.compose.ui.dialogs.DialogAction

internal data class AboutScreenState(
    val version: String,
    val buildDate: String,
    val backendVersion: String,
    val fsrsVersion: String?,
    val copyDebugInfoLabel: String,
    val updateCheckEnabled: Boolean,
)

@Composable
internal fun AboutScreen(
    state: AboutScreenState,
    searchTarget: String?,
    onBack: (() -> Unit)?,
    onLogo: () -> Unit,
    onLink: (String) -> Unit,
    onSupportUpstream: () -> Unit,
    onChangelog: () -> Unit,
    onCopyDebugInfo: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onUpdateCheckEnabledChange: (Boolean) -> Unit,
) {
    val foundation = MaterialTheme.foundation
    val name = stringResource(CommonString.fork_name)
    SettingsScrollPage(stringResource(CommonString.pref_cat_about_title), onBack) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Image(
                painterResource(R.mipmap.hayaki_mark),
                contentDescription = name,
                modifier = Modifier.size(88.dp).clip(foundation.shapes.dialog).clickable(role = Role.Button, onClick = onLogo),
            )
            Text(name, style = foundation.type.pageTitle, color = foundation.colors.onBackground)
            AboutValue(state.version, searchTarget == "version")
            AboutValue(state.buildDate, searchTarget == "build_date")
            AboutValue(state.backendVersion, searchTarget == "backend_version")
            state.fsrsVersion?.let { AboutValue(it, searchTarget == "fsrs_version") }
        }
        SettingsDivider()
        AboutSectionHeading(stringResource(CommonString.about_fork_title), searchTarget == "about_contributors_title")
        AboutLinks(
            stringResource(
                CommonString.about_fork_description,
                stringResource(R.string.link_source),
                stringResource(R.string.link_contributors),
            ),
            onLink,
        )
        AboutSectionHeading(stringResource(CommonString.license), searchTarget == "about_license_title")
        SettingsSearchTarget(searchTarget == "license_description") { modifier, highlighted ->
            Column(modifier.aboutHighlight(highlighted)) {
                AboutLinks(
                    stringResource(
                        CommonString.license_description,
                        stringResource(R.string.licence_wiki),
                        stringResource(R.string.link_agpl_wiki),
                        stringResource(R.string.link_fork_source),
                    ),
                    onLink,
                )
            }
        }
        AboutLinks(stringResource(CommonString.other_licenses, stringResource(R.string.dependency_license_wiki)), onLink)
        SettingsRow(
            stringResource(CommonString.help_title_support_ankidroid),
            icon = R.drawable.symbol_volunteer_activism,
            onClick = onSupportUpstream,
        )
        Column(Modifier.padding(top = 18.dp)) {
            SettingsDivider()
            SettingsSearchTarget(searchTarget == "open_changelog") { modifier, highlighted ->
                SettingsRow(
                    stringResource(CommonString.open_changelog),
                    icon = R.drawable.symbol_history,
                    modifier = modifier,
                    highlighted = highlighted,
                    onClick = onChangelog,
                )
            }
            SettingsSearchTarget(searchTarget == "check_for_updates") { modifier, highlighted ->
                SettingsRow(
                    stringResource(CommonString.update_check_title),
                    icon = R.drawable.symbol_download,
                    modifier = modifier,
                    highlighted = highlighted,
                    onClick = onCheckForUpdates,
                )
            }
            SettingsSearchTarget(searchTarget == "auto_check_for_updates") { modifier, highlighted ->
                SettingsSwitchRow(
                    stringResource(CommonString.update_check_auto),
                    checked = state.updateCheckEnabled,
                    onChange = onUpdateCheckEnabledChange,
                    modifier = modifier,
                    highlighted = highlighted,
                )
            }
            SettingsSearchTarget(searchTarget == "copy_debug_info") { modifier, highlighted ->
                SettingsRow(
                    state.copyDebugInfoLabel,
                    icon = R.drawable.symbol_content_copy,
                    modifier = modifier,
                    highlighted = highlighted,
                    onClick = onCopyDebugInfo,
                )
            }
        }
    }
}

@Composable
private fun AboutLinks(
    html: String,
    onLink: (String) -> Unit,
) {
    val foundation = MaterialTheme.foundation
    val description = remember(html) { html.parseAsHtml() }
    description.getSpans(0, description.length, URLSpan::class.java).forEach { link ->
        val label = description.subSequence(description.getSpanStart(link), description.getSpanEnd(link)).toString()
        SettingsRow(
            label,
            onClick = { onLink(link.url) },
            trailing = {
                Icon(painterResource(R.drawable.symbol_open_in_new), null, tint = foundation.colors.onBackgroundMuted)
            },
        )
    }
}

@Composable
private fun AboutValue(
    value: String,
    isSearchTarget: Boolean,
) {
    val foundation = MaterialTheme.foundation
    SettingsSearchTarget(isSearchTarget) { modifier, highlighted ->
        Text(
            value,
            modifier.aboutHighlight(highlighted).padding(horizontal = 4.dp),
            style = foundation.type.body,
            textAlign = TextAlign.Center,
            color = foundation.colors.onBackgroundMuted,
        )
    }
}

@Composable
private fun AboutSectionHeading(
    title: String,
    isSearchTarget: Boolean,
) {
    SettingsSearchTarget(isSearchTarget) { modifier, highlighted ->
        SettingsSectionTitle(title, modifier.aboutHighlight(highlighted))
    }
}

@Composable
private fun Modifier.aboutHighlight(highlighted: Boolean): Modifier {
    val foundation = MaterialTheme.foundation
    return if (highlighted) border(foundation.shapes.hairline, foundation.colors.emphasis, foundation.shapes.button) else this
}

@Composable
internal fun EnableDeveloperOptionsDialog(
    onCancel: () -> Unit,
    onEnable: () -> Unit,
) {
    ConfirmationDialog(
        title = stringResource(CommonString.dev_options_enabled_pref),
        message = stringResource(CommonString.dev_options_warning),
        markIcon = R.drawable.symbol_warning,
        confirm = DialogAction(stringResource(CommonString.dialog_ok), onEnable),
        dismissText = stringResource(CommonString.dialog_cancel),
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    )
}
