/*
 * Copyright 2025 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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

package com.ollitert.llm.server.data.repository
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.ModelFile
import com.ollitert.llm.server.data.model.pendingUpdateFile
import com.ollitert.llm.server.data.allowlist.ModelScopeSite
import com.ollitert.llm.server.data.allowlist.isHuggingFaceUrl
import com.ollitert.llm.server.data.model.ModelDownloadStatus
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.data.storage.KEY_MODEL_COMMIT_HASH
import com.ollitert.llm.server.data.storage.HuggingFaceDownloadAccess
import com.ollitert.llm.server.data.storage.KEY_HUGGING_FACE_DOWNLOAD_ACCESS
import com.ollitert.llm.server.data.storage.KEY_MODELSCOPE_ACCESS_DENIED_SITE
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_ERROR_MESSAGE
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_FILE_NAME
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_MODEL_DIR
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_RATE
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_RECEIVED_BYTES
import com.ollitert.llm.server.data.storage.KEY_MODEL_EXTRA_DATA_DOWNLOAD_FILE_NAMES
import com.ollitert.llm.server.data.storage.KEY_MODEL_EXTRA_DATA_URLS
import com.ollitert.llm.server.data.storage.KEY_MODEL_IS_ZIP
import com.ollitert.llm.server.data.storage.KEY_MODEL_NAME
import com.ollitert.llm.server.data.storage.KEY_MODEL_SOURCE
import com.ollitert.llm.server.data.storage.KEY_MODEL_START_UNZIPPING
import com.ollitert.llm.server.data.storage.KEY_MODEL_STAGE_UPDATE
import com.ollitert.llm.server.data.storage.KEY_MODEL_UPDATE_IN_PLACE
import com.ollitert.llm.server.data.storage.KEY_MODEL_TOTAL_BYTES
import com.ollitert.llm.server.data.storage.KEY_MODEL_FILE_BYTES
import com.ollitert.llm.server.data.storage.KEY_MODEL_EXTRA_FILE_BYTES
import com.ollitert.llm.server.data.storage.KEY_MODEL_UNZIPPED_DIR
import com.ollitert.llm.server.data.storage.KEY_MODEL_URL

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.ollitert.llm.server.worker.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OlliteRT.DownloadRepo"
private const val MODEL_NAME_TAG = "modelName"
private const val UPDATE_VERSION_TAG_PREFIX = "updateVersion:"
private const val UPDATE_FILE_TAG_PREFIX = "updateFile:"

private fun WorkInfo.updateVersion(): String? =
  tags.firstOrNull { it.startsWith(UPDATE_VERSION_TAG_PREFIX) }?.removePrefix(UPDATE_VERSION_TAG_PREFIX)

private fun WorkInfo.updateTarget(): ModelFile? {
  val version = updateVersion() ?: return null
  val fileName = tags.firstOrNull { it.startsWith(UPDATE_FILE_TAG_PREFIX) }?.removePrefix(UPDATE_FILE_TAG_PREFIX)
    ?: return null
  return ModelFile(fileName, version)
}

internal data class QueuedDownload(val id: UUID, val enqueue: Operation)

internal data class ModelDownloadWork(
  val activeWorkers: Map<String, UUID> = emptyMap(),
  val accessDenied: Map<String, ModelDownloadStatus> = emptyMap(),
  val canceledModels: Set<String> = emptySet(),
)

internal fun modelDownloadWork(work: List<WorkInfo>): ModelDownloadWork {
  val activeWorkers = mutableMapOf<String, UUID>()
  val accessDenied = mutableMapOf<String, ModelDownloadStatus>()
  val canceledModels = mutableSetOf<String>()
  for (info in work) {
    val name = info.tags.firstOrNull { it.startsWith("$MODEL_NAME_TAG:") }
      ?.removePrefix("$MODEL_NAME_TAG:")?.takeIf { it.isNotEmpty() } ?: continue
    if (!info.state.isFinished) {
      activeWorkers[name] = info.id
    } else if (info.state == WorkInfo.State.FAILED) {
      val failure = info.failedDownloadStatus()
      if (ModelScopeSite.entries.any { it.name == failure.modelScopeAccessDeniedSite }) {
        accessDenied[name] = failure
      }
    } else if (info.state == WorkInfo.State.CANCELLED) {
      canceledModels.add(name)
    }
  }
  return ModelDownloadWork(activeWorkers, accessDenied - activeWorkers.keys, canceledModels - activeWorkers.keys)
}

private fun WorkInfo.failedDownloadStatus(receivedBytes: Long = 0L, totalBytes: Long = 0L): ModelDownloadStatus {
  val output = outputData
  return ModelDownloadStatus(
    status = ModelDownloadStatusType.FAILED,
    errorMessage = output.getString(KEY_MODEL_DOWNLOAD_ERROR_MESSAGE).orEmpty(),
    modelScopeAccessDeniedSite = output.getString(KEY_MODELSCOPE_ACCESS_DENIED_SITE).orEmpty(),
    failureId = id.toString(),
    receivedBytes = receivedBytes,
    totalBytes = totalBytes,
    updateTarget = updateTarget(),
  )
}

/** Serializes the stable worker-input contract for one model download. */
internal fun buildDownloadRequestData(
  model: Model,
  huggingFaceAccess: HuggingFaceDownloadAccess = HuggingFaceDownloadAccess.ANONYMOUS,
): Data {
  val update = model.pendingUpdateFile()
  val builder =
    Data.Builder()
      .putString(KEY_MODEL_NAME, model.name)
      .putString(KEY_MODEL_SOURCE, model.sourceRepositoryId)
      .putString(KEY_MODEL_URL, model.url)
      .putString(KEY_MODEL_COMMIT_HASH, update?.commitHash ?: model.version)
      .putString(KEY_MODEL_DOWNLOAD_MODEL_DIR, model.normalizedName)
      .putString(KEY_MODEL_DOWNLOAD_FILE_NAME, update?.fileName ?: model.downloadFileName)
      .putBoolean(KEY_MODEL_STAGE_UPDATE, update != null)
      .putBoolean(KEY_MODEL_UPDATE_IN_PLACE, update != null && (update.commitHash == model.version || update.commitHash.isEmpty()))
      .putBoolean(KEY_MODEL_IS_ZIP, model.isZip)
      .putString(KEY_MODEL_UNZIPPED_DIR, model.unzipDir)
      // Model.preProcess() defines totalBytes as the main file plus every extra file.
      .putLong(KEY_MODEL_TOTAL_BYTES, model.totalBytes)
      .putLong(KEY_MODEL_FILE_BYTES, model.sizeInBytes)

  if (model.extraDataFiles.isNotEmpty()) {
    builder
      .putString(KEY_MODEL_EXTRA_DATA_URLS, model.extraDataFiles.joinToString(",") { it.url })
      .putString(KEY_MODEL_EXTRA_FILE_BYTES, model.extraDataFiles.joinToString(",") { it.sizeInBytes.toString() })
      .putString(
        KEY_MODEL_EXTRA_DATA_DOWNLOAD_FILE_NAMES,
        model.extraDataFiles.joinToString(",") { it.downloadFileName },
      )
  }
  if (huggingFaceAccess != HuggingFaceDownloadAccess.ANONYMOUS &&
    (isHuggingFaceUrl(model.url) || model.extraDataFiles.any { isHuggingFaceUrl(it.url) })
  ) {
    builder.putString(KEY_HUGGING_FACE_DOWNLOAD_ACCESS, huggingFaceAccess.name)
  }
  return builder.build()
}

/**
 * Repository for managing model downloads using WorkManager.
 *
 * This class provides methods to initiate model downloads, cancel downloads, observe download
 * progress, and retrieve information about enqueued or running download tasks. It utilizes
 * WorkManager to handle background download operations.
 */
@Singleton
class DownloadRepository @Inject constructor(
  @ApplicationContext context: Context,
) {
  private val workManager = WorkManager.getInstance(context)

  internal fun downloadModel(model: Model, huggingFaceAccess: HuggingFaceDownloadAccess): QueuedDownload {
    val data = buildDownloadRequestData(model, huggingFaceAccess)
    val request = OneTimeWorkRequestBuilder<DownloadWorker>()
      .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
      .setInputData(data)
      // Keep terminal access results available when the app returns after a background failure.
      .keepResultsForAtLeast(7, TimeUnit.DAYS)
      .addTag("$MODEL_NAME_TAG:${model.name}")
      .apply {
        if (data.getBoolean(KEY_MODEL_STAGE_UPDATE, false)) {
          addTag(UPDATE_VERSION_TAG_PREFIX + checkNotNull(data.getString(KEY_MODEL_COMMIT_HASH)))
          addTag(UPDATE_FILE_TAG_PREFIX + checkNotNull(data.getString(KEY_MODEL_DOWNLOAD_FILE_NAME)))
        }
      }
      .build()
    return QueuedDownload(request.id, workManager.enqueueUniqueWork(model.name, ExistingWorkPolicy.REPLACE, request))
  }

  /** Cancel the attempts observed now, without canceling a later replacement by name. */
  internal fun cancelUnfinishedModelDownload(modelName: String): Set<String> {
    val attempts = workForModel(modelName)
    attempts.filterNot { it.state.isFinished }.forEach { info ->
      workManager.cancelWorkById(info.id).result.get(10, TimeUnit.SECONDS)
    }
    // The catalog may now point at a newer revision. Clean only stages owned by these attempts.
    return attempts.filter { it.state != WorkInfo.State.SUCCEEDED }.mapNotNullTo(mutableSetOf()) { it.updateVersion() }
  }

  /** Called on an IO dispatcher while the model's download lock is held. */
  fun hasUnfinishedDownload(modelName: String): Boolean =
    workForModel(modelName).any { !it.state.isFinished }

  private fun workForModel(modelName: String): List<WorkInfo> =
    workManager.getWorkInfosForUniqueWork(modelName).get(10, TimeUnit.SECONDS)

  /** Reads pending work and terminal provider access failures in one WorkManager snapshot. */
  internal fun getModelDownloadWork(): ModelDownloadWork {
    val query = WorkQuery.Builder.fromTags(listOf(DownloadWorker::class.java.name))
      .addStates(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED,
        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED))
      .build()
    // REPLACE removes the previous record for this model when a new attempt is enqueued.
    return modelDownloadWork(workManager.getWorkInfos(query).get(10, TimeUnit.SECONDS))
  }

  fun cancelDownloads(modelNames: List<String>, onComplete: (Boolean) -> Unit) {
    val executor = Executors.newSingleThreadExecutor()
    executor.execute {
      try {
        val operations = modelNames.map { workManager.cancelUniqueWork(it) }
        operations.forEach { it.result.get(10, TimeUnit.SECONDS) }
        onComplete(true)
      } catch (e: Exception) {
        Log.e(TAG, "Could not cancel pending model downloads", e)
        onComplete(false)
      } finally {
        executor.shutdown()
      }
    }
  }

  internal fun observeWorkerProgress(workerId: UUID, totalBytes: Long): Flow<ModelDownloadStatus> =
    modelDownloadStatuses(workManager.getWorkInfoByIdFlow(workerId), totalBytes)
}

/** Emits progress and the terminal state for one collection, then releases the WorkManager subscription. */
internal fun modelDownloadStatuses(work: Flow<WorkInfo?>, totalBytes: Long): Flow<ModelDownloadStatus> = flow {
  var receivedBytes = 0L
  work.first { current ->
    // Collection starts after a successful enqueue or against an ID from WorkManager.
    val info = checkNotNull(current) { "The download record is no longer available" }
    val status = when (info.state) {
      WorkInfo.State.RUNNING -> {
        val progress = info.progress
        val bytes = progress.getLong(KEY_MODEL_DOWNLOAD_RECEIVED_BYTES, 0L)
        when {
          progress.getBoolean(KEY_MODEL_START_UNZIPPING, false) ->
            ModelDownloadStatus(status = ModelDownloadStatusType.UNZIPPING)
          bytes > 0 -> {
            receivedBytes = bytes
            ModelDownloadStatus(
              status = ModelDownloadStatusType.IN_PROGRESS,
              totalBytes = totalBytes,
              receivedBytes = receivedBytes,
              bytesPerSecond = progress.getLong(KEY_MODEL_DOWNLOAD_RATE, 0L),
            )
          }
          else -> null
        }
      }
      WorkInfo.State.SUCCEEDED -> ModelDownloadStatus(status = ModelDownloadStatusType.SUCCEEDED)
      WorkInfo.State.FAILED -> info.failedDownloadStatus(receivedBytes, totalBytes)
      WorkInfo.State.CANCELLED -> ModelDownloadStatus(status = ModelDownloadStatusType.NOT_DOWNLOADED)
      WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> null
    }
    if (status != null) emit(status)
    info.state.isFinished
  }
}
