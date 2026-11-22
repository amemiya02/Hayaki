// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.decks.SetDeckCollapsedRequest
import com.ichi2.anki.CommonString
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.SECONDS_PER_DAY
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.setIntroductionSlidesShown
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import com.ichi2.anki.withDeckPicker
import com.ichi2.testutils.BackupManagerTestUtilities
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyDecksTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @After
    override fun tearDown() {
        super.tearDown()
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `today's summary counts a subdeck once and shows empty queues`() {
        setTimeToStartOfStudyDay()
        val nested = addDeck("Japanese::N3")
        col.setDeck(listOf(addBasicNote("Vocabulary").firstCard().id), nested)
        addBasicNote("Anatomy")
        addRevBasicNoteDueToday("Geography", "Answer")

        withDeckPicker(deckCount = 0) {
            awaitText("3 cards to study today")
            composeRule.onNodeWithText("3 cards to study today").performScrollTo().assertIsDisplayed()
            awaitText("0 studied so far")
            composeRule.onNodeWithText("0 studied so far").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithContentDescription("New: 2, Learning: 0, Due: 1").assertIsDisplayed()
        }
    }

    @Test
    fun `a populated collection can be clear for today`() {
        val card = addBasicNote().firstCard()
        col.sched.suspendCards(listOf(card.id))

        withDeckPicker(deckCount = 0) {
            composeRule.onNodeWithText("Nothing left to study today").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("New: 0, Learning: 0, Due: 0").assertIsDisplayed()
            composeRule.onNodeWithText("No decks yet").assertDoesNotExist()
        }
    }

    @Test
    fun `studying the current Deck under a collapsed parent finishes there and refreshes Decks`() {
        // Keep review timestamps on the Engine's current study day.
        setTimeToStartOfStudyDay()
        val nested = addDeck("Japanese::N3")
        col.setDeck(listOf(addBasicNote("Vocabulary").firstCard().id), nested)
        addBasicNote("Anatomy")
        col.decks.select(nested)
        col.decks.setCollapsed(col.decks.id("Japanese"), collapsed = true, SetDeckCollapsedRequest.Scope.REVIEWER)

        withDeckPicker(deckCount = 0) { deckPicker ->
            composeRule.onNodeWithText("N3").assertDoesNotExist()
            tap("Study · N3")
            reviewerPageLoaded(deckPicker)
            tap(deckPicker.getString(CommonString.show_answer))
            tapEasy(deckPicker)
            awaitText("Easy 1")
            composeRule.onNodeWithText("Reviewed").assertIsDisplayed()
            tap(deckPicker.getString(CommonString.finished_done))

            awaitText("1 studied so far")
            composeRule.onNodeWithText("1 card to study today").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithContentDescription("New: 1, Learning: 0, Due: 0").assertIsDisplayed()
            composeRule.onNodeWithText("Study · N3").assertDoesNotExist()
        }
    }

    @Test
    fun `Study All reviews each top-level Deck including its children and advances after Finished closes`() {
        setTimeToStartOfStudyDay()
        val geography = addDeck("Geography")
        val japanese = addDeck("Languages::Japanese")
        col.setDeck(listOf(addBasicNote("Capitals").firstCard().id), geography)
        col.setDeck(listOf(addBasicNote("Vocabulary").firstCard().id), japanese)

        withDeckPicker(deckCount = 0) { deckPicker ->
            val showAnswer = deckPicker.getString(CommonString.show_answer)
            tap("Study All")
            reviewerPageLoaded(deckPicker)
            awaitText("Geography")
            awaitText(showAnswer)
            composeRule.onNodeWithText("Geography").assertIsDisplayed()
            tap(showAnswer)
            tapEasy(deckPicker)
            awaitText("Done")
            composeRule.onNodeWithText("Geography").assertIsDisplayed()
            composeRule.onNodeWithText(showAnswer).assertDoesNotExist()
            tap("Done")

            reviewerPageLoaded(deckPicker)
            awaitText("Languages › Japanese")
            tap(showAnswer)
            tapEasy(deckPicker)
            awaitText("Done")
            composeRule.onNodeWithText("Languages").assertIsDisplayed()
            tap("Done")

            awaitText("2 studied so far")
            awaitText("Nothing left to study today")
            composeRule.onNodeWithText("Study All").assertDoesNotExist()
        }
    }

    @Test
    fun `Study All stops if the learner backs out of a running review`() {
        addBasicNote("Geography")
        val languages = addDeck("Languages")
        col.setDeck(listOf(addBasicNote("Vocabulary").firstCard().id), languages)

        withDeckPicker(deckCount = 0) { deckPicker ->
            tap("Study All")
            awaitText(deckPicker.getString(CommonString.show_answer))
            composeRule
                .onNodeWithContentDescription(deckPicker.getString(androidx.appcompat.R.string.abc_action_bar_up_description))
                .performClick()

            awaitText("2 cards to study today")
            awaitText("0 studied so far")
            composeRule.onNodeWithText("0 studied so far").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Study All").assertIsDisplayed()
        }
    }

    @Test
    fun `Study All restores review and completion and skips a Deck that became ineligible`() {
        setTimeToStartOfStudyDay()
        val geography = addDeck("Geography")
        val history = addDeck("History")
        val languages = addDeck("Languages")
        col.setDeck(listOf(addBasicNote("Capitals").firstCard().id), geography)
        val historyCard = addBasicNote("Timeline").firstCard()
        col.setDeck(listOf(historyCard.id), history)
        col.setDeck(listOf(addBasicNote("Vocabulary").firstCard().id), languages)

        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        editPreferences { putBoolean("backupPromptDisabled", true) }
        val activity = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        val showAnswer = activity.get().getString(CommonString.show_answer)
        tap("Study All")
        awaitText(showAnswer)
        activity.recreate()
        advanceRobolectricLooper()
        reviewerPageLoaded(activity.get(), recreated = true)
        awaitText("Geography")
        tap(showAnswer)
        tapEasy(activity.get())
        awaitText("Done")
        activity.recreate()
        advanceRobolectricLooper()
        awaitText("Done")

        col.sched.suspendCards(listOf(historyCard.id))
        activity.get().onBackPressedDispatcher.onBackPressed()
        reviewerPageLoaded(activity.get())
        awaitText("Languages")
        awaitText(showAnswer)
        composeRule.onNodeWithText("Languages").assertIsDisplayed()
        composeRule.onNodeWithText("History").assertDoesNotExist()
    }

    // Robolectric does not run WebView JavaScript or dispatch its page-loaded callback.
    private fun reviewerPageLoaded(
        activity: DeckPicker,
        recreated: Boolean = false,
    ) {
        composeRule.waitUntil(10_000) {
            advanceRobolectricLooper()
            activity.currentScreenFragment is ReviewerFragment
        }
        (activity.currentScreenFragment as ReviewerFragment).viewModel.onPageFinished(recreated)
        advanceRobolectricLooper()
    }

    private fun setTimeToStartOfStudyDay() {
        TimeManager.resetWith(MockTime((col.sched.dayCutoff - SECONDS_PER_DAY + 60) * 1000, step = 10))
    }

    private fun awaitText(text: String) =
        advanceRobolectricLooperUntil(lazyMessage = {
            val visible =
                composeRule
                    .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
                    .joinToString()
            "Expected '$text' in: $visible"
        }) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

    private fun tapEasy(activity: DeckPicker) {
        // The installed button combines the next interval and the rating into a single label.
        val label = "\n" + activity.getString(CommonString.ease_button_easy)
        composeRule.mainClock.advanceTimeBy(Prefs.doubleTapInterval.toLong())
        val button = composeRule.onNodeWithText(label, substring = true)
        composeRule.waitUntil(10_000) {
            advanceRobolectricLooper()
            button.isDisplayed()
        }
        button.performClick()
    }

    private fun tap(text: String) {
        awaitText(text)
        composeRule.onNodeWithText(text).performClick()
    }
}
