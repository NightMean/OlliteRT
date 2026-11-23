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

package com.ollitert.llm.server.data.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/** Keeps a model lock alive while a worker or removal operation holds or waits for it. */
internal object ModelDownloadLocks {
  // Shared with service teardown so removal waits until native engines release their files.
  val cleanupLatch = AtomicReference<CountDownLatch?>(null)
  fun isCleanupPending(): Boolean = cleanupLatch.get()?.count?.let { it > 0 } == true
  private data class Entry(val mutex: Mutex = Mutex(), var users: Int = 0)
  private val locks = mutableMapOf<String, Entry>()

  suspend fun <T> withModel(modelName: String, action: suspend () -> T): T {
    val entry = retain(modelName)
    return try {
      entry.mutex.withLock { action() }
    } finally {
      release(modelName, entry)
    }
  }

  /** Returns immediately when a download, startup, or removal owns this model. */
  suspend fun <T> tryWithModel(modelName: String, action: suspend () -> T): T? {
    val entry = retain(modelName)
    try {
      if (!entry.mutex.tryLock()) return null
      return try {
        action()
      } finally {
        entry.mutex.unlock()
      }
    } finally {
      release(modelName, entry)
    }
  }

  private fun retain(modelName: String): Entry = synchronized(locks) {
    locks.getOrPut(modelName) { Entry() }.also { it.users++ }
  }

  private fun release(modelName: String, entry: Entry) {
    synchronized(locks) {
      entry.users--
      if (entry.users == 0) locks.remove(modelName)
    }
  }
}
