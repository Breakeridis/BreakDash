# Clearer driving maps

Recommended day maps use a bundled derivative of OpenFreeMap Bright. Road fills
retain their distinct upstream colours, with stronger edges and 20% larger street
names. Recommended night maps use OpenFreeMap Dark's base, with near-black land,
subdued flat buildings, light local roads and amber major roads. Bright's full
transportation and shield stack is used at night to retain its bridge/tunnel
separation, ramps, junctions and road hierarchy. The existing blue route and its
halo are added above either style.

Road lines and casings are exactly 20% wider from zoom 14. Each affected layer is
split at that zoom, retaining the original width functions below it and scaled
outputs above it. This avoids invalid nested zoom expressions and preserves
fractional-zoom behaviour, layer order, filters, dashes, joins and caps.

Only fuel and car-parking POIs remain. Icons start at zoom 15; names at 16, with
optional text so a name collision need not hide its icon. There is no rank gate.
`fuel/charging_station` is excluded; `fuel/fuel`, fuel without a subclass,
`parking` and `parking_garage` are accepted. Bicycle/motorcycle parking, business
and transit symbols are excluded. Road names, shields and place labels remain.
Collision detection stays enabled, with road names/shields above the POI layer
in placement priority. Hosted OFM fuel/parking icons have light circular backing.

The circular viewport, gestures, heading follow, 45-degree navigation tilt,
finite vehicle interpolation, visible-dashboard lifecycle and 20 FPS cap remain.
Theme recreation retains route progress, destination/selection, measured location,
heading, camera and follow/explore ownership using data-only configuration state.
Old style completions are rejected by generation and style identity; destruction
invalidates pending completions. Each accepted completion restores current
route, destination, selection and vehicle sources.

## Settings and consent

- Fresh installs use recommended styles and keep online maps **off** until consent.
- Stored day Positron and night Dark default URLs (including a trailing slash)
  migrate to recommended. Other custom HTTPS URLs keep their own appearance.
- Blank advanced day/night fields mean recommended. **Use recommended maps**
  clears only those two saved overrides, reloads the style and preserves the
  online-map choice, route, search endpoint and router endpoint.
- Saving changed providers retains the existing disconnect/reconnect consent
  flow. Search and router settings are not changed by migration or reset.
- Offline/GPS-only mode uses a source-free local background; selecting recommended
  maps cannot enable network maps. Bundled style JSON does **not** bundle tiles,
  fonts, sprites or offline navigation. All recommended remote resources still
  come from OpenFreeMap. No new hosted service is introduced.

## Provenance and maintenance

Inputs in `tools/maps/upstream/` were downloaded from the public OFM Bright/Dark
style endpoints and sprite index on 2026-10-29. These snapshots make the derivative
repeatable; CI does not require upstream availability. Edit
`tools/maps/generate.mjs` and regenerate assets with
`node tools/maps/generate.mjs`. Generation only writes assets; do not run the
validator, tests, Gradle, lint or app locally.

Bundled files are `app/src/main/assets/maps/driving-day.json` and
`driving-night.json`. `NOTICE.txt` records modifications and provenance; the
`licenses/` directory contains complete upstream notices. They are readable in
**Credits → Bundled map style licences**, alongside upstream and design-licence
links. The map's visible OSM/OpenMapTiles/OpenFreeMap credit remains.

Primary sources:

- [OFM styles and their ancestry](https://github.com/hyperknot/openfreemap-styles)
- [Bright upstream licence](https://github.com/openmaptiles/osm-bright-gl-style/blob/master/LICENSE.md)
- [Dark upstream licence](https://github.com/openmaptiles/dark-matter-gl-style/blob/master/LICENSE.md)
- [POI schema and fuel grouping](https://github.com/openmaptiles/openmaptiles/blob/master/layers/poi/poi.yaml)
- [Pinned Native SDK asset URI support](https://github.com/maplibre/maplibre-native/blob/android-v11.8.2/platform/android/MapLibreAndroid/src/main/java/org/maplibre/android/maps/Style.java)

## Verification

GitHub Actions runs the pinned MapLibre style-spec validator, derivative
reproducibility checks, hosted-source URL and icon checks, POI filter examples,
zoom thresholds, collision flags and road geometry/width/label contracts. JVM
tests cover missing, blank, legacy and custom style selection. Existing CI runs
debug/release lint, JVM tests and APK assembly for ARMv7 and ARM64, then checks
the packaged native architectures. No local builds or tests are allowed.

The following device acceptance checks remain manual; CI does not claim to
measure head-unit glare or GPU performance:

1. Upgrade with both old defaults, then with one or two custom HTTPS styles.
   Confirm only old defaults change, optional fields are blank for recommended,
   and custom styles retain their appearance.
2. During navigation switch day/night repeatedly, including while styles are
   loading. Confirm the route, destination, vehicle, progress and follow/45-degree
   camera return. Repeat while exploring and during the initial route preview;
   exploration must remain under gesture control.
3. Reset recommended maps during navigation with non-default search/router URLs.
   Confirm the route, consent and both endpoints remain; only overrides clear.
4. With online maps disabled, reset and switch themes, then reopen the dashboard.
   Confirm GPS-only background and no map-resource requests. Reconnect explicitly.
5. Disconnect network while loading, switch themes, and reopen the dashboard.
   Confirm no crash, no lost navigation data, bounded rendering and a useful
   unavailable status. Reconnect and reopen to confirm recovery.
6. On the head unit inspect dense streets, bridges, tunnels, junctions and motorway
   exits at zoom 15.5 and navigation zoom 16.5, including the 45-degree view. Check
   road hierarchy, label legibility, fuel/parking icons, name collisions and route
   prominence in daylight glare and darkness. Pan/zoom to 14/15/16 boundaries.
7. Cover/reopen the dashboard and background/foreground the app. GPS and rendering
   must stop while hidden and resume smoothly. Check frame pacing and memory on
   the 32-bit head unit; a desktop preview cannot establish acceptance.
