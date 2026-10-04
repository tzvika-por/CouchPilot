# My Remote

Native Android remote for LG 55UK6700YVD, Xiaomi Google TV Box S (3rd Gen), and Samsung HW-M360. One dark Hebrew/RTL-capable screen handles sources, sound, navigation, media, digits, channels and the yes+ Last Channel macro. Production adapters use LG SSAP/WSS, Google TV Remote Service v2/TLS and Samsung Bluetooth Classic RFCOMM. An optional native Android Bluetooth HID Xiaomi adapter can avoid the blocked Wi-Fi path. No ADB, Developer Options, vendor CLI or online computer is required by the shipped app.

## Current evidence

- **PROVEN physically:** MyRemote LG discovery, registration, Connected, Power Off and HDMI 3/Xiaomi → HDMI 2/Mac mini switching; MyRemote Samsung setup, volume up/down and mute/unmute; Samsung Audio Remote volume/mute on optical D.IN; ADB yes+ key/macro semantics. The Galaxy/Xiaomi Bluetooth bond was established, Xiaomi retained the phone accessory, and MyRemote eventually reported Connected after manual recovery.
- **FAILED physically / unresolved:** prior LG wake and phone-to-Xiaomi TCP reachability. Earlier LG input denial is historical; input switching now succeeds after the customer refreshed and approved authorization with build 8a1c428.
- **FAILED historical / stability issue:** the Galaxy was absent from the earlier TV accessory search. Phone-initiated pairing later established the bond, but initially connected briefly and entered a reconnect loop. Automatic recovery improvements are implemented and require no new bond; their physical stability is not yet proven.
- **FAILED latest physical check:** revised LG WOL targeting did not turn the TV back on after successful power-off.
- **IMPLEMENTED / remaining limits:** MyRemote Xiaomi LAN pairing/keys/wake, Bluetooth key delivery/long press/standby wake, and the revised reconnect stability. Source switching succeeded; the exact direct-versus-launcher path was not captured.
- **OPEN QUESTION:** LG's precise authorization difference from the working CLI, the selective Wi-Fi restriction toward the powered-on Xiaomi. The current implementation is a validation build, not a completed useful release.

## Build and automated verification

Use Gradle Wrapper (9.6), Android SDK Platform 37 / Build Tools 36, and a compatible JDK (17+). Dependencies download on first build. Set ANDROID_HOME or ignored local.properties sdk.dir. The development SDK/cache under /private/tmp are disposable; reinstall or configure a durable SDK if they are cleared. Versions are pinned in build.gradle.kts/app/build.gradle.kts. No globally installed Gradle is required.

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

The second command needs an emulator/development device. Only debug JVM unit tests are enabled in the current AGP configuration. Release assembles an **unsigned** validation APK; signing/distribution are not configured or performed. Local protocol-server tests need loopback sockets. They never send TV/soundbar commands or require physical devices. See docs/DEVICE_VALIDATION.md for exact latest results.

## Setup and behavior

LG: discover/select or enter hostname/IP; ordinary TV approval stores the key and TLS pin. Existing working keys are reused. A command denial keeps the connection; input launch fallback uses only TV-reported metadata. Refresh authorization is for rejected registration, not an instruction to repeat the failed input test. Household wake MACs are matched to this installation's UUID. Wake is not marked confirmed merely because UDP was sent.

Xiaomi: discover Google TV or enter hostname/IP, then enter the TV code. All available IPv4/IPv6 addresses, command port and reachable address persist. Network reachability is a prerequisite; no hard-coded Xiaomi host is used. Powered-on Mac Ethernet now reaches both Remote Service ports over IPv4/IPv6; Wi-Fi still fails. Native socket cancellation and real mutual-TLS command/pairing sessions are automatically verified, not physically proven.

Xiaomi Bluetooth fallback: choose Bluetooth in Xiaomi setup and allow Bluetooth access. Open Xiaomi’s Remotes & accessories → Pair accessory screen, then tap Pair Xiaomi in MyRemote and approve Android’s pairing prompts. The phone initiates bonding to the configured Xiaomi address; finding the phone in the TV accessory list is not required. Keep MyRemote foreground. Android owns the bond; later openings reuse it. Brief Bluetooth disconnects retain HID registration and back off at 3/6/12 seconds; after three attempts the app waits for the TV or an explicit Retry. Only a connection lasting at least 30 seconds resets this budget. Connected setup shows the saved-pairing state and disables the pairing action. A connected phone keyboard/mouse may be disconnected while the HID remote is active. This route, including long OK and wake, is not physically proven; see [XIAOMI_BLUETOOTH_PROTOCOL.md](docs/XIAOMI_BLUETOOTH_PROTOCOL.md).

Samsung: Set up soundbar → allow Bluetooth on Android 12+ → select the existing paired Samsung. Android stores the bond; MyRemote stores the selection and connects directly to the control service. Keep D.IN and close Samsung Audio Remote to avoid competing control sessions. No new Bluetooth scan/location permission, A2DP playback or generic AVRCP workaround is used. Forget removes MyRemote selection without removing the Android bond.

Connections are active while the app is foreground and close in background. Source buttons use stable HDMI IDs. Watch yes+ selects HDMI 3; launching yes+ or waking it automatically is withheld until reliable. Global Xiaomi keys remain available on all sources. Volume/mute always targets Samsung. Sound buttons use speaker icons with localized accessibility labels. After a valid mute status reply, the middle icon offers the opposite action (unmute when muted); unknown status uses a neutral mute toggle. There is no Power Off All.

## Project map

- domain/: intentions, typed failures, state, controller ports and macro routing.
- lg/: SSDP, pairing storage, WSS/SSAP correlation, input strategy and wake.
- google/: NSD, Keystore identity, persistent endpoints, TLS and Polo/protobuf.
- hid/: independent keyboard/consumer reports, native Bluetooth profile, selection and lifecycle; domain route chooses one streamer adapter.
- samsung/: Bluetooth bond setup, RFCOMM transport, protocol and lifecycle.
- network/: per-socket LAN selection and broadcast calculation.
- diagnostics/: allowlisted structured events; no keys, codes, addresses or raw packets.
- ui/ and values-iw/: one Compose remote/setup and Hebrew localization.
- data/: fake adapters exclusively for development/tests/previews.
- docs/: [architecture](docs/ARCHITECTURE.md), [hardware](docs/HARDWARE_SPECIFICATIONS.md), [device evidence](docs/DEVICE_VALIDATION.md), protocol references and [execution plan](docs/EXECUTION_PLAN.md).

No push, tag or release has been performed.

Watch yes+ currently selects LG HDMI_3/Xiaomi and makes Xiaomi the active remote target. Automatic launch of the yes+ app is not implemented. The latest customer correctness session reported working behavior except LG wake; see DEVICE_VALIDATION for the scope of that aggregate report.
