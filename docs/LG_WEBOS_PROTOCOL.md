# LG webOS LAN control

## Discovery and identity

The app sends a bounded SSDP `M-SEARCH` to `239.255.255.250:1900` for `urn:lge-com:service:webos-second-screen:1`, following LG's Connect SDK discovery filter. Each response supplies a source IP and usually a UUID. If `LOCATION` points back to that same host, the app reads its UPnP `friendlyName` and `modelName` with a short timeout and safe XML parser. Android permits cleartext HTTP for this local discovery description; the control channel uses WSS. Discovery lasts four seconds, holds a Wi-Fi multicast lock, and closes its socket and lock when stopped. Some webOS firmware or network arrangements may omit this service, so a manual host field remains available.

## Registration and storage

The app connects to `wss://<host>:3001/`, sends `hello`, then a `register` message with prompt pairing and a narrow manifest: `TEST_OPEN`, `TEST_PROTECTED`, `CONTROL_INPUT_TV`, `READ_INPUT_DEVICE_LIST`, and `CONTROL_POWER`. The TV may show an approval prompt. A `response` with `pairingType: PROMPT` keeps the UI in Pairing; a `registered` message must contain `client-key` before the controller proceeds. The app persists the selected host, device identity, client key, TV certificate SHA-256 pin, and configured wake MACs in app-private preferences. Android backup is disabled. Reconnects send the saved key and verify the saved certificate pin. The first registration trusts the TV's self-signed certificate after validity checking and pins it only after TV approval; it does not have a public CA identity. A changed certificate requires re-pairing. No registration secret is logged.

## SSAP requests and connection lifecycle

Messages are JSON objects with `type`, `id`, `uri`, and optional `payload`. The app assigns a unique ID to each request, matches replies by ID, rejects `error` and `returnValue: false`, and times out requests. One reader owns each WebSocket. The TV becomes Connected only after registration and `ssap://tv/getExternalInputList` succeed. A closed connection moves to Disconnected and retries after 5, 10, 20, 30, then 60 seconds; initial registration failures do not loop indefinitely. The ViewModel closes the transport, pending requests, discovery, and coroutine scope when it is cleared.

The source buttons map PS5 to `HDMI_1`, Mac mini to `HDMI_2`, Xiaomi to `HDMI_3`, and PC to `HDMI_4`. The controller first checks these stable IDs against the TV's returned `devices[].id` values, then sends `ssap://tv/switchInput` with `{ "inputId": "HDMI_N" }`. User-facing labels are not used for matching.

Power off sends `ssap://system/turnOff`. When the LG connection is absent, the Power button takes the wake path. Power on sends the standard 102-byte Wake-on-LAN magic packet on UDP port 9 to local broadcast destinations, using the selected device's persisted MAC addresses. The two known MACs for this installation are assigned to the selected TV by `LgInstallation`, separate from packet and SSAP code. A deployment with a different TV must replace this installation configuration. [LG's mobile power guidance](https://www.lg.com/us/support/help-library/lg-tv-how-to-set-up-the-lg-thinq-app-on-your-lg-smart-tv--20152745625356) says network power-on support depends on webOS version and TV settings. The actual wake path through this home's network is unproven.

## References and limits

Protocol and discovery behavior were checked against [LG's Connect SDK webOS TV service](https://github.com/ConnectSDK/Connect-SDK-Android-Core/blob/master/src/com/connectsdk/service/WebOSTVService.java), [its WebSocket registration client](https://github.com/ConnectSDK/Connect-SDK-Android-Core/blob/master/src/com/connectsdk/service/webos/WebOSTVServiceSocketClient.java), and [Connect SDK's external input API](https://connectsdk.com/en/latest/apis-and/and-webostvservice.html). The implementation here is independent. The LG 55UK6700YVD [product support page](https://www.lg.com/levant_en/support/product/lg-55UK6700YVD.AMF) identifies the target model.

Previous Windows CLI pairing, input enumeration, and switching to HDMI 3 are physically proven. MyRemote's registration, input switching, power off, and Wake-on-LAN remain implemented but unproven on the physical LG. This adapter uses secure port 3001; a TV exposing only plaintext port 3000 will require a deliberate compatibility decision after evidence from the first physical validation.
