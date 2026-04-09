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
  exportSchema = false,
)
abstract class OlliteDatabase : RoomDatabase() {
  abstract fun requestLogDao(): RequestLogDao
}
