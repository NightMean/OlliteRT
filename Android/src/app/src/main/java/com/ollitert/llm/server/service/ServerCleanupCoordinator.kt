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

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.MIN_STORAGE_FOR_MODEL_INIT_BYTES
import com.ollitert.llm.server.data.bytesToMb
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "OlliteRT.Service"

/**
 * Coordinates native LiteRT engine cleanup synchronization across service lifecycles
 * and validates storage headroom before engine allocation.
 */
internal object ServerCleanupCoordinator {

  /**
   * Latch that the background cleanup thread in onDestroy signals when native memory is released.
   * The next service instance's model load thread waits on this before initializing to avoid
   * racing with the old instance's Engine/Conversation cleanup.
   *
   * Uses AtomicReference instead of @Volatile to avoid race conditions where the latch is
   * nulled out between the new instance's read and wait. The latch is never nulled — once
   * counted down, it stays counted-down and await() returns immediately.
   */
  val cleanupLatch = AtomicReference<CountDownLatch?>(null)

  /** Checks available storage before native model init to avoid SIGABRT from LiteRT. */
  fun checkStorageBeforeLoad(context: Context) {
    try {
      val stat = StatFs(Environment.getDataDirectory().path)
      if (stat.availableBytes < MIN_STORAGE_FOR_MODEL_INIT_BYTES) {
        val availMb = stat.availableBytes.bytesToMb()
        throw RuntimeException(
          context.getString(
            R.string.error_storage_low_model_init,
            availMb.toString(),
            MIN_STORAGE_FOR_MODEL_INIT_BYTES.bytesToMb().toString(),
          )
        )
      }
    } catch (e: RuntimeException) {
      throw e
    } catch (_: Exception) {
      // StatFs failed — proceed and let native code decide
    }
  }

  /** Waits for the previous service instance's native cleanup to finish. */
  fun enqueueCleanup(threadName: String, cleanup: () -> Unit) {
    lateinit var predecessor: CountDownLatch
    val next = CountDownLatch(1)
    while (true) {
      val current = cleanupLatch.get()
      if (cleanupLatch.compareAndSet(current, next)) {
        predecessor = current ?: CountDownLatch(0)
        break
      }
    }
    Thread({
      var wasInterrupted = false
      try {
        while (predecessor.count > 0) {
          try {
            predecessor.await()
          } catch (_: InterruptedException) {
            wasInterrupted = true
          }
        }
        cleanup()
      } finally {
        if (wasInterrupted) Thread.currentThread().interrupt()
        next.countDown()
      }
    }, threadName).start()
  }

  /** Waits for every previously queued native cleanup to finish. */
  fun awaitPreviousCleanup() {
    cleanupLatch.get()?.let { latch ->
      if (latch.count > 0) {
        Log.i(TAG, "Waiting for previous model cleanup to finish...")
        var wasInterrupted = false
        while (latch.count > 0) {
          try {
            latch.await()
          } catch (_: InterruptedException) {
            wasInterrupted = true
          }
        }
        if (wasInterrupted) Thread.currentThread().interrupt()
        Log.i(TAG, "Previous cleanup finished, proceeding with model load")
      }
    }
  }
}
