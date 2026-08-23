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

package com.ollitert.llm.server.runtime

import android.os.Build
import android.util.Log
import com.ollitert.llm.server.data.EventCategory
import com.ollitert.llm.server.data.LogLevel
import com.ollitert.llm.server.data.RequestLogStore
import java.io.File

private const val TAG = "OlliteRT.GpuAvailability"

object GpuAvailability {
  // The SDK's sampler uses dlopen("libOpenCL.so") without a full path. On some
  // devices (Pixel 5), the library exists only in /vendor/lib64/ which is not
  // accessible from the app's linker namespace even though File.exists() returns
  // true (because /system/vendor/ is a symlink to /vendor/).
  val isOpenClAccessible: Boolean by lazy {
    Log.i(TAG, "OpenCL probe: device=${Build.DEVICE} model=${Build.MODEL} " +
      "SOC=${Build.SOC_MODEL} SDK=${Build.VERSION.SDK_INT}")

    val probeResults = StringBuilder()

    // Step 1: Check if System.loadLibrary can find it (app linker namespace).
    // This is the most reliable signal — if the app's own classloader can't
    // load it, the SDK's native dlopen definitely can't either.
    val javaLoadSuccess = try {
      System.loadLibrary("OpenCL")
      probeResults.append("System.loadLibrary=OK; ")
      true
    } catch (e: UnsatisfiedLinkError) {
      probeResults.append("System.loadLibrary=FAIL(${e.message}); ")
      false
    }

    // Step 2: Check where the library physically exists (diagnostic only).
    // /system/vendor/ is a symlink to /vendor/ on most devices — both are
    // blocked by linker namespace restrictions for app processes.
    val searchPaths = listOf(
      "/system/lib64/libOpenCL.so",
      "/system/lib/libOpenCL.so",
      "/system/vendor/lib64/libOpenCL.so",
      "/system/vendor/lib/libOpenCL.so",
      "/vendor/lib64/libOpenCL.so",
      "/vendor/lib/libOpenCL.so",
    )
    val foundPaths = searchPaths.filter { File(it).exists() }
    probeResults.append("paths_found=$foundPaths")

    // System.loadLibrary is authoritative: if it fails, OpenCL is not usable
    // regardless of which paths show the file. The file may physically exist
    // but the linker namespace prevents loading it.
    val accessible = javaLoadSuccess

    Log.i(TAG, "OpenCL probe result: accessible=$accessible — $probeResults")
    // Only surface in user-facing Logs tab when something is wrong (probe failed).
    if (!accessible) {
      RequestLogStore.addEvent(
        "OpenCL probe: accessible=$accessible, " +
          "javaLoad=${if (javaLoadSuccess) "OK" else "FAIL"}, " +
          "found=${foundPaths.map { it.removePrefix("/system").removePrefix("/") }}",
        level = LogLevel.WARNING,
        category = EventCategory.MODEL,
      )
    }

    accessible
  }
}
