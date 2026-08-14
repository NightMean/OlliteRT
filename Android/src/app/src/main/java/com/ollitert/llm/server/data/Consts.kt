/*
 * Copyright 2025 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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

package com.ollitert.llm.server.data

// ── UI and Interaction Constants ────────────────────────────────────────────

// Live UI timer tick interval (uptime, loading elapsed, metric refresh).
const val UI_TIMER_TICK_MS = 1000L

// Debounce delay for server start/stop/reload button to prevent double-tap races.
const val ACTION_IN_FLIGHT_DEBOUNCE_MS = 1000L

// ── Log Persistence & Trimming Constants ────────────────────────────────────

// Log persistence pruning intervals.
const val DEFAULT_IN_MEMORY_LOG_CAP = 100
const val HARD_MAX_IN_MEMORY_ENTRIES = 10_000
const val MIN_PRUNE_INTERVAL_MS = 60_000L             // 1 minute
const val MAX_PRUNE_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours

// Error-message preview lengths for log events. Short used for headline/single-line
// log entries (toasts, single-line cards); long used for body-level entries
// (multi-line cards, init failures) where more context aids diagnosis.
const val LOG_ERROR_PREVIEW_SHORT_CHARS = 80
const val LOG_ERROR_PREVIEW_LONG_CHARS = 120

// ── Backward-compatible utility re-exports ──────────────────────────────────
fun Long.bytesToGb(): Float = this / (1024f * 1024f * 1024f)
fun Long.bytesToMb(): Long = this / (1024L * 1024L)
val SOC: String get() = com.ollitert.llm.server.common.SOC
