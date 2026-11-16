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

import com.ollitert.llm.server.data.allowlist.ProviderTokenVerifier
import com.ollitert.llm.server.data.allowlist.ProviderToken
import com.ollitert.llm.server.data.allowlist.TokenCheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ollitert.llm.server.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ollitert.llm.server.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class ProviderTokensState(val saved: Map<ProviderToken, String>, val current: Map<ProviderToken, String>, val checks: Map<ProviderToken, TokenCheck> = emptyMap()) {
  val hasChanges get() = current != saved
  override fun toString(): String = "ProviderTokensState(redacted)"
}

@HiltViewModel
internal class ProviderTokensViewModel @Inject constructor(private val preferences: PreferencesRepository,
  private val verifier: ProviderTokenVerifier,
  @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
  private val initial = mapOf(
    ProviderToken.HUGGING_FACE to preferences.getHfToken(),
    ProviderToken.MODELSCOPE_AI to preferences.getModelScopeToken(true),
    ProviderToken.MODELSCOPE_CN to preferences.getModelScopeToken(false),
  )
  private val _state = MutableStateFlow(ProviderTokensState(initial, initial))
  val state = _state.asStateFlow()

  private val jobs = mutableMapOf<ProviderToken, Job>()

  fun update(provider: ProviderToken, value: String) {
    jobs.remove(provider)?.cancel()
    _state.update { it.copy(current = it.current + (provider to value.trim()), checks = it.checks - provider) }
  }

  fun test(provider: ProviderToken) {
    val token = _state.value.current.getValue(provider)
    if (token.isBlank()) return
    jobs.remove(provider)?.cancel()
    _state.update { it.copy(checks = it.checks + (provider to TokenCheck.TESTING)) }
    jobs[provider] = viewModelScope.launch {
      val result = withContext(ioDispatcher) { verifier.verify(provider, token) }
      _state.update { it.copy(checks = it.checks + (provider to result)) }
    }
  }

  fun save() {
    val current = _state.value.current
    val saved = _state.value.saved
    if (current[ProviderToken.HUGGING_FACE] != saved[ProviderToken.HUGGING_FACE]) preferences.setHfToken(current.getValue(ProviderToken.HUGGING_FACE))
    if (current[ProviderToken.MODELSCOPE_AI] != saved[ProviderToken.MODELSCOPE_AI]) preferences.setModelScopeToken(true, current.getValue(ProviderToken.MODELSCOPE_AI))
    if (current[ProviderToken.MODELSCOPE_CN] != saved[ProviderToken.MODELSCOPE_CN]) preferences.setModelScopeToken(false, current.getValue(ProviderToken.MODELSCOPE_CN))
    _state.update { it.copy(saved = current) }
  }
}
