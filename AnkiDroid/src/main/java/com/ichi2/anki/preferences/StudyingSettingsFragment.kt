// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import anki.config.Preferences
import anki.zz_hayaki.RwkvStatusResponse
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.analytics.AnalyticsEvent
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.preferences.components.PreferenceStepperCard
import com.ichi2.anki.preferences.reviewer.ReviewerMenuSettingsFragment
import com.ichi2.anki.scheduling.RwkvScheduling
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.compose.ui.dialogs.DialogAction
import com.ichi2.compose.ui.dialogs.DialogActionStyle
import com.ichi2.compose.ui.dialogs.DialogChoice
import com.ichi2.compose.ui.dialogs.PaperDialog
import com.ichi2.compose.ui.dialogs.SingleChoiceDialog
import com.ichi2.utils.FragmentFactoryUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class StudyingSettingsFragment : Fragment() {
    private var collectionSettings by mutableStateOf<Preferences?>(null)
    private var rwkvStatus by mutableStateOf<RwkvStatusResponse?>(null)
    private var rwkvPolling: Job? = null
    private val collectionWrites = Mutex()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Analytics.sendAnalyticsScreenView("prefs.studyScreen")
        val parent = parentFragment as? PreferencesFragment
        return settingsComposeView(R.id.settings_studying) {
            StudyingSettings(
                preferences = sharedPrefs(),
                collection = collectionSettings,
                searchTarget = settingsSearchTarget,
                onBack = settingsBackAction,
                onOpenTimebox = { parent?.openSubpage(TimeboxSettingsFragment()) },
                onOpenToolbar = { parent?.openSubpage(ReviewerMenuSettingsFragment()) },
                onRelated = { parent?.openSubpage(FragmentFactoryUtils.instantiate(requireActivity(), it.destination.java.name)) },
                onChangeRollover = { hours -> editCollection { setDayOffset(requireContext(), hours) } },
                onChangeLearnAhead = { minutes ->
                    editCollection { CollectionPreferences.setLearnAheadLimit(minutes.toDuration(DurationUnit.MINUTES)) }
                },
                rwkvStatus = rwkvStatus,
                onChangeRwkv = { enabled -> changeRwkv { RwkvScheduling.setEnabled(requireContext(), enabled) } },
                onRetryRwkv = { changeRwkv { RwkvScheduling.prepare(requireContext()) } },
                onChangeCollectionSwitch = { setting, checked ->
                    editCollection {
                        when (setting) {
                            StudySetting.REMAINING -> CollectionPreferences.setShowRemainingDueCounts(checked)
                            StudySetting.AUDIO -> CollectionPreferences.setHideAudioPlayButtons(!checked)
                            StudySetting.ESTIMATES -> CollectionPreferences.setShowIntervalsOnButtons(checked)
                            else -> error("Not a collection setting: ${setting.name}")
                        }
                    }
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        editCollection {}
        pollRwkv()
    }

    override fun onPause() {
        rwkvPolling?.cancel()
        super.onPause()
    }

    /** Refreshes the RWKV status, every second while the Engine prepares. */
    private fun pollRwkv() {
        rwkvPolling?.cancel()
        rwkvPolling =
            launchCatchingTask {
                while (isActive) {
                    val status = RwkvScheduling.status()
                    rwkvStatus = status
                    val preparing =
                        status.enabled &&
                            (status.state == RwkvStatusResponse.State.PREPARING || status.state == RwkvStatusResponse.State.NOT_PREPARED)
                    delay(if (preparing) 1.seconds else 5.seconds)
                }
            }
    }

    private fun changeRwkv(change: suspend () -> Unit) {
        launchCatchingTask {
            try {
                change()
            } catch (e: Exception) {
                Timber.w(e, "RWKV scheduling")
                showThemedToast(requireContext(), R.string.rwkv_scheduling_save_error, shortLength = true)
            }
            rwkvStatus = RwkvScheduling.status()
        }
    }

    private fun editCollection(edit: suspend () -> Unit) {
        launchCatchingTask {
            collectionWrites.withLock {
                edit()
                collectionSettings = withCol { getPreferences() }
            }
        }
    }
}

internal enum class StudySection(
    @StringRes val title: Int,
) {
    SCHEDULING(CommonString.pref_cat_scheduling),
    SCREEN(R.string.settings_study_screen),
    ANSWERS(CommonString.answer_buttons),
    TOOLBAR(CommonString.toolbar),
}

internal enum class StudySetting(
    val section: StudySection,
    @StringRes val key: Int,
    @StringRes val title: Int = 0,
    @StringRes val summary: Int = 0,
    @ArrayRes val choices: Int = 0,
    @ArrayRes val values: Int = 0,
    val defaultChecked: Boolean = false,
) {
    ROLLOVER(StudySection.SCHEDULING, R.string.day_offset_preference, CommonString.day_offset_with_description),
    LEARN_AHEAD(StudySection.SCHEDULING, R.string.learn_cutoff_preference, CommonString.learn_cutoff),
    TIMEBOX(StudySection.SCHEDULING, R.string.time_limit_preference, CommonString.time_limit),
    KEEP_ON(
        StudySection.SCREEN,
        R.string.keep_screen_on_preference,
        CommonString.pref_keep_screen_on,
        CommonString.pref_keep_screen_on_summ,
    ),
    CENTER(
        StudySection.SCREEN,
        R.string.center_vertically_preference,
        CommonString.vertical_centering,
        CommonString.vertical_centering_summ,
    ),
    FRAME(
        StudySection.SCREEN,
        R.string.reviewer_frame_style_key,
        CommonString.reviewer_frame_style,
        choices = R.array.reviewer_frame_style_entries,
        values = R.array.reviewer_frame_style_values,
    ),
    HIDE_BARS(
        StudySection.SCREEN,
        R.string.hide_system_bars_key,
        CommonString.hide_system_bars,
        choices = R.array.hide_system_bars_entries,
        values = R.array.hide_system_bars_values,
    ),
    CUTOUT(StudySection.SCREEN, R.string.ignore_display_cutout_key, CommonString.ignore_display_cutout),
    FEEDBACK(StudySection.SCREEN, R.string.show_answer_feedback_key, CommonString.show_answer_feedback, defaultChecked = true),
    REMAINING(StudySection.SCREEN, R.string.show_progress_preference),
    AUDIO(StudySection.SCREEN, R.string.show_audio_play_buttons_key),
    ANSWERS(
        StudySection.ANSWERS,
        R.string.show_answer_buttons_key,
        CommonString.show_answer_buttons,
        defaultChecked = true,
    ),
    ESTIMATES(StudySection.ANSWERS, R.string.show_estimates_preference),
    HIDE_HARD_EASY(StudySection.ANSWERS, R.string.hide_hard_and_easy_key, CommonString.hide_hard_and_easy),
    TOOLBAR_ACTIONS(StudySection.TOOLBAR, R.string.reviewer_menu_settings_key, CommonString.toolbar_actions),
    TOOLBAR_POSITION(
        StudySection.TOOLBAR,
        R.string.reviewer_toolbar_position_key,
        CommonString.toolbar_position,
        choices = R.array.reviewer_toolbar_position_entries,
        values = R.array.reviewer_toolbar_position_values,
    ),
    ;

    fun label(resources: Resources): String =
        when (this) {
            REMAINING -> TR.preferencesShowRemainingCardCount()
            AUDIO -> TR.preferencesShowPlayButtonsOnCardsWith()
            ESTIMATES -> TR.preferencesShowNextReviewTimeAboveAnswer()
            else -> resources.getString(title)
        }
}

internal fun Context.studyingSearchEntries(path: String): List<SettingsSearchEntry> =
    StudySetting.entries.map { setting ->
        val subpage =
            when (setting) {
                StudySetting.TIMEBOX -> TimeboxSettingsFragment::class
                StudySetting.TOOLBAR_ACTIONS -> ReviewerMenuSettingsFragment::class
                else -> null
            }
        SettingsSearchEntry(
            title = setting.label(resources),
            path = "$path › ${getString(setting.section.title)}",
            destination = subpage ?: StudyingSettingsFragment::class,
            key = if (subpage == null) getString(setting.key) else null,
            details =
                listOfNotNull(setting.summary.takeIf { it != 0 }?.let(::getString)) +
                    (
                        setting.choices
                            .takeIf { it != 0 }
                            ?.let(resources::getStringArray)
                            ?.toList()
                            .orEmpty()
                    ),
        )
    }

@Composable
private fun StudyingSettings(
    preferences: SharedPreferences,
    collection: Preferences?,
    searchTarget: String?,
    onBack: (() -> Unit)?,
    onOpenTimebox: () -> Unit,
    onOpenToolbar: () -> Unit,
    onRelated: (SettingsGroup) -> Unit,
    onChangeRollover: (Int) -> Unit,
    onChangeLearnAhead: (Int) -> Unit,
    rwkvStatus: RwkvStatusResponse?,
    onChangeRwkv: (Boolean) -> Unit,
    onRetryRwkv: () -> Unit,
    onChangeCollectionSwitch: (StudySetting, Boolean) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val resources = LocalResources.current
    var preferencesVersion by remember { mutableIntStateOf(0) }
    var dialog by rememberSaveable { mutableStateOf<StudySetting?>(null) }
    val deviceValues =
        remember(preferences, preferencesVersion, configuration, resources) {
            StudySetting.entries.filter { it !in collectionSwitches && it !in collectionOrNavigationRows }.associateWith { setting ->
                val key = resources.getString(setting.key)
                if (setting.values != 0) {
                    val values = resources.getStringArray(setting.values)
                    values.indexOf(preferences.getString(key, values.first())).coerceAtLeast(0)
                } else {
                    if (preferences.getBoolean(key, setting.defaultChecked)) 1 else 0
                }
            }
        }

    fun isChecked(setting: StudySetting) = (deviceValues[setting] ?: 0) != 0

    SettingsScrollPage(stringResource(R.string.settings_studying), onBack) {
        for (section in StudySection.entries) {
            SettingsSectionTitle(stringResource(section.title))
            SettingsDivider()
            for (setting in StudySetting.entries.filter { it.section == section }) {
                val key = stringResource(setting.key)
                val title = setting.label(resources)
                val openDialog = {
                    Analytics.send(AnalyticsEvent.SettingTapped(key))
                    dialog = setting
                }
                SettingsSearchTarget(searchTarget == key) { modifier, highlighted ->
                    when (setting) {
                        StudySetting.ROLLOVER, StudySetting.LEARN_AHEAD, StudySetting.TIMEBOX -> {
                            val value =
                                when (setting) {
                                    StudySetting.ROLLOVER ->
                                        collection?.scheduling?.rollover?.let {
                                            pluralStringResource(CommonPlurals.day_offset_summ, it, it)
                                        }
                                    StudySetting.LEARN_AHEAD ->
                                        collection?.scheduling?.learnAheadSecs?.div(60)?.let {
                                            pluralStringResource(CommonPlurals.pref_summ_minutes, it, it)
                                        }
                                    else ->
                                        collection?.reviewing?.timeLimitSecs?.div(60)?.let {
                                            if (it <= 0) {
                                                stringResource(CommonString.disabled)
                                            } else {
                                                pluralStringResource(CommonPlurals.pref_summ_minutes, it, it)
                                            }
                                        }
                                }
                            SettingsRow(
                                title = title,
                                value = value,
                                onClick =
                                    if (setting == StudySetting.TIMEBOX) {
                                        {
                                            Analytics.send(AnalyticsEvent.SettingTapped(key))
                                            onOpenTimebox()
                                        }
                                    } else {
                                        openDialog
                                    },
                                enabled = setting == StudySetting.TIMEBOX || collection != null,
                                highlighted = highlighted,
                                modifier = modifier,
                                trailing = if (setting == StudySetting.TIMEBOX) ({ SettingsChevron() }) else null,
                            )
                        }
                        StudySetting.TOOLBAR_ACTIONS ->
                            SettingsRow(
                                title,
                                onClick = {
                                    Analytics.send(AnalyticsEvent.SettingTapped(key))
                                    onOpenToolbar()
                                },
                                modifier = modifier,
                                trailing = { SettingsChevron() },
                            )
                        StudySetting.FRAME, StudySetting.HIDE_BARS, StudySetting.TOOLBAR_POSITION ->
                            SettingsRow(
                                title,
                                value = resources.getStringArray(setting.choices)[deviceValues.getValue(setting)],
                                onClick = openDialog,
                                highlighted = highlighted,
                                modifier = modifier,
                            )
                        else -> {
                            val collectionValue =
                                when (setting) {
                                    StudySetting.REMAINING -> collection?.reviewing?.showRemainingDueCounts
                                    StudySetting.AUDIO -> collection?.reviewing?.hideAudioPlayButtons?.not()
                                    StudySetting.ESTIMATES -> collection?.reviewing?.showIntervalsOnButtons
                                    else -> null
                                }
                            val isCollectionSetting = setting in collectionSwitches
                            val enabled =
                                when (setting) {
                                    StudySetting.CUTOUT -> deviceValues.getValue(StudySetting.HIDE_BARS) != 0
                                    StudySetting.ESTIMATES -> isChecked(StudySetting.ANSWERS) && collectionValue != null
                                    StudySetting.HIDE_HARD_EASY -> isChecked(StudySetting.ANSWERS)
                                    else -> !isCollectionSetting || collectionValue != null
                                }
                            SettingsSwitchRow(
                                title = title,
                                summary = setting.summary.takeIf { it != 0 }?.let { stringResource(it) },
                                checked = collectionValue ?: isChecked(setting),
                                onChange = {
                                    Analytics.send(AnalyticsEvent.SettingTapped(key))
                                    if (isCollectionSetting) {
                                        onChangeCollectionSwitch(setting, it)
                                    } else {
                                        preferences.edit { putBoolean(key, it) }
                                        preferencesVersion++
                                    }
                                },
                                enabled = enabled,
                                highlighted = highlighted,
                                modifier = modifier,
                            )
                        }
                    }
                }
            }
            if (section == StudySection.SCHEDULING) {
                RwkvSchedulingRows(rwkvStatus, onChangeRwkv, onRetryRwkv)
            }
        }
        SettingsSectionTitle(stringResource(CommonString.related_settings_title))
        SettingsDivider()
        for (group in listOf(SettingsGroup.CONTROLS, SettingsGroup.ACCESSIBILITY)) {
            SettingsRow(stringResource(group.title), onClick = { onRelated(group) }, trailing = { SettingsChevron() })
        }
    }

    val dismiss = { dialog = null }
    when (val selected = dialog) {
        null -> Unit
        StudySetting.ROLLOVER ->
            if (collection != null) {
                val hours = (0..23).map { DialogChoice(pluralStringResource(CommonPlurals.day_offset_summ, it, it)) }
                SingleChoiceDialog(
                    selected.label(resources),
                    hours,
                    selectedIndex = collection.scheduling.rollover,
                    onSelect = {
                        onChangeRollover(it)
                        dismiss()
                    },
                    onDismissRequest = dismiss,
                )
            }
        StudySetting.LEARN_AHEAD ->
            if (collection != null) {
                LearnAheadLimitDialog(collection.scheduling.learnAheadSecs / 60, onChangeLearnAhead, dismiss)
            }
        else -> {
            val key = stringResource(selected.key)
            val choices = resources.getStringArray(selected.choices)
            val values = resources.getStringArray(selected.values)
            SingleChoiceDialog(
                selected.label(resources),
                choices.map { DialogChoice(it) },
                selectedIndex = deviceValues.getValue(selected),
                onSelect = { index ->
                    preferences.edit { putString(key, values[index]) }
                    preferencesVersion++
                    dismiss()
                },
                onDismissRequest = dismiss,
            )
        }
    }
}

private val collectionOrNavigationRows =
    setOf(StudySetting.ROLLOVER, StudySetting.LEARN_AHEAD, StudySetting.TIMEBOX, StudySetting.TOOLBAR_ACTIONS)
private val collectionSwitches = setOf(StudySetting.REMAINING, StudySetting.AUDIO, StudySetting.ESTIMATES)

@Composable
private fun LearnAheadLimitDialog(
    minutes: Int,
    onSave: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var value by rememberSaveable { mutableIntStateOf(minutes.coerceIn(0..999)) }
    PaperDialog(
        title = stringResource(CommonString.learn_cutoff),
        onDismissRequest = onDismissRequest,
        actions =
            listOf(
                DialogAction(stringResource(CommonString.dialog_cancel), onDismissRequest),
                DialogAction(
                    stringResource(CommonString.dialog_ok),
                    {
                        onSave(value)
                        onDismissRequest()
                    },
                    DialogActionStyle.Filled,
                ),
            ),
    ) {
        PreferenceStepperCard(
            title = pluralStringResource(CommonPlurals.timebox_minutes_suffix, value),
            value = value,
            onValueChange = { value = it },
            validRange = 0..999,
            unitSuffix = { "" },
        )
    }
}

/** The RWKV scheduling switch and its status (ADR-0009). */
@Composable
private fun RwkvSchedulingRows(
    status: RwkvStatusResponse?,
    onChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    SettingsSwitchRow(
        title = stringResource(R.string.rwkv_scheduling_title),
        summary = stringResource(R.string.rwkv_scheduling_summary),
        checked = status?.enabled == true,
        onChange = onChange,
        enabled = status != null,
    )
    if (status?.enabled != true) return
    val failed = status.state == RwkvStatusResponse.State.FAILED
    val text =
        when (status.state) {
            RwkvStatusResponse.State.READY -> stringResource(R.string.rwkv_status_ready)
            RwkvStatusResponse.State.PREPARING ->
                if (status.total > 0) {
                    stringResource(R.string.rwkv_status_preparing, status.done, status.total)
                } else {
                    stringResource(R.string.rwkv_status_downloading)
                }
            RwkvStatusResponse.State.FAILED -> stringResource(R.string.rwkv_status_failed, status.error)
            else -> stringResource(R.string.rwkv_status_waiting)
        }
    SettingsRow(title = text, onClick = if (failed) onRetry else null)
}
