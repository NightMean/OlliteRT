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

import java.util.Collections
import java.util.IdentityHashMap

internal const val INFERENCE_RUNTIME_QUARANTINED_ERROR = "runtime_quarantined"

/** Owns whether one inference pipeline can safely admit native work. */
internal class InferenceRuntimeHealth {
  private val stateLock = Any()
  private val activeOwners: MutableSet<Any> =
    Collections.newSetFromMap(IdentityHashMap())
  private var quarantinedOwner: Any? = null

  fun tryAdmit(owner: Any): Boolean = synchronized(stateLock) {
    if (quarantinedOwner != null) return@synchronized false
    activeOwners.add(owner)
    true
  }

  /** Quarantines only while [owner] still has native settlement outstanding. */
  fun quarantine(owner: Any): Boolean = synchronized(stateLock) {
    if (owner !in activeOwners) return@synchronized false
    val currentOwner = quarantinedOwner
    if (currentOwner != null && currentOwner !== owner) return@synchronized false
    quarantinedOwner = owner
    true
  }

  /** Late settlement can heal only the quarantine created by the same owner. */
  fun markSettled(owner: Any) {
    synchronized(stateLock) {
      activeOwners.remove(owner)
      if (quarantinedOwner === owner) quarantinedOwner = null
    }
  }

  fun isQuarantined(): Boolean = synchronized(stateLock) { quarantinedOwner != null }
}
