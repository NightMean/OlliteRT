/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ollitert.llm.server.ui.modelmanager

/**
 * Process-local ownership for model download enqueue sequences.
 *
 * WorkManager becomes authoritative after enqueue, but its observable state is
 * asynchronous. Holding one reservation per model closes the interval in which
 * rapid initial or retry actions could both enqueue unique work with REPLACE.
 */
internal class DownloadStartGate {
  private val reservedModelNames = mutableSetOf<String>()

  fun tryAcquire(modelName: String): Boolean = synchronized(reservedModelNames) {
    reservedModelNames.add(modelName)
  }

  fun release(modelName: String) {
    synchronized(reservedModelNames) {
      reservedModelNames.remove(modelName)
    }
  }
}
