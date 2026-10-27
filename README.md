# BreakDash

A native Android home launcher for the Junsun / Nowada K2401 head unit in a Mitsubishi Lancer. Designed for the **real Android 10 / API 29, 32-bit Allwinner A133, 1280 × 720** hardware described in [the device reference](docs/headunit_info.md).

The app uses Android platform Views, direct hardware radio IPC, and one MapLibre vector map. There is no WebView, Compose, Google Maps SDK, Play Services dependency, always-on service, boot receiver, or background scheduler. The dependency footprint and real memory consumption still need measuring on the head unit; the firmware's reported RAM is not a reliable performance budget.

## Get the APK — GitHub builds only

1. Open the [latest signed release](https://github.com/Breakeridis/BreakDash/releases/latest).
2. Download **32-bit ARM (armeabi-v7a)** for the head unit, or **64-bit ARM (arm64-v8a)** for compatible 64-bit ARM devices.
3. Install the APK through Android's installer. No ZIP extraction is needed for release downloads.
4. Open **BreakDash** and allow precise location when you tap GPS. Choose **Settings → Device → Default launcher** if you want it to replace the stock launcher.
5. Use the dashboard's **Updates** button for future signed updates.

Development APKs remain available under successful [GitHub Actions runs](https://github.com/Breakeridis/BreakDash/actions/workflows/android.yml), in the **BreakDash-debug-armeabi-v7a** and **BreakDash-debug-arm64-v8a** ZIP artifacts. They are labeled **BreakDash Dev**, with package ID `com.breakdash.launcher.debug`. Release builds use `com.breakdash.launcher`; the two can coexist and have separate preferences. GitHub's default debug signing key is ephemeral, so installing a later development APK may require removing the previous development installation. Signed release builds support updates without losing preferences. Older development builds were also labeled BreakDash; check for a `-dev` version in About if there are two identical icons.

The two APKs are standalone installers, each containing only one native architecture. The 32-bit ARM download does not include 64-bit libraries. Devices that support only 64-bit ARM applications require the `arm64-v8a` APK. The cockpit fills the actual landscape screen aspect ratio while uniformly scaling its controls and circular map; there are no fixed-aspect letterbox bars. The NWD radio and other vendor-only apps remain unavailable on a phone.

**No local app builds, test runs, emulators, or SDK installations are required.** GitHub installs the pinned Gradle/Android toolchain, runs JVM tests and Android lint for both build types, checks an optimized unsigned release build and the encrypted report receiver, and publishes the installable development APK. There is deliberately no wrapper JAR or local build bootstrap script. Unsigned release builds are validation outputs only and are not published as installation artifacts.

## Cockpit controls

- **Clock:** follows Android's locale, date, time zone, and 12/24-hour setting; seconds update while visible.
- **Radio:** seek, audio on/standby, band change, live frequency, PS/RT, and six presets. Tap a preset to tune; hold to save to the MCU and local preferences. Tap RADIO to open the stock app.
- **Map:** the largest square that fits between the side columns, with no outer screen padding or column gutters. At 1280 × 720 the map region is 720 × 720, surrounded by a thin instrument ring. Search and the **◎ Recenter** button float at the top right, with compact heading, GPS, and speed readouts around the map. Drag to pan, pinch or double-tap to zoom, and use two fingers to rotate. Gestures pause camera following so GPS cannot snap you back while browsing; tap **◎** to restore heading-up tracking. Tap KM/H or MPH in the compact speed pill to persist your preferred units. Tap the GPS/accuracy chip for location controls; hold it for the full status tooltip. The Navigate quick tile opens your chosen external navigation app.
- **Pinned destinations:** hold a point on the map to place an amber pin. Tap **Navigate** in the floating banner near the map's lower edge to calculate a driving route from your current GPS fix, or **Clear** to remove the pending pin. The banner appears when a pin or route is active and never resizes the map; route overviews leave room above it for the destination marker. Pinning does not make a search or route request; calculation starts only when you choose Navigate. GPS speed and heading continue updating while you browse. A fresh GPS fix and connected online maps are required to start the route.
- **Route search:** after connecting online maps and obtaining a fresh fix, tap search for debounced address suggestions. Select a result for a route, destination pin, distance, and visual next-turn guidance. After a brief flat route overview, the camera switches to a **45° tilted driving view**, follows GPS travel direction, and places the vehicle below center to show more road ahead. The vehicle arrow shows the last reliable GPS course; stops and slow GPS drift do not spin the map. Movement is required to establish the initial direction. Dragging still enters explore mode; **◎ Recenter** restores the driving view. Stop cancels guidance and returns to the flat dashboard view. If you leave the route, the banner asks you to search again; there is no continuous rerouting or voice guidance.
- **Quick tiles:** dashcam, phone projection, navigation, and music. Tap to launch; hold to choose an installed app. ZLink/AutoKit/TLink detection uses installed app labels.
- **Dock:** All Apps opens a searchable grid; hold an app in that grid to pin it. The scrollable dock supports four favorites. Hold a pinned shortcut to replace or remove it.
- **Settings:** four large tabs: **Appearance** (Day/Night), **Maps** (connect/disconnect and providers), **Apps** (Aurora Store and Google services), and **Device** (default launcher, Android settings, health and advanced tools). Local ADB and the full inspector are under **Device → Advanced tools**. Technical details open separately.
- **Updates:** installed/latest versions, a clear status and reason when installation is unavailable, checked APK downloads, and **What's new**. The notification dot appears here. Development builds offer **Get signed release** in the browser instead of attempting an incompatible in-place update.
- **About:** app version and three large tiles: **Report a problem**, **Device info**, and **Credits**. Reports have an optional note and a preview. **Send feedback** encrypts the exact previewed capture to a fixed ntfy inbox, with no link copying. Ask the recipient to check within three hours. **Temporary link** retains Uguu sharing; **My server** retains the optional owner-controlled receiver.

The CPU/RAM sampler runs only while **Settings → Device → Device health** is visible and the activity is resumed. GPS subscriptions, clock ticks, map rendering, and radio subscriptions stop when a dialog covers the dashboard or another app takes over. Opening a diagnostic report performs a one-shot capture. Automatic update checks are limited to once per day while the cockpit is visible; they never download or install automatically. Panels provide **Back** for one level and **Close** to return directly to the cockpit.

## Online maps

GPS speed and heading work without online services. Map connections are **off by default**. The on-device Connect screen lists the providers and explains the location/search information involved before enabling requests.

Defaults are OpenFreeMap map styles, Photon address search, and OSRM driving routes. Public search/routing services have usage limits and no availability guarantee. Address autocomplete waits 700 ms, requires three characters, suppresses stale results, and allows one request in flight. Provider URLs can be changed in **Settings → Maps → Map providers**; changing a provider disconnects maps until you choose to reconnect.

Map credits and links remain available at the map's lower right. These are online maps, with a small SDK cache, rather than an offline navigation package.

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

The updater reads `Breakeridis/BreakDash` by default; change it under **Updates → Update source**. It selects an APK using Android's preferred supported ARM architecture, validates its package, increasing version code and signing certificate, checks GitHub's SHA-256 asset digest when supplied, and invokes the Android installer. The system's **Allow from this source** permission is required. Development builds open a signed-release download in the browser because their package/signature differs. Once installed, open the release app for future in-place updates.

## Google controls and diagnostics

See [hardware validation](docs/hardware-validation.md) before testing vendor integration. Google package actions are restricted in code to Framework, Play Services, and Play Store; no arbitrary package or shell command is accepted from UI input. Enable/Disable buttons try local ADB, then available root, then the system API. All actions are user initiated. ADB authorization keys stay under the app's private, non-backed-up storage, and each connection closes when its action ends. BreakDash cannot enable TCP debugging without existing system privileges.

The inspector includes raw firmware properties and system settings. Cloud capture masks common identifier/credential keys and does not include GPS locations; review the preview before sending because firmware can store additional personal values under unexpected keys. All sharing methods encrypt the report and note with AES-256-GCM before upload.

**Fixed feedback inbox:** open **About → Report a problem → Preview report → Send feedback**, then ask your recipient to check the latest feedback within three hours. The app uploads an encrypted attachment to one fixed [ntfy](https://ntfy.sh/) topic. Each report has a fresh AES key, wrapped with the recipient's RSA public key. The private key stays outside the APK, repository, ntfy, and GitHub Actions. Official releases are configured through the `BREAKDASH_NTFY_TOPIC` and `BREAKDASH_FEEDBACK_PUBLIC_KEY` Actions variables; forks without them can still use temporary links. See [fixed inbox setup and retrieval](docs/ntfy-feedback.md).

**Temporary sharing:** open **About → Report a problem → Preview report → Temporary link**, then copy/share the complete link. [Uguu](https://uguu.se/) advertises a three-hour file lifetime. Each upload has a fresh encryption key, kept in the share link's `#breakdash-v1=…` fragment rather than sent to Uguu. Anyone with the complete link can decrypt the file. Copy the link before closing; it is not saved in preferences or logs. Paste it in your support chat before expiry. The recipient-side [reader and format guide](docs/temporary-reports.md) explain how to retrieve and decrypt it without sending the key to the host. Uploads happen only on an explicit tap; no background reporting or automatic retries.

**Own server:** configure an HTTPS receiver and RSA public key under **About → Report a problem → Own server setup** to enable **My server**. This wraps the AES key with RSA-OAEP-SHA256; the private key stays with you. The optional [encrypted inbox](backend/README.md) stores ciphertext on **your own server**, without decrypting, forwarding, or creating GitHub issues. No server is provisioned or deployed by this project.

## Project guide

| Location | Purpose |
| --- | --- |
| `app/src/main/java/com/breakdash/launcher` | Platform UI, lifecycle, radio, GPS/map, apps, updates, and settings |
| `app/src/main/java/com/breakdash/launcher/core` | Pure Java frequency, semantic version, camera tracking, and route-progress logic |
| `app/src/test` | CI-only tests for units, boundaries, update ordering, and route progression |
| `.github/workflows/android.yml` | Remote checks, APK artifacts, signing, and releases |
| `backend` | Temporary-report reader, synthetic CI checks, and optional owner-server inbox |
| `docs` | Original hardware/specification references and on-device validation checklist |

Implementation sources: [MapLibre Android lifecycle](https://github.com/maplibre/maplibre-native/blob/main/platform/android/docs/getting-started.md), [OpenFreeMap mobile styles](https://openfreemap.org/quick_start/), [Photon API and usage policy](https://github.com/komoot/photon), [OSRM routing API](https://project-osrm.org/docs/v5.15.2/api/), [dadb](https://github.com/mobile-dev-inc/dadb), and [Android Gradle compatibility](https://developer.android.com/build/releases/about-agp).
