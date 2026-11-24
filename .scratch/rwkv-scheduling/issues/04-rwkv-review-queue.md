# Study review Cards by RWKV on desktop

Status: done
Type: AFK
Blocked by: 03
User stories: 4, 6, 7

## Parent

[Opt-in RWKV scheduling specification](../spec.md)

## What to build

With the switch on and RWKV ready, a desktop study session shows review Cards by the queue rule in the specification. A Card not answered today is due when its predicted recall at the end of today is at or below the home Deck's effective desired retention. A Card answered today returns when its recall now is at or below that target, 30 seconds have passed, and 5 other Cards have been answered since. Due Cards are shown lowest recall first, within the Deck's review limit. The Decks screen counts review Cards by the same rule. Each answer is appended to the model, and scores are refreshed through the runtime's score table. While RWKV is not ready, the session uses Upstream's queue.

## Acceptance criteria

- [x] A seeded collection with scores on both sides of the target queues the right Cards in the right order.
- [x] The same-day guard holds a Card back until both conditions are met.
- [x] With sibling burying off, the review count on Decks equals the number of review Cards the session shows.
- [x] Filtered Decks show the same Cards with the switch on as with it off.
- [x] New, learning and relearning Cards are queued exactly as with the switch off.
- [x] Every answer still writes FSRS or SM-2 due date, interval and memory state, identical to the switch off.
- [x] Turning the switch off mid-session returns to the stored due dates on the next Card.

## Answer

The queue rule, deck counts, same-day guard, effective desired retention, filtered-deck exclusion and the upstream fallback are in `rslib/src/scheduler/rwkv/mod.rs` and the queue builder. `queue_follows_recall_and_deck_counts_agree` checks order, counts and switching off; `answering_writes_the_same_rows_with_the_switch_on` checks that answering is unchanged. Scores are cached per card and recomputed every 20 answers, as the specification starts with.
