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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogEntry
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

/**
 * Room entity for persisted request logs.
 *
 * Uses a **hybrid schema**: frequently-queried fields are indexed columns (for filtering/sorting),
 * while everything else is stored in a single JSON [extras] column. This means adding new fields
 * to [RequestLogEntry] only requires updating [ExtrasJson] — no Room migration needed.
 */
@Entity(
  tableName = "request_logs",
  indices = [
    Index("timestamp"),
    Index("level"),
    Index("modelName"),
  ],
)
data class RequestLogEntity(
  @PrimaryKey val id: String,

  // --- Indexed / queryable columns ---
  val timestamp: Long,
  val method: String,
  val path: String,
  val statusCode: Int,
  val level: String, // LogLevel enum name
  @ColumnInfo(defaultValue = "") val modelName: String?,
  val eventCategory: String, // EventCategory enum name
  val latencyMs: Long,
  val isStreaming: Boolean,
  val inputTokenEstimate: Long,
  val maxContextTokens: Long,

  // --- JSON blob for everything else (flexible, no migration for new fields) ---
  val extras: String,
) {

  /**
   * Moshi-serialized JSON structure for non-indexed fields.
   * Unknown keys are silently ignored on deserialization, so old DB rows
   * deserialize safely even after new fields are added here.
   */
  @JsonClass(generateAdapter = true)
  data class ExtrasJson(
    val requestBody: String? = null,
    val originalRequestBodySize: Int = 0,
    val responseBody: String? = null,
    val tokens: Long = 0,
    val clientIp: String? = null,
    val isPending: Boolean = false,
    val isThinking: Boolean = false,
    val isCompacted: Boolean = false,
    val compactionDetails: String? = null,
    val compactedPrompt: String? = null,
    val isCancelled: Boolean = false,
    val cancelledByUser: Boolean = false,
    val partialText: String? = null,
    val isExactTokenCount: Boolean = false,
    val ignoredClientParams: String? = null,
    // Per-request performance metrics
    val ttfbMs: Long = 0,
    val decodeSpeed: Double = 0.0,
    val prefillSpeed: Double = 0.0,
    val itlMs: Double = 0.0,
  )

  /** Convert back to the in-memory [RequestLogEntry]. */
  fun toEntry(moshi: Moshi): RequestLogEntry {
    val adapter = moshi.adapter(ExtrasJson::class.java)
    val ext = try {
      adapter.fromJson(extras) ?: ExtrasJson()
    } catch (_: Exception) {
      ExtrasJson()
    }
    return RequestLogEntry(
      id = id,
      timestamp = timestamp,
      method = method,
      path = path,
      requestBody = ext.requestBody,
      originalRequestBodySize = ext.originalRequestBodySize,
      responseBody = ext.responseBody,
      statusCode = statusCode,
      tokens = ext.tokens,
      latencyMs = latencyMs,
      isStreaming = isStreaming,
      modelName = modelName,
      clientIp = ext.clientIp,
      level = try { LogLevel.valueOf(level) } catch (_: Exception) { LogLevel.INFO },
      isPending = ext.isPending,
      isThinking = ext.isThinking,
      isCompacted = ext.isCompacted,
      compactionDetails = ext.compactionDetails,
      compactedPrompt = ext.compactedPrompt,
      isCancelled = ext.isCancelled,
      cancelledByUser = ext.cancelledByUser,
      partialText = ext.partialText,
      eventCategory = try { EventCategory.valueOf(eventCategory) } catch (_: Exception) { EventCategory.GENERAL },
      inputTokenEstimate = inputTokenEstimate,
      maxContextTokens = maxContextTokens,
      isExactTokenCount = ext.isExactTokenCount,
      ignoredClientParams = ext.ignoredClientParams,
      ttfbMs = ext.ttfbMs,
      decodeSpeed = ext.decodeSpeed,
      prefillSpeed = ext.prefillSpeed,
      itlMs = ext.itlMs,
    )
  }

  companion object {
    /** Convert an in-memory [RequestLogEntry] to a persistable entity. */
    fun fromEntry(entry: RequestLogEntry, moshi: Moshi): RequestLogEntity {
      val adapter = moshi.adapter(ExtrasJson::class.java)
      val ext = ExtrasJson(
        requestBody = entry.requestBody,
        originalRequestBodySize = entry.originalRequestBodySize,
        responseBody = entry.responseBody,
        tokens = entry.tokens,
        clientIp = entry.clientIp,
        isPending = entry.isPending,
        isThinking = entry.isThinking,
        isCompacted = entry.isCompacted,
        compactionDetails = entry.compactionDetails,
        compactedPrompt = entry.compactedPrompt,
        isCancelled = entry.isCancelled,
        cancelledByUser = entry.cancelledByUser,
        partialText = entry.partialText,
        isExactTokenCount = entry.isExactTokenCount,
        ignoredClientParams = entry.ignoredClientParams,
        ttfbMs = entry.ttfbMs,
        decodeSpeed = entry.decodeSpeed,
        prefillSpeed = entry.prefillSpeed,
        itlMs = entry.itlMs,
      )
      return RequestLogEntity(
        id = entry.id,
        timestamp = entry.timestamp,
        method = entry.method,
        path = entry.path,
        statusCode = entry.statusCode,
        level = entry.level.name,
        modelName = entry.modelName,
        eventCategory = entry.eventCategory.name,
        latencyMs = entry.latencyMs,
        isStreaming = entry.isStreaming,
        inputTokenEstimate = entry.inputTokenEstimate,
        maxContextTokens = entry.maxContextTokens,
        extras = adapter.toJson(ext),
      )
    }
  }
}
