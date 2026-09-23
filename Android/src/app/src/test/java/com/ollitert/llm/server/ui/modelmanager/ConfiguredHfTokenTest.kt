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

package com.ollitert.llm.server.ui.modelmanager

import com.ollitert.llm.server.data.allowlist.configuredHfTokenOrNull
import com.ollitert.llm.server.data.allowlist.isHuggingFaceUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfiguredHfTokenTest {

  @Test
  fun blankTokenIsTreatedAsMissing() {
    assertNull(configuredHfTokenOrNull("   "))
  }

  @Test
  fun configuredTokenIsTrimmedForDownloadsAndResumes() {
    assertEquals("hf_example", configuredHfTokenOrNull("  hf_example  "))
  }

  // ── isHuggingFaceUrl (token scoping) ──────────────────────────────────────

  @Test
  fun huggingFaceHostsAndSubdomainsAreAllowed() {
    assertTrue(isHuggingFaceUrl("https://huggingface.co/repo/file.litertlm"))
    assertTrue(isHuggingFaceUrl("https://cdn-lfs.huggingface.co/repo/file.litertlm"))
    assertTrue(isHuggingFaceUrl("https://huggingface.co:443/repo/file.litertlm"))
    assertTrue(isHuggingFaceUrl("HTTPS://HUGGINGFACE.CO/repo/file.litertlm"))
  }

  @Test
  fun tokensAreNotAllowedOnCleartextOrAmbiguousOrigins() {
    for (url in listOf(
        "http://huggingface.co/repo/file.litertlm",
        "http://cdn-lfs.huggingface.co/repo/file.litertlm",
        "ftp://huggingface.co/repo/file.litertlm",
        "https://user@huggingface.co/repo/file.litertlm",
        "https://huggingface.co:8443/repo/file.litertlm",
      )
    ) {
      assertFalse(url, isHuggingFaceUrl(url))
    }
  }

  @Test
  fun lookalikeAndThirdPartyHostsAreRejected() {
    assertFalse(isHuggingFaceUrl("https://not-huggingface.co/repo/file.litertlm"))
    assertFalse(isHuggingFaceUrl("https://huggingface.co.evil.example/repo/file.litertlm"))
    assertFalse(isHuggingFaceUrl("https://example.com/model.litertlm"))
    assertFalse(isHuggingFaceUrl("https://"))
  }
}
