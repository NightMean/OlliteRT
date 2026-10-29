/*
 * Copyright 2026 @NightMean (https://github.com/NightMean)
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

package com.ollitert.llm.server

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ollitert.llm.server.common.SOC
import com.ollitert.llm.server.runtime.GpuAvailability
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceCompatibilityTest {

  @Test
  fun packagedManifestAllowsAndroid11() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals(Build.VERSION_CODES.R, context.applicationInfo.minSdkVersion)
  }

  @Test
  fun socDetectionUsesPlatformMetadataOnlyWhenAvailable() {
    val expected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Build.SOC_MODEL.lowercase()
    } else {
      Build.UNKNOWN
    }

    assertEquals(expected, SOC)
    assertEquals(SOC, com.ollitert.llm.server.data.prefs.SOC)
  }

  @Test
  fun openClProbeDoesNotRequireAndroid12DeviceFields() {
    // Probes driver accessibility, not LiteRT inference (unsupported on x86_64 emulators).
    val accessible = GpuAvailability.isOpenClAccessible
    assertEquals(accessible, GpuAvailability.isOpenClAccessible)
  }
}
