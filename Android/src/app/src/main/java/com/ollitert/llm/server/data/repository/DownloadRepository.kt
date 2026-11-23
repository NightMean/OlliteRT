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
import com.ollitert.llm.server.data.model.ModelDownloadStatus
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.data.storage.KEY_MODEL_COMMIT_HASH
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_ACCESS_TOKEN
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

internal data class QueuedDownload(val id: UUID, val enqueue: Operation)

internal fun unfinishedModelWorkers(work: List<WorkInfo>): Map<String, UUID> = buildMap {
  work.filterNot { it.state.isFinished }.forEach { info ->
    val name = info.tags.firstOrNull { it.startsWith("$MODEL_NAME_TAG:") }
      ?.removePrefix("$MODEL_NAME_TAG:")?.takeIf { it.isNotEmpty() }
    if (name != null) put(name, info.id)
  }
}

/** Serializes the stable worker-input contract for one model download. */
internal fun buildDownloadRequestData(model: Model): Data {
  val builder =
    Data.Builder()
      .putString(KEY_MODEL_NAME, model.name)
      .putString(KEY_MODEL_SOURCE, model.sourceRepositoryId)
      .putString(KEY_MODEL_URL, model.url)
      .putString(KEY_MODEL_COMMIT_HASH, model.version)
      .putString(KEY_MODEL_DOWNLOAD_MODEL_DIR, model.normalizedName)
      .putString(KEY_MODEL_DOWNLOAD_FILE_NAME, model.downloadFileName)
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
  model.accessToken?.let { builder.putString(KEY_MODEL_DOWNLOAD_ACCESS_TOKEN, it) }
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

  internal fun downloadModel(model: Model): QueuedDownload {
    val request = OneTimeWorkRequestBuilder<DownloadWorker>()
      .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
      .setInputData(buildDownloadRequestData(model))
      .addTag("$MODEL_NAME_TAG:${model.name}")
      .build()
    return QueuedDownload(request.id, workManager.enqueueUniqueWork(model.name, ExistingWorkPolicy.REPLACE, request))
  }

  fun cancelDownloadModel(model: Model) {
    workManager.cancelAllWorkByTag("$MODEL_NAME_TAG:${model.name}")
  }

  /** Called on an IO dispatcher while the model's download lock is held. */
  fun hasUnfinishedDownload(modelName: String): Boolean =
    workManager.getWorkInfosForUniqueWork(modelName).get(10, TimeUnit.SECONDS).any { !it.state.isFinished }

  /** Finds work from a previous process, including requests that have not created a file yet. */
  fun getUnfinishedModelDownloads(): Map<String, UUID> {
    val query = WorkQuery.Builder.fromStates(
      listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED),
    ).build()
    return unfinishedModelWorkers(workManager.getWorkInfos(query).get(10, TimeUnit.SECONDS))
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
      WorkInfo.State.FAILED -> ModelDownloadStatus(
        status = ModelDownloadStatusType.FAILED,
        errorMessage = info.outputData.getString(KEY_MODEL_DOWNLOAD_ERROR_MESSAGE).orEmpty(),
        modelScopeAccessDeniedSite = info.outputData.getString(KEY_MODELSCOPE_ACCESS_DENIED_SITE).orEmpty(),
        failureId = info.id.toString(),
        receivedBytes = receivedBytes,
        totalBytes = totalBytes,
      )
      WorkInfo.State.CANCELLED -> ModelDownloadStatus(status = ModelDownloadStatusType.NOT_DOWNLOADED)
      WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> null
    }
    if (status != null) emit(status)
    info.state.isFinished
  }
}
