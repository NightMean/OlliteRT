# gallery fork

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build](https://github.com/Zyach/gallery/actions/workflows/build_android.yaml/badge.svg)](https://github.com/Zyach/gallery/actions/workflows/build_android.yaml)
[![JVM Tests](https://github.com/Zyach/gallery/actions/workflows/jvm-tests.yml/badge.svg)](https://github.com/Zyach/gallery/actions/workflows/jvm-tests.yml)

Fork de [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery) que añade un bridge HTTP local para inferencia on-device mediante una API compatible con OpenAI.

## Qué añade este fork

| Feature | Detalle |
|---|---|
| **HTTP bridge** (`127.0.0.1:9006`) | API compatible OpenAI: `/v1/chat/completions`, `/v1/responses`, `/v1/models`. Auth Bearer, SSE streaming real token-by-token, tool calls. |
| **89 tests JVM** | Upstream tiene cero. 16 archivos cubriendo toda la capa del bridge. |
| **CI completo** | Tests + lint + APK artifacts en cada push. Upstream solo hace release build. |
| **DataStore async** | Eliminado `runBlocking` de `DataStoreRepository`. |
| **Error handling** | Errores visibles en single-turn; reset de sesión con max retries. |

## Pendiente de upstream

- Agent Chat con Skills importables (`customtasks/agentchat/`)
- Sistema de promo banners
- Thinking tokens via SDK nativo (`message.channels["thought"]`)

## Build

```bash
cd Android/src
./gradlew assembleDebug
```

Requiere Android Studio o JDK 21 + Android SDK. Para descarga de modelos desde HuggingFace, configurar `ProjectConfig.kt` — ver [DEVELOPMENT.md](DEVELOPMENT.md).

## Documentación

[Estado](docs/STATE.md) · [Roadmap](docs/ROADMAP.md) · [Divergencias](docs/FORK_DIVERGENCES.md)

## Licencia

Apache 2.0 — ver [LICENSE](LICENSE).
