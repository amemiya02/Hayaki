// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.navigation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.get
import androidx.navigation.navOptions
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.BrowserAppearanceFragment
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CardTemplateBrowserAppearanceEditor
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.CardTemplateEditorFragment
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.DrawingFragment
import com.ichi2.anki.MoreFragment
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorPaneFragment
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.StudyOptionsActivity
import com.ichi2.anki.StudyOptionsFlowFragment
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.account.AccountFlowFragment
import com.ichi2.anki.browser.CardBrowserFragment
import com.ichi2.anki.browser.CardBrowserPaneFragment
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment
import com.ichi2.anki.instantnoteeditor.InstantNoteEditorFragment
import com.ichi2.anki.introduction.WelcomeFragment
import com.ichi2.anki.mediacheck.MediaCheckFragment
import com.ichi2.anki.multimedia.MultimediaActivity
import com.ichi2.anki.multimedia.MultimediaFlowFragment
import com.ichi2.anki.notetype.ManageNoteTypesFragment
import com.ichi2.anki.notetype.ManageNotetypes
import com.ichi2.anki.pages.AnkiPackageImporterFragment
import com.ichi2.anki.pages.CardInfoFragment
import com.ichi2.anki.pages.ChangeNoteTypePage
import com.ichi2.anki.pages.CsvImporter
import com.ichi2.anki.pages.DeckOptions
import com.ichi2.anki.pages.ImageOcclusion
import com.ichi2.anki.pages.Statistics
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.preferences.PreferencesFragment
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.PreviewerFragment
import com.ichi2.anki.previewer.TemplatePreviewerFragment
import com.ichi2.anki.reviewreminders.ReminderTroubleshootingFragment
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.shareddeck.SharedDecksActivity
import com.ichi2.anki.shareddeck.SharedDecksFragment
import com.ichi2.anki.study.FinishedFragment
import com.ichi2.anki.ui.windows.managespace.ManageSpaceActivity
import com.ichi2.anki.ui.windows.managespace.ManageSpaceFragment
import com.ichi2.anki.ui.windows.permissions.AllPermissionsExplanationActivity
import com.ichi2.anki.ui.windows.permissions.AllPermissionsExplanationFragment
import com.ichi2.anki.ui.windows.permissions.CollectionPermissionsFragment
import com.ichi2.anki.ui.windows.permissions.PermissionsActivity
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import com.ichi2.anki.updateTabChrome
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.imagecropper.ImageCropper

/** Owns the in-app back stack and keeps platform entry intents at the Android boundary. */
internal class AppNavigation(
    private val deckPicker: DeckPicker,
    private val home: View,
    private val showWelcome: Boolean,
) {
    private var controller: NavHostController? = null
    private val pending = ArrayDeque<(NavHostController) -> Unit>()
    private val destinations = FragmentDestinations(deckPicker.supportFragmentManager)
    private val completedResults = mutableMapOf<String, ActivityResult>()
    private val forwardedResults = mutableSetOf<String>()

    val currentFragment: Fragment?
        get() = destinations.find(controller?.currentBackStackEntry)

    val showsStandaloneScreen: Boolean
        get() = controller?.currentDestination?.route?.let { it !in tabRouteNames } == true

    @Composable
    fun Content() {
        val navigation = rememberNavController()
        NavHost(
            navController = navigation,
            startDestination = if (showWelcome) WELCOME else HOME,
            modifier = Modifier.fillMaxSize().nestedScroll(rememberNestedScrollInteropConnection()),
            // Fragment-backed screens own their view lifecycle and system insets.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(HOME) {
                AndroidView(
                    factory = {
                        (home.parent as? ViewGroup)?.removeView(home)
                        home.isNestedScrollingEnabled = true
                        home
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            composable(WELCOME) { entry ->
                HostedScreen(entry, WelcomeFragment::class.java)
            }
            tabFragments.forEach { (tab, fragmentClass) ->
                composable(tab.tag) { entry ->
                    val bottomClearance = with(LocalDensity.current) { deckPicker.bottomNavContentClearance.intValue.toDp() }
                    val arguments =
                        if (tab ==
                            NavigationItem.STATS
                        ) {
                            Bundle().apply { putBoolean(Statistics.ARG_HIDE_BACK_BUTTON, true) }
                        } else {
                            null
                        }
                    HostedScreen(entry, fragmentClass, Modifier.padding(bottom = bottomClearance), arguments)
                }
            }
            fragmentScreens.values.forEach { fragmentClass ->
                composable(fragmentClass.name) { entry ->
                    HostedScreen(entry, fragmentClass)
                }
            }
        }
        val route =
            navigation
                .currentBackStackEntryAsState()
                .value
                ?.destination
                ?.route
        LaunchedEffect(route) {
            if (route in tabRouteNames && !deckPicker.bottomNavigationEnabled && route != HOME) {
                navigation.popBackStack(HOME, inclusive = false)
            } else {
                deckPicker.updateTabChrome(NavigationItem.entries.find { it.tag == route })
            }
        }
        LaunchedEffect(navigation) {
            var previousEntries = emptyList<NavBackStackEntry>()
            navigation.currentBackStack.collect { entries ->
                val popped = previousEntries.filterNot { entry -> entries.any { it.id == entry.id } }
                previousEntries = entries
                popped.forEach(::onEntryPopped)
            }
        }
        SideEffect {
            controller = navigation
            while (pending.isNotEmpty()) pending.removeFirst()(navigation)
        }
    }

    /**
     * Preserve a covered Fragment's ViewModel and registered Activity Result keys.
     * Its view and back callbacks are active only while its destination is presented.
     */
    @Composable
    private fun HostedScreen(
        entry: NavBackStackEntry,
        fragmentClass: Class<out Fragment>,
        modifier: Modifier = Modifier,
        screenArguments: Bundle? = entry.arguments?.getBundle(SCREEN_ARGUMENTS),
    ) {
        destinations.Content(entry, fragmentClass.name, screenArguments, modifier)
    }

    private fun onEntryPopped(entry: NavBackStackEntry) {
        destinations.remove(entry)
        if (forwardedResults.remove(entry.id)) return
        if (entry.arguments?.getBoolean(RETURN_TO_SHARING_APP) == true) {
            deckPicker.moveTaskToBack(true)
        }
        val defaultResult = entry.arguments?.getInt(DEFAULT_RESULT, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        // System Back can pop a restored Finished entry directly through the NavHost.
        val result =
            completedResults.remove(entry.id)
                ?: if (entry.destination.route == FinishedFragment::class.java.name) {
                    FinishedFragment.studyAllDismissalResult(entry.arguments?.getBundle(SCREEN_ARGUMENTS))
                } else {
                    null
                }
                ?: ActivityResult(defaultResult, null)
        val requestCode = entry.arguments?.getInt(RESULT_REQUEST_CODE, NO_RESULT) ?: NO_RESULT
        if (requestCode != NO_RESULT) deckPicker.activityResultRegistry.dispatchResult(requestCode, result.resultCode, result.data)
    }

    fun selectTab(tab: NavigationItem) {
        navigateWhenReady { navigation ->
            if (navigation.currentDestination?.route == tab.tag) return@navigateWhenReady
            (currentFragment as? CardBrowserFragment)?.closeSearch()
            if (tab == NavigationItem.HOME) {
                navigation.popBackStack(HOME, inclusive = false)
            } else {
                navigation.navigate(tab.tag) {
                    popUpTo(HOME)
                    launchSingleTop = true
                }
            }
        }
    }

    fun completeWelcome() {
        navigateWhenReady { navigation ->
            navigation.navigate(HOME) {
                popUpTo(WELCOME) { inclusive = true }
            }
        }
    }

    fun launch(
        intent: Intent,
        requestCode: Int = NO_RESULT,
    ): Boolean {
        val target = intent.component ?: return false
        if (target.packageName != deckPicker.packageName) return false
        val component = target.className
        val fragment =
            activityScreens[component] ?: if (component in fragmentHosts) {
                intent.getStringExtra(SingleFragmentActivity.EXTRA_FRAGMENT_NAME)?.let(fragmentScreens::get) ?: return false
            } else {
                return false
            }
        val screenArguments =
            when (component) {
                CardBrowser::class.java.name -> CardBrowserPaneFragment.arguments(intent)
                NoteEditorActivity::class.java.name -> NoteEditorPaneFragment.arguments(intent)
                CardTemplateBrowserAppearanceEditor::class.java.name,
                CardTemplateEditor::class.java.name,
                MultimediaActivity::class.java.name,
                PermissionsActivity::class.java.name,
                -> intent.extras
                else -> intent.getBundleExtra(SingleFragmentActivity.EXTRA_FRAGMENT_ARGS)
            }
        val defaultResult = if (component == CardBrowser::class.java.name) Activity.RESULT_OK else Activity.RESULT_CANCELED
        navigateWhenReady { navigation ->
            val forward = intent.flags and Intent.FLAG_ACTIVITY_FORWARD_RESULT != 0
            val caller = navigation.currentBackStackEntry?.takeIf { forward && navigation.previousBackStackEntry != null }
            val outgoingRequest = caller?.arguments?.getInt(RESULT_REQUEST_CODE, NO_RESULT) ?: requestCode
            val options =
                caller?.let {
                    forwardedResults.add(it.id)
                    navOptions { popUpTo(it.destination.id) { inclusive = true } }
                }
            val arguments =
                Bundle().apply {
                    putBundle(SCREEN_ARGUMENTS, screenArguments)
                    putInt(RESULT_REQUEST_CODE, outgoingRequest)
                    putInt(DEFAULT_RESULT, defaultResult)
                    putBoolean(
                        RETURN_TO_SHARING_APP,
                        fragment == InstantNoteEditorFragment::class.java || caller?.arguments?.getBoolean(RETURN_TO_SHARING_APP) == true,
                    )
                }
            navigation.navigate(navigation.graph[fragment.name].id, arguments, options)
        }
        return true
    }

    fun finish(
        source: Fragment,
        result: ActivityResult,
    ) {
        val navigation = controller ?: return
        if (!isCurrentScreen(source)) return
        val entry = navigation.currentBackStackEntry ?: return
        completedResults[entry.id] = result
        navigation.popBackStack()
    }

    fun hasResultCaller(fragment: Fragment): Boolean =
        isCurrentScreen(fragment) &&
            (controller?.currentBackStackEntry?.arguments?.getInt(RESULT_REQUEST_CODE, NO_RESULT) ?: NO_RESULT) != NO_RESULT

    private fun isCurrentScreen(fragment: Fragment): Boolean {
        val root = currentFragment ?: return false
        return generateSequence(fragment) { it.parentFragment }.any { it === root }
    }

    private fun navigateWhenReady(block: (NavHostController) -> Unit) {
        val navigation = controller
        if (navigation == null) pending.add(block) else block(navigation)
    }

    private companion object {
        const val HOME = "home"
        const val WELCOME = "welcome"
        const val NO_RESULT = -1
        const val SCREEN_ARGUMENTS = "screenArguments"
        const val RESULT_REQUEST_CODE = "resultRequestCode"
        const val DEFAULT_RESULT = "defaultResultCode"
        const val RETURN_TO_SHARING_APP = "returnToSharingApp"

        val tabFragments =
            mapOf(
                NavigationItem.BROWSER to CardBrowserFragment::class.java,
                NavigationItem.STATS to Statistics::class.java,
                NavigationItem.MORE to MoreFragment::class.java,
            )
        val tabRouteNames = NavigationItem.entries.map { it.tag }

        val activityScreens =
            mapOf(
                CardBrowser::class.java.name to CardBrowserPaneFragment::class.java,
                CardTemplateBrowserAppearanceEditor::class.java.name to BrowserAppearanceFragment::class.java,
                CardTemplateEditor::class.java.name to CardTemplateEditorFragment::class.java,
                ManageNotetypes::class.java.name to ManageNoteTypesFragment::class.java,
                AllPermissionsExplanationActivity::class.java.name to AllPermissionsExplanationFragment::class.java,
                PermissionsActivity::class.java.name to CollectionPermissionsFragment::class.java,
                ManageSpaceActivity::class.java.name to ManageSpaceFragment::class.java,
                SharedDecksActivity::class.java.name to SharedDecksFragment::class.java,
                StudyOptionsActivity::class.java.name to StudyOptionsFlowFragment::class.java,
                MultimediaActivity::class.java.name to MultimediaFlowFragment::class.java,
                NoteEditorActivity::class.java.name to NoteEditorPaneFragment::class.java,
            )

        val fragmentHosts =
            setOf(
                AccountActivity::class.java.name,
                SingleFragmentActivity::class.java.name,
                ConfigAwareSingleFragmentActivity::class.java.name,
                PreferencesActivity::class.java.name,
                CardViewerActivity::class.java.name,
            )

        val fragmentScreens =
            listOf(
                AccountFlowFragment::class.java,
                AnkiPackageImporterFragment::class.java,
                CardInfoFragment::class.java,
                ChangeNoteTypePage::class.java,
                CsvImporter::class.java,
                DeckOptions::class.java,
                DrawingFragment::class.java,
                FilteredDeckOptionsFragment::class.java,
                FinishedFragment::class.java,
                ImageCropper::class.java,
                ImageOcclusion::class.java,
                InstantNoteEditorFragment::class.java,
                MediaCheckFragment::class.java,
                PreferencesFragment::class.java,
                PreviewerFragment::class.java,
                ReviewerFragment::class.java,
                ReminderTroubleshootingFragment::class.java,
                ScheduleRemindersFragment::class.java,
                Statistics::class.java,
                TemplatePreviewerFragment::class.java,
            ).plus(activityScreens.values).associateBy { it.name }
    }
}

internal fun Fragment.finishScreen(
    resultCode: Int = Activity.RESULT_CANCELED,
    data: Intent? = null,
) {
    val host = requireActivity()
    if (host is DeckPicker) {
        host.finishScreen(this, ActivityResult(resultCode, data))
    } else {
        host.setResult(resultCode, data)
        host.finish()
    }
}

internal fun Fragment.hasScreenResultCaller(): Boolean {
    val host = requireActivity()
    return if (host is DeckPicker) host.hasScreenResultCaller(this) else host.callingActivity != null
}
