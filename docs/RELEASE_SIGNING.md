# Release signing and upgrade continuity

The prepared public release is **1.0.0 / versionCode 3**, with applicationId **com.myremote.app** unchanged. Android requires a signing certificate for an installable APK/AAB. `assembleRelease` produces an unsigned, non-debuggable APK; the approved private local signing process signs that artifact with the permanent release identity only.

## Permanent public identity

Alias: `couchpilot-release`. Public certificate SHA-256:

```
610c73bee9b90fdea955bbf9a1a1f6d1f092feed3050b29847421cb68e418e7e
```

All future public releases use this signer (B) only. No old development key or private migration lineage is required by normal public release builds. Signing material is outside Git. The local signing credential is retrieved from macOS Keychain and passed through private inherited pipes, never printed, written to a password file, or placed in command-line values.

## Existing installation

The customer's Android 16 / API 36 installation completed the private A-to-B migration without uninstall. Application data, pairing/configuration and Android Keystore-backed data survived; the B-only 1.0.0-rc / code 3 validation upgrade and Google TV/Samsung volume smoke checks passed. The final 1.0.0 / code 3 artifact keeps that package and signer. Android accepts a same-versionCode replacement with the same signer; no uninstall or data clearing is required. No additional physical installation is part of artifact preparation.

The private bridge remains unpublished. Old-signer update/rollback authority was disabled. Its temporary signature-permission compatibility was required for AndroidX permission redeclaration; the validated B-only completion removed the old signing history/permission authority on API 36. Other differently signed development installations need their own deliberate transition; the public APK is not a universal migration bridge. Do not uninstall to bypass a signer mismatch without understanding the loss of saved pairing and Keystore data.

## Storage and distribution gates

Never put signing keys, passwords or private migration material in Git, shared artifact folders, logs or issue reports. Keep restrictive local permissions, a verified authoritative private keystore backup and an independent recoverable credential backup beyond a single Mac Keychain. Google Play App Signing is not configured.

Debug builds retain a local development identity for emulator/developer use. Public CI builds unsigned release artifacts without signing secrets and must not publish releases automatically. Preparing a signed APK does not authorize a tag, public upload or GitHub Release. The authoritative Drive keystore backup is complete and hash-verified, and an independent recoverable password backup exists outside macOS Keychain. These were confirmed by the product owner; no signing material is stored in this repository.

The LG WebSocket pre-assembly availability limitation has been reviewed and accepted; it is not claimed to be eliminated. See [security](SECURITY.md#lg-websocket-message-memory).
