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

import com.ollitert.llm.server.data.repository.FakePreferencesRepository
import org.junit.Assert.*
import org.junit.Test

class ProviderTokensViewModelTest {
  @Test fun tokenDraftsSaveIndependentlyAndReload() {
    val prefs = FakePreferencesRepository()
    prefs.setHfToken("old")
    val vm = ProviderTokensViewModel(prefs)
    vm.update(ProviderToken.HUGGING_FACE, " new ")
    vm.update(ProviderToken.MODELSCOPE_AI, "ai")
    vm.update(ProviderToken.MODELSCOPE_CN, "cn")
    assertTrue(vm.state.value.hasChanges)
    assertEquals("old", prefs.getHfToken())
    vm.save()
    assertFalse(vm.state.value.hasChanges)
    assertEquals("new", prefs.getHfToken())
    assertEquals("ai", prefs.getModelScopeToken(true))
    assertEquals("cn", prefs.getModelScopeToken(false))
    assertEquals(vm.state.value.current, ProviderTokensViewModel(prefs).state.value.current)
    assertFalse(vm.state.value.toString().contains("new"))
  }
}
