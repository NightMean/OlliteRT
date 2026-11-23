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

import androidx.work.workDataOf
import com.ollitert.llm.server.data.storage.HuggingFaceDownloadAccess
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_ACCESS_TOKEN
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HuggingFaceDownloadConnectionTest {
  private val hubUrl = URL("https://huggingface.co/org/repo/resolve/main/model.litertlm")

  @Test fun publicPartialDownloadStaysAnonymous() {
    val publicFile = response(HttpURLConnection.HTTP_PARTIAL)
    assertSame(publicFile, openHuggingFaceDownloadConnection(
      hubUrl, 64L, HuggingFaceDownloadAccess.IF_REQUIRED, getToken = { error("No credential lookup expected") },
      open = { publicFile },
    ))
    verify(exactly = 0) { publicFile.setRequestProperty("Authorization", any()) }
    verify { publicFile.setRequestProperty("Range", "bytes=64-") }
  }

  @Test fun legacyWorkAndExplicitRetryCanResolveTheCurrentCredential() {
    val legacyWork = workDataOf(KEY_MODEL_DOWNLOAD_ACCESS_TOKEN to "old value")
    val access = huggingFaceDownloadAccess(legacyWork)
    assertEquals(HuggingFaceDownloadAccess.IF_REQUIRED, access)
    val denied = response(HttpURLConnection.HTTP_UNAUTHORIZED)
    val granted = response(HttpURLConnection.HTTP_PARTIAL)
    val attempts = listOf(denied, granted).iterator()
    assertSame(granted, openHuggingFaceDownloadConnection(
      hubUrl, 64L, access, getToken = { "current value" }, open = { attempts.next() },
    ))
    verify(exactly = 0) { denied.setRequestProperty("Authorization", any()) }
    verify { denied.disconnect() }
    verify { granted.setRequestProperty("Authorization", "Bearer current value") }
    verify { granted.setRequestProperty("Range", "bytes=64-") }
  }

  @Test fun storageRedirectNeverGetsTheCredentialOrTriggersATokenRetry() {
    val direct = response(HttpURLConnection.HTTP_MOVED_TEMP)
    val storage = response(HttpURLConnection.HTTP_FORBIDDEN)
    every { direct.getHeaderField("Location") } returns "https://storage.example.test/signed/model"
    val result = openHuggingFaceDownloadConnection(
      hubUrl, 64L, HuggingFaceDownloadAccess.AUTHENTICATED, getToken = { "current value" },
      open = { if (it.host == hubUrl.host) direct else storage },
    )
    assertSame(storage, result)
    verify { direct.setRequestProperty("Authorization", "Bearer current value") }
    verify { direct.disconnect() }
    verify(exactly = 0) { storage.setRequestProperty("Authorization", any()) }
    verify { storage.setRequestProperty("Range", "bytes=64-") }
    assertSame(storage, openHuggingFaceDownloadConnection(
      hubUrl, 64L, HuggingFaceDownloadAccess.IF_REQUIRED, getToken = { error("Storage denial is not a Hub denial") },
      open = { if (it.host == hubUrl.host) direct else storage },
    ))
  }

  @Test fun clearingTheConfiguredCredentialLeavesTheOriginalDenial() {
    val denied = response(HttpURLConnection.HTTP_FORBIDDEN)
    var attempts = 0
    assertSame(denied, openHuggingFaceDownloadConnection(
      hubUrl, 64L, HuggingFaceDownloadAccess.IF_REQUIRED, getToken = { null }, open = { attempts++; denied },
    ))
    assertEquals(1, attempts)
    verify(exactly = 0) { denied.setRequestProperty("Authorization", any()) }
  }

  @Test fun resumedDownloadCanAuthenticateAfterASameOriginRename() {
    val renamed = response(HttpURLConnection.HTTP_MOVED_TEMP)
    every { renamed.getHeaderField("Location") } returns "/org/new/resolve/main/model.litertlm"
    val denied = response(HttpURLConnection.HTTP_UNAUTHORIZED)
    val granted = response(HttpURLConnection.HTTP_PARTIAL)
    val attempts = listOf(renamed, denied, granted).iterator()
    assertSame(granted, openHuggingFaceDownloadConnection(
      hubUrl, 64L, HuggingFaceDownloadAccess.IF_REQUIRED, getToken = { "current value" },
      open = { attempts.next() },
    ))
    verify(exactly = 0) { denied.setRequestProperty("Authorization", any()) }
    verify { denied.disconnect() }
    verify { granted.setRequestProperty("Authorization", "Bearer current value") }
  }

  private fun response(status: Int): HttpURLConnection = mockk(relaxed = true) {
    every { responseCode } returns status
  }
}
