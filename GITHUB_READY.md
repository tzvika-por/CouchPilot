# GitHub source publication

- Repository: **tzvika-por/CouchPilot**
- Public source URL: **https://github.com/tzvika-por/CouchPilot**
- Visibility: **PUBLIC** (approved)
- Description: **A native Android universal remote for LG webOS TV, Google TV, and Samsung HW-M360.**
- License: **Apache-2.0** (official root LICENSE)
- Publication branch: **main**, sanitized history only
- Topics: android, kotlin, jetpack-compose, remote-control, google-tv, android-tv, webos, lg, samsung, bluetooth, mdns

Privacy rewriting, complete audit, public repository creation and the first main push are explicitly authorized. The history gate passed; see [publication hygiene](docs/PUBLICATION_AUDIT.md) and [rewrite evidence](docs/HISTORY_SANITIZATION.md). The customer verified that the navigation layout regression is resolved and the UI looks good.

Source publication is separate from a stable signed product release. Version 1.0.0 / code 3 is prepared with the permanent B-only signer; the private migration was physically validated. No v1.0.0 tag, GitHub Release or public APK upload is authorized by artifact preparation. The authoritative Drive keystore backup is hash-verified and an independent recoverable credential backup exists outside macOS Keychain. Publication still requires explicit authorization; automatic recovery retains its documented hardware caveats. LG WebSocket pre-assembly memory exposure is retained as an accepted documented limitation within the trusted-LAN/trusted-device scope; it has not been fixed. See [security](docs/SECURITY.md) and [release signing](docs/RELEASE_SIGNING.md).

`~/Videos/` is only for artifacts the customer needs to transfer to or use on another device. Internal reports and audit evidence remain local; signing material and recovery backups must remain private outside the shared folder.
