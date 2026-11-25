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

/**
 * Minimal WAV → PCM decoder for the batch ASR/TTS path.
 *
 * Handles the uncompressed PCM (format tag 1) subset of WAV:
 * 16-bit mono or stereo at any sample rate, downmixing stereo to mono and resampling to 16 kHz
 * (the strict requirement of the MediaPipe AudioTranscriber). Non-PCM formats throw with a
 * descriptive error — convert with ffmpeg before sending.
 *
 * Returns samples in `[-1f, 1f]`.
 */
object AudioWavDecoder {

  private const val WAV_FORMAT_PCM = 1

  data class WavPcm(
    val samples: FloatArray,
    val sampleRateHz: Int,
  )

  /**
   * Decodes [bytes] into 16 kHz mono Float PCM.
   *
   * @throws IllegalArgumentException if the input is not a valid 16-bit PCM WAV.
   */
  fun decode(bytes: ByteArray, targetSampleRateHz: Int = 16_000): WavPcm {
    val (fmtOffset, dataOffset, dataSize, channels, sampleRate, bitsPerSample, fmtSize) =
      parseHeader(bytes)

    if (bitsPerSample != 16) {
      throw IllegalArgumentException(
        "Only 16-bit PCM WAV is supported (got ${bitsPerSample}-bit). Convert to 16-bit PCM first."
      )
    }
    if (channels != 1 && channels != 2) {
      throw IllegalArgumentException(
        "Only mono (1) and stereo (2) WAV is supported (got $channels channels)."
      )
    }

    val data = bytes.copyOfRange(dataOffset, dataOffset + dataSize)
    val frameCount = dataSize / (2 * channels)
    val readable = frameCount * 2 * channels

    val mono = FloatArray(frameCount)
    var idx = 0
    if (channels == 1) {
      for (i in 0 until readable step 2) {
        val s = (readInt16LE(data, idx)) / 32768.0f
        mono[i / 2] = s
        idx += 2
      }
      // mono array length == frameCount
    } else {
      for (i in 0 until readable step 4) {
        val l = readInt16LE(data, idx) / 32768.0f
        val r = readInt16LE(data, idx + 2) / 32768.0f
        mono[i / 4] = (l + r) / 2.0f
        idx += 4
      }
    }

    val target = if (sampleRate == targetSampleRateHz) mono else resample(mono, sampleRate, targetSampleRateHz)
    return WavPcm(target, targetSampleRateHz)
  }

  /** Linear-interpolation resampler. Cheap and good enough for speech. */
  private fun resample(samples: FloatArray, fromRate: Int, toRate: Int): FloatArray {
    if (fromRate <= 0) return samples
    if (fromRate == toRate) return samples
    val ratio = fromRate.toDouble() / toRate.toDouble()
    val targetLength = Math.max(1, (samples.size / ratio).toInt())
    val out = FloatArray(targetLength)
    for (i in 0 until targetLength) {
      val srcPos = i * ratio
      val srcIdx = srcPos.toInt()
      val frac = (srcPos - srcIdx).toFloat()
      val a = if (srcIdx in samples.indices) samples[srcIdx] else 0f
      val b = if (srcIdx + 1 in samples.indices) samples[srcIdx + 1] else 0f
      out[i] = a + (b - a) * frac
    }
    return out
  }

  private fun parseHeader(bytes: ByteArray): ParsedHeader {
    if (bytes.size < 44) {
      throw IllegalArgumentException("WAV file too small (${bytes.size} bytes).")
    }
    if (String(bytes, 0, 4, Charsets.ISO_8859_1) != "RIFF" ||
      String(bytes, 8, 4, Charsets.ISO_8859_1) != "WAVE"
    ) {
      throw IllegalArgumentException("Not a RIFF/WAVE file.")
    }

    var pos = 12
    var fmtOffset: Int? = null
    var fmtSize: Int = 0
    var dataOffset: Int? = null
    var dataSize = 0

    while (pos + 8 <= bytes.size) {
      val id = String(bytes, pos, 4, Charsets.ISO_8859_1)
      val chunkSize = readInt32LE(bytes, pos + 4).let { if (it < 0) Int.MAX_VALUE else it }

      when (id) {
        "fmt " -> {
          if (chunkSize >= 16) {
            fmtOffset = pos + 8
            fmtSize = chunkSize
          }
        }
        "data" -> {
          dataOffset = pos + 8
          dataSize = minOf(chunkSize, bytes.size - (pos + 8))
        }
      }

      pos += 8 + chunkSize + (chunkSize and 1)
      if (chunkSize == Int.MAX_VALUE) break
    }

    val resolvedFmt = fmtOffset ?: throw IllegalArgumentException("WAV missing 'fmt ' chunk.")
    val resolvedData = dataOffset ?: throw IllegalArgumentException("WAV missing 'data' chunk.")
    if (resolvedFmt + 16 > bytes.size) {
      throw IllegalArgumentException("WAV 'fmt ' chunk truncated.")
    }

    val formatTag = readInt16LE(bytes, resolvedFmt)
    val channels = readInt16LE(bytes, resolvedFmt + 2)
    val sampleRate = readInt32LE(bytes, resolvedFmt + 4)
    val bitsPerSample = readInt16LE(bytes, resolvedFmt + 14)

    if (formatTag != WAV_FORMAT_PCM) {
      throw IllegalArgumentException(
        "Unsupported WAV format tag $formatTag. Only uncompressed PCM (tag 1) is supported."
      )
    }

    return ParsedHeader(resolvedFmt, resolvedData, dataSize, channels, sampleRate, bitsPerSample, fmtSize)
  }

  private data class ParsedHeader(
    val fmtOffset: Int,
    val dataOffset: Int,
    val dataSize: Int,
    val channels: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val fmtSize: Int,
  )

  private fun readInt16LE(bytes: ByteArray, offset: Int): Int {
    return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
  }

  private fun readInt32LE(bytes: ByteArray, offset: Int): Int {
    return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
      ((bytes[offset + 2].toInt() and 0xFF) shl 16) or ((bytes[offset + 3].toInt() and 0xFF) shl 24)
  }
}
