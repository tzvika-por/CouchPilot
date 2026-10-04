# My Remote

Native Android remote for LG 55UK6700YVD, Xiaomi Google TV Box S (3rd Gen), and Samsung HW-M360. One dark Hebrew/RTL-capable screen handles sources, sound, navigation, media, digits, channels and the yes+ Last Channel macro. Production adapters use LG SSAP/WSS, Google TV Remote Service v2/TLS and Samsung Bluetooth Classic RFCOMM. An optional native Android Bluetooth HID Xiaomi adapter can avoid the blocked Wi-Fi path. No ADB, Developer Options, vendor CLI or online computer is required by the shipped app.

## Current evidence

Latest customer confirmation after eba8ec5: TV blinking on app switching is gone, all three Off controls work, and all three power-on attempts fail. [Power-on research](docs/POWER_ON_RESEARCH.md) documents LG mobile/network wake, Samsung optical/Bluetooth wake, Xiaomi limits and a concrete LG saved-MAC configuration gap. This supersedes earlier unproven off/flicker notes. The subsequent wake engineering candidate described below fixes the identified LG configuration gap and adds a bounded soundbar wake attempt; the customer has now tested that candidate and both LG and Samsung wake still fail.

- **PROVEN physically:** MyRemote LG discovery, registration, Connected, Power Off and HDMI 3/Xiaomi → HDMI 2/Mac mini switching; MyRemote Samsung setup, volume up/down and mute/unmute; Samsung Audio Remote volume/mute on optical D.IN; ADB yes+ key/macro semantics. The Galaxy/Xiaomi Bluetooth bond was established, Xiaomi retained the phone accessory, and MyRemote eventually reported Connected after manual recovery.
- **FAILED physically / unresolved:** prior LG wake and phone-to-Xiaomi TCP reachability. Earlier LG input denial is historical; input switching now succeeds after the customer refreshed and approved authorization with build 8a1c428.
- **FAILED historical / stability issue:** the Galaxy was absent from the earlier TV accessory search. Phone-initiated pairing later established the bond, but initially connected briefly and entered a reconnect loop. Automatic recovery improvements are implemented and require no new bond; their physical stability is not yet proven.
- **FAILED latest physical check:** revised LG WOL targeting did not turn the TV back on after successful power-off.
- **IMPLEMENTED / remaining limits:** Xiaomi Google LAN interoperability remains unproven. Bluetooth correctness was reported in aggregate and Off now works; exact key timing/recovery measurements are absent and wake fails. LG source switching succeeded; the exact direct-versus-launcher path was not captured.
- **OPEN QUESTION:** LG's precise authorization difference from the working CLI, the selective Wi-Fi restriction toward the powered-on Xiaomi. The current implementation is a validation build, not a completed useful release.

## Build and automated verification

Use Gradle Wrapper (9.6), Android SDK Platform 37 / Build Tools 36, and a compatible JDK (17+). Dependencies download on first build. Set ANDROID_HOME or ignored local.properties sdk.dir. The development SDK/cache under /private/tmp are disposable; reinstall or configure a durable SDK if they are cleared. Versions are pinned in build.gradle.kts/app/build.gradle.kts. No globally installed Gradle is required.

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

The second command needs an emulator/development device. The background-service suite requires POST_NOTIFICATIONS denied before instrumentation. Gradle's install step or a reused emulator may leave it granted. After the APK build, use the following setup on the isolated API 35 emulator (never revoke permissions on the customer's phone):

```sh
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell pm revoke com.myremote.app android.permission.POST_NOTIFICATIONS
adb -s emulator-5554 shell am instrument -w com.myremote.app.test/androidx.test.runner.AndroidJUnitRunner
```

The runner must finish with `OK` and the expected test count; a successful adb exit code alone does not establish test success. Only debug JVM unit tests are enabled in the current AGP configuration. Release assembles an **unsigned** validation APK; signing/distribution are not configured or performed. Local protocol-server tests need loopback sockets. They never send TV/soundbar commands or require physical devices. See docs/DEVICE_VALIDATION.md for exact latest results.

## Setup and behavior

LG: discover/select or enter hostname/IP; ordinary TV approval stores the key and TLS pin. Existing working keys are reused. A command denial keeps the connection; input launch fallback uses only TV-reported metadata. Refresh authorization is for rejected registration, not an instruction to repeat the failed input test. Household wake MACs are matched to this installation's UUID. Legacy missing MACs are migrated; manual-host setup can learn the UUID from the TV's secure hello response after registration. Metadata updates preserve the key/pin. Wake requires registered reconnection within 45 seconds, and a failed attempt cancels its reconnect job; a saved address error is distinct from an unconfirmed wake.

Xiaomi: discover Google TV or enter hostname/IP, then enter the TV code. All available IPv4/IPv6 addresses, command port and reachable address persist. Network reachability is a prerequisite; no hard-coded Xiaomi host is used. Powered-on Mac Ethernet now reaches both Remote Service ports over IPv4/IPv6; Wi-Fi still fails. Native socket cancellation and real mutual-TLS command/pairing sessions are automatically verified, not physically proven.

Xiaomi Bluetooth fallback: choose Bluetooth in Xiaomi setup and allow Bluetooth access. Open Xiaomi’s Remotes & accessories → Pair accessory screen, then tap Pair Xiaomi in MyRemote and approve Android’s pairing prompts. The phone initiates bonding to the configured Xiaomi address; finding the phone in the TV accessory list is not required. A connected-device service keeps MyRemote available while other apps are used; its quiet notification and in-app Disconnect control end the session explicitly. Android owns the bond; later openings reuse it. Brief Bluetooth disconnects retain HID registration and back off at 3/6/12 seconds; after three attempts the app waits for the TV or an explicit Retry. Only a connection lasting at least 30 seconds resets this budget. Connected setup shows the saved-pairing state and disables the pairing action. A connected phone keyboard/mouse may be disconnected while the HID remote is active. The customer reported working Xiaomi correctness in aggregate and now confirms Off; individual long-press timings remain unmeasured and wake fails; see [XIAOMI_BLUETOOTH_PROTOCOL.md](docs/XIAOMI_BLUETOOTH_PROTOCOL.md).

Samsung: Set up soundbar → allow Bluetooth on Android 12+ → select the existing paired Samsung. Android stores the bond; MyRemote stores the selection and connects directly to the control service. Keep D.IN and close Samsung Audio Remote to avoid competing control sessions. No new Bluetooth scan/location permission, A2DP playback or generic AVRCP workaround is used. Forget removes MyRemote selection without removing the Android bond.

Connections belong to a connected-device foreground service and stay active across app switching, screen lock and Activity recreation. Opening the remote starts the service; repeated visible starts preserve live sessions. Leaving the screen stops discovery only. Disconnect remote (in the app or notification) releases the connections without sending device power commands. Notification permission is optional for the service; the in-app control remains usable if it is denied. The service does not restart itself after process death or device reboot. Source buttons use stable HDMI IDs. The redundant Watch yes+ shortcut is removed. The Xiaomi source button selects HDMI 3; direct yes+ launch is unavailable through the working Bluetooth HID route. Global Xiaomi keys remain available on all sources. Volume/mute always targets Samsung. Sound buttons use speaker icons with localized accessibility labels. After a valid mute status reply, the middle icon offers the opposite action (unmute when muted); unknown status uses a neutral mute toggle. A dedicated Xiaomi Off button sends standby on the selected Xiaomi connection, independently of HDMI selection. The original power button identifies its current LG/Xiaomi target. Soundbar power sends the Samsung toggle once while connected and then pauses automatic reconnection, including after app restart. A Power tap while disconnected explicitly attempts one reconnect, bounded to 15 seconds, without sending a toggle or changing audio input; setup Retry remains available. Successful protocol reconnection does not prove physical wake. The customer now confirms both individual off effects; disconnected wake remains failed/unresolved. There is no Power Off All.

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

Watch yes+ has been removed at the customer’s request because it did not launch the app. Xiaomi source selection remains available. Automatic yes+ launch needs a usable app-launch protocol; Bluetooth HID only provides input reports. The latest customer correctness session reported working behavior except LG wake; see DEVICE_VALIDATION for the scope of that aggregate report.


Latest individual-power candidate: 96 passing JVM tests; 14 passing API 35 emulator tests; both APK builds, both lint variants (0 errors, 20 advisory warnings), Compose test APK compilation and resource/diff checks passed. The updated debug package is delivered as `/Volumes/Expansion/Videos/MyRemote.apk`, with byte-for-byte checksum verification and the existing signing identity. Individual Xiaomi standby and Samsung power-toggle effects remain physically unverified; LG wake remains failed.


Latest background-service candidate: 98 JVM tests and 17 API 35 emulator tests passed, including real native HID retention across Activity stop/recreation and notification-denied service operation. Both APK builds, both lint variants (0 errors, 20 advisory warnings), Compose compilation and resource/diff checks passed. The verified debug APK replaces the same shared `Videos/MyRemote.apk`. Actual disappearance of the customer’s TV blink is not inferred from emulator evidence.

## Wake engineering candidate

LG saved wake addresses now migrate without another pairing approval. Same-device discovery metadata and endpoint updates retain credentials; the pinned WSS hello identity can finish older manual-host configuration. An unexpected identity is rejected before sending the stored client key. Only this installation's verified UUID receives household MACs; other TVs remain unaffected. LG wake timeout stops its owned reconnect attempt.

Disconnected Soundbar power now requests one bounded RFCOMM reconnect and never follows it with a toggle, because reconnect may already wake Bluetooth Power On. Failure/cancellation restores automatic-connection suppression; ordinary app switching still cannot wake it automatically. This is a candidate for SPP-capable standby, not a physical success claim or an optical setting change. Optical Auto Power Link remains a TV/soundbar feature, requiring no app packet but device support/configuration. Xiaomi protocol and native background retention are unchanged. No UI redesign or new pairing is introduced.

Latest wake candidate: **115 JVM tests and 17 API 35 emulator tests passed**; both builds, both lint variants (0 errors/20 advisory warnings), Compose compilation, 98 matching localized strings and documentation checks passed. The verified debug APK is in `/Volumes/Expansion/Videos/MyRemote.apk`. LG and Samsung wake paths are implemented candidates; physical wake success remains unproven. Xiaomi disconnected wake remains unresolved.

### Latest wake result

The customer reports that `a8b9fcb` still does not wake the LG TV or Samsung soundbar. Existing control and background-retention successes remain accepted. No further code fix is proven; standby settings and wake delivery remain unresolved. See [power-on research](docs/POWER_ON_RESEARCH.md) and [device validation](docs/DEVICE_VALIDATION.md).
