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

package com.ollitert.llm.server.ui.modelmanager

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ollitert.llm.server.data.allowlist.DiscoveredModel
import com.ollitert.llm.server.data.allowlist.DiscoveredModels
import com.ollitert.llm.server.data.allowlist.HuggingFaceCatalog
import com.ollitert.llm.server.data.allowlist.HuggingFaceFile
import com.ollitert.llm.server.data.allowlist.HuggingFaceModel
import com.ollitert.llm.server.data.allowlist.HuggingFaceSort
import com.ollitert.llm.server.data.allowlist.HuggingFaceSearchProgress
import com.ollitert.llm.server.data.allowlist.HuggingFaceSearchLimitException
import com.ollitert.llm.server.data.allowlist.HuggingFacePaginationException
import com.ollitert.llm.server.data.allowlist.isValidHuggingFaceModelId
import com.ollitert.llm.server.data.allowlist.parseHuggingFaceLink
import com.ollitert.llm.server.data.repository.PreferencesRepository
import com.ollitert.llm.server.proto.ImportedModel
import java.util.Locale
import java.security.MessageDigest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SEARCH_CACHE_MAX_ENTRIES = 4
private const val SEARCH_CACHE_MAX_BYTES = 8L * 1024 * 1024
private const val SEARCH_CACHE_MAX_AGE_NANOS = 300_000_000_000L

internal data class HuggingFaceExploreState(
  val query: String = "",
  val sort: HuggingFaceSort = HuggingFaceSort.TRENDING,
  val models: List<HuggingFaceModel> = emptyList(),
  val details: HuggingFaceModel? = null,
  val openingModelId: String? = null,
  val loading: Boolean = false,
  val loadingMore: Boolean = false,
  val searchComplete: Boolean = false,
  val searchError: Boolean = false,
  val searchLimitReached: Boolean = false,
  val searchInvalidPagination: Boolean = false,
  val error: Boolean = false,
  val failedModel: HuggingFaceModel? = null,
  val addedModelName: String? = null,
  val saving: Boolean = false,
  val alreadyAdded: Boolean = false,
  val linkLoading: Boolean = false,
  val linkError: Boolean = false,
  val selectedPath: String? = null,
)

@HiltViewModel
internal class HuggingFaceExploreViewModel internal constructor(
  private val context: Context,
  private val preferences: PreferencesRepository,
  private val client: HuggingFaceCatalog,
  private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
  @Inject constructor(@ApplicationContext context: Context, preferences: PreferencesRepository) :
    this(context, preferences, HuggingFaceCatalog(), Dispatchers.IO)
  private val _state = MutableStateFlow(HuggingFaceExploreState())
  val state = _state.asStateFlow()
  private var job: Job? = null
  private var linkJob: Job? = null
  private var searchJob: Job? = null
  private val searchLock = Any()
  private var searchGeneration = 0L
  private var searchProgress: HuggingFaceSearchProgress? = null
  private data class SearchKey(val query: String, val sort: HuggingFaceSort, val tokenHash: List<Byte>)
  private data class CachedSearch(val progress: HuggingFaceSearchProgress, val savedAt: Long)
  private val cachedSearches = LinkedHashMap<SearchKey, CachedSearch>(4, 0.75f, true)
  private var currentKey: SearchKey? = null

  private fun key(query: String, sort: HuggingFaceSort, token: String?): SearchKey = SearchKey(
    query.trim(), sort, MessageDigest.getInstance("SHA-256").digest(token.orEmpty().toByteArray(Charsets.UTF_8)).toList())

  init { refresh() }

  fun search(query: String) {
    if (_state.value.saving) return
    synchronized(searchLock) {
      _state.update { it.copy(query = query) }
      load(debounce = true)
    }
  }

  fun sort(sort: HuggingFaceSort) {
    if (_state.value.saving) return
    synchronized(searchLock) {
      _state.update { it.copy(sort = sort) }
      load(debounce = false)
    }
  }

  fun refresh() = load(debounce = false, refresh = true)
  fun retrySearch() { if (!_state.value.searchLimitReached && !_state.value.searchInvalidPagination) load(debounce = false, resume = true) }
  fun retryError() { _state.value.failedModel?.let { open(it) } ?: refresh() }
  fun loadMore() {
    val state = _state.value
    if (!state.loading && !state.loadingMore && !state.searchError && !state.error && !state.saving &&
      state.openingModelId == null && state.details == null && !state.linkLoading && searchProgress?.nextUrl != null) {
      load(debounce = false, resume = true)
    }
  }

  private fun load(debounce: Boolean, resume: Boolean = false, refresh: Boolean = false) {
    if (_state.value.saving) return
    searchJob?.cancel()
    if (!resume) job?.cancel()
    val query = _state.value.query
    val sort = _state.value.sort
    val token = preferences.getHfToken()
    val key = key(query, sort, token)
    if (resume && currentKey != key) { load(debounce = false, refresh = true); return }
    val (generation, progress) = synchronized(searchLock) {
      searchGeneration++
      if (refresh) cachedSearches.remove(key)
      val cached = if (!resume && !refresh) cachedSearches[key]?.takeIf { System.nanoTime() - it.savedAt < SEARCH_CACHE_MAX_AGE_NANOS } else null
      if (cached != null) {
        currentKey = key
        searchProgress = cached.progress
        _state.update { it.copy(loading = false, loadingMore = false, searchComplete = cached.progress.nextUrl == null,
          searchError = false, searchLimitReached = false, searchInvalidPagination = false, error = false, failedModel = null,
          details = null, openingModelId = null, models = cached.progress.models) }
        return
      }
      val initial = if (resume) searchProgress ?: client.initialSearch(query, sort) else client.initialSearch(query, sort)
      currentKey = key
      searchProgress = initial
      _state.update { it.copy(loading = !resume, loadingMore = resume, searchComplete = false, searchError = false, searchLimitReached = false, searchInvalidPagination = false,
        error = if (resume) it.error else false, failedModel = if (resume) it.failedModel else null, details = if (resume) it.details else null,
        openingModelId = if (resume) it.openingModelId else null, models = initial.models) }
      searchGeneration to initial
    }
    searchJob = viewModelScope.launch {
      try {
        if (debounce) delay(300)
        val result = withContext(ioDispatcher) {
          client.searchPage(progress, token).also { kotlin.coroutines.coroutineContext.ensureActive() }
        }
        synchronized(searchLock) {
          if (searchGeneration == generation) {
            searchProgress = result
            if (result.responseBytes <= SEARCH_CACHE_MAX_BYTES) {
              cachedSearches[key] = CachedSearch(result, System.nanoTime())
              while (cachedSearches.size > SEARCH_CACHE_MAX_ENTRIES || cachedSearches.values.sumOf { it.progress.responseBytes } > SEARCH_CACHE_MAX_BYTES) {
                cachedSearches.remove(cachedSearches.keys.first())
              }
            } else cachedSearches.remove(key)
            _state.update { it.copy(models = result.models, loading = false, loadingMore = false, searchComplete = result.nextUrl == null) }
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w("OlliteRT.HFExplore", "Model search failed", e)
        synchronized(searchLock) {
          if (searchGeneration == generation) _state.update { it.copy(loading = false, loadingMore = false, searchError = true,
            searchLimitReached = e is HuggingFaceSearchLimitException, searchInvalidPagination = e is HuggingFacePaginationException) }
        }
      }
    }
  }

  fun open(model: HuggingFaceModel) {
    if (_state.value.saving) return
    job?.cancel()
    _state.update { it.copy(openingModelId = model.id, error = false, failedModel = null) }
    job = viewModelScope.launch {
      try {
        val details = withContext(ioDispatcher) { client.details(model.id, preferences.getHfToken()) }
        require(details.id.equals(model.id, ignoreCase = true) && isValidHuggingFaceModelId(details.id)) { "Invalid repository details" }
        _state.update { it.copy(details = details, selectedPath = null, openingModelId = null) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w("OlliteRT.HFExplore", "Model details failed", e)
        _state.update { it.copy(openingModelId = null, error = true, failedModel = model) }
      }
    }
  }

  fun beginLink() {
    if (_state.value.saving) return
    job?.cancel()
    _state.update { it.copy(openingModelId = null, error = false, failedModel = null, linkError = false) }
  }

  fun openLink(value: String) {
    if (_state.value.saving) return
    val link = parseHuggingFaceLink(value)
    if (link == null) {
      _state.update { it.copy(linkError = true) }
      return
    }
    job?.cancel()
    linkJob?.cancel()
    _state.update { it.copy(error = false, linkLoading = true, linkError = false) }
    linkJob = viewModelScope.launch {
      try {
        val details = withContext(ioDispatcher) { client.details(link.repository, preferences.getHfToken(), link.revision) }
        require(details.id.equals(link.repository, ignoreCase = true) && isValidHuggingFaceModelId(details.id))
        require(link.path == null || details.modelFiles.any { it.path == link.path })
        _state.update { it.copy(details = details, selectedPath = link.path, linkLoading = false) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w("OlliteRT.HFExplore", "Could not open repository link", e)
        _state.update { it.copy(linkLoading = false, linkError = true) }
      }
    }
  }

  fun consumeImportedModel() { _state.update { it.copy(addedModelName = null) } }

  fun closeLink() {
    linkJob?.cancel()
    _state.update { it.copy(linkLoading = false, linkError = false) }
  }

  fun closeDetails() {
    if (_state.value.saving) return
    _state.update { it.copy(details = null, error = false, alreadyAdded = false) }
  }

  fun add(file: HuggingFaceFile, info: ImportedModel) {
    val details = _state.value.details ?: return
    val revision = details.sha ?: return
    val size = file.sizeBytes ?: return
    if (_state.value.saving) return
    _state.update { it.copy(saving = true, error = false, alreadyAdded = false) }
    job = viewModelScope.launch {
      try {
        val inserted = withContext(ioDispatcher) {
          val directory = context.getExternalFilesDir(null) ?: error("Model storage unavailable")
          val config = info.llmConfig
          val discovered = DiscoveredModel(
            repository = details.id, path = file.path, revision = revision, size = size,
            vision = config.supportImage, audio = config.supportAudio, thinking = config.supportThinking,
            tools = config.supportTools, accelerators = config.compatibleAcceleratorsList.map { it.trim().lowercase(Locale.ROOT) },
            displayName = info.displayName, maxTokens = config.defaultMaxTokens, topK = config.defaultTopk,
            topP = config.defaultTopp, temperature = config.defaultTemperature,
            speculativeDecoding = config.supportSpeculativeDecoding,
          )
          val inserted = DiscoveredModels(directory).add(discovered)
          if (inserted) discovered.toAllowedModel().name else null
        }
        _state.update { it.copy(saving = inserted != null, addedModelName = inserted, alreadyAdded = inserted == null) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w("OlliteRT.HFExplore", "Could not save selected model", e)
        _state.update { it.copy(saving = false, error = true) }
      }
    }
  }
}
