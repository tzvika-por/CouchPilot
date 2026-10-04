# Contributing

Use JDK25 (validated), Android SDK37, and the checked-in Gradle9.6 wrapper. Android minimum26, target36; Kotlin/Java language target17. Configure SDK locally with ANDROID_HOME or ignored local.properties.

```sh
./gradlew clean assembleDebug assembleRelease test lint :app:lintRelease assembleAndroidTest
./gradlew connectedDebugAndroidTest
tools/check-font-scale.sh # emulator only; restores native font setting
```

Emulator suite uses an API35 image with Bluetooth enabled. Screenshot scenarios are synthetic UI fixtures, not physical-control proof. Compose density injection covers the main screen; the supplemental script uses Android's actual 200% font setting for native dialog windows and contextual tests. Native Bluetooth API tests need the emulator's connected-device support. No additional formatting/Detekt/Ktlint task is configured; follow existing Kotlin formatting and run `git diff --check`.

Keep Compose free of protocol logic. Domain controllers express intentions; adapters own transports, storage and lifecycle. Preserve applicationId, preference/Keystore identities and signing upgrade implications. Maintain per-device ordering, bounded pending work, cancellation/key-release cleanup and current-session ownership.

Never weaken established TLS pins, replay uncertain power commands, log packet bodies/credentials or silently trust an arbitrary discovery advertisement. New HTTP code must receive explicit scope/cleartext review. Include deterministic error, timeout, malformed-frame and reconnect tests for protocol changes; keep physical results separate.

Do not put actual pairing keys/codes, private keys, signing material, real device IDs/MACs, customer hostnames, Wi-Fi names or local paths in code, tests, screenshots or issues. Use documentation IPs and locally administered synthetic MACs. Contributions use Apache-2.0; declare upstream adaptations and required notices. Keep PRs focused and state validation and limitations.
