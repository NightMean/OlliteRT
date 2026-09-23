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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection

/**
 * Unit tests for [DownloadGateCoordinator] — the pre-download access decision chain
 * extracted from DownloadAndTryButton. Probes are faked via lambdas; no network.
 */
class DownloadGateCoordinatorTest {

  private val hfModel = Model(name = "hf-model", url = "${GitHubConfig.HUGGINGFACE_BASE_URL}/org/repo")
  private val plainModel = Model(name = "plain", url = "https://example.com/model.task")
  private val mirroredModel = Model(
    name = "Qwen2.5-1.5B-Instruct",
    url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
  )

  private fun coordinator(
    probe: (Model, String?) -> ModelUrlResult,
    storedToken: String? = null,
    fallbackEnabled: Boolean = false,
  ) = DownloadGateCoordinator(
    probeUrl = { model, token -> probe(model, token) },
    storedHfToken = { storedToken },
    fallbackEnabled = { fallbackEnabled },
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
  fun transientServerFailureDoesNotAskForAHuggingFaceToken() = runTest {
    val gate = coordinator(probe = { _, _ -> ModelUrlResult.Success(503) })

    assertTrue(gate.resolveDownloadAccess(hfModel) is DownloadGateOutcome.NetworkError)
  }

  @Test
  fun allFourPublicMappingsCanFailOverOnTemporaryNetworkOrServerFailures() = runTest {
    val urls = listOf(
      mirroredModel.url,
      "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm",
      "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/28299f30ee4d43294517a4ac93abd6163412f07f/gemma-4-E4B-it.litertlm",
      "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/e34bb88632342d1f9640bad579a45134eb1cf988/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
    )
    for (url in urls) {
      for (failure in listOf(
          ModelUrlResult.Error("timeout", retryable = true),
          ModelUrlResult.Success(408),
          ModelUrlResult.Success(429),
          ModelUrlResult.Success(503),
        )
      ) {
        val gate = coordinator(probe = { _, _ -> failure }, fallbackEnabled = true)
        val outcome = gate.resolveDownloadAccess(mirroredModel.copy(url = url))
        assertTrue(outcome is DownloadGateOutcome.StartDownload)
        assertTrue((outcome as DownloadGateOutcome.StartDownload).modelScopePrimaryError != null)
        assertEquals(null, outcome.accessToken)
      }
    }
  }

  @Test
  fun noncanonicalQueryDoesNotOfferOrStartPreflightFallback() = runTest {
    val model = mirroredModel.copy(url = "${mirroredModel.url}?download=false")
    for (consent in listOf(false, true)) {
      val gate = coordinator(
        probe = { _, _ -> ModelUrlResult.Error("offline", retryable = true) },
        fallbackEnabled = consent,
      )
      assertEquals(DownloadGateOutcome.NetworkError("offline"), gate.resolveDownloadAccess(model))
    }
  }

  @Test
  fun eligibleFailureRequiresConsentByDefault() = runTest {
    val gate = coordinator(probe = { _, _ -> ModelUrlResult.Error("offline", retryable = true) })
    assertEquals(
      DownloadGateOutcome.NeedsModelScopeConsent("offline"),
      gate.resolveDownloadAccess(mirroredModel),
    )
  }

  @Test
  fun failedAnonymousAccessCheckCannotAuthorizeAGatedMirror() = runTest {
    val gatedUrls = listOf(
      "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/42d538a932e8d5b12e6b3b455f5572560bd60b2c/gemma3-1b-it-int4.litertlm",
      "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/ba9ca88da013b537b6ed38108be609b8db1c3a16/gemma-3n-E2B-it-int4.litertlm",
      "https://huggingface.co/google/gemma-3n-E4B-it-litert-lm/resolve/297ed75955702dec3503e00c2c2ecbbf475300bc/gemma-3n-E4B-it-int4.litertlm",
    )
    for (url in gatedUrls) {
      for (failure in listOf(ModelUrlResult.Error("timeout", retryable = true), ModelUrlResult.Success(503))) {
        for (token in listOf(null, "unverified-token")) {
          val gate = coordinator(probe = { _, _ -> failure }, storedToken = token, fallbackEnabled = true)
          assertTrue(
            "An unverified token or mirror consent must not establish access to $url",
            gate.resolveDownloadAccess(mirroredModel.copy(url = url)) is DownloadGateOutcome.NetworkError,
          )
        }
      }
    }
  }

  @Test
  fun cleartextHuggingFaceUrlDoesNotEnterTheStoredTokenFlow() = runTest {
    val gate = coordinator(
      probe = { _, _ -> error("Cleartext URL must not be probed with credentials") },
      storedToken = "test-token",
    )
    assertEquals(
      DownloadGateOutcome.StartDownload(null),
      gate.resolveDownloadAccess(hfModel.copy(url = "http://huggingface.co/org/repo")),
    )
  }

  @Test
  fun consentedFailureStartsMirrorWithoutForwardingTheToken() = runTest {
    val gate = coordinator(
      probe = { _, _ -> ModelUrlResult.Error("offline", retryable = true) },
      storedToken = "private-token",
      fallbackEnabled = true,
    )
    assertEquals(
      DownloadGateOutcome.StartDownload(null, modelScopePrimaryError = "offline"),
      gate.resolveDownloadAccess(mirroredModel),
    )
  }

  @Test
  fun consentDoesNotBypassAuthenticationOrCertificateErrors() = runTest {
    for (code in listOf(401, 403)) {
      val gate = coordinator(probe = { _, _ -> ModelUrlResult.Success(code) }, fallbackEnabled = true)
      assertTrue(gate.resolveDownloadAccess(mirroredModel) is DownloadGateOutcome.NeedsHfToken)
    }
    val gate = coordinator(
      probe = { _, _ -> ModelUrlResult.Error("certificate rejected", retryable = false) },
      fallbackEnabled = true,
    )
    assertEquals(
      DownloadGateOutcome.NetworkError("certificate rejected"),
      gate.resolveDownloadAccess(mirroredModel),
    )
  }

  @Test
  fun healthyPrimaryDoesNotSelectMirrorEvenAfterConsent() = runTest {
    val gate = coordinator(probe = { _, _ -> ModelUrlResult.Success(200) }, fallbackEnabled = true)
    assertEquals(DownloadGateOutcome.StartDownload(null), gate.resolveDownloadAccess(mirroredModel))
  }

  @Test
  fun failureWhileVerifyingATokenDoesNotBypassAnEarlierAccessDenial() = runTest {
    val gate = coordinator(
      probe = { _, token ->
        if (token == null) ModelUrlResult.Success(401)
        else ModelUrlResult.Error("timeout", retryable = true)
      },
      storedToken = "token",
      fallbackEnabled = true,
    )
    assertEquals(
      DownloadGateOutcome.NetworkError("timeout"),
      gate.resolveDownloadAccess(mirroredModel),
    )
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
