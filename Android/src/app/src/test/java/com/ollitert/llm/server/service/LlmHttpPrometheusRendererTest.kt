package com.ollitert.llm.server.service

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LlmHttpPrometheusRendererTest {

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
    val output = LlmHttpPrometheusRenderer.render()
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
    val output = LlmHttpPrometheusRenderer.render()
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
    ServerMetrics.incrementErrorCount()
    ServerMetrics.recordModality(hasImages = false, hasAudio = false) // text
    ServerMetrics.recordModality(hasImages = true, hasAudio = false) // image
    ServerMetrics.recordLatency(250)

    val output = LlmHttpPrometheusRenderer.render()

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

    val output = LlmHttpPrometheusRenderer.render()

    assertTrue("Request count should be 0 after reset", output.contains("ollitert_requests_total 0"))
    assertTrue("Uptime should be 0 after stop", output.contains("ollitert_uptime_seconds 0.0000"))
  }

  @Test
  fun renderComputesUptimeFromStartedAtMs() {
    ServerMetrics.onServerRunning("127.0.0.1")
    val startedAt = ServerMetrics.startedAtMs.value
    // Render with a fake "now" 10 seconds after start
    val output = LlmHttpPrometheusRenderer.render(nowMs = startedAt + 10_000)

    assertTrue("Uptime should be ~10 seconds", output.contains("ollitert_uptime_seconds 10.0000"))
  }

  @Test
  fun renderShowsInferringState() {
    ServerMetrics.onInferenceStarted()
    val outputInferring = LlmHttpPrometheusRenderer.render()
    assertTrue("Should show 1 when inferring", outputInferring.contains("ollitert_requests_processing 1"))

    ServerMetrics.onInferenceCompleted()
    val outputIdle = LlmHttpPrometheusRenderer.render()
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

    val output = LlmHttpPrometheusRenderer.render()

    // Total prefill: 200 + 150 = 350ms = 0.35s
    assertTrue("Cumulative prefill should be 0.35s", output.contains("ollitert_prompt_seconds_total 0.3500"))
    // Total decode: 1000 + 800 = 1800ms = 1.8s
    assertTrue("Cumulative decode should be 1.8s", output.contains("ollitert_generation_seconds_total 1.8000"))
  }

  @Test
  fun renderUsesPrometheusExpositionFormat() {
    val output = LlmHttpPrometheusRenderer.render()
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
      LlmHttpPrometheusRenderer.CONTENT_TYPE.contains("text/plain"),
    )
    assertTrue(
      "Content type should include version",
      LlmHttpPrometheusRenderer.CONTENT_TYPE.contains("version=0.0.4"),
    )
  }

  @Test
  fun noMetricNameContainsHyphen() {
    // Prometheus metric names must match [a-zA-Z_:][a-zA-Z0-9_:]*
    val output = LlmHttpPrometheusRenderer.render()
    val valueLinePattern = Regex("""^([a-zA-Z_:][a-zA-Z0-9_:]*)\s""", RegexOption.MULTILINE)
    val metricNames = valueLinePattern.findAll(output).map { it.groupValues[1] }.toList()
    assertTrue("Should find metric names", metricNames.isNotEmpty())
    for (name in metricNames) {
      assertFalse("Metric name '$name' contains hyphen (invalid)", name.contains("-"))
      assertTrue("Metric name '$name' has invalid characters", name.matches(Regex("[a-zA-Z_:][a-zA-Z0-9_:]*")))
    }
  }
}
