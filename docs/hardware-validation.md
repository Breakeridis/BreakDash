# First head-unit validation

All app builds and automated checks belong in GitHub Actions. The following checks require the physical head unit after installing its APK; they have not been simulated as successful.

## Launcher and memory

1. Install the development APK, open it in landscape, and confirm that controls fit the actual 1280 × 720 screen and vendor system bars.
2. Tap GPS, allow precise location, and check GPS lock outdoors. If location is disabled, use the Android Location screen. A fix expires after 15 seconds; speed must show a dash instead of a stale value.
3. Open Settings. Record BreakDash PSS with maps disconnected, connected, and during a route. Check real memory pressure with the raw `/proc/meminfo` report; reported total RAM may be inflated.
4. Open and close Settings repeatedly; CPU sampling must resume each time. Change to another app and return; subscriptions must resume without duplicates.
5. Select the day theme under direct sunlight and the night theme in darkness. Check app picker, scrolling favorites, long-press actions, and the default Home selection.

## NWD radio

The source uses only the documented `com.nwd.radio.service.RadioFeature` primitive transactions: tune 1, seek 3, band 5, background audio flag 13, save 20, and RT text 28. Frequency, source, state, band, and PS are synchronized through documented broadcasts and exact-key settings observers. Bindings are released when the cockpit is hidden; playback is not deliberately stopped.

The reference does **not** supply the `Frequency` / `RadioPoint` Parcelable layout or callback method ordering. BreakDash does not deserialize those or register a guessed callback. The preset shelf starts empty and saves stations explicitly. Raw band bytes are captured from the vendor; the app does not impose a guessed FM/AM band enumeration. Preset Binder indices are 0–5; verify their slot mapping on this firmware.

1. Tap RADIO to open the stock app, tune a known station, then return. Confirm displayed frequency and PS/RT.
2. Seek both directions. Confirm actual sound and state, rather than assuming Binder acknowledgement proves audio routing.
3. Hold each of six preset slots and compare its stock-radio slot. Tap to recall, restart BreakDash, then retest persistence.
4. Change stations in the stock app and switch audio to music. Return and check source/standby synchronization.
5. Test band change and AM formatting (531–1620 kHz). FM raw values are 8750–10800 in 10 kHz units, e.g. 10250 → 102.50 MHz.
6. Test power/audio routing. Transaction 13 is named `setRadioBackServiceOn`; its exact mute/standby semantics can vary by firmware. The live MCU source and radio-state settings remain the UI authority.

Vendor services are outside this APK and have not been exercised here. Capture the inspector report if a call is rejected or a setting is absent.

## Navigation and maintenance

1. Use the explicit Connect screen to enable your selected online map services. Check heading-up map rotation while moving and stable orientation while stationary.
2. Drag, pinch, double-tap, and rotate the map. Confirm GPS speed keeps updating but the camera stays where you moved it. Tap **Recenter** to restore heading-up tracking.
3. Hold a map point and check that an amber pin appears at the touched coordinate, including after zooming/rotating and on the scaled phone layout. Tap **Navigate**, verify the route and destination pin, then stop. A pending point can be cleared or replaced; canceling/replacing it during calculation must not display the old route when its response arrives. Without a fresh GPS fix, the pending pin remains available and Navigate stays disabled until GPS locks.
4. Search, choose a destination, check the full route framing and return to vehicle tracking, verify next-turn and remaining road distance, and cancel. Drag during the route overview; the map must remain in explore mode until Recenter. If off route, use a new search or pinned destination to recalculate.
5. Revoke location permission or disable GPS; the dashboard must show the loss of a fresh fix. Disconnect internet; the app must remain usable for radio, apps, and GPS instrumentation.
6. In Settings, Test/Connect ADB only after enabling the loopback daemon. Accept Android's RSA prompt. A closed port or denied authorization should display an error, not claim a successful package change.
7. Enable and disable each Google package, then test bulk actions. Standard third-party apps normally lack system API permission; ADB/root must be available for package control.
8. Download Aurora and verify Android's install permission handoff. A different existing Aurora signing certificate requires choosing a consistent distribution or manually replacing the old app.
9. For release updates, install a signed release and publish a higher version using the same keystore. Test download, installer permission, and preservation of app preferences.
10. Review a cloud report, add a note, and upload only to your configured HTTPS inbox. Confirm that the saved file contains ciphertext and that your private key can decrypt it with the owner utility.
