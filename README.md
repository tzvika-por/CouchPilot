# My Remote

A native Android remote app for an LG webOS TV, Xiaomi TV Box S (3rd Gen), and Samsung HW-M360 soundbar. The current milestone is a **simulated remote**: all controls run through in-memory fake adapters and do not control physical devices.

## Build

Requirements: Android SDK Platform 37, Build Tools 36.0.0, JDK 17 or newer compatible with Gradle 9.6, and internet access for Gradle dependencies on first build.

On the development Mac used for this milestone, the SDK is installed under `/private/tmp/myremote-android-sdk`; an ignored `local.properties` file points Gradle to it. If that temporary directory is cleared, install the SDK elsewhere and update `local.properties` or set `ANDROID_HOME`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The instrumented Compose test can be compiled without a device using `./gradlew :app:assembleDebugAndroidTest`. Running it needs an emulator or connected Android phone:

```sh
./gradlew :app:connectedDebugAndroidTest
```

## Project map

- `app/src/main/java/com/myremote/app/domain/`: device ports, actions, state, routing, and the yes+ Last Channel macro.
- `app/src/main/java/com/myremote/app/data/`: fake controller adapters used by this milestone.
- `app/src/main/java/com/myremote/app/ui/`: dark Compose remote screen and theme.
- `app/src/main/res/values-iw/`: Hebrew text; English is the default locale.
- `docs/ARCHITECTURE.md`: architecture and integration plan.
- `docs/DEVICE_VALIDATION.md`: observed hardware facts and open questions.

No ADB, developer mode, or Wireless Debugging is required by the app. This milestone declares no network or Bluetooth permissions because no production protocol adapter is active yet.
