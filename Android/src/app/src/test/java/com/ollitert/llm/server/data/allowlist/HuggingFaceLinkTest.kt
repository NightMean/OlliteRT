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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class HuggingFaceLinkTest {
  @Test fun fileUrlPinsRevisionAndEncodesEachPathSegment() {
    val revision = "a".repeat(40)
    val url = huggingFaceFileUrl("owner/repo", revision, "nested/file + name.litertlm")
    assertEquals("https://huggingface.co/owner/repo/resolve/$revision/nested/file%20%2B%20name.litertlm?download=true", url)
    assertThrows(IllegalArgumentException::class.java) { huggingFaceFileUrl("owner/repo", "main", "file.litertlm") }
    assertThrows(IllegalArgumentException::class.java) { huggingFaceFileUrl("owner/repo", revision, "../file.litertlm") }
  }

  @Test fun parsesRepositoryAndFileLinks() {
    assertEquals(HuggingFaceLink("owner/repo"), parseHuggingFaceLink("owner/repo"))
    assertEquals(HuggingFaceLink("owner/repo"), parseHuggingFaceLink("https://huggingface.co/owner/repo/"))
    assertEquals(HuggingFaceLink("owner/repo", "branch/name", "dir/file+one.litertlm"),
      parseHuggingFaceLink("https://huggingface.co/owner/repo/resolve/branch%2Fname/dir/file+one.litertlm?download=true"))
    assertEquals(HuggingFaceLink("owner/repo", "refs/pr/12", "file.litertlm"),
      parseHuggingFaceLink("https://huggingface.co/owner/repo/blob/refs/pr/12/file.litertlm"))
  }

  @Test fun rejectsUntrustedAndInvalidLinks() {
    listOf("http://huggingface.co/a/b", "https://huggingface.co.evil.test/a/b", "https://user@huggingface.co/a/b",
      "https://huggingface.co:444/a/b", "https://huggingface.co/a/b/resolve/main/../file.litertlm",
      "https://huggingface.co/a/b/resolve/main/model.gguf", "https://huggingface.co/a/b/resolve/refs/convert/file.litertlm", "https://huggingface.co/a/b/resolve/main/a%2Fb.litertlm")
      .forEach { assertNull(it, parseHuggingFaceLink(it)) }
  }
}
