# Architecture

One Android module with three production adapters. Compose emits RemoteAction, RemoteCoordinator routes intentions, and adapters own discovery, registration, credentials, transport, protocol and lifecycle. Fakes remain in data/ for tests/previews; none is instantiated by production RemoteViewModel.

| Area | Components |
|---|---|
| Product | domain/Controllers, RemoteModels, RemoteCoordinator, DeviceFailure |
| LG | SSDP, LgPairingStore, LgSsapSession/LgRequests, pinned WSS transport, LgTvController, WOL |
| Xiaomi | NSD, private pairing store, Android Keystore identity, TLS/pins, protobuf/Polo codec, streamer controller |
| Samsung | paired-bond selector, RFCOMM factory, SamsungProtocol, SamsungSession, SamsungSoundbarController |
| Networking | LanNetwork: socket-local non-VPN Wi-Fi/Ethernet selection, network DNS and prefix-directed broadcasts |
| Observability | RemoteDiagnostics bounded ring and Android structured logs with allowlisted metadata |
| UI | one dark remote, localizable setup/status for all devices, Hebrew/RTL, meaningful error text |

## Product routing

Sources PS5/Mac mini/Xiaomi/PC map to reported HDMI_1/2/3/4. Activity selection changes only after accepted input control. Xiaomi becomes active for HDMI 3, otherwise TV. Global D-pad/media/digits/channels always target Xiaomi regardless of source. Last Channel is serialized long CENTER then short CENTER in domain logic; Compose exposes one button. Rewind and fast-forward now appear on the main remote.

The redundant Watch yes+ action/button is removed: Xiaomi source selection is the single HDMI 3 action. Google Remote Service v2 has an app-link launch feature, but the working HID route has no package-intent launch channel; no fragile launcher-tile macro or guessed package is introduced. LG Power uses connectivity to choose off vs reconnection wake; wake now works after enabling Turn on via Wi-Fi. LG HDMI_3 selection also physically wakes Xiaomi, and optical Auto Power Link wakes Samsung with the TV. The contextual power button now identifies its LG/Xiaomi target. A separate StreamerOff action always invokes StreamerController.powerOff, independent of input/active target; Xiaomi uses SLEEP/WAKEUP on a connected channel. SoundbarPower routes exclusively to SoundbarController.togglePower. SamsungSession serializes a live status query and exactly one vendor toggle; no post-off acknowledgement or automatic replay is expected. The controller persists connection suppression before attempting the toggle and disconnects afterward, even on uncertain write/cancellation. Foreground entry and device-list refresh respect it; explicit setup Retry/selection clears it. An older power operation must not close a replacement connection. No observed standby state or wake success is invented. There is no Power Off All.

## Lifetimes, networking and trust

RemoteApplication owns RemoteSession and its controllers; a connected-device service owns the active connection lease. Activity START ensures the service is running without restarting existing sessions. Activity STOP closes discovery only. Explicit Disconnect/service destruction releases pairing and connection resources; UI ViewModel recreation does not dispose them. LAN selection affects sockets only; it does not change router, Wi-Fi or Tailscale settings. Discovered Google TV Network is preferred if still a usable LAN. All NSD addresses and last-success address persist; dynamic Network handles do not. Generation checks prevent callbacks from an earlier stopped discovery changing a later run.

LG and Google TV trust managers are app-local; persisted pins reject identity changes. Google private RSA key stays in Android Keystore. LG keys and selected endpoints are app-private preferences excluded from backup. Samsung pairing remains Android's bond; only selected address persists. No credentials/pairing codes are logged. Local SSAP and Google TV protocol logic stay outside Compose.

Registration-level LG 401 requires authorization recovery. Command-level 401 keeps the working grant, records operation/code and returns a typed failure. Input fallback uses only the TV-reported appId through launcher; a transport timeout is never retried as another state-changing command. Samsung queries serialize because packets have no request IDs; a timeout closes the stream. Status validates connectivity but does not establish physical volume change.

## Errors and verification

DeviceFailure/FailureKind carry typed failures; the UI maps them to localized recovery text rather than raw protocol strings. RemoteDiagnostics accepts only fixed device categories, bounded operation/outcome identifiers and numeric codes, keeping the last 100 events. Packet bodies, keys, pins, hosts and Bluetooth addresses are not accepted by the logger. Setup errors are also redacted.

Tests include domain routing/macros, protocol vectors, stream framing, stored identity/endpoints, controller state/backoff, real local UDP, IPv4 refusal to IPv6 fallback, real IPv6 TLS and pin rejection, real LG WSS fake server and Compose interaction/RTL/privacy. An emulator validates UI/runtime only; physical radio, real TV authorization and actual sound/image changes remain device facts. See DEVICE_VALIDATION.md and protocol docs for evidence categories.

Sound controls use original vector speaker icons and localized content descriptions. The soundbar's nullable reported mute state feeds RemoteState through the coordinator; Compose selects artwork/action labels only. It does not invert a guessed hardware state. Volume changes and connection loss invalidate the known mute state. No protocol logic or added polling lives in the UI. LG input switching and MyRemote Samsung volume/mute are now physically confirmed; see the latest customer record.


GoogleTvSocketIo transfers native socket ownership to cancellable operations; cancellation closes blocking TCP/TLS/frame IO immediately. GoogleTvCommandSession separates the single framed reader and serialized key/ping writes from Android lifecycle/storage. A reported RemoteStart power flag feeds domain state independently of readiness. Real local mutual-TLS tests exercise this same production session; no fake physical success follows from them.


## Optional Bluetooth streamer route

StreamerRoute delegates each intention to exactly one explicitly selected adapter, preserving the existing StreamerController port and domain macro. Google TLS storage/discovery is unchanged. HidProtocol/HidReports/HidSession are Android-independent; HidStreamerController owns state, deadlines/backoff and generation guards; AndroidHidBluetooth owns the public API proxy/SDP/callback executor. HidStore persists selection/mode, never radio keys. Compose only presents setup intents and the same remote actions. No failed command is replayed on a different transport.

The native profile is registered only for the foreground Bluetooth selection and is released on background/switch/disposal. Android's HID Host role restriction is explained in setup. The soundbar retains its separate RFCOMM adapter; actual dual-profile radio interoperability requires hardware evidence. See XIAOMI_BLUETOOTH_PROTOCOL.md. The observed installation Bluetooth address is isolated in XiaomiInstallation and can be replaced by an explicitly selected OS bond.

### Bluetooth first association correction

HidAssociation separates OS bond state from profile readiness. Only an explicit UI action requests createBond; BOND_BONDED causes profile connect, and the HID callback alone enables remote commands. AndroidHidBluetooth owns a selected-host bond receiver with native lifetime cleanup. HidStreamerController starts the first-association deadline on the explicit action, disables duplicate attempts and retains idle registration while the connected-device service keeps the UID foreground-eligible. Saved bonds reconnect without re-pairing. The UI no longer asks the TV to discover the phone or requests ADVERTISE. The phone’s inquiry class and SDP subclass are distinct; keyboard SDP classification is not claimed to change TV discovery filtering. Existing LG, Google LAN and Samsung commands remain unchanged.

### Bluetooth reconnection after an established bond

HidStreamerController retains one registered HidTransport through transient bonded disconnects and uses reconnect() on that profile. The command session is invalidated when the link goes away; writes belong to a specific logical session, preventing an old press/release failure from affecting a replacement. Three outgoing attempts back off at 3/6/12 seconds. Connections shorter than 30 seconds keep the accumulated budget. Exhaustion listens passively for the TV or a user retry; no repeated bond request occurs. Fatal errors or lifecycle/selection cleanup still release the native proxy, receiver and executor. HidAssociation.disconnected clears only the profile-request latch, leaving OS pairing intact. The saved-bond UI disables pairing when Connected.


## Background connection ownership — 2026-10-04

The customer's TV flicker on app switching exposed an implementation defect: MainActivity's ON_STOP/onDispose called RemoteViewModel.stopConnections, unregistering HID and closing all device channels; ON_START restarted them. The exact visible TV reaction is not observed from the Mac, but this teardown is proven from code and matches the symptom.

RemoteApplication now owns one RemoteSession. RemoteViewModel is a UI facade; clearing/recreating it does not release connections. RemoteConnectionService promotes itself before opening device sessions and owns an idempotent ConnectionLifetime lease. Repeated Activity starts/recreation do not restart LG, Samsung or HID. Activity stop pauses only discovery; pending serialized key presses retain their guaranteed release. Explicit Disconnect/service destruction cancels pending actions/pairing, stops discovery and releases all transports/profile resources without sending wake/off/input commands. The same process-owned session can resume afterward. Session.close remains full terminal cleanup for tests/process ownership; Android terminates native resources on process death.

The service is private, non-sticky, typed connectedDevice, with FOREGROUND_SERVICE and FOREGROUND_SERVICE_CONNECTED_DEVICE permissions. Existing multicast permission and/or granted BLUETOOTH_CONNECT meet Android's type prerequisites. It starts from a visible Activity, never boot/work scheduling. A low-importance ongoing notification opens the remote or disconnects it. Android 13+ notification consent is asked once; denial does not block a foreground service and the in-app Disconnect control remains available. No screen hold, wake lock, location, battery-optimization exemption or hidden Bluetooth API is used. Native registration/loss diagnostics contain allowlisted metadata only.

Android HID requires an eligible foreground UID. [AOSP HidDeviceService](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/main/android/app/src/com/android/bluetooth/hid/HidDeviceService.java) uses IMPORTANCE_VISIBLE as its cutoff; foreground-service importance is higher priority. This is why simply deleting ON_STOP cleanup without a service would not suffice. References: [connected-device foreground service](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device), [notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission), [Bluetooth HID API](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice).

Explicit Samsung setup Retry now calls retrySoundbar, rather than device-list refresh, so deliberate post-power connection suppression can actually be resumed by the visible Retry button. Automatic service startup still uses connectStored and respects suppression. Protocol commands and previously validated device mappings are preserved.

## Standby wake research boundary

Latest physical evidence confirms background retention removes TV blinking and all three Off controls work; all three wake attempts fail. Ordinary control sessions and standby receivers have separate availability. LG already uses out-of-session WOL, Samsung currently permits only a live-session toggle and suppresses reconnect, and Xiaomi's wake reports need a connected HID/LAN channel. [POWER_ON_RESEARCH.md](POWER_ON_RESEARCH.md) documents supported alternatives and the LG saved-MAC migration gap. This research makes no runtime change and does not turn connectivity into a claimed physical power state.

## Wake configuration and explicit connection intent

LG stores same-device metadata updates independently of grant reset. A secure-session hello UUID enriches manual-host setup after registration; expected UUID and certificate pin remain identity guards. Legacy missing MAC migration affects only the verified installation. Its bounded wake job is canceled on timeout/failure. Wake-configuration versus wake-unconfirmed failures have localizable English/Hebrew messages.

Samsung's existing power intention branches in the controller: connected → one guarded toggle/off and persisted reconnect suppression; disconnected → one bounded connection/status attempt without a toggle, settings change or audio-input switch. Failure restores suppression; successful connection returns to normal lifecycle policy without claiming measured physical wake. Protocol logic remains outside Compose. Google/Xiaomi adapters and the background connection service are unchanged.

## Restoring sound control after optical wake

Physical evidence now establishes LG network wake and Samsung optical Auto Power Link wake. Samsung's persisted post-Off suppression still prevents automatic connection on Activity/process return. A new explicit volume/mute command may restore a disconnected control channel through the existing bounded connection/status initialization, then sends that command once. Compose and the coordinator contain no optical or Samsung protocol logic. No new interface method, power toggle, audio-input switch, pairing or automatic wake loop is introduced. Failed/cancelled initialization restores suppression; uncertain command writes are not replayed. Connected commands reuse the existing session. Standalone SPP wake is still physically unsuccessful; Connected is a live control service, not a measured amplifier power state.

## Recovery after external device wake

StreamerController and SoundbarController expose a nonblocking reconnectAfterWake hook, implemented by production adapters and recorded by test fakes. RemoteCoordinator invokes both hooks only after successful LG power-on or accepted Xiaomi input switching. Errors from ancillary recovery cannot turn an accepted TV command into a failed/replayed command. StreamerRoute forwards recovery only to the chosen adapter; Google LAN reuses a stored grant and never starts pairing. Compose contains no recovery/protocol logic and its layout is unchanged.

HID distinguishes explicit pairing from automatic recovery requests. A bonded saved host can resume the passive budget or interrupt backoff while retaining its native profile. Unbonded recovery never initiates pairing. Existing finite retries and 30-second stable-connection reset remain; connected/in-progress sessions are not recreated.

Samsung recovery extends its existing connection job rather than adding a competing socket owner. Up to three initial attempts have 15-second deadlines and 3/6-second backoff; initial exhaustion or terminal permission/unpaired/security failure restores persisted suppression. Once a valid status arrives, its existing connected-session reconnect policy resumes. Off, explicit Disconnect and device replacement cancel/close the same owner. Recovery sends initialization/status only, never a power toggle or audio-input change. Ordinary foreground entry still respects Off suppression.

TV wake and Xiaomi input selection are explicit new wake intentions. An arbitrary network reconnect is not interpreted as physical TV wake, and no background action changes HDMI input or wakes equipment merely because the Activity becomes visible. Existing process/service connection retention stays intact. Physical post-wake automatic connection success remains unproven.
