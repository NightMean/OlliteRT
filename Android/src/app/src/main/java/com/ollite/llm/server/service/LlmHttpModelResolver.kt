package com.ollite.llm.server.service

import com.ollite.llm.server.data.AllowedModel

object LlmHttpModelResolver {
  fun pickDefaultAllowedModel(allowlist: List<AllowedModel>): AllowedModel? {
    val preferred = allowlist.firstOrNull { it.name == "Gemma3-1B-IT" }
    return preferred ?: allowlist.firstOrNull { allowed ->
      allowed.taskTypes.any { it.startsWith("llm", ignoreCase = true) }
    }
  }

  fun selectAllowedModel(allowlist: List<AllowedModel>, requestedModel: String?): AllowedModel? {
    val requested = requestedModel?.trim().orEmpty()
    if (
      requested.isEmpty() ||
        requested.equals("local", ignoreCase = true) ||
        requested.equals("default", ignoreCase = true)
    ) {
      return null
    }

    val key = LlmHttpBridgeUtils.normalizeModelKey(requested)
    return allowlist.firstOrNull { model ->
      LlmHttpBridgeUtils.normalizeModelKey(model.name) == key ||
        LlmHttpBridgeUtils.normalizeModelKey(model.modelId) == key
    }
  }
}
