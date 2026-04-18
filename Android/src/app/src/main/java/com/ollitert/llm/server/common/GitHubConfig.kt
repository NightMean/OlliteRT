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

package com.ollitert.llm.server.common

/**
 * Single source of truth for the OlliteRT GitHub repository location.
 * All repo-derived URLs are built from [OWNER] and [REPO] so that a repo
 * rename or transfer only requires updating these two values.
 */
object GitHubConfig {
  const val OWNER = "NightMean"
  const val REPO = "OlliteRT"

  /** Repository homepage (e.g. for "Learn More" links). */
  const val REPO_URL = "https://github.com/$OWNER/$REPO"

  /** GitHub Releases page. */
  const val RELEASES_URL = "$REPO_URL/releases"

  /** Base URL for the GitHub REST API for this repo. */
  const val API_BASE = "https://api.github.com/repos/$OWNER/$REPO"

  /** URL to open a new bug report issue with the YAML template. */
  const val NEW_BUG_REPORT_URL = "$REPO_URL/issues/new?template=01_bug_report.yml"

  // ---------------------------------------------------------------------------
  // Donation
  // ---------------------------------------------------------------------------

  const val DONATE_GITHUB_SPONSORS = "https://github.com/sponsors/NightMean"
  const val DONATE_BUY_ME_A_COFFEE = "https://www.buymeacoffee.com/nightmean"
  const val DONATE_KOFI = "https://ko-fi.com/nightmean"

  // ---------------------------------------------------------------------------
  // Model allowlists and documentation (hosted in this repo)
  // ---------------------------------------------------------------------------

  /**
   * Base URL for fetching remote model allowlist JSON files.
   * Versioned by format generation (v1/, v2/, etc.) so breaking schema changes
   * don't affect older app versions. Each app release needs a matching file
   * (e.g. v1/0_8_0.json for v0.8.0). Copy the previous file if models haven't changed.
   */
  const val ALLOWLIST_BASE_URL =
    "https://raw.githubusercontent.com/$OWNER/$REPO/refs/heads/main/model_allowlists/v1"

  /** LiteRT-LM SDK documentation base URL (used in model descriptions). */
  const val LITERT_LM_DOCS_URL = "https://github.com/google-ai-edge/LiteRT-LM"
}
