/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ollitert.llm.server.common

/** Transient detail for the model-loading portion of the server lifecycle. */
enum class ModelLoadPhase {
  STARTING,
  RETRYING_CPU,
}
