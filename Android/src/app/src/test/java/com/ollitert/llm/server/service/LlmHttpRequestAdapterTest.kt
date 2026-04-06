package com.ollitert.llm.server.service

import org.junit.Assert.assertEquals
import org.junit.Test

class LlmHttpRequestAdapterTest {
  @Test
  fun buildConversationPromptSingleTurnReturnsPlainText() {
    val prompt =
      LlmHttpRequestAdapter.buildConversationPrompt(
        listOf(
          InputMsg(
            role = "user",
            content =
              listOf(
                InputContent(type = "input_text", text = "keep me"),
                InputContent(type = "text", text = "final"),
              ),
          ),
        )
      )

    assertEquals("keep me final", prompt)
  }

  @Test
  fun buildConversationPromptMultiTurnFormatsHistory() {
    val prompt =
      LlmHttpRequestAdapter.buildConversationPrompt(
        listOf(
          InputMsg(
            role = "user",
            content = listOf(InputContent(type = "text", text = "first")),
          ),
          InputMsg(
            role = "assistant",
            content = listOf(InputContent(type = "text", text = "ignore")),
          ),
          InputMsg(
            role = "user",
            content =
              listOf(
                InputContent(type = "input_text", text = "keep me"),
                InputContent(type = "text", text = "final"),
              ),
          ),
        )
      )

    assertEquals("User: first\n\nAssistant: ignore\n\nUser: keep me final", prompt)
  }

  @Test
  fun synthesizeToolCallBuildsQueryArguments() {
    val toolCall =
      LlmHttpRequestAdapter.synthesizeToolCall(
        tool = ToolSpec(function = ToolFunctionDef(name = "search_docs")),
        prompt = "hello world",
        callId = "call_123",
      )

    assertEquals("call_123", toolCall.id)
    assertEquals("search_docs", toolCall.function.name)
    assertEquals("{\"query\":\"hello world\"}", toolCall.function.arguments)
  }
}
