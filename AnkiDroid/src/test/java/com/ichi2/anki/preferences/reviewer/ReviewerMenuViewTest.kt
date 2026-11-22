// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.reviewer

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.children
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import androidx.appcompat.R as AppCompatR
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
class ReviewerMenuViewTest : RobolectricTest() {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun `an outside tap removes the overflow popup before it can be repositioned`() {
        addBasicNote()
        ActivityScenario.launch<CardViewerActivity>(ReviewerFragment.getIntent(targetContext)).use {
            compose
                .onNodeWithContentDescription(targetContext.getString(AppCompatR.string.abc_action_menu_overflow_description))
                .performClick()
            val menuItem = compose.onNodeWithText(ViewerAction.DECK_OPTIONS.title(targetContext)).assertIsDisplayed()
            val popup =
                compose.runOnIdle {
                    WindowInspector.getGlobalWindowViews().single { view ->
                        (view.layoutParams as? WindowManager.LayoutParams)?.type == WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
                    }
                }

            compose.mainClock.autoAdvance = false
            try {
                compose.runOnIdle {
                    val now = SystemClock.uptimeMillis()
                    val outside = MotionEvent.obtain(now, now, MotionEvent.ACTION_OUTSIDE, -1f, -1f, 0)
                    try {
                        assertTrue(popup.dispatchTouchEvent(outside), "The popup did not handle the outside tap")
                    } finally {
                        outside.recycle()
                    }
                    Snapshot.sendApplyNotifications()
                }
                // Apply the dismiss state and start any exit transition without advancing that transition.
                repeat(2) { compose.mainClock.advanceTimeByFrame() }
                menuItem.assertDoesNotExist()
                compose.runOnIdle {
                    assertFalse(
                        popup in WindowInspector.getGlobalWindowViews(),
                        "An empty overflow popup remains attached during dismissal",
                    )
                }
            } finally {
                compose.mainClock.autoAdvance = true
            }
        }
    }

    @Test
    fun `the undo button is drawn in the text colour, not a disabled grey`() {
        addBasicNote()
        ActivityScenario.launch<CardViewerActivity>(ReviewerFragment.getIntent(targetContext)).use { scenario ->
            scenario.onActivity { activity ->
                val menu =
                    activity.window.decorView
                        .descendants()
                        .filterIsInstance<ReviewerMenuView>()
                        .single()
                val undo = assertNotNull(menu.findItem(ViewerAction.UNDO.menuId)?.icon, "The study screen has no undo icon")
                val textColour = MaterialColors.getColor(menu, CommonR.attr.foundationOnBackground)

                assertEquals(Integer.toHexString(textColour or 0xFF000000.toInt()), Integer.toHexString(undo.toBitmap().opaqueColour()))
            }
        }
    }

    private fun View.descendants(): Sequence<View> =
        sequenceOf(this) + ((this as? ViewGroup)?.children?.flatMap { it.descendants() } ?: emptySequence())

    /** The colour, without alpha, of the icon's most opaque pixel: every painted pixel shares its tint */
    private fun Bitmap.opaqueColour(): Int {
        val pixels = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
        val pixel = pixels.maxBy(Color::alpha)
        check(Color.alpha(pixel) > 0) { "The icon is empty" }
        return pixel or 0xFF000000.toInt()
    }
}
