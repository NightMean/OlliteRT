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

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.ollitert.llm.server.data.prefs.IMAGE_PLACEHOLDER

object MultimodalContentBuilder {

  /**
   * Constructs a [Contents] object for LiteRT-LM inference from prompt text, images, and audio clips.
   * Handles multi-image interleaving using [IMAGE_PLACEHOLDER] when present.
   */
  fun buildContents(
    input: String,
    images: List<ByteArray> = emptyList(),
    audioClips: List<ByteArray> = emptyList(),
  ): Contents {
    val contents = mutableListOf<Content>()
    if (images.isNotEmpty() && input.contains(IMAGE_PLACEHOLDER)) {
      // Multi-image interleaving: split on placeholders and interleave Content.Text / Content.ImageBytes
      val segments = input.split(IMAGE_PLACEHOLDER)
      var imageIndex = 0
      for ((i, segment) in segments.withIndex()) {
        if (segment.trim().isNotEmpty()) {
          contents.add(Content.Text(segment.trim()))
        }
        // After each segment except the last, insert the corresponding image
        if (i < segments.size - 1 && imageIndex < images.size) {
          contents.add(Content.ImageBytes(images[imageIndex]))
          imageIndex++
        }
      }
      // Append any remaining images that had no placeholder (safe fallback)
      while (imageIndex < images.size) {
        contents.add(Content.ImageBytes(images[imageIndex]))
        imageIndex++
      }
    } else {
      // Single-image or non-chat path: images before text (LiteRT expects image content first)
      for (image in images) {
        contents.add(Content.ImageBytes(image))
      }
      if (input.trim().isNotEmpty()) {
        contents.add(Content.Text(input))
      }
    }
    for (audioClip in audioClips) {
      contents.add(Content.AudioBytes(audioClip))
    }
    return Contents.of(contents)
  }
}
