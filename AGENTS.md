# CouchPilot project rules

## Customer artifact delivery

`~/Videos` is ONLY for files the customer needs to transfer to, install on, or directly use from another device over the network. The rule is purpose-based, not file-type-based. Deliver an APK there when the customer needs to install it on the phone; deliver a ZIP/package, screenshot or exported file there only when the customer needs to retrieve or use it on another device. Report the exact `~/Videos/<filename>` path for such deliveries. Verify copied binary hashes; overwrite an APK only after its build and validation pass.

Do not put internal reports, audit evidence, Git backups, engineering logs, validation or CI reports, internal screenshots, documentation, review notes, temporary artifacts or bookkeeping files in `~/Videos` by default. Keep them in their appropriate project/internal location. Local bookkeeping reports belong in `.local/reports/`, with `.local/` excluded through `.git/info/exclude`.

`~/Videos` is network-shared. Never place unsanitized Git-history bundles, credentials, private identifiers, signing material or other sensitive internal backups there. Store sensitive recovery material outside the repository and shared folder, with restrictive permissions (directory 0700/file 0600).

## Installed-app continuity

Retain `com.myremote.app` applicationId and the established private debug signing identity for customer development updates. Do not create production signing keys, release tags or public APK/AAB releases without explicit approval. Keep physical evidence separate from automated protocol/UI results.
