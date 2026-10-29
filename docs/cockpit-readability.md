# Navigation-first contrast and layout

v1.0.11 follows the chosen distinct-section/navigation-first design. The existing three columns and largest circular map remain unchanged (720 × 720 map at the 1280 × 720 reference size). Radio connection, map-motion timing, GPS, routing, package controls and reporting retain their behavior.

## Palette and controls

`ThemePalette` is the shared source for native view and map-overlay colours. Material day/night fallback styles mirror its system-widget colours; the saved theme is selected before activity creation so inputs and dialogs also use the appropriate native theme.

| Role | Day | Night |
| --- | --- | --- |
| Background / surface | `#FFFFFF` / `#E4E9EF` | `#080A0D` / `#20252C` |
| Main / secondary text | `#101820` / `#344150` | `#FFFFFF` / `#CBD2D9` |
| Outline | `#536170` | `#929BA6` |
| Navigation accent / fill | `#00408F` / `#00408F` | `#75B7FF` / `#173E69` |
| Radio accent / fill | `#623800` / `#623800` | `#FFD166` / `#3D2C0A` |
| Confirmed active | `#075025` | `#7DE2A0` |
| Stop/error label | `#8B1111` | `#FFAAA5` |

Filled primary and selected-preset controls use white text. Neutral controls have solid opaque surfaces and 2 dp outlines; navigation banners and selected presets have 3 dp outlines. Press feedback preserves contrast. Switch tracks are opaque, their thumbs contrast with both On and Off tracks, and their text labels remain explicit. Inputs keep outlined surfaces. Important labels are 18 sp or larger, primary actions 20–22 sp, and typefaces use medium weights rather than thin display text. Optional seconds, preset help and map credits are smaller; full labels remain available through accessibility descriptions/tooltips where display text is ellipsized.

GitHub tests require at least 7:1 for essential text/semantic colours against the theme background/surface and for white text on filled actions; meaningful outlines and switch thumbs require at least 3:1. Tests also check pressed controls. These are design benchmarks based on [W3C enhanced text contrast](https://www.w3.org/WAI/WCAG22/Understanding/contrast-enhanced.html) and [non-text contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html), not a certification of automotive readability. Map tiles and third-party app icons are external imagery; route/vehicle/pin graphics have theme-aware contrasting outlines. Sunlight, glare, brightness and physical display quality still need head-unit checks.

## Cockpit hierarchy

- The upper-left map overlay becomes a minimum-144 dp opaque navigation banner, with 32 sp turn distance, 24 sp instruction (up to two lines), 18 sp remaining-distance/destination detail and a separate 64 dp Stop control. It reserves space for a right-hand stack of labeled Search/Recenter buttons. The heading chip is hidden during an active route.
- Arrival, off-route and missing-GPS states use the same banner. `NavigationGuidance` carries distance, instruction, detail and state separately; display code never splits a formatted sentence. Route progress, thresholds and distance formatting remain the same.
- Route-overview bounds reserve top clearance for the banner and pin heads (scaled with system fonts), so the new overlay does not hide the northern endpoint. Follow-camera angle, lower vehicle position and motion timing are unchanged.
- Pinned destinations retain their own bottom Navigate/Clear panel, above the larger 56 sp speed readout. Selecting a new pin temporarily hides the existing route's banner; clearing the pin restores the route information.
- The clock is reduced to 48 sp medium type with readable date and discreet seconds. Radio uses an amber identity, 40 sp frequency, 18 sp status/metadata, larger presets and an explicit hold-to-save hint. Selected presets use an amber outline, dark fill and selected accessibility state.
- With larger system fonts, preset/metadata rows and utility tiles gain height. The radio column can scroll if needed rather than hiding controls; at the reference font size its contents fit normally. Long preset/app labels ellipsize while their accessibility labels retain the full value.
- Greetings, slogans, branding and repeated configuration hints are removed. The right-hand 2 × 2 launcher grid uses larger icons/labels and assigned app names; the Navigation app tile is blue. Unassigned shortcuts say Choose app. Settings/Updates/About retain their bottom positions.
- Settings tabs have a filled selection. Online-map consent, report preview/send and available update/install actions use filled primary buttons. Unavailable release downloads stay hidden and the repository remains a compact link.

## Validation

All executable verification belongs to GitHub Actions: palette constraints, guidance fields/states/units, existing radio, motion, tracking, privacy, encryption and signing tests, Android builds/lint and ABI/signature checks. No local app builds, tests or emulators are used.

On the head unit, inspect day mode in sunlight and night mode in darkness. Check long road/destination names, route arrival/off-route/GPS loss, pan/recenter, pin/clear while a route exists, overlays extending outside the circle, radio labels/presets, empty/assigned app tiles, toggle/tab states, update-check failures/current/new versions and input focus. Check normal and larger system fonts for clipping and phone landscape scrolling. Physical visual/performance validation remains pending until tested there.
