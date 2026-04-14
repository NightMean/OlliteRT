package com.ollitert.llm.server.service

import com.ollitert.llm.server.common.ErrorCategory
import com.ollitert.llm.server.ui.navigation.ServerStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive tests for [ServerMetrics] singleton.
 * Covers all public functions and StateFlow properties.
 *
 * Note: [ServerMetricsUpdateStateTest] already covers [ServerMetrics.setAvailableUpdate]
 * and its persistence across server stop/start cycles. This file tests everything else.
 */
class ServerMetricsTest {

  @Before
  fun setUp() {
    ServerMetrics.onServerStopped()
  }

  @After
  fun tearDown() {
    ServerMetrics.setAvailableUpdate(null, null)
    ServerMetrics.onServerStopped()
  }

  // ── incrementRequestCount() ──────────────────────────────────────────────

  @Test
  fun incrementRequestCountSingle() {
    ServerMetrics.incrementRequestCount()
    assertEquals(1L, ServerMetrics.requestCount.value)
  }

  @Test
  fun incrementRequestCountAccumulates() {
    repeat(5) { ServerMetrics.incrementRequestCount() }
    assertEquals(5L, ServerMetrics.requestCount.value)
  }

  // ── addTokens() ──────────────────────────────────────────────────────────

  @Test
  fun addTokensAccumulates() {
    ServerMetrics.addTokens(100)
    ServerMetrics.addTokens(50)
    assertEquals(150L, ServerMetrics.tokensGenerated.value)
  }

  // ── addTokensIn() ────────────────────────────────────────────────────────

  @Test
  fun addTokensInAccumulates() {
    ServerMetrics.addTokensIn(200)
    ServerMetrics.addTokensIn(300)
    assertEquals(500L, ServerMetrics.tokensIn.value)
  }

  // ── recordLatency() ──────────────────────────────────────────────────────

  @Test
  fun recordLatencySetsLastValue() {
    ServerMetrics.recordLatency(100)
    assertEquals(100L, ServerMetrics.lastLatencyMs.value)
  }

  @Test
  fun recordLatencyTracksPeak() {
    ServerMetrics.recordLatency(100)
    ServerMetrics.recordLatency(200)
    ServerMetrics.recordLatency(50)
    assertEquals(200L, ServerMetrics.peakLatencyMs.value)
  }

  @Test
  fun recordLatencyPeakOnlyIncreases() {
    ServerMetrics.recordLatency(200)
    ServerMetrics.recordLatency(100)
    assertEquals(200L, ServerMetrics.peakLatencyMs.value)
    // Last value should still update
    assertEquals(100L, ServerMetrics.lastLatencyMs.value)
  }

  @Test
  fun recordLatencyCalculatesAverage() {
    ServerMetrics.recordLatency(100)
    ServerMetrics.recordLatency(200)
    // Average of 100 and 200 = 150 (integer division: 300/2)
    assertEquals(150L, ServerMetrics.avgLatencyMs.value)
  }

  @Test
  fun recordLatencyAverageThreeValues() {
    ServerMetrics.recordLatency(100)
    ServerMetrics.recordLatency(200)
    ServerMetrics.recordLatency(300)
    // Average of 100, 200, 300 = 200 (integer division: 600/3)
    assertEquals(200L, ServerMetrics.avgLatencyMs.value)
  }

  // ── recordModality() ─────────────────────────────────────────────────────

  @Test
  fun recordModalityTextOnly() {
    ServerMetrics.recordModality(hasImages = false, hasAudio = false)
    assertEquals(1L, ServerMetrics.textRequests.value)
    assertEquals(0L, ServerMetrics.imageRequests.value)
    assertEquals(0L, ServerMetrics.audioRequests.value)
  }

  @Test
  fun recordModalityWithImages() {
    ServerMetrics.recordModality(hasImages = true, hasAudio = false)
    assertEquals(0L, ServerMetrics.textRequests.value)
    assertEquals(1L, ServerMetrics.imageRequests.value)
    assertEquals(0L, ServerMetrics.audioRequests.value)
  }

  @Test
  fun recordModalityWithAudio() {
    ServerMetrics.recordModality(hasImages = false, hasAudio = true)
    assertEquals(0L, ServerMetrics.textRequests.value)
    assertEquals(0L, ServerMetrics.imageRequests.value)
    assertEquals(1L, ServerMetrics.audioRequests.value)
  }

  @Test
  fun recordModalityImageTakesPriorityOverAudio() {
    // When both images and audio are present, images should win (when block evaluates images first)
    ServerMetrics.recordModality(hasImages = true, hasAudio = true)
    assertEquals(1L, ServerMetrics.imageRequests.value)
    assertEquals(0L, ServerMetrics.audioRequests.value)
  }

  @Test
  fun recordModalityAccumulatesAcrossRequests() {
    ServerMetrics.recordModality(hasImages = false, hasAudio = false)
    ServerMetrics.recordModality(hasImages = true, hasAudio = false)
    ServerMetrics.recordModality(hasImages = false, hasAudio = true)
    ServerMetrics.recordModality(hasImages = false, hasAudio = false)
    assertEquals(2L, ServerMetrics.textRequests.value)
    assertEquals(1L, ServerMetrics.imageRequests.value)
    assertEquals(1L, ServerMetrics.audioRequests.value)
  }

  // ── incrementErrorCount() ────────────────────────────────────────────────

  @Test
  fun incrementErrorCountNoCategory() {
    ServerMetrics.incrementErrorCount()
    assertEquals(1L, ServerMetrics.errorCount.value)
    // Category counters should remain zero
    assertEquals(0L, ServerMetrics.modelLoadErrors.value)
    assertEquals(0L, ServerMetrics.inferenceErrors.value)
    assertEquals(0L, ServerMetrics.networkErrors.value)
    assertEquals(0L, ServerMetrics.systemErrors.value)
  }

  @Test
  fun incrementErrorCountModelLoad() {
    ServerMetrics.incrementErrorCount(ErrorCategory.MODEL_LOAD)
    assertEquals(1L, ServerMetrics.errorCount.value)
    assertEquals(1L, ServerMetrics.modelLoadErrors.value)
  }

  @Test
  fun incrementErrorCountInference() {
    ServerMetrics.incrementErrorCount(ErrorCategory.INFERENCE)
    assertEquals(1L, ServerMetrics.errorCount.value)
    assertEquals(1L, ServerMetrics.inferenceErrors.value)
  }

  @Test
  fun incrementErrorCountNetwork() {
    ServerMetrics.incrementErrorCount(ErrorCategory.NETWORK)
    assertEquals(1L, ServerMetrics.errorCount.value)
    assertEquals(1L, ServerMetrics.networkErrors.value)
  }

  @Test
  fun incrementErrorCountSystem() {
    ServerMetrics.incrementErrorCount(ErrorCategory.SYSTEM)
    assertEquals(1L, ServerMetrics.errorCount.value)
    assertEquals(1L, ServerMetrics.systemErrors.value)
  }

  @Test
  fun incrementErrorCountMixedCategoriesAccumulate() {
    ServerMetrics.incrementErrorCount()
    ServerMetrics.incrementErrorCount(ErrorCategory.INFERENCE)
    ServerMetrics.incrementErrorCount(ErrorCategory.NETWORK)
    // Aggregate should be 3 (1 uncategorized + 2 categorized)
    assertEquals(3L, ServerMetrics.errorCount.value)
    assertEquals(1L, ServerMetrics.inferenceErrors.value)
    assertEquals(1L, ServerMetrics.networkErrors.value)
    assertEquals(0L, ServerMetrics.modelLoadErrors.value)
    assertEquals(0L, ServerMetrics.systemErrors.value)
  }

  // ── recordTtfb() ─────────────────────────────────────────────────────────

  @Test
  fun recordTtfbSetsLastValue() {
    ServerMetrics.recordTtfb(50)
    assertEquals(50L, ServerMetrics.lastTtfbMs.value)
  }

  @Test
  fun recordTtfbIgnoresZero() {
    ServerMetrics.recordTtfb(100)
    ServerMetrics.recordTtfb(0)
    // Should still be the first value since 0 is ignored
    assertEquals(100L, ServerMetrics.lastTtfbMs.value)
  }

  @Test
  fun recordTtfbIgnoresNegative() {
    ServerMetrics.recordTtfb(100)
    ServerMetrics.recordTtfb(-5)
    assertEquals(100L, ServerMetrics.lastTtfbMs.value)
  }

  @Test
  fun recordTtfbCalculatesAverage() {
    ServerMetrics.recordTtfb(100)
    ServerMetrics.recordTtfb(200)
    assertEquals(150L, ServerMetrics.avgTtfbMs.value)
  }

  // ── recordInferenceMetrics() ─────────────────────────────────────────────

  @Test
  fun recordInferenceMetricsDecodeSpeed() {
    // 50 output tokens in 1000ms = 50.0 tokens/sec
    ServerMetrics.recordInferenceMetrics(
      inputTokens = 100,
      outputTokens = 50,
      ttfbMs = 200,
      generationMs = 1000,
    )
    assertEquals(50.0, ServerMetrics.lastDecodeSpeed.value, 0.01)
  }

  @Test
  fun recordInferenceMetricsPrefillSpeed() {
    // 100 input tokens in 200ms = 500.0 tokens/sec
    ServerMetrics.recordInferenceMetrics(
      inputTokens = 100,
      outputTokens = 50,
      ttfbMs = 200,
      generationMs = 1000,
    )
    assertEquals(500.0, ServerMetrics.lastPrefillSpeed.value, 0.01)
  }

  @Test
  fun recordInferenceMetricsInterTokenLatency() {
    // 50 tokens in 1000ms generation time → ITL = 1000 / (50 - 1) ≈ 20.41ms
    ServerMetrics.recordInferenceMetrics(
      inputTokens = 100,
      outputTokens = 50,
      ttfbMs = 200,
      generationMs = 1000,
    )
    assertEquals(20.41, ServerMetrics.lastItlMs.value, 0.01)
  }

  @Test
  fun recordInferenceMetricsContextUtilization() {
    // 500 input tokens out of 1000 max = 50%
    ServerMetrics.recordInferenceMetrics(
      inputTokens = 500,
      outputTokens = 50,
      ttfbMs = 200,
      generationMs = 1000,
      maxContextTokens = 1000,
    )
    assertEquals(50.0, ServerMetrics.lastContextUtilization.value, 0.01)
  }

  @Test
  fun recordInferenceMetricsContextUtilizationNotSetWhenMaxIsZero() {
    ServerMetrics.recordInferenceMetrics(
      inputTokens = 500,
      outputTokens = 50,
      ttfbMs = 200,
      generationMs = 1000,
      maxContextTokens = 0,
    )
    assertEquals(0.0, ServerMetrics.lastContextUtilization.value, 0.01)
  }

  @Test
  fun recordInferenceMetricsSkipsDecodeSpeedWhenOutputIsZero() {
    ServerMetrics.recordInferenceMetrics(
      inputTokens = 100,
      outputTokens = 0,
      ttfbMs = 200,
      generationMs = 1000,
    )
    assertEquals(0.0, ServerMetrics.lastDecodeSpeed.value, 0.01)
  }

  @Test
  fun recordInferenceMetricsSkipsDecodeSpeedWhenGenerationIsZero() {
    ServerMetrics.recordInferenceMetrics(
      inputTokens = 100,
      outputTokens = 50,
      ttfbMs = 200,
      generationMs = 0,
    )
    assertEquals(0.0, ServerMetrics.lastDecodeSpeed.value, 0.01)
  }

  @Test
  fun recordInferenceMetricsPeakDecodeSpeedTracksHighest() {
    // First request: 50 t/s
    ServerMetrics.recordInferenceMetrics(
      inputTokens = 100, outputTokens = 50, ttfbMs = 200, generationMs = 1000,
    )
    // Second request: 100 t/s (faster)
    ServerMetrics.recordInferenceMetrics(
      inputTokens = 100, outputTokens = 100, ttfbMs = 200, generationMs = 1000,
    )
    // Third request: 25 t/s (slower)
    ServerMetrics.recordInferenceMetrics(
      inputTokens = 100, outputTokens = 25, ttfbMs = 200, generationMs = 1000,
    )
    assertEquals(100.0, ServerMetrics.peakDecodeSpeed.value, 0.01)
  }

  @Test
  fun recordInferenceMetricsAccumulatesCumulativeTimings() {
    ServerMetrics.recordInferenceMetrics(
      inputTokens = 100, outputTokens = 50, ttfbMs = 200, generationMs = 1000,
    )
    ServerMetrics.recordInferenceMetrics(
      inputTokens = 100, outputTokens = 50, ttfbMs = 300, generationMs = 2000,
    )
    assertEquals(500L, ServerMetrics.totalPrefillMs) // 200 + 300
    assertEquals(3000L, ServerMetrics.totalDecodeMs) // 1000 + 2000
  }

  @Test
  fun recordInferenceMetricsSkipsItlWhenOnlyOneOutputToken() {
    // ITL requires outputTokens > 1 (divides by outputTokens - 1)
    ServerMetrics.recordInferenceMetrics(
      inputTokens = 100, outputTokens = 1, ttfbMs = 200, generationMs = 1000,
    )
    assertEquals(0.0, ServerMetrics.lastItlMs.value, 0.01)
  }

  // ── onInferenceStarted() / onInferenceCompleted() ────────────────────────

  @Test
  fun inferenceStateToggles() {
    assertFalse(ServerMetrics.isInferring.value)
    ServerMetrics.onInferenceStarted()
    assertTrue(ServerMetrics.isInferring.value)
    ServerMetrics.onInferenceCompleted()
    assertFalse(ServerMetrics.isInferring.value)
  }

  // ── onModelIdleUnloaded() / onModelReloadedFromIdle() ────────────────────

  @Test
  fun idleUnloadStateToggles() {
    assertFalse(ServerMetrics.isIdleUnloaded.value)
    ServerMetrics.onModelIdleUnloaded()
    assertTrue(ServerMetrics.isIdleUnloaded.value)
    ServerMetrics.onModelReloadedFromIdle()
    assertFalse(ServerMetrics.isIdleUnloaded.value)
  }

  // ── updateMemorySnapshot() ───────────────────────────────────────────────

  @Test
  fun updateMemorySnapshotSetsAllFlows() {
    ServerMetrics.updateMemorySnapshot(
      nativeHeapBytes = 1_000_000,
      appHeapUsedBytes = 2_000_000,
      appTotalPssBytes = 3_000_000,
      deviceAvailRamBytes = 4_000_000_000,
      deviceTotalRamBytes = 8_000_000_000,
    )
    assertEquals(1_000_000L, ServerMetrics.nativeHeapBytes.value)
    assertEquals(2_000_000L, ServerMetrics.appHeapUsedBytes.value)
    assertEquals(3_000_000L, ServerMetrics.appTotalPssBytes.value)
    assertEquals(4_000_000_000L, ServerMetrics.deviceAvailRamBytes.value)
    assertEquals(8_000_000_000L, ServerMetrics.deviceTotalRamBytes.value)
  }

  @Test
  fun updateMemorySnapshotWithZeros() {
    // Set non-zero first, then zero — verify flows update to 0
    ServerMetrics.updateMemorySnapshot(100, 200, 300, 400, 500)
    ServerMetrics.updateMemorySnapshot(0, 0, 0, 0, 0)
    assertEquals(0L, ServerMetrics.nativeHeapBytes.value)
    assertEquals(0L, ServerMetrics.appHeapUsedBytes.value)
    assertEquals(0L, ServerMetrics.appTotalPssBytes.value)
    assertEquals(0L, ServerMetrics.deviceAvailRamBytes.value)
    assertEquals(0L, ServerMetrics.deviceTotalRamBytes.value)
  }

  // ── Simple setters ───────────────────────────────────────────────────────

  @Test
  fun setActiveModelSize() {
    ServerMetrics.setActiveModelSize(4_000_000_000)
    assertEquals(4_000_000_000, ServerMetrics.activeModelSize.value)
  }

  @Test
  fun setActiveAccelerator() {
    ServerMetrics.setActiveAccelerator("GPU")
    assertEquals("GPU", ServerMetrics.activeAccelerator.value)
  }

  @Test
  fun setActiveAcceleratorNull() {
    ServerMetrics.setActiveAccelerator("GPU")
    ServerMetrics.setActiveAccelerator(null)
    assertNull(ServerMetrics.activeAccelerator.value)
  }

  @Test
  fun setThinkingEnabled() {
    ServerMetrics.setThinkingEnabled(true)
    assertTrue(ServerMetrics.thinkingEnabled.value)
    ServerMetrics.setThinkingEnabled(false)
    assertFalse(ServerMetrics.thinkingEnabled.value)
  }

  @Test
  fun recordModelLoadTime() {
    ServerMetrics.recordModelLoadTime(1500)
    assertEquals(1500L, ServerMetrics.modelLoadTimeMs.value)
  }

  // ── Server lifecycle ─────────────────────────────────────────────────────

  @Test
  fun onServerStartingSetsLoadingState() {
    ServerMetrics.onServerStarting(port = 9000, modelName = "gemma-2b")
    assertEquals(ServerStatus.LOADING, ServerMetrics.status.value)
    assertEquals(9000, ServerMetrics.port.value)
    assertEquals("gemma-2b", ServerMetrics.activeModelName.value)
    assertTrue(ServerMetrics.loadingStartedAtMs.value > 0)
    assertNull(ServerMetrics.lastError.value)
  }

  @Test
  fun onServerRunningSetsRunningState() {
    ServerMetrics.onServerRunning("0.0.0.0:8000")
    assertEquals(ServerStatus.RUNNING, ServerMetrics.status.value)
    assertEquals("0.0.0.0:8000", ServerMetrics.bindAddress.value)
    assertTrue(ServerMetrics.startedAtMs.value > 0)
    assertEquals(0L, ServerMetrics.loadingStartedAtMs.value)
    assertNull(ServerMetrics.lastError.value)
    assertFalse(ServerMetrics.isInferring.value)
    assertFalse(ServerMetrics.isIdleUnloaded.value)
  }

  @Test
  fun onServerStoppedResetsCounters() {
    // Set up some state
    ServerMetrics.onServerRunning("0.0.0.0:8000")
    ServerMetrics.incrementRequestCount()
    ServerMetrics.addTokens(100)
    ServerMetrics.addTokensIn(200)
    ServerMetrics.recordLatency(500)
    ServerMetrics.recordModality(hasImages = true, hasAudio = false)
    ServerMetrics.incrementErrorCount(ErrorCategory.INFERENCE)
    ServerMetrics.recordTtfb(50)
    ServerMetrics.recordInferenceMetrics(100, 50, 200, 1000, 4000)
    ServerMetrics.setActiveModelSize(1_000_000)
    ServerMetrics.setActiveAccelerator("GPU")
    ServerMetrics.setThinkingEnabled(true)
    ServerMetrics.recordModelLoadTime(2000)
    ServerMetrics.onInferenceStarted()
    ServerMetrics.onModelIdleUnloaded()
    ServerMetrics.updateMemorySnapshot(100, 200, 300, 400, 500)

    // Stop resets everything
    ServerMetrics.onServerStopped()

    assertEquals(ServerStatus.STOPPED, ServerMetrics.status.value)
    assertNull(ServerMetrics.activeModelName.value)
    assertNull(ServerMetrics.bindAddress.value)
    assertEquals(0L, ServerMetrics.startedAtMs.value)
    assertEquals(0L, ServerMetrics.requestCount.value)
    assertEquals(0L, ServerMetrics.tokensGenerated.value)
    assertEquals(0L, ServerMetrics.tokensIn.value)
    assertEquals(0L, ServerMetrics.lastLatencyMs.value)
    assertEquals(0L, ServerMetrics.peakLatencyMs.value)
    assertEquals(0L, ServerMetrics.avgLatencyMs.value)
    assertEquals(0L, ServerMetrics.textRequests.value)
    assertEquals(0L, ServerMetrics.imageRequests.value)
    assertEquals(0L, ServerMetrics.audioRequests.value)
    assertEquals(0L, ServerMetrics.errorCount.value)
    assertEquals(0L, ServerMetrics.modelLoadErrors.value)
    assertEquals(0L, ServerMetrics.inferenceErrors.value)
    assertEquals(0L, ServerMetrics.networkErrors.value)
    assertEquals(0L, ServerMetrics.systemErrors.value)
    assertEquals(0L, ServerMetrics.lastTtfbMs.value)
    assertEquals(0L, ServerMetrics.avgTtfbMs.value)
    assertEquals(0.0, ServerMetrics.lastDecodeSpeed.value, 0.01)
    assertEquals(0.0, ServerMetrics.peakDecodeSpeed.value, 0.01)
    assertEquals(0.0, ServerMetrics.lastPrefillSpeed.value, 0.01)
    assertEquals(0.0, ServerMetrics.lastItlMs.value, 0.01)
    assertEquals(0.0, ServerMetrics.lastContextUtilization.value, 0.01)
    assertEquals(0L, ServerMetrics.totalPrefillMs)
    assertEquals(0L, ServerMetrics.totalDecodeMs)
    assertEquals(0L, ServerMetrics.modelLoadTimeMs.value)
    assertEquals(0L, ServerMetrics.loadingStartedAtMs.value)
    assertEquals(0L, ServerMetrics.activeModelSize.value)
    assertNull(ServerMetrics.activeAccelerator.value)
    assertFalse(ServerMetrics.thinkingEnabled.value)
    assertNull(ServerMetrics.lastError.value)
    assertFalse(ServerMetrics.isInferring.value)
    assertFalse(ServerMetrics.isIdleUnloaded.value)
    assertEquals(0L, ServerMetrics.nativeHeapBytes.value)
    assertEquals(0L, ServerMetrics.appHeapUsedBytes.value)
    assertEquals(0L, ServerMetrics.appTotalPssBytes.value)
    assertEquals(0L, ServerMetrics.deviceAvailRamBytes.value)
    assertEquals(0L, ServerMetrics.deviceTotalRamBytes.value)
  }

  @Test
  fun onServerErrorSetsErrorState() {
    ServerMetrics.onServerError("Port already in use")
    assertEquals(ServerStatus.ERROR, ServerMetrics.status.value)
    assertEquals("Port already in use", ServerMetrics.lastError.value)
    assertEquals(0L, ServerMetrics.startedAtMs.value)
    assertEquals(0L, ServerMetrics.loadingStartedAtMs.value)
  }

  @Test
  fun onServerErrorClearsLoadingTimestamp() {
    ServerMetrics.onServerStarting(port = 8000, modelName = "gemma")
    assertTrue(ServerMetrics.loadingStartedAtMs.value > 0)
    ServerMetrics.onServerError("OOM")
    assertEquals(0L, ServerMetrics.loadingStartedAtMs.value)
  }

  @Test
  fun onServerErrorWithNullMessage() {
    ServerMetrics.onServerError()
    assertEquals(ServerStatus.ERROR, ServerMetrics.status.value)
    assertNull(ServerMetrics.lastError.value)
  }
}
