package com.ollitert.llm.server.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RequestLogStoreTest {

  /** Record all persistence callback invocations for verification. */
  private class TestCallback : RequestLogStore.PersistenceCallback {
    val added = mutableListOf<RequestLogEntry>()
    val updated = mutableListOf<Pair<RequestLogEntry, Boolean>>() // entry to isTerminal
    var clearCount = 0

    override fun onEntryAdded(entry: RequestLogEntry) { added.add(entry) }
    override fun onEntryUpdated(entry: RequestLogEntry, isTerminal: Boolean) {
      updated.add(entry to isTerminal)
    }
    override fun onEntriesCleared() { clearCount++ }
  }

  private fun entry(id: String, isPending: Boolean = false, isCancelled: Boolean = false) =
    RequestLogEntry(
      id = id,
      method = "POST",
      path = "/v1/chat/completions",
      isPending = isPending,
      isCancelled = isCancelled,
    )

  @Before
  fun setUp() {
    // Reset singleton state before each test
    RequestLogStore.clear()
    RequestLogStore.setPersistenceCallback(null)
    RequestLogStore.setMaxEntries(100)
  }

  @After
  fun tearDown() {
    RequestLogStore.clear()
    RequestLogStore.setPersistenceCallback(null)
    RequestLogStore.setMaxEntries(100)
  }

  // --- Basic add/update/clear ---

  @Test
  fun addInsertsNewestFirst() {
    RequestLogStore.add(entry("a"))
    RequestLogStore.add(entry("b"))
    val ids = RequestLogStore.entries.value.map { it.id }
    assertEquals(listOf("b", "a"), ids)
  }

  @Test
  fun addRespectsMaxEntries() {
    RequestLogStore.setMaxEntries(3)
    RequestLogStore.add(entry("1"))
    RequestLogStore.add(entry("2"))
    RequestLogStore.add(entry("3"))
    RequestLogStore.add(entry("4"))
    val ids = RequestLogStore.entries.value.map { it.id }
    assertEquals("oldest entry should be evicted", listOf("4", "3", "2"), ids)
  }

  @Test
  fun updateModifiesEntryById() {
    RequestLogStore.add(entry("x", isPending = true))
    RequestLogStore.update("x") { it.copy(isPending = false, latencyMs = 500) }
    val e = RequestLogStore.entries.value.first()
    assertFalse("entry should no longer be pending", e.isPending)
    assertEquals(500L, e.latencyMs)
  }

  @Test
  fun updateIgnoresUnknownId() {
    RequestLogStore.add(entry("x"))
    RequestLogStore.update("nonexistent") { it.copy(latencyMs = 999) }
    assertEquals("entry count should be unchanged", 1, RequestLogStore.entries.value.size)
    assertEquals("original entry should be untouched", 0L, RequestLogStore.entries.value[0].latencyMs)
  }

  @Test
  fun clearRemovesAllEntries() {
    RequestLogStore.add(entry("a"))
    RequestLogStore.add(entry("b"))
    RequestLogStore.clear()
    assertTrue("entries should be empty after clear", RequestLogStore.entries.value.isEmpty())
  }

  @Test
  fun loadEntriesReplacesCurrentList() {
    RequestLogStore.add(entry("old"))
    val loaded = listOf(entry("db-1"), entry("db-2"), entry("db-3"))
    RequestLogStore.loadEntries(loaded)
    assertEquals(3, RequestLogStore.entries.value.size)
    assertEquals("db-1", RequestLogStore.entries.value[0].id)
  }

  // --- Dynamic maxEntries ---

  @Test
  fun setMaxEntriesUpdatesLimit() {
    RequestLogStore.setMaxEntries(500)
    assertEquals(500, RequestLogStore.maxEntries)
  }

  @Test
  fun setMaxEntrisTrimsExcessImmediately() {
    // Add 5 entries with a limit of 100 (newest first: e4, e3, e2, e1, e0)
    repeat(5) { RequestLogStore.add(entry("e$it")) }
    assertEquals(5, RequestLogStore.entries.value.size)

    // Lower the limit to 2 — oldest entries are trimmed immediately
    RequestLogStore.setMaxEntries(2)
    assertEquals("should trim to new max", 2, RequestLogStore.entries.value.size)
    assertEquals("newest entries should survive", "e4", RequestLogStore.entries.value[0].id)
    assertEquals("e3", RequestLogStore.entries.value[1].id)
  }

  // --- Persistence callback: onEntryAdded ---

  @Test
  fun addNotifiesCallbackWithEntry() {
    val cb = TestCallback()
    RequestLogStore.setPersistenceCallback(cb)
    val e = entry("p1")
    RequestLogStore.add(e)
    assertEquals(1, cb.added.size)
    assertEquals("p1", cb.added[0].id)
  }

  @Test
  fun addDoesNotCrashWithNoCallback() {
    RequestLogStore.setPersistenceCallback(null)
    RequestLogStore.add(entry("safe")) // should not throw
    assertEquals(1, RequestLogStore.entries.value.size)
  }

  // --- Persistence callback: onEntryUpdated + isTerminal ---

  @Test
  fun updateSignalsTerminalWhenPendingBecomesFalse() {
    val cb = TestCallback()
    RequestLogStore.add(entry("r1", isPending = true))
    RequestLogStore.setPersistenceCallback(cb)

    RequestLogStore.update("r1") { it.copy(isPending = false, responseBody = "done") }

    assertEquals(1, cb.updated.size)
    assertTrue("should be terminal (pending→complete)", cb.updated[0].second)
  }

  @Test
  fun updateSignalsTerminalWhenCancelledBecomesTrue() {
    val cb = TestCallback()
    RequestLogStore.add(entry("r2", isPending = true))
    RequestLogStore.setPersistenceCallback(cb)

    RequestLogStore.update("r2") { it.copy(isCancelled = true) }

    assertEquals(1, cb.updated.size)
    assertTrue("should be terminal (cancelled)", cb.updated[0].second)
  }

  @Test
  fun updateSignalsNonTerminalForPartialTextUpdate() {
    val cb = TestCallback()
    RequestLogStore.add(entry("r3", isPending = true))
    RequestLogStore.setPersistenceCallback(cb)

    // Simulate streaming partialText update — entry stays pending
    RequestLogStore.update("r3") { it.copy(partialText = "streaming tokens...") }

    assertEquals(1, cb.updated.size)
    assertFalse("partialText-only update should not be terminal", cb.updated[0].second)
  }

  @Test
  fun updateSignalsNonTerminalWhenNothingChanges() {
    val cb = TestCallback()
    RequestLogStore.add(entry("r4"))
    RequestLogStore.setPersistenceCallback(cb)

    // No pending/cancelled state change
    RequestLogStore.update("r4") { it.copy(latencyMs = 100) }

    assertEquals(1, cb.updated.size)
    assertFalse("no state transition = not terminal", cb.updated[0].second)
  }

  @Test
  fun updateDoesNotNotifyCallbackForUnknownId() {
    val cb = TestCallback()
    RequestLogStore.setPersistenceCallback(cb)
    RequestLogStore.update("ghost") { it.copy(latencyMs = 999) }
    assertEquals(0, cb.updated.size)
  }

  @Test
  fun multipleStreamingUpdatesFollowedByCompletionProducesOneTerminal() {
    val cb = TestCallback()
    RequestLogStore.add(entry("stream", isPending = true))
    RequestLogStore.setPersistenceCallback(cb)

    // Simulate ~5 streaming updates (all non-terminal)
    repeat(5) { i ->
      RequestLogStore.update("stream") { it.copy(partialText = "token $i") }
    }
    // Then complete (terminal)
    RequestLogStore.update("stream") { it.copy(isPending = false, responseBody = "final") }

    val terminalCount = cb.updated.count { it.second }
    val nonTerminalCount = cb.updated.count { !it.second }
    assertEquals("should have exactly 1 terminal update", 1, terminalCount)
    assertEquals("should have 5 non-terminal updates", 5, nonTerminalCount)
  }

  // --- Persistence callback: onEntriesCleared ---

  @Test
  fun clearNotifiesCallback() {
    val cb = TestCallback()
    RequestLogStore.setPersistenceCallback(cb)
    RequestLogStore.add(entry("a"))
    RequestLogStore.clear()
    assertEquals(1, cb.clearCount)
  }

  @Test
  fun clearNotifiesCallbackEvenWhenEmpty() {
    val cb = TestCallback()
    RequestLogStore.setPersistenceCallback(cb)
    RequestLogStore.clear()
    assertEquals(1, cb.clearCount)
  }

  // --- loadEntries does NOT trigger callback ---

  @Test
  fun loadEntriesDoesNotTriggerAddCallback() {
    val cb = TestCallback()
    RequestLogStore.setPersistenceCallback(cb)
    RequestLogStore.loadEntries(listOf(entry("db-1"), entry("db-2")))
    assertEquals("loadEntries should not fire onEntryAdded", 0, cb.added.size)
  }

  // --- addEvent ---

  @Test
  fun addEventCreatesEventEntry() {
    RequestLogStore.addEvent("Model loaded", category = EventCategory.MODEL, modelName = "gemma")
    val e = RequestLogStore.entries.value.first()
    assertEquals("EVENT", e.method)
    assertEquals("Model loaded", e.path)
    assertEquals(EventCategory.MODEL, e.eventCategory)
    assertEquals("gemma", e.modelName)
  }

  @Test
  fun addEventTriggersCallbackLikeRegularAdd() {
    val cb = TestCallback()
    RequestLogStore.setPersistenceCallback(cb)
    RequestLogStore.addEvent("test event")
    assertEquals(1, cb.added.size)
    assertEquals("EVENT", cb.added[0].method)
  }

  // --- cancelRequest ---

  @Test
  fun cancelRequestSetsCancelledByUserAndInvokesCallback() {
    val cb = TestCallback()
    var callbackInvoked = false
    RequestLogStore.add(entry("cancel-me", isPending = true))
    RequestLogStore.registerCancellation("cancel-me") { callbackInvoked = true }
    RequestLogStore.setPersistenceCallback(cb)

    RequestLogStore.cancelRequest("cancel-me")

    assertTrue("cancellation callback should be invoked", callbackInvoked)
    val e = RequestLogStore.entries.value.first()
    assertTrue("cancelledByUser should be true", e.cancelledByUser)
    // cancelRequest sets cancelledByUser but NOT isCancelled, so the update is non-terminal.
    // The actual isCancelled flag is set later by the inference cancellation callback.
    assertEquals("should fire one update", 1, cb.updated.size)
    assertFalse("cancelledByUser alone is not terminal (isCancelled unchanged)", cb.updated[0].second)
  }
}
