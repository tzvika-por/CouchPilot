# Device validation

These observations came from customer physical tests and the recorded Mac investigation. Latest confirmation: LG wake on a8b9fcb succeeds after enabling Turn on via Wi-Fi, with Bluetooth wake left off. Background blinking is resolved; all three Off controls work. Samsung optical Auto Power Link wake with the TV is now physically confirmed. Standalone Samsung Bluetooth wake remains unsuccessful. The latest customer confirms LG HDMI_3 selection wakes Xiaomi; automatic post-wake control reconnection failed for Xiaomi and Samsung, while manual saved-device selection restored both quickly. LG inputs and Samsung volume/mute remain physically proven; earlier Xiaomi correctness was an aggregate success report. Google LAN interoperability, detailed standby behavior and the exact causes of failed wake remain unresolved. The latest record below supersedes historical unproven off/flicker statements.

## Proven

- Latest customer report: selecting Xiaomi/HDMI_3 in CouchPilot wakes the Xiaomi TV Box S (3rd Gen). After wake, Xiaomi and Samsung do not automatically reconnect in the installed fa7d43c package; selecting their saved devices in setup restores connections quickly and controls work. This establishes the LG-mediated wake route separately from the failed standalone HID wake.

- Latest customer confirmation after eba8ec5: normal app switching no longer causes TV blinking. All three device Off controls work; power-on through their current controls fails. Samsung remains a toggle whose off effect was observed.

- CouchPilot discovers the LG 55UK6700YVD, registers and reaches Connected. The latest customer session confirmed switching to HDMI 3/Xiaomi and back to HDMI 2/Mac mini after authorization refresh and TV approval. Earlier 401 failures are historical evidence below.
- CouchPilot Samsung setup succeeds; Volume Up, Volume Down, Mute and Unmute worked on the HW-M360 in the latest customer session.
- LG 55UK6700YVD webOS TV: LAN pairing, input list query, and direct HDMI switching succeeded with a Windows CLI. Switching from PC to HDMI 3 / Xiaomi was observed.
- Xiaomi TV Box S (3rd Gen): LAN reachability and Android key input were proven using exploratory ADB over Wireless Debugging. This does not validate the production remote protocol.
- yes+: `KEYCODE_1` switched to channel 1 during playback.
- yes+: `KEYCODE_LAST_CHANNEL` did not work. Long `DPAD_CENTER` opened quick actions with “Last Channel” selected by default; a following short `DPAD_CENTER` switched to the previous channel.
- Samsung HW-M360: Samsung Audio Remote on an Android phone controlled Bluetooth Volume Up, Volume Down, and Mute while the soundbar stayed on optical `D.IN`.
- Historical 2026-10-03 observation: the Xiaomi advertised `Xiaomi TV Box._androidtvremote2._tcp.local.` with command port `6466`. From the Mac's Ethernet interface, the Remote Service accepted TCP connections over IPv6 on command port `6466` and pairing port `6467`. This proves listener reachability, not a completed TLS or pairing exchange.
- Historical Mac Wi-Fi probes could not reach Xiaomi over either address family; the phone's manual IPv4 attempt failed before TLS or pairing. An early 2026-10-04 probe also failed on Ethernet while the Xiaomi was off; later powered-on Ethernet probes succeeded, as recorded below.

## Not yet proven

- This app's Android TV Remote Service v2 discovery, pairing, connection, and key control on the physical Xiaomi. Code and automated protocol tests alone do not prove device interoperability.
- Xiaomi power and wake behavior through that protocol.
- LG wake reliability across extended standby, repeated cycles and network changes. One physical wake now succeeds after enabling Turn on via Wi-Fi; no repeat reliability session is requested.
- Automatic LG reconnection without another approval across repeated physical launches. Credential persistence/reuse is proven automatically; the customer has not yet reported repeated launches. No repeat pairing test is requested.
- Independent Samsung wake from a disconnected session. The live-session toggle now physically turns it off.
- Automatic foregrounding or launching of yes+.
- CouchPilot LAN Remote Service v2 pairing, command channel and long press on the physical Xiaomi. Bluetooth correctness has separate aggregate customer evidence; exact long-press timing was not captured.
- The exact router, access point, or filtering setting responsible for Wi-Fi isolation.

## Failed CouchPilot physical test — 2026-10-03

- Xiaomi TV Box S (3rd Gen) is `192.0.2.8`; the box itself reported `wlan0 = 192.0.2.8/24`. Windows ADB still reaches it.
- The Android phone is `192.0.2.7`. CouchPilot found no Xiaomi through `_androidtvremote2._tcp.` discovery.
- Manual pairing to `192.0.2.8:6467` failed at TCP connect from `192.0.2.7` after 8 seconds with `EHOSTUNREACH (No route to host)`. TLS and the Polo pairing exchange were never reached.
- Temporarily disabling Tailscale on the phone did not change the result. Android App Info displayed “No permissions required.”
- Protocol review confirms that v2 normally advertises the command service on `_androidtvremote2._tcp.` with command port 6466, while pairing uses TCP 6467. The app uses the resolved command port and a separate pairing port. Its manifest declares `INTERNET`, `ACCESS_WIFI_STATE`, and `CHANGE_WIFI_MULTICAST_STATE`; it holds a Wi-Fi multicast lock during discovery. With `targetSdk = 36`, Android's current local-network guidance says `INTERNET` implicitly grants local-network access unless Android 16 local-network restrictions were explicitly opted in. App Info does not display normal install-time permissions, so its wording alone does not prove they are missing.
- The app does not bind the process or socket to a network. The reported source address `192.0.2.7` is consistent with the phone's Wi-Fi path. An incorrect PIN hash was found separately in code review and fixed; it cannot cause the earlier TCP connect error.

## Mac network-side investigation — 2026-10-03

- The Mac has `en0 = 192.0.2.3/24` (Ethernet) and `en1 = 192.0.2.19/24` (Wi-Fi). Its ordinary route to `192.0.2.8` uses `en0`.
- On Ethernet, TCP `192.0.2.8:8009` accepted a connection, confirming IPv4 reachability to the Xiaomi's Cast service. ICMP echo received no reply. TCP `192.0.2.8:6466` and `:6467` each timed out; neither accepted an IPv4 connection during these probes.
- mDNS advertised `Xiaomi TV Box._androidtvremote2._tcp.local.` on interface `en0`. Its SRV target was `tv.local.:6466`; A resolved to `192.0.2.8`. AAAA included `2001:db8:7::2` and `2001:db8:7::3`. Both IPv6 addresses accepted TCP on `6466` and `6467` through `en0` (confirmed by route lookup). The record's advertised command port matches CouchPilot's default and resolved command port; pairing port `6467` is separate and was accepting IPv6 connections.
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

- Proven before CouchPilot: the LG 55UK6700YVD was reachable from Windows, accepted webOS pairing, returned its input list, and switched directly to HDMI 3 / Xiaomi.
- At the 2026-10-03 boundary, implemented in CouchPilot pending a physical test: SSDP discovery and manual host setup, prompt registration with a persisted client key, secure WebSocket with a persisted certificate pin, input enumeration and ID-based switching, `system/turnOff`, and Wake-on-LAN.
- The known installation MAC addresses are wired `<device-mac>` and Wi-Fi `<device-mac>`. They are saved with the target device configuration; they are not general LG model constants. Whether this TV wakes from either interface remains unproven.
- The initial validation plan was to stop after registration, Connected status, and one HDMI 3 switch. Power-off and Wake-on-LAN should be tested only after that path is confirmed. No further Xiaomi test is part of this milestone.

## Ownership-audit boundary before the successful customer session — 2026-10-04

### PROVEN

- Latest customer evidence: CouchPilot LG discovery, registration, Connected and Power Off succeeded. Windows CLI input switching (HDMI 3 included) succeeded. Samsung Audio Remote volume/mute remains proven on D.IN. ADB key semantics remain proven independently of production protocol.
- Fresh Mac passive/low-rate checks: en0 192.0.2.3 and en1 192.0.2.19. LG 192.0.2.4 responds to SSDP and TCP 3000/3001 on both. Description at http://192.0.2.4:1070/ reports exact modelNumber 55UK6700YVD, UUID <device-uuid>, WebOS/4.1.0 UPnP/1.0 advertisement. No new pairing or state-changing TV command was sent from the Mac.
- Current DHCP identifies gateway/DNS/server 192.0.2.1, /24, server name <gateway-id>, suggesting Sagemcom F@ST 5674 family. Route to old Xiaomi IPv4 goes through en0. No router credentials/settings/network changes.
- Public Samsung Audio Remote APK signature verified; static protocol transport/command observations recorded in SAMSUNG_M360_PROTOCOL.md. No APK/vendor source is shipped or executed.

### FAILED

- CouchPilot LG input 401 persisted after unpairing, fresh pairing and refresh. Previous WOL did not wake the actual LG. Those failures remain; new code is not physical proof.
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

Install the new debug APK over the existing app; reuse LG pairing. If the older app already erased its key after a 401, ordinary connection approval may be needed when prompted; do not use Forget or Refresh. With TV on, tap Xiaomi once to exercise the material alternate input path. Close Samsung Audio Remote, allow CouchPilot Bluetooth access, select the existing paired soundbar and try volume down/up and mute/unmute while observing D.IN. This batches safe LG/Samsung interoperability; no repeated reset, router/ADB work, power-off or new Xiaomi pairing test. Record the outcomes, not protocol diagnostics. Physical confirmation is necessary for TV authorization and Bluetooth service interoperability; automated simulators cannot establish either.

## Final automated ownership-audit gate — 2026-10-04

**PROVEN — automated:**

- Gradle Wrapper command passed: `:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest --no-daemon --max-workers=2`. Final run completed in 3m 20s. Release output is unsigned; this is a validation build, not a distribution release.
- All **64 JVM unit/integration tests passed**, zero failures/errors/skips. Baseline had 39. Coverage includes domain routing and Last Channel; registration/key reuse and denied-command grant retention; actual TV-reported launcher fallback; request correlation; real local WSS registration/requests and pin rejection; real UDP wake packet reception; actual IPv4 refusal to IPv6 TCP fallback; actual IPv6 TLS with production Google trust policy; persisted alternate endpoints; stale discovery generation/appearance tokens; Samsung independent packet vectors, fragmented/coalesced framing, status correlation, timeouts, command serialization, persistence, permission failure and reconnect backoff.
- Debug and release lint both passed: **0 errors, 16 advisory warnings each**. Categories are target/dependency/Gradle update suggestions and SharedPreferences KTX suggestions. No lint baseline, disabled gate or NewApi suppression was added. Discovery's network hint is guarded at API 33.
- Final application/test APKs were installed on a temporary API 35 x86_64 emulator. `am instrument -w com.myremote.app.test/androidx.test.runner.AndroidJUnitRunner` executed **6 Compose tests**, all passed in 13.157 seconds. These cover routing callbacks, physical direction under RTL, rewind/fast-forward, authorization setup/error redaction, and Samsung permission/bond selection. They do not require physical devices.
- The actual production MainActivity launched on the emulator; English and Hebrew layouts were visually checked. Safe drawing insets protect controls from system bars. English/Hebrew resource XML parses, with matching sets of 74 unique names. `git diff --check` passed. No additional static checker is configured.
- Development tools/emulator are only validation infrastructure; they are not runtime dependencies. No production adapter uses ADB, a vendor CLI or an online computer. No TV state-changing Mac command, router change, push, tag or release was performed.

**IMPLEMENTED BUT UNPROVEN / FAILED / OPEN QUESTION:** the new LG launcher route, revised wake and Samsung adapter remain unproven on hardware. Prior input denial/wake failure remains evidence; the exact LG authorization cause is unresolved. CouchPilot Xiaomi production pairing/control remains unproven and historical endpoints are currently unreachable. A simulator cannot establish the TV's actual authorization grant, Bluetooth SDP/radio service, audio change or panel wake. The single session above is the current physical dependency; do not repeat pairing resets or request more Xiaomi/router diagnostics.

## Successful customer session with 8a1c428 — 2026-10-04

**PROVEN — physical customer report:**

- The installed app requested LG authorization refresh. The customer refreshed and accepted approval on the TV.
- CouchPilot switched to Xiaomi / HDMI 3, then back to Mac mini / HDMI 2. Input switching is now working; do not repeat the earlier failed refresh-only experiment.
- After CouchPilot soundbar setup, Samsung Volume Up, Volume Down, Mute and Unmute all worked. Native RFCOMM interoperability is now physically established for this installation. The previous Audio Remote D.IN observation remains recorded; the latest report did not separately quote the soundbar display.

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


## Customer power correction and autonomous follow-up — 2026-10-04

**PROVEN — customer:** Xiaomi was off during the latest failed Mac reachability check, then was turned on. The prior all-interface outage must not be attributed to the app or to a newly proven network cause.

**PROVEN — fresh non-invasive Mac checks:** en0 (192.0.2.3) accepts IPv4 TCP 6466, 6467 and 8009 to Xiaomi 192.0.2.8. IPv6 6466/6467 also accept. TLS-only checks on 6466/6467 negotiate TLS 1.3 and present certificate SHA-256 <device-certificate-sha256>. No Polo registration, remote key or TV approval was initiated. en1 (192.0.2.19) still times out on all three IPv4 ports and both IPv6 Remote Service ports. LG TCP 3000/3001 and SSDP remain reachable on both interfaces.

Remote Service v2 appears only on en0 as Xiaomi TV Box._androidtvremote2._tcp.local., advertising tv.local.:6466. Resolved addresses and TXT are recorded in GOOGLE_TV_PROTOCOL.md; the service hostname changed since historical investigation. Pairing 6467 remains directly observed, not inferred from the unknown wp=6465 TXT attribute. An existing Tailscale TV peer has a different certificate, so it is not used as a Xiaomi workaround.

**ASSUMED / OPEN QUESTION:** selective Wi-Fi station/bridge filtering best fits powered-on wired reachability and Wi-Fi failure, while LG remains reachable. AP isolation, guest/mesh segmentation and exact router configuration remain unidentified. Phone-to-Xiaomi reachability and CouchPilot production pairing/commands are not newly proven by Mac TLS success.

**PROVEN — automated:** debug and unsigned release APKs build; 70 JVM tests pass with zero failures/errors/skips; debug/release lint each reports 0 errors and 16 advisory warnings; Compose test APK compiles. Full wrapper gate passed in 3m 17s. Installed APKs on API 35 emulator execute 7 Compose tests successfully in 15.313s. Five new tests use actual IPv6 mutual TLS, independent pairing/command wire vectors, all keys/Last Channel, standby reporting and native socket cancellation. These establish implementation behavior without claiming device success.

**Customer involvement boundary:** no repeated LG/Samsung test or hardware test for sound icons. Continue autonomous engineering toward one complete candidate. Only one final connection/correctness session may be needed for physical behavior that cannot be simulated; no request is made at this follow-up boundary.


## Bluetooth fallback candidate — 2026-10-04

**IMPLEMENTED BUT UNPROVEN physically:** native Android Bluetooth HID control for Xiaomi avoids the remaining IP-path restriction while preserving the Google TV LAN adapter. Explicit selection, ordinary one-time accessory pairing, Android bond reuse, global keys/media/digits/channels/Last Channel, callback-driven Connected, bounded reconnect and foreground cleanup are implemented. The observed Xiaomi BT address is environment configuration, not generic protocol code. No app depends on ADB, a CLI or an online computer.

**PROVEN — automated:** debug and unsigned release builds pass; all 79 JVM tests pass with zero failures/errors/skips; debug/release lint has 0 errors and 18 advisory warnings each; Compose test APK compiles. Nine added HID tests cover independent vectors, parsed descriptor input/output lengths, host report handling, readiness, persisted selection, timeout/backoff/permission handling, session replacement, release on cancellation and domain routing. All 10 Compose tests pass on the API 35 emulator, including three new Bluetooth setup tests. Existing LG, Google mutual-TLS, Samsung and RTL tests pass. Resource sets match at 85 unique names; XML and diff checks pass. Production startup and English/Hebrew Bluetooth setup were inspected in the emulator, including explicit permission handling. No physical Bluetooth connection is inferred from emulator success.

**OPEN QUESTION / FAILED historical:** phone HID service availability, Xiaomi accessory/report compatibility, yes+ long OK, physical reconnect/standby wake and simultaneous soundbar radio behavior. LG's earlier wake failure remains; revised wake is unproven. Samsung power remains deliberately unavailable because the observed command is a toggle without a trustworthy power state. LG input/soundbar volume/mute customer successes remain valid and do not need repeating for these UI changes.

**Final hardware boundary:** one candidate connection/correctness session may establish Xiaomi Bluetooth association and useful global/yes+ control. Install over the existing app; retain working LG/Samsung setup. Make the phone visible from Xiaomi Bluetooth setup, approve normal pairing from Xiaomi Remotes & accessories, confirm Connected, then try Home/navigation/OK and yes+ digits/Last Channel. Reopening checks bond reuse. No router, ADB, Mac proxy, repeated LG authorization or UI-only hardware test is requested. Because LG input switching and Power Off were already physically confirmed, the same final session may finish with one TV power cycle to verify the materially revised wake path: select Mac mini, Power off, wait 10 seconds, then Power once to wake. If Bluetooth association fails, stop and return to engineering rather than adding exploratory steps. If association fails, return to autonomous investigation rather than a troubleshooting chain.


Final candidate wrapper run completed in 1m 25s. Final installed APK/test APK run completed all 10 Compose tests in 10.598s. The production app also registered its real native HID proxy/SDP on the emulator's virtual Bluetooth stack: callback-driven registration enabled phone visibility; background/foreground released and re-registered the profile using the saved selection. No TV peer was associated, no key report reached hardware and no AndroidRuntime crash was observed. Emulator Bluetooth/permissions changes are development-only; no customer phone or Mac radio settings were changed. This reduces native integration uncertainty without proving the S23+/Xiaomi combination.

The final session can include one soundbar volume tap while Xiaomi Bluetooth is connected to confirm the new simultaneous-radio scenario. This is not a repeat of the LG/Samsung command investigation or a hardware test for icons.


**Superseded — historical candidate sequence:** TV and Xiaomi on; install over the existing app and select Xiaomi. In Xiaomi setup choose Bluetooth and make the phone visible; approve its normal accessory pairing on Xiaomi. Confirm Connected and Home/direction/OK, then yes+ channels 1/2 and Last Channel. Reopen to check bond reuse and make one volume tap for dual-profile coexistence. Finish with the single LG wake cycle above. These are the remaining connection/correctness facts for a complete candidate; existing source/mute/icon tests are not requested again. Power Off All and unverified soundbar power remain absent.


## Mac Tailscale hypothesis tested directly — 2026-10-04

The customer clarified that Tailscale is connected on the Mac and believed it was not connected on Xiaomi. CouchPilot's direct LAN endpoint does not require Xiaomi to join the tailnet. The unrelated online TV peer remains excluded by its differing certificate.

**PROVEN — configuration/routing:** Mac Tailscale is Running, with no selected or automatic exit node and ShieldsUp=false. Subnet-route acceptance is enabled (RouteAll=true), but the actual route table has no VPN route for 192.0.2.0/24 or Xiaomi's local IPv6 prefix. Default internet traffic uses en0. Scoped Xiaomi routes use en0/en1; the Tailscale tunnel carries its overlay prefixes. ExitNodeAllowLANAccess=false is not an active exit-node LAN restriction when no exit node is selected. The meaning of RouteAll follows the [official Tailscale preference definition](https://github.com/tailscale/tailscale/blob/main/ipn/prefs.go); exit-node LAN behavior follows [Tailscale documentation](https://tailscale.com/docs/features/exit-nodes?tab=macos).

**PROVEN — controlled comparison:** the earlier IPv4 audit bound a source IP only. Fresh probes additionally set macOS IP_BOUND_IF=25 or IPV6_BOUND_IF=125 and read back physical interface indices 4/en0 and 7/en1. With Tailscale Running, Ethernet accepts Xiaomi IPv4 6466/6467/8009 and IPv6 6466/6467; Wi-Fi times out on every one. LG IPv4 3001 accepts on both, and Xiaomi's en1 ARP entry remains incomplete.

One brief autonomous comparison paused Tailscale, explicitly confirmed BackendState=Stopped, and repeated the same strictly bound sockets in parallel. **The result was identical:** all Ethernet probes accepted; all Xiaomi Wi-Fi probes timed out; LG Wi-Fi accepted. A finally block restored Tailscale to Running; WantRunning=true and all compared configuration fields were unchanged. No logout, authentication change, router/AP/Wi-Fi configuration change, TV command or persistent networking change was made.

**Conclusion:** the active Mac Tailscale connection is not required for the observed Wi-Fi failure; pausing it did not fix connectivity. Selective Wi-Fi neighbor/client/bridge filtering remains the strongest hypothesis. Exact AP/router cause is still unproven, and this is not a physical Android pairing/control success. The comparison does not claim to remove or audit every installed network extension. Packet capture was unavailable without an administrator password; no password or customer diagnostic was requested because the controlled socket comparison answered the active-VPN hypothesis.

Application code, APK and prior 79-JVM/10-Compose validation remain unchanged. This is a documentation-only evidence update. No further physical test is requested by this investigation; no push, tag or release.

## Failed Galaxy visibility — 2026-10-04

**FAILED — physical customer attempt:** Xiaomi’s Bluetooth accessory search did not show the Galaxy phone. A headset-like “HK BTA 10” item was shown; its identity is unknown and it was not treated as the phone. Pairing/control was not reached. Prior LG HDMI switching and Samsung volume/mute successes remain valid.

**PROVEN — code/platform review:** the earlier Bluetooth setup depended on phone discoverability plus TV inquiry filtering, ignored discoverability consent result, and could expire HID registration before the user began TV pairing. AOSP accessory criteria filter inquiry Class of Device; native HID SDP registration does not supply a public inquiry-class override. SDP keyboard classification alone is insufficient evidence of a visibility fix.

**STRONGEST HYPOTHESIS / NOT PROVEN:** TV classification filtering excluded the phone, with early expiration another possible cause. No Galaxy radio trace or Xiaomi vendor filter capture exists. This failure does not establish missing Bluetooth support or a defective phone.

**IMPLEMENTED BUT UNPROVEN physically:** revised explicit phone-initiated OS bonding to the configured Xiaomi address, selected-host bond receiver, callback-only connection, keyboard SDP subclass, user-action-based deadline and no ADVERTISE requirement. No external test is requested until internal gates finish. Successful Galaxy/Xiaomi bonding, key receipt, yes+ long press, coexistence and Xiaomi wake remain unproven. The previous discover-phone acceptance instructions are superseded.

**PROVEN — corrected candidate automated validation:** debug and unsigned release APK builds pass; all 84 JVM tests pass (0 failures/errors/skips); both lint variants pass (0 errors, 18 advisory warnings each); Compose test APK compiles. The final API 35 instrumentation run passes 12 tests in 10.497 seconds: 11 Compose tests plus one real public HID profile/SDP registration test. Five added JVM tests cover association, stored bond reuse, rejection, explicit-action timing and cancellation; the revised timeout test also proves reading instructions does not consume the pairing deadline. Duplicate bond callbacks/requests cannot initiate duplicate profile connects. Both localized resource sets have 85 unique matching names; XML and diff checks pass.

**PROVEN — production emulator execution:** native HID registration occurred in the production app; tapping Pair Xiaomi called Android createBond and produced BOND_NONE → BOND_BONDING plus the localized waiting state. No radio peer exists in the emulator, and this is not successful bonding. English and Hebrew/RTL setup were inspected. No customer phone, TV or router settings were changed during this investigation.

**Remaining decisive hardware boundary:** install the corrected candidate, open Xiaomi’s Pair accessory screen, then use CouchPilot’s Pair Xiaomi action and approve system prompts. Connected plus a received Home command establishes the initial association/control path. If that attempt fails, stop and return to autonomous investigation. Already-proven LG source and Samsung command tests are not requested again.

## Established Xiaomi Bluetooth association and reconnect-loop report — 2026-10-04

**PROVEN — customer report:** first association briefly connected for about two seconds, then disconnected and repeated connection attempts. The customer exited CouchPilot and Xiaomi accessory search; Xiaomi retained the phone as an accessory. Manual TV Connect and reopening the app eventually reached a connection. A subsequent screenshot shows CouchPilot LG, Xiaomi and Samsung all reporting Connected.

**NOT YET PROVEN:** Xiaomi key receipt, Home/navigation/digits, production yes+ long press/Last Channel, a measured stable connection duration, recovery without manual TV Connect, simultaneous command delivery, and Xiaomi standby wake. The screenshot is not evidence for these commands or for resolution of the Wi-Fi path restriction. The older statement that no physical Bluetooth bond was established is superseded by this report.

**PROVEN — code inspection and deterministic reproduction:** the old Connected callback reset automatic reconnect backoff, allowing short connections to perpetuate a three-second loop. Every transient loss also tore down SDP/profile registration. The new controller keeps registration through transient bonded loss, makes at most three outgoing retries at 3/6/12 seconds, and resets the budget only after at least 30 seconds of connection or an explicit retry. After exhaustion it passively accepts TV-originated recovery. The first physical disconnect’s cause remains unknown; no phone trace was collected. No unsupported claim that the radio/host cause is fixed is made.

**IMPLEMENTED BUT NOT PHYSICALLY PROVEN:** revised automatic recovery, preservation of profile/SDP during loss, stale-press ownership guards, and saved-bond UI with pairing disabled while Connected. No automatic unbond/re-pair, command replay, router change or additional customer diagnostic is introduced. Existing LG/Samsung commands and Google LAN implementation remain intact.

**Delivery:** customer requested the debug APK in the externally shared videos folder, allowing phone installation without a computer cable. The candidate is copied to <shared-folder>/MyRemote.apk and verified byte-for-byte against the validated build. No push, tag or release.

**PROVEN — final automated gate:** both debug and unsigned release APKs build; all 89 JVM tests pass with zero failures/errors/skips; debug/release lint passes with zero errors and 18 advisory warnings each; Compose APK compiles. All 13 instrumentation tests pass on API 35 in 11.407 seconds (12 Compose plus one real native HID registration). Added deterministic coverage reproduces repeated two-second connections, proves 3/6/12-second retry bounds, passive TV recovery without registration teardown or re-pairing, stable-link budget reset, manual retry, permission stop, and old-press ownership during connection replacement. Both localized string sets have 86 unique matching names; XML and git diff checks pass. No physical retry is requested for this code change.

## Customer correctness session — LG wake still fails — 2026-10-04

The customer reported that everything in the requested correctness session seemed to work except turning the LG TV back on. LG power-off succeeded; the second Power action did not wake it. This is a failed physical result for the revised wake implementation, superseding its earlier unproven state. The aggregate success report supports the preceding Xiaomi control/yes+ macro, concurrent volume and reopening checks; individual key receipts and timings were not separately described or captured. Xiaomi standby wake was not part of that session. No repeat session is requested.

The customer also asked what Watch yes+ does. Code inspection confirms RemoteAction.WatchYesPlus invokes selectInput(InputSource.XIAOMI): it switches LG to HDMI_3 and makes Xiaomi active. Automatic launch of the yes+ application is not implemented, and the button is not claimed to do so.

The shared file <shared-folder>/MyRemote.apk was rechecked against the current debug build: both are 23,213,837 bytes with SHA-256 54c2c23567c40bbf3bf0efe27bc144edb36395d10c0d7e9ce16fc99d9567cfdc. This evidence update changes no application code or APK; the prior 89-JVM/13-emulator/build/lint validation still applies. The cause of the LG wake failure remains unresolved, and no new router/TV-setting claim is made.


## Individual power controls and redundant shortcut removal — 2026-10-04

The customer requested Xiaomi and soundbar off controls and asked to remove Watch yes+ if it cannot open the app. **IMPLEMENTED:** independent Xiaomi Off (existing native sleep command on the selected LAN/HID adapter); named contextual LG/Xiaomi power; Samsung power toggle through its existing RFCOMM service, guarded by a live status query and one write, with persisted automatic reconnect suppression afterward. Soundbar power is labelled as power, not a guaranteed absolute Off/On command. Setup Retry explicitly resumes connection; no toggle is replayed. Watch yes+ is removed; the existing Xiaomi source button retains HDMI 3 selection.

**PROVEN — protocol inspection/automation only:** Google Remote Service v2 defines app-link launch, whereas CouchPilot's current Bluetooth HID connection sends standard key reports and has no Android package-launch request. A LAN-only launch implementation would not make this shortcut usable across the customer's known restricted Wi-Fi path. Reference: [maintained protocol client](https://raw.githubusercontent.com/tronikos/androidtvremote2/main/src/androidtvremote2/remote.py). No app ID/tile positions are guessed and no ADB/computer dependency is added.

**IMPLEMENTED BUT NOT PHYSICALLY PROVEN:** new Xiaomi standby action; Samsung power-toggle/standby effect. Neither is claimed to wake a disconnected device. **FAILED:** the latest LG wake test remains failed; no LG wake fix is inferred from this change. Existing successful source, sound and aggregate Xiaomi correctness results remain recorded above. No repeated owner diagnostic or UI-only test is requested.


Validated the individual-power candidate: debug and unsigned release APK builds; **96 JVM tests, zero failures/errors/skips**; debug/release lint, **zero errors and 20 advisory warnings each** (dependency/target recommendations, explicit SharedPreferences commit/KTX suggestions and an unused legacy power-label resource); Compose test APK compilation; **14 actual API 35 emulator tests** (13 Compose, one native HID registration), all passed in 12.652 seconds. The seven additional JVM tests cover dedicated routing/failure state, Samsung live-status gating, exact single-toggle/no-ack behavior, persisted suppression across recreation, uncertain-write non-replay, and standby disconnect during a pending write. One additional Compose test checks named power targets and independent actions in RTL; the existing controls test now asserts Watch yes+ is absent. All 90 English/Hebrew string keys match; XML parses and git diff check pass.

Delivery: <shared-folder>/MyRemote.apk was atomically replaced and verified against the built debug APK: 23,213,837 bytes, SHA-256 **8e63177d2f8ac80c1754a221262d0db635ef0f79c3ce5fe181d48314aa37eccc**. APK signature verification confirms the signing identity matches the previous shared APK, preserving update compatibility. No push, tag, release publication or new physical customer diagnostic was performed.


## Customer-reported TV blink on app switching — 2026-10-04

**PROVEN — customer report:** while using the existing build, moving from CouchPilot to any other phone screen produces a TV blink/refresh. The customer reasonably objects to keeping the phone occupied by the remote. The exact TV overlay or HDMI reaction was not captured, and the observation alone does not prove lost pairing.

**PROVEN — code review:** every Activity ON_STOP/onDispose explicitly paused HID, Google LAN, LG and Samsung; HID pause unregisters the phone keyboard. ON_START opened connections again. This is an unnecessary disconnect/reconnect cycle and the strongest implementation explanation for the reported symptom. Android's HID UID-importance rule requires a foreground service for continued registration after the UI becomes hidden.

**IMPLEMENTED:** application-owned RemoteSession, idempotent connected-device service ownership, background/recreation retention, discovery-only screen cleanup, quiet notification, optional notification consent and explicit Disconnect controls. Explicit disconnect does not send device power or input commands. Native setup uses the same OS bond/profile; no new pairing is forced. Google LAN and Samsung/LG adapters retain their protocol behavior. Force-stop/actual radio loss are outside ordinary Activity retention.

**NOT YET PHYSICALLY PROVEN:** absence of TV flicker on this customer's Galaxy/Xiaomi after app switching or screen lock. Automated native retention evidence can establish the lifecycle correction, not the visible panel outcome. Existing power-test limits and failed LG wake remain unchanged. No owner network/pairing diagnostic or repeat of successful source/sound/navigation testing is requested.


Background candidate validation: debug and unsigned release APK builds passed; **98 JVM tests, zero failures/errors/skips**; debug/release lint **zero errors, 20 advisory warnings each**; Compose test APK compiled; **17 actual API 35 emulator tests passed in 64.446 seconds**. The production MainActivity/RemoteConnectionService/native HID test preserves registration across Activity stop, return and recreation, then verifies explicit disconnect releases it. A second production service test passes with notification permission denied. The isolated emulator uses only a synthetic unbonded host and requests no physical pairing. The additional Compose test verifies connection cleanup invokes no device power/input action. Two pure tests cover idempotent lease start/stop and partial-start cleanup/retry. All 96 English/Hebrew string keys match; XML/diff checks pass. Native registration/loss diagnostics are allowlisted and reveal no credentials/addresses.

The debug APK replaced `<shared-folder>/MyRemote.apk` atomically and is byte-for-byte verified: **23,213,837 bytes**, SHA-256 **1ccd02218bde74aa6ad8a7f86be4b52c3d77574b198c2231f5f2ec2f3ae203a5**. APK signature verification confirms the previous shared signing identity is preserved. No push, tag, release publication, physical re-pair or owner diagnostic was performed. The real TV flicker outcome remains physically unconfirmed; existing power/wake evidence is unchanged.

## Latest customer confirmation and power-on research — 2026-10-04

**PROVEN — physical customer report, after eba8ec5:** ordinary app switching no longer makes the TV blink. LG, Xiaomi and Samsung Off controls turn their devices off. **FAILED — physical:** the current controls do not turn any of the three back on. This supersedes the earlier individual-power and background candidate's unproven states. No trace of the phone's saved LG MACs or exact device standby radio behavior was supplied; do not assign a root cause beyond the observations.

**PROVEN — primary research/code inspection:** LG's exact model has mobile wake capabilities; the official Samsung manual documents optical Auto Power Link and Bluetooth Power On. LG saved-selection metadata has a missing-MAC migration path that can prevent packet creation. Samsung's current control intentionally requires Connected and suppresses reconnect after its off toggle. Xiaomi HID WAKEUP requires a live connection; no manufacturer-documented disconnected phone-HID wake method was found. Detailed references and limits are in [POWER_ON_RESEARCH.md](POWER_ON_RESEARCH.md).

**NOT PROVEN:** physical wake through any replacement approach, enabled standby settings, SPP-only Samsung wake while preserving D.IN, or TV-to-Xiaomi CEC wake. LG source-to-TV CEC power synchronization must not be assumed to work in reverse. No new owner diagnostic or device setting/power change was requested or performed. Research/documentation only; application and the shared APK remain eba8ec5, with the prior 98-JVM/17-emulator/build/lint/Compose validation unchanged. Documentation diff and relative-link checks pass; no push, tag or release.

## Wake engineering implementation — 2026-10-04

**IMPLEMENTED / awaiting physical evidence:** LG missing-MAC migration, credential-preserving metadata/endpoint updates, secure hello UUID learning for legacy manual-host selection, pre-registration identity conflict rejection, separate localized wake errors and bounded wake cleanup. Samsung disconnected Power now attempts one RFCOMM connection/status exchange without toggle replay or audio/input changes, stopping/restoring suppression on failure or cancellation. LG network broadcast format and Samsung's ordinary volume/mute/off packets remain unchanged; Xiaomi and background retention are unchanged.

**Still NOT PROVEN:** physical wake for this candidate, current Mobile TV On/Bluetooth Power/Auto Power Link settings, actual hello UUID delivery on this LG, SPP acceptance in M360 standby or any new Xiaomi wake route. The earlier physical results remain: all three Off controls work, background blinking is gone, and all three wake attempts failed. No new phone/TV/router diagnostic, pairing approval or device setting change has been performed during engineering.

Wake candidate validation: debug and unsigned release APKs build; **115 JVM tests, zero failures/errors/skips**; both lint variants **0 errors, 20 advisory warnings each**; Compose test APK compiles; **17 API 35 emulator tests pass in 64.003 seconds**, including native background retention and notification-denied service behavior. The 17 new JVM tests cover six store migration/identity/credential cases, six controller registration/wake/timeout cases and five soundbar bounded reconnect/non-replay cases. The actual local WSS test also verifies hello identity receipt over the pinned production transport. All 98 English/Hebrew string keys match; XML, relative-documentation links and diff checks pass.

The first Gradle instrumentation run passed 16 tests and failed the notification-denied precondition because the reused/installed emulator app had notification permission granted. No runtime fix was needed: APKs were explicitly installed, POST_NOTIFICATIONS revoked on the **emulator only**, and the entire same test APK rerun directly with AndroidJUnitRunner. That corrected run passed all 17; no phone test, physical bond or device command was used. See README for the repeatable permission-precondition setup.

Delivery: `<shared-folder>/MyRemote.apk` was atomically replaced and verified byte-for-byte against the built debug APK: **23,213,837 bytes**, SHA-256 **29443c3d46e6118d7a80e7d8a3f65e0da50848bd52610f8bf3b5107e031d133e**. APK signatures match the previous shared build, preserving update compatibility and app data. Packaged DEX contains the new wake code. Physical wake remains unproven for this revision; previous failed results are retained. No push, tag or release.

## Latest wake candidate fails physically — 2026-10-04

**FAILED — customer report on a8b9fcb:** LG TV and Samsung soundbar still do not turn on. The wake candidate is now physically unsuccessful, superseding its UNPROVEN/awaiting-test status. Exact phone error/configuration and device standby settings were not supplied. Existing successful Off, source, volume/mute and background-blink results remain valid; no repeat of those controls is requested.

**PROVEN — new read-only Mac observation:** LG `192.0.2.4` accepts TCP 3000 and 3001 from both `192.0.2.3` (en0) and `192.0.2.19` (en1). ARP entries match the configured wired MAC `<device-mac>`. This does not prove panel power state, standby wake, Android broadcast delivery or current wake settings. No pairing prompt, wake packet, control command or persistent network/device change was made.

Code review found no additional proven fix. [POWER_ON_RESEARCH.md](POWER_ON_RESEARCH.md) records the public-API limits and remaining hypotheses. The only requested owner information is whether LG's General → Mobile TV On → Turn on via Wi-Fi is enabled; no new package, re-pair or repeat power cycle. Samsung optical wake has not been independently disproven because TV wake failed. Documentation-only checks pass; the prior 115-JVM/17-instrumentation/build/lint/Compose results remain unchanged and were not rerun. No push, tag or release.

## LG wake physically succeeds after prerequisite enabled

**PROVEN — customer report:** both LG Mobile TV On options were off. The customer enabled **Turn on via Wi-Fi** only, leaving Bluetooth off, and confirmed success in the requested single LG wake check using the already installed a8b9fcb APK. This supersedes the earlier LG wake failure/unknown-setting status. Disabled network wake was a demonstrated blocker for the latest attempt: enabling it was the only instructed change before success. No new APK, re-pair, key refresh or router change was required. The earlier configuration migration remains independently justified by code/tests; this result does not establish that the phone previously had missing MACs.

This confirms the observed LG On outcome, not repeated/extended-standby reliability, a panel-state API, Samsung optical/Bluetooth wake or Xiaomi wake. The customer answered the LG-specific check; no soundbar success is inferred. No repeat LG/source/volume/blinking session is requested. Documentation-only update: application and shared APK unchanged, prior automated gates retained, documentation diff/link checks run. No push, tag or release.

## Soundbar optical wake physically succeeds

**PROVEN — latest customer report:** after the focused Auto Power Link configuration-and-wake sequence, the customer confirms the soundbar turns on with the LG. The installed APK was unchanged. This establishes TV-following optical/D.IN wake in this installation and supersedes its unproven status. The customer did not report the setting's initial display, so do not claim Auto Power Link was originally disabled. The result does not prove independent SPP/Bluetooth wake, extended standby reliability, or CouchPilot volume/mute reconnection after this cycle. Standalone soundbar wake through the earlier app reconnect remains unsuccessful. Xiaomi wake is unchanged.

**Code-proven continuation:** after CouchPilot's soundbar Off toggle, persisted reconnect suppression survives Activity/process return. Optical wake alone cannot clear it; the previous volume/mute path failed immediately while disconnected. A new explicit volume/mute intention now uses the existing bounded 15-second connection/status initialization before sending the requested sound command exactly once. It never sends a power toggle, selects an audio input or re-pairs. Failure/cancellation restores suppression and closes only the owned attempt; ordinary app switching still honors Off. Existing connected commands retain their live session and are not retried after uncertain writes. Four deterministic regression tests cover Off/process recreation/volume recovery, mute recovery, silent service failure and cancelled native connect. This is automated reconnection evidence, not an additional physical control claim.

No new physical repeat of LG/input/volume/blinking is requested. Remaining TV-independent soundbar wake and Xiaomi standby compatibility are kept separate. Application validation/delivery for the reconnect change is recorded below after gates complete; no push, tag or release.

## Optical wake continuation — final automated validation and delivery

Debug and unsigned release builds pass; **119 JVM tests pass with zero failures/errors/skips**, including four new post-optical-wake control-recovery regressions. Debug and release lint each report **0 errors/fatal findings and 20 advisory warnings**. Compose test APK compiles. The complete isolated API 35 instrumentation suite passes **17 tests in 67.261 seconds**, including background native HID retention and notification-denied service behavior. No physical device is used by these tests. All 98 English/Hebrew string keys match; XML parses, 22 relative documentation links resolve and git diff checks pass. The physical optical wake result is independent customer evidence; automated tests do not establish production volume/mute recovery after this new path.

The debug APK atomically replaces `<shared-folder>/MyRemote.apk`; copied bytes are verified against the built APK: **23,213,837 bytes**, SHA-256 **37424b2cdbf95c10baa5a3f5db73bc713a214968b8cc1245853af86d567a05ce**. APK signature verification preserves the existing debug signing certificate `dd57e000b36d89ac8c47f77f370dbc9b2e3494b1f40c0a0077647ee2d755ddd6`. No re-pair, new physical test, push, tag or release. Next UI work awaits the owner's requested layout/style specifics; original Xiaomi standby wake remains unresolved rather than declared impossible. No speculative Xiaomi protocol change is made.

## Xiaomi HDMI-CEC wake candidate — not yet physically proven

Following owner-authorized Reddit and protocol review, LG → HDMI_3 → Xiaomi wake is a credible alternative. Exact-model users report related CEC workarounds; official Android source has conditional routing wake behavior. LG's guide does not guarantee reverse power synchronization. Current SIMPLINK/box HDMI-CEC settings and the physical TV-to-box wake outcome remain unknown. Existing Xiaomi Off, LG wake and source switching are separately proven; together they are not proof of this new standby scenario.

[XIAOMI_CEC_WAKE.md](XIAOMI_CEC_WAKE.md) defines one combined physical session using the existing APK and why further Mac networking cannot resolve HDMI behavior. No new command, pairing, code or package is introduced. Documentation diff/link checks pass; prior application gates are unchanged, not rerun. Record any result without converting an input acknowledgement into wake proof.

## Xiaomi HDMI wake confirmed; automatic connection gap — 2026-10-04

**PROVEN physically:** the customer reports that selecting Xiaomi wakes it. After TV off/on, Xiaomi and soundbar control sessions do not recover automatically; each is found quickly in setup and selecting it restores working control. Existing OS bonds are usable; another pairing/network investigation is unnecessary. LG network wake and Samsung optical wake remain independently confirmed.

**Code findings:** HID exhausts its finite retry budget during standby and retains the profile while waiting for TV/manual recovery. The wake/input coordinator never resumed that budget. Samsung's deliberately persisted post-Off suppression prevented service startup from reconnecting after optical wake. Neither failure requires changing CEC, Bluetooth packets, credentials or LAN routing.

**Implemented, not yet physically confirmed:** successful LG power-on and accepted Xiaomi input selection start nonblocking recovery of both saved devices. HID separates automatic reconnect from explicit pairing, interrupts backoff/resumes the paused budget and preserves native registration. Samsung makes up to three connection/status attempts with 15-second individual deadlines and 3/6-second backoff, then restores suppression on initial exhaustion or terminal permission/bond failure. A valid status is required for Connected. No power toggle, key replay, input replay or new bond is sent. Unconfigured/connected devices are skipped; explicit Disconnect, forgetting/reselection and power-off retain connection ownership/cleanup semantics. This covers app wake/source intentions; it does not infer an external TV power event from every transient WebSocket reconnect.

Sixteen deterministic new regressions cover coordinator routing/failure boundaries, selected transport, HID passive/backoff recovery with a bounded budget, missing bonds, retained profile, deduplication, Samsung startup timing/deadlines, suppression, permission failures and cancellation. Full gates and shared delivery are recorded below after completion. No repeated owner LG/input/volume/background test is requested, and no physical automatic-reconnect success is inferred.

## Automatic reconnect update — completed validation and delivery

Debug and unsigned release APK builds pass. All **135 JVM tests in 21 suites pass with zero failures/errors/skips**, including 16 new automatic-recovery regressions. Debug/release lint each report **0 errors/fatal findings and 20 advisory warnings**. Compose test APK compiles; all **17 actual API 35 instrumentation tests pass**, including native HID/background connection retention and notification-denied service behavior. Tests use fakes/an isolated emulator, not physical equipment. All 98 English/Hebrew keys match, XML parses, 26 relative documentation links resolve and git diff checks pass.

The debug APK atomically replaces `<shared-folder>/MyRemote.apk`. Verified shared copy: **23,213,837 bytes**, SHA-256 **222a9eac8e9351a9b990a8bb464544c15c67ed1b18f2acb412f05e68e48db807**. APK signature verification confirms the unchanged debug certificate SHA-256 `dd57e000b36d89ac8c47f77f370dbc9b2e3494b1f40c0a0077647ee2d755ddd6`; existing app data and Android bonds can be retained through an update. The new automatic post-wake recovery remains physically unproven; the customer's LG network wake, Samsung optical wake and Xiaomi HDMI wake successes remain distinct accepted evidence. No repeat owner test, new pairing, push, tag or release. UI redesign awaits the owner's specifics.

## Owner-supplied UI reference — 2026-10-04

The owner supplies a dark Hebrew remote image as the desired style. Implemented in Compose: compact live device cards, settings/help/power header, navy panels, cyan selected-source border/glow, original scalable icons, number grid and circular directional pad. The actual four HDMI inputs and all existing power/sound/channel/navigation/media intentions remain. No unsupported TV source or yes+ launch shortcut is introduced. Sound remains icon-only as previously requested. Connection management now appears under settings; no protocol, pairing, wake/reconnect or service-lifecycle code changes.

Three new emulator regressions cover settings/device/connection callbacks without remote commands, Hebrew selected states and accessible circular controls, and enlarged RTL text/digit/direction routing. Screenshots show a synthetic Connected fixture solely for visual inspection. Physical automatic reconnection from 4dc325d is still unconfirmed; this UI work supplies no new hardware evidence. No repeated owner test of existing connections/controls or a UI-only physical test is requested. Completed local gates/delivery follow below.

## Reference UI update — completed validation and delivery

The owner-provided design is implemented. Debug and unsigned release APKs build; all **135 JVM tests in 21 suites pass with zero failures/errors/skips**. Debug/release lint each report **0 errors/fatal findings and 20 advisory warnings**. Compose test APK compiles; all **20 actual API 35 instrumentation tests pass**, including three new reference-layout/accessibility/settings tests and the existing native HID/background-lifecycle suite. Hebrew screenshots were visually inspected; fixtures are marked as synthetic in UI_DESIGN.md. Android 8/API 26 compatibility is preserved. All **105 English/Hebrew string keys match**, resource XML parses, documentation links resolve and git diff checks pass.

The signed debug APK atomically replaces `<shared-folder>/MyRemote.apk`. Verified copy: **23,213,837 bytes**, SHA-256 **13356ba33b76ed6c16e0ef6c3710aac5a7c40bdc5f11489922c41ebb107a5e58**. Signature verification preserves debug certificate SHA-256 `dd57e000b36d89ac8c47f77f370dbc9b2e3494b1f40c0a0077647ee2d755ddd6`; existing setup/bonds can be retained when updating. This is a UI change: device protocol, command routes, wake/reconnect implementation and service lifetime are unchanged. Physical automatic reconnection from 4dc325d remains unconfirmed. No repeat owner connection/control/background or UI-only hardware test is requested. No push, tag or release.

## Code/security review — no additional physical test

Reviewed the reference UI baseline and hardened credentials, backups, discovery, parser bounds, request/close handling and cancellation without changing the device commands, HDMI mappings or accepted wake paths. Local HTTP/WSS simulators prove redirect destinations are not contacted and changed certificates fail. Android emulator tests verify native Keystore migration and malicious XML rejection; they are not tests of the physical LG/Xiaomi/Samsung. Existing physical control/wake/background success remains accepted. Automatic saved-device recovery after wake is still not physically confirmed. No owner hardware/network/router test is requested for this review.

## Completed review validation and delivery

Debug and unsigned release APKs build successfully. The aggregate `:app:test` runs every enabled unit-test variant (currently debug only): **152 tests in 22 suites pass**, with zero failures/errors/skips. Both lint variants report **0 fatal/errors and 21 advisory warnings**: target/plugin/library freshness and KTX style suggestions; the explicit checked preference commits are deliberate. The Compose test APK compiles. **23 actual API 35 instrumentation tests pass in 72.512 seconds**, including real Keystore migration/authentication, backup domains, safe XML parsing and the existing UI/HID/background suites. No physical device is required.

All **105 English/Hebrew resource keys match**, all 10 Android source XML files parse, 38 relative documentation links resolve, and `git diff --check` passes. The release APK contains neither fake controllers nor a debuggable application; its merged AndroidX startup provider is private and the exported profile-install receiver requires `android.permission.DUMP`. The debug APK contains the final credential/resolver classes; fake controllers remain available only for development. OSV returned no advisory IDs for 104 resolved release-runtime coordinates. The tracked Gradle wrapper JAR matches its official checksum; distribution integrity is pinned for future downloads.

The signed debug package atomically replaces `<shared-folder>/MyRemote.apk`: **23,213,837 bytes**, SHA-256 **`43f64bf538f88022b6b1b2d9567c8143ab179806e10b804b9f95108ed4f822c4`**. APK verification preserves the existing signing certificate SHA-256 `dd57e000b36d89ac8c47f77f370dbc9b2e3494b1f40c0a0077647ee2d755ddd6`. Successful legacy LG migration retains the existing authorization without a fresh TV prompt. Existing setup and Bluetooth bonds can survive an ordinary update. This remains a development APK, not a public release. No push, tag, release or repeat product-owner device test. Previously accepted physical control/wake evidence remains intact; automatic post-wake recovery remains physically unconfirmed.


## CouchPilot hardening boundary

The final contextual UI, per-device scheduling, Samsung generation guards and LAN-scope containment are validated with automated/emulator fixtures. Existing physical successes and failures above are retained; no additional physical test or power/control success is inferred. Stored device credentials and wake addresses remain private across an in-place update with the same applicationId/signing certificate. Fresh installations now explicitly configure wake/Bluetooth addresses instead of using household constants. Exact final results are recorded in FINAL_VALIDATION.md. Public history sanitation requires separate explicit approval; no remote/push/tag/release is created here.
