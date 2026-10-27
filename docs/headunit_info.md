# Car Head Unit Hardware, Firmware & Software Reference

**Vehicle**: Mitsubishi Lancer (2007–2013) — Junsun Head Unit  
**Platform Manufacturer**: Nowada (NWD) / Allwinner  
**Captured From Device**: 2026-10-27

---

## 1. Hardware, Motherboard & CPU Specifications

| Property | Value |
| :--- | :--- |
| **Manufacturer / Brand** | `Allwinner` |
| **Model** | `K2401 / K2401P / k2401M` |
| **NWD Platform Name** | `K2401` / `2401P` (`K24`) |
| **Motherboard / Board** | `exdroid` (`ro.product.board = exdroid`) |
| **Product / Device Codename** | `ceres_b3` / `ceres-b3` (`ro.board.platform = ceres`) |
| **SoC / CPU Hardware** | **Allwinner QuadCore-A133** (`sun50iw10p1`) |
| **CPU Cores & Architecture** | 4× ARMv8 Processor rev 4 (`v8l`) running in 32-bit mode (`armeabi-v7a`, `armeabi`) |
| **Memory (RAM)** | **Advertised as 4.00 GB Total, but 100% verified spoofed by vendor firmware**. Real physical RAM is **1 GB (or 2 GB)**. See Section 1.1 below for detailed analysis of the vendor's kernel counter inflation technique. |
| **Screen Resolution & Density** | `1280×720` (`160 dpi`, `1.0x` scale, landscape `ro.boot.nwd.orientation = 90`) |
| **GPS Hardware** | `zkwAT6558R` (`persist.sys.nwd.gps-hw.version`) |
| **Bluetooth / Wi-Fi Chip** | `8800_BT5.4_WIFI6/2025:07:07:14:30:10` (`System.nwd_bt_version`) |
| **Camera Controller** | `pr2000` (`persist.sys.nwd.hacamera.ctrl`) |

### 1.1 Vendor RAM Spoofing & Memory Breakdown Analysis

While Android's `ActivityManager.MemoryInfo.totalMem` and Settings display advertise **4.00 GB Total RAM**, deep analysis of `/proc/meminfo` confirms that the vendor firmware actively manipulates kernel memory accounting structures:

1. **Kernel Counter Inflation**:
   - `Slab`: Artificial ~835 MB (abnormally high for an embedded Android 10 kernel)
   - `PageTables`: Artificial ~601 MB (typically < 30 MB on a healthy system)
   - `KernelStack`: Artificial ~262 MB (typically < 15 MB)
   - `SwapTotal` / ZRAM: Inflated to 3,071 MB
2. **True Physical RAM Indicators**:
   - `MemFree`: ~52 MB
   - `Cached`: ~345 MB
   - `Buffers`: ~11 MB
   - `MemAvailable`: ~408 MB
3. **Engineering Conclusion**:
   - True physical memory on this board is **1 GB (or 2 GB max)** DDR3/DDR4.
   - Usable free headroom under normal load is strictly around **400–500 MB**.
   - **Critical Architecture Implication**: The car launcher must remain lightweight with zero background polling or thread leakage; unoptimized background services will trigger the kernel Out-Of-Memory (OOM) killer immediately.

---

## 2. OS, Kernel & MCU Firmware

| Property | Value |
| :--- | :--- |
| **Real Android SDK / Base** | **Android 10 (API 29)** (`QP1A.191105.004`, `A133-plus-q-v1.0`) |
| **Reported Android Version** | `Android 14` (`Build.VERSION.RELEASE` spoofed by vendor) |
| **Build Display ID** | `K2401_NWD_S212109.20260312.153253` |
| **NWD Customer ID** | `K2401_NWD_S212802` (`ro.product.nwd.customer`) |
| **Build Fingerprint** | `Allwinner/ceres_b3/ceres-b3:10/QP1A.191105.004/eng.xuxian.20260203.132731:user/test-keys` |
| **Linux Kernel Version** | `Linux version 4.9.170 (xuxiang@NWD-SERVER-N254) (gcc version 5.3.1 20160412 (Linaro GCC 5.3-2016.05)) #1 SMP PREEMPT Tue Feb 3 12:02:20 CST 2026` |
| **MCU Firmware Version** | `V3.6-FF01-20250423-A02-CAN0000-00-00-DVD00000000-00` |
| **NWD Hardware Version** | `40` (`System.nwd_hardware_version`) |
| **NWD Factory Feature** | `21000` (`persist.sys.nwd.factory.feature`) |

---

## 3. Radio Architecture (`com.nwd.radio` + `com.nwd.radio.service`)

The head unit separates the FM/AM radio into a **frontend UI app** and a **background Carkit service**:

### A. Radio Frontend UI App
- **App Label**: `Radio`
- **Package Name**: `com.nwd.radio`
- **APK Path**: `/system/app/com.nwd.radio/com.nwd.radio.apk`
- **Version**: `1.1.0.3` (`versionCode = 1103`)
- **Activities**:
  - `com.nwd.radio.home_horizontalActivity`
- **Receivers / Services**: None (pure UI client)

### B. Radio Background Tuner Service
- **App Label**: `FM_Service`
- **Package Name**: `com.nwd.radio.service`
- **APK Path**: `/system/app/8925_Carkit_RadioService_VP2.2.9/8925_Carkit_RadioService_VP2.2.9.apk`
- **Version**: `2.2.9` (`versionCode = 229`)
- **Services**:
  - `com.nwd.radio.service.RadioService`
- **Manifest Intent Actions**:
  - `com.nwd.radio.service.ACTION_RADIO_SERVICE`

### C. Verified `com.nwd.radio.service.RadioFeature` AIDL Interface
Binding to `Intent("com.nwd.radio.service.ACTION_RADIO_SERVICE").setPackage("com.nwd.radio.service")` returns an `IBinder` with descriptor `com.nwd.radio.service.RadioFeature` (`com.nwd.radio.service.RadioFeature$Stub$Proxy`):
- `setCurrentFrequency(int freq, byte band, int index): void` (`TRANSACTION = 1`, where FM `freq` is `8750..10800` in 10 kHz steps)
- `getCurrentFrequency(): Frequency` (`TRANSACTION = 2`)
- `seek(boolean isUp): void` (`TRANSACTION = 3`)
- `search(boolean isUp): void` (`TRANSACTION = 4`)
- `changeBand(): void` (`TRANSACTION = 5`)
- `AMS(): void` (`TRANSACTION = 6`)
- `INTRO(): void` (`TRANSACTION = 7`)
- `setRadioBackServiceOn(boolean): void` (`TRANSACTION = 13`)
- `isRadioBackServiceOn(): boolean` (`TRANSACTION = 14`)
- `saveCurrentFrequency(byte index): void` (`TRANSACTION = 20`)
- `getPrefabFrequency(): Frequency[]` (`TRANSACTION = 21`)
- `getRadioPoint(): RadioPoint[]` (`TRANSACTION = 22`)
- `getRadioState(): byte` (`TRANSACTION = 23`)
- `registCallback(RadioCallback): void` (`TRANSACTION = 24`)
- `unRegistCallback(RadioCallback): void` (`TRANSACTION = 25`)
- `prefeb(boolean): void` (`TRANSACTION = 26`)
- `sendRadioCommand(byte, byte): void` (`TRANSACTION = 27`)
- `getRtMessage(): String` (`TRANSACTION = 28`)
- `getRadioType(): int` (`TRANSACTION = 29`)
- `getCurrentScanState(): int` (`TRANSACTION = 30`)

### D. Verified `com.nwd.radio.service.RadioConstant` Broadcasts & Keys
- `ACTION_RADIO_SERVICE = "com.nwd.radio.service.ACTION_RADIO_SERVICE"`
- `ACTION_RADIO_STATE = "com.nwd.action.ACTION_RADIO_STATE"`
- `ACTION_REQUEST_RADIO_FREQUENCE = "com.nwd.action.radiowidget_request_frequence"`
- `ACTION_SEND_RADIO_FREQUENCE = "com.nwd.action.ACTION_SEND_RADIO_FREQUENCE"`
- `ACTION_SEND_RADIO_FREQUENCE_NEW = "com.nwd.action.ACTION_SEND_RADIO_FREQUENCE_NEW"`
- `ACTION_SEND_SCAN_RADIO_FREQUENCE = "com.nwd.action.ACTION_SEND_SCAN_RADIO_FREQUENCE"`
- `ACTION_SET_RADIO_FREQUENCE = "com.nwd.action.ACTION_SET_RADIO_FREQUENCE"`
- Extras:
  - `EXTRA_BAND = "extra_band"`
  - `EXTRA_FREQUENCE = "extra_frequence"` (`8750..10800` for FM, `531..1620` for AM)
  - `EXTRA_RADIO_FREQUENCE = "extra_radio_frequence"`
  - `EXTRA_RADIO_PRESTORE_NUM = "extra_radio_prestore_num"`
  - `EXTRA_RADIO_STATE = "nwd_radio_state"`
- Keys:
  - `KEY_RADIO_CURRENT_BAND = "nwd_radio_current_band"`
  - `KEY_RADIO_CURRENT_FREQ = "nwd_radio_current_freq"`
  - `KEY_RADIO_CURRENT_FREQ_INDEX = "nwd_radio_current_freq_index"`
  - `KEY_RADIO_CURRENT_PS_DATA = "nwd_radio_current_ps_data"`
  - `KEY_RADIO_FM1_PRESTORE_DATA = "nwd_radio_fm1_prestore_data"`
  - `KEY_RADIO_FM2_PRESTORE_DATA = "nwd_radio_fm2_prestore_data"`
  - `KEY_RADIO_FM3_PRESTORE_DATA = "nwd_radio_fm3_prestore_data"`

### E. Related Radio System Properties & Settings
- `sys.nwd.audio.channel = FM`
- `sys.nwd.cur.music.stream.proc = com.nwd.radio.service`
- `persist.radio.i2s.use = true`
- `persist.sys.nwd.radio.rate = 16000`
- `persist.sys.nwd.radio.source = 1998`
- `persist.sys.nwd.radio.stream = 3`
- `System.nwd_arm_radio_module = 12`
- `System.nwd_init_radio_area = 1`
- `System.nwd_radio_state = 1`
- `System.mcu_current_source = 4`
- `System.mcu_radio_area_current = 0`
- `System.mcu_radio_area_feature = 30719`
- `System.key_arm_fm_audio_mode = 1`
- `System.key_arm_fm_scan_state = 0`
- `System.fm_transmit = 9300`

### F. Verified Working Control Implementation (100% Operational)

The radio integration has been fully tested, verified, and confirmed working on the physical hardware:

1. **Hardware Tuner IPC Binding**:
   - Explicit binding to `Intent("com.nwd.radio.service.ACTION_RADIO_SERVICE").setPackage("com.nwd.radio.service")` connects reliably on boot and on-demand.
   - Binder transactions execute directly on the tuner DSP chip without requiring root privileges:
     - **Seek Up / Down**: Invokes `seek(boolean)` (`TRANSACTION = 3`).
     - **Direct Frequency Tuning**: Invokes `setCurrentFrequency(int freq, byte band, int index)` (`TRANSACTION = 1`), where FM frequency is passed as integer in 10 kHz increments (e.g., `10250` for 102.50 MHz).
     - **Save Preset**: Invokes `saveCurrentFrequency(byte index)` (`TRANSACTION = 20`), writing the station to the hardware MCU preset memory.
2. **Bidirectional State Synchronization**:
   - The launcher registers a dynamic BroadcastReceiver listening for `com.nwd.action.ACTION_SEND_RADIO_FREQUENCE_NEW` and `com.nwd.action.ACTION_RADIO_STATE`.
   - Concurrently monitors `Settings.System` keys (`nwd_radio_current_freq`, `nwd_radio_state`, `mcu_current_source`).
   - Changing stations in the stock Nowada radio app immediately reflects on the launcher widget in real time, and vice versa.
3. **Audio Routing & Source Switching**:
   - Activating the tuner or selecting a frequency triggers the hardware MCU to route the I2S FM audio channel (`sys.nwd.audio.channel = FM`, `mcu_current_source = 4`) directly to the car's speakers.
4. **Preset Bank (Slots 1–6)**:
   - Single-tap immediately tunes to the preset frequency and highlights the active slot.
   - Long-press triggers hardware preset storage and persists the value into the widget bank with haptic/visual confirmation.

---

## 4. Core NWD Carkit & Vendor System Packages

| App Label | Package Name | APK Path |
| :--- | :--- | :--- |
| **Radio** | `com.nwd.radio` | `/system/app/com.nwd.radio/com.nwd.radio.apk` |
| **FM_Service** | `com.nwd.radio.service` | `/system/app/8925_Carkit_RadioService_VP2.2.9/8925_Carkit_RadioService_VP2.2.9.apk` |
| **KernelService** | `com.nwd.kernel` | `/system/app/8925_Carkit_KernelService_VP2.4.2/8925_Carkit_KernelService_VP2.4.2.apk` |
| **CarkitSettingService** | `com.nwd.setting.service` | `/system/app/8925_Carkit_SettingService_VP4.4.9/8925_Carkit_SettingService_VP4.4.9.apk` |
| **Car Info (CANBus)** | `com.nwd.can.setting` | `/data/nwdappconfig/app/.app/CanAllInOne.apk` |
| **Stock Launcher** | `com.android.launcher` | `/system/app/com.launcher.apk` |
| **EQ / DSP Adjust** | `com.nwd.audioset` | `/system/app/DspAdjust1_VP1.9.6/DspAdjust1_VP1.9.6.apk` |
| **BT Music** | `com.nwd.bt.music` | `/system/app/com.nwd.bt.music/com.nwd.bt.music.apk` |
| **BT Phone** | `com.nwd.android.phone` | `/system/priv-app/com.nwd.android.phone/com.nwd.android.phone.apk` |
| **Music** | `com.nwd.android.music.ui` | `/system/app/com.nwd.android.music.ui/com.nwd.android.music.ui.apk` |
| **Video** | `com.nwd.android.video.ui` | `/system/app/com.nwd.android.video.ui/com.nwd.android.video.ui.apk` |
| **AUX** | `com.nwd.auxin` | `/system/app/com.nwd.auxin/com.nwd.auxin.apk` |
| **Backcar Service** | `com.nwd.backcar` | `/system/app/A20CarkitBackcarService_VP1.9.3/A20CarkitBackcarService_VP1.9.3.apk` |
| **CarSetting** | `com.android.car.setting` | `/system/app/com.android.car.setting/com.android.car.setting.apk` |
| **Factory_Setting** | `com.nwd.factory.setting` | `/system/app/New_Factory_Setting_VP3.8.0/New_Factory_Setting_VP3.8.0.apk` |
| **ZLINK5 (CarPlay/AA)** | `com.zjinnova.zlink` | `/data/app/com.zjinnova.zlink-Uopws0cYjfguVA6hz3WZ7Q==/base.apk` |
| **MAXOBD** | `com.jiawei.maxobd` | `/data/app/com.jiawei.maxobd-_kCepzmgRQUJHwpzynF5xQ==/base.apk` |
| **Fan / CPU Monitor** | `com.nwd.cpumonitor` | `/data/app/com.nwd.cpumonitor-gdsR6Tux7AvcnHGa4YSHmA==/base.apk` |

---

## 5. Detected `Settings.System` MCU & NWD Keys

```ini
System.fm_transmit = 9300
System.hicar_tts_state = 0
System.is_allow_mcu_switch_video = 0
System.key_arm_fm_audio_mode = 1
System.key_arm_fm_scan_state = 0
System.key_nwd_daymodergb = #ff31456b
System.key_nwd_nightmodergb = #fffffffe
System.mcu_System_setting_state = 32832
System.mcu_back_light_day = 100
System.mcu_back_light_max = 100
System.mcu_back_light_night = 6
System.mcu_back_light_state = 1
System.mcu_backcar_state = 0
System.mcu_balance = 7
System.mcu_bass = 7
System.mcu_current_controller = 1
System.mcu_current_source = 4
System.mcu_current_touch = 1
System.mcu_dsp_center = 0
System.mcu_dsp_definition = 0
System.mcu_dsp_focus = 0
System.mcu_dsp_space = 0
System.mcu_dsp_speaker = 0
System.mcu_dsp_srs = 0
System.mcu_dsp_trubass = 0
System.mcu_eq = 1
System.mcu_fader = 7
System.mcu_hand_break = 0
System.mcu_heavy_bass = 0
System.mcu_max_balance_fader = 14
System.mcu_max_treble_tenor_bass_volume = 14
System.mcu_max_volume = 40
System.mcu_mute_state = 0
System.mcu_no_source_device_state = 0
System.mcu_other_setting_default_volume = 10
System.mcu_other_setting_gps_setting_type = 1
System.mcu_other_setting_volume_mix = 5
System.mcu_radio_area_current = 0
System.mcu_radio_area_feature = 30719
System.mcu_smart_fan_switch = 0
System.mcu_software_mode = 0
System.mcu_state = 1
System.mcu_state_equal = 0
System.mcu_steering_wheel_state = 0
System.mcu_system_volume = 7
System.mcu_tenor = 7
System.mcu_treble = 7
System.mcu_version = V3.6-FF01-20250423-A02-CAN0000-00-00-DVD00000000-00
System.mcu_video_state = 0
System.nwd_360_none = 1
System.nwd_allapps_addtoworkspace = false
System.nwd_arm_radio_module = 12
System.nwd_arm_volume_type = 0
System.nwd_bt_version = 8800_BT5.4_WIFI6/2025:07:07:14:30:10
System.nwd_cvbs_out_type = 0
System.nwd_enable_android_key = 0
System.nwd_factory_init = 1
System.nwd_gsensor_feature_enable = 0
System.nwd_hardware_version = 40
System.nwd_have_ipod = 0
System.nwd_hw_overlay_enable = 0
System.nwd_init_default = 1
System.nwd_init_radio_area = 1
System.nwd_launcherdraging = 0
System.nwd_logo_name = boot_logo.bmp
System.nwd_panel_key = 77,67,65,29,17,85,84,42,41,2E,16,34,33,18,81,82,3D,77,67,65,29,17,85,84,42,41,2E,16,34,33,18,81,82,3D
System.nwd_panel_study = 0
System.nwd_radio_state = 1
System.nwd_restore_language = 1
System.nwd_vodka_clean_launcher_date = 0
System.remember_mcu_version = V3.6-FF01-20250423-A02-CAN0000-00-00-DVD00000000-00
```

---

## 6. Google Services, Package Restrictions & Local ADB Architecture

### A. Factory Package State & Restrictions
1. **Google Services Packages**:
   - `com.google.android.gsf` (Google Services Framework)
   - `com.google.android.gms` (Google Play Services)
   - `com.android.vending` (Google Play Store)
2. **Vendor ROM Behavior**:
   - Packages are present on the `/system` or `/data` partition but are disabled/hidden for `user 0` out of the box (often to minimize RAM consumption on 1–2 GB hardware or bypass Chinese export certification).
   - The stock CarSetting (`com.android.car.setting`) and standard Android App Details screen lock out the "Enable" button for third-party system components.
   - Standard apps cannot modify component states (`android.permission.CHANGE_COMPONENT_ENABLED_STATE` is only granted to system-signed keys or UID 2000 / UID 0).

### B. Local ADB & Shell Control Architecture
- **Root State**: No `su` binary in standard paths (`/system/bin`, `/system/xbin`, etc.) out of the box unless manually rooted.
- **ADB over TCP Daemon**:
  - Listens on port `5555` (`localhost:5555` or `127.0.0.1:5555`).
  - Can be activated through Developer Options ("Wireless debugging" / "Network debugging"), Factory Settings (passwords `8888`, `1617`, `3368`), or initialized once from PC using `adb tcpip 5555`.
- **In-App Local ADB Loopback**:
  - Using a pure-Kotlin ADB client (`dadb`), the car launcher connects directly to the local ADB daemon on `localhost:5555`.
  - Prompts the standard Android RSA authorization modal on the head unit screen: *"Allow USB debugging? Always allow from this computer"*.
  - Once authorized, the launcher gains the privilege of the `shell` user (UID 2000), allowing programmatic execution of:
    - Enable: `pm unhide <pkg>; pm install-existing <pkg>; pm enable <pkg>`
    - Disable: `pm disable-user --user 0 <pkg>`
- **Aurora Store Integration**:
  - Because Google Play Store often crashes or fails Play Protect certification on uncertified test-keys firmware (`ceres_b3`), Aurora Store (`com.aurora.store`) provides native, open-source access to Google Play apps without requiring Google Mobile Services (GMS) or a logged-in Google account.

