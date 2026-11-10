/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ollitert.llm.server.data.allowlist

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.net.HttpURLConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceCatalogTest {
  @Test fun searchPrioritizesCommunityAndPreservesApiOrderWithinGroups() {
    val connection = mockk<HttpURLConnection>(relaxed = true)
    every { connection.responseCode } returns 200
    every { connection.inputStream } returns """[
      {"id":"other/first","siblings":[{"rfilename":"model.litertlm"}]},
      {"id":"LiteRT-Community/one","siblings":[{"rfilename":"model.litertlm"}]},
      {"id":"litert-community-extra/not-community","siblings":[{"rfilename":"model.litertlm"}]},
      {"id":"litert-community/two","siblings":[{"rfilename":"model.litertlm"}]},
      {"id":"other/last","siblings":[{"rfilename":"model.litertlm"}]}
    ]""".byteInputStream()
    val models = HuggingFaceCatalog { connection }.search("", HuggingFaceSort.LIKES, null)
    assertEquals(listOf("LiteRT-Community/one", "litert-community/two", "other/first",
      "litert-community-extra/not-community", "other/last"), models.map { it.id })
  }

  @Test fun searchReturnsOnlyRepositoriesWithSupportedFiles() {
    val connection = mockk<HttpURLConnection>(relaxed = true)
    every { connection.responseCode } returns 200
    every { connection.inputStream } returns """[
      {"id":"author/model","lastModified":"2026-11-10T03:56:06Z","siblings":[{"rfilename":"folder/model.litertlm"},{"rfilename":"second.litertlm"},{"rfilename":"model-web.litertlm"}]},
      {"id":"author/web","siblings":[{"rfilename":"model-web.litertlm"}]},
      {"id":"author/other","siblings":[{"rfilename":"model.gguf"}]}
    ]""".byteInputStream()
    var requestedUrl = ""
    val catalog = HuggingFaceCatalog { requestedUrl = it; connection }

    val models = catalog.search("some model", HuggingFaceSort.DOWNLOADS, null)

    assertEquals(listOf("author/model"), models.map { it.id })
    assertEquals("2026-11-10T03:56:06Z", models.single().lastModified)
    assertEquals(2, models.single().modelFiles.size)
    assertTrue(requestedUrl.contains("expand=lastModified"))
    assertTrue(requestedUrl.contains("search=some+model"))
  }

  @Test fun detailsUsesFileSizeAndRejectsInvalidRepositoryId() {
    val connection = mockk<HttpURLConnection>(relaxed = true)
    every { connection.responseCode } returns 200
    every { connection.inputStream } returns """{"id":"author/model","sha":"abcd","siblings":[{"rfilename":"model.litertlm","size":0,"lfs":{"size":123}}]}""".byteInputStream()
    var requestedUrl = ""
    val catalog = HuggingFaceCatalog { requestedUrl = it; connection }

    assertEquals(123L, catalog.details("author/model", null, "branch/name").modelFiles.single().sizeBytes)
    assertTrue(requestedUrl.contains("/revision/branch%2Fname?blobs=true"))
    assertThrows(IllegalArgumentException::class.java) { catalog.details("../model", null) }
  }

  @Test fun redirectsFailWithoutFollowingThem() {
    val connection = mockk<HttpURLConnection>(relaxed = true)
    every { connection.responseCode } returns 302
    val catalog = HuggingFaceCatalog { connection }

    assertThrows(IOException::class.java) { catalog.search("", HuggingFaceSort.TRENDING, "token") }
    verify { connection.instanceFollowRedirects = false }
    verify { connection.setRequestProperty("Authorization", "Bearer token") }
    verify { connection.disconnect() }
  }

  @Test fun fileFilterRejectsUnsafeAndUnsupportedPaths() {
    assertTrue(isSupportedHuggingFaceFile("folder/model.LITERTLM"))
    assertFalse(isSupportedHuggingFaceFile("../model.litertlm"))
    assertFalse(isSupportedHuggingFaceFile("folder\\model.litertlm"))
    assertFalse(isSupportedHuggingFaceFile("model-web.litertlm"))
  }

}
