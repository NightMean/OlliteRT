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

import com.ollitert.llm.server.data.HARD_MAX_IN_MEMORY_ENTRIES
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

  @Test
  fun loadEntriesClampsStalePendingEntriesToCancelled() {
    val loaded = listOf(
      entry("ok", isPending = false),
      entry("stuck", isPending = true),
      entry("already-cancelled", isPending = false, isCancelled = true),
    )
    RequestLogStore.loadEntries(loaded)
    val entries = RequestLogStore.entries.value
    assertEquals(3, entries.size)
    // Non-pending entry is untouched
    assertFalse(entries[0].isPending)
    assertFalse(entries[0].isCancelled)
    // Stale pending entry is clamped to cancelled
    assertFalse("stale pending entry should have isPending=false", entries[1].isPending)
    assertTrue("stale pending entry should be marked cancelled", entries[1].isCancelled)
    // Already-cancelled entry is untouched
    assertFalse(entries[2].isPending)
    assertTrue(entries[2].isCancelled)
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

  // ── removeOlderThan() ────────────────────────────────────────────────────

  @Test
  fun removeOlderThanFiltersOldEntries() {
    val now = System.currentTimeMillis()
    RequestLogStore.add(RequestLogEntry(id = "old", method = "GET", path = "/", timestamp = now - 10_000))
    RequestLogStore.add(RequestLogEntry(id = "new", method = "GET", path = "/", timestamp = now))
    // Cutoff at now - 5000 → only "new" survives
    RequestLogStore.removeOlderThan(now - 5_000)
    val ids = RequestLogStore.entries.value.map { it.id }
    assertEquals(listOf("new"), ids)
  }

  @Test
  fun removeOlderThanKeepsExactCutoff() {
    val cutoff = 1000L
    RequestLogStore.add(RequestLogEntry(id = "exact", method = "GET", path = "/", timestamp = cutoff))
    RequestLogStore.add(RequestLogEntry(id = "before", method = "GET", path = "/", timestamp = cutoff - 1))
    RequestLogStore.removeOlderThan(cutoff)
    val ids = RequestLogStore.entries.value.map { it.id }
    assertEquals("entry at exact cutoff should survive (>= check)", listOf("exact"), ids)
  }

  @Test
  fun removeOlderThanEmptyStoreIsNoOp() {
    RequestLogStore.removeOlderThan(System.currentTimeMillis())
    assertTrue(RequestLogStore.entries.value.isEmpty())
  }

  @Test
  fun removeOlderThanDoesNotTriggerPersistenceCallback() {
    val cb = TestCallback()
    val now = System.currentTimeMillis()
    RequestLogStore.add(RequestLogEntry(id = "a", method = "GET", path = "/", timestamp = now - 10_000))
    RequestLogStore.setPersistenceCallback(cb)
    cb.added.clear() // ignore the add

    RequestLogStore.removeOlderThan(now)

    assertEquals("removeOlderThan should not fire onEntryUpdated", 0, cb.updated.size)
    assertEquals("removeOlderThan should not fire onEntriesCleared", 0, cb.clearCount)
  }

  // ── setMaxEntries(0) — hard ceiling mode ─────────────────────────────────

  @Test
  fun setMaxEntriesZeroUsesHardCeiling() {
    RequestLogStore.setMaxEntries(0)
    repeat(200) { RequestLogStore.add(entry("e$it")) }
    assertEquals("entries below hard ceiling should all survive", 200, RequestLogStore.entries.value.size)
  }

  @Test
  fun setMaxEntriesZeroDoesNotTrimExistingBelowCeiling() {
    repeat(10) { RequestLogStore.add(entry("e$it")) }
    RequestLogStore.setMaxEntries(0)
    assertEquals("existing entries below hard ceiling should survive", 10, RequestLogStore.entries.value.size)
  }

  @Test
  fun addRespectsHardCeilingWhenMaxIsZero() {
    RequestLogStore.setMaxEntries(0)
    repeat(5) { RequestLogStore.add(entry("e$it")) }
    assertEquals(5, RequestLogStore.entries.value.size)
    repeat(5) { RequestLogStore.add(entry("f$it")) }
    assertEquals(10, RequestLogStore.entries.value.size)
  }

  // ── updatePartialText() ──────────────────────────────────────────────────

  @Test
  fun updatePartialTextEmitsToFlow() {
    RequestLogStore.updatePartialText("entry-1", "streaming tokens...")
    val (id, text) = RequestLogStore.pendingPartialText.value
    assertEquals("entry-1", id)
    assertEquals("streaming tokens...", text)
  }

  @Test
  fun updatePartialTextDoesNotModifyEntriesList() {
    RequestLogStore.add(entry("a"))
    val sizeBefore = RequestLogStore.entries.value.size
    RequestLogStore.updatePartialText("a", "some text")
    assertEquals("entries list should be unchanged", sizeBefore, RequestLogStore.entries.value.size)
    // The entry in the list should NOT have partialText set
    assertEquals(null, RequestLogStore.entries.value[0].partialText)
  }

  // ── registerCancellation / unregisterCancellation ────────────────────────

  @Test
  fun unregisterCancellationPreventsCallbackOnCancel() {
    var invoked = false
    RequestLogStore.add(entry("x", isPending = true))
    RequestLogStore.registerCancellation("x") { invoked = true }
    RequestLogStore.unregisterCancellation("x")
    RequestLogStore.cancelRequest("x")
    assertFalse("callback should NOT be invoked after unregister", invoked)
  }

  @Test
  fun cancelRequestWithNoRegistrationIsNoOp() {
    RequestLogStore.add(entry("y", isPending = true))
    // No registerCancellation — cancelRequest should be a no-op (nothing to cancel)
    RequestLogStore.cancelRequest("y")
    assertFalse("cancelledByUser should NOT be set when no callback was registered", RequestLogStore.entries.value[0].cancelledByUser)
  }

  // ── Hard ceiling when maxEntries == 0 ────────────────────────────────

  @Test
  fun effectiveMaxEntriesReturnsHardCeilingWhenMaxIsZero() {
    RequestLogStore.setMaxEntries(0)
    assertEquals(
      "effective cap should equal HARD_MAX_IN_MEMORY_ENTRIES",
      HARD_MAX_IN_MEMORY_ENTRIES,
      RequestLogStore.effectiveMaxEntries,
    )
  }

  @Test
  fun effectiveMaxEntriesReturnsUserValueWhenNonZero() {
    RequestLogStore.setMaxEntries(500)
    assertEquals(500, RequestLogStore.effectiveMaxEntries)
  }

  // ── Memory pressure trimming ─────────────────────────────────────────

  @Test
  fun trimToPercentageReducesEntryCount() {
    RequestLogStore.setMaxEntries(100)
    repeat(100) { RequestLogStore.add(entry("e$it")) }
    assertEquals(100, RequestLogStore.entries.value.size)

    RequestLogStore.trimToPercentage(50)

    assertEquals("should trim to 50% of current size", 50, RequestLogStore.entries.value.size)
    assertEquals("newest entry should survive", "e99", RequestLogStore.entries.value.first().id)
  }

  @Test
  fun trimToPercentageClampsToMinimumOfOne() {
    RequestLogStore.setMaxEntries(100)
    repeat(100) { RequestLogStore.add(entry("e$it")) }

    RequestLogStore.trimToPercentage(0)

    assertEquals("should keep at least 1 entry", 1, RequestLogStore.entries.value.size)
  }

  @Test
  fun trimToPercentageOnEmptyListIsNoOp() {
    RequestLogStore.trimToPercentage(50)
    assertTrue(RequestLogStore.entries.value.isEmpty())
  }
}
