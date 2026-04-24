/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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

package com.ollitert.llm.server.service

import android.content.Context
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.ErrorCategory

/**
 * Specific error types, assigned at catch sites. Each maps to exactly one recovery suggestion.
 * Only add entries here when you can verify the exact error condition from the code.
 */
enum class ErrorKind(val category: ErrorCategory) {
  CONTEXT_OVERFLOW(ErrorCategory.INFERENCE),
  TIMEOUT(ErrorCategory.INFERENCE),
  MODEL_NOT_FOUND(ErrorCategory.MODEL_LOAD),
  MODEL_FILES_MISSING(ErrorCategory.MODEL_LOAD),
  PORT_BIND_FAILURE(ErrorCategory.NETWORK),
  MODEL_INSTANCE_NULL(ErrorCategory.INFERENCE),
  IMAGE_DECODE_FAILED(ErrorCategory.INFERENCE),
  OOM(ErrorCategory.SYSTEM),

  /** Errors from LiteRT SDK where we don't know the exact string. */
  UNKNOWN_LITERT(ErrorCategory.INFERENCE),

  /** Catch-all for unrecognized errors. */
  UNKNOWN(ErrorCategory.SYSTEM),
}

/**
 * Maps [ErrorKind] values to actionable recovery suggestions for the user.
 *
 * Suggestions are displayed as supplementary text below the original error message —
 * they never replace or modify the raw error string.
 *
 * For API responses, the suggestion is a separate `"suggestion"` JSON field inside the
 * `"error"` object so clients can distinguish the original message from the guidance.
 */
object LlmHttpErrorSuggestions {

  /**
   * Returns an actionable suggestion for the given error kind, or null if
   * no suggestion is appropriate (e.g. for unknown LiteRT errors where
   * we can't confidently recommend a fix).
   */
  internal fun suggestionResId(kind: ErrorKind): Int? = when (kind) {
    ErrorKind.CONTEXT_OVERFLOW -> R.string.suggestion_context_overflow
    ErrorKind.TIMEOUT -> R.string.suggestion_timeout
    ErrorKind.MODEL_NOT_FOUND -> R.string.suggestion_model_not_found
    ErrorKind.MODEL_FILES_MISSING -> R.string.suggestion_model_files_missing
    ErrorKind.PORT_BIND_FAILURE -> R.string.suggestion_port_bind_failure
    ErrorKind.MODEL_INSTANCE_NULL -> R.string.suggestion_model_instance_null
    ErrorKind.IMAGE_DECODE_FAILED -> R.string.suggestion_image_decode_failed
    ErrorKind.OOM -> R.string.suggestion_oom
    ErrorKind.UNKNOWN_LITERT -> null
    ErrorKind.UNKNOWN -> null
  }

  fun suggest(kind: ErrorKind, context: Context): String? =
    suggestionResId(kind)?.let { context.getString(it) }

  /**
   * Fallback: attempt to classify an opaque error string from LiteRT's onError callback.
   * Only matches patterns verified in the codebase (e.g. extractActualTokenCounts regex).
   * Returns [ErrorKind.UNKNOWN_LITERT] for unrecognized LiteRT errors.
   */
  fun classifyFromString(error: String): ErrorKind {
    if (error.isBlank()) return ErrorKind.UNKNOWN_LITERT
    val lower = error.lowercase()
    return when {
      // Context overflow — verified: LiteRT produces "N >= M" format
      // (used by extractActualTokenCounts regex at LlmHttpInferenceRunner.kt)
      lower.contains(">=") || lower.contains("too long") ||
        lower.contains("exceed") || lower.contains("too many tokens") ->
        ErrorKind.CONTEXT_OVERFLOW
      // Timeout — app-generated string "timeout" (LlmHttpInferenceGateway.kt)
      error == "timeout" || lower.contains("timeout") ->
        ErrorKind.TIMEOUT
      // Model instance null — app-generated (ServerLlmModelHelper.kt)
      lower.contains("not initialized") ->
        ErrorKind.MODEL_INSTANCE_NULL
      // OOM — JVM error class name in the string
      lower.contains("outofmemoryerror") || lower.contains("out of memory") ->
        ErrorKind.OOM
      else -> ErrorKind.UNKNOWN_LITERT
    }
  }

  /**
   * Maps [ErrorKind] to an OpenAI-compatible error type string.
   * Used in JSON error responses for API clients.
   */
  fun openAiErrorType(kind: ErrorKind?): String = when (kind) {
    ErrorKind.CONTEXT_OVERFLOW -> "invalid_request_error"
    ErrorKind.IMAGE_DECODE_FAILED -> "invalid_request_error"
    ErrorKind.MODEL_NOT_FOUND -> "not_found_error"
    else -> "server_error"
  }

  /**
   * Maps [ErrorKind] to an OpenAI-compatible error code string, or null.
   * Only context overflow has a specific code that clients (LiteLLM, Open WebUI) check.
   */
  fun openAiErrorCode(kind: ErrorKind?): String? = when (kind) {
    ErrorKind.CONTEXT_OVERFLOW -> "context_length_exceeded"
    else -> null
  }
}
