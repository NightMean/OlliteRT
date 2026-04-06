package com.ollite.llm.server.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmHttpBodyParserTest {
  @Test
  fun returnsBodyAndUtf8ByteCountWhenWithinLimit() {
    val parsed = LlmHttpBodyParser.parse("hola", maxBodyBytes = 10)

    requireNotNull(parsed)
    assertEquals("hola", parsed.body)
    assertEquals(4, parsed.bodyBytes)
  }

  @Test
  fun returnsNullForMissingBody() {
    assertNull(LlmHttpBodyParser.parse(null, maxBodyBytes = 10))
  }

  @Test
  fun returnsNullWhenUtf8SizeExceedsLimit() {
    assertNull(LlmHttpBodyParser.parse("abcdef", maxBodyBytes = 5))
  }

  @Test
  fun countsUtf8BytesPrecisely() {
    assertEquals(2, LlmHttpBodyParser.bodySizeBytes("ñ"))
  }
}
