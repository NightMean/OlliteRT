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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [BlockingQueueInputStream] — an InputStream backed by a LinkedBlockingQueue.
 * Tests verify enqueue/read ordering, EOF semantics, cancel behavior, and multibyte UTF-8.
 */
class BlockingQueueInputStreamTest {

  // ── Single-byte read ─────────────────────────────────────────────────────

  @Test
  fun readReturnsBytesInOrder() {
    val stream = BlockingQueueInputStream()
    stream.enqueue("Hi")
    stream.finish()

    assertEquals('H'.code, stream.read())
    assertEquals('i'.code, stream.read())
  }

  @Test
  fun readReturnsMinusOneAfterFinish() {
    val stream = BlockingQueueInputStream()
    stream.enqueue("A")
    stream.finish()

    stream.read() // consume 'A'
    assertEquals(-1, stream.read())
  }

  @Test
  fun readAfterEofAlwaysReturnsMinusOne() {
    val stream = BlockingQueueInputStream()
    stream.finish()

    assertEquals(-1, stream.read())
    assertEquals(-1, stream.read()) // repeated reads still return -1
  }

  // ── Bulk read ────────────────────────────────────────────────────────────

  @Test
  fun readBulkReturnsChunkData() {
    val stream = BlockingQueueInputStream()
    stream.enqueue("Hello")
    stream.finish()

    val buf = ByteArray(10)
    val bytesRead = stream.read(buf, 0, buf.size)
    assertEquals(5, bytesRead)
    assertEquals("Hello", String(buf, 0, bytesRead, Charsets.UTF_8))
  }

  @Test
  fun readBulkReturnsMinusOneAfterEof() {
    val stream = BlockingQueueInputStream()
    stream.enqueue("AB")
    stream.finish()

    val buf = ByteArray(10)
    stream.read(buf, 0, buf.size) // consume "AB"
    assertEquals(-1, stream.read(buf, 0, buf.size))
  }

  // ── Multi-chunk ──────────────────────────────────────────────────────────

  @Test
  fun readSpansMultipleChunks() {
    val stream = BlockingQueueInputStream()
    stream.enqueue("AB")
    stream.enqueue("CD")
    stream.finish()

    val result = StringBuilder()
    var b: Int
    while (stream.read().also { b = it } != -1) {
      result.append(b.toChar())
    }
    assertEquals("ABCD", result.toString())
  }

  @Test
  fun readBulkSpansChunks() {
    val stream = BlockingQueueInputStream()
    stream.enqueue("AB")
    stream.enqueue("CD")
    stream.finish()

    // First read returns first chunk (2 bytes)
    val buf = ByteArray(10)
    val n1 = stream.read(buf, 0, buf.size)
    assertEquals(2, n1)
    assertEquals("AB", String(buf, 0, n1))

    // Second read returns second chunk (2 bytes)
    val n2 = stream.read(buf, 0, buf.size)
    assertEquals(2, n2)
    assertEquals("CD", String(buf, 0, n2))

    // Third read returns EOF
    assertEquals(-1, stream.read(buf, 0, buf.size))
  }

  // ── Finish / EOF semantics ───────────────────────────────────────────────

  @Test
  fun finishSignalsEof() {
    val stream = BlockingQueueInputStream()
    stream.enqueue("data")
    stream.finish()

    // Read all data
    val buf = ByteArray(10)
    stream.read(buf, 0, buf.size)
    // Next read should be EOF
    assertEquals(-1, stream.read())
  }

  // ── Cancel semantics ─────────────────────────────────────────────────────

  @Test
  fun cancelSetsFlagAndSignalsEof() {
    val stream = BlockingQueueInputStream()
    stream.enqueue("data")
    stream.cancel()

    assertTrue("isCancelled should be true", stream.isCancelled)
    // Read the pre-cancel data
    val buf = ByteArray(10)
    stream.read(buf, 0, buf.size)
    // After cancel, read returns -1 (EOF sentinel was placed)
    assertEquals(-1, stream.read())
  }

  @Test
  fun enqueueAfterCancelIsIgnored() {
    val stream = BlockingQueueInputStream()
    stream.cancel()
    stream.enqueue("should be ignored")

    // Only the EOF sentinel should be in the queue
    assertEquals(-1, stream.read())
  }

  @Test
  fun cancelUnblocksPendingRead() {
    val stream = BlockingQueueInputStream()
    val readCompleted = CountDownLatch(1)
    var readResult = 0

    // Start reader thread that blocks on empty queue
    val reader = Thread {
      readResult = stream.read()
      readCompleted.countDown()
    }
    reader.start()

    // Give the reader thread time to block on queue.take()
    Thread.sleep(50)
    assertFalse("reader should still be blocked", readCompleted.await(10, TimeUnit.MILLISECONDS))

    // Cancel should unblock the reader
    stream.cancel()
    assertTrue("reader should complete after cancel", readCompleted.await(1, TimeUnit.SECONDS))
    assertEquals(-1, readResult)
  }

  // ── UTF-8 multibyte ─────────────────────────────────────────────────────

  @Test
  fun enqueueHandlesUtf8Multibyte() {
    val stream = BlockingQueueInputStream()
    val original = "caf\u00e9 \u2603" // café ☃ (mix of 1-byte, 2-byte, and 3-byte UTF-8)
    stream.enqueue(original)
    stream.finish()

    val allBytes = mutableListOf<Byte>()
    var b: Int
    while (stream.read().also { b = it } != -1) {
      allBytes.add(b.toByte())
    }
    val reconstructed = String(allBytes.toByteArray(), Charsets.UTF_8)
    assertEquals(original, reconstructed)
  }

  @Test
  fun bulkReadHandlesUtf8Multibyte() {
    val stream = BlockingQueueInputStream()
    val original = "\u00f1\u00e9" // ñé (2-byte UTF-8 characters)
    stream.enqueue(original)
    stream.finish()

    val buf = ByteArray(20)
    val n = stream.read(buf, 0, buf.size)
    val reconstructed = String(buf, 0, n, Charsets.UTF_8)
    assertEquals(original, reconstructed)
  }

  // ── Initial state ────────────────────────────────────────────────────────

  @Test
  fun initialStateIsNotCancelled() {
    val stream = BlockingQueueInputStream()
    assertFalse(stream.isCancelled)
  }
}
