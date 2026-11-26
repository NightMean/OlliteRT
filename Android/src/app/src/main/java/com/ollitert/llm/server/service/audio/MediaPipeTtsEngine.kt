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
import com.google.mediapipe.tasks.audio.texttospeech.AudioChunk
import com.google.mediapipe.tasks.audio.texttospeech.SynthesisOptions
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeech
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeechOptions
import com.google.mediapipe.tasks.audio.texttospeech.VoiceInfo
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.withLock

private const val TAG = "AudioTtsEngine"

data class TtsResult(val samples: FloatArray, val sampleRateHz: Int)

/**
 * Batch-mode on-device TTS engine backed by MediaPipe's `TextToSpeech` task, mirroring Google AI
 * Edge Gallery's `MediaPipeTtsEngine` but scoped to whole-utterances (no streaming/playback).
 *
 * Output: mono Float PCM at the model's native rate (24 kHz for Kokoro/PocketTTS/Phonon).
 * The engine is stateful — one model loaded at a time; callers serialize through
 * [AudioServiceManager].
 */
class MediaPipeTtsEngine(
  private val context: Context,
  private val modelFile: File,
  private val spec: AudioModelSpec,
  private val delegate: Delegate? = null,
  /** Engine-level speaking rate (OpenAI `speed`); MediaPipe applies it at create time, not per request. */
  private val speakingRate: Float = 1.0f,
) : AutoCloseable {

  private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "MediaPipeTtsEngine").apply { isDaemon = true }
  }

  internal val engineDispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

  private val inFlight = AtomicBoolean(false)

  @Volatile
  private var tts: TextToSpeech? = null

  /**
   * The voice the current task instance has graphed (last task creation). Each Kokoro voice is
   * an internal vocoder graph on the GPU; the task accumulates them and never releases them,
   * so a third voice poisons Mali allocations ("Failed to invoke the compiled model",
   * kokoro_vocoder_stage.cc) and sticks the engine. Switching voices recreates the task so at
   * most one voice graph is resident at a time.
   */
  @Volatile
  private var loadedVoiceId: String? = null

  fun voices(): List<String> = tts?.listVoices()?.map { it.voiceId }?.filter { it.isNotEmpty() } ?: spec.defaultVoiceIds

  /** Full voice metadata (id, display name, locale) when the engine is loaded; empty otherwise. */
  fun voiceInfos(): List<VoiceInfo> = tts?.listVoices() ?: emptyList()

  private val prepareMutex = kotlinx.coroutines.sync.Mutex()

  suspend fun prepare() {
    if (tts != null) return
    prepareMutex.withLock {
      if (tts != null) return@withLock
    val instance = withContext(engineDispatcher) { createTask() }
    tts = instance
    // Warm up the loaded model on the engine thread. Warm-up is best-effort — the engine
    // still works if it fails.
    if (spec.warmUpText.isNotEmpty()) {
      try {
        withContext(engineDispatcher) {
          synthesizeLocked(spec.warmUpText, null, null)
        }
      } catch (e: Exception) {
        Log.w(TAG, "TTS warm-up failed (non-fatal)", e)
      }
    }
    }
  }

  /** Builds the task options and creates a fresh task. Must run on the engine dispatcher. */
  private fun createTask(): TextToSpeech {
    val resolvedDelegate =
      if (delegate != null && (spec.supportsGpu || delegate == Delegate.CPU)) delegate
      else if (spec.supportsGpu) Delegate.GPU else Delegate.CPU
    val cacheDir = File(context.cacheDir, CACHE_DIRECTORY_NAME).apply { mkdirs() }
    val options = TextToSpeechOptions(
      baseOptions = BaseOptions.builder()
        .setModelAssetPath(modelFile.absolutePath)
        .setDelegate(resolvedDelegate)
        .build(),
      defaultVoiceId = null,
      defaultLocale = "en-US",
      speakingRate = speakingRate.coerceIn(0.25f, 4.0f),
      cacheDirectory = cacheDir.absolutePath,
    )
    return TextToSpeech.create(options)
  }

  /**
   * Closes the current task (releasing its resident voice graph from the GPU) and creates a
   * fresh one, then best-effort warms it with [voiceId] so the internal graph is built now and
   * any allocation failure surfaces here, where the caller's retry layer can catch it. Must run
   * on the engine dispatcher while no synthesis is in flight (the inFlight guard in
   * [synthesize] makes it so).
   */
  private suspend fun recreateTaskForVoice(voiceId: String?) {
    val old = tts
    tts = null
    runCatching { old?.close() }.onFailure { Log.w(TAG, "TTS task close failed", it) }
    val fresh = createTask()
    tts = fresh
    loadedVoiceId = voiceId
    if (spec.warmUpText.isNotEmpty()) {
      runCatching {
        fresh
          .synthesizeStream(
            spec.warmUpText,
            SynthesisOptions(
              voiceId = voiceId?.takeIf { it.isNotEmpty() },
              locale = null,
            ),
          )
          .collect { }
      }.onFailure { Log.w(TAG, "TTS re-warm-up after recreate failed (non-fatal)", it) }
    }
  }

  /**
   * Synthesizes [text] and returns the flattened mono Float PCM at the model's native rate
   * (typically 24 kHz). Blocks the caller.
   *
   * @throws IllegalStateException if the engine is busy or not prepared.
   */
  suspend fun synthesize(text: String, voiceId: String? = null, locale: String? = null): TtsResult {
    if (text.trim().isEmpty()) return TtsResult(FloatArray(0), SAMPLE_RATE_HZ_DEFAULT)
    prepare()
    if (!inFlight.compareAndSet(false, true)) {
      throw IllegalStateException("TTS engine is already processing another request.")
    }
    try {
      return withContext(engineDispatcher) {
        val wantVoice = voiceId?.takeIf { it.isNotEmpty() }
        if (wantVoice != null && wantVoice != loadedVoiceId) {
          // A voice switch would accumulate a second resident GPU graph in the task — recreate
          // instead (see loadedVoiceId).
          recreateTaskForVoice(wantVoice)
        }
        try {
          synthesizeLocked(text.trim(), wantVoice, locale)
        } catch (e: Exception) {
          // Sticky GPU allocation failure ("Failed to invoke the compiled model"): the task's
          // graph is poisoned for its life. Recreate it once and retry the same request. We are
          // on our own coroutine frame here (the exception already propagated out of the native
          // call), so closing the task is safe — the native-callback close deadlock of the chat
          // engine does not apply. A second failure propagates to the caller (500).
          Log.w(TAG, "TTS synthesis failed, recreating task and retrying once", e)
          recreateTaskForVoice(wantVoice)
          synthesizeLocked(text.trim(), wantVoice, locale)
        }
      }
    } finally {
      inFlight.set(false)
    }
  }

  private suspend fun synthesizeLocked(
    text: String,
    voiceId: String?,
    locale: String?,
  ): TtsResult {
    val instance = tts ?: throw IllegalStateException("TTS engine not prepared.")
    val options = SynthesisOptions(
      voiceId = voiceId?.takeIf { it.isNotEmpty() },
      locale = locale?.takeIf { it.isNotEmpty() },
    )
    val all = mutableListOf<AudioChunk>()
    instance.synthesizeStream(text, options).toList(all)
    if (all.isEmpty()) return TtsResult(FloatArray(0), SAMPLE_RATE_HZ_DEFAULT)
    val sampleRate = all.first().sampleRateHz
    val total = all.sumOf { it.samples.size }
    val out = FloatArray(total)
    var offset = 0
    for (chunk in all) {
      chunk.samples.copyInto(out, offset)
      offset += chunk.samples.size
    }
    return TtsResult(out, sampleRate)
  }

  /** Cancels any in-flight synthesis. */
  fun cancel() {
    runCatching { tts?.cancel() }.onFailure { Log.w(TAG, "TTS cancel failed", it) }
  }

  override fun close() {
    cancel()
    val instance = tts
    tts = null
    runCatching { instance?.close() }.onFailure { Log.w(TAG, "TTS close failed", it) }
    runCatching { executor.shutdown() }.onFailure { Log.w(TAG, "TTS dispatcher close failed", it) }
  }

  companion object {
    /** Cache directory for the compiled TextToSpeech delegate. */
    const val CACHE_DIRECTORY_NAME = "mediapipe_tts_cache"

    /** Used only when the synthesized chunks somehow carry no rate. */
    const val SAMPLE_RATE_HZ_DEFAULT = 24_000
  }
}
