# Device validation

These observations came from physical tests supplied by the product owner. The app has not yet performed hardware control.

## Proven

- LG 55UK6700YVD webOS TV: LAN pairing, input list query, and direct HDMI switching succeeded with a Windows CLI. Switching from PC to HDMI 3 / Xiaomi was observed.
- Xiaomi TV Box S (3rd Gen): LAN reachability and Android key input were proven using exploratory ADB over Wireless Debugging. This does not validate the production remote protocol.
- yes+: `KEYCODE_1` switched to channel 1 during playback.
- yes+: `KEYCODE_LAST_CHANNEL` did not work. Long `DPAD_CENTER` opened quick actions with “Last Channel” selected by default; a following short `DPAD_CENTER` switched to the previous channel.
- Samsung HW-M360: Samsung Audio Remote on an Android phone controlled Bluetooth Volume Up, Volume Down, and Mute while the soundbar stayed on optical `D.IN`.

## Not yet proven

- This app's Android TV Remote Service v2 discovery, pairing, connection, and key control on the physical Xiaomi. Code and automated protocol tests alone do not prove device interoperability.
- Xiaomi power and wake behavior through that protocol.
- LG power-on and power-off behavior in this app's own implementation.
- Samsung Bluetooth control protocol details and how to implement them in this app.
- Samsung power control.
- Automatic foregrounding or launching of yes+.

## Failed MyRemote physical test — 2026-10-03

- Xiaomi TV Box S (3rd Gen) is `192.0.2.8`; the box itself reported `wlan0 = 192.0.2.8/24`. Windows ADB still reaches it.
- The Android phone is `192.0.2.7`. MyRemote found no Xiaomi through `_androidtvremote2._tcp.` discovery.
- Manual pairing to `192.0.2.8:6467` failed at TCP connect from `192.0.2.7` after 8 seconds with `EHOSTUNREACH (No route to host)`. TLS and the Polo pairing exchange were never reached.
- Temporarily disabling Tailscale on the phone did not change the result. Android App Info displayed “No permissions required.”
- Protocol review confirms that v2 normally advertises the command service on `_androidtvremote2._tcp.` with command port 6466, while pairing uses TCP 6467. The app uses the resolved command port and a separate pairing port. Its manifest declares `INTERNET`, `ACCESS_WIFI_STATE`, and `CHANGE_WIFI_MULTICAST_STATE`; it holds a Wi-Fi multicast lock during discovery. With `targetSdk = 36`, Android's current local-network guidance says `INTERNET` implicitly grants local-network access unless Android 16 local-network restrictions were explicitly opted in. App Info does not display normal install-time permissions, so its wording alone does not prove they are missing.
- The app does not bind the process or socket to a network. The reported source address `192.0.2.7` is consistent with the phone's Wi-Fi path. The evidence does not yet distinguish phone-to-box reachability, a phone-specific local-network restriction, and TV Remote Service availability. An incorrect PIN hash was found separately in code review and fixed; it cannot cause the earlier TCP connect error.

## Known TV input map

| Input | Device |
|---|---|
| HDMI_1 | PS5 |
| HDMI_2 | Mac mini |
| HDMI_3 | Xiaomi |
| HDMI_4 | PC |

The Samsung soundbar normally remains on `D.IN`. No production adapter in this repository uses ADB. Physical Xiaomi discovery, pairing, Connected status, and one D-pad action remain to be validated after the TCP reachability failure is isolated.
