/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ollitert.llm.server.ui.modelmanager

/**
 * Serializes allowlist load ownership and its terminal publication.
 *
 * The publication callback is deliberately non-suspending and runs while the
 * generation is locked. A newer load therefore cannot begin between the
 * freshness check and the state write it authorizes.
 */
internal class AllowlistLoadGenerationGate {
  private var currentGeneration = 0

  fun beginLoad(): Int = synchronized(this) {
    currentGeneration += 1
    currentGeneration
  }

  fun publishIfCurrent(generation: Int, publish: () -> Unit): Boolean = synchronized(this) {
    if (generation != currentGeneration) {
      false
    } else {
      publish()
      true
    }
  }
}
