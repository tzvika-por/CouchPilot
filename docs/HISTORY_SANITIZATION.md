# Public-history privacy rewrite

Approved original HEAD: `d579b09795e4f255dda3334e9fa9435fe777020e`.
Rewritten baseline HEAD: `39d1f5d0c7cc1e4f5f5140ac93766950fd6c085d`.

The customer explicitly approved private recovery backup, privacy rewriting, complete re-audit and public source publication on 2026-10-05. This is a privacy rewrite, not a squash: all 35 original commits, parent relationships, messages (apart from private content) and original dates/timezones were preserved. Git hashes changed.

## Recovery and containment

An all-ref Git bundle was created outside the repository and shared Videos folder, with directory 0700/file 0600 permissions. Bundle verification, isolated bare-repository recovery, strict fsck, 35-commit recovery and approved-tree comparison passed. The temporary recovery repository was removed. The unsanitized bundle is private and must never be shared or pushed. Its exact private path is recorded only in local recovery records and the customer publication report.

Obsolete Codex checkpoint refs and original/replace refs were removed; rewrite execution metadata was moved out of the publishable repository. Reflogs expired and garbage collection completed. Strict fsck passed; the original approved commit cannot be read from the publishable object database. Every physically stored object equals the audited reachable set. Only main is intended for publication; never mirror or push private recovery refs.

## Sanitization policy

Exact, reviewed installation data was replaced with synthetic equivalents: household IPv4 addresses, device-specific IPv6 addresses, three device MACs, LG installation identity, Android/Cast host and service identifiers, gateway identifier, device-certificate fingerprint and personal paths/emails. Model names, standard mDNS/SSAP service names, ports, Bluetooth service UUIDs and legitimate synthetic network-policy tests remain. Substitutions retain address family and protocol meaning; generic private-scope tests were not blindly converted to public addresses.

Historical private author/committer identities became `tzvika-por <284562407+tzvika-por@users.noreply.github.com>`, the authenticated owner's actual GitHub noreply identity. Existing `CouchPilot Contributors <contributors@example.invalid>` attribution remains. Local Git identity uses the same authenticated noreply policy. No private email or fabricated third-party identity is used.

## Source-integrity check

Before execution, an expected sanitized manifest was calculated for every approved file and mode. The rewritten baseline matched it exactly. Only `docs/DEVICE_VALIDATION.md` and `docs/GOOGLE_TV_PROTOCOL.md` changed at current HEAD, removing remaining installation identifiers and fixing a duplicated anonymized hostname suffix. All production source, tests, resources, build configuration and screenshot bytes are identical to the physically approved baseline. Later publication commits add/update documentation and delivery rules only.

## Audit evidence

The 35-commit rewritten baseline contained 577 trees and 539 unique reachable blobs. Every reachable object, commit message and identity was scanned; no actual credential/private-key/token or remaining known installation identifier was found. Twenty credential-literal occurrences were reviewed as five intentional synthetic test fixtures. No sensitive credential/signing file was tracked. Five historical image blobs underwent private offline OCR with no private identifier or credential flags. All original parent graphs, timestamps and commit messages were checked against the private pre-rewrite inventory.

Publication and release signing are separate: this approval covers public source, not a signed 1.0 release, tag, public APK upload or new production key. See [publication audit](PUBLICATION_AUDIT.md), [security](SECURITY.md) and [release signing](RELEASE_SIGNING.md).
