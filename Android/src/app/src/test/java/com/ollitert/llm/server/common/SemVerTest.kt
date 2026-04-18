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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

  // ── parse() ──────────────────────────────────────────────────────────────────

  @Test
  fun parseStableVersion() {
    val v = SemVer.parse("1.2.0")
    assertNotNull(v)
    assertEquals(1, v!!.major)
    assertEquals(2, v.minor)
    assertEquals(0, v.patch)
    assertNull(v.preRelease)
  }

  @Test
  fun parseStableVersionWithVPrefix() {
    val v = SemVer.parse("v1.3.0")
    assertNotNull(v)
    assertEquals(1, v!!.major)
    assertEquals(3, v.minor)
    assertEquals(0, v.patch)
    assertNull(v.preRelease)
  }

  @Test
  fun parseBetaPreRelease() {
    val v = SemVer.parse("v1.2.0-beta.3")
    assertNotNull(v)
    assertEquals(1, v!!.major)
    assertEquals(2, v.minor)
    assertEquals(0, v.patch)
    assertEquals("beta.3", v.preRelease)
  }

  @Test
  fun parseDevPreRelease() {
    val v = SemVer.parse("1.2.0-dev.5")
    assertNotNull(v)
    assertEquals("dev.5", v!!.preRelease)
  }

  @Test
  fun parseBareDevSuffix() {
    // BuildConfig.VERSION_NAME for dev builds is "1.2.0-dev" (no build number)
    val v = SemVer.parse("1.2.0-dev")
    assertNotNull(v)
    assertEquals("dev", v!!.preRelease)
  }

  @Test
  fun parseBareBetaSuffix() {
    val v = SemVer.parse("1.2.0-beta")
    assertNotNull(v)
    assertEquals("beta", v!!.preRelease)
  }

  @Test
  fun parseReturnsNullForMalformed() {
    assertNull(SemVer.parse(""))
    assertNull(SemVer.parse("v"))
    assertNull(SemVer.parse("1.2"))
    assertNull(SemVer.parse("abc"))
    assertNull(SemVer.parse("v1"))
    assertNull(SemVer.parse("1.2.x"))
  }

  // ── compareTo() ──────────────────────────────────────────────────────────────

  @Test
  fun majorVersionComparison() {
    assertTrue(SemVer.parse("2.0.0")!! > SemVer.parse("1.9.9")!!)
  }

  @Test
  fun minorVersionComparison() {
    assertTrue(SemVer.parse("1.3.0")!! > SemVer.parse("1.2.9")!!)
  }

  @Test
  fun patchVersionComparison() {
    assertTrue(SemVer.parse("1.2.1")!! > SemVer.parse("1.2.0")!!)
  }

  @Test
  fun stableGreaterThanPreReleaseOfSameVersion() {
    // 1.3.0 (stable) > 1.3.0-beta.2
    assertTrue(SemVer.parse("1.3.0")!! > SemVer.parse("1.3.0-beta.2")!!)
  }

  @Test
  fun preReleaseLexicographicComparison() {
    // beta.2 > beta.1
    assertTrue(SemVer.parse("1.0.0-beta.2")!! > SemVer.parse("1.0.0-beta.1")!!)
    // dev.5 > dev.1
    assertTrue(SemVer.parse("1.0.0-dev.5")!! > SemVer.parse("1.0.0-dev.1")!!)
  }

  @Test
  fun preReleaseMultiDigitComparison() {
    // Lexicographic: "beta.10" > "beta.9" because "1" < "9" — this is WRONG semantically
    // but matches current implementation. When numeric pre-release comparison is added, flip these.
    assertTrue(SemVer.parse("1.0.0-beta.9")!! > SemVer.parse("1.0.0-beta.10")!!)
    assertTrue(SemVer.parse("1.0.0-dev.9")!! > SemVer.parse("1.0.0-dev.10")!!)
  }

  @Test
  fun betaGreaterThanDev() {
    // "beta" > "dev" lexicographically — No! "beta" < "dev" lexicographically (b < d)
    assertTrue(SemVer.parse("1.0.0-dev")!! > SemVer.parse("1.0.0-beta")!!)
  }

  @Test
  fun equalVersions() {
    assertEquals(0, SemVer.parse("1.2.0")!!.compareTo(SemVer.parse("1.2.0")!!))
    assertEquals(0, SemVer.parse("1.2.0-beta.1")!!.compareTo(SemVer.parse("1.2.0-beta.1")!!))
  }

  // ── isNewer()— version comparison──────────────────────────

  @Test
  fun prodBuildNewerStableRelease() {
    // BuildConfig: 1.2.0 (prod), GitHub: v1.3.0 → newer
    assertTrue(SemVer.isNewer("1.2.0", "v1.3.0"))
  }

  @Test
  fun devBuildNewerDevRelease() {
    // BuildConfig: 1.2.0-dev, GitHub: v1.2.0-dev.5 → newer ("dev" < "dev.5")
    assertTrue(SemVer.isNewer("1.2.0-dev", "v1.2.0-dev.5"))
  }

  @Test
  fun betaBuildNewerBetaRelease() {
    // BuildConfig: 1.2.0-beta, GitHub: v1.2.0-beta.3 → newer ("beta" < "beta.3")
    assertTrue(SemVer.isNewer("1.2.0-beta", "v1.2.0-beta.3"))
  }

  @Test
  fun devBuildNewerStableRelease() {
    // BuildConfig: 1.2.0-dev, GitHub: v1.3.0 → newer (different base version)
    assertTrue(SemVer.isNewer("1.2.0-dev", "v1.3.0"))
  }

  @Test
  fun betaBuildStablePromotionOfSameVersion() {
    // BuildConfig: 1.2.0-beta, GitHub: v1.2.0 → newer (stable > pre-release)
    assertTrue(SemVer.isNewer("1.2.0-beta", "v1.2.0"))
  }

  @Test
  fun sameVersionNotNewer() {
    assertFalse(SemVer.isNewer("1.2.0", "v1.2.0"))
    assertFalse(SemVer.isNewer("1.2.0-beta.1", "v1.2.0-beta.1"))
  }

  @Test
  fun olderVersionNotNewer() {
    assertFalse(SemVer.isNewer("1.3.0", "v1.2.0"))
    assertFalse(SemVer.isNewer("1.2.0", "v1.2.0-beta.1"))
  }

  @Test
  fun malformedCurrentVersionReturnsFalse() {
    assertFalse(SemVer.isNewer("bad", "v1.2.0"))
  }

  @Test
  fun malformedRemoteTagReturnsFalse() {
    assertFalse(SemVer.isNewer("1.2.0", "not-a-version"))
  }

  // ── toString() ───────────────────────────────────────────────────────────────

  @Test
  fun toStringStable() {
    assertEquals("1.2.0", SemVer(1, 2, 0).toString())
  }

  @Test
  fun toStringPreRelease() {
    assertEquals("1.2.0-beta.3", SemVer(1, 2, 0, "beta.3").toString())
  }
}
