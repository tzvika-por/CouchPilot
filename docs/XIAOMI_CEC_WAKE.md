# Xiaomi wake through LG HDMI-CEC

Reviewed 2026-10-04. Original research used application commit `fa7d43ce4cc4dcb8bb527c550c3d5f8451eace3b`. Latest customer evidence now confirms selecting Xiaomi/HDMI_3 wakes this box. The automatic reconnection gap found afterward is repaired below; no new CEC packet or device-setting change is introduced.

## Goal and evidence boundary

The owner explicitly asks to investigate LG → HDMI → Xiaomi wake before UI work. Target is Xiaomi TV Box S **3rd Gen**, connected directly to LG 55UK6700YVD **HDMI_3**. LG network wake and Samsung optical Auto Power Link wake are physically confirmed. Xiaomi's disconnected phone Bluetooth wake remains unsuccessful. A root module, replacement hardware, always-on computer or ADB proxy is not needed to investigate this alternative.

The candidate chain is MyRemote wakes LG via existing Wake-on-LAN, waits for registered Connected, then selects the TV-reported HDMI_3 input. LG's CEC controller may wake the selected source. MyRemote does not transmit directly onto the HDMI wire. The customer now physically confirms that selecting Xiaomi wakes this box. The exact CEC frames/vendor property were not captured; successful wake does not establish automatic Bluetooth control recovery.

## Primary protocol and manufacturer review

- [LG's same-generation SIMPLINK guide](https://eguide.lgappstv.com/manual/w18/atsc/Contents/control/simplinkuse_k_u_b/enga/w40__control__simplinkuse_k_u_b__enga.html) documents General → SIMPLINK (HDMI-CEC), master enablement, remote control of HDMI devices and power synchronization. Its Main Power On description establishes source → TV wake, not a guarantee of TV → source wake. Auto Power Sync also governs power-off propagation; it is not newly enabled as a prerequisite for this first input-selection check.
- [AOSP HDMI-CEC architecture](https://source.android.com/docs/devices/tv/hdmi-cec) makes standby CEC support hardware/vendor dependent and reserves direct HDMI control for system components. A phone HID application cannot directly address the box's HDMI controller.
- [Android 14 source-device handling](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/hdmi/HdmiCecLocalDeviceSource.java) handles Set Stream Path and active-source changes. [Playback handling](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/hdmi/HdmiCecLocalDevicePlayback.java) includes vendor-property-controlled routing behavior: NONE, WAKE_UP_ONLY or WAKE_UP_AND_SEND_ACTIVE_SOURCE. It also wakes an active source on CEC user-control input. These official files were retrieved directly with Gitiles format=TEXT into temporary inspection storage because the browser tool could not render them. No source code copied into the app. This establishes mechanisms and conditional behavior; Xiaomi's property, standby firmware and LG's actual emitted CEC sequence remain unknown.
- [Maintained aiowebostv endpoints](https://github.com/home-assistant-libs/aiowebostv/blob/main/aiowebostv/endpoints.py) expose tv/switchInput and TV power/screen APIs, without an established direct external-device CEC wake method. LG Connect SDK and lgtv2 were reviewed as well. No generic TV turnOn or POWER button is substituted for a source-specific wake, and no protected Luna bridge/settings command is invented. Absence in these clients is not proof that every firmware-private endpoint is absent.

## Community evidence requested by owner

- [Exact-model replacement-remote report](https://www.reddit.com/r/MiBox/comments/1vl2wox/original_replacement_remote_for_the_mi_tv_box_s/): Google Streamer/ONN remotes can sleep the 3rd Gen box but fail to wake it. A responder reports a TV → AVR → Xiaomi CEC wake chain; that topology differs from this installation.
- [3rd Gen replacement-remote discussion](https://www.reddit.com/r/MiBox/comments/1s51iao/hi_ive_had_my_mi_box_3rd_gen_a_few_days_but_dont/): reports standby wake failure with a third-party remote and proposes TV-to-box CEC wake.
- [September 3rd Gen thread](https://www.reddit.com/r/MiBox/comments/1weywxy/anyone_found_a_good_replacement_remote_for_the/): users report TV-remote CEC control, including Samsung and LG, but disagree about Bluetooth/deep-standby behavior. LG navigation is not itself proof of standby wake.

These are user reports, not manufacturer guarantees or a verified root cause. CoreELEC and older-generation reports are not treated as evidence for this stock 3rd Gen Google TV firmware. Root/wakelock reports change standby behavior and are not part of this application architecture.

## Implementation review before the successful physical session

LgTvController.powerOn sends validated configured WOL packets and waits at most 45 seconds for registered Connected. switchInput matches actual HDMI IDs, issues tv/switchInput and uses only TV-reported launcher metadata as the authorization-denied fallback. The existing Xiaomi source button therefore exercises the relevant LG input route already; no app change is needed to establish feasibility. A successful input acknowledgement does not establish box wake.

RemoteCoordinator's current Xiaomi Power intention still targets the streamer controller. It does not yet automatically perform LG wake plus HDMI_3 selection, and selecting an input does not optimistically mark the streamer awake. The retained native HID profile can accept a host-originated reconnection after CEC wake; successful reconnection and usable controls remain separate from a TV input acknowledgement. If the physical route succeeds, implement the explicit Xiaomi On coordination and deterministic success/failure/cancellation tests without replaying toggles or discarding pairing. Do not implement it as a claimed fix before compatibility is established.

The Mac has neither direct access to the TV/box HDMI bus nor the phone's stored LG authorization key. Further LAN probes would not answer this question. No fresh Mac pairing prompt, ADB repair, router interaction or exploratory network test is requested.

## Completed physical-session request using the existing APK (historical)

With both devices awake, ensure LG General → SIMPLINK (HDMI-CEC) is On and the Xiaomi's HDMI-CEC master option is enabled. Firmware menu labels on Xiaomi may vary; no exact unverified path is prescribed. Leave other CEC/energy/input settings alone for this first check.

In MyRemote select Mac mini so the main Power control targets LG. Tap the dedicated Xiaomi off control; if LG remains on, turn it off as well. Wait 30 seconds. Tap LG power to wake the TV, wait for LG Connected, then tap Xiaomi under Sources (HDMI_3). Observe for up to 30 seconds whether the Xiaomi interface appears without pressing its original remote's power button. Report that one result. This tests a new combined CEC wake route; there is no repeat pairing/source/volume/background diagnostic.

If it fails, record failure of this specific chain rather than declaring all CEC wake impossible. If the settings cannot be located, pause the dependent physical check rather than claiming it ran. No physical success is inferred from code, a simulator or Reddit.

## Validation and delivery

Documentation-only investigation: diff and relative-link checks run before local commit. No application tests/builds rerun; prior validated code remains 119 JVM tests, 17 API 35 instrumentation tests, both builds/lint variants and Compose compilation. `/Volumes/Expansion/Videos/MyRemote.apk` remains the verified fa7d43c build. No push, tag or release.

## Latest result and engineering continuation

**PROVEN — customer:** Xiaomi wakes on source selection. The saved Xiaomi and Samsung connections do not restore automatically after waking; manual setup selection finds them quickly and restores working controls. The requested feasibility session is complete. No additional CEC setting, pairing, router or ADB test is requested.

RemoteCoordinator now starts saved-device recovery after accepted Xiaomi HDMI selection and successful LG power-on. HID resumes its paused finite connection budget without replacing the registered profile or prompting for pairing. Samsung resumes a bounded status-validated connection after optical wake, without a power toggle. Connected devices are retained, selected Xiaomi transport is honored and explicit Disconnect cancels recovery. Code does not mark the box awake merely from SSAP input acceptance, infer every transient TV network reconnect as wake, or introduce a new Xiaomi power macro. Post-wake automatic connection behavior in this update remains unproven physically; it is covered by deterministic tests and normal build/lint/emulator gates.
