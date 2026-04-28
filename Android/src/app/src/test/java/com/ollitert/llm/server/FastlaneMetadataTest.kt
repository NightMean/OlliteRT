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

package com.ollitert.llm.server

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
    assumeTrue("Changelogs directory not found — skipping", changelogsDir.exists())

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
    assumeTrue("short_description.txt not found — skipping", file.exists())

    val length = file.readText().trim().length
    assertTrue(
      "short_description.txt is $length chars (max $MAX_SHORT_DESCRIPTION_CHARS)",
      length <= MAX_SHORT_DESCRIPTION_CHARS,
    )
  }

  @Test
  fun fullDescriptionUnder4000Chars() {
    val file = File(metadataDir, "full_description.txt")
    assumeTrue("full_description.txt not found — skipping", file.exists())

    val length = file.readText().length
    assertTrue(
      "full_description.txt is $length chars (max $MAX_FULL_DESCRIPTION_CHARS)",
      length <= MAX_FULL_DESCRIPTION_CHARS,
    )
  }
}
