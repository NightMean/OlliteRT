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

package com.ollitert.llm.server.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseParsingTest {

  // ── parseRelease ───────────────────────────────────────────────────────────

  @Test
  fun `parseRelease returns ReleaseInfo for valid JSON`() {
    val json = """{"tag_name":"v1.2.0","html_url":"https://github.com/repo/releases/tag/v1.2.0"}"""
    val result = UpdateCheckWorker.parseRelease(json, "etag-123")
    assertNotNull(result)
    assertEquals("v1.2.0", result!!.tagName)
    assertEquals("https://github.com/repo/releases/tag/v1.2.0", result.htmlUrl)
    assertEquals("etag-123", result.etag)
  }

  @Test
  fun `parseRelease returns null when tag_name is missing`() {
    val json = """{"html_url":"https://github.com/repo/releases/tag/v1.2.0"}"""
    assertNull(UpdateCheckWorker.parseRelease(json, null))
  }

  @Test
  fun `parseRelease returns null when tag_name is blank`() {
    val json = """{"tag_name":"","html_url":"https://github.com/repo/releases/tag/v1.2.0"}"""
    assertNull(UpdateCheckWorker.parseRelease(json, null))
  }

  @Test
  fun `parseRelease returns null when html_url is missing`() {
    val json = """{"tag_name":"v1.2.0"}"""
    assertNull(UpdateCheckWorker.parseRelease(json, null))
  }

  @Test
  fun `parseRelease returns null when html_url is blank`() {
    val json = """{"tag_name":"v1.2.0","html_url":""}"""
    assertNull(UpdateCheckWorker.parseRelease(json, null))
  }

  @Test
  fun `parseRelease passes null etag through`() {
    val json = """{"tag_name":"v1.0.0","html_url":"https://example.com"}"""
    val result = UpdateCheckWorker.parseRelease(json, null)
    assertNotNull(result)
    assertNull(result!!.etag)
  }

  // ── findBestRelease ────────────────────────────────────────────────────────

  private val stablePattern = UpdateCheckWorker.STABLE_TAG_PATTERN
  private val betaPattern = UpdateCheckWorker.BETA_TAG_PATTERN
  private val devPattern = UpdateCheckWorker.DEV_TAG_PATTERN

  private fun releasesJson(vararg releases: String): String {
    return "[${releases.joinToString(",")}]"
  }

  private fun release(
    tagName: String,
    htmlUrl: String = "https://github.com/repo/releases/tag/$tagName",
    draft: Boolean = false,
  ): String {
    return """{"tag_name":"$tagName","html_url":"$htmlUrl","draft":$draft}"""
  }

  @Test
  fun `findBestRelease returns first matching stable release`() {
    val json = releasesJson(
      release("v1.2.0"),
      release("v1.1.0"),
    )
    val result = UpdateCheckWorker.findBestRelease(json, stablePattern)
    assertNotNull(result)
    assertEquals("v1.2.0", result!!.tagName)
  }

  @Test
  fun `findBestRelease skips drafts`() {
    val json = releasesJson(
      release("v1.2.0", draft = true),
      release("v1.1.0"),
    )
    val result = UpdateCheckWorker.findBestRelease(json, stablePattern)
    assertNotNull(result)
    assertEquals("v1.1.0", result!!.tagName)
  }

  @Test
  fun `findBestRelease filters by tag pattern`() {
    val json = releasesJson(
      release("v1.2.0-dev.1"),
      release("v1.1.0-beta.1"),
      release("v1.0.0"),
    )
    val result = UpdateCheckWorker.findBestRelease(json, stablePattern)
    assertNotNull(result)
    assertEquals("v1.0.0", result!!.tagName)
  }

  @Test
  fun `findBestRelease with beta pattern matches stable and beta`() {
    val json = releasesJson(
      release("v1.2.0-dev.1"),
      release("v1.1.0-beta.2"),
      release("v1.0.0"),
    )
    val result = UpdateCheckWorker.findBestRelease(json, betaPattern)
    assertNotNull(result)
    assertEquals("v1.1.0-beta.2", result!!.tagName)
  }

  @Test
  fun `findBestRelease with dev pattern matches all valid tags`() {
    val json = releasesJson(
      release("v1.2.0-dev.1"),
      release("v1.1.0-beta.2"),
      release("v1.0.0"),
    )
    val result = UpdateCheckWorker.findBestRelease(json, devPattern)
    assertNotNull(result)
    assertEquals("v1.2.0-dev.1", result!!.tagName)
  }

  @Test
  fun `findBestRelease returns null for empty list`() {
    assertNull(UpdateCheckWorker.findBestRelease("[]", stablePattern))
  }

  @Test
  fun `findBestRelease returns null when no tags match pattern`() {
    val json = releasesJson(
      release("v1.0.0-dev.1"),
      release("v1.0.0-beta.1"),
    )
    assertNull(UpdateCheckWorker.findBestRelease(json, stablePattern))
  }

  @Test
  fun `findBestRelease skips entries with blank tag_name`() {
    val json = """[{"tag_name":"","html_url":"https://example.com","draft":false},{"tag_name":"v1.0.0","html_url":"https://example.com/v1","draft":false}]"""
    val result = UpdateCheckWorker.findBestRelease(json, stablePattern)
    assertNotNull(result)
    assertEquals("v1.0.0", result!!.tagName)
  }

  @Test
  fun `findBestRelease skips entries with blank html_url`() {
    val json = """[{"tag_name":"v2.0.0","html_url":"","draft":false},{"tag_name":"v1.0.0","html_url":"https://example.com","draft":false}]"""
    val result = UpdateCheckWorker.findBestRelease(json, stablePattern)
    assertNotNull(result)
    assertEquals("v1.0.0", result!!.tagName)
  }

  @Test
  fun `findBestRelease skips all drafts and returns null`() {
    val json = releasesJson(
      release("v1.2.0", draft = true),
      release("v1.1.0", draft = true),
    )
    assertNull(UpdateCheckWorker.findBestRelease(json, stablePattern))
  }

  // ── findCrossChannelRelease ────────────────────────────────────────────────

  @Test
  fun `findCrossChannelRelease for stable user finds beta`() {
    val json = releasesJson(
      release("v1.0.0"),
      release("v1.1.0-beta.1"),
      release("v1.0.0-dev.3"),
    )
    val result = UpdateCheckWorker.findCrossChannelRelease(json, stablePattern)
    assertNotNull(result)
    assertEquals("v1.1.0-beta.1", result!!.tagName)
  }

  @Test
  fun `findCrossChannelRelease for stable user finds dev`() {
    val json = releasesJson(
      release("v1.0.0"),
      release("v1.0.0-dev.3"),
    )
    val result = UpdateCheckWorker.findCrossChannelRelease(json, stablePattern)
    assertNotNull(result)
    assertEquals("v1.0.0-dev.3", result!!.tagName)
  }

  @Test
  fun `findCrossChannelRelease for beta user finds dev`() {
    val json = releasesJson(
      release("v1.1.0-beta.1"),
      release("v1.0.0"),
      release("v1.0.0-dev.3"),
    )
    val result = UpdateCheckWorker.findCrossChannelRelease(json, betaPattern)
    assertNotNull(result)
    assertEquals("v1.0.0-dev.3", result!!.tagName)
  }

  @Test
  fun `findCrossChannelRelease for dev user returns null`() {
    val json = releasesJson(
      release("v1.1.0-beta.1"),
      release("v1.0.0"),
      release("v1.0.0-dev.3"),
    )
    assertNull(UpdateCheckWorker.findCrossChannelRelease(json, devPattern))
  }

  @Test
  fun `findCrossChannelRelease skips drafts`() {
    val json = releasesJson(
      release("v1.1.0-beta.1", draft = true),
      release("v1.0.0-dev.3"),
    )
    val result = UpdateCheckWorker.findCrossChannelRelease(json, stablePattern)
    assertNotNull(result)
    assertEquals("v1.0.0-dev.3", result!!.tagName)
  }

  @Test
  fun `findCrossChannelRelease skips invalid semver tags`() {
    val json = """[{"tag_name":"not-a-version","html_url":"https://example.com","draft":false},{"tag_name":"v1.0.0-beta.1","html_url":"https://example.com/beta","draft":false}]"""
    val result = UpdateCheckWorker.findCrossChannelRelease(json, stablePattern)
    assertNotNull(result)
    assertEquals("v1.0.0-beta.1", result!!.tagName)
  }

  @Test
  fun `findCrossChannelRelease returns null when only own-channel releases exist`() {
    val json = releasesJson(
      release("v1.2.0"),
      release("v1.1.0"),
    )
    assertNull(UpdateCheckWorker.findCrossChannelRelease(json, stablePattern))
  }

  @Test
  fun `findCrossChannelRelease returns null for empty list`() {
    assertNull(UpdateCheckWorker.findCrossChannelRelease("[]", stablePattern))
  }
}
