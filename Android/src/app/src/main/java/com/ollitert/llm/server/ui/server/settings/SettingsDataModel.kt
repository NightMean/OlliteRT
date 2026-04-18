package com.ollitert.llm.server.ui.server.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Reactive state holder for a single setting. Tracks both the persisted (saved)
 * value and the current (editable) value, enabling change detection, revert,
 * and apply operations.
 *
 * Both [saved] and [current] use Compose [mutableStateOf] so that Compose
 * detects changes to either — required because [isChanged] reads both, and
 * [apply] updates [saved] without touching [current].
 */
class SettingEntry<T>(initialValue: T) {
  var saved by mutableStateOf(initialValue)
    private set
  var current by mutableStateOf(initialValue)

  val isChanged: Boolean get() = saved != current

  fun update(value: T) { current = value }
  fun revert() { current = saved }
  fun apply() { saved = current }
  fun reset(default: T) { saved = default; current = default }
}

/** Identifies which settings card a setting belongs to. */
enum class CardId {
  GENERAL,
  HF_TOKEN,
  SERVER_CONFIG,
  AUTO_LAUNCH,
  METRICS,
  LOG_PERSISTENCE,
  HOME_ASSISTANT,
  ADVANCED,
  DEVELOPER,
  RESET,
}

/**
 * Sealed class hierarchy describing every setting's metadata. Each subclass
 * corresponds to a control type (toggle, text input, etc.).
 *
 * Settings with intentionally different fresh-install vs factory-reset defaults
 * specify both [default] and [resetDefault]. For the majority where they match,
 * [resetDefault] defaults to [default].
 */
sealed class SettingDef(
  val key: String,
  val labelRes: Int,
  val descriptionRes: Int,
  val card: CardId,
  val searchKeywords: String,
) {
  class Toggle(
    key: String,
    labelRes: Int,
    descriptionRes: Int,
    card: CardId,
    searchKeywords: String,
    val default: Boolean,
    val resetDefault: Boolean = default,
    val prefsKey: String,
    val requiresRestart: Boolean = false,
  ) : SettingDef(key, labelRes, descriptionRes, card, searchKeywords)

  class TextInput(
    key: String,
    labelRes: Int,
    descriptionRes: Int,
    card: CardId,
    searchKeywords: String,
    val default: String,
    val resetDefault: String = default,
    val prefsKey: String,
    val isPassword: Boolean = false,
  ) : SettingDef(key, labelRes, descriptionRes, card, searchKeywords)

  class NumericInput(
    key: String,
    labelRes: Int,
    descriptionRes: Int,
    card: CardId,
    searchKeywords: String,
    val default: Int,
    val prefsKey: String,
    val min: Int,
    val max: Int,
    val maxLength: Int = 5,
  ) : SettingDef(key, labelRes, descriptionRes, card, searchKeywords)

  class NumericWithUnit(
    key: String,
    labelRes: Int,
    descriptionRes: Int,
    card: CardId,
    searchKeywords: String,
    val defaultValue: Long,
    val defaultUnit: String,
    val prefsKey: String,
    val unitOptions: List<String>,
    val toBaseUnit: (value: Long, unit: String) -> Long,
    val fromBaseUnit: (base: Long) -> Pair<Long, String>,
    val min: Long,
    val max: Long,
  ) : SettingDef(key, labelRes, descriptionRes, card, searchKeywords)

  class NumericPlain(
    key: String,
    labelRes: Int,
    descriptionRes: Int,
    card: CardId,
    searchKeywords: String,
    val default: Int,
    val prefsKey: String,
    val min: Int,
    val max: Int,
  ) : SettingDef(key, labelRes, descriptionRes, card, searchKeywords)

  class Dropdown(
    key: String,
    labelRes: Int,
    descriptionRes: Int,
    card: CardId,
    searchKeywords: String,
    val default: String?,
    val resetDefault: String? = default,
    val prefsKey: String,
  ) : SettingDef(key, labelRes, descriptionRes, card, searchKeywords)

  /** Settings with custom renderers (bearer token, HA, action buttons, external links). */
  class Custom(
    key: String,
    labelRes: Int,
    descriptionRes: Int,
    card: CardId,
    searchKeywords: String,
  ) : SettingDef(key, labelRes, descriptionRes, card, searchKeywords)
}

/** Describes a settings card: its identity, display metadata, and ordered list of settings. */
data class CardDef(
  val id: CardId,
  val titleRes: Int,
  val icon: ImageVector,
  val settings: List<SettingDef>,
)
