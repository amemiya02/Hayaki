// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2009 Andrew Dubya <andrewdubya@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Daniel Svard <daniel.svard@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>

// usage of 'this' in constructors when class is non-final - weak warning
// should be OK as this is only non-final for tests
@file:Suppress("LeakingThis")

package com.ichi2.anki

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.SQLException
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.Window
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.SearchView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.os.BundleCompat
import androidx.core.util.component1
import androidx.core.util.component2
import androidx.core.view.MenuItemCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.draganddrop.DropHelper
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import anki.collection.OpChanges
import com.google.android.material.behavior.HideViewOnScrollBehavior
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckPickerFloatingActionMenu.CloseAnimation
import com.ichi2.anki.DeckPickerFloatingActionMenu.FloatingActionBarToggleListener
import com.ichi2.anki.InitialActivity.StartupFailure
import com.ichi2.anki.InitialActivity.StartupFailure.DBError
import com.ichi2.anki.InitialActivity.StartupFailure.DatabaseLocked
import com.ichi2.anki.InitialActivity.StartupFailure.DirectoryNotAccessible
import com.ichi2.anki.InitialActivity.StartupFailure.DiskFull
import com.ichi2.anki.InitialActivity.StartupFailure.FutureAnkidroidVersion
import com.ichi2.anki.InitialActivity.StartupFailure.SDCardNotMounted
import com.ichi2.anki.InitialActivity.StartupFailure.StorageUndecided
import com.ichi2.anki.StudyOptionsFragment.Companion.registerStudyOptionsAddEditReminderHandler
import com.ichi2.anki.StudyOptionsFragment.Companion.registerStudyOptionsStudyHandler
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.android.back.exitViaDoubleTapBackCallback
import com.ichi2.anki.android.input.Shortcut
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.android.view.locationInWindow
import com.ichi2.anki.browser.CardBrowserFragment
import com.ichi2.anki.common.android.AdaptionUtil
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.destinations.DeferredNavigation
import com.ichi2.anki.common.destinations.PreferencesDestination
import com.ichi2.anki.common.destinations.ReviewDeckDestination
import com.ichi2.anki.common.destinations.StudyOptionsDestination
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.common.destinations.toIntent
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.android.getColorFromAttr
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.compat.CompatHelper.Companion.compat
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.contextmenu.DeckPickerMenuContentProvider
import com.ichi2.anki.deckpicker.BackgroundImage
import com.ichi2.anki.deckpicker.DeckDeletionResult
import com.ichi2.anki.deckpicker.DeckListActions
import com.ichi2.anki.deckpicker.DeckListScreen
import com.ichi2.anki.deckpicker.DeckListTitle
import com.ichi2.anki.deckpicker.DeckPickerProgress
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.deckpicker.DeckPickerViewModel.AnkiDroidEnvironment
import com.ichi2.anki.deckpicker.DeckPickerViewModel.AutomaticSyncAction
import com.ichi2.anki.deckpicker.DeckPickerViewModel.StartupResponse
import com.ichi2.anki.deckpicker.DisplayDeckNode
import com.ichi2.anki.deckpicker.EmptyCardsResult
import com.ichi2.anki.deckpicker.OptionsMenuState
import com.ichi2.anki.deckpicker.ShortcutData
import com.ichi2.anki.deckpicker.SyncIconState
import com.ichi2.anki.dialogs.AsyncDialogFragment
import com.ichi2.anki.dialogs.BackupPromptDialog
import com.ichi2.anki.dialogs.CreateDeckDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.CustomExceptionData
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DeckPickerBackupNoSpaceLeftDialog
import com.ichi2.anki.dialogs.DeckPickerConfirmDeleteDeckDialog
import com.ichi2.anki.dialogs.DeckPickerContextMenu
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.DeckPickerMaintenanceDialog
import com.ichi2.anki.dialogs.DeckPickerMaintenanceDialog.Check
import com.ichi2.anki.dialogs.DeckPickerNoSpaceLeftDialog
import com.ichi2.anki.dialogs.DialogHandlerMessage
import com.ichi2.anki.dialogs.EditDeckDescriptionDialog
import com.ichi2.anki.dialogs.EmptyCardsDialogFragment
import com.ichi2.anki.dialogs.FatalErrorDialog
import com.ichi2.anki.dialogs.ImportFileSelectionFragment
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ApkgImportResultLauncherProvider
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.CsvImportResultLauncherProvider
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ImportFileType
import com.ichi2.anki.dialogs.ImportViewModel
import com.ichi2.anki.dialogs.MeteredSyncDialog
import com.ichi2.anki.dialogs.ProfileLoadFailureDialog
import com.ichi2.anki.dialogs.SchedulerUpgradeDialog
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.dialogs.SyncErrorDialog.Companion.newInstance
import com.ichi2.anki.dialogs.SyncErrorDialog.SyncErrorDialogListener
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction.Companion.REQUEST_KEY
import com.ichi2.anki.dialogs.registerMeteredSyncHandler
import com.ichi2.anki.dialogs.registerProfileLoadFailureClosedHandler
import com.ichi2.anki.dialogs.setDeckPickerContextMenuResultListener
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment
import com.ichi2.anki.introduction.CollectionPermissionScreenLauncher
import com.ichi2.anki.introduction.WelcomeFragment
import com.ichi2.anki.introduction.hasCollectionStoragePermissions
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.mediacheck.MediaCheckFragment
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.navigation.AppNavigation
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.pages.AnkiPackageImporterFragment
import com.ichi2.anki.progress.observeProgress
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.uiConfigPrefs
import com.ichi2.anki.shareddeck.SharedDecksActivity
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.study.FinishedFragment
import com.ichi2.anki.study.ReviewSessionSummary
import com.ichi2.anki.study.StudyAvailability
import com.ichi2.anki.study.StudyCompletionUi
import com.ichi2.anki.study.currentDeckStudyState
import com.ichi2.anki.sync.MeteredSyncPermission
import com.ichi2.anki.sync.MeteredSyncPolicy
import com.ichi2.anki.sync.launchCatchingRequiringOneWaySyncDiscardUndo
import com.ichi2.anki.ui.BottomFadeFrameLayout
import com.ichi2.anki.ui.ResizablePaneManager
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.ui.windows.permissions.PermissionsActivity
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import com.ichi2.anki.update.UpdateDialogs
import com.ichi2.anki.update.UpdateViewModel
import com.ichi2.anki.utils.Destination
import com.ichi2.anki.utils.ShortcutUtils
import com.ichi2.anki.utils.ext.collectInLifecycle
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.utils.ext.onGlobalLayout
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.anki.utils.ext.setImageDrawableSafe
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.worker.SyncMediaWorker
import com.ichi2.anki.worker.SyncWorker
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.theme.foundation
import com.ichi2.compose.ui.dialogs.DialogAction
import com.ichi2.compose.ui.dialogs.DialogActionStyle
import com.ichi2.compose.ui.dialogs.DialogMark
import com.ichi2.compose.ui.dialogs.ErrorDialog
import com.ichi2.compose.ui.dialogs.PaperDialog
import com.ichi2.compose.ui.dialogs.createPaperDialog
import com.ichi2.themes.Themes
import com.ichi2.ui.AccessibleSearchView
import com.ichi2.ui.BadgeDrawableBuilder
import com.ichi2.utils.ClipboardUtil.IMPORT_MIME_TYPES
import com.ichi2.utils.NetworkUtils
import com.ichi2.utils.Permissions
import com.ichi2.utils.VersionUtils
import com.ichi2.utils.configureView
import com.ichi2.utils.isRtl
import com.ichi2.utils.showDialogIfWebViewOutdated
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.Translations
import timber.log.Timber
import java.io.File
import java.util.Date
import com.ichi2.anki.common.android.R as CommonR

/**
 * The current entry point for AnkiDroid. Displays decks, allowing users to study. Many other functions.
 *
 * On a tablet, this is a fragmented view, with [StudyOptionsFragment] to the right: [tryShowStudyOptionsPanel]
 *
 * Often used as navigation to: [ReviewerFragment], [NoteEditorFragment] (adding notes), [StudyOptionsFragment] [SharedDecksDownloadFragment]
 *
 * Responsibilities:
 * * Setup/upgrades of the application: [handleStartup]
 * * Error handling [handleDbLocked]
 * * Displaying a tree of decks, some of which may be collapsible: [DeckListScreen]
 *   * Allows users to study the decks
 *   * Displays deck progress
 *   * A long press opens a menu allowing modification of the deck
 *   * Filtering decks (if more than 10) [toolbarSearchView]
 * * Controlling syncs
 *   * A user may [pull down][pullToSyncWrapper] on the 'tree view' to sync
 *   * A [button][updateSyncIconFromState] which relies on [SyncIconState] to display whether a sync is needed
 *   * Blocks the UI and displays sync progress when syncing
 * * Displaying 'General' AnkiDroid options: backups, import, 'check media' etc...
 *   * General handler for error/global dialogs (search for 'as DeckPicker')
 *   * Such as import: [ImportViewModel]
 * * A Floating Action Button [floatingActionMenu] allowing the user to quickly add notes/cards.
 * * A custom image as a background can be added: [applyDeckPickerBackground]
 */
@KotlinCleanup("lots to do")
@NeedsTest("If the collection has been created, the app intro is not displayed")
@NeedsTest("If the user selects 'Sync Profile' in the app intro, a sync starts immediately")
@NeedsTest("Regression test of #19555 or remove 'android:configChanges' for the screen")
open class DeckPicker :
    NavigationDrawerActivity(),
    SyncErrorDialogListener,
    OnRequestPermissionsResultCallback,
    ChangeManager.Subscriber,
    ImportColpkgListener,
    BaseSnackbarBuilderProvider,
    ApkgImportResultLauncherProvider,
    CsvImportResultLauncherProvider,
    CollectionPermissionScreenLauncher,
    WelcomeFragment.Host {
    private lateinit var appNavigation: AppNavigation
    private val showsStandaloneScreen: Boolean
        get() = ::appNavigation.isInitialized && appNavigation.showsStandaloneScreen
    private var awaitingWelcome = false
    private var collectionReadyForEntry = false
    private var pendingEntry: Intent? = null
    private var waitForWelcomeAction = false
    internal val bottomNavContentClearance = mutableIntStateOf(0)

    val viewModel: DeckPickerViewModel by viewModels()

    private val importViewModel: ImportViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()

    internal lateinit var binding: DeckPickerLayout
        private set

    @VisibleForTesting
    internal val deckPickerBinding: DeckListLayout
        get() = binding.deckPickerPane

    @VisibleForTesting
    val floatingActionButtonBinding: AddMenuLayout
        get() = deckPickerBinding.floatingActionButton
    override var fragmented: Boolean
        get() = binding.studyoptionsFragment != null
        set(_) = throw UnsupportedOperationException()

    override val analyticsScreenName: String
        get() = selectedBottomNavItem()?.analyticsScreenName ?: super.analyticsScreenName

    /** Phones navigate with the bottom bar; the split-pane layout of large screens keeps the side drawer. */
    internal val bottomNavigationEnabled: Boolean
        get() = !hasNavigationDrawer

    // Short animation duration from system
    private var shortAnimDuration = 0

    /** The scroll position of the deck list ([DeckListScreen]) */
    private val deckListState = LazyListState()

    /** Space below the last Deck, so it scrolls clear of the add button, the bottom bar and the keyboard */
    @VisibleForTesting
    internal val deckListBottomPadding = mutableIntStateOf(0)

    /** Opacity of the deck rows: the user's choice while a background image is shown, otherwise opaque. */
    private val deckRowOpacity = mutableFloatStateOf(1f)

    /** The add button's menu is open: the deck list is dimmed behind it and screen readers skip it */
    private val isDeckListBehindAddMenu = mutableStateOf(false)

    /** Today, as the deck list's title shows it on phones */
    private val deckListDate = mutableStateOf(Date(TimeManager.time.intTimeMS()))

    private var launchStudyAllJob: Job? = null

    private val deckListActions =
        object : DeckListActions {
            override fun onStudyAll() = openNextStudyAllDeck()

            override fun onStudyCurrentDeck(deckId: DeckId) = onDeckClick(deckId, DeckSelectionType.SKIP_STUDY_OPTIONS)

            override fun onDeckClick(deckId: DeckId) = onDeckClick(deckId, DeckSelectionType.DEFAULT)

            override fun onDeckCountsClick(deckId: DeckId) = onDeckClick(deckId, DeckSelectionType.SHOW_STUDY_OPTIONS)

            override fun onDeckLongClick(deckId: DeckId) {
                viewModel.requestContextMenu(deckId)
            }

            override fun onDeckRightClick(
                deckId: DeckId,
                x: Float,
                y: Float,
            ) {
                viewModel.requestRightClickContextMenu(deckId, x, y)
                Timber.d("Right Click on deck recorded!! %d, %f %f", deckId, x, y)
            }

            override fun onToggleExpand(deckId: DeckId) {
                viewModel.toggleDeckExpand(deckId)
                dismissAllDialogFragments()
            }

            override fun onGetSharedDecks() = openAnkiWebSharedDecks()

            override fun onImport() = showImportDialog()

            override fun onCreateDeck() = showCreateDeckDialog()
        }
    private var backgroundImageErrorShown = false
    private lateinit var pullToSyncWrapper: SwipeRefreshLayout

    @VisibleForTesting
    lateinit var floatingActionMenu: DeckPickerFloatingActionMenu

    var activeSnackBar: Snackbar? = null
    private val activeSnackbarCallback =
        object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
            override fun onShown(transientBottomBar: Snackbar?) {
                activeSnackBar = transientBottomBar
            }

            override fun onDismissed(
                transientBottomBar: Snackbar?,
                event: Int,
            ) {
                activeSnackBar = null
            }
        }
    override val baseSnackbarBuilder: SnackbarBuilder = {
        anchorView = floatingActionButtonBinding.fabMain.takeIf { it.isShown }
        // Follow the FAB as it moves when the keyboard opens or closes.
        isAnchorViewLayoutListenerEnabled = true
        addCallback(activeSnackbarCallback)
    }

    private var syncMediaProgressJob: Job? = null

    // flag keeping track of when the app has been paused
    var activityPaused = false
        private set

    @VisibleForTesting
    val dueTree: DeckNode?
        get() = viewModel.dueTree

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var searchDecksIcon: MenuItem? = null

    /**
     * Flag to indicate whether the activity will perform a sync in its onResume.
     * Since syncing closes the database, this flag allows us to avoid doing any
     * work in onResume that might use the database and go straight to syncing.
     */
    private var syncOnResume = false

    private val deckToolbarMenu = mutableStateOf<DeckPickerToolbarMenu?>(null)

    @get:VisibleForTesting
    internal val visibleOptionsMenu: Menu? get() = deckToolbarMenu.value

    private val closeDeckSearch =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                toolbarSearchItem?.collapseActionView()
            }
        }
    private val deckMediaStatus = mutableIntStateOf(0)
    private val deckToolbarMediaSync = mutableStateOf(false)
    private val deckToolbarSyncState = mutableStateOf<SyncIconState?>(null)
    private var toolbarSearchItem: MenuItem? = null
    private var toolbarSearchView: AccessibleSearchView? = null

    override val permissionScreenLauncher = recreateActivityResultLauncher()

    private val reviewLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                processReviewResults(it)
            },
        )

    private val loginForSyncLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                if (it.resultCode == RESULT_OK) {
                    syncOnResume = true
                    // A hosted sign-in returns without resuming the activity.
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) refreshState()
                }
                afterWelcomeAction()
            },
        )

    private val requestPathUpdateLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                // The collection path was inaccessible on startup so just close the activity and let user restart
                finish()
            },
        )

    private val apkgFileImportResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                if (it.resultCode == RESULT_OK) {
                    onSelectedFileToImport(it.data!!)
                }
                afterWelcomeAction()
            },
        )

    private val csvImportResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                if (it.resultCode == RESULT_OK) {
                    onSelectedFileToImport(it.data!!)
                }
            },
        )

    // Use distinct registrations so the restored back stack still knows which result should continue Study All.
    private val studyAllReviewLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback { processReviewResults(it, studyAll = true) },
        )

    private val studyAllFinishedLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                if (it.resultCode == RESULT_OK) {
                    val finishedDeckId = FinishedFragment.studyAllDeckIdFromResult(it.data)
                    if (finishedDeckId != null) openNextStudyAllDeck(afterFinishedDeckId = finishedDeckId)
                }
            },
        )

    private val exitAndSyncBackCallback =
        object : OnBackPressedCallback(enabled = true) {
            override fun handleOnBackPressed() {
                if (awaitingWelcome) {
                    finish()
                    return
                }
                // TODO: Room for improvement now we use back callbacks
                // can't use launchCatchingTask because any errors
                // would need to be shown in the UI
                lifecycleScope
                    .launch {
                        automaticSync(runInBackground = true)
                    }.invokeOnCompletion {
                        finish()
                    }
            }
        }

    private val closeFloatingActionBarBackPressCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                floatingActionMenu.closeFloatingActionMenu(style = CloseAnimation.SHORT)
            }
        }

    private inner class DeckPickerActivityResultCallback(
        private val callback: (result: ActivityResult) -> Unit,
    ) : ActivityResultCallback<ActivityResult> {
        override fun onActivityResult(result: ActivityResult) {
            if (result.resultCode == RESULT_MEDIA_EJECTED) {
                onSdCardNotMounted()
                return
            }
            callback(result)
        }
    }

    // stored for testing purposes
    @VisibleForTesting
    var createMenuJob: Job? = null

    init {
        ChangeManager.subscribe(this)
    }

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------
    private fun onDeckClick(
        deckId: DeckId,
        selectionType: DeckSelectionType,
    ) {
        Timber.i("DeckPicker:: Selected deck with id %d", deckId)
        launchCatchingTask {
            handleDeckSelection(deckId, selectionType)
            if (fragmented) {
                updateDeckList()
            }
        }
    }

    private suspend fun showDeckPickerContextMenu(deckId: DeckId) {
        val menu = DeckPickerContextMenu.newInstance(deckId)
        CardBrowser.clearLastDeckId()
        showDialogFragment(menu)
    }

    private suspend fun showDeckPickerRightClickContextMenu(request: DeckPickerViewModel.RightClickMenuRequest) {
        DeckPickerMenuContentProvider.show(
            deckPicker = this@DeckPicker,
            deckId = request.deckId,
            x = request.x,
            y = request.y,
        )
    }

    // ----------------------------------------------------------------------------
    // ANDROID ACTIVITY METHODS
    // ----------------------------------------------------------------------------

    /** Called when the activity is first created.  */
    @Throws(SQLException::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { Themes.isNightTheme },
            navigationBarStyle = BottomFadeFrameLayout.navigationBarStyle(this),
        )

        binding = DeckPickerLayout(this)

        awaitingWelcome = !hasShownAppIntro() && AnkiDroidApp.fatalError == null
        pendingEntry = savedInstanceState?.let { BundleCompat.getParcelable(it, STATE_PENDING_ENTRY, Intent::class.java) }
        waitForWelcomeAction = savedInstanceState?.getBoolean(STATE_WAIT_FOR_WELCOME_ACTION) == true
        if (intent.hasExtra(EXTRA_SYNC_FROM_LOGIN)) {
            Timber.d("launched from introduction activity login: syncing")
            syncOnResume = true
        }

        setDrawerContent(binding.root)
        if (!fragmented) {
            // On recreation into this single-pane layout (e.g. a foldable changing form
            // factor), saved state can still hold the split-pane's side panel fragment.
            // A restored fragment without its container is never displayed, but it would
            // still reach its view lifecycle and register its menu items with this
            // activity. Remove it before that happens. See #21555.
            supportFragmentManager.findFragmentById(R.id.studyoptions_fragment)?.let { restoredFragment ->
                supportFragmentManager.commitNow {
                    remove(restoredFragment)
                }
            }
        }
        // TODO This method is run on every activity recreation, which can happen often.
        //  It seems that the original idea was for this to only run once, on app start.
        //  This method triggers backups, sync, and may re-show dialogs
        //  that may have been dismissed. Make this run only once?
        if (!awaitingWelcome) handleStartup()

        registerReceiver()

        // create inherited navigation drawer layout here so that it can be used by parent class
        initNavigationDrawer()
        if (bottomNavigationEnabled) {
            disableDrawerSwipe()
            disableDrawerIndicator()
        }
        setupBottomNavigation()
        savedInstanceState?.getString(STATE_SELECTED_TAB)?.let { tag ->
            binding.bottomNavigation?.selectedTab = NavigationItem.entries.firstOrNull { it.tag == tag } ?: NavigationItem.HOME
        }
        setupEdgeToEdge()
        title = resources.getString(R.string.app_name)

        setupDeckList()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                applyDeckPickerBackground()
                try {
                    awaitCancellation()
                } finally {
                    deckPickerBinding.background.setImageDrawable(null)
                }
            }
        }

        setupPullToSync()
        // Setup the FloatingActionButtons
        floatingActionMenu =
            DeckPickerFloatingActionMenu(
                context = this,
                homescreenBinding = binding,
                deckPicker = this,
                toggleListener =
                    FloatingActionBarToggleListener { isOpening ->
                        closeFloatingActionBarBackPressCallback.isEnabled = isOpening
                        isDeckListBehindAddMenu.value = isOpening
                    },
                initiallyOpen = savedInstanceState?.getBoolean("mIsFABOpen") == true,
            )

        setupDeckPickerToolbar()

        shortAnimDuration = resources.getInteger(android.R.integer.config_shortAnimTime)

        with(this) { showDialogIfWebViewOutdated() }

        // If a review reminder deserialization error has recently occurred
        // (ex. on app boot, when the app opened, etc.), inform the user via a dialog
        ReviewRemindersDatabase.checkDeserializationErrors(this)

        setFragmentResultListener(REQUEST_KEY) { _, bundle ->
            when (CustomStudyAction.fromBundle(bundle)) {
                CustomStudyAction.CUSTOM_STUDY_SESSION -> {
                    Timber.d("Custom study created")
                    updateDeckList()
                    openStudyOptions()
                }
                CustomStudyAction.EXTEND_STUDY_LIMITS -> {
                    Timber.d("Study limits updated")
                    fragment?.refreshInterface()
                    updateDeckList()
                }
            }
        }

        setDeckPickerContextMenuResultListener { result ->
            handleContextMenuSelection(result.option, result.deckId)
        }

        setFragmentResultListener(Check.MEDIA.requestKey) { _, _ ->
            Timber.i("Starting media check")
            startActivity(MediaCheckFragment.getIntent(this))
        }
        setFragmentResultListener(Check.DATABASE.requestKey) { _, _ ->
            performIntegrityCheck()
        }
        setFragmentResultListener(SchedulerUpgradeDialog.REQUEST_SCHEDULER_UPGRADE) { _, result ->
            if (result.getBoolean(SchedulerUpgradeDialog.RESULT_UPGRADE)) {
                launchCatchingRequiringOneWaySyncDiscardUndo {
                    this@DeckPicker.withProgress { withCol { sched.upgradeToV2() } }
                    showThemedToast(this@DeckPicker, TR.schedulingUpdateDone(), false)
                }
            } else {
                onBackPressedDispatcher.onBackPressed()
            }
        }
        registerMeteredSyncHandler { syncMedia, manual ->
            if (manual) Prefs.mediaSyncPaused = false
            handleNewSync(
                conflict = null,
                syncMedia = syncMedia,
                meteredSyncPermission = MeteredSyncPermission.ALLOW_METERED_SYNC_THIS_TIME,
            )
        }

        registerStudyOptionsStudyHandler {
            Timber.d("Opening study screen from DeckPicker's study options panel")
            openReviewer()
        }

        registerStudyOptionsAddEditReminderHandler { did: DeckId ->
            Timber.d("Opening review reminders screen from DeckPicker's study options panel")
            openScheduleReminders(did)
        }

        // A fatal startup error waits until the profile explanation is closed, so it can't cover it
        registerProfileLoadFailureClosedHandler {
            (viewModel.flowOfStartupResponse.value as? StartupResponse.FatalError)?.let { handleStartupFailure(it.failure) }
        }

        pullToSyncWrapper.configureView(
            this,
            IMPORT_MIME_TYPES,
            DropHelper.Options
                .Builder()
                .setHighlightColor(getColorFromAttr(this, CommonR.attr.foundationEmphasis))
                .setHighlightCornerRadiusPx(0)
                .build(),
            onReceiveContentListener,
        )

        observeProgress(viewModel) { progress -> progressMessage(progress) }
        setupFlows()
        installScreenNavigation()
        if (savedInstanceState == null) deferEntryIntent(intent)
    }

    private fun installScreenNavigation() {
        val home = drawerLayout.getChildAt(0)
        appNavigation = AppNavigation(this, home, showWelcome = awaitingWelcome)
        val host =
            ComposeView(this).apply {
                id = R.id.app_navigation
                consumeWindowInsets = false
                setContent { AnkiDroidTheme { appNavigation.Content() } }
            }
        val params = home.layoutParams
        drawerLayout.removeViewAt(0)
        val layout =
            CoordinatorLayout(this).apply {
                // Feedback can arrive while the Decks view is covered by another destination.
                id = R.id.root_layout
                addView(host, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                binding.bottomNavigation?.let { bar ->
                    (bar.parent as ViewGroup).removeView(bar)
                    addView(bar, bar.layoutParams)
                }
            }
        drawerLayout.addView(layout, 0, params)
        ViewCompat.requestApplyInsets(drawerLayout)
    }

    internal fun selectNavigationTab(tab: NavigationItem) = appNavigation.selectTab(tab)

    override fun launchInApp(intent: Intent): Boolean = ::appNavigation.isInitialized && appNavigation.launch(intent)

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun startActivityForResult(
        intent: Intent,
        requestCode: Int,
        options: Bundle?,
    ) {
        if (!::appNavigation.isInitialized || !appNavigation.launch(intent, requestCode)) {
            super.startActivityForResult(intent, requestCode, options)
        }
    }

    internal val currentScreenFragment: Fragment?
        get() = if (::appNavigation.isInitialized) appNavigation.currentFragment else null

    internal fun finishScreen(
        fragment: Fragment,
        result: ActivityResult,
    ) = appNavigation.finish(fragment, result)

    internal fun hasScreenResultCaller(fragment: Fragment): Boolean = appNavigation.hasResultCaller(fragment)

    override fun onWelcomeCompleted(nextAction: String?) {
        if (!awaitingWelcome || isFinishing) return
        awaitingWelcome = false
        if (nextAction != null && (nextAction != EXTRA_IMPORT || pendingEntry?.action != ACTION_DEFERRED_IMPORT)) {
            intent.putExtra(nextAction, true)
        }
        appNavigation.completeWelcome()
        handleStartup()
        viewModel.onScreenResumed()
    }

    override fun setupBackPressedCallbacks() {
        onBackPressedDispatcher.addCallback(this, exitAndSyncBackCallback)
        onBackPressedDispatcher.addCallback(this, exitViaDoubleTapBackCallback())
        onBackPressedDispatcher.addCallback(this, closeFloatingActionBarBackPressCallback)
        onBackPressedDispatcher.addCallback(this, closeDeckSearch)
        super.setupBackPressedCallbacks()
    }

    override fun fitsSystemWindows(): Boolean = false

    /** Draws the deck list ([DeckListScreen]) from the view model's state */
    private fun setupDeckList() {
        deckPickerBinding.deckList.setContent {
            AnkiDroidTheme {
                val state by viewModel.flowOfDeckListScreen.collectAsStateWithLifecycle()
                val bottom = with(LocalDensity.current) { deckListBottomPadding.intValue.toDp() }
                state?.let {
                    Column(modifier = if (isDeckListBehindAddMenu.value) Modifier.clearAndSetSemantics { } else Modifier) {
                        if (deckMediaStatus.intValue != 0) {
                            Text(
                                text = getString(deckMediaStatus.intValue),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DeckListScreen(
                            state = it,
                            actions = deckListActions,
                            listState = deckListState,
                            contentPadding = PaddingValues(bottom = bottom),
                            rowOpacity = deckRowOpacity.floatValue,
                        )
                    }
                }
                UpdateDialogs(updateViewModel)
            }
        }
    }

    private fun setupDeckPickerToolbar() {
        binding.toolbarContainer.setContent {
            AnkiDroidTheme {
                DeckPickerToolbar(
                    menu = deckToolbarMenu.value,
                    syncState = deckToolbarSyncState.value,
                    syncingMedia = deckToolbarMediaSync.value,
                    onNavigate = if (hasNavigationDrawer) ::onNavigationPressed else null,
                    onAction = { onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, it) },
                ) {
                    if (bottomNavigationEnabled) {
                        DeckListTitle(date = deckListDate.value)
                    } else {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.foundation.colors.onBackground,
                        )
                    }
                }
            }
        }
        invalidateOptionsMenu()
    }

    override fun invalidateOptionsMenu() {
        if (!::binding.isInitialized || !navDrawerIsReady() || !::floatingActionMenu.isInitialized) return
        val menu = DeckPickerToolbarMenu(this)
        onCreatePanelMenu(Window.FEATURE_OPTIONS_PANEL, menu)
        deckToolbarMenu.value = menu
    }

    /** Applied edge-to-edge insets for the screen */
    private fun setupEdgeToEdge() {
        /**
         * Pads the end of the deck list so its last Deck scrolls clear of what covers the list's
         * bottom: the add button, or (while it is hidden, when searching) the keyboard or the bars.
         */
        fun updateDeckListBottomPadding() {
            val deckList = deckPickerBinding.deckList
            if (deckList.height == 0) return
            val listBottom = deckList.locationInWindow().y + deckList.height
            val fab = floatingActionButtonBinding.fabMain
            val fabRoot = floatingActionButtonBinding.root
            val coveredFrom =
                if (fab.isVisible && fab.height > 0) {
                    fab.locationInWindow().y - ((fab.layoutParams as? MarginLayoutParams)?.topMargin ?: 0)
                } else {
                    fabRoot.locationInWindow().y + fabRoot.height - fabRoot.paddingBottom
                }
            val padding = (listBottom - coveredFrom).coerceAtLeast(0)
            if (deckListBottomPadding.intValue != padding) deckListBottomPadding.intValue = padding
        }

        var systemBottom = 0
        var keyboardBottom = 0

        fun updateFabClearance() {
            val bar = binding.bottomNavigation
            val barHeight =
                if (bar != null && bar.isShown && HideViewOnScrollBehavior.from(bar).isScrolledIn) {
                    (bar.height - bar.paddingBottom).takeIf { it > 0 } ?: resources.getDimensionPixelSize(R.dimen.bottom_bar_height)
                } else {
                    0
                }
            floatingActionButtonBinding.root.updatePadding(bottom = maxOf(systemBottom + barHeight, keyboardBottom))
        }

        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { _, insets ->
            val bars = insets.getInsets(systemBars() or displayCutout())
            val navigation = insets.getInsets(navigationBars() or displayCutout())
            binding.toolbarContainer.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            applyDrawerInsets(insets)
            binding.bottomNavigation?.updatePadding(left = navigation.left, right = navigation.right, bottom = navigation.bottom)
            systemBottom = navigation.bottom
            keyboardBottom = insets.getInsets(ime()).bottom
            updateFabClearance()
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(deckPickerBinding.root) { deckPickerInclude, insets ->
            val bars = insets.getInsets(systemBars() or displayCutout())
            // Let the system draw its own scrim when the nav bar is on the side
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val nav = insets.getInsets(navigationBars())
                window.isNavigationBarContrastEnforced = nav.left > 0 || nav.right > 0
            }
            val isRtl = deckPickerInclude.isRtl()
            deckPickerInclude.updatePadding(
                left = if (isRtl && fragmented) 0 else bars.left,
                right = if (!isRtl && fragmented) 0 else bars.right,
            )

            insets
        }
        // Insets move the FAB's ancestors without changing the FAB's bounds within its parent.
        // Wait until the whole hierarchy is laid out before reading positions in the window.
        deckPickerBinding.root.onGlobalLayout(this) {
            updateFabClearance()
            updateDeckListBottomPadding()
        }
        if (fragmented) {
            val studyoptionsView = binding.studyoptionsFragment ?: return
            ViewCompat.setOnApplyWindowInsetsListener(studyoptionsView) { _, insets ->
                val bars = insets.getInsets(systemBars() or displayCutout())
                // the toolbar clears the top and the deck list clears the side touching the divider:
                // the panel's fragments apply the remaining insets themselves, so their scrolled content
                // renders underneath the navigation bar
                val isRtl = studyoptionsView.isRtl()
                insets.inset(
                    if (isRtl) 0 else bars.left,
                    bars.top,
                    if (isRtl) bars.right else 0,
                    0,
                )
            }
        }
    }

    private fun setupPullToSync() {
        pullToSyncWrapper =
            deckPickerBinding.pullToSyncWrapper.apply {
                setDistanceToTriggerSync(SWIPE_TO_SYNC_TRIGGER_DISTANCE)
                setOnRefreshListener {
                    Timber.i("Pull to Sync: Syncing")
                    pullToSyncWrapper.isRefreshing = false
                    sync()
                }
            }
        // Only allow pull-to-sync when the deck list is scrolled to the top.
        lifecycleScope.launch {
            snapshotFlow { deckListState.firstVisibleItemIndex == 0 && deckListState.firstVisibleItemScrollOffset == 0 }
                .collect { atTop -> pullToSyncWrapper.isEnabled = atTop }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun setupFlows() {
        fun onDeckDeleted(result: DeckDeletionResult) {
            floatingActionButtonBinding.fabMain.isVisible = true
            showSnackbar(result.toHumanReadableString(), Snackbar.LENGTH_SHORT) {
                setAction(CommonString.undo) { undo() }
            }
        }

        fun onCardsEmptied(result: EmptyCardsResult) {
            showSnackbar(result.toHumanReadableString(), Snackbar.LENGTH_SHORT) {
                setAction(CommonString.undo) { undo() }
            }
        }

        fun onDeckCountsChanged(unit: Unit) {
            updateDeckList()
            tryShowStudyOptionsPanel()
        }

        fun onDestinationChanged(destination: Destination) {
            startActivity(destination.toIntent(this))
        }

        fun onExportDeck(deckId: DeckId) {
            ExportDialogFragment.forDeckList(deckId).show(supportFragmentManager, "exportOptions")
        }

        fun onPromptUserToUpdateScheduler(op: Unit) {
            if (supportFragmentManager.findFragmentByTag(SchedulerUpgradeDialog.TAG) == null) {
                SchedulerUpgradeDialog().showNow(supportFragmentManager, SchedulerUpgradeDialog.TAG)
            }
        }

        fun onOptionsMenuUpdated(unused: OptionsMenuState) {
            deckToolbarMenu.value?.let { menu ->
                updateMenuFromState(menu)
                updateSearchVisibilityFromState(menu)
            }
        }

        fun onCollectionStatusChanged(isInInitialState: Boolean) {
            // Hide the background when there are no cards to improve text readability.
            deckPickerBinding.background.isVisible = !isInInitialState
        }

        fun onResizingDividerVisibilityChanged(isVisible: Boolean) {
            binding.resizingDivider?.isVisible = isVisible
        }

        fun onStudyOptionsVisibilityChanged(collectionHasNoCards: Boolean) {
            invalidateOptionsMenu()
            binding.studyoptionsFrame?.isVisible = !collectionHasNoCards
        }

        fun onFocusedDeckChanged(deckId: DeckId?) {
            if (deckId != null) tryShowStudyOptionsPanel()
            val position = deckId?.let { viewModel.findDeckPosition(it) } ?: 0
            // the summary and the column headings come before the Decks
            val item = position + DECK_LIST_ITEMS_BEFORE_DECKS
            // Skip centering if the deck is already on screen.
            val layout = deckListState.layoutInfo
            val visible = layout.visibleItemsInfo.firstOrNull { it.index == item }
            if (visible != null && visible.offset >= layout.viewportStartOffset &&
                visible.offset + visible.size <= layout.viewportEndOffset
            ) {
                return
            }
            lifecycleScope.launch {
                deckListState.scrollToItem(item, scrollOffset = -deckPickerBinding.deckList.height / 2)
            }
        }

        fun onDecksReloaded(param: Unit) {
            deckPickerBinding.progressBar.isVisible = false
            // TODO: Refresh deck metadata in the reminder pane
            fragment?.refreshInterface()
        }

        fun onStartupResponse(response: StartupResponse) {
            Timber.d("onStartupResponse: %s", response)
            when (response) {
                is StartupResponse.RequestPermissions -> {
                    viewModel.flowOfStartupResponse.value = null // Prevent duplicate permission screen launches
                    permissionScreenLauncher.launch(
                        PermissionsActivity.getIntent(this, response.requiredPermissions),
                    )
                }

                is StartupResponse.Success -> {
                    // Set flowOfStartupResponse to null after handling so it isn't re-emitted on resume.
                    // Must stay here: clearing in ViewModel would break cold start (collector is only active at RESUMED).
                    viewModel.flowOfStartupResponse.value = null
                    invalidateOptionsMenu()
                    showStartupScreensAndDialogs(sharedPrefs(), 0)
                    showProfileLoadFailureIfAny()
                    collectionReadyForEntry = true
                    if (intent.hasExtra(EXTRA_IMPORT) || intent.hasExtra(EXTRA_SYNC_SETUP)) {
                        waitForWelcomeAction = true
                        refreshState()
                    }
                    openPendingEntry()

                    if (tryShowStudyOptionsPanel()) {
                        ResizablePaneManager(
                            parentLayout = requireNotNull(binding.deckpickerXlView) { "deckpickerXlView" },
                            divider = requireNotNull(binding.resizingDivider) { "resizingDivider" },
                            startPane = deckPickerBinding.root,
                            endPane = requireNotNull(binding.studyoptionsFragment) { "studyoptionsFragment" },
                            sharedPrefs = uiConfigPrefs(),
                            startPaneWeightKey = PREF_DECK_PICKER_PANE_WEIGHT,
                            endPaneWeightKey = PREF_STUDY_OPTIONS_PANE_WEIGHT,
                            onResizingChanged = { isResizing -> fragment?.onResizingChanged(isResizing) },
                        )
                    }
                }
                is StartupResponse.FatalError -> {
                    // the startup check is asynchronous, so the menu may already have been
                    // built by the time a failure arrives; rebuild it so onCreateOptionsMenu
                    // sees the error and blanks it
                    invalidateOptionsMenu()
                    if (showProfileLoadFailureIfAny()) {
                        Timber.i("Showing the startup failure once the profile load failure is closed")
                    } else {
                        handleStartupFailure(response.failure)
                    }
                }
            }
        }

        fun onError(errorMessage: String) {
            createPaperDialog(this) { dialog ->
                ErrorDialog(
                    title = getString(CommonString.vague_error),
                    message = errorMessage,
                    onDismissRequest = dialog::dismiss,
                )
            }.show()
        }

        viewModel.deckDeletedNotification.collectInLifecycle(::onDeckDeleted)
        viewModel.flowOfDeleteDeckConfirmation.collectInLifecycle(::showDeleteDeckConfirmationDialog)
        viewModel.flowOfRenameDeck.collectInLifecycle(::renameDeckDialog)
        viewModel.flowOfStudyDeck.collectInLifecycle { deckId ->
            launchCatchingTask {
                handleDeckSelection(deckId, DeckSelectionType.SKIP_STUDY_OPTIONS)
            }
        }
        viewModel.emptyCardsNotification.collectInLifecycle(::onCardsEmptied)
        viewModel.flowOfDeckCountsChanged.collectInLifecycle(::onDeckCountsChanged)
        viewModel.flowOfDestination.collectInLifecycle(::onDestinationChanged)
        viewModel.flowOfNavigate.collectInLifecycle { navigate(it) }
        viewModel.flowOfExportDeck.collectInLifecycle(::onExportDeck)
        viewModel.flowOfCreateShortcut.collectInLifecycle(::createIcon)
        viewModel.flowOfDisableShortcuts.collectInLifecycle(::disableDeckAndChildrenShortcuts)
        viewModel.onError.collectInLifecycle(::onError)
        viewModel.flowOfPromptUserToUpdateScheduler.collectInLifecycle(::onPromptUserToUpdateScheduler)
        viewModel.flowOfOptionsMenuState.filterNotNull().collectInLifecycle(::onOptionsMenuUpdated)
        viewModel.flowOfDeckListInInitialState.filterNotNull().collectInLifecycle(::onCollectionStatusChanged)
        viewModel.flowOfCollectionHasNoCards.collectInLifecycle(::onStudyOptionsVisibilityChanged)
        viewModel.flowOfFocusedDeck.collectInLifecycle(::onFocusedDeckChanged)
        viewModel.flowOfResizingDividerVisible.collectInLifecycle(::onResizingDividerVisibilityChanged)
        viewModel.flowOfDecksReloaded.collectInLifecycle(::onDecksReloaded)
        viewModel.flowOfStartupResponse.filterNotNull().collectInLifecycle(::onStartupResponse)
        viewModel.flowOfShowContextMenu.collectInLifecycle(::showDeckPickerContextMenu)
        viewModel.flowOfShowRightClickContextMenu.collectInLifecycle(::showDeckPickerRightClickContextMenu)
        // navigation should be done on RESUMED
        importViewModel.importAddFlow.filterNotNull().collectInLifecycle(Lifecycle.State.RESUMED) {
            importViewModel.takeImportAdd()?.let(::importAdd)
        }
        importViewModel.importReplaceFlow.filterNotNull().collectInLifecycle(Lifecycle.State.RESUMED) {
            importViewModel.takeImportReplace()?.let(::importReplace)
        }
    }

    private val onReceiveContentListener =
        OnReceiveContentListener { _, payload ->
            val (uriContent, remaining) = payload.partition { item -> item.uri != null }

            val clip = uriContent?.clip ?: return@OnReceiveContentListener remaining
            val uri = clip.getItemAt(0).uri
            val importJob = onSelectedFileToImport(Intent().setData(uri))
            compat.retainDropPermissions(payload, importJob)

            return@OnReceiveContentListener remaining
        }

    private fun handleContextMenuSelection(
        selectedOption: DeckPickerContextMenuOption,
        deckId: DeckId,
    ) {
        when (selectedOption) {
            DeckPickerContextMenuOption.DELETE_DECK -> {
                Timber.i("ContextMenu: Delete deck selected")

                /* we can only disable the shortcut for now as it is restricted by Google https://issuetracker.google.com/issues/68949561?pli=1#comment4
                 * if fixed or given free hand to delete the shortcut with the help of API update this method and use the new one
                 */
                // TODO: it feels buggy that this is not called on all deck deletion paths
                viewModel.disableDeckAndChildrenShortcuts(deckId)
                dismissAllDialogFragments()
                deleteDeck(deckId)
            }
            DeckPickerContextMenuOption.DECK_OPTIONS -> {
                Timber.i("ContextMenu: Open deck options selected")
                viewModel.openDeckOptions(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.CUSTOM_STUDY -> {
                Timber.i("ContextMenu: Custom study option selected")
                showDialogFragment(CustomStudyDialog.createInstance(deckId))
            }
            DeckPickerContextMenuOption.CREATE_SHORTCUT -> {
                Timber.i("ContextMenu: Create icon for a deck")
                viewModel.createIcon(deckId)
            }
            DeckPickerContextMenuOption.RENAME_DECK -> {
                Timber.i("ContextMenu: Rename deck selected")
                renameDeckDialog(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.EXPORT_DECK -> {
                Timber.i("ContextMenu: Export deck selected")
                viewModel.exportDeck(deckId)
            }
            DeckPickerContextMenuOption.UNBURY -> {
                Timber.i("ContextMenu: Unbury deck selected")
                viewModel.unburyDeck(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.CUSTOM_STUDY_REBUILD -> {
                Timber.i("ContextMenu: Rebuild deck selected")
                viewModel.rebuildFilteredDeck(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.CUSTOM_STUDY_EMPTY -> {
                Timber.i("ContextMenu: Empty deck selected")
                emptyFiltered(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.CREATE_SUBDECK -> {
                Timber.i("ContextMenu: Create Subdeck selected")
                createSubDeckDialog(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.BROWSE_CARDS -> {
                Timber.i("ContextMenu: Browse cards")
                viewModel.browseCards(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.ADD_CARD -> {
                Timber.i("ContextMenu: Add selected")
                viewModel.addNote(deckId, setAsCurrent = true)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.EDIT_DESCRIPTION -> {
                Timber.i("Editing deck description for deck '%d'", deckId)
                showDialogFragment(EditDeckDescriptionDialog.newInstance(deckId))
            }
            DeckPickerContextMenuOption.SCHEDULE_REMINDERS -> {
                Timber.i("Scheduling review reminders for deck '%d'", deckId)
                dismissAllDialogFragments()
                openScheduleReminders(deckId)
            }
        }
    }

    /**
     * @see DeckPickerViewModel.handleStartup
     */
    private fun handleStartup() {
        val context = appContext

        val environment: AnkiDroidEnvironment =
            object : AnkiDroidEnvironment {
                private val permissions = selectStoragePermissions(context)

                override fun hasRequiredPermissions(): Boolean = permissions.hasRequiredPermissions(context)

                override val requiredPermissions: StoragePermissionSet
                    get() = permissions

                override val preferences: SharedPreferences
                    get() = context.sharedPrefs()

                override fun decideStorageIfUndecided() = InitialActivity.decideStorageIfUndecided(context)

                override fun initializeAnkiDroidFolder(): Boolean = CollectionHelper.isCurrentAnkiDroidDirAccessible(context)
            }

        viewModel.handleStartup(environment = environment)
    }

    @VisibleForTesting
    fun handleStartupFailure(failure: StartupFailure) {
        when (failure) {
            is SDCardNotMounted -> {
                Timber.i("SD card not mounted")
                onSdCardNotMounted()
            }
            is DirectoryNotAccessible -> {
                Timber.i("AnkiDroid directory inaccessible")
                if (ScopedStorageService.collectionWasMadeInaccessibleAfterUninstall(this)) {
                    showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL)
                } else {
                    showDirectoryNotAccessibleDialog()
                }
            }
            is FutureAnkidroidVersion -> {
                Timber.i("Displaying database versioning")
                showDatabaseErrorDialog(DatabaseErrorDialogType.INCOMPATIBLE_DB_VERSION)
            }
            is DatabaseLocked -> {
                Timber.i("Displaying database locked error")
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DB_LOCKED)
            }
            is StartupFailure.InitializationError -> FatalErrorDialog.build(this, failure).show()
            is DiskFull -> displayNoStorageError()
            is DBError -> displayDatabaseFailure(CustomExceptionData.fromException(failure.exception))
            is StorageUndecided -> {
                // Undecided requires PREF_COLLECTION_PATH to be unset: only expected if
                // choosing a default collection path failed (SystemStorageException) after
                // the permission checks passed
                // TODO: #19552 - replace with the storage setup flow
                Timber.w("storage setup flow (#19552) not implemented; showing load-failure options")
                CrashReportService.sendExceptionReport(
                    IllegalStateException("StorageUndecided reached without a startup failure"),
                    "DeckPicker::handleStartupFailure",
                )
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED)
            }
        }
    }

    private fun showDirectoryNotAccessibleDialog() {
        val url = getString(R.string.link_full_storage_access)
        val message = getString(CommonString.directory_inaccessible_info, url)
        createPaperDialog(this) { dialog ->
            PaperDialog(
                title = getString(CommonString.directory_inaccessible),
                onDismissRequest = dialog::dismiss,
                mark = DialogMark(R.drawable.symbol_error, warning = true),
                actions =
                    listOf(
                        DialogAction(getString(CommonString.open_settings), {
                            requestPathUpdateLauncher.navigate(PreferencesDestination.Advanced)
                            dialog.dismiss()
                        }, DialogActionStyle.Filled),
                    ),
            ) {
                val colors = MaterialTheme.foundation.colors
                val linkStyle = TextLinkStyles(style = SpanStyle(color = colors.emphasis, textDecoration = TextDecoration.Underline))
                Text(
                    buildAnnotatedString {
                        append(message.substringBefore(url))
                        if (url in message) {
                            withLink(LinkAnnotation.Url(url, styles = linkStyle)) { append(url) }
                            append(message.substringAfter(url))
                        }
                    },
                )
            }
        }.show()
    }

    private fun displayDatabaseFailure(exceptionData: CustomExceptionData? = null) {
        Timber.i("Displaying database failure")
        showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED, exceptionData)
    }

    private fun displayNoStorageError() {
        Timber.i("Displaying no storage error")
        showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DISK_FULL)
    }

    private suspend fun applyDeckPickerBackground() {
        val result = BackgroundImage.resolve(this)
        if (result is BackgroundImage.ResolveResult.Failure) {
            showBackgroundImageError(result.message(this))
        }
        val drawable = (result as? BackgroundImage.ResolveResult.Ready)?.drawable
        // The default opacity keeps the Foundation's deck text at AA contrast over worst-case black or white
        // pixels. Higher opacities are the user's choice.
        deckPickerBinding.background.imageAlpha = BackgroundImage.imageAlpha(Prefs.backgroundImageOpacity)
        deckRowOpacity.floatValue = if (drawable != null) Prefs.deckRowOpacity.coerceIn(0, 100) / 100f else 1f
        deckPickerBinding.background.setImageDrawableSafe(drawable) {
            showBackgroundImageError(getString(CommonString.background_image_too_large))
        }
    }

    private fun showBackgroundImageError(message: String) {
        if (backgroundImageErrorShown) return
        backgroundImageErrorShown = true
        showThemedToast(this, message, shortLength = false)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (viewModel.flowOfStartupResponse.value is StartupResponse.FatalError) {
            return false
        }

        Timber.d("onCreateOptionsMenu()")
        if (isDrawerOpen && ::floatingActionMenu.isInitialized) {
            floatingActionMenu.closeFloatingActionMenu(style = CloseAnimation.LONG)
        }
        // Fragments own their menus: each fragment registers a MenuProvider against this
        // activity (see StudyOptionsFragment), and the menu host dispatches creation,
        // preparation and selection to them. This activity never drives a fragment's menu.
        menuInflater.inflate(R.menu.deck_picker, menu)
        menu.findItem(R.id.deck_picker_action_filter)?.let {
            toolbarSearchItem = it
            setupSearchIcon(it)
            toolbarSearchView = it.actionView as AccessibleSearchView
        }
        toolbarSearchView?.maxWidth = Integer.MAX_VALUE

        menu.findItem(R.id.action_export_collection)?.title = TR.actionsExport()
        menu.findItem(R.id.action_import)?.title = TR.actionsImport()
        menu.findItem(R.id.action_check_database)?.title = TR.sentenceCase.checkDatabase
        menu.findItem(R.id.action_check_media)?.title = TR.sentenceCase.checkMediaAction
        menu.findItem(R.id.action_empty_cards)?.title = TR.sentenceCase.emptyCards
        menu.findItem(R.id.action_deck_rename)?.title = TR.sentenceCase.renameDeck
        menu.findItem(R.id.action_deck_delete)?.title = TR.sentenceCase.deleteDeck
        setupMediaSyncMenuItem(menu)
        // redraw menu synchronously to avoid flicker
        updateMenuFromState(menu)
        updateSearchVisibilityFromState(menu)
        restoreSearchState(menu)
        // ...then launch a task to possibly update the visible icons.
        // Store the job so that tests can easily await it. In the future
        // this may be better done by injecting a custom test scheduler
        // into CollectionManager, and awaiting that.
        createMenuJob =
            launchCatchingTask {
                viewModel.refreshMenuState()
                updateSearchVisibilityFromState(menu)
                restoreSearchState(menu)
                updateDeckRelatedMenuItems(menu)
                updateMenuFromState(menu)
            }
        return super.onCreateOptionsMenu(menu)
    }

    fun setupMediaSyncMenuItem(menu: Menu) {
        // shouldn't be necessary, but `invalidateOptionsMenu()` is called way more than necessary
        syncMediaProgressJob?.cancel()

        val syncItem = menu.findItem(R.id.action_sync)
        val provider = MenuItemCompat.getActionProvider(syncItem) as? SyncActionProvider ?: return
        val workManager = WorkManager.getInstance(this)
        val flow = workManager.getWorkInfosForUniqueWorkFlow(UniqueWorkNames.SYNC_MEDIA)

        syncMediaProgressJob =
            lifecycleScope.launch {
                flow.flowWithLifecycle(lifecycle).collectLatest {
                    val active = it.firstOrNull { work -> !work.state.isFinished }
                    provider.isProgressShown = active?.state == WorkInfo.State.RUNNING
                    deckMediaStatus.intValue =
                        when {
                            active == null -> 0
                            provider.isProgressShown -> CommonString.sync_media_transferring
                            active.constraints.requiredNetworkType == NetworkType.UNMETERED && NetworkUtils.isActiveNetworkMetered() ->
                                CommonString.sync_media_waiting_wifi
                            else -> CommonString.sync_media_queued
                        }
                    deckToolbarMediaSync.value = provider.isProgressShown
                }
            }
    }

    private fun setupSearchIcon(menuItem: MenuItem) {
        menuItem.setOnActionExpandListener(
            object : MenuItem.OnActionExpandListener {
                // When SearchItem is expanded
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    Timber.i("DeckPicker:: SearchItem opened")
                    closeDeckSearch.isEnabled = true
                    viewModel.updateDeckSearchExpanded(true)
                    // Hide the floating action button if it is visible
                    floatingActionMenu.hideFloatingActionButton()
                    activeSnackBar?.anchorView = null
                    return true
                }

                // When SearchItem is collapsed
                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    Timber.i("DeckPicker:: SearchItem closed")
                    closeDeckSearch.isEnabled = false
                    viewModel.updateDeckSearchExpanded(false)
                    viewModel.updateDeckFilter("")
                    // Show the floating action button if it is hidden
                    floatingActionMenu.showFloatingActionButton()
                    activeSnackBar?.anchorView = floatingActionButtonBinding.fabMain
                    return true
                }
            },
        )

        val searchView = menuItem.actionView as AccessibleSearchView
        searchView.run {
            queryHint = getString(CommonString.search_decks)
            setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean {
                        clearFocus()
                        return true
                    }

                    override fun onQueryTextChange(newText: String): Boolean {
                        if (newText.isNotEmpty()) {
                            viewModel.updateDeckFilter(newText)
                        } else {
                            // Ignore empty callbacks from a replaced action view.
                            binding.root.post {
                                if (
                                    !isDestroyed &&
                                    toolbarSearchView === searchView &&
                                    menuItem.isActionViewExpanded &&
                                    searchView.query.isEmpty()
                                ) {
                                    viewModel.updateDeckFilter("")
                                }
                            }
                        }
                        return true
                    }
                },
            )
        }
        searchDecksIcon = menuItem
    }

    private fun restoreSearchState(menu: Menu) {
        val searchItem = menu.findItem(R.id.deck_picker_action_filter)
        if (viewModel.isDeckSearchExpanded && searchItem.isVisible) {
            val searchView = searchItem.actionView as AccessibleSearchView
            val query = viewModel.deckSearchQuery
            // Expanding the action view clears its text, so restore the query afterwards.
            searchItem.expandActionView()
            searchView.setQuery(query, false)
        }
    }

    fun updateMenuFromState(menu: Menu) {
        viewModel.optionsMenuState?.run {
            // The menu may have been cleared while refreshMenuState was suspended.
            menu.findItem(R.id.action_undo)?.let { updateUndoLabelFromState(it, undoLabel, undoAvailable) }
            menu.findItem(R.id.action_sync)?.let { updateSyncIconFromState(it, this) }
        }
        updateDeckRelatedMenuItems(menu)
    }

    /**
     * Shows/hides deck related menu items using the ViewModel's action eligibility.
     */
    private fun updateDeckRelatedMenuItems(menu: Menu) {
        viewModel.optionsMenuState?.run {
            menu.findItem(R.id.action_deck_rename)?.isVisible = showDeckActions
            menu.findItem(R.id.action_deck_delete)?.isVisible = showDeckActions
            // added to the menu by StudyOptionsFragment
            menu.findItem(R.id.action_deck_or_study_options)?.isVisible = showDeckActions
        }
    }

    private fun updateSearchVisibilityFromState(menu: Menu) {
        viewModel.optionsMenuState?.run {
            menu.findItem(R.id.deck_picker_action_filter)?.isVisible = searchIcon
        }
    }

    private fun updateUndoLabelFromState(
        menuItem: MenuItem,
        undoLabel: String?,
        undoAvailable: Boolean,
    ) {
        menuItem.run {
            if (undoLabel != null && undoAvailable) {
                isVisible = true
                title = undoLabel
            } else {
                isVisible = false
            }
        }
    }

    private fun updateSyncIconFromState(
        menuItem: MenuItem,
        state: OptionsMenuState,
    ) {
        deckToolbarSyncState.value = state.syncIcon
        val provider =
            MenuItemCompat.getActionProvider(menuItem) as? SyncActionProvider
                ?: return
        val tooltipText =
            when (state.syncIcon) {
                SyncIconState.Normal, SyncIconState.PendingChanges -> CommonString.button_sync
                SyncIconState.OneWay -> CommonString.sync_menu_title_one_way_sync
                SyncIconState.NotLoggedIn -> CommonString.sync_menu_title_no_account
            }
        provider.setTooltipText(getString(tooltipText))
        when (state.syncIcon) {
            // Without a sync server no sync is needed: the icon stays plain, as in the Mockup, and
            // tapping it opens sync setup
            SyncIconState.Normal, SyncIconState.NotLoggedIn -> {
                BadgeDrawableBuilder.removeBadge(provider)
            }
            SyncIconState.PendingChanges -> {
                BadgeDrawableBuilder(this)
                    .withColorAttr(CommonR.attr.badgeWarningColor)
                    .replaceBadge(provider)
            }
            SyncIconState.OneWay -> {
                BadgeDrawableBuilder(this)
                    .withText('!')
                    .withColorAttr(CommonR.attr.badgeErrorColor)
                    .replaceBadge(provider)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        when (item.itemId) {
            R.id.action_undo -> {
                Timber.i("DeckPicker:: Undo button pressed")
                undo()
                return true
            }
            R.id.deck_picker_action_filter -> {
                Timber.i("DeckPicker:: Search button pressed")
                return true
            }
            R.id.action_sync -> {
                Timber.i("DeckPicker:: Sync button pressed")
                toolbarSearchItem?.collapseActionView()
                val actionProvider = MenuItemCompat.getActionProvider(item) as? SyncActionProvider
                if (actionProvider?.isProgressShown == true) {
                    launchCatchingTask {
                        monitorMediaSync(this@DeckPicker)
                    }
                } else {
                    sync()
                }
                return true
            }
            R.id.action_import -> {
                Timber.i("DeckPicker:: Import button pressed")
                showImportDialog()
                return true
            }
            R.id.action_check_database -> {
                Timber.i("DeckPicker:: Check database button pressed")
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
                return true
            }
            R.id.action_check_media -> {
                Timber.i("DeckPicker:: Check media button pressed")
                showMediaCheckDialog()
                return true
            }
            R.id.action_empty_cards -> {
                Timber.i("DeckPicker:: Empty cards button pressed")
                EmptyCardsDialogFragment().show(
                    supportFragmentManager,
                    EmptyCardsDialogFragment.TAG,
                )
                return true
            }
            R.id.action_model_browser_open -> {
                Timber.i("DeckPicker:: Model browser button pressed")
                viewModel.openManageNoteTypes()
                return true
            }
            R.id.action_restore_backup -> {
                Timber.i("DeckPicker:: Restore from backup button pressed")
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_RESTORE_BACKUP)
                return true
            }
            R.id.action_deck_rename -> {
                viewModel.requestRenameSelectedDeck()
                return true
            }
            R.id.action_deck_delete -> {
                deleteSelectedDeck()
                return true
            }
            R.id.action_export_collection -> {
                Timber.i("DeckPicker:: Export menu item selected")
                ExportDialogFragment.forDeckList().show(supportFragmentManager, "exportDialog")
                return true
            }
            R.id.action_create_backup -> {
                Timber.i("DeckPicker::Create backup")
                createBackup()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun createBackup(): Job =
        launchCatchingTask {
            val created = viewModel.createBackup()
            val message = if (created) TR.profilesBackupCreated() else TR.profilesBackupUnchanged()
            showThemedToast(this@DeckPicker, message, false)
        }

    private fun showMediaCheckDialog() {
        Timber.i("showing media check dialog")
        DeckPickerMaintenanceDialog.newInstance(Check.MEDIA).show(supportFragmentManager, DeckPickerMaintenanceDialog.TAG)
    }

    fun showCreateFilteredDeckDialog() {
        startActivity(FilteredDeckOptionsFragment.getIntent(this))
    }

    fun exportCollection() {
        ExportDialogFragment.forDeckList().show(supportFragmentManager, "exportDialog")
    }

    private fun processReviewResults(
        result: ActivityResult,
        studyAll: Boolean = false,
    ) {
        if (result.resultCode != ReviewerFragment.RESULT_NO_MORE_CARDS) return
        val sessionSummary = ReviewSessionSummary.fromIntent(result.data)
        launchCatchingTask {
            when {
                studyAll -> {
                    val finishedDeckId = withCol { decks.selected() }
                    studyAllFinishedLauncher.launch(
                        FinishedFragment.getIntent(
                            this@DeckPicker,
                            studyAllDeckId = finishedDeckId,
                            sessionSummary = sessionSummary,
                        ),
                    )
                }
                sessionSummary != null -> StudyCompletionUi.openFinishedScreen(this@DeckPicker, sessionSummary)
                else -> {
                    val state = withCol { currentDeckStudyState() }
                    StudyCompletionUi.onReviewsCompleted(this@DeckPicker, state)
                }
            }
        }
        fragment?.refreshInterface()
    }

    override fun onResume() {
        activityPaused = false
        // stop onResume() processing the message.
        // we need to process the message after `loadDeckCounts` is added in refreshState
        // As `loadDeckCounts` is cancelled in `migrate()`
        val message = dialogHandler.popMessage()
        super.onResume()
        // the title shows today's date: the day may have changed while the app was away
        deckListDate.value = Date(TimeManager.time.intTimeMS())
        if (!awaitingWelcome) {
            if (navDrawerIsReady() && hasCollectionStoragePermissions()) {
                refreshState()
            }
            viewModel.onScreenResumed()
            updateViewModel.checkAutomatically()
        }
        message?.let { dialogHandler.sendStoredMessage(it) }
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        showDeferredMeteredSyncConfirmation()
    }

    fun refreshState() {
        // Welcome actions can arrive before storage permission is granted or via onNewIntent.
        if (collectionReadyForEntry && hasCollectionStoragePermissions()) {
            when {
                intent.getBooleanExtra(EXTRA_IMPORT, false) -> {
                    intent.removeExtra(EXTRA_IMPORT)
                    // Both package types use the same launcher. The selected file determines the importer.
                    ImportFileSelectionFragment.openImportFilePicker(this, ImportFileType.APKG)
                }
                intent.getBooleanExtra(EXTRA_SYNC_SETUP, false) -> {
                    intent.removeExtra(EXTRA_SYNC_SETUP)
                    loginToSyncServer()
                }
            }
        }
        if (syncOnResume && hasCollectionStoragePermissions()) {
            syncOnResume = false
            intent.removeExtra(EXTRA_SYNC_FROM_LOGIN)
            Timber.i("Performing Sync on Resume")
            Permissions.requestNotificationPermissionsForSyncing(this)
            sync()
        } else {
            selectNavigationItem(R.id.nav_decks)
            updateDeckList()
            title = resources.getString(R.string.app_name)
        }
        // Update sync status (if we've come back from a screen)
        invalidateOptionsMenu()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("mIsFABOpen", floatingActionMenu.isFABOpen)
        outState.putString(STATE_SELECTED_TAB, binding.bottomNavigation?.selectedTab?.tag)
        outState.putParcelable(STATE_PENDING_ENTRY, pendingEntry)
        outState.putBoolean(STATE_WAIT_FOR_WELCOME_ACTION, waitForWelcomeAction)
        importColpkgListener?.let {
            if (it is DatabaseRestorationListener) {
                outState.putString("dbRestorationPath", it.newAnkiDroidDirectory.absolutePath)
            }
        }
        outState.putString("sessionOnSyncConflict", sessionOnSyncConflict)
        outState.putSerializable("mediaUsnOnConflict", mediaUsnOnConflict)
        outState.putSerializable("mediaNetworkTypeOnConflict", mediaNetworkTypeOnConflict)
        floatingActionMenu.showFloatingActionButton()
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        savedInstanceState.getString("dbRestorationPath")?.let { path ->
            val path = File(path)
            CollectionHelper.ankiDroidDirectoryOverride = path
            importColpkgListener = DatabaseRestorationListener(this, path)
        }
        sessionOnSyncConflict = savedInstanceState.getString("sessionOnSyncConflict")
        mediaUsnOnConflict = savedInstanceState.getSerializableCompat("mediaUsnOnConflict")
        mediaNetworkTypeOnConflict =
            savedInstanceState.getSerializableCompat<NetworkType>("mediaNetworkTypeOnConflict") ?: NetworkType.UNMETERED
    }

    override fun onPause() {
        activityPaused = true
        // The deck count will be computed on resume. No need to compute it now
        viewModel.loadDeckCounts?.cancel()
        super.onPause()
    }

    /**
     * Performs a sync if the conditions are met, e.g. user is logged in, there are changes,
     * and auto sync is enabled.
     * @param runInBackground whether the sync should be performed in the background or not
     * @return whether a sync was performed or not.
     */
    private suspend fun automaticSync(runInBackground: Boolean = false): Boolean {
        if (hasPendingMeteredSyncConfirmation()) {
            Timber.d("autoSync: waiting for metered sync confirmation")
            return false
        }
        val action = viewModel.automaticSyncAction(runInBackground)
        // A manual request may have opened the confirmation during the status check.
        if (hasPendingMeteredSyncConfirmation()) {
            Timber.d("autoSync: waiting for metered sync confirmation")
            return false
        }
        when (action) {
            AutomaticSyncAction.NONE -> return false
            AutomaticSyncAction.MEDIA_ONLY -> {
                if (shouldFetchMedia()) {
                    val auth = syncAuth() ?: return false
                    SyncMediaWorker.start(this, auth)
                }
                setLastSyncTimeToNow()
            }
            AutomaticSyncAction.COLLECTION -> {
                if (runInBackground) {
                    Timber.i("autoSync: starting background")
                    val auth = syncAuth() ?: return false
                    SyncWorker.start(this, auth, shouldFetchMedia())
                } else {
                    Timber.i("autoSync: starting foreground")
                    sync(conflict = null, manual = false)
                }
                return true
            }
        }
        return false
    }

    /** Where the current downward drag started: reset whenever the finger moves up */
    private var touchAnchorY = 0f
    private var lastTouchY = 0f

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Content which fits once the bottom bar hides can no longer scroll, so scrolling alone
        // could never bring the bar back: a downward drag always restores it.
        if (bottomNavigationEnabled) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchAnchorY = event.y
                    lastTouchY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.y < lastTouchY) {
                        touchAnchorY = event.y
                    } else if (event.y - touchAnchorY > ViewConfiguration.get(this).scaledTouchSlop) {
                        binding.bottomNavigation?.let { HideViewOnScrollBehavior.from(it).slideIn(it) }
                    }
                    lastTouchY = event.y
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val current = currentScreenFragment
        if (current is DispatchKeyEventListener && current.dispatchKeyEvent(event)) return true
        if (showsStandaloneScreen || !bottomNavigationEnabled || event.action != KeyEvent.ACTION_DOWN || !event.isAltPressed) {
            return super.dispatchKeyEvent(event)
        }

        val bottomNavigation = binding.bottomNavigation ?: return super.dispatchKeyEvent(event)
        val destination =
            when (event.keyCode) {
                KeyEvent.KEYCODE_1 -> NavigationItem.HOME
                KeyEvent.KEYCODE_2 -> NavigationItem.BROWSER
                KeyEvent.KEYCODE_3 -> NavigationItem.STATS
                KeyEvent.KEYCODE_4 -> NavigationItem.MORE
                else -> return super.dispatchKeyEvent(event)
            }
        bottomNavigation.selectTab(destination)
        return true
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (showsStandaloneScreen) return super.onKeyUp(keyCode, event)
        if (selectedBottomNavItem() == NavigationItem.BROWSER) {
            val browser = currentScreenFragment as? CardBrowserFragment
            if (browser?.isVisible == true) {
                return browser.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)
            }
        }
        if (toolbarSearchView?.hasFocus() == true) {
            Timber.d("Skipping keypress: search action bar is focused")
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_A -> {
                Timber.i("Adding Note from keypress")
                viewModel.addNote(deckId = null, setAsCurrent = true)
                return true
            }
            KeyEvent.KEYCODE_B -> {
                if (event.isShiftPressed && event.isCtrlPressed) {
                    // shortcut SHIFT + CTRL + B
                    Timber.i("Create backup from keypress")
                    createBackup()
                } else if (event.isCtrlPressed) {
                    // Shortcut: CTRL + B
                    Timber.i("show restore backup dialog from keypress")
                    showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_RESTORE_BACKUP)
                } else {
                    // Shortcut: B
                    Timber.i("Open Browser from keypress")
                    openCardBrowser()
                }
                return true
            }
            KeyEvent.KEYCODE_Y -> {
                Timber.i("Sync from keypress")
                sync()
                return true
            }
            KeyEvent.KEYCODE_SLASH -> {
                Timber.d("Search from keypress")
                if (toolbarSearchItem?.isVisible == true) {
                    toolbarSearchItem?.expandActionView()
                }
                return true
            }
            KeyEvent.KEYCODE_S -> {
                Timber.i("Study from keypress")
                viewModel.requestStudySelectedDeck()
                return true
            }
            KeyEvent.KEYCODE_T -> {
                Timber.i("Open Statistics from keypress")
                openStatistics()
                return true
            }
            KeyEvent.KEYCODE_C -> {
                // Shortcut: C
                Timber.i("Check database from keypress")
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
                return true
            }
            KeyEvent.KEYCODE_D -> {
                // Shortcut: D
                Timber.i("Create Deck from keypress")
                showCreateDeckDialog()
                return true
            }
            KeyEvent.KEYCODE_F -> {
                Timber.i("Create Filtered Deck from keypress")
                showCreateFilteredDeckDialog()
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                // This action on a deck should only occur when the user see the deck name very clearly,
                // that is, when it appears in the trailing study option fragment
                if (fragmented) {
                    if (event.isShiftPressed) {
                        // Shortcut: Shift + DEL - Delete deck without confirmation dialog
                        Timber.i("Shift+DEL: Deck deck without confirmation")
                        deleteSelectedDeck()
                    } else {
                        // Shortcut: DEL
                        Timber.i("Delete Deck from keypress")
                        viewModel.requestDeleteDeckConfirmation()
                    }
                    return true
                }
            }
            KeyEvent.KEYCODE_R -> {
                // Shortcut: R
                // This action on a deck should only occur when the user see the deck name very clearly,
                // that is, when it appears in the trailing study option fragment
                if (fragmented) {
                    Timber.i("Rename Deck from keypress")
                    viewModel.requestRenameSelectedDeck()
                    return true
                }
            }
            KeyEvent.KEYCODE_P -> {
                Timber.i("Open Settings from keypress")
                openSettings()
                return true
            }
            KeyEvent.KEYCODE_M -> {
                Timber.i("Check media from keypress")
                showMediaCheckDialog()
                return true
            }
            KeyEvent.KEYCODE_E -> {
                if (event.isCtrlPressed) {
                    // Shortcut: CTRL + E
                    Timber.i("Show export dialog from keypress")
                    exportCollection()
                    return true
                }
            }
            KeyEvent.KEYCODE_I -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    // Shortcut: CTRL + Shift + I
                    Timber.i("Show import dialog from keypress")
                    showImportDialog()
                    return true
                }
            }
            KeyEvent.KEYCODE_N -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    // Shortcut: CTRL + Shift + N
                    Timber.i("Open ManageNoteTypes from keypress")
                    viewModel.openManageNoteTypes()
                    return true
                }
            }
            else -> {}
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Displays a confirmation dialog for deleting deck.
     */
    private fun showDeleteDeckConfirmationDialog(request: DeckPickerViewModel.DeleteDeckConfirmationRequest) {
        val confirmDeleteDeckDialog =
            DeckPickerConfirmDeleteDeckDialog.newInstance(
                deckName = request.deckName,
                deckId = request.deckId,
                totalCards = request.totalCards,
                isFilteredDeck = request.isFilteredDeck,
            )
        showDialogFragment(confirmDeleteDeckDialog)
    }

    /**
     * Perform the following tasks:
     * Automatic backup
     * loadStudyOptionsFragment() if tablet
     * Automatic sync
     */
    private fun onFinishedStartup() {
        launchCatchingTask {
            if (!automaticSync()) {
                BackupPromptDialog.showIfAvailable(this@DeckPicker)
            }
        }
    }

    private fun showProfileLoadFailureIfAny(): Boolean {
        val failure = ProfileManager.consumeLoadFailure()
        if (supportFragmentManager.findFragmentByTag(ProfileLoadFailureDialog.TAG) != null) return true
        if (failure == null) return false
        Timber.i("Showing the profile load failure")
        ProfileLoadFailureDialog.newInstance(failure).show(supportFragmentManager, ProfileLoadFailureDialog.TAG)
        return true
    }

    private fun showCollectionErrorDialog() {
        dialogHandler.sendMessage(CollectionLoadingErrorDialog().toMessage())
    }

    // VisibleForTesting: method is mocked, should be replaced
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun addNote(did: DeckId? = null) {
        viewModel.addNote(did, true)
    }

    private fun showStartupScreensAndDialogs(
        preferences: SharedPreferences,
        skip: Int,
    ) {
        if (!BackupManager.enoughDiscSpace(CollectionHelper.getCurrentAnkiDroidDirectory(this))) {
            Timber.i("Not enough space to do backup")
            showDialogFragment(DeckPickerNoSpaceLeftDialog.newInstance())
        } else if (preferences.getBoolean("noSpaceLeft", false)) {
            Timber.i("No space left")
            showDialogFragment(DeckPickerBackupNoSpaceLeftDialog.newInstance())
            preferences.edit { remove("noSpaceLeft") }
        } else if (InitialActivity.performSetupFromFreshInstallOrClearedPreferences(preferences)) {
            onFinishedStartup()
        } else if (skip < 2 && !InitialActivity.isLatestVersion(preferences)) {
            Timber.i("AnkiDroid is being updated and a collection already exists.")

            // For upgrades, we check if we are upgrading
            // to a version that contains additions to the database integrity check routine that we would
            // like to run on all collections. A missing version number is assumed to be a fresh
            // installation of AnkiDroid and we don't run the check.
            val current = VersionUtils.pkgVersionCode
            Timber.i("Current AnkiDroid version: %s", current)
            val previous: Long =
                if (preferences.contains(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION)) {
                    // Upgrading currently installed app
                    viewModel.getPreviousVersion(preferences, current)
                } else {
                    // Fresh install
                    current
                }
            preferences.edit { putLong(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION, current) }

            val upgradedPreferences = InitialActivity.upgradePreferences(this, previous)
            // Integrity check loads asynchronously and then restart deck picker when finished
            if (upgradedPreferences) {
                Timber.i("Updated preferences with no integrity check - restarting activity")
                // If integrityCheck() doesn't occur, but we did update preferences we should restart DeckPicker to
                // proceed
                ActivityCompat.recreate(this)
                return
            }

            // Hayaki has no changelog screen: record the version so this code is not reached again
            InitialActivity.setUpgradedToLatestVersion(preferences)
            val ver = resources.getString(CommonString.updated_version, VersionUtils.pkgVersionName)
            postSnackbar(ver, Snackbar.LENGTH_SHORT)
            showStartupScreensAndDialogs(preferences, 2)
        } else {
            // This is the main call when there is nothing special required
            Timber.i("No startup screens required")
            onFinishedStartup()
        }
    }

    // #16061. We have to queue snackbar to avoid the misaligned snackbar showed from onCreate()
    private fun postSnackbar(
        text: CharSequence,
        duration: Int = Snackbar.LENGTH_LONG,
    ) {
        binding.rootLayout.post { showSnackbar(text, duration) }
    }

    private fun undo() {
        launchCatchingTask {
            undoAndShowSnackbar()
        }
    }

    /**
     * Show a specific sync error dialog
     * @param dialogType id of dialog to show
     */
    override fun showSyncErrorDialog(dialogType: SyncErrorDialog.Type) {
        showSyncErrorDialog(dialogType, "")
    }

    /**
     * Show a specific sync error dialog
     * @param dialogType id of dialog to show
     * @param message text to show
     */
    override fun showSyncErrorDialog(
        dialogType: SyncErrorDialog.Type,
        message: String?,
    ) {
        val newFragment: AsyncDialogFragment = newInstance(dialogType, message)
        showAsyncDialogFragment(newFragment, NotificationChannel.SYNC)
    }

    // Callback method to submit error report
    fun sendErrorReport() {
        CrashReportService.sendExceptionReport(RuntimeException(), "DeckPicker.sendErrorReport")
    }

    // Callback method to handle repairing deck
    fun repairCollection() {
        Timber.i("Repairing the Collection")
        // TODO: doesn't work on null collection-only on non-openable(is this still relevant with withCol?)
        launchCatchingTask(resources.getString(CommonString.deck_repair_error)) {
            Timber.d("doInBackgroundRepairCollection")
            val result =
                withProgress(resources.getString(CommonString.backup_repair_deck_progress)) {
                    Timber.i("RepairCollection: Closing collection")
                    CollectionManager.ensureClosed()
                    val colFile =
                        CollectionManager.collectionPathInValidFolder().requireDiskBasedCollection().colDb
                    BackupManager.repairCollection(colFile)
                }
            if (!result) {
                showThemedToast(this@DeckPicker, resources.getString(CommonString.deck_repair_error), true)
                showCollectionErrorDialog()
            }
        }
    }

    // Callback method to handle database integrity check
    override fun integrityCheck() {
        // #5852 - We were having issues with integrity checks where the users had run out of space.
        // display a dialog box if we don't have the space
        val status = CollectionIntegrityStorageCheck.createInstance(this)
        if (status.shouldWarnOnIntegrityCheck()) {
            Timber.d("Displaying File Size confirmation")
            DeckPickerMaintenanceDialog
                .newInstance(Check.DATABASE, status.getWarningDetails(this))
                .show(supportFragmentManager, DeckPickerMaintenanceDialog.TAG)
        } else {
            performIntegrityCheck()
        }
    }

    private fun performIntegrityCheck() {
        Timber.i("performIntegrityCheck()")
        handleDatabaseCheck()
    }

    override fun mediaCheck() {
        showMediaCheckDialog()
    }

    open fun handleDbLocked() {
        Timber.i("Displaying Database Locked")
        showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DB_LOCKED)
    }

    fun restoreFromBackup(path: String) {
        importColpkg(path)
    }

    // Helper function to check if there are any saved stacktraces
    fun hasErrorFiles(): Boolean {
        for (file in fileList()) {
            if (file.endsWith(".stacktrace")) {
                return true
            }
        }
        return false
    }

    // TODO: Move both conflict fields into pending full-sync context carried by dialog arguments,
    //  confirmation callbacks, and deferred dialog messages.

    /** In the conflict case, we need to store the USN received from the initial sync, and reuse
     it after the user has decided. */
    var mediaUsnOnConflict: Int? = null
    var sessionOnSyncConflict: String? = null

    /** Preserve the original attempt's network restriction while the user chooses upload/download. */
    var mediaNetworkTypeOnConflict: NetworkType = NetworkType.UNMETERED

    /**
     * The mother of all syncing attempts. This might be called from sync() as first attempt to sync a collection OR
     * from the mSyncConflictResolutionListener if the first attempt determines that a full-sync is required.
     */
    override fun sync(conflict: ConflictResolution?) = sync(conflict, manual = true)

    private fun sync(
        conflict: ConflictResolution?,
        manual: Boolean,
    ) {
        if (!isLoggedIn() && syncServerAddress() == null) {
            Timber.i("Opening sync account")
            pullToSyncWrapper.isRefreshing = false
            loginToSyncServer()
            return
        }
        if (!isLoggedIn()) {
            Timber.w("User not logged in")
            pullToSyncWrapper.isRefreshing = false
            showSyncErrorDialog(SyncErrorDialog.Type.DIALOG_USER_NOT_LOGGED_IN_SYNC)
            return
        }

        // Preference or network changes must not bypass an unanswered confirmation.
        if (conflict == null && hasPendingMeteredSyncConfirmation()) {
            refreshState()
            return
        }

        val syncMedia = shouldFetchMedia() && (manual || !Prefs.mediaSyncPaused)
        MeteredSyncPolicy.confirmThen(
            // After selecting 'upload/download', the user has already accepted the metered warning.
            skipPrompt = conflict != null,
            onConfirmationRequired = {
                viewModel.deferredMeteredSyncMedia = syncMedia
                viewModel.deferredMeteredSyncManual = manual
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    showDeferredMeteredSyncConfirmation()
                }
                // Dismiss the deck-list loading indicator while the sync decision is pending.
                refreshState()
            },
        ) { permission ->
            if (manual && conflict == null) Prefs.mediaSyncPaused = false
            handleNewSync(conflict, syncMedia, meteredSyncPermission = permission)
        }
    }

    private fun hasPendingMeteredSyncConfirmation(): Boolean =
        viewModel.deferredMeteredSyncMedia != null || supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG) != null

    private fun showDeferredMeteredSyncConfirmation() {
        val syncMedia = viewModel.deferredMeteredSyncMedia ?: return
        if (supportFragmentManager.isStateSaved) return
        if (supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG) == null) {
            MeteredSyncDialog
                .newInstance(
                    syncMedia,
                    viewModel.deferredMeteredSyncManual,
                ).showNow(supportFragmentManager, MeteredSyncDialog.TAG)
        }
        viewModel.deferredMeteredSyncMedia = null
    }

    /** Opens sign-in to the sync server, or sync setup while no server is set up */
    override fun loginToSyncServer() {
        val intent = AccountActivity.getIntent(this, forResult = true)
        loginForSyncLauncher.launch(intent)
    }

    // Callback to import a file -- adding it to existing collection
    fun importAdd(importPath: String) {
        Timber.d("importAdd() for file %s", importPath)
        startActivity(AnkiPackageImporterFragment.getIntent(this, importPath))
    }

    // Callback to import a file -- replacing the existing collection
    fun importReplace(importPath: String) {
        Timber.d("importReplace() for file %s", importPath)
        importColpkg(importPath)
    }

    /**
     * Displays [StudyOptionsFragment] in a side panel on larger devices
     *
     * @see [DeckPickerLayout.studyoptionsFragment]
     *
     * @return whether the panel was shown
     */
    private fun tryShowStudyOptionsPanel(): Boolean {
        val container = binding.studyoptionsFragment ?: return false
        supportFragmentManager.onContainerAvailable(container)

        // Only clear navigation when closing another side panel. Import dialogs can already
        // be on the back stack when startup finishes, and must survive panel initialization.
        val sidePanel = supportFragmentManager.findFragmentById(container.id)
        if (sidePanel != null && sidePanel !is StudyOptionsFragment) {
            supportFragmentManager.popBackStackImmediate(
                null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE,
            )
        }

        val studyOptions = fragment
        if (studyOptions != null) {
            studyOptions.refreshInterface()
        } else {
            supportFragmentManager.commitNow {
                // Startup can restore a route above Decks before the AndroidView attaches the pane.
                supportFragmentManager.findFragmentById(container.id)?.let { remove(it) }
                add(container, StudyOptionsFragment(), null)
            }
        }
        return true
    }

    @NeedsTest("Instrumented tests for review reminders")
    private fun tryShowScheduleRemindersPanel(deckId: DeckId): Boolean {
        val sidePanel = binding.studyoptionsFragment ?: return false
        if (!sidePanel.isVisible) return false
        val newFragment =
            ScheduleRemindersFragment.newInstance(
                scope = ReviewReminderScope.DeckSpecific(deckId),
                host = ScheduleRemindersFragment.FragmentHost.STUDY_OPTIONS_FRAGMENT,
            )
        supportFragmentManager.commit {
            replace(sidePanel.id, newFragment)
            addToBackStack(null)
        }
        return true
    }

    val fragment: StudyOptionsFragment?
        get() = supportFragmentManager.findFragmentById(R.id.studyoptions_fragment) as? StudyOptionsFragment

    /**
     * Refresh the deck picker when the SD card is inserted.
     */
    override val broadcastsActions =
        super.broadcastsActions +
            mapOf(
                SdCardReceiver.ACTION_MEDIA_MOUNT
                    to { ActivityCompat.recreate(this) },
            )

    fun openAnkiWebSharedDecks() {
        if (!NetworkUtils.isOnline) {
            showSnackbar(CommonString.check_network)
            Timber.d("DeckPicker:: No network, Shared deck download failed")
            return
        }
        val intent = Intent(this, SharedDecksActivity::class.java)
        startActivity(intent)
    }

    private fun openStudyOptions() {
        if (tryShowStudyOptionsPanel()) return

        // otherwise, we need to launch the activity
        Timber.i("Opening Study Options")
        reviewLauncher.navigate(StudyOptionsDestination)
    }

    @NeedsTest("Instrumented tests for review reminders")
    private fun openScheduleReminders(deckId: DeckId) {
        if (tryShowScheduleRemindersPanel(deckId)) return

        // otherwise, we need to launch the activity
        Timber.i("Opening Schedule Reminders")
        viewModel.scheduleReviewReminders(deckId)
    }

    private fun openReviewerOrStudyOptions(selectionType: DeckSelectionType) {
        when (selectionType) {
            DeckSelectionType.DEFAULT -> {
                if (tryShowStudyOptionsPanel()) return
                openReviewer()
            }
            DeckSelectionType.SHOW_STUDY_OPTIONS -> openStudyOptions()
            DeckSelectionType.SKIP_STUDY_OPTIONS -> openReviewer()
        }
    }

    @NeedsTest("14608: Ensure that the deck options refer to the selected deck")
    @NeedsTest("18586: handle clicking on an empty filtered deck")
    private suspend fun handleDeckSelection(
        did: DeckId,
        selectionType: DeckSelectionType,
    ) {
        // ignore requests(ex: from keyboard shortcuts) when the collection is empty(and
        // adapter has no decks)
        if (displayedDecks.isEmpty()) {
            return
        }

        fun showEmptyDeckSnackbar() =
            showSnackbar(CommonString.empty_deck) {
                setAction(CommonString.menu_add) { viewModel.addNote(did, true) }
            }

        /** Check if we need to update the fragment or update the deck list */
        fun updateUi() {
            // Tablets must always show the study options that corresponds to the current deck,
            // regardless of whether the deck is currently reviewable or not.
            if (tryShowStudyOptionsPanel()) return

            // On phones, we update the deck list to ensure the currently selected deck is
            // highlighted correctly.
            updateDeckList()
        }

        val study =
            withCol {
                decks.select(did)
                currentDeckStudyState()
            }
        focusSelectedDeck(did)

        if (study.availability == StudyAvailability.CardsAvailable) {
            openReviewerOrStudyOptions(selectionType)
            return
        }

        if (study.isEmptyRegularDeck) {
            showEmptyDeckSnackbar()
            updateUi()
        } else {
            StudyCompletionUi.openFinishedScreen(this)
        }
    }

    /**
     * @see DeckPickerViewModel.updateDeckList
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    fun updateDeckList() {
        launchCatchingTask {
            withProgress { viewModel.updateDeckList().join() }
        }
    }

    private fun createIcon(shortcutData: ShortcutData) {
        // This code should not be reachable with lower versions
        val shortcut =
            ShortcutInfoCompat
                .Builder(this, shortcutData.deckId.toString())
                .setIntent(
                    with(DeferredNavigation) { ReviewDeckDestination.ExternalLaunch(shortcutData.deckId).toIntent() },
                ).setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                .setShortLabel(shortcutData.shortLabel)
                .setLongLabel(shortcutData.longLabel)
                .build()
        try {
            // success does not mean the user selected 'create', only that the feature is supported
            val success = ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
            Timber.i("Create shortcut for deck %d. Request displayed: %b", shortcutData.deckId, success)

            // User report: "success" is true even if Vivo does not have permission
            if (AdaptionUtil.isVivo) {
                showThemedToast(this, getString(CommonString.create_shortcut_error_vivo), false)
            }
            // #18601: MIUI gates shortcut creation behind Settings - Privacy Protection -
            // Other permissions - AnkiDroid - Home screen shortcuts [add shortcuts to Home screen]
            // TODO: Determine the result of 'success'
            if (AdaptionUtil.isMiui) {
                showThemedToast(this, getString(CommonString.create_shortcut_error_miui, getString(R.string.app_name)), false)
            }
            if (!success) {
                showThemedToast(this, getString(CommonString.create_shortcut_failed), false)
            }
        } catch (e: Exception) {
            Timber.w(e)
            showThemedToast(this, getString(CommonString.create_shortcut_error, e.localizedMessage), false)
        }
    }

    /** Disables the shortcut of the deck and the children belonging to it.*/
    @NeedsTest("ensure collapsed decks are also deleted")
    private fun disableDeckAndChildrenShortcuts(deckTreeDids: List<String>) {
        val errorMessage: CharSequence = getString(CommonString.deck_shortcut_doesnt_exist)
        ShortcutUtils.disableShortcuts(this, deckTreeDids, errorMessage)
    }

    fun renameDeckDialog(did: DeckId) {
        launchCatchingTask {
            val currentName = withCol { decks.name(did) }
            val createDeckDialog =
                CreateDeckDialog(
                    context = this@DeckPicker,
                    title = TR.sentenceCase.renameDeck,
                    deckDialogType = CreateDeckDialog.DeckDialogType.RENAME_DECK,
                    parentId = null,
                    renamedDeckId = did,
                )
            createDeckDialog.deckName = currentName
            createDeckDialog.onNewDeckCreated = {
                dismissAllDialogFragments()
                updateDeckList()
                tryShowStudyOptionsPanel()
            }
            createDeckDialog.showComposeDialog()
        }
    }

    /**
     * Displays a dialog for creating a new deck.
     *
     * @see CreateDeckDialog
     */
    fun showCreateDeckDialog() {
        val createDeckDialog =
            CreateDeckDialog(
                context = this@DeckPicker,
                title = TR.sentenceCase.createDeck,
                deckDialogType = CreateDeckDialog.DeckDialogType.DECK,
                parentId = null,
            )
        createDeckDialog.onNewDeckCreated = ::onDeckCreated
        createDeckDialog.showComposeDialog()
    }

    /**
     * Handles a deck created from the deck picker: selects it so the toolbar and, on tablets, the
     * [StudyOptionsFragment] panel follow the new deck instead of the previously current deck, which
     * is often the hidden Default deck.
     */
    private fun onDeckCreated(deckId: DeckId) =
        launchCatchingTask {
            viewModel.selectDeck(deckId).join()
            updateDeckList()
            tryShowStudyOptionsPanel()
            invalidateOptionsMenu()
        }

    /**
     * Deletes the provided deck, child decks, and all cards inside.
     * @param did ID of the deck to delete
     */
    fun deleteDeck(did: DeckId) =
        launchCatchingTask {
            viewModel.deleteDeck(did).join()
        }

    private fun deleteSelectedDeck() =
        launchCatchingTask {
            viewModel.deleteSelectedDeck().join()
        }

    private fun emptyFiltered(did: DeckId) {
        launchCatchingTask {
            viewModel.emptyFilteredDeck(did).join()
        }
    }

    override fun onAttachedToWindow() {
        if (!fragmented) {
            val window = window
            window.setFormat(PixelFormat.RGBA_8888)
        }
    }

    private fun openNextStudyAllDeck(afterFinishedDeckId: DeckId? = null) {
        if (showsStandaloneScreen || launchStudyAllJob?.isActive == true) return
        launchStudyAllJob =
            launchCatchingTask {
                val deckId =
                    withCol {
                        // The Engine includes each child Deck in the queue of its top-level parent.
                        val dueDecks = sched.deckDueTree().children.filter(DeckNode::hasCardsReadyToStudy)
                        // Let other eligible Decks go first if the completed Deck gained more work.
                        val next =
                            dueDecks.firstOrNull { it.did != afterFinishedDeckId }
                                ?: dueDecks.firstOrNull()
                                ?: return@withCol null
                        decks.select(next.did)
                        next.did
                    } ?: return@launchCatchingTask
                focusSelectedDeck(deckId)
                openReviewer(studyAll = true)
            }
    }

    private fun focusSelectedDeck(deckId: DeckId) {
        CardBrowser.clearLastDeckId()
        viewModel.focusedDeck = deckId
    }

    private fun openReviewer(studyAll: Boolean = false) {
        Timber.i("Opening Reviewer")
        val launcher = if (studyAll) studyAllReviewLauncher else reviewLauncher
        launcher.navigate(ReviewDeckDestination.CurrentDeck)
    }

    private fun createSubDeckDialog(did: DeckId) {
        val createDeckDialog =
            CreateDeckDialog(
                context = this@DeckPicker,
                title = getString(CommonString.create_subdeck),
                deckDialogType = CreateDeckDialog.DeckDialogType.SUB_DECK,
                parentId = did,
            )
        createDeckDialog.onNewDeckCreated = { deckId ->
            // a deck was created
            dismissAllDialogFragments()
            onDeckCreated(deckId)
        }
        createDeckDialog.showComposeDialog()
    }

    /**
     * The number of decks which are visible to the user (excluding decks if the parent is collapsed).
     * Not the total number of decks
     */
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    val visibleDeckCount: Int
        get() = displayedDecks.size

    /**
     * Check if at least one deck is being displayed.
     */
    fun hasAtLeastOneDeckBeingDisplayed(): Boolean =
        viewModel.flowOfDeckListScreen.value?.isEmptyCollection == false && displayedDecks.isNotEmpty() &&
            deckPickerBinding.deckList.isShown

    /** The Decks the deck list shows (Decks inside a collapsed Deck are left out) */
    @VisibleForTesting
    internal val displayedDecks: List<DisplayDeckNode>
        get() =
            viewModel.flowOfDeckListScreen.value
                ?.decks
                .orEmpty()

    private enum class DeckSelectionType {
        /** Show study options if fragmented, otherwise, review  */
        DEFAULT,

        /** Always show study options (if the deck counts are clicked)  */
        SHOW_STUDY_OPTIONS,

        /** Always open reviewer (keyboard shortcut)  */
        SKIP_STUDY_OPTIONS,
    }

    override val shortcuts
        get(): ShortcutGroup {
            fun bottomNavShortcut(
                keys: String,
                destination: NavigationItem,
            ) = if (bottomNavigationEnabled) Shortcut(keys, destination.shortcutLabel(this)) else null

            return ShortcutGroup(
                listOfNotNull(
                    shortcut("A", CommonString.menu_add_note),
                    bottomNavShortcut("Alt+1", NavigationItem.HOME),
                    bottomNavShortcut("Alt+2", NavigationItem.BROWSER),
                    bottomNavShortcut("Alt+3", NavigationItem.STATS),
                    bottomNavShortcut("Alt+4", NavigationItem.MORE),
                    shortcut("B", CommonString.card_browser_context_menu),
                    shortcut("Y", CommonString.pref_cat_sync),
                    shortcut("/", CommonString.deck_conf_cram_search),
                    shortcut("S", Translations::decksStudyDeck),
                    shortcut("T", CommonString.open_statistics),
                    shortcut("C") { this.sentenceCase.checkDatabase },
                    shortcut("D") { sentenceCase.createDeck },
                    shortcut("F", CommonString.new_dynamic_deck),
                    if (fragmented) shortcut("DEL") { this.sentenceCase.deleteDeck } else null,
                    if (fragmented) shortcut("Shift+DEL", CommonString.delete_deck_without_confirmation) else null,
                    if (fragmented) shortcut("R") { this.sentenceCase.renameDeck } else null,
                    shortcut("P", CommonString.open_settings),
                    shortcut("M") { this.sentenceCase.checkMediaAction },
                    shortcut("Ctrl+E", CommonString.export_collection),
                    shortcut("Ctrl+Shift+I", Translations::actionsImport),
                    shortcut("Ctrl+Shift+N", CommonString.model_browser_label),
                ),
                CommonString.deck_picker_group,
            )
        }

    companion object {
        /** The deck list draws today's summary and the column headings before the first Deck */
        private const val DECK_LIST_ITEMS_BEFORE_DECKS = 2

        /**
         * Result codes from other activities
         */
        const val RESULT_MEDIA_EJECTED = 202

        /**
         * If passed into the intent, the user should have been logged in and DeckPicker
         * should sync immediately.
         *
         * This is for downloading an existing collection from a configured sync server.
         */
        const val EXTRA_SYNC_FROM_LOGIN = "syncFromLogin"

        /** Opens the system picker for a collection or Deck package. */
        const val EXTRA_IMPORT = "importOnStart"

        /** Opens the self-hosted sync setup from the welcome screen. */
        internal const val EXTRA_SYNC_SETUP = "setupSyncOnStart"

        /**
         * Available options performed by other activities (request codes for onActivityResult())
         */
        @VisibleForTesting
        const val REQUEST_STORAGE_PERMISSION = 0

        private const val SWIPE_TO_SYNC_TRIGGER_DISTANCE = 400

        private const val PREF_DECK_PICKER_PANE_WEIGHT = "deckPickerPaneWeight"
        private const val PREF_STUDY_OPTIONS_PANE_WEIGHT = "studyOptionsPaneWeight"

        /**
         * Builds an intent for [DeckPicker]
         */
        internal const val EXTRA_ENTRY_SCREEN = "appNavigationEntryScreen"
        private const val STATE_PENDING_ENTRY = "pendingEntryScreen"
        private const val STATE_SELECTED_TAB = "selectedNavigationTab"
        private const val ACTION_DEFERRED_IMPORT = "com.ichi2.anki.DEFERRED_IMPORT"
        private const val EXTRA_IMPORT_SOURCE = "deferredImportSource"

        internal fun getDeferredImportIntent(
            context: Context,
            source: Intent,
        ): Intent =
            getEntryIntent(
                context,
                Intent(ACTION_DEFERRED_IMPORT).putExtra(EXTRA_IMPORT_SOURCE, Intent(source)),
                source,
            )

        private const val STATE_WAIT_FOR_WELCOME_ACTION = "waitForWelcomeAction"

        internal fun getEntryIntent(
            context: Context,
            screen: Intent,
            source: Intent? = null,
            resetTask: Boolean = false,
        ): Intent =
            getIntent(context).apply {
                val payload =
                    Intent(screen).apply {
                        if (source != null && data == null && type == null) setDataAndType(source.data, source.type)
                    }
                putExtra(EXTRA_ENTRY_SCREEN, payload)
                addFlags(
                    if (resetTask) {
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    } else {
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                )
                if (source != null) {
                    val uri = source.data ?: IntentCompat.getParcelableExtra(source, Intent.EXTRA_STREAM, Uri::class.java)
                    clipData =
                        source.clipData ?: uri?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }?.let { ClipData.newRawUri(null, it) }
                    val uriFlags =
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    addFlags(source.flags and uriFlags)
                }
            }

        fun getIntent(
            context: Context,
            autoSync: Boolean = false,
        ) = Intent(context, DeckPicker::class.java).apply {
            if (autoSync) {
                putExtra(EXTRA_SYNC_FROM_LOGIN, true)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        deferEntryIntent(intent)
        if (intent.hasExtra(EXTRA_SYNC_FROM_LOGIN)) {
            Timber.i("Sync requested from Login")
            this.syncOnResume = true
        }
    }

    private fun deferEntryIntent(intent: Intent) {
        val screen = IntentCompat.getParcelableExtra(intent, EXTRA_ENTRY_SCREEN, Intent::class.java) ?: return
        intent.removeExtra(EXTRA_ENTRY_SCREEN)
        pendingEntry = screen
        openPendingEntry()
    }

    private fun afterWelcomeAction() {
        if (!waitForWelcomeAction) return
        waitForWelcomeAction = false
        openPendingEntry()
    }

    private fun openPendingEntry() {
        if (!collectionReadyForEntry || awaitingWelcome || waitForWelcomeAction) return
        val screen = pendingEntry ?: return
        pendingEntry = null
        if (screen.action == ACTION_DEFERRED_IMPORT) {
            val source = IntentCompat.getParcelableExtra(screen, EXTRA_IMPORT_SOURCE, Intent::class.java) ?: return
            onSelectedFileToImport(source)
        } else {
            startActivity(screen)
        }
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        lifecycleScope.launch { viewModel.refreshMenuState() }
        if (changes.studyQueues && handler !== this && handler !== viewModel) {
            if (!activityPaused) {
                // No need to update while the activity is paused, because `onResume` calls `refreshState` that calls `updateDeckList`.
                updateDeckList()
            }
        }
    }

    override fun onImportColpkg(colpkgPath: String?) {
        launchCatchingTask {
            // as the current collection is closed before importing a new collection, make sure the
            // new collection is open before the code to update the DeckPicker ui runs
            withCol { }
            invalidateOptionsMenu()
            updateDeckList()
            importColpkgListener?.onImportColpkg(colpkgPath)
        }
    }

    override fun getApkgFileImportResultLauncher(): ActivityResultLauncher<Intent> = apkgFileImportResultLauncher

    override fun getCsvFileImportResultLauncher(): ActivityResultLauncher<Intent> = csvImportResultLauncher
}

/** Android's onCreateOptionsMenu does not play well with coroutines, as
 * it expects the menu to have been fully configured by the time the routine
 * returns. This results in flicker, as the menu gets blanked out, and then
 * configured a moment later when the coroutine runs. To work around this,
 * the current state is stored in the deck picker so that we can redraw the
 * menu immediately. */

class CollectionLoadingErrorDialog :
    DialogHandlerMessage(
        WhichDialogHandler.MSG_SHOW_COLLECTION_LOADING_ERROR_DIALOG,
        "CollectionLoadErrorDialog",
    ) {
    override fun handleAsyncMessage(activity: AnkiActivity) {
        // Collection could not be opened
        activity.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED)
    }

    override fun toMessage() = emptyMessage(this.what)
}

internal val DeckPickerLayout.studyoptionsFrame: FragmentContainerView?
    get() = studyoptionsFragment

private fun Context.progressMessage(progress: DeckPickerProgress): String =
    when (progress) {
        DeckPickerProgress.DELETING_DECK -> getString(CommonString.delete_deck)
        DeckPickerProgress.REBUILDING_FILTERED_DECK -> getString(CommonString.rebuild_filtered_deck)
        DeckPickerProgress.DELETING_EMPTY_CARDS -> TR.emptyCardsDeleting()
        DeckPickerProgress.CREATING_BACKUP -> TR.sentenceCase.creatingBackup
    }
