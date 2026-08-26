/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.ollitert.llm.server.worker

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val REPLACEMENT_BACKUP_SUFFIX = ".replacement-backup"

internal class DownloadFinalizationException(
  message: String,
  cause: Throwable? = null,
) : IOException(message, cause)

private fun moveAtomically(source: Path, destination: Path) {
  Files.move(
    source,
    destination,
    StandardCopyOption.ATOMIC_MOVE,
    StandardCopyOption.REPLACE_EXISTING,
  )
}

private fun performAtomicMove(
  source: File,
  destination: File,
  atomicMove: (source: Path, destination: Path) -> Unit,
  failureMessage: String,
) {
  try {
    atomicMove(source.toPath(), destination.toPath())
  } catch (failure: IOException) {
    throw DownloadFinalizationException(failureMessage, failure)
  } catch (failure: SecurityException) {
    throw DownloadFinalizationException(failureMessage, failure)
  }
}

/** Replaces a final artifact without first deleting the last known-good file. */
internal fun finalizeDownloadedFile(
  stagingFile: File,
  finalFile: File,
  atomicMove: (source: Path, destination: Path) -> Unit = ::moveAtomically,
) {
  try {
    if (!stagingFile.isFile) {
      throw DownloadFinalizationException("Downloaded staging file is missing: ${stagingFile.name}")
    }
    if (stagingFile.canonicalFile.parentFile != finalFile.canonicalFile.parentFile) {
      throw DownloadFinalizationException("Staging and final files must share a directory")
    }
    performAtomicMove(
      stagingFile,
      finalFile,
      atomicMove,
      "Failed to atomically finalize downloaded file: ${finalFile.name}",
    )
  } catch (failure: DownloadFinalizationException) {
    throw failure
  } catch (failure: IOException) {
    throw DownloadFinalizationException(
      "Failed to atomically finalize downloaded file: ${finalFile.name}",
      failure,
    )
  } catch (failure: SecurityException) {
    throw DownloadFinalizationException(
      "Storage access denied while finalizing downloaded file: ${finalFile.name}",
      failure,
    )
  }
}

/**
 * Replaces an extracted directory while retaining a recoverable sibling backup.
 * A prior backup is restored first after process death interrupted an earlier swap.
 */
internal fun finalizeExtractedDirectory(
  stagingDirectory: File,
  finalDirectory: File,
  atomicMove: (source: Path, destination: Path) -> Unit = ::moveAtomically,
) {
  try {
    if (!stagingDirectory.isDirectory) {
      throw DownloadFinalizationException("Extracted staging directory is missing")
    }
    if (stagingDirectory.canonicalFile.parentFile != finalDirectory.canonicalFile.parentFile) {
      throw DownloadFinalizationException("Staging and final directories must share a parent")
    }

    val backupDirectory = File(finalDirectory.absolutePath + REPLACEMENT_BACKUP_SUFFIX)
    if (backupDirectory.exists()) {
      if (finalDirectory.exists()) {
        if (!backupDirectory.deleteRecursively()) {
          throw DownloadFinalizationException("Could not clear stale extraction backup")
        }
      } else {
        performAtomicMove(
          backupDirectory,
          finalDirectory,
          atomicMove,
          "Could not restore the previous extracted model",
        )
      }
    }

    if (finalDirectory.exists()) {
      performAtomicMove(
        finalDirectory,
        backupDirectory,
        atomicMove,
        "Could not preserve the previous extracted model",
      )
    }

    try {
      performAtomicMove(
        stagingDirectory,
        finalDirectory,
        atomicMove,
        "Could not finalize the extracted model directory",
      )
    } catch (replacementFailure: DownloadFinalizationException) {
      if (backupDirectory.exists() && !finalDirectory.exists()) {
        try {
          performAtomicMove(
            backupDirectory,
            finalDirectory,
            atomicMove,
            "Could not restore the previous extracted model after replacement failed",
          )
        } catch (restoreFailure: DownloadFinalizationException) {
          replacementFailure.addSuppressed(restoreFailure)
        }
      }
      throw replacementFailure
    }

    // The new directory is authoritative now. A failed backup cleanup is safe;
    // the next replacement removes this sibling before modifying the final path.
    if (backupDirectory.exists()) backupDirectory.deleteRecursively()
  } catch (failure: DownloadFinalizationException) {
    throw failure
  } catch (failure: IOException) {
    throw DownloadFinalizationException("Failed to finalize extracted model directory", failure)
  } catch (failure: SecurityException) {
    throw DownloadFinalizationException("Storage access denied while finalizing extraction", failure)
  }
}
