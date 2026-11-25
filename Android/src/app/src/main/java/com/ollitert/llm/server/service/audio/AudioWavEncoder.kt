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

package com.ollitert.llm.server.service.audio

import java.io.ByteArrayOutputStream

/**
 * Encodes mono Float PCM as a 16-bit PCM WAV byte array suitable for the server response.
 * Matches the decoder's expectations exactly (RIFF/WAVE, fmt + data chunks, 16-bit PCM).
 */
object AudioWavEncoder {

  fun encode(samples: FloatArray, sampleRateHz: Int): ByteArray {
    val dataSize = samples.size * 2
    val out = ByteArrayOutputStream(44 + dataSize)

    fun w8(v: Int) = out.write(v and 0xFF)
    fun w16(v: Int) { w8(v); w8(v shr 8) }
    fun w32(v: Int) { w8(v); w8(v shr 8); w8(v shr 16); w8(v shr 24) }
    fun wString(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))

    wString("RIFF")
    w32(36 + dataSize)
    wString("WAVE")
    wString("fmt ")
    w32(16)
    w16(1) // PCM
    w16(1) // mono
    w32(sampleRateHz)
    w32(sampleRateHz * 2) // byte rate
    w16(2) // block align
    w16(16) // bits per sample
    wString("data")
    w32(dataSize)

    for (sample in samples) {
      val clamped = sample.coerceIn(-1f, 1f)
      val value = (clamped * 32767.0f).toInt()
      w16(value)
    }
    return out.toByteArray()
  }
}
