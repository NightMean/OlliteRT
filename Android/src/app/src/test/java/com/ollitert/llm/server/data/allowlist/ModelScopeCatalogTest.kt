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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelScopeCatalogTest {
  private fun connection(body: String): HttpURLConnection = mockk<HttpURLConnection>(relaxed = true).also {
    every { it.responseCode } returns 200
    every { it.inputStream } returns body.byteInputStream()
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
    assertEquals("$MODELSCOPE_BASE_URL/models/author/model/resolve/$revision/folder/model.litertlm", modelScopeFileUrl("author/model", revision, file.path))
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
