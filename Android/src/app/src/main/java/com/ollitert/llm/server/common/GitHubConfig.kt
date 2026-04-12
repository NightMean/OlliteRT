package com.ollitert.llm.server.common

/**
 * Single source of truth for the OlliteRT GitHub repository location.
 * All repo-derived URLs are built from [OWNER] and [REPO] so that a repo
 * rename or transfer only requires updating these two values.
 */
object GitHubConfig {
  const val OWNER = "NightMean"
  const val REPO = "ollitert"

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
   * Each app version requires a corresponding file (e.g. 2_0_0.json for v2.0.0).
   * The file can be a copy of the previous version if models haven't changed.
   */
  const val ALLOWLIST_BASE_URL =
    "https://raw.githubusercontent.com/$OWNER/$REPO/refs/heads/master/model_allowlists"

  /** LiteRT-LM SDK documentation base URL (used in model descriptions). */
  const val LITERT_LM_DOCS_URL = "https://github.com/google-ai-edge/LiteRT-LM"
}
