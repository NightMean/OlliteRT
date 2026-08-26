/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ollitert.llm.server.ui.modelmanager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStartGateTest {

  @Test
  fun initialAndRetryStartsShareOneReservationPerModel() {
    val gate = DownloadStartGate()

    assertTrue(gate.tryAcquire("gemma"))
    assertFalse(gate.tryAcquire("gemma"))
    assertTrue(gate.tryAcquire("qwen"))
  }

  @Test
  fun terminalStatusReleasesReservationForRetry() {
    val gate = DownloadStartGate()
    assertTrue(gate.tryAcquire("gemma"))

    gate.release("gemma")

    assertTrue(gate.tryAcquire("gemma"))
  }
}
