// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.ichi2.anki.CheckAccessibility
import com.ichi2.anki.ComposeScreenshotTest
import com.ichi2.anki.FontSizeConfig.PERCENT_200
import com.ichi2.anki.IncludeFontSizes
import com.ichi2.anki.IncludeLanguages
import com.ichi2.anki.LanguageConfig.ARABIC
import com.ichi2.anki.LanguageConfig.JAPANESE
import com.ichi2.anki.LanguageConfig.SIMPLIFIED_CHINESE
import org.junit.Test

@CheckAccessibility
@IncludeLanguages(SIMPLIFIED_CHINESE, JAPANESE, ARABIC)
@IncludeFontSizes(PERCENT_200)
class AboutScreenshotTest : ComposeScreenshotTest() {
    @Test
    fun aboutAndLicences() {
        capture(
            "about",
            beforeCapture = { composeRule.onNodeWithText("Hayaki").assertIsDisplayed() },
        ) {
            AboutScreen(
                sampleAbout,
                onLink = {},
                onLogo = {},
                onBack = {},
                searchTarget = null,
                onSupportUpstream = {},
                onChangelog = {},
                onCopyDebugInfo = {},
                onCheckForUpdates = {},
                onUpdateCheckEnabledChange = {},
            )
        }
    }

    @Test
    fun aboutActionsAndSource() {
        capture(
            "about_actions",
            beforeCapture = { composeRule.onNodeWithText("AGPL-3.0").performScrollTo().assertIsDisplayed() },
        ) {
            AboutScreen(
                sampleAbout,
                onLink = {},
                onLogo = {},
                onBack = {},
                searchTarget = null,
                onSupportUpstream = {},
                onChangelog = {},
                onCopyDebugInfo = {},
                onCheckForUpdates = {},
                onUpdateCheckEnabledChange = {},
            )
        }
    }

    private val sampleAbout =
        AboutScreenState(
            "2.26.0",
            "18 May 2026",
            "(anki 25.09 / 00000000)",
            "(FSRS 0.6.4)",
            "Copy debug info",
            true,
        )
}
