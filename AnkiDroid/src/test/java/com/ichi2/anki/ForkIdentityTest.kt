// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ichi2.testutils.EmptyApplication
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Fork installs beside the Official App as a separate app with its own data,
 * so nothing it declares may share an identifier with the Official App.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class ForkIdentityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager: PackageManager = context.packageManager
    private val forkPackage = context.packageName

    @Test
    fun `the Fork has its own application ID in every build type`() {
        assertTrue(
            forkPackage == FORK_APPLICATION_ID || forkPackage == "$FORK_APPLICATION_ID.debug",
            "unexpected application ID: $forkPackage",
        )
        assertFalse(forkPackage in OFFICIAL_APPLICATION_IDS)
    }

    @Test
    fun `the launcher shows Hayaki, and the working name only in a debug build`() {
        val expected = if (BuildConfig.DEBUG) "Hayaki Debug" else "Hayaki"
        assertEquals(expected, context.applicationInfo.loadLabel(packageManager).toString())
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun `the launcher icon is adaptive, with a themed form`() {
        for (icon in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val adaptive = assertIs<AdaptiveIconDrawable>(AppCompatResources.getDrawable(context, icon))
            assertNotNull(adaptive.monochrome, "no themed icon")
        }
        assertEquals(R.mipmap.ic_launcher, context.applicationInfo.icon)
    }

    @Test
    fun `the public API authority and permission belong to the Fork`() {
        assertEquals("$forkPackage.flashcards", FlashCardsContract.AUTHORITY)
        assertEquals("$forkPackage.permission.READ_WRITE_DATABASE", FlashCardsContract.READ_WRITE_PERMISSION)

        assertNotNull(packageManager.resolveContentProvider(FlashCardsContract.AUTHORITY, 0))
        for (official in OFFICIAL_APPLICATION_IDS) {
            assertNull(packageManager.resolveContentProvider("$official.flashcards", 0))
        }
    }

    @Test
    fun `the Fork does not ask for the Official App's API permission`() {
        val requested =
            packageManager.getPackageInfo(forkPackage, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        for (official in OFFICIAL_APPLICATION_IDS) {
            assertFalse("$official.permission.READ_WRITE_DATABASE" in requested, "requests $official's permission")
        }
    }

    @Test
    fun `shared text is offered to the Fork`() {
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "hola")
        assertResolvesOnlyToFork(share)
    }

    @Test
    fun `a Deck package opened from another app is offered to the Fork`() {
        val open =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType("content://com.example.files/Download/Spanish.apkg".toUri(), "application/apkg")
        assertResolvesOnlyToFork(open)
    }

    @Test
    fun `a collection package opened from a file manager is offered to the Fork`() {
        val open =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType("content://com.example.files/Download/collection.colpkg".toUri(), "application/octet-stream")
        assertResolvesOnlyToFork(open)
    }

    @Test
    fun `the home-screen widgets belong to the Fork`() {
        val widgets = packageManager.queryBroadcastReceivers(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE), 0)
        assertTrue(widgets.isNotEmpty(), "no widgets declared")
        assertTrue(widgets.all { it.activityInfo.packageName == forkPackage }, "widgets: $widgets")
    }

    private fun assertResolvesOnlyToFork(intent: Intent) {
        val handlers = packageManager.queryIntentActivities(intent, 0)
        assertTrue(handlers.isNotEmpty(), "nothing handles $intent")
        assertTrue(handlers.all { it.activityInfo.packageName == forkPackage }, "handlers: $handlers")
    }

    companion object {
        const val FORK_APPLICATION_ID = "io.github.amemiya02.hayaki"
        val OFFICIAL_APPLICATION_IDS = listOf("com.ichi2.anki", "com.ichi2.anki.debug")
    }
}
