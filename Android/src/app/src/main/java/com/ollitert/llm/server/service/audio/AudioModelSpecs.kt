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

import java.io.File

/**
 * Static metadata for a MediaPipe audio model, mirroring the engine-side registry in Google AI
 * Edge Gallery's `services/asr` and `services/tts` packages (but scoped to batch, file-based
 * input for the server fork).
 *
 * @property modelId Stable engine identifier (e.g. `parakeet-tdt-0.6b-v3`).
 * @property kind [AudioModelKind.ASR] or [AudioModelKind.TTS].
 * @property displayName User-facing name.
 * @property expectedFileName File name when the model is sideloaded/downloaded (used for hints).
 * @property defaultVoiceIds Voices shipped with the model (TTS only).
 * @property defaultLanguages BCP-47 locales the model speaks (TTS) or understands (ASR).
 * @property supportsGpu Whether the model can use the OpenCL GPU delegate.
 * @property warmUpText Utterance synthesised during warm-up; empty string skips warm-up
 *   synthesis.
 */
data class AudioModelSpec(
  val modelId: String,
  val kind: AudioModelKind,
  val displayName: String,
  val expectedFileName: String,
  val defaultVoiceIds: List<String> = emptyList(),
  val defaultLanguages: List<String> = emptyList(),
  val supportsGpu: Boolean = true,
  val warmUpText: String = "Hi.",
)

enum class AudioModelKind {
  ASR,
  TTS,
}

/** Engine-side view of the allowlist: one spec per supported model. */
object AudioModelSpecs {

  const val PARAKEET_TDT_06B_V3_ID = "parakeet-tdt-0.6b-v3"
  const val KOKORO_82M_ID = "kokoro-82m"
  const val POCKET_TTS_100M_ID = "pocket-tts-100m"
  const val PHONON_31M_ID = "phonon-31m"

  val PARAKEET_TDT_06B_V3 = AudioModelSpec(
    modelId = PARAKEET_TDT_06B_V3_ID,
    kind = AudioModelKind.ASR,
    displayName = "Parakeet TDT 0.6B ASR",
    expectedFileName = "parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm",
    defaultLanguages = listOf("en-US"),
    supportsGpu = true,
  )

  val KOKORO_82M = AudioModelSpec(
    modelId = KOKORO_82M_ID,
    kind = AudioModelKind.TTS,
    displayName = "Kokoro 82M",
    expectedFileName = "kokoro_int8_gpu_fp16.litertlm",
    defaultVoiceIds = listOf("af_heart", "af_alloy", "am_adam", "bf_alice", "bm_george"),
    defaultLanguages = listOf("en-US", "en-GB", "es-ES", "fr-FR", "hi-IN", "it-IT", "pt-BR", "ja-JP", "zh-CN"),
    supportsGpu = true,
  )

  val POCKET_TTS_100M = AudioModelSpec(
    modelId = POCKET_TTS_100M_ID,
    kind = AudioModelKind.TTS,
    displayName = "PocketTTS 100M",
    expectedFileName = "pocket_tts_int8.litertlm",
    defaultVoiceIds = listOf("alba", "marius", "javert", "jean", "fantine", "cosette", "eponine", "azelma"),
    defaultLanguages = listOf("en-US", "fr-FR", "de-DE", "es-ES", "it-IT", "pt-BR", "nl-NL"),
    supportsGpu = false,
    warmUpText = "",
  )

  val PHONON_31M = AudioModelSpec(
    modelId = PHONON_31M_ID,
    kind = AudioModelKind.TTS,
    displayName = "Phonon 31M",
    expectedFileName = "phonon_int8.litertlm",
    defaultVoiceIds = listOf("freya", "claire", "lukas", "sofia", "lucas"),
    defaultLanguages = listOf("en-US"),
    supportsGpu = false,
    warmUpText = "",
  )

  val ALL: List<AudioModelSpec> = listOf(PARAKEET_TDT_06B_V3, KOKORO_82M, POCKET_TTS_100M, PHONON_31M)

  fun byId(modelId: String?): AudioModelSpec? =
    if (modelId.isNullOrEmpty()) null else ALL.firstOrNull { it.modelId == modelId }

  fun asList(): List<AudioModelSpec> = ALL

  /**
   * Resolves the model file for [modelId]. Batch-input service does not own the download flow — the
   * file is supplied by the caller (or the app's normal model download pipeline, reusing the
   * [File] from the external-files model directory).
   */
  fun resolveFile(context: android.content.Context, modelId: String): File? {
    val spec = byId(modelId) ?: return null
    return findModelFile(context, spec)
  }

  private fun findModelFile(context: android.content.Context, spec: AudioModelSpec): File? {
    val externalDir = context.getExternalFilesDir(null)?.absolutePath ?: return null
    val byExt = File(externalDir, spec.expectedFileName)
    if (byExt.exists()) return byExt
    // Also look inside per-model subdirectories (matches the Model.getPath layout:
    // <external>/<normalizedName>/<version>/<fileName>)
    val root = File(externalDir)
    val found = root.walkTopDown()
      .filter { it.isFile && it.name.equals(spec.expectedFileName, ignoreCase = true) }
      .firstOrNull()
    return found
  }
}
