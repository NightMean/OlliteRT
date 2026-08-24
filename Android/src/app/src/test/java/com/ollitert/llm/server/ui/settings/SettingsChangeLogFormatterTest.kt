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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [SettingsChangeLogFormatter] — pure formatting rules for the
 * Settings save change-log. These strings land in diagnostic logs, so their exact
 * shape is part of the contract (grep-able across locales).
 */
class SettingsChangeLogFormatterTest {

  private val card = CardId.GENERAL

  private fun toggleDef() = SettingDef.Toggle(
    key = "k", labelRes = 1, descriptionRes = 1, card = card, default = false,
    prefsKey = "pk", read = { false }, write = { _, _ -> },
  )

  private fun textDef(isPassword: Boolean = false) = SettingDef.TextInput(
    key = "k", labelRes = 1, descriptionRes = 1, card = card, default = "",
    prefsKey = "pk", isPassword = isPassword, read = { "" }, write = { _, _ -> },
  )

  private fun numericDef(min: Int = 0, max: Int = 100) = SettingDef.NumericInput(
    key = "k", labelRes = 1, descriptionRes = 1, card = card, default = 0,
    prefsKey = "pk", min = min, max = max, read = { 0 }, write = { _, _ -> },
  )

  private fun dropdownDef() = SettingDef.Dropdown(
    key = "k", labelRes = 1, descriptionRes = 1, card = card, default = null,
    prefsKey = "pk", read = { null }, write = { _, _ -> },
  )

  private fun numericWithUnitDef(fromBase: (Long) -> Pair<Long, String>) =
    SettingDef.NumericWithUnit(
      key = "k", labelRes = 1, descriptionRes = 1, card = card,
      defaultValue = 0L, defaultUnit = "minutes", prefsKey = "pk",
      unitOptions = listOf("minutes"), toBaseUnit = { v, _ -> v }, fromBaseUnit = fromBase,
      min = 0L, max = Long.MAX_VALUE, baseUnitLabel = "minutes",
      read = { 0L }, write = { _, _ -> },
    )

  @Test
  fun toggleChangeFormatsSavedAndCurrent() {
    val entry = SettingEntry(false)
    entry.update(true)
    assertEquals("Keep alive: disabled → enabled",
      SettingsChangeLogFormatter.formatChange("Keep alive", toggleDef(), entry))
  }

  @Test
  fun plainTextInputFormatsBlankAsDisabled() {
    val entry = SettingEntry("old")
    entry.update("")
    assertEquals("Prompt: old → disabled",
      SettingsChangeLogFormatter.formatChange("Prompt", textDef(), entry))
  }

  @Test
  fun passwordInputNeverLogsValues() {
    val entry = SettingEntry("secret-old")
    entry.update("secret-new")
    assertEquals("Token: changed",
      SettingsChangeLogFormatter.formatChange("Token", textDef(isPassword = true), entry))
  }

  @Test
  fun numericInputChangeFormatsValues() {
    val entry = SettingEntry(5)
    entry.update(9)
    assertEquals("Count: 5 → 9",
      SettingsChangeLogFormatter.formatChange("Count", numericDef(), entry))
  }

  @Test
  fun dropdownChangeFormatsNullAsNone() {
    val entry = SettingEntry<String?>(null)
    entry.update("gpu")
    assertEquals("Accelerator: none → gpu",
      SettingsChangeLogFormatter.formatChange("Accelerator", dropdownDef(), entry))
  }

  @Test
  fun customDefYieldsNoLogLine() {
    val def = SettingDef.Custom(key = "k", labelRes = 1, descriptionRes = 1, card = card)
    assertNull(SettingsChangeLogFormatter.formatChange("X", def, SettingEntry(0)))
  }

  @Test
  fun numericWithUnitUsesSingularForOne() {
    // Base unit is seconds; definition converts to whole minutes for display.
    val def = numericWithUnitDef(fromBase = { it / 60L to "minutes" })
    val entry = SettingEntry(60L)
    entry.update(0L)
    assertEquals("Timeout: 1 minute → disabled",
      SettingsChangeLogFormatter.formatChange("Timeout", def, entry))
  }

  @Test
  fun numericWithUnitPluralizesAboveOne() {
    val def = numericWithUnitDef(fromBase = { it / 60L to "minutes" })
    val entry = SettingEntry(120L)
    entry.update(240L)
    assertEquals("Timeout: 2 minutes → 4 minutes",
      SettingsChangeLogFormatter.formatChange("Timeout", def, entry))
  }

  @Test
  fun countIpRulesCountsCommaAndNewlineSeparatedNonBlankRules() {
    assertEquals(4, SettingsChangeLogFormatter.countIpRules("a,b\n c ,, \r\nd"))
    assertEquals(0, SettingsChangeLogFormatter.countIpRules(",,"))
  }

  @Test
  fun fmtToggleUsesStableLowercaseWords() {
    assertEquals("enabled", SettingsChangeLogFormatter.fmtToggle(true))
    assertEquals("disabled", SettingsChangeLogFormatter.fmtToggle(false))
  }
}
