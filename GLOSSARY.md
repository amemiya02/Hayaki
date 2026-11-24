# AnkiDroid Redesign

The language used while redesigning the AnkiDroid app. It covers the kinds of change the redesign makes and the Anki concepts the interface must keep naming the way Anki does.

## Language

### Kinds of change

**Redesign**:
The whole effort: a Reskin, Restructure and Reflow of the app, taken together.
_Avoid_: Overhaul, revamp, refresh

**Reskin**:
A change to how a screen looks (typeface, colour, spacing, shape, iconography, arrangement) that leaves what the screen contains and how it is reached unchanged. The list is open-ended.
_Avoid_: Restyle, facelift, theme change

**Restructure**:
A change to where things live in the app: its navigation and the grouping of screens and settings.
_Avoid_: Reorganise, IA change

**Reflow**:
A change to the steps a person takes to complete a task within a screen or across screens.
_Avoid_: Flow change, UX change

### What the Redesign touches

**Native Surface**:
Any part of the app drawn by the app itself: a screen, dialog, settings page, home-screen widget or notification.
_Avoid_: View, UI element, component

**Anki Page**:
A screen that is a web page shared with desktop Anki, such as statistics or deck options, rather than a Native Surface.
_Avoid_: Web screen, Svelte page, backend page

**Harmonisation**:
Matching an Anki Page's colours and typeface to the Redesign without changing its content or arrangement.
_Avoid_: Restyle, theming

**Engine**:
The part of the app that stores the collection, schedules Cards and syncs it with AnkiWeb or a self-hosted sync server. It is Upstream's code plus one patch, the opt-in RWKV review queue ([ADR-0009](docs/adr/0009-opt-in-rwkv-scheduling.md)). Sync and storage stay identical to Upstream.
_Avoid_: Backend, core, libanki

**Card Content**:
What a Card Template renders for a Card: the question and answer as their author styled them.
_Avoid_: Card body, card HTML

**Card Frame**:
Everything on the study screen that surrounds the Card Content: bars, counts, answer buttons and menus.
_Avoid_: Chrome, reviewer UI

**Core Loop**:
The path a person follows every day: choose a Deck, study its Cards, reach the finished screen.
_Avoid_: Main flow, happy path, study flow

### How the Redesign proceeds

**Upstream**:
The AnkiDroid project this work is derived from, and its source as it continues to change.
_Avoid_: Original, mainline, AnkiDroid proper

**Fork**:
Our own copy of Upstream that carries the Redesign, with its own name, icon and app ID.
_Avoid_: Our version, the new app, the clone

**Hayaki**:
The offline study app for Anki collections on Android, iPhone and iPad. Its name is written the same way in every language.
_Avoid_: AnkiDroid Redesign (the working name for development builds only), AnkiDroid

**Official App**:
AnkiDroid as Upstream publishes it, which can be installed beside the Fork.
_Avoid_: Stock app, real AnkiDroid

**Direction**:
One candidate look for the Redesign, shown as a Mockup so it can be compared with the others and chosen by eye.
_Avoid_: Style, theme, concept

**Foundation**:
The app-wide base of the chosen Direction (colour, type, spacing and shape) that every Native Surface inherits.
_Avoid_: Design system, tokens, theme

**Theme**:
The learner's choice of accent colours and, for Paper and Midnight, the default study background.
_Avoid_: Skin, colour mode

**Appearance**:
The learner's choice of System, Light, Dark or Black, applied to their chosen Theme.
_Avoid_: Theme when referring to light or dark mode

**Mockup**:
A picture of a Native Surface as it is intended to look, agreed before that surface is built.
_Avoid_: Prototype, wireframe, design

**Milestone**:
A group of Native Surfaces redesigned together and running on a device before the next group starts.
_Avoid_: Phase, sprint, release

**Quality Bar**:
The set of checks a redesigned Native Surface must pass to count as done.
_Avoid_: Acceptance criteria, definition of done

### People

**Daily Reviewer**:
A person who already has Decks and studies them most days.
_Avoid_: Power user, existing user

**Newcomer**:
A person opening the app for the first time with no Decks.
_Avoid_: Beginner, new user

### Anki concepts

These are Anki's own words. The interface uses them verbatim, never friendlier substitutes.

**Note**:
A unit of information made of fields, from which one or more Cards are generated.
_Avoid_: Entry, item, fact

**Card**:
A single question-and-answer pair generated from a Note; the thing that is scheduled and studied.
_Avoid_: Flashcard, question

**Deck**:
A named group of Cards studied together.
_Avoid_: Collection, set, folder

**Note Type**:
The definition of a Note's fields and its Card Templates.
_Avoid_: Model, template, format

**Card Template**:
The pattern that turns a Note's fields into the front and back of a Card.
_Avoid_: Card type, layout
