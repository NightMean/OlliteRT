/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ollitert.llm.server.ui.modelmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowlistLoadGenerationGateTest {

  @Test
  fun olderResultCannotPublishAfterNewerLoadBegins() {
    val gate = AllowlistLoadGenerationGate()
    val olderGeneration = gate.beginLoad()
    val newerGeneration = gate.beginLoad()
    val publishedValues = mutableListOf<String>()

    val olderPublished = gate.publishIfCurrent(olderGeneration) {
      publishedValues += "older"
    }
    val newerPublished = gate.publishIfCurrent(newerGeneration) {
      publishedValues += "newer"
    }

    assertFalse(olderPublished)
    assertTrue(newerPublished)
    assertEquals(listOf("newer"), publishedValues)
  }

  @Test
  fun currentResultPublicationRuns() {
    val gate = AllowlistLoadGenerationGate()
    val generation = gate.beginLoad()
    var publicationCount = 0

    val wasPublished = gate.publishIfCurrent(generation) {
      publicationCount += 1
    }

    assertTrue(wasPublished)
    assertEquals(1, publicationCount)
  }
}
