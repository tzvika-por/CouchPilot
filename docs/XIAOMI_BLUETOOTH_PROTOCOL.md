# Optional Xiaomi Bluetooth control

## Why this adapter exists

**PROVEN — network:** with Xiaomi powered on, Mac Ethernet reaches both Google TV Remote Service ports over IPv4 and IPv6; Mac Wi-Fi cannot. LG remains reachable from Wi-Fi. A separate existing VPN TV endpoint presents a different TLS certificate and cannot be assumed to be Xiaomi. Exact AP isolation/bridge cause remains open. Google TV LAN code and credentials remain intact.

**IMPLEMENTED BUT UNPROVEN physically:** Android's native Bluetooth HID Device profile supplies a phone-to-TV route independent of IP discovery, LAN filtering, ADB, developer options, a PC or a vendor service. This is an optional explicitly selected connection method, not a silent retry of a possibly delivered LAN command. It is a candidate workaround, not evidence that Xiaomi/phone radio interoperability succeeds.

## Setup and storage

Xiaomi setup offers Wi-Fi and Bluetooth without changing the main remote. Choosing Bluetooth selects the household's observed Xiaomi Bluetooth address, 02:00:00:00:00:02, from the current en0 Remote Service TXT bt attribute. XiaomiInstallation holds that environment configuration outside reusable protocol/transport code. Existing bonded TVs with recognizable names can instead be explicitly selected. Unknown hosts are disconnected and never receive reports. No local scan, location permission, name change, forced bonding, router change or audio connection is made.

For first association the phone becomes discoverable through Android's consent dialog for 120 seconds. On Xiaomi, ordinary Remotes & accessories pairing selects the phone's actual Bluetooth name (shown in setup) and approves the Android bond. Later foreground launches reuse the saved selection and Android bond; no intentional repeat pairing. MyRemote stores only connection mode/name/address in private preferences with backups disabled. Android owns the bond keys. Forget removes the app selection, not the OS bond. Switching back to Wi-Fi preserves both existing Google TLS pairing and the Bluetooth bond.

API 28+ and a phone Bluetooth HID Device service are required. Android 12+ requests BLUETOOTH_CONNECT and, only for visibility, BLUETOOTH_ADVERTISE. Earlier versions use normal BLUETOOTH/BLUETOOTH_ADMIN permissions. Bluetooth hardware remains optional for installation. No BLUETOOTH_SCAN or location permission is added. The OEM may not expose this profile; unsupported/service-in-use errors stay visible with Wi-Fi fallback.

## Native lifecycle

The public profile proxy registers one independent SDP report descriptor. Registration and connection are established only by platform callbacks, never by a successful request-return boolean. An unbonded host has a bounded initial association window; a bonded host has a connection deadline. Established sessions have no arbitrary length limit. Bonded disconnects retry at 3/6/12/24/30 seconds. Permission or unavailable-profile failures stop; first association never enters an automatic pairing loop.

Foreground-only registration matches Android's requirements. Background, selection change and ViewModel disposal release reports, disconnect the selected host, unregister the app, close the profile proxy and stop the callback executor. A late proxy callback closes its own proxy; generation/ownership guards prevent cancelled session cleanup affecting a new selection. While registered, Android disables the phone's HID Host role; an attached Bluetooth keyboard/mouse may disconnect. Setup explains this product side effect. Samsung uses separate RFCOMM and is not deliberately disconnected or routed through HID; simultaneous radio behavior remains unproven physically.

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

HID has no reliable reported TV power state or current app. Sleep/wake require a live Bluetooth connection and remain unproven; wake cannot work if the box disconnects Bluetooth in standby. No absolute wake or successful state change is claimed merely because Android accepts a report. App launch and soundbar power are not added by this adapter.

## Automated evidence and physical boundary

Pure tests independently validate all usage vectors, report lengths from parsing the actual SDP descriptor, host GET/SET handling, callback-driven readiness, stored-selection reuse, background cleanup, no unbonded retry loop, permission stop, reconnect delay, stale session replacement, cancellation release and production domain Last Channel routing. Compose tests exercise permission-before-visibility, registration gating, explicit TV selection and return to LAN. Existing Google mutual-TLS, LG WSS/UDP, Samsung and RTL tests remain required gates.

**OPEN QUESTION:** actual S23+ HID profile availability, Xiaomi accessory acceptance, OEM key layout/long-press behavior, foreground reconnection, simultaneous Samsung control and standby wake. A Mac TCP test or Android emulator cannot establish these facts. No new hardware success is claimed. Customer verification is reserved for one final connection/correctness session, not exploratory router work or incremental UI tests.

## Primary references

- [Android BluetoothHidDevice](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice): public API, callback semantics, foreground registration, role restriction, report framing and permission.
- [SDP settings API](https://developer.android.com/reference/android/bluetooth/BluetoothHidDeviceAppSdpSettings): independent descriptor registration.
- [Android Bluetooth device setup](https://developer.android.com/develop/connectivity/bluetooth/find-bluetooth-devices): saved bonds and bounded consent-based discoverability.
- [USB HID Usage Tables 1.5](https://www.usb.org/sites/default/files/hut1_5.pdf): keyboard, Consumer Control and System Control usages.
- [AOSP Linux HID input mapping](https://android.googlesource.com/kernel/common/+/refs/heads/android-mainline/drivers/hid/hid-input.c) and [Android 14 Generic.kl](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-14.0.0_r1/data/keyboards/Generic.kl): semantic key mapping evidence. These do not establish Xiaomi's vendor layout.

No third-party implementation, descriptor source, vendor asset or GPL source is copied into the app. Protocol values and public API behavior are independently implemented.


**PROVEN — native API on emulator:** the production Android adapter registers the real HID service/SDP on API 35's virtual Bluetooth stack and re-registers after background/foreground using the saved selection. Visibility remains gated by the registration callback. There is no bonded TV or physical report receipt in this evidence; S23+ profile availability and Xiaomi compatibility remain unproven.
