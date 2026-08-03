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

package com.ollitert.llm.server

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidManifestFloatingMonitorTest {
  @Test
  fun `manifest declares overlay permission exactly once`() {
    val manifest = File("src/main/AndroidManifest.xml")
    assertTrue("AndroidManifest.xml must exist from the app module test working directory", manifest.isFile)

    val declaration = "android.permission.SYSTEM_ALERT_WINDOW"
    assertEquals(1, Regex(Regex.escape(declaration)).findAll(manifest.readText()).count())
  }
}
