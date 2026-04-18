package com.ollitert.llm.server.ui.server.settings

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
    assertTrue(HOST_PORT.min == 1024)
    assertTrue(HOST_PORT.max == 65535)
  }

  @Test
  fun `KEEP_ALIVE_TIMEOUT range is 1 to 7200 minutes`() {
    assertTrue(KEEP_ALIVE_TIMEOUT.min == 1L)
    assertTrue(KEEP_ALIVE_TIMEOUT.max == 7200L)
    assertTrue(KEEP_ALIVE_TIMEOUT.baseUnitLabel == "minutes")
  }

  @Test
  fun `CHECK_FREQUENCY range is 1 to 720 hours`() {
    assertTrue(CHECK_FREQUENCY.min == 1L)
    assertTrue(CHECK_FREQUENCY.max == 720L)
    assertTrue(CHECK_FREQUENCY.baseUnitLabel == "hours")
  }

  @Test
  fun `LOG_MAX_ENTRIES range is 0 to 99999`() {
    assertTrue(LOG_MAX_ENTRIES.min == 0)
    assertTrue(LOG_MAX_ENTRIES.max == 99999)
  }

  @Test
  fun `LOG_AUTO_DELETE range is 0 to 525600 minutes`() {
    assertTrue(LOG_AUTO_DELETE.min == 0L)
    assertTrue(LOG_AUTO_DELETE.max == 525600L)
    assertTrue(LOG_AUTO_DELETE.baseUnitLabel == "minutes")
  }
}
