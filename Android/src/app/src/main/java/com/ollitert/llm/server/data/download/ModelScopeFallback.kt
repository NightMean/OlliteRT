package com.ollitert.llm.server.data.download

import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException

internal data class ModelScopeFallback(
  val url: String,
  val sizeInBytes: Long,
  val sha256: String,
  val requiresAccessConfirmation: Boolean = true,
)

private data class MirrorArtifact(
  val repository: String,
  val file: String,
  val primaryRevision: String,
  val mirrorRevision: String,
  val bytes: Long,
  val sha256: String,
  val requiresAccessConfirmation: Boolean = true,
)

// Exact revision mappings: a catalog update must not silently reuse an older mirror.
private val artifacts = listOf(
  MirrorArtifact(
    "litert-community/gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm",
    "6e5c4f1e395deb959c494953478fa5cec4b8008f", "1bacc155f57965ae45832a9ccfe96cdfcb0e94e9",
    2588147712L, "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
    requiresAccessConfirmation = false,
  ),
  MirrorArtifact(
    "litert-community/gemma-4-E4B-it-litert-lm", "gemma-4-E4B-it.litertlm",
    "28299f30ee4d43294517a4ac93abd6163412f07f", "46feb4dd5a76f4620508e7055ac9f13cecc267d2",
    3659530240L, "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
    requiresAccessConfirmation = false,
  ),
  MirrorArtifact(
    "google/gemma-3n-E2B-it-litert-lm", "gemma-3n-E2B-it-int4.litertlm",
    "ba9ca88da013b537b6ed38108be609b8db1c3a16", "5c2307361e9740e1e253a247eaac8e29c0170619",
    3655827456L, "2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6",
  ),
  MirrorArtifact(
    "google/gemma-3n-E4B-it-litert-lm", "gemma-3n-E4B-it-int4.litertlm",
    "297ed75955702dec3503e00c2c2ecbbf475300bc", "f1cf210e20560c3d17966e6e5dded590f0d813d3",
    4919541760L, "2e67a6cd51dfe0f793431e6bd4ed8d029c88e10f52ca0469ad38445e3cd3c1f4",
  ),
  MirrorArtifact(
    "litert-community/Gemma3-1B-IT", "gemma3-1b-it-int4.litertlm",
    "42d538a932e8d5b12e6b3b455f5572560bd60b2c", "ea05e64beb281629d682a58fdf681d0c5116f93f",
    584417280L, "1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be",
  ),
  MirrorArtifact(
    "litert-community/Qwen2.5-1.5B-Instruct",
    "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
    "19edb84c69a0212f29a6ef17ba0d6f278b6a1614", "d800dafdf808efa44d49ae6b643c9b6bff024adf",
    1597931520L, "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9",
    requiresAccessConfirmation = false,
  ),
  MirrorArtifact(
    "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
    "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
    "e34bb88632342d1f9640bad579a45134eb1cf988", "b407f8a7661f11e7e1282a9828535d4aa2d890c9",
    1833451520L, "69b35f01759eed765641ab4af589bbe98131fd2825662a086d9037409b8c1295",
    requiresAccessConfirmation = false,
  ),
)

internal fun modelScopeFallback(primaryUrl: String): ModelScopeFallback? {
  val uri = try {
    URI(primaryUrl)
  } catch (_: java.net.URISyntaxException) {
    return null
  }
  if (uri.scheme != "https" || uri.host != "huggingface.co" ||
    uri.userInfo != null || uri.port !in listOf(-1, 443) ||
    uri.rawQuery !in listOf(null, "download=true")
  ) return null
  val artifact = artifacts.firstOrNull {
    uri.rawPath == "/${it.repository}/resolve/${it.primaryRevision}/${it.file}"
  } ?: return null
  return ModelScopeFallback(
    url = "https://modelscope.cn/models/${artifact.repository}/resolve/${artifact.mirrorRevision}/${artifact.file}",
    sizeInBytes = artifact.bytes,
    sha256 = artifact.sha256,
    requiresAccessConfirmation = artifact.requiresAccessConfirmation,
  )
}

internal class DownloadNetworkException(cause: IOException) :
  IOException("Connection failed (${cause.javaClass.simpleName})", cause)

internal class DownloadHttpException(val status: Int) : IOException("HTTP $status")

internal fun isTransientDownloadStatus(status: Int): Boolean =
  status == 408 || status == 429 || status in 500..599

internal fun isTransientDownloadFailure(failure: Throwable): Boolean = when (failure) {
  is DownloadHttpException -> isTransientDownloadStatus(failure.status)
  is DownloadNetworkException, is UnknownHostException, is SocketTimeoutException,
  is SocketException, is EOFException -> true
  else -> false
}
