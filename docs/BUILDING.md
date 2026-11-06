# Building OlliteRT

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Android 11 Compatibility Attempt](#android-11-compatibility-attempt)
- [Product Flavors](#product-flavors)
- [App Icons](#app-icons)
- [Versioning](#versioning)
- [Signing Release Builds](#signing-release-builds)
- [Lint & Tests](#lint--tests)
- [Model Allowlist](#model-allowlist)
- [R8 & ProGuard](#r8--proguard)

---

## Prerequisites

- **Android Studio** (latest stable) or the Android SDK command-line tools
- **JDK 21** — required by AGP 9.x. Android Studio bundles a compatible JBR. The bytecode target is Java 11.
- **Android SDK** — API level 36 (`compileSdk 36`), target SDK 35
- **Gradle** 9.4.1 (bundled via wrapper)
- **Git** — required at build time to embed the commit hash in `BuildConfig.GIT_HASH` and for auto-versioning (`APP_VERSION_CODE=auto`)
- **LiteRT LM SDK** — bundled via Gradle dependency (see [SDK Compatibility](SDK_COMPATIBILITY.md) for version mapping)
- **Minimum SDK** — Android 11 (API 30), experimental; Android 12+ remains recommended

### `local.properties`

Android Studio creates this file automatically. If you're building from the command line without Android Studio, create `Android/src/local.properties` manually:

```properties
sdk.dir=/path/to/your/Android/Sdk
```

This file is gitignored — every developer sets their own path.

For the internal architecture, package structure, threading model, and request flow, see **[ARCHITECTURE.md](ARCHITECTURE.md)**.

## Quick Start

> [!IMPORTANT]
> These instructions use Linux/macOS shell syntax. On **Windows**, use `gradlew.bat` instead of `./gradlew`, and set environment variables with `set JAVA_HOME=...` (cmd) or `$env:JAVA_HOME = "..."` (PowerShell) instead of `export`.

```bash
cd Android/src

# Debug build (uses debug signing, no R8 minification)
./gradlew :app:assembleStableDebug

# Compile check only (fastest verification)
./gradlew :app:compileStableDebugKotlin
```

If your Java or Android SDK paths differ from the defaults, override them:

```bash
JAVA_HOME="/path/to/jbr" ANDROID_HOME="/path/to/sdk" ./gradlew :app:assembleStableDebug
```

### APK Output

After building, APKs are in:

```
Android/src/app/build/outputs/apk/{flavor}/{buildType}/
└── OlliteRT-{flavor}-{gitHash}-arm64-v8a-{buildType}.apk

# For the Quick Start command (assembleStableDebug):
#   OlliteRT-stable-3b93b24-arm64-v8a-debug.apk
#
# Other examples:
#   OlliteRT-stable-3b93b24-arm64-v8a-release.apk
#   OlliteRT-beta-3b93b24-arm64-v8a-release.apk
#   OlliteRT-dev-3b93b24-arm64-v8a-debug.apk
```

> [!NOTE]
> Only **arm64-v8a** is supported. The LiteRT native library crashes on x86_64 emulators (SIGILL — unsupported CPU instructions), and 32-bit architectures have no native libraries at all. Nearly all Android devices from 2017+ are arm64-v8a.

## Android 11 Compatibility Attempt

This branch lowers the app's minimum to **API 30** without lowering `compileSdk`
or `targetSdk`, overriding dependency manifests, or replacing LiteRT-LM. Android
11 support is **experimental**: installation and Android API compatibility are
separate from successful native inference on a particular phone.

Use the `dev` flavor to keep the experiment separate from a stable/beta
installation. Existing upstream release APKs with `minSdk=31` are not made
compatible by these source changes.

From the repository root, on Windows PowerShell:

```powershell
Set-Location Android\src
.\gradlew.bat :app:assembleDevDebug :app:lintDevDebug :app:testDevDebugUnitTest

# Replace SERIAL with the intended device from adb devices -l.
$apk = Get-ChildItem .\app\build\outputs\apk\dev\debug\*.apk | Select-Object -First 1
adb -s SERIAL install -r $apk.FullName
adb -s SERIAL shell am start -n com.ollitert.llm.server.dev/com.ollitert.llm.server.MainActivity
```

The APK remains **arm64-v8a only**. A debug APK uses the local debug signing key;
it cannot update an existing `dev` installation signed with a different key.

### Automated coverage

JVM regressions cover unknown SoC metadata, generic model-file selection, and
rejection of unmatched NPU-only models. The instrumented workflow runs on API
**30 and 31**, checking the packaged minimum SDK, real platform SoC access, the
OpenCL accessibility probe, and the existing persistence tests.

To run the instrumented suite on a connected emulator:

```powershell
.\gradlew.bat :app:connectedDevDebugAndroidTest -PDISABLE_ABI_SPLITS=true
```

ABI splits are disabled only for the emulator test APK. These tests do **not**
load the LiteRT inference engine: an x86_64 emulator is not evidence that ARM64
CPU or GPU inference works.

### Physical-device acceptance checklist

For the Smartisan R2 / Snapdragon 865 family / 12 GB RAM target, record the ROM,
Android API level, app commit, model revision, accelerator, and context size.
Do not promote Android 11 support from experimental until these checks succeed:

1. Install the branch APK on Android 11, cold-launch it, complete onboarding,
   browse the catalog, and reopen it after backgrounding. On API 30, an unknown
   SoC is intentional: board names are not used to guess NPU compatibility.
2. Download or import a small **CPU-capable text model**, such as Gemma 3 1B.
   Select **CPU** explicitly and start with a small context, such as 1024 tokens.
   With notifications enabled, confirm a background download completes and its
   completion notification appears, then confirm the server notification appears.
   On API 30/31/32, also confirm the download completes with notifications disabled;
   no visible notification is required in that case.
3. Confirm `/health`, `/v1/models`, and both non-streaming and streaming
   `/v1/chat/completions` requests work. Exercise cancellation, stop/restart,
   model reload, and screen-off/background serving.
4. Only after CPU succeeds, test **GPU** separately with the same model and
   settings. Record the effective backend from Logs: an OpenCL probe passing is
   not proof that a GPU kernel works, and a CPU fallback is not a GPU success.
5. Repeat the smoke checks on an Android 12+ ARM64 device. Test boot auto-start
   if enabled, and test vision/audio separately before claiming those work.

Capture relevant errors with `adb -s SERIAL logcat -d -v threadtime` and redact
tokens, client prompts, and other private data before sharing. Native linkage
errors, `SIGILL`, or GPU driver crashes require device-level investigation;
lowering the manifest cannot resolve them.

See [SDK Compatibility](SDK_COMPATIBILITY.md#android-version-compatibility) for
the dependency evidence and its limitations.

## Product Flavors

| Flavor | Application ID | Icon | Purpose |
|:-------|:---------------|:----:|:--------|
| `stable` | `com.ollitert.llm.server` | <img src="../assets/Icons/OlliteRT_Logo_Icon_Stable.png" width="28" /> | Stable release |
| `beta` | `com.ollitert.llm.server.beta` | <img src="../assets/Icons/OlliteRT_Logo_Icon_Beta.png" width="28" /> | Beta testing |
| `dev` | `com.ollitert.llm.server.dev` | <img src="../assets/Icons/OlliteRT_Logo_Icon_Dev.png" width="28" /> | Local development |

All three flavors can be installed side-by-side on the same device.

Build variants follow the pattern `{flavor}{Debug|Release}` — e.g. `stableDebug`, `betaRelease`.

## App Icons

Source icon files (1024x1024 PNG) are in `assets/Icons/`:

| File | Flavor | |
|:-----|:-------|:---:|
| `OlliteRT_Logo_Icon_Stable.png` | `stable` — blue hexagon | <img src="../assets/Icons/OlliteRT_Logo_Icon_Stable.png" width="28" /> |
| `OlliteRT_Logo_Icon_Beta.png` | `beta` — yellow hexagon + BETA badge | <img src="../assets/Icons/OlliteRT_Logo_Icon_Beta.png" width="28" /> |
| `OlliteRT_Logo_Icon_Dev.png` | `dev` — red hexagon + DEV badge | <img src="../assets/Icons/OlliteRT_Logo_Icon_Dev.png" width="28" /> |

Flavor-specific Android resources are in:
- `Android/src/app/src/main/res/` — stable (default)
- `Android/src/app/src/dev/res/` — dev overrides
- `Android/src/app/src/beta/res/` — beta overrides

See the icon generation table in the App Icon section of the project's internal design reference for sizes and pre-padding requirements.

## Versioning

Version is defined in `gradle.properties`:

```properties
APP_VERSION_NAME=0.8.0
APP_VERSION_CODE=auto
```

When `APP_VERSION_CODE=auto`, the version code is derived from `git rev-list --count HEAD` at build time. CI can override both values via Gradle project properties:

```bash
./gradlew :app:assembleStableRelease \
  -PAPP_VERSION_CODE=42 \
  -PAPP_VERSION_NAME=1.0.0
```

The short git commit hash is automatically captured at build time and available as `BuildConfig.GIT_HASH`.

## Signing Release Builds

Release builds **require** a signing keystore — they will fail without one. Debug builds are not affected and always use the debug keystore.

The build validates the signing config at configuration time:
- Missing or blank properties → warning with the specific missing fields
- Keystore file doesn't exist at the given path → warning with the path
- No signing config at all → release build fails at the signing step

> [!IMPORTANT]
> Use **forward slashes** in paths, even on Windows (`C:/Users/you/keystore.jks`). Java's `Properties.load()` treats backslashes as escape characters and silently strips them.

### Option A: Local `keystore.properties` file (recommended for local dev)

Create `Android/src/keystore.properties` (gitignored):

```properties
storeFile=/path/to/your-keystore.jks
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

All four fields are required. The `storeFile` path can be absolute or relative to the `app/` module directory.

### Option B: Environment variables (CI)

The GitHub Actions release workflow uses this method with repository secrets:

```bash
export KEYSTORE_FILE=/path/to/keystore.jks
export STORE_PASSWORD=...
export KEY_ALIAS=...
export KEY_PASSWORD=...
```

### Building

```bash
# Signed release build
./gradlew :app:assembleStableRelease

# Android App Bundle (for Play Store)
./gradlew :app:bundleStableRelease
```

> [!NOTE]
> If you see `WARNING: Release keystore not configured` during a debug build, this is informational only — debug builds are not affected. You only need the keystore for release variants.

## Lint & Tests

```bash
# Lint check (flavor-specific)
./gradlew :app:lintStableDebug

# Unit tests
./gradlew :app:testStableDebugUnitTest

# Both at once
./gradlew :app:compileStableDebugKotlin :app:lintStableDebug :app:testStableDebugUnitTest
```

## Model Allowlist

The model allowlist source of truth is:

```
model_allowlists/v1/model_allowlist.json   ← edit this file
```

A Gradle `syncAllowlist` task (defined in `app/build.gradle.kts`) copies it to `Android/src/app/src/main/assets/model_allowlist.json` during `preBuild`. **Never edit the assets copy directly** — it will be overwritten on the next build.

See [MODEL_ALLOWLIST_SCHEMA.md](MODEL_ALLOWLIST_SCHEMA.md) for the full field reference.

## R8 & ProGuard

Release builds (`*Release` variants) are minified and shrunk with R8. ProGuard rules are in `Android/src/app/proguard-rules.pro` with keep rules for kotlinx.serialization, Kotlin Reflect, Ktor CIO, Protobuf Lite, Hilt/Dagger, LiteRT LM, and Compose.

If you add a new library that uses reflection or serialization, you may need to add ProGuard keep rules to that file — otherwise R8 will strip classes that are only accessed via reflection, causing runtime crashes in release builds only.
