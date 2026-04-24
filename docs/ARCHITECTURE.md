# Architecture

This document describes OlliteRT's internal architecture for contributors and anyone interested in how the app works.

## Table of Contents

- [High-Level Overview](#high-level-overview)
- [Package Structure](#package-structure)
- [Key Components](#key-components)
- [Threading Model](#threading-model)
- [Persistence](#persistence)
- [Request Flow](#request-flow)
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
│  │        LlmHttpService (Foreground)     │ ← Server │
│  │  ┌─────────────┐  ┌─────────────────┐  │          │
│  │  │  NanoHTTPD  │  │  LiteRT Engine  │  │          │
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
| `LlmHttpService.kt` | Service lifecycle — start, stop, model loading, intent handling |
| `LlmHttpServer.kt` | NanoHTTPD HTTP server — routing, CORS, auth, response dispatch |
| `LlmHttpRouteResolver.kt` | URL → handler mapping for all endpoints |
| `LlmHttpEndpointHandlers.kt` | Inference API endpoints (`/generate`, `/v1/chat/completions`, `/v1/completions`, `/v1/responses`) |
| `LlmHttpInferenceRunner.kt` | Inference execution — streaming, non-streaming, tool call detection |
| `LlmHttpInferenceGateway.kt` | Request validation and inference orchestration |
| `LlmHttpBodyParser.kt` | Request body parsing and validation |
| `LlmHttpPayloadBuilders.kt` | JSON response construction (health, models, server info) |
| `LlmHttpResponseRenderer.kt` | Renders LLM responses to JSON with capabilities metadata |
| `LlmHttpApiModels.kt` | Kotlin data classes for OpenAI API request/response format |
| `LlmHttpAudioTranscriptionHandler.kt` | Audio transcription endpoint (`/v1/audio/transcriptions`) |
| `LlmHttpAudioPreprocessor.kt` | Audio format detection and stereo-to-mono downmix |
| `LlmHttpToolCallParser.kt` | Post-inference [tool call](TROUBLESHOOTING.md#tool-calling-experimental) detection (5 output patterns) |
| `LlmHttpRequestAdapter.kt` | Prompt building, tool schema injection, image/audio extraction, tool_choice resolution |
| `LlmHttpPromptCompactor.kt` | Context window overflow handling |
| `LlmHttpPrometheusRenderer.kt` | Prometheus `/metrics` exposition format |
| `LlmHttpModelLifecycle.kt` | Model load/unload/reload, keep-alive idle timeout |
| `LlmHttpModelFactory.kt` | Builds `Model` instances from allowlist and imported sources |
| `LlmHttpAllowlistLoader.kt` | Loads and caches the allowed model list |
| `LlmHttpNotificationHelper.kt` | Foreground notification building |
| `LlmHttpCorsHelper.kt` | CORS header management |
| `LlmHttpBridgeUtils.kt` | Utility functions for ID generation, model normalization, authorization, SSE escaping, and base64 compaction |
| `LlmHttpErrorSuggestions.kt` | Maps error types to user-facing recovery suggestions |
| `LlmHttpLogger.kt` | File-based request/response logging |
| `TokenEstimation.kt` | Estimates token count from character length |
| `ServerMetrics.kt` | Singleton metrics accumulator (counters, gauges, timing) |
| `RequestLogStore.kt` | In-memory log store for the Logs screen |
| `FlushingSseResponse.kt` | NanoHTTPD response that flushes SSE chunks immediately |
| `BlockingQueueInputStream.kt` | Thread-safe InputStream backed by a queue for SSE streaming |
| `BootReceiver.kt` | Auto-starts the server on device boot if configured |
| `CopyUrlReceiver.kt` | Copies server endpoint URL to clipboard from notification |

### Runtime Layer (`runtime/`)

| File | Responsibility |
|:-----|:---------------|
| `LlmModelHelper.kt` | Interface and type aliases for inference callbacks and cleanup handlers |
| `ServerLlmModelHelper.kt` | Bridge to LiteRT LM SDK — model initialization, inference, cleanup, conversation management |

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
| `LlmHttpPrefs.kt` | SharedPreferences accessor for server config |
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

### UI Layer (`ui/`)

All screens use Jetpack Compose with Material 3. State is managed via `@HiltViewModel` classes. Settings uses a data-driven `SettingEntry<T>` model with Compose `mutableStateOf`; other ViewModels expose `StateFlow`.

| Screen | Files | Description |
|:-------|:------|:------------|
| Getting Started | `gettingstarted/` | One-time onboarding |
| Models | `modelmanager/` | Model list, download, import, delete |
| Status | `server/StatusScreen.kt` | Live metrics dashboard |
| Logs | `server/LogsScreen.kt` + `server/logs/` | Request/response logs with event parsing |
| Settings | `server/SettingsScreen.kt`, `SettingsViewModel.kt` + `server/settings/` (12 card files, data model, definitions, dialogs, footer, renderers, validators) | Server configuration |
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
  → NanoHTTPD (LlmHttpServer)
    → CORS header computation (LlmHttpCorsHelper)
    → Route resolution (LlmHttpRouteResolver)
    → Auth check (bearer token)
    → Endpoint orchestration (LlmHttpEndpointHandlers)
      → Body parsing (LlmHttpBodyParser)
      → Request adaptation (LlmHttpRequestAdapter — prompt building, tool schema, image/audio)
      → Prompt compaction (LlmHttpPromptCompactor — history truncation, context fitting)
      → Inference (LlmHttpInferenceRunner → LlmHttpInferenceGateway → ServerLlmModelHelper → LiteRT Engine)
      → Tool call detection (LlmHttpToolCallParser)
      → Response building (LlmHttpPayloadBuilders)
    → CORS headers applied
    → HTTP response to client
```
## Dependencies

| Library | Purpose |
|:--------|:--------|
| **[LiteRT LM](https://github.com/google-ai-edge/LiteRT-LM)** | On-device LLM inference runtime |
| **[NanoHTTPD](https://github.com/NanoHttpd/nanohttpd)** | Lightweight HTTP server |
| **[Hilt](https://dagger.dev/hilt/)** | Dependency injection |
| **[Jetpack Compose](https://developer.android.com/compose)** | UI framework (Material 3) |
| **[Room](https://developer.android.com/training/data-storage/room)** | SQLite database for request log persistence |
| **[Proto DataStore](https://developer.android.com/topic/libraries/architecture/datastore)** | Typed key-value storage (settings, credentials, imports) |
| **[Protobuf Java Lite](https://protobuf.dev/)** | Serialization format for DataStore schemas |
| **[WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)** | Background tasks (downloads, update checks, allowlist refresh) |
| **[Coil](https://coil-kt.github.io/coil/)** | Async image loading (model source icons) |
| **[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)** | JSON serialization for API models |
| **[Gson](https://github.com/google/gson)** | JSON serialization (used by LiteRT SDK and allowlist parsing) |
| **[Moshi](https://github.com/square/moshi)** | JSON serialization (used by model config and data layer) |
| **[AppAuth](https://github.com/openid/AppAuth-Android)** | OAuth 2.0 flow for HuggingFace sign-in |
| **[Commonmark](https://github.com/commonmark/commonmark-java)** | Markdown parsing for rich text rendering |
| **[Compose Rich Text](https://github.com/nicehash/compose-richtext)** | Rich text rendering in Compose |
| **[Splash Screen](https://developer.android.com/develop/ui/views/launch/splash-screen)** | Android 12+ splash screen API |
