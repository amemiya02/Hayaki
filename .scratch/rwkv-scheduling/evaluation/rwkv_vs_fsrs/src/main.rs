//! RWKV (fushi_rwkv, deployment path) against FSRS-6 (fsrs 6.6.2, the crate
//! upstream Anki 26.09.4 uses) on the same reviews.
//!
//! For each user:
//! - RWKV replays the whole review log once and records the immediate-head
//!   prediction made before every answered review.
//! - FSRS items are built the way Anki builds them: one item per review that is
//!   not the card's first and whose delta_t (in days) is > 0.
//! - Items are split in time order with fsrs's TimeSeriesSplit (5 folds). Each
//!   fold fits FSRS-6 on the earlier items and predicts the fold's test items.
//! - Both models are scored on exactly those test items.
//!
//! Usage: rwkv_vs_fsrs <weights.int8.bin> <rows_dir> <out_dir> <user>...
//! Writes <out_dir>/<user>.tsv (one line per scored review) and prints one
//! summary line per user.

use std::io::Write;
use std::path::{Path, PathBuf};
use std::time::Instant;

use fsrs::{
    compute_parameters, current_retrievability, ComputeParametersInput, FSRSItem, FSRSReview,
    DEFAULT_PARAMETERS, FSRS,
};
use fushi_rwkv::{FrwkvReview, Model, RwkvRuntime, StatePrecision};
use rayon::prelude::*;

fn read_rows(path: &Path) -> Vec<FrwkvReview> {
    let bytes = std::fs::read(path).unwrap();
    assert_eq!(bytes.len() % 80, 0, "{}", path.display());
    bytes
        .chunks_exact(80)
        .map(|b| {
            let i = |k: usize| i64::from_le_bytes(b[8 * k..8 * k + 8].try_into().unwrap());
            FrwkvReview {
                card_id: i(0),
                note_id: i(1),
                deck_id: i(2),
                preset_id: i(3),
                duration_millis: i(4),
                card_type: i(5),
                day_offset: i(6),
                elapsed_days: i(7),
                elapsed_seconds: i(8),
                present: u32::from_le_bytes(b[72..76].try_into().unwrap()),
                ease: b[76],
                reserved: [0; 3],
            }
        })
        .collect()
}

/// One FSRS item and the review row it predicts.
struct Item {
    row: usize,
    card: i64,
    delta_t: u32,
    label: u8,
    item: FSRSItem,
}

fn build_items(rows: &[FrwkvReview]) -> Vec<Item> {
    let mut history: std::collections::HashMap<i64, (Vec<FSRSReview>, i64)> =
        std::collections::HashMap::new();
    let mut items = Vec::new();
    for (row, r) in rows.iter().enumerate() {
        let entry = history.entry(r.card_id).or_insert_with(|| (Vec::new(), r.day_offset));
        let delta_t = if entry.0.is_empty() {
            0
        } else {
            (r.day_offset - entry.1).max(0) as u32
        };
        entry.0.push(FSRSReview {
            rating: r.ease as u32,
            delta_t,
        });
        entry.1 = r.day_offset;
        if entry.0.len() >= 2 && delta_t > 0 {
            items.push(Item {
                row,
                card: r.card_id,
                delta_t,
                label: u8::from(r.ease > 1),
                item: FSRSItem {
                    reviews: entry.0.clone(),
                },
            });
        }
    }
    items
}

fn predict(fsrs: &FSRS, decay: f32, item: &FSRSItem) -> f32 {
    let n = item.reviews.len();
    let history = FSRSItem {
        reviews: item.reviews[..n - 1].to_vec(),
    };
    match fsrs.memory_state(history, None) {
        Ok(state) => current_retrievability(state, item.reviews[n - 1].delta_t as f32, decay),
        Err(_) => f32::NAN,
    }
}

fn logloss(p: f32, y: u8) -> f64 {
    let p = (p as f64).clamp(1e-6, 1.0 - 1e-6);
    if y == 1 {
        -p.ln()
    } else {
        -(1.0 - p).ln()
    }
}

fn run_user(model: &Model, rows_dir: &Path, out_dir: &Path, user: &str) -> String {
    let started = Instant::now();
    let rows = read_rows(&rows_dir.join(format!("{user}.bin")));
    let inputs: Vec<_> = rows.iter().map(|r| r.to_input(false).unwrap()).collect();

    let mut runtime = RwkvRuntime::new(model, StatePrecision::Int4, 1).unwrap();
    let mut rwkv = vec![f32::NAN; inputs.len()];
    for (block, chunk) in inputs.chunks(65_536).enumerate() {
        for (index, p) in runtime.replay(chunk, true).unwrap() {
            rwkv[block * 65_536 + index] = p;
        }
    }
    let rwkv_seconds = started.elapsed().as_secs_f64();

    // RWKV on every non-first review, for comparison with Fushi's replay_eval.
    let (mut all_loss, mut all_n) = (0.0f64, 0usize);
    for (r, p) in rows.iter().zip(&rwkv) {
        if r.elapsed_days != -1 && p.is_finite() {
            all_loss += logloss(*p, u8::from(r.ease > 1));
            all_n += 1;
        }
    }

    let items = build_items(&rows);
    let segment = items.len() / 6;
    let default_fsrs = FSRS::new(&DEFAULT_PARAMETERS).unwrap();
    let default_decay = DEFAULT_PARAMETERS[20];
    let mut out = std::io::BufWriter::new(
        std::fs::File::create(out_dir.join(format!("{user}.tsv"))).unwrap(),
    );
    writeln!(out, "fold\tdelta_t\tlabel\tp_rwkv\tp_fsrs\tp_fsrs_default").unwrap();
    let (mut l_rwkv, mut l_fsrs, mut l_def, mut n) = (0.0f64, 0.0f64, 0.0f64, 0usize);
    let mut skipped = 0usize;
    if segment > 0 {
        for fold in 0..5 {
            let test_start = (fold + 1) * segment;
            let test_end = if fold == 4 { items.len() } else { (fold + 2) * segment };
            let train = &items[..test_start];
            let params = compute_parameters(ComputeParametersInput {
                train_set: train.iter().map(|i| i.item.clone()).collect(),
                card_ids: Some(train.iter().map(|i| i.card).collect()),
                ..ComputeParametersInput::default()
            });
            let params = match params {
                Ok(p) => p,
                Err(_) => DEFAULT_PARAMETERS.to_vec(),
            };
            let fsrs = FSRS::new(&params).unwrap();
            let decay = params[20];
            for it in &items[test_start..test_end] {
                let p_rwkv = rwkv[it.row];
                let p_fsrs = predict(&fsrs, decay, &it.item);
                let p_def = predict(&default_fsrs, default_decay, &it.item);
                if !(p_rwkv.is_finite() && p_fsrs.is_finite() && p_def.is_finite()) {
                    skipped += 1;
                    continue;
                }
                writeln!(
                    out,
                    "{fold}\t{}\t{}\t{p_rwkv:.6}\t{p_fsrs:.6}\t{p_def:.6}",
                    it.delta_t, it.label
                )
                .unwrap();
                l_rwkv += logloss(p_rwkv, it.label);
                l_fsrs += logloss(p_fsrs, it.label);
                l_def += logloss(p_def, it.label);
                n += 1;
            }
        }
    }
    let d = n.max(1) as f64;
    format!(
        "{user}\t{}\t{}\t{n}\t{skipped}\t{:.6}\t{:.6}\t{:.6}\t{:.6}\t{rwkv_seconds:.1}\t{:.1}",
        rows.len(),
        items.len(),
        l_rwkv / d,
        l_fsrs / d,
        l_def / d,
        all_loss / all_n.max(1) as f64,
        started.elapsed().as_secs_f64(),
    )
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let model = Model::from_bytes(&std::fs::read(&args[1]).unwrap()).unwrap();
    let rows_dir = PathBuf::from(&args[2]);
    let out_dir = PathBuf::from(&args[3]);
    std::fs::create_dir_all(&out_dir).unwrap();
    println!(
        "user\trows\titems\tscored\tskipped\tll_rwkv\tll_fsrs\tll_fsrs_default\tll_rwkv_all_nonfirst\trwkv_s\ttotal_s"
    );
    let users: Vec<String> = args[4..].to_vec();
    let stdout = std::sync::Mutex::new(std::io::stdout());
    users.par_iter().for_each(|user| {
        let line = run_user(&model, &rows_dir, &out_dir, user);
        let mut lock = stdout.lock().unwrap();
        writeln!(lock, "{line}").unwrap();
        lock.flush().unwrap();
    });
}
