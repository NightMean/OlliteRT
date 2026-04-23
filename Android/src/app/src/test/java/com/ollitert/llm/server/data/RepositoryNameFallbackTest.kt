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

package com.ollitert.llm.server.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoryNameFallbackTest {

  @Test
  fun extractsUserRepoFromRawGithubusercontent() {
    assertEquals(
      "alice/my-models",
      deriveRepositoryName("https://raw.githubusercontent.com/alice/my-models/main/list.json"),
    )
  }

  @Test
  fun extractsUserRepoFromGithubDotCom() {
    assertEquals(
      "bob/repo",
      deriveRepositoryName("https://github.com/bob/repo/blob/main/models.json"),
    )
  }

  @Test
  fun fallsToDomainForNonGithubUrl() {
    assertEquals(
      "example.com",
      deriveRepositoryName("https://example.com/path/to/models.json"),
    )
  }

  @Test
  fun fallsToDomainForShortGithubPath() {
    assertEquals(
      "raw.githubusercontent.com",
      deriveRepositoryName("https://raw.githubusercontent.com/onlyone"),
    )
  }

  @Test
  fun handlesTrailingSlash() {
    assertEquals(
      "alice/repo",
      deriveRepositoryName("https://github.com/alice/repo/"),
    )
  }

  @Test
  fun handlesEmptyUrl() {
    assertEquals("", deriveRepositoryName(""))
  }

  @Test
  fun handlesMalformedUrl() {
    assertEquals("not-a-url", deriveRepositoryName("not-a-url"))
  }
}
