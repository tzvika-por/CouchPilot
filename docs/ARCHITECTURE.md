# Architecture

The app has one Android application module. `RemoteScreen` renders `RemoteState` and emits `RemoteAction`; it has no protocol calls. `RemoteViewModel` owns the real Xiaomi controller and setup flow. `RemoteCoordinator` routes each action to `TvController`, `StreamerController`, or `SoundbarController`. LG and soundbar still use in-memory adapters; Xiaomi uses `GoogleTvStreamerController`.

## Action routing

| User action | Domain behavior | Adapter |
|---|---|---|
| Select PS5, Mac mini, Xiaomi, PC | Switch to HDMI 1, 2, 3, 4 respectively | TV |
| Watch yes+ | Select HDMI 3 and make Xiaomi active | TV |
| Power | Toggle the selected controllable device: Xiaomi for HDMI 3, LG TV for other inputs | TV or streamer |
| Volume and mute | Control Samsung soundbar | Soundbar |
| D-pad, OK, Back, Home, Play/Pause, digits, channels | Send streamer key | Streamer |
| Last Channel | Send long CENTER, then short CENTER in order | Streamer |

The first Watch yes+ action intentionally only selects HDMI 3. Waking Xiaomi or launching yes+ will be added after those production capabilities are validated. Selecting PS5, Mac mini, or PC makes the TV the active *controllable* device because this app has no controller for those sources. Xiaomi navigation remains available even while another input is selected.

The initial power state is a local toggle, not a reading from hardware. LG and soundbar connection indicators explicitly say “Simulated.” Xiaomi reports its transport state, but sleep/wake behavior remains unverified on physical hardware.

## Google TV boundary

`GoogleTvStreamerController` implements the suspendable `StreamerController` port. `NsdGoogleTvDiscovery` handles service discovery and retains all resolved addresses; on Android 16+ it also retains the advertised service hostname. `AndroidClientIdentity` creates and keeps the TLS client private key in Android Keystore. `PairingStore` keeps the selected hostname, command port, last reachable address, and server certificate pin. The socket adapter tries resolved IPv4 and IPv6 addresses for TCP connection before TLS, then validates the certificate pin. The protocol package handles pairing state, varint framing, key mapping, long press, and reconnect timing without Android UI dependencies. `RemoteCoordinator` retains the one-action yes+ Last Channel macro; the adapter expands a long press into START_LONG, hold, END_LONG before the coordinator sends short CENTER.

The controller owns an IO coroutine scope and one active TLS socket. It answers configure/active/ping messages, reports Connected after RemoteStart, and reconnects with bounded exponential delay. Closing the ViewModel closes discovery, pairing, connection, and coroutine resources. Protocol unit tests run without hardware. See [GOOGLE_TV_PROTOCOL.md](GOOGLE_TV_PROTOCOL.md) for message and security details.
