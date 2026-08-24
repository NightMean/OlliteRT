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

import com.ollitert.llm.server.data.repository.PreferencesRepository
import com.ollitert.llm.server.data.repository.RequestLogRepository
import com.ollitert.llm.server.data.repository.RequestLogStore
import javax.inject.Inject

/**
 * Coordinates the log-retention side effects of saving, resetting, and clearing
 * settings — the bridge between Settings UI state and [RequestLogRepository].
 */
class LogRetentionCoordinator @Inject constructor(
  private val persistence: RequestLogRepository,
  private val preferencesRepository: PreferencesRepository,
) {

  /** Retention prefs captured before a save overwrites them. */
  data class PruningSnapshot(val persistenceWasEnabled: Boolean, val autoDeleteWasMinutes: Long)

  /** Captures retention prefs before they are overwritten by a save. */
  fun snapshotPruningPrefs(): PruningSnapshot =
    PruningSnapshot(
      persistenceWasEnabled = preferencesRepository.isLogPersistenceEnabled(),
      autoDeleteWasMinutes = preferencesRepository.getLogAutoDeleteMinutes(),
    )

  /** True if applying [newMaxEntries] would trim existing in-memory logs. */
  fun wouldTrimLogs(newMaxEntries: Int, maxEntriesChanged: Boolean): Boolean =
    maxEntriesChanged && newMaxEntries < RequestLogStore.entries.value.size

  /**
   * Re-syncs the persistence layer after retention settings were persisted.
   *
   * First-time enablement back-fills today's session logs so they survive a restart;
   * pruning is rescheduled only when its configuration actually changed.
   */
  fun syncAfterSave(enabledNow: Boolean, snapshot: PruningSnapshot) {
    if (enabledNow && !snapshot.persistenceWasEnabled) {
      persistence.persistCurrentEntries()
    }
    persistence.updateMaxEntries()

    val enabled = preferencesRepository.isLogPersistenceEnabled()
    val pruningConfigChanged = enabled != snapshot.persistenceWasEnabled ||
      (enabled && preferencesRepository.getLogAutoDeleteMinutes() != snapshot.autoDeleteWasMinutes)
    if (pruningConfigChanged) {
      persistence.schedulePruning()
    }
  }

  /** Clears both in-memory and persisted logs ("Clear Persisted Logs" action). */
  fun clearAllLogs() {
    RequestLogStore.clear()
    persistence.clearPersistedLogs()
  }

  /** Re-syncs retention after a factory reset (retention prefs are defaults again). */
  fun syncAfterReset() {
    persistence.updateMaxEntries()
    persistence.schedulePruning()
    persistence.clearPersistedLogs()
  }
}
