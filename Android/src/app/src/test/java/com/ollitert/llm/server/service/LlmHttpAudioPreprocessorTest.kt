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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LlmHttpAudioPreprocessorTest {

  // ---- detectFormat --------------------------------------------------------

  @Test
  fun detectsWav() {
    val bytes = buildWavMono16bit(sampleRate = 44100, samples = shortArrayOf(0))
    assertEquals(AudioFormat.WAV, LlmHttpAudioPreprocessor.detectFormat(bytes))
  }

  @Test
  fun detectsMp3Id3() {
    val bytes = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 0)
    assertEquals(AudioFormat.MP3, LlmHttpAudioPreprocessor.detectFormat(bytes))
  }

  @Test
  fun detectsMp3SyncWord() {
    val bytes = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0, 0)
    assertEquals(AudioFormat.MP3, LlmHttpAudioPreprocessor.detectFormat(bytes))
  }

  @Test
  fun detectsMp3SyncWordF3() {
    val bytes = byteArrayOf(0xFF.toByte(), 0xF3.toByte(), 0, 0)
    assertEquals(AudioFormat.MP3, LlmHttpAudioPreprocessor.detectFormat(bytes))
  }

  @Test
  fun detectsMp3SyncWordF2() {
    val bytes = byteArrayOf(0xFF.toByte(), 0xF2.toByte(), 0, 0)
    assertEquals(AudioFormat.MP3, LlmHttpAudioPreprocessor.detectFormat(bytes))
  }

  @Test
  fun detectsOgg() {
    val bytes = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte())
    assertEquals(AudioFormat.OGG, LlmHttpAudioPreprocessor.detectFormat(bytes))
  }

  @Test
  fun detectsFlac() {
    val bytes = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte())
    assertEquals(AudioFormat.FLAC, LlmHttpAudioPreprocessor.detectFormat(bytes))
  }

  @Test
  fun unknownForRandomBytes() {
    val bytes = byteArrayOf(0x00, 0x11, 0x22, 0x33)
    assertEquals(AudioFormat.UNKNOWN, LlmHttpAudioPreprocessor.detectFormat(bytes))
  }

  @Test
  fun unknownForEmptyArray() {
    assertEquals(AudioFormat.UNKNOWN, LlmHttpAudioPreprocessor.detectFormat(byteArrayOf()))
  }

  @Test
  fun unknownForTooShort() {
    assertEquals(AudioFormat.UNKNOWN, LlmHttpAudioPreprocessor.detectFormat(byteArrayOf(0x52)))
  }

  @Test(expected = IllegalArgumentException::class)
  fun webmThrowsDescriptiveError() {
    val bytes = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte())
    LlmHttpAudioPreprocessor.detectFormat(bytes)
  }

  @Test
  fun webmErrorMentionsWebM() {
    val bytes = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte())
    val ex = runCatching { LlmHttpAudioPreprocessor.detectFormat(bytes) }.exceptionOrNull()
    assertEquals(true, ex?.message?.contains("WebM", ignoreCase = true))
  }

  @Test
  fun riffWithoutWaveIsNotWav() {
    // "RIFF" at 0-3, but "AVI " at 8-11 — should not detect as WAV
    val bytes = ByteArray(12)
    bytes[0] = 'R'.code.toByte(); bytes[1] = 'I'.code.toByte()
    bytes[2] = 'F'.code.toByte(); bytes[3] = 'F'.code.toByte()
    bytes[8] = 'A'.code.toByte(); bytes[9] = 'V'.code.toByte()
    bytes[10] = 'I'.code.toByte(); bytes[11] = ' '.code.toByte()
    assertEquals(AudioFormat.UNKNOWN, LlmHttpAudioPreprocessor.detectFormat(bytes))
  }

  // ---- ensureMono — passthrough cases --------------------------------------

  @Test
  fun mp3PassthroughReturnsSameArray() {
    val bytes = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 0)
    val result = LlmHttpAudioPreprocessor.ensureMono(bytes, AudioFormat.MP3)
    assertSame(bytes, result)
  }

  @Test
  fun oggPassthroughReturnsSameArray() {
    val bytes = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte())
    val result = LlmHttpAudioPreprocessor.ensureMono(bytes, AudioFormat.OGG)
    assertSame(bytes, result)
  }

  @Test
  fun flacPassthroughReturnsSameArray() {
    val bytes = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte())
    val result = LlmHttpAudioPreprocessor.ensureMono(bytes, AudioFormat.FLAC)
    assertSame(bytes, result)
  }

  @Test
  fun unknownPassthroughReturnsSameArray() {
    val bytes = byteArrayOf(0x00, 0x01, 0x02)
    val result = LlmHttpAudioPreprocessor.ensureMono(bytes, AudioFormat.UNKNOWN)
    assertSame(bytes, result)
  }

  // ---- ensureMono — WAV mono pass-through ----------------------------------

  @Test
  fun wavMonoReturnedUnchanged() {
    val original = buildWavMono16bit(sampleRate = 16000, samples = shortArrayOf(100, 200, -100))
    val result = LlmHttpAudioPreprocessor.ensureMono(original, AudioFormat.WAV)
    // For mono WAV the identical array reference should come back
    assertSame(original, result)
  }

  // ---- ensureMono — WAV stereo downmix -------------------------------------

  @Test
  fun stereoDownmixProducesMonoChannelCount() {
    val wav = buildWavStereo16bit(sampleRate = 44100, leftSamples = shortArrayOf(100), rightSamples = shortArrayOf(200))
    val result = LlmHttpAudioPreprocessor.ensureMono(wav, AudioFormat.WAV)
    // Channel count is at fmt_offset + 2; fmt is at offset 20 (after 12-byte RIFF header + 8-byte chunk header)
    val fmtDataOffset = 20
    val channels = (result[fmtDataOffset + 2].toInt() and 0xFF) or ((result[fmtDataOffset + 3].toInt() and 0xFF) shl 8)
    assertEquals(1, channels)
  }

  @Test
  fun stereoDownmixAveragesSamples() {
    // L=100, R=200 → average = 150
    val wav = buildWavStereo16bit(sampleRate = 44100, leftSamples = shortArrayOf(100), rightSamples = shortArrayOf(200))
    val result = LlmHttpAudioPreprocessor.ensureMono(wav, AudioFormat.WAV)
    val fmtDataOffset = 20
    val fmtSize = 16
    val dataChunkStart = fmtDataOffset + fmtSize + 8 // skip fmt data + data chunk header
    val monoSample = (result[dataChunkStart].toInt() and 0xFF) or ((result[dataChunkStart + 1].toInt() and 0xFF) shl 8)
    // Sign-extend from 16-bit
    val monoSampleSigned = if (monoSample >= 0x8000) monoSample - 0x10000 else monoSample
    assertEquals(150, monoSampleSigned)
  }

  @Test
  fun stereoDownmixHandlesNegativeSamples() {
    // L=-100, R=-200 → average = -150
    val wav = buildWavStereo16bit(sampleRate = 44100, leftSamples = shortArrayOf(-100), rightSamples = shortArrayOf(-200))
    val result = LlmHttpAudioPreprocessor.ensureMono(wav, AudioFormat.WAV)
    val fmtDataOffset = 20
    val fmtSize = 16
    val dataChunkStart = fmtDataOffset + fmtSize + 8
    val raw = (result[dataChunkStart].toInt() and 0xFF) or ((result[dataChunkStart + 1].toInt() and 0xFF) shl 8)
    val signed = if (raw >= 0x8000) raw - 0x10000 else raw
    assertEquals(-150, signed)
  }

  @Test
  fun stereoDownmixOutputSizeIsSmaller() {
    val nSamples = 100
    val leftSamples = ShortArray(nSamples) { it.toShort() }
    val rightSamples = ShortArray(nSamples) { (it * 2).toShort() }
    val wav = buildWavStereo16bit(sampleRate = 44100, leftSamples = leftSamples, rightSamples = rightSamples)
    val result = LlmHttpAudioPreprocessor.ensureMono(wav, AudioFormat.WAV)
    // Mono output data is half the stereo data; total file should be smaller
    assertEquals(true, result.size < wav.size)
  }

  @Test
  fun stereoDownmixOutputIsValidWav() {
    val wav = buildWavStereo16bit(sampleRate = 16000, leftSamples = shortArrayOf(0, 1000, -1000), rightSamples = shortArrayOf(0, 2000, -2000))
    val result = LlmHttpAudioPreprocessor.ensureMono(wav, AudioFormat.WAV)
    // Re-detect: output should still be a valid WAV
    assertEquals(AudioFormat.WAV, LlmHttpAudioPreprocessor.detectFormat(result))
    // Re-ensureMono: result is now mono, so it should be returned as-is
    val second = LlmHttpAudioPreprocessor.ensureMono(result, AudioFormat.WAV)
    assertSame(result, second)
  }

  @Test
  fun stereoDownmixRiffSizeIsUpdated() {
    val wav = buildWavStereo16bit(sampleRate = 44100, leftSamples = shortArrayOf(0, 1, 2), rightSamples = shortArrayOf(3, 4, 5))
    val result = LlmHttpAudioPreprocessor.ensureMono(wav, AudioFormat.WAV)
    // RIFF chunk size at bytes 4-7 must equal total file size - 8
    val riffSize = (result[4].toInt() and 0xFF) or
      ((result[5].toInt() and 0xFF) shl 8) or
      ((result[6].toInt() and 0xFF) shl 16) or
      ((result[7].toInt() and 0xFF) shl 24)
    assertEquals(result.size - 8, riffSize)
  }

  @Test
  fun stereoDownmixByteRateIsUpdated() {
    val sampleRate = 22050
    val wav = buildWavStereo16bit(sampleRate = sampleRate, leftSamples = shortArrayOf(0), rightSamples = shortArrayOf(0))
    val result = LlmHttpAudioPreprocessor.ensureMono(wav, AudioFormat.WAV)
    val fmtDataOffset = 20
    val byteRate = (result[fmtDataOffset + 8].toInt() and 0xFF) or
      ((result[fmtDataOffset + 9].toInt() and 0xFF) shl 8) or
      ((result[fmtDataOffset + 10].toInt() and 0xFF) shl 16) or
      ((result[fmtDataOffset + 11].toInt() and 0xFF) shl 24)
    // Mono 16-bit: byteRate = sampleRate * 1 channel * 2 bytes = sampleRate * 2
    assertEquals(sampleRate * 2, byteRate)
  }

  @Test
  fun wavWithExtraChunkBetweenFmtAndData() {
    // Real-world WAV files (from audio editors, HA) may have LIST/fact/bext chunks
    // between fmt and data. Ensure the chunk scanner finds data correctly.
    val wav = buildWavStereo16bitWithListChunk(sampleRate = 16000, leftSamples = shortArrayOf(500), rightSamples = shortArrayOf(-500))
    val result = LlmHttpAudioPreprocessor.ensureMono(wav, AudioFormat.WAV)
    // Should succeed and produce mono output
    assertEquals(AudioFormat.WAV, LlmHttpAudioPreprocessor.detectFormat(result))
    val fmtDataOffset = 20
    val channels = (result[fmtDataOffset + 2].toInt() and 0xFF) or ((result[fmtDataOffset + 3].toInt() and 0xFF) shl 8)
    assertEquals(1, channels)
  }

  // ---- ensureMono — WAV error cases ----------------------------------------

  @Test(expected = IllegalArgumentException::class)
  fun tooSmallWavThrows() {
    LlmHttpAudioPreprocessor.ensureMono(byteArrayOf(0x52, 0x49, 0x46, 0x46), AudioFormat.WAV)
  }

  @Test(expected = IllegalArgumentException::class)
  fun missingFmtChunkThrows() {
    // Valid RIFF/WAVE header but only a "data" chunk, no "fmt "
    val bytes = ByteArray(28)
    "RIFF".toByteArray().copyInto(bytes, 0)
    writeInt32LETest(bytes, 4, 20)
    "WAVE".toByteArray().copyInto(bytes, 8)
    "data".toByteArray().copyInto(bytes, 12)
    writeInt32LETest(bytes, 16, 4)
    LlmHttpAudioPreprocessor.ensureMono(bytes, AudioFormat.WAV)
  }

  @Test(expected = IllegalArgumentException::class)
  fun missingDataChunkThrows() {
    // Valid RIFF/WAVE header + fmt chunk but no "data" chunk
    val bytes = buildWavMono16bit(sampleRate = 16000, samples = shortArrayOf(0))
    // Corrupt the "data" FourCC to "xxxx"
    val dataPos = findChunkOffset(bytes, "data")
    if (dataPos >= 0) {
      bytes[dataPos] = 'x'.code.toByte(); bytes[dataPos + 1] = 'x'.code.toByte()
      bytes[dataPos + 2] = 'x'.code.toByte(); bytes[dataPos + 3] = 'x'.code.toByte()
    }
    LlmHttpAudioPreprocessor.ensureMono(bytes, AudioFormat.WAV)
  }

  @Test(expected = IllegalArgumentException::class)
  fun unsupportedFormatTagThrows() {
    // Build a WAV with format tag 3 (IEEE float) instead of 1 (PCM) — stereo to trigger downmix path
    val wav = buildWavStereo16bit(sampleRate = 44100, leftSamples = shortArrayOf(0), rightSamples = shortArrayOf(0))
    // Format tag is at fmt_data_offset + 0 (bytes 20-21)
    wav[20] = 3; wav[21] = 0
    LlmHttpAudioPreprocessor.ensureMono(wav, AudioFormat.WAV)
  }

  @Test(expected = IllegalArgumentException::class)
  fun stereoNon16bitThrows() {
    // Build a WAV with 8-bit stereo — downmix of non-16-bit is unsupported
    val wav = buildWavStereo8bit(sampleRate = 44100, leftSamples = byteArrayOf(128.toByte()), rightSamples = byteArrayOf(200.toByte()))
    LlmHttpAudioPreprocessor.ensureMono(wav, AudioFormat.WAV)
  }

  // ---- Helpers -------------------------------------------------------------

  /**
   * Builds a minimal mono 16-bit PCM WAV: RIFF header + fmt chunk + data chunk.
   */
  private fun buildWavMono16bit(sampleRate: Int, samples: ShortArray): ByteArray {
    val dataBytes = samples.size * 2
    val buf = ByteArray(44 + dataBytes) // standard 44-byte header for simple PCM
    "RIFF".toByteArray().copyInto(buf, 0)
    writeInt32LETest(buf, 4, 36 + dataBytes) // RIFF chunk size
    "WAVE".toByteArray().copyInto(buf, 8)
    "fmt ".toByteArray().copyInto(buf, 12)
    writeInt32LETest(buf, 16, 16) // fmt chunk size
    writeInt16LETest(buf, 20, 1) // PCM format tag
    writeInt16LETest(buf, 22, 1) // channels = 1
    writeInt32LETest(buf, 24, sampleRate)
    writeInt32LETest(buf, 28, sampleRate * 2) // byteRate = sampleRate * 1ch * 2bytes
    writeInt16LETest(buf, 32, 2) // blockAlign = 1ch * 2bytes
    writeInt16LETest(buf, 34, 16) // bitsPerSample
    "data".toByteArray().copyInto(buf, 36)
    writeInt32LETest(buf, 40, dataBytes)
    for (i in samples.indices) {
      writeInt16LETest(buf, 44 + i * 2, samples[i].toInt())
    }
    return buf
  }

  /**
   * Builds a stereo 16-bit PCM WAV with interleaved L/R samples.
   */
  private fun buildWavStereo16bit(sampleRate: Int, leftSamples: ShortArray, rightSamples: ShortArray): ByteArray {
    require(leftSamples.size == rightSamples.size)
    val nFrames = leftSamples.size
    val dataBytes = nFrames * 4 // 2 channels × 2 bytes each
    val buf = ByteArray(44 + dataBytes)
    "RIFF".toByteArray().copyInto(buf, 0)
    writeInt32LETest(buf, 4, 36 + dataBytes)
    "WAVE".toByteArray().copyInto(buf, 8)
    "fmt ".toByteArray().copyInto(buf, 12)
    writeInt32LETest(buf, 16, 16)
    writeInt16LETest(buf, 20, 1) // PCM
    writeInt16LETest(buf, 22, 2) // channels = 2
    writeInt32LETest(buf, 24, sampleRate)
    writeInt32LETest(buf, 28, sampleRate * 4) // byteRate = sampleRate * 2ch * 2bytes
    writeInt16LETest(buf, 32, 4) // blockAlign = 2ch * 2bytes
    writeInt16LETest(buf, 34, 16)
    "data".toByteArray().copyInto(buf, 36)
    writeInt32LETest(buf, 40, dataBytes)
    for (i in 0 until nFrames) {
      writeInt16LETest(buf, 44 + i * 4, leftSamples[i].toInt())
      writeInt16LETest(buf, 44 + i * 4 + 2, rightSamples[i].toInt())
    }
    return buf
  }

  /**
   * Builds a stereo 8-bit PCM WAV (to test non-16-bit rejection).
   */
  private fun buildWavStereo8bit(sampleRate: Int, leftSamples: ByteArray, rightSamples: ByteArray): ByteArray {
    require(leftSamples.size == rightSamples.size)
    val nFrames = leftSamples.size
    val dataBytes = nFrames * 2 // 2 channels × 1 byte each
    val buf = ByteArray(44 + dataBytes)
    "RIFF".toByteArray().copyInto(buf, 0)
    writeInt32LETest(buf, 4, 36 + dataBytes)
    "WAVE".toByteArray().copyInto(buf, 8)
    "fmt ".toByteArray().copyInto(buf, 12)
    writeInt32LETest(buf, 16, 16)
    writeInt16LETest(buf, 20, 1) // PCM
    writeInt16LETest(buf, 22, 2) // channels = 2
    writeInt32LETest(buf, 24, sampleRate)
    writeInt32LETest(buf, 28, sampleRate * 2) // byteRate = sampleRate * 2ch * 1byte
    writeInt16LETest(buf, 32, 2) // blockAlign
    writeInt16LETest(buf, 34, 8) // bitsPerSample = 8
    "data".toByteArray().copyInto(buf, 36)
    writeInt32LETest(buf, 40, dataBytes)
    for (i in 0 until nFrames) {
      buf[44 + i * 2] = leftSamples[i]
      buf[44 + i * 2 + 1] = rightSamples[i]
    }
    return buf
  }

  /**
   * Builds a stereo 16-bit PCM WAV with a fake LIST chunk inserted between fmt and data.
   * This simulates real-world files from audio editors that embed metadata chunks.
   */
  private fun buildWavStereo16bitWithListChunk(sampleRate: Int, leftSamples: ShortArray, rightSamples: ShortArray): ByteArray {
    require(leftSamples.size == rightSamples.size)
    val nFrames = leftSamples.size
    val dataBytes = nFrames * 4

    // Fake LIST chunk content: 4 bytes ("INFO") + 4 bytes text
    val listContent = "INFO    ".toByteArray(Charsets.ISO_8859_1)
    val listChunkSize = listContent.size

    val totalSize = 12 + // RIFF header
      8 + 16 + // fmt chunk header + data
      8 + listChunkSize + // LIST chunk
      8 + dataBytes // data chunk

    val buf = ByteArray(totalSize)
    var pos = 0

    // RIFF header
    "RIFF".toByteArray().copyInto(buf, pos); pos += 4
    writeInt32LETest(buf, pos, totalSize - 8); pos += 4
    "WAVE".toByteArray().copyInto(buf, pos); pos += 4

    // fmt chunk
    "fmt ".toByteArray().copyInto(buf, pos); pos += 4
    writeInt32LETest(buf, pos, 16); pos += 4
    writeInt16LETest(buf, pos, 1); pos += 2  // PCM
    writeInt16LETest(buf, pos, 2); pos += 2  // channels=2
    writeInt32LETest(buf, pos, sampleRate); pos += 4
    writeInt32LETest(buf, pos, sampleRate * 4); pos += 4  // byteRate
    writeInt16LETest(buf, pos, 4); pos += 2  // blockAlign
    writeInt16LETest(buf, pos, 16); pos += 2  // bitsPerSample

    // LIST chunk (metadata — scanner must skip this and continue to data)
    "LIST".toByteArray().copyInto(buf, pos); pos += 4
    writeInt32LETest(buf, pos, listChunkSize); pos += 4
    listContent.copyInto(buf, pos); pos += listChunkSize

    // data chunk
    "data".toByteArray().copyInto(buf, pos); pos += 4
    writeInt32LETest(buf, pos, dataBytes); pos += 4
    for (i in 0 until nFrames) {
      writeInt16LETest(buf, pos, leftSamples[i].toInt()); pos += 2
      writeInt16LETest(buf, pos, rightSamples[i].toInt()); pos += 2
    }

    return buf
  }

  /** Finds the byte offset of a RIFF chunk's FourCC in [bytes], or -1 if not found. */
  private fun findChunkOffset(bytes: ByteArray, fourCC: String): Int {
    var pos = 12
    while (pos + 8 <= bytes.size) {
      val id = String(bytes, pos, 4, Charsets.ISO_8859_1)
      if (id == fourCC) return pos
      val size = (bytes[pos + 4].toInt() and 0xFF) or
        ((bytes[pos + 5].toInt() and 0xFF) shl 8) or
        ((bytes[pos + 6].toInt() and 0xFF) shl 16) or
        ((bytes[pos + 7].toInt() and 0xFF) shl 24)
      pos += 8 + size + (size and 1)
      if (size <= 0) break
    }
    return -1
  }

  private fun writeInt16LETest(buf: ByteArray, offset: Int, value: Int) {
    buf[offset] = (value and 0xFF).toByte()
    buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
  }

  private fun writeInt32LETest(buf: ByteArray, offset: Int, value: Int) {
    buf[offset] = (value and 0xFF).toByte()
    buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
    buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
    buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
  }
}
