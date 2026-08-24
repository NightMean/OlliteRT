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
import com.ollitert.llm.server.data.model.Repository

/**
 * Repository interface for request logging persistence, history synchronization,
 * and database maintenance.
 */
interface RequestLogRepository {
  /** Initialize the persistence layer. */
  fun initialize()

  /** Update the in-memory entry cap to match current persistence settings. */
  fun updateMaxEntries()

  /** Persist all current in-memory entries to the database. */
  fun persistCurrentEntries()

  /** Clear all persisted request logs from the database. */
  fun clearPersistedLogs()

  /** Schedule recurring background pruning based on user retention settings. */
  fun schedulePruning()

  /** Prune expired or overflow entries from the database. */
  fun prune()

  /** Load persisted log entries from the database into the in-memory store. */
  fun loadFromDb()

  /** Shut down background jobs and cancel scheduled pruning. */
  fun shutdown()
}
