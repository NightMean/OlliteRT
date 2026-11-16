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
import com.ollitert.llm.server.data.allowlist.DiscoveredProvider
import com.ollitert.llm.server.data.allowlist.ModelScopeAccessException
import com.ollitert.llm.server.data.allowlist.ModelScopeCatalog
import com.ollitert.llm.server.data.allowlist.ModelScopeFile
import com.ollitert.llm.server.data.allowlist.ModelScopeModel
import com.ollitert.llm.server.data.allowlist.ModelScopePage
import com.ollitert.llm.server.data.allowlist.ModelScopeRateLimitException
import com.ollitert.llm.server.data.allowlist.ModelScopeSort
import com.ollitert.llm.server.data.allowlist.ModelScopeSite
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ModelScopeExploreViewModelTest {
  @get:Rule val temporary = TemporaryFolder()
  private val dispatcher = StandardTestDispatcher()
  @Before fun setup() { Dispatchers.setMain(dispatcher); mockkStatic(Log::class); every { Log.w(any(), any(), any<Throwable>()) } returns 0 }
  @After fun teardown() { unmockkStatic(Log::class); Dispatchers.resetMain() }

  @Test fun modelFilesRecoverAfterTemporaryRateLimit() = runTest(dispatcher) {
    val model = ModelScopeModel("author/model")
    val file = ModelScopeFile("model.litertlm", 123, "a".repeat(40), null)
    var attempts = 0
    val catalog = mockk<ModelScopeCatalog> {
      every { search(any(), any(), any()) } returns ModelScopePage(listOf(model), false, false)
      every { files(model.id, any()) } answers { if (++attempts < 4) throw ModelScopeRateLimitException() else listOf(file) }
    }
    val vm = ModelScopeExploreViewModel(mockk(relaxed = true), catalog, dispatcher, initialSite = ModelScopeSite.CHINA)
    advanceUntilIdle()
    vm.open(model)
    advanceUntilIdle()
    assertEquals(4, attempts)
    assertEquals(model, vm.state.value.details)
    assertEquals(listOf(file), vm.state.value.files)
    assertEquals(ModelScopeSite.CHINA, vm.state.value.detailsSite)
  }

  @Test fun chinaLinkRetriesMetadataAndFilesOnItsOwnSite() = runTest(dispatcher) {
    val model = ModelScopeModel("author/model")
    val file = ModelScopeFile("model.litertlm", 123, "a".repeat(40), null)
    var metadataAttempts = 0
    var fileAttempts = 0
    val aiCatalog = mockk<ModelScopeCatalog> {
      every { search(any(), any(), any()) } returns ModelScopePage(emptyList(), false, false)
    }
    val cnCatalog = mockk<ModelScopeCatalog> {
      every { details(model.id) } answers { if (++metadataAttempts == 1) throw ModelScopeRateLimitException() else model }
      every { files(model.id, "master") } answers { if (++fileAttempts == 1) throw ModelScopeRateLimitException() else listOf(file) }
    }
    val vm = ModelScopeExploreViewModel(mockk(relaxed = true), aiCatalog, dispatcher,
      catalogForSite = { if (it == ModelScopeSite.CHINA) cnCatalog else aiCatalog })
    advanceUntilIdle()
    vm.openLink("https://modelscope.cn/models/author/model")
    advanceUntilIdle()
    assertEquals(2, metadataAttempts)
    assertEquals(2, fileAttempts)
    assertEquals(ModelScopeSite.CHINA, vm.state.value.detailsSite)
    assertEquals(listOf(file), vm.state.value.files)
  }

  @Test fun closingChinaLinkCancelsThePendingRetry() = runTest(dispatcher) {
    val model = ModelScopeModel("author/model")
    var attempts = 0
    val aiCatalog = mockk<ModelScopeCatalog> {
      every { search(any(), any(), any()) } returns ModelScopePage(emptyList(), false, false)
    }
    val cnCatalog = mockk<ModelScopeCatalog> {
      every { details(model.id) } returns model
      every { files(model.id, "master") } answers { attempts++; throw ModelScopeRateLimitException() }
    }
    val vm = ModelScopeExploreViewModel(mockk(relaxed = true), aiCatalog, dispatcher,
      catalogForSite = { if (it == ModelScopeSite.CHINA) cnCatalog else aiCatalog })
    advanceUntilIdle()
    vm.openLink("https://modelscope.cn/models/author/model")
    runCurrent()
    assertEquals(1, attempts)
    vm.closeLink()
    advanceUntilIdle()
    assertEquals(1, attempts)
    assertTrue(!vm.state.value.linkLoading)
  }

  @Test fun dismissingModelErrorAllowsMorePages() = runTest(dispatcher) {
    val model = ModelScopeModel("author/model")
    val next = ModelScopeModel("author/next")
    val catalog = mockk<ModelScopeCatalog> {
      every { search(any(), any(), 1) } returns ModelScopePage(listOf(model), true, false)
      every { search(any(), any(), 2) } returns ModelScopePage(listOf(next), false, false)
      every { files(model.id, any()) } throws IOException("unavailable")
    }
    val vm = ModelScopeExploreViewModel(mockk(relaxed = true), catalog, dispatcher)
    advanceUntilIdle()
    vm.open(model)
    advanceUntilIdle()
    assertTrue(vm.state.value.error)
    vm.dismissModelError()
    vm.loadMore()
    advanceUntilIdle()
    assertTrue(!vm.state.value.error)
    assertEquals(listOf(model, next), vm.state.value.models)
  }

  @Test fun selectionIsSavedBeforeImportCompletes() = runTest(dispatcher) {
    val directory = temporary.newFolder()
    val context = mockk<Context> { every { getExternalFilesDir(null) } returns directory }
    val model = ModelScopeModel("author/model")
    val file = ModelScopeFile("model.litertlm", 123, "a".repeat(40), null)
    val catalog = mockk<ModelScopeCatalog> {
      every { search(any(), any(), any()) } returns ModelScopePage(listOf(model), false, false)
      every { files(model.id, any()) } returns listOf(file)
    }
    val vm = ModelScopeExploreViewModel(context, catalog, dispatcher)
    advanceUntilIdle()
    vm.open(model)
    assertEquals(model.id, vm.state.value.openingModelId)
    assertEquals(null, vm.state.value.details)
    assertTrue(!vm.state.value.loading)
    advanceUntilIdle()
    assertEquals(null, vm.state.value.openingModelId)
    val config = LlmConfig.newBuilder().addCompatibleAccelerators("CPU").setDefaultMaxTokens(1024).build()
    vm.add(file, ImportedModel.newBuilder().setLlmConfig(config).build())
    vm.search("ignored during save")
    advanceUntilIdle()
    assertEquals("", vm.state.value.query)
    assertTrue(vm.state.value.saving)
    assertNotNull(vm.state.value.addedModelName)
    val saved = DiscoveredModels(directory, DiscoveredProvider.MODELSCOPE).read().single()
    assertEquals(file.revision, saved.revision)
    assertEquals(file.path, saved.path)
  }

  @Test fun loadMoreKeepsResultsAndStopsAtTheLastPage() = runTest(dispatcher) {
    val context = mockk<Context>(relaxed = true)
    val first = ModelScopeModel("author/first")
    val second = ModelScopeModel("author/second")
    val catalog = mockk<ModelScopeCatalog> {
      every { search("", ModelScopeSort.DEFAULT, 1) } returns ModelScopePage(listOf(first), true, false)
      every { search("", ModelScopeSort.DEFAULT, 2) } returns ModelScopePage(listOf(second), false, false)
    }
    val vm = ModelScopeExploreViewModel(context, catalog, dispatcher)
    advanceUntilIdle()
    vm.loadMore()
    advanceUntilIdle()
    assertEquals(listOf(first, second), vm.state.value.models)
    assertTrue(!vm.state.value.hasMore)
  }

  @Test fun siteChangePersistsChoiceAndReloadsTheSelectedCatalog() = runTest(dispatcher) {
    val international = ModelScopeModel("author/international")
    val china = ModelScopeModel("author/china")
    val aiCatalog = mockk<ModelScopeCatalog> {
      every { search(any(), any(), any()) } returns ModelScopePage(listOf(international), false, false)
    }
    val cnCatalog = mockk<ModelScopeCatalog> {
      every { search(any(), any(), any()) } returns ModelScopePage(listOf(china), false, false)
    }
    var saved: ModelScopeSite? = null
    val vm = ModelScopeExploreViewModel(mockk(relaxed = true), aiCatalog, dispatcher,
      catalogForSite = { if (it == ModelScopeSite.CHINA) cnCatalog else aiCatalog }, saveSite = { saved = it })
    advanceUntilIdle()
    assertEquals(listOf(international), vm.state.value.models)
    vm.selectSite(ModelScopeSite.CHINA)
    advanceUntilIdle()
    assertEquals(ModelScopeSite.CHINA, saved)
    assertEquals(listOf(china), vm.state.value.models)
  }

  @Test fun linkUsesItsOwnSiteAndImportedModelKeepsThatSite() = runTest(dispatcher) {
    val directory = temporary.newFolder()
    val context = mockk<Context> { every { getExternalFilesDir(null) } returns directory }
    val model = ModelScopeModel("author/model")
    val file = ModelScopeFile("model.litertlm", 123, "a".repeat(40), null)
    val aiCatalog = mockk<ModelScopeCatalog> {
      every { search(any(), any(), any()) } returns ModelScopePage(emptyList(), false, false)
    }
    val cnCatalog = mockk<ModelScopeCatalog> {
      every { details(model.id) } returns model
      every { files(model.id, "master") } returns listOf(file)
    }
    val vm = ModelScopeExploreViewModel(context, aiCatalog, dispatcher,
      catalogForSite = { if (it == ModelScopeSite.CHINA) cnCatalog else aiCatalog })
    advanceUntilIdle()
    vm.openLink("https://modelscope.cn/models/author/model")
    advanceUntilIdle()
    assertEquals(ModelScopeSite.CHINA, vm.state.value.detailsSite)
    val config = LlmConfig.newBuilder().addCompatibleAccelerators("CPU").setDefaultMaxTokens(1024).build()
    vm.add(file, ImportedModel.newBuilder().setLlmConfig(config).build())
    advanceUntilIdle()
    val restored = DiscoveredModels(directory, DiscoveredProvider.MODELSCOPE).read().single()
    assertEquals(ModelScopeSite.CHINA, restored.modelScopeSite)
    assertTrue(restored.toAllowedModel().url!!.startsWith(ModelScopeSite.CHINA.baseUrl))
    assertEquals(ModelScopeSite.INTERNATIONAL, vm.state.value.site)
  }

  @Test fun failedPageKeepsResultsAndOnlyRetriesWhenRequested() = runTest(dispatcher) {
    val context = mockk<Context>(relaxed = true)
    val first = ModelScopeModel("author/first")
    val second = ModelScopeModel("author/second")
    var attempts = 0
    val catalog = mockk<ModelScopeCatalog> {
      every { search("", ModelScopeSort.DEFAULT, 1) } returns ModelScopePage(listOf(first), true, false)
      every { search("", ModelScopeSort.DEFAULT, 2) } answers {
        if (++attempts == 1) throw IOException("offline") else ModelScopePage(listOf(second), false, false)
      }
    }
    val vm = ModelScopeExploreViewModel(context, catalog, dispatcher)
    advanceUntilIdle()
    vm.loadMore()
    assertTrue(vm.state.value.loadingMore)
    assertTrue(!vm.state.value.loading)
    advanceUntilIdle()
    assertEquals(listOf(first), vm.state.value.models)
    assertTrue(vm.state.value.paginationError)
    vm.loadMore()
    advanceUntilIdle()
    assertEquals(1, attempts)
    vm.retryLoadMore()
    advanceUntilIdle()
    assertEquals(listOf(first, second), vm.state.value.models)
    assertTrue(!vm.state.value.paginationError)
  }

  @Test fun repeatedPageStopsAutomaticPagination() = runTest(dispatcher) {
    val context = mockk<Context>(relaxed = true)
    val model = ModelScopeModel("author/model")
    val catalog = mockk<ModelScopeCatalog> {
      every { search(any(), any(), any()) } returns ModelScopePage(listOf(model), true, false)
    }
    val vm = ModelScopeExploreViewModel(context, catalog, dispatcher)
    advanceUntilIdle()
    vm.loadMore()
    advanceUntilIdle()
    assertEquals(2, vm.state.value.page)
    assertTrue(vm.state.value.paginationError)
    assertTrue(vm.state.value.paginationStalled)
    assertEquals(listOf(model), vm.state.value.models)
  }

  @Test fun emptyFirstPageCanLoadTheNextPage() = runTest(dispatcher) {
    val context = mockk<Context>(relaxed = true)
    val model = ModelScopeModel("author/model")
    val catalog = mockk<ModelScopeCatalog> {
      every { search("", ModelScopeSort.DEFAULT, 1) } returns ModelScopePage(emptyList(), true, false)
      every { search("", ModelScopeSort.DEFAULT, 2) } returns ModelScopePage(listOf(model), false, false)
    }
    val vm = ModelScopeExploreViewModel(context, catalog, dispatcher)
    advanceUntilIdle()
    assertTrue(vm.state.value.hasMore)
    vm.loadMore()
    advanceUntilIdle()
    assertEquals(listOf(model), vm.state.value.models)
    assertEquals(2, vm.state.value.page)
    assertTrue(!vm.state.value.paginationError)
  }

  @Test fun emptyIntermediatePageCanContinueToLaterModels() = runTest(dispatcher) {
    val context = mockk<Context>(relaxed = true)
    val first = ModelScopeModel("author/first")
    val third = ModelScopeModel("author/third")
    val catalog = mockk<ModelScopeCatalog> {
      every { search("", ModelScopeSort.DEFAULT, 1) } returns ModelScopePage(listOf(first), true, false)
      every { search("", ModelScopeSort.DEFAULT, 2) } returns ModelScopePage(emptyList(), true, false)
      every { search("", ModelScopeSort.DEFAULT, 3) } returns ModelScopePage(listOf(third), false, false)
    }
    val vm = ModelScopeExploreViewModel(context, catalog, dispatcher)
    advanceUntilIdle()
    vm.loadMore()
    advanceUntilIdle()
    assertTrue(vm.state.value.paginationStalled)
    assertEquals(2, vm.state.value.page)
    vm.retryLoadMore()
    advanceUntilIdle()
    assertEquals(listOf(first, third), vm.state.value.models)
    assertEquals(3, vm.state.value.page)
    assertTrue(!vm.state.value.paginationError)
  }

  @Test fun searchFailureRetriesSearchRatherThanOpeningARepository() = runTest(dispatcher) {
    val context = mockk<Context>(relaxed = true)
    val model = ModelScopeModel("author/model")
    var attempts = 0
    val catalog = mockk<ModelScopeCatalog> {
      every { search(any(), any(), any()) } answers {
        if (++attempts == 1) throw IOException("offline") else ModelScopePage(listOf(model), false, false)
      }
    }
    val vm = ModelScopeExploreViewModel(context, catalog, dispatcher)
    advanceUntilIdle()
    assertTrue(vm.state.value.error)
    assertEquals(null, vm.state.value.failedModel)
    vm.retryError()
    advanceUntilIdle()
    assertEquals(listOf(model), vm.state.value.models)
    assertTrue(!vm.state.value.error)
  }

  @Test fun fileListingFailureRetriesTheSameModel() = runTest(dispatcher) {
    val context = mockk<Context>(relaxed = true)
    val model = ModelScopeModel("author/model")
    val file = ModelScopeFile("model.litertlm", 123, "a".repeat(40), null)
    var attempts = 0
    val catalog = mockk<ModelScopeCatalog> {
      every { search(any(), any(), any()) } returns ModelScopePage(listOf(model), false, false)
      every { files(model.id, any()) } answers { if (++attempts == 1) throw IOException("offline") else listOf(file) }
    }
    val vm = ModelScopeExploreViewModel(context, catalog, dispatcher)
    advanceUntilIdle()
    vm.open(model)
    advanceUntilIdle()
    assertEquals(model, vm.state.value.failedModel)
    assertTrue(vm.state.value.error)
    vm.retryError()
    advanceUntilIdle()
    assertEquals(model, vm.state.value.details)
    assertEquals(listOf(file), vm.state.value.files)
    assertEquals(2, attempts)
  }

  @Test fun fileRateLimitIsExposedAndClearedOnRetry() = runTest(dispatcher) {
    val model = ModelScopeModel("author/model")
    val catalog = mockk<ModelScopeCatalog> {
      every { search(any(), any(), any()) } returns ModelScopePage(listOf(model), false, false)
      every { files(model.id, any()) } throws ModelScopeRateLimitException()
    }
    val vm = ModelScopeExploreViewModel(mockk(relaxed = true), catalog, dispatcher)
    advanceUntilIdle()
    vm.open(model)
    advanceUntilIdle()
    assertTrue(vm.state.value.rateLimited)
    vm.retryError()
    assertTrue(!vm.state.value.rateLimited)
  }

  @Test fun openingLinkCancelsPageLoadWithoutBlockingLaterPaging() = runTest(dispatcher) {
    val context = mockk<Context>(relaxed = true)
    val model = ModelScopeModel("author/model")
    val catalog = mockk<ModelScopeCatalog> {
      every { search(any(), any(), any()) } returns ModelScopePage(listOf(model), true, false)
    }
    val vm = ModelScopeExploreViewModel(context, catalog, dispatcher)
    advanceUntilIdle()
    vm.loadMore()
    assertTrue(vm.state.value.loadingMore)
    vm.beginLink()
    vm.closeLink()
    assertTrue(!vm.state.value.loadingMore)
    assertEquals(listOf(model), vm.state.value.models)
    vm.loadMore()
    assertTrue(vm.state.value.loadingMore)
  }

  @Test fun openingLinkCancelsPendingDetailsRequest() = runTest(dispatcher) {
    val context = mockk<Context>(relaxed = true)
    val model = ModelScopeModel("author/model")
    val catalog = mockk<ModelScopeCatalog> {
      every { search(any(), any(), any()) } returns ModelScopePage(listOf(model), false, false)
    }
    val vm = ModelScopeExploreViewModel(context, catalog, dispatcher)
    advanceUntilIdle()
    vm.open(model)
    vm.beginLink()
    advanceUntilIdle()
    assertEquals(null, vm.state.value.details)
    assertTrue(!vm.state.value.loading)
  }

  @Test fun dismissingLinkResumesCancelledInitialSearch() = runTest(dispatcher) {
    val context = mockk<Context>(relaxed = true)
    val model = ModelScopeModel("author/model")
    val catalog = mockk<ModelScopeCatalog> {
      every { search(any(), any(), any()) } returns ModelScopePage(listOf(model), false, false)
    }
    val vm = ModelScopeExploreViewModel(context, catalog, dispatcher)
    vm.beginLink()
    vm.closeLink()
    advanceUntilIdle()
    assertEquals(listOf(model), vm.state.value.models)
    assertEquals(1, vm.state.value.page)
    assertTrue(!vm.state.value.loading)
  }

  @Test fun deniedChinaLinkIdentifiesTheLinkSiteAndEachFailure() = runTest(dispatcher) {
    val ai = mockk<ModelScopeCatalog> {
      every { search(any(), any(), any()) } returns ModelScopePage(emptyList(), false, false)
    }
    val china = mockk<ModelScopeCatalog> {
      every { details("author/model") } throws ModelScopeAccessException()
    }
    val vm = ModelScopeExploreViewModel(mockk(relaxed = true), ai, dispatcher,
      catalogForSite = { if (it == ModelScopeSite.CHINA) china else ai })
    advanceUntilIdle()
    repeat(2) { attempt ->
      vm.openLink("https://modelscope.cn/models/author/model")
      advanceUntilIdle()
      assertTrue(vm.state.value.accessDenied)
      assertEquals(ModelScopeSite.CHINA, vm.state.value.accessDeniedSite)
      assertEquals((attempt + 1).toLong(), vm.state.value.accessDeniedSequence)
      assertEquals(attempt.toLong(), vm.state.value.acknowledgedAccessSequence)
      vm.acknowledgeAccessDenied()
      assertEquals(vm.state.value.accessDeniedSequence, vm.state.value.acknowledgedAccessSequence)
    }
    assertEquals(ModelScopeSite.INTERNATIONAL, vm.state.value.site)
  }

  @Test fun deniedRemoteFilesAllowOnlyLocalManagementAndExposeAccessFailure() = runTest(dispatcher) {
    val model = ModelScopeModel("author/model")
    val catalog = mockk<ModelScopeCatalog> {
      every { search(any(), any(), any()) } returns ModelScopePage(listOf(model), false, false)
      every { files(model.id, any()) } throws ModelScopeAccessException()
    }
    val vm = ModelScopeExploreViewModel(mockk(relaxed = true), catalog, dispatcher, initialSite = ModelScopeSite.CHINA)
    advanceUntilIdle()
    vm.open(model, hasLocalImports = true)
    advanceUntilIdle()
    assertEquals(model, vm.state.value.details)
    assertTrue(vm.state.value.files.isEmpty())
    assertTrue(vm.state.value.detailsOffline)
    assertTrue(vm.state.value.accessDenied)
    assertEquals(ModelScopeSite.CHINA, vm.state.value.accessDeniedSite)
    assertEquals(1L, vm.state.value.accessDeniedSequence)
  }
}
