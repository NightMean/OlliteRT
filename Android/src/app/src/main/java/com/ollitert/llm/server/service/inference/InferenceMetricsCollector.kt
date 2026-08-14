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

package com.ollitert.llm.server.service.inference

import android.content.Context
import android.util.Log
import com.ollitert.llm.server.common.ErrorCategory
import com.ollitert.llm.server.data.EventCategory
import com.ollitert.llm.server.data.LogLevel
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.RequestPrefsSnapshot
import com.ollitert.llm.server.data.ServerPrefs

private const val TAG = "OlliteRT.Inference"

/**
 * Encapsulates performance telemetry, latency tracking, error metrics enrichment, and debug logging.
 */
internal object InferenceMetricsCollector {

  fun recordSuccess(
    promptTokens: Int,
    completionTokens: Int,
    totalDurationMs: Long,
    ttftMs: Long,
  ) {
    ServerMetrics.recordRequestMetrics(
      promptTokens = promptTokens,
      completionTokens = completionTokens,
      durationMs = totalDurationMs,
      ttftMs = ttftMs,
    )
  }

  fun recordError(
    errorMsg: String,
    model: Model?,
    logId: String?,
    emitDebugStackTrace: (Throwable, String, String?) -> Unit,
    throwable: Throwable? = null,
  ) {
    Log.e(TAG, "Inference error for model ${model?.name}: $errorMsg", throwable)
    ServerMetrics.incrementErrorCount(ErrorCategory.INFERENCE)
    RequestLogStore.addEvent(
      "Inference error: $errorMsg",
      level = LogLevel.ERROR,
      modelName = model?.name,
      category = EventCategory.INFERENCE,
      requestId = logId,
    )
    if (throwable != null) {
      emitDebugStackTrace(throwable, "inference_execution", model?.name)
    }
  }

  fun logVerbosePrompt(
    context: Context,
    model: Model,
    prompt: String,
    requestId: String,
  ) {
    if (!ServerPrefs.isVerboseDebugEnabled(context)) return
    RequestLogStore.addEvent(
      "Formatted prompt for inference",
      level = LogLevel.DEBUG,
      modelName = model.name,
      category = EventCategory.PROMPT,
      requestId = requestId,
      body = prompt,
    )
  }
}
