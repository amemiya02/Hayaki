// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.content.pm.PackageInfo
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.utils.VersionUtils
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Hayaki updates in place, so opening after an upgrade goes straight to the deck list. */
@RunWith(AndroidJUnit4::class)
class DeckPickerUpgradeTest : RobolectricTest() {
    @Before
    fun upgradedFromAnEarlierVersion() {
        // A release build: the hundreds digit of the versionCode is 3
        val release =
            PackageInfo().apply {
                packageName = targetContext.packageName
                versionName = "0.2.4"
                @Suppress("DEPRECATION")
                versionCode = 22_600_304
            }
        shadowOf(targetContext.packageManager).installPackage(release)
        assertTrue(VersionUtils.isReleaseVersion)
        setIntroductionSlidesShown(true)
        addBasicNote()
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        InitialActivity.setUpgradedToLatestVersion(targetContext.sharedPrefs())
        targetContext.sharedPrefs().edit {
            putString("lastVersion", "0.2.3")
            putLong(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION, VersionUtils.pkgVersionCode)
            putBoolean("backupPromptDisabled", true)
        }
    }

    @After
    fun resetBackupSpace() = BackupManagerTestUtilities.reset()

    @Test
    fun `after an upgrade the deck list opens no changelog and records the new version`() {
        startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        advanceRobolectricLooper()

        // The upgrade recreates the deck list: look at the one now on screen
        val deckList =
            ActivityLifecycleMonitorRegistry
                .getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<DeckPicker>()
                .single()
        val screens = deckList.supportFragmentManager.fragments.map { it.javaClass.simpleName }
        assertFalse("InfoFragment" in screens, "AnkiDroid's changelog opened over the deck list: $screens")
        assertTrue(InitialActivity.isLatestVersion(targetContext.sharedPrefs()))
    }
}
