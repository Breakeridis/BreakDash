# Bluetooth diagnostic foundation

Open **Settings → Bluetooth → Open Bluetooth**. This first stage provides native
Classic/BLE discovery, pairing and a finite BLE connection test. Factory Phone,
Bluetooth Music and MAXOBD remain the working applications for their functions.
Full phone/music controls and live OBD readings require verified device evidence
from this stage; this release does not implement vehicle commands.

## Device flow

- Grant Bluetooth permissions using the visible button. Android 10/11 also need
  Location permission and the Location service for discovery. Android 12+ requests
  Nearby Devices permissions; optional hardware features preserve installability.
- Classic and BLE scans are separate, explicitly started, and limited to 12 seconds.
  Results merge repeated device observations and list at most 32 devices including
  paired devices. Paired, discovered and connected are different states.
- Selecting a device stops discovery. Classic devices can use Android's pairing
  prompt. Forgetting devices opens Android Bluetooth settings, without hidden APIs.
- **Test BLE connection** uses `connectGatt` with direct LE transport, then one
  service-discovery request. Connection and discovery share a 25-second deadline.
  The probe closes its connection on completion, failure, timeout or cancellation.
  It never reads/writes characteristics, subscribes, negotiates MTU or sends OBD data.
- The visible page owns scans, receivers and probes. Covering it with another panel,
  leaving the app, closing, or recreating the activity cancels its work. Stale
  callbacks cannot complete a new operation. Reopening never automatically restarts.
  Android itself may finish an already-requested pairing prompt independently.

## Diagnostic evidence and privacy

**Diagnostics → Inspect factory interfaces** runs a bounded read-only inspection
of the known NWD phone, Bluetooth music, kernel and settings packages. It reports
versions, service names, export/enabled flags and required permissions. Interface
signatures are inspected only from installed read-only factory APKs accepted by
the existing factory trust policy. Classes are loaded without initialization;
no constructors or methods are invoked, no field values are read, and no unknown
services are bound or called.
The v1.0.14 inspector selects Bluetooth-related classes across factory namespaces,
including vendor `btphone`/`gocsdk` packages, rather than restricting names to
`com.nwd.*`. It reports public method signatures on relevant classes and interfaces,
including static methods, without invoking them. CAN and unrelated settings classes
cannot consume the selection budget. Names are ranked before loading; Stub/contract
duplicates and generated UI/proxy classes are excluded. Each package gets its own
text/time allocation, with 16 candidates per package and a five-second overall
budget checked between operations. An individual Android metadata/class-loading call is not
preemptible; cancellation interrupts the worker and prevents accepting its result.
Unavailable packages/classes and exhausted budgets are reported explicitly.

Bluetooth feedback includes a passive adapter/location/permission snapshot,
MAXOBD/BreakDash versions and declared/granted relevant permissions, separate last
Classic/BLE scan summaries, the latest operation stages/timings/numeric statuses,
and explicitly collected factory data. Android 12+ scan/connect permissions are
labeled not applicable on Android 10/11. Internal scan error 3 is explained as a
scan-start failure, not an OBD connection error.
The total Bluetooth section is capped at 16 KB. Results are saved in app-private
preferences until replaced or cleared; no device identity is persisted there.

Names and addresses appear only in the local device UI. Reports omit device names,
addresses, raw advertisements, contacts, phone numbers and characteristic values.
GATT service/characteristic definitions use 16 space-separated hex octets so that
the existing MAC-address privacy filter preserves them. Characteristic property
flags are included. Protocol definitions are not a proof of OBD compatibility.
No owner-specific identifiers are hardcoded in source or tests.

Opening a report never initiates a scan, connection or factory inspection. The
existing preview and encrypted feedback flow is retained, including the exact
previewed capture. No background service, polling, reconnection, root or automatic
ADB access is added. Dashboard GPS/radio remain paused while panels cover it.

### Android Bluetooth-manager capture

**Capture Android Bluetooth status** explicitly runs the fixed read-only command
`dumpsys -t 4 bluetooth_manager` in the app process, with a six-second process
deadline. Android may require its privileged DUMP permission; a denial is saved
as a clear diagnostic result without attempting escalation automatically.

**Capture status through local ADB** is a separate explicit action. It reuses the
existing app-private ADB key and Android authorization flow on 127.0.0.1:5555;
BreakDash cannot enable TCP debugging itself. **Local ADB setup / authorization**
opens the existing setup page. After granting Android's prompt, return and retry
the capture if leaving the page cancelled it. No root, package changes, Bluetooth
reset, HCI logging or arbitrary shell input is introduced.

The ADB command has the same four-second service deadline and a 64 KB output cap;
connection/read timeouts are two seconds/6.5 seconds. Closing/covering the page,
changing sections or clearing history cancels the job and closes an attached ADB
connection. Initialization and individual platform calls remain cooperatively
bounded; they are not background subscriptions.

Raw dumps contain sensitive device information and never appear in the UI,
preferences, feedback or diagnostic history. A strict parser retains only known
manager booleans/state, crash/registration counts, selected numeric stack fields,
known profile names and registrations for BreakDash/MAXOBD. Names, addresses,
connection history, other packages and unknown formats are omitted. No recognized
fields, missing service, timeouts and permission denial are explicit outcomes.
Capturing feedback itself never executes dumpsys or opens ADB.

## Verification

All executable verification runs on GitHub Actions: JVM state-machine/privacy
tests, existing regressions, debug/release lint and both ARM APK builds. Synthetic
tests cover cancellation/resource release, replacing operations, stale callbacks,
connection/discovery ordering, hard deadlines, numeric failures, pairing timeout
and readable service definitions through the existing privacy filter.
Additional synthetic regressions verify that Bluetooth classes outside the NWD
namespace outrank unrelated CAN/settings classes, selection remains bounded,
Stub aliases deduplicate, and the native manager projection retains status while
rejecting device identities, history, unknown fields and arbitrary exception text.

Device acceptance:

1. Deny/grant permissions; disable Bluetooth/Location; check missing-adapter/BLE
   handling on other devices. No crash or automatic retry should occur.
2. Scan Classic then BLE. Stop/switch rapidly, check duplicate merging, empty
   results, RSSI and the 32-device limit. Existing scans from other apps should
   cause a clear retry message rather than being silently cancelled.
3. For KW906, first confirm it still works on the smartphone. Turn smartphone
   Bluetooth off and close other OBD apps. Find the scanner under BLE, select it
   and test. Check connecting/discovery/success, rejected operations, lost power,
   timeout and failure status. Success means service discovery, not live car data.
4. Cover/close/background the page during every operation and switch theme.
   Check prompt cleanup, released GATT connection and no stale results. Then
   open MAXOBD and verify the probe no longer occupies the adapter.
5. Inspect factory interfaces, leave during inspection, reopen and retry. Review
   the saved summary, clear history, and confirm a new report reflects the clear.
6. Use **Send feedback**, preview and upload. Verify useful UUID octets/error
   codes survive and identity/characteristic values are absent. Use
   `backend/read-feedback.ps1` locally to inspect; never submit actual reports
   or factory APKs to CI/public sources.
7. Check factory phone pairing/calls/music and existing radio, map, USB and
   signed-update behavior. Phone/music capability claims require physical checks.
8. Run Classic and BLE scans sequentially and confirm both summaries remain in
   feedback. Inspect factory Bluetooth interfaces, then capture Android status.
   Check denied/unavailable/no-recognized-fields results. If TCP ADB is available,
   authorize through the setup page and explicitly capture through local ADB.
   Cancel/close during each capture and confirm no late result is saved; clearing
   diagnostic history must clear both scan summaries and native/factory captures.

The next implementation follows the reported interfaces and transport. Do not
guess vendor callback layouts, characteristic UUIDs, pairing PINs or OBD frames.
