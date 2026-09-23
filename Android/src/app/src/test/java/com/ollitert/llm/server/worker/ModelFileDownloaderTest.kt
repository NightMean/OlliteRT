package com.ollitert.llm.server.worker

import com.ollitert.llm.server.data.download.ModelScopeFallback
import com.ollitert.llm.server.data.download.modelScopeFallback
import com.ollitert.llm.server.data.download.DownloadNetworkException
import com.ollitert.llm.server.data.storage.modelScopeStagingFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelFileDownloaderTest {
  @get:Rule val temp = TemporaryFolder()
  private val primary = "https://huggingface.co/org/model/resolve/revision/model.litertlm"
  private val mirrorUrl = "https://modelscope.cn/models/org/model/resolve/pinned/model.litertlm"
  private val bytes = "LITERTLMtest model bytes".toByteArray()
  private val hash = "b2894da5f244943816b2b1f89715a493091c6db89b9da06a2a5d20903448865c"
  private val mirror = ModelScopeFallback(
    mirrorUrl, bytes.size.toLong(), hash, requiresAccessConfirmation = false,
  )
  private val gatedMirror = requireNotNull(modelScopeFallback(
    "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/42d538a932e8d5b12e6b3b455f5572560bd60b2c/gemma3-1b-it-int4.litertlm",
  )).copy(url = mirrorUrl, sizeInBytes = bytes.size.toLong(), sha256 = hash)

  private open class Response(
    url: URL,
    private val body: ByteArray,
    private val status: Int = 200,
    private val headers: Map<String, String> = emptyMap(),
  ) : HttpURLConnection(url) {
    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun usingProxy() = false
    override fun getResponseCode() = status
    override fun getHeaderField(name: String): String? = headers[name]
    override fun getInputStream(): InputStream = ByteArrayInputStream(body)
  }

  private fun staging(): File = File(temp.root, "model.olliterttmp")

  @Test
  fun successfulPrimaryNeverContactsMirror() = runTest {
    val requests = mutableListOf<String>()
    val downloader = ModelFileDownloader { url ->
      requests.add(url.toString())
      Response(url, bytes)
    }
    val result = downloader.download(primary, staging(), mirror, { true })
    assertArrayEquals(bytes, result.readBytes())
    assertEquals(listOf(primary), requests)
  }

  @Test
  fun primaryUsesTheActualBearerTokenAndKeepsItOffRedirectHosts() = runTest {
    lateinit var primaryRequest: Response
    lateinit var cdnRequest: Response
    val downloader = ModelFileDownloader { url ->
      if (url.toString() == primary) {
        Response(url, byteArrayOf(), 302, mapOf("Location" to "https://cdn.example.test/model"))
          .also { primaryRequest = it }
      } else {
        Response(url, bytes).also { cdnRequest = it }
      }
    }

    val result = downloader.download(primary, staging(), mirror, { true }, accessToken = "test-token")

    assertArrayEquals(bytes, result.readBytes())
    assertEquals("Bearer test-token", primaryRequest.getRequestProperty("Authorization"))
    assertNull(cdnRequest.getRequestProperty("Authorization"))
  }

  @Test
  fun huggingFaceRedirectRequestsDoNotReceivePrimaryCredentials() = runTest {
    for (redirectUrl in listOf(
        "https://cdn-lfs.huggingface.co/model",
        "https://huggingface.co/redirected/model",
      )
    ) {
      lateinit var primaryRequest: Response
      lateinit var redirectRequest: Response
      val downloader = ModelFileDownloader { url ->
        if (url.toString() == primary) {
          Response(url, byteArrayOf(), 302, mapOf("Location" to redirectUrl))
            .also { primaryRequest = it }
        } else {
          Response(url, bytes).also { redirectRequest = it }
        }
      }
      val staging = File(temp.newFolder(), "model.olliterttmp")

      val result = downloader.download(primary, staging, mirror, { true }, accessToken = "test-token")

      assertArrayEquals(bytes, result.readBytes())
      assertEquals("Bearer test-token", primaryRequest.getRequestProperty("Authorization"))
      assertNull(redirectUrl, redirectRequest.getRequestProperty("Authorization"))
    }
  }

  @Test
  fun redirectingBackToThePrimaryDoesNotReattachCredentials() = runTest {
    val requests = mutableListOf<Response>()
    val cdn = "https://cdn-lfs.huggingface.co/model"
    val downloader = ModelFileDownloader { url ->
      when (requests.size) {
        0 -> Response(url, byteArrayOf(), 302, mapOf("Location" to cdn))
        1 -> Response(url, byteArrayOf(), 302, mapOf("Location" to primary))
        else -> Response(url, bytes)
      }.also { requests.add(it) }
    }

    val result = downloader.download(primary, staging(), mirror, { true }, accessToken = "test-token")

    assertArrayEquals(bytes, result.readBytes())
    assertEquals(listOf(primary, cdn, primary), requests.map { it.url.toString() })
    assertEquals("Bearer test-token", requests.first().getRequestProperty("Authorization"))
    assertTrue(requests.drop(1).all { it.getRequestProperty("Authorization") == null })
  }

  @Test
  fun successRemovesObsoleteMirrorPartialsButPreservesOtherFiles() = runTest {
    val staging = staging()
    val obsolete = modelScopeStagingFile(staging, "0".repeat(64)).apply { writeText("old partial") }
    val legacy = File("${staging.path}.modelscope").apply { writeText("legacy partial") }
    val unrelated = File(temp.root, "different.olliterttmp.${"1".repeat(64)}.modelscope")
      .apply { writeText("other download") }
    val finalFile = File(temp.root, "model.litertlm").apply { writeText("installed model") }
    val directory = File("${staging.path}.${"2".repeat(64)}.modelscope").apply { mkdir() }
    val downloader = ModelFileDownloader { url -> Response(url, bytes) }

    val result = downloader.download(primary, staging, mirror, { true })

    assertArrayEquals(bytes, result.readBytes())
    assertFalse(obsolete.exists())
    assertFalse(legacy.exists())
    assertEquals("other download", unrelated.readText())
    assertEquals("installed model", finalFile.readText())
    assertTrue(directory.isDirectory)
  }

  @Test
  fun networkFailureRequiresConsentWithoutContactingMirror() = runTest {
    val requests = mutableListOf<String>()
    val downloader = ModelFileDownloader { url ->
      requests.add(url.toString())
      throw SocketTimeoutException("timed out")
    }
    try {
      downloader.download(primary, staging(), mirror, { false })
      fail("Expected consent request")
    } catch (failure: ModelScopeConsentRequiredException) {
      assertTrue(failure.primaryError.isNotBlank())
    }
    assertEquals(listOf(primary), requests)
  }

  @Test
  fun gatedNetworkFailureDoesNotTreatATokenOrMirrorConsentAsAccess() = runTest {
    val requests = mutableListOf<String>()
    val downloader = ModelFileDownloader { url ->
      requests.add(url.toString())
      throw SocketTimeoutException("no access response")
    }
    try {
      downloader.download(primary, staging(), gatedMirror, { true }, accessToken = "unverified-token")
      fail("Expected the original network failure without a mirror request")
    } catch (_: DownloadNetworkException) { }
    assertEquals(listOf(primary), requests)
  }

  @Test
  fun gatedPreflightFailureCannotSkipFreshAccessValidation() = runTest {
    for (code in listOf(401, 403)) {
      val requests = mutableListOf<String>()
      val downloader = ModelFileDownloader { url ->
        requests.add(url.toString())
        Response(url, byteArrayOf(), code)
      }
      try {
        downloader.download(
          primary, staging(), gatedMirror, { true }, accessToken = "revoked-token",
          primaryError = "an earlier request timed out",
        )
        fail("Expected the current permission denial")
      } catch (failure: com.ollitert.llm.server.data.download.DownloadHttpException) {
        assertEquals(code, failure.status)
      }
      assertEquals(listOf(primary), requests)
    }
  }

  @Test
  fun gatedTransferCanFallBackAfterReceivingAuthorizedModelBytes() = runTest {
    val requests = mutableListOf<String>()
    val downloader = ModelFileDownloader { url ->
      requests.add(url.toString())
      if (url.toString() == primary) {
        object : Response(url, byteArrayOf()) {
          override fun getInputStream(): InputStream =
            object : ByteArrayInputStream(bytes.copyOf(12)) {
              override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (available() == 0) throw SocketTimeoutException("transfer interrupted")
                return super.read(buffer, offset, length)
              }
            }
        }
      } else Response(url, bytes)
    }
    val result = downloader.download(primary, staging(), gatedMirror, { true }, accessToken = "valid-token")
    assertArrayEquals(bytes, result.readBytes())
    assertEquals(listOf(primary, mirrorUrl), requests)
  }

  @Test
  fun loginHtmlIsNotEvidenceOfAuthorizedArtifactAccess() = runTest {
    val requests = mutableListOf<String>()
    val downloader = ModelFileDownloader { url ->
      requests.add(url.toString())
      if (url.toString() == primary) Response(url, "<html>sign in</html>".toByteArray())
      else Response(url, bytes)
    }
    try {
      downloader.download(primary, staging(), gatedMirror, { true }, accessToken = "invalid-token")
      fail("Expected invalid model content, not mirror fallback")
    } catch (_: DownloadIntegrityException) { }
    assertEquals(listOf(primary), requests)
  }

  @Test
  fun alternateStartsAtZeroAndNeverReceivesPrimaryCredentials() = runTest {
    val staging = staging().apply { writeBytes("old primary bytes".toByteArray()) }
    lateinit var mirrorRequest: Response
    val downloader = ModelFileDownloader { url ->
      if (url.toString() == primary) throw SocketTimeoutException("timed out")
      Response(url, bytes).also { mirrorRequest = it }
    }
    val result = downloader.download(primary, staging, mirror, { true }, accessToken = "hf-private")
    assertArrayEquals(bytes, result.readBytes())
    assertNull(mirrorRequest.getRequestProperty("Authorization"))
    assertNull(mirrorRequest.getRequestProperty("Range"))
    assertFalse(staging.exists())
  }

  @Test
  fun mirrorRedirectDoesNotReceivePrimaryCredentials() = runTest {
    lateinit var cdnRequest: Response
    val downloader = ModelFileDownloader { url ->
      when (url.host) {
        "huggingface.co" -> throw SocketTimeoutException()
        "modelscope.cn" -> Response(
          url, byteArrayOf(), 302, mapOf("Location" to "https://cdn-lfs-cn-1.modelscope.cn/file"),
        )
        else -> Response(url, bytes).also { cdnRequest = it }
      }
    }
    downloader.download(primary, staging(), mirror, { true }, accessToken = "hf-private")
    assertNull(cdnRequest.getRequestProperty("Authorization"))
  }

  @Test
  fun savedMirrorPartialResumesOnlyAgainstTheSameMirror() = runTest {
    val staging = staging()
    modelScopeStagingFile(staging, mirror.sha256).writeBytes(bytes.take(8).toByteArray())
    lateinit var mirrorRequest: Response
    val downloader = ModelFileDownloader { url ->
      if (url.toString() == primary) throw SocketTimeoutException()
      Response(
        url, bytes.drop(8).toByteArray(), 206,
        mapOf("Content-Range" to "bytes 8-${bytes.lastIndex}/${bytes.size}"),
      ).also { mirrorRequest = it }
    }
    val result = downloader.download(primary, staging, mirror, { true })
    assertEquals("bytes=8-", mirrorRequest.getRequestProperty("Range"))
    assertArrayEquals(bytes, result.readBytes())
  }

  @Test
  fun ignoredRangeRestartsInsteadOfAppending() = runTest {
    val staging = staging().apply { writeBytes(bytes.take(8).toByteArray()) }
    val downloader = ModelFileDownloader { url -> Response(url, bytes) }
    val result = downloader.download(primary, staging, mirror, { true })
    assertArrayEquals(bytes, result.readBytes())
  }

  @Test
  fun bothProviderFailuresAreReportedAndAttemptsAreBounded() = runTest {
    val requests = mutableListOf<String>()
    val downloader = ModelFileDownloader { url ->
      requests.add(url.toString())
      Response(url, byteArrayOf(), 503)
    }
    try {
      downloader.download(primary, staging(), mirror, { true })
      fail("Expected both failures")
    } catch (failure: ModelScopeDownloadException) {
      assertTrue(failure.message.orEmpty().contains("Hugging Face"))
      assertTrue(failure.message.orEmpty().contains("ModelScope"))
    }
    assertEquals(listOf(primary, mirrorUrl), requests)
  }

  @Test
  fun authenticationNotFoundAndCertificateErrorsNeverFallBack() = runTest {
    for (code in listOf(401, 403, 404)) {
      val requests = mutableListOf<String>()
      val downloader = ModelFileDownloader { url ->
        requests.add(url.toString())
        Response(url, byteArrayOf(), code)
      }
      try {
        downloader.download(primary, staging(), mirror, { true })
        fail("Expected HTTP failure")
      } catch (_: com.ollitert.llm.server.data.download.DownloadHttpException) { }
      assertEquals(listOf(primary), requests)
    }
    val requests = mutableListOf<String>()
    val downloader = ModelFileDownloader { url ->
      requests.add(url.toString())
      throw SSLHandshakeException("certificate rejected")
    }
    try {
      downloader.download(primary, staging(), mirror, { true })
      fail("Expected TLS failure")
    } catch (_: SSLHandshakeException) { }
    assertEquals(listOf(primary), requests)
  }

  @Test
  fun changedMirrorBytesCannotBePublishedDespiteMatchingSizeAndHeader() = runTest {
    val corrupt = bytes.copyOf().apply { this[lastIndex] = 0 }
    val downloader = ModelFileDownloader { url ->
      if (url.toString() == primary) throw SocketTimeoutException()
      Response(url, corrupt)
    }
    try {
      downloader.download(primary, staging(), mirror, { true })
      fail("Expected checksum failure")
    } catch (failure: ModelScopeDownloadException) {
      assertTrue(failure.cause is DownloadIntegrityException)
    }
    assertFalse(modelScopeStagingFile(staging(), mirror.sha256).exists())
  }

  @Test
  fun revokedConsentPreventsSkippingToTheMirror() = runTest {
    val requests = mutableListOf<String>()
    val downloader = ModelFileDownloader { url ->
      requests.add(url.toString())
      Response(url, bytes)
    }
    downloader.download(primary, staging(), mirror, { false }, primaryError = "earlier timeout")
    assertEquals(listOf(primary), requests)
  }

  @Test
  fun approvedPreflightFailureDoesNotRepeatThePrimaryAttempt() = runTest {
    val requests = mutableListOf<String>()
    val downloader = ModelFileDownloader { url ->
      requests.add(url.toString())
      Response(url, bytes)
    }
    downloader.download(primary, staging(), mirror, { true }, primaryError = "DNS failed")
    assertEquals(listOf(mirrorUrl), requests)
  }

  @Test
  fun cancellationNeverContactsTheAlternate() = runTest {
    val requests = mutableListOf<String>()
    val downloader = ModelFileDownloader { url ->
      requests.add(url.toString())
      throw CancellationException("cancelled")
    }
    try {
      downloader.download(primary, staging(), mirror, { true })
      fail("Expected cancellation")
    } catch (_: CancellationException) { }
    assertEquals(listOf(primary), requests)
  }

  @Test
  fun failedLocalWriteNeverContactsTheAlternate() = runTest {
    val requests = mutableListOf<String>()
    val downloader = ModelFileDownloader { url ->
      requests.add(url.toString())
      Response(url, bytes)
    }
    val missingParent = File(temp.root, "missing/model.olliterttmp")
    try {
      downloader.download(primary, missingParent, mirror, { true })
      fail("Expected local file failure")
    } catch (_: java.io.FileNotFoundException) { }
    assertEquals(listOf(primary), requests)
  }

  @Test
  fun truncatedPrimaryFallsBackWithoutCombiningBytes() = runTest {
    val downloader = ModelFileDownloader { url ->
      Response(url, if (url.toString() == primary) bytes.take(10).toByteArray() else bytes)
    }
    val result = downloader.download(primary, staging(), mirror, { true })
    assertArrayEquals(bytes, result.readBytes())
    assertEquals(modelScopeStagingFile(staging(), mirror.sha256), result)
  }

  @Test
  fun invalidMirrorResumeRangeIsRejectedAndItsStagingFileRemoved() = runTest {
    val staging = staging()
    modelScopeStagingFile(staging, mirror.sha256).writeBytes(bytes.take(8).toByteArray())
    val downloader = ModelFileDownloader { url ->
      if (url.toString() == primary) throw SocketTimeoutException()
      Response(url, bytes, 206, mapOf("Content-Range" to "bytes 0-${bytes.lastIndex}/${bytes.size}"))
    }
    try {
      downloader.download(primary, staging, mirror, { true })
      fail("Expected resume mismatch")
    } catch (failure: ModelScopeDownloadException) {
      assertTrue(failure.cause is DownloadIntegrityException)
    }
    assertFalse(modelScopeStagingFile(staging, mirror.sha256).exists())
  }

  @Test
  fun completedMirrorStagingIsVerifiedBeforeReuse() = runTest {
    val staging = staging()
    modelScopeStagingFile(staging, mirror.sha256).writeBytes(bytes)
    val requests = mutableListOf<String>()
    val downloader = ModelFileDownloader { url ->
      requests.add(url.toString())
      throw SocketTimeoutException()
    }
    val result = downloader.download(primary, staging, mirror, { true })
    assertArrayEquals(bytes, result.readBytes())
    assertEquals(listOf(primary), requests)
  }

  @Test
  fun downgradeRedirectIsRejectedWithoutFallback() = runTest {
    val requests = mutableListOf<String>()
    val downloader = ModelFileDownloader { url ->
      requests.add(url.toString())
      Response(url, byteArrayOf(), 302, mapOf("Location" to "http://example.test/model"))
    }
    try {
      downloader.download(primary, staging(), mirror, { true })
      fail("Expected unsafe redirect rejection")
    } catch (_: java.net.ProtocolException) { }
    assertEquals(listOf(primary), requests)
  }

  @Test
  fun changingTheMirrorArtifactNeverReusesItsOldPartialFile() = runTest {
    val staging = staging()
    val oldMirror = mirror.copy(url = "$mirrorUrl-old", sha256 = "0".repeat(64))
    val first = ModelFileDownloader { url ->
      if (url.toString() == primary) throw SocketTimeoutException()
      Response(url, bytes.take(10).toByteArray())
    }
    try {
      first.download(primary, staging, oldMirror, { true })
      fail("Expected incomplete old mirror")
    } catch (_: ModelScopeDownloadException) { }

    lateinit var mirrorRequest: Response
    val second = ModelFileDownloader { url ->
      if (url.toString() == primary) throw SocketTimeoutException()
      Response(url, bytes).also { mirrorRequest = it }
    }
    val result = second.download(primary, staging, mirror, { true })
    assertArrayEquals(bytes, result.readBytes())
    assertNull(mirrorRequest.getRequestProperty("Range"))
    assertFalse(modelScopeStagingFile(staging, oldMirror.sha256).exists())
  }

  @Test
  fun aCompletedMirrorPartialDoesNotAuthorizeAGatedRetry() = runTest {
    val staging = staging()
    val cached = modelScopeStagingFile(staging, gatedMirror.sha256).apply { writeBytes(bytes) }
    val requests = mutableListOf<String>()
    val downloader = ModelFileDownloader { url ->
      requests.add(url.toString())
      throw SocketTimeoutException("access unavailable")
    }

    try {
      downloader.download(primary, staging, gatedMirror, { true }, accessToken = "unverified-token")
      fail("A saved mirror file must not bypass the current access check")
    } catch (_: DownloadNetworkException) { }

    assertEquals(listOf(primary), requests)
    assertArrayEquals(bytes, cached.readBytes())
  }

  @Test
  fun cachedPrimaryHeaderAndAnEmptyReadDoNotEstablishCurrentAccess() = runTest {
    val staging = staging().apply { writeBytes(bytes.copyOf(8)) }
    val requests = mutableListOf<String>()
    val downloader = ModelFileDownloader { url ->
      requests.add(url.toString())
      if (url.toString() != primary) {
        Response(url, bytes)
      } else {
        object : Response(
          url, byteArrayOf(), 206,
          mapOf("Content-Range" to "bytes 8-${bytes.lastIndex}/${bytes.size}"),
        ) {
          override fun getInputStream(): InputStream = object : InputStream() {
            private var emptyRead = false
            override fun read(): Int = throw SocketTimeoutException("no data")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
              if (emptyRead) throw SocketTimeoutException("no data")
              emptyRead = true
              return 0
            }
          }
        }
      }
    }
    try {
      downloader.download(primary, staging, gatedMirror, { true }, accessToken = "token")
      fail("No new model bytes arrived")
    } catch (_: DownloadNetworkException) { }
    assertEquals(listOf(primary), requests)
  }

  @Test
  fun primaryCredentialsNeverReachAnInsecureOrAmbiguousOrigin() = runTest {
    for (origin in listOf(
        "http://huggingface.co",
        "https://user@huggingface.co",
        "https://huggingface.co:8443",
        "https://huggingface.co.example.test",
      )
    ) {
      lateinit var connection: Response
      val downloader = ModelFileDownloader { url -> Response(url, bytes).also { connection = it } }
      downloader.download("$origin/model", staging(), mirror, { true }, accessToken = "test-token")
      assertNull(connection.getRequestProperty("Authorization"))
      assertTrue(staging().delete())
    }
  }
}
