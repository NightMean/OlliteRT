# SDK Compatibility

OlliteRT bundles Google's [LiteRT LM](https://github.com/google-ai-edge/LiteRT-LM) runtime for on-device inference. The SDK version determines which `.litertlm` models the app can run — newer models may require a newer SDK (and therefore an app update) to work. See [LiteRT LM releases](https://github.com/google-ai-edge/LiteRT-LM/releases) for the SDK changelog.


## Version Matrix

| OlliteRT | LiteRT LM SDK |
|:---------|:--------------|
| 0.9.0 – 0.9.5 | 0.10.0 |
| 0.9.6 | 0.11.0 |
| 0.9.7 | 0.16.1 |

For source builds, `Android/src/gradle/libs.versions.toml` is authoritative.

## Android Version Compatibility

This branch allows **Android 11 / API 30 experimentally**. Android 12+ remains
recommended. No dependency manifest override or native rebuild is used.

Static inspection of the published
[`com.google.ai.edge.litertlm:litertlm-android:0.16.1` AAR](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/0.16.1/litertlm-android-0.16.1.aar)
found:

| Artifact property | Observed value |
|:------------------|:---------------|
| AAR manifest minimum SDK | API 24 |
| ARM64 `liblitertlm_jni.so` Android build note | API 26, NDK r29 |
| Versioned native imports | `LIBC`, `LIBC_O` (no `LIBC_S` requirement observed) |
| AAR SHA-256 | `e407719c1a29f2685fcb6aa3feea0b9f7155fe316c66dae053c1b5b2f54cda73` |

These observations do **not** establish an API 31 native linkage requirement.
They also do **not** certify Android 11 execution: dynamically loaded vendor
libraries, CPU instruction support, GPU kernels, and model memory requirements
still need testing on real hardware. An accessible OpenCL library is not a
successful inference result.

The original app-level `minSdk=31` gate and unguarded `Build.SOC_MODEL` reads
are separate from the runtime dependency. The compatibility attempt addresses
those app-level constraints and starts validation with CPU inference; GPU and
multimodal support remain device-specific. See the
[Android 11 acceptance checklist](BUILDING.md#android-11-compatibility-attempt).
