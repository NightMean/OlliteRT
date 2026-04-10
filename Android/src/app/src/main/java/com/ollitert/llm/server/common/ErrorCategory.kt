package com.ollitert.llm.server.common

/**
 * Classifies errors for metrics, filtering, and Prometheus breakdowns.
 * Distinct from [com.ollitert.llm.server.service.EventCategory] which labels log events by source area.
 */
enum class ErrorCategory {
  /** Model loading, initialization, engine creation, file access failures. */
  MODEL_LOAD,
  /** Inference failures: context overflow, timeout, OOM during generation, model crash. */
  INFERENCE,
  /** HTTP layer: port bind, malformed request, payload too large, auth failure. */
  NETWORK,
  /** System-level: service lifecycle, DB errors, permissions, resource exhaustion. */
  SYSTEM,
}
