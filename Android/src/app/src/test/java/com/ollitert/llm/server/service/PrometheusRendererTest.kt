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

import com.ollitert.llm.server.common.ErrorCategory
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrometheusRendererTest {

  @Before
  fun setUp() {
    // Reset ServerMetrics to a clean state before each test
    ServerMetrics.onServerStopped()
  }

  @After
  fun tearDown() {
    ServerMetrics.onServerStopped()
  }

  @Test
  fun renderContainsAllExpectedCounters() {
    val output = PrometheusRenderer.render()
    val expectedCounters = listOf(
      "ollitert_requests_total",
      "ollitert_prompt_tokens_total",
      "ollitert_generation_tokens_total",
      "ollitert_prompt_seconds_total",
      "ollitert_generation_seconds_total",
      "ollitert_errors_total",
      "ollitert_request_text_total",
      "ollitert_request_image_total",
      "ollitert_request_audio_total",
    )
    for (name in expectedCounters) {
      assertTrue("Missing HELP for $name", output.contains("# HELP $name "))
      assertTrue("Missing TYPE counter for $name", output.contains("# TYPE $name counter"))
      assertTrue("Missing value line for $name", output.contains("\n$name "))
    }
  }

  @Test
  fun renderContainsAllExpectedGauges() {
    val output = PrometheusRenderer.render()
    val expectedGauges = listOf(
      "ollitert_uptime_seconds",
      "ollitert_model_load_time_seconds",
      "ollitert_prompt_tokens_per_second",
      "ollitert_generation_tokens_per_second",
      "ollitert_generation_tokens_per_second_peak",
      "ollitert_time_to_first_token_ms",
      "ollitert_time_to_first_token_avg_ms",
      "ollitert_inter_token_latency_ms",
      "ollitert_request_latency_ms",
      "ollitert_request_latency_avg_ms",
      "ollitert_request_latency_peak_ms",
      "ollitert_context_utilization_percent",
      "ollitert_requests_processing",
    )
    for (name in expectedGauges) {
      assertTrue("Missing HELP for $name", output.contains("# HELP $name "))
      assertTrue("Missing TYPE gauge for $name", output.contains("# TYPE $name gauge"))
      assertTrue("Missing value line for $name", output.contains("\n$name "))
    }
  }

  @Test
  fun renderReflectsServerMetricsValues() {
    // Simulate some activity
    ServerMetrics.incrementRequestCount()
    ServerMetrics.incrementRequestCount()
    ServerMetrics.addTokens(150)
    ServerMetrics.addTokensIn(500)
    ServerMetrics.incrementErrorCount(ErrorCategory.INFERENCE)
    ServerMetrics.recordModality(hasImages = false, hasAudio = false) // text
    ServerMetrics.recordModality(hasImages = true, hasAudio = false) // image
    ServerMetrics.recordLatency(250)

    val output = PrometheusRenderer.render()

    assertTrue("Request count should be 2", output.contains("ollitert_requests_total 2"))
    assertTrue("Generated tokens should be 150", output.contains("ollitert_generation_tokens_total 150"))
    assertTrue("Input tokens should be 500", output.contains("ollitert_prompt_tokens_total 500"))
    assertTrue("Error count should be 1", output.contains("ollitert_errors_total 1"))
    assertTrue("Text requests should be 1", output.contains("ollitert_request_text_total 1"))
    assertTrue("Image requests should be 1", output.contains("ollitert_request_image_total 1"))
    assertTrue("Latency should be 250", output.contains("ollitert_request_latency_ms 250"))
  }

  @Test
  fun renderShowsZerosAfterReset() {
    ServerMetrics.incrementRequestCount()
    ServerMetrics.onServerStopped()

    val output = PrometheusRenderer.render()

    assertTrue("Request count should be 0 after reset", output.contains("ollitert_requests_total 0"))
    assertTrue("Uptime should be 0 after stop", output.contains("ollitert_uptime_seconds 0.0000"))
  }

  @Test
  fun renderComputesUptimeFromStartedAtMs() {
    ServerMetrics.onServerRunning("127.0.0.1")
    val startedAt = ServerMetrics.startedAtMs.value
    // Render with a fake "now" 10 seconds after start
    val output = PrometheusRenderer.render(nowMs = startedAt + 10_000)

    assertTrue("Uptime should be ~10 seconds", output.contains("ollitert_uptime_seconds 10.0000"))
  }

  @Test
  fun renderShowsInferringState() {
    ServerMetrics.onInferenceStarted()
    val outputInferring = PrometheusRenderer.render()
    assertTrue("Should show 1 when inferring", outputInferring.contains("ollitert_requests_processing 1"))

    ServerMetrics.onInferenceCompleted()
    val outputIdle = PrometheusRenderer.render()
    assertTrue("Should show 0 when idle", outputIdle.contains("ollitert_requests_processing 0"))
  }

  @Test
  fun renderAccumulatesCumulativeTimings() {
    // Two requests with prefill and decode timings
    ServerMetrics.recordInferenceMetrics(
      inputTokens = 100, outputTokens = 50, ttfbMs = 200, generationMs = 1000,
    )
    ServerMetrics.recordInferenceMetrics(
      inputTokens = 80, outputTokens = 30, ttfbMs = 150, generationMs = 800,
    )

    val output = PrometheusRenderer.render()

    // Total prefill: 200 + 150 = 350ms = 0.35s
    assertTrue("Cumulative prefill should be 0.35s", output.contains("ollitert_prompt_seconds_total 0.3500"))
    // Total decode: 1000 + 800 = 1800ms = 1.8s
    assertTrue("Cumulative decode should be 1.8s", output.contains("ollitert_generation_seconds_total 1.8000"))
  }

  @Test
  fun renderUsesPrometheusExpositionFormat() {
    val output = PrometheusRenderer.render()
    val lines = output.lines()

    // Every metric should follow the pattern: HELP, TYPE, value
    var i = 0
    while (i < lines.size) {
      val line = lines[i]
      if (line.startsWith("# HELP ")) {
        assertTrue("TYPE line should follow HELP", i + 1 < lines.size && lines[i + 1].startsWith("# TYPE "))
        assertTrue("Value line should follow TYPE", i + 2 < lines.size && !lines[i + 2].startsWith("#"))
        i += 3
      } else {
        i++
      }
    }
  }

  @Test
  fun contentTypeIsCorrect() {
    assertTrue(
      "Content type should be Prometheus v0.0.4",
      PrometheusRenderer.CONTENT_TYPE.contains("text/plain"),
    )
    assertTrue(
      "Content type should include version",
      PrometheusRenderer.CONTENT_TYPE.contains("version=0.0.4"),
    )
  }

  @Test
  fun renderContainsLabeledErrorCounters() {
    val output = PrometheusRenderer.render()
    assertTrue("Missing HELP for labeled errors", output.contains("# HELP ollitert_errors_by_category_total "))
    assertTrue("Missing TYPE for labeled errors", output.contains("# TYPE ollitert_errors_by_category_total counter"))
    assertTrue("Missing model_load label", output.contains("""ollitert_errors_by_category_total{category="model_load"}"""))
    assertTrue("Missing inference label", output.contains("""ollitert_errors_by_category_total{category="inference"}"""))
    assertTrue("Missing network label", output.contains("""ollitert_errors_by_category_total{category="network"}"""))
    assertTrue("Missing system label", output.contains("""ollitert_errors_by_category_total{category="system"}"""))
  }

  @Test
  fun renderLabeledErrorCountersReflectIncrements() {
    ServerMetrics.incrementErrorCount(ErrorCategory.MODEL_LOAD)
    ServerMetrics.incrementErrorCount(ErrorCategory.MODEL_LOAD)
    ServerMetrics.incrementErrorCount(ErrorCategory.INFERENCE)
    ServerMetrics.incrementErrorCount(ErrorCategory.NETWORK)
    ServerMetrics.incrementErrorCount(ErrorCategory.SYSTEM)
    ServerMetrics.incrementErrorCount(ErrorCategory.SYSTEM)
    ServerMetrics.incrementErrorCount(ErrorCategory.SYSTEM)

    val output = PrometheusRenderer.render()
    assertTrue("model_load should be 2", output.contains("""ollitert_errors_by_category_total{category="model_load"} 2"""))
    assertTrue("inference should be 1", output.contains("""ollitert_errors_by_category_total{category="inference"} 1"""))
    assertTrue("network should be 1", output.contains("""ollitert_errors_by_category_total{category="network"} 1"""))
    assertTrue("system should be 3", output.contains("""ollitert_errors_by_category_total{category="system"} 3"""))
  }

  @Test
  fun renderLabeledErrorCountersResetToZero() {
    ServerMetrics.incrementErrorCount(ErrorCategory.INFERENCE)
    ServerMetrics.onServerStopped()

    val output = PrometheusRenderer.render()
    assertTrue("model_load should be 0", output.contains("""ollitert_errors_by_category_total{category="model_load"} 0"""))
    assertTrue("inference should be 0", output.contains("""ollitert_errors_by_category_total{category="inference"} 0"""))
    assertTrue("network should be 0", output.contains("""ollitert_errors_by_category_total{category="network"} 0"""))
    assertTrue("system should be 0", output.contains("""ollitert_errors_by_category_total{category="system"} 0"""))
  }

  @Test
  fun noMetricNameContainsHyphen() {
    // Prometheus metric names must match [a-zA-Z_:][a-zA-Z0-9_:]*
    val output = PrometheusRenderer.render()
    val valueLinePattern = Regex("""^([a-zA-Z_:][a-zA-Z0-9_:]*)\s""", RegexOption.MULTILINE)
    val metricNames = valueLinePattern.findAll(output).map { it.groupValues[1] }.toList()
    assertTrue("Should find metric names", metricNames.isNotEmpty())
    for (name in metricNames) {
      assertFalse("Metric name '$name' contains hyphen (invalid)", name.contains("-"))
      assertTrue("Metric name '$name' has invalid characters", name.matches(Regex("[a-zA-Z_:][a-zA-Z0-9_:]*")))
    }
  }

  // ── Memory gauge metrics ─────────────────────────────────────────────────

  @Test
  fun renderContainsMemoryGauges() {
    val output = PrometheusRenderer.render()
    assertTrue("should contain native heap gauge", output.contains("ollitert_memory_native_heap_bytes"))
    assertTrue("should contain app heap gauge", output.contains("ollitert_memory_app_heap_used_bytes"))
    assertTrue("should contain app pss gauge", output.contains("ollitert_memory_app_total_pss_bytes"))
    assertTrue("should contain device avail gauge", output.contains("ollitert_memory_device_available_bytes"))
    assertTrue("should contain device total gauge", output.contains("ollitert_memory_device_total_bytes"))
  }

  @Test
  fun renderReflectsMemorySnapshotValues() {
    ServerMetrics.updateMemorySnapshot(
      nativeHeapBytes = 1234567,
      appHeapUsedBytes = 2345678,
      appTotalPssBytes = 3456789,
      deviceAvailRamBytes = 4000000000,
      deviceTotalRamBytes = 8000000000,
    )
    val output = PrometheusRenderer.render()
    assertTrue("native heap value", output.contains("ollitert_memory_native_heap_bytes 1234567"))
    assertTrue("app heap value", output.contains("ollitert_memory_app_heap_used_bytes 2345678"))
    assertTrue("app pss value", output.contains("ollitert_memory_app_total_pss_bytes 3456789"))
    assertTrue("device avail value", output.contains("ollitert_memory_device_available_bytes 4000000000"))
    assertTrue("device total value", output.contains("ollitert_memory_device_total_bytes 8000000000"))
  }

  // ── Idle unloaded gauge ──────────────────────────────────────────────────

  @Test
  fun renderShowsIdleUnloadedZeroByDefault() {
    val output = PrometheusRenderer.render()
    assertTrue("should default to 0", output.contains("ollitert_model_idle_unloaded 0"))
  }

  @Test
  fun renderShowsIdleUnloadedState() {
    ServerMetrics.onModelIdleUnloaded()
    val output = PrometheusRenderer.render()
    assertTrue("should show 1 when idle unloaded", output.contains("ollitert_model_idle_unloaded 1"))
  }
}
