"""Convert anki-revlogs-10k parquet files to fushi_rwkv FrwkvReview rows (80 bytes each).

Usage: python convert.py <dataset_dir> <out_dir> <user_id> [<user_id> ...]

Row layout follows native/fushi_rwkv/README.md (FrwkvReview) and the field
mapping in the sm56 INTEGRATION.md section 6:
  card_id, note_id, deck_id, preset_id, duration_millis, card_type,
  day_offset, elapsed_days, elapsed_seconds (i64 each), present (u32),
  ease (u8), reserved (3 bytes).
"""

import struct
import sys
from pathlib import Path

import pyarrow.parquet as pq

NOTE, DECK, PRESET, DURATION, CARD_TYPE, DAY, ELAPSED_DAYS, ELAPSED_SECONDS = (
    1, 2, 4, 8, 16, 32, 64, 128,
)
ROW = struct.Struct("<9qIB3x")
assert ROW.size == 80


def read(path: Path, columns: list[str]) -> dict[str, list]:
    if not path.exists():
        return {c: [] for c in columns}
    table = pq.read_table(path, columns=columns)
    return {c: table.column(c).to_pylist() for c in columns}


def convert(dataset: Path, out: Path, user: int) -> int:
    revlogs = read(
        dataset / f"revlogs/user_id={user}/data.parquet",
        ["card_id", "day_offset", "rating", "state", "duration", "elapsed_days", "elapsed_seconds"],
    )
    cards = read(dataset / f"cards/user_id={user}/data.parquet", ["card_id", "note_id", "deck_id"])
    decks = read(dataset / f"decks/user_id={user}/data.parquet", ["deck_id", "preset_id"])
    card_note = dict(zip(cards["card_id"], cards["note_id"]))
    card_deck = dict(zip(cards["card_id"], cards["deck_id"]))
    deck_preset = dict(zip(decks["deck_id"], decks["preset_id"]))

    buffer = bytearray()
    for cid, day, rating, state, duration, ed, es in zip(
        revlogs["card_id"], revlogs["day_offset"], revlogs["rating"], revlogs["state"],
        revlogs["duration"], revlogs["elapsed_days"], revlogs["elapsed_seconds"],
    ):
        present = DURATION | CARD_TYPE | DAY | ELAPSED_DAYS | ELAPSED_SECONDS
        note = card_note.get(cid)
        deck = card_deck.get(cid)
        preset = deck_preset.get(deck) if deck is not None else None
        if note is not None:
            present |= NOTE
        if deck is not None:
            present |= DECK
        if preset is not None:
            present |= PRESET
        # card_type is Fushi's RwkvReviewState: 0 = start of a learning sequence,
        # otherwise revlog type + 1. The dataset has the card state instead; the
        # small model zeroes this feature, so the mapping only has to be valid.
        if ed == -1:
            card_type = 0
        else:
            card_type = {0: 1, 1: 1, 2: 2, 3: 3}.get(state, 2)
        buffer += ROW.pack(
            cid, note or 0, deck or 0, preset or 0, max(int(duration), 0), card_type,
            day, ed, es, present, rating,
        )
    (out / f"{user}.bin").write_bytes(buffer)
    return len(buffer) // ROW.size


def main() -> None:
    dataset, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    for user in sys.argv[3:]:
        print(user, convert(dataset, out, int(user)), flush=True)


if __name__ == "__main__":
    main()
