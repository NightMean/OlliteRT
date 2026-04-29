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

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaInjectionBridgeTest {

  @Test
  fun toolSpecsToProviders_convertsValidSpec() {
    val specs = listOf(
      ToolSpec(
        type = "function",
        function = ToolFunctionDef(
          name = "get_weather",
          description = "Get weather for a city",
          parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
              putJsonObject("city") {
                put("type", JsonPrimitive("string"))
              }
            }
          },
        ),
      ),
    )
    val providers = SchemaInjectionBridge.toolSpecsToProviders(specs)
    assertEquals(1, providers.size)
  }

  @Test
  fun toolSpecsToProviders_emptyList_returnsEmpty() {
    val providers = SchemaInjectionBridge.toolSpecsToProviders(emptyList())
    assertEquals(0, providers.size)
  }

  @Test
  fun convertNativeToolCalls_mapsFieldsCorrectly() {
    val nativeCalls = listOf(
      com.google.ai.edge.litertlm.ToolCall(
        name = "get_weather",
        arguments = mapOf("city" to "London", "units" to "celsius"),
      ),
    )
    val result = SchemaInjectionBridge.convertNativeToolCalls(nativeCalls)
    assertEquals(1, result.size)
    assertEquals("get_weather", result[0].function.name)
    assertTrue(result[0].function.arguments.contains("London"))
    assertTrue(result[0].function.arguments.contains("celsius"))
    assertEquals("function", result[0].type)
    assertNotNull(result[0].id)
  }

  @Test
  fun convertNativeToolCalls_emptyArguments() {
    val nativeCalls = listOf(
      com.google.ai.edge.litertlm.ToolCall(
        name = "stop_music",
        arguments = emptyMap(),
      ),
    )
    val result = SchemaInjectionBridge.convertNativeToolCalls(nativeCalls)
    assertEquals("{}", result[0].function.arguments)
  }

  @Test
  fun buildInitialMessages_skipsSystemAndDropsLast() {
    val msgs = listOf(
      ChatMessage(role = "system", content = ChatContent("You are helpful")),
      ChatMessage(role = "user", content = ChatContent("Hello")),
      ChatMessage(role = "assistant", content = ChatContent("Hi there")),
      ChatMessage(role = "user", content = ChatContent("What's the weather?")),
    )
    val result = SchemaInjectionBridge.buildInitialMessages(msgs)
    assertEquals(2, result.size)
  }

  @Test
  fun buildInitialMessages_singleMessage_returnsEmpty() {
    val msgs = listOf(
      ChatMessage(role = "user", content = ChatContent("Hello")),
    )
    val result = SchemaInjectionBridge.buildInitialMessages(msgs)
    assertEquals(0, result.size)
  }

  @Test
  fun buildLastUserInput_normalMessage() {
    val msgs = listOf(
      ChatMessage(role = "user", content = ChatContent("Hello")),
      ChatMessage(role = "assistant", content = ChatContent("Hi")),
      ChatMessage(role = "user", content = ChatContent("What's up?")),
    )
    val result = SchemaInjectionBridge.buildLastUserInput(msgs)
    assertEquals("What's up?", result)
  }

  @Test
  fun buildLastUserInput_toolResultMessages_includesWorkaround() {
    val msgs = listOf(
      ChatMessage(role = "user", content = ChatContent("Turn on the lights")),
      ChatMessage(
        role = "assistant",
        content = ChatContent(""),
        tool_calls = listOf(
          ToolCall(id = "call_1", type = "function", function = ToolCallFunction("turn_on", "{\"entity_id\":\"light.living_room\"}"))
        ),
      ),
      ChatMessage(role = "tool", content = ChatContent("{\"success\": true}"), tool_call_id = "call_1"),
    )
    val result = SchemaInjectionBridge.buildLastUserInput(msgs)
    assertTrue(result.contains("function's return value"))
    assertTrue(result.contains("turn_on"))
    assertTrue(result.contains("{\"success\": true}"))
  }
}
