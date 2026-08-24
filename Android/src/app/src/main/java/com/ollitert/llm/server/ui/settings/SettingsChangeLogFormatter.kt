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

/**
 * Pure formatter for settings change-log entries written to the Logs tab when the
 * user saves Settings. Extracted from [SettingsViewModel] so formatting rules are
 * unit-testable without Android or ViewModel state.
 *
 * Log event text is intentionally English-only — these are diagnostic messages for
 * the Logs tab, not user-facing UI strings. They must be stable and grep-able
 * across locales.
 */
internal object SettingsChangeLogFormatter {

  /**
   * Formats a single setting's old→new change for the log entry.
   * The caller resolves the localized [label]; everything else is derived from the
   * definition and entry values only.
   */
  fun formatChange(label: String, def: SettingDef, entry: SettingEntry<*>): String? = when (def) {
    is SettingDef.Toggle -> {
      @Suppress("UNCHECKED_CAST")
      val e = entry as SettingEntry<Boolean>
      "$label: ${fmtToggle(e.saved)} → ${fmtToggle(e.current)}"
    }
    is SettingDef.TextInput -> {
      @Suppress("UNCHECKED_CAST")
      val e = entry as SettingEntry<String>
      if (def.isPassword) "$label: changed" // sensitive — don't log value
      else "$label: ${e.saved.ifBlank { "disabled" }} → ${e.current.ifBlank { "disabled" }}"
    }
    is SettingDef.NumericInput -> {
      @Suppress("UNCHECKED_CAST")
      val e = entry as SettingEntry<Int>
      "$label: ${e.saved} → ${e.current}"
    }
    is SettingDef.NumericWithUnit -> formatNumericWithUnitChange(def, entry, label)
    is SettingDef.NumericPlain -> {
      @Suppress("UNCHECKED_CAST")
      val e = entry as SettingEntry<Int>
      "$label: ${e.saved} → ${e.current}"
    }
    is SettingDef.Dropdown -> {
      @Suppress("UNCHECKED_CAST")
      val e = entry as SettingEntry<String?>
      "$label: ${e.saved ?: "none"} → ${e.current ?: "none"}"
    }
    is SettingDef.Custom -> null
  }

  /** Formats NumericWithUnit changes using the definition's unit conversion. */
  private fun formatNumericWithUnitChange(
    def: SettingDef.NumericWithUnit,
    entry: SettingEntry<*>,
    label: String,
  ): String {
    fun fmt(base: Long): String {
      if (base == 0L) return "disabled"
      val (value, unit) = def.fromBaseUnit(base)
      val singular = unit.removeSuffix("s")
      val display = if (value == 1L) singular else unit
      return "$value $display"
    }
    val e = entry as SettingEntry<Long>
    return "$label: ${fmt(e.saved)} → ${fmt(e.current)}"
  }

  internal fun fmtToggle(enabled: Boolean) = if (enabled) "enabled" else "disabled"

  /** Counts non-blank rules in a client-IP rules text block (comma/newline separated). */
  internal fun countIpRules(rulesText: String): Int = rulesText
    .split(Regex("[,\\r\\n]+"))
    .count { it.isNotBlank() }
}
