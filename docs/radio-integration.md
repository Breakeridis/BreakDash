# Factory radio status and controls

v1.0.10 restores visible-dashboard radio connection without sending `radiowidget_request_frequence`. A previous receiver attempted to read integer extras from a broadcast containing an unknown vendor Parcelable; Android unmarshalled the whole Bundle and threw `BadParcelableException`, restarting the launcher. Passive startup avoided that interaction but left devices without persistent frequency settings unable to show the current station.

## Actual vendor model code

The adapter resolves only the fixed `com.nwd.radio.service` package. It requires `FLAG_SYSTEM` plus canonical, readable, non-writable APK files under `/system/`, `/system_ext/`, `/product/`, `/vendor/`, `/odm/` or `/oem/`. User-installed packages, writable files and updates under `/data/app` are refused as executable model code. Every split is checked too. It uses a `PathClassLoader` with the boot/system parent, not a package context that runs a foreign AppComponentFactory, and never downloads, copies or uploads vendor APKs. A package name alone is not treated as trusted code provenance.

The installed factory `RadioFeature$Stub.asInterface(IBinder)` creates its real generated proxy. Its documented `getCurrentFrequency()` method deserializes the actual model with the vendor's own Parcelable implementation. BreakDash does not guess field order, presence flags, callback transactions or vendor Parcel layouts. `RadioModel` reads only explicitly named numeric frequency/band fields or getters on the resulting object. Conflicting aliases, unknown names, non-integral values, different units and invalid ranges remain unavailable. Band bytes retain their raw bits. Diagnostics record bounded model member names/types so an unsupported variant can be identified without inventing a mapping or dumping its object values.

Before accessing any incoming radio extras, the receiver assigns the trusted model class loader using Android's [setExtrasClassLoader](https://developer.android.com/reference/android/content/Intent#setExtrasClassLoader(java.lang.ClassLoader)). Missing/undecodable Parcelable dependencies, malformed extras and linkage failures are caught; they cannot propagate through this receiver as the previous unhandled exception did. If model code is unavailable, the receiver leaves opaque extras unread and continues using allowed settings/primitive controls. Reports include ignored-broadcast counts, bounded extra-key/type metadata and failure types, without arbitrary extra values or exception messages.

## Lifecycle and commands

- Opening the visible cockpit registers observers, binds the documented service, and reads frequency/RT once. Initial connection has an eight-second timeout. No startup power, tune, seek, activity launch or frequency-request broadcast is sent.
- Incoming radio events and relevant settings changes coalesce into status reads. Explicit tuner commands refresh status and get one bounded settling refresh. There is no periodic timer/poll, callback-layout guess or always-on service.
- Opening a panel, leaving the app or destroying it cancels scheduled reads and pending commands, releases the binding and leaves playback untouched. A new visible session obtains a fresh station snapshot.
- Primitive tune/seek/band/power/save transactions retain their documented implementation. Missing frequency/band settings no longer overwrite a valid vendor-model reading with zero. Preset saving requires a known station and band; empty preset slots are populated by holding them, rather than guessing a stock bank mapping.

The cockpit shows Connecting, Connected/On air, or Radio offline and offers a 60 dp refresh button. Previous/next seek and six hold-to-save local presets remain available. There is no new user permission, root requirement or ADB connection for this adapter.

## Verification

GitHub JVM tests use synthetic model classes to check named fields/getters, ambiguity refusal, units, failing getters, integer validation, byte normalization and metadata privacy. Factory-code trust tests reject package squatting, writable/unreadable files, outside paths and data-partition updates. Android build/lint and existing reporting, motion, signer and ABI checks run in GitHub Actions only.

The actual factory proxy, Parcelable dependencies, station availability, Home behavior and tuner audio require the head unit. Open Radio, tune a station, return Home, verify the station and green connection indicator, seek both ways, hold a preset and recall it, then retest Home and background/foreground. If a station remains unavailable, send feedback: the new model/schema/failure metadata identifies the remaining adapter limitation. No real feedback or factory APK is submitted to CI or source control.
