// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.common.android.Animations
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.sched.Counts
import com.ichi2.anki.libanki.sched.Counts.Queue
import com.ichi2.anki.ui.color
import com.ichi2.anki.ui.label
import com.ichi2.compose.theme.foundation
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import com.ichi2.anki.common.android.R as CommonR

/** What a person can do on the deck list ([DeckListScreen]) */
interface DeckListActions {
    fun onStudyAll()

    /** Study the current Deck directly from the daily summary. */
    fun onStudyCurrentDeck(deckId: DeckId)

    /** Study the Deck, or open the finished screen when nothing is left in it */
    fun onDeckClick(deckId: DeckId)

    /** The counts of a Deck were tapped: its study options */
    fun onDeckCountsClick(deckId: DeckId)

    /** The Deck's menu: rename, options, export, delete... */
    fun onDeckLongClick(deckId: DeckId)

    /** The Deck's menu at a mouse position, in pixels from the deck list's top-left corner */
    fun onDeckRightClick(
        deckId: DeckId,
        x: Float,
        y: Float,
    )

    fun onToggleExpand(deckId: DeckId)

    fun onGetSharedDecks()

    fun onImport()

    fun onCreateDeck()
}

/**
 * The deck list in the Material 3 Direction (ADR-0006): today's summary, then the Deck tree, each
 * top-level Deck and its open sub-Decks in one surface container, with three columns of counts
 * (new, learning, due) told apart by colour. A Newcomer without Decks is told what to do first.
 */
@Composable
fun DeckListScreen(
    state: DeckListScreenState,
    actions: DeckListActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    /** Opacity of the deck rows, below 1 when a background image should show through them. */
    rowOpacity: Float = 1f,
) {
    val context = LocalContext.current
    var animate by remember(context) { mutableStateOf(Animations.areAnimationsEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        animate = Animations.areAnimationsEnabled(context)
    }
    val root = modifier.semantics { testTagsAsResourceId = true }
    if (state.isEmptyCollection) {
        EmptyDeckList(
            actions = actions,
            contentPadding = contentPadding,
            modifier = root.testTag("${LocalContext.current.packageName}:id/no_decks_placeholder"),
        )
    } else {
        Decks(
            state = state,
            actions = actions,
            listState = listState,
            contentPadding = contentPadding,
            animate = animate,
            rowOpacity = rowOpacity,
            modifier = root,
        )
    }
}

/** The deck list's title: "Decks" with today's date under it. Shown in the top bar on phones. */
@Composable
fun DeckListTitle(
    date: Date,
    modifier: Modifier = Modifier,
) {
    val foundation = MaterialTheme.foundation
    val configuration = LocalConfiguration.current
    val dateText =
        remember(date, configuration) {
            val locale = ConfigurationCompat.getLocales(configuration)[0] ?: java.util.Locale.getDefault()
            SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale).format(date)
        }
    Column(modifier = modifier.padding(top = 6.dp, bottom = 10.dp).semantics(mergeDescendants = true) { heading() }) {
        Text(text = TR.actionsDecks(), style = foundation.type.screenTitle, color = foundation.colors.onBackground)
        Text(text = dateText, style = foundation.type.caption, color = foundation.colors.onBackgroundMuted)
    }
}

@Composable
private fun Decks(
    state: DeckListScreenState,
    actions: DeckListActions,
    listState: LazyListState,
    contentPadding: PaddingValues,
    animate: Boolean,
    rowOpacity: Float,
    modifier: Modifier,
) {
    val foundation = MaterialTheme.foundation
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val deepestLevel = remember(state.decks) { state.decks.maxOfOrNull { it.depth }?.coerceAtLeast(1) ?: 1 }
    val preferredNameWidth = with(density) { (foundation.type.deckName.fontSize * PREFERRED_NAME_EMS).toDp() }
    val minimumNameWidth = with(density) { (foundation.type.deckName.fontSize * MINIMUM_NAME_EMS).toDp() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val horizontalPadding = contentPadding.calculateLeftPadding(layoutDirection) + contentPadding.calculateRightPadding(layoutDirection)
        val contentWidth = (maxWidth - horizontalPadding - foundation.spacing.screenMargin * 2 - ROW_END_PADDING).coerceAtLeast(0.dp)
        val columnWidths = countColumnWidths(state.decks, contentWidth)
        val countsWidth = columnWidths.reduce(Dp::plus)
        val nameWidth = contentWidth - foundation.spacing.touchTarget - NAME_END_PADDING
        // A deep tree indents less, so its deepest names keep a readable width beside their counts
        val indentPerLevel = ((nameWidth - countsWidth - preferredNameWidth) / deepestLevel).coerceIn(MIN_NESTED_INDENT, NESTED_INDENT)
        LazyColumn(
            state = listState,
            // hands scrolling to the bottom bar's HideViewOnScrollBehavior
            modifier = Modifier.fillMaxSize().nestedScroll(rememberNestedScrollInteropConnection()),
            contentPadding = contentPadding,
        ) {
            item(key = "summary") {
                TodaySummary(
                    dueToday = state.dueToday,
                    queuesToday = state.queuesToday,
                    studiedToday = state.studiedToday,
                    current = state.currentStudyDeck,
                    rowOpacity = rowOpacity,
                    onStudyAll = actions::onStudyAll,
                    onStudyCurrent = actions::onStudyCurrentDeck,
                )
            }
            itemsIndexed(state.decks, key = { _, deck -> deck.did }) { index, deck ->
                val next = state.decks.getOrNull(index + 1)
                val indent = indentPerLevel * deck.depth
                val inlineNameWidth = nameWidth - indent - countsWidth
                val nameStyle = if (deck.depth == 0) foundation.type.deckName else foundation.type.deckNameNested
                // The counts stay beside the name. Only when even the least indent leaves no readable
                // width (very large text on a narrow screen) do they move under a name that does not fit.
                val countsInline =
                    inlineNameWidth >= minimumNameWidth ||
                        (
                            inlineNameWidth > 0.dp &&
                                with(density) {
                                    val singleLineName = measurer.measure(deck.lastDeckNameComponent, nameStyle, maxLines = 1)
                                    singleLineName.size.width.toDp() <= inlineNameWidth
                                }
                        )
                DeckRow(
                    deck = deck,
                    columnWidths = columnWidths,
                    countsInline = countsInline,
                    indent = indent,
                    actions = actions,
                    animate = animate,
                    rowOpacity = rowOpacity,
                    position =
                        GroupPosition(
                            isFirst = deck.depth == 0,
                            isLast = next == null || next.depth == 0,
                            isFirstGroup = index == 0,
                        ),
                    modifier =
                        if (animate) {
                            Modifier.animateItem(
                                fadeInSpec = tween(EXPAND_MILLIS),
                                placementSpec = tween(EXPAND_MILLIS),
                                fadeOutSpec = tween(EXPAND_MILLIS),
                            )
                        } else {
                            Modifier
                        },
                )
            }
            item(key = "end") { Spacer(Modifier.height(GROUP_GAP)) }
        }
    }
}

/**
 * Today at a glance, laid out as on iOS: "58 cards to study today" over "9 studied so far", the new,
 * learning and due totals, then buttons to study all or the current Deck. It sits on the same
 * surface as the Deck rows, so a background image shows through it as much as through them.
 */
@Composable
private fun TodaySummary(
    dueToday: Int,
    queuesToday: Counts,
    studiedToday: Int,
    current: DisplayDeckNode?,
    rowOpacity: Float,
    onStudyAll: () -> Unit,
    onStudyCurrent: (DeckId) -> Unit,
) {
    val foundation = MaterialTheme.foundation
    val margin = foundation.spacing.screenMargin
    val nothingDue = dueToday == 0
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = margin, end = margin, top = 4.dp, bottom = 16.dp)
                .clip(RoundedCornerShape(GROUP_CORNER))
                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = rowOpacity))
                .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text =
                    if (nothingDue) {
                        stringResource(CommonR.string.deck_list_nothing_due)
                    } else {
                        pluralStringResource(CommonR.plurals.deck_list_due_today, dueToday, dueToday)
                    },
                style = foundation.type.summary,
                color = foundation.colors.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = pluralStringResource(CommonR.plurals.deck_list_studied_today, studiedToday, studiedToday),
                style = foundation.type.body,
                color = foundation.colors.onBackgroundMuted,
            )
        }
        TodayQueueCounts(queuesToday)
        if (!nothingDue) {
            Button(
                onClick = onStudyAll,
                modifier = Modifier.fillMaxWidth().heightIn(min = foundation.spacing.primaryButton),
                shape = foundation.shapes.button,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(stringResource(CommonR.string.deck_list_study_all), style = foundation.type.button)
            }
            if (current != null) {
                FilledTonalButton(
                    onClick = { onStudyCurrent(current.did) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = foundation.spacing.primaryButton),
                    shape = foundation.shapes.button,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(CommonR.string.studyoptions_start) + " · " + current.lastDeckNameComponent,
                        style = foundation.type.button,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Today's new, learning and due totals, each a large number in its queue's colour over its name.
 * At large text sizes they run inline ("60 New") and wrap, as on iOS.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayQueueCounts(counts: Counts) {
    val foundation = MaterialTheme.foundation
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: java.util.Locale.getDefault()
    val format = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val totals =
        Queue.entries.map { queue ->
            val count =
                when (queue) {
                    Queue.NEW -> counts.new
                    Queue.LRN -> counts.lrn
                    Queue.REV -> counts.rev
                }
            Triple(queue, count, if (count == 0) foundation.colors.onBackgroundMuted else queue.color(foundation.colors))
        }
    val description = totals.map { (queue, count) -> stringResource(CommonR.string.deck_list_count_description, queue.label(), count) }
    val semantics = Modifier.testTag(TODAY_COUNTS_TAG).clearAndSetSemantics { contentDescription = description.joinToString() }
    if (LocalDensity.current.fontScale >= LARGE_TEXT_SCALE) {
        FlowRow(
            modifier = semantics,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            totals.forEach { (queue, count, colour) ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(format.format(count), style = foundation.type.studyTitle, color = colour)
                    Text(queue.label(), style = foundation.type.body, color = foundation.colors.onBackgroundMuted)
                }
            }
        }
    } else {
        Row(modifier = semantics.fillMaxWidth()) {
            totals.forEach { (queue, count, colour) ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = format.format(count),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colour,
                        maxLines = 1,
                    )
                    Text(queue.label(), style = foundation.type.caption, color = foundation.colors.onBackgroundMuted)
                }
            }
        }
    }
}

/** Where a row sits in its Deck's container, which rounds its outer corners */
private data class GroupPosition(
    val isFirst: Boolean,
    val isLast: Boolean,
    val isFirstGroup: Boolean,
)

@Composable
private fun DeckRow(
    deck: DisplayDeckNode,
    columnWidths: List<Dp>,
    countsInline: Boolean,
    indent: Dp,
    actions: DeckListActions,
    animate: Boolean,
    rowOpacity: Float,
    position: GroupPosition,
    modifier: Modifier = Modifier,
) {
    val foundation = MaterialTheme.foundation
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val corner = GROUP_CORNER
    val shape =
        RoundedCornerShape(
            topStart = if (position.isFirst) corner else 0.dp,
            topEnd = if (position.isFirst) corner else 0.dp,
            bottomStart = if (position.isLast) corner else 0.dp,
            bottomEnd = if (position.isLast) corner else 0.dp,
        )
    val onCountsClick = { actions.onDeckCountsClick(deck.did) }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = foundation.spacing.screenMargin)
                .padding(top = if (position.isFirst && !position.isFirstGroup) GROUP_GAP else 0.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = rowOpacity))
                .heightIn(min = if (deck.depth == 0) foundation.spacing.deckRow else foundation.spacing.deckRowNested)
                .testTag("${LocalContext.current.packageName}:id/deck_name")
                .onGloballyPositioned { coordinates = it }
                .pointerInput(deck.did) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Press || !event.buttons.isSecondaryPressed) continue
                            val change = event.changes.first()
                            val inList = coordinates?.localToRoot(change.position) ?: change.position
                            change.consume()
                            actions.onDeckRightClick(deck.did, inList.x, inList.y)
                        }
                    }
                }.combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = stringResource(CommonR.string.deck_list_deck_actions),
                    onLongClick = { actions.onDeckLongClick(deck.did) },
                    onClick = { actions.onDeckClick(deck.did) },
                ).semantics { if (deck.isSelected) selected = true }
                .padding(end = ROW_END_PADDING),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.End,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = indent),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeckMarker(deck = deck, animate = animate, onToggle = { actions.onToggleExpand(deck.did) })
            Text(
                text = deck.lastDeckNameComponent,
                style = if (deck.depth == 0) foundation.type.deckName else foundation.type.deckNameNested,
                color = if (deck.filtered || deck.isSelected) foundation.colors.emphasis else foundation.colors.onBackground,
                modifier = Modifier.weight(1f).padding(top = 8.dp, bottom = 8.dp, end = NAME_END_PADDING),
            )
            if (countsInline) DeckCounts(deck, columnWidths, onCountsClick)
        }
        if (!countsInline) DeckCounts(deck, columnWidths, onCountsClick)
    }
}

/** The chevron of a Deck with sub-Decks, turning as it opens; a dot marks the current Deck */
@Composable
private fun DeckMarker(
    deck: DisplayDeckNode,
    animate: Boolean,
    onToggle: () -> Unit,
) {
    val foundation = MaterialTheme.foundation
    val toggle =
        if (deck.canCollapse) {
            val label = stringResource(if (deck.collapsed) CommonR.string.expand else CommonR.string.collapse)
            Modifier.clickable(role = Role.Button, onClick = onToggle).semantics { contentDescription = label }
        } else {
            Modifier
        }
    // the chevron points along the reading direction when closed, and down when open
    val open = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -90f else 90f
    val target = if (deck.collapsed) 0f else open
    val turn =
        if (animate) {
            val animatedTurn by animateFloatAsState(targetValue = target, animationSpec = tween(EXPAND_MILLIS), label = "deck chevron")
            animatedTurn
        } else {
            target
        }
    Box(
        modifier = Modifier.size(foundation.spacing.touchTarget).then(toggle),
        contentAlignment = Alignment.Center,
    ) {
        when {
            deck.canCollapse ->
                Icon(
                    painter = painterResource(CommonR.drawable.symbol_chevron_right),
                    contentDescription = null,
                    tint = if (deck.isSelected) foundation.colors.emphasis else foundation.colors.onBackgroundMuted,
                    modifier = Modifier.size(24.dp).rotate(turn),
                )
            deck.isSelected ->
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(foundation.colors.emphasis))
        }
    }
}

@Composable
private fun DeckCounts(
    deck: DisplayDeckNode,
    columnWidths: List<Dp>,
    onClick: () -> Unit,
) {
    val foundation = MaterialTheme.foundation
    val clickable =
        Modifier
            .heightIn(min = foundation.spacing.touchTarget)
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(CommonR.string.menu__study_options),
                onClick = onClick,
            )
    if (!deck.hasCardsLeft) {
        Row(
            modifier = clickable.width(columnWidths.reduce(Dp::plus)).semantics(mergeDescendants = true) { },
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.symbol_check_circle),
                contentDescription = null,
                tint = foundation.colors.countDue,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(CommonR.string.finished_title_done),
                style = foundation.type.caption,
                color = foundation.colors.onBackgroundMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    Row(modifier = clickable, verticalAlignment = Alignment.CenterVertically) {
        Queue.entries.forEachIndexed { index, queue ->
            val count = deck.count(queue)
            val width = Modifier.width(columnWidths[index])
            if (count == 0) {
                Spacer(width)
            } else {
                val description = stringResource(CommonR.string.deck_list_count_description, queue.label(), count)
                Box(
                    modifier = width.clearAndSetSemantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    Count(count, queue.color(foundation.colors))
                }
            }
        }
    }
}

/** A nonzero count on a tint of its column's colour */
@Composable
private fun Count(
    count: Int,
    colour: Color,
) {
    val foundation = MaterialTheme.foundation
    Text(
        text = count.toString(),
        style = foundation.type.count,
        color = colour,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                // Center the text within the pill's measured width.
                .width(IntrinsicSize.Max)
                .clip(CircleShape)
                .background(colour.copy(alpha = COUNT_TINT))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Share each queue's column width across Decks. */
@Composable
private fun countColumnWidths(
    decks: List<DisplayDeckNode>,
    availableWidth: Dp,
): List<Dp> {
    val foundation = MaterialTheme.foundation
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(decks, availableWidth, foundation.type, foundation.spacing.countColumn, density, measurer) {
        val widths =
            Queue.entries.map { queue ->
                val count = decks.maxOfOrNull { it.count(queue) } ?: 0
                with(density) {
                    val countText = measurer.measure(count.toString(), foundation.type.count)
                    maxOf(foundation.spacing.countColumn, countText.size.width.toDp() + COUNT_SIDE_ROOM)
                }
            }
        val scale = (availableWidth / widths.reduce(Dp::plus)).coerceAtMost(1f)
        widths.map { it * scale }
    }
}

/** The deck list for a Newcomer: get shared Decks first, or import a file, or create a Deck */
@Composable
private fun EmptyDeckList(
    actions: DeckListActions,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    val foundation = MaterialTheme.foundation
    val margin = foundation.spacing.screenMargin
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .nestedScroll(rememberNestedScrollInteropConnection())
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(start = margin, end = margin, top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(R.mipmap.hayaki_mark),
            contentDescription = null,
            modifier = Modifier.size(foundation.spacing.emptyMark),
        )
        Text(
            text = stringResource(CommonR.string.deck_list_empty_title),
            style = foundation.type.pageTitle,
            color = foundation.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Button(
            onClick = actions::onGetSharedDecks,
            modifier = Modifier.fillMaxWidth().heightIn(min = foundation.spacing.primaryButton),
            shape = foundation.shapes.button,
            colors = ButtonDefaults.buttonColors(containerColor = foundation.colors.primary, contentColor = foundation.colors.onPrimary),
            elevation = null,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.symbol_download),
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp).size(20.dp),
            )
            Text(stringResource(CommonR.string.menu_get_shared_decks), style = foundation.type.button, textAlign = TextAlign.Center)
        }
        Column(
            modifier =
                Modifier
                    .padding(bottom = 16.dp)
                    .clip(foundation.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            EmptyListRow(
                icon = R.drawable.symbol_file_open,
                title = stringResource(CommonR.string.deck_list_import_file),
                onClick = actions::onImport,
            )
            EmptyListRow(
                icon = R.drawable.symbol_create_new_folder,
                title = stringResource(CommonR.string.deck_list_create_deck),
                onClick = actions::onCreateDeck,
            )
        }
    }
}

@Composable
private fun EmptyListRow(
    @DrawableRes icon: Int,
    title: String,
    onClick: () -> Unit,
) {
    val foundation = MaterialTheme.foundation
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = foundation.spacing.listRow)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = foundation.colors.onBackgroundMuted,
            modifier = Modifier.size(24.dp),
        )
        Text(title, Modifier.weight(1f), style = foundation.type.rowTitle, color = foundation.colors.onBackground)
        Icon(
            painter = painterResource(CommonR.drawable.symbol_chevron_right),
            contentDescription = null,
            tint = foundation.colors.onBackgroundMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** How far a sub-Deck is indented under its parent; a deep tree on a narrow screen indents less */
private val NESTED_INDENT = 24.dp
private val MIN_NESTED_INDENT = 8.dp

/** The width, in ems of the Deck name, the deepest Deck's name keeps beside its counts */
private const val PREFERRED_NAME_EMS = 5

/** Narrower than this, in ems, a name that does not fit on one line takes the row above its counts */
private const val MINIMUM_NAME_EMS = 3

/** The space between Deck containers, and their corners */
private val GROUP_GAP = 8.dp
private val GROUP_CORNER = 16.dp

/** Room between the last count and the container's edge */
private val ROW_END_PADDING = 8.dp

/** Room after a Deck name */
private val NAME_END_PADDING = 8.dp

/** Marks today's totals in the summary, apart from the Decks' own counts */
internal const val TODAY_COUNTS_TAG = "today_counts"

/** From this text scale up, today's totals run inline and wrap */
private const val LARGE_TEXT_SCALE = 1.3f

/** Combined horizontal space on either side of the text in a count column */
private val COUNT_SIDE_ROOM = 20.dp

/** The tint behind a count: its own colour, faint enough that the count still reads at AA */
private const val COUNT_TINT = 0.10f

/** Opening or closing a Deck: the chevron turns and the sub-Decks settle in this time */
private const val EXPAND_MILLIS = 200

/** Whether any of the Deck's new, learning or due Cards are left today */
private val DisplayDeckNode.hasCardsLeft: Boolean
    get() = newCount > 0 || lrnCount > 0 || revCount > 0
