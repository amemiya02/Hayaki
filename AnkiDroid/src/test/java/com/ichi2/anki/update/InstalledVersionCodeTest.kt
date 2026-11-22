// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.update

import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Every per-ABI APK shares one BuildConfig, whose VERSION_CODE is the first split's (armeabi-v7a).
 * The updater must read the installed APK's own versionCode, or an arm64 phone downloads the
 * armeabi-v7a APK and the install fails with INSTALL_FAILED_NO_MATCHING_ABIS.
 */
@RunWith(AndroidJUnit4::class)
class InstalledVersionCodeTest {
    @Test
    fun `the installed arm64 APK is updated with the arm64 asset`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val installed =
            PackageInfo().apply {
                packageName = context.packageName
                @Suppress("DEPRECATION")
                versionCode = 322_600_302
                longVersionCode = 322_600_302
            }
        shadowOf(context.packageManager).installPackage(installed)

        assertThat(ApkVariant.of(installedVersionCode(context)), equalTo(ApkVariant.ARM64_V8A))
    }
}
