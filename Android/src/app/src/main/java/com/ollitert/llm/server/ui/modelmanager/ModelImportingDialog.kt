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

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.humanReadableSize
import com.ollitert.llm.server.data.model.IMPORTS_DIR
import com.ollitert.llm.server.proto.ImportedModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private const val TAG = "OlliteRT.Import"

@Composable
fun ModelImportingDialog(
  uri: Uri,
  info: ImportedModel,
  onDismiss: () -> Unit,
  onDone: (ImportedModel) -> Unit,
) {
  var error by remember { mutableStateOf("") }
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var progress by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(Unit) {
    importModel(
      context = context,
      coroutineScope = coroutineScope,
      fileName = info.fileName,
      fileSize = info.fileSize,
      uri = uri,
      onDone = { onDone(info) },
      onProgress = { progress = it },
      onError = { error = it },
    )
  }

  Dialog(
    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    onDismissRequest = onDismiss,
  ) {
    Card(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          stringResource(R.string.dialog_import_model_title),
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(bottom = 8.dp),
        )

        if (error.isEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              "${info.fileName} (${info.fileSize.humanReadableSize()})",
              style = MaterialTheme.typography.labelSmall,
            )
            val animatedProgress = remember { Animatable(0f) }
            LinearProgressIndicator(
              progress = { animatedProgress.value },
              modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            LaunchedEffect(progress) {
              animatedProgress.animateTo(progress, animationSpec = tween(150))
            }
          }
        } else {
          Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              Icons.Rounded.Error,
              contentDescription = stringResource(R.string.cd_error),
              tint = MaterialTheme.colorScheme.error,
            )
            Text(
              error,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(top = 4.dp),
            )
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = { onDismiss() }) { Text(stringResource(R.string.close)) }
          }
        }
      }
    }
  }
}

internal fun importModel(
  context: Context,
  coroutineScope: CoroutineScope,
  fileName: String,
  fileSize: Long,
  uri: Uri,
  onDone: () -> Unit,
  onProgress: (Float) -> Unit,
  onError: (String) -> Unit,
) {
  coroutineScope.launch(Dispatchers.IO) {
    val decodedUri = URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8.name())
    Log.d(TAG, "importing model from $decodedUri. File name: $fileName. File size: $fileSize")

    val externalDir = context.getExternalFilesDir(null)
      ?: throw IOException("External storage unavailable — cannot import model")
    val importsDir = File(externalDir, IMPORTS_DIR)
    if (!importsDir.exists()) {
      importsDir.mkdirs()
    }

    val finalFile = File(externalDir, "$IMPORTS_DIR/$fileName")
    val tmpFile = File(externalDir, "$IMPORTS_DIR/${fileName}.tmp")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var bytesRead: Int
    var lastSetProgressTs: Long = 0
    var importedBytes = 0L
    try {
      val inputStream = context.contentResolver.openInputStream(uri)
      if (inputStream == null) {
        if (!tmpFile.delete()) Log.w(TAG, "Failed to delete temp file: ${tmpFile.name}")
        onError(context.getString(R.string.error_import_failed))
        return@launch
      }
      inputStream.use { input ->
        FileOutputStream(tmpFile).use { outputStream ->
          while (input.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            importedBytes += bytesRead

            val curTs = System.currentTimeMillis()
            if (curTs - lastSetProgressTs > 200) {
              Log.d(TAG, "importing progress: $importedBytes, $fileSize")
              lastSetProgressTs = curTs
              if (fileSize != 0L) {
                onProgress(importedBytes.toFloat() / fileSize.toFloat())
              }
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Import failed during file copy", e)
      if (!tmpFile.delete()) Log.w(TAG, "Failed to delete temp file: ${tmpFile.name}")
      onError(e.message ?: context.getString(R.string.error_import_failed))
      return@launch
    }

    if (finalFile.exists()) {
      if (!finalFile.delete()) Log.w(TAG, "Failed to delete existing file before rename: ${finalFile.name}")
    }
    if (!tmpFile.renameTo(finalFile)) {
      try {
        tmpFile.copyTo(finalFile, overwrite = true)
        if (!tmpFile.delete()) Log.w(TAG, "Failed to delete temp file after copy: ${tmpFile.name}")
      } catch (e: Exception) {
        if (!tmpFile.delete()) Log.w(TAG, "Failed to delete temp file: ${tmpFile.name}")
        onError(context.getString(R.string.error_import_finalize_failed, e.message ?: ""))
        return@launch
      }
    }
    Log.d(TAG, "import done")
    onProgress(1f)
    onDone()
  }
}

internal fun getFileSizeAndDisplayNameFromUri(context: Context, uri: Uri): Pair<Long, String> {
  val contentResolver = context.contentResolver
  var fileSize = 0L
  var displayName = ""

  try {
    contentResolver
      .query(uri, arrayOf(OpenableColumns.SIZE, OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { cursor ->
        if (cursor.moveToFirst()) {
          val sizeIndex = cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)
          fileSize = cursor.getLong(sizeIndex)

          val nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
          displayName = cursor.getString(nameIndex)
        }
      }
  } catch (e: Exception) {
    Log.e(TAG, "Failed to query file size/name from URI", e)
    return Pair(0L, "")
  }

  return Pair(fileSize, displayName)
}
