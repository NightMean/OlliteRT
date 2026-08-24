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

import com.ollitert.llm.server.data.repository.FakeProtoDataStoreRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BenchmarkPersistenceWriterTest {

  @Test
  fun operationsCompleteInSubmissionOrder() = runTest {
    val repository = FakeProtoDataStoreRepository()
    val firstStarted = CompletableDeferred<Unit>()
    val releaseFirst = CompletableDeferred<Unit>()
    val completed = mutableListOf<String>()
    val writer = BenchmarkPersistenceWriter(repository, backgroundScope) { _, error -> throw error }

    writer.enqueue("add") {
      firstStarted.complete(Unit)
      releaseFirst.await()
      completed += "add"
    }
    writer.enqueue("delete") { completed += "delete" }

    firstStarted.await()
    releaseFirst.complete(Unit)
    writer.awaitIdle()

    assertEquals(listOf("add", "delete"), completed)
  }
}
