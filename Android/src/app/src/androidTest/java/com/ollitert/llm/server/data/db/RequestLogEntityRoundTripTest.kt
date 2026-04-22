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

package com.ollitert.llm.server.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogEntry
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RequestLogEntityRoundTripTest {

  private lateinit var db: OlliteDatabase
  private lateinit var dao: RequestLogDao
  private val moshi = Moshi.Builder().build()

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    db = Room.inMemoryDatabaseBuilder(context, OlliteDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    dao = db.requestLogDao()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun fullFieldRoundTrip() = runTest {
    val original = RequestLogEntry(
      id = "test-123",
      timestamp = 1700000000000,
      method = "POST",
      path = "/v1/chat/completions",
      requestBody = """{"messages":[{"role":"user","content":"hello"}]}""",
      originalRequestBodySize = 150,
      responseBody = """{"choices":[{"message":{"content":"Hi!"}}]}""",
      statusCode = 200,
      tokens = 42,
      latencyMs = 350,
      isStreaming = true,
      modelName = "gemma-3-4b",
      clientIp = "192.168.1.100",
      level = LogLevel.INFO,
      isPending = false,
      isThinking = true,
      isCompacted = true,
      compactionDetails = "base64 image replaced with size placeholder",
      compactedPrompt = "You are a helpful assistant.",
      isCancelled = false,
      cancelledByUser = false,
      partialText = null,
      eventCategory = EventCategory.GENERAL,
      inputTokenEstimate = 120,
      maxContextTokens = 8192,
      isExactTokenCount = true,
      ignoredClientParams = "temperature=0.9",
      ttfbMs = 85,
      decodeSpeed = 24.5,
      prefillSpeed = 180.0,
      itlMs = 40.8,
    )

    val entity = RequestLogEntity.fromEntry(original, moshi)
    dao.upsert(entity)
    val loaded = dao.getRecent(1).single()
    val restored = loaded.toEntry(moshi)

    assertEquals(original.id, restored.id)
    assertEquals(original.timestamp, restored.timestamp)
    assertEquals(original.method, restored.method)
    assertEquals(original.path, restored.path)
    assertEquals(original.requestBody, restored.requestBody)
    assertEquals(original.originalRequestBodySize, restored.originalRequestBodySize)
    assertEquals(original.responseBody, restored.responseBody)
    assertEquals(original.statusCode, restored.statusCode)
    assertEquals(original.tokens, restored.tokens)
    assertEquals(original.latencyMs, restored.latencyMs)
    assertEquals(original.isStreaming, restored.isStreaming)
    assertEquals(original.modelName, restored.modelName)
    assertEquals(original.clientIp, restored.clientIp)
    assertEquals(original.level, restored.level)
    assertEquals(original.isPending, restored.isPending)
    assertEquals(original.isThinking, restored.isThinking)
    assertEquals(original.isCompacted, restored.isCompacted)
    assertEquals(original.compactionDetails, restored.compactionDetails)
    assertEquals(original.compactedPrompt, restored.compactedPrompt)
    assertEquals(original.isCancelled, restored.isCancelled)
    assertEquals(original.cancelledByUser, restored.cancelledByUser)
    assertEquals(original.partialText, restored.partialText)
    assertEquals(original.eventCategory, restored.eventCategory)
    assertEquals(original.inputTokenEstimate, restored.inputTokenEstimate)
    assertEquals(original.maxContextTokens, restored.maxContextTokens)
    assertEquals(original.isExactTokenCount, restored.isExactTokenCount)
    assertEquals(original.ignoredClientParams, restored.ignoredClientParams)
    assertEquals(original.ttfbMs, restored.ttfbMs)
    assertEquals(original.decodeSpeed, restored.decodeSpeed, 0.001)
    assertEquals(original.prefillSpeed, restored.prefillSpeed, 0.001)
    assertEquals(original.itlMs, restored.itlMs, 0.001)
  }

  @Test
  fun nullableFieldsRoundTrip() = runTest {
    val original = RequestLogEntry(
      id = "null-test",
      method = "GET",
      path = "/ping",
      modelName = null,
      requestBody = null,
      responseBody = null,
      clientIp = null,
      compactionDetails = null,
      compactedPrompt = null,
      partialText = null,
      ignoredClientParams = null,
    )

    val entity = RequestLogEntity.fromEntry(original, moshi)
    dao.upsert(entity)
    val restored = dao.getRecent(1).single().toEntry(moshi)

    assertNull(restored.modelName)
    assertNull(restored.requestBody)
    assertNull(restored.responseBody)
    assertNull(restored.clientIp)
    assertNull(restored.compactionDetails)
    assertNull(restored.compactedPrompt)
    assertNull(restored.partialText)
    assertNull(restored.ignoredClientParams)
  }

  @Test
  fun corruptExtrasJsonFallsBackToDefaults() = runTest {
    val entity = RequestLogEntity(
      id = "corrupt",
      timestamp = 1000,
      method = "POST",
      path = "/v1/chat/completions",
      statusCode = 200,
      level = "INFO",
      modelName = null,
      eventCategory = "GENERAL",
      latencyMs = 0,
      isStreaming = false,
      inputTokenEstimate = 0,
      maxContextTokens = 0,
      extras = "NOT VALID JSON {{{",
    )
    dao.upsert(entity)
    val restored = dao.getRecent(1).single().toEntry(moshi)

    assertEquals("corrupt", restored.id)
    assertNull(restored.requestBody)
    assertEquals(0L, restored.tokens)
    assertEquals(0.0, restored.decodeSpeed, 0.0)
  }

  @Test
  fun unknownLogLevelFallsBackToInfo() = runTest {
    val entity = RequestLogEntity(
      id = "bad-level",
      timestamp = 1000,
      method = "GET",
      path = "/ping",
      statusCode = 200,
      level = "NONEXISTENT_LEVEL",
      modelName = null,
      eventCategory = "GENERAL",
      latencyMs = 0,
      isStreaming = false,
      inputTokenEstimate = 0,
      maxContextTokens = 0,
      extras = "{}",
    )
    dao.upsert(entity)
    val restored = dao.getRecent(1).single().toEntry(moshi)
    assertEquals(LogLevel.INFO, restored.level)
  }

  @Test
  fun unknownEventCategoryFallsBackToGeneral() = runTest {
    val entity = RequestLogEntity(
      id = "bad-category",
      timestamp = 1000,
      method = "GET",
      path = "/ping",
      statusCode = 200,
      level = "INFO",
      modelName = null,
      eventCategory = "NONEXISTENT_CATEGORY",
      latencyMs = 0,
      isStreaming = false,
      inputTokenEstimate = 0,
      maxContextTokens = 0,
      extras = "{}",
    )
    dao.upsert(entity)
    val restored = dao.getRecent(1).single().toEntry(moshi)
    assertEquals(EventCategory.GENERAL, restored.eventCategory)
  }

  @Test
  fun allLogLevelsRoundTrip() = runTest {
    for (level in LogLevel.entries) {
      val entry = RequestLogEntry(
        id = "level-${level.name}",
        method = "GET",
        path = "/ping",
        level = level,
      )
      val entity = RequestLogEntity.fromEntry(entry, moshi)
      dao.upsert(entity)
      val restored = dao.getRecent(100).first { it.id == "level-${level.name}" }.toEntry(moshi)
      assertEquals(level, restored.level)
    }
  }

  @Test
  fun allEventCategoriesRoundTrip() = runTest {
    for (cat in EventCategory.entries) {
      val entry = RequestLogEntry(
        id = "cat-${cat.name}",
        method = "EVENT",
        path = cat.name,
        eventCategory = cat,
      )
      val entity = RequestLogEntity.fromEntry(entry, moshi)
      dao.upsert(entity)
      val restored = dao.getRecent(100).first { it.id == "cat-${cat.name}" }.toEntry(moshi)
      assertEquals(cat, restored.eventCategory)
    }
  }
}
