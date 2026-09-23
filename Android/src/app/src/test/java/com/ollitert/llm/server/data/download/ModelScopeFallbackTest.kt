package com.ollitert.llm.server.data.download

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.*
import org.junit.Test

class ModelScopeFallbackTest {
  private val gemmaUrl =
    "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/42d538a932e8d5b12e6b3b455f5572560bd60b2c/gemma3-1b-it-int4.litertlm?download=true"

  @Test
  fun everyBundledModelHasASizeMatchedPinnedAlternative() {
    val catalogFile = generateSequence(java.io.File(System.getProperty("user.dir"))) { it.parentFile }
      .map { java.io.File(it, "model_allowlists/v1/model_allowlist.json") }
      .first { it.isFile }
    val catalog = com.ollitert.llm.server.data.allowlist.ModelAllowlistJson.decode(catalogFile.readText())
    assertEquals(7, catalog.models.size)
    for (model in catalog.models) {
      val fallback = requireNotNull(modelScopeFallback(model.toModel().url)) { model.name }
      assertEquals(model.sizeInBytes, fallback.sizeInBytes)
      assertFalse(fallback.url.contains("/master/"))
      assertTrue(fallback.sha256.matches(Regex("[0-9a-f]{64}")))
    }
  }

  @Test
  fun knownArtifactSelectsAnImmutableMirrorWithIntegrityMetadata() {
    val fallback = requireNotNull(modelScopeFallback(gemmaUrl))
    assertEquals(
      "https://modelscope.cn/models/litert-community/Gemma3-1B-IT/resolve/ea05e64beb281629d682a58fdf681d0c5116f93f/gemma3-1b-it-int4.litertlm",
      fallback.url,
    )
    assertEquals(584417280L, fallback.sizeInBytes)
    assertEquals("1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be", fallback.sha256)
  }

  @Test
  fun unknownRevisionsAndOtherHostsCannotSelectTheMirror() {
    assertNull(modelScopeFallback(gemmaUrl.replace("42d538a932e8d5b12e6b3b455f5572560bd60b2c", "main")))
    assertNull(modelScopeFallback(gemmaUrl.replace("huggingface.co", "huggingface.co.example.test")))
    assertNull(modelScopeFallback(gemmaUrl.replace("https:", "http:")))
    assertNull(modelScopeFallback(gemmaUrl.replace("gemma3-1b-it-int4.litertlm", "other.litertlm")))
  }

  @Test
  fun onlyTheCanonicalDownloadQueryCanSelectTheMirror() {
    val canonical = gemmaUrl.substringBefore('?')
    assertNotNull(modelScopeFallback(canonical))
    assertNotNull(modelScopeFallback("$canonical?download=true"))
    for (query in listOf(
        "download=false",
        "revision=main",
        "download=true&revision=main",
        "download=true&download=false",
        "%64ownload=true",
        "",
      )
    ) {
      assertNull(query, modelScopeFallback("$canonical?$query"))
    }
  }

  @Test
  fun onlyTransientHttpFailuresPermitFallback() {
    for (code in listOf(408, 429, 500, 502, 503, 504)) {
      assertTrue("$code should permit fallback", isTransientDownloadStatus(code))
    }
    for (code in listOf(200, 301, 400, 401, 403, 404, 416)) {
      assertFalse("$code must not permit fallback", isTransientDownloadStatus(code))
    }
  }

  @Test
  fun localAndTlsErrorsDoNotPermitFallback() {
    assertTrue(isTransientDownloadFailure(UnknownHostException("offline")))
    assertTrue(isTransientDownloadFailure(SocketTimeoutException("timed out")))
    assertFalse(isTransientDownloadFailure(IOException("disk write failed")))
    assertFalse(isTransientDownloadFailure(SSLHandshakeException("certificate rejected")))
  }
}
