/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ollitert.llm.server

import com.ollitert.llm.server.common.ServerStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerErrorDialogPolicyTest {

  @Test
  fun identicalErrorShowsAgainAfterRecovery() {
    val firstError = ServerErrorDialogPolicy.reconcile(
      status = ServerStatus.ERROR,
      message = "Out of memory",
      dismissedSignature = null,
    )
    assertTrue(firstError.shouldShow)

    val dismissedSignature = firstError.activeSignature
    val sameOccurrence = ServerErrorDialogPolicy.reconcile(
      status = ServerStatus.ERROR,
      message = "Out of memory",
      dismissedSignature = dismissedSignature,
    )
    assertFalse(sameOccurrence.shouldShow)

    val recovered = ServerErrorDialogPolicy.reconcile(
      status = ServerStatus.RUNNING,
      message = null,
      dismissedSignature = sameOccurrence.dismissedSignature,
    )
    assertNull(recovered.dismissedSignature)

    val repeatedError = ServerErrorDialogPolicy.reconcile(
      status = ServerStatus.ERROR,
      message = "Out of memory",
      dismissedSignature = recovered.dismissedSignature,
    )
    assertTrue(repeatedError.shouldShow)
  }
}
