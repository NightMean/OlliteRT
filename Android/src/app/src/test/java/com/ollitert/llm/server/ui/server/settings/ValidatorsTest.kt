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

package com.ollitert.llm.server.ui.server.settings

import com.ollitert.llm.server.data.MAX_VALID_PORT
import com.ollitert.llm.server.data.MIN_VALID_PORT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

  // ─── isValidCorsOrigins ──────────────────────────────────────────────────

  @Test
  fun `cors - blank input is valid`() {
    assertTrue(isValidCorsOrigins(""))
    assertTrue(isValidCorsOrigins("   "))
  }

  @Test
  fun `cors - wildcard is valid`() {
    assertTrue(isValidCorsOrigins("*"))
  }

  @Test
  fun `cors - single http origin is valid`() {
    assertTrue(isValidCorsOrigins("http://localhost:3000"))
  }

  @Test
  fun `cors - single https origin is valid`() {
    assertTrue(isValidCorsOrigins("https://example.com"))
  }

  @Test
  fun `cors - comma-separated origins are valid`() {
    assertTrue(isValidCorsOrigins("http://localhost:3000, https://example.com"))
  }

  @Test
  fun `cors - origin without scheme is invalid`() {
    assertFalse(isValidCorsOrigins("localhost:3000"))
  }

  @Test
  fun `cors - ftp scheme is invalid`() {
    assertFalse(isValidCorsOrigins("ftp://example.com"))
  }

  @Test
  fun `cors - origin with space in host is invalid`() {
    assertFalse(isValidCorsOrigins("http://exam ple.com"))
  }

  @Test
  fun `cors - origin with leading slash after scheme is invalid`() {
    assertFalse(isValidCorsOrigins("http:///path"))
  }

  @Test
  fun `cors - empty host after scheme is invalid`() {
    assertFalse(isValidCorsOrigins("http://"))
  }

  @Test
  fun `cors - mixed valid and invalid origins are invalid`() {
    assertFalse(isValidCorsOrigins("http://localhost, no-protocol"))
  }

  @Test
  fun `cors - origin with trailing whitespace is valid`() {
    assertTrue(isValidCorsOrigins("  http://localhost:8080  "))
  }

  // ─── SettingDef constraint consistency ───────────────────────────────────

  @Test
  fun `CORS_ORIGINS has validate lambda`() {
    assertTrue(
      "CORS_ORIGINS should have a validate lambda",
      CORS_ORIGINS.validate != null,
    )
  }

  @Test
  fun `CORS_ORIGINS validate lambda rejects invalid input`() {
    val validate = CORS_ORIGINS.validate!!
    assertTrue("Valid wildcard should return null", validate("*") == null)
    assertTrue("Invalid input should return error", validate("no-protocol") != null)
  }

  @Test
  fun `NumericWithUnit definitions have baseUnitLabel`() {
    val defs = allSettingDefs.filterIsInstance<SettingDef.NumericWithUnit>()
    assertTrue("Should have NumericWithUnit defs", defs.isNotEmpty())
    for (def in defs) {
      assertTrue(
        "${def.key} should have non-blank baseUnitLabel",
        def.baseUnitLabel.isNotBlank(),
      )
    }
  }

  @Test
  fun `HOST_PORT range is 1024 to 65535`() {
    assertEquals(MIN_VALID_PORT, HOST_PORT.min)
    assertEquals(MAX_VALID_PORT, HOST_PORT.max)
  }

  @Test
  fun `KEEP_ALIVE_TIMEOUT range is 1 to 7200 minutes`() {
    assertEquals(1L, KEEP_ALIVE_TIMEOUT.min)
    assertEquals(7200L, KEEP_ALIVE_TIMEOUT.max)
    assertEquals("minutes", KEEP_ALIVE_TIMEOUT.baseUnitLabel)
  }

  @Test
  fun `CHECK_FREQUENCY range is 1 to 720 hours`() {
    assertEquals(1L, CHECK_FREQUENCY.min)
    assertEquals(720L, CHECK_FREQUENCY.max)
    assertEquals("hours", CHECK_FREQUENCY.baseUnitLabel)
  }

  @Test
  fun `LOG_MAX_ENTRIES range is 0 to 99999`() {
    assertEquals(0, LOG_MAX_ENTRIES.min)
    assertEquals(99999, LOG_MAX_ENTRIES.max)
  }

  @Test
  fun `LOG_AUTO_DELETE range is 0 to 525600 minutes`() {
    assertEquals(0L, LOG_AUTO_DELETE.min)
    assertEquals(525600L, LOG_AUTO_DELETE.max)
    assertEquals("minutes", LOG_AUTO_DELETE.baseUnitLabel)
  }

  @Test
  fun `cors - trailing comma is invalid`() {
    assertFalse(isValidCorsOrigins("http://localhost,"))
  }

  @Test
  fun `cors - double comma produces empty entry and is invalid`() {
    assertFalse(isValidCorsOrigins("http://a,,http://b"))
  }
}
