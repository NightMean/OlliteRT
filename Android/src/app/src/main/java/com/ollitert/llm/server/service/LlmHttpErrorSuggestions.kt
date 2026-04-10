package com.ollitert.llm.server.service

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
  fun suggest(kind: ErrorKind): String? = when (kind) {
    // ── Verified from code: app-generated error strings ──
    ErrorKind.CONTEXT_OVERFLOW ->
      "Try enabling prompt compaction (Truncate History, Compact Tool Schemas, or Trim Prompt) in OlliteRT Settings, or send shorter prompts."
    ErrorKind.TIMEOUT ->
      "Try sending a shorter prompt or restarting the server."
    ErrorKind.MODEL_NOT_FOUND ->
      "Check the Models screen for available downloaded models."
    ErrorKind.MODEL_FILES_MISSING ->
      "The model files may have been deleted. Try re-downloading the model from the Models screen."
    ErrorKind.PORT_BIND_FAILURE ->
      "The port is already in use by another app. Close the app occupying the port or change the port number in Settings."
    ErrorKind.MODEL_INSTANCE_NULL ->
      "The model is not loaded. Wait for loading to complete or restart the server."
    ErrorKind.IMAGE_DECODE_FAILED ->
      "The base64 image data could not be decoded. Verify the data URI is correctly formatted (data:image/...;base64,...) and the base64 payload is not truncated."
    ErrorKind.OOM ->
      "The device ran out of memory. Try closing other apps, using a smaller model, or reducing Max Tokens in Inference Settings."
    // ── LiteRT SDK errors: exact strings unknown without on-device testing ──
    ErrorKind.UNKNOWN_LITERT -> null // Don't guess — no suggestion is better than a wrong one
    ErrorKind.UNKNOWN -> null
  }

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
      // (used by extractActualTokenCounts regex at LlmHttpService.kt)
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
   * Maps [ErrorCategory] to an OpenAI-compatible error type string.
   * Used in JSON error responses for API clients.
   */
  fun openAiErrorType(category: ErrorCategory, errorMessage: String? = null): String = when (category) {
    ErrorCategory.NETWORK -> {
      val lower = errorMessage?.lowercase() ?: ""
      if (lower.contains("unauthorized") || lower.contains("forbidden")) "authentication_error"
      else "invalid_request_error"
    }
    else -> "server_error"
  }
}
