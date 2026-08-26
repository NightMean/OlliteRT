/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.ollitert.llm.server.data.repository

import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.ModelDataFile
import com.ollitert.llm.server.data.storage.KEY_MODEL_TOTAL_BYTES
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadRequestDataTest {
  @Test
  fun buildDownloadRequestDataUsesCanonicalTotalIncludingExtraFilesOnce() {
    val model =
      Model(
        name = "multi-file-model",
        sizeInBytes = 100L,
        extraDataFiles =
          listOf(
            ModelDataFile(
              url = "https://example.test/vision.bin",
              downloadFileName = "vision.bin",
              sizeInBytes = 25L,
            ),
          ),
      )
    model.preProcess()

    val inputData = buildDownloadRequestData(model)

    assertEquals(125L, inputData.getLong(KEY_MODEL_TOTAL_BYTES, -1L))
  }
}
