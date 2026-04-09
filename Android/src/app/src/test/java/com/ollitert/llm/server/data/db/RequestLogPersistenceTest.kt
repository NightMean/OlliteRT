package com.ollitert.llm.server.data.db

import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogEntry
import com.ollitert.llm.server.service.RequestLogStore
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests the persistence callback behavior by simulating the flow that
 * [RequestLogPersistence] implements: add→persist, update(terminal)→persist,
 * update(non-terminal)→skip, clear→wipe.
 *
 * We test the callback contract directly via [RequestLogStore] rather than
 * instantiating [RequestLogPersistence] (which needs Android Context for prefs).
 * This verifies the core logic: terminal detection, callback invocation timing,
 * and the write-behind contract (2 writes per request).
 */
class RequestLogPersistenceTest {

  /** In-memory fake DAO that records all operations. */
  private class FakeDao : RequestLogDao {
    val rows = mutableMapOf<String, RequestLogEntity>()
    var deleteAllCount = 0
    var deleteOlderThanCalls = mutableListOf<Long>()
    var pruneToCountCalls = mutableListOf<Int>()

    override suspend fun upsert(entity: RequestLogEntity) { rows[entity.id] = entity }
    override suspend fun upsertAll(entities: List<RequestLogEntity>) {
      entities.forEach { rows[it.id] = it }
    }
    override suspend fun getRecent(limit: Int): List<RequestLogEntity> =
      rows.values.sortedByDescending { it.timestamp }.take(limit)
    override suspend fun deleteAll() { rows.clear(); deleteAllCount++ }
    override suspend fun deleteOlderThan(olderThanMs: Long) {
      deleteOlderThanCalls.add(olderThanMs)
      rows.entries.removeIf { it.value.timestamp < olderThanMs }
    }
    override suspend fun pruneToCount(maxCount: Int) {
      pruneToCountCalls.add(maxCount)
      val sorted = rows.values.sortedByDescending { it.timestamp }
      if (sorted.size > maxCount) {
        val toRemove = sorted.drop(maxCount).map { it.id }
        toRemove.forEach { rows.remove(it) }
      }
    }
    override suspend fun count(): Int = rows.size
  }

  /**
   * A persistence callback that writes to the fake DAO synchronously.
   * Mirrors [RequestLogPersistence]'s logic without needing Android Context.
   */
  private class TestPersistence(
    private val dao: FakeDao,
    private val moshi: Moshi,
    var enabled: Boolean = true,
  ) : RequestLogStore.PersistenceCallback {

    override fun onEntryAdded(entry: RequestLogEntry) {
      if (!enabled) return
      runBlocking { dao.upsert(RequestLogEntity.fromEntry(entry, moshi)) }
    }

    override fun onEntryUpdated(entry: RequestLogEntry, isTerminal: Boolean) {
      if (!enabled || !isTerminal) return
      runBlocking { dao.upsert(RequestLogEntity.fromEntry(entry, moshi)) }
    }

    override fun onEntriesCleared() {
      if (!enabled) return
      runBlocking { dao.deleteAll() }
    }

    fun persistCurrentEntries() = runBlocking {
      val entries = RequestLogStore.entries.value
      dao.upsertAll(entries.map { RequestLogEntity.fromEntry(it, moshi) })
    }

    fun clearPersistedLogs() = runBlocking { dao.deleteAll() }

    fun prune(maxCount: Int, retentionMinutes: Long) = runBlocking {
      val cutoffMs = System.currentTimeMillis() - (retentionMinutes * 60_000L)
      dao.deleteOlderThan(cutoffMs)
      dao.pruneToCount(maxCount)
    }
  }

  private val moshi = Moshi.Builder().build()
  private lateinit var fakeDao: FakeDao
  private lateinit var persistence: TestPersistence

  private fun entry(id: String, isPending: Boolean = false, timestamp: Long = System.currentTimeMillis()) =
    RequestLogEntry(
      id = id,
      timestamp = timestamp,
      method = "POST",
      path = "/v1/chat/completions",
      isPending = isPending,
    )

  @Before
  fun setUp() {
    RequestLogStore.clear()
    RequestLogStore.setPersistenceCallback(null)
    RequestLogStore.setMaxEntries(100)
    fakeDao = FakeDao()
    persistence = TestPersistence(fakeDao, moshi)
    RequestLogStore.setPersistenceCallback(persistence)
  }

  @After
  fun tearDown() {
    RequestLogStore.clear()
    RequestLogStore.setPersistenceCallback(null)
    RequestLogStore.setMaxEntries(100)
  }

  // --- Write-behind: 2 writes per request ---

  @Test
  fun requestLifecycleProducesTwoDbWrites() {
    // 1. Create pending entry → persisted
    RequestLogStore.add(entry("req1", isPending = true))
    assertEquals("add should persist", 1, fakeDao.rows.size)

    // 2. Streaming updates → NOT persisted (non-terminal)
    repeat(5) { i ->
      RequestLogStore.update("req1") { it.copy(partialText = "token $i") }
    }
    // Still just 1 row (the initial add), upsert not called for streaming
    val entityAfterStreaming = fakeDao.rows["req1"]!!
    // The entity in the DB should still be the initial add (isPending=true in extras)
    val extrasAfterStreaming = moshi.adapter(RequestLogEntity.ExtrasJson::class.java)
      .fromJson(entityAfterStreaming.extras)!!
    assertTrue("DB entry should still be pending after streaming updates", extrasAfterStreaming.isPending)

    // 3. Complete (terminal) → persisted
    RequestLogStore.update("req1") { it.copy(isPending = false, responseBody = "done", latencyMs = 500) }
    val finalEntity = fakeDao.rows["req1"]!!
    val finalExtras = moshi.adapter(RequestLogEntity.ExtrasJson::class.java)
      .fromJson(finalEntity.extras)!!
    assertFalse("DB entry should be completed after terminal update", finalExtras.isPending)
    assertEquals("done", finalExtras.responseBody)
  }

  @Test
  fun cancelledRequestIsPersistedAsTerminal() {
    RequestLogStore.add(entry("cancel-me", isPending = true))
    RequestLogStore.update("cancel-me") { it.copy(isCancelled = true) }

    val entity = fakeDao.rows["cancel-me"]!!
    val extras = moshi.adapter(RequestLogEntity.ExtrasJson::class.java).fromJson(entity.extras)!!
    assertTrue("cancelled entry should be persisted", extras.isCancelled)
  }

  // --- Persistence disabled ---

  @Test
  fun disabledPersistenceDoesNotWrite() {
    persistence.enabled = false
    RequestLogStore.add(entry("should-not-persist"))
    assertEquals("should not write when disabled", 0, fakeDao.rows.size)
  }

  @Test
  fun disabledPersistenceDoesNotClearDb() {
    // Pre-populate DB
    runBlocking { fakeDao.upsert(RequestLogEntity.fromEntry(entry("existing"), moshi)) }
    persistence.enabled = false
    RequestLogStore.clear()
    assertEquals("should not clear DB when disabled", 1, fakeDao.rows.size)
  }

  // --- Clear ---

  @Test
  fun clearWipesDatabase() {
    RequestLogStore.add(entry("a"))
    RequestLogStore.add(entry("b"))
    assertEquals(2, fakeDao.rows.size)

    RequestLogStore.clear()
    assertEquals(0, fakeDao.rows.size)
    assertEquals(1, fakeDao.deleteAllCount)
  }

  // --- persistCurrentEntries (first-time enable sync) ---

  @Test
  fun persistCurrentEntriesBulkInsertsInMemoryLogs() {
    // Add entries without persistence
    persistence.enabled = false
    RequestLogStore.add(entry("mem-1"))
    RequestLogStore.add(entry("mem-2"))
    RequestLogStore.add(entry("mem-3"))
    assertEquals(0, fakeDao.rows.size)

    // "Enable" persistence and sync
    persistence.enabled = true
    persistence.persistCurrentEntries()
    assertEquals(3, fakeDao.rows.size)
    assertTrue(fakeDao.rows.containsKey("mem-1"))
    assertTrue(fakeDao.rows.containsKey("mem-2"))
    assertTrue(fakeDao.rows.containsKey("mem-3"))
  }

  // --- clearPersistedLogs (Settings button) ---

  @Test
  fun clearPersistedLogsWipesDbButNotMemory() {
    RequestLogStore.add(entry("a"))
    RequestLogStore.add(entry("b"))
    assertEquals(2, fakeDao.rows.size)
    assertEquals(2, RequestLogStore.entries.value.size)

    persistence.clearPersistedLogs()

    assertEquals("DB should be empty", 0, fakeDao.rows.size)
    assertEquals("in-memory should be untouched", 2, RequestLogStore.entries.value.size)
  }

  // --- Pruning ---

  @Test
  fun pruneByCountRemovesOldestEntries() {
    val now = System.currentTimeMillis()
    runBlocking {
      fakeDao.upsert(RequestLogEntity.fromEntry(entry("old", timestamp = now - 3000), moshi))
      fakeDao.upsert(RequestLogEntity.fromEntry(entry("mid", timestamp = now - 2000), moshi))
      fakeDao.upsert(RequestLogEntity.fromEntry(entry("new", timestamp = now - 1000), moshi))
    }
    assertEquals(3, fakeDao.rows.size)

    persistence.prune(maxCount = 2, retentionMinutes = 365L * 24 * 60)

    assertEquals(2, fakeDao.rows.size)
    assertFalse("oldest entry should be pruned", fakeDao.rows.containsKey("old"))
    assertTrue(fakeDao.rows.containsKey("mid"))
    assertTrue(fakeDao.rows.containsKey("new"))
  }

  @Test
  fun pruneByAgeRemovesExpiredEntries() {
    val now = System.currentTimeMillis()
    val twoDaysAgo = now - 2 * 86_400_000L
    val tenDaysAgo = now - 10 * 86_400_000L
    runBlocking {
      fakeDao.upsert(RequestLogEntity.fromEntry(entry("recent", timestamp = twoDaysAgo), moshi))
      fakeDao.upsert(RequestLogEntity.fromEntry(entry("expired", timestamp = tenDaysAgo), moshi))
    }

    persistence.prune(maxCount = 10000, retentionMinutes = 7L * 24 * 60)

    assertEquals(1, fakeDao.rows.size)
    assertTrue("recent entry should survive", fakeDao.rows.containsKey("recent"))
    assertFalse("expired entry should be pruned", fakeDao.rows.containsKey("expired"))
  }

  // --- loadEntries round-trip ---

  @Test
  fun loadFromDbPopulatesInMemoryStore() {
    val now = System.currentTimeMillis()
    runBlocking {
      fakeDao.upsert(RequestLogEntity.fromEntry(
        RequestLogEntry(
          id = "db-1", timestamp = now, method = "POST", path = "/test",
          requestBody = "body1", statusCode = 200, level = LogLevel.INFO,
        ),
        moshi,
      ))
      fakeDao.upsert(RequestLogEntity.fromEntry(
        RequestLogEntry(
          id = "db-2", timestamp = now - 1000, method = "GET", path = "/v1/models",
          statusCode = 200, level = LogLevel.INFO,
        ),
        moshi,
      ))
    }

    // Simulate startup load
    runBlocking {
      val entities = fakeDao.getRecent(100)
      val entries = entities.map { it.toEntry(moshi) }
      RequestLogStore.loadEntries(entries)
    }

    assertEquals(2, RequestLogStore.entries.value.size)
    assertEquals("db-1", RequestLogStore.entries.value[0].id) // newest first
    assertEquals("db-2", RequestLogStore.entries.value[1].id)
    assertEquals("body1", RequestLogStore.entries.value[0].requestBody)
  }

  // --- Event entries ---

  @Test
  fun eventEntriesArePersistedLikeRegularEntries() {
    RequestLogStore.addEvent("Model loaded", modelName = "gemma3")
    assertEquals(1, fakeDao.rows.size)
    val entity = fakeDao.rows.values.first()
    assertEquals("EVENT", entity.method)
    assertEquals("Model loaded", entity.path)
    assertEquals("gemma3", entity.modelName)
  }
}
