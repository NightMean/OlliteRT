package com.ollitert.llm.server.ui.modelmanager

import android.content.Context
import android.util.Log
import com.ollitert.llm.server.data.IMPORTS_DIR
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelDownloadStatus
import com.ollitert.llm.server.data.ModelDownloadStatusType
import com.ollitert.llm.server.data.TMP_FILE_EXT
import java.io.File

private const val TAG = "ModelFileManager"

/**
 * Manages model file operations: existence checks, downloads status,
 * deletion of model files and import directories.
 *
 * Separated from ModelManagerViewModel to isolate file system concerns
 * from download orchestration, UI state, and model initialization.
 */
class ModelFileManager(
  private val context: Context,
  private val externalFilesDir: File?,
) : ModelFileOps {

  /**
   * Delete stale .tmp files left by interrupted model imports.
   * Called on startup to reclaim storage from partially-copied models.
   */
  fun cleanupStaleImportTmpFiles() {
    try {
      val importsDir = File(externalFilesDir ?: return, IMPORTS_DIR)
      if (!importsDir.exists()) return
      val tmpFiles = importsDir.listFiles { _, name -> name.endsWith(".tmp") } ?: return
      for (file in tmpFiles) {
        Log.i(TAG, "Cleaning up stale import temp file: ${file.name} (${file.length() / (1024 * 1024)}MB)")
        file.delete()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to clean up stale import temp files: ${e.message}")
    }
  }

  override fun isFileInExternalFilesDir(fileName: String): Boolean {
    if (externalFilesDir != null) {
      val file = File(externalFilesDir, fileName)
      return file.exists()
    } else {
      return false
    }
  }

  override fun deleteFileFromExternalFilesDir(fileName: String) {
    if (isFileInExternalFilesDir(fileName)) {
      val file = File(externalFilesDir, fileName)
      file.delete()
    }
  }

  /**
   * Deletes files from the model imports directory whose absolute paths start with a given prefix.
   */
  override fun deleteFilesFromImportDir(fileName: String) {
    val dir = context.getExternalFilesDir(null) ?: return

    val prefixAbsolutePath = "${dir.absolutePath}${File.separator}$fileName"
    val filesToDelete =
      File(dir, IMPORTS_DIR).listFiles { dirFile, name ->
        File(dirFile, name).absolutePath.startsWith(prefixAbsolutePath)
      } ?: arrayOf()
    for (file in filesToDelete) {
      Log.d(TAG, "Deleting file: ${file.name}")
      file.delete()
    }
  }

  override fun deleteDirFromExternalFilesDir(dir: String) {
    if (isFileInExternalFilesDir(dir)) {
      val file = File(externalFilesDir, dir)
      file.deleteRecursively()
    }
  }

  override fun isModelPartiallyDownloaded(model: Model): Boolean {
    if (model.localModelFilePathOverride.isNotEmpty()) {
      return false
    }
    val tmpFilePath =
      model.getPath(context = context, fileName = "${model.downloadFileName}.$TMP_FILE_EXT")
    return File(tmpFilePath).exists()
  }

  override fun isModelDownloaded(model: Model): Boolean {
    val modelRelativePath =
      listOf(model.normalizedName, model.version, model.downloadFileName)
        .joinToString(File.separator)
    val downloadedFileExists =
      model.downloadFileName.isNotEmpty() &&
        ((model.localModelFilePathOverride.isEmpty() &&
          isFileInExternalFilesDir(modelRelativePath)) ||
          (model.localModelFilePathOverride.isNotEmpty() &&
            File(model.localModelFilePathOverride).exists()))

    val unzippedDirectoryExists =
      model.isZip &&
        model.unzipDir.isNotEmpty() &&
        isFileInExternalFilesDir(
          listOf(model.normalizedName, model.version, model.unzipDir).joinToString(File.separator)
        )

    return downloadedFileExists || unzippedDirectoryExists
  }

  override fun getModelDownloadStatus(model: Model): ModelDownloadStatus {
    Log.d(TAG, "Checking model ${model.name} download status...")

    if (model.localFileRelativeDirPathOverride.isNotEmpty()) {
      Log.d(TAG, "Model has localFileRelativeDirPathOverride set. Set status to SUCCEEDED")
      return ModelDownloadStatus(
        status = ModelDownloadStatusType.SUCCEEDED,
        receivedBytes = 0,
        totalBytes = 0,
      )
    }

    var status = ModelDownloadStatusType.NOT_DOWNLOADED
    var receivedBytes = 0L
    var totalBytes = 0L

    if (isModelPartiallyDownloaded(model = model)) {
      status = ModelDownloadStatusType.PARTIALLY_DOWNLOADED
      val tmpFilePath =
        model.getPath(context = context, fileName = "${model.downloadFileName}.$TMP_FILE_EXT")
      val tmpFile = File(tmpFilePath)
      receivedBytes = tmpFile.length()
      totalBytes = model.totalBytes
      Log.d(TAG, "${model.name} is partially downloaded. $receivedBytes/$totalBytes")
    } else if (isModelDownloaded(model = model)) {
      status = ModelDownloadStatusType.SUCCEEDED
      Log.d(TAG, "${model.name} has been downloaded.")
    } else {
      Log.d(TAG, "${model.name} has not been downloaded.")
    }

    return ModelDownloadStatus(
      status = status,
      receivedBytes = receivedBytes,
      totalBytes = totalBytes,
    )
  }
}
