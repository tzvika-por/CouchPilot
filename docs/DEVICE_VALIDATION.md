# Device validation

These observations came from physical tests supplied by the customer and the recorded Mac investigation. MyRemote LG discovery, registration, Power Off and input switching are physically proven. MyRemote Samsung setup, volume up/down and mute/unmute are now physically proven. LG wake and Xiaomi production control remain unresolved.

## Proven

- MyRemote discovers the LG 55UK6700YVD, registers and reaches Connected. The latest customer session confirmed switching to HDMI 3/Xiaomi and back to HDMI 2/Mac mini after authorization refresh and TV approval. Earlier 401 failures are historical evidence below.
- MyRemote Samsung setup succeeds; Volume Up, Volume Down, Mute and Unmute worked on the HW-M360 in the latest customer session.
- LG 55UK6700YVD webOS TV: LAN pairing, input list query, and direct HDMI switching succeeded with a Windows CLI. Switching from PC to HDMI 3 / Xiaomi was observed.
- Xiaomi TV Box S (3rd Gen): LAN reachability and Android key input were proven using exploratory ADB over Wireless Debugging. This does not validate the production remote protocol.
- yes+: `KEYCODE_1` switched to channel 1 during playback.
- yes+: `KEYCODE_LAST_CHANNEL` did not work. Long `DPAD_CENTER` opened quick actions with “Last Channel” selected by default; a following short `DPAD_CENTER` switched to the previous channel.
- Samsung HW-M360: Samsung Audio Remote on an Android phone controlled Bluetooth Volume Up, Volume Down, and Mute while the soundbar stayed on optical `D.IN`.
- Historical 2026-10-03 observation: the Xiaomi advertised `Xiaomi TV Box._androidtvremote2._tcp.local.` with command port `6466`. From the Mac's Ethernet interface, the Remote Service accepted TCP connections over IPv6 on command port `6466` and pairing port `6467`. This proves listener reachability, not a completed TLS or pairing exchange.
- Historical Mac Wi-Fi probes could not reach Xiaomi over either address family; the phone's manual IPv4 attempt failed before TLS or pairing. Current 2026-10-04 probes also fail on Ethernet; see the dated current record below.

## Not yet proven

- This app's Android TV Remote Service v2 discovery, pairing, connection, and key control on the physical Xiaomi. Code and automated protocol tests alone do not prove device interoperability.
- Xiaomi power and wake behavior through that protocol.
- LG wake: prior physical attempts failed; revised WOL targeting is not yet physically proven.
- Automatic LG reconnection without another approval across repeated physical launches. Credential persistence/reuse is proven automatically; the customer has not yet reported repeated launches. No repeat pairing test is requested.
- Samsung power control.
- Automatic foregrounding or launching of yes+.
- MyRemote pairing, command channel, and long press through the production protocol on the physical Xiaomi.
- The exact router, access point, or filtering setting responsible for Wi-Fi isolation.

## Failed MyRemote physical test — 2026-10-03

- Xiaomi TV Box S (3rd Gen) is `192.0.2.8`; the box itself reported `wlan0 = 192.0.2.8/24`. Windows ADB still reaches it.
- The Android phone is `192.0.2.7`. MyRemote found no Xiaomi through `_androidtvremote2._tcp.` discovery.
- Manual pairing to `192.0.2.8:6467` failed at TCP connect from `192.0.2.7` after 8 seconds with `EHOSTUNREACH (No route to host)`. TLS and the Polo pairing exchange were never reached.
- Temporarily disabling Tailscale on the phone did not change the result. Android App Info displayed “No permissions required.”
- Protocol review confirms that v2 normally advertises the command service on `_androidtvremote2._tcp.` with command port 6466, while pairing uses TCP 6467. The app uses the resolved command port and a separate pairing port. Its manifest declares `INTERNET`, `ACCESS_WIFI_STATE`, and `CHANGE_WIFI_MULTICAST_STATE`; it holds a Wi-Fi multicast lock during discovery. With `targetSdk = 36`, Android's current local-network guidance says `INTERNET` implicitly grants local-network access unless Android 16 local-network restrictions were explicitly opted in. App Info does not display normal install-time permissions, so its wording alone does not prove they are missing.
- The app does not bind the process or socket to a network. The reported source address `192.0.2.7` is consistent with the phone's Wi-Fi path. An incorrect PIN hash was found separately in code review and fixed; it cannot cause the earlier TCP connect error.

## Mac network-side investigation — 2026-10-03

- The Mac has `en0 = 192.0.2.3/24` (Ethernet) and `en1 = 192.0.2.19/24` (Wi-Fi). Its ordinary route to `192.0.2.8` uses `en0`.
- On Ethernet, TCP `192.0.2.8:8009` accepted a connection, confirming IPv4 reachability to the Xiaomi's Cast service. ICMP echo received no reply. TCP `192.0.2.8:6466` and `:6467` each timed out; neither accepted an IPv4 connection during these probes.
- mDNS advertised `Xiaomi TV Box._androidtvremote2._tcp.local.` on interface `en0`. Its SRV target was `tv.local:6466`; A resolved to `192.0.2.8`. AAAA included `2001:db8:7::2` and `2001:db8:7::3`. Both IPv6 addresses accepted TCP on `6466` and `6467` through `en0` (confirmed by route lookup). The record's advertised command port matches MyRemote's default and resolved command port; pairing port `6467` is separate and was accepting IPv6 connections.
- The Xiaomi also advertised `MiTV-example._googlecast._tcp.local.` at `00000000-0000-4000-8000-000000000002.local.:8009`; that host resolved to `192.0.2.8`. This confirmed mDNS was functioning on the wired Mac interface. No Xiaomi Remote Service v2 advertisement was observed on the Mac's Wi-Fi interface during the browse.
- With the Mac socket bound specifically to Wi-Fi `en1`, IPv4 TCP `6466` and `6467` timed out, and TCP `8009` returned `Host is down`. IPv6 TCP `6466`, `6467`, and `8009` also timed out when bound to `en1`. The Mac's ARP entry for `192.0.2.8` was resolved on `en0` but incomplete on `en1`, while `en1` had resolved entries for other local peers. This is direct evidence of a Wi-Fi-to-Xiaomi link-path restriction or selective filtering, consistent with the phone's `EHOSTUNREACH` and absent discovery. The wired IPv4 Remote Service timeouts are a separate IPv4-only limitation; the service is alive on IPv6. The exact router/AP or TV setting responsible is not established.
- Passive Mac checks found `192.0.2.1` as the gateway, DNS server, and DHCP server for Wi-Fi `en1` (`192.0.2.19/24`). Ethernet `en0` (`192.0.2.3/24`) and Wi-Fi use the same IPv4 subnet and gateway. The DHCP server name is `<gateway-id>`, suggesting a Sagemcom F@ST 5674 gateway, but this label alone does not confirm the physical AP model. The unauthenticated gateway landing page only said “Gateways” and exposed no relevant configuration.
- A separate routed IP subnet is unlikely because both Mac interfaces have `192.0.2.0/24` on-link routes. Selective Wi-Fi client/bridge filtering, guest behavior on a shared subnet, or mesh/AP segmentation remain plausible; these passive observations do not distinguish their settings or prove the exact cause. No network settings were changed.
- The network results do not justify changing pairing or TLS behavior to work around the phone's `EHOSTUNREACH`. A separate code audit found that NSD discarded alternate addresses, so IPv6 address fallback was added independently. The phone still needs a usable LAN path before physical pairing can be validated. No further product-owner diagnostic test is requested in this milestone.

## Superseded authorization hypothesis — 2026-10-04

The earlier commit 662b866 added CONTROL_DISPLAY, revision migration and grant refresh after every 401. That was a hypothesis. Latest physical evidence disproves stale pairing as a sufficient explanation: input 401 persisted after fresh registration and refresh. Power Off worked. The new implementation retains registration for endpoint denials and attempts a distinct input launcher path only when an appId was actually returned by the TV. See the current record below. Do not repeat the earlier refresh-only test.

## Historical automated authorization validation at baseline 662b866 — 2026-10-04

- Final Gradle gate: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --no-daemon` passed using the existing temporary Android SDK and Gradle cache.
- All 39 JVM unit tests passed, with zero failures, errors, or skips. Ten new tests cover grant migration/reset, authorization classification and controller state, refresh during registration, preservation of TLS trust, reconnect suppression, and failed HDMI routing. Existing manifest and SSAP request assertions were strengthened; all Xiaomi and HDMI mapping tests still pass.
- Lint passed with zero errors and 13 advisory warnings (SDK/dependency updates and SharedPreferences KTX suggestions).
- The Compose test APK compiled, including the new authorization-refresh callback/error-redaction test. Instrumented tests were not executed on hardware or an emulator.
- `git diff --check` passed; English and Hebrew authorization resources parse and are present exactly once. Xiaomi implementation/tests and LG transport code are unchanged. No additional static checker is configured.
- These automated results do not prove sufficient permission grants or successful physical input/power control.

## Known TV input map

| Input | Device |
|---|---|
| HDMI_1 | PS5 |
| HDMI_2 | Mac mini |
| HDMI_3 | Xiaomi |
| HDMI_4 | PC |

The Samsung soundbar normally remains on `D.IN`. No production adapter in this repository uses ADB. Physical Xiaomi discovery, pairing, Connected status, and one D-pad action remain unproven; no new Xiaomi test is requested.

## LG Milestone 2 validation boundary — 2026-10-03

- Proven before MyRemote: the LG 55UK6700YVD was reachable from Windows, accepted webOS pairing, returned its input list, and switched directly to HDMI 3 / Xiaomi.
- At the 2026-10-03 boundary, implemented in MyRemote pending a physical test: SSDP discovery and manual host setup, prompt registration with a persisted client key, secure WebSocket with a persisted certificate pin, input enumeration and ID-based switching, `system/turnOff`, and Wake-on-LAN.
- The known installation MAC addresses are wired `02:00:00:00:00:03` and Wi-Fi `02:00:00:00:00:01`. They are saved with the target device configuration; they are not general LG model constants. Whether this TV wakes from either interface remains unproven.
- The initial validation plan was to stop after registration, Connected status, and one HDMI 3 switch. Power-off and Wake-on-LAN should be tested only after that path is confirmed. No further Xiaomi test is part of this milestone.

## Ownership-audit boundary before the successful customer session — 2026-10-04

### PROVEN

- Latest customer evidence: MyRemote LG discovery, registration, Connected and Power Off succeeded. Windows CLI input switching (HDMI 3 included) succeeded. Samsung Audio Remote volume/mute remains proven on D.IN. ADB key semantics remain proven independently of production protocol.
- Fresh Mac passive/low-rate checks: en0 192.0.2.3 and en1 192.168.7.19. LG 192.0.2.4 responds to SSDP and TCP 3000/3001 on both. Description at http://192.0.2.4:1070/ reports exact modelNumber 55UK6700YVD, UUID 00000000-0000-4000-8000-000000000001, WebOS/4.1.0 UPnP/1.0 advertisement. No new pairing or state-changing TV command was sent from the Mac.
- Current DHCP identifies gateway/DNS/server 192.0.2.1, /24, server name <gateway-id>, suggesting Sagemcom F@ST 5674 family. Route to old Xiaomi IPv4 goes through en0. No router credentials/settings/network changes.
- Public Samsung Audio Remote APK signature verified; static protocol transport/command observations recorded in SAMSUNG_M360_PROTOCOL.md. No APK/vendor source is shipped or executed.

### FAILED

- MyRemote LG input 401 persisted after unpairing, fresh pairing and refresh. Previous WOL did not wake the actual LG. Those failures remain; new code is not physical proof.
- At this inspection the historical Xiaomi IPv4 192.0.2.8 times out on 6466/6467/8009 from both Mac interfaces. Both historical IPv6 addresses time out on 6466/6467 on each interface. Old hostname tv.local no longer resolves. A browse saw the Xiaomi service name on en0, but bounded resolution did not produce a current host/port record. Cache presence is not a live listener. Earlier IPv6 Ethernet success remains historical evidence, not a current result.

### IMPLEMENTED BUT UNPROVEN

- LG command denial retains the working grant; returned input appId can launch through a separate API; source updates require command acceptance. LAUNCH is now requested without forced key retirement. This is a material change, not another re-pair experiment.
- WOL uses selected LAN source/directed broadcast, repeats packets, protects unrelated TVs from installation MAC defaults, and requires registered connectivity before considering wake complete.
- Samsung real native RFCOMM setup/bond reuse, app-start/status exchange, volume/mute, cancellation/backoff; no fake success, generic AVRCP, A2DP/input change or reflected channels.
- Xiaomi network-specific sockets, persisted address alternatives, stale callback protection and reconnect hardening. Production device pairing/control/long press/wake remain unproven.
- One remote with rewind/fast-forward, localized errors, Samsung status/setup and foreground connection cleanup.

### ASSUMED / OPEN QUESTION

- Historical evidence is consistent with selective Wi-Fi client/bridge filtering, guest behavior or AP/mesh segmentation. It does not identify the responsible setting. Current LG Wi-Fi reachability makes a blanket ban on all Wi-Fi-to-LAN access less likely; it does not disprove selective filtering toward Xiaomi.
- Xiaomi may be offline/asleep, have changed addresses or be selectively unreachable; current observations cannot distinguish those. No aggressive scan was used. Tailscale is not proven causal. A legitimate socket-local LAN choice is implemented; a future Bluetooth HID adapter could avoid LAN dependence but would require TV-side association and protocol work. ADB/computer proxies are excluded from the product.
- LG exact input permission/firmware difference from the working CLI is unknown; the actual CLI identity/transcript and the app's on-device key were unavailable. No Mac approval prompt was triggered merely for research.
- Samsung hardware SDP support of the standard UUID path and status timing remain physical dependencies. The Mac is not bonded to the soundbar and no customer Android is attached for automation. Emulator radio behavior cannot answer them.

### Completed session instructions (historical)

Install the new debug APK over the existing app; reuse LG pairing. If the older app already erased its key after a 401, ordinary connection approval may be needed when prompted; do not use Forget or Refresh. With TV on, tap Xiaomi once to exercise the material alternate input path. Close Samsung Audio Remote, allow MyRemote Bluetooth access, select the existing paired soundbar and try volume down/up and mute/unmute while observing D.IN. This batches safe LG/Samsung interoperability; no repeated reset, router/ADB work, power-off or new Xiaomi pairing test. Record the outcomes, not protocol diagnostics. Physical confirmation is necessary for TV authorization and Bluetooth service interoperability; automated simulators cannot establish either.

## Final automated ownership-audit gate — 2026-10-04

**PROVEN — automated:**

- Gradle Wrapper command passed: `:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest --no-daemon --max-workers=2`. Final run completed in 3m 20s. Release output is unsigned; this is a validation build, not a distribution release.
- All **64 JVM unit/integration tests passed**, zero failures/errors/skips. Baseline had 39. Coverage includes domain routing and Last Channel; registration/key reuse and denied-command grant retention; actual TV-reported launcher fallback; request correlation; real local WSS registration/requests and pin rejection; real UDP wake packet reception; actual IPv4 refusal to IPv6 TCP fallback; actual IPv6 TLS with production Google trust policy; persisted alternate endpoints; stale discovery generation/appearance tokens; Samsung independent packet vectors, fragmented/coalesced framing, status correlation, timeouts, command serialization, persistence, permission failure and reconnect backoff.
- Debug and release lint both passed: **0 errors, 16 advisory warnings each**. Categories are target/dependency/Gradle update suggestions and SharedPreferences KTX suggestions. No lint baseline, disabled gate or NewApi suppression was added. Discovery's network hint is guarded at API 33.
- Final application/test APKs were installed on a temporary API 35 x86_64 emulator. `am instrument -w com.myremote.app.test/androidx.test.runner.AndroidJUnitRunner` executed **6 Compose tests**, all passed in 13.157 seconds. These cover routing callbacks, physical direction under RTL, rewind/fast-forward, authorization setup/error redaction, and Samsung permission/bond selection. They do not require physical devices.
- The actual production MainActivity launched on the emulator; English and Hebrew layouts were visually checked. Safe drawing insets protect controls from system bars. English/Hebrew resource XML parses, with matching sets of 74 unique names. `git diff --check` passed. No additional static checker is configured.
- Development tools/emulator are only validation infrastructure; they are not runtime dependencies. No production adapter uses ADB, a vendor CLI or an online computer. No TV state-changing Mac command, router change, push, tag or release was performed.

**IMPLEMENTED BUT UNPROVEN / FAILED / OPEN QUESTION:** the new LG launcher route, revised wake and Samsung adapter remain unproven on hardware. Prior input denial/wake failure remains evidence; the exact LG authorization cause is unresolved. MyRemote Xiaomi production pairing/control remains unproven and historical endpoints are currently unreachable. A simulator cannot establish the TV's actual authorization grant, Bluetooth SDP/radio service, audio change or panel wake. The single session above is the current physical dependency; do not repeat pairing resets or request more Xiaomi/router diagnostics.

## Successful customer session with 8a1c428 — 2026-10-04

**PROVEN — physical customer report:**

- The installed app requested LG authorization refresh. The customer refreshed and accepted approval on the TV.
- MyRemote switched to Xiaomi / HDMI 3, then back to Mac mini / HDMI 2. Input switching is now working; do not repeat the earlier failed refresh-only experiment.
- After MyRemote soundbar setup, Samsung Volume Up, Volume Down, Mute and Unmute all worked. Native RFCOMM interoperability is now physically established for this installation. The previous Audio Remote D.IN observation remains recorded; the latest report did not separately quote the soundbar display.

**OPEN QUESTION:** the precise reason for earlier LG 401 and whether the successful calls used direct input switching or the reported-app launcher fallback. The customer outcome does not identify the wire path. LG wake, Xiaomi reachability/pairing/control and soundbar power are unchanged physical boundaries.

**PROVEN — code and automated persistence:** registered LG key and pin are committed to private storage; successful registration removes the refresh-required marker. Background/foreground and recreated controller sessions send the saved key with forcePairing=false. Ordinary launches do not intentionally request fresh approval. The refresh in this session could have recovered a persisted old authorization marker or rejected grant, but its specific cause was not captured. Physical repeat-launch persistence has not been separately reported; no customer diagnostic is requested.

**Implemented sound UI refinement:** speaker icons replace the three visible sound labels; localized spoken labels remain. A valid soundbar mute reply selects the unmute icon/action description. Unknown status, disconnect or volume change clears the known mute state; the UI does not guess a hardware state from taps. No additional Bluetooth packet, query, pairing cycle or permission is introduced.

This completes the requested LG/Samsung interoperability session. No further physical test is requested for the icon refinement.

### Sound-icon follow-up automated gate — 2026-10-04

- Debug APK and unsigned release APK built; Compose test APK compiled. Gradle gate `:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest --no-daemon --max-workers=2` passed in 2m 19s.
- **65 JVM tests passed**, zero failures/errors/skips. New credential-reuse regression covers explicit refresh, background/foreground and controller recreation with saved keys and forcePairing=false. Samsung controller assertions verify reported true/false mute state and unknown-state invalidation after volume/disconnect.
- **7 Compose tests passed** on API 35 emulator in 9.074 seconds. New coverage checks icon-only sound controls, localized content descriptions, volume/mute action routing and confirmed mute/unmute display state.
- Debug/release lint: **0 errors and 16 advisory warnings each**. English/Hebrew sets match at 76 unique strings; speaker-vector XML parses; staged diff check passes. No dependency, permission or wire-packet changes.
- Latest physical success above belongs to build 8a1c428. The icon refinement is automatically verified and does not imply new physical wake/Xiaomi proof. No repeated hardware session is requested.
