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

import com.ollitert.llm.server.data.model.RequestLogEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestLogDatabaseWriterTest {

  private class FakeDao : RequestLogDao {
    val rows = linkedMapOf<String, RequestLogEntity>()
    val writes = mutableListOf<String>()
    var beforeUpsert: suspend (RequestLogEntity) -> Unit = {}

    override suspend fun upsert(entity: RequestLogEntity) {
      beforeUpsert(entity)
      writes += entity.toEntry().let { if (it.isPending) "pending" else "terminal" }
      rows[entity.id] = entity
    }

    override suspend fun upsertAll(entities: List<RequestLogEntity>) {
      entities.forEach { upsert(it) }
    }

    override suspend fun getRecent(limit: Int): List<RequestLogEntity> =
      rows.values.sortedByDescending { it.timestamp }.take(limit)

    override suspend fun deleteAll() {
      writes += "clear"
      rows.clear()
    }

    override suspend fun deleteOlderThan(olderThanMs: Long) {
      rows.entries.removeIf { it.value.timestamp < olderThanMs }
    }

    override suspend fun pruneToCount(maxCount: Int) {
      val idsToKeep = rows.values.sortedByDescending { it.timestamp }.take(maxCount).map { it.id }.toSet()
      rows.keys.retainAll(idsToKeep)
    }

    override suspend fun count(): Int = rows.size
  }

  @Test
  fun terminalUpdateCannotOvertakeInitialInsert() = runTest {
    val dao = FakeDao()
    val firstWriteStarted = CompletableDeferred<Unit>()
    val releaseFirstWrite = CompletableDeferred<Unit>()
    dao.beforeUpsert = { entity ->
      if (entity.toEntry().isPending) {
        firstWriteStarted.complete(Unit)
        releaseFirstWrite.await()
      }
    }
    val failures = mutableListOf<Throwable>()
    val writer = RequestLogDatabaseWriter(dao, backgroundScope) { _, error -> failures += error }
    val pending = RequestLogEntity.fromEntry(
      RequestLogEntry(id = "request", method = "POST", path = "/v1/chat/completions", isPending = true)
    )
    val terminal = RequestLogEntity.fromEntry(pending.toEntry().copy(isPending = false, responseBody = "done"))

    writer.enqueue("initial insert") { upsert(pending) }
    writer.enqueue("terminal update") { upsert(terminal) }
    firstWriteStarted.await()
    releaseFirstWrite.complete(Unit)
    writer.awaitIdle()

    assertEquals(listOf("pending", "terminal"), dao.writes)
    assertFalse(dao.rows.getValue("request").toEntry().isPending)
    assertEquals("done", dao.rows.getValue("request").toEntry().responseBody)
    assertTrue(failures.isEmpty())
  }

  @Test
  fun clearStaysOrderedBetweenEarlierAndLaterWrites() = runTest {
    val dao = FakeDao()
    val writer = RequestLogDatabaseWriter(dao, backgroundScope) { _, error -> throw error }
    val oldEntry = RequestLogEntity.fromEntry(RequestLogEntry(id = "old", method = "GET", path = "/old"))
    val newEntry = RequestLogEntity.fromEntry(RequestLogEntry(id = "new", method = "GET", path = "/new"))

    writer.enqueue("old insert") { upsert(oldEntry) }
    writer.enqueue("clear") { deleteAll() }
    writer.enqueue("new insert") { upsert(newEntry) }
    writer.awaitIdle()

    assertEquals(listOf("terminal", "clear", "terminal"), dao.writes)
    assertEquals(setOf("new"), dao.rows.keys)
  }

  @Test
  fun failedOperationDoesNotStopLaterWrites() = runTest {
    val dao = FakeDao()
    val failures = mutableListOf<String>()
    val writer = RequestLogDatabaseWriter(dao, backgroundScope) { operation, _ -> failures += operation }
    val entry = RequestLogEntity.fromEntry(RequestLogEntry(id = "survivor", method = "GET", path = "/health"))

    writer.enqueue("broken") { error("database failure") }
    writer.enqueue("following insert") { upsert(entry) }
    writer.awaitIdle()

    assertEquals(listOf("broken"), failures)
    assertEquals(setOf("survivor"), dao.rows.keys)
  }
}
