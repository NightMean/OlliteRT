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

import java.util.Locale

/**
 * Renders [ServerMetrics] in Prometheus exposition format (text/plain; version=0.0.4).
 *
 * Follows the same pattern as llama.cpp server: simple counters and gauges, no histograms
 * or summaries. No Prometheus client library needed — just plain string building.
 *
 * Content-Type for the HTTP response: `text/plain; version=0.0.4; charset=utf-8`
 *
 * @see <a href="https://prometheus.io/docs/instrumenting/exposition_formats/">Prometheus Exposition Formats</a>
 */
object LlmHttpPrometheusRenderer {

  /** MIME type for Prometheus exposition format v0.0.4. */
  const val CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8"

  /**
   * Build the full `/metrics` response body from current [ServerMetrics] state.
   *
   * @param nowMs current wall-clock time in epoch millis (injectable for testing)
   */
  fun render(nowMs: Long = System.currentTimeMillis()): String = buildString {
    val m = ServerMetrics
    val uptimeSeconds = if (m.startedAtMs.value > 0L)
      (nowMs - m.startedAtMs.value) / 1000.0 else 0.0

    // ── Counters (cumulative, monotonically increasing) ──────────────────

    counter(
      "ollitert_requests_total",
      "Total number of inference requests processed.",
      m.requestCount.value,
    )
    counter(
      "ollitert_prompt_tokens_total",
      "Total number of prompt tokens processed (estimated).",
      m.tokensIn.value,
    )
    counter(
      "ollitert_generation_tokens_total",
      "Total number of generation tokens produced (estimated).",
      m.tokensGenerated.value,
    )
    counter(
      "ollitert_prompt_seconds_total",
      "Total time spent in prompt processing / prefill (seconds).",
      m.totalPrefillMs / 1000.0,
    )
    counter(
      "ollitert_generation_seconds_total",
      "Total time spent in token generation / decode (seconds).",
      m.totalDecodeMs / 1000.0,
    )
    counter(
      "ollitert_errors_total",
      "Total number of request errors.",
      m.errorCount.value,
    )
    // Per-category error breakdown using Prometheus labels
    labeledCounter(
      "ollitert_errors_by_category_total",
      "Total errors by category.",
      "category",
      mapOf(
        "model_load" to m.modelLoadErrors.value,
        "inference" to m.inferenceErrors.value,
        "network" to m.networkErrors.value,
        "system" to m.systemErrors.value,
      ),
    )
    counter(
      "ollitert_request_text_total",
      "Total text-only requests.",
      m.textRequests.value,
    )
    counter(
      "ollitert_request_image_total",
      "Total multimodal requests containing images.",
      m.imageRequests.value,
    )
    counter(
      "ollitert_request_audio_total",
      "Total multimodal requests containing audio.",
      m.audioRequests.value,
    )

    // ── Gauges (point-in-time values) ────────────────────────────────────

    gauge(
      "ollitert_uptime_seconds",
      "Time in seconds since the server entered RUNNING state.",
      uptimeSeconds,
    )
    gauge(
      "ollitert_model_load_time_seconds",
      "Time in seconds to load the current model.",
      m.modelLoadTimeMs.value / 1000.0,
    )
    gauge(
      "ollitert_prompt_tokens_per_second",
      "Last request prefill throughput (tokens/second).",
      m.lastPrefillSpeed.value,
    )
    gauge(
      "ollitert_generation_tokens_per_second",
      "Last request decode throughput (tokens/second).",
      m.lastDecodeSpeed.value,
    )
    gauge(
      "ollitert_generation_tokens_per_second_peak",
      "Peak decode throughput observed since server start (tokens/second).",
      m.peakDecodeSpeed.value,
    )
    gauge(
      "ollitert_time_to_first_token_ms",
      "Last request time to first token (milliseconds).",
      m.lastTtfbMs.value,
    )
    gauge(
      "ollitert_time_to_first_token_avg_ms",
      "Average time to first token across all requests (milliseconds).",
      m.avgTtfbMs.value,
    )
    gauge(
      "ollitert_inter_token_latency_ms",
      "Last request average inter-token latency (milliseconds).",
      m.lastItlMs.value,
    )
    gauge(
      "ollitert_request_latency_ms",
      "Last request total latency (milliseconds).",
      m.lastLatencyMs.value,
    )
    gauge(
      "ollitert_request_latency_avg_ms",
      "Average request latency across all requests (milliseconds).",
      m.avgLatencyMs.value,
    )
    gauge(
      "ollitert_request_latency_peak_ms",
      "Peak request latency observed since server start (milliseconds).",
      m.peakLatencyMs.value,
    )
    gauge(
      "ollitert_context_utilization_percent",
      "Last request input tokens as percentage of model context window.",
      m.lastContextUtilization.value,
    )
    gauge(
      "ollitert_requests_processing",
      "Number of requests currently being processed (0 or 1).",
      if (m.isInferring.value) 1L else 0L,
    )
    gauge(
      "ollitert_model_idle_unloaded",
      "Whether the model is unloaded due to keep_alive idle timeout (0 or 1). Next request triggers auto-reload.",
      if (m.isIdleUnloaded.value) 1L else 0L,
    )

    // ── Memory gauges (populated by UI-side polling every 3s) ───────────

    gauge(
      "ollitert_memory_native_heap_bytes",
      "Native heap allocated bytes (dominated by LiteRT model weights).",
      m.nativeHeapBytes.value,
    )
    gauge(
      "ollitert_memory_app_heap_used_bytes",
      "JVM heap used bytes (totalMemory - freeMemory).",
      m.appHeapUsedBytes.value,
    )
    gauge(
      "ollitert_memory_app_total_pss_bytes",
      "Total process PSS including JVM heap, native heap, and resident mmap'd model pages.",
      m.appTotalPssBytes.value,
    )
    gauge(
      "ollitert_memory_device_available_bytes",
      "Device available RAM in bytes.",
      m.deviceAvailRamBytes.value,
    )
    gauge(
      "ollitert_memory_device_total_bytes",
      "Device total RAM in bytes.",
      m.deviceTotalRamBytes.value,
    )
  }

  // ── Helpers ────────────────────────────────────────────────────────────

  private fun StringBuilder.counter(name: String, help: String, value: Number) =
    metric(name, "counter", help, value)

  private fun StringBuilder.gauge(name: String, help: String, value: Number) =
    metric(name, "gauge", help, value)

  private fun StringBuilder.metric(name: String, type: String, help: String, value: Number) {
    append("# HELP ").append(name).append(' ').appendLine(help)
    append("# TYPE ").append(name).append(' ').appendLine(type)
    append(name).append(' ')
    // Integer formatting for whole numbers, 4-decimal precision for fractional
    // Locale.US ensures '.' decimal separator — Prometheus rejects ',' from European locales
    if (value is Long || value is Int) append(value) else append(String.format(Locale.US, "%.4f", value.toDouble()))
    appendLine()
  }

  /** Emit a labeled counter with one line per label value. */
  private fun StringBuilder.labeledCounter(name: String, help: String, labelKey: String, values: Map<String, Long>) {
    append("# HELP ").append(name).append(' ').appendLine(help)
    append("# TYPE ").append(name).append(" counter").appendLine()
    for ((label, value) in values) {
      append(name).append('{').append(labelKey).append("=\"").append(label).append("\"} ").append(value).appendLine()
    }
  }
}
