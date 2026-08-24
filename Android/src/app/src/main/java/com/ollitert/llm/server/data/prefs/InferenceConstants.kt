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

package com.ollitert.llm.server.data.prefs
import com.ollitert.llm.server.data.model.Accelerator

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

// Placeholder token inserted into prompts where images appear.
const val IMAGE_PLACEHOLDER = "<|image|>"

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

// Streaming SSE coroutine safety buffer: the outer `withTimeout` wrapping channel
// consumption is `inferenceTimeoutSeconds + this`, giving the inner inference
// timeout room to fire and unwind before the outer timeout cancels the coroutine.
const val STREAM_OUTER_TIMEOUT_SAFETY_BUFFER_SECONDS = 30L

// Fallback outer-timeout (ms) for SSE responses that don't run real inference.
const val DEFAULT_SSE_OUTER_TIMEOUT_MS = 150_000L

// Interval between SSE ping events sent during long prefill.
const val SSE_PING_INTERVAL_MS = 10_000L

// Maximum time (seconds) to wait for previous model cleanup before initializing a new one.
const val CLEANUP_AWAIT_TIMEOUT_SECONDS = 15L

// Maximum time (ms) for runBlocking DataStore reads during service init / keep-alive reload.
const val DATASTORE_READ_TIMEOUT_MS = 5_000L

// Keep-alive settings.
// When model is inferring at keep-alive timeout, recheck after this delay (ms).
const val KEEP_ALIVE_RECHECK_MS = 30_000L

// Minimum free storage (bytes) before attempting model init via LiteRT Engine.
const val MIN_STORAGE_FOR_MODEL_INIT_BYTES = 500L * 1024 * 1024

// Debounce interval (ms) for updating the Logs screen preview during streaming inference.
const val LOG_STREAMING_PREVIEW_DEBOUNCE_MS = 300L

// Approximate characters per token for English text (~3.5–4 for Gemma/GPT tokenizers).
const val CHARS_PER_TOKEN_ESTIMATE = 4
