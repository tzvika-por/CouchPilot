# Publication hygiene

## Privacy gate: PASS

The customer approved history rewriting and public source publication. The full 35-commit original history was deterministically sanitized with git-filter-repo while preserving chronology, graph and messages. Current production/test source is byte-identical to the approved navigation-fix baseline; two current documentation files received privacy-only substitutions. See [rewrite evidence](HISTORY_SANITIZATION.md).

The rewritten baseline audit covered 35 commits, 577 trees and 539 blobs. No actual authentication secret, private key, signing material, customer installation identifier or private author email remained. Twenty literal matches are five intentional synthetic test fixtures, reviewed in context. Generic test addresses and protocol constants remain intentionally. Five historical screenshots were audited with offline OCR. All physically stored objects belong to sanitized reachable history; old refs/reflogs/objects were removed. The original approved commit is absent from the local object database.

A verified, recovery-tested unsanitized bundle is restricted in a private local directory outside Git and network-shared Videos. Never publish that bundle, internal audit inventories, recovery refs or any original checkout. Push main explicitly; no mirror/all-ref push.

The official Apache-2.0 license, notices and dependency obligations remain unchanged. Final validation after rewriting and remote verification are recorded in the safe customer publication report at `~/Videos/CouchPilot-Publication-Report.txt`. Current source-publication approval does not authorize tags, GitHub Releases, production signing keys or public APK/AAB distribution.
