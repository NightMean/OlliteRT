package com.ollitert.llm.server.ui.server.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDefsTest {

  @Test
  fun `every CardId has a card definition`() {
    val definedIds = allCardDefs.map { it.id }.toSet()
    for (id in CardId.entries) {
      assertTrue("CardId.$id has no matching CardDef in allCardDefs", id in definedIds)
    }
    assertEquals("allCardDefs has entries not in CardId enum",
      CardId.entries.size, allCardDefs.size)
  }

  @Test
  fun `card ordering matches CardId enum order`() {
    assertEquals(CardId.entries.toList(), allCardDefs.map { it.id })
  }

  @Test
  fun `all setting keys are unique`() {
    val keys = allSettingDefs.map { it.key }
    assertEquals("Duplicate keys found: ${keys.groupBy { it }.filter { it.value.size > 1 }.keys}",
      keys.size, keys.toSet().size)
  }

  @Test
  fun `every setting appears in exactly one card`() {
    val settingsInCards = allCardDefs.flatMap { card -> card.settings.map { it.key } }
    val allKeys = allSettingDefs.map { it.key }

    assertEquals("Settings count in cards doesn't match total definitions",
      allKeys.size, settingsInCards.size)

    val settingsInCardsSet = settingsInCards.toSet()
    assertEquals("Duplicate settings across cards",
      settingsInCards.size, settingsInCardsSet.size)

    for (key in allKeys) {
      assertTrue("Setting '$key' not found in any card", key in settingsInCardsSet)
    }
  }

  @Test
  fun `allSettingDefs contains same settings as flattened cards`() {
    val fromCards = allCardDefs.flatMap { it.settings }.toSet()
    val allDefs = allSettingDefs.toSet()
    assertEquals("allSettingDefs and flattened card settings should contain the same entries",
      fromCards, allDefs)
  }

  @Test
  fun `settingDefsByKey contains all settings`() {
    assertEquals(allSettingDefs.size, settingDefsByKey.size)
    for (def in allSettingDefs) {
      assertEquals(def, settingDefsByKey[def.key])
    }
  }

  @Test
  fun `card IDs are unique`() {
    val ids = allCardDefs.map { it.id }
    assertEquals(ids.size, ids.toSet().size)
  }

  @Test
  fun `setting subtype counts sum to total`() {
    val toggles = allSettingDefs.filterIsInstance<SettingDef.Toggle>()
    val textInputs = allSettingDefs.filterIsInstance<SettingDef.TextInput>()
    val dropdowns = allSettingDefs.filterIsInstance<SettingDef.Dropdown>()
    val customs = allSettingDefs.filterIsInstance<SettingDef.Custom>()
    val numericUnits = allSettingDefs.filterIsInstance<SettingDef.NumericWithUnit>()
    val numericInputs = allSettingDefs.filterIsInstance<SettingDef.NumericInput>()
    val numericPlains = allSettingDefs.filterIsInstance<SettingDef.NumericPlain>()

    assertEquals("Subtype counts don't sum to total",
      allSettingDefs.size,
      toggles.size + textInputs.size + dropdowns.size + customs.size +
        numericUnits.size + numericInputs.size + numericPlains.size)
  }

  @Test
  fun `every setting card field matches the card it belongs to`() {
    for (cardDef in allCardDefs) {
      for (setting in cardDef.settings) {
        assertEquals("Setting '${setting.key}' has card=${setting.card} but is listed in CardId.${cardDef.id}",
          cardDef.id, setting.card)
      }
    }
  }

  @Test
  fun `toggle settings with different fresh and reset defaults`() {
    val dualDefaults = allSettingDefs.filterIsInstance<SettingDef.Toggle>()
      .filter { it.default != it.resetDefault }
    assertTrue("Expected at least 1 toggle with dual defaults", dualDefaults.isNotEmpty())
    for (def in dualDefaults) {
      assertTrue("Toggle '${def.key}' has dual defaults but is not a known case",
        def.key in setOf("keep_screen_awake", "truncate_history", "compact_tool_schemas"))
    }
  }

  @Test
  fun `text inputs with different fresh and reset defaults`() {
    val dualDefaults = allSettingDefs.filterIsInstance<SettingDef.TextInput>()
      .filter { it.default != it.resetDefault }
    for (def in dualDefaults) {
      assertTrue("TextInput '${def.key}' has dual defaults but is not a known case",
        def.key in setOf("cors_origins"))
    }
  }

  @Test
  fun `dropdown with different fresh and reset defaults`() {
    val dualDefaults = allSettingDefs.filterIsInstance<SettingDef.Dropdown>()
      .filter { it.default != it.resetDefault }
    for (def in dualDefaults) {
      assertTrue("Dropdown '${def.key}' has dual defaults but is not a known case",
        def.key in setOf("default_model"))
    }
  }

  @Test
  fun `keep alive toBaseUnit converts hours to minutes correctly`() {
    assertEquals(300L, KEEP_ALIVE_TIMEOUT.toBaseUnit(5L, "hours"))
    assertEquals(5L, KEEP_ALIVE_TIMEOUT.toBaseUnit(5L, "minutes"))
  }

  @Test
  fun `keep alive fromBaseUnit converts minutes to hours when evenly divisible`() {
    assertEquals(Pair(2L, "hours"), KEEP_ALIVE_TIMEOUT.fromBaseUnit(120L))
    assertEquals(Pair(90L, "minutes"), KEEP_ALIVE_TIMEOUT.fromBaseUnit(90L))
  }

  @Test
  fun `check frequency toBaseUnit converts days to hours correctly`() {
    assertEquals(168L, CHECK_FREQUENCY.toBaseUnit(7L, "days"))
    assertEquals(7L, CHECK_FREQUENCY.toBaseUnit(7L, "hours"))
  }

  @Test
  fun `log auto delete toBaseUnit converts all units correctly`() {
    assertEquals(10080L, LOG_AUTO_DELETE.toBaseUnit(7L, "days"))
    assertEquals(420L, LOG_AUTO_DELETE.toBaseUnit(7L, "hours"))
    assertEquals(7L, LOG_AUTO_DELETE.toBaseUnit(7L, "minutes"))
  }

  @Test
  fun `log auto delete fromBaseUnit selects largest unit`() {
    assertEquals(Pair(7L, "days"), LOG_AUTO_DELETE.fromBaseUnit(10080L))
    assertEquals(Pair(2L, "hours"), LOG_AUTO_DELETE.fromBaseUnit(120L))
    assertEquals(Pair(90L, "minutes"), LOG_AUTO_DELETE.fromBaseUnit(90L))
    assertEquals(Pair(0L, "minutes"), LOG_AUTO_DELETE.fromBaseUnit(0L))
  }
}
