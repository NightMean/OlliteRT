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
import com.ollitert.llm.server.data.model.ModelFile
import com.ollitert.llm.server.data.storage.KEY_MODEL_COMMIT_HASH
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_FILE_NAME
import com.ollitert.llm.server.data.storage.KEY_MODEL_STAGE_UPDATE
import com.ollitert.llm.server.data.storage.KEY_MODEL_TOTAL_BYTES
import com.ollitert.llm.server.data.storage.KEY_MODEL_FILE_BYTES
import com.ollitert.llm.server.data.storage.KEY_MODEL_EXTRA_FILE_BYTES
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_ACCESS_TOKEN
import com.ollitert.llm.server.data.storage.KEY_HUGGING_FACE_DOWNLOAD_ACCESS
import com.ollitert.llm.server.data.storage.HuggingFaceDownloadAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRequestDataTest {
  @Test fun updateWorkTargetsTheReplacementWithoutChangingTheInstalledVersion() {
    val latest = ModelFile("replacement.litertlm", "latest")
    val model = Model(name = "Catalog model", version = "installed", downloadFileName = "original.litertlm",
      latestModelFile = latest, updatable = true)

    val data = buildDownloadRequestData(model)

    assertTrue(data.getBoolean(KEY_MODEL_STAGE_UPDATE, false))
    assertEquals(latest.commitHash, data.getString(KEY_MODEL_COMMIT_HASH))
    assertEquals(latest.fileName, data.getString(KEY_MODEL_DOWNLOAD_FILE_NAME))
    assertEquals("installed", model.version)
  }

  @Test fun workInputDoesNotPersistTheModelCredential() {
    val customModel = Model(name = "custom", url = "https://example.test/model")
    val customWork = buildDownloadRequestData(customModel, HuggingFaceDownloadAccess.IF_REQUIRED)
    assertNull(customWork.getString(KEY_MODEL_DOWNLOAD_ACCESS_TOKEN))
    assertNull(customWork.getString(KEY_HUGGING_FACE_DOWNLOAD_ACCESS))

    val hubModel = Model(name = "hub", url = "https://huggingface.co/org/model/resolve/main/model.litertlm")
    val hubWork = buildDownloadRequestData(hubModel, HuggingFaceDownloadAccess.AUTHENTICATED)
    assertNull(hubWork.getString(KEY_MODEL_DOWNLOAD_ACCESS_TOKEN))
    assertEquals(HuggingFaceDownloadAccess.AUTHENTICATED.name, hubWork.getString(KEY_HUGGING_FACE_DOWNLOAD_ACCESS))
  }

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
    assertEquals(100L, inputData.getLong(KEY_MODEL_FILE_BYTES, -1L))
    assertEquals("25", inputData.getString(KEY_MODEL_EXTRA_FILE_BYTES))
  }
}
