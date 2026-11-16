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
import com.ollitert.llm.server.data.allowlist.DiscoveredModels
import com.ollitert.llm.server.data.allowlist.DiscoveredProvider
import com.ollitert.llm.server.data.allowlist.ModelScopeCatalog
import com.ollitert.llm.server.data.allowlist.ModelScopeAccessException
import com.ollitert.llm.server.data.allowlist.ModelScopeFile
import com.ollitert.llm.server.data.allowlist.ModelScopeModel
import com.ollitert.llm.server.data.allowlist.ModelScopeRateLimitException
import com.ollitert.llm.server.data.allowlist.ModelScopeSort
import com.ollitert.llm.server.data.allowlist.ModelScopeSite
import com.ollitert.llm.server.data.allowlist.parseModelScopeLink
import com.ollitert.llm.server.data.allowlist.retryModelScopeRateLimit
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.proto.ImportedModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ModelScopeExploreState(
  val query: String = "",
  val sort: ModelScopeSort = ModelScopeSort.DEFAULT,
  val site: ModelScopeSite = ModelScopeSite.INTERNATIONAL,
  val detailsSite: ModelScopeSite = ModelScopeSite.INTERNATIONAL,
  val models: List<ModelScopeModel> = emptyList(),
  val details: ModelScopeModel? = null,
  val detailsOffline: Boolean = false,
  val openingModelId: String? = null,
  val files: List<ModelScopeFile> = emptyList(),
  val loading: Boolean = false,
  val loadingMore: Boolean = false,
  val paginationError: Boolean = false,
  val paginationStalled: Boolean = false,
  val saving: Boolean = false,
  val error: Boolean = false,
  val rateLimited: Boolean = false,
  val accessDenied: Boolean = false,
  val alreadyAdded: Boolean = false,
  val hasMore: Boolean = false,
  val searchLimitReached: Boolean = false,
  val page: Int = 0,
  val addedModelName: String? = null,
  val linkLoading: Boolean = false,
  val linkError: Boolean = false,
  val selectedPath: String? = null,
  val failedModel: ModelScopeModel? = null,
)

@HiltViewModel
internal class ModelScopeExploreViewModel internal constructor(
  private val context: Context,
  private val catalog: ModelScopeCatalog,
  private val io: CoroutineDispatcher,
  initialSite: ModelScopeSite = ModelScopeSite.INTERNATIONAL,
  private val catalogForSite: (ModelScopeSite) -> ModelScopeCatalog = { catalog },
  private val saveSite: (ModelScopeSite) -> Unit = {},
) : ViewModel() {
  @Inject constructor(@ApplicationContext context: Context) : this(
    context, ModelScopeCatalog(), Dispatchers.IO,
    ModelScopeSite.entries.firstOrNull { it.name == ServerPrefs.getModelScopeSite(context) } ?: ModelScopeSite.INTERNATIONAL,
    { site -> ModelScopeCatalog(endpoint = site.baseUrl,
      token = ServerPrefs.getModelScopeToken(context, site == ModelScopeSite.INTERNATIONAL)) },
    { site -> ServerPrefs.setModelScopeSite(context, site.name) },
  )

  private val _state = MutableStateFlow(ModelScopeExploreState(site = initialSite, detailsSite = initialSite))
  val state = _state.asStateFlow()
  private var job: Job? = null

  init { refresh() }

  fun search(query: String) {
    if (_state.value.saving) return
    _state.update { it.copy(query = query) }
    load(reset = true, debounce = true)
  }

  fun sort(sort: ModelScopeSort) {
    if (_state.value.saving) return
    _state.update { it.copy(sort = sort) }
    load(reset = true)
  }

  fun selectSite(site: ModelScopeSite) {
    if (_state.value.saving || _state.value.site == site) return
    saveSite(site)
    _state.update { it.copy(site = site) }
    load(reset = true)
  }

  fun refresh() = load(reset = true)
  fun loadMore() {
    val current = _state.value
    if (current.hasMore && !current.loading && !current.loadingMore && current.openingModelId == null && !current.paginationError &&
      !current.error && !current.saving && !current.linkLoading && current.details == null) load(reset = false)
  }

  fun retryLoadMore() {
    val current = _state.value
    if (current.paginationError && !current.loading && !current.loadingMore && current.openingModelId == null && !current.saving) load(reset = false)
  }

  fun retryError() {
    val failed = _state.value.failedModel
    if (failed != null) open(failed) else refresh()
  }

  fun dismissModelError() {
    _state.update { if (it.failedModel != null) it.copy(error = false, failedModel = null, rateLimited = false, accessDenied = false) else it }
  }

  private fun load(reset: Boolean, debounce: Boolean = false) {
    if (_state.value.saving) return
    job?.cancel()
    val before = _state.value
    val page = if (reset) 1 else before.page + 1
    _state.update {
      it.copy(loading = reset, loadingMore = !reset, openingModelId = null, error = false, rateLimited = false, accessDenied = false, paginationError = false, paginationStalled = false, failedModel = null, details = null,
        models = if (reset) emptyList() else it.models,
        page = if (reset) 0 else it.page,
        hasMore = if (reset) false else it.hasMore,
        searchLimitReached = if (reset) false else it.searchLimitReached)
    }
    job = viewModelScope.launch {
      try {
        if (debounce) delay(300)
        val result = withContext(io) { catalogForSite(before.site).search(before.query, before.sort, page) }
        _state.update {
          val models = if (reset) result.models else (it.models + result.models).distinctBy { model -> model.id }
          // Do not automatically request every remaining page if the provider repeats a page.
          val stalled = !reset && models.size == it.models.size && result.hasMore
          it.copy(models = models, page = page, hasMore = result.hasMore,
            searchLimitReached = result.searchLimitReached, loading = false, loadingMore = false,
            paginationError = stalled, paginationStalled = stalled)
        }
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) {
        Log.w("OlliteRT.ModelScope", "Model search failed", e)
        _state.update { it.copy(loading = false, loadingMore = false, error = reset, paginationError = !reset, rateLimited = e is ModelScopeRateLimitException, accessDenied = e is ModelScopeAccessException) }
      }
    }
  }

  fun open(model: ModelScopeModel, hasLocalImports: Boolean = false) {
    if (_state.value.saving) return
    job?.cancel()
    val site = _state.value.site
    _state.update { it.copy(loading = false, loadingMore = false, openingModelId = model.id, detailsSite = site, error = false, rateLimited = false, accessDenied = false, failedModel = null) }
    job = viewModelScope.launch {
      try {
        val files = withContext(io) { retryModelScopeRateLimit { catalogForSite(site).files(model.id) } }
        _state.update { it.copy(details = model, detailsOffline = false, files = files, selectedPath = null, openingModelId = null) }
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) {
        Log.w("OlliteRT.ModelScope", "Model file listing failed", e)
        _state.update { if (hasLocalImports) it.copy(openingModelId = null, details = model, detailsOffline = true, files = emptyList(), selectedPath = null)
          else it.copy(openingModelId = null, error = true, failedModel = model, rateLimited = e is ModelScopeRateLimitException, accessDenied = e is ModelScopeAccessException) }
      }
    }
  }

  fun beginLink() {
    if (_state.value.saving) return
    job?.cancel()
    _state.update { it.copy(loading = false, loadingMore = false, openingModelId = null, error = false, rateLimited = false, accessDenied = false, failedModel = null, linkError = false) }
  }

  fun openLink(value: String) {
    if (_state.value.saving) return
    val link = parseModelScopeLink(value)
    if (link == null) { _state.update { it.copy(linkError = true, rateLimited = false, accessDenied = false) }; return }
    val site = link.site ?: _state.value.site
    job?.cancel()
    _state.update { it.copy(linkLoading = true, linkError = false, rateLimited = false, accessDenied = false, loading = false) }
    job = viewModelScope.launch {
      try {
        val (model, files) = withContext(io) {
          catalogForSite(site).let { catalog ->
            val model = retryModelScopeRateLimit { catalog.details(link.repository) }
            val files = retryModelScopeRateLimit { catalog.files(link.repository, link.revision ?: "master") }
            model to files
          }
        }
        require(link.path == null || files.any { it.path == link.path })
        _state.update { it.copy(details = model, detailsSite = site, detailsOffline = false, files = files, selectedPath = link.path, linkLoading = false) }
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) {
        Log.w("OlliteRT.ModelScope", "Could not open repository link", e)
        _state.update { it.copy(linkLoading = false, linkError = true, rateLimited = e is ModelScopeRateLimitException, accessDenied = e is ModelScopeAccessException) }
      }
    }
  }

  fun closeLink() {
    if (_state.value.linkLoading) job?.cancel()
    _state.update { it.copy(linkLoading = false, linkError = false) }
    if (_state.value.page == 0) refresh()
  }

  fun closeDetails() {
    if (_state.value.saving) return
    _state.update { it.copy(details = null, detailsOffline = false, alreadyAdded = false, error = false) }
    if (_state.value.page == 0) refresh()
  }
  fun consumeImportedModel() { _state.update { it.copy(addedModelName = null) } }

  fun add(file: ModelScopeFile, info: ImportedModel) {
    val current = _state.value
    val model = current.details ?: return
    if (current.loading || current.saving || current.detailsOffline || file !in current.files) return
    _state.update { it.copy(loading = true, saving = true, error = false, alreadyAdded = false) }
    job = viewModelScope.launch {
      try {
        val inserted = withContext(io) {
          val directory = context.getExternalFilesDir(null) ?: error("Model storage unavailable")
          DiscoveredModels(directory, DiscoveredProvider.MODELSCOPE)
            .addConfiguredFile(model.id, file.path, file.revision, file.size, info, current.detailsSite)
        }
        _state.update { it.copy(loading = false, saving = inserted != null, addedModelName = inserted, alreadyAdded = inserted == null) }
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) {
        Log.w("OlliteRT.ModelScope", "Could not save model", e)
        _state.update { it.copy(loading = false, saving = false, error = true) }
      }
    }
  }
}
