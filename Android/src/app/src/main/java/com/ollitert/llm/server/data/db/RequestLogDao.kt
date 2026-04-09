package com.ollitert.llm.server.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Room DAO for persisted request log entries. */
@Dao
interface RequestLogDao {

  /** Insert or update a single log entry (upsert by primary key). */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: RequestLogEntity)

  /** Bulk insert/update — used when persistence is first enabled to sync current in-memory entries. */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<RequestLogEntity>)

  /** Load the most recent [limit] entries, newest first. */
  @Query("SELECT * FROM request_logs ORDER BY timestamp DESC LIMIT :limit")
  suspend fun getRecent(limit: Int): List<RequestLogEntity>

  /** Delete all persisted log entries. */
  @Query("DELETE FROM request_logs")
  suspend fun deleteAll()

  /** Delete entries older than the given timestamp (age-based pruning). */
  @Query("DELETE FROM request_logs WHERE timestamp < :olderThanMs")
  suspend fun deleteOlderThan(olderThanMs: Long)

  /**
   * Keep only the newest [maxCount] entries, delete the rest (count-based pruning).
   * Uses a subquery to find the cutoff timestamp at the Nth newest position.
   */
  @Query(
    """
    DELETE FROM request_logs WHERE timestamp < (
      SELECT timestamp FROM request_logs ORDER BY timestamp DESC LIMIT 1 OFFSET :maxCount
    )
    """
  )
  suspend fun pruneToCount(maxCount: Int)

  /** Total number of persisted entries. */
  @Query("SELECT COUNT(*) FROM request_logs")
  suspend fun count(): Int
}
