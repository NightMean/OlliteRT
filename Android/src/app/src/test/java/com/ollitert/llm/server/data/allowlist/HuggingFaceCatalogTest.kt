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
  private fun HuggingFaceCatalog.fetchAll(query: String, sort: HuggingFaceSort, onPage: (List<HuggingFaceModel>) -> Unit = {}): List<HuggingFaceModel> {
    var progress = initialSearch(query, sort)
    while (progress.nextUrl != null) {
      progress = searchPage(progress, null)
      onPage(progress.models)
    }
    return progress.models
  }
  @Test fun searchPageRequestsOnlyOnePageAndKeepsTheNextCursor() {
    val connection = mockk<HttpURLConnection>(relaxed = true)
    every { connection.responseCode } returns 200
    every { connection.getHeaderField("Link") } returns "<https://huggingface.co/api/models?cursor=next>; rel=\"next\""
    every { connection.inputStream } returns """[{"id":"owner/model","siblings":[{"rfilename":"model.litertlm"}]}]""".byteInputStream()
    var calls = 0
    val catalog = HuggingFaceCatalog { calls++; connection }
    val page = catalog.searchPage(catalog.initialSearch("", HuggingFaceSort.TRENDING), null)
    assertEquals(1, calls)
    assertEquals("https://huggingface.co/api/models?cursor=next", page.nextUrl)
    assertEquals(listOf("owner/model"), page.models.map { it.id })
  }
  @Test fun searchLoadsEveryPageAndReportsCumulativeSupportedModels() {
    val first = mockk<HttpURLConnection>(relaxed = true)
    val second = mockk<HttpURLConnection>(relaxed = true)
    every { first.responseCode } returns 200
    every { second.responseCode } returns 200
    every { first.getHeaderField("Link") } returns "<https://huggingface.co/api/models?cursor=next>; rel=\"next\""
    every { second.getHeaderField("Link") } returns null
    every { first.inputStream } returns """[{"id":"other/a","siblings":[{"rfilename":"a.litertlm"}]}]""".byteInputStream()
    every { second.inputStream } returns """[{"id":"litert-community/b","siblings":[{"rfilename":"b.litertlm"}]},{"id":"other/a","siblings":[{"rfilename":"a.litertlm"}]}]""".byteInputStream()
    var requests = 0
    val pages = mutableListOf<List<String>>()
    val catalog = HuggingFaceCatalog { if (requests++ == 0) first else second }
    val result = catalog.fetchAll("", HuggingFaceSort.TRENDING) { models -> pages.add(models.map { it.id }) }
    assertEquals(listOf(listOf("other/a"), listOf("litert-community/b", "other/a")), pages)
    assertEquals(pages.last(), result.map { it.id })
  }

  @Test fun paginationRejectsUntrustedOrRepeatedLinks() {
    assertThrows(IOException::class.java) { nextPageUrl("<https://huggingface.co.example.test/api/models?cursor=x>; rel=\"next\"") }
    assertThrows(IOException::class.java) { nextPageUrl("<http://huggingface.co/api/models?cursor=x>; rel=\"next\"") }
    val first = mockk<HttpURLConnection>(relaxed = true)
    every { first.responseCode } returns 200
    every { first.getHeaderField("Link") } returns "<https://huggingface.co/api/models?cursor=repeat>; rel=\"next\""
    every { first.inputStream } answers { "[]".byteInputStream() }
    assertThrows(IOException::class.java) { HuggingFaceCatalog { first }.fetchAll("", HuggingFaceSort.TRENDING) }
  }

  @Test fun linkHeaderHandlesMultipleRelationsAndQuotedCommas() {
    val url = "https://huggingface.co/api/models?cursor=a,b"
    val header = "<https://huggingface.co/api/models?cursor=old>; rel=\"prev\", <$url>; title=\"a,b\"; rel=\"next prev\""
    assertEquals(url, nextPageUrl(header))
    assertThrows(IOException::class.java) { nextPageUrl("<https://huggingface.co/api/models?cursor=x; rel=next") }
  }

  @Test fun cumulativeResponseBudgetStopsBeforePublishingOverLimitPage() {
    val connection = mockk<HttpURLConnection>(relaxed = true)
    every { connection.responseCode } returns 200
    every { connection.getHeaderField("Link") } returns null
    every { connection.inputStream } returns "[]".byteInputStream()
    val progress = HuggingFaceSearchProgress(emptyList(), "https://huggingface.co/api/models?cursor=next",
      responseBytes = MAX_SEARCH_RESPONSE_BYTES)
    assertThrows(HuggingFaceSearchLimitException::class.java) {
      HuggingFaceCatalog { connection }.searchPage(progress, null)
    }
  }
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
    val models = HuggingFaceCatalog { connection }.fetchAll("", HuggingFaceSort.LIKES)
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

    val models = catalog.fetchAll("some model", HuggingFaceSort.DOWNLOADS)

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

    assertThrows(IOException::class.java) { catalog.searchPage(catalog.initialSearch("", HuggingFaceSort.TRENDING), "token") }
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
