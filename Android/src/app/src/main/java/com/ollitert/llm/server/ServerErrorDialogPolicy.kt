/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ollitert.llm.server

import com.ollitert.llm.server.common.ServerStatus

internal data class ServerErrorDialogDecision(
  val activeSignature: String?,
  val dismissedSignature: String?,
) {
  val shouldShow: Boolean
    get() = activeSignature != null && dismissedSignature != activeSignature
}

/** Keeps dialog dismissal scoped to one uninterrupted server-error occurrence. */
internal object ServerErrorDialogPolicy {

  fun reconcile(
    status: ServerStatus,
    message: String?,
    dismissedSignature: String?,
  ): ServerErrorDialogDecision {
    val isPortInUse = !message.isNullOrBlank() && message.contains("Port") && message.contains("already in use")
    val activeSignature = if (status == ServerStatus.ERROR && !message.isNullOrBlank()) {
      "$status:$message"
    } else if (isPortInUse && !message.isNullOrBlank()) {
      // Keep port-in-use errors visible until explicitly dismissed, even if status leaves ERROR
      "PORT_IN_USE:$message"
    } else {
      null
    }

    return ServerErrorDialogDecision(
      activeSignature = activeSignature,
      // Recovery ends the occurrence. Forget its dismissal so a later identical
      // failure remains visible instead of being suppressed by stale UI state.
      dismissedSignature = if (activeSignature == null) null else dismissedSignature,
    )
  }
}
