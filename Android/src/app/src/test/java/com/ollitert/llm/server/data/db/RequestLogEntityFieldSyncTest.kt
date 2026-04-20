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

import com.ollitert.llm.server.service.RequestLogEntry
import org.junit.Assert.fail
import org.junit.Test

/**
 * Reflection-based guard ensuring every [RequestLogEntry] field is accounted
 * for in [RequestLogEntity] — either as an indexed column or an [ExtrasJson] field.
 * Fails at CI time if a new field is added to RequestLogEntry but not mapped.
 */
class RequestLogEntityFieldSyncTest {

  @Test
  fun everyRequestLogEntryFieldIsMappedInEntity() {
    val entryFields = RequestLogEntry::class.java.declaredFields
      .filter { !it.isSynthetic }
      .map { it.name }
      .toSet()

    val entityColumns = RequestLogEntity::class.java.declaredFields
      .filter { !it.isSynthetic && it.name != "extras" }
      .map { it.name }
      .toSet()

    val extrasFields = RequestLogEntity.ExtrasJson::class.java.declaredFields
      .filter { !it.isSynthetic }
      .map { it.name }
      .toSet()

    val covered = entityColumns + extrasFields

    val missing = entryFields - covered
    if (missing.isNotEmpty()) {
      fail(
        "RequestLogEntry fields not mapped in RequestLogEntity or ExtrasJson: $missing\n" +
          "Add each missing field to either the entity columns (if queryable) or ExtrasJson, " +
          "then update toEntry() and fromEntry()."
      )
    }
  }
}
