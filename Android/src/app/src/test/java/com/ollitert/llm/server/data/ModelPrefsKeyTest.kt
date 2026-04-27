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

package com.ollitert.llm.server.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelPrefsKeyTest {

  @Test
  fun allowlistModelUsesDownloadFileName() {
    val model = Model(
      name = "Gemma-4-E2B-it",
      downloadFileName = "gemma-4-E2B-it.litertlm",
      imported = false,
    )
    assertEquals("gemma-4-E2B-it.litertlm", model.prefsKey)
  }

  @Test
  fun importedModelUsesName() {
    val model = Model(
      name = "my-custom-model.litertlm",
      downloadFileName = "__imports/my-custom-model.litertlm",
      imported = true,
    )
    assertEquals("my-custom-model.litertlm", model.prefsKey)
  }

  @Test
  fun allowlistModelWithDefaultDownloadFileName() {
    val model = Model(
      name = "SomeModel",
      imported = false,
    )
    assertEquals("_", model.prefsKey)
  }
}
