# Power-on feasibility research

Research date: 2026-10-04. Application inspected at `eba8ec5a5b40da64ca5105018bbd31133c8d9f2a`. Research/documentation only: no application, device setting, connection policy or shared APK change.

## Latest physical evidence

The customer confirms that ordinary phone app switching no longer makes the TV blink. MyRemote turns off the LG TV, Xiaomi box and Samsung soundbar. Turning each back on through the current controls fails. These observations supersede earlier unproven off/flicker states; they do not establish which radio, standby setting or wake packet caused each failure. The Samsung command remains a toggle, with successful off observed from the on state.

## Conclusion

Losing a control connection prevents delivery on that connection. Standby hardware can still listen for a separate wake signal. This research concerns standby with mains power available; it does not describe waking unplugged equipment.

| Device | Available wake route | Engineering conclusion |
|---|---|---|
| LG 55UK6700YVD | Mobile TV On / Wake-on-LAN; model also lists Bluetooth wake | Keep pursuing network wake. Exact-model support exists; MyRemote's failed result does not prove it impossible. Saved configuration has a concrete gap to fix before another test. |
| Samsung HW-M360 | Optical Auto Power Link; paired-device Bluetooth Power On | Optical wake fits the existing D.IN installation. Direct SPP-only wake remains unverified; Bluetooth audio wake may change the input. |
| Xiaomi TV Box S (3rd Gen) | Original remote; possibly compatible HDMI-CEC or a standby network receiver | No documented reliable disconnected wake route found for a normal Android phone using this production HID adapter. Firmware/standby compatibility is unresolved, not proven impossible. |

## LG: supported hardware, unresolved implementation/configuration outcome

[LG's exact Israeli model specifications](https://www.lg.com/il/tv/lg-55UK6700YVD) list Mobile TV On, Wi-Fi TV On and Bluetooth wake. The actual television previously advertised webOS 4.1; its installed firmware build and standby settings remain unknown.

The [official 2018 webOS 4.0 Mobile TV On guide](https://eguide.lgappstv.com/manual/w18/atsc/Contents/settings/general/mobiletvon_k_u_b/enga/w40__settings__general__mobiletvon_k_u_b__enga.html) places the feature under General → Mobile TV On. Network wake requires the corresponding option enabled, mains power and the same network. This generation's guide limits Bluetooth wake to certain LG smartphones. The newer [ThinQ support article](https://www.lg.com/us/support/help-library/lg-tv-how-to-set-up-the-lg-thinq-app-on-your-lg-smart-tv--20152745625356) describes wider Android Wi-Fi/Bluetooth wake support, but its webOS 6.0 menu and general compatibility statement do not prove a Galaxy Bluetooth implementation for this older model. No undocumented BLE packet is invented.

[Home Assistant's maintained LG integration](https://www.home-assistant.io/integrations/webostv/#turning-on-the-tv-from-home-assistant) uses a separate wake mechanism and documents Wake-on-LAN, commonly over Ethernet. Its troubleshooting section identifies Mobile TV On / Turn On Via WiFi for 2017+ models. This corroborates the network approach; it does not require adding Home Assistant or an always-on computer to MyRemote.

MyRemote already sends standard 102-byte magic packets to the selected LAN's IPv4 directed broadcast, on UDP 9, for configured installation MACs. It binds the Android LAN/source and repeats three times. Registered WSS reconnection is required afterward; UDP send alone is not wake proof. Closing WSS is therefore not, by itself, an explanation for LG's failed wake. [LG Connect SDK's webOS service](https://github.com/ConnectSDK/Connect-SDK-Android-Core/blob/master/src/com/connectsdk/service/webos/WebOSTVDeviceService.java) has no supported SSAP powerOn implementation; ordinary control and standby wake are separate.

### Concrete code finding

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

## Engineering boundary

Research establishes LG and Samsung wake capabilities, not physical success in this installation. Xiaomi's phone-only disconnected wake is unresolved. No customer test, router interaction, re-pair, device power cycle, new hardware purchase or UI redesign is requested. Address the LG configuration gap first; retain Xiaomi/Samsung safeguards and the physically successful background retention. UI requirements can follow separately.

Only documentation changes in this update. `git diff --check` and relative documentation-link checks are required; the existing application validation remains 98 passing JVM tests, 17 passing API 35 emulator tests, debug/unsigned-release builds, both lint variants (0 errors, 20 advisory warnings each) and Compose test APK compilation. These gates were not rerun for research-only edits. The shared APK remains the previously verified background-service build. No push, tag or release.
