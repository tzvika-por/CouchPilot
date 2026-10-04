# LG webOS LAN control

## Evidence first

**PROVEN:** Windows CLI pairing/input enumeration/direct HDMI 3 switch; MyRemote discovery/registration/Connected, Power Off, and (latest customer session on 8a1c428) HDMI 3/Xiaomi → HDMI 2/Mac mini switching after authorization refresh/TV approval. **FAILED — historical:** input 401 on previous builds, including prior refresh attempts; previous WOL fails. The earlier stale-grant hypothesis and CONTROL_DISPLAY addition did not establish a fix. **OPEN QUESTION:** exact authorization difference from the unnamed working Windows CLI. Its registration transcript/key is not available on the Mac, and no Android device is connected for development inspection. No additional pairing prompt was initiated from the Mac.

On 2026-10-04 the actual LG advertises webOS 4.1, modelNumber 55UK6700YVD, at 192.168.7.4. Ports 3000/3001 and SSDP are reachable on Ethernet and Wi-Fi. WSS is already physically usable. Newer webOS 26 signature reports do not establish this model's cause.

## Discovery, transport and registration

Bounded SSDP M-SEARCH targets `urn:lge-com:service:webos-second-screen:1`, then reads same-host UPnP identity metadata with a safe XML parser/size/time limits. Exact modelNumber is preferred to generic modelName. Multicast lock and sockets are released on stop. Manual hostname/IP setup remains available.

Production connects to WSS port 3001 using an app-local trust manager. First approved registration records the certificate SHA-256 pin; reconnect checks it. The pin authenticates continuity, not a public CA identity (some LG generations share factory certificates). There is no automatic plaintext downgrade. WebSocket pings run every 15 seconds; incoming queue and message sizes are bounded. Local Wi-Fi/Ethernet socket factory and DNS selection avoid default VPN routing without rebinding the process.

Hello uses MyRemote identity. Registration uses `pairingType: PROMPT`, `forcePairing: false`, an unsigned outer manifest with manifestVersion 1 and appVersion 1.0. Requested rights are READ_INPUT_DEVICE_LIST, CONTROL_INPUT_TV, CONTROL_DISPLAY, CONTROL_POWER and LAUNCH. LAUNCH is for the alternate input application's launcher API; there are no LG test rights, signed block, vendor impersonation or copied signature. A stored client-key is reused. Revision is recorded for diagnostics, not treated as proof of granted rights or a reason to discard an otherwise working key. A registration-level 401 invalidates the grant and stops reconnection. A command-level 401 is a typed capability denial and leaves the registered connection/key intact, so proven Power Off remains usable. An input-list denial leaves other capabilities connected and unavailable input controls fail clearly.

Explicit Refresh remains available for a genuinely rejected registration; it preserves identity/pin/MACs and obtains TV approval without the old key. This is not the proposed solution to the repeatedly failed input test. Backup is disabled; credentials/pairing codes are never logged.

## Input requests and compatibility

Unique request IDs correlate SSAP JSON replies, a single reader owns the WebSocket, and `error`/false returnValue/timeouts fail the action. Source selection checks returned stable input IDs: PS5 HDMI_1, Mac mini HDMI_2, Xiaomi HDMI_3, PC HDMI_4.

First path: `ssap://tv/switchInput` with object payload `{inputId: "HDMI_N"}`. This matches [LG Connect SDK's external input code](https://github.com/ConnectSDK/Connect-SDK-Android-Core/blob/master/src/com/connectsdk/service/WebOSTVService.java) and [maintained aiowebostv endpoints](https://github.com/home-assistant-libs/aiowebostv/blob/main/aiowebostv/endpoints.py). They use broader registration rights; their correctness does not establish our target's minimum grant. [Current aiowebostv registration](https://github.com/home-assistant-libs/aiowebostv/blob/main/aiowebostv/handshake.py) uses an unsigned manifest. A [candidate public Windows CLI](https://github.com/klattimer/LGWebOSRemote) also uses tv/switchInput, but the customer did not identify their exact tool, so it is not claimed to reproduce that experiment.

Alternate path, only after input-switch 401: retain the TV's returned `devices[].appId` and request `ssap://system.launcher/launch` with `{id: reportedAppId}`. Launcher is an established SSAP API and LAUNCH is in [LG's open permission set](https://connectsdk.com/en/latest/apis-and/and-webostvservice.html). Never synthesize com.webos.app.hdmiN or use a user-editable source label. Missing appId or launcher denial fails without changing selected activity or discarding the grant. No fallback after timeout/transport failures, since the original switch may have executed.

**PROVEN — product outcome:** input switching succeeds in the latest customer session. **OPEN QUESTION:** whether those successful calls used direct switchInput or launcher fallback; the customer report contains no endpoint trace. The fallback is not independently proven as the root-cause fix. Structured logs distinguish switch_input/launch_input denied or accepted and preserve numeric 401 without key/payload dumps.

## Power and connection lifecycle

Off uses `ssap://system/turnOff`; physical success is already proven. No CEC cascade is intentionally requested. Power state is inferred from connectivity and successful commands, not a verified TV power subscription.

Wake packet is standard 102 bytes, directed to the selected LAN IPv4 prefix's broadcast on UDP 9, bound to that network/source, three transmissions 100 ms apart. VPN and unrelated interface broadcasts are excluded. New device selection receives this household's known MACs only for the observed installation UUID. Existing persisted configuration remains reusable. A missing configured MAC fails; other models do not inherit this household's addresses. Registered reconnection must succeed within 45 seconds; packet send alone never marks wake as confirmed. Registered connection is still not proof of an illuminated panel, so WOL physical success remains **UNPROVEN** and the previous physical result remains **FAILED**.

Unexpected disconnect retries at 5/10/20/30/60 seconds. App backgrounding closes sockets, discovery and pending work; returning reconnects stored devices. Registration errors stop aggressive retries. Local tests cover actual TLS/WebSocket exchange, pin mismatch, UDP packet receipt, denied-input grant retention, reported-app fallback, revision migration, correlation, persistence and cancellation.

**OPEN QUESTION:** why this TV's input authorization differs from the working CLI; its actual firmware/grants/standby receiver state. The evidence supports an endpoint-specific authorization rejection, not a network or HDMI-ID diagnosis. No TV/router setting was changed and no repeated re-pair experiment is requested.

## Approval reuse

The customer's successful refresh generated a stored grant and cleared authorization_refresh_required. Later ordinary app starts send that client-key with forcePairing=false. Backgrounding closes only connections; it retains pairing. Tests cover refresh → background/foreground → new controller with the same stored preferences. Another approval is expected only if local credentials are removed or the TV rejects/revokes them; approval every launch is not intended behavior. The observed refresh cause was not captured, so it is not attributed definitively to migration.
