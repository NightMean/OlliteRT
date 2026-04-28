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

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for OlliteRT local data.
 *
 * Currently stores persisted request logs only. Uses [AutoMigration] avoidance via
 * hybrid schema — the [RequestLogEntity.extras] JSON column absorbs new fields
 * without schema changes. [fallbackToDestructiveMigration] on the builder is a
 * safety net: losing log history is acceptable if indexed columns ever change.
 */
@Database(
  entities = [RequestLogEntity::class],
  version = 1,
  exportSchema = true,
)
abstract class OlliteDatabase : RoomDatabase() {
  abstract fun requestLogDao(): RequestLogDao
}
