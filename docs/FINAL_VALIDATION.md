# CouchPilot final validation

Latest validation: [post-privacy-rewrite acceptance](#post-privacy-rewrite-acceptance--2026-10-05). Earlier milestone sections are historical evidence; their former publication block is superseded by the approved, audited rewrite.

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

## Navigation feedback regression — 2026-10-05

Baseline: ceda7e364f16e868f40bea2be590cc845ab93ae3. Customer physical testing reported successful Xiaomi commands with a visible screen shift on each press. No new physical confirmation of the patched UI is claimed.

### Root cause and patch

`RemoteSession` observes the device scheduler's busy set; each command changes `RemoteState.busyDevices`. `RemoteScreen` used that set to insert/remove a “Sending command” Text row in the scrolling Column above the sources and navigation. This changed content height and the coordinates of every lower control. The new regression test, run before the fix, failed at the first Up command: navigation and neighboring controls moved down 34px on the 320x640/160dpi emulator. Press-down alone did not move them.

Pressed ripples, borders, shadows and geometry were audited. No pressed-state size/padding animation, conditional key icon/label, animateContentSize, AnimatedVisibility, navigation scroll-to or successful-command focus change exists in this screen. Successful key actions increment actionCount without changing device labels or input selection. Static selected-source borders and shadows are drawing effects; the reproduced reflow began with busy-state insertion.

Command progress now uses a thin Box overlay, outside the scrolling content; error feedback is likewise overlaid and has an accessible 48dp dismiss action. The Snackbar remains an overlay. Transient busy/error state does not change remote content size, scroll range or neighboring positions. Existing button ripples, labels, roles and touch targets are retained. Protocols, connection lifecycle, command scheduling, applicationId, signing and version are unchanged.

### Tests and validation

- New `NavigationLayoutStabilityTest`: three parameter cases (100/150/200% Compose font scale, RTL). All ten navigation/media controls exercise press-down, busy execution, completion and rapid taps. Each case checks 70 delivered actions, exact anchor positions/sizes, unchanged scroll offset, >=48dp targets, visible progress, failure feedback, dismissal and recovery. Total 210 actions. Focused run: **3 passed**, 174.517 seconds.
- Screenshot harness: two new busy scenarios (top and scrolled navigation). The error-bottom scenario now scrolls to the keypad while retaining visible overlay feedback. Busy/error screenshots were inspected visually; these are production composables with synthetic state, not physical-device proof or golden pixel comparisons.
- Clean Gradle gate: `./gradlew clean assembleDebug assembleRelease test lint :app:lintRelease assembleAndroidTest --no-daemon --max-workers=2`. **BUILD SUCCESSFUL**, 3m13s, 136 tasks (135 executed, one up-to-date).
- JVM: **177 tests**, 25 suites; zero failures, errors or skipped tests. Includes existing scheduling/order/cancellation/rapid-command/concurrency regressions.
- Debug and unsigned release APKs built; Android/Compose test APK compiled.
- Debug and release lint: **zero fatal/errors; 21 warnings each** (13 UseKtx, six NewerVersionAvailable, one OldTargetApi, one AndroidGradlePluginVersion). Same advisory inventory as the baseline. No formatting/Detekt/Ktlint task is configured.
- Final complete emulator run: **64 passed**, 274.349 seconds; **30 functional tests plus 34 screenshot scenarios**, zero failed/ignored tests. Actual display 320x640/160dpi, API35; no physical peer used.
- Native-system-font helper: **4 contextual tests passed**, 9.465 seconds; **6 screenshot tests passed**, 13.024 seconds at actual Android font scale2.0. These are supplemental executions of existing cases. The helper restored system font scale1.0.
- Initial full run: 64 executed, 63 passed and one failed because the lifecycle suite's documented notification-denied precondition had not been reset. No production code was changed for this. After emulator-only `pm revoke com.myremote.app android.permission.POST_NOTIFICATIONS`, the entire 64-test suite passed as recorded above. The intentional pre-fix layout failure is separate reproduction evidence, not a passing validation run.
- Post-validation delivery build: `./gradlew assembleDebug --no-daemon --max-workers=2`, **BUILD SUCCESSFUL**, 11s; 38 tasks up-to-date.
- Git whitespace check passed. Debug APK signature verified; signer SHA-256 remains `dd57e000b36d89ac8c47f77f370dbc9b2e3494b1f40c0a0077647ee2d755ddd6`. Package remains `com.myremote.app`, version0.1.0/code1. No human TalkBack audit is claimed.

### Delivery and publication boundary

The validated debug APK is `app/build/outputs/apk/debug/app-debug.apk`, 12,740,863 bytes; SHA-256 `8299eb5c9174423631fd148baaca5cce60e3aeb620a14f8bc096aad70b4f2d28`. It was atomically copied and hash-verified at `<shared-folder>/CouchPilot.apk`; existing MyRemote.apk/app-debug.apk delivery aliases were also updated to identical bytes. This supports an in-place upgrade with the existing signing identity; no uninstall or new pairing is required by the patch.

No Git-history rewrite, remote creation, push, tag or publication occurred. Existing publication/signing limitations remain unchanged. No repeat physical-device troubleshooting sequence was requested.

## Post-privacy-rewrite acceptance — 2026-10-05

Original physically approved HEAD: `d579b09795e4f255dda3334e9fa9435fe777020e`. Final rewritten baseline: `39d1f5d0c7cc1e4f5f5140ac93766950fd6c085d`. The customer confirmed navigation no longer shifts the layout and the UI looks good. No further physical test was requested.

- Final clean gate: `./gradlew clean assembleDebug assembleRelease test lint :app:lintRelease assembleAndroidTest --no-daemon --max-workers=2`: **BUILD SUCCESSFUL**, 2m 9s; 136 tasks, 135 executed and one up-to-date. Signed debug, unsigned release and instrumentation APKs built.
- JVM: **177 tests in 25 suites**, zero failures, errors or skipped tests.
- Full emulator suite: **64 passed**, 275.040 seconds; **30 functional tests and 34 screenshot scenarios**, zero failures/skips. Includes three navigation-stability cases at 100%, 150% and 200% Compose font scale, 210 delivered actions with unchanged bounds and scroll offset.
- Native-system-font helper: **4 contextual tests passed**, 9.225 seconds, and **6 screenshot tests passed**, 14.753 seconds, at actual Android font scale 2.0. These are supplemental executions, not additional unique scenarios. Font scale was restored.
- Debug and release lint: **zero errors/fatal findings and 21 warnings each**: 13 UseKtx, six NewerVersionAvailable, one OldTargetApi and one AndroidGradlePluginVersion. No formatter, Detekt or Ktlint task is configured.
- Static checks: Git whitespace, shell syntax, both workflow YAML files, all seven pinned action uses against upstream commits, 109 English/Hebrew resource keys, ten XML files and relative documentation/image links passed.
- Dependency/license checks: 18 direct dependency records reviewed, no incompatible copied protocol code/GPL/AGPL found; Apache LICENSE matches the official text. Existing same-day OSV evidence covers 111 resolved runtime coordinates with zero advisory IDs; dependencies did not change. Separate MPL public-suffix notices remain retained.
- History audit: all 35 original commits preserved, parent graphs/dates verified; 539 unique blobs and 577 trees scanned. Zero actual credential/private-key/token, known installation-identifier or private-metadata findings. Five historical image blobs also passed offline OCR review. Reviewed literal matches are synthetic test fixtures. Original refs/reflogs/unreachable objects were removed, and the original approved commit is unavailable in the publishable object database.
- Approved-tree integrity: production source, tests, resources, build configuration and screenshots are byte-identical. Only two baseline documentation files received privacy substitutions; subsequent changes are publication documentation/rules. See [history sanitization](HISTORY_SANITIZATION.md).

ApplicationId/namespace remain `com.myremote.app`, version 0.1.0/code 1. The existing debug signer is retained. Customer delivery is `~/Videos/CouchPilot.apk`; SHA-256 `8299eb5c9174423631fd148baaca5cce60e3aeb620a14f8bc096aad70b4f2d28`. It remains byte-identical to the physically approved APK. No new key, release tag, GitHub Release or public APK upload is authorized.

The external all-ref recovery bundle is verified, privately permissioned and never delivered to Videos or GitHub. The final publication report records post-documentation-commit history counts, final local/remote HEAD and initial hosted CI status at `~/Videos/CouchPilot-Publication-Report.txt`.
