# Google TV Remote Service v2 adapter

## Discovery and connection

Android `NsdManager` discovers `_androidtvremote2._tcp.` services. Resolved entries retain the service name, all available IPv4/IPv6 addresses on Android 14+, and the advertised hostname on Android 16+; older releases provide the one address exposed by the legacy NSD API. Users may instead enter a DNS hostname, IPv4 address, or IPv6 literal. A Wi-Fi multicast lock is held only while the setup dialog discovers devices. The resolved service port is used for commands (normally 6466); pairing uses 6467. Discovery stops on dialog close or device selection.

Both pairing and command sockets try the last successful address, addresses supplied by NSD, and addresses resolved from the retained hostname, without assuming IPv4 is usable. Only TCP connect failures advance to another address. TLS starts after TCP selects an endpoint; a failed handshake or certificate pin check fails the connection instead of trying another address. Successful connections update the cached address while retaining the advertised hostname and command port for later DNS resolution and reconnects. This is code-level IPv6 readiness; pairing and commands over IPv6 have not yet been physically validated on the Xiaomi.

The client opens a TLS command connection, responds to configure and active-feature negotiation, answers ping requests, and marks Connected only when RemoteStart arrives. The IO coroutine reconnects after a network failure with 1, 2, 4, 8, 16, then 30 second delays. A closed ViewModel closes its sockets and cancels work. Remote readiness and reconnection still need validation on the physical Xiaomi.

## Pairing and security

The app creates a persistent 2048-bit RSA client signing key and self-signed client certificate in Android Keystore. The private key is non-exportable. The pairing TLS connection allows a single self-signed RSA TV certificate only inside its own `SSLContext`; the app never changes global TLS defaults. The six-character TV code authenticates both public keys through the Polo SHA-256 secret calculation. Only after the TV acknowledges the secret does the app store the TV certificate's SHA-256 fingerprint. Every subsequent command TLS connection requires this exact pin. A changed TV certificate requires pairing again. Pairing data are in app-private preferences and Android backup is disabled; neither code nor key material is logged or stored in preferences. The initial pairing channel is subject to the security of the TV-displayed code; the self-signed certificate has no public CA chain.

The pairing steps are request → request acknowledgment → hexadecimal encoding options → configuration → code entry → secret → secret acknowledgment. The code is entered only in the app. `PairingHandshake` validates message order. Errors close the pairing socket and surface in setup.

## Wire format and controls

Messages are protobuf wire format with a varint length prefix. A bounded, small Kotlin codec handles only integer and length-delimited fields needed here; it avoids committing generated classes for a small, reverse-engineered subset and rejects oversized/truncated frames. The decoder skips protobuf 32- and 64-bit unknown fields. Android keycodes are mapped independently from UI actions. Normal keys use direction SHORT=3. A long key sends START_LONG=1, holds 650 ms, then sends END_LONG=2 on the same TLS connection. `RemoteCoordinator` implements Last Channel as long CENTER followed by short CENTER. The duration is a compatibility starting value, not yet physically validated. SLEEP and WAKEUP keycodes back the selected Xiaomi Power action; actual sleep/wake behavior is unproven.

## References and limitations

Protocol behavior was cross-checked against the [Apache-2.0 androidtvremote2 project](https://github.com/tronikos/androidtvremote2), its [Polo message schema](https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/polo.proto), and the [MIT androidtv-remote project](https://github.com/louis49/androidtv-remote). Implementation code here is independent. Android API choices follow the official [NsdManager](https://developer.android.com/reference/android/net/nsd/NsdManager) and [KeyGenParameterSpec](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec) documentation.

The Xiaomi adapter does not launch yes+, read current app or power state, control Samsung hardware, or claim that Xiaomi pairing/control is proven. LG control is implemented separately; see [LG_WEBOS_PROTOCOL.md](LG_WEBOS_PROTOCOL.md). TV-side protocol variations may require adaptation after physical validation. No ADB, Wireless Debugging, or Developer Options are used.
