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

package com.ollitert.llm.server.worker

import com.ollitert.llm.server.data.storage.modelUpdateStagingDirectory
import java.io.File

private const val PUBLISHED_UPDATE_MARKER = ".published-update"

/** Holds the replacement apart from installed models until all its files are ready. */
internal class ModelUpdateFiles(root: File, version: String, private val inPlace: Boolean = false) {
  val stagingDirectory = File(root, modelUpdateStagingDirectory(version))
  private val finalDirectory = if (version.isEmpty()) root else File(root, version)

  fun isPublished(fileName: String, extractedDirectory: String?, extraFileNames: List<String>): Boolean {
    val mainArtifactExists = if (extractedDirectory == null) File(finalDirectory, fileName).isFile
      else File(finalDirectory, extractedDirectory).isDirectory
    return File(finalDirectory, PUBLISHED_UPDATE_MARKER).isFile && mainArtifactExists &&
      extraFileNames.all { File(finalDirectory, it).isFile }
  }

  fun publish(mainArtifactName: String, extraFileNames: List<String>) {
    if (inPlace) {
      // A pinned revision can contain multiple model variants. Keep the installed files
      // and publish the new main artifact last, after every required companion is present.
      for (name in extraFileNames) {
        val destination = File(finalDirectory, name)
        if (!destination.isFile) publishArtifact(name)
      }
      File(finalDirectory, PUBLISHED_UPDATE_MARKER).createNewFile()
      publishArtifact(mainArtifactName)
      stagingDirectory.deleteRecursively()
      return
    }
    // If WorkManager restarts after the rename, the completed artifact needs no transfer.
    File(stagingDirectory, PUBLISHED_UPDATE_MARKER).createNewFile()
    finalizeDownloadedDirectory(stagingDirectory, finalDirectory)
  }

  private fun publishArtifact(name: String) {
    val destination = File(finalDirectory, name)
    destination.parentFile?.mkdirs()
    performAtomicMove(File(stagingDirectory, name), destination,
      failureMessage = "Could not publish the staged model file")
  }
}
