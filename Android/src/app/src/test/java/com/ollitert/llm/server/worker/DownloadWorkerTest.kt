package com.ollitert.llm.server.worker

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerParameters
import androidx.work.impl.utils.futures.SettableFuture
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.download.modelScopeFallback
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.data.prefs.KEY_MODELSCOPE_FALLBACK
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.data.repository.DefaultModelStorageRepository
import com.ollitert.llm.server.data.repository.buildDownloadRequestData
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_ERROR_MESSAGE
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_RECEIVED_BYTES
import com.ollitert.llm.server.data.storage.KEY_MODEL_FROM_MODELSCOPE
import com.ollitert.llm.server.data.storage.TMP_FILE_EXT
import com.ollitert.llm.server.data.storage.modelScopeStagingFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.UUID
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadWorkerTest {
  @get:Rule val temp = TemporaryFolder()
  private val context = mockk<Context>(relaxed = true)
  private val preferences = mockk<SharedPreferences>(relaxed = true)
  private val progress = mutableListOf<Data>()
  private var fallbackEnabled = false

  @Before
  fun setUp() {
    every { context.applicationContext } returns context
    every { context.getExternalFilesDir(null) } returns temp.root
    every { context.getSharedPreferences(any(), any()) } returns preferences
    ServerPrefs.resetToDefaults(context)
    every { preferences.getBoolean(KEY_MODELSCOPE_FALLBACK, false) } answers { fallbackEnabled }
    every { context.getString(any()) } returns "notification"
    every { context.getString(any(), *anyVararg()) } returns "notification"
    every { context.getString(R.string.download_error_timeout) } returns "timeout"
    every { context.getString(R.string.download_error_no_internet) } returns "offline"
    every { context.getString(R.string.download_error_connection_lost) } returns "connection lost"
    every { context.getString(R.string.download_error_network) } returns "network"
    every { context.getString(R.string.download_error_unauthorized) } returns "unauthorized"
    every { context.getString(R.string.download_error_not_found) } returns "not found"
    mockkStatic(Environment::class)
    every { Environment.getDataDirectory() } returns temp.root
    mockkConstructor(StatFs::class, NotificationCompat.Builder::class)
    every { anyConstructed<StatFs>().availableBytes } returns 2L * 1024 * 1024 * 1024
    every { anyConstructed<NotificationCompat.Builder>().build() } returns mockk<Notification>()
  }

  @After
  fun tearDown() {
    ServerPrefs.resetToDefaults(context)
    unmockkConstructor(StatFs::class, NotificationCompat.Builder::class)
    unmockkStatic(Environment::class)
  }

  private fun gatedModel() = Model(
    name = "Gemma3-1B-IT",
    downloadFileName = "gemma3-1b-it-int4.litertlm",
    version = "42d538a932e8d5b12e6b3b455f5572560bd60b2c",
    url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/42d538a932e8d5b12e6b3b455f5572560bd60b2c/gemma3-1b-it-int4.litertlm",
    sizeInBytes = 584417280L,
  ).apply { preProcess() }

  private fun publicModel() = Model(
    name = "Gemma-4-E2B",
    downloadFileName = "gemma-4-E2B-it.litertlm",
    version = "6e5c4f1e395deb959c494953478fa5cec4b8008f",
    url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm",
    sizeInBytes = 2588147712L,
  ).apply { preProcess() }

  private fun worker(
    model: Model,
    openConnection: (URL) -> HttpURLConnection,
  ): DownloadWorker {
    val foregroundUpdater = mockk<ForegroundUpdater>()
    every { foregroundUpdater.setForegroundAsync(any(), any(), any()) } answers {
      SettableFuture.create<Void>().apply { set(null) }
    }
    val progressUpdater = mockk<ProgressUpdater>()
    every { progressUpdater.updateProgress(any(), any(), any()) } answers {
      progress.add(thirdArg())
      SettableFuture.create<Void>().apply { set(null) }
    }
    val params = mockk<WorkerParameters>(relaxed = true)
    every { params.id } returns UUID.randomUUID()
    every { params.inputData } returns buildDownloadRequestData(model)
    every { params.workerContext } returns Dispatchers.IO
    every { params.foregroundUpdater } returns foregroundUpdater
    every { params.progressUpdater } returns progressUpdater
    return DownloadWorker(context, params, openConnection)
  }

  private class Response(
    url: URL,
    private val body: ByteArray = byteArrayOf(),
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

  @Test
  fun gatedNetworkFailuresKeepTheirSpecificWorkerErrorMessages() = runTest {
    val model = gatedModel()
    for ((failure, expected) in listOf(
        SocketTimeoutException("test timeout") to "timeout",
        UnknownHostException("test DNS failure") to "offline",
        SocketException("test disconnect") to "connection lost",
      )
    ) {
      for (consent in listOf(false, true)) {
        fallbackEnabled = consent
        val requests = mutableListOf<String>()
        val worker = worker(model) { url ->
          requests.add(url.toString())
          throw failure
        }

        val result = worker.doWork() as ListenableWorker.Result.Failure

        assertEquals(expected, result.outputData.getString(KEY_MODEL_DOWNLOAD_ERROR_MESSAGE))
        assertFalse(result.outputData.getBoolean(KEY_MODEL_FROM_MODELSCOPE, false))
        assertEquals(listOf(model.url), requests)
      }
    }
  }

  @Test
  fun restoredProgressMatchesThePrimaryPartialResumedByTheWorker() = runTest {
    val model = gatedModel()
    val staging = File(model.getPath(context, "${model.downloadFileName}.$TMP_FILE_EXT"))
    staging.parentFile.mkdirs()
    staging.writeText("LITERTLM")
    val fallback = requireNotNull(modelScopeFallback(model.url))
    val mirror = modelScopeStagingFile(staging, fallback.sha256).apply { writeBytes(ByteArray(16)) }
    val stored = DefaultModelStorageRepository(context).getModelDownloadStatus(model)
    lateinit var response: Response
    val requests = mutableListOf<String>()
    val worker = worker(model) { url ->
      requests.add(url.toString())
      Response(
        url, status = 206,
        headers = mapOf("Content-Range" to "bytes 8-584417279/584417280"),
      ).also { response = it }
    }

    val result = worker.doWork()

    assertTrue(result is ListenableWorker.Result.Failure)
    assertEquals(listOf(model.url), requests)
    assertEquals("bytes=8-", response.getRequestProperty("Range"))
    assertEquals(8L, progress.first().getLong(KEY_MODEL_DOWNLOAD_RECEIVED_BYTES, -1L))
    assertEquals(ModelDownloadStatusType.PARTIALLY_DOWNLOADED, stored.status)
    assertEquals(8L, stored.receivedBytes)
    assertFalse(stored.fromModelScope)
    assertEquals(8L, staging.length())
    assertEquals(16L, mirror.length())
  }

  @Test
  fun retryReportsPrimaryProgressBeforeConnectingWithOnlyAMirrorPartial() = runTest {
    val model = gatedModel()
    val staging = File(model.getPath(context, "${model.downloadFileName}.$TMP_FILE_EXT"))
    staging.parentFile.mkdirs()
    val fallback = requireNotNull(modelScopeFallback(model.url))
    val mirror = modelScopeStagingFile(staging, fallback.sha256).apply { writeBytes(ByteArray(16)) }
    val stored = DefaultModelStorageRepository(context).getModelDownloadStatus(model)
    assertTrue(stored.fromModelScope)
    assertEquals(16L, stored.receivedBytes)
    var progressBeforeRequest = emptyList<Data>()
    val requests = mutableListOf<String>()
    fallbackEnabled = true
    val worker = worker(model) { url ->
      requests.add(url.toString())
      progressBeforeRequest = progress.toList()
      throw SocketTimeoutException("test timeout")
    }

    val result = worker.doWork() as ListenableWorker.Result.Failure

    assertEquals(listOf(model.url), requests)
    assertEquals(1, progressBeforeRequest.size)
    assertEquals(0L, progressBeforeRequest.single().getLong(KEY_MODEL_DOWNLOAD_RECEIVED_BYTES, -1L))
    assertFalse(progressBeforeRequest.single().getBoolean(KEY_MODEL_FROM_MODELSCOPE, false))
    assertEquals("timeout", result.outputData.getString(KEY_MODEL_DOWNLOAD_ERROR_MESSAGE))
    assertFalse(staging.exists())
    assertEquals(16L, mirror.length())
  }

  @Test
  fun workManagerCanStillUseTheTwoArgumentConstructor() {
    assertNotNull(DownloadWorker::class.java.getConstructor(Context::class.java, WorkerParameters::class.java))
  }

  @Test
  fun unmappedDownloadsKeepTheirSpecificNetworkMessages() = runTest {
    val model = gatedModel().copy(url = "https://huggingface.co/org/model/resolve/revision/model.litertlm")
    for ((failure, expected) in listOf(
        SocketTimeoutException("test timeout") to "timeout",
        UnknownHostException("test DNS failure") to "offline",
        SocketException("test disconnect") to "connection lost",
      )
    ) {
      val requests = mutableListOf<String>()
      val worker = worker(model) { url ->
        requests.add(url.toString())
        throw failure
      }

      val result = worker.doWork() as ListenableWorker.Result.Failure

      assertEquals(expected, result.outputData.getString(KEY_MODEL_DOWNLOAD_ERROR_MESSAGE))
      assertEquals(listOf(model.url), requests)
    }
  }

  @Test
  fun permissionAndMissingFileErrorsDoNotTryTheMirror() = runTest {
    val model = publicModel()
    fallbackEnabled = true
    for ((code, expected) in listOf(401 to "unauthorized", 403 to "unauthorized", 404 to "not found")) {
      val requests = mutableListOf<String>()
      val worker = worker(model) { url ->
        requests.add(url.toString())
        Response(url, status = code)
      }

      val result = worker.doWork() as ListenableWorker.Result.Failure

      assertEquals(expected, result.outputData.getString(KEY_MODEL_DOWNLOAD_ERROR_MESSAGE))
      assertEquals(listOf(model.url), requests)
      assertFalse(result.outputData.getBoolean(KEY_MODEL_FROM_MODELSCOPE, false))
    }
  }

  @Test
  fun certificateAndProtocolErrorsDoNotTryTheMirror() = runTest {
    val model = publicModel()
    fallbackEnabled = true
    for (failure in listOf(SSLHandshakeException("test certificate error"), ProtocolException("test redirect error"))) {
      val requests = mutableListOf<String>()
      val worker = worker(model) { url ->
        requests.add(url.toString())
        throw failure
      }

      val result = worker.doWork() as ListenableWorker.Result.Failure

      assertEquals("network", result.outputData.getString(KEY_MODEL_DOWNLOAD_ERROR_MESSAGE))
      assertEquals(listOf(model.url), requests)
      assertFalse(result.outputData.getBoolean(KEY_MODEL_FROM_MODELSCOPE, false))
    }
  }

  @Test
  fun cancellationPropagatesWithoutTryingTheMirror() = runTest {
    val model = publicModel()
    fallbackEnabled = true
    val requests = mutableListOf<String>()
    val cancellation = CancellationException("test cancellation")
    val worker = worker(model) { url ->
      requests.add(url.toString())
      throw cancellation
    }

    try {
      worker.doWork()
      fail("Expected cancellation")
    } catch (failure: CancellationException) {
      assertEquals("test cancellation", failure.message)
    }

    assertEquals(listOf(model.url), requests)
  }

  @Test
  fun localWriteFailurePreservesTheInstalledModelWithoutTryingTheMirror() = runTest {
    val model = publicModel()
    fallbackEnabled = true
    val staging = File(model.getPath(context, "${model.downloadFileName}.$TMP_FILE_EXT"))
    staging.mkdirs()
    val installed = File(model.getPath(context)).apply { writeText("installed model") }
    val requests = mutableListOf<String>()
    val worker = worker(model) { url ->
      requests.add(url.toString())
      Response(url, "LITERTLM".toByteArray())
    }

    val result = worker.doWork() as ListenableWorker.Result.Failure

    assertEquals("network", result.outputData.getString(KEY_MODEL_DOWNLOAD_ERROR_MESSAGE))
    assertEquals(listOf(model.url), requests)
    assertEquals("installed model", installed.readText())
    assertTrue(staging.isDirectory)
  }

  @Test
  fun bothProviderFailuresKeepTheirReasonsAndTheActiveSource() = runTest {
    val model = publicModel()
    val fallback = requireNotNull(modelScopeFallback(model.url))
    fallbackEnabled = true
    val requests = mutableListOf<String>()
    val worker = worker(model) { url ->
      requests.add(url.toString())
      if (url.toString() == model.url) throw UnknownHostException("test DNS failure")
      throw SocketTimeoutException("test mirror timeout")
    }

    val result = worker.doWork() as ListenableWorker.Result.Failure
    val message = result.outputData.getString(KEY_MODEL_DOWNLOAD_ERROR_MESSAGE).orEmpty()

    assertEquals(listOf(model.url, fallback.url), requests)
    assertTrue(message.contains("Hugging Face: Connection failed (UnknownHostException)"))
    assertTrue(message.contains("ModelScope: Connection failed (SocketTimeoutException)"))
    assertTrue(result.outputData.getBoolean(KEY_MODEL_FROM_MODELSCOPE, false))
    assertEquals(listOf(false, true), progress.map { it.getBoolean(KEY_MODEL_FROM_MODELSCOPE, false) }.distinct())
  }

  @Test
  fun stalePreflightAndCachedMirrorDoNotSkipCurrentGatedAuthorization() = runTest {
    val model = gatedModel().apply { modelScopePrimaryError = "earlier timeout" }
    fallbackEnabled = true
    val staging = File(model.getPath(context, "${model.downloadFileName}.$TMP_FILE_EXT"))
    staging.parentFile.mkdirs()
    val fallback = requireNotNull(modelScopeFallback(model.url))
    val mirror = modelScopeStagingFile(staging, fallback.sha256).apply { writeBytes(ByteArray(16)) }
    val requests = mutableListOf<String>()
    val worker = worker(model) { url ->
      requests.add(url.toString())
      Response(url, status = 403)
    }

    val result = worker.doWork() as ListenableWorker.Result.Failure

    assertEquals("unauthorized", result.outputData.getString(KEY_MODEL_DOWNLOAD_ERROR_MESSAGE))
    assertEquals(listOf(model.url), requests)
    assertFalse(result.outputData.getBoolean(KEY_MODEL_FROM_MODELSCOPE, false))
    assertEquals(16L, mirror.length())
  }
}
