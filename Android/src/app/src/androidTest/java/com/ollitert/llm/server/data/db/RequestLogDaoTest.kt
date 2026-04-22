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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RequestLogDaoTest {

  private lateinit var db: OlliteDatabase
  private lateinit var dao: RequestLogDao

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

  private fun entity(
    id: String,
    timestamp: Long = System.currentTimeMillis(),
    statusCode: Int = 200,
    level: String = "INFO",
    modelName: String? = null,
  ) = RequestLogEntity(
    id = id,
    timestamp = timestamp,
    method = "POST",
    path = "/v1/chat/completions",
    statusCode = statusCode,
    level = level,
    modelName = modelName,
    eventCategory = "GENERAL",
    latencyMs = 100,
    isStreaming = false,
    inputTokenEstimate = 50,
    maxContextTokens = 8192,
    extras = "{}",
  )

  @Test
  fun upsertAndCount() = runTest {
    dao.upsert(entity("a"))
    assertEquals(1, dao.count())
  }

  @Test
  fun upsertReplacesExistingById() = runTest {
    dao.upsert(entity("a", statusCode = 200))
    dao.upsert(entity("a", statusCode = 500))
    assertEquals(1, dao.count())
    val row = dao.getRecent(1).single()
    assertEquals(500, row.statusCode)
  }

  @Test
  fun upsertAllBulkInsert() = runTest {
    val entities = (1..10).map { entity("id-$it", timestamp = it.toLong()) }
    dao.upsertAll(entities)
    assertEquals(10, dao.count())
  }

  @Test
  fun getRecentReturnsNewestFirst() = runTest {
    dao.upsert(entity("old", timestamp = 1000))
    dao.upsert(entity("mid", timestamp = 2000))
    dao.upsert(entity("new", timestamp = 3000))
    val results = dao.getRecent(3)
    assertEquals(listOf("new", "mid", "old"), results.map { it.id })
  }

  @Test
  fun getRecentRespectsLimit() = runTest {
    (1..5).forEach { dao.upsert(entity("id-$it", timestamp = it.toLong())) }
    assertEquals(2, dao.getRecent(2).size)
  }

  @Test
  fun deleteAllRemovesEverything() = runTest {
    (1..3).forEach { dao.upsert(entity("id-$it")) }
    dao.deleteAll()
    assertEquals(0, dao.count())
  }

  @Test
  fun deleteOlderThanRemovesOnlyOldEntries() = runTest {
    dao.upsert(entity("old", timestamp = 1000))
    dao.upsert(entity("new", timestamp = 3000))
    dao.deleteOlderThan(2000)
    assertEquals(1, dao.count())
    assertEquals("new", dao.getRecent(1).single().id)
  }

  @Test
  fun pruneToCountKeepsNewest() = runTest {
    // SQL uses strict < (not <=), so the entry at OFFSET maxCount survives too.
    // pruneToCount(3) with unique timestamps keeps maxCount + 1 entries.
    (1..10).forEach { dao.upsert(entity("id-$it", timestamp = it * 1000L)) }
    dao.pruneToCount(3)
    val remaining = dao.getRecent(10)
    assertEquals(4, remaining.size)
    assertEquals(listOf("id-10", "id-9", "id-8", "id-7"), remaining.map { it.id })
  }

  @Test
  fun pruneToCountWithFewerEntriesThanMaxIsNoOp() = runTest {
    dao.upsert(entity("only-one"))
    dao.pruneToCount(100)
    assertEquals(1, dao.count())
  }

  @Test
  fun pruneToCountOnEmptyTableIsNoOp() = runTest {
    dao.pruneToCount(10)
    assertEquals(0, dao.count())
  }

  @Test
  fun deleteOlderThanBoundaryExcludesExactTimestamp() = runTest {
    dao.upsert(entity("exact", timestamp = 2000))
    dao.upsert(entity("older", timestamp = 1999))
    dao.deleteOlderThan(2000)
    assertEquals(1, dao.count())
    assertEquals("exact", dao.getRecent(1).single().id)
  }

  @Test
  fun pruneToCountWithDuplicateTimestampsKeepsAllAtBoundary() = runTest {
    // Entries at the boundary timestamp all survive (strict < doesn't delete equal)
    dao.upsert(entity("a", timestamp = 3000))
    dao.upsert(entity("b", timestamp = 2000))
    dao.upsert(entity("c", timestamp = 2000))
    dao.upsert(entity("d", timestamp = 2000))
    dao.upsert(entity("e", timestamp = 1000))
    dao.pruneToCount(1)
    // OFFSET 1 returns 2000; DELETE WHERE timestamp < 2000 keeps a(3000), b/c/d(2000) = 4
    val remaining = dao.getRecent(10)
    assertEquals(4, remaining.size)
    assertEquals("a", remaining.first().id)
  }

  @Test
  fun largeDatasetPruning() = runTest {
    val entities = (1..500).map { entity("id-$it", timestamp = it.toLong()) }
    dao.upsertAll(entities)
    dao.pruneToCount(50)
    // Same off-by-one: strict < keeps the boundary entry (OFFSET 50 = timestamp 450)
    assertEquals(51, dao.count())
    val newest = dao.getRecent(1).single()
    assertEquals("id-500", newest.id)
  }
}
