package com.ollitert.llm.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Basic JVM smoke tests for the gallery app.
 */
class SmokeTest {

  @Test
  fun packageNameIsCorrect() {
    assertEquals("com.ollitert.llm.server", SmokeTest::class.java.packageName)
  }

  @Test
  fun thinkingTagParsingOpensCorrectly() {
    val input = "<think>reasoning here</think>answer"
    val openIdx = input.indexOf("<think>")
    assertEquals(0, openIdx)
    val afterOpen = input.substring(openIdx + "<think>".length)
    val closeIdx = afterOpen.indexOf("</think>")
    assertEquals(14, closeIdx)
    val thinking = afterOpen.substring(0, closeIdx)
    assertEquals("reasoning here", thinking)
    val answer = afterOpen.substring(closeIdx + "</think>".length)
    assertEquals("answer", answer)
  }

  @Test
  fun thinkingTagParsingNoTags() {
    val input = "plain answer without thinking"
    val openIdx = input.indexOf("<think>")
    assertEquals(-1, openIdx)
  }

  @Test
  fun thinkingTagParsingPartialToken() {
    val input = "some text"
    val closeIdx = input.indexOf("</think>")
    assertEquals(-1, closeIdx)
  }

  @Test
  fun modelKeyNormalization() {
    val normalize = { value: String ->
      value.lowercase().replace(Regex("[^a-z0-9]"), "")
    }
    assertEquals("gemma31bit", normalize("Gemma3-1B-IT"))
    assertEquals("gemma31bit", normalize("gemma3-1b-it"))
    assertEquals("gemma31bit", normalize("GEMMA3_1B_IT"))
  }

  @Test
  fun sseEscaping() {
    val esc = { s: String ->
      s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }
    assertEquals("hello world", esc("hello world"))
    assertEquals("line1\\nline2", esc("line1\nline2"))
    assertEquals("say \\\"hi\\\"", esc("say \"hi\""))
    assertEquals("back\\\\slash", esc("back\\slash"))
  }
}
