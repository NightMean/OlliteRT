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
import com.google.mediapipe.tasks.audio.audiotranscriber.AudioTranscriber
import com.google.mediapipe.tasks.audio.audiotranscriber.AudioTranscriberResult
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asCoroutineDispatcher

private const val TAG = "AudioAsrEngine"

/**
 * Batch-mode on-device ASR engine backed by MediaPipe's `AudioTranscriber` task, mirroring Google
 * AI Edge Gallery's `SpeechProcessorAsrService` but scoped to whole-clip files (no live mic).
 *
 * Input: mono 16 kHz Float PCM (`FloatArray` in `[-1, 1]`). Output: cumulative final transcript.
 * The engine is stateful — one clip at a time; concurrent callers must serialize through
 * [AudioServiceManager].
 */
class MediaPipeAsrEngine(
  private val context: Context,
  private val modelFile: File,
  private val spec: AudioModelSpec,
  private val delegate: Delegate? = null,
) : AutoCloseable {

  private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "MediaPipeAsrEngine").apply { isDaemon = true }
  }

  internal val engineDispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

  private val state = AtomicReference(EngineState())
  private val lock = Object()

  @Volatile
  private var inFlight = AtomicBoolean(false)

  internal data class EngineState(
    val transcriber: AudioTranscriber? = null,
    val modelFingerprint: String? = null,
  )

  private fun cacheDir(): File = File(context.cacheDir, CACHE_DIRECTORY_NAME).apply { mkdirs() }

  fun prepare() {
    synchronized(lock) {
      if (state.get().transcriber != null) return@synchronized
    val fingerprint = "${modelFile.absolutePath}:${modelFile.lastModified()}:${modelFile.length()}"
    val baseOptions = BaseOptions.builder()
      .setModelAssetPath(modelFile.absolutePath)
      .setDelegate(resolveDelegate())
      .build()
    val options = AudioTranscriber.AudioTranscriberOptions.builder()
      .setBaseOptions(baseOptions)
      .setSampleRateHz(16_000)
      .setEnableInterimResults(true)
      .setModelArchitecture(AudioTranscriber.ModelArchitecture.AUTO)
      .setCacheDir(cacheDir().absolutePath)
      .build()
    val transcriber = AudioTranscriber.createFromOptions(context, options)
    state.set(EngineState(transcriber, fingerprint))
    // Warm up so the first batch call doesn't pay the JIT/native cold-start cost.
    runCatching {
      transcriber.prewarm()
    }.onFailure { Log.w(TAG, "ASR prewarm failed (non-fatal)", it) }
    }
  }

  internal fun resolveDelegate(): Delegate {
    val supported = if (spec.supportsGpu) setOf(Delegate.GPU, Delegate.CPU) else setOf(Delegate.CPU)
    val requested = delegate
    if (requested != null && requested in supported) return requested
    return if (supported.contains(Delegate.GPU)) Delegate.GPU else Delegate.CPU
  }

  /**
   * Transcribes a whole 16 kHz mono clip. Blocks the caller thread (or the caller's dispatcher)
   * until the clip has been fully processed.
   *
   * @throws IllegalStateException if [prepare] has not been called or the engine is in a bad state.
   */
  suspend fun transcribeClip(samples: FloatArray): String {
    prepare()
    if (!inFlight.compareAndSet(false, true)) {
      throw IllegalStateException("ASR engine is already processing another clip.")
    }
    try {
      return withContext(engineDispatcher) {
        transcribeLocked(samples)
      }
    } finally {
      inFlight.set(false)
    }
  }

  private fun transcribeLocked(samples: FloatArray): String {
    val transcriber = state.get().transcriber ?: throw IllegalStateException("ASR engine not prepared.")
    val finalSegments = mutableListOf<String>()

    // Feed in ~100 ms chunks (1600 samples) the same way the gallery does.
    var offset = 0
    while (offset < samples.size) {
      val end = minOf(offset + CHUNK_SAMPLES, samples.size)
      val chunk = samples.copyOfRange(offset, end)
      val result = transcriber.transcribe(chunk, false)
      collectFinalSegments(result, finalSegments)
      offset = end
    }

    val finalResult = transcriber.finishStream()
    collectFinalSegments(finalResult, finalSegments)

    return finalSegments.joinToString(" ").trim()
  }

  private fun collectFinalSegments(result: AudioTranscriberResult?, out: MutableList<String>) {
    val segments = result?.segments() ?: return
    for (segment in segments) {
      val text = segment.transcript().trim()
      if (text.isEmpty()) continue
      if (segment.isFinal) out.add(text)
    }
  }

  /** Releases the native engine. Safe to call multiple times. */
  override fun close() {
    val transcriber = state.get().transcriber
    state.set(EngineState())
    runCatching { transcriber?.close() }.onFailure { Log.w(TAG, "ASR close failed", it) }
    runCatching { executor.shutdown() }.onFailure { Log.w(TAG, "ASR dispatcher close failed", it) }
  }

  companion object {
    /** 16 kHz sampling rate — the AudioTranscriber's fixed requirement. */
    const val SAMPLE_RATE_HZ = 16_000

    /** ~100 ms of samples per transcribe() call. */
    const val CHUNK_SAMPLES = 1_600

    /** Cache directory for the compiled AudioTranscriber delegate. */
    const val CACHE_DIRECTORY_NAME = "mediapipe_asr_cache"
  }
}
