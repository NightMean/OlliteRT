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

package com.ollitert.llm.server.service.http

import android.content.Context
import com.ollitert.llm.server.data.model.Accelerator
import com.ollitert.llm.server.data.prefs.ConfigKeys
import com.ollitert.llm.server.data.model.ErrorKind
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.prefs.MAX_MAX_TOKENS
import com.ollitert.llm.server.data.prefs.MAX_TEMPERATURE
import com.ollitert.llm.server.data.prefs.MAX_TOPK
import com.ollitert.llm.server.data.prefs.MAX_TOPP
import com.ollitert.llm.server.data.prefs.MIN_MAX_TOKENS
import com.ollitert.llm.server.data.prefs.MIN_TEMPERATURE
import com.ollitert.llm.server.data.prefs.MIN_TOPK
import com.ollitert.llm.server.data.prefs.MIN_TOPP
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.data.prefs.RequestPrefsSnapshot
import com.ollitert.llm.server.data.prefs.SAMPLER_SEED_CONFIG_KEY
import com.ollitert.llm.server.data.model.maxContextTokens
import com.ollitert.llm.server.runtime.LlmModelInstance
import com.ollitert.llm.server.service.inference.InferenceRunner
import com.ollitert.llm.server.service.inference.PromptCompactor
import com.ollitert.llm.server.service.inference.ServerMetrics
import com.ollitert.llm.server.service.inference.estimateTokens
import com.ollitert.llm.server.service.inference.estimateTokensLong

/** Returns (paramName, errorMessage) if n is invalid, null if valid. */
internal fun validateNParam(n: Int?): Pair<String, String>? {
  if (n != null && n < 1) return "n" to "Invalid value for n: must be >= 1."
  if (n != null && n > 1) return "n" to "This server does not support parallel completions (n > 1). Omit the parameter or set n=1."
  return null
}

/** Returns (paramName, errorMessage) if best_of > 1, null if valid. */
internal fun validateBestOfParam(bestOf: Int?): Pair<String, String>? {
  if (bestOf != null && bestOf > 1) return "best_of" to "This server does not support best_of > 1. Omit the parameter or set best_of=1."
  return null
}

internal fun handleBlockingInferenceError(
  llmError: String?,
  logId: String?,
  context: Context,
): HttpResponse {
  val (errorMsg, kind) = InferenceRunner.enrichLlmError(llmError ?: "llm error", context)
  ServerMetrics.incrementErrorCount(kind.category)
  val suggestion = ErrorSuggestions.suggest(kind, context)
  if (logId != null) {
    val errorJson = ResponseRenderer.renderJsonError(errorMsg, suggestion, kind)
    RequestLogStore.update(logId) { it.copy(responseBody = errorJson, level = LogLevel.ERROR, errorKind = kind) }
  }
  return httpInternalError(errorMsg, suggestion, kind)
}

internal fun resolveSamplerOverrides(
  model: Model,
  prefs: RequestPrefsSnapshot,
  temperature: Double?,
  topP: Double?,
  topK: Int?,
  maxTokens: Int?,
  seed: Int?,
  logId: String?,
): Map<String, Any>? {
  val ignore = prefs.ignoreClientSamplerParams
  val effectiveTemp = temperature.takeUnless { ignore }
  val effectiveTopP = topP.takeUnless { ignore }
  val effectiveTopK = topK.takeUnless { ignore }
  val effectiveMaxTokens = maxTokens.takeUnless { ignore }
  val effectiveSeed = seed.takeUnless { ignore }
  if (ignore && logId != null) {
    val ignored = describeClientSamplerParams(temperature, topP, topK, maxTokens, seed)
    if (ignored != null) RequestLogStore.update(logId) { it.copy(ignoredClientParams = ignored) }
  }
  if (!ignore && logId != null && model.isGpuBackend() &&
    hasSamplerSensitiveParams(temperature, topP, topK, seed) &&
    model.claimGpuSamplerWarningForCurrentLoad()
  ) {
    RequestLogStore.addEvent(
      "Sampler params are ignored on LiteRT-LM 0.11 GPU",
      level = LogLevel.WARNING,
      modelName = model.name,
      category = EventCategory.SERVER,
      body = "LiteRT-LM 0.11.0's GPU executor uses fixed sampler defaults instead of the request's " +
        "temperature/top_p/top_k/seed (upstream issue google-ai-edge/LiteRT-LM#2080), causing deterministic " +
        "greedy decoding. Select CPU accelerator for sampler-sensitive requests.",
    )
  }
  return buildPerRequestConfig(model, effectiveTemp, effectiveTopP, effectiveTopK, effectiveMaxTokens, effectiveSeed)
}

internal fun hasSamplerSensitiveParams(
  temperature: Double?,
  topP: Double?,
  topK: Int?,
  seed: Int?,
): Boolean = temperature != null || topP != null || topK != null || seed != null

private fun Model.claimGpuSamplerWarningForCurrentLoad(): Boolean =
  (instance as? LlmModelInstance)?.diagnostics?.claimGpuSamplerWarning() == true

private fun Model.isGpuBackend(): Boolean =
  getStringConfigValue(key = ConfigKeys.ACCELERATOR, defaultValue = Accelerator.GPU.label) == Accelerator.GPU.label

internal fun describeClientSamplerParams(
  temperature: Double?,
  topP: Double?,
  topK: Int?,
  maxTokens: Int?,
  seed: Int? = null,
): String? = listOfNotNull(
  temperature?.let { "temperature=$it" },
  topP?.let { "top_p=$it" },
  topK?.let { "top_k=$it" },
  maxTokens?.let { "max_tokens=$it" },
  seed?.let { "seed=$it" },
).joinToString(", ").ifEmpty { null }

/**
 * Builds a config snapshot with per-request sampler overrides applied.
 * Returns null if no overrides are needed. Extracted as a top-level function
 * so multiple endpoint handlers (chat completions, transcription) can share it.
 * Used for streaming requests where the config must be applied on the executor
 * thread, not the request-handling thread.
 */
internal fun buildPerRequestConfig(
  model: Model,
  temperature: Double? = null,
  topP: Double? = null,
  topK: Int? = null,
  maxTokens: Int? = null,
  seed: Int? = null,
): Map<String, Any>? {
  if (temperature == null && topP == null && topK == null && maxTokens == null && seed == null) return null
  val overridden = model.configValues.toMutableMap()
  temperature?.let { overridden[ConfigKeys.TEMPERATURE.id] = clampTemperature(it) }
  topP?.let { overridden[ConfigKeys.TOPP.id] = clampTopP(it) }
  topK?.let { overridden[ConfigKeys.TOPK.id] = clampTopK(it) }
  seed?.let { overridden[SAMPLER_SEED_CONFIG_KEY] = it }
  maxTokens?.let {
    val clamped = clampMaxTokens(it)
    val engineMax = model.maxContextTokens
    overridden[ConfigKeys.MAX_TOKENS.id] = if (engineMax != null) clamped.coerceAtMost(engineMax) else clamped
  }
  return overridden.toMap()
}

internal fun recordContextUtilization(logId: String?, prompt: String, maxContext: Int?) {
  if (logId == null) return
  val inputEst = estimateTokensLong(prompt)
  val maxCtx = (maxContext ?: 0).toLong()
  RequestLogStore.update(logId) { it.copy(inputTokenEstimate = inputEst, maxContextTokens = maxCtx) }
}

internal fun clampTemperature(value: Double): Float =
  value.toFloat().coerceIn(MIN_TEMPERATURE, MAX_TEMPERATURE)

internal fun clampTopP(value: Double): Float =
  value.toFloat().coerceIn(MIN_TOPP, MAX_TOPP)

internal fun clampTopK(value: Int): Int =
  value.coerceIn(MIN_TOPK, MAX_TOPK)

internal fun clampMaxTokens(value: Int): Int =
  value.coerceIn(MIN_MAX_TOKENS, MAX_MAX_TOKENS)

/**
 * Logs compaction details and updates the request log entry when prompt compaction was applied.
 *
 * @param maxContext When non-null, appends estimatedTokens and maxContext to the log line.
 *   The /generate endpoint passes null because it logs context utilization separately.
 * @param updateLog Callback receiving (details, compactedPrompt); invoked only when logId is non-null.
 */
internal fun logCompactionResult(
  result: PromptCompactor.CompactionResult,
  requestId: String,
  endpoint: String,
  logId: String?,
  maxContext: Int?,
  logEvent: (String) -> Unit,
  updateLog: (details: String, compactedPrompt: String) -> Unit = { _, _ -> },
) {
  if (!result.compacted) return
  val details = result.strategies.joinToString(", ")
  val tokenSuffix = if (maxContext != null) {
    " estimatedTokens=${estimateTokens(result.prompt)} maxContext=$maxContext"
  } else ""
  logEvent("prompt_compacted id=$requestId endpoint=$endpoint strategies=[$details]$tokenSuffix")
  if (logId != null) updateLog(details, result.prompt)
}

internal fun compactionLogUpdater(logId: String?): (String, String) -> Unit = { details, compactedPrompt ->
  if (logId != null) {
    RequestLogStore.update(logId) { it.copy(isCompacted = true, compactionDetails = details, compactedPrompt = compactedPrompt) }
  }
}
