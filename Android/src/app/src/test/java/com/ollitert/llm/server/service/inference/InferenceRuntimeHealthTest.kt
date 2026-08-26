/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.ollitert.llm.server.service.inference

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceRuntimeHealthTest {
  @Test
  fun quarantinedOwnerRejectsWorkUntilItsNativeSettlementArrives() {
    val health = InferenceRuntimeHealth()
    val hungOwner = Any()
    val rejectedOwner = Any()

    assertTrue(health.tryAdmit(hungOwner))
    assertTrue(health.quarantine(hungOwner))
    assertTrue(health.isQuarantined())
    assertFalse(health.tryAdmit(rejectedOwner))

    health.markSettled(hungOwner)

    assertFalse(health.isQuarantined())
    assertTrue(health.tryAdmit(rejectedOwner))
  }

  @Test
  fun staleSettlementCannotClearANewerOwnersQuarantine() {
    val health = InferenceRuntimeHealth()
    val oldOwner = Any()
    val newerOwner = Any()

    assertTrue(health.tryAdmit(oldOwner))
    health.markSettled(oldOwner)
    assertTrue(health.tryAdmit(newerOwner))
    assertTrue(health.quarantine(newerOwner))

    health.markSettled(oldOwner)

    assertTrue(health.isQuarantined())
    health.markSettled(newerOwner)
    assertFalse(health.isQuarantined())
  }
}
