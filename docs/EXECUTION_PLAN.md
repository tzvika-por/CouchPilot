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
