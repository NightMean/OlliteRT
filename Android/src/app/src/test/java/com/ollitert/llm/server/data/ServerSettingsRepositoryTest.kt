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

package com.ollitert.llm.server.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSettingsRepositoryTest {

  @Test
  fun `ServerSettings defaults to expected values`() {
    val settings = ServerSettings()
    assertEquals(DEFAULT_PORT, settings.hostPort)
    assertFalse(settings.autoTruncateHistory)
    assertFalse(settings.autoTrimPrompts)
    assertTrue(settings.streamLogsPreview)
    assertTrue(settings.compactImageData)
    assertTrue(settings.sttTranscriptionPromptEnabled)
  }

  @Test
  fun `ServerSettings converts to RequestPrefsSnapshot accurately`() {
    val settings = ServerSettings(
      autoTruncateHistory = true,
      autoTrimPrompts = true,
      ignoreClientSamplerParams = true,
      eagerVisionInit = true,
      streamLogsPreview = false,
      keepPartialResponse = true,
      compactImageData = false,
      resolveClientHostnames = true,
      hideHealthLogs = true,
      verboseDebug = true,
      rejectWhenBusy = true,
      sttTranscriptionPromptEnabled = false,
      sttTranscriptionPromptText = "test prompt",
      schemaInjectionToolCalling = false,
    )

    val snapshot = settings.toRequestPrefsSnapshot()

    assertTrue(snapshot.autoTruncateHistory)
    assertTrue(snapshot.autoTrimPrompts)
    assertTrue(snapshot.ignoreClientSamplerParams)
    assertTrue(snapshot.eagerVisionInit)
    assertFalse(snapshot.streamLogsPreview)
    assertTrue(snapshot.keepPartialResponse)
    assertFalse(snapshot.compactImageData)
    assertTrue(snapshot.resolveClientHostnames)
    assertTrue(snapshot.hideHealthLogs)
    assertTrue(snapshot.verboseDebug)
    assertTrue(snapshot.rejectWhenBusy)
    assertFalse(snapshot.sttTranscriptionPromptEnabled)
    assertEquals("test prompt", snapshot.sttTranscriptionPromptText)
    assertFalse(snapshot.schemaInjectionToolCalling)
  }
}
