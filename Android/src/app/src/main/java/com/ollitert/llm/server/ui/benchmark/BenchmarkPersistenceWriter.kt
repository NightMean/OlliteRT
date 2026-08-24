/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.ollitert.llm.server.ui.benchmark

import com.ollitert.llm.server.data.repository.DataStoreRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Executes benchmark persistence mutations in UI submission order. */
internal class BenchmarkPersistenceWriter(
  private val repository: DataStoreRepository,
  scope: CoroutineScope,
  dispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val onFailure: (operation: String, error: Throwable) -> Unit,
) {
  private data class Operation(
    val description: String,
    val execute: suspend DataStoreRepository.() -> Unit,
  )

  private val operations = Channel<Operation>(Channel.UNLIMITED)

  init {
    scope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
      for (operation in operations) {
        try {
          operation.execute(repository)
        } catch (error: CancellationException) {
          throw error
        } catch (error: Exception) {
          onFailure(operation.description, error)
        }
      }
    }
  }

  fun enqueue(description: String, operation: suspend DataStoreRepository.() -> Unit) {
    val result = operations.trySend(Operation(description, operation))
    if (result.isFailure) {
      onFailure(
        description,
        result.exceptionOrNull() ?: IllegalStateException("Benchmark persistence queue unavailable"),
      )
    }
  }

  internal suspend fun awaitIdle() {
    val barrier = CompletableDeferred<Unit>()
    operations.send(Operation("await idle") { barrier.complete(Unit) })
    barrier.await()
  }
}
