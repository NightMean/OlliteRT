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
import android.os.SystemClock
import androidx.annotation.WorkerThread
import com.ollitert.llm.server.data.EventCategory
import com.ollitert.llm.server.data.LOG_ERROR_PREVIEW_SHORT_CHARS
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.RequestLogStore
import com.ollitert.llm.server.data.RequestPrefsSnapshot
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.data.WARMUP_MESSAGE

/**
 * Handles warmup inference routines and request cancellation state reporting.
 */
internal object InferenceWarmupHelper {

  /**
   * Warm up the model with a short test inference.
   * Used during model loading to pre-fill caches and verify the model works.
   */
  @WorkerThread
  fun warmUpModel(
    context: Context,
    model: Model,
    runLlm: suspend (model: Model, prompt: String, requestId: String, endpoint: String, timeoutSeconds: Long, eagerVisionInit: Boolean) -> Pair<String?, String?>,
  ) {
    val startMs = SystemClock.elapsedRealtime()
    val eagerVision = ServerPrefs.isEagerVisionInit(context)
    val (result, error) = kotlinx.coroutines.runBlocking {
      runLlm(
        model,
        WARMUP_MESSAGE,
        "warmup",
        "warmup",
        ServerPrefs.getTimeoutWarmup(context),
        eagerVision,
      )
    }
    val elapsedMs = SystemClock.elapsedRealtime() - startMs
    if (error != null && error.startsWith("model_init_failed:")) {
      throw RuntimeException(error.removePrefix("model_init_failed: "))
    }
    val snippet = result?.take(LOG_ERROR_PREVIEW_SHORT_CHARS)?.replace("\n", " ") ?: "no response"
    RequestLogStore.addEvent(
      "Sending a warmup message: \"$WARMUP_MESSAGE\" → \"$snippet\" (${elapsedMs}ms)",
      modelName = model.name,
      category = EventCategory.MODEL,
    )
  }

  /**
   * Handles non-streaming request cancellation state updates in RequestLogStore and event log.
   */
  fun handleCancellation(
    context: Context,
    result: InferenceResult,
    logId: String?,
    requestId: String,
    endpoint: String,
    prefs: RequestPrefsSnapshot?,
    logSuffix: String,
    returnMessage: String,
    logEvent: (String) -> Unit,
  ): Pair<String?, String> {
    val keepPartial = prefs?.keepPartialResponse ?: ServerPrefs.isKeepPartialResponse(context)
    val partial = if (keepPartial && !result.output.isNullOrEmpty()) result.output else null
    if (logId != null) {
      RequestLogStore.update(logId) {
        it.copy(
          partialText = partial,
          isPending = false,
          isCancelled = true,
          statusCode = 499,
          latencyMs = result.totalMs,
        )
      }
    }
    logEvent("request_cancelled id=$requestId endpoint=$endpoint streaming=false $logSuffix outputChars=${result.output?.length ?: 0}")
    return null to returnMessage
  }
}
