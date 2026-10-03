# Architecture

The app has one Android application module. `RemoteScreen` renders `RemoteState` and emits `RemoteAction`; it has no protocol calls. `RemoteViewModel` owns the LG and Xiaomi controllers and their setup flows. `RemoteCoordinator` routes each action to `TvController`, `StreamerController`, or `SoundbarController`. LG uses `LgTvController`, Xiaomi uses `GoogleTvStreamerController`, and the soundbar still uses an in-memory adapter. Fake TV, streamer, and soundbar controllers remain available to tests and previews.

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

Power state is not read from hardware. When the LG connection is absent, its Power action takes the Wake-on-LAN path; when connected, it sends power off. LG and Xiaomi connection indicators report their transport states; the soundbar indicator says “Simulated.” LG and Xiaomi sleep/wake behavior remains unverified on physical hardware.

## LG webOS boundary

`SsdpLgDiscovery` runs a bounded M-SEARCH for the LG second-screen service, fetches same-host UPnP description metadata where available, and releases its socket and multicast lock on stop. The setup dialog shows discovered identity and supports manual host entry. `LgTvController` implements suspendable `TvController`, owns registration and connection state, and keeps one `LgSsapSession` at a time. `OkHttpLgTransportFactory` handles the pinned `wss://` connection; `LgRequests` correlates concurrent SSAP replies by ID. The controller requests the external input list after registration and switches by the TV's `HDMI_1`–`HDMI_4` IDs. `LgPairingStore` persists the host, identity, client key, certificate pin, and wake MAC configuration in app-private preferences with backup disabled. `LgInstallation` holds this household's known MACs outside reusable protocol code. Wake-on-LAN packet generation is separate from the controller. See [LG_WEBOS_PROTOCOL.md](LG_WEBOS_PROTOCOL.md).

## Google TV boundary

`GoogleTvStreamerController` implements the suspendable `StreamerController` port. `NsdGoogleTvDiscovery` handles service discovery and retains all resolved addresses; on Android 16+ it also retains the advertised service hostname. `AndroidClientIdentity` creates and keeps the TLS client private key in Android Keystore. `PairingStore` keeps the selected hostname, command port, last reachable address, and server certificate pin. The socket adapter tries resolved IPv4 and IPv6 addresses for TCP connection before TLS, then validates the certificate pin. The protocol package handles pairing state, varint framing, key mapping, long press, and reconnect timing without Android UI dependencies. `RemoteCoordinator` retains the one-action yes+ Last Channel macro; the adapter expands a long press into START_LONG, hold, END_LONG before the coordinator sends short CENTER.

The controller owns an IO coroutine scope and one active TLS socket. It answers configure/active/ping messages, reports Connected after RemoteStart, and reconnects with bounded exponential delay. Closing the ViewModel closes discovery, pairing, connection, and coroutine resources. Protocol unit tests run without hardware. See [GOOGLE_TV_PROTOCOL.md](GOOGLE_TV_PROTOCOL.md) for message and security details.
