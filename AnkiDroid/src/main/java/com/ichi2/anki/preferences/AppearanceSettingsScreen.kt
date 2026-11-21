// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.annotation.StringRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.settings.enums.Theme
import com.ichi2.compose.theme.foundation
import com.ichi2.compose.theme.toFoundation
import com.ichi2.compose.ui.dialogs.DialogAction
import com.ichi2.compose.ui.dialogs.PaperDialog
import com.ichi2.themes.HayakiTheme
import com.ichi2.themes.Themes
import com.ichi2.anki.common.android.R as CommonR

internal enum class AppearanceMode(
    @StringRes val label: Int,
) {
    SYSTEM(R.string.settings_theme_system),
    LIGHT(R.string.settings_theme_light),
    DARK(R.string.settings_theme_dark),
    BLACK(R.string.settings_theme_black),
}

internal data class AppearanceSettingsState(
    val appearance: AppearanceMode,
    val nightTheme: NightTheme,
    val theme: HayakiTheme,
    val hasBackground: Boolean,
    /** Percentage opacity of the background image. */
    val backgroundImageOpacity: Int = 8,
    /** Percentage opacity of the deck rows over the background image. */
    val deckRowOpacity: Int = 100,
)

@Composable
internal fun AppearanceSettingsScreen(
    state: AppearanceSettingsState,
    searchTarget: String?,
    onBack: (() -> Unit)?,
    onAppearanceChange: (AppearanceMode) -> Unit,
    onThemeChange: (HayakiTheme) -> Unit,
    onNightThemeChange: (NightTheme) -> Unit,
    onChooseBackground: () -> Unit,
    onRemoveBackground: () -> Unit,
    onBackgroundImageOpacityChange: (Int) -> Int = { it },
    onDeckRowOpacityChange: (Int) -> Int = { it },
) {
    val foundation = MaterialTheme.foundation
    var choosingTheme by rememberSaveable { mutableStateOf(false) }
    SettingsScrollPage(stringResource(R.string.settings_appearance), onBack) {
        SettingsSearchTarget(searchTarget == stringResource(R.string.hayaki_theme_key)) { modifier, highlighted ->
            SettingsRow(
                title = stringResource(CommonString.app_theme),
                value = stringResource(state.theme.label),
                icon = R.drawable.symbol_palette,
                highlighted = highlighted,
                modifier = modifier,
                onClick = { choosingTheme = true },
                trailing = {
                    ThemeSwatch(foundation.colors.primary)
                    SettingsChevron()
                },
            )
        }
        SettingsDivider()
        SettingsSearchTarget(searchTarget == stringResource(R.string.app_theme_key)) { modifier, highlighted ->
            Column(
                if (highlighted) {
                    modifier.border(
                        foundation.shapes.hairline,
                        foundation.colors.emphasis,
                        foundation.shapes.button,
                    )
                } else {
                    modifier
                },
            ) {
                SettingsSectionTitle(stringResource(R.string.settings_appearance_mode))
                AppearanceTiles(state, onAppearanceChange)
            }
        }
        Column {
            Text(
                stringResource(R.string.settings_theme_note),
                Modifier.padding(top = 12.dp, bottom = 8.dp),
                style = foundation.type.caption,
                color = foundation.colors.onBackgroundMuted,
            )
            SettingsDivider()
            SettingsSearchTarget(searchTarget == stringResource(R.string.night_theme_key)) { modifier, highlighted ->
                NightThemeRow(state, highlighted, onNightThemeChange, modifier)
            }
            SettingsDivider()
        }
        SettingsSectionTitle(stringResource(R.string.settings_deck_list_background))
        SettingsDivider()
        SettingsSearchTarget(searchTarget == stringResource(R.string.pref_deck_picker_background_key)) { modifier, highlighted ->
            SettingsRow(
                title = stringResource(R.string.settings_choose_image),
                summary = stringResource(R.string.settings_choose_image_summary),
                icon = R.drawable.symbol_image,
                highlighted = highlighted,
                modifier = modifier,
                onClick = onChooseBackground,
                trailing = { SettingsChevron() },
            )
        }
        SettingsSearchTarget(searchTarget == stringResource(R.string.pref_remove_wallpaper_key)) { modifier, highlighted ->
            SettingsRow(
                title = stringResource(R.string.settings_remove_background),
                icon = R.drawable.symbol_hide_image,
                onClick = onRemoveBackground,
                enabled = state.hasBackground,
                highlighted = highlighted,
                modifier = modifier,
            )
        }
        if (state.hasBackground) {
            SettingsDivider()
            SettingsSearchTarget(searchTarget == stringResource(R.string.pref_background_image_opacity_key)) { modifier, highlighted ->
                SettingsSlider(
                    title = stringResource(R.string.settings_background_image_opacity),
                    value = state.backgroundImageOpacity,
                    valueRange = 0..100,
                    stepSize = 1f,
                    label = { "$it%" },
                    onValueChangeFinished = onBackgroundImageOpacityChange,
                    highlighted = highlighted,
                    modifier = modifier,
                )
            }
            SettingsSearchTarget(searchTarget == stringResource(R.string.pref_deck_row_opacity_key)) { modifier, highlighted ->
                SettingsSlider(
                    title = stringResource(R.string.settings_deck_row_opacity),
                    value = state.deckRowOpacity,
                    valueRange = 0..100,
                    stepSize = 1f,
                    label = { "$it%" },
                    onValueChangeFinished = onDeckRowOpacityChange,
                    highlighted = highlighted,
                    modifier = modifier,
                )
            }
        }
    }
    if (choosingTheme) {
        ThemePicker(
            selected = state.theme,
            onSelect = {
                choosingTheme = false
                onThemeChange(it)
            },
            onDismiss = { choosingTheme = false },
        )
    }
}

@Composable
private fun ThemePicker(
    selected: HayakiTheme,
    onSelect: (HayakiTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val appearance = Themes.currentTheme
    val foundation = MaterialTheme.foundation
    PaperDialog(
        title = stringResource(CommonString.app_theme),
        onDismissRequest = onDismiss,
        actions = listOf(DialogAction(stringResource(CommonString.dialog_cancel), onDismiss)),
    ) {
        Column(Modifier.selectableGroup()) {
            HayakiTheme.entries.forEach { theme ->
                val accent =
                    remember(context, theme, appearance) {
                        val preview = ContextThemeWrapper(context, theme.styleFor(appearance))
                        Color(MaterialColors.getColor(preview, CommonR.attr.foundationPrimary, "Hayaki Theme"))
                    }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = foundation.spacing.touchTarget)
                        .selectable(theme == selected, role = Role.RadioButton) { onSelect(theme) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = theme == selected, onClick = null)
                    Text(stringResource(theme.label), Modifier.weight(1f), style = foundation.type.body)
                    ThemeSwatch(accent)
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(accent: Color) {
    Box(Modifier.size(18.dp).background(accent, CircleShape))
}

@Composable
private fun AppearanceTiles(
    state: AppearanceSettingsState,
    onAppearanceChange: (AppearanceMode) -> Unit,
) {
    val appearances = AppearanceMode.entries
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columnCount = if (maxWidth / LocalDensity.current.fontScale >= 300.dp) appearances.size else 2
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            appearances.chunked(columnCount).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { appearance ->
                        AppearanceTile(
                            appearance,
                            state.theme,
                            state.nightTheme,
                            state.appearance == appearance,
                            { onAppearanceChange(appearance) },
                            Modifier.weight(1f),
                        )
                    }
                    repeat(columnCount - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun AppearanceTile(
    appearance: AppearanceMode,
    theme: HayakiTheme,
    nightTheme: NightTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val foundation = MaterialTheme.foundation
    Box(
        modifier
            .border(
                if (selected) 2.dp else foundation.shapes.hairline,
                if (selected) foundation.colors.emphasis else foundation.colors.outline,
                foundation.shapes.button,
            ).selectable(selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppearanceThumbnail(appearance, theme, nightTheme)
            Box(Modifier.heightIn(min = 34.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(appearance.label),
                    color = foundation.colors.onBackground,
                    style = foundation.type.body,
                    fontWeight = if (selected) FontWeight.SemiBold else null,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(foundation.colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.symbol_check), null, Modifier.size(16.dp), tint = foundation.colors.onPrimary)
            }
        }
    }
}

@Composable
private fun AppearanceThumbnail(
    appearance: AppearanceMode,
    theme: HayakiTheme,
    nightTheme: NightTheme,
) {
    val context = LocalContext.current
    val colors =
        remember(context, appearance, theme, nightTheme) {
            val variants: List<Theme> =
                when (appearance) {
                    AppearanceMode.SYSTEM -> listOf(DayTheme.LIGHT, nightTheme)
                    AppearanceMode.LIGHT -> listOf(DayTheme.LIGHT)
                    AppearanceMode.DARK -> listOf(NightTheme.DARK)
                    AppearanceMode.BLACK -> listOf(NightTheme.BLACK)
                }
            variants.map { ContextThemeWrapper(context, theme.styleFor(it)).toFoundation().colors }
        }
    val lightSide = colors.first()
    val darkSide = colors.last()
    val outline =
        if (appearance == AppearanceMode.DARK || appearance == AppearanceMode.BLACK) {
            darkSide.onBackgroundMuted
        } else {
            lightSide.onBackgroundMuted
        }
    Canvas(Modifier.size(width = 56.dp, height = 84.dp)) {
        val corner = CornerRadius(4.dp.toPx())
        val background =
            if (appearance == AppearanceMode.SYSTEM) {
                Brush.linearGradient(0.5f to lightSide.background, 0.5f to darkSide.background)
            } else {
                Brush.linearGradient(listOf(lightSide.background, lightSide.background))
            }
        drawRoundRect(background, cornerRadius = corner)
        drawRoundRect(outline, cornerRadius = corner, style = Stroke(1.dp.toPx()))

        val detail = Brush.linearGradient(0.5f to lightSide.onBackgroundMuted, 0.5f to darkSide.onBackgroundMuted)
        val inset = 8.dp.toPx()
        val lineCorner = CornerRadius(2.dp.toPx())
        drawRoundRect(detail, Offset(inset, 10.dp.toPx()), Size(28.dp.toPx(), 6.dp.toPx()), lineCorner)
        listOf(25, 33, 41).forEachIndexed { index, top ->
            val width = if (index == 2) 24.dp else 39.dp
            drawRoundRect(detail, Offset(inset, top.dp.toPx()), Size(width.toPx(), 3.dp.toPx()), lineCorner)
        }
        drawRoundRect(
            Brush.linearGradient(0.5f to lightSide.primary, 0.5f to darkSide.primary),
            Offset(inset, size.height - 18.dp.toPx()),
            Size(40.dp.toPx(), 8.dp.toPx()),
            lineCorner,
        )
    }
}

@Composable
private fun NightThemeRow(
    state: AppearanceSettingsState,
    highlighted: Boolean,
    onChange: (NightTheme) -> Unit,
    modifier: Modifier,
) {
    val foundation = MaterialTheme.foundation
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (highlighted) {
                    Modifier.border(foundation.shapes.hairline, foundation.colors.emphasis, foundation.shapes.button)
                } else {
                    Modifier
                },
            ).padding(vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val title: @Composable () -> Unit = {
            Text(stringResource(R.string.settings_night_theme), style = foundation.type.rowTitle, color = foundation.colors.onBackground)
        }
        val options: @Composable () -> Unit = {
            NightThemeOptions(state.nightTheme, enabled = state.appearance == AppearanceMode.SYSTEM, onChange = onChange)
        }
        if (maxWidth / LocalDensity.current.fontScale >= 300.dp) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.weight(1f)) { title() }
                options()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                title()
                options()
            }
        }
    }
}

@Composable
private fun NightThemeOptions(
    selected: NightTheme,
    enabled: Boolean,
    onChange: (NightTheme) -> Unit,
) {
    val foundation = MaterialTheme.foundation
    val foreground = if (enabled) foundation.colors.onBackground else foundation.colors.onBackgroundMuted
    Row(
        Modifier
            .selectableGroup()
            .border(foundation.shapes.hairline, foundation.colors.outline, foundation.shapes.button)
            .clip(foundation.shapes.button),
    ) {
        val options = listOf(NightTheme.DARK to R.string.settings_theme_dark, NightTheme.BLACK to R.string.settings_theme_black)
        options.forEach { (theme, label) ->
            val current = theme == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    Modifier
                        .heightIn(min = foundation.spacing.touchTarget)
                        .selectable(current, enabled, Role.RadioButton) { onChange(theme) }
                        .drawWithContent {
                            drawContent()
                            if (current) {
                                val thickness = foundation.shapes.hairline.toPx() * 2
                                drawLine(
                                    color = if (enabled) foundation.colors.emphasis else foreground,
                                    start = Offset(0f, size.height - thickness / 2),
                                    end = Offset(size.width, size.height - thickness / 2),
                                    strokeWidth = thickness,
                                )
                            }
                        }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (current) {
                        Icon(painterResource(R.drawable.symbol_check), null, Modifier.size(16.dp), tint = foreground)
                    }
                    Text(
                        stringResource(label),
                        color = foreground,
                        style = foundation.type.body,
                        fontWeight = if (current) FontWeight.SemiBold else null,
                    )
                }
            }
        }
    }
}
