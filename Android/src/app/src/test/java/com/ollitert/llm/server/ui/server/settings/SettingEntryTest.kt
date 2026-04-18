package com.ollitert.llm.server.ui.server.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingEntryTest {

  // ─── Boolean ────────────────────────────────────────────────────────────

  @Test
  fun `boolean - initial state is not changed`() {
    val entry = SettingEntry(false)
    assertFalse(entry.isChanged)
    assertEquals(false, entry.saved)
    assertEquals(false, entry.current)
  }

  @Test
  fun `boolean - update makes isChanged true`() {
    val entry = SettingEntry(false)
    entry.update(true)
    assertTrue(entry.isChanged)
    assertEquals(true, entry.current)
    assertEquals(false, entry.saved)
  }

  @Test
  fun `boolean - update back to saved makes isChanged false`() {
    val entry = SettingEntry(false)
    entry.update(true)
    entry.update(false)
    assertFalse(entry.isChanged)
  }

  @Test
  fun `boolean - revert restores current to saved`() {
    val entry = SettingEntry(true)
    entry.update(false)
    assertTrue(entry.isChanged)
    entry.revert()
    assertFalse(entry.isChanged)
    assertEquals(true, entry.current)
  }

  @Test
  fun `boolean - apply advances saved to current`() {
    val entry = SettingEntry(false)
    entry.update(true)
    assertTrue(entry.isChanged)
    entry.apply()
    assertFalse(entry.isChanged)
    assertEquals(true, entry.saved)
    assertEquals(true, entry.current)
  }

  @Test
  fun `boolean - reset sets both saved and current to the given default`() {
    val entry = SettingEntry(true)
    entry.update(false)
    entry.reset(true)
    assertFalse(entry.isChanged)
    assertEquals(true, entry.saved)
    assertEquals(true, entry.current)
  }

  // ─── Int ────────────────────────────────────────────────────────────────

  @Test
  fun `int - update and apply cycle`() {
    val entry = SettingEntry(8000)
    entry.update(9000)
    assertTrue(entry.isChanged)
    assertEquals(8000, entry.saved)
    assertEquals(9000, entry.current)
    entry.apply()
    assertFalse(entry.isChanged)
    assertEquals(9000, entry.saved)
  }

  @Test
  fun `int - revert after update`() {
    val entry = SettingEntry(8000)
    entry.update(9000)
    entry.revert()
    assertFalse(entry.isChanged)
    assertEquals(8000, entry.current)
  }

  @Test
  fun `int - reset to different default`() {
    val entry = SettingEntry(9000)
    entry.reset(8000)
    assertFalse(entry.isChanged)
    assertEquals(8000, entry.saved)
    assertEquals(8000, entry.current)
  }

  // ─── String ─────────────────────────────────────────────────────────────

  @Test
  fun `string - empty default`() {
    val entry = SettingEntry("")
    assertFalse(entry.isChanged)
    entry.update("hello")
    assertTrue(entry.isChanged)
    assertEquals("", entry.saved)
    assertEquals("hello", entry.current)
  }

  @Test
  fun `string - apply and reset`() {
    val entry = SettingEntry("initial")
    entry.update("modified")
    entry.apply()
    assertEquals("modified", entry.saved)
    entry.reset("default")
    assertEquals("default", entry.saved)
    assertEquals("default", entry.current)
  }

  // ─── Long ───────────────────────────────────────────────────────────────

  @Test
  fun `long - update and revert`() {
    val entry = SettingEntry(10080L)
    entry.update(60L)
    assertTrue(entry.isChanged)
    entry.revert()
    assertFalse(entry.isChanged)
    assertEquals(10080L, entry.current)
  }

  // ─── Nullable String ────────────────────────────────────────────────────

  @Test
  fun `nullable string - null default`() {
    val entry = SettingEntry<String?>(null)
    assertFalse(entry.isChanged)
    entry.update("model-name")
    assertTrue(entry.isChanged)
    entry.apply()
    assertFalse(entry.isChanged)
    assertEquals("model-name", entry.saved)
  }

  @Test
  fun `nullable string - null to empty string is a change`() {
    val entry = SettingEntry<String?>(null)
    entry.update("")
    assertTrue(entry.isChanged)
  }

  @Test
  fun `nullable string - reset to null`() {
    val entry = SettingEntry<String?>("something")
    entry.reset(null)
    assertFalse(entry.isChanged)
    assertEquals(null, entry.saved)
    assertEquals(null, entry.current)
  }

  // ─── Edge cases ─────────────────────────────────────────────────────────

  @Test
  fun `apply when not changed is a no-op`() {
    val entry = SettingEntry(42)
    entry.apply()
    assertFalse(entry.isChanged)
    assertEquals(42, entry.saved)
    assertEquals(42, entry.current)
  }

  @Test
  fun `multiple updates - only final value matters for isChanged`() {
    val entry = SettingEntry(0)
    entry.update(1)
    entry.update(2)
    entry.update(0)
    assertFalse(entry.isChanged)
  }

  @Test
  fun `reset after apply resets to new default`() {
    val entry = SettingEntry(false)
    entry.update(true)
    entry.apply()
    assertEquals(true, entry.saved)
    entry.reset(false)
    assertEquals(false, entry.saved)
    assertEquals(false, entry.current)
    assertFalse(entry.isChanged)
  }
}
