# Samsung HW-M360 Bluetooth control

## Evidence and provenance

**PROVEN — physical:** Samsung Audio Remote controls volume and mute on the actual HW-M360 while it remains on D.IN. The latest customer session with MyRemote 8a1c428 confirms setup, Volume Up, Volume Down, Mute and Unmute with our native adapter.

Public documentation does not publish this binary protocol. Research found [Samsung's official app listing](https://play.google.com/store/apps/details?id=com.samsung.samsungband), model documentation and no maintained public M360 packet implementation. To avoid customer Bluetooth captures, a publicly downloadable [Audio Remote 1.5.16 artifact](https://apkpure.net/audio-remote/com.samsung.samsungband/download) was downloaded into temporary development storage and inspected statically. It was not installed or run. Android `apksigner verify --print-certs` validated its signature:

- Package: `com.samsung.samsungband`; version 1.5.16.
- APK SHA-256: `3009f6a78a7e0faf2d622761a5ab2c7309f99aa5397ae1af825511999584032f`.
- Signing certificate SHA-256: `c5875f022f8f60f2b445907176f54f46fba9f1e67e7f6af31683da1815b9f56c`.
- Certificate subject: Samsung, SRCNJ, Nanjing/Jiangsu/CN; SHA-1 also matches independent public artifact listings.

**PROVEN — static analysis:** the Bluetooth operator selects insecure RFCOMM by public service-record API with SPP UUID `00001101-0000-1000-8000-00805f9b34fb`. Transfer command definitions and the stream writer identify the following packets. The vendor source/assets/APK are not included in the repository. MyRemote's codec, transports and tests were independently written from interoperability facts. No proprietary implementation code was copied.

## Wire subset

Frame: `FF family length command parameters...`. Length counts command plus parameters, excluding the three-byte prefix. No checksum is appended to these volume/mute/start commands. Some other vendor command families contain their own checksum; MyRemote does not implement them. RFCOMM is a byte stream: reads must handle fragmentation and coalesced replies.

| Operation | Exact hexadecimal packet |
|---|---|
| App start | `FF 08 02 01 01` |
| App end (identified, not needed for close) | `FF 08 02 01 00` |
| Volume up | `FF 0B 03 7F 01 01` |
| Volume down | `FF 0B 03 7F 01 00` |
| Mute toggle | `FF 0B 02 74 00` |
| Power toggle | `FF 0B 02 20 01` |
| Query volume | `FF 0B 02 7F 00` |
| Query mute | `FF 0B 03 74 10 00` |

Volume responses use family `0B`, command `7F`, with parameter bytes marker/current/max. Mute responses use family `0B`, command `74`, marker/state (0/1). Unknown frames are consumed completely. The decoder rejects truncation, zero-length commands and more than 1024 bytes of unframed noise. One-byte length bounds allocation to 254 parameter bytes.

## Adapter and lifecycle

`SamsungProtocol` is the pure codec. `SamsungSession` owns one reader and serializes commands/queries because the protocol has no request IDs. Initialization sends app-start then queries volume; Connected requires a valid Samsung response. Volume/mute send the command and obtain valid status. A status response establishes a live service, not proof that a physical action changed audio. Unsolicited matching replies are possible, so these are not called unique command acknowledgements.

Four-second status timeouts close the stream to prevent late replies satisfying the next query. Native RFCOMM connect has a ten-second limit and closes the socket on cancellation. `SamsungSoundbarController` stores only the selected bond address in private preferences (backup disabled); Android stores pairing keys. Reconnect waits 3, 6, 12, 24, then 30 seconds, and stops for permission/off/unpaired failures. Activity backgrounding preserves connections through the connected-device service. Explicit Disconnect closes them; reopening or explicit Connect resumes the service, respecting post-power reconnect suppression. Forget removes only MyRemote's selection, not the Android bond.

Setup lists already-paired Samsung/soundbar devices; the customer's existing Samsung Audio Remote bond is reusable. On Android 12+, [BLUETOOTH_CONNECT](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions) needs runtime Nearby Devices consent. Earlier Android uses normal BLUETOOTH permission. There is no app Bluetooth scan, location permission, hidden channel reflection, A2DP connection, AVRCP proxy, input switch, media session or audio playback. If a different installation has no bond, Android Bluetooth settings provides ordinary pairing. The vendor's hidden RFCOMM channel 1/2 fallback is deliberately not used; an SDP failure remains diagnosable.

**PROVEN — physical:** production setup, standard SPP UUID connection, RFCOMM initialization, volume and mute/unmute now succeed on the actual soundbar. Deterministic packet/status/lifecycle tests remain independent automated evidence. **OPEN QUESTION:** long-term status timing/reliability, concurrent Audio Remote sessions and standby power semantics. No generic AVRCP assumption is made. No further customer session is requested for these already confirmed controls.

## Power boundary

The previously identified vendor power-toggle packet (`FF 0B 02 20 01`) is now exposed as **Soundbar power**, following the customer's request for an off control. This is deliberately a toggle, not a claimed absolute Off/On operation. It requires an existing Connected session and a fresh valid volume response before the one toggle is sent; the query verifies a live control service, not the panel's on/off state. No acknowledgement is required after the toggle because standby can disconnect RFCOMM. The controller persists automatic-connection suppression before attempting the command and closes its session afterward, even on uncertain write/cancellation. Repeated taps cannot resend while disconnected. Foreground/background/process restart and setup-device refresh respect suppression. Explicit Retry/selection restores ordinary connection attempts without replaying a toggle.

**PROVEN — latest physical customer report:** MyRemote's live-session toggle turns the soundbar off. **FAILED:** the current control does not turn it back on. Its Connected requirement and persisted reconnect suppression mean this failure is not evidence that hardware standby wake is impossible. [Power-on research](POWER_ON_RESEARCH.md) verifies optical Auto Power Link and Bluetooth Power On in the official M360 manual; SPP-only wake while retaining D.IN and current standby settings remain unproven. No A2DP/input change or settings command is added.

## Mute indicator

The middle sound button is icon-only with localized TalkBack labels. SamsungSession returns the validated mute status; the controller exposes a nullable status flow. The icon offers unmute only when the TV-independent soundbar response reports muted. A successful mute response reporting false offers mute. On a new connection or volume change the state is unknown (volume may clear mute), so the button uses a neutral mute toggle. Disconnect/failure clears it. No optimistic tap-based inversion, persisted guessed state or extra protocol query is used.


Background lifecycle update: app switching no longer closes RFCOMM. The connected-device service owns the connection until explicit Disconnect/destruction. Post-power automatic reconnect suppression remains persisted and respected by service startup; explicit setup Retry now reaches controller.retry directly. No power toggle is replayed on Activity return.

## Explicit disconnected wake attempt — 2026-10-04

`SoundbarController.togglePower()` retains its live-session off toggle. When disconnected, the same explicit Power intention now requests one native RFCOMM connection/status initialization, bounded to 15 seconds. It sends the existing app-start/volume-status messages, no power toggle, A2DP request, input-selection command or settings packet. A paired Bluetooth reconnection may wake Bluetooth Power On; SPP-only standby acceptance and physical on-state remain UNPROVEN. Connected means valid protocol status, not measured audio/power state.

Before the first valid Connected status, failures do not retry. Failure/timeout/cancellation closes the owned attempt and persists automatic-connection suppression again; later foreground entry does not retry it. After successful initialization the ordinary bounded-backoff connection policy resumes. An older failed operation cannot close a replacement job. The tested uncertain off-write remains non-replayed; only a new explicit wake/Retry intention can resume connection.

Optical Auto Power Link remains the installation-compatible candidate when LG wake brings back optical audio on D.IN. Its current setting is unknown; this patch does not change it. A soundbar Power tap can also reconnect MyRemote after an externally awakened soundbar, without sending a toggle that could turn it off again. Original off/volume/mute successes remain valid; physical wake through this new path is not yet established.

## Candidate physical failure supersedes awaiting-validation status

The customer's latest a8b9fcb result is **FAILED** for standalone soundbar wake, including the bounded disconnected SPP attempt. This is not physical proof of Bluetooth audio wake through this control profile. The official optical Auto Power Link route remains untested independently: LG itself did not wake, so returning TV optical audio was not established. Existing Off/volume/mute success remains valid. No toggle replay, hidden A2DP connect, input switch or undocumented setting packet is added. See [POWER_ON_RESEARCH.md](POWER_ON_RESEARCH.md).

## LG wake resolved; soundbar evidence remains separate

The customer confirms LG wake succeeds after enabling the TV's Turn on via Wi-Fi option. The requested test targeted LG only, so this is not evidence that the HW-M360 also woke. Standalone SPP wake remains unsuccessful from the prior test. Optical Auto Power Link now has a working TV-wake prerequisite; its current setting and actual response remain unconfirmed. No Samsung protocol, audio input or device setting changed in this update.

## Next physical boundary: optical Auto Power Link

The official M360 full manual ENG-10/ENG-21 and [Samsung M-series instructions](https://www.samsung.com/at/support/tv-audio-video/was-bewirkt-die-auto-power-link-funktion-meiner-soundbar-m-serie/) establish the existing optical/D.IN wake route and the original remote's Left-button five-second toggle. This is a configuration toggle, not a status read; if the display says OFF, another hold is needed to leave it ON. The current installation setting remains unknown. MyRemote has no proven command to configure it and cannot directly energize the optical link without the TV. No Bluetooth audio routing or settings packet is added.

One focused session can configure Auto Power Link, place both TV and soundbar in standby, then wake the LG with the existing MyRemote APK and observe whether the soundbar wakes too. This is new soundbar interoperability evidence; no repeat source/volume test is requested. LG wake success alone is insufficient. If optical wake succeeds, review automatic restoration of MyRemote's soundbar control connection separately: post-power reconnect suppression must not replay a toggle or be cleared on ordinary app switching. No standalone soundbar wake or reconnection success is claimed yet.
