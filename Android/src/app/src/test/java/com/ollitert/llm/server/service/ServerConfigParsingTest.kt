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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConfigParsingTest {

  @Test
  fun `parseConfigDouble returns value for valid double`() {
    val obj = Json.parseToJsonElement("""{"temperature": 0.7}""").jsonObject
    val result = parseConfigDouble(obj, "temperature")
    assertEquals(0.7, result!!, 0.001)
  }

  @Test
  fun `parseConfigDouble returns null when field absent`() {
    val obj = Json.parseToJsonElement("""{}""").jsonObject
    assertNull(parseConfigDouble(obj, "temperature"))
  }

  @Test
  fun `parseConfigDouble throws with field name for string value`() {
    val obj = Json.parseToJsonElement("""{"temperature": "hot"}""").jsonObject
    val ex = try {
      parseConfigDouble(obj, "temperature")
      null
    } catch (e: ConfigFieldException) { e }
    assertTrue(ex != null)
    assertEquals("temperature", ex!!.fieldName)
    assertTrue(ex.message!!.contains("temperature"))
    assertTrue(ex.message!!.contains("number"))
  }

  @Test
  fun `parseConfigDouble throws with field name for null value`() {
    val obj = Json.parseToJsonElement("""{"temperature": null}""").jsonObject
    val ex = try {
      parseConfigDouble(obj, "temperature")
      null
    } catch (e: ConfigFieldException) { e }
    assertTrue(ex != null)
    assertEquals("temperature", ex!!.fieldName)
  }

  @Test
  fun `parseConfigInt returns value for valid int`() {
    val obj = Json.parseToJsonElement("""{"max_tokens": 1024}""").jsonObject
    assertEquals(1024, parseConfigInt(obj, "max_tokens"))
  }

  @Test
  fun `parseConfigInt throws with field name for boolean value`() {
    val obj = Json.parseToJsonElement("""{"max_tokens": true}""").jsonObject
    val ex = try {
      parseConfigInt(obj, "max_tokens")
      null
    } catch (e: ConfigFieldException) { e }
    assertTrue(ex != null)
    assertEquals("max_tokens", ex!!.fieldName)
    assertTrue(ex.message!!.contains("integer"))
  }

  @Test
  fun `parseConfigInt throws with field name for decimal value`() {
    val obj = Json.parseToJsonElement("""{"max_tokens": 10.5}""").jsonObject
    val ex = try {
      parseConfigInt(obj, "max_tokens")
      null
    } catch (e: ConfigFieldException) { e }
    assertTrue(ex != null)
    assertEquals("max_tokens", ex!!.fieldName)
  }

  @Test
  fun `parseConfigBool returns value for valid boolean`() {
    val obj = Json.parseToJsonElement("""{"thinking_enabled": true}""").jsonObject
    assertEquals(true, parseConfigBool(obj, "thinking_enabled"))
  }

  @Test
  fun `parseConfigBool throws with field name for string value`() {
    val obj = Json.parseToJsonElement("""{"thinking_enabled": "yes"}""").jsonObject
    val ex = try {
      parseConfigBool(obj, "thinking_enabled")
      null
    } catch (e: ConfigFieldException) { e }
    assertTrue(ex != null)
    assertEquals("thinking_enabled", ex!!.fieldName)
    assertTrue(ex.message!!.contains("boolean"))
  }

  @Test
  fun `parseConfigString returns value for valid string`() {
    val obj = Json.parseToJsonElement("""{"system_prompt": "hello"}""").jsonObject
    assertEquals("hello", parseConfigString(obj, "system_prompt"))
  }

  @Test
  fun `parseConfigString coerces numeric value to string`() {
    val obj = Json.parseToJsonElement("""{"system_prompt": 42}""").jsonObject
    assertEquals("42", parseConfigString(obj, "system_prompt"))
  }

  @Test
  fun `parseConfigString throws with field name for array value`() {
    val obj = Json.parseToJsonElement("""{"system_prompt": [1, 2]}""").jsonObject
    val ex = try {
      parseConfigString(obj, "system_prompt")
      null
    } catch (e: ConfigFieldException) { e }
    assertTrue(ex != null)
    assertEquals("system_prompt", ex!!.fieldName)
    assertTrue(ex.message!!.contains("string"))
  }
}
