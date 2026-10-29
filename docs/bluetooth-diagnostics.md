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

**Diagnostics → Inspect vendor SDK entry points** runs a bounded read-only inspection
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

The v1.0.15 inspector prioritizes the observed `com.bt.hd.link.BluetoothAPI`,
service-controller helper and callback, `BTFeature`, `BTSourceFeature` and SPP
callback definitions. Constants cannot crowd them out. It records declared
constructors, static factories/initialization methods, superclass/interfaces,
inherited vendor methods and callback argument/return types. Relevant field
names/types are metadata only; values are never read. BLE/GATT/discovery methods
rank ahead of ordinary control methods and resets. Up to 16 related vendor types
per inspected class can extend the candidate list to 32, with at most 24 inspected
types per package and the same five-second cooperative limit. Class presence does
not identify the active Bluetooth backend or establish a usable BLE transport.

Bluetooth feedback includes a passive adapter/location/permission snapshot,
MAXOBD/BreakDash versions and declared/granted relevant permissions, separate last
Classic/BLE scan summaries, the latest operation stages/timings/numeric statuses,
and explicitly collected factory data. Android 12+ scan/connect permissions are
labeled not applicable on Android 10/11. Internal scan error 3 is explained as a
scan-start failure, not an OBD connection error.
The total Bluetooth section is capped at 48 KB. Results are saved in app-private
preferences until replaced or cleared; no device identity is persisted there.
New captures include the producing app version as well as their timestamp.
Unversioned results are older captures; updating does not silently rerun scans.

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

App-process and local-ADB captures are now saved independently, with their own
timestamps. Every result retains its method and stage, including failures. A
closed loopback port is identified before attempting ADB authorization; an open
port alone does not prove ADB authorization. Connection/authorization failures
and status-read failures are distinguished without retaining exception messages.
The old single capture is shown as legacy evidence with an unknown method until
a new capture is saved. Clearing history removes both new captures and the old one.

### Concrete controller inspection

v1.0.16 prioritizes `com.bt.BTController` and its observed direct parent,
`com.android.utils.utils.AbsServiceControllerHelper`. The helper and its nested
contracts are a narrow exception to the Bluetooth namespace filter; unrelated
Android utilities remain excluded. Inherited public methods on this helper are
included. Newly referenced parent/callback types are ranked into the remaining
queue instead of waiting behind unrelated managers. This is metadata inspection,
not an SDK connection or a guessed callback implementation.

The controller, its parent and main feature interfaces have larger individual
allocations, within a 28 KB factory section and the same five-second overall
cooperative limit. Up to 96 relevant method definitions are captured per class.
Service-intent and initialization methods, and static methods returning the
controller type, rank first. Definitions include explicit constructor/method/field
omission markers. Identical complete captured schemas are referenced across
packages; truncated, cancelled, failed or differing schemas never deduplicate.
This leaves later packages room for their distinct SDK implementations without
assuming that a shared class name means identical code or an active backend.

### Factory status connection

v1.0.17 adds **Check factory Bluetooth status**. This explicitly returns to the
dashboard and starts one eight-second session there. Stay on the cockpit until
the saved-result message, then reopen Bluetooth Diagnostics to review/send it.
Opening a panel, leaving the app or destroying the activity cancels the check
and releases its binding. Returning to the cockpit does not restart it.

The fixed, installed phone SDK must pass the existing read-only factory APK
policy, including every split. After checking the observed method signatures,
the adapter invokes `BTController.getInstance(Context)` and the controller's
`getServiceIntent()` to obtain its real routing definition. This action differs
from **Inspect vendor SDK entry points**, which still never initializes classes
or invokes methods. The SDK receives an application-context wrapper that rejects
SDK-owned service bindings, starts/stops, receiver registrations and ordinary
broadcast sends during instance/intent construction. An unexpected initialization
requirement produces a fixed failure outcome instead of leaving hidden work.

Only action/package/component/categories are copied from the returned intent;
opaque extras, URI grants and Parcelable data are not forwarded or decoded.
Data/type/selector/clip intents are rejected. Exactly one enabled, exported,
permitted service must resolve, and its APKs must also pass factory trust checks.
BreakDash makes the resulting explicit service binding itself. It checks the
Binder descriptor against `com.bt.BTFeature`, then uses that APK's own generated
`BTFeature$Stub.asInterface(IBinder)` proxy for all status calls. No vendor Parcel
layout, transaction number or Binder callback is implemented by BreakDash.

Reads are limited to six observed boolean power/profile getters and four
zero-argument integer state/feature getters. Signatures must match primitive
return types and instance methods. Missing/failing getters produce fixed
unavailable markers; exception messages, object dumps, PINs, device identities,
contacts and phone data are excluded. Integer vendor state meanings remain
unknown. SDK routing names, stage timings, fixed outcomes and the allowed status
values are saved in a separate 1.6 KB section within the 40 KB Bluetooth cap.

The adapter does not use the SDK's connect/disconnect helpers or register its
PIM callbacks: it owns the Android connection directly. It issues no discovery,
pairing, power, reset, phone, music or SPP/vehicle commands. A successful status
check establishes factory-service access, not BLE scanning or KW906 compatibility.
It does not require root, local ADB or Android's DUMP permission.

SDK initialization and synchronous reads run on one temporary HandlerThread,
with a main-thread deadline and cleanup. Android binding callbacks remain on
the main thread. A synchronous vendor call may ignore interruption; late results
are discarded, the session unbinds and requests worker shutdown, and a global
in-flight guard prevents accumulating more workers until that call returns.
No periodic retry, polling or always-on service is introduced.

v1.0.18 fixes the transition from Diagnostics to this dashboard check. Dialog
dismissal callbacks update the panel stack asynchronously; the earlier immediate
visibility check could return before those callbacks, leaving no saved attempt.
The tap now immediately records WAITING_FOR_DASHBOARD, then starts exactly once
from the resumed/empty-stack visibility callback. A three-second handoff deadline
records DASHBOARD_UNAVAILABLE instead of silently dropping the request. Starting
and cancellation are recorded too. Backgrounding or opening another panel clears
pending work, so it cannot start on a later unrelated return to Home. The actual
vendor session retains its separate eight-second deadline.

A temporary dashboard notice shows preparation, checking and completion while
this user-requested operation is active. It uses the right column, leaving map
and navigation controls available; opening a panel hides it. Feedback carries
bounded request/start/completion events in addition to the saved status section.

### Factory discovery and BLE options

v1.0.19 adds **Scan factory Bluetooth** on the Devices page. It reuses the verified
dashboard handoff and factory service/proxy. One explicit native search lasts
12 seconds after startup, within a 20-second overall session. It invokes only the
observed boolean `startSearchDevice_BC()` and `stopSearchDevice_BC()` methods with
validated signatures. A rejected start does not issue stop. An accepted start
owns one stop attempt, including cancellation while the start RPC returns or
while waiting. Leaving the dashboard unregisters the receiver and unbinds; an
already executing SDK call may finish its stop cleanup afterward. The global
single-worker guard remains held until that worker exits.

Discovery broadcasts are selected from the installed SDK's immutable public
String action constants containing discovery/search/found markers. This live
action reads those protocol constants, separately from metadata-only inspection.
No actions are fabricated, and missing verifiable event definitions produce an
explicit unsupported outcome before a search starts. Incoming broadcasts are
untrusted: the trusted SDK class loader is assigned before extras are decoded,
malformed Parcelables are caught, and only actual SDK `BTDevice` objects (or
bounded lists/arrays of them) use observed String `getName`/`getAddress` methods.
The SDK's real Parcelable implementation handles its wire format. Other values
are not interpreted as devices. No phonebook/PIM Binder callback is implemented.

At most 32 unique, validated addresses/names are held in activity memory for
local display after reopening Bluetooth. They never enter preferences or feedback.
Reports contain only counts, fixed outcomes, protocol definitions and broad
payload shapes; no extra keys, raw payloads, device strings or exception messages.
Unreadable or unsupported event payloads make results partial; zero parsed models
does not prove zero nearby devices. Factory transport remains unverified. Selecting
a factory result enables an explicit Android **Test BLE connection** by its address,
which bypasses Android scanning without assuming that the factory result is BLE.

**Scan BLE** retains the modern Android scanner, now handling bounded batched
results too. **Scan BLE · compatibility** uses the legacy Android callback API as
a separate explicit 12-second scan. It may reach the same firmware stack and is
not a guaranteed workaround for error 3. Legacy API rejection is reported; accepted
legacy scans have no asynchronous error callback, so an empty result is explicitly
inconclusive. Modern and compatibility summaries are saved independently, along
with observation counts. Scans do not run concurrently or retry automatically.
Raw advertisements are never retained or uploaded. Names/addresses remain local.

Factory discovery also performs bounded metadata-only BLE/GATT class inspection
of the actual resolved service APK, rather than assuming that the phone SDK's
classes describe every backend. These definitions are saved separately; no
unverified vendor BLE method is executed. Generic vendor BLE/GATT support and
KW906 connectivity remain unproven until device evidence establishes them.

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
Synthetic SDK fixtures also verify that constructor/static-factory/inherited and
callback definitions are captured without class initialization or invocation;
small text budgets and cancellation remain bounded. Capture regressions verify
method/stage attribution, listener/authorization/read failures and privacy.
Controller-parent regressions cover the narrow utility-helper exception,
inherited lifecycle/callback definitions, static factories with other names,
and refusing to deduplicate incomplete or differing schemas. Fixtures contain
synthetic definitions only; factory APKs and real feedback never enter CI.
Factory-status regressions verify primitive getter allowlisting, signature
rejection, private-data/control exclusion, exception privacy, cancellation,
deadline precedence, exactly-once cleanup and rejection of late completions.
Dashboard-handoff regressions reproduce delayed dismissal callbacks and a partly
closed panel stack, check exactly-once launch, and cover cancellation, timeout,
duplicate taps and refusal to restart when the dashboard is visited later.
Discovery regressions validate the public protocol-constant allowlist, primitive
control signatures, typed SDK-model decoding, bounded/cyclic payload handling,
private getter exclusion and accepted-search stop ownership across cancellation.

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
9. Run **Inspect vendor SDK entry points** and check for initialization, helper,
   callback and BLE/GATT definitions, or explicit missing/budget results. Capture
   app status followed by optional ADB status and confirm both remain in feedback.
   A stopped ADB daemon should report the listener stage; failed authorization
   should report the connection stage. Neither result proves BLE incapability.
10. Check the concrete controller and actual helper appear before general SDK
    classes, with inherited connection/cleanup signatures. Later packages should
    reference only identical complete captured schemas and retain differences.
    New scan, manager and inspection captures should show the producing version;
    saved older scan timestamps must not imply a fresh post-update attempt.
11. Tap **Check factory Bluetooth status**, wait on the dashboard for the result,
    reopen Diagnostics and send feedback. Check success/partial and fixed failure
    stages. Open another panel or background during SDK preparation, binding
    and reading: confirm cancellation, no binding while covered, no late status
    overwrite and no automatic restart. Repeat after completion and check that
    stalled work reports busy without adding workers. Confirm factory phone,
    music, radio, maps and USB behavior remain intact on the head unit.
12. From stacked Settings/Bluetooth panels, tap the factory status action and
    confirm the dashboard notice progresses past preparation. Reopen Diagnostics
    and check a new requested/starting/completed or explicit failure result; it
    must not remain Not checked. Reopen a panel or background during handoff,
    wait beyond three seconds, then return: no delayed automatic check should run.
13. Close other Bluetooth/OBD scans and temporarily disconnect the scanner from
    the phone. Run **Scan factory Bluetooth**, wait on the dashboard until the
    saved-result notice, reopen Devices and review factory rows. Run modern BLE
    and compatibility BLE separately; check their distinct summaries and explicit
    error/empty/inconclusive states. If the scanner appears in factory results,
    select it and explicitly test BLE. Test closing/backgrounding while the native
    start or stop RPC is pending, during discovery and while decoding a result;
    verify no repeated workers, no retained binding and no late history overwrite.
    Send feedback even when the event contract or BLE path is unavailable.

The next implementation follows the reported interfaces and transport. Do not
guess vendor callback layouts, characteristic UUIDs, pairing PINs or OBD frames.
