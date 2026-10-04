# Engineering execution plan

2026-10-04 baseline: 662b866. Repository, history, adapters, UI, tests, documentation and latest physical evidence reviewed. Earlier authorization refresh did not fix LG inputs. LG power off works. Production soundbar was a fake.

1. **Implemented; physical behavior remains unproven where noted:** preserve LG's working grant on command denial; compare verified SSAP clients; retain returned input application IDs for a safe alternate launcher path; repair wake network targeting and confirm connectivity rather than equating UDP send with wake.
2. **Implemented; physical behavior remains unproven where noted:** independently implement Samsung RFCOMM control from verified vendor protocol observations; bonded-device setup, permissions, status response, cancellation, reconnection, deterministic harness.
3. **Implemented; physical behavior remains unproven where noted:** strengthen Xiaomi address persistence, discovery callback lifetime and local-network socket selection; keep TLS trust and production protocol. Do not attribute physical reachability to an unproven cause.
4. **Implemented; physical behavior remains unproven where noted:** finish media buttons, localizable failures and safe diagnostic events; preserve one remote and RTL.
5. **Passed:** debug/release builds, 64 JVM tests, debug/release lint (0 errors, 16 advisory warnings each), Compose APK compilation and 6 actual API 35 emulator tests, resource/diff checks. Coherent work committed locally after these gates.
6. **Awaiting unavoidable physical interoperability:** stop at the real TV authorization/Bluetooth service boundary. LG input/wake root causes and Xiaomi reachability are still open; this is not a completed useful release. One batched safe session only after implementation materially changes. No repeated pairing experiment, network administration or command-line customer work.

No physical-control claim follows from a fake server, emulator or successful build. No push, tag or release.

Latest customer session after 8a1c428: LG authorization refreshed/approved; Xiaomi and Mac mini input switching succeeded. Samsung setup/volume/mute/unmute succeeded. That interoperability boundary is resolved. Follow-up UI refinement replaces sound labels with accessible state-aware icons; LG credential reuse is verified across reconnection/controller recreation. Do not request the completed physical session again. LG wake and Xiaomi production control remain separate unresolved work.


## Current continuation after customer correction

1. Completed: accessible sound icons, persistent LG credential reuse, 65 JVM and 7 emulator tests. The customer has already proven LG inputs and Samsung volume/mute; never request those again for UI changes.
2. Completed: powered-on Xiaomi network/TLS investigation corrects the off-state outage; Ethernet works over both families, Wi-Fi still fails. Existing VPN peer identity differs and is excluded. Google production session/cancellation/reporting work passes 70 JVM and 7 emulator tests plus both build/lint variants.
3. Investigate a native Android Bluetooth HID streamer fallback, preserving Google TV LAN and avoiding ADB/online-computer dependencies. Verify Android API/lifecycle, TV key mappings and security before implementation. No radio interoperability claim before physical evidence.
4. Continue toward one complete candidate; perform automated protocol/state/UI/emulator gates internally. Only a final concise connection/correctness session may be requested for unavoidable hardware facts. No incremental UI test, router interaction or repeated exploratory pairing.


5. Completed candidate implementation: native Android Bluetooth HID fallback and explicit persisted streamer routing; Google LAN and known-working LG/Samsung adapters remain intact. Nine deterministic HID tests and three Compose setup tests added. Final gate: 79 JVM tests, 10 actual API 35 Compose tests, both APK builds, both lint variants (0 errors, 18 advisory warnings each), 85 matching localized strings and diff/XML checks.
6. Unavoidable remaining boundary: real phone/Xiaomi accessory association and key behavior. A final candidate session is permitted; no repeated LG/Samsung command investigation or UI-only physical test. The final session can include one simultaneous-radio volume tap and one revised LG wake cycle to cover remaining device facts together. Revised LG wake and Xiaomi standby remain unproven and must not be sold as success. Stop at this hardware boundary, report the limits and preserve a clean local commit. No push, tag or distribution release.

7. The candidate’s physical Xiaomi association failed before pairing: Galaxy was absent from TV inquiry results. Returned to autonomous platform/code review instead of another diagnostic chain. Replaced TV-discover-phone dependency with explicit phone-originated Android bonding and callback-driven HID connection. Final connection/correctness validation must use this corrected candidate; no repeat of already-proven LG/Samsung command tests or router investigation.

8. Corrected candidate passes 84 JVM tests, 12 actual emulator tests (11 Compose and one native HID registration), both APK builds and lint variants (0 errors, 18 advisory warnings each). Production emulator Pair Xiaomi reaches actual Android BOND_BONDING; no physical success is inferred. The remaining boundary is one real association/control check, not another discover-phone troubleshooting chain.
