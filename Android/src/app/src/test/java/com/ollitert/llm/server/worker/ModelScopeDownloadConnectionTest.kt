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

package com.ollitert.llm.server.worker

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import com.ollitert.llm.server.data.allowlist.ModelScopeRateLimitException
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ModelScopeDownloadConnectionTest {
  private fun connection(status: Int, location: String? = null) = mockk<HttpURLConnection>(relaxed = true).also {
    every { it.responseCode } returns status
    every { it.getHeaderField("Location") } returns location
  }

  @Test fun redirectToStorageNeverReceivesSiteCredentialsAndKeepsRange() {
    val origin = connection(302, "https://storage.example.test/file")
    val storage = connection(206)
    val result = openModelScopeDownloadConnection(URL("https://modelscope.cn/file"), "ms-example", 123) {
      if (it.host == "modelscope.cn") origin else storage
    }
    assertSame(storage, result)
    verify { origin.setRequestProperty("Cookie", "m_session_id=ms-example") }
    verify { origin.setRequestProperty("Authorization", "Bearer ms-example") }
    verify(exactly = 0) { storage.setRequestProperty("Cookie", any()) }
    verify(exactly = 0) { storage.setRequestProperty("Authorization", any()) }
    verify { storage.setRequestProperty("Range", "bytes=123-") }
    verify { origin.disconnect() }
  }

  @Test fun downgradeRedirectIsRejected() {
    val origin = connection(302, "http://storage.example.test/file")
    assertThrows(IOException::class.java) {
      openModelScopeDownloadConnection(URL("https://modelscope.cn/file"), "ms-example", 0) { origin }
    }
    verify { origin.disconnect() }
  }

  @Test fun unauthorizedTokenRetriesPublicFileAnonymously() {
    val rejected = connection(401)
    every { rejected.errorStream } returns "{}".byteInputStream()
    val public = connection(200)
    var calls = 0
    val result = openModelScopeDownloadConnection(URL("https://www.modelscope.ai/file"), "ms-example", 0) {
      if (calls++ == 0) rejected else public
    }
    assertSame(public, result)
    verify(exactly = 0) { public.setRequestProperty("Authorization", any()) }
    verify(exactly = 0) { public.setRequestProperty("Cookie", any()) }
  }

  @Test fun rateLimitDoesNotTriggerAnAnonymousRetry() {
    val limited = connection(403)
    val bytes = """{"Code":10013201001}""".toByteArray()
    every { limited.errorStream } answers { object : java.io.InputStream() {
      var index = 0
      override fun read(): Int = if (index < bytes.size) bytes[index++].toInt() and 0xff else -1
      override fun read(target: ByteArray, offset: Int, length: Int): Int {
        val value = read()
        if (value < 0) return -1
        target[offset] = value.toByte()
        return 1
      }
    } }
    var calls = 0
    assertThrows(ModelScopeRateLimitException::class.java) {
      openModelScopeDownloadConnection(URL("https://modelscope.cn/file"), "ms-example", 0) {
        calls++
        limited
      }
    }
    org.junit.Assert.assertEquals(1, calls)
  }

  @Test fun hiddenFileAfterAuthRejectionReportsAccessFailure() {
    val denied = connection(403)
    every { denied.errorStream } returns "{}".byteInputStream()
    val hidden = connection(404)
    var calls = 0
    assertThrows(ModelScopeDownloadAccessException::class.java) {
      openModelScopeDownloadConnection(URL("https://modelscope.cn/file"), "ms-example", 0) {
        if (calls++ == 0) denied else hidden
      }
    }
  }

  @Test fun accessDeniedWithoutTokenReportsModelScopeAccessFailure() {
    val denied = connection(403)
    every { denied.errorStream } returns "{}".byteInputStream()
    assertThrows(ModelScopeDownloadAccessException::class.java) {
      openModelScopeDownloadConnection(URL("https://modelscope.cn/file"), null, 0) { denied }
    }
  }

  @Test fun sameSiteRedirectKeepsCredentialsAndRedirectLoopStops() {
    val origin = connection(302, "https://www.modelscope.cn/next")
    val redirected = connection(200)
    openModelScopeDownloadConnection(URL("https://modelscope.cn/file"), "ms-example", 0) {
      if (it.host == "modelscope.cn") origin else redirected
    }
    verify { redirected.setRequestProperty("Authorization", "Bearer ms-example") }
    val loop = connection(302, "https://modelscope.cn/file")
    assertThrows(IOException::class.java) {
      openModelScopeDownloadConnection(URL("https://modelscope.cn/file"), "ms-example", 0) { loop }
    }
  }



  @Test fun storageAccessDenialIsNotReportedAsProviderTokenDenial() {
    val origin = connection(302, "https://storage.example.test/file")
    val storage = connection(403)
    val result = openModelScopeDownloadConnection(URL("https://modelscope.cn/file"), null, 0) {
      if (it.host == "modelscope.cn") origin else storage
    }
    assertSame(storage, result)
  }

  @Test fun accessDenialRecordsTheSiteThatRejectedTheRequest() {
    val denied = connection(403)
    every { denied.errorStream } returns "{}".byteInputStream()
    val error = assertThrows(ModelScopeDownloadAccessException::class.java) {
      openModelScopeDownloadConnection(URL("https://modelscope.cn/file"), null, 0) { denied }
    }
    org.junit.Assert.assertEquals(com.ollitert.llm.server.data.allowlist.ModelScopeSite.CHINA, error.site)
  }

  @Test fun providerDenialsAndRateLimitsAreNeverInferredToBeDiskFailures() {
    val site = com.ollitert.llm.server.data.allowlist.ModelScopeSite.CHINA
    org.junit.Assert.assertFalse(mayBeDiskWriteFailure(ModelScopeDownloadAccessException(site)))
    org.junit.Assert.assertFalse(mayBeDiskWriteFailure(ModelScopeRateLimitException()))
    org.junit.Assert.assertTrue(mayBeDiskWriteFailure(IOException("write failed")))
  }
}
