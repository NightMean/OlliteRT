<div align="center">
  <img src="assets/Icons/OlliteRT_Logo_Icon_Stable.png" alt="OlliteRT Icon" width="120" />

  <h1>OlliteRT</h1>

  [**Quick Start**](#quick-start) | [**Screenshots**](#screenshots) | 
  [**Features**](#features) | [**Models**](#available-models) | [**Integrations**](#integrations) | [**API**](#api-endpoints) | [**FAQ**](#faq--troubleshooting) | 
  [**Report a bug**](https://github.com/NightMean/ollitert/issues/new?template=01_bug_report.yml) 

  <p><strong>Turn your Android phone into an OpenAI-compatible API LLM server — fully local, private, and open source</strong></p>

  <div>
    <a href="https://github.com/NightMean/ollitert/releases/latest"><img src="https://img.shields.io/github/v/release/NightMean/ollitert?include_prereleases&style=flat" alt="GitHub Release" /></a>
    <a href="https://github.com/NightMean/ollitert/releases"><img src="https://img.shields.io/github/downloads/NightMean/ollitert/total?style=flat" alt="Downloads" /></a>
    <a href="https://www.buymeacoffee.com/nightmean"><img src="https://img.shields.io/badge/Buy_Me_a_Coffee-FFDD00?logo=buymeacoffee&logoColor=black" alt="Buy Me a Coffee" /></a>
    <br />
    <img src="https://img.shields.io/badge/Android_12+-34A853?logo=android&logoColor=white" alt="Android 12+" />
    <a href="LICENSE"><img src="https://img.shields.io/github/license/NightMean/ollitert?style=flat" alt="License" /></a>
  </div>

</div>
<h1></h1>


## What is OlliteRT?

**Think of it as [Ollama](https://ollama.com) for Android.** Pick a model, tap Start, and your phone becomes an LLM server — runs LLMs on your mobile GPU/CPU via Google's [LiteRT](https://ai.google.dev/edge/litert) runtime and serves them as a standard [OpenAI-compatible HTTP API](https://platform.openai.com/docs/api-reference) on your local network.

**No cloud. No API keys. No subscriptions. Just your phone.**

## Features
- **Multi-model Support** — One-tap download from HuggingFace, import `.litertlm` files or model lists from local storage, or add custom model sources via JSON URL
- **Multimodal & Reasoning** — Vision, audio, thinking, streaming support, and tool calling (experimental) for capable models
- **Benchmark Built-in** — Test and compare models on your device to find the best fit for your hardware
- **Activity Logs** — Detailed request/response logs with search, filtering, and JSON highlighting
- **Always On, Low Power** — Configurable auto-start on boot, sips ~5-10W vs 300W+ for a GPU server — perfect for that old phone in your drawer*
- **Highly Configurable** — Per-model inference settings, GPU/CPU accelerator, idle model unload, bearer token auth, and more
- **Model & Server Monitoring** — Live stats dashboard, [Prometheus metrics](docs/integrations/PROMETHEUS.md) for Grafana, and [Home Assistant REST API](docs/integrations/HOME_ASSISTANT.md) for remote server control
- **Broad Compatibility** — Home Assistant, Open WebUI, OpenClaw, Python, curl — if it talks to OpenAI, it works

> [!NOTE]
> Home Assistant currently requires a custom integration such as [Extended OpenAI Conversation](https://github.com/jekalmin/extended_openai_conversation) or [Local OpenAI LLM](https://github.com/skye-harris/hass_local_openai_llm) and [OpenAI STT](https://github.com/NightMean/OpenAI_STT_HASS) for voice commands — see the [Home Assistant client setup](docs/CLIENT_SETUP.md#home-assistant)

<sub>* I am not responsible for any swollen batteries, crispy phones, or spontaneous pocket warmers. Please don't run your LLM on your phone while it's under your pillow. You've been warned.</sub>


## Screenshots

<p align="center">
  <img src="assets/Screenshots/OlliteRT_Screen_Models.png" width="23%" alt="Models" />
  <img src="assets/Screenshots/OlliteRT_Screen_Models_Inference.png" width="23%" alt="Inference" />  
  <img src="assets/Screenshots/OlliteRT_Screen_Status.png" width="23%" alt="Status" />
  <img src="assets/Screenshots/OlliteRT_Screen_Logs.png" width="23%" alt="Logs" />
</p>

## Quick Start

1. **Download & install** the APK
   <br><a href="https://github.com/NightMean/ollitert/releases/latest"><img src="assets/Github/Download_Get_it_on_Github.png" alt="Get it on GitHub" height="45" /></a>
2. **Download a model** — **Gemma 4 E2B** is recommended for most devices (2.4 GB, runs on 8 GB RAM)
3. **Start the server** — Tap the Start Server button on the downloaded model card
4. **Configure your client** — Use the endpoint shown on the Status screen (e.g. `http://PHONE_IP:8000/v1`) with any OpenAI-compatible client — Open WebUI, OpenClaw, Home Assistant, Python, etc. See **[Client Setup](docs/CLIENT_SETUP.md)** for detailed guides.

> [!IMPORTANT]
> Requires: Android 12+ · **arm64-v8a** device · 6 GB RAM minimum · 8 GB+ recommended for multimodal models (see [model table](#available-models))

## Available Models

| Model | Size | Min RAM | Context | Capabilities |
|:------|-----:|--------:|--------:|:-------------|
| **Gemma 4 E2B** ⭐ | 2.4 GB | 8 GB | 32K | Text · Vision · Audio · Thinking · Tools |
| **Gemma 4 E4B** ⭐ | 3.4 GB | 12 GB | 32K | Text · Vision · Audio · Thinking · Tools |
| **Gemma 3n E2B** | 3.4 GB | 8 GB | 4K | Text · Vision · Audio |
| **Gemma 3n E4B** | 4.6 GB | 12 GB | 4K | Text · Vision · Audio |
| **Gemma 3 1B** | 0.5 GB | 6 GB | 1K | Text |
| **Qwen 2.5 1.5B** | 1.5 GB | 6 GB | 4K | Text |
| **DeepSeek-R1 1.5B** | 1.7 GB | 6 GB | 4K | Text |

⭐ Recommended — E2B for most devices, E4B for high-end

> [!NOTE]
> Tool calling is experimental and may not always be reliable due to model limitations.

See the **[Model Guide](docs/MODELS.md)** for recommendations, capability details, and import instructions.

## Integrations

- **[Prometheus metrics](docs/integrations/PROMETHEUS.md)** — `/metrics` endpoint with 29 metrics for Grafana, Datadog, etc.
- **[Home Assistant REST API](docs/integrations/HOME_ASSISTANT.md)** — monitor server status, control model, update settings remotely

## API Endpoints

<details>
<summary><strong>Available endpoints — click to expand</strong></summary>

| Method | Endpoint | Description |
|:-------|:---------|:------------|
| `POST` | `/v1/chat/completions` | OpenAI Chat Completions API (streaming + non-streaming) |
| `POST` | `/v1/completions` | OpenAI Completions API |
| `POST` | `/v1/responses` | OpenAI Responses API |
| `POST` | `/v1/audio/transcriptions` | Audio transcription |
| `GET`  | `/v1/models` | List available models |
| `GET`  | `/v1/models/{id}` | Get detail for a specific model |
| `GET`  | `/` or `/v1` | Server info (version, status, endpoints) |
| `GET`  | `/health` | Health check (with optional `?metrics=true`) |
| `GET`  | `/metrics` | Prometheus metrics |
| `GET`  | `/ping` | Simple liveness check |

</details>

Full API docs and examples: **[docs/api/API.md](docs/api/API.md)**

## Limitations

<details>
<summary><strong>Known limitations — click to expand</strong></summary>

- **arm64-v8a only** — other architectures (armeabi-v7a, x86, x86_64) are not supported. The LiteRT runtime ships native libraries for x86_64 but they crash on Android emulators due to unsupported CPU instructions. Nearly all Android devices from 2017+ are arm64-v8a.
- **Single model, single request** — one model loaded at a time, requests queue sequentially (LiteRT SDK limitation). On-demand model loading via client requests is planned for a future release.
- **Tool calling is experimental** — Full native tool calling in the LiteRT SDK is currently broken, so OlliteRT uses schema injection (tool schemas injected into the model's context via the SDK) for structured output. A prompt-based fallback is available if schema injection doesn't work with your model. Results may vary — works best with Gemma 4 models.
- **Token counts are estimated** — the LiteRT runtime doesn't expose a tokenizer API, so counts are approximated using character length ÷ 4. Reasonably accurate for English text, less so for code or multilingual content.
- **Imported models are copied to app storage** — when importing a model from your device, the file is copied rather than moved. You can delete the original after import to reclaim space.
- **No GGUF support** — only `.litertlm` models are supported (LiteRT runtime limitation). Models are available from the [LiteRT Community on HuggingFace](https://huggingface.co/litert-community). Advanced users can convert HuggingFace models to `.litertlm` using Google's [`litert-torch`](https://github.com/google-ai-edge/litert-torch) tooling (Linux, 32GB+ RAM required).
- **LiteRT runtime constraints** — OlliteRT is built on Google's [LiteRT](https://ai.google.dev/edge/litert) runtime, optimized for mobile. Features like logprobs, grammar-based output constraints, repetition penalties, and LoRA adapters are not available.

</details>

## FAQ & Troubleshooting

- **[FAQ](docs/FAQ.md)** — Model support, privacy, battery, architecture, tool calling
- **[Troubleshooting](docs/TROUBLESHOOTING.md)** — Connection issues, performance, crashes, auto-start, storage

## Privacy & Security

- **[Privacy Policy](docs/PRIVACY_POLICY.md)** — no data is collected, no telemetry, no analytics
- **[Security Guide](docs/SECURITY.md)** — bearer token auth, network exposure, HTTPS, securing your server

## Contributing

- Found a bug? [Report it here](https://github.com/NightMean/ollitert/issues/new?template=01_bug_report.yml)
- Want to request a feature? [Open an issue](https://github.com/NightMean/ollitert/issues/new)

## Building from Source

- **[Building](docs/BUILDING.md)** — build instructions, signing setup, and HuggingFace OAuth configuration
- **[Architecture](docs/ARCHITECTURE.md)** — package structure, request flow, and dependency list

Product flavors — all installable side-by-side:

| Flavor | Icon | Purpose |
|:-------|:----:|:--------|
| `stable` | <img src="assets/Icons/OlliteRT_Logo_Icon_Stable.png" width="28" /> | Stable release |
| `beta` | <img src="assets/Icons/OlliteRT_Logo_Icon_Beta.png" width="28" /> | Beta testing |
| `dev` | <img src="assets/Icons/OlliteRT_Logo_Icon_Dev.png" width="28" /> | Local development |

## Support the Project

What happens on your phone stays on your phone. If that matters to you, consider supporting OlliteRT.

<p align="center">
  <a href="https://github.com/sponsors/NightMean"><img src="https://img.shields.io/badge/Sponsor-ea4aaa?style=for-the-badge&logo=githubsponsors&logoColor=white" /></a>&nbsp;
  <a href="https://www.buymeacoffee.com/nightmean"><img src="https://img.shields.io/badge/Buy_Me_a_Coffee-FFDD00?style=for-the-badge&logo=buymeacoffee&logoColor=black" /></a>&nbsp;
  <a href="https://ko-fi.com/nightmean"><img src="https://img.shields.io/badge/Ko--fi-FF5E5B?style=for-the-badge&logo=kofi&logoColor=white" /></a>
</p>

## Credits

- **[Google AI Edge Gallery](https://github.com/google-ai-edge/gallery)** — Original project this was built upon
- **[LiteRT](https://github.com/google/litert)** — Google's on-device AI runtime
- **[Ktor](https://ktor.io/)** — Coroutine-based HTTP server framework

## License

Licensed under the [Apache License 2.0](LICENSE).

