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
import com.ollitert.llm.server.data.allowlist.DiscoveredModels
import com.ollitert.llm.server.data.allowlist.HuggingFaceCatalog
import com.ollitert.llm.server.data.allowlist.HuggingFaceFile
import com.ollitert.llm.server.data.allowlist.HuggingFaceModel
import com.ollitert.llm.server.data.allowlist.HuggingFaceSearchProgress
import com.ollitert.llm.server.data.allowlist.HuggingFaceSearchLimitException
import com.ollitert.llm.server.data.allowlist.HuggingFaceSort
import com.ollitert.llm.server.data.repository.PreferencesRepository
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.proto.LlmConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class HuggingFaceExploreViewModelTest {
  @get:Rule val temporary = TemporaryFolder()
  private val dispatcher = StandardTestDispatcher()
  @Before fun setup() { Dispatchers.setMain(dispatcher); mockkStatic(Log::class); every { Log.w(any(), any(), any<Throwable>()) } returns 0 }
  @After fun teardown() { unmockkStatic(Log::class); Dispatchers.resetMain() }

  @Test fun dismissingModelErrorAllowsMorePages() = runTest(dispatcher) {
    val model = HuggingFaceModel("author/model")
    val initial = HuggingFaceSearchProgress(emptyList(), "first")
    val page = HuggingFaceSearchProgress(listOf(model), "next")
    val prefs = mockk<PreferencesRepository> { every { getHfToken() } returns "" }
    val catalog = mockk<HuggingFaceCatalog> {
      every { initialSearch(any(), any()) } returns initial
      every { searchPage(initial, any()) } returns page
      every { searchPage(page, any()) } returns page.copy(nextUrl = null)
      every { details(model.id, "", null) } throws IOException("unavailable")
    }
    val vm = HuggingFaceExploreViewModel(mockk(relaxed = true), prefs, catalog, dispatcher)
    advanceUntilIdle()
    vm.open(model)
    advanceUntilIdle()
    assertTrue(vm.state.value.error)
    vm.dismissModelError()
    vm.loadMore()
    advanceUntilIdle()
    assertFalse(vm.state.value.error)
    assertTrue(vm.state.value.searchComplete)
  }

  @Test fun loadsOnePageAndReusesCachedSortWithItsCursor() = runTest(dispatcher) {
    val trending = HuggingFaceSearchProgress(emptyList(), "trending")
    val downloads = HuggingFaceSearchProgress(emptyList(), "downloads")
    val first = HuggingFaceSearchProgress(listOf(HuggingFaceModel("author/first")), "next")
    val second = first.copy(models = first.models + HuggingFaceModel("author/second"), nextUrl = null)
    val popular = HuggingFaceSearchProgress(listOf(HuggingFaceModel("author/popular")), null)
    val prefs = mockk<PreferencesRepository> { every { getHfToken() } returns "" }
    var calls = 0
    val catalog = mockk<HuggingFaceCatalog> {
      every { initialSearch(any(), HuggingFaceSort.TRENDING) } returns trending
      every { initialSearch(any(), HuggingFaceSort.DOWNLOADS) } returns downloads
      every { searchPage(trending, any()) } answers { calls++; first }
      every { searchPage(downloads, any()) } answers { calls++; popular }
      every { searchPage(first, any()) } answers { calls++; second }
    }
    val vm = HuggingFaceExploreViewModel(mockk(relaxed = true), prefs, catalog, dispatcher)
    advanceUntilIdle()
    assertEquals(1, calls)
    assertFalse(vm.state.value.searchComplete)
    vm.sort(HuggingFaceSort.DOWNLOADS)
    advanceUntilIdle()
    assertEquals(2, calls)
    vm.sort(HuggingFaceSort.TRENDING)
    assertEquals(first.models, vm.state.value.models)
    advanceUntilIdle()
    assertEquals(2, calls)
    vm.loadMore()
    advanceUntilIdle()
    assertEquals(3, calls)
    assertEquals(second.models, vm.state.value.models)
    assertTrue(vm.state.value.searchComplete)
  }

  @Test fun changedTokenDoesNotReuseCachedPrivateResults() = runTest(dispatcher) {
    val initial = HuggingFaceSearchProgress(emptyList(), "first")
    var token = "old"
    val prefs = mockk<PreferencesRepository> { every { getHfToken() } answers { token } }
    var calls = 0
    val catalog = mockk<HuggingFaceCatalog> {
      every { initialSearch(any(), any()) } returns initial
      every { searchPage(initial, any()) } answers { calls++; HuggingFaceSearchProgress(listOf(HuggingFaceModel("author/$token")), null) }
    }
    val vm = HuggingFaceExploreViewModel(mockk(relaxed = true), prefs, catalog, dispatcher)
    advanceUntilIdle()
    token = "new"
    vm.sort(HuggingFaceSort.TRENDING)
    advanceUntilIdle()
    assertEquals(2, calls)
    assertEquals("author/new", vm.state.value.models.single().id)
  }

  @Test fun oversizedSearchDoesNotLeaveAnOlderCachedCursor() = runTest(dispatcher) {
    val initial = HuggingFaceSearchProgress(emptyList(), "first")
    val partial = HuggingFaceSearchProgress(listOf(HuggingFaceModel("author/a")), "next", responseBytes = 1)
    val large = partial.copy(nextUrl = null, responseBytes = 8L * 1024 * 1024 + 1)
    val prefs = mockk<PreferencesRepository> { every { getHfToken() } returns "" }
    var starts = 0
    val catalog = mockk<HuggingFaceCatalog> {
      every { initialSearch(any(), any()) } returns initial
      every { searchPage(initial, any()) } answers { starts++; partial }
      every { searchPage(partial, any()) } returns large
    }
    val vm = HuggingFaceExploreViewModel(mockk(relaxed = true), prefs, catalog, dispatcher)
    advanceUntilIdle()
    vm.loadMore()
    advanceUntilIdle()
    vm.sort(HuggingFaceSort.DOWNLOADS)
    advanceUntilIdle()
    vm.sort(HuggingFaceSort.TRENDING)
    advanceUntilIdle()
    assertEquals(3, starts)
  }

  @Test fun savesBeforeReportingImportAndIgnoresSearchDuringSave() = runTest(dispatcher) {
    val dir = temporary.newFolder()
    val context = mockk<Context> { every { getExternalFilesDir(null) } returns dir }
    val preferences = mockk<PreferencesRepository> { every { getHfToken() } returns "" }
    val file = HuggingFaceFile("model.litertlm", size = 123)
    val model = HuggingFaceModel("author/model", sha = "a".repeat(40), siblings = listOf(file))
    val initial = HuggingFaceSearchProgress(emptyList(), "https://huggingface.co/api/models?cursor=first")
    val complete = HuggingFaceSearchProgress(listOf(model), null)
    val catalog = mockk<HuggingFaceCatalog> {
      every { initialSearch(any(), any()) } returns initial
      every { searchPage(any(), any()) } returns complete
      every { details(model.id, "", null) } returns model
    }
    val vm = HuggingFaceExploreViewModel(context, preferences, catalog, dispatcher)
    advanceUntilIdle()
    vm.open(model)
    assertEquals(model.id, vm.state.value.openingModelId)
    assertNull(vm.state.value.details)
    assertFalse(vm.state.value.loading)
    advanceUntilIdle()
    assertNull(vm.state.value.openingModelId)
    val config = LlmConfig.newBuilder().addCompatibleAccelerators("CPU").setDefaultMaxTokens(1024).build()
    vm.add(file, ImportedModel.newBuilder().setLlmConfig(config).build())
    vm.search("ignored")
    vm.closeDetails()
    assertTrue(vm.state.value.saving)
    assertNull(vm.state.value.addedModelName)
    advanceUntilIdle()
    assertEquals(1, DiscoveredModels(dir).read().size)
    assertNotNull(vm.state.value.addedModelName)
    assertEquals("", vm.state.value.query)
    assertTrue(vm.state.value.saving)
  }

  @Test fun savedDetailsStayManagementOnlyWhenRemoteFilesCannotBeRead() = runTest(dispatcher) {
    val directory = temporary.newFolder()
    val context = mockk<Context> { every { getExternalFilesDir(null) } returns directory }
    val preferences = mockk<PreferencesRepository> { every { getHfToken() } returns "" }
    val file = HuggingFaceFile("second.litertlm", size = 123)
    val fallback = HuggingFaceModel("author/model", sha = "a".repeat(40), siblings = listOf(file))
    val result = HuggingFaceSearchProgress(emptyList(), null)
    val catalog = mockk<HuggingFaceCatalog> {
      every { initialSearch(any(), any()) } returns result
      every { searchPage(any(), any()) } returns result
      every { details(fallback.id, "", null) } throws java.io.IOException("Offline")
    }
    val vm = HuggingFaceExploreViewModel(context, preferences, catalog, dispatcher)
    advanceUntilIdle()
    vm.open(fallback, fallback)
    advanceUntilIdle()

    val config = LlmConfig.newBuilder().addCompatibleAccelerators("CPU").build()
    vm.add(file, ImportedModel.newBuilder().setLlmConfig(config).build())
    advanceUntilIdle()

    assertTrue(vm.state.value.detailsOffline)
    assertNull(vm.state.value.addedModelName)
    assertTrue(DiscoveredModels(directory).read().isEmpty())
  }

  @Test fun failedLaterPageKeepsResultsAndRetryResumesFromCursor() = runTest(dispatcher) {
    val model = HuggingFaceModel("author/model")
    val initial = HuggingFaceSearchProgress(emptyList(), "first")
    val partial = HuggingFaceSearchProgress(listOf(model), "second", setOf("first"))
    val complete = partial.copy(nextUrl = null)
    val preferences = mockk<PreferencesRepository> { every { getHfToken() } returns "" }
    var firstAttempts = 0
    val catalog = mockk<HuggingFaceCatalog> {
      every { initialSearch(any(), any()) } returns initial
      every { searchPage(initial, any()) } answers {
        firstAttempts++
        partial
      }
      var pageAttempts = 0
      every { searchPage(partial, any()) } answers { if (++pageAttempts == 1) throw IOException("temporary failure") else complete }
    }
    val vm = HuggingFaceExploreViewModel(mockk(relaxed = true), preferences, catalog, dispatcher)
    advanceUntilIdle()
    vm.loadMore()
    advanceUntilIdle()
    assertEquals(listOf(model), vm.state.value.models)
    assertTrue(vm.state.value.searchError)
    assertFalse(vm.state.value.searchComplete)
    vm.retrySearch()
    advanceUntilIdle()
    assertEquals(1, firstAttempts)
    assertTrue(vm.state.value.searchComplete)
    assertEquals(listOf(model), vm.state.value.models)
  }

  @Test fun aPageCanAdvanceWithoutAddingVisibleModels() = runTest(dispatcher) {
    val model = HuggingFaceModel("author/model")
    val initial = HuggingFaceSearchProgress(emptyList(), "first")
    val firstPage = HuggingFaceSearchProgress(listOf(model), "second", setOf("first"))
    val secondPage = firstPage.copy(nextUrl = "third", visited = setOf("first", "second"))
    val preferences = mockk<PreferencesRepository> { every { getHfToken() } returns "" }
    val catalog = mockk<HuggingFaceCatalog> {
      every { initialSearch(any(), any()) } returns initial
      every { searchPage(initial, any()) } returns firstPage
      every { searchPage(firstPage, any()) } returns secondPage
    }
    val vm = HuggingFaceExploreViewModel(mockk(relaxed = true), preferences, catalog, dispatcher)
    advanceUntilIdle()
    assertEquals(1, vm.state.value.pagesLoaded)

    vm.loadMore()
    advanceUntilIdle()
    assertEquals(listOf(model), vm.state.value.models)
    assertEquals(2, vm.state.value.pagesLoaded)
    assertTrue(!vm.state.value.searchComplete)
  }

  @Test fun openingModelDoesNotCancelAnOngoingSearch() = runTest(dispatcher) {
    val model = HuggingFaceModel("author/model")
    val initial = HuggingFaceSearchProgress(emptyList(), "first")
    val partial = HuggingFaceSearchProgress(listOf(model), "next")
    val complete = partial.copy(nextUrl = null)
    val preferences = mockk<PreferencesRepository> { every { getHfToken() } returns "" }
    lateinit var vm: HuggingFaceExploreViewModel
    val catalog = mockk<HuggingFaceCatalog> {
      every { initialSearch(any(), any()) } returns initial
      every { searchPage(initial, any()) } answers {
        vm.open(model)
        assertTrue(vm.state.value.loading)
        complete
      }
      every { details(model.id, "", null) } returns model
    }
    vm = HuggingFaceExploreViewModel(mockk(relaxed = true), preferences, catalog, dispatcher)
    advanceUntilIdle()
    assertTrue(vm.state.value.searchComplete)
    assertEquals(model, vm.state.value.details)
    vm.closeDetails()
    assertEquals(listOf(model), vm.state.value.models)
  }

  @Test fun oldSearchCannotPublishAfterQueryChanges() = runTest(dispatcher) {
    val old = HuggingFaceSearchProgress(emptyList(), "old")
    val fresh = HuggingFaceSearchProgress(emptyList(), "fresh")
    val stalePage = HuggingFaceSearchProgress(listOf(HuggingFaceModel("author/old")), null)
    val newPage = HuggingFaceSearchProgress(listOf(HuggingFaceModel("author/new")), null)
    val preferences = mockk<PreferencesRepository> { every { getHfToken() } returns "" }
    lateinit var vm: HuggingFaceExploreViewModel
    val catalog = mockk<HuggingFaceCatalog> {
      every { initialSearch("", any()) } returns old
      every { initialSearch("new", any()) } returns fresh
      every { searchPage(old, any()) } answers {
        vm.search("new")
        stalePage
      }
      every { searchPage(fresh, any()) } returns newPage
    }
    vm = HuggingFaceExploreViewModel(mockk(relaxed = true), preferences, catalog, dispatcher)
    advanceUntilIdle()
    assertEquals("new", vm.state.value.query)
    assertEquals(newPage.models, vm.state.value.models)
    assertTrue(vm.state.value.searchComplete)
  }

  @Test fun searchLimitRequiresARefinedSearchInsteadOfRetrying() = runTest(dispatcher) {
    val initial = HuggingFaceSearchProgress(emptyList(), "first")
    val preferences = mockk<PreferencesRepository> { every { getHfToken() } returns "" }
    var calls = 0
    val catalog = mockk<HuggingFaceCatalog> {
      every { initialSearch(any(), any()) } returns initial
      every { searchPage(any(), any()) } answers { calls++; throw HuggingFaceSearchLimitException() }
    }
    val vm = HuggingFaceExploreViewModel(mockk(relaxed = true), preferences, catalog, dispatcher)
    advanceUntilIdle()
    assertTrue(vm.state.value.searchLimitReached)
    vm.retrySearch()
    advanceUntilIdle()
    assertEquals(1, calls)
  }

  @Test fun failedModelDetailsRetryTheSameModel() = runTest(dispatcher) {
    val model = HuggingFaceModel("author/model")
    val initial = HuggingFaceSearchProgress(emptyList(), "first")
    val complete = HuggingFaceSearchProgress(listOf(model), null)
    val preferences = mockk<PreferencesRepository> { every { getHfToken() } returns "" }
    var attempts = 0
    val catalog = mockk<HuggingFaceCatalog> {
      every { initialSearch(any(), any()) } returns initial
      every { searchPage(any(), any()) } returns complete
      every { details(model.id, "", null) } answers { if (++attempts == 1) throw IOException("temporary") else model }
    }
    val vm = HuggingFaceExploreViewModel(mockk(relaxed = true), preferences, catalog, dispatcher)
    advanceUntilIdle()
    vm.open(model)
    advanceUntilIdle()
    assertEquals(model, vm.state.value.failedModel)
    vm.retryError()
    advanceUntilIdle()
    assertEquals(model, vm.state.value.details)
    assertEquals(2, attempts)
  }

  @Test fun searchAndModelFailuresRemainIndependentlyRetryable() = runTest(dispatcher) {
    val model = HuggingFaceModel("author/model")
    val initial = HuggingFaceSearchProgress(emptyList(), "first")
    val partial = HuggingFaceSearchProgress(listOf(model), "next")
    val preferences = mockk<PreferencesRepository> { every { getHfToken() } returns "" }
    var attempts = 0
    val catalog = mockk<HuggingFaceCatalog> {
      every { initialSearch(any(), any()) } returns initial
      every { searchPage(initial, any()) } returns partial
      every { searchPage(partial, any()) } throws IOException("page failure")
      every { details(model.id, "", null) } answers { if (++attempts == 1) throw IOException("details failure") else model }
    }
    val vm = HuggingFaceExploreViewModel(mockk(relaxed = true), preferences, catalog, dispatcher)
    advanceUntilIdle()
    vm.loadMore()
    advanceUntilIdle()
    vm.open(model)
    advanceUntilIdle()
    assertTrue(vm.state.value.searchError)
    assertTrue(vm.state.value.error)
    vm.retryError()
    advanceUntilIdle()
    assertEquals(model, vm.state.value.details)
    assertTrue(vm.state.value.searchError)
  }
}
