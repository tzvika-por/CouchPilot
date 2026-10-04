# Optional Xiaomi Bluetooth control

## Why this adapter exists

**PROVEN — network:** with Xiaomi powered on, Mac Ethernet reaches both Google TV Remote Service ports over IPv4 and IPv6; Mac Wi-Fi cannot. LG remains reachable from Wi-Fi. A separate existing VPN TV endpoint presents a different TLS certificate and cannot be assumed to be Xiaomi. Exact AP isolation/bridge cause remains open. Google TV LAN code and credentials remain intact.

**IMPLEMENTED / physical evidence:** Android's native Bluetooth HID Device profile supplies a phone-to-TV route independent of IP discovery, LAN filtering, ADB, developer options, a PC or a vendor service. It is an explicitly selected method, not a silent replay of a LAN command. The customer established a Galaxy/Xiaomi bond and later reported working correctness in aggregate. Latest confirmation after eba8ec5 proves Xiaomi Off and disappearance of background TV blinking; wake fails. Individual key timings and standby receiver behavior remain unmeasured.

## Setup and storage

Xiaomi setup offers Wi-Fi and Bluetooth without changing the main remote. Choosing Bluetooth selects the household's observed Xiaomi Bluetooth address, 02:00:00:00:00:02, from the current en0 Remote Service TXT bt attribute. XiaomiInstallation holds that environment configuration outside reusable protocol/transport code. Existing bonded TVs with recognizable names can instead be explicitly selected. Unknown hosts are disconnected and never receive reports. No local scan, location permission, name/class change, router change or audio connection is made. Bond creation occurs only after the user explicitly taps Pair Xiaomi; Android owns all confirmation prompts.

For first association the user opens Xiaomi’s ordinary Remotes & accessories → Pair accessory screen, then taps Pair Xiaomi on the phone. MyRemote calls the public BluetoothDevice.createBond() for the configured host. The asynchronous BOND_BONDED broadcast triggers BluetoothHidDevice.connect(); only the HID connection callback establishes Connected. A successful createBond return is not a bond or connection. Approval/rejection stays with Android; no PIN is guessed and no pairing confirmation is bypassed. The TV search screen is used to make the TV available for association, not to select the phone from its filtered results.

Registration can wait while instructions are read; the 120-second initial association deadline starts with the explicit Pair Xiaomi action. Repeated taps are disabled/serialized. Rejection, immediate request failure and timeout terminate that attempt without an automatic unbonded pairing loop. Retry remains available. Later foreground launches reuse the saved selection and Android bond; no intentional repeat pairing. MyRemote stores only connection mode/name/address in private preferences with backups disabled. Android owns the bond keys. Forget removes the app selection, not the OS bond. Switching back to Wi-Fi preserves Google credentials.

API 28+ and a phone Bluetooth HID Device service are required. Android 12+ requests BLUETOOTH_CONNECT. The revised association requires neither phone discoverability nor BLUETOOTH_ADVERTISE. Earlier versions use normal BLUETOOTH/BLUETOOTH_ADMIN permissions. Bluetooth hardware remains optional for installation. No BLUETOOTH_SCAN or location permission is added. The OEM may not expose this profile; unsupported/service-in-use errors stay visible with Wi-Fi fallback.

## Native lifecycle

The public profile proxy registers one independent SDP report descriptor with keyboard subclass, consistent with its actual keyboard report. A scoped receiver listens only for the selected host’s protected Android bond-state broadcasts. It is unregistered during native transport cleanup. SDP subclass does not guarantee a change to the phone’s inquiry Class of Device. Registration and connection are established only by platform callbacks, never by a successful request-return boolean. An unbonded host has a bounded initial association window; a bonded host has a connection deadline. Established sessions have no arbitrary length limit. Bonded transient disconnects keep the profile/SDP registered, clear the command session and retry the existing profile at 3/6/12 seconds. After three automatic attempts the app waits passively for a TV-initiated connection or explicit Retry. A connection lasting at least 30 seconds resets the automatic retry budget; brief incoming connections do not. Permission, unavailable-profile or security failures stop and clean up; first association never enters an automatic pairing loop.

A connected-device foreground service keeps Android UID importance eligible for HID when the Activity is hidden. Backgrounding or ViewModel recreation preserves registration. Explicit Disconnect/service destruction and selection changes release reports, disconnect the selected host, unregister the app, close the profile proxy and stop the callback executor. A late proxy callback closes its own proxy; generation/ownership guards prevent cancelled session cleanup affecting a new selection. While registered, Android disables the phone's HID Host role; an attached Bluetooth keyboard/mouse may disconnect. Setup explains this product side effect. Samsung uses separate RFCOMM and is not deliberately disconnected or routed through HID; simultaneous radio behavior remains unproven physically.

## Reports and control mapping

Independent descriptor: report 1 is an 8-byte keyboard input with 1-byte LED output, report 2 is a 2-byte little-endian Consumer Control array, report 3 is a 1-byte System Control array. The Android sendReport argument excludes the report-ID byte. GET_REPORT returns the current pressed/released data; declared LED output supports GET/SET without injecting keys. Invalid type/ID/size or boot-mode requests are rejected; this composite device uses report protocol.

| Intention | HID page / usage |
|---|---|
| Up / Down / Left / Right | Consumer 0042 / 0043 / 0044 / 0045 |
| OK | Consumer 0041, Menu Pick |
| Home / Back | Consumer 0223 / 0224 |
| Play-pause / Rewind / Fast-forward | Consumer 00CD / 00B4 / 00B3 |
| Channel Up / Down | Consumer 009C / 009D |
| Digits 1–9 / 0 | Keyboard 001E–0026 / 0027 |
| Sleep / Wake | Generic Desktop 0082 / 0083 |

The reviewed kernel mappings and Android 14 Generic.kl map Menu Pick to Linux KEY_SELECT and Android DPAD_CENTER, rather than ordinary keyboard ENTER. This distinction matters for yes+ long OK. Short presses hold 60 ms then release; long presses hold 650 ms then release. All writes serialize through a whole press. Cancellation attempts release in a non-cancellable finally block. The existing domain Last Channel macro remains long CENTER then short CENTER; no macro lives in Compose.

HID has no reliable reported TV power state or current app. Sleep/wake reports require a live Bluetooth connection. Sleep now has physical success; wake fails. A wake report cannot be delivered while the HID connection is absent; this does not prove every alternative wake mechanism impossible. No absolute wake or successful state change is claimed merely because Android accepts a report. The dedicated Xiaomi Off action now exposes the existing System Sleep report independently of HDMI selection. The off effect is physically confirmed; wake remains failed and exact standby radio behavior is unknown. App launch is not provided by this adapter; the redundant Watch yes+ shortcut is removed. Samsung power belongs to its separate RFCOMM adapter.

## Automated evidence and physical boundary

Pure tests independently validate all usage vectors, report lengths from parsing the actual SDP descriptor, host GET/SET handling, callback-driven readiness, stored-selection reuse, explicit connection cleanup, no unbonded retry loop, permission stop, reconnect delay, stale session replacement, cancellation release and production domain Last Channel routing. Compose tests exercise permission-before-pairing, registration gating, explicit TV selection, prevention of duplicate requests, saved-bond Connected state and return to LAN. Native emulator instrumentation also registers the actual public profile/SDP without a radio peer or bond request. Association tests cover explicit createBond, callback-only connection, cancellation/rejection and stored bond reuse. Existing Google mutual-TLS, LG WSS/UDP, Samsung and RTL tests remain required gates.

**PROVEN — customer report:** the Galaxy/Xiaomi OS bond was created, the phone remained in Xiaomi’s accessory list, and MyRemote eventually reported Connected after manual recovery. The screenshot also reports LG and Samsung Connected concurrently. **OPEN QUESTION:** durable automatic recovery, received key reports, OEM key layout/long-press behavior, simultaneous command delivery and standby wake. A Mac TCP test or Android emulator cannot establish these facts. No command success is inferred from the Connected report. Customer verification is reserved for one final connection/correctness session, not exploratory router work or incremental UI tests.

## Primary references

- [Android BluetoothHidDevice](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice): public API, callback semantics, foreground registration, role restriction, report framing and permission.
- [SDP settings API](https://developer.android.com/reference/android/bluetooth/BluetoothHidDeviceAppSdpSettings): independent descriptor registration.
- [Android Bluetooth device setup](https://developer.android.com/develop/connectivity/bluetooth/find-bluetooth-devices): saved bonds and bounded consent-based discoverability.
- [USB HID Usage Tables 1.5](https://www.usb.org/sites/default/files/hut1_5.pdf): keyboard, Consumer Control and System Control usages.
- [AOSP Linux HID input mapping](https://android.googlesource.com/kernel/common/+/refs/heads/android-mainline/drivers/hid/hid-input.c) and [Android 14 Generic.kl](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-14.0.0_r1/data/keyboards/Generic.kl): semantic key mapping evidence. These do not establish Xiaomi's vendor layout.

No third-party implementation, descriptor source, vendor asset or GPL source is copied into the app. Protocol values and public API behavior are independently implemented.


**PROVEN — native API on emulator:** the production Android adapter registers the real HID service/SDP on API 35's virtual Bluetooth stack and re-registers after background/foreground using the saved selection. That earlier registration evidence did not validate discoverability or the TV accessory filter. There is no bonded TV or physical report receipt in this evidence; S23+ profile availability and Xiaomi compatibility remain unproven.

## Failed phone visibility and corrected association — 2026-10-04

**FAILED — physical:** the customer searched Xiaomi’s Bluetooth accessory list and could not find the Galaxy phone. An unidentified headset-like “HK BTA 10” entry was visible. No identity, pairing, HID connection or received key report is established by that entry. No further exploratory customer test was requested during the investigation.

**PROVEN — implementation/platform review:** the old app registered an uncategorized SDP HID service, waited for TV-initiated association, started its 120-second deadline before the discoverability action, and ignored the discoverability activity’s result. AOSP TV Settings filters inquiry results by Class of Device (peripheral with keyboard/pointing/remote/gamepad/joystick bits, or supported audio categories). BluetoothHidDevice registration installs the service record; it does not offer a public way to force the phone’s inquiry class to peripheral. Changing SDP subclass alone cannot be claimed to fix visibility.

**STRONGEST HYPOTHESIS:** TV accessory classification filtering can exclude the phone despite a registered HID service. An expired registration/discoverability window is another supported code-level failure path. Xiaomi’s actual filter and the Galaxy’s radio state during the failed attempt were not captured, so the exact physical cause remains unproven.

**EARLIER IMPLEMENTATION / subsequent physical report:** phone-initiated, Android-approved bonding avoids reliance on TV inquiry selection. The configured TXT bt address is observed installation data; the subsequent customer report supports successful association to the selected installation target. Reliable automatic recovery and key delivery remain unproven. Android 35 emulator registration, state tests and UI tests cannot establish Galaxy/Xiaomi interoperability.

Additional primary references (reviewed independently; no source copied):

- [AOSP TV InputDeviceCriteria](https://android.googlesource.com/platform/packages/apps/TvSettings/+/refs/heads/main/Settings/src/com/android/tv/settings/accessories/InputDeviceCriteria.java) and [BluetoothDeviceCriteria](https://android.googlesource.com/platform/packages/apps/TvSettings/+/refs/heads/main/Settings/src/com/android/tv/settings/accessories/util/bluetooth/BluetoothDeviceCriteria.java): inquiry Class of Device filtering.
- [AOSP TV BluetoothDevicePairer](https://android.googlesource.com/platform/packages/apps/TvSettings/+/1f1474537e3f3c1682829959db3a33f8ccc3d8f4/Settings/src/com/android/tv/settings/accessories/BluetoothDevicePairer.java): supported input/audio criteria and TV pairing availability. Xiaomi vendor behavior is not established by this reference.
- [Android createBond API](https://developer.android.com/reference/android/bluetooth/BluetoothDevice#createBond()): public asynchronous association, system consent and bond broadcasts. The transport-specific overload is API 37; this app uses the public no-argument method on earlier Android versions without reflection.
- [AOSP HidDeviceService](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/main/android/app/src/com/android/bluetooth/hid/HidDeviceService.java), [native HID registration](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/main/system/bta/hd/bta_hd_act.cc), and [SDP creation](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/main/system/stack/hid/hidd_api.cc): foreground restriction, registration and subclass service attribute. Android’s native SDP also advertises boot capability; the app currently handles report protocol and rejects boot-mode requests, so boot-only hosts are not supported.

## Established bond and reconnect instability — 2026-10-04

**PROVEN — customer report / screenshot:** the phone and Xiaomi briefly paired/connected, then repeatedly disconnected/retried. After closing the app/accessory search, Xiaomi retained the Galaxy as a saved accessory. Manual TV Connect and reopening MyRemote eventually produced Connected. The supplied main-screen screenshot shows LG, Xiaomi and Samsung all reporting Connected. This proves successful association and an eventual connected state, not remote key receipt, a measured stable duration or simultaneous command delivery.

**PROVEN — implementation defect:** the previous controller reset its retry counter on every Connected callback, so two-second connections could repeatedly restart the three-second delay forever. It also closed/unregistered the native profile after each transient loss. Removing the SDP/listener while the TV tries to recover can compete with TV-initiated reconnection. The initial physical disconnect reason has not been captured; foreground loss, host negotiation and radio behavior remain possible explanations rather than established causes.

**IMPLEMENTED / automated:** one native profile owns the foreground connection lifecycle. Transient bonded loss replaces only the command session; selected-host callbacks and SDP remain available. Three outgoing retries use 3/6/12-second backoff; only a stable 30-second connection or an explicit retry resets the budget. Exhaustion enters passive listening, allowing TV Connect to recover without another bond or service teardown. Fatal permission/profile/security errors, selection change and background still close resources. No failed key is replayed. A previous press cannot send its release into or close a replacement logical session. Setup distinguishes a saved bond from first pairing and disables Pair while Connected.

**NOT PHYSICALLY PROVEN:** the revised recovery policy’s stability against this Galaxy/Xiaomi pair, key receipt and standby behavior. No repeat customer pairing diagnostic is requested. The shared candidate APK is delivered under Expansion/Videos/MyRemote.apk for phone access without USB.


## Foreground service retention supersedes screen-only ownership

The remote no longer requires keeping its Activity on screen. The connectedDevice service keeps Android UID importance eligible for native HID while other apps are used. Normal screen stop/recreation does not unregister SDP, disconnect the host or initiate another bond; discovery alone pauses. A quiet service notification and in-app Disconnect control give explicit cleanup. Notification denial does not block the service. Force-stop, Bluetooth disablement, process/device shutdown or an actual transport loss can still disconnect; uninterrupted physical TV playback on the customer's Galaxy/Xiaomi remains unverified until normal usage confirms it. This update supersedes earlier foreground-screen-only instructions above. Primary AOSP/API/service references are in ARCHITECTURE.md.

Latest customer confirmation after eba8ec5: background flicker is gone and Off works; On fails. [Power-on research](POWER_ON_RESEARCH.md) records the public HID limits, exact-model lack of IR, conditional CEC direction and absence of a verified phone-only disconnected wake path. No new physical test is requested.
