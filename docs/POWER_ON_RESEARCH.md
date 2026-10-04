# Power-on feasibility research

Latest update 2026-10-04: **LG wake succeeds physically** on `a8b9fcb6de580b54e6d57da1e38c6e5a8ba8fcf1` after enabling Turn on via Wi-Fi. Both LG wake settings were previously off; Bluetooth wake remains off. Samsung optical wake with the TV is now physically confirmed; standalone MyRemote Bluetooth wake remains unsuccessful. The original investigation below inspected `eba8ec5`; its identified implementation gaps were repaired in `a8b9fcb`. The latest follow-up changes documentation only; no new APK or device setting change.

## Earlier physical evidence — before enabling LG network wake

The customer confirms that ordinary phone app switching no longer makes the TV blink. MyRemote turns off the LG TV, Xiaomi box and Samsung soundbar. Turning each back on through the current controls fails. These observations supersede earlier unproven off/flicker states; they do not establish which radio, standby setting or wake packet caused each failure. The Samsung command remains a toggle, with successful off observed from the on state.

## Conclusion

Losing a control connection prevents delivery on that connection. Standby hardware can still listen for a separate wake signal. This research concerns standby with mains power available; it does not describe waking unplugged equipment.

| Device | Available wake route | Engineering conclusion |
|---|---|---|
| LG 55UK6700YVD | Mobile TV On / Wake-on-LAN; model also lists Bluetooth wake | Physically successful on the installed APK after enabling Turn on via Wi-Fi. Disabled network wake was the demonstrated prerequisite blocking the latest attempt. No additional code or pairing change was needed; long-term reliability is not established by one cycle. |
| Samsung HW-M360 | Optical Auto Power Link; paired-device Bluetooth Power On | Optical wake with the TV is physically confirmed after the Auto Power Link sequence. Standalone SPP wake remains unsuccessful; Bluetooth audio wake is separate and may change the input. |
| Xiaomi TV Box S (3rd Gen) | Original remote; possibly compatible HDMI-CEC or a standby network receiver | No documented reliable disconnected wake route found for a normal Android phone using this production HID adapter. Firmware/standby compatibility is unresolved, not proven impossible. |

## LG: supported hardware, unresolved implementation/configuration outcome

[LG's exact Israeli model specifications](https://www.lg.com/il/tv/lg-55UK6700YVD) list Mobile TV On, Wi-Fi TV On and Bluetooth wake. The actual television previously advertised webOS 4.1; its firmware build remains unknown. The customer found both Mobile TV On options off and enabled Turn on via Wi-Fi only; MyRemote subsequently woke it successfully.

The [official 2018 webOS 4.0 Mobile TV On guide](https://eguide.lgappstv.com/manual/w18/atsc/Contents/settings/general/mobiletvon_k_u_b/enga/w40__settings__general__mobiletvon_k_u_b__enga.html) places the feature under General → Mobile TV On. Network wake requires the corresponding option enabled, mains power and the same network. This generation's guide limits Bluetooth wake to certain LG smartphones. The newer [ThinQ support article](https://www.lg.com/us/support/help-library/lg-tv-how-to-set-up-the-lg-thinq-app-on-your-lg-smart-tv--20152745625356) describes wider Android Wi-Fi/Bluetooth wake support, but its webOS 6.0 menu and general compatibility statement do not prove a Galaxy Bluetooth implementation for this older model. No undocumented BLE packet is invented.

[Home Assistant's maintained LG integration](https://www.home-assistant.io/integrations/webostv/#turning-on-the-tv-from-home-assistant) uses a separate wake mechanism and documents Wake-on-LAN, commonly over Ethernet. Its troubleshooting section identifies Mobile TV On / Turn On Via WiFi for 2017+ models. This corroborates the network approach; it does not require adding Home Assistant or an always-on computer to MyRemote.

MyRemote already sends standard 102-byte magic packets to the selected LAN's IPv4 directed broadcast, on UDP 9, for configured installation MACs. It binds the Android LAN/source and repeats three times. Registered WSS reconnection is required afterward; UDP send alone is not wake proof. Closing WSS is therefore not, by itself, an explanation for LG's failed wake. [LG Connect SDK's webOS service](https://github.com/ConnectSDK/Connect-SDK-Android-Core/blob/master/src/com/connectsdk/service/webos/WebOSTVDeviceService.java) has no supported SSAP powerOn implementation; ordinary control and standby wake are separate.

### Historical code finding — repaired in a8b9fcb

`LgInstallation.forSelectedDevice()` supplies known installation MACs only for the observed TV UUID. `LgPairingStore.read()` returns persisted MACs without backfilling an empty list. `LgTvController.connectSelected()` ignores updated selection metadata when host/known UUID does not count as a device change. Consequently, an older selection with missing MACs can remain missing even after rediscovery/reselection, and packet generation rejects an empty MAC list. A manual selection without the known UUID can also remain without a wake address. Tests already cover valid packet generation; they do not cover this legacy metadata migration.

This is a code-proven failure path, **not proof of the phone's stored values or this physical failure's root cause**. Other unresolved causes are disabled standby wake, inactive-interface MAC selection or broadcast delivery/NIC behavior. Previous LG TCP reachability does not establish UDP broadcast delivery in standby. No new network probe, wake transmission or TV setting change was performed for this research.

Next engineering work: preserve the existing client key/certificate pin while merging identity-matched discovery metadata and backfilling only this verified installation's missing MACs; add deterministic migration/credential-preservation tests and distinguish missing configuration from a wake timeout. Do not solve metadata loss by calling `select()` blindly, because it clears pairing credentials. Gate any application change before asking for another physical wake check.

## Samsung: optical wake fits D.IN; Bluetooth wake needs separate verification

The [official M360 full manual](https://downloadcenter.samsung.com/content/UM/201808/20180824105838902/HW-M360-XN-FullManual_01_ENG_DEU_DUT_FRA_180803.pdf) was downloaded from Samsung's support endpoint and its English sections inspected:

- **ENG-10 (PDF page 14):** Auto Power Link can wake the soundbar when the TV starts, using the optical connection in D.IN. It defaults to enabled; compatibility can vary. Its current setting and behavior here are unknown.
- **ENG-16 and ENG-21 (PDF pages 20/25):** Bluetooth Power On supports wake by a previously paired device when enabled. The documented audio connection uses A2DP and can select BT input. That does not prove SPP-only wake while preserving D.IN.
- **ENG-4 (PDF page 8):** connections are optical, USB, AUX and power; there is no HDMI/CEC port.

The live RFCOMM toggle now physically turns the soundbar off. MyRemote deliberately suppresses reconnect after that toggle and requires Connected for another toggle; it has no disconnected power-on operation. Thus a failed second tap does not establish hardware wake impossibility. Automatic reconnect suppression remains important: reconnect itself could wake a device, and an uncertain power toggle must not be replayed.

The practical installation candidate is TV wake followed by optical Auto Power Link, which preserves TV audio. This cannot promise independent soundbar On while the TV remains off. A future explicit Bluetooth wake operation would need bounded connection/status handling and proof of SPP standby availability, without automatic toggle replay or an unwanted audio-input change. No undocumented Samsung settings packet is added.

## Xiaomi: current HID wake cannot cross a lost connection

Both Xiaomi production adapters require a Connected session: native Bluetooth sends System Wake Up; Google Remote Service v2 sends Android WAKEUP. The HID controller retains its registration after transient loss and already attempts bounded 3/6/12-second reconnection before passive listening. Keeping the phone app/service alive fixes app-induced loss; it cannot ensure the box's standby receiver accepts a connection.

[Android's public BluetoothHidDevice API](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice) provides paired-host connection and report delivery on the HID interrupt channel. These are not a documented generic wake broadcast for a disconnected host, nor a guarantee that a phone emulates an original remote's standby radio behavior. Exact Xiaomi wake behavior for the original remote versus a phone HID connection is unknown. No BLE impersonation or undocumented vendor wake payload is proposed.

[Xiaomi's exact-model FAQ](https://www.mi.com/uk/support/faq/details/KA-567165/) confirms Bluetooth and says the box has no IR support or built-in RJ45. The included remote's infrared feature must not be mistaken for an IR receiver in the box. The reviewed manufacturer information does not specify a usable Wake-on-WLAN packet or third-party HID standby wake contract. Sending LG-style magic packets to a Wi-Fi MAC would be speculative. A network wake command additionally needs a reachable, listening service; the established phone Wi-Fi path is restricted even when the box is on.

### HDMI-CEC is conditional, with direction important

The [2018 LG Simplink control guide](https://eguide.lgappstv.com/manual/w18/atsc/Contents/control/simplinkuse_k_u_b/enga/w40__control__simplinkuse_k_u_b__enga.html) describes a source turning on the TV and TV-off propagating to connected devices. It does **not** guarantee TV-on wakes a sleeping box. [AOSP's HDMI-CEC design](https://source.android.com/docs/devices/tv/hdmi-cec) makes standby CEC handling hardware/implementation dependent and limits direct control to privileged system components. A phone app cannot directly drive the TV's HDMI wire through Bluetooth HID.

Compatible TV-to-box CEC input/control behavior remains a potential path once the TV is awake, but neither LG input selection nor a CEC label proves this Xiaomi wakes. No unconditional TV → Xiaomi wake macro is justified. The Samsung optical connection cannot carry CEC.

## Original research boundary — before a8b9fcb

Research establishes LG and Samsung wake capabilities, not physical success in this installation. Xiaomi's phone-only disconnected wake is unresolved. No customer test, router interaction, re-pair, device power cycle, new hardware purchase or UI redesign is requested. Address the LG configuration gap first; retain Xiaomi/Samsung safeguards and the physically successful background retention. UI requirements can follow separately.

Only documentation changes in this update. `git diff --check` and relative documentation-link checks are required; the existing application validation remains 98 passing JVM tests, 17 passing API 35 emulator tests, debug/unsigned-release builds, both lint variants (0 errors, 20 advisory warnings each) and Compose test APK compilation. These gates were not rerun for research-only edits. The shared APK remains the previously verified background-service build. No push, tag or release.

## Subsequent engineering implementation

The user authorized proceeding after this research report. The configuration gap described above is now repaired: known-installation legacy MAC migration and same-device metadata updates preserve credentials; normal pinned WSS hello identity learning can complete manual-host setup after registration. Expected UUID conflicts fail before stored-key registration. Missing-address feedback is distinct from wake timeout; a failed LG wake cancels its bounded reconnect job.

The existing Samsung Power intention now attempts one bounded native connection when disconnected, without a power toggle or audio-input change. This is an engineering candidate for Bluetooth Power On, not proof that SPP wakes this hardware. Optical Auto Power Link remains device-controlled and unchanged. The earlier research-only validation paragraph describes that historical update; this new implementation requires fresh gates recorded in DEVICE_VALIDATION.md. No physical success, standby setting change or Xiaomi wake capability follows from these code changes.

## Latest failed wake candidate and remaining boundary

**FAILED — customer physical result:** after installing the wake candidate, neither LG nor Samsung turns back on. This supersedes the candidate's awaiting-validation status. It does not identify a phone error, prove its saved MAC values, or establish the state of either standby setting. Previously working input/volume/mute/Off controls and resolved background blinking remain accepted evidence.

**Autonomous read-only checks:** four source-bound IPv4 TCP connections to LG `192.0.2.4` succeeded: ports 3000 and 3001 from Mac Ethernet `192.0.2.3` and Wi-Fi `192.0.2.19`. The existing ARP entries on en0/en1 identify `02:00:00:00:00:03`, matching the installation's configured wired MAC. Both Mac interfaces are /24 with broadcast `192.0.2.255`. These observations establish current endpoint reachability and recorded MAC agreement, not panel state, standby reachability, phone packet transmission or broadcast reception. No registration, wake packet, power command, router login or device-setting change was performed.

**Code review:** packet construction, broadcast calculation, per-socket LAN/source binding, identity-specific MAC migration, explicit disconnected Samsung connection and bounded cleanup remain implemented and covered by existing deterministic tests. The manifest declares the networking/Bluetooth permissions used by these paths. No additional root cause was proven in this review. Increasing retransmissions or changing ports without device evidence is not a justified fix.

**LG prerequisite remains unknown:** the same-generation official guide requires General → Mobile TV On → Turn on via Wi-Fi enabled. The [maintained integration's troubleshooting](https://www.home-assistant.io/integrations/webostv/#wakeonlan-does-not-work) corroborates that setting for 2017+ models. [LG's public Settings Service](https://webostv.developer.lge.com/develop/references/settings-service) documents locale/country/accessibility keys through a TV-local Luna API; it does not document a remotely readable or writable Mobile TV On key. This does not prove every private firmware API unavailable. MyRemote's phone grant is not available on the Mac; a fresh pairing prompt or speculative private settings command is not used. A single read of that setting is the next decisive information needed, rather than another off/on cycle. Disabled standby reception and failed broadcast delivery remain hypotheses, not diagnoses.

**Samsung distinction:** the failed explicit SPP reconnect does not establish that the documented Bluetooth audio wake is supported through SPP. [Android's public A2DP API](https://developer.android.com/reference/android/bluetooth/BluetoothA2dp) exposes profile status, not a public app-controlled connect method; a hidden-API workaround or automatic audio-input switch is not added. Optical Auto Power Link depends on returning TV optical audio and its own setting. Since LG wake failed, this result does not independently test optical wake. The practical route for this D.IN installation remains establishing LG wake first; standalone MyRemote soundbar wake is still physically unsuccessful.

**Validation/delivery:** documentation-only follow-up; diff and relative-link checks run. Application gates are unchanged from a8b9fcb: 115 JVM tests, 17 actual API 35 instrumentation tests, both builds/lint variants and Compose compilation passed. They are historical validation, not fresh runs or proof of hardware wake. The existing shared APK is unchanged. No push, tag or release.

## LG wake physically succeeds after prerequisite enabled

**PROVEN — customer report:** both LG Mobile TV On options were off. The customer enabled **Turn on via Wi-Fi** only, leaving Bluetooth off, and confirmed success in the requested single LG wake check using the already installed a8b9fcb APK. This supersedes the earlier LG wake failure/unknown-setting status. Disabled network wake was a demonstrated blocker for the latest attempt: enabling it was the only instructed change before success. No new APK, re-pair, key refresh or router change was required. The earlier configuration migration remains independently justified by code/tests; this result does not establish that the phone previously had missing MACs.

This confirms the observed LG On outcome, not repeated/extended-standby reliability, a panel-state API, Samsung optical/Bluetooth wake or Xiaomi wake. The customer answered the LG-specific check; no soundbar success is inferred. No repeat LG/source/volume/blinking session is requested. Documentation-only update: application and shared APK unchanged, prior automated gates retained, documentation diff/link checks run. No push, tag or release.

## Soundbar optical wake physically succeeds

**PROVEN — latest customer report:** after the focused Auto Power Link configuration-and-wake sequence, the customer confirms the soundbar turns on with the LG. The installed APK was unchanged. This establishes TV-following optical/D.IN wake in this installation and supersedes its unproven status. The customer did not report the setting's initial display, so do not claim Auto Power Link was originally disabled. The result does not prove independent SPP/Bluetooth wake, extended standby reliability, or MyRemote volume/mute reconnection after this cycle. Standalone soundbar wake through the earlier app reconnect remains unsuccessful. Xiaomi wake is unchanged.

**Code-proven continuation:** after MyRemote's soundbar Off toggle, persisted reconnect suppression survives Activity/process return. Optical wake alone cannot clear it; the previous volume/mute path failed immediately while disconnected. A new explicit volume/mute intention now uses the existing bounded 15-second connection/status initialization before sending the requested sound command exactly once. It never sends a power toggle, selects an audio input or re-pairs. Failure/cancellation restores suppression and closes only the owned attempt; ordinary app switching still honors Off. Existing connected commands retain their live session and are not retried after uncertain writes. Four deterministic regression tests cover Off/process recreation/volume recovery, mute recovery, silent service failure and cancelled native connect. This is automated reconnection evidence, not an additional physical control claim.

No new physical repeat of LG/input/volume/blinking is requested. Remaining TV-independent soundbar wake and Xiaomi standby compatibility are kept separate. Application validation/delivery for the reconnect change is recorded below after gates complete; no push, tag or release.
