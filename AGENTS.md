# CouchPilot project rules

## Customer artifact delivery

Every artifact intended for the customer to access, install, inspect, transfer or share must also be copied to `~/Videos/`. Report the exact `~/Videos/<filename>` path. This includes APKs, review ZIPs, shareable reports, requested screenshots and customer-facing exported logs. Verify hashes for copied binary artifacts; overwrite an APK only after its build and validation pass.

`~/Videos` is network-shared. Never place unsanitized Git-history bundles, credentials, private identifiers, signing material or other sensitive internal backups there. Store sensitive recovery material outside the repository and shared folder, with restrictive permissions (directory 0700/file 0600).

## Installed-app continuity

Retain `com.myremote.app` applicationId and the established private debug signing identity for customer development updates. Do not create production signing keys, release tags or public APK/AAB releases without explicit approval. Keep physical evidence separate from automated protocol/UI results.
