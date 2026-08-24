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

package com.ollitert.llm.server.data.prefs

// Port validation range (IANA: 0–1023 reserved for well-known services).
// DEFAULT_PORT aliases the canonical constant in common so lower layers can use it
// without a data-layer dependency.
const val DEFAULT_PORT = com.ollitert.llm.server.common.SERVER_DEFAULT_PORT
const val MIN_VALID_PORT = 1024
const val MAX_VALID_PORT = 65535

// HTTP connection timeouts — fail fast so local fallback kicks in quickly.
const val HTTP_CONNECT_TIMEOUT_MS = 5_000
const val HTTP_READ_TIMEOUT_MS = 10_000

// Download timeouts — longer than metadata-fetch timeouts because model downloads
// are multi-GB and can legitimately pause during network congestion.
const val DOWNLOAD_CONNECT_TIMEOUT_MS = 30_000
const val DOWNLOAD_READ_TIMEOUT_MS = 60_000

// CORS preflight response cache duration (24 hours).
const val CORS_PREFLIGHT_MAX_AGE_SECONDS = 86400L

// Base64 data URI compaction threshold — payloads shorter than ~1 KB (1365 base64 chars ≈ 1024 bytes)
// are left inline (thumbnails, icons). Longer payloads are replaced with a size placeholder
// to avoid Compose rendering freezes in the Logs tab.
const val BASE64_COMPACT_THRESHOLD_CHARS = 1365
