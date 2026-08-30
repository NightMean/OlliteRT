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

package com.ollitert.llm.server.ui.server.logs

import com.ollitert.llm.server.data.model.RequestLogEntry

/** Mutually exclusive response presentation states for one request log entry. */
internal sealed interface LogResponseContent {
  data class Pending(val partialText: String?, val isGenerating: Boolean) : LogResponseContent
  data class Cancelled(val partialText: String?, val cancelledByUser: Boolean) : LogResponseContent
  data class Completed(val body: String) : LogResponseContent
  data object None : LogResponseContent
}

internal fun resolveLogResponseContent(entry: RequestLogEntry): LogResponseContent = when {
  entry.isPending -> LogResponseContent.Pending(entry.partialText, entry.isGenerating)
  entry.isCancelled -> LogResponseContent.Cancelled(entry.partialText, entry.cancelledByUser)
  !entry.responseBody.isNullOrBlank() -> LogResponseContent.Completed(entry.responseBody)
  else -> LogResponseContent.None
}
