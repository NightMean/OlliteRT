package com.ollitert.llm.server.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmHttpBodyParserTest {
  @Test
  fun returnsBodyAndUtf8ByteCount() {
    val parsed = LlmHttpBodyParser.parse("hola")

    requireNotNull(parsed)
    assertEquals("hola", parsed.body)
    assertEquals(4, parsed.bodyBytes)
  }

  @Test
  fun returnsNullForMissingBody() {
    assertNull(LlmHttpBodyParser.parse(null))
  }

  @Test
  fun countsUtf8BytesPrecisely() {
    assertEquals(2, LlmHttpBodyParser.bodySizeBytes("ñ"))
  }
}
