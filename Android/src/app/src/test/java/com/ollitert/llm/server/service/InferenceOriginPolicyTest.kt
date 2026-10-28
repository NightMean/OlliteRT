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

package com.ollitert.llm.server.service

import com.ollitert.llm.server.service.http.isAllowedAnonymousInferenceOrigin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceOriginPolicyTest {
  private val server = "http://phone.local:8000"

  @Test
  fun nativeAndSameOriginRequestsAreAllowed() {
    assertTrue(isAllowedAnonymousInferenceOrigin(emptyList(), emptyList(), server, "*"))
    assertTrue(isAllowedAnonymousInferenceOrigin(emptyList(), listOf("same-origin"), server, "*"))
    assertTrue(isAllowedAnonymousInferenceOrigin(listOf("http://PHONE.local:8000"), emptyList(), server, "*"))
    assertTrue(isAllowedAnonymousInferenceOrigin(listOf("https://[::1]"), emptyList(), "https://[::1]:443", "*"))
  }

  @Test
  fun wildcardDoesNotTrustAnonymousBrowserOrigins() {
    assertFalse(isAllowedAnonymousInferenceOrigin(listOf("https://evil.example"), listOf("cross-site"), server, "*"))
    assertFalse(isAllowedAnonymousInferenceOrigin(emptyList(), listOf("cross-site"), server, "*"))
    assertFalse(isAllowedAnonymousInferenceOrigin(emptyList(), listOf("same-site"), server, "*"))
  }

  @Test
  fun explicitlyConfiguredOriginsMustMatchSchemeHostAndPort() {
    val allowed = "https://trusted.example, http://localhost:3000"
    assertTrue(isAllowedAnonymousInferenceOrigin(listOf("https://TRUSTED.example:443"), emptyList(), server, allowed))
    assertTrue(isAllowedAnonymousInferenceOrigin(listOf("http://localhost:3000"), emptyList(), server, allowed))
    assertFalse(isAllowedAnonymousInferenceOrigin(listOf("http://trusted.example"), emptyList(), server, allowed))
    assertFalse(isAllowedAnonymousInferenceOrigin(listOf("http://localhost:3001"), emptyList(), server, allowed))
    assertFalse(isAllowedAnonymousInferenceOrigin(listOf("https://trusted.example.evil.example"), emptyList(), server, allowed))
  }

  @Test
  fun malformedOrAmbiguousBrowserHeadersAreRejected() {
    for (origin in listOf("null", "https://user@trusted.example", "https://trusted.example/path", "https://trusted.example?x=1", "https://trusted.example:99999")) {
      assertFalse(isAllowedAnonymousInferenceOrigin(listOf(origin), emptyList(), server, "https://trusted.example"))
    }
    assertFalse(isAllowedAnonymousInferenceOrigin(listOf("http://phone.local:8000", "https://evil.example"), emptyList(), server, "*"))
    assertFalse(isAllowedAnonymousInferenceOrigin(emptyList(), listOf("same-origin", "cross-site"), server, "*"))
  }
}
