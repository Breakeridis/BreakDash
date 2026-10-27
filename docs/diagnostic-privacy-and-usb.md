# Diagnostic privacy, USB and Home navigation

Updated in v1.0.6 after physical head-unit feedback.

### v1.0.8 follow-up

The vendor Home issue remained observable, so task separation alone is not treated as a confirmed hardware fix. Dashboard entry is now passive: it registers the documented radio observers/broadcast receiver and reads settings, but does not bind the tuner, query RT, or send a state-request broadcast on resume. Commands bind only following an explicit tap, time out after eight seconds if unconnected, and release when the cockpit is hidden. Frequency/PS still depend on the fields the firmware actually exposes; RT becomes available after a dashboard command connects the tuner. No undocumented Parcelable layout, firmware property write or forced foreground loop is introduced.

Feedback now includes up to 32 persisted app-only lifecycle/focus/radio events, the most recent Java crash's exception types and stack frames, installed-app certificate fingerprints, and the last update validation result. These bounded records remain in app-private storage across restarts and are sent only through the existing explicit preview/send flow. Crash messages and intent extras/URLs are omitted. The normal Android crash handler still runs. Native crashes, process kills and firmware Home routing may leave no Java exception; absence of a crash record is not proof of correct behavior.

The updater requests `GET_SIGNING_CERTIFICATES` first and falls back to `GET_SIGNATURES` when the vendor returns no certificates. Both are public [Android package-manager APIs](https://developer.android.com/reference/android/content/pm/PackageManager#GET_SIGNATURES). Android's [PackageInfo contract](https://developer.android.com/reference/android/content/pm/PackageInfo#signatures) says certificate ordering can vary; comparison uses exact nonempty fingerprint sets. Missing or different signers remain errors. No key rotation is introduced. A head unit blocked by the old **Missing APK signature** check needs one manual APK installation over the current app before it can use the fallback.

## Uploaded system capture

`ReportPrivacy` normalizes property/setting names before checking identity, account, credential, Bluetooth, registration/activation, network and location fields. It covers vendor spellings of Bluetooth MAC, local address, PIN code, paired phone name, MFi ID, registration code, eMMC CID, and network-derived city/coordinates. Tests use synthetic values, never a real report.

The parser groups multiline `getprop` records before masking sensitive values. Free-text filtering additionally masks common IP/MAC/coordinate/email/URL/credential forms, with a final pass over the assembled uploaded capture. Firmware/model/radio/USB class and device IDs remain useful. This cannot promise to understand every vendor-defined field, so preview remains explicit. User notes are included unchanged.

Raw **Full system & radio report** is a local inspector and may contain unredacted values. Upload actions use the separate cloud capture and the exact previewed note/report. No real report is committed or sent to CI.

## USB snapshot

USB diagnostics run only when collecting a report. They read:

- `UsbManager.getDeviceList()`: VID/PID, non-serial product/manufacturer/revision descriptors, device/interface classes and protocols, endpoint types/directions/packet sizes, and whether access was already granted.
- Accessory-mode manufacturer/model/revision, without serials or URI fields.
- Removable volume state and primary/emulated flags. Uploads omit labels and UUIDs. API 29 cannot reliably map these volumes to USB versus SD, so the report does not claim that association.
- Optional, read-only `/sys/bus/usb/devices` vendor/product/class/manufacturer/product descriptors, without serial attributes. This helps when vendor firmware hides a device from UsbManager. Permission failures are reported as unavailable.

Collection is bounded to 16 devices/kernel entries, 16 interfaces per device and 16 endpoints per interface, with a section size budget. There is no polling, device opening, interface claiming, access request, storage permission, user-file read, or USB attach auto-launch receiver. USB features are optional in the manifest, preserving phone installability.

Older USB system properties, such as MTP configuration, cannot identify previously attached host devices. A new capture must be taken while the device is connected. Missing descriptors do not prove no device exists because firmware permissions can hide it.

## Home fix and follow-up evidence

The explicit NWD Radio fallback previously omitted `FLAG_ACTIVITY_NEW_TASK`. It could place radio in the Home task rather than its own app task. All app launches now use NEW_TASK and RESET_TASK_IF_NEEDED, including radio, settings, browser and sharing; installer/system-source screens also get separate-task flags while preserving URI grants.

The launcher remains singleTask, with separate MAIN/HOME/DEFAULT and MAIN/LAUNCHER filters and clearTaskOnLaunch. New MAIN intents dismiss panels to reveal the cockpit. Dashboard Back stays at Home. No task is force-stopped and no radio power command is issued on Home.

Reports include preferred Android Home and own-app task metadata. A bounded 16-event in-memory trace records creation, resume/pause, new intents, and app launches, with action/component/flags only. It does not collect URI data, extras, or other apps' task histories. Settings → Device points to Home selection when Android prefers another launcher.

Check the head unit after installing:

1. Confirm BreakDash is selected under Settings → Device → Default launcher.
2. Open Radio, play audio, and press both bezel Home and radio's own Home. The cockpit should appear while audio continues.
3. Open other apps and Android settings; test Home versus Back, including when a BreakDash panel was previously open.
4. Reproduce any remaining issue, then send feedback with the new task/default-Home trace. Firmware keys that never deliver Home to BreakDash cannot be diagnosed from app code alone.
5. Keep the USB device connected and capture another preview. Check model identifiers/interfaces and removable storage state, with serials and private vendor fields masked.
