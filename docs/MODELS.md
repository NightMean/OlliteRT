# Model Guide

## Table of Contents

- [Supported Models](#supported-models)
- [Which Model Should I Pick?](#which-model-should-i-pick)
- [Capabilities Explained](#capabilities-explained)
- [RAM Requirements](#ram-requirements)
- [Context Window](#context-window)
- [Importing Your Own Models](#importing-your-own-models)
- [Model Storage](#model-storage)

---

## Supported Models

| Model | Size | Context | Capabilities | Min RAM | Best For |
|:------|-----:|--------:|:-------------|--------:|:---------|
| **Gemma 4 E2B** | 2.4 GB | 32K | Text · Vision · Audio · Thinking · Tools | 8 GB | All-rounder — chat, vision, audio, tool calling |
| **Gemma 4 E4B** | 3.4 GB | 32K | Text · Vision · Audio · Thinking · Tools | 12 GB | Higher quality than E2B, same capabilities, needs more RAM |
| **Gemma 3n E2B** | 3.4 GB | 4K | Text · Vision · Audio | 8 GB | Vision and audio tasks on 8 GB devices |
| **Gemma 3n E4B** | 4.6 GB | 4K | Text · Vision · Audio | 12 GB | Higher quality vision/audio on 12 GB+ devices |
| **Gemma 3 1B** | 0.5 GB | 1K | Text | 6 GB | Smallest model, fastest responses, text-only |
| **Qwen 2.5 1.5B** | 1.5 GB | 4K | Text | 6 GB | Good text quality for its size, longer context than Gemma 3 1B |
| **DeepSeek-R1 1.5B** | 1.7 GB | 4K | Text | 6 GB | Reasoning-focused, includes chain-of-thought |

All models are downloaded from [HuggingFace](https://huggingface.co/litert-community) in `.litertlm` format and run on-device via Google's [LiteRT](https://ai.google.dev/edge/litert) runtime.

## Which Model Should I Pick?

**Start with Gemma 4 E2B.** It's the best balance of quality, speed, and capability — multimodal (vision + audio), tool calling (experimental), thinking mode, and a 32K context window.

| Your Use Case | Recommended Model | Why |
|:--------------|:------------------|:----|
| Chat UI (e.g. Open WebUI) | Gemma 4 E2B or E4B | Best conversational quality, thinking mode for complex questions |
| Image/audio analysis | Gemma 4 E2B or Gemma 3n E2B | Both support vision and audio input |
| Low-RAM device (6 GB) | Gemma 3 1B or Qwen 2.5 1.5B | Only text-capable models fit in 6 GB |
| Fastest possible responses | Gemma 3 1B | Smallest model, lowest latency |
| Longer conversations | Gemma 4 E2B/E4B | 32K context vs 1K–4K on smaller models |
| Reasoning tasks | Gemma 4 E2B or Gemma 4 E4B | Both support chain-of-thought reasoning |

## Capabilities Explained

| Capability | What It Means |
|:-----------|:--------------|
| **Text** | Standard text chat and completions |
| **Vision** | Send images in API requests — the model can describe, analyze, and answer questions about them |
| **Audio** | Send audio in API requests — the model can transcribe and respond via text to spoken content |
| **Thinking** | Chain-of-thought reasoning mode — the model shows its reasoning process before answering (toggle per model in inference settings) |
| **Tools** | **Experimental.** Function/tool calling — not natively supported by the LiteRT runtime, so tool definitions are injected into the system prompt and the model's output is parsed for tool call patterns. Best with Gemma 4 models, smaller models may not follow tool instructions reliably. See [Troubleshooting → Tool Calling](TROUBLESHOOTING.md#tool-calling-experimental) for tips |

## RAM Requirements

The "Min RAM" column refers to your device's total RAM, not available RAM. These values come from Google's official model specifications in the [LiteRT community](https://huggingface.co/litert-community) model cards.

> [!NOTE]
> The model file is loaded into memory alongside the Android OS and other apps. A device with exactly the minimum RAM may experience slow performance or out-of-memory (OOM) kills — Android forcefully closing apps to free RAM. See [Troubleshooting → Server crashes](TROUBLESHOOTING.md#the-server-crashes--stops-unexpectedly) if this happens.

In practice:
- 8 GB+ is recommended for a smooth experience with most models
- 12 GB+ is recommended for Gemma 4 E4B and Gemma 3n E4B

## Context Window

The context window determines how much conversation history the model can "see" at once.

| Context | Roughly Equivalent To |
|--------:|:----------------------|
| 1K | ~750 words — a few message exchanges |
| 4K | ~3,000 words — a medium conversation |
| 32K | ~24,000 words — a long conversation or very long message |

> [!NOTE]
> The word counts in the table above are approximate, based on ~0.75 words per token. Actual numbers vary depending on language, content type, and vocabulary — code and non-English text typically use more tokens per word.

When a conversation exceeds the context window, OlliteRT can automatically compact older messages to fit. Three compaction strategies are available in Settings → Advanced (all disabled by default). See the [FAQ](FAQ.md#what-is-prompt-compaction) for details, or [Troubleshooting](TROUBLESHOOTING.md#long-conversations-fail--context-window-exceeded) if conversations are failing.

## Importing Your Own Models

OlliteRT supports importing `.litertlm` model files from local storage:

1. Go to the **Models** screen
2. Tap the **Import** button
3. Select a `.litertlm` file from your device

> [!IMPORTANT]
> - Only `.litertlm` format is supported — **GGUF files cannot be used** (LiteRT runtime limitation)
> - Imported models are copied to app storage (Android scoped storage requirement), so the file will temporarily use double the disk space
> - Capabilities (vision, audio, thinking, tools) are not auto-detected for imported models — they default to text-only. You can edit capabilities after import by tapping the edit icon on the model card. Enabling a capability only tells OlliteRT to advertise and use it — the model itself must actually support it, otherwise requests using that capability will fail or produce garbage output
>
> If import fails, see [Troubleshooting → Model import fails](TROUBLESHOOTING.md#model-import-fails).

## Model Storage

Models are stored in the app's private storage directory. You can manage them from the Models screen:

- **Download** — one-tap download from HuggingFace
- **Delete** — removes the model file and frees storage
- **Storage indicator** — the bottom bar shows available vs used storage
