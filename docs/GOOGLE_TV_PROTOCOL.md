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

The Xiaomi adapter does not launch yes+, read the current app, control Samsung hardware, or claim that Xiaomi pairing/control is proven. LG control is implemented separately; see [LG_WEBOS_PROTOCOL.md](LG_WEBOS_PROTOCOL.md). TV-side protocol variations may require adaptation after physical validation. No ADB, Wireless Debugging, or Developer Options are used.

## Audit follow-up — 2026-10-04

**IMPLEMENTED BUT UNPROVEN:** all NSD addresses now persist alongside the retained host, advertised command port and last-success address; this prevents pre-Android-16 discovery collapsing back to an IPv4-only endpoint after restart. The resolved Network remains an in-memory hint, never a persisted network ID. Per-socket non-VPN Wi-Fi/Ethernet selection uses scoped network DNS and prefers the discovered network, without process binding. This is a legitimate LAN path choice, not proof that Tailscale caused the historical failure.

NSD discovery uses one listener per generation; old resolve/lost/start-failure callbacks cannot modify a new run. Stop releases multicast lock and invalidates its generation. Reconnect backoff resets after successful readiness negotiation. Background lifecycle closes sockets/pairing/discovery; foreground reconnects stored pairing. Varint decoding now rejects overflow at bit 63. Production trust verification is independently testable, including a real IPv6 TLS exchange and pin mismatch.

**PROVEN — automated:** real loopback IPv4 refusal can fall back to a listening IPv6 socket, alternate addresses survive restart, and real IPv6 TLS succeeds with a synthetic certificate under the production trust policy. These do not establish Xiaomi device pairing or commands. **FAILED — historical physical:** phone TCP reachability before TLS. **OPEN QUESTION:** currently reachable Xiaomi endpoint and selective Wi-Fi path cause; all historical addresses now time out, so a stale advertisement name is insufficient current reachability evidence. No Xiaomi address is hard-coded in production and no ADB dependency was introduced.

NSD network hints are read only on API 33+; older phones use the selected non-VPN LAN. API guards follow the [NsdServiceInfo reference](https://developer.android.com/reference/android/net/nsd/NsdServiceInfo). No network handle is persisted across launches.


## Cancellable sessions and powered-on evidence — 2026-10-04

**IMPLEMENTED BUT UNPROVEN physically:** socket operations explicitly own and close the native socket on coroutine cancellation. This unblocks TCP connect, TLS negotiation and framed reads rather than waiting for native timeouts after the app backgrounds. Pairing transitions serialize; cancellation does not become an authorization error. One command-session reader owns negotiation and pings; writes serialize across an entire long press. RemoteStart.started updates reported power before Connected, including false/standby; readiness is independent of that flag. Reconnect retains the stored identity and endpoint alternatives.

**PROVEN — automated:** a real loopback IPv6 mutual-TLS server exercises configure/active/start/ping, all 22 key mappings, serialized long-OK/short-OK, reported standby/wake/sleep, and independent Polo certificate/code exchange. Malformed frames and server errors cannot establish readiness. Cancellation closes stalled TLS and frame-read sockets. Five new tests bring the suite to 70 JVM tests; these are protocol simulations, not physical Xiaomi control.

**PROVEN — Mac network, after the customer turned Xiaomi on:** en0 reaches 192.0.2.8 TCP 6466/6467/8009 over IPv4 and 6466/6467 over IPv6. Both Remote Service ports complete TLS 1.3 with the same certificate; no application request or pairing prompt was sent. en1 still times out on those IPv4 ports and both IPv6 service ports. The off state explains the immediately preceding Ethernet outage; it does not explain the remaining interface-specific difference.

Current en0 mDNS: Xiaomi TV Box._androidtvremote2._tcp.local., SRV tv.local:6466. Addresses include 192.0.2.8, fe80::2%en0, 2001:db8:7::3 and 2001:db8:7::1. TXT reports bt=02:00:00:00:00:02, wp=6465 and isDeviceInStandbyMode=false. The meaning of wp is unknown; observed pairing remains 6467. This host supersedes the old host for current investigation; no literal LAN address is embedded in the app.

**OPEN QUESTION:** Wi-Fi client/bridge filtering remains the strongest explanation, not an identified router setting. An existing Tailscale Android TV peer accepts TLS but presents a different certificate from Xiaomi's LAN service; it is not an authenticated alternate Xiaomi endpoint and is excluded. Physical MyRemote pairing/commands remain unproven. No repeated UI, router or Xiaomi diagnostic is requested.


### Mac VPN comparison

Fresh strictly interface-bound IPv4/IPv6 sockets reproduce Ethernet success and Wi-Fi failure both with Mac Tailscale Running and explicitly Stopped. Tailscale was restored to Running with unchanged configuration. No exit node or LAN-overriding VPN route was present. The active Mac VPN hypothesis is not supported by this comparison; selective Wi-Fi path restrictions remain an OPEN QUESTION. See DEVICE_VALIDATION.md for the exact controls and limits. Direct LAN control does not require a Tailscale client on Xiaomi, and no application code changed for this hypothesis.
