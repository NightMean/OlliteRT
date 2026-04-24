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

  /** Privacy Policy hosted in the repo docs folder. */
  const val PRIVACY_POLICY_URL = "$REPO_URL/blob/main/docs/PRIVACY_POLICY.md"

  // ---------------------------------------------------------------------------
  // Donation
  // ---------------------------------------------------------------------------

  const val DONATE_GITHUB_SPONSORS = "https://github.com/sponsors/NightMean"
  const val DONATE_BUY_ME_A_COFFEE = "https://www.buymeacoffee.com/nightmean"
  const val DONATE_KOFI = "https://ko-fi.com/nightmean"

  // ---------------------------------------------------------------------------
  // HuggingFace
  // ---------------------------------------------------------------------------

  const val HUGGINGFACE_BASE_URL = "https://huggingface.co"

  // ---------------------------------------------------------------------------
  // Model allowlists and documentation (hosted in this repo)
  // ---------------------------------------------------------------------------

  /**
   * URL for the single master model allowlist JSON file.
   * Version filtering is handled by minAppVersion/maxAppVersion fields in the JSON.
   */
  const val ALLOWLIST_URL =
    "https://raw.githubusercontent.com/$OWNER/$REPO/refs/heads/main/model_allowlists/v1/model_allowlist.json"

}
