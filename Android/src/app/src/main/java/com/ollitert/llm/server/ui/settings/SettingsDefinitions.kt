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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Token
import androidx.compose.material.icons.outlined.Tune
import com.ollitert.llm.server.R

// ─── All Setting Definitions (ordered) ──────────────────────────────────────
// Order must match: CardId enum, allCardDefs, and SettingsScreen.kt rendering.

val allSettingDefs: List<SettingDef> = listOf(
  // Repositories
  REPOSITORIES_NAV,
  // HF Token
  HF_TOKEN,
  // General
  KEEP_SCREEN_AWAKE, SHOW_MODEL_RECOMMENDATIONS, RESOLVE_CLIENT_HOSTNAMES,
  WRAP_LOG_TEXT, AUTO_EXPAND_LOGS, STREAM_RESPONSE_PREVIEW, KEEP_PARTIAL_RESPONSE, COMPACT_IMAGE_DATA,
  HIDE_HEALTH_LOGS, CLEAR_LOGS_ON_STOP, CONFIRM_CLEAR_LOGS,
  // Server Config
  SERVER_BIND_MODE, CUSTOM_BIND_ADDRESS, HOST_PORT, CLIENT_IP_POLICY_MODE, CLIENT_IP_RULES,
  BEARER_TOKEN, CORS_ORIGINS,
  // Auto-Launch
  DEFAULT_MODEL, START_ON_BOOT, KEEP_ALIVE, KEEP_ALIVE_TIMEOUT, DONTKILLMYAPP,
  // Model Behaviour
  CUSTOM_PROMPTS, SCHEMA_INJECTION_TOOL_CALLING, REJECT_WHEN_BUSY, WARMUP_MESSAGE,
  PRE_INIT_VISION, AUDIO_GPU_ACCELERATION, IGNORE_CLIENT_PARAMS, STT_TRANSCRIPTION_PROMPT, STT_TRANSCRIPTION_PROMPT_TEXT,
  // Context Management
  TRUNCATE_HISTORY, TRIM_PROMPT,
  // Metrics
  SHOW_REQUEST_TYPES, SHOW_ADVANCED_METRICS, NOTIF_REQUEST_COUNT, FORCE_STREAM_USAGE,
  // Log Persistence
  LOG_PERSISTENCE_ENABLED, LOG_MAX_ENTRIES, LOG_AUTO_DELETE, CLEAR_ALL_LOGS,
  // Home Assistant
  HA_INTEGRATION,
  // Updates
  AUTO_UPDATE_CHECK, CHECK_FREQUENCY, CROSS_CHANNEL_NOTIFY, CHECK_FOR_UPDATES, NOTIFICATION_SETTINGS,
  // Developer
  VERBOSE_DEBUG, EXPORT_LOGCAT,
  // Advanced Timeouts
  DEFAULT_SEED, TIMEOUT_CHAT_COMPLETIONS, TIMEOUT_RESPONSES, TIMEOUT_STREAMING, TIMEOUT_BLOCKING,
  TIMEOUT_WARMUP, TIMEOUT_KEEP_ALIVE_RECHECK, TIMEOUT_CLEANUP_AWAIT,
  // Reset
  RESET_TO_DEFAULTS,
)

/** Lookup table: setting key → SettingDef. */
val settingDefsByKey: Map<String, SettingDef> = allSettingDefs.associateBy { it.key }

// ─── Card Definitions ───────────────────────────────────────────────────────
// Order must match: CardId enum, allSettingDefs sections, and SettingsScreen.kt rendering.

val allCardDefs: List<CardDef> = listOf(
  CardDef(
    id = CardId.REPOSITORIES,
    titleRes = R.string.settings_card_repositories,
    icon = CardIcon.Vector(Icons.Outlined.Inventory2),
    settings = listOf(REPOSITORIES_NAV),
  ),
  CardDef(
    id = CardId.HF_TOKEN,
    titleRes = R.string.settings_card_hf_token,
    icon = CardIcon.Vector(Icons.Outlined.Key),
    settings = listOf(HF_TOKEN),
  ),
  CardDef(
    id = CardId.GENERAL,
    titleRes = R.string.settings_card_general,
    icon = CardIcon.Vector(Icons.Outlined.PhoneAndroid),
    settings = listOf(
      KEEP_SCREEN_AWAKE, SHOW_MODEL_RECOMMENDATIONS, RESOLVE_CLIENT_HOSTNAMES,
      WRAP_LOG_TEXT, AUTO_EXPAND_LOGS, STREAM_RESPONSE_PREVIEW, KEEP_PARTIAL_RESPONSE, COMPACT_IMAGE_DATA,
      HIDE_HEALTH_LOGS, CLEAR_LOGS_ON_STOP, CONFIRM_CLEAR_LOGS,
    ),
  ),
  CardDef(
    id = CardId.SERVER_CONFIG,
    titleRes = R.string.settings_card_server_config,
    icon = CardIcon.Vector(Icons.Outlined.Tune),
    settings = listOf(
      SERVER_BIND_MODE, CUSTOM_BIND_ADDRESS, HOST_PORT, CLIENT_IP_POLICY_MODE, CLIENT_IP_RULES,
      BEARER_TOKEN, CORS_ORIGINS,
    ),
  ),
  CardDef(
    id = CardId.AUTO_LAUNCH,
    titleRes = R.string.settings_card_auto_launch,
    icon = CardIcon.Vector(Icons.Outlined.PlayArrow),
    settings = listOf(
      DEFAULT_MODEL, START_ON_BOOT, KEEP_ALIVE, KEEP_ALIVE_TIMEOUT, DONTKILLMYAPP,
    ),
  ),
  CardDef(
    id = CardId.MODEL_BEHAVIOUR,
    titleRes = R.string.settings_card_model_behaviour,
    icon = CardIcon.Vector(Icons.Outlined.Token),
    settings = listOf(
      CUSTOM_PROMPTS, SCHEMA_INJECTION_TOOL_CALLING, REJECT_WHEN_BUSY, WARMUP_MESSAGE,
      PRE_INIT_VISION, AUDIO_GPU_ACCELERATION, IGNORE_CLIENT_PARAMS, STT_TRANSCRIPTION_PROMPT, STT_TRANSCRIPTION_PROMPT_TEXT,
    ),
  ),
  CardDef(
    id = CardId.CONTEXT_MANAGEMENT,
    titleRes = R.string.settings_card_context_management,
    icon = CardIcon.Vector(Icons.Outlined.Compress),
    settings = listOf(TRUNCATE_HISTORY, TRIM_PROMPT),
  ),
  CardDef(
    id = CardId.METRICS,
    titleRes = R.string.settings_card_metrics,
    icon = CardIcon.Vector(Icons.Outlined.BarChart),
    settings = listOf(SHOW_REQUEST_TYPES, SHOW_ADVANCED_METRICS, NOTIF_REQUEST_COUNT, FORCE_STREAM_USAGE),
  ),
  CardDef(
    id = CardId.LOG_PERSISTENCE,
    titleRes = R.string.settings_card_log_persistence,
    icon = CardIcon.Vector(Icons.Outlined.Storage),
    settings = listOf(LOG_PERSISTENCE_ENABLED, LOG_MAX_ENTRIES, LOG_AUTO_DELETE, CLEAR_ALL_LOGS),
  ),
  CardDef(
    id = CardId.HOME_ASSISTANT,
    titleRes = R.string.settings_card_home_assistant,
    icon = CardIcon.Resource(R.drawable.ic_home_assistant),
    settings = listOf(HA_INTEGRATION),
  ),
  CardDef(
    id = CardId.UPDATES,
    titleRes = R.string.settings_card_updates,
    icon = CardIcon.Vector(Icons.Outlined.SystemUpdate),
    settings = listOf(AUTO_UPDATE_CHECK, CHECK_FREQUENCY, CROSS_CHANNEL_NOTIFY, CHECK_FOR_UPDATES, NOTIFICATION_SETTINGS),
  ),
  CardDef(
    id = CardId.DEVELOPER,
    titleRes = R.string.settings_card_developer,
    icon = CardIcon.Vector(Icons.Outlined.BugReport),
    settings = listOf(VERBOSE_DEBUG, EXPORT_LOGCAT),
  ),
  CardDef(
    id = CardId.ADVANCED_SETTINGS,
    titleRes = R.string.settings_card_advanced_settings,
    icon = CardIcon.Vector(Icons.Outlined.Science),
    settings = listOf(
      DEFAULT_SEED, TIMEOUT_CHAT_COMPLETIONS, TIMEOUT_RESPONSES, TIMEOUT_STREAMING, TIMEOUT_BLOCKING,
      TIMEOUT_WARMUP, TIMEOUT_KEEP_ALIVE_RECHECK, TIMEOUT_CLEANUP_AWAIT,
    ),
  ),
  CardDef(
    id = CardId.RESET,
    titleRes = R.string.settings_reset_to_defaults,
    icon = CardIcon.Vector(Icons.Outlined.RestartAlt),
    settings = listOf(RESET_TO_DEFAULTS),
  ),
)

/** Lookup table: CardId → CardDef. */
val cardDefsById: Map<CardId, CardDef> = allCardDefs.associateBy { it.id }
