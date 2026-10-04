# CouchPilot architecture

One Android app module, namespace/applicationId `com.myremote.app`, min26/target36/compile37. Compose/UI depends on domain state and intentions; adapters depend on domain interfaces. No SSAP/protobuf/RFCOMM logic lives in Compose. Debug fakes remain available only in debug/tests/previews.

## Ownership and commands

`RemoteApplication` owns one `RemoteSession`. `RemoteViewModel` exposes it without disposing on Activity rotation. `RemoteConnectionService` owns an explicit connected-device foreground lease; switching apps stops discovery, not established channels. Stop cancels commands/pairing, pauses adapters and closes sessions. Process death requires opening the app; the service is nonsticky.

`CommandScheduler` is confined to the session's Main dispatcher. Three structured workers serialize TV, streamer and soundbar independently. Each has8 pending slots; pending input switches coalesce; stale TV work expires at5s and streamer/sound at1.5s. Power target is captured at submission. The last successfully requested source is restored as a local UI hint after process recreation, without sending a source change; it is not live TV-source telemetry. Active uncertain writes are never replayed. Explicit cancellation invalidates the worker generation before clearing queues, so an old worker cannot clear newer busy state. `RemoteCoordinator` uses separate device mutexes for direct callers, maintains state on the same Main dispatcher, routes intentions and preserves failures from unrelated successful devices. No global command lock or per-tap unbounded job collection remains.

## Adapters

- `lg/LgTvController`, `LgSsapSession`, `LgTransport`: WSS3001, SSAP request correlation, stored grant/pin, TV input enumeration and switchInput with TV-reported app fallback. One connection replacement waits for the prior cancelled job to finish before configuration/registration. Credential migration/read and power-command credential access use adapter IO. `LgPairingStore` startup status does not decrypt/generate keys. `LgDiscovery` uses bounded SSDP/same-sender HTTP description; `LgWakeOnLan` sends explicit persisted MAC configuration on selected LAN broadcast. No household UUID/MAC seed remains.
- `google/GoogleTvStreamerController`, `GoogleTvSecurity`, `GoogleTvAddresses`: NSD `_androidtvremote2._tcp`, retained hostname/addresses, pairing6467 and advertised command port (default6466), Keystore identity and pinned TLS. TCP address fallback precedes TLS. Discovery/attempt ownership prevents stale callbacks.
- `hid/StreamerRoute` explicitly chooses LAN or Classic HID. `AndroidHidBluetooth` owns public HID service/callbacks, Android bonds and selected host; state events are conflated. `HidStreamerController` guards generations/retry budget; `HidSession` serializes press/release and releases keys on cancellation. First-time host address is explicit setup configuration, never a compiled installation constant.
- `samsung/SamsungSoundbarController`, `SamsungSession`, `SamsungBluetooth`: Android-owned selected bond, legacy RFCOMM and bounded vendor framing/status queries. A synchronized generation authority guards every public state/error/mute change and session assignment/removal; stale attempts/errors/disconnects close only their own resources. Power persistence is completed on IO before uncertain toggle writes. Saved Off suppression is reused; explicit recovery does not replay a toggle.
- `network/LanNetwork` and `LocalAddressPolicy`: non-VPN LAN selection, socket-local binding, private/link-local or connected global IPv6 prefix scope. No process-wide binding/VPN changes or public WAN/loopback command connections.

## Main flows

1. Source tile → session queue TV lane → coordinator → LG enumerated HDMI ID request → selected source/active target after success; Xiaomi input resumes saved ancillary recovery.
2. Power → captured TV/streamer lane → adapter off or bounded wake; TV WOL requires persisted address/network wake setting. State hints do not establish physical panel power.
3. Xiaomi navigation/digits/media/channels → streamer lane → explicitly selected transport. Previous channel is long OK then short OK as one ordered intention; no inferred app launch.
4. Volume/mute → sound lane → selected Samsung session → validated status. Disconnected recovery is bounded; no uncertain command replay.
5. Device status/settings → device management → discovery/manual selection/approval; LG grant, Google pin and selected Android bonds persist privately. Refresh authorization is explicit; changing label does not invalidate credentials.
6. Lost connection → owning adapter cleanup/backoff → StateFlow → localized status/error; user retry/forget remains explicit. Snackbar/top feedback is independent of scroll position.

## UI and diagnostics

`ui/RemoteScreen` renders common header/power/sources/sound and Xiaomi-only navigation/media/yes+ controls. Device setup dialogs own saveable non-secret drafts; pairing codes stay transient. Numeric/source/direction order remains physical LTR within Hebrew layout; mixed brand strings are bidi wrapped/isolated. Compact status chips wrap at large fonts and expose text/state semantics. `RemoteDiagnostics` keeps bounded allowlisted in-memory events; only debug builds log sanitized metadata. No analytics/backend.

See SECURITY.md for protocol-constrained/residual risks and FINAL_VALIDATION.md for evidence. Hardware-specific facts remain in DEVICE_VALIDATION.md.
