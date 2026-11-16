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
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelScopeCatalogTest {
  private fun connection(body: String): HttpURLConnection = mockk<HttpURLConnection>(relaxed = true).also {
    every { it.responseCode } returns 200
    every { it.inputStream } returns body.byteInputStream()
  }

  @Test fun retryAfterSupportsSecondsDatesAndOverflow() {
    val now = Instant.parse("2026-11-16T12:00:00Z")
    assertEquals(3_000L, parseRetryAfter("3", now))
    assertEquals(5_000L, parseRetryAfter("Mon, 16 Nov 2026 12:00:05 GMT", now))
    assertEquals(0L, parseRetryAfter("Mon, 16 Nov 2026 11:59:55 GMT", now))
    assertEquals(Long.MAX_VALUE, parseRetryAfter("999999999999999999999", now))
    assertEquals(Long.MAX_VALUE, parseRetryAfter("invalid", now))
    assertEquals(null, parseRetryAfter(null, now))
  }

  @Test fun retryAfterIsRetainedForHttpAndProviderBodyRateLimits() {
    val http = connection("")
    every { http.responseCode } returns 429
    every { http.getHeaderField("Retry-After") } returns "3"
    val httpError = assertThrows(ModelScopeRateLimitException::class.java) {
      ModelScopeCatalog(openConnection = { http }).files("author/model")
    }
    assertEquals(3_000L, httpError.retryAfterMillis)
    val body = connection("""{"Success":false,"Code":10013201001}""")
    every { body.getHeaderField("Retry-After") } returns "5"
    val bodyError = assertThrows(ModelScopeRateLimitException::class.java) {
      ModelScopeCatalog(openConnection = { body }).files("author/model")
    }
    assertEquals(5_000L, bodyError.retryAfterMillis)
  }

  @Test fun searchUsesTaggedCatalogAndPreservesMetadata() {
    var requested = ""
    val connection = connection("""{"success":true,"data":{"models":[{"id":"author/model","downloads":12,"likes":3,"last_modified":"2026-01-01T00:00:00Z"}],"total_count":120}}""")
    val catalog = ModelScopeCatalog(openConnection = { requested = it; connection })
    val result = catalog.search("some model", ModelScopeSort.LIKES, page = 2)
    val model = result.models.single()
    assertEquals("author/model", model.id)
    assertEquals(12, model.downloads)
    assertTrue(requested.contains("filter.custom_tag=litert-lm"))
    assertTrue(requested.contains("search=some+model"))
    assertTrue(requested.contains("sort=likes"))
    assertTrue(requested.contains("page_number=2"))
    assertTrue(result.hasMore)
    verify { connection.instanceFollowRedirects = false }
    verify(exactly = 0) { connection.setRequestProperty("Authorization", any()) }
  }

  @Test fun catalogUsesSiteScopedCredentialsAndRejectsUnsafeHosts() {
    val connection = connection("""{"success":true,"data":{"models":[],"total_count":0}}""")
    ModelScopeCatalog(openConnection = { connection }, endpoint = ModelScopeSite.INTERNATIONAL.baseUrl,
      token = "ms-example").search("", ModelScopeSort.DEFAULT)
    verify { connection.setRequestProperty("Authorization", "Bearer ms-example") }
    verify { connection.setRequestProperty("Cookie", "m_session_id=ms-example") }
    assertEquals(null, modelScopeSiteForUrl("https://modelscope.cn.example.test/models/a/b"))
    assertEquals(null, modelScopeSiteForUrl("http://modelscope.cn/models/a/b"))
    assertEquals(null, modelScopeSiteForUrl("https://user@modelscope.cn/models/a/b"))
    assertThrows(IllegalArgumentException::class.java) { setModelScopeCredentials(connection, "bad;cookie") }
  }

  @Test fun rejectedTokenRetriesPublicCatalogWithoutCredentials() {
    val rejected = connection("")
    every { rejected.responseCode } returns 401
    every { rejected.errorStream } returns "{}".byteInputStream()
    val public = connection("""{"success":true,"data":{"models":[],"total_count":0}}""")
    var calls = 0
    val result = ModelScopeCatalog(openConnection = { if (calls++ == 0) rejected else public }, token = "ms-example")
      .search("", ModelScopeSort.DEFAULT)
    assertTrue(result.models.isEmpty())
    verify(exactly = 0) { public.setRequestProperty("Authorization", any()) }
    verify(exactly = 0) { public.setRequestProperty("Cookie", any()) }
  }

  @Test fun deniedFilesHiddenFromAnonymousCallerPreserveAccessError() {
    val denied = connection("")
    every { denied.responseCode } returns 403
    every { denied.errorStream } returns "{}".byteInputStream()
    val hidden = connection("")
    every { hidden.responseCode } returns 404
    every { hidden.errorStream } returns null
    var calls = 0
    val catalog = ModelScopeCatalog(openConnection = { if (calls++ == 0) denied else hidden }, token = "ms-example")
    assertThrows(ModelScopeAccessException::class.java) { catalog.files("author/model") }
  }

  @Test fun missingOpenApiDetailsFallBackToLegacyPrivateMetadata() {
    val missing = connection("")
    every { missing.responseCode } returns 404
    every { missing.errorStream } returns null
    val legacy = connection("""{"Success":true,"Data":{"Path":"author","Name":"model","Downloads":7,"Stars":2}}""")
    val requested = mutableListOf<String>()
    val catalog = ModelScopeCatalog(openConnection = { url ->
      requested.add(url)
      if (requested.size == 1) missing else legacy
    }, token = "ms-example")
    val result = catalog.details("author/model")
    assertEquals("author/model", result.id)
    assertEquals(7, result.downloads)
    assertTrue(requested[0].contains("/openapi/v1/models/"))
    assertTrue(requested[1].contains("/api/v1/models/"))
    verify { legacy.setRequestProperty("Cookie", "m_session_id=ms-example") }
  }

  @Test fun filesUsePerFileRevisionAndSkipUnsupportedEntries() {
    val revision = "a".repeat(40)
    val connection = connection("""{"Success":true,"Code":200,"Data":{"Files":[
      {"Path":"folder/model.litertlm","Size":123,"Revision":"$revision","Type":"blob"},
      {"Path":"folder","Size":null,"Revision":null,"Type":"tree","Sha256":null},
      {"Path":"folder/other.gguf","Size":123,"Revision":"$revision","Type":"blob"},
      {"Path":"../bad.litertlm","Size":123,"Revision":"$revision","Type":"blob"},
      {"Path":"missing.litertlm","Size":123,"Revision":"master","Type":"blob"}
    ]}}""")
    val file = ModelScopeCatalog(openConnection = { connection }).files("author/model").single()
    assertEquals("folder/model.litertlm", file.path)
    assertEquals(revision, file.revision)
    assertEquals("$MODELSCOPE_BASE_URL/api/v1/models/author/model/repo?Revision=$revision&FilePath=folder%2Fmodel.litertlm", modelScopeFileUrl("author/model", revision, file.path))
  }

  @Test fun applicationErrorsAndRedirectsAreFailures() {
    val failed = connection("""{"Success":false,"Code":10013201001,"Message":"rate limited"}""")
    assertThrows(IOException::class.java) { ModelScopeCatalog(openConnection = { failed }).files("author/model") }
    val redirect = connection("")
    every { redirect.responseCode } returns 302
    assertThrows(IOException::class.java) { ModelScopeCatalog(openConnection = { redirect }).search("", ModelScopeSort.DEFAULT) }
    verify { redirect.disconnect() }
  }

  @Test fun rateLimitResponseIsDistinguishedFromOtherForbiddenResponses() {
    val limited = connection("")
    every { limited.responseCode } returns 403
    every { limited.errorStream } returns """{"Code":10013201001,"Success":false}""".byteInputStream()
    assertThrows(ModelScopeRateLimitException::class.java) {
      ModelScopeCatalog(openConnection = { limited }).files("author/model")
    }
    val forbidden = connection("")
    every { forbidden.responseCode } returns 403
    every { forbidden.errorStream } returns """{"Code":123,"Success":false}""".byteInputStream()
    assertThrows(IOException::class.java) {
      ModelScopeCatalog(openConnection = { forbidden }).files("author/model")
    }.also { assertTrue(it !is ModelScopeRateLimitException) }
  }

  @Test fun unsafeRepositoryAndPathsAreRejected() {
    assertThrows(IllegalArgumentException::class.java) { ModelScopeCatalog().files("../model") }
    assertThrows(IllegalArgumentException::class.java) { ModelScopeCatalog().search("", ModelScopeSort.LIKES, 61) }
    assertThrows(IllegalArgumentException::class.java) { modelScopeFileUrl("author/model", "a".repeat(40), "../model.litertlm") }
  }

  @Test fun missingFilesOrModelsAreNotTreatedAsEmptyResults() {
    val missingFiles = connection("""{"Success":true,"Data":{}}""")
    assertThrows(Exception::class.java) { ModelScopeCatalog(openConnection = { missingFiles }).files("author/model") }
    val missingModels = connection("""{"success":true,"data":{"total_count":0}}""")
    assertThrows(Exception::class.java) { ModelScopeCatalog(openConnection = { missingModels }).search("", ModelScopeSort.LIKES) }
  }
}
