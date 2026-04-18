package com.ollitert.llm.server

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates fastlane metadata files comply with Play Store / F-Droid constraints.
 * These limits are platform-enforced — exceeding them causes upload rejection.
 */
class FastlaneMetadataTest {

  companion object {
    /** Play Store / F-Droid max characters per changelog file. */
    private const val MAX_CHANGELOG_CHARS = 500

    /** Play Store max characters for short description. */
    private const val MAX_SHORT_DESCRIPTION_CHARS = 80

    /** Play Store max characters for full description. */
    private const val MAX_FULL_DESCRIPTION_CHARS = 4000

    private val metadataDir = File("../fastlane/metadata/android/en-US")
  }

  @Test
  fun allChangelogFilesUnder500Chars() {
    val changelogsDir = File(metadataDir, "changelogs")
    if (!changelogsDir.exists()) return // Skip if no changelogs yet

    val violations = changelogsDir.listFiles()
      ?.filter { it.extension == "txt" }
      ?.filter { it.readText().length > MAX_CHANGELOG_CHARS }
      ?.map { "${it.name}: ${it.readText().length} chars (max $MAX_CHANGELOG_CHARS)" }
      ?: emptyList()

    assertTrue(
      "Changelog files exceed $MAX_CHANGELOG_CHARS char limit:\n${violations.joinToString("\n")}",
      violations.isEmpty(),
    )
  }

  @Test
  fun shortDescriptionUnder80Chars() {
    val file = File(metadataDir, "short_description.txt")
    if (!file.exists()) return

    val length = file.readText().trim().length
    assertTrue(
      "short_description.txt is $length chars (max $MAX_SHORT_DESCRIPTION_CHARS)",
      length <= MAX_SHORT_DESCRIPTION_CHARS,
    )
  }

  @Test
  fun fullDescriptionUnder4000Chars() {
    val file = File(metadataDir, "full_description.txt")
    if (!file.exists()) return

    val length = file.readText().length
    assertTrue(
      "full_description.txt is $length chars (max $MAX_FULL_DESCRIPTION_CHARS)",
      length <= MAX_FULL_DESCRIPTION_CHARS,
    )
  }
}
