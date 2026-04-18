package com.ollitert.llm.server.service

import com.ollitert.llm.server.data.CORS_PREFLIGHT_MAX_AGE_SECONDS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmHttpCorsHelperTest {

  // ── Wildcard mode ──────────────────────────────────────────────────────────

  @Test
  fun wildcardAllowsAnyOrigin() {
    val headers = LlmHttpCorsHelper.buildCorsHeaders("*", "http://example.com")
    assertEquals("*", headers["Access-Control-Allow-Origin"])
    assertEquals("GET, POST, OPTIONS", headers["Access-Control-Allow-Methods"])
    assertEquals("Content-Type, Authorization, User-Agent, Accept, X-Requested-With", headers["Access-Control-Allow-Headers"])
    assertEquals(CORS_PREFLIGHT_MAX_AGE_SECONDS, headers["Access-Control-Max-Age"])
  }

  @Test
  fun wildcardWorksWithNullOrigin() {
    val headers = LlmHttpCorsHelper.buildCorsHeaders("*", null)
    assertEquals("*", headers["Access-Control-Allow-Origin"])
  }

  @Test
  fun wildcardDoesNotIncludeVaryHeader() {
    val headers = LlmHttpCorsHelper.buildCorsHeaders("*", "http://example.com")
    assertFalse(headers.containsKey("Vary"))
  }

  // ── Specific origins mode ──────────────────────────────────────────────────

  @Test
  fun specificOriginMatchReturnsRequestOrigin() {
    val headers = LlmHttpCorsHelper.buildCorsHeaders(
      "http://localhost:3000",
      "http://localhost:3000",
    )
    assertEquals("http://localhost:3000", headers["Access-Control-Allow-Origin"])
    assertEquals("Origin", headers["Vary"])
  }

  @Test
  fun specificOriginMismatchReturnsNoHeaders() {
    val headers = LlmHttpCorsHelper.buildCorsHeaders(
      "http://localhost:3000",
      "http://evil.com",
    )
    assertTrue(headers.isEmpty())
  }

  @Test
  fun multipleOriginsAllowMatchingOne() {
    val allowed = "http://localhost:3000, https://my-app.com, https://webui.local:8080"
    val headers = LlmHttpCorsHelper.buildCorsHeaders(allowed, "https://my-app.com")
    assertEquals("https://my-app.com", headers["Access-Control-Allow-Origin"])
    assertEquals("Origin", headers["Vary"])
  }

  @Test
  fun multipleOriginsRejectNonMatching() {
    val allowed = "http://localhost:3000, https://my-app.com"
    val headers = LlmHttpCorsHelper.buildCorsHeaders(allowed, "https://evil.com")
    assertTrue(headers.isEmpty())
  }

  @Test
  fun specificOriginsWithNullRequestOriginReturnsNoHeaders() {
    val headers = LlmHttpCorsHelper.buildCorsHeaders("http://localhost:3000", null)
    assertTrue(headers.isEmpty())
  }

  @Test
  fun originMatchingIsCaseInsensitive() {
    val headers = LlmHttpCorsHelper.buildCorsHeaders(
      "http://LOCALHOST:3000",
      "http://localhost:3000",
    )
    // Returns the request origin (not the configured one) so the browser recognizes it
    assertEquals("http://localhost:3000", headers["Access-Control-Allow-Origin"])
  }

  @Test
  fun originsWithExtraWhitespaceAreTrimmed() {
    val headers = LlmHttpCorsHelper.buildCorsHeaders(
      "  http://localhost:3000 ,  https://my-app.com  ",
      "https://my-app.com",
    )
    assertEquals("https://my-app.com", headers["Access-Control-Allow-Origin"])
  }

  // ── Disabled mode (empty/blank) ────────────────────────────────────────────

  @Test
  fun emptyStringDisablesCors() {
    val headers = LlmHttpCorsHelper.buildCorsHeaders("", "http://example.com")
    assertTrue(headers.isEmpty())
  }

  @Test
  fun blankStringDisablesCors() {
    val headers = LlmHttpCorsHelper.buildCorsHeaders("   ", "http://example.com")
    assertTrue(headers.isEmpty())
  }

  // ── Preflight helper ───────────────────────────────────────────────────────

  @Test
  fun preflightAllowedForWildcard() {
    assertTrue(LlmHttpCorsHelper.shouldAllowPreflight("*", "http://example.com"))
  }

  @Test
  fun preflightAllowedForMatchingOrigin() {
    assertTrue(LlmHttpCorsHelper.shouldAllowPreflight("http://localhost:3000", "http://localhost:3000"))
  }

  @Test
  fun preflightDeniedForNonMatchingOrigin() {
    assertFalse(LlmHttpCorsHelper.shouldAllowPreflight("http://localhost:3000", "http://evil.com"))
  }

  @Test
  fun preflightDeniedWhenCorsDisabled() {
    assertFalse(LlmHttpCorsHelper.shouldAllowPreflight("", "http://example.com"))
  }

  // ── Edge cases ─────────────────────────────────────────────────────────────

  @Test
  fun emptyOriginsInCommaListAreIgnored() {
    // "http://localhost:3000,," should not match empty strings
    val headers = LlmHttpCorsHelper.buildCorsHeaders("http://localhost:3000,,", "")
    assertTrue(headers.isEmpty())
  }

  @Test
  fun singleOriginWithTrailingComma() {
    val headers = LlmHttpCorsHelper.buildCorsHeaders(
      "http://localhost:3000,",
      "http://localhost:3000",
    )
    assertEquals("http://localhost:3000", headers["Access-Control-Allow-Origin"])
  }

  // ── CRLF injection defense ────────────────────────────────────────────────

  @Test
  fun crlfInOriginIsStrippedBeforeMatching() {
    // A tainted origin with CRLF should not match and should not be reflected
    val headers = LlmHttpCorsHelper.buildCorsHeaders(
      "http://localhost:3000",
      "http://localhost:3000\r\nX-Injected: evil",
    )
    // After stripping \r\n, the origin becomes "http://localhost:3000X-Injected: evil"
    // which doesn't match — no CORS headers returned
    assertTrue(headers.isEmpty())
  }

  @Test
  fun crlfInOriginStrippedStillMatchesIfClean() {
    // If the origin has a trailing \r\n but is otherwise valid, it should match
    // after sanitization (trailing CRLF stripped = clean origin)
    val headers = LlmHttpCorsHelper.buildCorsHeaders(
      "http://localhost:3000",
      "http://localhost:3000\r\n",
    )
    // After stripping, origin = "http://localhost:3000" which matches
    assertEquals("http://localhost:3000", headers["Access-Control-Allow-Origin"])
  }

  @Test
  fun reflectedOriginDoesNotContainCrLf() {
    // Even if the origin somehow matches, the reflected value must be clean
    val headers = LlmHttpCorsHelper.buildCorsHeaders(
      "http://localhost:3000",
      "http://localhost:3000\r\n",
    )
    val reflected = headers["Access-Control-Allow-Origin"] ?: ""
    assertFalse(reflected.contains("\r"))
    assertFalse(reflected.contains("\n"))
  }
}
