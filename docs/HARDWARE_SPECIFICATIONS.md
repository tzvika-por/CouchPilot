# Hardware specifications and control evidence

Research date: 2026-10-04. **PROVEN** means a manufacturer/API fact or an identified observation; the text specifies which. **IMPLEMENTED BUT UNPROVEN** means MyRemote code exists but physical interoperability is pending. **FAILED** preserves a physical failure. **ASSUMED** and **OPEN QUESTION** do not establish device behavior.

## LG 55UK6700YVD

**PROVEN — manufacturer:** [LG's exact Israeli model page](https://www.lg.com/il/tv/lg-55UK6700YVD) lists webOS, Ethernet, 802.11ac Wi-Fi, Bluetooth 4.2, four HDMI ports, Simplink/HDMI-CEC, ARC on HDMI 2, optical audio out, LG TV Plus, Mobile TV On, Wi-Fi TV On and BLE TV On. This does not specify the exact installed firmware or guarantee standby wake in this installation.

**PROVEN — local observation:** SSDP from the actual television on 2026-10-04 reports `WebOS/4.1.0 UPnP/1.0`. Its UPnP description reports modelNumber `55UK6700YVD`, friendlyName `[LG] webOS TV UK6700YVD`, UUID `00000000-0000-4000-8000-000000000001`. Host at inspection was `192.0.2.4`, with ports 3000 and 3001 reachable from both Mac LAN interfaces. This is webOS 4.1 family evidence; installed LG firmware build is **OPEN QUESTION**. Do not equate a support-site downloadable firmware with installed firmware or apply webOS 26-specific regressions as fact.

**PROVEN — physical:** Windows CLI pairing, input enumeration and HDMI 3 switching. MyRemote discovery, registration, Connected and Power Off.

**FAILED:** MyRemote `tv/switchInput` returns 401 even after fresh pairing and authorization refresh; previous Wake-on-LAN did not wake the TV. A registration acknowledgement does not establish all requested rights.

**IMPLEMENTED BUT UNPROVEN:** SSAP WSS 3001, stored key/pin, input-ID mapping, alternate launch using a TV-reported input appId, revised LAN-directed WOL and bounded confirmation of a registered connection. No plaintext downgrade, vendor impersonation, test signature or TV settings change.

**OPEN QUESTION:** exact permission/firmware distinction from the unnamed Windows CLI; whether launcher fallback is allowed by the existing TV grant; standby NIC reception and Mobile TV On state. The app uses unsigned rights following [LG Connect SDK](https://github.com/ConnectSDK/Connect-SDK-Android-Core/blob/master/src/com/connectsdk/service/webos/WebOSTVServiceSocketClient.java). A 401 on one command must not invalidate a grant known to support Power Off. See [LG protocol](LG_WEBOS_PROTOCOL.md).

Installation-only wake addresses: wired `02:00:00:00:00:03`, wireless `02:00:00:00:00:01`. They identify this customer's television, not every LG model. New selection applies them only when its UUID matches this recorded installation.

## Xiaomi TV Box S (3rd Gen)

**PROVEN — manufacturer:** [Xiaomi's global specifications](https://www.mi.com/global/product/xiaomi-tv-box-s-3rd-gen/specs/) list Google TV, 2 GB RAM, 32 GB storage, 4K/60 decoding, dual-band Wi-Fi, Bluetooth 5.2, one HDMI 2.1 and one USB 2.0 port. There is no built-in Ethernet connector in the published port list. USB Ethernet compatibility and exact Android build remain **OPEN QUESTION**.

**PROVEN — physical/development:** ADB controlled yes+ digits and the long-OK/short-OK Last Channel sequence. This establishes app key semantics, not MyRemote production protocol interoperability. Historical mDNS advertised `_androidtvremote2._tcp` command 6466; pairing 6467 accepted TCP over Ethernet IPv6. The phone failed before TLS with EHOSTUNREACH. Current recorded endpoints no longer answer; do not hard-code historical addresses.

**IMPLEMENTED BUT UNPROVEN:** Remote Service v2 discovery, certificate-authenticated Polo pairing, persistent Android Keystore identity, IPv4/IPv6 address fallback, pinned command channel, heartbeats, short/long keys, reconnect and SLEEP/WAKEUP. Actual production pairing/control and wake are **OPEN QUESTION**. [Protocol schemas/maintained client](https://github.com/tronikos/androidtvremote2) establish wire behavior; [Google TV protocol](GOOGLE_TV_PROTOCOL.md) documents MyRemote's subset.

**ASSUMED:** CEC may assist activation if both TV and streamer enable it. No exact installed CEC setting or power coupling was measured. Do not use it as an autonomous power fallback or promise wake from a disconnected/full-off device. A Bluetooth HID remote is a possible legitimate LAN-independent product path, but would require TV-side association and a separate adapter; it is not implemented or physically validated. Neither ADB nor a permanently online computer is a product workaround.

## Samsung HW-M360

**PROVEN — manufacturer:** [Samsung's M360 specification sheet](https://image-us.samsung.com/SamsungUS/home/televisions-and-home-theater/home-theater/sound-bars/pdp/hw-m360-za/pdf/HW_M360_Spec_Final.pdf) describes Bluetooth control and a 2.1-channel system with wireless subwoofer. [Samsung Audio Remote's official listing](https://play.google.com/store/apps/details?id=com.samsung.samsungband) supports the M-series. [Exact model support/manuals](https://www.samsung.com/uk/support/model/HW-M360/EN/) are the source for optical D.IN and Bluetooth Power behavior. No evidence establishes Wi-Fi/SmartThings control for this model; modern Q-series APIs are not substituted.

**PROVEN — physical:** Samsung Audio Remote on Android controlled Volume Up, Volume Down and Mute while audio remained optical/D.IN.

**PROVEN — static vendor-app observation:** Samsung-signed Audio Remote 1.5.16 uses Bluetooth Classic RFCOMM, SPP UUID `00001101-0000-1000-8000-00805f9b34fb`, and a proprietary framed stream. Volume and mute are explicit control packets; they do not require A2DP playback or generic AVRCP. The verified public artifact, hashes, command vectors and research boundaries are recorded in [Samsung protocol](SAMSUNG_M360_PROTOCOL.md). This establishes what the vendor app sends, not current MyRemote physical success.

**IMPLEMENTED BUT UNPROVEN:** independent RFCOMM adapter, existing Android bond reuse, permission/setup, app-start/status negotiation, volume/mute, response validation, timeout/cancellation and reconnect. Power is withheld: vendor power-toggle bytes exist, but toggle is not a reliable explicit wake/off contract and standby availability is unmeasured. Bluetooth Power may activate Bluetooth playback/input; the app does not change the soundbar away from D.IN.

## Sources and activity safety

| LG input | Product source | Automation boundary |
|---|---|---|
| HDMI_1 | PS5 | Input selection only; no console shutdown or CEC power action. |
| HDMI_2 | Mac mini | Input selection only; computer wake/sleep is not implemented. |
| HDMI_3 | Xiaomi | One Watch yes+ action selects input; global streamer controls remain available. |
| HDMI_4 | PC | Input selection only; no OS/network wake commands. |

Simplink can couple power across connected devices depending on settings; [LG's Simplink guidance](https://www.lgappstv.com/manual/l16/common/option/simplink_all/eng/l16__option__simplink_all__eng.html) describes Auto Power Sync. MyRemote does not send CEC commands or implement Power Off All. Optical audio carries no HDMI-CEC control; Samsung volume must use its Bluetooth control service. Launching yes+ automatically is withheld until a supported reliable app-launch path is established. No installed app ID is guessed.
