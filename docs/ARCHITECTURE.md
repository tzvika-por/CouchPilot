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

Watch yes+ currently selects HDMI 3 without guessing an app ID or claiming wake/launch. LG Power uses connectivity to choose off vs confirmed reconnection wake; Xiaomi uses SLEEP/WAKEUP on a connected channel. Individual power limitations are documented; there is no Power Off All. Sound always targets Samsung's RFCOMM control service, never a production fake or generic AVRCP. Samsung power is withheld because toggle/standby state is not trustworthy.

## Lifetimes, networking and trust

ViewModel owns controllers. Lifecycle START reconnects stored selections, STOP closes discovery, pairing and connection resources, and ViewModel clear disposes scopes. LAN selection affects sockets only; it does not change router, Wi-Fi or Tailscale settings. Discovered Google TV Network is preferred if still a usable LAN. All NSD addresses and last-success address persist; dynamic Network handles do not. Generation checks prevent callbacks from an earlier stopped discovery changing a later run.

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

HidAssociation separates OS bond state from profile readiness. Only an explicit UI action requests createBond; BOND_BONDED causes profile connect, and the HID callback alone enables remote commands. AndroidHidBluetooth owns a selected-host bond receiver with native lifetime cleanup. HidStreamerController starts the first-association deadline on the explicit action, disables duplicate attempts and retains idle registration only while foreground. Saved bonds reconnect without re-pairing. The UI no longer asks the TV to discover the phone or requests ADVERTISE. The phone’s inquiry class and SDP subclass are distinct; keyboard SDP classification is not claimed to change TV discovery filtering. Existing LG, Google LAN and Samsung commands remain unchanged.
