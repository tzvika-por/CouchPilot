# Changelog

Meaningful pre-1.0 development history; no public stable release or tag exists yet.

## [Unreleased]

### Added
- CouchPilot public-source metadata, Apache-2.0 license/notices, contributor guidance and CI workflows.
- Per-device bounded command workers with expiry, pending input supersession and concurrency regressions.
- Explicit LG wake-address and first-time Xiaomi Bluetooth-host configuration for reusable installations.

### Changed
- Visible branding to CouchPilot while retaining installed package and credential identity.
- Contextual main remote: common sources/power/sound; Xiaomi navigation/media/yes+ controls only for Xiaomi.
- Compact wrapping device status, clearer power target, top feedback/Snackbar and larger-font management actions.
- Device protocol display names and release diagnostics policy.

### Fixed
- Samsung stale-session state/error/mute publication across cancellation/reconnect.
- LG startup credential work on UI dispatcher; command credential reads and Samsung power persistence use IO.
- Unbounded HID state-event buffering and unrestricted production WAN endpoint resolution.

## [0.1.0] — development baseline

- Real LG SSAP/WSS pairing, input enumeration/switching, off, pinned registration reuse and LAN WOL.
- Google TV Remote Service v2 LAN pairing/commands with address-family fallback; public Classic HID fallback.
- Samsung HW-M360 RFCOMM volume/mute/off and bounded wake recovery with optical D.IN behavior.
- Process/service-owned connections to avoid TV blinking when changing phone apps.
- Yes+ numeric/previous-channel behavior, English/Hebrew remote, deterministic protocol and emulator tests.
- Keystore-wrapped LG grants, backup exclusions and bounded secure discovery.

1.0.0 is the target first stable release. It will be added as a released section only after remaining hardening/signing readiness is resolved.
