# Branding and installed-app identity

Visible product name and Gradle root project: **CouchPilot**. Module: `app`.

Namespace and applicationId remain **com.myremote.app**. Namespace/package refactoring adds migration work without product benefit. The unchanged applicationId permits upgrades only when the signing certificate also matches. Changing a visible label, protocol display name, theme name or GitHub repository name does not change installed data identity.

Preference names, Android Keystore aliases, LG AES-GCM associated-data string and service action identity deliberately retain historical `myremote` spelling. Changing them could lose bonds/configuration or make encrypted grants unreadable. Existing certificates are reused; new client certificates display CouchPilot.

The local workspace may remain named MyRemote. Documentation uses `<repo-root>`; recommended public GitHub repository is CouchPilot. Old installation identifiers have been removed from reusable source. Already saved wake MACs and selected Bluetooth hosts remain in private preferences; fresh installations explicitly supply their device configuration.

Prepared release identity: versionName 1.0.0, versionCode 3. The existing installation completed a private signing migration and now uses the permanent release signer. Preparation does not create a public tag or release. LG WebSocket pre-assembly memory exposure remains an accepted documented limitation within the trusted-LAN/trusted-device scope; no strict receive-memory cap is claimed. See [security](SECURITY.md#lg-websocket-message-memory). The permanent release key remains outside Git; public artifacts are signed only with that identity.
