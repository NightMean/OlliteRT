# Model Allowlist JSON Schema

This document describes the JSON format for the model allowlist file (`model_allowlists/v1/model_allowlist.json`). The app loads this file from bundled assets (offline fallback) or fetches it from GitHub (latest version).

Parsed by `ModelAllowlistJson.decode()` → `ModelAllowlist` / `AllowedModel` data classes.

## Table of Contents

- [Top-Level Structure](#top-level-structure)
- [AllowedModel Object](#allowedmodel-object)
  - [Required Fields](#required-fields)
  - [Optional Fields](#optional-fields)
  - [Badge Values](#badge-values)
- [DefaultConfig Object](#defaultconfig-object)
- [SocModelFile Object](#socmodelfile-object)
- [Example](#example)
- [Backward Compatibility](#backward-compatibility)
- [Related Files](#related-files)

---

## Top-Level Structure

```json
{
  "schemaVersion": 1,
  "contentVersion": 1,
  "models": [ ... ]
}
```

| Field | Type | Required | Default | Description |
|:------|:-----|:---------|:--------|:------------|
| `schemaVersion` | `int` | No | `1` | Schema format version. App rejects lists with unsupported versions |
| `contentVersion` | `int` | No | `0` | Monotonically increasing content revision. When the network is unavailable, the app compares the disk cache's `contentVersion` against the bundled asset's and uses whichever is higher. **Bump this on every content change** (new models, changed fields, etc.) |
| `models` | `AllowedModel[]` | Yes | — | Array of model definitions |

---

## `AllowedModel` Object

Each entry in the `models` array.

### Required Fields

| Field | Type | Description |
|:------|:-----|:------------|
| `name` | `string` | Unique model identifier (no `/` characters). Used as display name if `displayName` is absent |
| `modelId` | `string` | HuggingFace repo ID (e.g. `litert-community/gemma-4-E2B-it-litert-lm`) |
| `modelFile` | `string` | Filename of the `.litertlm` model file in the repo |
| `description` | `string` | Model description (Markdown supported) |
| `sizeInBytes` | `long` | Model file size in bytes |
| `defaultConfig` | `DefaultConfig` | Inference configuration defaults (see below) |

### Optional Fields

| Field | Type | Default | Description |
|:------|:-----|:--------|:------------|
| `commitHash` | `string` | `""` | HuggingFace commit hash for download URL construction |
| `llmSupportImage` | `boolean` | `null` | Model supports vision/image input |
| `llmSupportAudio` | `boolean` | `null` | Model supports audio input |
| `llmSupportThinking` | `boolean` | `null` | Model supports extended thinking mode |
| `badge` | `string` | `null` | Badge key for UI display. Known keys: `best_overall`, `new`, `fastest`. Unknown keys render as title-cased text |
| `minDeviceMemoryInGb` | `int` | `null` | Minimum device RAM in GB (shows warning dialog if insufficient) |
| `localModelFilePathOverride` | `string` | `null` | Override path for local testing |
| `url` | `string` | `null` | Custom download URL (overrides HuggingFace URL construction) |
| `runtimeType` | `string` | `null` | Runtime type: `litert_lm` or `unknown` |
| `socToModelFiles` | `object` | `null` | Per-SoC model file overrides (see below) |

### Badge Values

The `badge` field drives a data-driven chip displayed on model cards (icon + label for known keys, text-only for unknown keys):

| Key | Icon | Color | Label |
|:----|:-----|:------|:------|
| `best_overall` | Star | Gold (#FCC934) | "Best overall" |
| `new` | NewReleases | Green (#4CAF50) | "New" |
| `fastest` | Speed | Blue (#2196F3) | "Fastest" |
| *(any other)* | None | Default | Title-cased key (e.g. `low_memory` → "Low Memory") |

---

## `DefaultConfig` Object

| Field | Type | Default | Description |
|:------|:-----|:--------|:------------|
| `topK` | `int` | `40` | Top-K sampling parameter |
| `topP` | `float` | `0.95` | Top-P (nucleus) sampling parameter |
| `temperature` | `float` | `1.0` | Sampling temperature |
| `accelerators` | `string` | `"gpu,cpu"` | Comma-separated list of accelerators: `cpu`, `gpu`, `npu` |
| `visionAccelerator` | `string` | `"gpu"` | Accelerator for vision encoder: `cpu`, `gpu`, `npu` |
| `maxContextLength` | `int` | `null` | Maximum context window size in tokens |
| `maxTokens` | `int` | `1024` | Maximum output tokens per response |

---

## `SocModelFile` Object

Per-SoC overrides inside `socToModelFiles`. The key is the SoC identifier string.

| Field | Type | Description |
|:------|:-----|:------------|
| `modelFile` | `string` | SoC-specific model filename |
| `url` | `string` | SoC-specific download URL |
| `commitHash` | `string` | SoC-specific commit hash |
| `sizeInBytes` | `long` | SoC-specific file size |

---

## Example

```json
{
  "schemaVersion": 1,
  "contentVersion": 1,
  "models": [
    {
      "name": "Gemma-4-E2B-it",
      "modelId": "litert-community/gemma-4-E2B-it-litert-lm",
      "modelFile": "gemma-4-E2B-it.litertlm",
      "description": "Gemma 4 E2B with multimodal support and 32K context.",
      "sizeInBytes": 2583085056,
      "minDeviceMemoryInGb": 8,
      "commitHash": "7fa1d78473894f7e736a21d920c3aa80f950c0db",
      "llmSupportImage": true,
      "llmSupportAudio": true,
      "llmSupportThinking": true,
      "badge": "best_overall",
      "defaultConfig": {
        "topK": 64,
        "topP": 0.95,
        "temperature": 1.0,
        "maxContextLength": 32000,
        "maxTokens": 4000,
        "accelerators": "gpu,cpu",
        "visionAccelerator": "gpu"
      }
    }
  ]
}
```

---

## Backward Compatibility

- **Gson ignores unknown fields** — adding new optional fields to the JSON won't break older app versions.
- **Kotlin defaults apply for missing fields** — omitting an optional field uses the default value listed above.
- **Exception:** Gson bypasses Kotlin default parameter values for non-nullable primitives. `ModelAllowlistJson.decode()` handles normalization (e.g. missing `schemaVersion` → `1`).
- **`contentVersion` defaults to `0`** — old JSON files without this field get version 0, so any bundled asset with `contentVersion >= 1` will correctly win over a stale disk cache.

---

## Related Files

| File | Role |
|:-----|:-----|
| `data/ModelAllowlist.kt` | `AllowedModel` and `ModelAllowlist` data classes |
| `data/ModelAllowlistJson.kt` | JSON decoder with Gson normalization |
| `data/ModelBadge.kt` | `ModelBadge` sealed class and `fromKey()` factory |
| `data/Model.kt` | Domain `Model` class (output of `AllowedModel.toModel()`) |
| `ui/common/modelitem/ModelBadgeChip.kt` | Badge chip composable |
| `ui/modelmanager/ModelAllowlistLoader.kt` | Asset/disk/network loader for the allowlist |
