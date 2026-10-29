/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ollitert.llm.server.worker

import java.io.File

internal enum class ArtifactProblem { HTML, SIZE }

/** Inspect only a small prefix: model files can be several gigabytes. */
internal fun validateDownloadedArtifact(file: File, expectedBytes: Long): ArtifactProblem? {
  val prefix = ByteArray(512)
  val count = file.inputStream().use { it.read(prefix) }.coerceAtLeast(0)
  val text = String(prefix, 0, count, Charsets.UTF_8).removePrefix("\uFEFF").trimStart()
  if (text.startsWith("<!doctype html", ignoreCase = true) ||
      text.startsWith("<html", ignoreCase = true)) return ArtifactProblem.HTML
  if (file.length() == 0L || (expectedBytes > 0L && file.length() != expectedBytes)) {
    return ArtifactProblem.SIZE
  }
  return null
}
