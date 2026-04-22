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

package com.ollitert.llm.server.ui.server.settings

import android.content.Context
import androidx.annotation.DrawableRes
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
  fun apply() { saved = current }
  fun reset(default: T) { saved = default; current = default }
}

/** Identifies which settings card a setting belongs to. */
enum class CardId {
  GENERAL,
  HF_TOKEN,
  SERVER_CONFIG,
  AUTO_LAUNCH,
  CONTEXT_MANAGEMENT,
  METRICS,
  LOG_PERSISTENCE,
  HOME_ASSISTANT,
  UPDATES,
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
) {
  class Toggle(
    key: String,
    labelRes: Int,
    descriptionRes: Int,
    card: CardId,
    val default: Boolean,
    val resetDefault: Boolean = default,
    val prefsKey: String,
    val requiresRestart: Boolean = false,
    val read: (Context) -> Boolean,
    val write: (Context, Boolean) -> Unit,
  ) : SettingDef(key, labelRes, descriptionRes, card)

  class TextInput(
    key: String,
    labelRes: Int,
    descriptionRes: Int,
    card: CardId,
    val default: String,
    val resetDefault: String = default,
    val prefsKey: String,
    val isPassword: Boolean = false,
    val validate: ((String) -> String?)? = null,
    val read: (Context) -> String,
    val write: (Context, String) -> Unit,
  ) : SettingDef(key, labelRes, descriptionRes, card)

  class NumericInput(
    key: String,
    labelRes: Int,
    descriptionRes: Int,
    card: CardId,
    val default: Int,
    val prefsKey: String,
    val min: Int,
    val max: Int,
    val maxLength: Int = 5,
    val read: (Context) -> Int,
    val write: (Context, Int) -> Unit,
  ) : SettingDef(key, labelRes, descriptionRes, card)

  class NumericWithUnit(
    key: String,
    labelRes: Int,
    descriptionRes: Int,
    card: CardId,
    val defaultValue: Long,
    val defaultUnit: String,
    val prefsKey: String,
    val unitOptions: List<String>,
    val toBaseUnit: (value: Long, unit: String) -> Long,
    val fromBaseUnit: (base: Long) -> Pair<Long, String>,
    val min: Long,
    val max: Long,
    val baseUnitLabel: String,
    val read: (Context) -> Long,
    val write: (Context, Long) -> Unit,
  ) : SettingDef(key, labelRes, descriptionRes, card)

  class NumericPlain(
    key: String,
    labelRes: Int,
    descriptionRes: Int,
    card: CardId,
    val default: Int,
    val prefsKey: String,
    val min: Int,
    val max: Int,
    val read: (Context) -> Int,
    val write: (Context, Int) -> Unit,
  ) : SettingDef(key, labelRes, descriptionRes, card)

  class Dropdown(
    key: String,
    labelRes: Int,
    descriptionRes: Int,
    card: CardId,
    val default: String?,
    val resetDefault: String? = default,
    val prefsKey: String,
    val read: (Context) -> String?,
    val write: (Context, String?) -> Unit,
  ) : SettingDef(key, labelRes, descriptionRes, card)

  /** Settings with custom renderers (bearer token, HA, action buttons, external links). */
  class Custom(
    key: String,
    labelRes: Int,
    descriptionRes: Int,
    card: CardId,
  ) : SettingDef(key, labelRes, descriptionRes, card)
}

/** Icon source for a settings card — either a vector icon or a drawable resource. */
sealed class CardIcon {
  data class Vector(val icon: ImageVector) : CardIcon()
  data class Resource(@DrawableRes val resId: Int) : CardIcon()
}

/** Describes a settings card: its identity, display metadata, and ordered list of settings. */
data class CardDef(
  val id: CardId,
  val titleRes: Int,
  val icon: CardIcon,
  val settings: List<SettingDef>,
)
