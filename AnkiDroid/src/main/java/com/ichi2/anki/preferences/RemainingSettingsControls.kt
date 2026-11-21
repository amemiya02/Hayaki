// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.text.InputType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.compose.theme.foundation
import com.ichi2.compose.ui.dialogs.DialogAction
import com.ichi2.compose.ui.dialogs.DialogActionStyle
import com.ichi2.compose.ui.dialogs.DialogChoice
import com.ichi2.compose.ui.dialogs.DialogChoiceRow
import com.ichi2.compose.ui.dialogs.DialogDetail
import com.ichi2.compose.ui.dialogs.DialogField
import com.ichi2.compose.ui.dialogs.ErrorDialog
import com.ichi2.compose.ui.dialogs.PaperDialog
import com.ichi2.compose.ui.dialogs.SingleChoiceDialog
import com.ichi2.compose.ui.dialogs.TextInputDialog
import com.ichi2.preferences.DialogPreference
import com.ichi2.preferences.ExternalDirectorySelectionPreference
import com.ichi2.preferences.IncrementerNumberRangePreferenceCompat
import com.ichi2.preferences.IncrementerNumberRangePreferenceCompat.Companion.ValidationResult
import com.ichi2.preferences.ListPreference
import com.ichi2.preferences.NumberRangePreferenceCompat
import com.ichi2.preferences.Preference
import com.ichi2.preferences.SliderPreference
import com.ichi2.preferences.SwitchPreference
import com.ichi2.preferences.VersatileTextPreference
import com.ichi2.preferences.VersatileTextWithASwitchPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.roundToInt
import com.ichi2.anki.common.android.R as CommonR

@Composable
internal fun PreferenceRow(
    preference: Preference,
    modifier: Modifier,
    highlighted: Boolean,
) {
    val title = preference.title?.toString() ?: return
    val summary = preference.summary?.toString()
    val enabled = preference.isEnabled
    val warningIcon =
        when (preference.key) {
            stringResource(R.string.pref_allow_dangerous_js_api),
            stringResource(R.string.pref_allow_card_external_launch_key),
            -> CommonR.drawable.symbol_warning
            else -> null
        }
    when (preference) {
        is SwitchPreference ->
            SettingsSwitchRow(
                title = title,
                checked = preference.isChecked,
                onChange = { preference.performClick() },
                summary = summary,
                enabled = enabled,
                modifier = modifier,
                highlighted = highlighted,
                icon = warningIcon,
            )
        is SliderPreference -> SliderSetting(preference, title, summary, modifier, highlighted)
        else ->
            SettingsRow(
                title = title,
                summary = summary,
                enabled = enabled,
                onClick = if (preference.isSelectable) preference::performClick else null,
                modifier = modifier,
                highlighted = highlighted,
                trailing = {
                    if (preference is VersatileTextWithASwitchPreference) {
                        val colors = MaterialTheme.foundation.colors
                        val canToggle = !preference.text.isNullOrEmpty()
                        Switch(
                            checked = preference.switchChecked,
                            onCheckedChange = if (canToggle) { checked -> preference.switchChecked = checked } else null,
                            enabled = enabled,
                            modifier =
                                Modifier.semantics {
                                    contentDescription = preference.title.toString()
                                },
                            colors =
                                SwitchDefaults.colors(
                                    checkedThumbColor = colors.onPrimary,
                                    checkedTrackColor = colors.primary,
                                    uncheckedThumbColor = colors.onBackgroundMuted,
                                    uncheckedTrackColor = Color.Transparent,
                                    uncheckedBorderColor = colors.onBackgroundMuted,
                                ),
                        )
                    } else if (preference is DialogPreference || preference.fragment != null) {
                        SettingsChevron()
                    }
                },
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderSetting(
    preference: SliderPreference,
    title: String,
    summary: String?,
    modifier: Modifier,
    highlighted: Boolean,
) {
    SettingsSlider(
        title = title,
        summary = summary,
        value = preference.value,
        valueRange = preference.valueFrom..preference.valueTo,
        stepSize = preference.stepSize,
        label = preference::valueLabel,
        enabled = preference.isEnabled,
        modifier = modifier,
        highlighted = highlighted,
        onValueChangeFinished = { proposed ->
            if (proposed != preference.value && preference.callChangeListener(proposed)) preference.value = proposed
            preference.value
        },
    )
}

/**
 * A settings row with a slider. [onValueChangeFinished] receives the chosen value when the user
 * lets go and returns the value actually stored, which the slider then shows.
 */
@Composable
internal fun SettingsSlider(
    title: String,
    value: Int,
    valueRange: IntRange,
    onValueChangeFinished: (Int) -> Int,
    modifier: Modifier = Modifier,
    summary: String? = null,
    stepSize: Float = 0f,
    label: (Int) -> String? = { null },
    enabled: Boolean = true,
    highlighted: Boolean = false,
) {
    val foundation = MaterialTheme.foundation
    val colors = foundation.colors
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val range = valueRange.first.toFloat()..valueRange.last.toFloat()
    val span = range.endInclusive - range.start
    val valueLabel = label(current.roundToInt())
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (highlighted) Modifier.border(foundation.shapes.hairline, colors.emphasis, foundation.shapes.button) else Modifier,
            ).padding(top = 12.dp, bottom = 4.dp),
    ) {
        Text(title, style = foundation.type.rowTitle, color = colors.onBackground)
        if (summary != null) Text(summary, style = foundation.type.caption, color = colors.onBackgroundMuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Slider(
                value = current,
                onValueChange = { current = it },
                onValueChangeFinished = { current = onValueChangeFinished(current.roundToInt()).toFloat() },
                modifier =
                    Modifier.weight(1f).height(foundation.spacing.touchTarget).semantics {
                        contentDescription = title
                        if (valueLabel != null) stateDescription = valueLabel
                    },
                enabled = enabled,
                valueRange = range,
                steps = if (stepSize == 0f) 0 else (span / stepSize).roundToInt() - 1,
                thumb = { Box(Modifier.size(18.dp).background(colors.progressFill, CircleShape)) },
                track = {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(colors.progressTrack)) {
                        Box(Modifier.fillMaxWidth((current - range.start) / span).height(3.dp).background(colors.progressFill))
                    }
                },
            )
            if (valueLabel != null) Text(valueLabel, style = foundation.type.body, color = colors.onBackground)
        }
    }
    SettingsDivider()
}

@Composable
internal fun SettingEditor(
    preference: DialogPreference,
    onDismiss: () -> Unit,
    showDialog: (@Composable (() -> Unit) -> Unit) -> Unit,
) {
    val title = (preference.dialogTitle ?: preference.title).toString()
    when (preference) {
        is ExternalDirectorySelectionPreference -> DirectoryEditor(preference, title, onDismiss, showDialog)
        is ListPreference -> {
            val labels = preference.entries.orEmpty()
            val values = preference.entryValues.orEmpty()
            SingleChoiceDialog(
                title = title,
                choices = labels.map { DialogChoice(it.toString()) },
                selectedIndex = preference.findIndexOfValue(preference.value).takeIf { it >= 0 },
                onSelect = { index ->
                    onDismiss()
                    val value = values[index].toString()
                    if (preference.callChangeListener(value)) preference.value = value
                },
                onDismissRequest = onDismiss,
            )
        }
        is NumberRangePreferenceCompat -> NumberEditor(preference, title, onDismiss)
        is VersatileTextPreference -> {
            val invalid = stringResource(CommonString.invalid_value)
            TextInputDialog(
                title = title,
                label = (preference.dialogHint ?: title).toString(),
                confirmText = TR.actionsSave(),
                initialText = preference.text.orEmpty(),
                capitalization = KeyboardCapitalization.None,
                singleLine = preference.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0,
                keyboardType =
                    if (preference.inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_URI) {
                        KeyboardType.Uri
                    } else {
                        KeyboardType.Text
                    },
                errorMessageFor = { value ->
                    try {
                        preference.continuousValidator?.validate(value)
                        null
                    } catch (_: Exception) {
                        invalid
                    }
                },
                onConfirm = { value ->
                    onDismiss()
                    if (preference.callChangeListener(value)) {
                        preference.text = value
                        if (preference is VersatileTextWithASwitchPreference) preference.onDialogClosedAndNewTextSet()
                    }
                },
                onDismissRequest = onDismiss,
            )
        }
    }
}

@Composable
private fun NumberEditor(
    preference: NumberRangePreferenceCompat,
    title: String,
    onDismiss: () -> Unit,
) {
    val initial = preference.text
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val min = preference.min
    val max = preference.max
    val number = input.text.toIntOrNull()
    val validation = IncrementerNumberRangePreferenceCompat.validate(input.text, number, min, max)
    val error =
        when (validation) {
            ValidationResult.INVALID -> stringResource(CommonString.invalid_value)
            ValidationResult.OVERFLOW -> stringResource(CommonString.maximum_value_is, max)
            ValidationResult.UNDERFLOW -> stringResource(CommonString.minimum_value_is, min)
            else -> null
        }

    fun step(amount: Int) {
        val value = ((number ?: initial.toIntOrNull() ?: min).toLong() + amount).coerceIn(min.toLong(), max.toLong()).toString()
        input = TextFieldValue(value, TextRange(value.length))
    }
    PaperDialog(
        title = title,
        onDismissRequest = onDismiss,
        actions =
            listOf(
                DialogAction(stringResource(CommonString.dialog_cancel), onDismiss),
                DialogAction(
                    TR.actionsSave(),
                    {
                        onDismiss()
                        if (number != null && preference.callChangeListener(number)) preference.setValue(number)
                    },
                    DialogActionStyle.Filled,
                    enabled = validation == ValidationResult.VALID,
                ),
            ),
    ) {
        DialogField(
            label = title,
            value = input,
            onValueChange = { proposed ->
                if (proposed.text.length <= preference.maxDigits && proposed.text.all(Char::isDigit)) input = proposed
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (error != null) DialogDetail(error)
        if (preference is IncrementerNumberRangePreferenceCompat) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { step(-1) }, enabled = number != null && number > min) {
                    Icon(painterResource(CommonR.drawable.symbol_remove), stringResource(CommonString.minus_sign))
                }
                IconButton(onClick = { step(1) }, enabled = number != null && number < max) {
                    Icon(painterResource(CommonR.drawable.symbol_add), stringResource(CommonString.plus_sign))
                }
            }
        }
    }
}

@Composable
private fun DirectoryEditor(
    preference: ExternalDirectorySelectionPreference,
    title: String,
    onDismiss: () -> Unit,
    showDialog: (@Composable (() -> Unit) -> Unit) -> Unit,
) {
    var directories by remember { mutableStateOf(emptyList<String>()) }
    var custom by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(preference) {
        directories = withContext(Dispatchers.IO) { preference.loadDirectories() }
    }
    if (custom) {
        val context = preference.context
        val invalid = stringResource(CommonString.invalid_value)
        TextInputDialog(
            title = stringResource(CommonString.pref_enter_custom_path),
            label = stringResource(CommonString.pref_custom_path),
            confirmText = stringResource(CommonString.dialog_ok),
            initialText = preference.value.orEmpty(),
            capitalization = KeyboardCapitalization.None,
            errorMessageFor = { if (it.isBlank()) invalid else null },
            onConfirm = { input ->
                onDismiss()
                try {
                    val path = Paths.get(input.trim()).toAbsolutePath().normalize()
                    Files.createDirectories(path)
                    if (!Files.isWritable(path)) {
                        showDialog { dismiss ->
                            ErrorDialog(title, context.getString(CommonString.pref_directory_not_writable), onDismissRequest = dismiss)
                        }
                    } else if (preference.callChangeListener(path.toString())) {
                        preference.value = path.toString()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showDialog { dismiss ->
                        ErrorDialog(context.getString(CommonString.could_not_create_dir, input), "", e.stackTraceToString(), dismiss)
                    }
                }
            },
            onDismissRequest = onDismiss,
        )
    } else {
        PaperDialog(
            title = title,
            onDismissRequest = onDismiss,
            actions =
                listOf(
                    DialogAction(stringResource(CommonString.dialog_cancel), onDismiss),
                    DialogAction(stringResource(CommonString.pref_custom_path), { custom = true }),
                ),
        ) {
            Column(Modifier.selectableGroup()) {
                directories.forEach { path ->
                    DialogChoiceRow(
                        choice = DialogChoice(path),
                        checked = path == preference.value,
                        onClick = {
                            onDismiss()
                            if (preference.callChangeListener(path)) preference.value = path
                        },
                        multiple = false,
                    )
                }
            }
        }
    }
}
