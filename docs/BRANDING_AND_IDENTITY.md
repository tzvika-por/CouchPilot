# Branding and installed-app identity

Visible product name and Gradle root project: **CouchPilot**. Module: `app`.

Namespace and applicationId remain **com.myremote.app**. Namespace/package refactoring adds migration work without product benefit. The unchanged applicationId permits upgrades only when the signing certificate also matches. Changing a visible label, protocol display name, theme name or GitHub repository name does not change installed data identity.

Preference names, Android Keystore aliases, LG AES-GCM associated-data string and service action identity deliberately retain historical `myremote` spelling. Changing them could lose bonds/configuration or make encrypted grants unreadable. Existing certificates are reused; new client certificates display CouchPilot.

The local workspace may remain named MyRemote. Documentation uses `<repo-root>`; recommended public GitHub repository is CouchPilot. Old installation identifiers have been removed from reusable source. Already saved wake MACs and selected Bluetooth hosts remain in private preferences; fresh installations explicitly supply their device configuration.

Version: 0.1.0, versionCode 1 (both retained from the baseline). Target first stable version is 1.0.0. It is not promoted while WebSocket pre-assembly memory containment and release signing/upgrade strategy remain unresolved. No production signing key has been created.
