# Device validation

These observations came from physical tests supplied by the product owner. The app has not yet performed hardware control.

## Proven

- LG 55UK6700YVD webOS TV: LAN pairing, input list query, and direct HDMI switching succeeded with a Windows CLI. Switching from PC to HDMI 3 / Xiaomi was observed.
- Xiaomi TV Box S (3rd Gen): LAN reachability and Android key input were proven using exploratory ADB over Wireless Debugging. This does not validate the production remote protocol.
- yes+: `KEYCODE_1` switched to channel 1 during playback.
- yes+: `KEYCODE_LAST_CHANNEL` did not work. Long `DPAD_CENTER` opened quick actions with “Last Channel” selected by default; a following short `DPAD_CENTER` switched to the previous channel.
- Samsung HW-M360: Samsung Audio Remote on an Android phone controlled Bluetooth Volume Up, Volume Down, and Mute while the soundbar stayed on optical `D.IN`.
- The Xiaomi advertises `Xiaomi TV Box._androidtvremote2._tcp.local.` with command port `6466`. From the Mac's Ethernet interface, the Remote Service accepts TCP connections over IPv6 on command port `6466` and pairing port `6467`. This proves listener reachability, not a completed TLS or pairing exchange.
- The Mac's Wi-Fi path cannot currently reach the Xiaomi over either address family, and the phone's manual IPv4 attempt fails before TLS or pairing.

## Not yet proven

- This app's Android TV Remote Service v2 discovery, pairing, connection, and key control on the physical Xiaomi. Code and automated protocol tests alone do not prove device interoperability.
- Xiaomi power and wake behavior through that protocol.
- LG power-on and power-off behavior in this app's own implementation.
- Samsung Bluetooth control protocol details and how to implement them in this app.
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

## Known TV input map

| Input | Device |
|---|---|
| HDMI_1 | PS5 |
| HDMI_2 | Mac mini |
| HDMI_3 | Xiaomi |
| HDMI_4 | PC |

The Samsung soundbar normally remains on `D.IN`. No production adapter in this repository uses ADB. Physical Xiaomi discovery, pairing, Connected status, and one D-pad action remain to be validated after the TCP reachability failure is isolated.
