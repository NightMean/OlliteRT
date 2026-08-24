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

package com.ollitert.llm.server.ui.settings

import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.repository.FakePreferencesRepository
import com.ollitert.llm.server.data.repository.RequestLogRepository
import com.ollitert.llm.server.data.repository.RequestLogStore
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LogRetentionCoordinatorTest {

  private lateinit var preferences: FakePreferencesRepository
  private lateinit var persistence: RequestLogRepository
  private lateinit var coordinator: LogRetentionCoordinator

  @Before
  fun setUp() {
    preferences = FakePreferencesRepository()
    persistence = mockk(relaxed = true)
    coordinator = LogRetentionCoordinator(persistence, preferences)
    RequestLogStore.setMaxEntries(0) // effectively unbounded so seeded entries survive
    RequestLogStore.clear()
  }

  @After
  fun tearDown() {
    RequestLogStore.clear()
  }

  private fun seedEntries(count: Int) {
    repeat(count) { i ->
      RequestLogStore.addEvent(
        "entry $i",
        level = LogLevel.INFO,
        category = EventCategory.GENERAL,
      )
    }
  }

  // ── wouldTrimLogs ──

  @Test
  fun `wouldTrimLogs true when max reduced below current count`() {
    seedEntries(10)
    assertTrue(coordinator.wouldTrimLogs(newMaxEntries = 5, maxEntriesChanged = true))
  }

  @Test
  fun `wouldTrimLogs false when value unchanged even if below current count`() {
    seedEntries(10)
    assertFalse(coordinator.wouldTrimLogs(newMaxEntries = 5, maxEntriesChanged = false))
  }

  @Test
  fun `wouldTrimLogs false when new max covers current count`() {
    seedEntries(10)
    assertFalse(coordinator.wouldTrimLogs(newMaxEntries = 50, maxEntriesChanged = true))
  }

  // ── syncAfterSave ──

  @Test
  fun `syncAfterSave backfills entries only on first-time enablement`() {
    val snapshot = coordinator.snapshotPruningPrefs() // persistence disabled by default
    preferences.setLogPersistenceEnabled(true)

    coordinator.syncAfterSave(enabledNow = true, snapshot)

    verify(exactly = 1) { persistence.persistCurrentEntries() }
  }

  @Test
  fun `syncAfterSave skips backfill when persistence was already enabled`() {
    preferences.setLogPersistenceEnabled(true)
    val snapshot = coordinator.snapshotPruningPrefs()

    coordinator.syncAfterSave(enabledNow = true, snapshot)

    verify(exactly = 0) { persistence.persistCurrentEntries() }
  }

  @Test
  fun `syncAfterSave always resyncs the in-memory cap`() {
    val snapshot = coordinator.snapshotPruningPrefs()

    coordinator.syncAfterSave(enabledNow = false, snapshot)

    verify(exactly = 1) { persistence.updateMaxEntries() }
  }

  @Test
  fun `syncAfterSave reschedules pruning when retention config changed`() {
    val snapshot = coordinator.snapshotPruningPrefs()
    preferences.setLogPersistenceEnabled(true)
    preferences.setLogAutoDeleteMinutes(60L)

    coordinator.syncAfterSave(enabledNow = true, snapshot)

    verify(exactly = 1) { persistence.schedulePruning() }
  }

  @Test
  fun `syncAfterSave does not reschedule pruning when config unchanged`() {
    val snapshot = coordinator.snapshotPruningPrefs()

    coordinator.syncAfterSave(enabledNow = false, snapshot)

    verify(exactly = 0) { persistence.schedulePruning() }
  }

  // ── clearAllLogs / syncAfterReset ──

  @Test
  fun `clearAllLogs clears store and persisted logs`() {
    seedEntries(3)

    coordinator.clearAllLogs()

    assertTrue(RequestLogStore.entries.value.isEmpty())
    verify(exactly = 1) { persistence.clearPersistedLogs() }
  }

  @Test
  fun `syncAfterReset resyncs cap pruning and wipes persisted logs`() {
    coordinator.syncAfterReset()

    verify(exactly = 1) { persistence.updateMaxEntries() }
    verify(exactly = 1) { persistence.schedulePruning() }
    verify(exactly = 1) { persistence.clearPersistedLogs() }
  }
}
