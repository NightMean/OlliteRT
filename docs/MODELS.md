# Model Guide

## Table of Contents

- [Supported Models](#supported-models)
- [Which Model Should I Pick?](#which-model-should-i-pick)
- [Capabilities Explained](#capabilities-explained)
- [RAM Requirements](#ram-requirements)
- [Context Window](#context-window)
- [Importing Your Own Models](#importing-your-own-models)
- [Model Sources](#model-sources)
- [Model Updates](#model-updates)
- [Model Storage](#model-storage)

---

## Supported Models

| Model | Size | Context | Capabilities | Min RAM | Best For |
|:------|-----:|--------:|:-------------|--------:|:---------|
| **Gemma 4 E2B** | 2.4 GB | 32K | Text · Vision · Audio · Thinking · Tools · MTP | 8 GB | All-rounder — chat, vision, audio, tool calling |
| **Gemma 4 E4B** | 3.4 GB | 32K | Text · Vision · Audio · Thinking · Tools · MTP | 12 GB | Higher quality than E2B, same capabilities, needs more RAM |
| **Gemma 3n E2B** | 3.4 GB | 4K | Text · Vision · Audio | 8 GB | Vision and audio tasks on 8 GB devices |
| **Gemma 3n E4B** | 4.6 GB | 4K | Text · Vision · Audio | 12 GB | Higher quality vision/audio on 12 GB+ devices |
| **Gemma 3 1B** | 0.5 GB | 1K | Text | 6 GB | Smallest model, fastest responses, text-only |
| **Qwen 2.5 1.5B** | 1.5 GB | 4K | Text | 6 GB | Good text quality for its size, longer context than Gemma 3 1B |
| **DeepSeek-R1 1.5B** | 1.7 GB | 4K | Text | 6 GB | Reasoning-focused, includes chain-of-thought |

The models in this table are downloaded from [Hugging Face](https://huggingface.co/litert-community). OlliteRT runs `.litertlm` files on the device with Google's [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) runtime.

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
| **Tools** | **Experimental.** Function/tool calling via SDK schema injection (default) or prompt-based fallback. With schema injection enabled, tool schemas are registered directly with the LiteRT SDK for structured output. Best with Gemma 4 models, smaller models may not follow tool instructions reliably. See [Troubleshooting → Tool Calling](TROUBLESHOOTING.md#tool-calling-experimental) for tips |
| **MTP** | Multi-Token Prediction (speculative decoding) — the model predicts multiple tokens at once for faster decode speed without quality loss. Requires a model file that includes an MTP draft head. Toggle per model in inference settings. See [FAQ → What is speculative decoding?](FAQ.md#what-is-speculative-decoding--mtp) |

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

When a conversation exceeds the context window, OlliteRT can automatically compact older messages to fit. Three compaction strategies are available in Settings → Context Management (all disabled by default). See the [FAQ](FAQ.md#what-is-prompt-compaction) for details, or [Troubleshooting](TROUBLESHOOTING.md#long-conversations-fail--context-window-exceeded) if conversations are failing.

## Favorite Models

Tap the star on a model card to add it to Favorites. Favorite models appear at the top of Models, and the Favorites filter appears when at least one model is marked. Tap the star again to remove a favorite. Deleting a downloaded file keeps its favorite if the model remains in the catalog.

## Importing Your Own Models

Tap the **+** button on the Models screen to explore Hugging Face or ModelScope, or import your own models.

**Explore Hugging Face:**
- The Hugging Face mark in the import menu is an [official brand asset](https://huggingface.co/brand); it remains the property of Hugging Face.
- Search and sort LiteRT-LM repositories, then select a repository and a `.litertlm` file. The link button also accepts a repository ID, model page, or direct file URL.
- A direct file link selects that file. If it is already imported, choose another file manually. When a provider is offline, you can manage saved files. Connect to the provider to import a variant.
- Check the model card for device compatibility. Select the accelerators and capabilities that the file supports, then tap **Import Model**. The model appears on Models, where you can start its download when ready.
- Files are grouped using filename hints and a device-memory estimate. This does not test whether a file will run; all files remain selectable.
- LiteRT Community repositories appear first within the results returned by Hugging Face. Check each model card for device compatibility.
- Results load page by page and remain incomplete until loading finishes. For a failed page request, Retry continues from the last completed page. Invalid pagination requires a refresh; exceptionally large searches must be narrowed to protect device memory.
- Some repositories require a Hugging Face token and acceptance of their terms before download. Configure the token in Settings and accept the terms on the model card if required.
- The selected revision stays fixed. To select another revision or change the selected capabilities, delete its downloaded file, remove the entry from Models, and add it again. Added models are independent of model sources and their automatic updates.
- The search only includes repositories tagged `litert-lm`. A matching file is not guaranteed to support chat or to run on your device. Web model files are excluded.

**From ModelScope:**
- The ModelScope mark in the import menu is an [official brand asset](https://modelscope.cn/models/modelscope/logos); it remains the property of ModelScope.
- Browse and search repositories tagged `litert-lm`, or open a ModelScope repository or file link with the link button.
- Choose International (`modelscope.ai`, the default) or China (`modelscope.cn`) in the browser. The sites have separate catalogs, so results can differ. Imported models keep their original site when you change the choice.
- Select a `.litertlm` file and configure its capabilities and accelerators. The model is added to Models; start its download there when ready.
- ModelScope lists downloads, likes, and update dates. File variants appear after opening a repository because search results do not include its files.
- Each selection is pinned to the file's revision. Compatibility estimates do not guarantee that a model will run. Check the model card and its license before downloading.
- Public repositories can be opened without a token. For private or gated repositories, add a read token in **Settings → Provider Tokens** for the matching site and complete any access approval on ModelScope. The two sites use separate tokens. ModelScope can rate-limit requests even with a token; retry later if needed.

**From a local `.litertlm` file:**
- Select a `.litertlm` model file from your device storage

**From a model list file (`.json`):**
- Select a JSON file from your device that follows the [Model Allowlist Schema](MODEL_ALLOWLIST_SCHEMA.md) — all models in the list are added to your Models screen in one go

**From a model list URL (`.json`):**
- Enter a URL pointing to a JSON model list (e.g. a raw GitHub link) — fetches the list and adds all models

> [!TIP]
> The JSON file and URL imports are one-time operations — models are added but the source is not tracked. For ongoing access to a third-party model source with automatic refresh and update detection, [add it as a model source](#model-sources) instead.

### Import Dialog

When importing a `.litertlm` file, the import dialog lets you configure the model before adding it:

- **Name** — editable model name (file extension is preserved automatically). If the name conflicts with an existing imported model, you're prompted to replace it. Names that conflict with built-in models are rejected
- **Capabilities** — toggle vision, audio, thinking, and tools support. These tell OlliteRT what to advertise — the model itself must actually support the capability
- **Max tokens** — configure the maximum output length
- **Context window** — set the model's context window size

You can also edit all of these settings after import via the model's inference settings (tap the gear icon on the model card). If the app stops during a file rename, it restores the old name or completes the rename when imported models are next loaded. The model's favorite and settings are preserved.

> [!IMPORTANT]
> - Only `.litertlm` format is supported — **GGUF files cannot be used** (LiteRT runtime limitation)
> - Imported models are copied to app storage (Android scoped storage requirement), so the file will temporarily use double the disk space
>
> If import fails, see [Troubleshooting → Model import fails](TROUBLESHOOTING.md#model-import-fails).

## Model Sources

OlliteRT uses a model source system for managing where models come from — similar to how F-Droid manages app repositories. Each model source is a JSON URL that provides a list of models available for download.

### Built-in Source

The **Official** model source is included by default and points to the [LiteRT community](https://huggingface.co/litert-community) models on HuggingFace. It cannot be removed.

### Custom Model Sources

You can add custom model sources to make additional models available:

1. Go to **Settings → Model Sources**
2. Tap the **+** button
3. Choose how to add the source:
   - **From a model list file** — select a local JSON file from your device
   - **From a URL** — enter the URL of a model list JSON file (e.g. a raw GitHub link)

The JSON file must follow the [Model Allowlist Schema](MODEL_ALLOWLIST_SCHEMA.md).

Custom model sources can be enabled, disabled, or removed at any time. Disabled sources are hidden from the Models screen but their configuration is preserved.

When enabled sources list the same model ID, the Official source takes precedence. Disable it to use a custom entry for that model. In a custom model list, `url` must be a direct URL to the model file, not a mirror's homepage; `sizeInBytes` must match the file's exact size.

### Community Model Sources

Model lists kept by their authors, not by the OlliteRT project. To use one, add its URL as a custom model source.

- [john-rocky LiteRT models](https://github.com/john-rocky/awesome-litert/tree/main/ollitert) — LiteRT-LM chat models from [litert-community](https://huggingface.co/litert-community): `https://raw.githubusercontent.com/john-rocky/awesome-litert/main/ollitert/models.json`

### Incompatible Models

The Model Sources detail screen shows all models in a source, including those incompatible with your device (shown greyed out with the reason, e.g. "Requires app version X.Y.Z"). The source list shows the count as "N models · M hidden" when some models are filtered out.

### Automatic Refresh

Model sources are automatically refreshed approximately every 24 hours in the background to check for new models and updates. You can also pull-to-refresh on the Models screen to trigger an immediate refresh.

> [!TIP]
> If you want to create your own model source, see the [Model Allowlist Schema](MODEL_ALLOWLIST_SCHEMA.md) for the JSON format.

## Model Updates

OlliteRT can detect when a newer version of a downloaded model is available in a model source:

- A background worker periodically checks each enabled model source for updated model files
- When an update is found, a notification is shown and the model card displays an update indicator
- The `/v1/models` API response includes an `update_available` field per model
- To update, download the new version — it replaces the existing model file

## Model Storage

Models are stored in the app's private storage directory. You can manage them from the Models screen:

- **Download** — one-tap download from HuggingFace (or from custom model sources)
- **Delete** — removes the model file and frees storage
- **Storage indicator** — the bottom bar shows available vs used storage

## SDK Compatibility

The LiteRT SDK version bundled with OlliteRT determines which models can run. Some newer models may require an app update even if they appear in a model source. See [SDK Compatibility](SDK_COMPATIBILITY.md) for the full compatibility table.

