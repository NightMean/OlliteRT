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

package com.ollitert.llm.server.data.db

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Executes Room operations in callback-submission order.
 *
 * Request-log callbacks can arrive faster than Room completes a write. Launching one coroutine per
 * callback lets a terminal update overtake its initial insert, or lets a clear race with an insert.
 * A single channel consumer awaits each suspending DAO call before accepting the next operation.
 */
internal class RequestLogDatabaseWriter(
  private val dao: RequestLogDao,
  scope: CoroutineScope,
  private val onFailure: (operation: String, error: Throwable) -> Unit,
) {
  private data class QueuedOperation(
    val description: String,
    val execute: suspend RequestLogDao.() -> Unit,
  )

  private val operations = Channel<QueuedOperation>(capacity = Channel.UNLIMITED)

  init {
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      for (operation in operations) {
        try {
          operation.execute(dao)
        } catch (error: CancellationException) {
          throw error
        } catch (error: Exception) {
          // One failed log write must not discard every operation queued behind it.
          onFailure(operation.description, error)
        }
      }
    }
  }

  fun enqueue(description: String, operation: suspend RequestLogDao.() -> Unit) {
    val result = operations.trySend(QueuedOperation(description, operation))
    if (result.isFailure) {
      onFailure(
        description,
        result.exceptionOrNull() ?: IllegalStateException("Request-log database queue is unavailable"),
      )
    }
  }

  /** Test/support barrier that completes after every previously submitted operation. */
  internal suspend fun awaitIdle() {
    val reachedBarrier = CompletableDeferred<Unit>()
    operations.send(QueuedOperation("await idle") { reachedBarrier.complete(Unit) })
    reachedBarrier.await()
  }
}
