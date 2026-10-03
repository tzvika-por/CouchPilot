# Architecture

The app has one Android application module. `RemoteScreen` renders `RemoteState` and emits `RemoteAction`; it has no protocol calls. `RemoteCoordinator` routes each action to `TvController`, `StreamerController`, or `SoundbarController`. Each controller is an interface so the current in-memory adapters can be replaced independently with production implementations.

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

The initial fake power state is a simulator value, not a reading from hardware. Connection indicators explicitly say “Simulated.” Real adapters must report actual connection and power capabilities rather than inheriting fake values.

## Next integration boundary

Implement and validate a non-debug Google TV remote adapter for `StreamerController` first. It must support key presses and a true long press so the yes+ Last Channel macro works. Keep pairing, transport, retries, and credentials within the adapter or its supporting data layer. Add device tests only after a buildable foundation and a concise physical pairing request.
