# My Remote

A native Android remote app for an LG webOS TV, Xiaomi TV Box S (3rd Gen), and Samsung HW-M360 soundbar. LG control now uses native webOS SSAP over a secure WebSocket, and Xiaomi control uses Android TV Remote Service v2 over TLS. The soundbar still uses a simulated adapter. MyRemote's physical LG and Xiaomi control await validation.

## Build

Requirements: Android SDK Platform 37, Build Tools 36.0.0, JDK 17 or newer compatible with Gradle 9.6, and internet access for Gradle dependencies on first build.

On the development Mac used for this milestone, the SDK is installed under `/private/tmp/myremote-android-sdk`; an ignored `local.properties` file points Gradle to it. If that temporary directory is cleared, install the SDK elsewhere and update `local.properties` or set `ANDROID_HOME`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

The instrumented Compose test can be compiled without a device using `./gradlew :app:assembleDebugAndroidTest`. Running it needs an emulator or connected Android phone:

```sh
./gradlew :app:connectedDebugAndroidTest
```

## Project map

- `app/src/main/java/com/myremote/app/domain/`: device ports, actions, state, routing, and the yes+ Last Channel macro.
- `app/src/main/java/com/myremote/app/google/`: NSD discovery, Keystore identity, pairing storage, TLS transport, connection lifecycle, and protocol codec.
- `app/src/main/java/com/myremote/app/lg/`: SSDP discovery, webOS registration, pinned WebSocket transport, SSAP requests, input switching, and Wake-on-LAN.
- `app/src/main/java/com/myremote/app/data/`: fake adapters for tests and previews.
- `app/src/main/java/com/myremote/app/RemoteViewModel.kt`: UI to controller lifecycle and setup state.
- `app/src/main/java/com/myremote/app/ui/`: dark Compose remote screen and theme.
- `app/src/main/res/values-iw/`: Hebrew text; English is the default locale.
- `docs/ARCHITECTURE.md`: architecture and integration plan.
- `docs/DEVICE_VALIDATION.md`: observed hardware facts and open questions.
- `docs/GOOGLE_TV_PROTOCOL.md`: wire behavior, security model, and references.
- `docs/LG_WEBOS_PROTOCOL.md`: webOS discovery, registration, SSAP, power, and current limits.

The app does not use ADB, developer mode, or Wireless Debugging. To set up Xiaomi, open **Set up Xiaomi**, select the discovered TV box or enter its host manually, then enter the six-character code shown on the TV. Pairing persists across app restarts. The first physical test found no Xiaomi through discovery and manual pairing failed at TCP connect; see [device validation](docs/DEVICE_VALIDATION.md).

To set up LG, open **Set up LG TV**, select a discovered webOS TV or enter its address, then approve My Remote on the TV if asked. The client key and TV certificate pin remain in app-private storage and are reused on later launches. The source buttons query the TV's actual input IDs before switching. LG discovery uses SSDP and may not cross network isolation; manual host entry remains available. LG power-on sends Wake-on-LAN packets to the installation's saved wired and Wi-Fi MAC addresses and still needs physical validation.
