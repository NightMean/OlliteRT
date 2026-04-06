package com.ollite.llm.server.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class Usage(val prompt_tokens: Int, val completion_tokens: Int)

@Serializable data class ResponsesRequest(
  val model: String? = null,
  val input: List<InputMsg>? = null,
  val messages: List<InputMsg>? = null,
  val stream: Boolean? = null,
  val tools: List<ToolSpec>? = null,
  val tool_choice: String? = null,
)

@Serializable data class InputMsg(
  val role: String,
  val content: List<InputContent>,
)

@Serializable data class InputContent(
  val type: String,
  val text: String,
)

@Serializable data class ResponsesResponse(
  val id: String,
  val `object`: String = "response",
  val created: Long,
  val model: String,
  val output: List<RespMessage>,
  val usage: Usage,
)

@Serializable data class RespMessage(
  val role: String = "assistant",
  val content: List<RespContent>,
  val finish_reason: String = "stop",
)

@Serializable data class RespContent(
  val type: String = "text",
  val text: String,
)

@Serializable data class GenRes(val text: String, val usage: Usage)

@Serializable data class ChatMessage(
  val role: String,
  val content: String,
  val tool_calls: List<ToolCall>? = null,
)

@Serializable data class ToolCallFunction(val name: String, val arguments: String)

@Serializable data class ToolCall(
  val id: String,
  val type: String = "function",
  val function: ToolCallFunction,
)

@Serializable data class ToolFunctionDef(
  val name: String,
  val description: String? = null,
  val parameters: JsonElement? = null,
)

@Serializable data class ToolSpec(val type: String = "function", val function: ToolFunctionDef)

@Serializable data class ChatRequest(
  val model: String? = null,
  val messages: List<ChatMessage> = emptyList(),
  val stream: Boolean? = null,
  val tools: List<ToolSpec>? = null,
  val tool_choice: String? = null,
)

@Serializable data class ChatChoice(
  val index: Int,
  val message: ChatMessage,
  val finish_reason: String,
)

@Serializable data class ChatResponse(
  val id: String,
  val created: Long,
  val model: String,
  val choices: List<ChatChoice>,
  val usage: Usage,
)

@Serializable data class GenReq(val prompt: String)
