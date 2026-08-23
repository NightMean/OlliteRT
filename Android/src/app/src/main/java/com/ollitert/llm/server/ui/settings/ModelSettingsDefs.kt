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

package com.ollitert.llm.server.ui.settings

import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT
import com.ollitert.llm.server.data.ServerPrefs

// ─── Repositories & HF Token ─────────────────────────────────────────

val REPOSITORIES_NAV = SettingDef.Custom(
  key = "repositories_nav",
  labelRes = R.string.settings_card_repositories,
  descriptionRes = R.string.settings_repositories_description,
  card = CardId.REPOSITORIES,
)

val HF_TOKEN = SettingDef.TextInput(
  key = "hf_token",
  labelRes = R.string.settings_card_hf_token,
  descriptionRes = R.string.settings_hf_token_desc,
  card = CardId.HF_TOKEN,
  default = "",
  prefsKey = "hf_token",
  isPassword = true,
  read = { ServerPrefs.getHfToken(it) },
  write = { ctx, v -> ServerPrefs.setHfToken(ctx, v) },
)

// ─── Model Behaviour Card ─────────────────────────────────────────────

val CUSTOM_PROMPTS = SettingDef.Toggle(
  key = "custom_prompts",
  labelRes = R.string.settings_custom_prompts,
  descriptionRes = R.string.settings_custom_prompts_desc,
  card = CardId.MODEL_BEHAVIOUR,
  default = false,
  prefsKey = "custom_prompts_enabled",
  read = { ServerPrefs.isCustomPromptsEnabled(it) },
  write = { ctx, v -> ServerPrefs.setCustomPromptsEnabled(ctx, v) },
)

val SCHEMA_INJECTION_TOOL_CALLING = SettingDef.Toggle(
  key = "schema_injection_tool_calling",
  labelRes = R.string.settings_schema_injection_tool_calling,
  descriptionRes = R.string.settings_schema_injection_tool_calling_desc,
  card = CardId.MODEL_BEHAVIOUR,
  default = true,
  prefsKey = "schema_injection_tool_calling",
  read = { ServerPrefs.isSchemaInjectionToolCalling(it) },
  write = { ctx, v -> ServerPrefs.setSchemaInjectionToolCalling(ctx, v) },
)

val REJECT_WHEN_BUSY = SettingDef.Toggle(
  key = "reject_when_busy",
  labelRes = R.string.settings_reject_when_busy,
  descriptionRes = R.string.settings_reject_when_busy_desc,
  card = CardId.MODEL_BEHAVIOUR,
  default = false,
  prefsKey = "reject_when_busy",
  read = { ServerPrefs.isRejectWhenBusy(it) },
  write = { ctx, v -> ServerPrefs.setRejectWhenBusy(ctx, v) },
)

val WARMUP_MESSAGE = SettingDef.Toggle(
  key = "warmup_message",
  labelRes = R.string.settings_warmup_message,
  descriptionRes = R.string.settings_warmup_message_desc,
  card = CardId.MODEL_BEHAVIOUR,
  default = true,
  prefsKey = "warmup_enabled",
  read = { ServerPrefs.isWarmupEnabled(it) },
  write = { ctx, v -> ServerPrefs.setWarmupEnabled(ctx, v) },
)

val PRE_INIT_VISION = SettingDef.Toggle(
  key = "pre_init_vision",
  labelRes = R.string.settings_pre_init_vision,
  descriptionRes = R.string.settings_pre_init_vision_desc,
  card = CardId.MODEL_BEHAVIOUR,
  default = false,
  prefsKey = "eager_vision_init",
  requiresRestart = true,
  read = { ServerPrefs.isEagerVisionInit(it) },
  write = { ctx, v -> ServerPrefs.setEagerVisionInit(ctx, v) },
)

val IGNORE_CLIENT_PARAMS = SettingDef.Toggle(
  key = "ignore_client_params",
  labelRes = R.string.settings_ignore_client_params,
  descriptionRes = R.string.settings_ignore_client_params_desc,
  card = CardId.MODEL_BEHAVIOUR,
  default = false,
  prefsKey = "ignore_client_sampler_params",
  read = { ServerPrefs.isIgnoreClientSamplerParams(it) },
  write = { ctx, v -> ServerPrefs.setIgnoreClientSamplerParams(ctx, v) },
)

val STT_TRANSCRIPTION_PROMPT = SettingDef.Toggle(
  key = "stt_transcription_prompt",
  labelRes = R.string.settings_stt_transcription_prompt,
  descriptionRes = R.string.settings_stt_transcription_prompt_desc,
  card = CardId.MODEL_BEHAVIOUR,
  default = true,
  prefsKey = "stt_transcription_prompt",
  read = { ServerPrefs.isSttTranscriptionPromptEnabled(it) },
  write = { ctx, v -> ServerPrefs.setSttTranscriptionPromptEnabled(ctx, v) },
)

val STT_TRANSCRIPTION_PROMPT_TEXT = SettingDef.TextInput(
  key = "stt_transcription_prompt_text",
  labelRes = R.string.settings_stt_transcription_prompt_text,
  descriptionRes = R.string.settings_stt_transcription_prompt_text_desc,
  card = CardId.MODEL_BEHAVIOUR,
  default = DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT,
  prefsKey = "stt_transcription_prompt_text",
  read = { ServerPrefs.getSttTranscriptionPromptText(it).ifBlank { DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT } },
  write = { ctx, v ->
    ServerPrefs.setSttTranscriptionPromptText(
      ctx, v.ifBlank { DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT },
    )
  },
)

// ─── Context Management Card ───────────────────────────────────────────────

val TRUNCATE_HISTORY = SettingDef.Toggle(
  key = "truncate_history",
  labelRes = R.string.settings_truncate_history,
  descriptionRes = R.string.settings_truncate_history_desc,
  card = CardId.CONTEXT_MANAGEMENT,
  default = false,
  resetDefault = true,
  prefsKey = "auto_truncate_history",
  read = { ServerPrefs.isAutoTruncateHistory(it) },
  write = { ctx, v -> ServerPrefs.setAutoTruncateHistory(ctx, v) },
)

val TRIM_PROMPT = SettingDef.Toggle(
  key = "trim_prompt",
  labelRes = R.string.settings_trim_prompt,
  descriptionRes = R.string.settings_trim_prompt_desc,
  card = CardId.CONTEXT_MANAGEMENT,
  default = false,
  prefsKey = "auto_trim_prompts",
  read = { ServerPrefs.isAutoTrimPrompts(it) },
  write = { ctx, v -> ServerPrefs.setAutoTrimPrompts(ctx, v) },
)
