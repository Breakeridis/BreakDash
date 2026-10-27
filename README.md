# BreakDash

A native Android home launcher for the Junsun / Nowada K2401 head unit in a Mitsubishi Lancer. Designed for the **real Android 10 / API 29, 32-bit Allwinner A133, 1280 × 720** hardware described in [the device reference](docs/headunit_info.md).

The app uses Android platform Views, direct hardware radio IPC, and one MapLibre vector map. There is no WebView, Compose, Google Maps SDK, Play Services dependency, always-on service, boot receiver, or background scheduler. The dependency footprint and real memory consumption still need measuring on the head unit; the firmware's reported RAM is not a reliable performance budget.

## Get the APK — GitHub builds only

1. Open [Build BreakDash in GitHub Actions](https://github.com/Breakeridis/BreakDash/actions/workflows/android.yml).
2. Select the latest successful run, or choose **Run workflow**.
3. Download **BreakDash-debug-armeabi-v7a** for the head unit, or **BreakDash-debug-arm64-v8a** for a Pixel 7 Pro / other 64-bit ARM phone, and extract the APK.
4. Copy it to the head unit and install it through Android's installer.
5. Open BreakDash and allow precise location when you tap GPS. In Settings, choose **Choose default Home app** if you want it to replace the stock launcher.

The first artifact is a development build with package ID `com.breakdash.launcher.debug`. Release builds use `com.breakdash.launcher`; the two can coexist. GitHub's default debug signing key is ephemeral, so installing a later development APK may require removing the previous development installation. Signed release builds support updates without losing preferences.

The two APKs are standalone installers, each containing only one native architecture. The 32-bit head-unit download does not include the phone's 64-bit libraries. Pixel 7 and Pixel 7 Pro require the `arm64-v8a` build because they cannot run 32-bit apps. The cockpit scales to fit a phone in landscape for UI testing; the NWD radio and other vendor-only apps remain unavailable on a phone.

**No local app builds, test runs, emulators, or SDK installations are required.** GitHub installs the pinned Gradle/Android toolchain, runs JVM tests and Android lint for both build types, checks an optimized unsigned release build and the encrypted report receiver, and publishes the installable development APK. There is deliberately no wrapper JAR or local build bootstrap script. Unsigned release builds are validation outputs only and are not published as installation artifacts.

## Cockpit controls

- **Clock:** follows Android's locale, date, time zone, and 12/24-hour setting; seconds update while visible.
- **Radio:** seek, audio on/standby, band change, live frequency, PS/RT, and six presets. Tap a preset to tune; hold to save to the MCU and local preferences. Tap RADIO to open the stock app.
- **Map:** heading-up vector roads, GPS accuracy indicator, compass, and speed. Drag to pan, pinch or double-tap to zoom, and use two fingers to rotate. Gestures pause camera following so GPS cannot snap you back while browsing; tap **Recenter** to restore heading-up tracking. Tap KM/H or MPH to persist your preferred units. The Navigate quick tile opens your chosen external navigation app.
- **Pinned destinations:** hold a point on the map to place an amber pin. Tap **Navigate** beneath the map to calculate a driving route from your current GPS fix, or **Clear** to remove the pending pin. Pinning does not make a search or route request; calculation starts only when you choose Navigate. GPS speed and heading continue updating while you browse. A fresh GPS fix and connected online maps are required to start the route.
- **Route search:** after connecting online maps and obtaining a fresh fix, tap search for debounced address suggestions. Select a result for a route, destination pin, distance, and visual next-turn guidance. Stop cancels guidance. If you leave the route, the banner asks you to search again; there is no continuous rerouting or voice guidance.
- **Quick tiles:** dashcam, phone projection, navigation, and music. Tap to launch; hold to choose an installed app. ZLink/AutoKit/TLink detection uses installed app labels.
- **Dock:** All Apps opens a searchable grid; hold an app in that grid to pin it. The scrollable dock supports four favorites. Hold a pinned shortcut to replace or remove it.
- **Settings:** CPU/RAM/PSS readings, Google package controls, local ADB, Aurora Store, connections, Home selection, and Android Settings. Hold Settings on the dashboard for the full inspector.
- **About:** day/night theme, GitHub releases, changelogs, checked APK downloads, diagnostic preview, encrypted upload, and full system information.

The CPU/RAM sampler runs only while Settings is the visible top dialog and the activity is resumed. GPS subscriptions, clock ticks, map rendering, and radio subscriptions stop when a dialog covers the dashboard or another app takes over. Opening a diagnostic report performs a one-shot capture. Automatic update checks are limited to once per day while the cockpit is visible; they never download or install automatically.

## Online maps

GPS speed and heading work without online services. Map connections are **off by default**. The on-device Connect screen lists the providers and explains the location/search information involved before enabling requests.

Defaults are OpenFreeMap map styles, Photon address search, and OSRM driving routes. Public search/routing services have usage limits and no availability guarantee. Address autocomplete waits 700 ms, requires three characters, suppresses stale results, and allows one request in flight. Provider URLs can be changed in **Settings → Connections**; changing a provider disconnects maps until you choose to reconnect.

Map credits and links remain available beneath the portal. These are online maps, with a small SDK cache, rather than an offline navigation package.

## Signed releases and in-app updates

In repository **Settings → Secrets and variables → Actions**, add:

| Secret | Value |
| --- | --- |
| `BREAKDASH_KEYSTORE_BASE64` | Base64-encoded release PKCS12 keystore |
| `BREAKDASH_KEYSTORE_PASSWORD` | Keystore password |
| `BREAKDASH_KEY_ALIAS` | Signing key alias |
| `BREAKDASH_KEY_PASSWORD` | Signing key password |

Also set the repository Actions variable `BREAKDASH_SIGNER_SHA256` to the SHA-256 fingerprint of the release signing certificate. CI verifies both APKs against it before publishing. PKCS12 is the default keystore type; an existing JKS keystore additionally needs the Actions variable `BREAKDASH_KEYSTORE_TYPE=JKS`.

If an exported single-key keystore uses an internal alias that differs from its certificate's friendly name, CI resolves that sole private-key entry automatically. The expected certificate fingerprint is still enforced; ambiguous multi-key stores are rejected when the configured alias is absent.

Generate and back up your signing key in a trusted environment, such as a private GitHub Codespace cloud terminal; no local app compilation is needed. Keep this same key for every release. Do not commit it, paste it in issues, or include it in APKs.

In **Actions → Build BreakDash → Run workflow**, enter a stable tag such as `v1.0.0` in the optional **release_version** field. Leaving it blank builds development APKs only. The workflow runs checks, builds and shrinks both ARM variants, verifies their signatures, creates their SHA-256 files, then creates the tag and publishes the release from the exact tested commit. An existing tag must point at that same commit. Pushing a stable version tag from a cloud terminal also triggers this release path. Signing secrets and the expected certificate fingerprint are mandatory; an unsigned APK is never published as a release. Stable versions use `major × 1,000,000 + minor × 1,000 + patch` for Android version codes (each component 0–999).

The updater reads `Breakeridis/BreakDash` by default. It selects an APK using Android's preferred supported ARM architecture, validates its package, increasing version code and signing certificate, checks GitHub's SHA-256 asset digest when supplied, and invokes the Android installer. The system's **Allow from this source** permission is required. Development builds disable release installation because they have a different package/signature.

## Google controls and diagnostics

See [hardware validation](docs/hardware-validation.md) before testing vendor integration. Google package actions are restricted in code to Framework, Play Services, and Play Store; no arbitrary package or shell command is accepted from UI input. Enable/Disable buttons try local ADB, then available root, then the system API. All actions are user initiated. ADB authorization keys stay under the app's private, non-backed-up storage, and each connection closes when its action ends. BreakDash cannot enable TCP debugging without existing system privileges.

The inspector includes raw firmware properties and system settings. Cloud capture masks common identifier/credential keys and does not include GPS locations; review the preview before sending because firmware can store additional personal values under unexpected keys. An upload encrypts the report and note with AES-256-GCM, then wraps the AES key using your RSA public key and OAEP-SHA256. The private key is held by you, outside both the APK and receiver.

Cloud uploads remain unavailable until you configure an HTTPS receiver and public key. The optional [encrypted inbox](backend/README.md) stores encrypted files on **your own server**. It does not decrypt them, forward them, or open GitHub issues. No cloud service is provisioned or deployed by this project.

## Project guide

| Location | Purpose |
| --- | --- |
| `app/src/main/java/com/breakdash/launcher` | Platform UI, lifecycle, radio, GPS/map, apps, updates, and settings |
| `app/src/main/java/com/breakdash/launcher/core` | Pure Java frequency, semantic version, camera tracking, and route-progress logic |
| `app/src/test` | CI-only tests for units, boundaries, update ordering, and route progression |
| `.github/workflows/android.yml` | Remote checks, APK artifacts, signing, and releases |
| `backend` | Optional encrypted-file inbox and owner decryption utility |
| `docs` | Original hardware/specification references and on-device validation checklist |

Implementation sources: [MapLibre Android lifecycle](https://github.com/maplibre/maplibre-native/blob/main/platform/android/docs/getting-started.md), [OpenFreeMap mobile styles](https://openfreemap.org/quick_start/), [Photon API and usage policy](https://github.com/komoot/photon), [OSRM routing API](https://project-osrm.org/docs/v5.15.2/api/), [dadb](https://github.com/mobile-dev-inc/dadb), and [Android Gradle compatibility](https://developer.android.com/build/releases/about-agp).
