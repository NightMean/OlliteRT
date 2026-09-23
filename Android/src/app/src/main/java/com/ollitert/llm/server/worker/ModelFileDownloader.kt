package com.ollitert.llm.server.worker

import com.ollitert.llm.server.data.allowlist.isHuggingFaceUrl
import com.ollitert.llm.server.data.download.DownloadHttpException
import com.ollitert.llm.server.data.download.DownloadNetworkException
import com.ollitert.llm.server.data.download.ModelScopeFallback
import com.ollitert.llm.server.data.download.isTransientDownloadFailure
import com.ollitert.llm.server.data.prefs.DOWNLOAD_CONNECT_TIMEOUT_MS
import com.ollitert.llm.server.data.prefs.DOWNLOAD_READ_TIMEOUT_MS
import com.ollitert.llm.server.data.storage.modelScopeStagingFile
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.SSLException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class ModelScopeConsentRequiredException(val primaryError: String) :
  IOException(primaryError)

internal class ModelScopeDownloadException(primaryError: String, cause: IOException) :
  IOException("Hugging Face: $primaryError; ModelScope: ${cause.message}", cause)

internal class DownloadIntegrityException(message: String) : IOException(message)

/** Transfers a known catalog artifact; only the alternate is checked against its published digest. */
internal class ModelFileDownloader(
  private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
  suspend fun download(
    primaryUrl: String,
    staging: File,
    fallback: ModelScopeFallback,
    fallbackEnabled: () -> Boolean,
    accessToken: String? = null,
    primaryError: String? = null,
    onProgress: suspend (receivedBytes: Long, modelScope: Boolean) -> Unit = { _, _ -> },
  ): File {
    currentCoroutineContext().ensureActive()
    val mirrorStaging = modelScopeStagingFile(staging, fallback.sha256)
    var primaryAccessConfirmed = false
    val failure = if (primaryError != null && fallbackEnabled() && !fallback.requiresAccessConfirmation) {
      primaryError
    } else {
      try {
        transfer(
          primaryUrl, staging, fallback.sizeInBytes, null, accessToken,
          onArtifactAccess = { primaryAccessConfirmed = true },
        ) {
          onProgress(it, false)
        }
        cleanObsoleteMirrorPartials(staging)
        return staging
      } catch (error: IOException) {
        if (!isTransientDownloadFailure(error) ||
          (fallback.requiresAccessConfirmation && !primaryAccessConfirmed)
        ) throw error
        error.message ?: error.javaClass.simpleName
      }
    }

    currentCoroutineContext().ensureActive()
    if (!fallbackEnabled()) throw ModelScopeConsentRequiredException(failure)
    try {
      onProgress(mirrorStaging.length(), true)
      transfer(fallback.url, mirrorStaging, fallback.sizeInBytes, fallback.sha256, null) {
        onProgress(it, true)
      }
      deleteStaging(staging)
      cleanObsoleteMirrorPartials(staging, keep = mirrorStaging)
      return mirrorStaging
    } catch (error: IOException) {
      throw ModelScopeDownloadException(failure, error)
    }
  }

  private suspend fun transfer(
    source: String,
    staging: File,
    expectedBytes: Long,
    sha256: String?,
    token: String?,
    onArtifactAccess: () -> Unit = {},
    onProgress: suspend (Long) -> Unit,
  ) {
    try {
      if (staging.length() == expectedBytes && staging.isFile) {
        verifyFile(staging, expectedBytes, sha256)
        onProgress(expectedBytes)
        return
      }
      if (staging.length() > expectedBytes) {
        throw DownloadIntegrityException("Partial model exceeds the expected size")
      }
      val offset = staging.length()
      onProgress(offset)
      val connection = connect(source, offset, token)
      try {
        val status = network { connection.responseCode }
        if (status != 200 && status != 206) throw DownloadHttpException(status)
        val start = if (status == 206) {
          val range = connection.getHeaderField("Content-Range")
          val parts = range?.let { Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(it) }
            ?: throw DownloadIntegrityException("Invalid model Content-Range")
          val first = parts.groupValues[1].toLongOrNull()
          val last = parts.groupValues[2].toLongOrNull()
          val total = parts.groupValues[3].toLongOrNull()
          if (first != offset || last == null || last < offset ||
            total == null || total != expectedBytes || last >= total
          ) {
            throw DownloadIntegrityException("Model resume range does not match the expected artifact")
          }
          offset
        } else {
          0L
        }
        var received = start
        var confirmedResponse = false
        network { connection.inputStream }.use { input ->
          FileOutputStream(staging, start > 0).use { output ->
            onProgress(received)
            val buffer = ByteArray(64 * 1024)
            while (true) {
              currentCoroutineContext().ensureActive()
              val count = network { input.read(buffer) }
              if (count == -1) break
              if (count == 0) continue
              received += count
              if (received > expectedBytes) throw DownloadIntegrityException("Model exceeds the expected size")
              output.write(buffer, 0, count)
              if (!confirmedResponse && received >= 8) {
                verifyModelHeader(staging)
                // Only a valid response in this attempt can establish gated access.
                // Neither persisted partials nor a previous preflight failure can do so.
                onArtifactAccess()
                confirmedResponse = true
              }
              onProgress(received)
            }
          }
        }
      } finally {
        connection.disconnect()
      }
      verifyFile(staging, expectedBytes, sha256)
    } catch (error: DownloadIntegrityException) {
      deleteStaging(staging)
      throw error
    }
  }

  private suspend fun connect(source: String, offset: Long, token: String?): HttpURLConnection {
    var url = URL(source)
    repeat(6) { redirectCount ->
      currentCoroutineContext().ensureActive()
      val connection = network { openConnection(url) }
      try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MS
        connection.readTimeout = DOWNLOAD_READ_TIMEOUT_MS
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
        if (redirectCount == 0 && token != null && isHuggingFaceUrl(url.toString())) {
          connection.setRequestProperty("Authorization", "Bearer $token")
        }
        val status = network { connection.responseCode }
        if (status !in listOf(301, 302, 303, 307, 308)) return connection
        val location = connection.getHeaderField("Location")
          ?: throw ProtocolException("Download redirect has no location")
        val next = URL(url, location)
        if (next.protocol != "https" || next.userInfo != null) {
          throw ProtocolException("Unsafe download redirect")
        }
        url = next
      } catch (failure: Throwable) {
        connection.disconnect()
        throw failure
      }
      connection.disconnect()
    }
    throw ProtocolException("Too many download redirects")
  }

  private suspend fun verifyFile(file: File, expectedBytes: Long, sha256: String?) {
    if (file.length() < expectedBytes) throw EOFException("Model download ended before all bytes arrived")
    if (file.length() != expectedBytes) throw DownloadIntegrityException("Unexpected model size")
    verifyModelHeader(file)
    if (sha256 != null) {
      val digest = MessageDigest.getInstance("SHA-256")
      file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
          currentCoroutineContext().ensureActive()
          val count = input.read(buffer)
          if (count == -1) break
          digest.update(buffer, 0, count)
        }
      }
      val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
      if (actual != sha256) throw DownloadIntegrityException("ModelScope SHA-256 verification failed")
    }
  }

  private fun verifyModelHeader(file: File) {
    val header = ByteArray(8)
    file.inputStream().use { input ->
      if (input.read(header) != header.size || !header.contentEquals("LITERTLM".toByteArray(Charsets.US_ASCII))) {
        throw DownloadIntegrityException("Downloaded file is not a LiteRT-LM model")
      }
    }
  }

  private fun cleanObsoleteMirrorPartials(primary: File, keep: File? = null) {
    val directory = primary.absoluteFile.parentFile
    val candidates = directory.listFiles()
      ?: throw IOException("Could not inspect model staging directory")
    val namePattern = Regex("${Regex.escape(primary.name)}\\.(?:[0-9a-f]{64}\\.)?modelscope")
    for (candidate in candidates) {
      if (candidate.isFile && candidate != keep?.absoluteFile && namePattern.matches(candidate.name)) {
        deleteStaging(candidate)
      }
    }
  }

  private fun deleteStaging(file: File) {
    if (file.exists() && !file.delete()) throw IOException("Could not remove staging file: ${file.name}")
  }

  private inline fun <T> network(operation: () -> T): T = try {
    operation()
  } catch (error: SSLException) {
    throw error
  } catch (error: ProtocolException) {
    throw error
  } catch (error: IOException) {
    throw DownloadNetworkException(error)
  }
}
