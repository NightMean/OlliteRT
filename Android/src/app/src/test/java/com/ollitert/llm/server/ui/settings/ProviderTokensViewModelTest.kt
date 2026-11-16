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
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

class ProviderTokensViewModelTest {
  @Test fun tokenDraftsSaveIndependentlyAndReload() {
    val prefs = FakePreferencesRepository()
    prefs.setHfToken("old")
    val vm = ProviderTokensViewModel(prefs, ProviderTokenVerifier(), Dispatchers.Unconfined)
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
    assertEquals(vm.state.value.current, ProviderTokensViewModel(prefs, ProviderTokenVerifier(), Dispatchers.Unconfined).state.value.current)
    assertFalse(vm.state.value.toString().contains("new"))
  }

  @Test fun editingCancelsPendingTokenChecksAndDoesNotSaveDrafts() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    Dispatchers.setMain(dispatcher)
    try {
      val prefs = FakePreferencesRepository()
      val verifier = mockk<ProviderTokenVerifier>()
      every { verifier.verify(any(), any()) } returns TokenCheck.ACCEPTED
      val vm = ProviderTokensViewModel(prefs, verifier, dispatcher)
      vm.update(ProviderToken.MODELSCOPE_CN, "first")
      vm.test(ProviderToken.MODELSCOPE_CN)
      assertEquals(TokenCheck.TESTING, vm.state.value.checks[ProviderToken.MODELSCOPE_CN])
      vm.update(ProviderToken.MODELSCOPE_CN, "second")
      advanceUntilIdle()
      assertNull(vm.state.value.checks[ProviderToken.MODELSCOPE_CN])
      verify(exactly = 0) { verifier.verify(any(), any()) }
      vm.test(ProviderToken.MODELSCOPE_CN)
      advanceUntilIdle()
      assertEquals(TokenCheck.ACCEPTED, vm.state.value.checks[ProviderToken.MODELSCOPE_CN])
      assertEquals("", prefs.getModelScopeToken(false))
      verify(exactly = 1) { verifier.verify(ProviderToken.MODELSCOPE_CN, "second") }
    } finally {
      Dispatchers.resetMain()
    }
  }
}
