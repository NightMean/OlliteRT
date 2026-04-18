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

package com.ollitert.llm.server.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TasksTest {

  @Test
  fun legacyTaskIdsStayMarkedLegacy() {
    assertTrue(isLegacyTasks(BuiltInTaskId.LLM_CHAT))
    assertTrue(isLegacyTasks(BuiltInTaskId.LLM_ASK_IMAGE))
    assertTrue(isLegacyTasks(BuiltInTaskId.LLM_ASK_AUDIO))
    assertTrue(isLegacyTasks(BuiltInTaskId.LLM_PROMPT_LAB))
    assertTrue(isLegacyTasks(BuiltInTaskId.LLM_AGENT_CHAT))
  }

  @Test
  fun nonLegacyTaskIdsAreNotMarkedLegacy() {
    assertFalse(isLegacyTasks("unknown_task"))
  }

  @Test
  fun allowThinkingIsLimitedToExpectedBuiltInTasks() {
    val thinkingIds =
      listOf(
        BuiltInTaskId.LLM_CHAT,
        BuiltInTaskId.LLM_ASK_IMAGE,
        BuiltInTaskId.LLM_ASK_AUDIO,
      )

    thinkingIds.forEach { id ->
      assertTrue(dummyTask(id).allowThinking())
    }

    listOf(
      BuiltInTaskId.LLM_PROMPT_LAB,
      "custom_task",
    ).forEach { id ->
      assertFalse(dummyTask(id).allowThinking())
    }
  }

  private fun dummyTask(id: String): Task =
    Task(
      id = id,
      label = id,
      category = Category.LLM,
      description = "test",
      models = mutableListOf(),
    )
}
