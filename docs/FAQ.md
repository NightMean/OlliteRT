# Frequently Asked Questions

- [What are the minimum requirements?](#what-are-the-minimum-requirements)
- [Does this replace dedicated AI hardware?](#does-this-replace-a-coral-tpu--jetson--dedicated-ai-hardware)
- [Which model should I pick?](#which-model-should-i-pick)
- [Can I use GGUF models?](#can-i-use-gguf-models)
- [How do I connect my client?](#how-do-i-connect-my-client)
- [Is my data private / does it work offline?](#is-my-data-private--does-it-work-offline)
- [What's the difference between GPU and CPU mode?](#whats-the-difference-between-gpu-and-cpu-mode)
- [Can I run multiple models at once?](#can-i-run-multiple-models-at-once)
- [Can multiple clients connect simultaneously?](#can-multiple-clients-connect-simultaneously)
- [Can I access it from outside my home network?](#can-i-access-it-from-outside-my-home-network)
- [How do I secure the API?](#how-do-i-secure-the-api)
- [Will it damage my phone / how much battery does it use?](#will-it-damage-my-phone--how-much-battery-does-it-use)
- [What do the benchmark numbers mean?](#what-do-the-benchmark-numbers-mean)
- [Why does importing a model use double the storage?](#why-does-importing-a-model-use-double-the-storage)
- [What is keep-alive / idle unload?](#what-is-keep-alive--idle-unload)
- [Can I auto-start the server on boot?](#can-i-auto-start-the-server-on-boot)
- [Why does OlliteRT show a persistent notification?](#why-does-ollitert-show-a-persistent-notification)
- [What is thinking / reasoning mode?](#what-is-thinking--reasoning-mode)
- [Can I change inference settings per model?](#can-i-change-inference-settings-per-model)
- [Can I set a custom system prompt?](#can-i-set-a-custom-system-prompt)
- [What is prompt compaction?](#what-is-prompt-compaction)
- [How does tool calling work?](#how-does-tool-calling-work)
- [Can I use OlliteRT with Home Assistant?](#can-i-use-ollitert-with-home-assistant)
- [How do I monitor OlliteRT from Home Assistant?](#how-do-i-monitor-ollitert-from-home-assistant)
- [How do I add or create a custom model source?](#how-do-i-add-or-create-a-custom-model-source)
- [How do I update a downloaded model?](#how-do-i-update-a-downloaded-model)

---

### What are the minimum requirements?

**Android 12+** (API 31) on an **arm64-v8a** device with at least **6 GB RAM** (8 GB+ recommended for multimodal models). Nearly all Android phones from 2017+ meet these requirements.

Android 12 is the minimum because Google's [LiteRT](https://ai.google.dev/edge/litert) runtime requires GPU acceleration APIs not available on older versions. **Other architectures (armeabi-v7a, x86, x86_64) are not supported**  — the LiteRT native library crashes on x86_64 emulators due to unsupported CPU instructions, and 32-bit architectures have no native libraries at all.

See the [Model Guide](MODELS.md) for per-model RAM requirements.

---

### Does this replace a Coral TPU / Jetson / dedicated AI hardware?

No, and it's not trying to. OlliteRT was built around a simple idea — **the best computer is the one you already have.** Rather than buying dedicated AI hardware, repurpose that old phone in a drawer, a device with a cracked screen, or a cheap second-hand phone.

Real-time tasks like continuous camera feed analysis (e.g. Home Assistant Frigate) are better suited for dedicated AI hardware. OlliteRT is designed for conversational AI, text generation, and on-demand image analysis where a few seconds of latency is acceptable.

---

### Which model should I pick?

See the [Model Guide](MODELS.md) for detailed recommendations.

---

### Can I use GGUF models?

No. OlliteRT uses Google's [LiteRT](https://ai.google.dev/edge/litert) runtime, which only supports `.litertlm` model files. GGUF (used by llama.cpp/Ollama) is a completely different format. See the [Model Guide](MODELS.md) for supported models and how to import `.litertlm` files.

---

### How do I connect my client?

Point any OpenAI-compatible client at the endpoint shown on the Status screen (e.g. `http://PHONE_IP:8000/v1`). Works with Home Assistant, Open WebUI, OpenClaw, Python, curl, and anything else that supports custom OpenAI base URLs.

See the [Client Setup Guide](CLIENT_SETUP.md) for step-by-step instructions for each client.

---

### Is my data private / does it work offline?

Yes to both. All processing runs on-device — no data leaves your phone. No telemetry, no analytics, no cloud. After downloading a model, the app works fully offline.

---

### What's the difference between GPU and CPU mode?

**GPU** (default) uses your phone's graphics processor for model processing — significantly faster. **CPU** uses the main processor and is noticeably slower. Stick with GPU unless you're experiencing issues, in which case try CPU as a fallback. See [Troubleshooting → Performance](TROUBLESHOOTING.md#performance) for speed tips.

---

### Can I run multiple models at once?

No. OlliteRT loads one model at a time. Switching models requires unloading the current one and loading the new one.

---

### Can multiple clients connect simultaneously?

Yes, but requests are processed one at a time. Additional requests queue until the current one finishes.

---

### Can I access it from outside my home network?

OlliteRT only serves on your local network. For remote access, use a VPN (e.g. [WireGuard](https://www.wireguard.com/), [Tailscale](https://tailscale.com/)).

> [!WARNING]
> **Do not** expose OlliteRT to the internet via port forwarding — there's no HTTPS and the authentication is basic bearer token. See the [Security Guide](SECURITY.md) for details.

---

### How do I secure the API?

Enable bearer token authentication in Settings → Server Configuration. When enabled, OlliteRT generates a random token that clients must include in the `Authorization: Bearer <token>` header. Requests without a valid token are rejected with `401 Unauthorized`.

See the [Security Guide](SECURITY.md) for more details.

---

### Will it damage my phone / how much battery does it use?

Running an LLM puts your GPU under load, comparable to playing a demanding game — roughly 5-10W (depending on phone model) while the model is generating responses, near-zero when idle with keep-alive unload enabled.

> [!CAUTION]
> For continuous use: keep the phone plugged in, remove the case for better heat dissipation, and place it on a cool, hard surface. Check the phone periodically for heat or battery swelling — especially older devices. Don't run OlliteRT on a phone that already has battery issues.

If the server stops unexpectedly during heavy use, see [Troubleshooting → Server crashes](TROUBLESHOOTING.md#the-server-crashes--stops-unexpectedly).

---

### What do the benchmark numbers mean?

| Metric | What It Measures | Higher or Lower? |
|:-------|:-----------------|:-----------------|
| **Prefill Speed** (tokens/sec) | How fast the model processes your input prompt | Higher is better |
| **Decode Speed** (tokens/sec) | How fast the model generates output text | Higher is better |
| **Time to First Token (TTFB)** | Delay before the first word appears — includes prompt processing | Lower is better |
| **Init Time** | How long the model takes to load into memory | Lower is better |

**Decode speed** is the most noticeable metric in daily use — it determines how fast text appears on screen.

**Prefill speed** matters more for long prompts (e.g. Home Assistant system prompts with many tool definitions). Higher prefill speed means lower TTFB.

---

### Why does importing a model use double the storage?

Android's scoped storage doesn't allow LiteRT to read files directly from outside the app storage, so imported models are copied to the app's private directory. Models downloaded from HuggingFace go directly to app storage — no duplication.

> [!TIP]
> The original imported file can be safely deleted after import to reclaim storage.

OlliteRT also reserves 3 GB of free space as a system buffer — downloads are blocked if there isn't enough room for the model plus this reserve. This prevents the device from running out of space for OS operations. A "Download Anyway" option is available if you want to bypass the check.

If import fails, see [Troubleshooting → Model import fails](TROUBLESHOOTING.md#model-import-fails).

---

### What is keep-alive / idle unload?

When enabled in Settings, OlliteRT automatically unloads the model from memory after a configurable idle timeout (e.g. 5 minutes with no requests). This frees RAM for other apps. The next incoming request automatically reloads the model — this adds a one-time delay (cold start) but keeps the phone usable between requests.

If the model unloads unexpectedly, see [Troubleshooting → The model keeps unloading](TROUBLESHOOTING.md#the-model-keeps-unloading).

---

### Can I auto-start the server on boot?

Yes. In Settings → Auto-Launch & Behavior:

1. Select a **Default Model** — the model to load automatically upon app startup
2. Enable **Start on Boot** — the server starts when the device boots. Useful when phone restarts or loses power.

> [!IMPORTANT]
> Disable battery optimization for OlliteRT (Android Settings → Apps → OlliteRT → Battery → Unrestricted). Without this, Android may kill the server in the background or prevent it from starting on boot. See [Troubleshooting](TROUBLESHOOTING.md#auto-start-on-boot-doesnt-work) for device-specific instructions.

---

### Why does OlliteRT show a persistent notification?

OlliteRT uses a foreground service notification to keep the HTTP server running in the background — without it, Android could kill the server after some time. The notification also provides quick actions to **Stop Server** and **Copy URL** (the server endpoint for your clients).

---

### What is thinking / reasoning mode?

Supported models (Gemma 4 E2B/E4B) can show their step-by-step reasoning process before giving a final answer. Enable it per model in the inference settings (tap the gear icon on a model card). When enabled, responses include a `<think>...</think>` block with the model's reasoning, followed by the actual answer.

If thinking mode isn't producing reasoning output, see [Troubleshooting → Thinking Mode](TROUBLESHOOTING.md#thinking-mode).

---

### Can I change inference settings per model?

Yes. Tap the gear icon on any model card to open inference settings. You can configure temperature, top-K, top-P, and max tokens individually for each model. Settings are saved per model and persist across server restarts.

> [!TIP]
> Some API clients send their own sampler values (e.g. temperature capped at 1.0) that may override your per-model settings. Enable **Ignore Client Sampler Parameters** in Settings → Advanced to discard client-sent values and always use your own inference settings instead.

> [!TIP]
> For imported models, you can also edit the default capabilities (vision, audio, thinking) and inference parameters after import — tap the edit icon on the imported model's card. Enabling a capability only tells OlliteRT to advertise and use it — the model itself must actually support it, otherwise requests using that capability will fail or produce garbage output.

---

### Can I set a custom system prompt?

Yes. Enable **Custom System Prompt** in Settings → Advanced, then set a per-model system prompt in the inference settings (tap the gear icon on a model card → expand "Custom System Prompt"). The system prompt is prepended to every conversation as an instruction to the model.

---

### What is prompt compaction?

When a conversation exceeds the model's context window, OlliteRT can automatically reduce the prompt to fit. Three strategies are available in Settings → Advanced, all **disabled by default**:

- **Truncate History** — drop older messages, keeping system prompts and the most recent messages
- **Compact Tool Schemas** — reduce tool definitions to names and descriptions only. If still too large, tool schemas are removed entirely
- **Trim Prompt** — last resort, hard-cuts the prompt to fit the context window

When enabled, compaction happens transparently — the client doesn't need to handle it.

See also [Troubleshooting → Context window exceeded](TROUBLESHOOTING.md#long-conversations-fail--context-window-exceeded).

---

### How does tool calling work?

Tool definitions are injected into the system prompt and the model's output is parsed for tool call patterns.

> [!NOTE]
> Tool calling currently works best with **Gemma 4** models. Smaller models may not follow tool calling instructions reliably.

See [Troubleshooting → Tool Calling](TROUBLESHOOTING.md#tool-calling-experimental) if tool calls aren't working as expected.

---

### Can I use OlliteRT with Home Assistant?

Yes. You need a custom integration — either [Extended OpenAI Conversation](https://github.com/jekalmin/extended_openai_conversation) or [Local OpenAI LLM](https://github.com/skye-harris/hass_local_openai_llm) — installed via HACS. 

See the [Client Setup Guide](CLIENT_SETUP.md#home-assistant) for the full setup walkthrough.

---

### How do I monitor OlliteRT from Home Assistant?

Use the built-in REST API to monitor and control the server. See [HOME_ASSISTANT.md](integrations/HOME_ASSISTANT.md) for the full guide.

---

### How do I add or create a custom model source?

**To add an existing model source:** Go to **Settings → Model Sources**, tap **+**, and enter the URL of a JSON model list (e.g. a raw GitHub link). The source is refreshed automatically (~24 hours) to check for new models. You can enable, disable, or remove it at any time.

**To create your own model source:** Host a JSON file following the [Model Allowlist Schema](MODEL_ALLOWLIST_SCHEMA.md) — for example, in a GitHub repository. The JSON defines model names, download URLs, capabilities, and metadata. Once hosted, add the raw URL as a model source in OlliteRT.

See the [Model Guide → Model Sources](MODELS.md#model-sources) for more details. You can also do a one-time import from a local JSON file or URL without adding it as a tracked source — see [Importing Your Own Models](MODELS.md#importing-your-own-models).

---

### How do I update a downloaded model?

OlliteRT checks your model sources periodically (~24 hours) for updated model files. When an update is available:

- A notification appears on your device
- The model card on the Models screen shows an update indicator
- The `/v1/models` API response includes `"update_available": true` for that model

To update, tap the model card and download the new version — it replaces the existing file. You can also pull-to-refresh on the Models screen to check for updates immediately.
