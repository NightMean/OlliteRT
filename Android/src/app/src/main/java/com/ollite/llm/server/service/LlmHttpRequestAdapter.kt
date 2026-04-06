package com.ollite.llm.server.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object LlmHttpRequestAdapter {
  private val json = Json { ignoreUnknownKeys = true }

  /**
   * Builds a prompt from a Responses API message list.
   * A single-message list returns the text without role decoration (backward-compatible).
   * Multi-turn lists are formatted as "Role: text" paragraphs so the model sees the
   * full conversation history instead of just the last user turn.
   */
  fun buildConversationPrompt(msgs: List<InputMsg>?): String {
    if (msgs == null) return ""
    if (msgs.size == 1) return extractTextFromMsg(msgs.first())
    return msgs
      .mapNotNull { msg ->
        val text = extractTextFromMsg(msg)
        if (text.isBlank()) null else "${formatRole(msg.role)}: $text"
      }
      .joinToString("\n\n")
  }

  /**
   * Builds a prompt from a Chat Completions message list.
   * A single-message list returns the content directly.
   * Multi-turn lists are formatted as "Role: content" paragraphs.
   */
  fun buildChatPrompt(msgs: List<ChatMessage>): String {
    if (msgs.isEmpty()) return ""
    if (msgs.size == 1) return msgs.first().content.text
    return msgs
      .filter { it.content.text.isNotBlank() }
      .joinToString("\n\n") { "${formatRole(it.role)}: ${it.content.text}" }
  }

  /** Extracts base64-encoded image data URIs from multimodal chat messages. */
  fun extractImageDataUris(msgs: List<ChatMessage>): List<String> {
    return msgs.flatMap { msg ->
      msg.content.parts
        .filter { it.type == "image_url" && it.image_url != null }
        .map { it.image_url!!.url }
    }
  }

  fun synthesizeToolCall(tool: ToolSpec, prompt: String, callId: String): ToolCall {
    val argsObj = JsonObject(mapOf("query" to JsonPrimitive(prompt)))
    return ToolCall(
      id = callId,
      function = ToolCallFunction(
        name = tool.function.name,
        arguments = json.encodeToString(argsObj),
      ),
    )
  }

  private fun extractTextFromMsg(msg: InputMsg): String =
    msg.content
      .filter { it.type == "text" || it.type == "input_text" || it.type == "output_text" }
      .joinToString(" ") { it.text }
      .trim()

  private fun formatRole(role: String): String = when (role.lowercase()) {
    "user" -> "User"
    "assistant" -> "Assistant"
    "system", "developer" -> "System"
    else -> role.replaceFirstChar { it.uppercase() }
  }
}
