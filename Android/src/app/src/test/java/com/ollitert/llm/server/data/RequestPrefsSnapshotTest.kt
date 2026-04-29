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

class RequestPrefsSnapshotTest {

  @Test
  fun defaultSnapshotMatchesServerPrefsDefaults() {
    val snapshot = RequestPrefsSnapshot()
    assertFalse(snapshot.autoTruncateHistory)
    assertFalse(snapshot.autoTrimPrompts)
    assertFalse(snapshot.ignoreClientSamplerParams)
    assertFalse(snapshot.eagerVisionInit)
    assertTrue(snapshot.streamLogsPreview)
    assertFalse(snapshot.keepPartialResponse)
    assertTrue(snapshot.compactImageData)
    assertFalse(snapshot.resolveClientHostnames)
    assertFalse(snapshot.hideHealthLogs)
    assertFalse(snapshot.verboseDebug)
    assertTrue(snapshot.sttTranscriptionPromptEnabled)
    assertEquals("", snapshot.sttTranscriptionPromptText)
  }

  @Test
  fun copyPreservesValues() {
    val snapshot = RequestPrefsSnapshot(
      autoTruncateHistory = true,
      verboseDebug = true,
      sttTranscriptionPromptText = "transcribe",
    )
    val copy = snapshot.copy(autoTrimPrompts = true)
    assertEquals(true, copy.autoTruncateHistory)
    assertEquals(true, copy.autoTrimPrompts)
    assertEquals(true, copy.verboseDebug)
    assertEquals("transcribe", copy.sttTranscriptionPromptText)
  }
}
