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

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "AudioServiceManager"

/**
 * Owns the loaded batch-ASR and batch-TTS engines for the app, keeping at most one instance per
 * kind alive at a time (native weights take ~600 MB and 100+ MB respectively).
 *
 * The returned engines are the callers' to close (or to drop); the manager releases them only on
 * explicit unload/release.
 */
class AudioServiceManager(private val context: Context) {

  private val mutex = Mutex()

  @Volatile
  private var asrEngine: MediaPipeAsrEngine? = null

  @Volatile
  private var asrModelId: String? = null

  @Volatile
  private var asrFilePath: String? = null

  @Volatile
  private var ttsEngine: MediaPipeTtsEngine? = null

  @Volatile
  private var ttsModelId: String? = null

  @Volatile
  private var ttsFilePath: String? = null

  @Volatile
  private var ttsSpeakingRate: Float = 1.0f

  /** Which ASR engines may be created/loaded (mirrors the allowlist). */
  val availableAsrModels: List<AudioModelSpec> = AudioModelSpecs.ALL.filter { it.kind == AudioModelKind.ASR }

  val availableTtsModels: List<AudioModelSpec> = AudioModelSpecs.ALL.filter { it.kind == AudioModelKind.TTS }

  fun currentAsrModelId(): String? = asrModelId

  fun currentTtsModelId(): String? = ttsModelId

  suspend fun loadAsr(modelId: String, modelFile: File? = null): MediaPipeAsrEngine = mutex.withLock {
    val spec = AudioModelSpecs.byId(modelId)
      ?: throw ModelNotFoundException("Unknown ASR model id: $modelId")
    val file = modelFile ?: AudioModelSpecs.resolveFile(context, modelId)
      ?: throw ModelNotFoundException(
        "ASR model file not found. Sideload ${spec.expectedFileName} into the app's external files directory."
      )
    if (!file.exists()) throw ModelNotFoundException("ASR model file missing: ${file.absolutePath}")

    if (asrEngine != null && asrModelId == modelId && asrFilePath == file.absolutePath) {
      return@withLock asrEngine!!
    }
    unloadAsrLocked()
    val engine = MediaPipeAsrEngine(context, file, spec)
    engine.prepare()
    asrEngine = engine
    asrModelId = modelId
    asrFilePath = file.absolutePath
    engine
  }

  suspend fun loadTts(modelId: String, modelFile: File? = null, speakingRate: Float = 1.0f): MediaPipeTtsEngine = mutex.withLock {
    val spec = AudioModelSpecs.byId(modelId)
      ?: throw ModelNotFoundException("Unknown TTS model id: $modelId")
    val file = modelFile ?: AudioModelSpecs.resolveFile(context, modelId)
      ?: throw ModelNotFoundException(
        "TTS model file not found. Sideload ${spec.expectedFileName} into the app's external files directory."
      )
    if (!file.exists()) throw ModelNotFoundException("TTS model file missing: ${file.absolutePath}")

    if (ttsEngine != null && ttsModelId == modelId && ttsFilePath == file.absolutePath && ttsSpeakingRate == speakingRate) {
      return@withLock ttsEngine!!
    }
    unloadTtsLocked()
    val engine = MediaPipeTtsEngine(context, file, spec, speakingRate = speakingRate)
    engine.prepare()
    ttsEngine = engine
    ttsModelId = modelId
    ttsFilePath = file.absolutePath
    ttsSpeakingRate = speakingRate
    engine
  }

  /**
   * The loaded TTS engine for [modelId] without triggering a load — null when nothing of that
   * model is resident. Used by the voices endpoint so a GET does not pull ~100 MB of weights.
   */
  fun ttsEngineIfLoaded(modelId: String?): MediaPipeTtsEngine? {
    val id = modelId ?: ttsModelId ?: return null
    return if (ttsModelId == id) ttsEngine else null
  }

  /** Transcribes [wavBytes] (16-bit PCM WAV, mono or stereo) with the loaded ASR engine. */
  suspend fun transcribe(wavBytes: ByteArray, modelId: String? = null): String {
    val samples = AudioWavDecoder.decode(wavBytes, 16_000).samples
    val engine = loadAsr(modelId ?: asrModelId ?: AudioModelSpecs.PARAKEET_TDT_06B_V3_ID)
    return engine.transcribeClip(samples)
  }

  /** Synthesizes [text] and returns mono Float PCM at the model's native rate with the loaded TTS engine. */
  suspend fun synthesize(
    text: String,
    modelId: String? = null,
    voiceId: String? = null,
    locale: String? = null,
    speed: Float = 1.0f,
  ): TtsResult {
    val engine = loadTts(modelId ?: ttsModelId ?: AudioModelSpecs.KOKORO_82M_ID, speakingRate = speed)
    return engine.synthesize(text, voiceId, locale)
  }

  /** Releases the currently-loaded ASR engine. */
  suspend fun unloadAsr() = mutex.withLock { unloadAsrLocked() }

  /** Releases the currently-loaded TTS engine. */
  suspend fun unloadTts() = mutex.withLock { unloadTtsLocked() }

  /** Releases both engines. */
  suspend fun release() = mutex.withLock {
    unloadAsrLocked()
    unloadTtsLocked()
  }

  private suspend fun unloadAsrLocked() {
    val engine = asrEngine ?: return
    asrEngine = null
    asrModelId = null
    asrFilePath = null
    runCatching { engine.close() }.onFailure { Log.w(TAG, "ASR engine close failed", it) }
  }

  private suspend fun unloadTtsLocked() {
    val engine = ttsEngine ?: return
    ttsEngine = null
    ttsModelId = null
    ttsFilePath = null
    ttsSpeakingRate = 1.0f
    runCatching { engine.close() }.onFailure { Log.w(TAG, "TTS engine close failed", it) }
  }

  class ModelNotFoundException(message: String) : IllegalStateException(message)
}
