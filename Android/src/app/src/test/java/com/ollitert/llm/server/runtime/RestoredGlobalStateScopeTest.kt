/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.ollitert.llm.server.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RestoredGlobalStateScopeTest {
  @Test
  fun restoresSnapshotWhenNativeWorkThrowsAnError() {
    var globalState = "original"
    val scope = RestoredGlobalStateScope<String>()

    try {
      scope.run(
        capture = { globalState },
        restore = { globalState = it },
      ) {
        globalState = "temporary"
        throw AssertionError("native failure")
      }
    } catch (_: AssertionError) {
      // Expected: the assertion models an Error rather than an Exception.
    }

    assertEquals("original", globalState)
  }

  @Test
  fun serializesConcurrentGlobalStateOwners() {
    val scope = RestoredGlobalStateScope<Unit>()
    val firstEntered = CountDownLatch(1)
    val releaseFirst = CountDownLatch(1)
    val secondEntered = CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(2)
    try {
      executor.submit {
        scope.run(capture = {}, restore = {}) {
          firstEntered.countDown()
          releaseFirst.await()
        }
      }
      assertTrue(firstEntered.await(1, TimeUnit.SECONDS))
      executor.submit {
        scope.run(capture = {}, restore = {}) { secondEntered.countDown() }
      }

      assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS))
      releaseFirst.countDown()
      assertTrue(secondEntered.await(1, TimeUnit.SECONDS))
    } finally {
      releaseFirst.countDown()
      executor.shutdownNow()
    }
  }
}
