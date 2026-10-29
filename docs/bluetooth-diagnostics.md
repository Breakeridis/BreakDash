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
no constructors, methods, field values, Binder calls or unknown services are used.
The budget is 64 interface candidates, five seconds checked between operations,
and bounded text. An individual Android metadata/class-loading call is not
preemptible; cancellation interrupts the worker and prevents accepting its result.
Unavailable packages/classes and exhausted budgets are reported explicitly.

Bluetooth feedback includes a passive adapter/location/permission snapshot,
MAXOBD/BreakDash versions and declared/granted relevant permissions, the latest
operation stages/timings/numeric statuses, and explicitly collected factory data.
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

## Verification

All executable verification runs on GitHub Actions: JVM state-machine/privacy
tests, existing regressions, debug/release lint and both ARM APK builds. Synthetic
tests cover cancellation/resource release, replacing operations, stale callbacks,
connection/discovery ordering, hard deadlines, numeric failures, pairing timeout
and readable service definitions through the existing privacy filter.

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

The next implementation follows the reported interfaces and transport. Do not
guess vendor callback layouts, characteristic UUIDs, pairing PINs or OBD frames.
