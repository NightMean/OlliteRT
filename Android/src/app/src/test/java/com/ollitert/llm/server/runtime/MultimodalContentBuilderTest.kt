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

package com.ollitert.llm.server.runtime

import com.ollitert.llm.server.data.prefs.IMAGE_PLACEHOLDER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MultimodalContentBuilderTest {

  @Test
  fun buildContentsTextOnly() {
    val contents = MultimodalContentBuilder.buildContents(
      input = "Hello world",
    )
    assertNotNull(contents)
  }

  @Test
  fun buildContentsWithInterleavedImages() {
    val img1 = byteArrayOf(1, 2, 3)
    val img2 = byteArrayOf(4, 5, 6)
    val prompt = "Look at this $IMAGE_PLACEHOLDER and then this $IMAGE_PLACEHOLDER"
    val contents = MultimodalContentBuilder.buildContents(
      input = prompt,
      images = listOf(img1, img2),
    )
    assertNotNull(contents)
  }

  @Test
  fun buildContentsWithAudio() {
    val audio = byteArrayOf(10, 20, 30)
    val contents = MultimodalContentBuilder.buildContents(
      input = "Transcribe",
      audioClips = listOf(audio),
    )
    assertNotNull(contents)
  }
}
