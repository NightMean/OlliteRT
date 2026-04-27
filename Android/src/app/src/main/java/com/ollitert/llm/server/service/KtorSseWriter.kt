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

import android.util.Log
import java.io.IOException
import java.io.Writer

/**
 * [SseWriter] implementation that wraps Ktor's [Writer] from `respondTextWriter`.
 *
 * Writes SSE-formatted text directly to the response stream and flushes after
 * each chunk so the client receives tokens in real time. If writing fails
 * (client disconnected), [isCancelled] is set so the inference loop can stop
 * generating tokens.
 */
class KtorSseWriterImpl(
  private val writer: Writer,
) : SseWriter {
  @Volatile
  override var isCancelled: Boolean = false
    private set

  override suspend fun emit(text: String) {
    try {
      writer.write(text)
      writer.flush()
    } catch (e: IOException) {
      Log.d(TAG, "SSE write failed (client disconnect): ${e.javaClass.simpleName}")
      isCancelled = true
    } catch (e: Exception) {
      Log.w(TAG, "SSE write failed (unexpected): ${e.javaClass.simpleName}: ${e.message}")
      isCancelled = true
    }
  }

  override suspend fun finish() {
    // No-op — Ktor closes the writer when the respondTextWriter block completes
  }

  private companion object {
    private const val TAG = "OlliteRT.SSE"
  }
}
