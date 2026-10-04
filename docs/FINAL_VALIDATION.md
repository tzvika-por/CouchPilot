# CouchPilot final validation

Date: 2026-10-05. Engineering baseline: e9e6ae21712cced6bb451b49b9648f2a457fb3b0. Candidate source was validated before its coherent local commits. No physical device command, push, tag, remote creation or production signing occurred.

## Build and automated gates

Environment: macOS, JDK 25.0.4; Gradle wrapper 9.6.0; AGP 9.4.0; Compose compiler plugin 2.4.10. Compile SDK 37, target 36, minimum 26; Java language target 17. API 35 isolated emulator. SDK/cache paths are local and intentionally not published.

| Command/check | Exact completed result |
| --- | --- |
| `./gradlew clean assembleDebug assembleRelease test lint :app:lintRelease assembleAndroidTest --no-daemon --max-workers=2` | BUILD SUCCESSFUL, 2m 36s; 136 tasks, 135 executed, 1 up-to-date |
| Same build/test/lint/test-APK gates after strengthening accessibility test configuration, without clean | BUILD SUCCESSFUL, 32s; 135 tasks, 8 executed, 127 up-to-date |
| `test` | 177 JVM tests in 25 suites; 0 failures, 0 errors, 0 skips; all enabled unit-test variants (currently debug) |
| `lint` | Debug: 0 fatal/errors, 21 warnings |
| `:app:lintRelease` | Release: 0 fatal/errors, 21 warnings |
| `assembleDebug` | Signed development APK built |
| `assembleRelease` | Unsigned, non-debuggable APK built |
| `assembleAndroidTest` | Compose/native instrumentation APK compiled |
| `git diff --check`, `sh -n tools/check-font-scale.sh` | Passed |
| Workflow YAML / action SHA checks | Both workflows parsed; read-only contents permission and all four verified 40-character action pins checked |

Per lint variant: 13 UseKtx, 6 NewerVersionAvailable, 1 OldTargetApi, 1 AndroidGradlePluginVersion. Checked preference commits are deliberate. No Detekt, Ktlint, formatter or dependency-analysis task is configured. The native-library strip advisory for libandroidx.graphics.path.so does not fail packaging.

## Emulator and screenshot results

Commands use `adb -s emulator-5554 shell am instrument -w ... com.myremote.app.test/androidx.test.runner.AndroidJUnitRunner` after installing debug and test APKs. Notification permission was revoked before the full functional run; Bluetooth was enabled. No physical peer was used.

- Final complete instrumentation suite: **59 tests passed**, 144.155 seconds: **27 functional tests** in nine classes plus **32 screenshot scenarios**. Small display: 320x640 at 160dpi. Includes real Android Keystore, native HID registration, foreground-service/background lifecycle and Compose setup/routing/accessibility.
- Earlier additional modern-viewport screenshot run: **32 unique scenarios passed**, 81.204 seconds. Actual captured portrait images: 1080x1920 at 420dpi; production UI code was unchanged by the later versionCode retention. The final complete suite repeats these scenarios on the small display. Includes English/Hebrew, default/connected/disconnected/error, contextual sources, navigation/keypad, authorization/pairing/permissions and injected 100/150/200% main-screen fonts.
- Actual landscape variant: **1 test passed**, 4.228 seconds.
- Three public-image captures at 360dpi: **3 tests passed**, 9.748 seconds; actual images 1080x1920.
- `tools/check-font-scale.sh`: **4 contextual tests passed**, 8.911 seconds, plus **6 capture tests passed**, 12.901 seconds, at Android's actual 200% font setting on the small display. It rejects physical devices and restores the previous font setting on exit. Native dialog windows reset injected Compose density, so these supplemental system-font checks are necessary.

Final instrumentation evidence therefore covers **27 unique functional tests plus 4 supplemental executions**, and **32 unique screenshot scenarios plus 10 supplemental executions** (42 screenshot-test executions). The earlier additional modern run and other development repeats are not added to those totals. Screenshots are synthetic implemented-state fixtures, not physical connection proof or golden pixel comparisons. No failure/error/ignored-test markers occurred in these final runs. No human TalkBack audit is claimed.

## Static, dependency and publication evidence

- 109 English/Hebrew keys match; all 10 source XML files parse; 45 relative documentation/image links resolve.
- 154 candidate source/configuration/documentation files scanned: no private-key/token, personal path/name, installation identifier or sensitive-file hits. Synthetic LAN-scope tests and reserved documentation IPs are not actual network configuration.
- Existing reachable history: 450 blobs scanned; no authentication-secret candidate found. A post-commit scan at ef9e743 also inspected 532 reachable blobs: no actual authentication secret found; one synthetic security-test migration literal was classified. Old household identifiers and personal author metadata remain; **public push is blocked pending explicit history-redaction approval**. See PUBLICATION_AUDIT.md.
- 18 direct dependency declarations have license metadata reviewed; no incompatible GPL/AGPL/copied protocol source found. Official Apache LICENSE matches the upstream text byte-for-byte. OkHttp's separate MPL-2.0 public-suffix data retains notices/license/source data; five license assets are present in both APKs.
- Fresh OSV querybatch: 111 resolved debug-runtime Maven coordinates (including platform/constraint metadata), no advisory IDs. This is not a guarantee against unpublished vulnerabilities. See DEPENDENCY_ADVISORIES.json and THIRD_PARTY_NOTICES.md.
- Release merged manifest: launcher Activity exported; connection service and startup provider private; exported AndroidX profile receiver requires DUMP. Debug test/tooling components absent; release is not debuggable. Existing debug signing certificate is unchanged, preserving upgrade continuity with applicationId com.myremote.app.

## Built artifacts (not distributed)

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| app-debug.apk | 12740863 | 4e2e04ed6b86ea4af975a93d7e2377b09ba7b249038efc56c553f343b3eaa669 |
| app-release-unsigned.apk | 9021413 | 3ff32f2dc765cab17c2d35b52c27d345fac02bafba230acc29b50dc2016935da |

No new shared-folder APK delivery occurred. Version remains 0.1.0 / code 1; WebSocket pre-assembly availability containment and production signing/upgrade strategy remain stable-release blockers. Hosted GitHub CI has not run because no repository/remote was created. Accepted device evidence remains in DEVICE_VALIDATION.md; no new pairing/control/wake success is inferred.

## Iteration issues resolved

Initial removal of installation constants exposed seven obsolete LG auto-MAC fixture assertions; tests now use explicit persisted configuration. One unused BoxWithConstraints lint error was fixed by using maxWidth. One lint FIR crash followed an edit during analysis; stable clean validation passed. One recursive Kotlin property inference error was fixed with an explicit scheduler property type. Dialog font injection was found insufficient during visual inspection; the actual-system-font helper supplies the missing coverage. These intermediate checks were not reported as passing.

Staged whitespace checks also caught extra final blank lines in the new scheduler test and generated dependency report; both were removed before commit. No executable behavior changed.
