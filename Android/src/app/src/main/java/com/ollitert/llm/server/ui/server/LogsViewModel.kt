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

package com.ollitert.llm.server.ui.server

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ollitert.llm.server.data.LogLevel
import com.ollitert.llm.server.data.RequestLogEntry
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.data.RequestLogStore
import com.ollitert.llm.server.ui.server.logs.LogFilter
import com.ollitert.llm.server.ui.server.logs.StatusRange
import com.ollitert.llm.server.ui.server.logs.matchesFilter
import com.ollitert.llm.server.ui.server.logs.toggle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel managing logs state, filtering, search query drafts, and actions for [LogsScreen].
 */
@HiltViewModel
class LogsViewModel @Inject constructor(
  @param:ApplicationContext private val context: Context,
) : ViewModel() {

  val entries: StateFlow<List<RequestLogEntry>> = RequestLogStore.entries

  private val _filter = MutableStateFlow(LogFilter())
  val filter: StateFlow<LogFilter> = _filter.asStateFlow()

  private val _searchDraft = MutableStateFlow("")
  val searchDraft: StateFlow<String> = _searchDraft.asStateFlow()

  private val _searchBarVisible = MutableStateFlow(false)
  val searchBarVisible: StateFlow<Boolean> = _searchBarVisible.asStateFlow()

  private val _isSearching = MutableStateFlow(false)
  val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

  private val _showClearConfirmDialog = MutableStateFlow(false)
  val showClearConfirmDialog: StateFlow<Boolean> = _showClearConfirmDialog.asStateFlow()

  private val _showClearActiveDialog = MutableStateFlow(false)
  val showClearActiveDialog: StateFlow<Boolean> = _showClearActiveDialog.asStateFlow()

  // Track search snapshot: freeze entries when text query is active to prevent jitter
  private var searchSnapshot: List<RequestLogEntry> = emptyList()
  private var lastCommittedQuery: String = ""

  val displayedEntries: StateFlow<List<RequestLogEntry>> = combine(entries, _filter) { allEntries, currentFilter ->
    if (currentFilter.query.isNotEmpty()) {
      if (currentFilter.query != lastCommittedQuery) {
        lastCommittedQuery = currentFilter.query
        searchSnapshot = allEntries
      }
      _isSearching.value = true
      val filtered = searchSnapshot.filter { it.matchesFilter(currentFilter, context) }
      _isSearching.value = false
      filtered
    } else {
      lastCommittedQuery = ""
      _isSearching.value = false
      if (!currentFilter.isActive) {
        allEntries
      } else {
        allEntries.filter { it.matchesFilter(currentFilter, context) }
      }
    }
  }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val autoExpand: Boolean get() = ServerPrefs.isAutoExpandLogs(context)
  val wrapLogText: Boolean get() = ServerPrefs.isWrapLogText(context)

  fun setSearchDraft(query: String) {
    _searchDraft.value = query
  }

  fun toggleSearchBar() {
    _searchBarVisible.value = !_searchBarVisible.value
  }

  fun hideSearchBar() {
    _searchBarVisible.value = false
  }

  fun commitSearch() {
    _filter.value = _filter.value.copy(query = _searchDraft.value.trim())
  }

  fun clearSearch() {
    _searchDraft.value = ""
    _filter.value = _filter.value.copy(query = "")
  }

  fun clearAllFilters() {
    _searchDraft.value = ""
    _filter.value = LogFilter()
  }

  fun toggleMethod(method: String) {
    _filter.value = _filter.value.copy(methods = _filter.value.methods.toggle(method))
  }

  fun toggleStatusRange(range: StatusRange) {
    _filter.value = _filter.value.copy(statusRanges = _filter.value.statusRanges.toggle(range))
  }

  fun toggleLevel(level: LogLevel) {
    _filter.value = _filter.value.copy(levels = _filter.value.levels.toggle(level))
  }

  fun setShowClearConfirmDialog(show: Boolean) {
    _showClearConfirmDialog.value = show
  }

  fun setShowClearActiveDialog(show: Boolean) {
    _showClearActiveDialog.value = show
  }

  fun clearLogs() {
    RequestLogStore.clear()
    _showClearConfirmDialog.value = false
  }

  fun cancelRequest(entryId: String) {
    RequestLogStore.cancelRequest(entryId)
  }
}
