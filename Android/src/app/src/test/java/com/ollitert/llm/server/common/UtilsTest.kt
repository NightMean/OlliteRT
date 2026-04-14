package com.ollitert.llm.server.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tests for pure utility functions in Utils.kt that have no Android dependencies.
 * Functions requiring Context (Wifi, Bitmap, Uri) are excluded.
 */
class UtilsTest {

  // ── cleanUpMediapipeTaskErrorMessage() ────────────────────────────────────

  @Test
  fun cleanUpMediapipeTaskErrorMessageTrimsTrace() {
    val msg = "Model failed to load=== Source Location Trace: file.cc:123\nmore stack data"
    assertEquals("Model failed to load", cleanUpMediapipeTaskErrorMessage(msg))
  }

  @Test
  fun cleanUpMediapipeTaskErrorMessageNoTraceReturnsUnchanged() {
    val msg = "Some normal error message"
    assertEquals(msg, cleanUpMediapipeTaskErrorMessage(msg))
  }

  @Test
  fun cleanUpMediapipeTaskErrorMessageEmptyString() {
    assertEquals("", cleanUpMediapipeTaskErrorMessage(""))
  }

  @Test
  fun cleanUpMediapipeTaskErrorMessageTraceAtStart() {
    val msg = "=== Source Location Trace: everything after"
    assertEquals("", cleanUpMediapipeTaskErrorMessage(msg))
  }

  @Test
  fun cleanUpMediapipeTaskErrorMessagePreservesTextBeforeTrace() {
    val msg = "Error: context overflow (6579 >= 4000)=== Source Location Trace: ..."
    assertEquals("Error: context overflow (6579 >= 4000)", cleanUpMediapipeTaskErrorMessage(msg))
  }

  // ── processLlmResponse() ──────────────────────────────────────────────────

  @Test
  fun processLlmResponseReplacesLiteralBackslashN() {
    assertEquals("line1\nline2", processLlmResponse("line1\\nline2"))
  }

  @Test
  fun processLlmResponseMultipleOccurrences() {
    assertEquals("a\nb\nc", processLlmResponse("a\\nb\\nc"))
  }

  @Test
  fun processLlmResponseNoBackslashN() {
    assertEquals("hello world", processLlmResponse("hello world"))
  }

  @Test
  fun processLlmResponseEmptyString() {
    assertEquals("", processLlmResponse(""))
  }

  @Test
  fun processLlmResponsePreservesRealNewlines() {
    // Real newlines should pass through unchanged
    assertEquals("a\nb", processLlmResponse("a\nb"))
  }

  @Test
  fun processLlmResponseConsecutiveLiteralNewlines() {
    assertEquals("\n\n", processLlmResponse("\\n\\n"))
  }

  // ── calculatePeakAmplitude() ──────────────────────────────────────────────

  @Test
  fun calculatePeakAmplitudeSilence() {
    // All zeros → peak = 0
    val buffer = ByteArray(8) // 4 samples of silence
    assertEquals(0, calculatePeakAmplitude(buffer, buffer.size))
  }

  @Test
  fun calculatePeakAmplitudePositiveSample() {
    // Single sample with value 1000 (little-endian 16-bit)
    val bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
    bb.putShort(1000)
    assertEquals(1000, calculatePeakAmplitude(bb.array(), 2))
  }

  @Test
  fun calculatePeakAmplitudeNegativeSample() {
    // Negative sample → abs(-500) = 500
    val bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
    bb.putShort(-500)
    assertEquals(500, calculatePeakAmplitude(bb.array(), 2))
  }

  @Test
  fun calculatePeakAmplitudeFindsMaxAcrossMultipleSamples() {
    // 3 samples: 100, -300, 200 → peak = 300
    val bb = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
    bb.putShort(100)
    bb.putShort(-300)
    bb.putShort(200)
    assertEquals(300, calculatePeakAmplitude(bb.array(), 6))
  }

  @Test
  fun calculatePeakAmplitudeRespectsPartialBuffer() {
    // Buffer has 3 samples but bytesRead covers only the first 2
    val bb = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
    bb.putShort(100)
    bb.putShort(200)
    bb.putShort(999) // should be ignored
    assertEquals(200, calculatePeakAmplitude(bb.array(), 4))
  }

  @Test
  fun calculatePeakAmplitudeMaxShortValue() {
    val bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
    bb.putShort(Short.MAX_VALUE) // 32767
    assertEquals(32767, calculatePeakAmplitude(bb.array(), 2))
  }

  @Test
  fun calculatePeakAmplitudeMinShortValue() {
    // Short.MIN_VALUE = -32768 → abs = 32768
    val bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
    bb.putShort(Short.MIN_VALUE)
    // .toInt() before abs() avoids Short overflow
    assertTrue("peak should handle Short.MIN_VALUE", calculatePeakAmplitude(bb.array(), 2) > 0)
  }
}
