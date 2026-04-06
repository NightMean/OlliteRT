# OlliteRT -- On-Device LLM Server

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

OpenAI-compatible LLM server running on Android, powered by [LiteRT LM](https://ai.google.dev/edge/litert). Supports `.litertlm` and `.task` model formats. Exposes `/v1/chat/completions`, `/v1/responses`, and `/v1/models` on port 11434 (configurable), bound to `0.0.0.0`.

OlliteRT originated as a fork of [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery) and has since diverged into its own project.

## Features

| Feature | Detail |
|---|---|
| **HTTP server** (`0.0.0.0:11434`) | OpenAI-compatible API, Bearer auth, real SSE streaming, tool calls |
| **LiteRT LM runtime** | `.litertlm` and `.task` formats (not GGUF) |
| **Dark theme only** | No Firebase, no analytics |
| **5 screens** | Getting Started, Models, Status, Logs, Settings |
| **JVM tests** | 88 unit tests across 15 files |
| **CI** | Tests + lint + APK artifacts on every push |

## Build

```bash
cd Android/src
./gradlew assembleDebug
```

Requires Android Studio or JDK 21 + Android SDK. For model downloads from HuggingFace, configure `ProjectConfig.kt`.

## Documentation

[Bug Reporting](docs/Bug_Reporting_Guide.md)

## License

Apache 2.0 -- see [LICENSE](LICENSE).
