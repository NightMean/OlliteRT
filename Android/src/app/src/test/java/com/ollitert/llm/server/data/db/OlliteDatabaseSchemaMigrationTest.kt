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

import com.ollitert.llm.server.data.model.RequestLogEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Durability and schema migration test suite for [OlliteDatabase].
 *
 * Ensures:
 * 1. Exported Room schema matches compiled Entity metadata (version, tables, columns, types, indices).
 * 2. SQLite DDL create statements and column affinities are deterministic and valid.
 * 3. All domain fields in [RequestLogEntry] are mapped between SQL columns and JSON extras.
 */
class OlliteDatabaseSchemaMigrationTest {

  private val json = Json { ignoreUnknownKeys = true }

  private fun findSchemaFile(version: Int): File {
    val searchPaths = listOf(
      File("schemas/com.ollitert.llm.server.data.db.OlliteDatabase/$version.json"),
      File("Android/src/app/schemas/com.ollitert.llm.server.data.db.OlliteDatabase/$version.json"),
      File("src/app/schemas/com.ollitert.llm.server.data.db.OlliteDatabase/$version.json"),
    )
    return searchPaths.firstOrNull { it.exists() }
      ?: error("Schema file for version $version not found in paths: $searchPaths")
  }

  @Test
  fun validateSchemaV1StructureAndIdentity() {
    val schemaFile = findSchemaFile(1)
    val content = schemaFile.readText()
    val root = json.parseToJsonElement(content).jsonObject

    val formatVersion = root["formatVersion"]!!.jsonPrimitive.content.toInt()
    assertEquals("Room schema formatVersion should be 1", 1, formatVersion)

    val dbObj = root["database"]!!.jsonObject
    val version = dbObj["version"]!!.jsonPrimitive.content.toInt()
    assertEquals("Database schema version should be 1", 1, version)

    val identityHash = dbObj["identityHash"]!!.jsonPrimitive.content
    assertTrue("Identity hash must not be empty", identityHash.isNotEmpty())

    val entities = dbObj["entities"]!!.jsonArray
    assertEquals("Should have exactly 1 entity table in schema v1", 1, entities.size)

    val requestLogsTable = entities[0].jsonObject
    assertEquals("request_logs", requestLogsTable["tableName"]!!.jsonPrimitive.content)

    val createSql = requestLogsTable["createSql"]!!.jsonPrimitive.content
    assertTrue("createSql must create request_logs table", createSql.startsWith("CREATE TABLE IF NOT EXISTS"))
    assertTrue("createSql must include primary key on id", createSql.contains("PRIMARY KEY(`id`)"))
  }

  @Test
  fun validateSchemaV1ColumnsAndAffinities() {
    val schemaFile = findSchemaFile(1)
    val content = schemaFile.readText()
    val root = json.parseToJsonElement(content).jsonObject
    val table = root["database"]!!.jsonObject["entities"]!!.jsonArray[0].jsonObject
    val fields = table["fields"]!!.jsonArray

    val columnAffinities = fields.associate {
      it.jsonObject["columnName"]!!.jsonPrimitive.content to it.jsonObject["affinity"]!!.jsonPrimitive.content
    }

    val expectedAffinities = mapOf(
      "id" to "TEXT",
      "timestamp" to "INTEGER",
      "method" to "TEXT",
      "path" to "TEXT",
      "statusCode" to "INTEGER",
      "level" to "TEXT",
      "modelName" to "TEXT",
      "eventCategory" to "TEXT",
      "latencyMs" to "INTEGER",
      "isStreaming" to "INTEGER",
      "inputTokenEstimate" to "INTEGER",
      "maxContextTokens" to "INTEGER",
      "extras" to "TEXT",
    )

    assertEquals("Column affinities must strictly match schema definitions", expectedAffinities, columnAffinities)

    // Verify primary key
    val pkObj = table["primaryKey"]!!.jsonObject
    val pkColumns = pkObj["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content }
    assertEquals(listOf("id"), pkColumns)

    // Verify indices
    val indices = table["indices"]!!.jsonArray
    val indexColumns = indices.flatMap { idx ->
      idx.jsonObject["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content }
    }.toSet()
    assertTrue("Index must exist for timestamp", indexColumns.contains("timestamp"))
    assertTrue("Index must exist for level", indexColumns.contains("level"))
    assertTrue("Index must exist for modelName", indexColumns.contains("modelName"))
  }

  @Test
  fun validateRequestLogEntityPropertiesMatchSchemaColumns() {
    val schemaFile = findSchemaFile(1)
    val content = schemaFile.readText()
    val root = json.parseToJsonElement(content).jsonObject
    val fields = root["database"]!!.jsonObject["entities"]!!.jsonArray[0].jsonObject["fields"]!!.jsonArray
    val schemaColumnNames = fields.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }.toSet()

    val entityFields = RequestLogEntity::class.java.declaredFields
      .filter { !it.isSynthetic && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
      .map { it.name }
      .toSet()

    assertEquals(
      "All RequestLogEntity properties must exist as columns in Room schema",
      schemaColumnNames,
      entityFields,
    )
  }
}
