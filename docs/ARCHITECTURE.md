# Architecture

This document describes OlliteRT's internal architecture for contributors and anyone interested in how the app works.

## Table of Contents

- [High-Level Overview](#high-level-overview)
- [Package Structure](#package-structure)
- [Key Components](#key-components)
- [Threading Model](#threading-model)
- [Persistence](#persistence)
- [Request Flow](#request-flow)
- [Tool Calling](#tool-calling)
- [Dependencies](#dependencies)

---

## High-Level Overview

```
┌──────────────────────────────────────────────────────┐
│                    Android App                       │
│                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │  Models  │  │  Status  │  │   Logs   │  ← UI      │
│  │  Screen  │  │  Screen  │  │  Screen  │            │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘            │
│       │             │             │                  │
│  ┌────┴─────────────┴─────────────┴─────┐            │  
│  │            ViewModels (Hilt)         │ ← State    │
│  └────────────────┬─────────────────────┘            │
│                   │                                  │
│  ┌────────────────┴───────────────────────┐          │
│  │        ServerService (Foreground)     │ ← Server │
│  │  ┌─────────────┐  ┌─────────────────┐  │          │
│  │  │  Ktor CIO   │  │  LiteRT Engine  │  │          │
│  │  │  HTTP Server│  │  (GPU/CPU)      │  │          │
│  │  └──────┬──────┘  └───────┬─────────┘  │          │
│  │         │                 │            │          │
│  │    Routes & Handlers   Inference       │          │
│  └────────────────────────────────────────┘          │
│                                                      │
│  ┌────────────────────────────────────────┐          │
│  │          Data Layer                    │ ← Persist│
│  │  SharedPrefs · Proto DataStore · Room  │          │
│  └────────────────────────────────────────┘          │
└──────────────────────────────────────────────────────┘
         │
         ▼ HTTP on port 8000
   OpenAI-compatible API
```

## Package Structure

```
com.ollitert.llm.server/
├── common/          # Shared constants, config (ProjectConfig, GitHubConfig)
├── data/            # Data models, serializers, config keys, repository management
│   └── db/          # Room database, DAOs, log persistence
├── di/              # Hilt dependency injection modules
├── runtime/         # LiteRT SDK bridge (ServerLlmModelHelper)
├── service/         # HTTP server, request handling, inference
├── ui/
│   ├── benchmark/   # Model benchmarking screen
│   ├── common/      # Shared UI components (model cards, tooltips, chips)
│   │   └── modelitem/  # Model card composables
│   ├── gettingstarted/ # One-time onboarding screen
│   ├── modelmanager/   # Models screen + download management
│   ├── navigation/     # Bottom nav, app scaffold, routing
│   ├── repositories/   # Model Sources screens (list + detail), ViewModel
│   ├── server/         # Status + Settings screens
│   │   ├── logs/       # Logs screen, event parsing, card rendering
│   │   └── settings/   # Settings cards, data model, definitions, validators
│   └── theme/          # Colors, typography, design tokens
└── worker/          # Background work (downloads, update checks, allowlist refresh)
```

## Key Components

### Service Layer (`service/`)

The heart of the app. Runs as an Android foreground service with a persistent notification.

| File | Responsibility |
|:-----|:---------------|
| `ServerService.kt` | Service lifecycle — start, stop, model loading, intent handling |
| `KtorServer.kt` | Ktor CIO HTTP server — routing, CORS plugin, bearer auth, response dispatch |
| `KtorRequestAdapter.kt` | Adapts Ktor `ApplicationCall` to the internal request model |
| `KtorSseWriter.kt` | SSE streaming writer — wraps Ktor's `Writer` from `respondTextWriter` |
| `SseWriter.kt` | SSE writer interface — abstracts streaming output for testability |
| `HttpResponse.kt` | Sealed class for response types (JSON, Binary, PlainText, SSE) |
| `RouteResolver.kt` | URL → handler mapping for all endpoints |
| `EndpointHandlers.kt` | Inference API endpoints (`/v1/chat/completions`, `/v1/completions`, `/v1/responses`) |
| `InferenceRequest.kt` | Internal request data class — wraps prompt, images, audio, config for inference |
| `InferenceRunner.kt` | Inference execution — streaming, non-streaming, tool call detection |
| `InferenceGateway.kt` | Request validation and inference orchestration |
| `PayloadBuilders.kt` | JSON response construction (health, models, server info) |
| `ResponseRenderer.kt` | Renders LLM responses to JSON with capabilities metadata |
| `FinishReason.kt` | Infers finish reason (`stop`, `length`, `tool_calls`) from token counts |
| `ApiModels.kt` | Kotlin data classes for OpenAI API request/response format |
| `AudioTranscriptionHandler.kt` | Audio transcription endpoint (`/v1/audio/transcriptions`) |
| `TranscriptionFormatter.kt` | Formats transcription output (json, text, verbose_json) |
| `AudioPreprocessor.kt` | Audio format detection and stereo-to-mono downmix |
| `SchemaInjectionBridge.kt` | SDK tool schema injection — converts OpenAI tool specs to LiteRT `ToolProvider`, builds native `Message` history, converts native `ToolCall` objects back to API format |
| `ToolCallParser.kt` | Fallback text-based [tool call](TROUBLESHOOTING.md#tool-calling-experimental) detection — 5 single-call patterns (`tool_call` wrapper, `<tool_call>` XML, native Gemma `<\|tool_call>`, `function` wrapper, bare `name`+`arguments` JSON) and 3 multi-call patterns (multiple XML blocks, multiple Gemma blocks, JSON array) |
| `PromptBuilder.kt` | Prompt building, tool schema injection (prompt-based fallback), image/audio extraction, tool_choice resolution |
| `PromptCompactor.kt` | Context window overflow handling |
| `PrometheusRenderer.kt` | Prometheus `/metrics` exposition format |
| `ModelLifecycle.kt` | Model load/unload/reload, keep-alive idle timeout |
| `ModelFactory.kt` | Builds `Model` instances from allowlist and imported sources |
| `AllowlistLoader.kt` | Loads and caches the allowed model list |
| `NotificationHelper.kt` | Foreground notification building |
| `BridgeUtils.kt` | Utility functions for ID generation, model normalization, authorization, SSE escaping, and base64 compaction |
| `ErrorSuggestions.kt` | Maps error types to user-facing recovery suggestions |
| `TokenEstimation.kt` | Estimates token count from character length |
| `ServerMetrics.kt` | Singleton metrics accumulator (counters, gauges, timing) |
| `RequestLogStore.kt` | In-memory log store for the Logs screen |
| `BootReceiver.kt` | Auto-starts the server on device boot if configured |
| `CopyUrlReceiver.kt` | Copies server endpoint URL to clipboard from notification |

### Runtime Layer (`runtime/`)

| File | Responsibility |
|:-----|:---------------|
| `ServerLlmModelHelper.kt` | Bridge to LiteRT LM SDK — model initialization, inference, cleanup, conversation management. Type aliases for inference callbacks |

### Data Layer (`data/`)

| File | Responsibility |
|:-----|:---------------|
| `Model.kt` | Model data class with config, capabilities, state |
| `Config.kt` | Per-model inference config keys, types, and defaults |
| `Consts.kt` | Shared constants (WorkManager keys, UI dimensions, storage thresholds) |
| `Types.kt` | Enums for hardware accelerators (CPU, GPU, NPU) |
| `Repository.kt` | Repository data class with proto serialization — represents a model source |
| `RepositoryManager.kt` | Coordinates multiple model sources — per-source allowlist loading, deduplication, CRUD |
| `ModelAllowlist.kt` | Data classes for model allowlist definitions — see [JSON schema](MODEL_ALLOWLIST_SCHEMA.md) |
| `ModelAllowlistJson.kt` | JSON parser for model allowlist |
| `ModelBadge.kt` | Badge sealed class (`BestOverall`, `New`, `Fastest`, `Other`) |
| `ModelStorageUtils.kt` | Temp file cleanup and storage requirement checks |
| `RepositoryNameFallback.kt` | Derives human-readable names for model sources when metadata is unavailable |
| `BoundedHttpFetcher.kt` | Size-limited HTTP fetcher for model source JSON (10 MB cap) |
| `ServerPrefs.kt` | SharedPreferences accessor for server config |
| `DataStoreRepository.kt` | Interface for persisting app state to Proto DataStore |
| `DownloadRepository.kt` | Manages model downloads with progress tracking |
| `SettingsSerializer.kt` | Proto DataStore serializer for user settings |
| `UserDataSerializer.kt` | Proto DataStore serializer for user data |
| `BenchmarkResultsSerializer.kt` | Proto DataStore serializer for benchmark results |
| `db/OlliteDatabase.kt` | Room database definition |
| `db/RequestLogDao.kt` | Room DAO for querying and persisting request logs |
| `db/RequestLogEntity.kt` | Room entity with indexed columns and JSON extras |
| `db/RequestLogPersistence.kt` | Log entry persistence to Room with pruning |

### Worker Layer (`worker/`)

Background tasks managed by WorkManager with Hilt integration (`@HiltWorker`).

| File | Responsibility |
|:-----|:---------------|
| `AllowlistRefreshWorker.kt` | Periodic allowlist refresh (~24h) — fetches each enabled model source's list, detects model updates, fires notifications |
| `UpdateCheckWorker.kt` | Periodic app update check — queries GitHub Releases API for newer OlliteRT versions |
| `DownloadWorker.kt` | Model file download with progress tracking |
| `UpdateDismissReceiver.kt` | Suppresses re-posting update notification after user dismisses it |

### UI Layer (`ui/`)

All screens use Jetpack Compose with Material 3. State is managed via `@HiltViewModel` classes. Settings uses a data-driven `SettingEntry<T>` model with Compose `mutableStateOf`; other ViewModels expose `StateFlow`.

| Screen | Files | Description |
|:-------|:------|:------------|
| Getting Started | `gettingstarted/` | One-time onboarding |
| Models | `modelmanager/` | Model list, download, import, delete |
| Status | `server/StatusScreen.kt` | Live metrics dashboard |
| Logs | `server/LogsScreen.kt` + `server/logs/` | Request/response logs with event parsing |
| Settings | `server/SettingsScreen.kt`, `SettingsViewModel.kt`, `InferenceSettingsSheet.kt` + `server/settings/` (13 card files, data model, definitions, dialogs, footer, renderers, validators) | Server configuration, per-model inference settings bottom sheet |
| Model Sources | `repositories/RepositoryListScreen.kt`, `RepositoryDetailScreen.kt`, `RepositoryViewModel.kt` | Model source management — add, remove, enable/disable model sources |
| Benchmark | `benchmark/` | Model performance benchmarking |

## Threading Model

- **Main thread** — Compose UI, Android lifecycle callbacks
- **`Dispatchers.IO`** — File I/O, SharedPreferences, network calls
- **`Dispatchers.Default`** — JSON parsing, search filtering
- **Single-thread executor** — All LiteRT inference (SDK is not thread-safe)

Requests are processed one at a time. The inference lock serializes all model interactions.

## Persistence

| Mechanism | Used For | Why |
|:----------|:---------|:----|
| **SharedPreferences** | Server config, per-model settings, feature toggles | Synchronous reads needed by the service on every request |
| **Proto DataStore** | HuggingFace token, imported model registry, onboarding state, benchmarks, model source configuration | Typed schemas, async API, encryption-ready |
| **Room** | Request log history | Queryable, prunable, survives process death |

## Request Flow

```
Client HTTP request
  → Ktor CIO (KtorServer)
    → CORS plugin (automatic preflight + headers)
    → Bearer auth (constant-time token validation)
    → Route resolution (KtorServer routing DSL)
    → Request adaptation (KtorRequestAdapter → internal request model)
    → Endpoint orchestration (EndpointHandlers)
      → Prompt building (PromptBuilder — tool schema injection, image/audio extraction)
      → Prompt compaction (PromptCompactor — history truncation, context fitting)
      → Inference (InferenceRunner → InferenceGateway → ServerLlmModelHelper → LiteRT Engine)
      → Tool call detection (SchemaInjectionBridge native calls, ToolCallParser fallback)
      → Response building (PayloadBuilders / ResponseRenderer)
    → HTTP response to client (JSON, SSE stream, or binary)
```

## Tool Calling

OlliteRT supports OpenAI-compatible tool calling via two modes:

### Schema Injection (default)

The client sends tools in OpenAI format (`tools` array with JSON Schema parameters). `SchemaInjectionBridge` translates this into the LiteRT LM SDK's native tool calling interface:

1. **Tool specs → ToolProviders** — each OpenAI `ToolSpec` is converted to a LiteRT `ToolProvider` object (name + parameter schema as `JsonObjectSchema`)
2. **Conversation history → native Messages** — prior `user`/`assistant`/`tool` messages are converted to LiteRT `Message` objects with the appropriate roles, skipping the system prompt (which is handled separately) and the last user message (which becomes the `inputText` parameter)
3. **Tool result workaround** — when the last messages are `assistant` (with tool_calls) followed by `tool` (with results), these are formatted into a synthetic user message describing the function return values, because the SDK doesn't support multi-turn tool result injection directly
4. **Native tool calls → API response** — when the model produces tool calls via the SDK callback (`onMessage` with `toolCalls`), `SchemaInjectionBridge` converts them back to OpenAI `ToolCall` objects (with generated call IDs, serialized arguments)

The SDK handles tool schema formatting internally — tool definitions don't appear in the text prompt.

### Prompt-based fallback

When Schema Injection is disabled, `PromptBuilder` injects tool schemas directly into the text prompt with explicit formatting instructions. `ToolCallParser` then attempts to parse tool calls from the model's raw text output using pattern matching (JSON wrappers, XML tags, Gemma-native format). This mode works with any model but is less reliable since the model must follow the formatting instructions exactly.

## Dependencies

| Library | Purpose |
|:--------|:--------|
| **[LiteRT LM](https://github.com/google-ai-edge/LiteRT-LM)** | On-device LLM inference runtime |
| **[Ktor CIO](https://ktor.io/)** | Coroutine-based HTTP server (CORS, content negotiation, status pages plugins) |
| **[Hilt](https://dagger.dev/hilt/)** | Dependency injection |
| **[Jetpack Compose](https://developer.android.com/compose)** | UI framework (Material 3) |
| **[Room](https://developer.android.com/training/data-storage/room)** | SQLite database for request log persistence |
| **[Proto DataStore](https://developer.android.com/topic/libraries/architecture/datastore)** | Typed key-value storage (settings, credentials, imports) |
| **[Protobuf Java Lite](https://protobuf.dev/)** | Serialization format for DataStore schemas |
| **[WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)** | Background tasks (downloads, update checks, allowlist refresh) |
| **[Coil](https://coil-kt.github.io/coil/)** | Async image loading (model source icons) |
| **[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)** | JSON serialization for API models |
| **[AppAuth](https://github.com/openid/AppAuth-Android)** | OAuth 2.0 flow for HuggingFace sign-in |
| **[Multiplatform Markdown Renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)** | Markdown rendering in Compose (Material 3) |
| **[Splash Screen](https://developer.android.com/develop/ui/views/launch/splash-screen)** | Android 12+ splash screen API |
| **[OSS Licenses](https://developers.google.com/android/guides/opensource)** | Open source license display |
