# Third-party dependencies and protocol references

CouchPilot source is Apache-2.0. Dependency code retains its own license; the project license does not relicense dependencies. Audit date: 2026-10-05. Declared/resolved versions, cached POM licenses and consulted upstream license files were checked. No direct GPL/AGPL or unusual restricted license was found. This is a source/dependency audit, not a claim about unknown patent rights.

| Direct dependency | Version | Purpose / scope | License |
| --- | --- | --- | --- |
| AndroidX Compose BOM |2026.09.00|Version alignment|Apache-2.0|
| Compose material3 |1.4.0|Production UI|Apache-2.0|
| Compose foundation / ui-tooling-preview |1.12.1|Production layout/preview declarations|Apache-2.0|
| Compose ui-tooling / ui-test-manifest |1.12.1|Debug tooling/test Activity|Apache-2.0|
| activity-compose |1.13.0|Activity host|Apache-2.0|
| lifecycle-viewmodel-compose / lifecycle-runtime-compose |2.11.0|ViewModel and flow lifecycle|Apache-2.0|
| kotlinx-coroutines-android |1.10.2|Structured coroutine runtime|Apache-2.0|
| OkHttp |4.12.0|LG WebSocket/TLS|Apache-2.0|
| JUnit |4.13.2|JVM tests only|EPL-1.0|
| MockWebServer / okhttp-tls |4.12.0|Local protocol/TLS tests only|Apache-2.0|
| kotlinx-coroutines-test |1.10.2|Virtual-time tests only|Apache-2.0|
| org.json |20240303|JVM test substitute for Android JSON|Public Domain, per exact POM/license|
| androidx.test.ext:junit |1.3.0|Instrumentation tests only|Apache-2.0|
| Compose ui-test-junit4 |1.12.1|Compose instrumentation only|Apache-2.0|

Important transitives include Kotlin stdlib2.4.10 (JetBrains), Okio3.6.0 (Square), AndroidX Core/Lifecycle/Startup/ProfileInstaller/SavedState (Android Open Source Project), all Apache-2.0 for their library code. OkHttp additionally embeds Public Suffix List data under MPL-2.0; this separate file-level license is compatible with the aggregate Apache project and is not relicensed. Its verbatim NOTICE, full MPL license and corresponding bundled rule data are in [licenses/](licenses/). No rules were changed. Gradle wrapper9.6.0 is Gradle Apache-2.0 infrastructure. AGP9.4.0 and Compose compiler plugin2.4.10 are build tooling, not packaged runtime adapters. Official Apache license is in the root LICENSE. JUnit's EPL applies to the separately licensed test dependency; no JUnit source or runtime code is bundled into production. Retain upstream licenses/notices with any binary redistribution and provide EPL source obligations if distributing that library. Maven dependency resolution is not copying upstream source into this repository.

AndroidX/Compose and Kotlin are active ecosystems. OkHttp4.12 is an older retained major line; support status is not established by age alone. No upgrades were made in this milestone. There are no production protobuf, custom cryptographic, Bluetooth, analytics or crash-reporting libraries; these use Android/JCA/public APIs and independently written protocol subsets. Exact graph is in DEPENDENCIES.txt. Any later dependency change requires renewed license/security review.

## Protocol discoveries (acknowledgements, not bundled source)

- Google Remote Service v2/Polo behavior: [tronikos/androidtvremote2](https://github.com/tronikos/androidtvremote2) (Apache-2.0) and [louis49/androidtv-remote](https://github.com/louis49/androidtv-remote) (MIT). Handwritten bounded Kotlin wire implementation; no copied protobuf implementation or client source.
- LG SSAP registration/input behavior: [Connect SDK Android Core](https://github.com/ConnectSDK/Connect-SDK-Android-Core) (Apache-2.0), [aiowebostv](https://github.com/home-assistant-libs/aiowebostv) (Apache-2.0), [LGWebOSRemote](https://github.com/klattimer/LGWebOSRemote) (MIT). No copied signed registration manifest, signatures, manufacturer identity or CLI code.
- Samsung behavior: public vendor app/protocol observations described in SAMSUNG_M360_PROTOCOL.md; no decompiled application source/assets or copied reverse-engineering implementation is distributed. Kotlin framing/session code is independent.
- HID usages and platform mappings: USB HID usage specification, Android public API documentation, AOSP key maps/TV pairing behavior. Linux/AOSP source was consulted for behavior; no GPL kernel code or descriptor implementation copied. Numeric protocol facts are independently encoded.

Source review found no copied upstream copyright blocks or GPL/AGPL source. This does not claim ownership of discovered protocols, vendor trademarks or specifications. Device names identify interoperability targets; CouchPilot is not affiliated with LG, Google, Xiaomi, Samsung or yes+.

## Advisory check

OSV querybatch on 2026-10-05 returned no advisory IDs for 111 resolved debug-runtime Maven coordinates, including platform/constraint metadata. Test-only licenses were reviewed separately. This is a point-in-time database result, not a guarantee against unpublished vulnerabilities or device firmware issues. [Machine-readable result](DEPENDENCY_ADVISORIES.json).
