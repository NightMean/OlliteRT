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

import com.ollitert.llm.server.common.SemVer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests cross-channel release filtering logic.
 *
 * Cross-channel means: the tag does NOT match the own-channel pattern but IS a valid
 * semver tag. This is how [UpdateCheckWorker.findCrossChannelRelease] identifies releases
 * from other channels to notify about.
 */
class CrossChannelFilterTest {

  private fun isCrossChannelFor(channel: String, tag: String): Boolean {
    val ownPattern = when (channel) {
      "stable" -> UpdateCheckWorker.STABLE_TAG_PATTERN
      "beta" -> UpdateCheckWorker.BETA_TAG_PATTERN
      "dev" -> UpdateCheckWorker.DEV_TAG_PATTERN
      else -> UpdateCheckWorker.STABLE_TAG_PATTERN
    }
    return !ownPattern.matches(tag) && SemVer.parse(tag) != null
  }

  // ── Stable user sees beta and dev as cross-channel ──────────────────────────

  @Test
  fun stableUserSeesBetaAsCrossChannel() {
    assertTrue(isCrossChannelFor("stable", "v1.0.0-beta.1"))
    assertTrue(isCrossChannelFor("stable", "v0.9.1-beta.2"))
    assertTrue(isCrossChannelFor("stable", "v2.0.0-beta.15"))
  }

  @Test
  fun stableUserSeesDevAsCrossChannel() {
    assertTrue(isCrossChannelFor("stable", "v1.0.0-dev.1"))
    assertTrue(isCrossChannelFor("stable", "v0.9.0-dev.6"))
    assertTrue(isCrossChannelFor("stable", "v2.0.0-dev.12"))
  }

  @Test
  fun stableUserDoesNotSeeStableAsCrossChannel() {
    assertFalse(isCrossChannelFor("stable", "v1.0.0"))
    assertFalse(isCrossChannelFor("stable", "v0.9.2"))
    assertFalse(isCrossChannelFor("stable", "v2.15.3"))
  }

  // ── Beta user sees dev as cross-channel, not stable or beta ─────────────────

  @Test
  fun betaUserSeesDevAsCrossChannel() {
    assertTrue(isCrossChannelFor("beta", "v1.0.0-dev.1"))
    assertTrue(isCrossChannelFor("beta", "v0.9.0-dev.6"))
    assertTrue(isCrossChannelFor("beta", "v2.0.0-dev.12"))
  }

  @Test
  fun betaUserDoesNotSeeStableAsCrossChannel() {
    assertFalse(isCrossChannelFor("beta", "v1.0.0"))
    assertFalse(isCrossChannelFor("beta", "v0.9.2"))
  }

  @Test
  fun betaUserDoesNotSeeBetaAsCrossChannel() {
    assertFalse(isCrossChannelFor("beta", "v1.0.0-beta.1"))
    assertFalse(isCrossChannelFor("beta", "v0.9.1-beta.2"))
  }

  // ── Dev user sees nothing as cross-channel (dev pattern matches all) ────────

  @Test
  fun devUserDoesNotSeeStableAsCrossChannel() {
    assertFalse(isCrossChannelFor("dev", "v1.0.0"))
    assertFalse(isCrossChannelFor("dev", "v0.9.2"))
  }

  @Test
  fun devUserDoesNotSeeBetaAsCrossChannel() {
    assertFalse(isCrossChannelFor("dev", "v1.0.0-beta.1"))
    assertFalse(isCrossChannelFor("dev", "v0.9.1-beta.2"))
  }

  @Test
  fun devUserDoesNotSeeDevAsCrossChannel() {
    assertFalse(isCrossChannelFor("dev", "v1.0.0-dev.1"))
    assertFalse(isCrossChannelFor("dev", "v0.9.0-dev.6"))
  }

  // ── Unparseable tags are never cross-channel ─────────────────────────────────

  @Test
  fun unparseableTagsAreNeverCrossChannel() {
    assertFalse(isCrossChannelFor("stable", "not-a-version"))
    assertFalse(isCrossChannelFor("stable", ""))
    assertFalse(isCrossChannelFor("stable", "v1.0"))
    assertFalse(isCrossChannelFor("beta", "release-1.0"))
    assertFalse(isCrossChannelFor("dev", ""))
  }

  @Test
  fun unknownPreReleaseTypesAreCrossChannelIfParseable() {
    // Tags with unrecognized suffixes (rc, alpha) are valid semver but don't match
    // any known channel pattern — they appear as cross-channel. In practice these
    // tags don't exist in the repo, but the filter correctly identifies them.
    assertTrue(isCrossChannelFor("stable", "v1.0.0-rc.1"))
    assertTrue(isCrossChannelFor("dev", "v1.0.0-alpha.1"))
  }

  // ── First-match behavior: stable user with mixed releases ───────────────────

  @Test
  fun stableUserGetsFirstNonStableFromList() {
    val tags = listOf("v0.9.2", "v0.9.1-beta.2", "v0.9.0-dev.6", "v0.9.1")
    val firstCrossChannel = tags.firstOrNull { isCrossChannelFor("stable", it) }
    assertTrue(firstCrossChannel == "v0.9.1-beta.2")
  }

  @Test
  fun stableUserWithOnlyStableReleasesGetsNoCrossChannel() {
    val tags = listOf("v0.9.2", "v0.9.1", "v0.9.0")
    val firstCrossChannel = tags.firstOrNull { isCrossChannelFor("stable", it) }
    assertNull(firstCrossChannel)
  }

  @Test
  fun betaUserGetsFirstDevFromList() {
    val tags = listOf("v0.9.2", "v0.9.1-beta.2", "v0.9.0-dev.6", "v0.9.1")
    val firstCrossChannel = tags.firstOrNull { isCrossChannelFor("beta", it) }
    assertTrue(firstCrossChannel == "v0.9.0-dev.6")
  }

  // ── Rapid releases: only latest is shown ────────────────────────────────────

  @Test
  fun rapidBetaReleasesOnlyShowsFirst() {
    val tags = listOf("v0.9.1-beta.2", "v0.9.1-beta.1", "v0.9.0-dev.3")
    val firstCrossChannel = tags.firstOrNull { isCrossChannelFor("stable", it) }
    assertTrue(firstCrossChannel == "v0.9.1-beta.2")
  }
}
