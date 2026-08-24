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

import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.data.allowlist.ModelUrlResult
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.ui.modelmanager.components.HfTokenDialogReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.HttpURLConnection

/**
 * Unit tests for [DownloadGateCoordinator] — the pre-download access decision chain
 * extracted from DownloadAndTryButton. Probes are faked via lambdas; no network.
 */
class DownloadGateCoordinatorTest {

  private val hfModel = Model(name = "hf-model", url = "${GitHubConfig.HUGGINGFACE_BASE_URL}org/repo")
  private val plainModel = Model(name = "plain", url = "https://example.com/model.task")

  private fun coordinator(
    probe: (Model, String?) -> ModelUrlResult,
    storedToken: String? = null,
  ) = DownloadGateCoordinator(
    probeUrl = { model, token -> probe(model, token) },
    storedHfToken = { storedToken },
  )

  @Test
  fun nonHuggingFaceUrlStartsDownloadWithoutProbe() = runTest {
    var probes = 0
    val gate = coordinator(probe = { _, _ -> probes++; error("must not probe") })
    assertEquals(DownloadGateOutcome.StartDownload(null), gate.resolveDownloadAccess(plainModel))
    assertEquals(0, probes)
  }

  @Test
  fun huggingFaceUrlWithAnonymousAccessStartsDownloadWithoutToken() = runTest {
    val gate = coordinator(probe = { _, token ->
      assertEquals(null, token)
      ModelUrlResult.Success(HttpURLConnection.HTTP_OK)
    })
    assertEquals(DownloadGateOutcome.StartDownload(null), gate.resolveDownloadAccess(hfModel))
  }

  @Test
  fun huggingFaceUrlNotFoundYieldsModelNotFound() = runTest {
    val gate = coordinator(probe = { _, _ -> ModelUrlResult.Success(HttpURLConnection.HTTP_NOT_FOUND) })
    assertEquals(DownloadGateOutcome.ModelNotFound, gate.resolveDownloadAccess(hfModel))
  }

  @Test
  fun anonymousProbeNetworkErrorYieldsNetworkError() = runTest {
    val gate = coordinator(probe = { _, _ -> ModelUrlResult.Error("offline") })
    assertEquals(DownloadGateOutcome.NetworkError("offline"), gate.resolveDownloadAccess(hfModel))
  }

  @Test
  fun authRequiredWithoutStoredTokenPromptsForMissingToken() = runTest {
    var probes = 0
    val gate = coordinator(
      probe = { _, _ -> probes++; ModelUrlResult.Success(HttpURLConnection.HTTP_UNAUTHORIZED) },
      storedToken = null,
    )
    assertEquals(
      DownloadGateOutcome.NeedsHfToken(HfTokenDialogReason.MISSING),
      gate.resolveDownloadAccess(hfModel),
    )
    // Only the anonymous probe ran — nothing to retry with.
    assertEquals(1, probes)
  }

  @Test
  fun storedTokenAcceptedStartsDownloadWithToken() = runTest {
    val gate = coordinator(
      probe = { _, token ->
        when (token) {
          null -> ModelUrlResult.Success(HttpURLConnection.HTTP_UNAUTHORIZED)
          "tok" -> ModelUrlResult.Success(HttpURLConnection.HTTP_OK)
          else -> error("unexpected token")
        }
      },
      storedToken = "tok",
    )
    assertEquals(DownloadGateOutcome.StartDownload("tok"), gate.resolveDownloadAccess(hfModel))
  }

  @Test
  fun gatedModelWithValidTokenYieldsAgreement() = runTest {
    val gate = coordinator(
      probe = { _, token ->
        when (token) {
          null -> ModelUrlResult.Success(HttpURLConnection.HTTP_UNAUTHORIZED)
          else -> ModelUrlResult.Success(HttpURLConnection.HTTP_FORBIDDEN)
        }
      },
      storedToken = "tok",
    )
    assertEquals(DownloadGateOutcome.NeedsAgreement, gate.resolveDownloadAccess(hfModel))
  }

  @Test
  fun rejectedStoredTokenYieldsInvalidTokenPrompt() = runTest {
    val gate = coordinator(
      probe = { _, token ->
        when (token) {
          null -> ModelUrlResult.Success(HttpURLConnection.HTTP_UNAUTHORIZED)
          else -> ModelUrlResult.Success(HttpURLConnection.HTTP_UNAUTHORIZED)
        }
      },
      storedToken = "stale",
    )
    assertEquals(
      DownloadGateOutcome.NeedsHfToken(HfTokenDialogReason.INVALID),
      gate.resolveDownloadAccess(hfModel),
    )
  }

  @Test
  fun notFoundWithTokenStillYieldsModelNotFound() = runTest {
    val gate = coordinator(
      probe = { _, _ -> ModelUrlResult.Success(HttpURLConnection.HTTP_NOT_FOUND) },
      storedToken = "tok",
    )
    assertEquals(DownloadGateOutcome.ModelNotFound, gate.resolveDownloadAccess(hfModel))
  }

  @Test
  fun tokenProbeNetworkErrorYieldsNetworkError() = runTest {
    val gate = coordinator(
      probe = { _, token ->
        if (token == null) ModelUrlResult.Success(HttpURLConnection.HTTP_UNAUTHORIZED)
        else ModelUrlResult.Error("timeout")
      },
      storedToken = "tok",
    )
    assertEquals(DownloadGateOutcome.NetworkError("timeout"), gate.resolveDownloadAccess(hfModel))
  }
}
