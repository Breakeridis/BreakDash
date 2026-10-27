# Settings, Updates and About

Implemented in v1.0.2 for the 1280 × 720 head unit. This replaces the combined utility/dialog design in original specification sections 3.6–3.8.

| Dashboard action | First screen | Further actions |
| --- | --- | --- |
| Settings | Left rail: Appearance, Maps, Apps, Device | Night mode switch; online maps switch/providers; Aurora presence and Google switches; launcher, Android settings, health, advanced tools |
| Updates | Installed/latest versions, status, check action | Download only when available, What's new, compact source link |
| About | Name/version and three tiles | Report a problem, Device info, Credits |

Overview screens fit without scrolling on the reference head unit. Small phone landscape windows can scroll the rail and content independently. Tabs are 80 dp high, main panel actions 80 dp high, and dashboard utility tiles 80 dp high. Labels are short; technical details open on separate pages.

Back dismisses one panel. Close dismisses the complete panel stack and cancels its work. Android Back also dismisses one panel. GPS/map/radio dashboard resources remain suspended while panels cover the cockpit. CPU/RAM sampling is restricted to the resumed, topmost Device health panel. Package state refreshes when the Google services page is shown or an action completes.

The Maps connection screen explains the network/location requests before connecting; provider endpoints and extra details remain available through Provider details. Saving changed providers disconnects maps until reconnection. Map, update-source, and report configuration are separate forms and preserve other configuration.

Since v1.0.7, the Apps tab shows Aurora's read-only installed switch and an Open/Install action alongside it. Installation is always confirmed by Android; the presence indicator never disables or uninstalls Aurora. Google Services Framework, Play Services and Play Store have individual switches directly in Apps. Confirmed enabled states have green switches and an explicit On label; Off, missing and hidden states have distinct text. Switches reset to the actual package state while a change is pending and refresh after completion or failure. Shell output and copyable commands live in Google service tools, together with the bulk switch and Local ADB. Package state is queried only when shown or after an explicit change, without polling.

Night mode and online maps use the same labeled switches with 80 dp rows and at least 64 dp switch touch targets. Enabling maps still opens the existing consent screen and leaves the switch Off until Connect maps is chosen. Disabling maps clears the active route and disconnects providers. Day mode, app shortcuts, Android settings and other one-time actions remain clearly labeled.

Updates hides Download & install before a successful result, during a check, after a failed check, and when no newer compatible APK is available. Hidden actions and their spacers take no layout space. Check for updates remains available, and What's new appears once release information is available. A release with no newer version shows Up to date. A newer version without a matching ABI shows Update unavailable. The repository source is a single 60 dp link with a smaller label that opens its separate configuration form. BreakDash Dev offers a browser download of the signed release after a successful lookup, which is a separate package. The production updater still verifies its own package, higher version code, signature, and supplied checksum before opening Android's installer.

Reports follow note → preview → upload. The preview includes the note and the captured report. Sending uses that same capture; it does not silently collect another one. Since v1.0.4, **Upload for 3 hours** encrypts to Uguu and returns a complete share link, with a one-time key in its fragment. **Send to my server** still requires a receiver/public key. Both stay disabled until capture completes. Temporary sharing needs no server setup and has explicit Copy/Share actions; full links are kept in memory rather than preferences or logs. There is no GitHub issue storage. See [temporary reporting](temporary-reports.md).

Since v1.0.5, **Send feedback** is the primary 80 dp action: it encrypts an attachment for a fixed ntfy topic and fixed recipient public key, eliminating per-report link sharing. **Temporary link** and **My server** are secondary alternatives. A successful fixed-inbox upload asks the user to request a check within three hours. Recipient credentials are retained outside source/APKs; see [fixed feedback](ntfy-feedback.md).

Device checks after installing the signed APK:

1. Verify all three utility tiles and the update badge fit on both head unit and phone.
2. Switch Settings tabs, open children, use Back, and use Close from several levels deep.
3. Connect/disconnect maps; save a changed map provider and verify reconnection is required.
4. Return from Android settings or the installer and verify the parent page refreshes.
5. Open Device health, background the app, return, and close it; verify no sampling remains on other pages.
6. Confirm current release, newer release, failed check, and development-build update messaging.
7. Preview a diagnostic capture with a note and confirm sending remains explicit and setup requirements are explained.
8. Verify Aurora present/missing, Google enabled/disabled/hidden/missing, and mixed bulk states. Tap and drag switches; failed or denied changes must retain the actual package state. Return from the installer or Android settings and check that presence/status refreshes.
9. Cancel map consent and confirm the switch stays Off. Connect, disconnect, and switch themes; check contrast in both themes and scrollability on phone landscape screens.
