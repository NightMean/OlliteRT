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

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.StringWriter
import java.io.Writer

class KtorSseWriterTest {

  @Test
  fun emitWritesTextAndFlushes() = runBlocking {
    val underlying = StringWriter()
    val writer = KtorSseWriterImpl(underlying)

    writer.emit("data: hello\n\n")

    assertEquals("data: hello\n\n", underlying.toString())
    assertFalse(writer.isCancelled)
  }

  @Test
  fun multipleEmitsAppendInOrder() = runBlocking {
    val underlying = StringWriter()
    val writer = KtorSseWriterImpl(underlying)

    writer.emit("data: first\n\n")
    writer.emit("data: second\n\n")

    assertEquals("data: first\n\ndata: second\n\n", underlying.toString())
  }

  @Test
  fun emitHandlesUtf8Multibyte() = runBlocking {
    val underlying = StringWriter()
    val writer = KtorSseWriterImpl(underlying)

    val text = "data: café ☃\n\n"
    writer.emit(text)

    assertEquals(text, underlying.toString())
    assertFalse(writer.isCancelled)
  }

  @Test
  fun writeFailureSetsCancelled() = runBlocking {
    val failingWriter = object : Writer() {
      override fun write(cbuf: CharArray, off: Int, len: Int) = throw IOException("client disconnected")
      override fun flush() {}
      override fun close() {}
    }
    val writer = KtorSseWriterImpl(failingWriter)

    assertFalse(writer.isCancelled)
    writer.emit("data: test\n\n")
    assertTrue(writer.isCancelled)
  }

  @Test
  fun flushFailureSetsCancelled() = runBlocking {
    val failOnFlush = object : Writer() {
      override fun write(cbuf: CharArray, off: Int, len: Int) {}
      override fun flush() = throw IOException("broken pipe")
      override fun close() {}
    }
    val writer = KtorSseWriterImpl(failOnFlush)

    writer.emit("data: test\n\n")
    assertTrue(writer.isCancelled)
  }

  @Test
  fun emitAfterCancelledStillSwallowsException() = runBlocking {
    val failingWriter = object : Writer() {
      override fun write(cbuf: CharArray, off: Int, len: Int) = throw IOException("disconnected")
      override fun flush() {}
      override fun close() {}
    }
    val writer = KtorSseWriterImpl(failingWriter)

    writer.emit("first")
    assertTrue(writer.isCancelled)
    writer.emit("second")
    assertTrue(writer.isCancelled)
  }

  @Test
  fun runtimeExceptionAlsoSetsCancelled() = runBlocking {
    val failingWriter = object : Writer() {
      override fun write(cbuf: CharArray, off: Int, len: Int) = throw RuntimeException("unexpected")
      override fun flush() {}
      override fun close() {}
    }
    val writer = KtorSseWriterImpl(failingWriter)

    assertFalse(writer.isCancelled)
    writer.emit("data: test\n\n")
    assertTrue(writer.isCancelled)
  }

  @Test
  fun initialStateIsNotCancelled() {
    val writer = KtorSseWriterImpl(StringWriter())
    assertFalse(writer.isCancelled)
  }

  @Test
  fun finishIsNoOp() = runBlocking {
    val underlying = StringWriter()
    val writer = KtorSseWriterImpl(underlying)

    writer.emit("data: hello\n\n")
    writer.finish()

    assertEquals("data: hello\n\n", underlying.toString())
    assertFalse(writer.isCancelled)
  }
}
