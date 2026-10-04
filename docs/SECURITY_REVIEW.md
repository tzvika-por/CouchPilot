# Code and security review — 2026-10-04

## Scope and outcome

Reviewed baseline `b21cfd21e15528efab968e0ce389fa69a7e23ada`: production adapters, discovery, protocol parsing, connection ownership, credentials, manifest/exported components, diagnostics, command routing and build dependencies. Fixes remain behind the existing controller abstraction. UI commands, HDMI mappings, wake mechanisms and the process-owned background session remain intact.

This is a source review with deterministic JVM tests, local HTTP/WSS simulators, Android emulator checks and dependency-advisory queries. It is not a penetration test of the customer's LAN, phone or TV firmware, and does not establish that every possible attack is prevented. No physical hardware test or router change was performed.

## Findings and fixes

| Finding | Impact before this review | Fix |
| --- | --- | --- |
| LG description redirects | A LAN advertisement could supply a same-host URL that redirects an automatic GET to another endpoint, bypassing the host check. | Reject URL credentials/non-HTTP schemes/different hosts; disable redirects; send no credentials. WSS redirects are also disabled, preserving the selected endpoint. Local server tests prove the redirect destination is never contacted. |
| Discovery-driven trust reset | A conflicting untrusted UUID at a saved TV's address could clear its grant/pin and reopen first-use trust. | Reject conflicting identity at the same pinned endpoint, retain the existing grant/pin and avoid a connection/registration attempt. Explicit Forget is required for a replacement at that address. Optional HTTP metadata cannot override the service-advertised UUID. Regression tests verify no transport opens. |
| Incomplete backup exclusions | `root` is a distinct backup domain and did not explicitly exclude SharedPreferences. Some vendors transfer app data despite `allowBackup=false`. | Explicit exclusions for every supported credential/device-protected storage domain in legacy, cloud and device-transfer rules; retain `allowBackup=false`. Android resource tests check all domains. |
| LG grant stored as plaintext | The app sandbox protected it, but copying readable preference files exposed the reusable TV grant. | AES-256-GCM with a fresh random IV, 128-bit authentication tag, versioned envelope and fixed application context as AAD; wrapping key belongs to Android Keystore. Existing grants migrate atomically and retain their TV certificate pin and device configuration. No plaintext fallback is used for network registration. |
| Late Xiaomi pairing after Forget/cancel | A handshake finishing concurrently with cancellation could persist configuration after it had been removed. | Serialize generation checks with grant persistence and invalidation. Stale attempts cannot commit; deterministic persistence tests cover cancellation and Forget. |
| LG metadata parser incompatible with Android | Emulator testing proved the old JAXP feature setup throws on Android, causing every optional description to fall back. | Portable SAX parser; strict UTF-8 decoding; mandatory DTD/entity-declaration rejection before parsing; external resolver rejects all resources. Provider security flags add defense in depth when available. Limits: 64 KiB, depth 32, 1,024 elements, 256 characters per identity field. JVM and actual Android tests cover normal XML and rejection. |
| Discovery resource/response handling | Description reads could outlive scans; repeated advertisements caused redundant HTTP work; slow trickles could extend the scan; default metadata routing could differ from discovery routing. | Track/disconnect the active HTTP request on stop; reject stale socket startup; release only owned/held multicast locks; one description per host, at most 32 hosts; monotonic shared scan budget with bounded I/O; metadata and UDP use the selected LAN network. Bad metadata falls back to the sender record. |
| NSD duplicates/floods/restarts | Duplicate found callbacks accumulated work; restarting could overlap an unresolved legacy Android resolution. | At most 64 service appearances per scan, duplicate work suppressed, lost/reappeared services receive fresh tokens. Keep one resolver owner across stop/start until its callback completes; reject stale results and prevent a stale callback from clearing the next owner. |
| Certificate rejection reconnects | A changed/expired certificate was already rejected, but reconnect attempts continued automatically. | Certificate rejection is terminal for LG and Google TV; preserve the grant/pin and expose Error. Transient stream/socket failures retain ordinary backoff. |
| WebSocket close/request bookkeeping | Server close notification waited for final closure; concurrent request tracking had no explicit cap. | Reply to closing handshake and close the incoming channel promptly; serialize correlation bookkeeping and cap pending LG requests at 32. Late replies remain isolated. |
| Unnecessary production code/work | Fake controllers shipped in release; duplicate input-matching helpers existed only to support tests; every Google heartbeat repeated the ready callback; diagnostics recompiled regexes. | Fakes remain in debug previews and the configured unit tests, absent from release APK. One real input matching function serves controller/tests. Ready callback fires once per command session. Reuse the diagnostics identifier regex. |
| Gradle download integrity | Wrapper used official HTTPS but lacked a distribution checksum. | Pin the official Gradle 9.6.0 distribution SHA-256. Independently compare the tracked wrapper JAR to its official published checksum. Ignore local signing-key/environment-secret files. |

## Credential migration and failure behavior

The LG encrypted value replaces `client_key` with `client_key_encrypted` in one synchronous preferences transaction. Migration does not alter the TV grant, selected device, certificate pin or authorization revision. Store operations are serialized, including Forget, so migration cannot reattach an old grant to another device.

Ciphertext alteration, a missing/unusable wrapping key or a temporary Keystore provider failure prevents grant reuse and exposes authorization recovery. The stored device/pin remain. A temporary provider failure does not destroy the old grant; it can migrate after the provider recovers. Explicit Refresh/Forget removes both old and encrypted local grant fields. Normal successful migration requires no TV approval. JVM fixtures use a test-only AES key; Android tests exercise the real Keystore with isolated synthetic preferences.

Google TV's private RSA key remains in Android Keystore. Google preferences contain endpoint information and a public certificate pin. Android owns Bluetooth bond keys; only selected device information is stored by MyRemote. Credentials and pairing codes never enter structured diagnostics.

## Attack surface reviewed

- Commands go only through the selected, approved/pinned TV or selected Bluetooth host. Discovery retrieves identity metadata; it never registers, wakes or controls an advertised TV automatically.
- Google TV uses certificate-bound code pairing and pinned mutual TLS. LG uses WSS and certificate continuity; neither controller downgrades to plaintext commands. Trust managers are client-local and do not change system trust. Socket-local network selection does not change routes, VPN settings or process binding.
- The merged AndroidX startup provider is private; the profile-install receiver requires system/signature-level `android.permission.DUMP`. The private foreground service is non-sticky. The exported launcher Activity accepts no remote-command intent/deep-link contract. Notification PendingIntents are explicit and immutable. The HID bond receiver listens only to a system-protected Bluetooth action and verifies the selected host; no hidden Bluetooth APIs or unrelated input injection are added.
- No camera, microphone, contacts, location, broad storage, accessibility-service, root or shell-execution capability exists in the app. Bluetooth selection uses native bonds instead of broad scanning. No analytics, cloud account, remote command server or application backend is configured.
- Protobuf frames, Samsung framing, LG incoming queue and diagnostics already have bounds. Non-idempotent input/power commands are not automatically replayed after uncertain delivery. HID long presses release on the same session, including cancellation; wrong-host callbacks are rejected.
- Repository inspection found no tracked private signing key, actual LG grant or pairing code. Installation-specific UUIDs/MACs remain deliberately recorded configuration; they are identifiers, and should be considered before any future public repository publication.

## Remaining limits

1. **LG first-use trust:** LG self-signed/factory certificates are not public CA host identities; some generations share factory certificates. Pinning proves continuity, not independent authenticity on initial registration. TV approval and a trusted LAN remain part of the protocol's security model. The app must not silently replace a changed pin.
2. **Legacy Bluetooth:** Samsung control uses the vendor-compatible insecure RFCOMM API to an already bonded, selected address. The vendor protocol has no application-layer authenticated encryption. Bluetooth platform/firmware security remains a dependency; selecting a bond is not a claim that every legacy link is strongly authenticated.
3. **Development delivery:** the shared APK is signed with the existing development identity and is debuggable. Authorized ADB access can inspect/run code under its UID; Keystore encryption does not protect against a compromised unlocked OS/app runtime. A public production distribution needs a non-debuggable build and managed production signing. No release or signing-identity change is part of this review.
4. **Cleartext discovery:** Android permits cleartext because optional UPnP descriptions are HTTP. Only the same responding host is fetched, redirects/URL credentials are forbidden, and the request contains no grant. This global manifest permission is not itself a per-host firewall; future HTTP code must preserve this constraint. WOL/SSDP/mDNS are unauthenticated LAN mechanisms, used with bounded discovery and selected wake configuration.
5. **Availability:** OkHttp assembles WebSocket messages before MyRemote's message-size check. A compromised selected peer can still cause memory pressure. Very large/flooded LANs may hit deliberate discovery caps; legacy NSD resolution can delay a restarted scan until its outstanding callback completes. Manual host setup remains available.
6. **Revocation:** Forget removes local selection/grants; it cannot revoke a TV's remembered authorization or erase Android-owned bonds on that other device. Existing Google client identity remains Keystore protected. Device-side revocation is a separate feature/security responsibility if a phone is lost.
7. **Advisory coverage:** OSV returned no advisories for 104 resolved release-runtime coordinates at review time (including platform metadata). This is not proof against unpublished vulnerabilities, compiler/build-tool flaws or device firmware issues. Libraries were not upgraded merely because newer versions exist.

## Validation

Final validation results and delivered APK metadata are recorded below. No physical success is inferred from simulators/emulator tests; previously accepted device-control evidence remains valid. Automatic recovery after wake remains physically unconfirmed.

## Primary references

- [Android backup domains and device-transfer caveat](https://developer.android.com/identity/data/autobackup)
- [Android Keystore guarantees and compromised-device limits](https://developer.android.com/privacy-and-security/keystore)
- [Android security checklist](https://developer.android.com/privacy-and-security/security-tips)
- [Gradle Wrapper checksum verification](https://docs.gradle.org/current/userguide/gradle_wrapper.html)
- [Official Gradle distribution/wrapper checksums](https://gradle.org/release-checksums/)
- [OSV API query model](https://google.github.io/osv.dev/api/)
- [Known OkHttp certificate advisory and affected versions](https://github.com/advisories/GHSA-3cqm-mf7h-prrj) — affects versions before 4.9.2; this app pins 4.12.0.
- [Android insecure RFCOMM API](https://developer.android.com/reference/android/bluetooth/BluetoothDevice#createInsecureRfcommSocketToServiceRecord(java.util.UUID))

Protocol interoperability references remain in [LG webOS](LG_WEBOS_PROTOCOL.md), [Google TV](GOOGLE_TV_PROTOCOL.md), [Xiaomi Bluetooth](XIAOMI_BLUETOOTH_PROTOCOL.md) and [Samsung M360](SAMSUNG_M360_PROTOCOL.md).

## Completed review validation and delivery

Debug and unsigned release APKs build successfully. The aggregate `:app:test` runs every enabled unit-test variant (currently debug only): **152 tests in 22 suites pass**, with zero failures/errors/skips. Both lint variants report **0 fatal/errors and 21 advisory warnings**: target/plugin/library freshness and KTX style suggestions; the explicit checked preference commits are deliberate. The Compose test APK compiles. **23 actual API 35 instrumentation tests pass in 72.512 seconds**, including real Keystore migration/authentication, backup domains, safe XML parsing and the existing UI/HID/background suites. No physical device is required.

All **105 English/Hebrew resource keys match**, all 10 Android source XML files parse, 38 relative documentation links resolve, and `git diff --check` passes. The release APK contains neither fake controllers nor a debuggable application; its merged AndroidX startup provider is private and the exported profile-install receiver requires `android.permission.DUMP`. The debug APK contains the final credential/resolver classes; fake controllers remain available only for development. OSV returned no advisory IDs for 104 resolved release-runtime coordinates. The tracked Gradle wrapper JAR matches its official checksum; distribution integrity is pinned for future downloads.

The signed debug package atomically replaces `/Volumes/Expansion/Videos/MyRemote.apk`: **23,213,837 bytes**, SHA-256 **`43f64bf538f88022b6b1b2d9567c8143ab179806e10b804b9f95108ed4f822c4`**. APK verification preserves the existing signing certificate SHA-256 `dd57e000b36d89ac8c47f77f370dbc9b2e3494b1f40c0a0077647ee2d755ddd6`. Successful legacy LG migration retains the existing authorization without a fresh TV prompt. Existing setup and Bluetooth bonds can survive an ordinary update. This remains a development APK, not a public release. No push, tag, release or repeat product-owner device test. Previously accepted physical control/wake evidence remains intact; automatic post-wake recovery remains physically unconfirmed.

Completed Google pairing launches also check their attempt token under the same controller lifecycle monitor as Forget/cancel. Connection publication and ready/power callbacks require an active owner; a cancelled session cannot reconnect or publish stale Connected state after Forget.
