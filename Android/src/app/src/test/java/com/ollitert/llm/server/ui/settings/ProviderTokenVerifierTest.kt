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

package com.ollitert.llm.server.ui.settings

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.HttpURLConnection
import org.junit.Assert.*
import org.junit.Test

class ProviderTokenVerifierTest {
  private fun connection(code: Int, body: String = ""): HttpURLConnection = mockk(relaxed = true) {
    every { responseCode } returns code
    every { inputStream } answers { body.byteInputStream() }
    every { errorStream } answers { body.byteInputStream() }
  }

  @Test fun identityRequiresAValidProfile() {
    assertTrue(hasIdentity(ProviderToken.HUGGING_FACE, """{"name":"alice"}"""))
    assertTrue(hasIdentity(ProviderToken.MODELSCOPE_CN, """{"data":{"preferred_username":"alice"}}"""))
    assertTrue(hasIdentity(ProviderToken.MODELSCOPE_AI, """{"Data":{"Username":"alice"}}"""))
    assertFalse(hasIdentity(ProviderToken.MODELSCOPE_AI, """{"data":{}}"""))
    assertFalse(hasIdentity(ProviderToken.HUGGING_FACE, """{"name":true}"""))
    assertFalse(hasIdentity(ProviderToken.HUGGING_FACE, "not json"))
    assertFalse(hasIdentity(ProviderToken.MODELSCOPE_CN, """{"success":false,"data":{"name":"alice"}}"""))
  }

  @Test fun requestsUseTheRightSiteAndDoNotFollowRedirects() {
    val connection = connection(200, """{"data":{"username":"alice"}}""")
    var endpoint = ""
    val verifier = ProviderTokenVerifier().apply { openConnection = { endpoint = it; connection } }
    assertEquals(TokenCheck.ACCEPTED, verifier.verify(ProviderToken.MODELSCOPE_CN, "example"))
    assertEquals("https://modelscope.cn/openapi/v1/users/me", endpoint)
    verify { connection.instanceFollowRedirects = false }
    verify { connection.setRequestProperty("Authorization", "Bearer example") }
    assertEquals(TokenCheck.ACCEPTED, verifier.verify(ProviderToken.MODELSCOPE_AI, "example"))
    assertEquals("https://www.modelscope.ai/openapi/v1/users/me", endpoint)
  }

  @Test fun errorsAndInvalidSuccessAreNotAccepted() {
    val verifier = ProviderTokenVerifier()
    fun check(status: Int, body: String = "") : TokenCheck {
      verifier.openConnection = { connection(status, body) }
      return verifier.verify(ProviderToken.MODELSCOPE_CN, "example")
    }
    assertEquals(TokenCheck.REJECTED, check(401))
    assertEquals(TokenCheck.DENIED, check(403))
    assertEquals(TokenCheck.RATE_LIMITED, check(403, """{"Code":10013201001}"""))
    assertEquals(TokenCheck.RATE_LIMITED, check(429))
    assertEquals(TokenCheck.UNAVAILABLE, check(302))
    assertEquals(TokenCheck.UNAVAILABLE, check(500))
    assertEquals(TokenCheck.UNAVAILABLE, check(200, "{}"))
    assertEquals(TokenCheck.UNAVAILABLE, check(200, "x".repeat(65537)))
  }
}
