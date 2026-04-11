package com.ollitert.llm.server.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests channel-aware tag pattern matching used by [UpdateCheckWorker]
 * to filter GitHub release tags for the correct update channel.
 *
 * Tag convention:
 * - Stable: vX.Y.Z (no suffix)
 * - Beta: vX.Y.Z-beta.N
 * - Dev: vX.Y.Z-dev.N
 */
class UpdateCheckTagPatternsTest {

  // ── STABLE_TAG_PATTERN — matches only vX.Y.Z (no pre-release suffix) ────────

  @Test
  fun stableMatchesCleanVersion() {
    assertTrue(UpdateCheckWorker.STABLE_TAG_PATTERN.matches("v1.0.0"))
    assertTrue(UpdateCheckWorker.STABLE_TAG_PATTERN.matches("v2.15.3"))
    assertTrue(UpdateCheckWorker.STABLE_TAG_PATTERN.matches("v0.0.1"))
  }

  @Test
  fun stableRejectsBetaTags() {
    assertFalse(UpdateCheckWorker.STABLE_TAG_PATTERN.matches("v1.0.0-beta.1"))
    assertFalse(UpdateCheckWorker.STABLE_TAG_PATTERN.matches("v1.0.0-beta.15"))
  }

  @Test
  fun stableRejectsDevTags() {
    assertFalse(UpdateCheckWorker.STABLE_TAG_PATTERN.matches("v1.0.0-dev.1"))
    assertFalse(UpdateCheckWorker.STABLE_TAG_PATTERN.matches("v1.0.0-dev.5"))
  }

  @Test
  fun stableRejectsMalformedTags() {
    assertFalse(UpdateCheckWorker.STABLE_TAG_PATTERN.matches("1.0.0"))       // no v prefix
    assertFalse(UpdateCheckWorker.STABLE_TAG_PATTERN.matches("v1.0"))        // only 2 parts
    assertFalse(UpdateCheckWorker.STABLE_TAG_PATTERN.matches("v1.0.0-rc.1")) // arbitrary suffix
    assertFalse(UpdateCheckWorker.STABLE_TAG_PATTERN.matches(""))
    assertFalse(UpdateCheckWorker.STABLE_TAG_PATTERN.matches("release-1.0"))
  }

  // ── BETA_TAG_PATTERN — matches vX.Y.Z and vX.Y.Z-beta.N ────────────────────

  @Test
  fun betaMatchesStableTags() {
    // Beta channel users should also be notified about stable releases
    assertTrue(UpdateCheckWorker.BETA_TAG_PATTERN.matches("v1.0.0"))
    assertTrue(UpdateCheckWorker.BETA_TAG_PATTERN.matches("v2.15.3"))
  }

  @Test
  fun betaMatchesBetaTags() {
    assertTrue(UpdateCheckWorker.BETA_TAG_PATTERN.matches("v1.0.0-beta.1"))
    assertTrue(UpdateCheckWorker.BETA_TAG_PATTERN.matches("v1.0.0-beta.15"))
    assertTrue(UpdateCheckWorker.BETA_TAG_PATTERN.matches("v2.3.1-beta.2"))
  }

  @Test
  fun betaRejectsDevTags() {
    assertFalse(UpdateCheckWorker.BETA_TAG_PATTERN.matches("v1.0.0-dev.1"))
    assertFalse(UpdateCheckWorker.BETA_TAG_PATTERN.matches("v1.0.0-dev.5"))
  }

  @Test
  fun betaRejectsMalformedTags() {
    assertFalse(UpdateCheckWorker.BETA_TAG_PATTERN.matches("1.0.0-beta.1")) // no v prefix
    assertFalse(UpdateCheckWorker.BETA_TAG_PATTERN.matches("v1.0.0-beta"))  // no build number
    assertFalse(UpdateCheckWorker.BETA_TAG_PATTERN.matches("v1.0.0-rc.1"))  // wrong suffix
  }

  // ── DEV_TAG_PATTERN — matches vX.Y.Z, vX.Y.Z-beta.N, and vX.Y.Z-dev.N ─────

  @Test
  fun devMatchesStableTags() {
    assertTrue(UpdateCheckWorker.DEV_TAG_PATTERN.matches("v1.0.0"))
    assertTrue(UpdateCheckWorker.DEV_TAG_PATTERN.matches("v2.15.3"))
  }

  @Test
  fun devMatchesBetaTags() {
    assertTrue(UpdateCheckWorker.DEV_TAG_PATTERN.matches("v1.0.0-beta.1"))
    assertTrue(UpdateCheckWorker.DEV_TAG_PATTERN.matches("v1.0.0-beta.15"))
  }

  @Test
  fun devMatchesDevTags() {
    assertTrue(UpdateCheckWorker.DEV_TAG_PATTERN.matches("v1.0.0-dev.1"))
    assertTrue(UpdateCheckWorker.DEV_TAG_PATTERN.matches("v1.0.0-dev.5"))
    assertTrue(UpdateCheckWorker.DEV_TAG_PATTERN.matches("v2.3.1-dev.12"))
  }

  @Test
  fun devRejectsArbitrarySuffixes() {
    // Dev matches dev and beta only, not arbitrary suffixes
    assertFalse(UpdateCheckWorker.DEV_TAG_PATTERN.matches("v1.0.0-rc.1"))
    assertFalse(UpdateCheckWorker.DEV_TAG_PATTERN.matches("v1.0.0-alpha.1"))
    assertFalse(UpdateCheckWorker.DEV_TAG_PATTERN.matches("v1.0.0-nightly.1"))
  }

  @Test
  fun devRejectsMalformedTags() {
    assertFalse(UpdateCheckWorker.DEV_TAG_PATTERN.matches("1.0.0-dev.1"))  // no v prefix
    assertFalse(UpdateCheckWorker.DEV_TAG_PATTERN.matches("v1.0.0-dev"))   // no build number
    assertFalse(UpdateCheckWorker.DEV_TAG_PATTERN.matches("v1.0-dev.1"))   // only 2 version parts
  }

  // ── Channel hierarchy ───────────────────────────────────────────────────────

  @Test
  fun channelHierarchyIsCorrect() {
    // Stable is the most restrictive, dev is the most permissive
    val stableTag = "v1.0.0"
    val betaTag = "v1.0.0-beta.1"
    val devTag = "v1.0.0-dev.1"

    // Stable pattern: only stable
    assertTrue(UpdateCheckWorker.STABLE_TAG_PATTERN.matches(stableTag))
    assertFalse(UpdateCheckWorker.STABLE_TAG_PATTERN.matches(betaTag))
    assertFalse(UpdateCheckWorker.STABLE_TAG_PATTERN.matches(devTag))

    // Beta pattern: stable + beta
    assertTrue(UpdateCheckWorker.BETA_TAG_PATTERN.matches(stableTag))
    assertTrue(UpdateCheckWorker.BETA_TAG_PATTERN.matches(betaTag))
    assertFalse(UpdateCheckWorker.BETA_TAG_PATTERN.matches(devTag))

    // Dev pattern: stable + beta + dev
    assertTrue(UpdateCheckWorker.DEV_TAG_PATTERN.matches(stableTag))
    assertTrue(UpdateCheckWorker.DEV_TAG_PATTERN.matches(betaTag))
    assertTrue(UpdateCheckWorker.DEV_TAG_PATTERN.matches(devTag))
  }
}
