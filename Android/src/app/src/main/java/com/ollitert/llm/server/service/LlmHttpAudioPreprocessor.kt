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

package com.ollitert.llm.server.service

/** Audio container formats that can be identified from magic bytes. */
enum class AudioFormat {
  WAV,
  MP3,
  OGG,
  FLAC,
  UNKNOWN,
}

/**
 * Preprocesses audio data to ensure compatibility with the LiteRT LM inference engine.
 *
 * The native engine requires mono audio — "Only mono audio is supported." The HA integration
 * sends WAV which may be stereo depending on microphone setup. This preprocessor detects
 * format from magic bytes and downmixes stereo WAV to mono. Other formats (MP3/OGG/FLAC)
 * are passed through unchanged since miniaudio handles their decoding; if those arrive
 * stereo, the error from the native engine surfaces clearly.
 */
object LlmHttpAudioPreprocessor {

  // RIFF WAV format tag for uncompressed PCM — only format we can safely downmix
  private const val WAV_FORMAT_PCM: Short = 1

  /**
   * Identifies the audio container format from the first few magic bytes.
   *
   * WAV requires both RIFF at bytes 0-3 AND WAVE at bytes 8-11 (two separate checks)
   * because RIFF is also used by AVI; the WAVE marker is what distinguishes audio.
   */
  fun detectFormat(bytes: ByteArray): AudioFormat {
    if (bytes.size < 4) return AudioFormat.UNKNOWN

    // WAV: "RIFF" at 0-3 AND "WAVE" at 8-11
    if (bytes.size >= 12 &&
      bytes[0] == 'R'.code.toByte() &&
      bytes[1] == 'I'.code.toByte() &&
      bytes[2] == 'F'.code.toByte() &&
      bytes[3] == 'F'.code.toByte() &&
      bytes[8] == 'W'.code.toByte() &&
      bytes[9] == 'A'.code.toByte() &&
      bytes[10] == 'V'.code.toByte() &&
      bytes[11] == 'E'.code.toByte()
    ) {
      return AudioFormat.WAV
    }

    // OGG: "OggS" at 0-3
    if (bytes[0] == 'O'.code.toByte() &&
      bytes[1] == 'g'.code.toByte() &&
      bytes[2] == 'g'.code.toByte() &&
      bytes[3] == 'S'.code.toByte()
    ) {
      return AudioFormat.OGG
    }

    // FLAC: "fLaC" at 0-3
    if (bytes[0] == 'f'.code.toByte() &&
      bytes[1] == 'L'.code.toByte() &&
      bytes[2] == 'a'.code.toByte() &&
      bytes[3] == 'C'.code.toByte()
    ) {
      return AudioFormat.FLAC
    }

    // WebM/Matroska: EBML magic 0x1A 0x45 0xDF 0xA3 — reject explicitly
    if (bytes[0] == 0x1A.toByte() &&
      bytes[1] == 0x45.toByte() &&
      bytes[2] == 0xDF.toByte() &&
      bytes[3] == 0xA3.toByte()
    ) {
      throw IllegalArgumentException(
        "WebM/Matroska audio is not supported. Convert to WAV, MP3, OGG, or FLAC before sending."
      )
    }

    // MP3: "ID3" tag at 0-2, or sync word 0xFF followed by 0xFB/0xF3/0xF2 at bytes 0-1
    if (bytes[0] == 'I'.code.toByte() &&
      bytes[1] == 'D'.code.toByte() &&
      bytes[2] == '3'.code.toByte()
    ) {
      return AudioFormat.MP3
    }
    if (bytes[0] == 0xFF.toByte() &&
      (bytes[1] == 0xFB.toByte() || bytes[1] == 0xF3.toByte() || bytes[1] == 0xF2.toByte())
    ) {
      return AudioFormat.MP3
    }

    return AudioFormat.UNKNOWN
  }

  /**
   * Ensures audio is mono. For WAV stereo input the L/R sample pairs are averaged.
   * For all other formats the bytes are returned unchanged — miniaudio handles decoding
   * and if the native engine rejects stereo compressed audio the error will surface clearly.
   *
   * Only 16-bit PCM WAV (format tag 1) is downmixed. 8-bit, 24-bit, 32-bit, and float WAV
   * are rejected with a descriptive error since those require different sample arithmetic.
   *
   * @throws IllegalArgumentException if the WAV is malformed or uses an unsupported sub-format.
   */
  fun ensureMono(bytes: ByteArray, format: AudioFormat): ByteArray {
    if (format != AudioFormat.WAV) return bytes
    return ensureMonoWav(bytes)
  }

  // ---- WAV internals -------------------------------------------------------

  private fun ensureMonoWav(bytes: ByteArray): ByteArray {
    if (bytes.size < 12) {
      throw IllegalArgumentException("WAV file too small to be valid (${bytes.size} bytes).")
    }

    // Scan RIFF chunks from offset 12 to find "fmt " and "data"
    var fmtOffset: Int? = null
    var fmtSize: Int? = null
    var dataOffset: Int? = null
    var dataSize: Int? = null

    var pos = 12
    while (pos + 8 <= bytes.size) {
      val id = String(bytes, pos, 4, Charsets.ISO_8859_1)
      // Chunk size is a 4-byte little-endian unsigned int; clamp to Int range for safety
      val chunkSize = readInt32LE(bytes, pos + 4).let { if (it < 0) Int.MAX_VALUE else it }

      when (id) {
        "fmt " -> {
          fmtOffset = pos + 8
          fmtSize = chunkSize
        }
        "data" -> {
          dataOffset = pos + 8
          // Actual available bytes may be less than the declared chunk size if the file
          // was truncated; cap at what is actually present
          dataSize = minOf(chunkSize, bytes.size - (pos + 8))
        }
      }

      // RIFF chunks are padded to even byte boundaries
      pos += 8 + chunkSize + (chunkSize and 1)

      // Guard against corrupt size fields that would cause an infinite loop —
      // chunkSize was clamped from negative to Int.MAX_VALUE above, so any
      // resulting pos that didn't advance (or overflowed) would loop forever
      if (chunkSize == Int.MAX_VALUE) break
    }

    val resolvedFmtOffset =
      fmtOffset ?: throw IllegalArgumentException("WAV file is missing the required 'fmt ' chunk.")
    val resolvedFmtSize =
      fmtSize ?: throw IllegalArgumentException("WAV file is missing the required 'fmt ' chunk.")
    val resolvedDataOffset =
      dataOffset
        ?: throw IllegalArgumentException("WAV file is missing the required 'data' chunk.")
    val resolvedDataSize = dataSize ?: 0

    // fmt chunk must be at least 16 bytes (standard PCM header)
    if (resolvedFmtSize < 16 || resolvedFmtOffset + 16 > bytes.size) {
      throw IllegalArgumentException("WAV 'fmt ' chunk is too small or truncated.")
    }

    val formatTag = readInt16LE(bytes, resolvedFmtOffset)
    val channels = readInt16LE(bytes, resolvedFmtOffset + 2)
    val sampleRate = readInt32LE(bytes, resolvedFmtOffset + 4)
    val bitsPerSample = readInt16LE(bytes, resolvedFmtOffset + 14)
    val blockAlign = readInt16LE(bytes, resolvedFmtOffset + 12)

    // Only process uncompressed PCM — other format tags (ADPCM, IEEE float, etc.)
    // would require format-specific arithmetic to downmix
    if (formatTag != WAV_FORMAT_PCM.toInt()) {
      throw IllegalArgumentException(
        "Unsupported WAV format tag $formatTag. Only uncompressed PCM (format tag 1) is supported."
      )
    }

    // Already mono — return as-is without copying
    if (channels == 1) return bytes

    if (channels != 2) {
      throw IllegalArgumentException(
        "WAV has $channels channels. Only mono (1) and stereo (2) are supported."
      )
    }

    // Only 16-bit PCM downmix is implemented — other depths need different arithmetic
    if (bitsPerSample != 16) {
      throw IllegalArgumentException(
        "Stereo WAV downmix requires 16-bit PCM. Got ${bitsPerSample}-bit. " +
          "Convert to 16-bit mono before sending."
      )
    }

    return downmixStereoTo16BitMono(
      bytes,
      resolvedFmtOffset,
      resolvedDataOffset,
      resolvedDataSize,
      sampleRate,
      blockAlign,
    )
  }

  /**
   * Downmixes 16-bit stereo PCM WAV to mono by averaging L/R sample pairs.
   *
   * Reconstruction approach: copy the original bytes verbatim, then patch:
   *  - fmt chunk: channels=1, blockAlign/=2, byteRate/=2
   *  - data chunk: write interleaved L/R average into first half, update chunk size
   *  - RIFF header: update total file size
   * This preserves any extra chunks (LIST, fact, bext, etc.) untouched.
   */
  private fun downmixStereoTo16BitMono(
    bytes: ByteArray,
    fmtOffset: Int,
    dataOffset: Int,
    dataSize: Int,
    sampleRate: Int,
    stereoBlockAlign: Int,
  ): ByteArray {
    val samplePairs = dataSize / 4 // each stereo frame = 2 × 16-bit = 4 bytes
    val monoDataSize = samplePairs * 2 // each mono frame = 1 × 16-bit = 2 bytes

    // Output is a copy of the original with the data section shrunk by half
    val shrinkBy = dataSize - monoDataSize
    val output = ByteArray(bytes.size - shrinkBy)

    // Copy everything up to and including the data chunk header (8 bytes before dataOffset)
    System.arraycopy(bytes, 0, output, 0, dataOffset)

    // Write mono samples into the data section
    for (i in 0 until samplePairs) {
      val srcPos = dataOffset + i * 4
      // Samples are signed little-endian 16-bit
      val left = readInt16LE(bytes, srcPos)
      val right = readInt16LE(bytes, srcPos + 2)
      // Average: integer arithmetic avoids floating-point rounding accumulation
      val mono = (left + right) / 2
      writeInt16LE(output, dataOffset + i * 2, mono)
    }

    // Copy any bytes that follow the data chunk (there usually aren't any, but be safe)
    val srcAfterData = dataOffset + dataSize
    val dstAfterData = dataOffset + monoDataSize
    if (srcAfterData < bytes.size) {
      System.arraycopy(bytes, srcAfterData, output, dstAfterData, bytes.size - srcAfterData)
    }

    // Patch fmt chunk: channels=1, blockAlign=stereoBlockAlign/2, byteRate=sampleRate*blockAlign/2
    writeInt16LE(output, fmtOffset + 2, 1) // channels
    val monoBlockAlign = stereoBlockAlign / 2
    writeInt16LE(output, fmtOffset + 12, monoBlockAlign) // blockAlign
    writeInt32LE(output, fmtOffset + 8, sampleRate * monoBlockAlign) // byteRate

    // Patch data chunk size (4 bytes LE immediately before dataOffset)
    writeInt32LE(output, dataOffset - 4, monoDataSize)

    // Patch RIFF chunk size (bytes 4-7 of the file = total file size - 8)
    writeInt32LE(output, 4, output.size - 8)

    return output
  }

  // ---- Little-endian byte helpers ------------------------------------------

  /** Reads a 16-bit signed little-endian integer. Returns 0 if out of bounds. */
  private fun readInt16LE(bytes: ByteArray, offset: Int): Int {
    if (offset + 1 >= bytes.size) return 0
    return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
  }

  /** Reads a 32-bit signed little-endian integer. Returns 0 if out of bounds. */
  private fun readInt32LE(bytes: ByteArray, offset: Int): Int {
    if (offset + 3 >= bytes.size) return 0
    return (bytes[offset].toInt() and 0xFF) or
      ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
      ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
      ((bytes[offset + 3].toInt() and 0xFF) shl 24)
  }

  /** Writes a 16-bit signed value as little-endian into [bytes] at [offset]. */
  private fun writeInt16LE(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = (value and 0xFF).toByte()
    bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
  }

  /** Writes a 32-bit signed value as little-endian into [bytes] at [offset]. */
  private fun writeInt32LE(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = (value and 0xFF).toByte()
    bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
    bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
    bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
  }
}
