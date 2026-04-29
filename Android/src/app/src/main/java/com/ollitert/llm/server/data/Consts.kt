/*
 * Copyright 2025 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ollitert.llm.server.data

import android.os.Build
import androidx.compose.ui.unit.dp

// Keys used to send/receive data to Work.
const val KEY_MODEL_URL = "KEY_MODEL_URL"
const val KEY_MODEL_NAME = "KEY_MODEL_NAME"
const val KEY_MODEL_COMMIT_HASH = "KEY_MODEL_COMMIT_HASH"
const val KEY_MODEL_DOWNLOAD_MODEL_DIR = "KEY_MODEL_DOWNLOAD_MODEL_DIR"
const val KEY_MODEL_DOWNLOAD_FILE_NAME = "KEY_MODEL_DOWNLOAD_FILE_NAME"
const val KEY_MODEL_TOTAL_BYTES = "KEY_MODEL_TOTAL_BYTES"
const val KEY_MODEL_DOWNLOAD_RECEIVED_BYTES = "KEY_MODEL_DOWNLOAD_RECEIVED_BYTES"
const val KEY_MODEL_DOWNLOAD_RATE = "KEY_MODEL_DOWNLOAD_RATE"
const val KEY_MODEL_DOWNLOAD_ERROR_MESSAGE = "KEY_MODEL_DOWNLOAD_ERROR_MESSAGE"
const val KEY_MODEL_DOWNLOAD_ACCESS_TOKEN = "KEY_MODEL_DOWNLOAD_ACCESS_TOKEN"
const val KEY_MODEL_EXTRA_DATA_URLS = "KEY_MODEL_EXTRA_DATA_URLS"
const val KEY_MODEL_EXTRA_DATA_DOWNLOAD_FILE_NAMES = "KEY_MODEL_EXTRA_DATA_DOWNLOAD_FILE_NAMES"
const val KEY_MODEL_IS_ZIP = "KEY_MODEL_IS_ZIP"
const val KEY_MODEL_UNZIPPED_DIR = "KEY_MODEL_UNZIPPED_DIR"
const val KEY_MODEL_START_UNZIPPING = "KEY_MODEL_START_UNZIPPING"

// Default values for LLM models.
const val MIN_MAX_TOKENS = 100
const val MAX_MAX_TOKENS = 32768
const val DEFAULT_MAX_TOKEN = 1024
const val MIN_TOPK = 5
const val MAX_TOPK = 100
const val DEFAULT_TOPK = 64
const val MIN_TOPP = 0.0f
const val MAX_TOPP = 1.0f
const val DEFAULT_TOPP = 0.95f
const val MIN_TEMPERATURE = 0.0f
const val MAX_TEMPERATURE = 2.0f
const val DEFAULT_TEMPERATURE = 1.0f
val DEFAULT_ACCELERATORS = listOf(Accelerator.GPU)
val DEFAULT_VISION_ACCELERATOR = Accelerator.GPU

// The size of capability icons shown beside model names in the model list screen.
val MODEL_INFO_ICON_SIZE = 18.dp

// The extension of the tmp download files.
const val TMP_FILE_EXT = "olliterttmp"

// Warmup inference settings — sent after model load to pre-fill caches and verify the engine works.
const val WARMUP_MESSAGE = "Hello"
// Maximum time (seconds) to wait for the warmup inference pass to complete.
const val WARMUP_TIMEOUT_SECONDS = 10L

// Inference timeouts (seconds).
// Timeout for /v1/chat/completions and /v1/completions endpoints.
const val CHAT_COMPLETIONS_TIMEOUT_SECONDS = 120L
// Timeout for /v1/responses endpoint.
const val RESPONSES_TIMEOUT_SECONDS = 90L
// Default timeout for streaming inference.
const val STREAMING_TIMEOUT_SECONDS = 90L
// Default timeout for non-streaming (blocking) inference.
const val BLOCKING_TIMEOUT_SECONDS = 30L
// Maximum time (seconds) to wait for previous model cleanup before initializing a new one.
const val CLEANUP_AWAIT_TIMEOUT_SECONDS = 15L
// Maximum time (ms) for runBlocking DataStore reads during service init / keep-alive reload.
// Protects against indefinite hangs if the DataStore file is corrupted or locked.
const val DATASTORE_READ_TIMEOUT_MS = 5_000L

// Keep-alive settings.
// When model is inferring at keep-alive timeout, recheck after this delay (ms).
const val KEEP_ALIVE_RECHECK_MS = 30_000L

// Minimum free storage (bytes) before attempting model init via LiteRT Engine.
// LiteRT needs scratch space for memory-mapping, temp files, GPU buffer allocation,
// and XNNPack weight caches that can be hundreds of MB.
const val MIN_STORAGE_FOR_MODEL_INIT_BYTES = 500L * 1024 * 1024

// Debounce interval (ms) for updating the Logs screen preview during streaming inference.
const val LOG_STREAMING_PREVIEW_DEBOUNCE_MS = 300L

// Approximate characters per token for English text (~3.5–4 for Gemma/GPT tokenizers).
// Drifts for code (~2.5) and multilingual text. No tokenizer API exists in LiteRT LM.
const val CHARS_PER_TOKEN_ESTIMATE = 4

// Port validation range (IANA: 0–1023 reserved for well-known services).
const val DEFAULT_PORT = 8000
const val MIN_VALID_PORT = 1024
const val MAX_VALID_PORT = 65535

// HTTP connection timeouts — fail fast so local fallback kicks in quickly.
const val HTTP_CONNECT_TIMEOUT_MS = 5_000
const val HTTP_READ_TIMEOUT_MS = 10_000

// Download timeouts — longer than metadata-fetch timeouts because model downloads
// are multi-GB and can legitimately pause during network congestion.
const val DOWNLOAD_CONNECT_TIMEOUT_MS = 30_000
const val DOWNLOAD_READ_TIMEOUT_MS = 60_000

// CORS preflight response cache duration (24 hours).
const val CORS_PREFLIGHT_MAX_AGE_SECONDS = 86400L

// Model allowlist asset/disk-cache filenames.
// Master lives at /model_allowlists/v1/model_allowlist.json (repo root) — Gradle copies it
// into assets/ on every build (see syncAllowlist task in app/build.gradle.kts).
const val MODEL_ALLOWLIST_CACHE_PREFIX = "model_allowlist_"
const val MODEL_ALLOWLIST_FILENAME = "model_allowlist.json"
const val MODEL_ALLOWLIST_TEST_FILENAME = "${MODEL_ALLOWLIST_CACHE_PREFIX}test.json"
const val MODEL_ALLOWLIST_OFFICIAL_FILENAME = "${MODEL_ALLOWLIST_CACHE_PREFIX}official.json"
const val OFFICIAL_REPO_ID = "official"
const val MAX_REPO_NAME_LENGTH = 100
const val MAX_REPO_DESCRIPTION_LENGTH = 500
const val MAX_REPO_ICON_URL_LENGTH = 2048
const val MAX_ALLOWLIST_RESPONSE_BYTES = 10L * 1024 * 1024
const val MAX_REDIRECTS = 5
const val MAX_MODELS_PER_REPO = 500
const val REPO_LIMIT_WARNING_THRESHOLD = 16
const val UNKNOWN_REPO_LABEL = "Unknown"
const val MAX_REPO_ERROR_LENGTH = 200
const val UNKNOWN_ERROR_FALLBACK = "Unknown error"

// Base64 data URI compaction threshold — payloads shorter than ~1 KB (1365 base64 chars ≈ 1024 bytes)
// are left inline (thumbnails, icons). Longer payloads are replaced with a size placeholder
// to avoid Compose rendering freezes in the Logs tab.
const val BASE64_COMPACT_THRESHOLD_CHARS = 1365

// Live UI timer tick interval (uptime, loading elapsed, metric refresh).
const val UI_TIMER_TICK_MS = 1000L

// Debounce delay for server start/stop/reload button to prevent double-tap races.
const val ACTION_IN_FLIGHT_DEBOUNCE_MS = 1000L

// Download progress reporting.
const val DOWNLOAD_PROGRESS_UPDATE_INTERVAL_MS = 200L
const val DOWNLOAD_SPEED_ROLLING_BUFFER_SIZE = 5
const val DOWNLOAD_UNZIP_BUFFER_SIZE = 65536

// Log persistence pruning intervals.
const val DEFAULT_IN_MEMORY_LOG_CAP = 100
const val HARD_MAX_IN_MEMORY_ENTRIES = 10_000
const val MIN_PRUNE_INTERVAL_MS = 60_000L          // 1 minute
const val MAX_PRUNE_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours

/** Convert bytes to gigabytes as Float (for UI display). */
fun Long.bytesToGb(): Float = this / (1024f * 1024f * 1024f)

/** Convert bytes to megabytes as Long (for log messages). */
fun Long.bytesToMb(): Long = this / (1024L * 1024L)

// Current device's SOC in lowercase.
val SOC = Build.SOC_MODEL.lowercase()
