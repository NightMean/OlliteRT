package com.ollitert.llm.server.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object LlmHttpRequestAdapter {
  private val json = Json { ignoreUnknownKeys = true }

  /**
   * Placeholder token inserted into the prompt at positions where images appear.
   * Used to interleave images at the correct conversation positions in the LiteRT
   * Contents list, so multi-turn image conversations associate each image with its turn.
   */
  const val IMAGE_PLACEHOLDER = "<|image|>"

  /**
   * Builds a prompt from a Responses API message list.
   * A single-message list returns the text without role decoration (backward-compatible).
   * Multi-turn lists are formatted as "Role: text" paragraphs so the model sees the
   * full conversation history instead of just the last user turn.
   *
   * @param chatTemplate optional per-model template with {role} and {content} placeholders.
   *   When blank/null, uses the default "Role: text" format.
   */
  fun buildConversationPrompt(msgs: List<InputMsg>?, chatTemplate: String? = null): String {
    if (msgs == null) return ""
    if (msgs.size == 1 && chatTemplate.isNullOrBlank()) return extractTextFromMsg(msgs.first())
    return msgs
      .mapNotNull { msg ->
        val text = extractTextFromMsg(msg)
        if (text.isBlank()) null else formatMessage(msg.role, text, chatTemplate)
      }
      .joinToString(if (chatTemplate.isNullOrBlank()) "\n\n" else "\n")
  }

  /**
   * Builds a prompt from a Chat Completions message list.
   * A single-message list returns the content directly.
   * Multi-turn lists are formatted as "Role: content" paragraphs.
   * Messages with `role: "tool"` are formatted as tool results.
   *
   * @param chatTemplate optional per-model template with {role} and {content} placeholders.
   *   When blank/null, uses the default "Role: content" format.
   * @param interleaveImagePlaceholders when true, inserts [IMAGE_PLACEHOLDER] tokens at the
   *   exact position each image_url part appears in the conversation. This allows the inference
   *   layer to split on the placeholders and interleave Content.Text / Content.ImageBytes
   *   so multi-turn image conversations associate each image with its correct turn.
   */
  fun buildChatPrompt(
    msgs: List<ChatMessage>,
    chatTemplate: String? = null,
    interleaveImagePlaceholders: Boolean = false,
  ): String {
    if (msgs.isEmpty()) return ""
    if (msgs.size == 1 && msgs.first().role != "tool" && msgs.first().tool_calls.isNullOrEmpty() && chatTemplate.isNullOrBlank()) {
      return extractChatContent(msgs.first(), interleaveImagePlaceholders)
    }
    return msgs
      .mapNotNull { msg ->
        when (msg.role) {
          "tool" -> {
            val callId = msg.tool_call_id?.let { " (call_id: $it)" } ?: ""
            val name = msg.name?.let { " [$it]" } ?: ""
            "Tool Result$name$callId: ${msg.content.text}"
          }
          else -> {
            // Build text from content + any tool_calls on assistant messages.
            // HA sends multi-turn tool conversations as:
            //   {role:"assistant", content:null, tool_calls:[{id:"call_xyz", function:{name:"HassTurnOn", arguments:"{...}"}}]}
            //   {role:"tool", tool_call_id:"call_xyz", content:"{\"success\":true}"}
            // Without preserving the assistant's tool_calls, the model can't see what it
            // previously called and can't correlate tool results with its own decisions.
            val toolCallsText = msg.tool_calls?.takeIf { it.isNotEmpty() }?.joinToString("\n") { tc ->
              "Tool Call [${tc.function.name}] (call_id: ${tc.id}): ${tc.function.arguments}"
            }
            val contentText = extractChatContent(msg, interleaveImagePlaceholders).takeIf { it.isNotBlank() }
            val combined = listOfNotNull(contentText, toolCallsText).joinToString("\n")
            if (combined.isNotBlank()) formatMessage(msg.role, combined, chatTemplate)
            else null
          }
        }
      }
      .joinToString(if (chatTemplate.isNullOrBlank()) "\n\n" else "\n")
  }

  /**
   * Builds a tool-aware prompt by injecting tool schemas into the system prompt.
   * Used when the client sends `tools` in the request.
   *
   * @param msgs Original message list from the client.
   * @param tools Tool definitions to inject.
   * @param toolChoice Resolved tool_choice string ("auto", "none", "required", or a function name).
   * @param chatTemplate Optional per-model chat template.
   */
  fun buildToolAwarePrompt(
    msgs: List<ChatMessage>,
    tools: List<ToolSpec>,
    toolChoice: String?,
    chatTemplate: String?,
    compact: Boolean = false,
    interleaveImagePlaceholders: Boolean = false,
  ): String {
    val toolSchemas = if (compact) {
      tools.joinToString("\n") { tool ->
        val desc = tool.function.description?.let { " — $it" } ?: ""
        "${tool.function.name}$desc"
      }
    } else {
      tools.joinToString("\n\n") { tool ->
        val params = tool.function.parameters?.let { formatJsonElement(it) } ?: "{}"
        buildString {
          append("Function: ${tool.function.name}")
          tool.function.description?.let { append("\nDescription: $it") }
          append("\nParameters: $params")
        }
      }
    }

    val choiceInstruction = when (toolChoice) {
      "required" -> "\nYou MUST call one of the available tools. Do not respond with plain text."
      "none" -> "" // Shouldn't reach here — caller should skip tool injection for "none"
      else -> "\nIf you don't need to call a tool, respond normally with text."
    }

    val toolInstruction = """You have access to the following tools/functions. To call a tool, respond ONLY with a JSON object in this exact format (no other text before or after):
{"name": "function_name", "arguments": {"param1": "value1"}}

To call multiple tools at once, respond with a JSON array:
[{"name": "function1", "arguments": {...}}, {"name": "function2", "arguments": {...}}]
$choiceInstruction

Available tools:
$toolSchemas"""

    // Inject as a system message at the start
    val systemMsg = ChatMessage("system", ChatContent(toolInstruction))
    val augmentedMessages = listOf(systemMsg) + msgs
    return buildChatPrompt(augmentedMessages, chatTemplate, interleaveImagePlaceholders)
  }

  /**
   * Resolves `tool_choice` from its polymorphic form (String or Object) into
   * a simple string: "auto", "none", "required", or a specific function name.
   */
  fun resolveToolChoice(toolChoice: kotlinx.serialization.json.JsonElement?): String? {
    if (toolChoice == null) return null
    return when (toolChoice) {
      is JsonPrimitive -> toolChoice.content
      is JsonObject -> {
        toolChoice.jsonObject["function"]?.let { fn ->
          if (fn is JsonObject) fn.jsonObject["name"]?.jsonPrimitive?.content
          else null
        } ?: "auto"
      }
      else -> null
    }
  }

  /**
   * Extracts base64-encoded image data URIs from all messages.
   *
   * The API is stateless — clients resend the full conversation history (including all
   * images), and the server processes all images from all turns. Images are extracted in
   * message order so they can be associated with their position in the conversation.
   */
  fun extractImageDataUris(msgs: List<ChatMessage>): List<String> {
    return msgs.flatMap { msg ->
      msg.content.parts
        .filter { it.type == "image_url" }
        .mapNotNull { it.image_url?.url }
    }
  }

  /**
   * Extracts text content from a chat message. When [interleaveImagePlaceholders] is true
   * and the message has multimodal parts, inserts [IMAGE_PLACEHOLDER] tokens before the text.
   * Image placeholders are placed before text because LiteRT vision models expect to process
   * the image content before the text that references it (image-first ordering).
   */
  private fun extractChatContent(msg: ChatMessage, interleaveImagePlaceholders: Boolean): String {
    if (!interleaveImagePlaceholders || msg.content.parts.isEmpty()) return msg.content.text
    val imageCount = msg.content.parts.count { it.type == "image_url" }
    if (imageCount == 0) return msg.content.text
    // Images before text — vision models need to "see" the image before the referencing text.
    val text = msg.content.parts
      .filter { it.type == "text" }
      .mapNotNull { it.text }
      .joinToString(" ")
      .trim()
    return IMAGE_PLACEHOLDER.repeat(imageCount) + text
  }

  private fun formatJsonElement(element: JsonElement): String =
    json.encodeToString(JsonElement.serializer(), element)

  private fun extractTextFromMsg(msg: InputMsg): String =
    msg.content
      .filter { it.type == "text" || it.type == "input_text" || it.type == "output_text" }
      .joinToString(" ") { it.text }
      .trim()

  private fun formatMessage(role: String, content: String, template: String?): String {
    if (template.isNullOrBlank()) return "${formatRole(role)}: $content"
    return template
      .replace("{role}", role)
      .replace("{Role}", formatRole(role))
      .replace("{ROLE}", role.uppercase())
      .replace("{content}", content)
  }

  private fun formatRole(role: String): String = when (role.lowercase()) {
    "user" -> "User"
    "assistant" -> "Assistant"
    "system", "developer" -> "System"
    else -> role.replaceFirstChar { it.uppercase() }
  }
}
