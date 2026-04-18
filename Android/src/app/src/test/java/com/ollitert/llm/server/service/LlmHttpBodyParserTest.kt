package com.ollitert.llm.server.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmHttpBodyParserTest {
  @Test
  fun returnsBodyAndCharCount() {
    val parsed = LlmHttpBodyParser.parse("Hello")

    requireNotNull(parsed)
    assertEquals("Hello", parsed.body)
    assertEquals(5, parsed.bodyLength)
  }

  @Test
  fun returnsNullForMissingBody() {
    assertNull(LlmHttpBodyParser.parse(null))
  }

  @Test
  fun emptyStringReturnsZeroLength() {
    val parsed = LlmHttpBodyParser.parse("")

    requireNotNull(parsed)
    assertEquals("", parsed.body)
    assertEquals(0, parsed.bodyLength)
  }

  @Test
  fun multibyteCountsCharNotBytes() {
    // "ñ" is 1 char but 2 UTF-8 bytes — verifies we count chars.
    val parsed = LlmHttpBodyParser.parse("ñ")

    requireNotNull(parsed)
    assertEquals(1, parsed.bodyLength)
  }

  @Test
  fun emojiCountsSurrogatePairLength() {
    // "👋" is 1 code point, 2 UTF-16 code units, 4 UTF-8 bytes.
    val parsed = LlmHttpBodyParser.parse("👋")

    requireNotNull(parsed)
    assertEquals(2, parsed.bodyLength)
  }
}
