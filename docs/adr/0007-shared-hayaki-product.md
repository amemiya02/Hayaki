---
status: accepted
---

# Share Hayaki's brand and daily study experience

Android and iOS ship one product from separate repositories. The iOS edition supplies the app icon, ten Themes and interaction reference for the Core Loop. Android retains its existing capability inventory and implements these interactions in Jetpack Compose and Material 3, so a learner can change devices and still find the same actions while using the accessibility, navigation and input conventions of each platform.

## Consequences

- This replaces the fixed purple palette in [ADR-0006](0006-direction-material-3-standard.md). Appearance and Theme are independent. Card counts and ratings retain their meanings across Themes. Card Templates control their own Card Content.
- Android presents its existing Anki Pages through Harmonisation and aligns their navigation with the rest of the product. Statistics retains its deck filter and PDF export. [ADR-0003](0003-interface-rebuilt-natively-in-compose.md) continues to govern the Engine and UI boundary.
- Each platform has its own public version, release notes and release schedule. Android's version is not tied to iOS's. *(Changed 2026-11-22: the two editions previously shared a public version, from 0.2.0.)*
- The [iOS repository](https://github.com/amemiya02/Hayaki-iOS) owns the brand manifest at `docs/brand/manifest.json`. Android vendors its resources and records the source revision. The two repositories can build independently.

### Updating the shared brand

Change the artwork or palette in Hayaki-iOS first. In Android, set the revision in `docs/brand/themes.json` to the iOS commit containing `docs/brand/manifest.json`. The icon importer reads that revision too. Copy the named accents and study canvases into the Android palette file, then run the icon importer with the iOS checkout and run `python3 tools/foundation/generate_colors.py`. Commit the generated resources with the updated pin. The generator checks text contrast for all ten themes in Light, Dark and Black.
