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

package com.ollitert.llm.server.data.repository

import com.ollitert.llm.server.data.prefs.ServerBindConfig
import com.ollitert.llm.server.data.prefs.ServerBindMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesRepositoryTest {

  private val repository: PreferencesRepository = FakePreferencesRepository()

  @Test
  fun portGetAndSet() {
    assertEquals(8000, repository.getPort())
    repository.savePort(9000)
    assertEquals(9000, repository.getPort())
  }

  @Test
  fun serverBindConfigGetAndSet() {
    assertEquals(ServerBindMode.ALL_INTERFACES, repository.getServerBindConfig().mode)
    repository.setServerBindConfig(ServerBindConfig(ServerBindMode.LOOPBACK, ""))
    assertEquals(ServerBindMode.LOOPBACK, repository.getServerBindConfig().mode)
  }

  @Test
  fun defaultModelNameGetAndSet() {
    assertEquals(null, repository.getDefaultModelName())
    repository.setDefaultModelName("gemma-2b")
    assertEquals("gemma-2b", repository.getDefaultModelName())
  }

  @Test
  fun logPersistenceSettings() {
    assertFalse(repository.isLogPersistenceEnabled())
    repository.setLogPersistenceEnabled(true)
    assertTrue(repository.isLogPersistenceEnabled())
    assertEquals(500, repository.getLogMaxEntries())
    repository.setLogMaxEntries(200)
    assertEquals(200, repository.getLogMaxEntries())
    assertEquals(10080L, repository.getLogAutoDeleteMinutes())
    repository.setLogAutoDeleteMinutes(1440L)
    assertEquals(1440L, repository.getLogAutoDeleteMinutes())
  }
}
