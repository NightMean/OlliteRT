package com.ollitert.llm.server.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteExtensionsTest {

  @Test
  fun `bytesToGb - converts 1 GB exactly`() {
    val oneGb = 1024L * 1024L * 1024L
    assertEquals(1.0f, oneGb.bytesToGb(), 0.001f)
  }

  @Test
  fun `bytesToGb - converts fractional GB`() {
    val halfGb = 512L * 1024L * 1024L
    assertEquals(0.5f, halfGb.bytesToGb(), 0.001f)
  }

  @Test
  fun `bytesToGb - zero bytes`() {
    assertEquals(0.0f, 0L.bytesToGb(), 0.001f)
  }

  @Test
  fun `bytesToMb - converts 1 MB exactly`() {
    val oneMb = 1024L * 1024L
    assertEquals(1L, oneMb.bytesToMb())
  }

  @Test
  fun `bytesToMb - truncates fractional MB`() {
    val almostTwoMb = 2L * 1024L * 1024L - 1
    assertEquals(1L, almostTwoMb.bytesToMb())
  }

  @Test
  fun `bytesToMb - zero bytes`() {
    assertEquals(0L, 0L.bytesToMb())
  }

  @Test
  fun `bytesToMb - large value`() {
    val fiveHundredMb = 500L * 1024L * 1024L
    assertEquals(500L, fiveHundredMb.bytesToMb())
  }
}
