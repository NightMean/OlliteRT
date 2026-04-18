package com.ollitert.llm.server.ui.server.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDefsTest {

  @Test
  fun `total setting definitions count is 37`() {
    assertEquals(37, allSettingDefs.size)
  }

  @Test
  fun `total card definitions count is 11`() {
    assertEquals(11, allCardDefs.size)
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
  fun `card ordering matches CardId enum order`() {
    val expectedOrder = listOf(
      CardId.GENERAL, CardId.HF_TOKEN, CardId.SERVER_CONFIG, CardId.AUTO_LAUNCH,
      CardId.METRICS, CardId.LOG_PERSISTENCE, CardId.HOME_ASSISTANT,
      CardId.ADVANCED, CardId.UPDATES, CardId.DEVELOPER, CardId.RESET,
    )
    assertEquals(expectedOrder, allCardDefs.map { it.id })
  }

  @Test
  fun `toggle settings with different fresh and reset defaults`() {
    val dualDefaults = allSettingDefs.filterIsInstance<SettingDef.Toggle>()
      .filter { it.default != it.resetDefault }
    assertEquals("Expected 3 toggles with dual defaults (keepScreenAwake, truncateHistory, compactToolSchemas)",
      3, dualDefaults.size)
    assertTrue(dualDefaults.any { it.key == "keep_screen_awake" })
    assertTrue(dualDefaults.any { it.key == "truncate_history" })
    assertTrue(dualDefaults.any { it.key == "compact_tool_schemas" })
  }

  @Test
  fun `text inputs with different fresh and reset defaults`() {
    val dualDefaults = allSettingDefs.filterIsInstance<SettingDef.TextInput>()
      .filter { it.default != it.resetDefault }
    assertEquals("Expected 1 text input with dual defaults (cors_origins)",
      1, dualDefaults.size)
    assertEquals("cors_origins", dualDefaults[0].key)
  }

  @Test
  fun `dropdown with different fresh and reset defaults`() {
    val dualDefaults = allSettingDefs.filterIsInstance<SettingDef.Dropdown>()
      .filter { it.default != it.resetDefault }
    assertEquals("Expected 1 dropdown with dual defaults (default_model)",
      1, dualDefaults.size)
    assertEquals("default_model", dualDefaults[0].key)
  }

  @Test
  fun `custom settings have no prefs key - count is 7`() {
    val customs = allSettingDefs.filterIsInstance<SettingDef.Custom>()
    assertEquals(7, customs.size)
  }

  @Test
  fun `numeric with unit settings - count is 3`() {
    val numericUnits = allSettingDefs.filterIsInstance<SettingDef.NumericWithUnit>()
    assertEquals(3, numericUnits.size)
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
