- This repository is a personal fork that carries the Redesign. Never open pull requests, issues or comments against upstream (`ankidroid/Anki-Android`). See [ADR-0001](docs/adr/0001-own-fork-no-upstreaming.md).
- Upstream's [AI policy](AI_POLICY.md) governs contributions to upstream. It does not apply to work in this fork, which is built with AI assistance and never offered upstream.
- Keep changes focused on the requested task. Avoid unrelated refactors, formatting, and settings changes.
- Keep branch history linear. Do not create merge commits, except to bring Engine updates in from upstream. See [ADR-0003](docs/adr/0003-interface-rebuilt-natively-in-compose.md).
- For bug fixes, write a regression test that fails for the expected reason before the fix, then confirm it passes with the fix.

## Agent skills

### Issue tracker

Issues and specs live as markdown files under `.scratch/` in this repo. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default triage roles, recorded as a `Status:` line in each issue file. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
