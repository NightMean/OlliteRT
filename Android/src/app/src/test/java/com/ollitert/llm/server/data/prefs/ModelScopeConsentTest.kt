package com.ollitert.llm.server.data.prefs

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class ModelScopeConsentTest {
  @Test
  fun fallbackIsOptInAndCanBeRevoked() {
    val saved = mutableMapOf<String, Boolean>()
    val prefs = mockk<SharedPreferences>()
    val editor = mockk<SharedPreferences.Editor>()
    every { prefs.getBoolean(any(), any()) } answers { saved[firstArg()] ?: secondArg() }
    every { prefs.edit() } returns editor
    every { editor.putBoolean(any(), any()) } answers {
      saved[firstArg()] = secondArg()
      editor
    }
    every { editor.apply() } returns Unit

    assertFalse(ServerPrefsNetwork.isModelScopeFallbackEnabled(prefs))
    ServerPrefsNetwork.setModelScopeFallbackEnabled(prefs, true)
    assertTrue(ServerPrefsNetwork.isModelScopeFallbackEnabled(prefs))
    ServerPrefsNetwork.setModelScopeFallbackEnabled(prefs, false)
    assertFalse(ServerPrefsNetwork.isModelScopeFallbackEnabled(prefs))
  }
}
