package com.ollitert.llm.server.ui.server.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.MAX_VALID_PORT
import com.ollitert.llm.server.data.MIN_VALID_PORT

// ─── General Card ───────────────────────────────────────────────────

val KEEP_SCREEN_AWAKE = SettingDef.Toggle(
  key = "keep_screen_awake",
  labelRes = R.string.settings_keep_screen_awake,
  descriptionRes = R.string.settings_keep_screen_awake_desc,
  card = CardId.GENERAL,
  searchKeywords = "Keep Screen Awake Prevent screen from turning off while app is open",
  default = true,
  resetDefault = false,
  prefsKey = "keep_screen_on",
)

val AUTO_EXPAND_LOGS = SettingDef.Toggle(
  key = "auto_expand_logs",
  labelRes = R.string.settings_auto_expand_logs,
  descriptionRes = R.string.settings_auto_expand_logs_desc,
  card = CardId.GENERAL,
  searchKeywords = "Auto-Expand Logs Show full request and response bodies in the Logs tab",
  default = false,
  prefsKey = "auto_expand_logs",
)

val STREAM_RESPONSE_PREVIEW = SettingDef.Toggle(
  key = "stream_response_preview",
  labelRes = R.string.settings_stream_response_preview,
  descriptionRes = R.string.settings_stream_response_preview_desc,
  card = CardId.GENERAL,
  searchKeywords = "Stream Response Preview Show model output as it generates in the Logs tab for streaming requests",
  default = true,
  prefsKey = "stream_logs_preview",
)

val COMPACT_IMAGE_DATA = SettingDef.Toggle(
  key = "compact_image_data",
  labelRes = R.string.settings_compact_image_data,
  descriptionRes = R.string.settings_compact_image_data_desc,
  card = CardId.GENERAL,
  searchKeywords = "Compact Image Data Replace base64 image data with size placeholder logs multimodal vision performance lag",
  default = true,
  prefsKey = "compact_image_data",
)

val HIDE_HEALTH_LOGS = SettingDef.Toggle(
  key = "hide_health_logs",
  labelRes = R.string.settings_hide_health_logs,
  descriptionRes = R.string.settings_hide_health_logs_desc,
  card = CardId.GENERAL,
  searchKeywords = "Hide Health Logs Suppress health check endpoint entries from Logs tab noise monitoring polling",
  default = false,
  prefsKey = "hide_health_logs",
)

val CLEAR_LOGS_ON_STOP = SettingDef.Toggle(
  key = "clear_logs_on_stop",
  labelRes = R.string.settings_clear_logs_on_stop,
  descriptionRes = R.string.settings_clear_logs_on_stop_desc,
  card = CardId.GENERAL,
  searchKeywords = "Clear Logs on Stop Automatically clear in-memory logs when the server stops",
  default = false,
  prefsKey = "clear_logs_on_stop",
)

val CONFIRM_CLEAR_LOGS = SettingDef.Toggle(
  key = "confirm_clear_logs",
  labelRes = R.string.settings_confirm_clear_logs,
  descriptionRes = R.string.settings_confirm_clear_logs_desc,
  card = CardId.GENERAL,
  searchKeywords = "Confirm Before Clearing Logs Show a confirmation dialog before clearing logs",
  default = true,
  prefsKey = "confirm_clear_logs",
)

val KEEP_PARTIAL_RESPONSE = SettingDef.Toggle(
  key = "keep_partial_response",
  labelRes = R.string.settings_keep_partial_response,
  descriptionRes = R.string.settings_keep_partial_response_desc,
  card = CardId.GENERAL,
  searchKeywords = "Keep Partial Response Preserve incomplete response text in logs when a streaming request is cancelled by the client",
  default = false,
  prefsKey = "keep_partial_response",
)

// ─── HF Token Card ────────────────────────────────────────────────────

val HF_TOKEN = SettingDef.TextInput(
  key = "hf_token",
  labelRes = R.string.settings_card_hf_token,
  descriptionRes = R.string.settings_hf_token_desc,
  card = CardId.HF_TOKEN,
  searchKeywords = "Hugging Face Token HuggingFace hf download models authentication required",
  default = "",
  prefsKey = "hf_token",
  isPassword = true,
)

// ─── Server Configuration Card ────────────────────────────────────

val HOST_PORT = SettingDef.NumericInput(
  key = "host_port",
  labelRes = R.string.settings_host_port_label,
  descriptionRes = R.string.settings_host_port_desc,
  card = CardId.SERVER_CONFIG,
  searchKeywords = "Host Port 1024 65535 server configuration default 8000 restart",
  default = 8000,
  prefsKey = "port",
  min = MIN_VALID_PORT,
  max = MAX_VALID_PORT,
)

val BEARER_TOKEN = SettingDef.Custom(
  key = "bearer_token",
  labelRes = R.string.settings_bearer_token,
  descriptionRes = R.string.settings_bearer_token_desc,
  card = CardId.SERVER_CONFIG,
  searchKeywords = "Require Bearer Token Protect API authentication Authorization header security",
)

val CORS_ORIGINS = SettingDef.TextInput(
  key = "cors_origins",
  labelRes = R.string.settings_cors_label,
  descriptionRes = R.string.settings_cors_desc,
  card = CardId.SERVER_CONFIG,
  searchKeywords = "CORS Allowed Origins cross-origin requests localhost",
  default = "*",
  resetDefault = "",
  prefsKey = "cors_allowed_origins",
  validate = { input ->
    if (!isValidCorsOrigins(input))
      "Invalid CORS origins — use *, blank, or comma-separated URLs with http(s)://"
    else null
  },
)

// ─── Auto-Launch & Behaviour Card ─────────────────────────────────

val DEFAULT_MODEL = SettingDef.Dropdown(
  key = "default_model",
  labelRes = R.string.settings_default_model_label,
  descriptionRes = R.string.settings_default_model_desc,
  card = CardId.AUTO_LAUNCH,
  searchKeywords = "Default Model Automatically load model when app launches",
  default = null,
  resetDefault = "",
  prefsKey = "default_model_name",
)

val START_ON_BOOT = SettingDef.Toggle(
  key = "start_on_boot",
  labelRes = R.string.settings_start_on_boot,
  descriptionRes = R.string.settings_start_on_boot_desc,
  card = CardId.AUTO_LAUNCH,
  searchKeywords = "Start on Boot Launch server automatically when device starts",
  default = false,
  prefsKey = "auto_start_on_boot",
)

val KEEP_ALIVE = SettingDef.Toggle(
  key = "keep_alive",
  labelRes = R.string.settings_keep_alive,
  descriptionRes = R.string.settings_keep_alive_desc,
  card = CardId.AUTO_LAUNCH,
  searchKeywords = "Keep Alive Unload model after idle timeout free RAM cold start Idle Timeout",
  default = false,
  prefsKey = "keep_alive_enabled",
)

val KEEP_ALIVE_TIMEOUT = SettingDef.NumericWithUnit(
  key = "keep_alive_timeout",
  labelRes = R.string.settings_idle_timeout_label,
  descriptionRes = R.string.settings_idle_timeout_desc,
  card = CardId.AUTO_LAUNCH,
  searchKeywords = "Keep Alive Unload model after idle timeout free RAM cold start Idle Timeout",
  defaultValue = 5L,
  defaultUnit = "minutes",
  prefsKey = "keep_alive_minutes",
  unitOptions = listOf("minutes", "hours"),
  toBaseUnit = { value, unit ->
    when (unit) {
      "hours" -> value * 60
      else -> value
    }
  },
  fromBaseUnit = { base ->
    when {
      base > 0 && base % 60 == 0L -> Pair(base / 60, "hours")
      else -> Pair(base, "minutes")
    }
  },
  min = 1,
  max = 7200,
  baseUnitLabel = "minutes",
)

val DONTKILLMYAPP = SettingDef.Custom(
  key = "dontkillmyapp",
  labelRes = R.string.settings_dontkillmyapp_title,
  descriptionRes = R.string.settings_dontkillmyapp_desc,
  card = CardId.AUTO_LAUNCH,
  searchKeywords = "Device background settings manufacturers kill background apps dontkillmyapp",
)

// ─── Updates Card ─────────────────────────────────────────────

val AUTO_UPDATE_CHECK = SettingDef.Toggle(
  key = "auto_update_check",
  labelRes = R.string.settings_auto_update_check,
  descriptionRes = R.string.settings_auto_update_check_desc,
  card = CardId.UPDATES,
  searchKeywords = "Check for Updates version new notification background frequency interval update available",
  default = true,
  prefsKey = "update_check_enabled",
)

val CHECK_FREQUENCY = SettingDef.NumericWithUnit(
  key = "check_frequency",
  labelRes = R.string.settings_check_frequency_label,
  descriptionRes = R.string.settings_check_frequency_desc,
  card = CardId.UPDATES,
  searchKeywords = "Check for Updates version new notification background frequency interval update available",
  defaultValue = 24L,
  defaultUnit = "hours",
  prefsKey = "update_check_interval_hours",
  unitOptions = listOf("hours", "days"),
  toBaseUnit = { value, unit ->
    when (unit) {
      "days" -> value * 24
      else -> value
    }
  },
  fromBaseUnit = { base ->
    when {
      base > 0 && base % 24 == 0L -> Pair(base / 24, "days")
      else -> Pair(base, "hours")
    }
  },
  min = 1,
  max = 720,
  baseUnitLabel = "hours",
)

val CHECK_FOR_UPDATES = SettingDef.Custom(
  key = "check_for_updates",
  labelRes = R.string.settings_check_for_updates,
  descriptionRes = R.string.settings_check_for_updates_desc,
  card = CardId.UPDATES,
  searchKeywords = "Check for Updates version new notification background frequency interval update available",
)

// ─── Metrics Card ─────────────────────────────────────────────────

val SHOW_REQUEST_TYPES = SettingDef.Toggle(
  key = "show_request_types",
  labelRes = R.string.settings_show_request_types,
  descriptionRes = R.string.settings_show_request_types_desc,
  card = CardId.METRICS,
  searchKeywords = "Show Request Types text vision audio request counts Status screen",
  default = false,
  prefsKey = "show_request_types",
)

val SHOW_ADVANCED_METRICS = SettingDef.Toggle(
  key = "show_advanced_metrics",
  labelRes = R.string.settings_show_advanced_metrics,
  descriptionRes = R.string.settings_show_advanced_metrics_desc,
  card = CardId.METRICS,
  searchKeywords = "Show Advanced Metrics prefill speed inter-token latency latency stats context utilization Status screen",
  default = false,
  prefsKey = "show_advanced_metrics",
)

// ─── Log Persistence Card ─────────────────────────────────────────

val LOG_PERSISTENCE_ENABLED = SettingDef.Toggle(
  key = "log_persistence_enabled",
  labelRes = R.string.settings_persist_logs,
  descriptionRes = R.string.settings_persist_logs_desc,
  card = CardId.LOG_PERSISTENCE,
  searchKeywords = "Log Persistence Persist Logs Database Maximum Log Entries Auto-Delete Clear All Logs storage oldest entries pruned survive app restarts",
  default = false,
  prefsKey = "log_persistence_enabled",
)

val LOG_MAX_ENTRIES = SettingDef.NumericPlain(
  key = "log_max_entries",
  labelRes = R.string.settings_max_log_entries_label,
  descriptionRes = R.string.settings_max_log_entries_desc,
  card = CardId.LOG_PERSISTENCE,
  searchKeywords = "Log Persistence Persist Logs Database Maximum Log Entries Auto-Delete Clear All Logs storage oldest entries pruned survive app restarts",
  default = 500,
  prefsKey = "log_max_entries",
  min = 0,
  max = 99999,
)

val LOG_AUTO_DELETE = SettingDef.NumericWithUnit(
  key = "log_auto_delete",
  labelRes = R.string.settings_auto_delete_label,
  descriptionRes = R.string.settings_auto_delete_desc,
  card = CardId.LOG_PERSISTENCE,
  searchKeywords = "Log Persistence Persist Logs Database Maximum Log Entries Auto-Delete Clear All Logs storage oldest entries pruned survive app restarts",
  defaultValue = 10080L,
  defaultUnit = "days",
  prefsKey = "log_auto_delete_minutes",
  unitOptions = listOf("minutes", "hours", "days"),
  toBaseUnit = { value, unit ->
    when (unit) {
      "hours" -> value * 60
      "days" -> value * 24 * 60
      else -> value
    }
  },
  fromBaseUnit = { base ->
    when {
      base == 0L -> Pair(0L, "minutes")
      base % (24 * 60) == 0L -> Pair(base / (24 * 60), "days")
      base % 60 == 0L -> Pair(base / 60, "hours")
      else -> Pair(base, "minutes")
    }
  },
  min = 0,
  max = 525600,
  baseUnitLabel = "minutes",
)

val CLEAR_ALL_LOGS = SettingDef.Custom(
  key = "clear_all_logs",
  labelRes = R.string.settings_clear_all_logs_button,
  descriptionRes = R.string.settings_clear_all_logs_desc,
  card = CardId.LOG_PERSISTENCE,
  searchKeywords = "Log Persistence Persist Logs Database Maximum Log Entries Auto-Delete Clear All Logs storage oldest entries pruned survive app restarts",
)

// ─── Home Assistant Card ──────────────────────────────────────────────

val HA_INTEGRATION = SettingDef.Custom(
  key = "ha_integration",
  labelRes = R.string.settings_ha_rest_api,
  descriptionRes = R.string.settings_ha_rest_api_desc,
  card = CardId.HOME_ASSISTANT,
  searchKeywords = "Home Assistant REST API Integration configuration yaml sensors commands stop reload thinking config",
)

// ─── Context Management Card ───────────────────────────────────────────────

val TRUNCATE_HISTORY = SettingDef.Toggle(
  key = "truncate_history",
  labelRes = R.string.settings_truncate_history,
  descriptionRes = R.string.settings_truncate_history_desc,
  card = CardId.CONTEXT_MANAGEMENT,
  searchKeywords = "Truncate Conversation History request exceeds context window drop older messages system prompts",
  default = false,
  resetDefault = true,
  prefsKey = "auto_truncate_history",
)

val COMPACT_TOOL_SCHEMAS = SettingDef.Toggle(
  key = "compact_tool_schemas",
  labelRes = R.string.settings_compact_tool_schemas,
  descriptionRes = R.string.settings_compact_tool_schemas_desc,
  card = CardId.CONTEXT_MANAGEMENT,
  searchKeywords = "Compact Tool Schemas reduce tool schemas names descriptions context window Home Assistant tool definitions",
  default = false,
  resetDefault = true,
  prefsKey = "compact_tool_schemas",
)

val TRIM_PROMPT = SettingDef.Toggle(
  key = "trim_prompt",
  labelRes = R.string.settings_trim_prompt,
  descriptionRes = R.string.settings_trim_prompt_desc,
  card = CardId.CONTEXT_MANAGEMENT,
  searchKeywords = "Trim Prompt last resort hard-cuts prompt fit context window recent content discarding beginning",
  default = false,
  prefsKey = "auto_trim_prompts",
)

// ─── Advanced Card ─────────────────────────────────────────────────────────

val WARMUP_MESSAGE = SettingDef.Toggle(
  key = "warmup_message",
  labelRes = R.string.settings_warmup_message,
  descriptionRes = R.string.settings_warmup_message_desc,
  card = CardId.ADVANCED,
  searchKeywords = "Warmup Message Send test message when model loads verify engine working startup",
  default = true,
  prefsKey = "warmup_enabled",
)

val PRE_INIT_VISION = SettingDef.Toggle(
  key = "pre_init_vision",
  labelRes = R.string.settings_pre_init_vision,
  descriptionRes = R.string.settings_pre_init_vision_desc,
  card = CardId.ADVANCED,
  searchKeywords = "Pre-initialize Vision Load vision backend multimodal model starts image request memory GPU",
  default = false,
  prefsKey = "eager_vision_init",
  requiresRestart = true,
)

val CUSTOM_PROMPTS = SettingDef.Toggle(
  key = "custom_prompts",
  labelRes = R.string.settings_custom_prompts,
  descriptionRes = R.string.settings_custom_prompts_desc,
  card = CardId.ADVANCED,
  searchKeywords = "Custom System Prompt per-model prompt instruction Inference Settings",
  default = false,
  prefsKey = "custom_prompts_enabled",
)

val IGNORE_CLIENT_PARAMS = SettingDef.Toggle(
  key = "ignore_client_params",
  labelRes = R.string.settings_ignore_client_params,
  descriptionRes = R.string.settings_ignore_client_params_desc,
  card = CardId.ADVANCED,
  searchKeywords = "Ignore Client Sampler Parameters Discard temperature top_p top_k max_tokens API clients server Inference Settings",
  default = false,
  prefsKey = "ignore_client_sampler_params",
)

// ─── Developer Card ───────────────────────────────────────────────

val VERBOSE_DEBUG = SettingDef.Toggle(
  key = "verbose_debug",
  labelRes = R.string.settings_verbose_debug,
  descriptionRes = R.string.settings_verbose_debug_desc,
  card = CardId.DEVELOPER,
  searchKeywords = "Verbose Debug Mode Logs additional details stack traces memory snapshots model config per-request timing performance",
  default = false,
  prefsKey = "verbose_debug_enabled",
)

val EXPORT_LOGCAT = SettingDef.Custom(
  key = "export_logcat",
  labelRes = R.string.settings_export_logcat,
  descriptionRes = R.string.settings_export_logcat_desc,
  card = CardId.DEVELOPER,
  searchKeywords = "Export Debug Logs logcat share system log buffer diagnose issues",
)

// ─── Reset Section ────────────────────────────────────────────────────

val RESET_TO_DEFAULTS = SettingDef.Custom(
  key = "reset",
  labelRes = R.string.settings_reset_to_defaults,
  descriptionRes = R.string.settings_reset_to_defaults,
  card = CardId.RESET,
  searchKeywords = "Reset to Defaults reset all settings port token inference",
)

// ─── All Setting Definitions (ordered) ──────────────────────────────────────

val allSettingDefs: List<SettingDef> = listOf(
  // General
  KEEP_SCREEN_AWAKE, AUTO_EXPAND_LOGS, STREAM_RESPONSE_PREVIEW, COMPACT_IMAGE_DATA,
  HIDE_HEALTH_LOGS, CLEAR_LOGS_ON_STOP, CONFIRM_CLEAR_LOGS, KEEP_PARTIAL_RESPONSE,
  // HF Token
  HF_TOKEN,
  // Server Config
  HOST_PORT, BEARER_TOKEN, CORS_ORIGINS,
  // Auto-Launch
  DEFAULT_MODEL, START_ON_BOOT, KEEP_ALIVE, KEEP_ALIVE_TIMEOUT, DONTKILLMYAPP,
  // Context Management
  TRUNCATE_HISTORY, COMPACT_TOOL_SCHEMAS, TRIM_PROMPT,
  // Metrics
  SHOW_REQUEST_TYPES, SHOW_ADVANCED_METRICS,
  // Log Persistence
  LOG_PERSISTENCE_ENABLED, LOG_MAX_ENTRIES, LOG_AUTO_DELETE, CLEAR_ALL_LOGS,
  // Home Assistant
  HA_INTEGRATION,
  // Advanced
  WARMUP_MESSAGE, PRE_INIT_VISION, CUSTOM_PROMPTS, IGNORE_CLIENT_PARAMS,
  // Updates
  AUTO_UPDATE_CHECK, CHECK_FREQUENCY, CHECK_FOR_UPDATES,
  // Developer
  VERBOSE_DEBUG, EXPORT_LOGCAT,
  // Reset
  RESET_TO_DEFAULTS,
)

/** Lookup table: setting key → SettingDef. */
val settingDefsByKey: Map<String, SettingDef> = allSettingDefs.associateBy { it.key }

// ─── Card Definitions ───────────────────────────────────────────────────────

val allCardDefs: List<CardDef> = listOf(
  CardDef(
    id = CardId.GENERAL,
    titleRes = R.string.settings_card_general,
    icon = CardIcon.Vector(Icons.Outlined.PhoneAndroid),
    settings = listOf(
      KEEP_SCREEN_AWAKE, AUTO_EXPAND_LOGS, STREAM_RESPONSE_PREVIEW, COMPACT_IMAGE_DATA,
      HIDE_HEALTH_LOGS, CLEAR_LOGS_ON_STOP, CONFIRM_CLEAR_LOGS, KEEP_PARTIAL_RESPONSE,
    ),
  ),
  CardDef(
    id = CardId.HF_TOKEN,
    titleRes = R.string.settings_card_hf_token,
    icon = CardIcon.Vector(Icons.Outlined.Key),
    settings = listOf(HF_TOKEN),
  ),
  CardDef(
    id = CardId.SERVER_CONFIG,
    titleRes = R.string.settings_card_server_config,
    icon = CardIcon.Vector(Icons.Outlined.Tune),
    settings = listOf(HOST_PORT, BEARER_TOKEN, CORS_ORIGINS),
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
    id = CardId.CONTEXT_MANAGEMENT,
    titleRes = R.string.settings_card_context_management,
    icon = CardIcon.Vector(Icons.Outlined.Compress),
    settings = listOf(TRUNCATE_HISTORY, COMPACT_TOOL_SCHEMAS, TRIM_PROMPT),
  ),
  CardDef(
    id = CardId.METRICS,
    titleRes = R.string.settings_card_metrics,
    icon = CardIcon.Vector(Icons.Outlined.BarChart),
    settings = listOf(SHOW_REQUEST_TYPES, SHOW_ADVANCED_METRICS),
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
    settings = listOf(AUTO_UPDATE_CHECK, CHECK_FREQUENCY, CHECK_FOR_UPDATES),
  ),
  CardDef(
    id = CardId.ADVANCED,
    titleRes = R.string.settings_card_advanced,
    icon = CardIcon.Vector(Icons.Outlined.Science),
    settings = listOf(WARMUP_MESSAGE, PRE_INIT_VISION, CUSTOM_PROMPTS, IGNORE_CLIENT_PARAMS),
  ),
  CardDef(
    id = CardId.DEVELOPER,
    titleRes = R.string.settings_card_developer,
    icon = CardIcon.Vector(Icons.Outlined.BugReport),
    settings = listOf(VERBOSE_DEBUG, EXPORT_LOGCAT),
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
