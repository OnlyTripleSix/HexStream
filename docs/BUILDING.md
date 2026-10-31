# Installing and building HexStream

## Install a release

Get the APK from [the official HexStream releases](https://github.com/OnlyTripleSix/HexStream/releases). Check its SHA-256 against the accompanying checksum file if you verify downloads. Android may ask you to allow installation from the browser/file manager you use.

The current package is `com.github.andreyasadchy.xtra.prototype`. The inherited namespace and package are retained for compatibility with existing HexStream installations; the launcher name is HexStream. The current published release is **1.0.0-beta.7**, version code **141**. This branch builds the **1.0.0-beta.8.3 test candidate**, version code **145**; its new [channel tools](CHANNEL-TOOLS.md) still need live-account verification. Do not uninstall an existing working installation merely to update it: a matching package/signature update preserves its app data.

The published APK uses the existing HexStream signing certificate. Its SHA-256 certificate fingerprint is:

```text
70f4c9d051037896d593ea841f30957204ef6c45d7e491a852c017552a46d543
```

An APK signed with your own key cannot replace that installation while preserving Android’s signature identity. Use a distinct package for your own development build, or a separate test device/profile. The `qa` build type uses `com.github.andreyasadchy.xtra.qa` and is intended for isolated device checks.

## Requirements

- JDK **21**.
- Android SDK platform **37**, build tools **37.0.0**, platform tools for device tests.
- The included Gradle **9.8.2** wrapper; Android Gradle Plugin **9.4.1**.
- Network access for Gradle dependencies on the first build.

Versions are pinned in [`libs.versions.toml`](../gradle/libs.versions.toml). Use Android Studio’s SDK settings or set `ANDROID_HOME`; a machine-local `local.properties` can also supply `sdk.dir`. Do not commit that file.

## Local checks and debug APK

On Windows, use `gradlew.bat`; on macOS/Linux, use `./gradlew`.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  --max-workers=2 -Dorg.gradle.jvmargs=-Xmx3072m -Pkotlin.daemon.jvmargs=-Xmx3072m
```

The debug APK is under `app/build/outputs/apk/debug/`. It uses your local Android debug signing key, not a bundled maintainer key. Test results are under `app/build/test-results/`; the lint report is under `app/build/reports/`.

## Optimized release APK

```sh
./gradlew :app:assembleRelease --max-workers=2 \
  -Dorg.gradle.jvmargs=-Xmx3072m -Pkotlin.daemon.jvmargs=-Xmx3072m
```

Without signing variables this produces an **unsigned** optimized APK under `app/build/outputs/apk/release/`. R8 shrinking/resource shrinking are enabled. Sign it with your own key before installing.

To sign during the build, set all four environment variables in your local environment:

| Variable | Value |
| --- | --- |
| `HEXSTREAM_KEYSTORE_FILE` | Absolute path to your own keystore. |
| `HEXSTREAM_KEYSTORE_PASSWORD` | Its store password. |
| `HEXSTREAM_KEY_ALIAS` | The signing key alias. |
| `HEXSTREAM_KEY_PASSWORD` | Its key password. |

No signing key or password is included in the source. Do not put secrets into Gradle files or commit a local environment file. Keep the generated release mapping privately when diagnosing a build you distribute.

The release tag contains the corresponding app source. The maintainer’s local signing configuration differs only to supply the existing certificate; development-machine paths and signing material are not part of the published source.

## Isolated Android tests

Use the `qa` build type to keep fixtures away from a real account’s installation:

```sh
./gradlew :app:connectedQaAndroidTest -PhexstreamTestBuildType=qa \
  -Pandroid.testInstrumentationRunnerArguments.notClass=com.github.andreyasadchy.xtra.util.FollowExemptionsDeviceTest \
  --max-workers=2 -Dorg.gradle.jvmargs=-Xmx3072m
```

The fixture that modifies preferences checks the QA package before doing so. Beta.1 ran seven account-free checks; beta.3 adds the eighth fixture described below. The follow-list integration test is excluded because it needs a dedicated signed-in QA account; its missing-account assumption is reported as a failure by some Gradle/runner combinations. Run that test separately with an appropriate test account. Do not run state-changing fixtures against a personal account installation. Remove only QA/test packages when cleaning up; never clear the production app’s data as a routine verification step.

Beta.3 adds an eighth account-free fixture for status-bar callbacks on an unattached player. It must run in the QA package too. With `-PhexstreamTestBuildType=qa`, use `:app:testQaUnitTest` and `:app:lintQa` for the JVM/static checks; the debug unit-test task is not available in that configuration. `:app:compileQaAndroidTestKotlin` compiles the device fixtures without installing or running them.

Beta.4 adds four settings-help checks, including rendering all 13 preference screens. To run just these in the isolated QA package, pass `-Pandroid.testInstrumentationRunnerArguments.class=com.github.andreyasadchy.xtra.ui.settings.SettingsHelpDeviceTest`. The fixture's saved screenshots stay in the QA app's external cache; do not package device caches with a release.

Beta.6 has five isolated chat-overlay checks in `com.github.andreyasadchy.xtra.ui.player.FloatingChatDeviceTest`. They exercise the controller with synthetic chat content: same-view/scroll preservation, transparent full-height rendering without shrinking the host, opacity/contrast changes with dock restoration, stored side selection, touch delivery to player controls beneath chat, and the real chat-input layout with a draft, keyboard inset and signed-out hint. This fixture does not connect a real Twitch chat or spend points.

On the beta verification setup, Gradle’s aggregate device result remained unsuccessful even with a seven-test/zero-failure XML report. The same checks were verified directly:

```sh
./gradlew :app:assembleQa :app:assembleQaAndroidTest -PhexstreamTestBuildType=qa
adb install -r app/build/outputs/apk/qa/app-qa.apk
adb install -r app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk
adb shell am instrument -w -e notClass com.github.andreyasadchy.xtra.util.FollowExemptionsDeviceTest \
  com.github.andreyasadchy.xtra.qa.test/androidx.test.runner.AndroidJUnitRunner
```

Check the runner’s actual result, not only the shell exit status. The beta run returned `OK (7 tests)`. This fallback installs only the QA package and its test runner.

## Release process

Releases are built locally, checked, tagged and uploaded manually. This repository has no GitHub Actions workflow. Before publishing, run the unit/static/build checks, verify the signed package on a device, check neutral fresh-install filters, inspect the public file list and scan for credentials, personal exports and signing material.

Keep the AGPL license, Xtra attribution and third-party notices with redistributed source. See [credits](../CREDITS.md) and the complete [license](../LICENSE).
