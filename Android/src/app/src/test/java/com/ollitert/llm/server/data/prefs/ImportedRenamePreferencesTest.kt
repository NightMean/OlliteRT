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
package com.ollitert.llm.server.data.prefs

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedRenamePreferencesTest {
  @Test fun failedCommitWithInMemoryMarkerMustBeFlushedBeforeSuccess() {
    val memory = mutableMapOf<String, Any>(
      "favorite_models" to setOf("old"),
      "default_model_name" to "old",
      "system_prompt_old.litertlm" to "prompt",
    )
    val disk = memory.toMutableMap()
    var commits = 0
    val prefs = mockk<SharedPreferences>()
    every { prefs.getString(any(), any()) } answers { memory[firstArg<String>()] as? String ?: secondArg() }
    every { prefs.getStringSet(any(), any()) } answers {
      @Suppress("UNCHECKED_CAST")
      (memory[firstArg<String>()] as? Set<String>) ?: secondArg()
    }
    every { prefs.getBoolean(any(), any()) } answers { memory[firstArg<String>()] as? Boolean ?: secondArg() }
    every { prefs.edit() } answers {
      val pending = mutableMapOf<String, Any?>()
      val editor = mockk<SharedPreferences.Editor>()
      every { editor.putString(any(), any()) } answers { pending[firstArg()] = secondArg<String?>(); editor }
      every { editor.putStringSet(any(), any()) } answers { pending[firstArg()] = secondArg<Set<String>?>()?.toSet(); editor }
      every { editor.putBoolean(any(), any()) } answers { pending[firstArg()] = secondArg<Boolean>(); editor }
      every { editor.remove(any()) } answers { pending[firstArg()] = null; editor }
      every { editor.commit() } answers {
        pending.forEach { (key, value) -> if (value == null) memory.remove(key) else memory[key] = value }
        commits++
        if (commits == 1) false else { disk.clear(); disk.putAll(memory); true }
      }
      editor
    }

    assertFalse(ServerPrefs.completeImportedRename(prefs, "old.litertlm", "new.litertlm", "old", "new"))
    assertEquals(setOf("old"), disk["favorite_models"])
    assertEquals(setOf("new"), memory["favorite_models"])
    assertTrue(ServerPrefs.completeImportedRename(prefs, "old.litertlm", "new.litertlm", "old", "new"))
    assertEquals(2, commits)
    assertEquals(setOf("new"), disk["favorite_models"])
    assertEquals("new", disk["default_model_name"])
    assertEquals("prompt", disk["system_prompt_new.litertlm"])
    assertFalse(disk.containsKey("system_prompt_old.litertlm"))
  }
}
