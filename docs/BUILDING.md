# Building OlliteRT

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Product Flavors](#product-flavors)
- [App Icons](#app-icons)
- [Versioning](#versioning)
- [Signing Release Builds](#signing-release-builds)
- [HuggingFace OAuth](#huggingface-oauth)
- [Lint & Tests](#lint--tests)
- [R8 & ProGuard](#r8--proguard)

---

## Prerequisites

- **Android Studio** (latest stable) or the Android SDK command-line tools
- **JDK 21** — required to run Gradle (AGP 8.9+ needs JDK 17 minimum). Android Studio bundles a compatible JBR. The bytecode target is Java 11.
- **Android SDK** — API level 36 (`compileSdk 36`), target SDK 35
- **Git** — required at build time to embed the commit hash in `BuildConfig.GIT_HASH` and for auto-versioning (`APP_VERSION_CODE=auto`)
- **Minimum SDK** — Android 12 (API 31)

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
Android/src/app/build/outputs/apk/stable/debug/
├── app-stable-arm64-v8a-debug.apk    ← most modern phones
├── app-stable-x86_64-debug.apk       ← emulators, Chromebooks
└── app-stable-universal-debug.apk    ← works on both (larger)
```

The build produces **per-ABI splits** — each APK contains native libraries for only one architecture, cutting size by ~50%. The universal APK includes both and works everywhere.

> [!NOTE]
> Only **arm64-v8a** and **x86_64** are supported. LiteRT does not ship native libraries for 32-bit architectures (armeabi-v7a, x86). Nearly all Android devices from 2017+ are 64-bit.

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

Release builds require a signing keystore. Two methods are supported:

### Option A: Local `keystore.properties` file

Create `Android/src/keystore.properties` (gitignored):

```properties
storeFile=../path/to/your-keystore.jks
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

### Option B: Environment variables (CI)

```bash
export KEYSTORE_FILE=/path/to/keystore.jks
export STORE_PASSWORD=...
export KEY_ALIAS=...
export KEY_PASSWORD=...
```

> [!WARNING]
> If neither is configured, release builds fall back to the debug keystore — suitable for local testing only, not for distribution.

```bash
# Signed release build
./gradlew :app:assembleStableRelease

# Android App Bundle (for Play Store)
./gradlew :app:bundleStableRelease
```

## HuggingFace OAuth

> [!NOTE]
> This is **not required** for most users. Users can enter their own HuggingFace API token directly in the app's Settings screen to download models — no OAuth setup needed. OAuth is only necessary if you want to enable the "Sign in with HuggingFace" flow for accessing gated models.

To set up OAuth:

1. Create a [HuggingFace Developer Application](https://huggingface.co/docs/hub/oauth#creating-an-oauth-app)
2. In `Android/src/app/src/main/java/com/ollitert/llm/server/common/ProjectConfig.kt`, replace the `clientId` and `redirectUri` placeholders with your HF app values
3. In `Android/src/app/build.gradle.kts`, update `manifestPlaceholders["appAuthRedirectScheme"]` to match your redirect URL

## Lint & Tests

```bash
# Lint check (flavor-specific)
./gradlew :app:lintStableDebug

# Unit tests
./gradlew :app:testStableDebugUnitTest

# Both at once
./gradlew :app:compileStableDebugKotlin :app:lintStableDebug :app:testStableDebugUnitTest
```

## R8 & ProGuard

Release builds (`*Release` variants) are minified and shrunk with R8. ProGuard rules are in `Android/src/app/proguard-rules.pro` with keep rules for kotlinx.serialization, Gson, Protobuf, Hilt, LiteRT LM, AppAuth, and NanoHTTPD.

If you add a new library that uses reflection or serialization, you may need to add ProGuard keep rules to that file — otherwise R8 will strip classes that are only accessed via reflection, causing runtime crashes in release builds only.
