package com.ollitert.llm.server.common

/**
 * Semantic version parser with pre-release suffix awareness.
 * Handles version strings like "1.2.0", "1.2.0-beta.3", "1.2.0-dev.5".
 *
 * Comparison rules:
 * - Major.minor.patch compared numerically
 * - Stable (no pre-release) is higher than any pre-release of the same version
 * - Pre-release suffixes compared lexicographically ("beta" < "beta.1" < "beta.5", "dev" < "dev.1")
 */
data class SemVer(
  val major: Int,
  val minor: Int,
  val patch: Int,
  val preRelease: String? = null,
) : Comparable<SemVer> {

  override fun compareTo(other: SemVer): Int {
    if (major != other.major) return major.compareTo(other.major)
    if (minor != other.minor) return minor.compareTo(other.minor)
    if (patch != other.patch) return patch.compareTo(other.patch)
    // Stable (no preRelease) is higher than any pre-release of the same version
    if (preRelease == null && other.preRelease != null) return 1
    if (preRelease != null && other.preRelease == null) return -1
    // Both pre-release: compare lexicographically (dev > beta since 'd' > 'b', beta.2 > beta.1)
    return (preRelease ?: "").compareTo(other.preRelease ?: "")
  }

  override fun toString(): String = buildString {
    append("$major.$minor.$patch")
    if (preRelease != null) append("-$preRelease")
  }

  companion object {
    /**
     * Parse a version string like "v1.2.0", "1.2.0-beta.3", "1.2.0-dev".
     * Returns null for non-semver strings (malformed tags, empty strings, etc.).
     */
    fun parse(tag: String): SemVer? {
      val s = tag.removePrefix("v")
      val preRelease: String?
      val core: String
      if ("-" in s) {
        val split = s.split("-", limit = 2)
        core = split[0]
        preRelease = split.getOrNull(1)
      } else {
        core = s
        preRelease = null
      }
      val versionParts = core.split(".").mapNotNull { it.toIntOrNull() }
      if (versionParts.size < 3) return null
      return SemVer(versionParts[0], versionParts[1], versionParts[2], preRelease)
    }

    /** Returns true if [remoteTag] represents a newer version than [currentVersion]. */
    fun isNewer(currentVersion: String, remoteTag: String): Boolean {
      val current = parse(currentVersion) ?: return false
      val remote = parse(remoteTag) ?: return false
      return remote > current
    }
  }
}
