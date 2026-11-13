/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ollitert.llm.server.ui.modelmanager

import java.util.Locale

/** Filename and memory clues only; the result does not establish runtime support. */
internal fun mayWorkOnDevice(path: String, size: Long?, totalMemory: Long?, device: String): Boolean {
  if (size != null && size > 0 && totalMemory != null && totalMemory > 0 && size > totalMemory / 2) return false
  val tokens = path.substringAfterLast('/').lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+"))
  if (tokens.any { it in setOf("apple", "metal", "ios", "intel", "amd", "nvidia") }) return false
  val deviceTokens = device.lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+"))
  val chip = Regex("(?:sm|mt)[0-9]{4,5}")
  val fileChips = tokens.filter { chip.matches(it) }.toSet()
  val deviceChips = deviceTokens.filter { chip.matches(it) }.toSet()
  if (fileChips.isNotEmpty() && deviceChips.isNotEmpty() && fileChips.intersect(deviceChips).isEmpty()) return false
  val vendors = listOf(
    setOf("tensor", "zuma", "gs101", "gs201", "laguna"),
    setOf("snapdragon", "qualcomm", "qcom"),
    setOf("dimensity", "mediatek", "mtk"),
    setOf("exynos", "samsung"),
  )
  val fileVendors = vendors.indices.filter { index -> tokens.any { it in vendors[index] } }.toSet()
  val deviceVendors = vendors.indices.filter { index -> deviceTokens.any { it in vendors[index] } }.toSet()
  return fileVendors.isEmpty() || deviceVendors.isEmpty() || fileVendors.intersect(deviceVendors).isNotEmpty()
}
