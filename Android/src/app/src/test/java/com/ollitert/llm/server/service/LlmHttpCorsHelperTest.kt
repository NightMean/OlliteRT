package com.ollitert.llm.server.service

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
    assertEquals("86400", headers["Access-Control-Max-Age"])
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
}
